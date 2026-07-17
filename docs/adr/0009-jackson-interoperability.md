# Jackson interoperability through explicit Builder import

Date: 2026-07-16

Status: Accepted

Tracking issues:

- [#428 — Jackson interoperability for immutable DSL Objects](https://github.com/klum-dsl/klum-ast/issues/428)
- [#447 — Jackson wire-format metadata decision](https://github.com/klum-dsl/klum-ast/issues/447)
- [#251 — resolved Jackson property names](https://github.com/klum-dsl/klum-ast/issues/251)
- [#463 — explicit importer modes and construction paths](https://github.com/klum-dsl/klum-ast/issues/463)
- [#464 — asymmetric interoperability closure](https://github.com/klum-dsl/klum-ast/issues/464)

Implementation status: JSON-1 property-aware Builder binding is implemented by
[#439](https://github.com/klum-dsl/klum-ast/issues/439), and JSON-2 identity/customization groundwork is implemented by
[#440](https://github.com/klum-dsl/klum-ast/issues/440). The JSON-3/#463 public importer contract was confirmed on
2026-07-17 and remains to be implemented; JSON-4/#464 owns interoperability compatibility closure. See the
[implementation plan](../implementation/adr-0009-jackson-interoperability.md).

Supersedes: [ADR 0007 — Jackson deserialization as configuration replay](0007-jackson-configuration-replay.md)

Parent decisions:

- [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)
- [ADR 0004 — AsBuilder composition](0004-asbuilder-composition-protocol.md)

## Context

ADR 0007 treated JSON as KlumAST persistence: serialize public Builder configuration and later replay it through one
lifecycle. The actual product need is different. Schema Developers must import externally owned YAML or JSON whose shape
KlumAST does not control, enrich it through lifecycle work, and export a completed model in the shape required by another
tool. A Groovy model script, not emitted JSON, is KlumAST's native durable representation.

Import and export can therefore differ intentionally. Import must adapt foreign property names and values without
replacing Klum Builder construction. Export must let a completed DSL Object behave like an ordinary consumer-facing POJO.
Adding a Klum envelope, reserved metadata property, or round-trip promise would collide with external schemas and solve a
use case KlumAST does not own.

## Decision

### Interoperability boundary

KlumAST defines no JSON/YAML wire format and adds no format, schema, producer, or library version to external documents.
An external version property is ordinary Schema-controlled data: missing, current, old, malformed, and future values follow
normal Jackson mapping, unknown-property, conversion, and validation behavior. KlumAST supplies no generic version
registry, migration adapter, or compatibility comparison.

`KlumAstModule.version()` may report the KlumAST artifact version as Jackson module metadata. Runtime version information
may support diagnostics, but it is not document producer metadata and is not part of stable exception text.

### Managed import

The public import seam is a final, data-format-neutral `KlumJacksonImporter` in
`com.blackbuild.klum.ast.jackson`. It is configured through a caller-supplied mapper or reader, has private construction,
is immutable and reusable, and exposes exactly these public operations:

```java
public final class KlumJacksonImporter {
    public static KlumJacksonImporter using(ObjectMapper mapper);
    public static KlumJacksonImporter using(ObjectReader reader);

    public <T> T readRoot(Class<T> type, KlumJacksonInput input);
    public <T> T readTemplate(Class<T> type, KlumJacksonInput input);
    public <T, B extends KlumBuilder<T>> B readBuilder(
            KlumFactory.BuilderFactory<T, B> factory,
            KlumJacksonInput input);
    public <B extends KlumBuilder<?>> B applyToBuilder(
            B builder,
            KlumJacksonInput input);
}

public final class KlumJacksonInput {
    public static KlumJacksonInput parser(JsonParser parser);
    public static KlumJacksonInput tree(JsonNode node);
    public static KlumJacksonInput map(Map<?, ?> values);
    public KlumJacksonInput named(String source);
}
```

The methods declare no checked exceptions. `readRoot` runs one lifecycle and returns completed `T`; `readTemplate` returns
`T` marked internally as a value-only Template and runs no lifecycle; `readBuilder` requires the active Construction
session and preserves the precise generated `Foo_DSL.Builder` return through `B`; `applyToBuilder` requires an unsealed
Builder in that session and returns the identical `B`. Hidden Builder implementation types and `ConstructionSession` are
not exposed. The current generated Java accessor spelling is `Create.getAsBuilder()` and Groovy property syntax is
`Create.AsBuilder`; final generated factory naming is coordinated by #467 without changing the importer signature.

`using(mapper)` snapshots `mapper.reader()` once and never mutates the mapper or registers `KlumAstModule` automatically.
`using(reader)` preserves the reader's views, attributes, injectable values, features, root configuration, and other
caller-owned settings, but accepts only an untyped reader with no value-to-update target. A typed or updating reader fails
at `using` with `IllegalArgumentException`. The importer is reusable and thread-safe for independent imports to the extent
that its captured reader and inputs are safe; Builder operations remain confined to their active session and thread.

`KlumJacksonInput` is an immutable adapter with no public constructor. A parser is borrowed, single-pass, and never closed
by the importer. A tree or Map is not mutated. `named` supplies an opaque diagnostic source identity and returns an input
with the same value; unnamed inputs use stable `parser`, `tree`, or `map` identities. Strings, readers, streams, files,
URLs, and YAML document iteration remain caller-owned Jackson concerns rather than importer overloads.

The caller selects one of the four modes explicitly; the importer never chooses a mode from ambient state. Each operation
accepts exactly one input. Missing values preserve current Builder state, while present values use the captured reader's
normal Jackson null, merge, and replacement semantics. An explicitly mapped Collection may therefore be set to `null`.
Repeated `applyToBuilder` calls are separate operations and do not promise configuration-layer composition. Ordered
composition, heterogeneous sources, and cross-layer overwrite/null/list/map policy are source-neutral core work owned by
#304; a future coordinator may treat one `KlumJacksonInput` as one layer.

A standalone raw `ObjectMapper.readValue(DslType)` remains a supported but discouraged 4.x compatibility path for an
independent managed root. A managed raw root read or `readRoot` inside an active Construction session fails before Builder
allocation with guidance to use `readBuilder` or `applyToBuilder`; ordinary non-DSL reads remain unchanged. An explicit
type-level deserializer is outside managed construction and retains ordinary Jackson behavior.

Root import retains the Builder-first order established by JSON-1: allocate Builders and initializers, run `PostCreate`,
bind present properties, run `PostApply`, then complete graph phases, Materialization, validation, and verification once.
Owned composition is reconstructed as Builders in that session. Lifecycle-derived values are recomputed rather than
rebound. A Template import binds values and owned Template composition but runs no lifecycle until normal Template
application.

### Jackson customization

Managed import honors ordinary property/value customization: names, aliases, access/ignore rules, naming strategies,
mixins, views, unknown-property policy, formats, Simple Value codecs, null/content policies, merge configuration,
polymorphic type ids, and Collection/Map element conversion.

Construction remains owned by KlumAST. `@JsonCreator`, direct completed-model mutation, and
`@JsonDeserialize(builder = ...)` fail explicitly rather than being ignored. A property/type deserializer that produces a
completed DSL Object cannot fill an owned relationship; guidance points to child annotations, a converter, or a
`FieldType.BUILDER` staging field. An explicit type-level custom deserializer is the deliberate full opt-out and owns the
`readRoot` result without an additional Klum lifecycle. Template and Builder modes reject that opt-out because it cannot
provide their managed state. Documentation should strongly discourage this advanced escape hatch in favor of converters
or Builder staging.

An explicit `LINK` property codec or object-id resolver may return an existing completed DSL Object. KlumAST represents it
as a sealed Builder wrapper, preserves its identity, and excludes it from mutating lifecycle phases. A same-session
Builder identity is also valid. The same completed value is rejected for a non-`LINK` relationship because owned
composition must join the current Construction session and lifecycle. Inline `LINK` input never silently creates owned
composition; it requires explicit identity/reference handling, property conversion, or later lifecycle resolution.

### Export

KlumAST provides no export facade. Client Developers serialize completed DSL Objects through ordinary Jackson APIs. The
external projection may include lifecycle-derived/read-only values and need not be accepted by the import path. Builder
state and synthetic members remain hidden; Owner and Role remain omitted in 4.0, with configurable override deferred.

A non-null `LINK` needs an explicit serialization choice. Identity/reference ids, omission, scalar projection, a custom
representation, and deliberate inline output are all valid; without a choice, serialization fails rather than selecting a
universal representation. Normal serialization rejects a marked Template because values cannot preserve recipe actions.
An explicit type-level serializer is a complete opt-out and may serialize a Template or project public model data and
`KlumObjectSupport`, but never internal companion state.

### Diagnostics

`KlumJacksonImporter` contributes a construction path for its public operation and target type, augmented by an import
source and available Jackson path. The stable path grammar is shaped as
`$/Order.readRoot:jackson(config.yaml)/input(#/services/2/public)`: the method name is the operation verb, Jackson property
names and indices retain their external spelling, and the input suffix is an RFC 6901 JSON Pointer. During binding this
external path replaces overlapping generated field segments rather than duplicating them; lifecycle failures after binding
continue under the normal Builder path. `BreadcrumbCollector` remains internal runtime terminology; construction path is
the public term and does not promise provenance or lineage.

Syntax, mapping, I/O, custom-binding, and unexpected non-Klum binding failures cross the managed seam as exactly one new
`KlumModelException`. Its direct cause is the top-level original failure. The stable message shape is
`Jackson readRoot import of com.acme.Order failed: <concise cause> at <path>`; operation, target type, source/path grammar,
and the prefix are compatibility commitments, while Jackson-controlled cause wording is not. Syntax diagnostics include
line/column when available without embedding source excerpts or duplicating Jackson's reference chain.

An existing `KlumModelException` or other established `KlumException` from lifecycle, validation, schema, or visitor work
is preserved as the identical instance. Internal Jackson wrapping is avoided and, if unavoidable, unwrapped at the
importer seam. JVM `Error`s pass through. Null public arguments fail with `NullPointerException` naming the argument;
session, sealed-Builder, module-configuration, and construction-takeover failures use `KlumModelException`. Raw Jackson
operations retain their normal exception behavior.

### Jackson major-version boundary

The 4.0 adapter compiles against Jackson 2 and supports the resolved baseline 2.14.2 through the latest 2.x endpoint
validated by JSON-3. The semantic contract has been checked against Jackson 3.1, but Jackson 3 changes Java packages and
public descriptors for mapper, reader, parser, and tree types. One binary cannot expose those types for both majors.
A future Jackson 3 adapter must therefore be compiled separately against the same data-format-neutral lifecycle engine;
reflection, `Object`-typed public signatures, and multi-release-JAR tricks are not compatibility strategies.

## Compatibility

The importer API and the managed Builder-first mapping/lifecycle/customization rules are 4.x source, binary, and behavioral
compatibility commitments. This includes the four method descriptors, input factories, factory/reader capture and input
ownership rules, raw compatibility behavior, and diagnostic grammar above. KlumAST does not promise byte-for-byte output,
property ordering, formatting, a universal wire schema, import/export round trips, or Jackson-controlled cause text. Given
an unchanged Schema and mapper configuration, KlumAST does not intentionally change property mapping during 4.x without
treating it as a compatibility break. Schema Developers own compatibility with their external formats.

Breaking changes from pre-4.0 beta Jackson behavior are acceptable. Existing `Create.FromMap` remains historical current
API but is not the canonical foreign-format import seam; any deprecation or removal requires a separate compatibility
decision. Optional annotation-generated import creators are later Jackson-module convenience and must delegate to the
central importer rather than silently rerouting existing factories.

## Consequences

- Foreign YAML/JSON can join Klum Builder construction without requiring Schema Developers to know internal allocation.
- Completed models remain natural Jackson POJOs for downstream tools such as Helm.
- Import and export fixtures must be asymmetric; round-trip tests do not define compatibility.
- A documentary test must read one foreign YAML input, enrich it through one lifecycle, and write intentionally different
  enriched YAML. Ordered source composition remains outside the Jackson API.
- Documentation distinguishes Domain API Developer, Schema Developer, Client Developer, and Model Writer responsibilities.
- ADR 0007 and its JSON-1/JSON-2 implementation remain historical groundwork, but its persistence language is not the 4.0
  contract.

## Rejected alternatives

A Klum-owned envelope or reserved metadata property is rejected because the external Schema owns the document. A stable
Klum wire version plus producer version is rejected because no Klum wire format exists. Generic wire migrations are
rejected as a separate model-versioning concern. A core Map-copy import path is rejected as the canonical integration
because it cannot honor Jackson metadata. A dedicated export API, silent active-session routing, inline `LINK` ownership,
construction-taking annotations, a mode enum or overload matrix, Jackson-owned ordered composition, cross-major
reflection, and round-trip compatibility tests are rejected because they obscure lifecycle ownership, weaken the Java
API, duplicate core policy, or recreate the false persistence contract.
