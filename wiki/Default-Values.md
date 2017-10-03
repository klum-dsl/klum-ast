Fields can be annotated with `@Default` to designate a default value, which is returned in case the value is not
Groovy true. `@Default` supports three different members (only one at a time), which result in different return values
being returned. The default value is coerced to the correct result type.

# Other fields (field)

If the annotated field is empty, the value of the target field is returned instead:

```groovy
@DSL
class Config {
 String name
 @Default(field = 'name') String id
}
```

creates the following method:

```groovy
String getId() {
    id ?: getName()
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

If the annotated field is empty, a property with the same name of the targeted delegate is returned. This is especially 
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

creates the following method:

```groovy
String getName() {
    name ?: getOwner()?.name
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

# Arbitrary code (code)

The `@Default` annotation can also include a closure to be executed if the annotated field is empty. The result of that
closure is returned instead.

```groovy
@DSL
class Config {
 String name
 @Default(code={name.toLowerCase()}) String lower
}
```

creates the following methods:

```groovy
String getLower() {
    lower ?: name.toLowerCase() // actually a closure is called, including setting of delegate etc...
}
```

Usage:

```groovy
def config = Config.create {
    name 'Hans'
}

assert config.lower == 'hans' // defaults to lowercase name
```

Note that default values do work with DSL fields and collections as well.
