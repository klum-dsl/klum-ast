# ADR 0023 implementation plan: schema-owned constraint annotations

This is the proposed design and tracer plan for [#799](https://github.com/klum-dsl/klum-ast/issues/799), governed by
[ADR 0023](../adr/0023-schema-owned-constraint-annotations.md). No production behavior or tracker state changes here.
The ADR is not accepted; S0/S1 and the remaining decisions at its end gate implementation. The maintainer has clarified
that a domain constraint annotation on a Schema field is required, `LINK`/`OPTIONAL_LINK` are part of that primary use
case, and combination with defaults is optional.

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
both completed value availability and a real typed-closure compatibility risk. It does **not** prove that the proposed
new meta-annotation callback compiles or runs, nor does it prove Groovy 4/5 or JPMS. S0 must test those before API freeze.

## Affected seams and compatibility constraints

- `klum-ast-annotations`: proposed runtime-retained marker in exported schema vocabulary. Leave `@DefaultValues`
  unchanged in the core slices. Keep the annotation artifact independent of runtime and do not add a transitive Groovy
  dependency. Decide package placement against the root versus `.layer3` ownership documented in ADR 0014.
- `klum-ast`: compile-time placement and signature checks through existing KlumCast validator patterns. Reject unsupported
  field target types, non-DSL containing classes, and invalid closure signatures with useful diagnostics.
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
| **CONSTRAINT-S0 — closure and resolved-link proof** | Add a narrow, non-shipping probe for a runtime-retained field domain annotation carrying a typed two-parameter closure on its meta-annotation. In the same proof, inspect an owner Model's completed fields at `VALIDATE`: owned child, completed external `LINK`, `LINK` to a same-root model, and `OPTIONAL_LINK` with owned, external, and null/unresolved cases. Test field reflection, separately compiled consumer loading, closure invocation, and Groovy 3/4/5 plus 4/5 JPMS. Record the result before freezing the API; if a link value is unavailable, report the smallest alternate owner-field seam and cost rather than excluding links. | Three-lane compilation/execution; separate binary consumer; no module-access exception. Values are resolved when the source owner validates; null/unresolved optional values skip only the constraint. The proof records that target identity, ownership, lifecycle execution count, and target validation result are unchanged by reading and checking the link. |
| **CONSTRAINT-S1 — owner-field rule across all relationship kinds** | Add the marker, KlumCast placement/signature validation, and a field-sourced evaluator in the existing owner `InstanceValidator` path in one vertical commit with tests. Keep it independent of `@Validate.Ignore`. Reject scalar/non-DSL placement while accepting owned, LINK, and OPTIONAL_LINK relationships. | `@Issue("799")` `ConstraintValuesTest`: owned, LINK, and OPTIONAL_LINK fields accept valid resolved values and reject invalid values on the **source owner's** result/path with the source field member, concrete constraint name, message, and level. Two annotated fields referencing the same target yield two distinct source-field failures; the target result/lifecycle/identity is unchanged. Verify `skipVerify`, later `verify()`, null/unresolved optional skip, separate `@Required` behavior, and no added Model property/generated signature. |
| **CONSTRAINT-S2 — collection, lifecycle and adapter depth** | Extend the same owner-field evaluator across collection/map entries and late value sources. Keep each coherent behavior with its passing tests in a reasoned commit. | Per-entry index/key appears in the message while member remains the source field; repeated same target at two list positions remains distinguishable in the stored result. Cover owned/LINK/OPTIONAL_LINK collections and maps, same-root link cycles, late `@PostTree` values, external Jackson import after resolution, optional Bean Validation coexistence, and no revalidation of an external target. Existing `DefaultValuesSpec` stays green without production changes. |
| **CONSTRAINT-S3 — documentation and consumer proof** | Add one readable `ConstraintValuesDocumentaryTest` with `@Issue("799")`, `@Tag("documentary")`, and `@See` to `docs/user/Validation.md`; update that page, cross-link from `docs/user/Default-Values.md`, and update `CHANGES.md` together. Include a separate consumer reflection/JPMS fixture as needed. | Documentary example uses two domain annotations on one field and matches current docs. Reflection exposes marker and concrete bounds from a compiled Schema field; generated source-mirror task output and bytecode remain unchanged. Run `:klum-ast:test`, `:klum-ast:groovy4Tests`, `:klum-ast:groovy5Tests` and affected module tests; run `git diff --check` and documentation checks. |
| **CONSTRAINT-S4 — optional class/combined form** | Take only after a maintainer decision and evidence of a real consumer. Class-declaration rules or one combined default-plus-constraint annotation may each need a separate issue/ADR amendment. | Dedicated class inheritance/traversal/OPTIONAL_LINK checks, or a selective default-member mapping with mixed-annotation tests, respectively. Neither blocks S1–S3. |

No committed test should remain pending or ignored without an actionable reason. Use the existing `AbstractDSLSpec` dynamic
schema fixture for compiler and end-to-end behavior, and the repository's scenario harness for separate source/binary
boundaries. Name every new executable class with `Test`, not `Spec`. Place the `@Issue` number at class level when the
whole class serves #799. After each code slice, keep production and passing tests in the same commit. Before publication,
review the entire issue-branch sequence and final diff per `docs/agents/commits.md`.

## Issue placement, risk, and decision gate

The bounded **field** core is viable as a **4.1 candidate**, contingent on S0/S1 and maintainer acceptance of the ADR's
remaining decisions. #799 can own S0–S3. S4 is optional and may be a separate successor only if the Hive and maintainer
choose it. No successor issue is created by this plan. Each partial pull request must be related to #799 rather than
automatically close it.

The primary risk is Groovy annotation closure encoding and completed-Model parameter typing across 3/4/5 and JPMS;
the temporary Groovy 3 probe demonstrates a Builder-typed `@Validate` closure mismatch that the new callback must not
inherit. Resolved external LINK/OPTIONAL_LINK values were readable in that probe, but same-root, collection, Groovy 4/5,
and named-module behavior still need S0/S1 proof. The source-field implementation pays one annotation/field scan per owner and
one callback per resolved annotated relationship entry, including repeated references; its cost is proportional to
annotated source entries and must be measured if a Schema uses large annotated collections. No target traversal,
ownership transfer, or lifecycle replay is part of the proposed mechanism. Accidental defaulting from mixed annotation
attributes remains a **follow-up risk only**; S1–S3 use separate annotations, so `@DefaultValues` needs no change.
Runtime reflection is a deliberately minimal metadata surface; a tooling catalog is deferred until there is a consumer.
The historical source-mirror path is an IDE projection of generated contracts, not a second Schema annotation authority.

Implementation stops if S0 cannot prove resolved link values or cross-Groovy callback viability without a broader
runtime change. Report the smallest alternate seam/cost and update this proposed ADR before adding production APIs. Do
not treat the example API names or issue-attribution format as approved until that decision.
