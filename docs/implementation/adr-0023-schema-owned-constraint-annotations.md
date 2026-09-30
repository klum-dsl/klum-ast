# ADR 0023 implementation plan: schema-owned constraint annotations

Status: S0–S2 merged; S3 delivered with production-marker named-module proof. S4 is optional and undecided.

This is the implementation plan for the accepted field contract in
[ADR 0023](../adr/0023-schema-owned-constraint-annotations.md), related to
[#799](https://github.com/klum-dsl/klum-ast/issues/799). No production behavior or tracker state changes here.
S0 selected the annotation member encoding. The accepted scope covers
owned, `LINK`, and `OPTIONAL_LINK` relationships; combination with defaults is optional.

## Confirmed behavior and failure paths

| Evidence | Current behavior / gap |
| --- | --- |
| `DefaultPhase`, `AnnotationHelper.getMetaAnnotated`, `DefaultValuesCheck`, `DefaultValuesSpec` | Runtime-visible, directly meta-annotated class/field annotations supply nondefault members to child/model Builder defaults. `valueTarget` only remaps `value`. Two separate domain annotations work without changing this path; a future combined annotation needs explicit mapping because extra bound attributes would be treated as defaults. |
| `ValidationPhase`, `SingleObjectValidationHandler`, `KlumFieldAnnotationsValidator` | `VALIDATE` traverses completed Models and executes `InstanceValidator`s once per source Model. The existing field validator already walks declared fields in each DSL layer and reads final field values by reflection. That is the narrow source-field seam, including LINK/OPTIONAL_LINK fields. Its `@Validate.Ignore` gate cannot be reused for separate constraint annotations. |
| `CompositionTraversal`, `ModelVisitingPhaseAction` | The standard visitor skips `LINK` fields and repeats by object identity; `OPTIONAL_LINK` is not excluded by its simple `isLink` check. Driving relationship-local constraints from child visitation would miss `LINK` and can collapse multiple source fields. The proposed field validator instead evaluates the owner when visited, regardless of whether a target is traversed. Existing OPTIONAL_LINK traversal behavior still needs a regression probe, but does not define field-rule eligibility. |
| `KlumValidationIssue`, `KlumValidationResult`, `KlumObjectSupport.Validation`, `VERIFY` | Issues carry object path, member, message, and level. Results are held per Model and deduplicate identical issues in a sorted set. Put a failure on the source owner's result with its field member; include collection index/map key in the message so repeated entries remain distinct. Stored validation can be inspected or verified without rerunning rules. |
| `docs/user/Validation.md`, `docs/user/Default-Values.md` | Current guidance covers direct `@Validate`, `@DefaultValues`, and optional Jakarta Bean Validation, but no combined domain-specific annotation. |

The metadata lives on Schema declarations. It must never be implemented as a new field of a Domain API type merely to make
the rule or documentation tooling accessible.

### Local feasibility probe (Groovy 3 only)

On 2026-09-29, a temporary `AbstractDSLSpec` probe ran with
`./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.ConstraintLinkLifecycleProbeTest --offline`.
The final version passed: an owner `@Validate` method read one completed external `LinkNode` through two `LINK` fields
and one `OPTIONAL_LINK` field, reported three distinct source-field issues through `klumValidation.errorAt`, left the
external target's stored result unchanged, and allowed a null optional value on a later valid construction. The temporary
test was removed after the run, leaving this design branch documentation-only.

An earlier version using a typed `@Validate` closure on `LINK` fields failed despite successful Schema compilation:
the generated closure expected `LinkNode$Builder`, while `VALIDATE` passed `LinkNode`; the resulting
`MissingMethodException` was attributed to source members `first` and `second` in the owner result. This establishes
both completed value availability and a real typed-closure compatibility risk. At that point it did **not** prove that
the proposed meta-annotation callback compiled or ran across Groovy 4/5 or JPMS; the committed S0 proof below supplies
that evidence.

### Committed S0 compatibility proof

`ConstraintCallbackProbeTest` proves a test-only runtime-retained meta-annotation member encoded as
`Class<?> value()`, with an authored `(concreteAnnotation, declaredModelType)` closure. It receives
completed Models at owner `VALIDATE` for owned, external `LINK`, same-root `LINK`, and `OPTIONAL_LINK` fields. The
parameter remains the authored Model type rather than being rewritten to a Builder. A supertype target parameter
(`Object`) works. A test-only semantic-analysis guard rejects a narrower `KafkaPool` parameter for a declared `Pool`
field, both in same-source and separately compiled annotation consumers. Null optional values skip evaluation;
external target identity, model path, validation count, and stored result remain unchanged. Reflection confirms the
annotation member's exact generic return type is `java.lang.Class<?>`, with no Groovy type in its signature.

The extended `JpmsPackageBoundaryTest` separately compiles the Schema and Java consumer, reflects the concrete
annotation and closure class, and invokes the callback on the Groovy 3/4/5 classpath and in Groovy 4/5 named modules.
The callback class, annotation, and Model have the Schema's loader and module, without additional module opens. The
three `:klum-ast` test lanes passed. The public marker finalized in S3 is `@RelationshipConstraint`; the annotation member encoding is
selected. This proof is non-shipping and has no production compiler check or evaluator.

The closure class-literal encoding is selected over `BiFunction`: its generic arguments erase at the annotation
boundary, and it does not encode Klum validation semantics. `Class<?>` preserves the authored closure literal, so no
rule interface is needed. If this form fails in a later compiler case, test a purpose-specific JDK-only
`ConstraintRule<A,T>` fallback and return for a maintainer decision before adding production API.

## Affected seams and compatibility constraints

- `klum-ast-annotations`: proposed runtime-retained marker in exported schema vocabulary with the selected
  `Class<?> value()` member. Leave `@DefaultValues`
  unchanged in the core slices. Keep the annotation artifact independent of runtime and add no Groovy dependency for
  this marker. Decide package placement against the root versus `.layer3` ownership documented in ADR 0014.
- `klum-ast`: a local transform on `@RelationshipConstraint` validates each marked domain annotation declaration and normalizes
  a single Groovy-truth expression into an assertion. The existing DSL field transformation checks each field use against
  its declared relationship target. These direct AST seams support same-source declarations without scanning unrelated
  source units or changing KlumCast. Reject a non-closure class literal,
  unsupported field target types, non-DSL containing classes, and invalid authored two-parameter signatures with useful
  diagnostics. Determine the
  target parameter's assignability from the declared relationship target type, never from a resolved runtime subtype;
  reject a callback requiring a narrower subtype during Schema compilation. Inspect the authored closure's two typed
  parameters without the existing `@Validate` field closure's Builder projection. Accept a single Groovy-truth
  expression by converting it to an assertion, and retain authored assertions and their messages. After conversion,
  normal completion of either form succeeds regardless of return value; false truth expressions and failed assertions
  contribute their assertion messages. Preserve the validation reporter context so callback reporter calls can add
  issues even on normal completion.
- `klum-ast-runtime`: evaluate resolved field values in the existing `KlumFieldAnnotationsValidator` or a similarly
  narrow owner-scoped validator during `VALIDATE`. Preserve `InstanceValidator` and `KlumObjectSupport` signatures,
  phase numbers, source-model validator memoization, and companion privacy. No relationship mutation or target lifecycle
  rerun is permitted. Map collections and maps to field-member issues with entry context in their messages.
- `klum-ast-bean-validation` and Jackson: no behavioral change; ensure the optional adapter's issues and imported final
  values coexist with the new core rule.
- Generated `Foo_DSL`/AnnoDocimal mirrors: no new generated method or mirror source. Confirm retained Schema annotations
  are reflectable in separate compiled consumers. Do not add current guidance to historical `wiki/`.
- A single Java 17 production artifact must work with Groovy 3/4/5. Groovy 3 stays classpath-only for JPMS; Groovy 4/5
  named-module fixtures must prove that runtime callback invocation can access the authored closure.

## Dependency-ordered tracer slices

| Label | Contract and reasoned commit boundary | Executable acceptance |
| --- | --- | --- |
| **CONSTRAINT-S0 — callback and resolved-link proof** | Add a narrow, non-shipping probe for a runtime-retained field domain annotation carrying a typed two-parameter closure on its meta-annotation. Inspect an owner Model's completed fields at `VALIDATE`: owned child, completed external `LINK`, `LINK` to a same-root model, and `OPTIONAL_LINK` with owned, external, and null/unresolved cases. For both owned and `LINK` fields declared as abstract `Pool` but resolving to completed `KafkaPool extends Pool`, prove callback target typing against the declared `Pool`: accept `(PoolBounds, Pool)`, reject `(PoolBounds, KafkaPool)` at Schema compilation, and test an appropriate supertype if supported by the selected callback form. Test field reflection, source compilation, separately compiled consumer loading, closure invocation, and Groovy 3/4/5 plus 4/5 JPMS as appropriate. If the `Class<?>` closure-literal form fails, test a purpose-specific JDK-only `ConstraintRule<A,T>` fallback to the same standard and return for a maintainer decision. Record the selected member encoding, supported supertype behavior, and proof in ADR 0023; leave the public marker name to S1. | Three-lane compilation/execution; separate binary consumer; no module-access exception. The callback receives the completed subtype Model through a parameter assignable from the **declared** relationship target type, regardless of the observed subtype. A narrower callback fails Schema compilation for both owned and `LINK` fields, even when the test value is that subtype. Values are resolved when the source owner validates; null/unresolved optional values skip only the constraint. Target identity, ownership, lifecycle execution count, and target validation result are unchanged by checking the link. |
| **CONSTRAINT-S1 — owner-field rule across all relationship kinds** | Add the marker without a KlumCast binding, KlumAST-local placement/signature validation at the DSL field seam, and a field-sourced evaluator in the existing owner `InstanceValidator` path in one vertical commit with tests. Keep it independent of `@Validate.Ignore`. Reject scalar/non-DSL placement while accepting owned, LINK, and OPTIONAL_LINK relationships. | `@Issue("799")` `RelationshipConstraintTest`: owned, LINK, and OPTIONAL_LINK fields accept valid resolved values and reject invalid values on the **source owner's** result/path with the source field member, concrete constraint name, message, and level. Two annotated fields referencing the same target yield two distinct source-field failures; the target result/lifecycle/identity is unchanged. Verify `skipVerify`, later `verify()`, null/unresolved optional skip, separate `@Required` behavior, and no added Model property/generated signature. |
| **CONSTRAINT-S2 — collection, lifecycle and adapter depth** | Extend the same owner-field evaluator across collection/map entries and late value sources. Keep each coherent behavior with its passing tests in a reasoned commit. | Entry context appears in the message while member remains the source field: a list index, map key, or opaque token for a non-positional collection. Repeated same target at two list positions remains distinguishable in the stored result. Cover owned/LINK/OPTIONAL_LINK collections and maps, same-root link cycles, late `@PostTree` values, external Jackson import after resolution, optional Bean Validation coexistence, and no revalidation of an external target. Existing `DefaultValuesSpec` stays green without production changes. |
| **CONSTRAINT-S3 — documentation and consumer proof** | Add one readable `RelationshipConstraintDocumentaryTest` with `@Issue("799")`, `@Tag("documentary")`, and `@See` to `docs/user/Validation.md`; update that page, cross-link from `docs/user/Default-Values.md`, and update `CHANGES.md` together. Include a separate consumer reflection/JPMS fixture as needed. | Documentary example uses two domain annotations on one field and matches current docs. Reflection exposes marker and concrete bounds from a compiled Schema field; generated source-mirror task output and bytecode remain unchanged. Run `:klum-ast:test`, `:klum-ast:groovy4Tests`, `:klum-ast:groovy5Tests` and affected module tests; run `git diff --check` and documentation checks. |
| **CONSTRAINT-S4 — optional class/combined form** | Take only after a maintainer decision and evidence of a real consumer. Class-declaration rules or one combined default-plus-constraint annotation may each need a separate issue/ADR amendment. | Dedicated class inheritance/traversal/OPTIONAL_LINK checks, or a selective default-member mapping with mixed-annotation tests, respectively. Neither blocks S1–S3. |

No committed test should remain pending or ignored without an actionable reason. Use the existing `AbstractDSLSpec` dynamic
schema fixture for compiler and end-to-end behavior, and the repository's scenario harness for separate source/binary
boundaries. Name every new executable class with `Test`, not `Spec`. Place the `@Issue` number at class level when the
whole class serves #799. After each code slice, keep production and passing tests in the same commit. Before publication,
review the entire issue-branch sequence and final diff per `docs/agents/commits.md`.

## Issue placement, risk, and decision gate

The bounded **field** core is an accepted **4.1 candidate**, contingent on S0/S1 evidence rather than another product
decision. #799 can own S0–S3. S4 is optional and may be a separate successor only if the Hive and maintainer
choose it. No successor issue is created by this plan. Each partial pull request must be related to #799 rather than
automatically close it.

S0 retired the primary closure-encoding risk across Groovy 3/4/5 and the applicable named-module lanes. The older
Groovy 3 `@Validate` field closure demonstrated the Builder-type mismatch avoided by S1's production marker and
declared-target check. S1 and S2 now cover compiler diagnostics, both supported callback forms, reporter compatibility,
runtime issue attribution, collection/map entries, and adapter coexistence. The source-field implementation pays one annotation/field scan per owner and one
callback per resolved annotated relationship entry, including repeated references; its cost is proportional to
annotated source entries and must be measured if a Schema uses large annotated collections. No target traversal,
ownership transfer, or lifecycle replay is part of the proposed mechanism. Accidental defaulting from mixed annotation
attributes remains a **follow-up risk only**; S1–S3 use separate annotations, so `@DefaultValues` needs no change.
Runtime reflection is a deliberately minimal metadata surface; a tooling catalog is deferred until there is a consumer.
The historical source-mirror path is an IDE projection of generated contracts, not a second Schema annotation authority.

The S0 gate passed with the JDK-only `Class<?>` annotation member. Source-owner path/field
attribution is accepted. S3 finalized the public marker name as `@RelationshipConstraint` without changing its
`Class<?> value()` encoding or S1/S2 behavior. KlumCast 0.4.0 recursively applies a
meta-annotation binding to domain-annotation uses and requires those annotation types to be compiled already. The
KlumAST-local field check preserves same-source declarations. A future generic same-source KlumCast or KlumGuard
dispatch capability can replace it only if released before #799's release qualification.

## S3 delivery and consumer gate

S3 finalized `@RelationshipConstraint` as the sole public name for the relationship-local field contract.
`RelationshipConstraintDocumentaryTest#'checks a completed child against domain-defined bounds after applying defaults'`
executes the example in [Validation](../user/Validation.md#domain-defined-relationship-constraints), with two separate
domain annotations on one relationship field. `JpmsPackageBoundaryTest#'a real schema and consumer prove the classpath
and named-module contracts'` builds a Schema artifact and separately compiles a Java consumer against it; that consumer
reflects the runtime marker and concrete bound and executes both a passing and a failing model. It passes on the
classpath in Groovy 3/4/5 and in Groovy 4/5 named modules. The named-module proof exposed a missing qualified opening
for `RelationshipConstraintDeclarationTransformation`; the maintainer approved adding `org.apache.groovy` to the existing
`compiler.internal.validation` opening while retaining KlumCast. This packaging correction changes no callback semantics,
generated signature, or source-mirror input. S4 is left for an explicit maintainer decision based on a concrete consumer; class-declaration
rules or combined default/constraint member mapping are neither implemented nor required to finish S3.
