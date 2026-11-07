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

## RCO-2 artifact and binary qualification

RCO-2 starts from the assigned RCO-1 commit `0266b9ae724dff4ad8e873e749654cf428d51ac3`. Its qualification
adds no production behavior, public signatures, metadata or module directives. It extends the existing
`RootRecipeConsumerTest` infrastructure rather than creating another compiler/process harness.

The fixture compiles the Schema, Java client and static/dynamic Groovy clients in separate Java 17 processes. Each
artifact is packaged into a JAR and its source/class directories are removed before downstream consumption. Runtime
omits the Klum compiler and transformation-only dependencies. Every client exercises all six forms: common keyed and
unkeyed roots, optional source names, and the keyed explicit-key pair. Struct assigns the initial value, the overlay
replaces it and supplies a completed external LINK, and root PostApply/validation require exactly one lifecycle tail.
Two independently selected LINK instances retain identity; caller Closure delegate/strategy remain unchanged. The existing
recipe-overlay cases remain in the same harness as regression coverage.

Groovy 3 qualifies classpath consumers. Groovy 4 and 5 each qualify both classpath and named-module consumers. Named
execution uses actual descriptors, exported Schema packages and the supported runtime opening, with no add-reads,
add-exports, add-opens or patched modules. Source mirrors remain absent from consumer inputs.

For bounded pre-addition binary-call evidence, the test reconstructs the old **public Factory surface** from the fixture
Schema JAR by removing exactly the twelve new Struct/Closure declarations across its two keyed and two unkeyed Factory
interfaces. A negative javac control proves the new overlay call is unavailable. Java and static Groovy clients then
compile all six existing no-overlay call shapes against that restricted surface. Their resulting JAR executes unchanged
against the complete current Schema and runtime, including both named-module lanes. The restricted Schema JAR is never a
runtime input. This proves callable-descriptor compatibility for those existing client shapes; it does not reproduce a
historical compiler/runtime pair or establish arbitrary mixed-version support.

RCO-1's documentary test and user guidance remain the behavior authority. RCO-2 introduces no additional user-visible
feature, so no new documentary example, migration rule or changelog entry is needed. Validation results and exact final
base/head belong in the PR/handoff; full hosted CI and SonarCloud remain separate gates.

Production Schelm qualification is still separate. Template/AsBuilder/Builder overlays, additional input sources,
context-before-Struct hooks and general composition remain outside #1038's accepted root slice. Tracker relationship
remains `Related: #1038`; the Hive owns issue/release reconciliation and no issue closure is selected here.

## RCO-3 consumer-owned lifecycle qualification

RCO-3 starts from exact RCO-2 head `16a2e6c6c3c0a09befc941595825f2ae6183f7ae`. It extends the same JAR-only
`RootRecipeConsumerTest` harness with a separate consumer-owned `fixture.lifecycle` Schema artifact and static Groovy
client. Compiler, annotations and runtime JARs are built together from the current stack checkout; no published Dev
version or mixed producer/runtime version is used. Schema and client sources/classes are removed after JAR creation,
and runtime retains the existing compiler-free classpath/named-module boundary. RCO-2's binary-call controls remain intact.

The documentary fixture takes already-parsed Environment and Application YAML mappings. Consumer setup completes two
Environments via ordinary `Environment.Create.FromStruct` before constructing any Application. An ordinary application
root overlay then assigns the selected completed Environment to a required LINK and replaces the input image. Owned
Services have already run PostCreate/PostApply without context; their AutoLink callbacks read their Owner's Environment,
then Default derives endpoint and image. They do not read context during Struct execution or reorder its operations.
Completed validation verifies the root/child owner and LINK identities. Captured public root Builders reject subsequent ordinary mutation;
the returned Models retain their configured state. Phase sequences and validation counts require one ordinary lifecycle tail.

A missing-overlay control fails at root PostApply after Struct configuration and before graph completion/validation.
Successful loads afterward exercise failure cleanup. Two roots reuse one Environment and a third uses the other; the
external objects retain exact identity, their original lifecycle sequences and validation count, and absent composition
ownership. The input mapping retains its original image and contains no Environment context entry.

This is a general contract qualification at the parsed-Struct boundary, not production Schelm execution or parser,
selection-policy, artifact-publication or release qualification. No production code/API, context mechanism, generated
contract, lifecycle ordering or grammar changes are introduced. User guidance links this documentary scenario under
[consumer-owned Environment setup](../user/Structured-Input.md#consumer-owned-environment-setup). Existing RCO-1 release
and migration entries remain accurate; this evidence-only extension needs no additional changelog or migration rule.
Final parent/head identities, commands, two-axis reviews and hosted CI/Sonar status belong in the PR/handoff.
