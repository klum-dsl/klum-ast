# Layer 3

Layer 3 is the API–Schema–Model modeling pattern. It separates a stable, consumer-facing Domain API from the concrete
Schema that makes models convenient to author. A Domain API Developer normally defines the API before the Schema
Developer realizes it; regardless of creation order, the API constrains the Schema. Generic Client Developers depend only
on that API, and Model Writers create configured Model instances through the Schema's construction DSL.

Layer 3 is not a package layout, Gradle convention, or Java-module boundary. There is no `@Layer3` marker. Separate `api`,
`schema`, and `model` projects are a useful way to enforce the dependencies, but colocated sources can follow the same
pattern when their public dependencies preserve the boundary.

## Recognizing a Layer 3 design

A design uses Layer 3 when all of these statements are true:

1. A distinct Domain API defines the completed model contract that generic clients consume.
2. Concrete Schema types depend on and realize that API; the API does not depend on the Schema.
3. Model configuration depends on the Schema's generated construction surface.
4. A generic client can compile against the Domain API without Schema-only types in its signatures or dependencies.

The usual dependency direction is:

```text
Domain API <--- Schema <--- Model configuration
     ^
     |
generic client
```

A Schema-specific client may additionally depend on the Schema. That does not invalidate the Layer 3 design; it is a
different consumer of the same completed Model and does not replace the API-only generic client boundary.

By contrast, a design is **direct-schema** when Schema types are themselves the consumer-facing contract. In that shape,
the Schema Developer also assumes the Domain API Developer role. Splitting one direct-schema design across packages or
using `@Cluster` does not turn it into Layer 3.

## The four roles

One person or team may hold several roles. The roles describe responsibility, not required organizational boundaries.

| Role | Owns | Depends on |
| --- | --- | --- |
| Domain API Developer | Stable completed-model types and operations that clients compile against | KlumAST's documented Schema-authoring contract, when the API types are DSL Objects |
| Schema Developer | Concrete DSL Object types, relationships, lifecycle behavior, validation, and external mappings | The Domain API and KlumAST Schema APIs |
| Model Writer | Concrete configuration in Groovy, structured inputs, Templates, or combinations of them | The Schema's generated construction surface |
| Client Developer | Integrations that read completed models | The Domain API for a generic client; optionally the Schema for a deliberately Schema-specific client |

## End-to-end example

Consider an environment model consumed by both a generic deployment pipeline and application-specific tests.

### Domain API Developer: define the consumer contract

The Domain API describes completed environments and applications without naming any concrete deployment:

```groovy
@DSL
abstract class Environment {
    @Key String name

    @Cluster(bounded = true)
    Map<String, Application> applications
}

@DSL
abstract class Application {
    String displayName
}

@DSL
abstract class Database {
    String url
}
```

`Environment.applications` is a generic projection. A Client Developer can read it without knowing which concrete
applications a particular Schema supplies.

### Schema Developer: realize the contract

The Schema adds named, domain-specific types and fields:

```groovy
@DSL
class CustomerEnvironment extends Environment {
    Shipping shipping
    Billing billing
}

@DSL
class Shipping extends Application {
    ShippingDatabase database
    ShippingFrontend frontend
}

@DSL
class Billing extends Application {
    BillingDatabase database
    BillingService service
}

@DSL class ShippingDatabase extends Database { }
@DSL class BillingDatabase extends Database { }
@DSL class ShippingFrontend { int replicas; boolean ssl }
@DSL class BillingService { String endpoint }
```

The concrete fields make the authoring DSL discoverable and typo-safe. The API's `@Cluster` projection exposes those
fields as `Map<String, Application>` to generic clients.

### Model Writer: configure one Model

Because the Cluster is bounded, matching application methods are available through the named `applications` factory:

```groovy
def production = CustomerEnvironment.Create.With('production') {
    applications {
        shipping {
            displayName 'Shipping'
            database { url 'jdbc:postgresql://shipping/prod' }
            frontend {
                replicas 3
                ssl true
            }
        }
        billing {
            displayName 'Billing'
            database { url 'jdbc:postgresql://billing/prod' }
            service { endpoint 'https://billing.example.test' }
        }
    }
}
```

The Model Writer depends on `CustomerEnvironment` and its generated construction API. The resulting object is completed
and read-only when the root factory returns it.

### Client Developer: consume at the intended boundary

A generic Java client depends only on the Domain API:

```java
public void deploy(Environment environment) {
    environment.getApplications().forEach((name, application) ->
            deployApplication(name, application));
}
```

A Schema-specific Groovy client can intentionally use the richer concrete type:

```groovy
void verifyShipping(CustomerEnvironment environment) {
    assert environment.shipping.frontend.ssl
    assert environment.shipping.database.url.startsWith('jdbc:postgresql:')
}
```

The first client is portable across Schemas that implement the Domain API. The second is coupled to this Schema and may
use its named fields directly. Both consume the same completed Model; neither constructs or mutates it through a Builder.

## Cluster projection

`@Cluster` is specialized support for the Layer 3 pattern. It projects matching concrete fields from a Schema subtype into
a generic `Map` declared by an API type. Map keys are the matching field names, and values are the field values. The
annotation may also filter fields by a runtime-retained annotation and can project matching collections.

The generated construction surface includes a Cluster factory named after the projected property. By default, using that
factory is optional. With `@Cluster(bounded = true)`, matching field methods are `protected` on the generated Builder and
remain available through the Cluster factory, as `applications { shipping { ... } }` demonstrates above. The bounded
setting may also be placed on a class, superclass, or package to apply to its Cluster fields.

`@Cluster` can annotate a field or a getter method. Prefer the field form for new Schemas. A getter may be abstract or have
an empty or `null` body when source tooling requires one.

Using `@AutoCreate` on a Cluster applies ordinary automatic creation to every matching field. That interaction is useful
in Layer 3 Schemas, but automatic creation itself remains a general KlumAST capability.

## Public surfaces and boundaries

Layer 3 adds no separate runtime extension SPI. Its supported surfaces are the same bounded contracts used elsewhere in
KlumAST:

- Domain API types and their completed values are the client contract.
- Public Schema annotations, including `@Cluster`, are the Schema-authoring contract.
- Generated `Foo_DSL` factory, Builder, collection-factory, and Cluster-factory interfaces may be named as parameter,
  return, or receiver types in client and extension signatures. Do not construct, implement, or subclass them; their
  generated implementations remain hidden.
- Use [Completed Object Support](Completed-Object-Support.md) for supported paths, composition traversal, and stored
  validation results. Internal companions, proxies, Cluster helpers, reflection utilities, and types under `internal`
  packages are not client or extension seams.

The public package `com.blackbuild.klum.ast.layer3` contains Schema annotations for historical and functional grouping.
Package membership does not classify an annotation as Layer-3-only and does not mark a design as Layer 3. In particular,
automatic creation and linking, ownership, roles, defaults, lifecycle callbacks, validation, and completed-model traversal
all work in direct-schema designs too. See [Basics](Basics.md), [Default Values](Default-Values.md),
[Model Phases](Model-Phases.md), [Validation](Validation.md), and
[Completed Object Support](Completed-Object-Support.md) for those general capabilities.

A broader inventory of possible third-party extension mechanisms remains a separate question in
[#453](https://github.com/klum-dsl/klum-ast/issues/453). Do not treat public visibility or a Layer 3 example as a promise of
an additional SPI.

## Boundaries that remain explicit

Layer 3 does not imply that a concrete keyed child receives its key from the Schema field that contains it. Cluster map
keys are field names, but changing the child's `@Key` value automatically is a separate behavior question tracked by
[#356](https://github.com/klum-dsl/klum-ast/issues/356). Until that issue is decided and implemented, configure child keys
through existing supported Schema and Model mechanisms; do not infer fixed-key behavior from this pattern.

Choose Layer 3 for an actual stable consumer boundary, not as insurance for a hypothetical future client. Choose
direct-schema when the same team owns the Schema and its consumers and Schema types are the appropriate public contract.
This choice is independent of [domain-first](Domain-First-Modeling.md) versus
[target-contract](Target-Contract-Modeling.md) modeling.
