# ADR 0007 implementation plan: Jackson configuration replay

This plan implements [ADR 0007](../adr/0007-jackson-configuration-replay.md) for canonical issues
[#428](https://github.com/klum-dsl/klum-ast/issues/428) and [#251](https://github.com/klum-dsl/klum-ast/issues/251).

## Current behavior and failure

`KlumDeserializer` reads an object into `Map<String,Object>` and calls `FactoryHelper.createFromSerializedState`. That path
replays all serializable fields through Builder copy semantics, so renamed/aliased properties are not resolved through
Jackson metadata and lifecycle-derived values can be restored then transformed again. `KlumAnnotationIntrospector` ignores
framework fields but does not define the full writable-configuration contract. `LINK` identity behavior is not explicit.

## Affected seams

- `klum-ast-jackson`: property discovery, token binding, identity/reference handling, Template rejection, errors.
- `klum-ast-runtime`: controlled Builder population hooks and deterministic PostCreate/bind/PostApply ordering.
- tests: `JsonExportSpec` plus focused Java/Jackson customization fixtures.
- wiki: persistence versus recomputation, supported customization, migration examples.

## Tracer-bullet slices

### [JSON-1 — Property-aware configuration replay](https://github.com/klum-dsl/klum-ast/issues/439)

For one DSL Object with a renamed scalar, alias, initializer, derived lifecycle value, and owned child, resolve
`BeanPropertyDefinition`s, allocate one Builder graph, bind only present public configuration inputs between PostCreate and
PostApply, and run the lifecycle once. Prove missing/present/null/empty replacement semantics, unknown-property policy, and
Template rejection. This resolves #251's core renamed-property failure.

### [JSON-2 — Identity-safe LINK and advanced property customization](https://github.com/klum-dsl/klum-ast/issues/440)

Add explicit identity/custom property handling for backward and forward `LINK` references, reject inline LINK objects and
output without a reference strategy, and verify mixins, views, naming strategies, formats, Simple Value custom codecs, and
polymorphic DSL subtypes. Verify forbidden construction overrides do not bypass Klum, while an explicit type-level custom
deserializer opts out cleanly.

### JSON-3 — Migration and compatibility closure

Remove provisional raw-state semantics, expand round-trip and non-idempotent lifecycle coverage, document configuration
replay and the breaking JSON boundary, update migration navigation and `CHANGES.md`, and run Jackson tests under Groovy
3/4/5 plus the aggregate build.

## Compatibility

Existing serialized JSON is not guaranteed to deserialize identically. There is no per-instance provenance migration.
Templates and inline LINK graphs fail loudly. A type-level custom deserializer remains the explicit escape hatch.

## Acceptance map

| ADR contract | Slice |
|---|---|
| writable Builder surface, lifecycle once, authoritative present values | JSON-1 |
| Jackson names/aliases/access/unknown policy and owned recursion | JSON-1 |
| LINK identity including forward references | JSON-2 |
| supported/unsupported customization boundary | JSON-2 |
| migration, wiki, CHANGES, compatibility lanes | JSON-3 |

## Risks

Jackson object identity and forward-reference APIs are stateful and easy to bypass by premature tree conversion. The
implementation should stream or buffer against resolved properties without reverting to an untyped raw Map. Polymorphic
owned values must still create Builders in the same Construction session.
