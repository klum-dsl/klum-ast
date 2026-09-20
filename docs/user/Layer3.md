# Layer 3

Layer 3 is the API–Schema–Model pattern in which concrete Schema fields are projected through a stable Domain API by
`@Cluster`. The Domain API gives generic clients a useful contract without exposing Schema-specific types, while the
Schema gives Model Writers a precise, discoverable language for one concrete domain.

`@Cluster` is the defining KlumAST feature of this pattern. Inheritance, separate projects, or an extra API abstraction
alone do not make a design Layer 3.

## Recognizing a Layer 3 design

A design uses Layer 3 when all of these statements are true:

1. Distinct abstract Domain API DSL classes define the completed-model contract.
2. Concrete Schema classes subclass and specialize those API classes; the API does not depend on the Schema.
3. Model configuration uses the Schema's generated construction surface.
4. At least one `@Cluster` projects concrete Schema members through the Domain API.
5. A meaningful generic client can compile against the Domain API without Schema-only types in its signatures or
   dependencies.

The dependency direction is:

```text
Domain API <--- Schema <--- Model configuration
     ^
     |
generic client
```

A Schema-specific client may additionally depend on the Schema. This does not invalidate the pattern; it is a second
consumer of the same completed Model and does not replace the generic API-only boundary.

Layer 3 is not a package layout, Gradle convention, Java-module boundary, or organizational arrangement. Separate `api`,
`schema`, and `model` projects can enforce the dependencies, but colocated sources can preserve the same architecture.
There is no `@Layer3` marker.

Abstract classes are the supported Domain API form. DSL interfaces currently cover only a subset of Schema features and
are not a Layer 3 API alternative. See [#753](https://github.com/klum-dsl/klum-ast/issues/753) for the separate interface
projection question.

## Discovery order is not architecture

API-first is the normal workflow: define a useful generic contract, then realize it with a Schema. Schema-first and even
Model-first exploration are also valid ways to discover that contract. For example, a team may first design a pleasant
domain Model, derive a Schema from it, and later extract a stable API.

These are development workflows, not Layer 3 variants. The resulting architecture is Layer 3 only when it has the
dependency direction and Cluster projection above.

## The four roles

One person or team may hold several roles. The roles describe responsibility, not required organizational boundaries.

| Role | Owns | Depends on |
| --- | --- | --- |
| Domain API Developer | Abstract, stable completed-model types and operations for generic clients | KlumAST's documented Schema-authoring contract |
| Schema Developer | Concrete DSL Object types, relationships, lifecycle behavior, validation, and mappings | The Domain API and KlumAST Schema APIs |
| Model Writer | Concrete configuration in Groovy, structured inputs, Templates, or combinations of them | The Schema's generated construction surface |
| Client Developer | Integrations that read completed models | The Domain API for a generic client; optionally the Schema for a Schema-specific client |

## End-to-end environment example

Consider an environment model consumed by both a generic deployment tool and application-specific tests. The Domain API
defines the generic language; the Schema defines more specific nouns and verbs in that language.

### Domain API Developer: define the generic contract

The Domain API names environments and applications without knowing which applications a particular customer runs:

```groovy
@DSL
abstract class Environment {
    @Key String name

    @Cluster(bounded = true, fixedKeys = true)
    abstract Map<String, Application> getApplications()
}

@DSL
abstract class Application {
    @Key String name
    String displayName
}

@DSL
abstract class Database {
    String url
}
```

The `applications` Cluster is the architectural projection. Generic clients can read every concrete application through
`Map<String, Application>` without knowing any Schema class.

`bounded = true` is optional, but recommended when the named Cluster factory should be the clear construction boundary.
`fixedKeys = true` makes each selected child inherit its key from the concrete Schema field name.

### Schema Developer: specialize the language

The Schema supplies named, domain-specific applications and their details:

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

The concrete `shipping` and `billing` fields need no key annotations. The Domain API owns the fixed-key convention through
its Cluster, and the Schema merely supplies the selected relationships.

### Model Writer: configure one Model

The bounded Cluster makes the `applications` factory the intended home for its application methods:

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

assert production.shipping.name == 'shipping'
assert production.billing.name == 'billing'
```

The Model Writer depends on `CustomerEnvironment` and its generated construction API. The returned object is completed
and read-only.

### Client Developer: consume the intended boundary

A generic Java deployer—comparable to a Helm-style consumer—depends only on the Domain API:

```java
public void deploy(Environment environment) {
    environment.getApplications().forEach((name, application) ->
            deployApplication(name, application));
}
```

A Schema-specific Groovy client can intentionally use the richer concrete language:

```groovy
void verifyShipping(CustomerEnvironment environment) {
    assert environment.shipping.frontend.ssl
    assert environment.shipping.database.url.startsWith('jdbc:postgresql:')
}
```

Schema tests and application-specific integrations are valid Schema consumers. The first client remains portable across
Schema realizations; the second deliberately does not. Both consume the same completed Model.

When several Schema realizations must prove the same generic Domain API behavior, keep the reusable contract with the
Domain API and let each Schema opt in explicitly. [Testing Models and Schemas](Testing-Models-and-Schemas.md#reuse-a-domain-api-contract-across-schema-realizations)
shows the application-owned fixture wiring and its Groovy/Spock alignment boundary.

## Fixed Cluster keys

`@Cluster(fixedKeys = true)` is a Cluster-level quality-of-life convention for direct, single, keyed DSL Object
relationships. It uses the selected Schema member name as the child key and removes the key parameter from its generated
creator. The policy belongs to the Domain API Cluster, so concrete Schema fields stay annotation-free.

Before this option, every selected field had to repeat the equivalent policy:

```groovy
@DSL
class CustomerEnvironment extends Environment {
    @Field(key = Field.FieldName) Shipping shipping
    @Field(key = Field.FieldName) Billing billing
}
```

Do not combine those field annotations with `fixedKeys = true`. Selected unkeyed fields, collections or maps, and fields
with an explicit `@Field(key = ...)` are rejected at Schema compilation. Unselected fields retain their ordinary creator
methods. The option changes neither ownership nor lifecycle behavior and can also be useful for Clusters outside a Layer
3 architecture.

## Automatic creation and linking

The environment model can also create infrastructure automatically and connect it to an application relationship:

```groovy
@DSL
class Shipping extends Application {
    ShippingDatabase database
    ShippingFrontend frontend

    @AutoCreate MonitoringService monitoring
}

@DSL
class MonitoringService {
    @Owner Shipping application
    @Field(FieldType.LINK) ShippingDatabase database

    @AutoLink
    void connectDatabase() {
        database ?= application.database
    }
}
```

The explicit `LINK` relationship reuses the database already owned by `Shipping`, while `?=` preserves any target set by
the Model Writer. `@AutoCreate` and `@AutoLink` were initially useful drivers for environment models like this one, but
they are not Layer 3 features. They work in direct-schema designs as well. See [Basics](Basics.md),
[Model Phases](Model-Phases.md), and [Completed Object Support](Completed-Object-Support.md) for their ownership,
relationship, and lifecycle rules.

## What Cluster supplies

For a Layer 3 Domain API property such as `applications`, `@Cluster` supplies the two useful projection surfaces:

- the completed-model getter, such as `getApplications()`, which projects matching concrete fields; and
- the generated named Cluster factory, such as `applications { ... }`, which groups the matching construction methods.

By default, the factory is optional. `bounded = true` makes matching methods `protected` on the general Builder while
retaining them inside the named factory. The annotation can filter fields by a runtime-retained annotation and can also
project matching collections.

These generated getter and factory contracts are the Layer 3-specific benefit. The general generated Builder API and
[`KlumObjectSupport`](Completed-Object-Support.md) remain ordinary KlumAST surfaces. Runtime `ClusterModel` helpers,
compiler transformations, companions, proxies, and other `internal` types are implementation details, not a Layer 3 SPI.

## Counterexamples

### Direct Schema

If concrete environment types are themselves the consumer contract, the Schema Developer also owns the Domain API
responsibility:

```groovy
@DSL
class DirectEnvironment {
    ShippingConfiguration shipping
    BillingConfiguration billing
}

@DSL class ShippingConfiguration { String databaseUrl }
@DSL class BillingConfiguration { String endpoint }
```

This can be the right design, but it is direct-schema modeling. Splitting it across packages or projects does not make it
Layer 3 because it has no distinct abstract API and no Cluster projection.

### Interface-only boundary

An interface can help communicate a border:

```groovy
@DSL
interface EnvironmentApi {
    Map<String, Application> getApplications()
}

@DSL
class CustomerEnvironment implements EnvironmentApi {
    Shipping shipping
    Billing billing
}
```

This is not the supported Layer 3 pattern: it has no Cluster projection, and DSL interfaces are not currently full Schema
citizens. Abstract API base classes remain mandatory for Layer 3 documentation until interface support matures.

## Choosing the pattern

Choose Layer 3 when a real generic consumer needs a stable domain language independent of concrete Schema types. If no
meaningful API-only consumer exists, the extra API is ceremonial and should not be presented as Layer 3. Choose
direct-schema when the Schema types themselves are the appropriate consumer contract.

This choice is independent of [domain-first](Domain-First-Modeling.md) versus
[target-contract](Target-Contract-Modeling.md) discovery. There are no separately named Layer 3 variants for different
source layouts, role assignments, or discovery orders.
