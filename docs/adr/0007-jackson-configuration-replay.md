# Jackson deserialization as configuration replay

Date: 2026-07-14

Status: Accepted

Tracking issues:

- [#428 — Decide deserialization lifecycle semantics](https://github.com/klum-dsl/klum-ast/issues/428)
- [#251 — Jackson deserialization does not work with renamed properties](https://github.com/klum-dsl/klum-ast/issues/251)

Implementation status: Planned. The current deserializer binds a raw Map and restores all serializable fields. See the
[implementation plan](../implementation/adr-0007-jackson-configuration-replay.md).

Parent decisions:

- [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)
- [ADR 0004 — AsBuilder composition](0004-asbuilder-composition-protocol.md)

## Context

Restoring a completed object snapshot and then rerunning mutating lifecycle callbacks can double-apply non-idempotent
derived logic. Skipping the lifecycle would bypass the invariants shared by factory construction. The current raw-Map
binding also ignores Jackson property definitions and makes renamed properties brittle.

DSL Objects distinguish writable configuration from recomputed lifecycle output, owned composition, aggregation `LINK`s,
and framework-managed fields. Deserialization needs to preserve that domain boundary while still respecting ordinary
Jackson property customization where it does not replace Klum construction.

## Decision

Jackson persistence is configuration replay, not completed-state snapshot restoration. Persist writable configuration
inputs and reconstruct a fresh Builder graph, then run the normal lifecycle once through materialization, validation, and
verification. Lifecycle-derived values are recomputed and are not Builder inputs.

The persistence schema follows the public Builder configuration surface statically, not per-instance provenance:

- public configurable fields and owned relationships are persisted;
- `PROTECTED`, `IGNORED`, and `BUILDER` fields are recomputed or internal and are not input;
- Owner, Role, and synthetic fields are never input;
- configurable `TRANSIENT` values retain Jackson's normal inclusion/default behavior;
- lifecycle-derived output may be exposed as Jackson read-only output.

A property cannot safely be both a persisted input and a non-idempotently transformed output. Schemas must split raw and
derived properties, make the transformation idempotent, or supply explicit custom handling.

Binding order is deterministic:

1. allocate the root and owned child Builders and apply source initializers;
2. run `PostCreate`;
3. bind present JSON properties authoritatively;
4. run `PostApply`;
5. run graph phases, materialization, validation, and verification.

A present scalar replaces its value, present `null` clears it where legal, and present containers replace the prior
container, with empty containers clearing it. A missing property leaves initializer, default, auto-create, or lifecycle
behavior intact. Deserialization does not use ambient `TemplateManager`, `@Overwrite` merge policy, or
`copyFrom(serializedState)` semantics.

The implementation resolves Jackson `BeanPropertyDefinition` and associated metadata instead of sending raw Map keys to
Builder methods. It honors naming strategies, `@JsonProperty`, `@JsonAlias`, ignore/access rules, mixins, views, inclusion,
formats, unknown-property policy, custom serializers/deserializers for Simple Values, polymorphic type information for the
concrete DSL subtype, and typed reference handling for `LINK` values.

It does not honor `@JsonCreator`, direct model setters, Jackson Builder instantiation, custom owned-relationship
deserializers that produce completed models, or generic managed/back references as a replacement for Klum ownership. An
explicit type-level custom deserializer opts that type out of Klum handling and wins.

Owned nested DSL values recursively populate Builders in the same Construction session. A `LINK` is a reference only and
is never serialized as inline owned configuration. It requires explicit `@JsonIdentityInfo`/always-as-id behavior or a
custom property serializer/deserializer. Input resolves an existing completed object or Builder identity in the same
session, including forward references. Inline object input at a `LINK` property throws `JsonMappingException`; non-null
output without an identity strategy fails rather than silently embedding or omitting it. Owner is always omitted and
recomputed.

Marked Templates are rejected by Jackson because value serialization cannot preserve their executable recipe contract.
The existing brittle JSON format is not a compatibility constraint; breaking changes are acceptable for this pre-stable
integration.

## Consequences

- Round trips reproduce configured intent plus current lifecycle rules, not every old derived bit.
- Non-idempotent lifecycle callbacks run once per deserialization.
- Renamed and aliased properties become part of the supported mapping contract, resolving #251 within this work.
- Ownership and `LINK` identity remain explicit instead of being inferred from arbitrary JSON nesting.
- Advanced Jackson customization is supported at property/value seams but cannot replace Klum construction invisibly.
- Existing JSON payloads may require migration.

## Rejected alternatives

Persisting all fields and rerunning lifecycle callbacks is rejected because it can double-transform derived state.
Persisting all fields while skipping callbacks is rejected because it bypasses construction invariants and validation
ordering. Recording per-instance configuration provenance is rejected as a second mutable graph with serialization and
memory cost. Raw Map copying is rejected because it ignores Jackson's resolved property model. Silently serializing a
Template or inline `LINK` target is rejected because it loses essential semantics.
