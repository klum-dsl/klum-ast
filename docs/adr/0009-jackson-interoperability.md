# Jackson interoperability through explicit Builder import

Date: 2026-07-16

Status: Accepted

Tracking issues:

- [#428 — Jackson interoperability for immutable DSL Objects](https://github.com/klum-dsl/klum-ast/issues/428)
- [#447 — Jackson wire-format metadata decision](https://github.com/klum-dsl/klum-ast/issues/447)
- [#251 — resolved Jackson property names](https://github.com/klum-dsl/klum-ast/issues/251)

Implementation status: JSON-1 property-aware Builder binding is implemented by
[#439](https://github.com/klum-dsl/klum-ast/issues/439), and JSON-2 identity/customization groundwork is implemented by
[#440](https://github.com/klum-dsl/klum-ast/issues/440). The explicit importer and interoperability compatibility closure
remain planned in the [implementation plan](../implementation/adr-0009-jackson-interoperability.md).

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

The public import seam is a data-format-neutral `KlumJacksonImporter` using a caller-supplied configured mapper or reader.
It exposes four explicit lifecycle modes; exact Java/Groovy signatures and input overloads are finalized in the importer
tracer bullet before implementation:

1. read a root Builder, bind one or more ordered inputs, run one lifecycle, and return the completed DSL Object;
2. read a marked value-only Template without running lifecycle processing;
3. create and bind a new Builder in an active Construction session for owned composition;
4. apply an input to an existing unsealed Builder.

The caller selects the mode. The importer does not route implicitly based on ambient lifecycle state. Applying to a
completed model fails. Multiple explicit inputs apply in call order; missing values preserve current Builder state, while
present values use normal Jackson null, merge, and replacement semantics. An explicitly mapped Collection may therefore
be set to `null`.

A standalone raw `ObjectMapper.readValue(DslType)` may still create an independent root but is discouraged in favor of the
importer. A raw DSL read inside an active Construction session fails with guidance to use the in-session importer; ordinary
non-DSL reads remain valid. Top-level arrays, Maps, and streams remain ordinary Jackson containers and do not imply a
shared Klum lifecycle. Format-specific YAML multi-document iteration belongs in an optional convenience layer; the core
importer accepts repeated explicit documents, parser input, trees, and in-memory Maps.

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
result without an additional Klum lifecycle. Documentation should strongly discourage that advanced escape hatch in favor
of converters or Builder staging.

`LINK` input never interprets an inline object as owned composition. It requires explicit identity/reference handling,
property conversion, or later lifecycle resolution and otherwise fails. Richer same-session external resolution is not a
4.0 requirement.

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

`KlumJacksonImporter` contributes its source/import operation to the current construction breadcrumb. Syntax, mapping,
and I/O failures cross the managed import boundary as `KlumModelException`, preserving the original Jackson or I/O
exception as the cause. Available Jackson path segments augment the Klum breadcrumb. Existing lifecycle
`KlumModelException`s are preserved rather than double-wrapped. Raw non-DSL Jackson operations retain ordinary Jackson
exception behavior.

## Compatibility

The importer API and the managed Builder-first mapping/lifecycle/customization rules are 4.x source, binary, and behavioral
compatibility commitments. KlumAST does not promise byte-for-byte output, property ordering, formatting, a universal wire
schema, or import/export round trips. Given an unchanged Schema and mapper configuration, KlumAST does not intentionally
change property mapping during 4.x without treating it as a compatibility break. Schema Developers own compatibility with
their external formats.

Breaking changes from pre-4.0 beta Jackson behavior are acceptable. Existing `Create.FromMap` remains historical current
API but is not the canonical foreign-format import seam; any deprecation or removal requires a separate compatibility
decision. Optional annotation-generated import creators are later Jackson-module convenience and must delegate to the
central importer rather than silently rerouting existing factories.

## Consequences

- Foreign YAML/JSON can join Klum Builder construction without requiring Schema Developers to know internal allocation.
- Completed models remain natural Jackson POJOs for downstream tools such as Helm.
- Import and export fixtures must be asymmetric; round-trip tests do not define compatibility.
- A documentary test must read foreign YAML, enrich it through one lifecycle, and write intentionally different enriched
  YAML. Multiple explicit inputs must be demonstrated without requiring YAML multi-document support in core.
- Documentation distinguishes Domain API Developer, Schema Developer, Client Developer, and Model Writer responsibilities.
- ADR 0007 and its JSON-1/JSON-2 implementation remain historical groundwork, but its persistence language is not the 4.0
  contract.

## Rejected alternatives

A Klum-owned envelope or reserved metadata property is rejected because the external Schema owns the document. A stable
Klum wire version plus producer version is rejected because no Klum wire format exists. Generic wire migrations are
rejected as a separate model-versioning concern. A core Map-copy import path is rejected as the canonical integration
because it cannot honor Jackson metadata. A dedicated export API, silent active-session routing, inline `LINK` input,
construction-taking annotations, and round-trip compatibility tests are rejected because they obscure lifecycle ownership
or recreate the false persistence contract.
