# ADR 0020 implementation plan: explicit shared Model and Builder capabilities

This plan implements accepted
[ADR 0020](../adr/0020-explicit-shared-model-builder-capabilities.md) for
[#689](https://github.com/klum-dsl/klum-ast/issues/689). It orders the narrower
[#651](https://github.com/klum-dsl/klum-ast/issues/651),
[#648](https://github.com/klum-dsl/klum-ast/issues/648), and
[#650](https://github.com/klum-dsl/klum-ast/issues/650) contracts as five independently testable tracer bullets. No slice may
restore blanket delegation, expose hidden Builder implementations, or weaken Construction-session and Materialization
boundaries.

The `BQ` identifiers are stable delivery labels. The vocabulary and compatibility prerequisite is deliberately `BQ-0`, so
the established `BQ-1` identity continues to name pure-query projection while every later slice retains dependency order.

## Delivery status

| Slice | Delivery |
| --- | --- |
| BQ-0 — vocabulary and legacy bridge | Merged PR [#780](https://github.com/klum-dsl/klum-ast/pull/780) |
| BQ-1 — queries | Merged PR [#778](https://github.com/klum-dsl/klum-ast/pull/778) |
| BQ-2 — predicates and narrowing | Merged PR [#781](https://github.com/klum-dsl/klum-ast/pull/781) |
| BQ-3 and BQ-4 — inputs and results | Merged PR [#782](https://github.com/klum-dsl/klum-ast/pull/782); both slices were delivered despite the task's BQ-3 label |
| BQ-D1 — selector-interface checkpoint | Deferred to untargeted [#783](https://github.com/klum-dsl/klum-ast/issues/783) |
| BQ-5 — final reconciliation | Combined behavior, public-descriptor, language-consumer, mirror, documentation, and inventory evidence under #689 |

## Recorded baseline before implementation

- `WriteAccessMethodsMover` moves legacy `@Mutator` and lifecycle methods from the Model to the hidden Builder and
  retargets only established Owner/virtual-field positions. `@Mutator` is not yet deprecated, and there is no canonical
  nested `Builder` annotation vocabulary or general explicit input/result projection contract.
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
- Legacy `@Mutator` methods can return values, but a Model-declared DSL Object result is not an explicit promise of an owned
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
| `klum-ast-annotations` | Public `Builder` namespace with nested `@Builder.Query`, `@Builder.Method`, `@Builder.Input`, and `@Builder.Result`; canonical `@Builder.Method` declares `@WriteAccess(MANUAL)`, while deprecated source-compatible `@Mutator` installs an early promotion transform; KlumCast validation bindings. |
| `klum-ast` mutator/type-checking pipeline | Promote legacy `@Mutator` source to `@Builder.Method` during semantic analysis, then classify only the canonical manual category for method movement and downstream generation; validate query purity and annotation combinations; clone/retarget selected method bodies; bind Builder-phase calls to exact twins; diagnose completed-Model tests and invalid projections. |
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
- `@Builder.Result` means owned, unsealed, same-session Builder. It never adopts a completed Model or converts a `LINK` into
  composition.
- `@Builder.Query` and `@Builder.Method` are mutually exclusive method categories. An ordinary unannotated instance method
  remains Model-only; `@Builder.Input` and `@Builder.Result` modify projected signature positions but do not classify an
  instance method by themselves.
- `@Mutator` remains source-compatible with its existing Builder-only behavior but is deprecated in favor of
  `@Builder.Method`. Semantic analysis promotes the legacy spelling to the canonical annotation before DSL transformation,
  so downstream compiler code has one manual `WriteAccess` category. Existing source is not forced to migrate in the same
  release; newly compiled reflection metadata uses the canonical annotation.
- Unmarked Model parameters/results retain their exact completed-state meaning. Existing schemas change neither generated
  surface nor runtime behavior until they opt in.
- Bulk state-interface projection is not part of the 4.1 public contract. BQ-D1 completed the mandatory post-slice review
  by deferring that separate, evidence-led design to untargeted #783.
- `KlumBuilder<T>` remains zero-operation, and generated implementation classes remain unsupported.
- The public surface is the emitted `Foo_DSL` contract; IDE mirrors must be derived from it and remain excluded from
  compilation, packaging, and downstream inputs.
- A precompiled type produced with this contract is consumed through its emitted generated API. Older/opaque bytecode is
  never analyzed to synthesize a new twin.
- Model serialization, Template recipe serialization, Jackson input/output, validation, and Materialization are unchanged.
- Every new test carries its driving issue number. Every user-visible capability has a `@Tag("documentary")` example with
  an `@See` link to `docs/user/Advanced-Techniques.md`.

## Tracer bullets and reasoned commits

### BQ-0 — Establish the Builder method vocabulary and legacy bridge (#689)

**Work:** add the final, non-instantiable public `com.blackbuild.klum.ast.Builder` namespace class and its public nested
`Method` marker and mark it with `@WriteAccess(MANUAL)`. Attach a semantic-analysis promotion transform to deprecated
`@Mutator`; it rejects an explicitly combined declaration, removes the legacy annotation node, and adds
`@Builder.Method` before the DSL transformation runs. Validation still consumes the shared manual `WriteAccess` category,
while movement, configurator diagnostics, generated contracts, and annotation copying see only the canonical marker.
Reserve the nested `Query`, `Input`, and `Result` names in the ADR contract, but implement their behavior only in their
owning slices.

**Acceptance:**

- A method marked `@Builder.Method` is callable from Builder configuration/lifecycle code and is absent from the completed
  Model, with the same field retargeting and generated public signature as an equivalent legacy `@Mutator`.
- Existing `@Mutator` schemas compile and behave unchanged apart from the documented deprecation signal and canonical
  runtime annotation; their generated Builder API remains source and binary compatible.
- Focused promotion coverage proves that `@Mutator` becomes `@Builder.Method` before movement, field retargeting,
  generated-contract projection, and annotation copying, with no downstream compiler dependency on `Mutator`.
- Applying both canonical and legacy markers, applying both `@Builder.Query` and `@Builder.Method`, or applying a
  Builder-only category outside a DSL Object produces a targeted diagnostic.
- An ordinary unannotated instance method remains only on the Model and does not appear on the hidden Builder,
  `Foo_DSL.Builder`, or the IDE mirror.
- Java reflection/KlumCast validation sees the documented runtime retention and nested binary names consistently on
  Groovy 3, 4, and 5.
- The outer `Builder` type has no annotation semantics and no relationship to generated `Foo_DSL.Builder` interfaces
  beyond grouping the schema annotations by purpose.

**Commit boundaries:** (1) namespace, `@Builder.Method`, shared manual-category normalization, compatibility alias,
validation, and focused behavior/public API tests; (2) deprecation documentation, migration example, documentary coverage,
and `CHANGES.md`. Keep the normalization seam, alias behavior, and their equivalence tests in the same commit.

### BQ-1 — Project pure scalar queries (#651)

**Work:** add `@Builder.Query`; validate public non-void instance methods; clone the method onto the hidden Builder while
retaining the original Model method; retarget reads to Builder fields and recursively bind other projected queries. Reject
direct field writes, known mutator/lifecycle/construction calls, `FieldType.BUILDER` reads, DSL Object/Builder-bearing
results, the mutually exclusive `@Builder.Method` category, and projected signature collisions. Publish the method through
`Foo_DSL.Builder` with Builder-state documentation.

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

### BQ-2 — Add explicit Model-or-Builder identity and exact narrowing (#648)

**Work:** extend `BuilderFactoryProvider<T, B>` and generated Factory implementations with `isModelOrBuilder`, `isBuilder`,
and `narrowBuilder`. Compare completed values with `getModelType().isInstance`; compare Builder values through the existing
internal declared-Model-type hook behind an approved runtime/generated bridge. `narrowBuilder` returns the same identity as `B`
and performs no lifecycle transition.

**Acceptance:**

- Base/subtype completed Models and Builders follow ordinary assignability rules.
- `isModelOrBuilder` accepts both states; `isBuilder` accepts only Builders; null, foreign values, and mismatched model
  hierarchies return false.
- `narrowBuilder` gives Java and `@CompileStatic` Groovy the exact subtype Builder contract and rejects a completed Model,
  mismatch, null, or foreign value with a stable `KlumModelException` diagnostic.
- A sealed Builder wrapper can be narrowed for read-only projected queries; mutation still fails through its existing
  guard. A captured inactive Builder receives the same identity result and retains existing lifecycle failures.
- The API exposes neither `InternalKlumBuilder` nor raw session/model metadata, and the generated-linkage inventory stays
  clean on Groovy 3, 4, and 5.

**Commit boundaries:** one runtime/generated-factory behavior commit with Java/Groovy tests, followed by one documentary
and migration commit if separating prose keeps both commits independently reviewable.

### BQ-3 — Project explicitly marked Builder inputs (#650)

**Work:** add parameter-level `@Builder.Input`; recursively map supported concrete/abstract DSL Object and Collection/Map
positions to exact public Builder types. On instance methods, require the enclosing method to be classified as
`@Builder.Query` or `@Builder.Method`; the facet does not select an otherwise ordinary Model method. Clone query bodies for
the Builder, retarget Builder-only methods in place, and extend the existing linked-twin path for source-visible static
helpers and converters. Leave unmarked Model parameters unchanged.

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

### BQ-4 — Project explicit owned Builder results (#650/#689)

**Work:** add method-level `@Builder.Result`; project supported DSL Object or Collection/Map result positions to exact
public Builder types. Reject `@Builder.Query @Builder.Result` because a query cannot produce or claim an owned Builder.
For `@Builder.Method` and legacy `@Mutator`, retarget the moved method directly.
Source-visible static helpers retain ADR 0004's linked-twin path. Require every successful runtime result to be an unsealed
Builder in the current session and let the existing relationship attachment/claim path establish ownership.

**Acceptance:**

- A helper with `@Builder.Input` and `@Builder.Result` flows an exact Builder through root navigation and into an exact owned
  relationship method under static checking.
- A selected `@Builder.Method @Builder.Result` method can attach and return an owned child Builder without exposing the
  method on the completed Model; the legacy `@Mutator @Builder.Result` spelling has the same behavior during migration.
- A source-visible static helper retains its completed-Model form while its linked Builder twin consumes/returns exact
  Builders and owns its root lifecycle.
- Returning a completed Model, Template, sealed Builder, cross-session Builder, unattached Builder at session completion,
  or wrong Builder type retains or gains a targeted ownership/session diagnostic; no result is silently rehydrated.
- An unmarked DSL Object result remains a Model result and cannot be passed as owned composition.
- Supported container kind, order, comparator, duplicate behavior, and map keys match ADR 0004; generic/opaque failures
  are deterministic across Groovy 3, 4, and 5.

**Commit boundaries:** (1) static/helper result projection with ownership tests; (2) `@Builder.Method`/`@Mutator`
integration and focused
return diagnostics; (3) Java/static-Groovy/mirror/documentary/migration/release closure. If implementation evidence shows
mutator results require a materially different lifecycle contract, stop after the first commit and create a dedicated
successor issue rather than weakening `@Builder.Result`.

### BQ-D1 — Revisit bulk state-interface projection (#689)

**Timing:** after BQ-0 through BQ-4 have executable evidence and before final contract reconciliation.

**Decision:** defer selector-interface grouping to untargeted
[#783](https://github.com/klum-dsl/klum-ast/issues/783). BQ-0 through BQ-4 show that the four explicit annotations cover
the accepted 4.1 use cases without another public declaration-mapping seam. The later issue may reassess real-world
annotation repetition, but it must define naming, inheritance, precompiled behavior, position classification, diagnostics,
and compatibility evidence before adopting a selector. No selector or companion-interface name is reserved, and neither
the generated Builder nor the Model gains another interface in this tranche.

### BQ-5 — Reconcile the complete public contract (#689)

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

**Commit boundary:** one evidence/reconciliation commit after the five behavior slices are green and BQ-D1 has a recorded
outcome; do not mix unrelated cleanup such as #503 into it.

### #689 acceptance traceability

| Accepted requirement | Executable evidence | Documentation section |
| --- | --- | --- |
| Opt-in syntax plus exact generated public and IDE-mirror signatures | `BuilderMethodTest#'the IDE mirror contains only explicitly selected Builder methods'`; `GeneratedDslSupportSpec#'publishes Builder queries to bytecode Java static Groovy and source mirrors'`; `GeneratedDslSupportSpec#'publishes explicit Builder inputs and results to bytecode Java static Groovy and source mirrors'` | `Advanced-Techniques.md#builder-only-methods`, `#sharing-a-pure-query-with-builders`, and `#flowing-builders-through-explicit-inputs-and-results` |
| Safe operations in Model and Builder contexts | `BuilderQueryTest#'projects a pure scalar query to Builder state and keeps the Model method'`; `BuilderQueryTest#'rejects mutation and construction-only state in a Builder query'`; `SharedCapabilitiesDocumentaryTest#'combines explicit capabilities across an inherited Builder'` | `Advanced-Techniques.md#sharing-a-pure-query-with-builders` and `#combining-builder-capabilities` |
| Parameter/result projection and subtype narrowing semantics | `BuilderInputResultProjectionTest#'projects explicit static Builder input and result through lifecycle attachment'`; `BuilderTypeNarrowingTest#'matches completed Models and Builders with ordinary hierarchy assignability'`; `BuilderTypeNarrowingTest#'narrows a related Builder under static checking during lifecycle execution'` | `Advanced-Techniques.md#flowing-builders-through-explicit-inputs-and-results` and `#narrowing-a-builder-by-model-type` |
| Ambiguous, unsupported, generic, and precompiled diagnostics | `BuilderQueryTest#'rejects an ambiguous overloaded DSL call in a Builder query'`; `BuilderInputResultProjectionTest#'rejects unsupported explicit Builder input shapes'`; `BuilderInputResultProjectionTest#'rejects unsupported explicit Builder result shapes'`; `GeneratedDslSupportSpec#'rejects an explicitly annotated precompiled helper without an emitted Builder twin'` | `Builder-First-Migration.md#migration-checklist` |
| Migration examples and Groovy 3/4/5 compatibility strategy | `SharedCapabilitiesDocumentaryTest` methods linked from each capability section; the same sources compile in `test`, `groovy4Tests`, and `groovy5Tests` | `Builder-First-Migration.md#mutator-to-buildermethod`, `#migration-checklist`, and this plan's `#acceptance-matrix` |
| Independently testable tracer slices and final combined reconciliation | `GeneratedDslSupportSpec#'reconciles direct and inherited Builder capabilities across bytecode consumers and source mirrors'` plus the focused tests above | This plan's `#delivery-status`, `#tracer-bullets-and-reasoned-commits`, and ADR 0020's `#defer-bulk-state-interface-projection-to-later-evidence` |

## Acceptance matrix

| Contract | Focused seam | Public/consumer evidence | Compatibility evidence |
| --- | --- | --- | --- |
| Method category and legacy bridge | New `BuilderMethodTest`; existing mutator/write-access tests | Deprecated `@Mutator` source is promoted to `@Builder.Method` and emits the same `Foo_DSL.Builder` method; neither remains on the Model | Groovy 3/4/5; canonical reflection, canonical/legacy conflict, and unannotated-method negatives |
| Pure query projection | New `BuilderQueryTest`; existing `ModelVerificationVisitor` tests | `GeneratedDslSupportSpec`, Java and `@CompileStatic` Groovy, IDE mirror | Groovy 3/4/5; `Query`/`Method` exclusion, inherited and precompiled fixtures |
| Type predicate/narrowing | Runtime factory-provider test | Exact `Special_DSL.Builder<Special>` result in Java/Groovy | Sealed/inactive Builder and model-hierarchy cases |
| Input projection | `BuilderMethodProjection` and method-category/type-checking tests | Classified instance method, static converter/helper, mirror signatures | facet-without-category/raw/wildcard/generic/opaque/collision negatives in all lanes |
| Result projection | `BuilderProjectionSpec`, ownership/session tests | exact single/Collection/Map result and `Builder.Method` return | query-result conflict plus completed/Template/sealed/cross-session/wrong-type negatives |
| Combined contract | `SharedCapabilitiesDocumentaryTest#'combines explicit capabilities across an inherited Builder'` | `GeneratedDslSupportSpec#'reconciles direct and inherited Builder capabilities across bytecode consumers and source mirrors'`; Java, `@CompileStatic` Groovy, and AnnoDocimal mirrors | direct declarations plus inherited Builder API; full Groovy 3/4/5 lanes |

Prefer new `*Test` class names. Reuse `BuilderProjectionSpec` and `GeneratedDslSupportSpec` only where their established
fixtures materially reduce duplication. No `@PendingFeature` test is needed until ADR 0020 is accepted and one of these
contracts is deliberately scheduled but not implemented; any such test must state the owning slice and removal condition.

## Documentation and release work

`Advanced-Techniques.md` owns the positive 4.1 examples because these annotations are Schema Developer techniques rather
than ordinary Model Writer syntax. `Builder-First-Migration.md` first shows the mechanical `@Mutator` to
`@Builder.Method` replacement and states that legacy source is promoted to the canonical runtime marker, then adds a diagnostic-to-replacement table
for duplicated queries, invalid `instanceof`, dynamic Builder parameter bridges, and unattachable generic
`KlumBuilder<T>` results.
`CHANGES.md` records each capability only when its executable slice ships. `CONTEXT.md` gains the term **shared Builder
capability** only when the ADR is accepted: an explicitly projected domain operation, not Model/Builder substitutability.
If BQ-D1 later adopts a bulk state contract, that decision updates the definition separately.

The migration is a spelling change, not a behavior change:

```groovy
// Existing 4.x source remains accepted, but this spelling is deprecated.
@Mutator
void normalizeHost() { host = host.toLowerCase() }

// Canonical spelling for new and migrated source.
@Builder.Method
void normalizeHost() { host = host.toLowerCase() }
```

Migration documentation must not imply that replacing `@Mutator` changes receiver state, visibility, generated signatures,
or lifecycle timing. It should explain that compilation already emits the canonical `@Builder.Method` marker, recommend
the canonical source spelling when a schema is next edited, and avoid requiring a bulk rewrite before the compatibility
alias is removed by a separately announced decision.

The documentary path should evolve this compact Groovy example:

```groovy
import com.blackbuild.klum.ast.Builder

@DSL
class Deployment {
    Registry registry

    @PostTree
    void normalizeRegistry() {
        if (SpecialRegistry.Create.isBuilder(registry)) {
            def special = SpecialRegistry.Create.narrowBuilder(registry)
            assert special.toUrl().startsWith('https://')
        }
    }
}

@DSL
class Registry {
    String host

    @Builder.Query
    String toUrl() { "https://$host" }

    @Builder.Method
    void normalizeHost() { host = host.toLowerCase() }
}

@DSL
class SpecialRegistry extends Registry {
    String tenant
}
```

BQ-3/4 extend the same fixture with one explicitly annotated donor parameter/result and owned attachment rather than
creating an unrelated example vocabulary.

## Risks and open questions

| Risk/question | Decision or control |
| --- | --- |
| Local purity checks miss mutation hidden in foreign non-DSL calls. | Document `@Builder.Query` as a Schema Developer assertion and reject locally visible construction/mutation; do not claim whole-program purity. |
| The nested `Builder.Method` name is mistaken for the rejected enum design. | Specify `Query` and `Method` as separate zero-argument categories, keep `Input` and `Result` as position facets, reject mixed categories, and never add a `MethodType` member. |
| `@Mutator` deprecation breaks existing schemas or creates two subtly different Builder-only paths. | Keep legacy source compatible but promote its annotation node during semantic analysis; every later compiler stage sees only `@Builder.Method`. Test equivalent emitted signatures, canonical reflection metadata, and the double-annotation diagnostic. |
| A speculative Builder State contract expands the current lane. | BQ-D1 deferred selector-interface grouping to untargeted #783; no annotation name or generated companion shape is reserved. |
| Projected overloads erase to one descriptor. | Reject the collision at Schema compilation and name both source signatures. |
| A Model result is confused with owned composition. | Require `@Builder.Result`; validate active-session unsealed Builder identity; leave every unmarked result unchanged. |
| Precompiled behavior differs from same-source behavior. | Treat emitted `Foo_DSL`/linked twins as the only precompiled authority; diagnose older opaque bytecode instead of analyzing method bodies. |
| Factory narrowing leaks internal Builder metadata. | Implement comparisons behind the runtime/generated bridge; expose only booleans and exact `B` identity. |
| Repeated Model/Builder inspection tempts a public union wrapper. | Keep any such view private to the runtime implementation until two real adapters need a seam; public methods retain exact Model or generated Builder-state types. |
| `@Builder.Result` on Builder-only methods proves lifecycle-incompatible. | Keep BQ-4's static-helper result independently deliverable and split the `@Builder.Method`/legacy `@Mutator` case into a successor issue rather than broadening ownership. |

The final implementation may choose package-private compiler helper names freely. The public `Builder` namespace and its
four nested annotation names, the `@Mutator` compatibility bridge, and the factory operations are ADR-level decisions;
changing them requires revising ADR 0020 before implementation publication.

## Issue-to-slice mapping

| Requirement/owner | Slices and acceptance evidence |
| --- | --- |
| Closed #651 pure Builder-visible queries | Delivered by BQ-1/PR #778 and reconciled by BQ-5; individual query parity, purity diagnostics, public/mirror signatures. |
| Closed #648 Model/Builder predicate and narrowing | Delivered by BQ-2/PR #781 and reconciled by BQ-5; factory-token identity matrix and exact Builder cast. |
| Closed #650 explicit Builder parameters/results | Delivered by BQ-3 and BQ-4/PR #782 and reconciled by BQ-5; root navigation, converter/helper twins, exact attachment, generic/precompiled diagnostics. |
| #689 canonical Builder vocabulary and shared capability design | BQ-0 through BQ-5; category/facet diagnostics, `@Mutator` migration, explicit retargeted return, ownership/session failures, combined direct/inherited evidence, and BQ-D1's deferral to #783. |
| ADR 0003 Materialization and ownership | Every slice; no Model-to-Builder conversion, nested root lifecycle, or post-materialization mutation. |
| ADR 0004 Builder-producing projection | BQ-3/4 reuse linked twins, source-visibility rules, container fidelity, and active-session validation. |
| ADR 0005 generated public API | BQ-0/1/3/4 generated Builder signatures and mirror parity; `KlumBuilder<T>` stays zero-operation. |
| ADR 0010 public-interface conventions | BQ-0 nested public vocabulary plus BQ-2 factory-token interface and classification; no public internal helpers. |
| ADR 0011 multi-Groovy contract | Every implementation slice runs focused Groovy 3 and final Groovy 4/5 compatibility evidence. |
