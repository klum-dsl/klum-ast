# ADR 0021 implementation plan: Builder-owned lifecycle classes

This plan implements accepted [ADR 0021](../adr/0021-builder-owned-lifecycle-classes.md)
for [#420](https://github.com/klum-dsl/klum-ast/issues/420). It authorizes no
blanket Model-to-Builder delegation, post-materialization mutation, or generic
phase-plugin facility.

## Confirmed starting behavior and failure paths

| Concern | Current seam | Consequence |
| --- | --- | --- |
| Builder callbacks | `WriteAccessMethodsMover` moves annotated lifecycle methods from a DSL Object to its generated Builder; `LifecycleHelper` invokes them through the Builder phases. | A lifecycle class must be rehomed as a Builder class before variable scope/type checking, not invoked against its authored Model outer. |
| Validation classes | `KlumInnerClassValidator` instantiates a public non-static `@Validate` class with the completed Model after phase 40. | It remains Model-only precedent and cannot be reused as the Builder implementation. |
| State transition | `INSTANTIATE` at 40 materializes Models, transfers provisional issues, and discards construction-only state. | Class callbacks through `POST_TREE` receive Builders; `@Validate` and `InstanceValidator` receive Models. |
| Builder capabilities | `@Builder.Query` and `@Builder.Method` have explicit Builder projections; unannotated Model methods do not. | A relocated class can use the former normally and must reject the latter with a source-local diagnostic. |
| Inner-class AST | `InnerClassNode` has no outer-class setter. The throwaway probe created a new Builder-owned node and transferred method nodes before scope/type checking. | The relocator must create a new node, preserve source positions/annotations, and remove the source class from the Model surface. |
| Test seams | `LifecycleSpec` dynamically compiles DSL sources; `BuilderMethodTest`, `BuilderQueryTest`, and generated-support tests cover Builder behavior; `ModelPhasesDocumentaryTest` documents lifecycle order. | Start with `LifecycleSpec` for end-to-end runtime behavior, then add static Java/Groovy and all-lane coverage where generated/compiler behavior becomes visible. |

## Affected modules and boundaries

- `klum-ast-annotations`: lifecycle annotation type placement and compiler-facing validation.
- `klum-ast`: class relocation, generated Builder runner, source diagnostics, generated/mirror exclusion, and compiler tests.
- `klum-ast-runtime`: only a narrow helper addition if the generated runner cannot use the established Builder lifecycle invocation path; it must not expose a new public extension seam.
- `docs/user`: delivered syntax, callback ordering, Builder-only legality, migration, and documentary test linkage.
- `CHANGES.md`: one 4.1 entry only when a user-visible slice ships.

## Thin implementation slices

### LC-1 — Real-transform `@PostTree` tracer

Extend the placement contract so `@PostTree` can mark a direct non-static inner
class of a DSL Object. Add a compiler-internal relocator after Builder creation
and before final variable-scope/type checking. It creates a non-static hidden
Builder inner class, transfers the source class's members, removes the authored
class from the completed Model, and emits one hidden `@PostTree` Builder runner.
The runner constructs one rules instance and invokes its public parameterless
methods in source order.

Acceptance: a dynamically compiled schema with two `@PostTree` rule methods
mutates scalar and owned-child Builder state, materializes the expected Model,
and proves one rules instance spans both callbacks. The first callback updates a
private instance field initialized from Builder state; the second observes it,
proving field, initializer, annotation, and helper-state transfer rather than
only method relocation. A private helper is not a callback. Reflection confirms
the completed Model has no authored rules class. Public, protected, package, and
private source class visibilities compile because the generated runner, not
validation-class reflection, owns construction. The tracer rejects static,
top-level, parameterized-callback, and explicit-constructor shapes with targeted
diagnostics. Each new test carries `@Issue("420")`.

Commit boundary: `Relocate PostTree lifecycle classes into Builders` with the
annotation, relocator/runner, and focused runtime/compiler tests together.

### LC-2 — Enforce the Builder-only source context

Make the relocator preserve Builder field types, projected `@Builder.Query`
calls, `@Builder.Method` calls, and deprecated `@Mutator` calls under dynamic
and `@CompileStatic` schema sources. Add a focused verifier that diagnoses an
unannotated Model-only method, explicit `Foo.this`, raw/unsupported reference
shapes, and an attempt to use a completed Model as an owned relationship value.
Do not infer a projection.

Acceptance: static Groovy proves normal field access, a query, a
`@Builder.Method`, and legacy `@Mutator` all execute on current Builder state;
dynamic and static schemas both retain the existing `@Mutator` source
compatibility. Every prohibited shape fails at schema compilation with the
lifecycle class/method and its missing Builder counterpart named.
Java/generated-public inventory tests prove no new `Foo_DSL` or mirror surface
is emitted.

Commit boundary: `Validate Builder-only lifecycle class references` with the
positive and negative compiler fixtures.

### LC-3 — Preserve annotation-specific scope

Do not generalize the `@PostTree` class contract. `@Default`, `@AutoCreate`,
and `@AutoLink` are semantically special cases, and `@PostCreate` is a
creation-time callback rather than a lifecycle phase. `@PostApply` and every
other annotation are out of #420's implementation scope as well. A later
proposal for any annotation needs its own decision/tracer before its target or
transform behavior changes; shared Builder state alone is not sufficient
justification.

Acceptance: #420 delivers only `@PostTree` lifecycle classes and keeps every
other annotation's current placement rules unchanged. The ADR and user guidance
make that boundary explicit; no feature test presents another annotation as a
planned extension of the `@PostTree` implementation.

Commit boundary: no production generalization is part of #420. A future
annotation-specific decision starts with evidence for that annotation's
receiver, ordering, ownership, template, and scheduling behavior.

### LC-4 — Lifecycle-class inheritance feasibility tracer

Prove or reject a base/derived schema case whose source lifecycle class extends
the parent lifecycle class. The tracer must rebind the source superclass to its
Builder counterpart, preserve both Builder hierarchy and callback order, and
prove that a child override runs once at the documented position. It must also
prove generated/model visibility and diagnostics for an unsupported partial
hierarchy.

Acceptance: only ship inheritance if the complete base/child proof passes under
Groovy 3, 4, and 5 without leaking source lifecycle classes into Models. If the
proof exposes an incompatible Groovy AST or generated-hierarchy constraint,
retain the LC-1 constructor/inheritance rejection with an actionable message;
record the evidence in #420 rather than silently broadening the contract.

Commit boundary: either `Support inherited Builder lifecycle classes` with its
complete matrix, or `Record lifecycle class inheritance limitation` with the
rejection diagnostic and evidence.

### LC-5 — Deliver public guidance and release evidence

Add a `@Tag("documentary")` lifecycle-class example to the existing lifecycle
documentation/test seam, document source declaration ordering and Builder-only
legality in `Model-Phases.md`, update Builder-first migration guidance, and add
the 4.1 `CHANGES.md` entry. Link the issue, documentation heading, and
documentary test with `@Issue("420")` and `@See`.

Acceptance: the example creates a completed model after multiple grouped
`@PostTree` callbacks; it visibly distinguishes the Builder class from a
completed-model `@Validate` class. Documentation contains no RW terminology and
does not claim custom phase, inheritance, serialization, or Model mutation
support beyond delivered slices.

Commit boundary: `Document Builder-owned lifecycle classes` with the
documentary test, user documentation, migration guidance, and release note.

## Compatibility and verification

- `LC-1` starts with the narrow Groovy 3 `LifecycleSpec` selection. Every
  compiler/AST or generated-class slice ends with the affected module's Groovy
  3 suite plus `groovy4Tests` and `groovy5Tests`.
- Run Java and `@CompileStatic` Groovy consumers wherever relocation could leak
  a public/generated type. Verify AnnoDocimal source-mirror absence explicitly.
- Exercise cyclic `LINK` and ownership only through existing Builder-state
  fixtures; no Model becomes owned after materialization.
- Documentation-only LC-5 uses `git diff --check` plus applicable document/link
  checks. Earlier slices are code changes and require their test lanes.

## Risks and open questions

| Risk or question | Containment |
| --- | --- |
| Moving source members changes source positions or annotation metadata. | LC-1 verifies source-linked diagnostics and keeps relocation internal; preserve positions while transferring nodes. |
| Reflection callback order is unstable. | Do not discover individual methods reflectively; generate one ordered Builder runner from class declaration order. |
| Dynamic Groovy resolves a Model-only method late. | LC-2 adds an explicit compiler verifier; no dynamic fallback or runtime Model delegation. |
| Source lifecycle classes leak as public nested Model types. | LC-1 verifies removal from the Model; LC-2 verifies no generated interface/mirror addition. |
| Inheritance needs a Builder-superclass name that source cannot name. | Keep it rejected until LC-4 proves a complete projected hierarchy across all lanes. |
| Templates or delayed actions retain a Builder callback object. | Classes are fresh, `@PostTree`-local construction machinery; no later annotation may inherit this guarantee without its own tracer. |

## Issue-to-slice map

| Item | Relationship |
| --- | --- |
| #420 | Governing issue; LC-1, LC-2, LC-4, and LC-5 are implementation slices, while LC-3 preserves the annotation-specific scope. |
| #415 | Delivered completed-Model validation classes; regression/contrast only. |
| #416 / ADR 0003 | Builder-first state and materialization authority consumed by every slice. |
| ADR 0020 / #689 | Explicit Builder query/method boundary enforced by LC-2; no blanket projection. |
| #305 / ADR 0008 | Custom phase registration remains excluded. |
| #783 | Interface-level Builder grouping remains unrelated and excluded. |
