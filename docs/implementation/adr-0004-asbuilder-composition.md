# ADR 0004 implementation context: `AsBuilder` composition

This document is the pickup package for implementing
[ADR 0004 — `AsBuilder` composition and Builder-producing factory projections](../adr/0004-asbuilder-composition-protocol.md).
It records the investigation performed on PR #429 so the follow-up can begin from confirmed evidence instead of repeating
repository archaeology. ADR 0003 and ADR 0004 remain authoritative when this document describes implementation options.
Implementation is tracked by [issue #431](https://github.com/klum-dsl/klum-ast/issues/431), which is blocked by #416 until
PR #429 merges.

Source positions below refer to PR #429 commit `0171fe54` unless a symbol name is given. Prefer the symbol when later edits
move a line.

## Merge and release recommendation

PR #429 may merge without implementing ADR 0004 because adding the complete source-transformation and construction-session
protocol would materially enlarge an already broad Builder-first change. The deferral is acceptable only when all of the
following remain true:

- desired compatibility behavior is represented by reasoned `@PendingFeature` tests rather than rewritten as intended
  rejection behavior;
- the PR, migration guide, and changelog explicitly identify the temporary incompatibility;
- issue #431 tracks issues #198, #270, #300, and #319 as affected established behavior and does not claim them preserved;
- root materialization inside an active lifecycle remains rejected until a Builder-producing path exists;
- ADR 0004 is resolved, or the compatibility removal is explicitly accepted, before the 4.0 API is finalized.

`@PendingFeature` is appropriate here because Spock still executes the scenario and expects the documented assertion to
fail. It records the target contract and will turn the build red when the implementation starts succeeding, forcing removal
of the annotation. Each annotation carries an actionable reason as required by `docs/agents/testing.md`.

## Confirmed regression

The concrete reported scenario is:

```groovy
Bar.Create.With {
    foos {
        From(configurationScript)
    }
}
```

Using a `DelegatingScript` makes this a safely Builder-applicable source, yet the current projection fails with:

```text
Cannot start an independent DSL Object factory while a Builder lifecycle is active.
```

The failure breadcrumb reaches `$/Foo.With/bars/From:script(...)`. A temporary Groovy 3 regression probe reproduced it
deterministically with:

```shell
./gradlew :klum-ast:test \
  --tests 'com.blackbuild.groovy.configdsl.transform.ConverterSpec.collection-local From creates a child Builder from a delegating script'
```

The same root cause affects:

- a collection-local projection of a custom single-result factory method;
- a collection-local projection returning a Collection or Map of DSL Objects;
- generated direct script collection methods such as `foos(scriptClass)`;
- DSL Object field, collection, map, and alternative converters whose implementation calls a root factory;
- abstract element factory projections that materialize concrete subclasses.

This is a regression of established behavior, not only a new convenience. The pre-PR tests expected successful collection
factory composition for issues #300/#319 and successful alternative converters for #270. Issue #319's motivating example
specifically defines a Collection-returning factory method whose implementation calls `From(file)` repeatedly. Issues #198
and its tests cover direct script-to-collection methods.

Regular Scripts whose `run()` returns `Element.Create.With(...)` are a separate category from `DelegatingScript`. They are
opaque materializing programs and remain intentionally rejected by ADR 0004 unless migrated to an explicit recipe or
Builder-producing protocol.

## Current source evidence

| Source position | What it proves |
|---|---|
| [`AlternativesClassBuilder.createDelegateFactoryMethod`](../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AlternativesClassBuilder.java), lines 272–324 | Generated collection-local factory methods copy the original return type and directly invoke `Element.Create.<method>`. Single, Collection, and Map results all use the materializing factory. |
| [`PhaseDriver.withBuilderLifecycle`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/process/PhaseDriver.java), lines 124–139 | Any root Builder factory entered while another lifecycle is active throws the observed exception. The method returns the completed root object, not a nested child. |
| [`FactoryHelper.createFrom`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/FactoryHelper.java), lines 269–303 | `DelegatingScript` already separates configuration into a `Consumer<KlumBuilder<T>>`, but `createFromDelegatingScript` unconditionally wraps it in the root `doCreate` lifecycle. This is the clearest extraction seam. |
| [`KlumBuilder.createNewBuilderFromParamsAndClosure`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), lines 698–709 | Generated child closure methods already implement the correct active-session pattern: allocate a Builder, apply Templates, run `PostCreate`, configure it, and return it without a nested `PhaseDriver`. |
| [`KlumBuilder.addElementsFromScriptsToCollection`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), lines 820–829 | Direct collection/map script overloads invoke the element's materializing `Create.From`, so they fail independently of the collection-local projection. |
| [`KlumBuilder.normalizeRelationshipValue`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), lines 432–445 | Composition accepts unsealed Builders. A completed DSL Object is wrapped only for `LINK`; other relationships reject it. Bypassing the lifecycle guard cannot make a completed result valid composition. |
| [`DSLASTTransformation`](../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.java), lines 947–953 and 1147–1153 | The generated direct collection/map overloads accept `Class<? extends Script>...` and route to the broken runtime helpers. |
| [`ConverterBuilder.createConverterMethod`](../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/ConverterBuilder.java), lines 216–276 | Generated converter methods preserve the model return type and invoke runtime setter/adder converter paths; they have no Builder-producing adaptation. |
| [`KlumBuilder.createObjectViaConverter`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), lines 634–676 | Runtime converter paths invoke the original converter and then normalize its result. A model-returning converter therefore fails before, or at, relationship normalization. |
| [`ConverterSpec`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ConverterSpec.groovy), features tagged for ADR 0004 | The PR originally changed previously successful converter and custom factory assertions into rejection assertions. They are now target-contract `@PendingFeature` tests. |
| [`AlternativesSpec`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/AlternativesSpec.groovy), issue #270 factory-method feature | Source-visible alternative converters have the same materializing call path and need the same hidden Builder twin. |
| [`ConvenienceFactoriesSpec`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ConvenienceFactoriesSpec.groovy), issue #198 list/map features | The existing examples use regular Scripts that return completed models. They deliberately remain rejection coverage for the opaque-script boundary. |

The relevant pre-PR merge base is `a3d9346eaf0490ba41d1a70b094abb784c2d4b7c`. Comparing its tests with PR #429
shows that successful assertions were replaced by lifecycle-error assertions; the generated projection itself was not made
Builder-aware.

## Why relaxing the nested-factory guard is insufficient

Before Builder-first materialization, nested `PhaseDriver.withPhaseDriver` calls incremented the active-object counter and
deferred phase execution until control returned to the root. Although the API call looked like a nested factory, it did not
execute a fully independent lifecycle immediately. That explains why #198/#270/#319 behavior previously worked.

The new `withBuilderLifecycle` cannot simply restore that implementation:

1. it is specified to return the completed root object, not the nested Builder;
2. the public root factory is declared to return model type `T`, so returning a Builder contextually would make the JVM
   signature untruthful and can introduce casts in Java or statically compiled Groovy;
3. returning a completed child would still be rejected by `normalizeRelationshipValue` for every non-`LINK` relationship;
4. independently materializing the child would run lifecycle and validation outside the owning graph and break identity,
   ownership, cycles, Template application, and graph-wide phase ordering.

Keep the root guard. Add an explicit active-session child-production path.

## Required semantic split

| Call context | Operation | Result | Lifecycle ownership |
|---|---|---|---|
| Standalone `Bar.Create.With/One/From` | Root creation | completed `Bar` | starts and completes one root lifecycle |
| `Bar.Create.AsBuilder` in active construction | Child production | unsealed `KlumBuilder<Bar>` / generated covariant Builder | joins current construction session |
| `bars { From(delegatingScript) }` | Collection projection | unsealed child Builder, then stored by owner | joins current construction session |
| `bars(delegatingScript)` | Direct collection recipe | one unsealed child Builder per script | joins current construction session |
| `bars { From(regularMaterializingScript) }` | Opaque nested materialization | rejected with migration guidance | none |
| completed `Bar` assigned to `LINK` | Aggregation | sealed Builder wrapper referencing the model | target keeps its original lifecycle |
| completed `Bar` assigned to composition | Adoption attempt | rejected | forbidden by ADR 0003 |
| Template returned at composition boundary | Recipe rehydration | fresh unsealed Builder graph | joins recipient construction session |

## Input adaptability

### Framework-owned inputs that can produce Builders

- `With`, `One`, and named-parameter configuration;
- `FromMap`;
- `DelegatingScript` classes;
- text, File, and URL sources parsed with `DelegatingScript` as their base class;
- Templates, by copying their recipe into a fresh Builder graph.

These paths should share one internal “prepare child Builder” primitive that performs, in order:

1. allocate the generated Builder and associate it with the active construction session;
2. mark it as a Template when the caller is building a Template graph;
3. apply active Templates;
4. run `PostCreate` once for ordinary construction;
5. apply values, closure, script, or data input;
6. run `PostApply` once;
7. return the still-unsealed Builder to the owning relationship method.

The root factory should wrap the same preparation primitive in `PhaseDriver.withBuilderLifecycle`; the child path must not.

### Inputs that require transformation or migration

- source-visible converters returning a DSL Object through recognizable `Create.With`, `Create.One`, or `Create.From` calls;
- source-visible custom factory methods, including methods that delegate to another custom method;
- source-visible Collection/Map-producing methods such as issue #319's `fromFolder` example;
- source-visible alternative converters that choose a concrete subtype.

Generate a hidden Builder-producing twin while retaining the original method and completed-model return type for direct
callers. Calls between adaptable methods in the twin must target other twins, not the original materializing methods.

### Opaque inputs

- precompiled model-returning converters or factory methods without a Builder contract;
- regular Scripts whose `run()` returns a completed model;
- classpath model scripts that only expose a completed-model result;
- arbitrary helper code whose construction path cannot be proven or redirected.

Do not project these as usable collection composition methods. Require an explicit `AsBuilder`/`KlumBuilder<T>` contract,
an explicitly marked Template recipe, or use as a root/aggregation `LINK` result. Never materialize and copy the result.

## Suggested implementation slices and commit order

Each slice should start by removing `@PendingFeature` from only the focused target test and making it green.

1. **Extract child preparation without behavior change.** Move the common Template, `PostCreate`, apply, and `PostApply`
   sequence out of `KlumBuilder.createNewBuilderFromParamsAndClosure` and `FactoryHelper.createFromDelegatingScript`. Keep
   root return values unchanged.
2. **Introduce construction-session-aware `AsBuilder`.** Provide the stable `KlumBuilder<T>` runtime method, active-session
   requirement, session association, and generated covariant override through the existing generated-type indirection.
   Test calls outside a session and cross-session adoption errors.
3. **Adapt framework-owned inputs.** Add Builder paths for configuration, `FromMap`, `DelegatingScript`, text, File, and URL.
   Keep regular materializing Scripts explicitly rejected. Verify lifecycle callbacks execute exactly once.
4. **Route collection projections.** Change `AlternativesClassBuilder.createDelegateFactoryMethod` so supported projected
   methods call Builder-producing counterparts and return Builders or Collections/Maps of Builders. Preserve keys,
   comparators where relevant, breadcrumbs, owner paths, and map key mappings.
5. **Route direct script collection methods.** Replace `addElementsFromScriptsToCollection/Map` calls to root `From` with the
   Builder path and narrow or validate the accepted Script category.
6. **Generate source-visible Builder twins.** Extend converter/custom factory transformation, including default parameters,
   keyed values, recursive custom method calls, Collection/Map results, and concrete subtype alternatives. Provide a
   source-retention hint for truthful explicit Builder contracts and IDE substitution.
7. **Filter opaque projections.** Do not generate composition methods that can only return completed models. Emit clear
   migration guidance at the nearest useful compile-time or invocation boundary without rejecting unrelated root usage.
8. **Finish Template consequences.** Introduce `KlumTemplateProxy`/`TemplateRecipeState`, graph-aware Template identity, and
   the `applyLater < INSTANTIATE` invariant. Move persisted modifying actions out of ordinary Model companions.
9. **Update public documentation and release notes.** Document `AsBuilder`, converter authoring, collection projections,
   opaque-script behavior, Template identity, and migration examples in the wiki and `CHANGES.md`.

Do not combine all source transformations in one commit. Single-result built-ins, direct script collections, multi-result
custom factories, and explicit converter contracts have distinct failure modes and should remain independently revertible.

## Existing target-contract tests

The following Groovy 3 features record behavior to restore and carry a reasoned `@PendingFeature` until their slice lands:

- `ConverterSpec`: DSL converter closures, keyed converter closures, source factory converters, default arguments, keyed
  single/list/map converters, custom collection/map factory projections, and abstract factory projections;
- `AlternativesSpec`: issue #270 source factory methods used as alternative methods;
- `BuilderFirstSpec`: source-visible DSL Object converter factory composition;
- `ConverterSpec`: collection-local `From(DelegatingScript)` and direct collection `scripts` overloads.

Keep the issue #198 `ConvenienceFactoriesSpec` rejection tests for regular Scripts returning completed models. They document
the opaque-script boundary selected by ADR 0004, not the adaptable `DelegatingScript` path.

When implementing, split features with multiple `when` blocks so single-result and multi-result paths can fail
independently. Add focused coverage for:

- root `Create.From` still returning a completed model;
- collection-local and direct `DelegatingScript` creation for List and keyed Map relationships;
- text, File, URL, and `FromMap` inputs;
- source custom factories returning one Builder, Collections of Builders, and Maps of Builders;
- recursive custom factory calls such as `WithAges -> WithAge -> With`;
- explicit Builder-returning converters and generated concrete return/delegate types;
- source-visible classic converter twins and unchanged direct completed-model calls;
- opaque/precompiled converters and regular Scripts failing with migration guidance;
- Templates returned from converters rehydrating into independent Builder graphs;
- ownership, breadcrumbs/model paths, cyclic relationships, active Templates, `PostCreate`/`PostApply` exactly once,
  graph-wide `POST_TREE`, materialization, and completed-model validation;
- rejection of cross-session Builder adoption.

Use Groovy 3 `test` as the focused baseline. Because the implementation changes AST-generated signatures and source
transformation, run `groovy4Tests` and `groovy5Tests` after each completed transformation slice where practical and always
at final handoff.

## Open implementation choices

These choices are not permission to change the ADR's semantic boundary:

- the name and source-retention shape of the compiler hint used to request generated concrete Builder types;
- whether the active construction session is represented by `PhaseDriver.Context` or a narrower internal capability;
- the internal naming scheme for hidden Builder-producing twins;
- how an unadaptable collection projection is omitted while still providing the best static/dynamic migration diagnostic;
- how generated Groovy documentation presents a collection-local method whose familiar name now returns a Builder;
- whether Collection/Map-producing twins return concrete containers of Builders or stream results directly into an owning
  relationship sink.

The public generated Builder name and nested/top-level layout remain owned by #394. General phase guards remain #281;
mutable Simple Value diagnostics remain #427; Jackson's persisted-versus-recomputed policy remains provisional under #428.

## Completion criteria

ADR 0004 is implemented when:

- all reasoned ADR 0004 `@PendingFeature` annotations have been removed because their target assertions pass;
- no generated composition method advertises a path that can only return a completed model and then fail adoption;
- root factory behavior remains completed-model-oriented;
- all owned children are unsealed Builders in one construction session through `POST_TREE`;
- Materialization and validation occur once for the complete graph;
- opaque completed-model producers have documented migration behavior;
- Template recipes retain modifying actions only in Template-specific companion state;
- focused Groovy 3 tests and the Groovy 4/5 compatibility lanes pass;
- wiki documentation, migration navigation, `CHANGES.md`, the tracking issue, PR issue links, and SonarCloud are current.
