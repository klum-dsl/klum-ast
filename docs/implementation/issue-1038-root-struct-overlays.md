# #1038 — ordinary root FromStruct overlays

Accepted contract: [#1038](https://github.com/klum-dsl/klum-ast/issues/1038), clarified 2026-11-07 to preserve
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) ordered partial execution. RCO-1 implements the first
vertical slice; repository delivery, hosted qualification and adopter execution are separate evidence.

## Public contract and owning seam

Append a Closure to every existing ordinary-root `FromStruct` signature:

```text
FromStruct(Map<?, ?> document, Closure<?> overlay)
FromStruct(Map<?, ?> document, String sourceName, Closure<?> overlay)
FromStruct(String rootKey, Map<?, ?> body, Closure<?> overlay)
FromStruct(String rootKey, Map<?, ?> body, String sourceName, Closure<?> overlay)
```

Common signatures serve keyed documents and unkeyed roots; explicit-key signatures remain keyed-only. Generated Factory
members return the exact selected Model and delegate closures to its public Builder with `DELEGATE_ONLY`. Template,
AsBuilder, relationship and Cluster APIs gain no overlay forms. Builder `applyStruct` semantics remain unchanged.

`StructuredInputSupport.readRoot` retains its source/concrete-type/static-contract/root-shape/key prerequisites and
its existing internal Domain traversal. One `FactoryHelper.createWithConfiguration` Consumer executes the Domain then
an invocation-local clone of the ordinary Builder closure. Existing root preparation supplies Templates and PostCreate
first, followed by one PostApply, graph phases, materialization and validation. No intermediate completed Model, context
channel, extra lifecycle or construction engine is introduced. Source names remain opaque diagnostics.

An invalid later operation may fail after earlier operations/child callbacks have run. Such a failure skips the overlay
and returns no completed root, without rollback. Missing required identity still prevents allocation; an overlay cannot
supply it. Overlay/lifecycle failures use normal session cleanup. Completed LINK targets retain identity and are not
owned or revisited by composition lifecycle traversal. Deferred actions and later callbacks retain ordinary authority.

Generated Factory forwarding explicitly casts its trailing Closure argument so a typed null retains its selected Closure
overload instead of redispatching to the String source-name overload. The change stays in the existing undelegated
closure wrapper generator; custom Factory methods retain override authority. Existing no-overlay descriptors remain.
Untyped null source/overlay arguments expose additive source ambiguity; use an explicit type. Recompile Schema artifacts
to gain the generated members. No grammar, JSON Schema protocol or module export changes are required.

## Acceptance coverage

- `RootStructOverlayBoundaryTest` retains the no-overlay ordered-failure and missing-key prerequisite characterization.
- `RootStructOverlayTest` covers all six keyed/unkeyed/source call shapes; one initializer, root PostApply and validation;
  one completed Model and sealed Builder; external LINK identity and untouched external lifecycle; Template/child timing;
  late-action authority; unknown/bad-payload/inner-key failures; allocation prerequisites; overlay/verification cleanup;
  reused closure delegate isolation, distinct roots, null/no-overlay parity and concrete default-type selection.
- `StructuredFactoryTest` compiles Java and static Groovy consumers, checks exact return/delegate metadata and source
  mirrors, rejects unsupported static Builder calls, verifies typed/untyped null selection, excluded surfaces and custom
  Factory override authority. The previous recipe-only exclusion assertion is updated for the separate Struct addition.
- Documentary example: `RootStructOverlayTest#'supplies a completed environment after Struct input before the root lifecycle tail'`,
  tagged `documentary`, with `@Issue('1038')` and `@See` pointing at the
  [user contract](../user/Structured-Input.md#root-builder-overlays). Factory/convenience navigation, migration and
  `CHANGES.md` describe the same contract; existing Sidebar links already expose these pages.

Focused validation uses `:klum-ast:test`, `:klum-ast:groovy4Tests` and `:klum-ast:groovy5Tests`, each filtered to
`*RootStructOverlay*`, `*StructuredFactoryTest` and `*RootRecipeFactoryContractTest`. Shared compiler/runtime changes also
require root `check` (including generator/plugin/binary/JPMS regressions) and `renderLocalDocumentation` with an exact
committed source/renderer revision. Final command results, two independent Standards/Specification local reviews and
commit identities belong in the handoff and eventual draft PR. No manual Sonar upload is performed.

A subsequent #1038 slice can independently qualify the new methods through JAR-only Java/static/dynamic Groovy clients
and supported Groovy 4/5 named modules, plus existing pre-addition binary calls. RCO-1 does not claim that dedicated
new-entry-point artifact matrix or production Schelm qualification. Tracker relationship remains `Related: #1038` for
Hive reconciliation; this slice does not select issue closure, release admission or a general composition design.
