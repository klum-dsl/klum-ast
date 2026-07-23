# Advanced Techniques

## Delegation Hints for Builder Closures

Generated methods accepting configuration closures receive the appropriate `@DelegatesTo` metadata automatically, so
modern IDEs can infer the available Builder methods.

Schema-defined Mutators sometimes accept and forward their own configuration closures. The generated Builder type does
not exist when that source method is parsed. Use `@DelegatesToBuilder` in that case. It tells the IDE and static type
checker about the generated Builder, not a mutable completed DSL Object.

The optional annotation value selects the DSL Object whose Builder receives the closure:

```groovy
@DSL
class Container {
    List<Element> elements

    @Mutator
    def circle(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'circle', body)
    }

    @Mutator
    def square(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'square', body)
    }
// ...
}
```

Here both Mutators execute on the `Container` Builder and delegate `body` to a newly created `Element` Builder.
`@DelegatesToBuilder` does not add a completed-model `apply` or `configure` path. An API that configures a DSL Object must
participate in factory/Builder construction; see [[Builder First Migration]] for the lifecycle boundary.

## Behavior Models and Parameter Hints

Fields can hold dynamic behavior as a closure or interface value. This lets a Model choose behavior without creating a
new Schema type.

Consider the following example:

```groovy
class ValueProvider {
    String name

    String getDescription(Map<String, String> environment) {
        "Value: $name: ${environment.name} -> ${environment.value}"
    }
}
```

If the description must contain another value, a subclass of `ValueProvider` would be required. That is inconvenient when
the behavior varies by Model rather than by Schema.

Make the description configurable instead (the Strategy pattern). Use either an interface/abstract class or a closure.

### Interface

```groovy
interface DescriptionProvider {
    String getDescription(Map<String, String> environment)
}

@DSL class ValueProvider {
    String name

    @Required
    DescriptionProvider descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

The Model can now supply the description algorithm. In Groovy, a single-abstract-method (SAM) interface can be supplied
as a closure:

```groovy
ValueProvider.Create.With {
    name "Blub"
    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

The closure has one `Map` parameter and returns `String`, which the compiler can check.

`DescriptionProvider` could instead be an abstract class, for example to add [[Converters#factory-method-converters]].

### Closure Attributes

The description provider could also be a Closure itself:

```groovy
@DSL class ValueProvider {
    String name

    Closure<String> descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}

ValueProvider.Create.With {
    name "Blub"

    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

The Model call looks the same as the SAM-interface form. With an unannotated Closure field, however, the IDE and type
checker do not know the parameter type, so they cannot offer parameter completion.

In normal Groovy, a method parameter can carry `@ClosureParams`. Because this setter is generated, use
`@ParameterAnnotation.ClosureHint` instead. For this closure field, the supplied hint carries the required parameter
annotation:

```groovy
@DSL class ValueProvider {
    String name

    @ParameterAnnotation.ClosureHint(params = @ClosureParams(value = FromString, options = "Map<String,Object>"))
    Closure<String> descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

This provides parameter completion and type checking for the generated methods. `@ParameterAnnotation` copies annotations
from a Schema field to the generated setter or single-element adder; see the
[`@ParameterAnnotation` API source and Javadoc](https://github.com/klum-dsl/klum-ast/blob/master/klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/ParameterAnnotation.java)
for the advanced annotation-mapping rules.

## Choosing a SAM Interface or Closure

Prefer a SAM interface when only parameter typing is needed, especially with factory converters.

Use a Closure with parameter annotations when its Groovy delegate mechanism makes the DSL materially clearer; a SAM
interface cannot reproduce that delegate behavior.

In short: use a SAM interface for ordinary typed behavior and a Closure with `@ParameterAnnotation` when delegation is
part of the DSL.
