# ADR 0020 implementation plan: explicit shared Model and Builder capabilities

This plan implements proposed
[ADR 0020](../adr/0020-explicit-shared-model-builder-capabilities.md) for
[#689](https://github.com/klum-dsl/klum-ast/issues/689). It orders the narrower
[#651](https://github.com/klum-dsl/klum-ast/issues/651),
[#648](https://github.com/klum-dsl/klum-ast/issues/648), and
[#650](https://github.com/klum-dsl/klum-ast/issues/650) contracts as independently testable tracer bullets. No slice may
restore blanket delegation, expose hidden Builder implementations, or weaken Construction-session and Materialization
boundaries.

## Confirmed current behavior and failure paths

- `WriteAccessMethodsMover` moves `@Mutator` and lifecycle methods from the Model to the hidden Builder and retargets only
  established Owner/virtual-field positions. It does not provide a general explicit input/result projection contract.
- `GeneratedDslSupport` publishes public non-synthetic hidden-Builder methods through `Foo_DSL.Builder` and projects hidden
  implementation types to public interfaces. AnnoDocimal mirrors that emitted public namespace.
- `BuilderMethodProjection` adapts source-visible model-producing factory/converter bodies to active-session Builder twins,
  including concrete `KlumBuilder<M>` and supported Collection/Map result shapes. Opaque/precompiled producers and raw,
  wildcard, or unresolved Builder results fail rather than being guessed.
- Ordinary Model query methods are not available on Builders. Calling `registry.toUrl()` in Builder lifecycle code fails
  static method resolution even when the method reads only scalar Builder-visible fields.
- `ModelVerificationVisitor` rejects `builderValue instanceof SomeModel` in Builder-phase code because the value is not a
  completed Model. No public operation combines Model/Builder type identity with an exact generated Builder cast.
- `KlumBuilder<T>` deliberately declares no operations. A `KlumBuilder<Registry>` result is therefore not assignable to a
  generated relationship method requiring `Registry_DSL.Builder<Registry>`, even when the runtime object is that Builder.
- `@Mutator` methods can return values, but a Model-declared DSL Object result is not an explicit promise of an owned
  Builder result and must not be reinterpreted automatically.
- The curation architecture map is explicitly a 2026-07-20 snapshot and its #208 row predates commit `5b382b44`, which
  removed the unused renamed `DelegateFromBuilderToModel` helper. Current source has no blanket Model-method delegation
  path; #208/#503 remain historical design evidence, not an implementation seam to reconnect.

The primary failure is nominal rather than runtime: the framework can hold and materialize the correct same-session
Builders, but source cannot express selected truthful Builder-side domain signatures without duplicating code or using a
dynamic bridge. The design must close only annotated positions.

## Affected modules and seams

| Module/seam | Planned responsibility |
| --- | --- |
| `klum-ast-annotations` | Public `@BuilderQuery`, `@BuilderInput`, and `@BuilderResult` schema vocabulary and KlumCast validation bindings. |
| `klum-ast` mutator/type-checking pipeline | Validate query purity and annotation combinations; clone/retarget selected method bodies; bind Builder-phase calls to exact twins; diagnose completed-Model tests and invalid projections. |
| `BuilderMethodProjection` | Reuse one recursive type projector and linked-twin mechanism for annotated inputs/results without broadening unannotated ADR 0004 inference. |
| `GeneratedDslSupport` | Publish exact non-static projected methods on `Foo_DSL.Builder`; preserve inheritance, overloads, documentation, and IDE-mirror parity. |
| `klum-ast-runtime` | Implement factory-token identity predicates and exact Builder cast without adding methods to `KlumBuilder<T>`. |
| Tests and scenarios | Groovy 3/4/5 compiler behavior, Java/static-Groovy generated API, source/precompiled boundaries, ownership failures, and documentary examples. |
| `docs/user/Advanced-Techniques.md` | Canonical 4.1 authoring examples for shared queries, narrowing, and explicitly projected helper flow. |
| `docs/user/Builder-First-Migration.md` and `CHANGES.md` | Migration from duplicated/dynamic bridges and release-facing diagnostics after each capability ships. |

ADR 0015's generated-runtime linkage inventory must remain green. New generated method bodies may call existing generated
bridges, but must not add schema-bytecode references to `runtime.internal` packages.

## Compatibility and migration constraints

- Root factories still return completed Models; Builder-producing paths still require an active Construction session.
- `@BuilderResult` means owned, unsealed, same-session Builder. It never adopts a completed Model or converts a `LINK` into
  composition.
- Unmarked Model parameters/results retain their exact completed-state meaning. Existing schemas change neither generated
  surface nor runtime behavior until they opt in.
- Bulk state-interface projection is not part of the initial public contract. It remains a mandatory post-slice decision,
  not an implementation prerequisite.
- `KlumBuilder<T>` remains zero-operation, and generated implementation classes remain unsupported.
- The public surface is the emitted `Foo_DSL` contract; IDE mirrors must be derived from it and remain excluded from
  compilation, packaging, and downstream inputs.
- A precompiled type produced with this contract is consumed through its emitted generated API. Older/opaque bytecode is
  never analyzed to synthesize a new twin.
- Model serialization, Template recipe serialization, Jackson input/output, validation, and Materialization are unchanged.
- Every new test carries its driving issue number. Every user-visible capability has a `@Tag("documentary")` example with
  an `@See` link to `docs/user/Advanced-Techniques.md`.

## Tracer bullets and reasoned commits

### SMB-1 — Project pure scalar queries (#651)

**Work:** add `@BuilderQuery`; validate public non-void instance methods; clone the method onto the hidden Builder while
retaining the original Model method; retarget reads to Builder fields and recursively bind other projected queries. Reject
direct field writes, known mutator/lifecycle/construction calls, `FieldType.BUILDER` reads, DSL Object/Builder-bearing
results, and projected signature collisions. Publish the method through `Foo_DSL.Builder` with Builder-state documentation.

**Acceptance:**

- A completed Model and its Builder return the same scalar query value from their respective current state.
- A lifecycle callback can call the query through an owned relationship under `@CompileStatic`.
- Unannotated Model methods remain absent from the Builder implementation, `Foo_DSL.Builder`, and IDE mirror.
- Mutation, construction-only state, void/DSL-bearing results, ambiguous overloads, and an opaque precompiled call each
  produce a targeted compilation diagnostic.
- Inherited annotated queries appear once with the correct self-model Builder hierarchy.
- Java and static-Groovy fixtures see the exact public method; Groovy 3, 4, and 5 focused lanes pass.

**Commit boundaries:** (1) annotation, validation, cloning, and focused behavior tests; (2) generated public/mirror surface,
Java/static-Groovy fixtures, documentary test, user guide, migration note, and `CHANGES.md`. Keep implementation and its
driving tests in the same commit.

### SMB-2 — Add explicit Model-or-Builder identity and exact narrowing (#648)

**Work:** extend `BuilderFactoryProvider<T, B>` and generated Factory implementations with `isModelOrBuilder`, `isBuilder`,
and `asBuilder`. Compare completed values with `getModelType().isInstance`; compare Builder values through the existing
internal declared-Model-type hook behind an approved runtime/generated bridge. `asBuilder` returns the same identity as `B`
and performs no lifecycle transition.

**Acceptance:**

- Base/subtype completed Models and Builders follow ordinary assignability rules.
- `isModelOrBuilder` accepts both states; `isBuilder` accepts only Builders; null, foreign values, and mismatched model
  hierarchies return false.
- `asBuilder` gives Java and `@CompileStatic` Groovy the exact subtype Builder contract and rejects a completed Model,
  mismatch, null, or foreign value with a stable `KlumModelException` diagnostic.
- A sealed Builder wrapper can be narrowed for read-only projected queries; mutation still fails through its existing
  guard. A captured inactive Builder receives the same identity result and retains existing lifecycle failures.
- The API exposes neither `InternalKlumBuilder` nor raw session/model metadata, and the generated-linkage inventory stays
  clean on Groovy 3, 4, and 5.

**Commit boundaries:** one runtime/generated-factory behavior commit with Java/Groovy tests, followed by one documentary
and migration commit if separating prose keeps both commits independently reviewable.

### SMB-3 — Project explicitly marked Builder inputs (#650)

**Work:** add parameter-level `@BuilderInput`; select the declaring source method for Builder-side projection; recursively
map supported concrete/abstract DSL Object and Collection/Map positions to exact public Builder types. Clone ordinary
instance method bodies for the Builder and extend the existing linked-twin path for source-visible static helpers and
converters. Leave unmarked Model parameters unchanged.

**Acceptance:**

- A root Builder helper receives an exact generated Builder parameter and can navigate it under static checking.
- A same-source converter/helper call binds directly to its linked twin; a precompiled implementing Schema works through
  its emitted API.
- Older or opaque precompiled helpers fail with a diagnostic that requests recompilation or an explicit generated
  relationship/Builder method.
- Raw, wildcard, unresolved generic, nested unsupported container, wrong-model, and overload-collapse cases name the
  offending parameter and do not emit a guessed signature.
- Unmarked parameters continue to accept completed Models for `LINK` and other completed-state operations.
- Bytecode, Java/static-Groovy consumers, IDE mirrors, and all three Groovy lanes agree.

**Commit boundaries:** (1) annotation/type projector and negative compiler matrix; (2) instance/static linked-twin flow and
exact generated API tests; (3) documentary/migration/release closure when the two implementation steps are green.

### SMB-4 — Project explicit owned Builder results (#650/#689)

**Work:** add method-level `@BuilderResult`; project supported DSL Object or Collection/Map result positions to exact public
Builder types. For ordinary source methods, retain the completed-Model method and build a linked Builder twin using ADR
0004's active-session call rewriting. For `@Mutator`, retarget the moved method directly. Require every successful runtime
result to be an unsealed Builder in the current session and let the existing relationship attachment/claim path establish
ownership.

**Acceptance:**

- A helper with `@BuilderInput` and `@BuilderResult` flows an exact Builder through root navigation and into an exact owned
  relationship method under static checking.
- A selected `@Mutator @BuilderResult` method can attach and return an owned child Builder without exposing the result on
  the completed Model.
- The ordinary Model form still consumes/returns completed Models and owns its root lifecycle.
- Returning a completed Model, Template, sealed Builder, cross-session Builder, unattached Builder at session completion,
  or wrong Builder type retains or gains a targeted ownership/session diagnostic; no result is silently rehydrated.
- An unmarked DSL Object result remains a Model result and cannot be passed as owned composition.
- Supported container kind, order, comparator, duplicate behavior, and map keys match ADR 0004; generic/opaque failures
  are deterministic across Groovy 3, 4, and 5.

**Commit boundaries:** (1) ordinary/helper result projection with ownership tests; (2) `@Mutator` integration and focused
return diagnostics; (3) Java/static-Groovy/mirror/documentary/migration/release closure. If implementation evidence shows
mutator results require a materially different lifecycle contract, stop after the first commit and create a dedicated
successor issue rather than weakening `@BuilderResult`.

### SMB-D1 — Revisit bulk state-interface projection (#689)

**Timing:** after SMB-1 through SMB-4 have executable evidence and before final contract reconciliation.

**Decision:** evaluate whether repeated per-method annotations, generic state consumers, or the implemented type projector
justify an interface-level bulk projection. Record exactly one outcome in ADR 0020:

- implement it in the current lane, with a separately reviewed public name, generated-contract shape, inheritance rules,
  precompiled behavior, and acceptance matrix;
- waive it because the three explicit annotations cover the demonstrated use cases; or
- create a later related issue when the need is credible but not required for this lane.

This checkpoint must not hold the four foundational slices open merely to preserve a hypothetical public seam. A generated
paired Model/Builder state contract is an option to evaluate, not a pre-approved implementation.

### SMB-5 — Reconcile the complete public contract (#689)

**Work:** run the combined query/narrowing/input/result matrix across direct-schema and inherited schemas; inspect emitted
descriptors and AnnoDocimal mirrors; reconcile public API inventory, architecture map, user documentation, migration
guidance, and release notes. Remove no historical workaround guidance until its replacement is executable.

**Acceptance:**

- `./gradlew :klum-ast:test`, affected runtime/annotation module tests, `groovy4Tests`, `groovy5Tests`, and final `check`
  pass with lane isolation.
- Generated bytecode and mirrors contain only explicitly selected operations and exact public types.
- A named-schema fixture, when the touched generated linkage requires it, has no new `runtime.internal` reference.
- Each #689 acceptance criterion points to an executable test and documentation section; #648/#650/#651 can be closed or
  narrowed without an orphaned requirement.

**Commit boundary:** one evidence/reconciliation commit after the four behavior slices are green and SMB-D1 has a recorded
outcome; do not mix unrelated cleanup such as #503 into it.

## Acceptance matrix

| Contract | Focused seam | Public/consumer evidence | Compatibility evidence |
| --- | --- | --- | --- |
| Pure query projection | New `BuilderQueryTest`; existing `ModelVerificationVisitor` tests | `GeneratedDslSupportSpec`, Java and `@CompileStatic` Groovy, IDE mirror | Groovy 3/4/5; inherited and precompiled fixtures |
| Type predicate/narrowing | Runtime factory-provider test | Exact `Special_DSL.Builder<Special>` result in Java/Groovy | Sealed/inactive Builder and model-hierarchy cases |
| Input projection | `BuilderMethodProjection` and mutator/type-checking tests | Instance method, static converter/helper, mirror signatures | raw/wildcard/generic/opaque/collision negatives in all lanes |
| Result projection | `BuilderProjectionSpec`, ownership/session tests | exact single/Collection/Map result and mutator return | completed/Template/sealed/cross-session/wrong-type negatives |
| Documentary flow | New `SharedCapabilitiesDocumentaryTest` | `Advanced-Techniques.md` links through `@See` | direct-schema plus inherited Schema example |

Prefer new `*Test` class names. Reuse `BuilderProjectionSpec` and `GeneratedDslSupportSpec` only where their established
fixtures materially reduce duplication. No `@PendingFeature` test is needed until ADR 0020 is accepted and one of these
contracts is deliberately scheduled but not implemented; any such test must state the owning slice and removal condition.

## Documentation and release work

`Advanced-Techniques.md` owns the positive 4.1 examples because these annotations are Schema Developer techniques rather
than ordinary Model Writer syntax. `Builder-First-Migration.md` adds a diagnostic-to-replacement table for duplicated
queries, invalid `instanceof`, dynamic Builder parameter bridges, and unattachable generic `KlumBuilder<T>` results.
`CHANGES.md` records each capability only when its executable slice ships. `CONTEXT.md` gains the term **shared Builder
capability** only when the ADR is accepted: an explicitly projected domain operation, not Model/Builder substitutability.
If SMB-D1 later adopts a bulk state contract, that decision updates the definition separately.

The documentary path should evolve this compact Groovy example:

```groovy
@DSL
class Deployment {
    Registry registry

    @PostTree
    void normalizeRegistry() {
        if (SpecialRegistry.Create.isBuilder(registry)) {
            def special = SpecialRegistry.Create.asBuilder(registry)
            assert special.toUrl().startsWith('https://')
        }
    }
}

@DSL
class Registry {
    String host

    @BuilderQuery
    String toUrl() { "https://$host" }
}

@DSL
class SpecialRegistry extends Registry {
    String tenant
}
```

SMB-3/4 extend the same fixture with one explicitly annotated donor parameter/result and owned attachment rather than
creating an unrelated example vocabulary.

## Risks and open questions

| Risk/question | Decision or control |
| --- | --- |
| Local purity checks miss mutation hidden in foreign non-DSL calls. | Document `@BuilderQuery` as a Schema Developer assertion and reject locally visible construction/mutation; do not claim whole-program purity. |
| Annotation combinations become another implicit method taxonomy. | Keep the three annotations orthogonal; defer any bulk-query seam until post-slice evidence shows that it adds leverage; do not add an open-ended method-kind enum. |
| A speculative Builder State contract expands the current lane. | SMB-D1 requires an evidence-led implement, waive, or later-issue decision; no annotation name or generated companion shape is reserved now. |
| Projected overloads erase to one descriptor. | Reject the collision at Schema compilation and name both source signatures. |
| A Model result is confused with owned composition. | Require `@BuilderResult`; validate active-session unsealed Builder identity; leave every unmarked result unchanged. |
| Precompiled behavior differs from same-source behavior. | Treat emitted `Foo_DSL`/linked twins as the only precompiled authority; diagnose older opaque bytecode instead of analyzing method bodies. |
| Factory narrowing leaks internal Builder metadata. | Implement comparisons behind the runtime/generated bridge; expose only booleans and exact `B` identity. |
| Repeated Model/Builder inspection tempts a public union wrapper. | Keep any such view private to the runtime implementation until two real adapters need a seam; public methods retain exact Model or generated Builder-state types. |
| `@BuilderResult` on mutators proves lifecycle-incompatible. | Keep SMB-4's ordinary helper result independently deliverable and split the mutator case into a successor issue rather than broadening ownership. |

The final implementation may choose package-private compiler helper names freely. The three public annotation names and
factory operations are ADR-level decisions; changing them requires revising ADR 0020 before implementation publication.

## Issue-to-slice mapping

| Requirement/owner | Slices and acceptance evidence |
| --- | --- |
| #651 pure Builder-visible queries | SMB-1 and SMB-5; individual query parity, purity diagnostics, public/mirror signatures. |
| #648 Model/Builder predicate and narrowing | SMB-2 and SMB-5; factory-token identity matrix and exact Builder cast. |
| #650 explicit Builder parameters/results | SMB-3, SMB-4, and SMB-5; root navigation, converter/helper twins, exact attachment, generic/precompiled diagnostics. |
| #689 shared capability design and selected mutator results | SMB-4, SMB-D1, and SMB-5; explicit retargeted return, ownership/session failures, and a recorded bulk-interface disposition. |
| ADR 0003 Materialization and ownership | Every slice; no Model-to-Builder conversion, nested root lifecycle, or post-materialization mutation. |
| ADR 0004 Builder-producing projection | SMB-3/4 reuse linked twins, source-visibility rules, container fidelity, and active-session validation. |
| ADR 0005 generated public API | SMB-1/3/4 generated Builder signatures and mirror parity; `KlumBuilder<T>` stays zero-operation. |
| ADR 0010 public-interface conventions | SMB-2 factory-token interface and classification; no public internal helpers. |
| ADR 0011 multi-Groovy contract | Every implementation slice runs focused Groovy 3 and final Groovy 4/5 compatibility evidence. |
