# Schema Style Guide

An idiomatic KlumAST Schema makes the model's meaning easy to see: what can be configured, how its parts relate, and
what makes a configuration valid. The advice here is about readability and design, not additional compiler restrictions.
Use the documentation matching your KlumAST release.

## Design the domain and Model DSL together

A Schema should express domain concepts and make them pleasant to configure. Ask early: **how will this read in the
Model DSL?** Sketch a small representative Model, ideally as a test, even before implementing the Schema:

```groovy
def deployment = Deployment.Create.With('catalog') {
    image 'catalog:1.0'
    capacity {
        minimumReplicas 2
        replicas 3
    }
}
```

Then shape the Schema around that example, adjusting both as the domain and the authoring syntax become clearer.
A useful domain model can still be awkward to configure; trying the DSL early exposes that friction. Tests through the
public construction API can check the resulting domain state and meaningful invalid cases; see
[Testing Models and Schemas](Testing-Models-and-Schemas.md).

KlumAST supplies the Builders and factories behind this syntax. They need not become extra domain state or handwritten
implementation helpers. Prefer descriptive names for nested helpers, such as `CapacityChecks`. Names resembling
generated implementation types make a Schema harder to read and can collide with types KlumAST generates. Actual
collisions are diagnosed by the compiler; leading underscores alone are not prohibited. See
[the exact collision rules](Migration.md#generated-inner-name-collisions-41) when a diagnostic asks you to rename a type.

## Keep the structure obvious

Fields describe domain state, and their types make relationships visible. Here a `Deployment` has an `Image` value and
a `Capacity` configured through its own DSL. The Schema mirrors the concepts visible in the Model above:

```groovy
package example.deployments

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate

@DSL
class Deployment {
    @Key String name

    @Required Image image

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

Choose composition when something is a part of another concept, and inheritance when types share a meaningful domain
contract. A separate [Layer 3](Layer3.md) Domain API can serve generic clients that should not depend on a concrete
Schema. These choices should explain the model or its consumer boundary, rather than merely organize implementation
helpers. Domain-first and target-contract modeling can both produce readable Schemas; [Gradle Onboarding](Gradle-Onboarding.md#choose-the-model-shape-first)
explains those independent choices.

## Use domain values with convenient input

Consider immutable domain objects instead of primitives or Strings, especially when the consuming client already needs
those types. A converter or factory method lets Model Writers keep concise input while clients receive a meaningful
value. In the same package as the Schema, this small non-DSL `Image` accepts just `name:tag`:

```groovy
final class Image {
    final String name
    final String tag

    Image(String name, String tag) {
        if (!name?.trim() || !tag?.trim())
            throw new IllegalArgumentException('Image name and tag must be nonblank')
        this.name = name
        this.tag = tag
    }

    static Image fromString(String reference) {
        String[] parts = reference?.split(':', -1)
        if (parts?.length != 2)
            throw new IllegalArgumentException('Use an image reference in name:tag form')
        new Image(parts[0], parts[1])
    }
}
```

KlumAST discovers `fromString` as a factory-method converter, so `image 'catalog:1.0'` stores an `Image` whose `name` and
`tag` the client can use directly. For a real image format, reuse the client's established value type and parser where
possible. See [Converters](Converters.md#factory-method-converters) for factory discovery and explicit field converters.

A value like `Image` does not need to be a DSL Object. Its constructor/factory validates immediately when the input is
converted; an invalid value fails at that point, rather than joining completed-model validation. That suits a
self-contained value. DSL validation runs after the owned model tree is completed, which suits rules needing other
fields or relationships. Choose with both the desired authoring syntax and validation timing in mind;
see [Validation](Validation.md#validation-of-nested-objects) for the model-wide boundary.

## Keep constraints close to the state they constrain

The example puts each small rule beside its field: `image` is required, and `replicas` has a local range. `@Required`
uses the default presence/Groovy-truth rule, not a universal non-null check. Here it requires an `Image` value;
that value's factory and constructor enforce its format and components. The field-type details belong in
[Validation](Validation.md#required-and-optional).

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

## Document domain meaning

Use types, converters/factory methods, and validation to express and enforce the contract before relying on prose.
The image format is checked in code; a comment saying it needs a name and tag would not protect a client from invalid
input. Documentation complements those checks by explaining meaning and rationale.

The comments above explain what is counted by `replicas` and why `minimumReplicas` exists. Likewise, document units,
semantics, constraints, and non-obvious relationships where their meaning is not already clear from the code. A comment
that merely repeats `capacity` or `image` adds little.

KlumAST projects property documentation onto generated public Model and Builder accessors, so these explanations also
help Model Writers and consumers at the point of use. See [Javadoc for models](Javadoc.md) for projection details and
explicit accessor documentation.

For ordinary package and source layout, follow [Gradle Onboarding](Gradle-Onboarding.md#create-the-gradle-project).
