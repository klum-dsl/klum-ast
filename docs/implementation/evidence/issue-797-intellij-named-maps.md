# Issue #797 — IntelliJ named-map acceptance

## Status

Repository preparation and automated evidence are complete. The available IntelliJ installation is 2026.2.3, build
`IU-262.10968.63` (About dialog observed on 2026-09-29). Native editor observation is pending for both fixture shapes.
No completion, type-help, or inspection result is claimed here yet.

## Fixture

Use [`named-map-ide`](../fixtures/named-map-ide/README.md):

- the same-project Schema/client uses the refreshed `Foo_DSL` mirror;
- a separate binary-consumer build uses the published `named-map-ide-schema:797.0` artifact only.

The Groovy client probes root, fixed single-child, and fixed collection-child literal maps; an inherited key; `setTitle`;
a method-first `mode(Integer)` override; a precise `String` key; an overloaded key whose safe metadata type is `Object`;
and a map-variable exclusion control. Unknown-key and wrong-value inspection probes are made by temporarily editing a
valid literal as described in the fixture README.

## Automated evidence

- `:klum-ast:test --tests com.blackbuild.klum.ast.NamedMapMetadataTest` — passed. This compiler suite covers literal
  metadata in same-source and separately compiled consumers, generated public contracts, the AnnoDocimal mirror,
  unknown and incompatible literal rejection, overload safety, and the map-variable exclusion.
- `:klum-ast-gradle-plugin:test --tests com.blackbuild.klum.ast.gradle.KlumDslSourceMirrorsIntegrationTest` — passed.
  This TestKit suite verifies the explicit source-mirror refresh lifecycle and IDE-only isolation from compile,
  publication, and downstream consumer inputs.

These checks verify the metadata/compiler contract and the mirror lifecycle. They do not establish IntelliJ editor
behavior.

## Manual IntelliJ record

| Fixture shape | Call shape | IntelliJ build | Completion | Value type help | Unknown key / wrong value inspection | Exclusion control |
| --- | --- | --- | --- | --- | --- | --- |
| Same-project source mirror | Root | IU-262.10968.63 | Pending | Pending | Pending | Pending |
| Same-project source mirror | Fixed single child | IU-262.10968.63 | Pending | Pending | Pending | Pending |
| Same-project source mirror | Fixed collection child | IU-262.10968.63 | Pending | Pending | Pending | Pending |
| Clean binary consumer | Root | IU-262.10968.63 | Pending | Pending | Pending | Pending |
| Clean binary consumer | Fixed single child | IU-262.10968.63 | Pending | Pending | Pending | Pending |
| Clean binary consumer | Fixed collection child | IU-262.10968.63 | Pending | Pending | Pending | Pending |

The installed build is recorded; each result cell must be filled after manually exercising that call shape in the stated
fixture. A GDSL change remains unjustified until the record demonstrates a specific native gap.
