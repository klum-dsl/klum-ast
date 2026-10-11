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

This protocol is relevant to [Converters](Converters.md), [Factory Classes](Factory-Classes.md), and [Alternatives Syntax](Alternatives-Syntax.md). Its architectural boundary
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

See [Jackson Integration](Jackson-Integration.md) for the supported API and foreign-format workflow.

## Builder-First Materialization

Before `INSTANTIATE`, closure receivers, child values, mutators, and lifecycle callbacks through `POST_TREE` are Builders.
`INSTANTIATE` materializes the complete composition graph, including cycles; validation and later phases receive completed
DSL Objects. Builders and completed objects are intentionally different states, so a nested root factory cannot join an
active parent lifecycle and a completed object cannot become new owned composition.

Generated `$klum$asBuilder$...` methods are JVM linkage details for projected producers, not a client API. Generated
`Foo_DSL.Builder` interfaces are the supported public construction capability. See [Builder First Migration](Builder-First-Migration.md) for the
actionable migration checklist and [ADR 0003](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0003-builder-first-materialization.md)
for the design decision.

## Builder-backed `@LinkTo` Providers

Schemas that implement their own provider policy may inspect
[owning Schema declarations](Completed-Object-Support.md#owning-schema-declarations) through Builder and completed Object
Structure. The [copied-provider example](Completed-Object-Support.md#copied-providers-and-absent-authority) handles absent
authority explicitly. These metadata queries preserve existing `@LinkTo`/`@LinkSource` behavior; binding, default,
ambiguity, and fallback decisions belong to the consuming Schema.

An explicit `@LinkTo` provider may follow the owner graph to another active Builder. Its `field` name selects configured
Builder storage before materialization. If no storage field exists, ordinary property access provides the getter fallback.

Completed `LINK` providers appear during construction as sealed Builder wrappers. Their domain-property reads depend on
the Builder's state, not the caller or lifecycle phase:

| State | Read semantics |
| --- | --- |
| Active Builder | Existing construction storage and property/getter behavior. |
| Builder sealed by normal materialization | Existing construction semantics remain intact. |
| Wrapper sealed to an existing completed Model | The completed Model supplies domain-property values. |

The same wrapper can be read from a configuration closure, a custom Builder method, `@AutoLink`, or a later owned
Builder callback such as `@PostTree`. Domain fields expose the completed Model's values rather than the wrapper's empty
storage. Dynamic property access also reads computed/getter-only Model properties when no existing Builder property
owns that name. Existing Builder properties retain their meaning, including infrastructure such as `modelType` and
`completedModel`.

Model-only getters are not added to the generated Builder contract, so direct statically checked Builder access to
those names remains unsupported. Use `@LinkTo(field = "alias")` or a dynamic property reader called from the Builder
code to read such a Model getter. This typed-access boundary is independent of the lifecycle phase.

Generated typed relationship getters still return the relationship's public Builder type during construction;
forwarding a dynamic read does not turn that typed contract into a completed-Model getter.

The completed `LINK` provider remains aggregation: its wrapper does not join the consuming composition lifecycle or
re-own the completed Model. An owned consumer callback can reach the wrapper through its Owner graph; `@Validate` and
later Model phases instead read completed Models. Generated setters, field configurators, and configuration entry
points retain their existing sealed-state guards. Forwarding reads does not route writes to the completed Model.

Completed-model and Map providers retain ordinary property access. A null provider or null completed value leaves the
link unset; a missing Map key also leaves it unset. An unknown object property retains Groovy's missing-property
diagnostic, including when an imperative lifecycle read fails through the lifecycle exception.

This is the existing provider-read contract from [#841](https://github.com/klum-dsl/klum-ast/issues/841).
A broader sealed-Builder dispatch and mutation policy is a separate investigation in
[#850](https://github.com/klum-dsl/klum-ast/issues/850); no unified policy is implied here.

(See: `LinkToBuilderFieldTest#'explicit provider field links through the owner environment during construction'`,
`LinkToBuilderFieldTest#'sealed LINK provider exposes a getter-only completed Model property'`, and
`LinkToBuilderFieldTest#'imperative auto link rejects an unknown property on a sealed LINK provider'`,
`LinkToBuilderFieldTest#'completed LINK reads depend on wrapper state across configuration Builder methods and PostTree'`,
and `LinkToBuilderFieldTest#'completed LINK property forwarding preserves supported sealed mutation rejection'`.)

```groovy
@DSL class OrderApplication {
    OrderEnvironment environment
    OrderKafka kafka
}
@DSL class OrderEnvironment {
    MessagingFacts messaging
}
@DSL class OrderKafka {
    @Owner OrderApplication application
    @Field(FieldType.LINK)
    @LinkTo(provider = { application.environment }, field = 'messaging')
    MessagingFacts facts
}
@Immutable class MessagingFacts {
    String topic
}

// AUTO_LINK runs after the complete configuration, even with kafka configured first.
def messagingFacts = new MessagingFacts('orders')
def order = OrderApplication.Create.With {
    kafka()
    environment { messaging messagingFacts }
}
assert order.kafka.facts.is(messagingFacts)
```
