Fields (of non DSL-types) can be annotated with `@Default` to designate a default value, which is set in the default phase in case the value is not
Groovy Truth. `@Default` supports three different members (only one at a time), which result in different return values
being returned. The default value is coerced to the correct result type.

Note that the behaviour was different before 2.0. Previously, the getter was modified so that the default value was 
returned, but since 2.0 the actual field is set during the "default"-phase.

The values can be set using the following strategies:

# Other fields (field)
The default value is taken from the value of the target field (of the same instance):

```groovy
@DSL
class Config {
 String name
 @Default(field = 'name') String id
}
```

Usage:

```groovy
def config = Config.create {
    name 'Hans'
}

assert config.id == 'Hans' // defaults to name 
```

# Delegate fields (delegate)

The default value is taken from a property with the same name of the targeted delegate. This is especially 
useful in object hierarchies in combination with the `@Owner` field.

```groovy
@DSL
class Container {
    String name

    Element element
}

@DSL
class Element {
    @Owner Container owner

    @Default(delegate = 'owner')
    String name
}
```

Usage:

```groovy
def container = Container.create {
    name 'cont'
    element {}
}

assert container.element.name == 'cont' // defaults to owner?.name 
```

Note that since the default phase runs after `Owner` as well as `AutoLink` and `AutoCreate` phases, the Default
annotation can make use of fields set in those phases.

# Arbitrary code (code)

The `@Default` annotation can also include a closure to be executed if the annotated field is empty. The result of that
closure is set as the value of that field.

```groovy
@DSL
class Config {
 String name
 @Default(code={name.toLowerCase()}) String lower
}
```

Usage:

```groovy
def config = Config.create {
    name 'Hans'
}

assert config.lower == 'hans' // defaults to lowercase name
```

# Default as lifecycle annotation

As with other annotations, `@Default` can also be used to annotate parameter less methods or Closure fields to run
in the Default-Phase, see [Model Phases](Model-Phases.md) for more information.

```groovy
