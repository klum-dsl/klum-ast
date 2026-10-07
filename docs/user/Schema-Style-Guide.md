# Schema Style Guide

An idiomatic KlumAST Schema makes the model's meaning easy to see: what can be configured, how its parts relate, and
what makes a configuration valid. The advice here is about readability and design, not additional compiler restrictions.
Use the documentation matching your KlumAST release.

## Model the domain, not the generated DSL

Name types and members after the concepts a Model Writer works with. A deployment has an image and a capacity;
KlumAST supplies the Builders, factories, and configuration methods that make those concepts usable as a DSL. There is
no need to represent that generated machinery as extra domain state or recreate it in handwritten helper types.

Prefer descriptive names for nested helpers, such as `CapacityChecks`. Names resembling generated implementation types
make a Schema harder to read and can collide with types KlumAST generates. Actual collisions are diagnosed by the
compiler; leading underscores alone are not prohibited. See [the exact collision rules](Migration.md#generated-inner-name-collisions-41)
when a diagnostic asks you to rename a type.

## Keep the structure obvious

Fields describe domain state, and their types make relationships visible. In this example, a `Deployment` has a
`Capacity`, rather than a map of loosely related settings or a helper that hides the relationship:

```groovy
package example.deployments

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate

@DSL
class Deployment {
    @Key String name

    /** Image reference including the version to deploy. */
    @Required String image

    @Required Capacity capacity
}

@DSL
class Capacity {
    /** Number of simultaneously running service instances. */
    @Validate({ it in 1..20 })
    int replicas = 1

    /** Lower bound required for this deployment's availability. */
    int minimumReplicas = 1

    @Validate
    void replicasMeetMinimum() {
        assert replicas >= minimumReplicas : 'replicas must meet the configured minimum'
    }

    boolean isScaledOut() { replicas > 1 }
}
```

The relationship also reads naturally in a Model:

```groovy
def deployment = Deployment.Create.With('catalog') {
    image 'catalog:1.0'
    capacity {
        minimumReplicas 2
        replicas 3
    }
}
```

Choose composition when something is a part of another concept, and inheritance when types share a meaningful domain
contract. A separate [Layer 3](Layer3.md) Domain API can serve generic clients that should not depend on a concrete
Schema. These choices should explain the model or its consumer boundary, rather than merely organize implementation
helpers. Domain-first and target-contract modeling can both produce readable Schemas; [Gradle Onboarding](Gradle-Onboarding.md#choose-the-model-shape-first)
explains those independent choices.

## Keep constraints close to the state they constrain

The example puts each small rule beside its field: `image` is required, and `replicas` has a local range. `@Required`
uses the default presence/Groovy-truth rule, not a universal non-null check: an empty image String fails.
The details vary by field type and belong in [Validation](Validation.md#required-and-optional).

A short field `@Validate` suits a local rule. The relationship between `replicas` and `minimumReplicas` deserves a
named method, because its meaning spans fields. Named validation methods also make substantial or reusable rules easier
to understand than dense closures. Keep their messages in domain language. See [choosing a validation form](Validation.md#choose-a-validation-form)
for the full contract and further validation options.

## Keep substantial behavior out of declarative structure

`isScaledOut()` is a small query derived from capacity state. It helps consumers ask a domain question without repeating
the calculation. Methods like this fit a [static data model](Static-Models.md); deployment execution, network calls, or
coordination with external services usually belong in a client or adapter that consumes the completed Model.

That separation keeps the Schema readable as a declaration of valid state, rather than turning it into a procedural
service object. It is design guidance, not a prohibition on methods or on the supported construction lifecycle.

Test the Schema through its public construction API, asserting domain-visible completed state and meaningful invalid
cases. [Testing Models and Schemas](Testing-Models-and-Schemas.md) shows the concrete testing conventions.

## Document domain meaning

The comments above explain the image's version, what is counted by `replicas`, and why `minimumReplicas` exists.
Likewise, document units, accepted values, constraints, and non-obvious relationships where their meaning is not already
clear from a name and type. A comment that merely repeats `capacity` or `image` adds little.

KlumAST projects property documentation onto generated public Model and Builder accessors, so these explanations also
help Model Writers and consumers at the point of use. See [Javadoc for models](Javadoc.md) for projection details and
explicit accessor documentation.

For ordinary package and source layout, follow [Gradle Onboarding](Gradle-Onboarding.md#create-the-gradle-project).
