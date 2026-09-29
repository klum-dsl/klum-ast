# ADR 0023 implementation plan: schema-owned constraint annotations

This is the proposed design and tracer plan for [#799](https://github.com/klum-dsl/klum-ast/issues/799), governed by
[ADR 0023](../adr/0023-schema-owned-constraint-annotations.md). No production behavior or tracker state changes here.
The ADR is not accepted; S0 and the remaining decisions at its end gate implementation. The maintainer has clarified
that a domain constraint annotation on a Schema field is required and combination with defaults is optional.

## Confirmed behavior and failure paths

| Evidence | Current behavior / gap |
| --- | --- |
| `DefaultPhase`, `AnnotationHelper.getMetaAnnotated`, `DefaultValuesCheck`, `DefaultValuesSpec` | Runtime-visible, directly meta-annotated class/field annotations supply nondefault members to child/model Builder defaults. `valueTarget` only remaps `value`. Two separate domain annotations work without changing this path; a future combined annotation needs explicit mapping because extra bound attributes would be treated as defaults. |
| `ValidationPhase`, `SingleObjectValidationHandler`, `KlumFieldAnnotationsValidator`, `KlumMethodAnnotationsValidator` | `VALIDATE` traverses completed owned DSL Objects, executes registered validators once per object/class, and records normal issues. `@Validate` field closures see a field value, but do not see a separate domain annotation instance. The public `InstanceValidator` lacks owner/field context. |
| `CompositionTraversal`, `ModelVisitingPhaseAction` | Traversal supplies the owning Model and source field for visited children, including collection/map members, while skipping Owner/LINK fields and repeated object identities. `OPTIONAL_LINK` is not excluded by the simple `isLink` field check; its existing validation behavior needs an explicit acceptance probe. This is the field-annotation seam only after checking the source field's eligibility. |
| `KlumValidationIssue`, `KlumObjectSupport.Validation`, `VERIFY` | Issues carry object path, member, message, and level. Stored validation can be inspected or verified without rerunning rules. |
| `docs/user/Validation.md`, `docs/user/Default-Values.md` | Current guidance covers direct `@Validate`, `@DefaultValues`, and optional Jakarta Bean Validation, but no combined domain-specific annotation. |

The metadata lives on Schema declarations. It must never be implemented as a new field of a Domain API type merely to make
the rule or documentation tooling accessible.

## Affected seams and compatibility constraints

- `klum-ast-annotations`: proposed runtime-retained marker in exported schema vocabulary. Leave `@DefaultValues`
  unchanged in the core slices. Keep the annotation artifact independent of runtime and do not add a transitive Groovy
  dependency. Decide package placement against the root versus `.layer3` ownership documented in ADR 0014.
- `klum-ast`: compile-time placement and signature checks through existing KlumCast validator patterns. Reject unsupported
  field target types, non-DSL containing classes, and invalid closure signatures with useful diagnostics.
- `klum-ast-runtime`: use the current completed-Model validation action and traversal context. Preserve
  `InstanceValidator` and `KlumObjectSupport` signatures, phase numbers, validator memoization, and companion privacy.
- `klum-ast-bean-validation` and Jackson: no behavioral change; ensure the optional adapter's issues and imported final
  values coexist with the new core rule.
- Generated `Foo_DSL`/AnnoDocimal mirrors: no new generated method or mirror source. Confirm retained Schema annotations
  are reflectable in separate compiled consumers. Do not add current guidance to historical `wiki/`.
- A single Java 17 production artifact must work with Groovy 3/4/5. Groovy 3 stays classpath-only for JPMS; Groovy 4/5
  named-module fixtures must prove that runtime callback invocation can access the authored closure.

## Dependency-ordered tracer slices

| Label | Contract and reasoned commit boundary | Executable acceptance |
| --- | --- | --- |
| **CONSTRAINT-S0 — compatibility proof** | Add a narrow, non-shipping test scenario/probe for a runtime-retained field domain annotation carrying a typed two-parameter closure on its meta-annotation. Test field reflection, separately compiled consumer loading, closure invocation, and Groovy 3/4/5 plus 4/5 JPMS. Record the result before freezing the API. Keep the proof in one commit; if it fails, return to the maintainer with the typed-rule-class fallback and no public marker. | Three-lane compilation and execution; separate binary consumer; no module-access exception. A rejected parameter type or invalid placement must be diagnosable by the later compiler slice, not assumed proven by this probe. |
| **CONSTRAINT-S1 — one owned field rule** | Add the marker, KlumCast placement/signature validation, and a field-sourced evaluator in the existing `VALIDATE` path in one vertical commit with tests. Use traversal owner/field context without changing `InstanceValidator`. Reject scalar/LINK/OPTIONAL_LINK/non-DSL placement. | `@Issue("799")` `ConstraintValuesTest`: a single child accepts a default and an explicit valid override, and rejects an invalid override with the child's path, concrete constraint name, message and level. Verify `skipVerify` stored results, later `verify()`, no added Model property/generated signature, null child, and no field-sourced rule for LINK or OPTIONAL_LINK. |
| **CONSTRAINT-S2 — collection, lifecycle and adapter depth** | Extend that field evaluator across collection/map children and late value sources while retaining the same rule contract. Keep each coherent behavior with its passing tests in a reasoned commit. | Element-specific paths, multiple independent field annotations, identity-cycle traversal, late `@PostTree` values, external Jackson import after resolution, and coexistence with optional Bean Validation. Existing `DefaultValuesSpec` stays green without production changes. |
| **CONSTRAINT-S3 — documentation and consumer proof** | Add one readable `ConstraintValuesDocumentaryTest` with `@Issue("799")`, `@Tag("documentary")`, and `@See` to `docs/user/Validation.md`; update that page, cross-link from `docs/user/Default-Values.md`, and update `CHANGES.md` together. Include a separate consumer reflection/JPMS fixture as needed. | Documentary example uses two domain annotations on one field and matches current docs. Reflection exposes marker and concrete bounds from a compiled Schema field; generated source-mirror task output and bytecode remain unchanged. Run `:klum-ast:test`, `:klum-ast:groovy4Tests`, `:klum-ast:groovy5Tests` and affected module tests; run `git diff --check` and documentation checks. |
| **CONSTRAINT-S4 — optional class/combined form** | Take only after a maintainer decision and evidence of a real consumer. Class-declaration rules or one combined default-plus-constraint annotation may each need a separate issue/ADR amendment. | Dedicated class inheritance/traversal/OPTIONAL_LINK checks, or a selective default-member mapping with mixed-annotation tests, respectively. Neither blocks S1–S3. |

No committed test should remain pending or ignored without an actionable reason. Use the existing `AbstractDSLSpec` dynamic
schema fixture for compiler and end-to-end behavior, and the repository's scenario harness for separate source/binary
boundaries. Name every new executable class with `Test`, not `Spec`. Place the `@Issue` number at class level when the
whole class serves #799. After each code slice, keep production and passing tests in the same commit. Before publication,
review the entire issue-branch sequence and final diff per `docs/agents/commits.md`.

## Issue placement, risk, and decision gate

The bounded **field** core is viable as a **4.1 candidate**, contingent on S0 and maintainer acceptance of the ADR's
remaining decisions. #799 can own S0–S3. S4 is optional and may be a separate successor only if the Hive and maintainer
choose it. No successor issue is created by this plan. Each partial pull request must be related to #799 rather than
automatically close it.

The primary risk is Groovy annotation closure encoding across 3/4/5 and JPMS. Accidental defaulting from mixed annotation
attributes remains a **follow-up risk only**; S1–S3 use separate annotations, so `@DefaultValues` needs no change. Field
metadata reaches owned children through the validation traversal, not through `InstanceValidator`, and requires careful
path assertions.
Runtime reflection is a deliberately minimal metadata surface; a tooling catalog is deferred until there is a consumer.
The historical source-mirror path is an IDE projection of generated contracts, not a second Schema annotation authority.

Implementation stops if S0 fails or if the maintainer declines the bounded field semantics; in either case update this
proposed ADR before adding production APIs. Do not treat the example API names as approved until that decision.
