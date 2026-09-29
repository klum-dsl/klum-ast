# Issue #797 — IntelliJ named-map acceptance

## Status

NAMED-IDE is an acceptance and evidence slice for the named-map metadata contract. It makes no compiler or runtime
semantic changes. IntelliJ IDEA 2026.2.3, build `IU-262.10968.63` (About dialog observed 2026-09-29), was used for both
fixture projects. Completion includes
both `title` and `setTitle` where both are public one-argument Builder operations; this accurately reflects the
NAMED-META catalog and runtime dispatch.

## Fixture

Use [`named-map-ide`](../fixtures/named-map-ide/README.md):

- the same-project Schema/client uses the refreshed `Foo_DSL` mirror;
- a separate binary-consumer build uses the published `named-map-ide-schema:797.0` artifact only.

The Groovy clients probe root, fixed single-child, and fixed collection-child literal maps; inherited operations; both
`title` and `setTitle`; a method-first `mode(Integer)` override; a precise `String` key; an overloaded key whose safe
metadata type is `Object`; and a map-variable exclusion control. Unknown-key and wrong-value inspection probes are made
by temporarily editing a valid literal as described in the fixture README.

## Automated evidence

- `:klum-ast:test --tests com.blackbuild.klum.ast.NamedMapMetadataTest` — passed on the unfiltered NAMED-META
  contract. This suite covers literal metadata in same-source and separately compiled consumers, generated public
  contracts, the AnnoDocimal mirror, unknown and incompatible literal rejection, overload safety, and map-variable
  exclusion.
- `:klum-ast-gradle-plugin:test --tests com.blackbuild.klum.ast.gradle.KlumDslSourceMirrorsIntegrationTest` — passed.
  This TestKit suite verifies the explicit source-mirror refresh lifecycle and IDE-only isolation from compile,
  publication, and downstream consumer inputs.
- The fixture Schema and binary consumer compile against the local artifacts pinned to
  `4.1.0-dev.133.uncommitted+codex.797.named.ide.9b75719`.

These checks verify the metadata/compiler contract and the mirror lifecycle. They do not establish IntelliJ editor
behavior.

## Manual IntelliJ record

| Fixture shape | Call shape | IntelliJ build | Completion | Value type help | Unknown key / wrong value inspection | Exclusion control |
| --- | --- | --- | --- | --- | --- | --- |
| Same-project source mirror | Root | IU-262.10968.63 | Works; includes both `title` and `setTitle` where applicable | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |
| Same-project source mirror | Fixed single child | IU-262.10968.63 | Works; includes both `title` and `setTitle` | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |
| Same-project source mirror | Fixed collection child | IU-262.10968.63 | Works; includes both `title` and `setTitle` | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |
| Clean binary consumer | Root | IU-262.10968.63 | Works; includes both `title` and `setTitle` where applicable | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |
| Clean binary consumer | Fixed single child | IU-262.10968.63 | Works; includes both `title` and `setTitle` | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |
| Clean binary consumer | Fixed collection child | IU-262.10968.63 | Works; includes both `title` and `setTitle` | Quick Documentation empty | No IDE error; compilation rejects invalid key/value | Works |

## Wrong-key IDE feedback investigation

Generated `With(Map)` and fixed-child `Map` parameters carry Groovy `@NamedParams` metadata. IntelliJ completion in both
source-mirror and binary builds reads the key catalog, including all public one-argument Builder operations such as
`title` and `setTitle`. The Groovy compiler rejects unknown names and incompatible values under `@CompileStatic`, but
the IDE does not mark those literal entries and Quick Documentation/type help is empty.

JetBrains guidance says GDSL cannot provide named-argument support for Map-backed calls and points to an IntelliJ
Groovy plugin extension (`GroovyNamedArgumentProvider`) for that integration. This indicates that a GDSL contributor
cannot fix the missing inspections. A dedicated IDE plugin and its documentation are out of scope, so no GDSL change is
added. See [JetBrains support](https://intellij-support.jetbrains.com/hc/en-us/community/posts/360008161720/comments/360001596780).
