# Behind the Curtain

Most pages describe the stable authoring API first. This page collects the implementation mechanics that explain why
some advanced calls have lifecycle boundaries or targeted diagnostics. You normally do not need these details to write a
Schema or Model.

## Builder Projection for Custom Producers

During construction, owned relationships hold Builders rather than completed DSL Objects. A source-visible custom factory
or converter that creates a DSL Object through a recognizable generated factory path can therefore be projected to a
Builder-producing path when it is called inside an owning relationship. The original method remains a normal root factory
when called directly.

The projection preserves the producer's outer container, order, comparator, duplicates, and map keys. Recursive
source-visible calls are projected in the same way. Opaque or precompiled producers that return a completed model without
an explicit `KlumBuilder<T>` contract cannot safely join the active Construction session, so KlumAST omits them from the
generated relationship API and reports focused migration guidance. Use the generated child method, expose a concrete
`KlumBuilder<T>` result, or compile the producer source with the Schema.

This protocol is relevant to [[Converters]], [[Factory Classes]], and [[Alternatives Syntax]]. Its architectural boundary
is recorded in [ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md).

## Managed Jackson Import Mechanics

`KlumJacksonImporter` reads foreign input into generated Builders, never partly initialized completed objects. Root import
allocates the root and owned Builders, runs `PostCreate`, binds the input, runs `PostApply`, then completes the normal
graph phases once. A missing input property leaves the current Builder value alone; a present value follows the caller's
Jackson null, merge, and replacement configuration.

The importer captures a caller-configured `ObjectReader` without registering modules or changing mapper configuration.
Each operation consumes one `KlumJacksonInput`; parsers remain caller-owned and open. Templates, `@Overwrite`, and
`copyFrom` do not participate in Jackson binding. This boundary is why managed import is interoperable with foreign JSON
and YAML but is not Klum persistence or document layering.

See [[Jackson Integration]] for the supported API and foreign-format workflow.

## Builder-First Materialization

Before `INSTANTIATE`, closure receivers, child values, mutators, and lifecycle callbacks through `POST_TREE` are Builders.
`INSTANTIATE` materializes the complete composition graph, including cycles; validation and later phases receive completed
DSL Objects. Builders and completed objects are intentionally different states, so a nested root factory cannot join an
active parent lifecycle and a completed object cannot become new owned composition.

Generated `$klum$asBuilder$...` methods are JVM linkage details for projected producers, not a client API. Generated
`Foo_DSL.Builder` interfaces are the supported public construction capability. See [[Builder First Migration]] for the
actionable migration checklist and [ADR 0003](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0003-builder-first-materialization.md)
for the design decision.
