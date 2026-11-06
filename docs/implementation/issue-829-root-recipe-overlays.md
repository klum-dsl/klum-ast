# #829 — root compiled-recipe overlays

Date: 2026-11-06. Status: **RCP-1/RCP-2 implementation plus speculative RCP-3 local consumer qualification; integration pending**.

Category B: extension of [ADR 0004](../adr/0004-asbuilder-composition-protocol.md#root-compiled-recipe-overlays--829-extension-2026-11-06).
Tracking: [#829](https://github.com/klum-dsl/klum-ast/issues/829). The maintainer selected closure-only overlays and
recipe → overlay → one root `PostApply`. ScHelm's compiled application plus completed external environment LINK is the
primary use case. No adopter qualification, issue completion, release admission, or remote delivery is implied.

Inspection base: `eec0a64a5357f69082711e92bf7a0aefa93538fc`. The
[decision brief and probe evidence](issue-829-root-recipe-decision-brief.md) retain the original investigation,
source-seam inventory, successful local characterization and explicit qualification gaps.

## Contract and current behavior

The accepted full design surface is exactly `From(Class<? extends DelegatingScript>, Closure)`, `FromClasspath(Closure)`, and
`FromClasspath(ClassLoader, Closure)` on ordinary root Factories. Generated signatures return their Model and annotate
the closure with the exact public Builder delegate and `DELEGATE_ONLY`. RCP-1 implements the direct-class overload;
RCP-2 adds both classpath forms through the same guarded recipe core:

```groovy
OrderApplication.Create.From(OrderApplicationScript) { environment selectedEnvironment }
OrderApplication.Create.FromClasspath { environment selectedEnvironment }
OrderApplication.Create.FromClasspath(modelLoader) { environment selectedEnvironment }
```

Existing `KlumFactory.From(Class<? extends Script>)` distinguishes recipes from regular Script completed-root programs.
Classpath routes resolve the conventional marker and reuse that dispatch. Classpath overlay overloads instead select
RCP-1's guarded recipe-only route; the existing resolver retains discovery, selected-loader and cause behavior.
`FactoryHelper.doCreate` / `prepareBuilder` already provide one ordinary root configuration Consumer between root
`PostCreate` and `PostApply`; execute the recipe and overlay there without additional completion operations.

Templates and root `PostCreate` run before the recipe. The overlay runs before root `PostApply`, graph phases 1–30,
materialization 40, validation 50 and verification 80. It cannot affect root initialization/PostCreate or already executed
recipe/child callbacks. `@Required` becomes completed-model validation. Use ordinary LINK normalization/sealing so the
external target retains identity and is not owned, copied, re-materialized or revisited by owned lifecycle traversal.
Late actions and callbacks may replace values later; no permanent caller priority is added.

Runtime recipe checks precede script construction/execution for overlay operations. Classpath discovery failures keep
existing marker/class context and causes. Configuration/phase failures abort the root, invalidate its Builders, and
leave subsequent invocations usable; no rollback of caller effects is promised. Nested independent roots stay rejected.
Clone the overlay before delegate mutation; do not change all existing closure application semantics as a side effect.

## Thin vertical slices and reasoned commits

### RCP-1 — direct compiled recipe

One vertical implementation commit adds the root class/closure overload, guarded internal recipe route and cloned
overlay execution with focused behavior/generated-contract tests and its documentary example. Keep runtime behavior and
the compiler-visible contract together rather than introducing a temporarily unsupported public method.

Owning seams: runtime `KlumFactory`, `FactoryHelper`, existing `BuilderDispatchSupport` / `InternalKlumBuilder.applyOnly`;
compiler `DSLASTTransformation` Factory specialization, `GeneratedDslSupport`, source mirrors, and projection omission
through `AlternativesClassBuilder` / `BuilderMethodProjection`. Session/materialization mechanics should need no change.

Acceptance: keyed/unkeyed roots, script-derived immutable key, exactly one root PostCreate/PostApply/materialization,
recipe then overlay, scalar/LINK conflicts, graph-phase context reads, Required/validation/verification, LINK identity
and unchanged external lifecycle, active Templates, deferred actions, failure cleanup, repeated distinct contexts,
concurrent invocations including a reused closure, nested-root rejection and surviving outer construction. Reject regular
Script input without constructing/running it. Check static rejection and actionable dynamic/erased-call diagnostics.
Prove exact delegate/return metadata and absence from AsBuilder, relationship/Cluster and Template APIs.

### RCP-2 — classpath marker

One vertical commit adds default/explicit-loader closure overloads through the same recipe-only core, with classpath
documentary coverage. Preserve script key derivation, marker lookup, loader choice, source diagnostics and original causes.

Acceptance: same root/context contract as RCP-1, missing marker/property/class, non-Script and regular-Script markers,
script/overlay failure cleanup, repeated/concurrent distinct contexts, and unchanged no-overlay regular Script results.
Do not interpret marker/file names as new key authority or introduce caller-provided source labels.

### RCP-3 — consumer qualification and release documentation

Finish with a focused qualification/documentation commit where useful; substantive fixes remain with the tests that
explain them. Exercise separately compiled Java and static/dynamic Groovy 3/4/5 clients, generated API inventory,
mirrors/GDSL, custom Factory signature collisions, existing binary calls and Groovy 4/5 JPMS without new internal exports.

Update `docs/user/Convenience-Factories.md`, `Factory-Classes.md`, relevant lifecycle/Template boundaries and migration
navigation plus `CHANGES.md`. Every added test carries `@Issue('829')`; happy-path DSL coverage is documentary with
`@Tag('documentary')` and `@See`, cross-linked from the user page and eventual issue acceptance record. No current user
documentation should describe the API as delivered before implementation.

Run focused Groovy 3 tests first, affected module suites and both compatibility lanes, applicable binary/JPMS/plugin
consumer checks, documentation/diff checks and root `check` for the shared generated/runtime contract. Hosted CI and
Sonar must be inspected on the eventual exact PR head; the existing characterization is not feature qualification.

## Compatibility, migration and stop conditions

Retain no-overlay descriptors and behavior. Existing Schemas need recompilation to expose the new generated interface
members; a runtime upgrade alone does not add them. Audit custom `(Class, Closure)`, `(Closure)` and
`(ClassLoader, Closure)` overrides. Test overload selection, including null literals: adding `FromClasspath(Closure)`
can make an untyped null ambiguous with the existing ClassLoader overload. Do not claim unconditional source compatibility.

Preserve Factory-selected concrete/default type, existing key authority, Template application/replay, LINK wrappers and
serialization. No public Builder/session extraction or general registry is required. The split dynamic-loader NPE noted
in the brief was a characterization gap. RCP-3 now exercises separate Schema/consumer compilation and JAR-only runtime
processes, including dynamic Groovy, without reproducing it. This qualifies those artifact boundaries; it does not
establish the cause of the original ad-hoc loader failure.

Stop and return for a decision if the consumer needs context before root PostCreate or during recipe execution, permanent
caller priority, additional source types, map overlays, nested independent roots, Template/AsBuilder overlay forms,
regular Script adaptation or generalized #304 composition. Those requirements must not enlarge these slices silently.

Issue-to-slice mapping: #829's direct recipe and lifecycle/context criteria → RCP-1; marker/loader parity → RCP-2;
Groovy/generated API/documentary and compatibility evidence → RCP-3. #304 remains separate. #953/ADR 0032 structured
input, Jackson and JSON Schema are unchanged. No GitHub issue/label/milestone update is part of this design execution.

## RCP-3 consumer qualification record

Speculative base: RCP-2 `adf94215733d6337689011f570a447654b684f4a`. RCP-3 adds test/documentation evidence only;
it changes no runtime or compiler implementation. RCP-2 integration and the eventual exact-head hosted CI/SonarCloud
remain delivery gates. No adopter execution, release admission or issue completion is claimed.

`RootRecipeConsumerTest` starts real Java 17 compiler/JVM processes. It compiles and packages the Schema before
compiling separate Java and static/dynamic Groovy clients, then deletes source and loose class trees. Consumers use only
JAR inputs; execution also removes the KlumAST compiler and compile-time transformations. The matched-generation matrix
covers Groovy 3/4/5 classpaths and Groovy 4/5 named modules with ordinary Schema exports/opens, no access-workaround flags
and no new internal exports. This is not a historical-version or arbitrary cross-generation artifact matrix.

For each route and client language, two completed environments retain exact LINK identity, the script supplies the root
key, and root PostApply/validation see one completed application. Java also checks that its reusable closure's delegate
and resolution strategy remain unchanged, and executes all three pre-existing no-overlay method descriptors against an
unkeyed Schema. Negative javac probes reject regular Script class overlays and untyped null ambiguity. The separately
projected source mirror retains the recipe bound, Model return and public Builder delegate; mirrors never enter consumer
compiler/runtime paths.

`RootRecipeFactoryContractTest` additionally checks custom overrides for all three overlay signatures and static Groovy
typed/untyped null selection. Existing keyed/unkeyed metadata and excluded-surface checks remain in place.
`KlumModelGdslBinaryContractTest` runs its published-Schema fixture on Groovy 3/4/5 and exercises a direct overlay after
resolving the real public Builder through portable GDSL. This establishes generated payload/runtime parity, not interactive
IntelliJ acceptance or a new IDE mapping.

The RCP-1/RCP-2 documentary examples remain in `RootRecipeOverlayDocumentaryTest`, linked from
[compiled root recipe overlays](../user/Convenience-Factories.md#compiled-root-recipe-overlays). No new DSL syntax needs a
second documentary example. Factory, migration and changelog guidance now describes artifact consumers; the existing
Sidebar and Migration links already expose all changed pages.

Focused qualification command (Java 17):

```shell
./gradlew :klum-ast:test --tests '*Root*Recipe*' \
  :klum-ast:groovy4Tests --tests '*Root*Recipe*' \
  :klum-ast:groovy5Tests --tests '*Root*Recipe*' \
  :klum-ast-gradle-plugin:test --tests '*KlumModelGdslBinaryContractTest' \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx1g
```

Full root `check`, standalone exact-revision documentation render/crawl and independent local Standards/Specification
reviews are required before the local handoff. Their final results and commit identities are recorded in the RCP-3
handoff; local success does not substitute for eventual hosted PR gates. Tracker impact is a proposed `Related: #829`
relationship for Hive reconciliation; no issue-state, label, milestone or curation mutation is part of this slice.

## FromStruct assessment — recommended follow-on, not accepted scope

Yes: the same **input → overlay → one root PostApply** contract fits structured root construction. Both current
`StructuredInputSupport.readRoot` routes prepare a Domain with existing key prerequisites, then pass `domain::execute`
to `FactoryHelper.createWithConfiguration`. A follow-on can execute that Domain and the cloned overlay in the same
Consumer. Calling public `applyStruct` inside `Create.With` is unsuitable: ADR 0032 intentionally gives it another
PostApply. Do not round-trip through an intermediate completed Model.

Illustrative future syntax, unavailable today:

```groovy
OrderApplication.Create.FromStruct([name: 'orders', image: 'orders:stable']) {
    environment selectedEnvironment
}
OrderApplication.Create.FromStruct('orders', [image: 'orders:stable'], 'orders.yaml') {
    environment selectedEnvironment
}
```

If separately accepted, append a Closure to each existing ordinary-root signature: `(Map, Closure)`,
`(Map, String sourceName, Closure)`, keyed `(String rootKey, Map, Closure)` and
`(String rootKey, Map, String sourceName, Closure)`. Keep generated delegate/return metadata consistent with #829.
This would be a bounded root Factory extension, not a new structured grammar or composition coordinator.

Preserve all existing document/body key authority, exact concrete type checks, ordered partial-failure behavior,
sourceName diagnostics and malformed-input rejection. The overlay cannot supply a missing construction key, repair an
invalid input operation, or run after failed input. External completed values enter through ordinary closure LINK calls;
their addition does not make LINK objects or references valid structured payloads and changes no JSON Schema.
Root PostCreate and structured-created child callback timing have the same limits as the recipe overlay. Do not project
these overloads to Template factories, AsBuilder or inherited Builder `applyStruct` without a separate decision.

The source seam establishes design feasibility. No new FromStruct behavior, API, executable probe or adopter result was
implemented/qualified here. Acceptance would need a separate explicit scope decision and its owning tracker relationship.
