Fields (of non DSL-types) can be annotated with `@Default` to designate a default value, which is set in the default phase in case the value is not
Groovy Truth. `@Default` supports three different members (only one at a time), which result in different return values
being returned. The default value is coerced to the correct result type.

Note that the behavior was different before 2.0. Previously, the getter was modified so that the default value was 
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
in the Default-Phase, see [[Model Phases]] for more information.

# DefaultValues annotation

Another option to set default values is by using annotations annotated with the `@DefaultValues` annotation. This is mostly useful 
in combination with inheritance and [[Layer3]].

There are two ways to use as `@DefaultValues` annotation, as a class annotation or as a field annotation.

## Class annotation

Consider a layer3 architecture for home automation, where the api layer defines an abstract `Room` class, which will be inherited
by quasi singleton classes for each room in the house (as part of the schema layer). The `Room` class has a display name field, which 
should be set by each room class to a default value. Instead of using a Default method or abstract getters, a @DefaultValues annotation can be used.: 

```groovy
// Retentention/Target
@DefaultValues // makes this annotation a DefaultValue-Provider
@interface HomeDefaults {
    String displayName() default ""
    String shortLabel() default ""
}

@DSL 
abstract class Room {
  String displayName
  String shortLabel
}

@DSL 
@HomeDefaults(displayName = 'Bath', shortLabel = 'BTH')
class Bathroom extends Room {
}

@DSL 
@HomeDefaults(displayName = 'Main Office', shortLabel = 'MOF')
class Office extends Room {
}
```

The main advantage of this approach is that it is a lot more concise than the other options (like using abstract getters or Default methods, which would also result in more duplicate code). The major disadvantage is that the compiler does not force subclasses of Room to set the annotation. But in a layer 3 architecture, one will have a couple of Rooms defined together, usually even in the 
same source file, so this is not a big issue.

## Field annotation

Default values annotations can also be used on fields to set the default value for that specific field's object. Unlike the class annotation, this makes more sense for non-singleton instances.

Staying with the home automation example, consider the Room containing one or more window members. Since windows are quite similar,
multiple subclasses for different windows are not necessary. Instead, the window type can be configured by a field annotation:

```groovy
@DSL 
class Bathroom extends Room {

  @HomeDefaults(shortLabel = "N")
  Window north

  @HomeDefaults(shortLabel = "E")
  Window east
}
```

## Closure and coercion

The system tries to coerce the default value to the correct type on a beste effort base. If the member is of type class and 
contains a closure, that closure is executed against the target object and the result is set as the default value (unless the 
target field is itself a closure, in that case an instance of that closure is used as default value instead).

```groovy
@DSL 
class Bathroom extends Room {

  @HomeDefaults(label = {owner.displayName + " North"})
  Window north
    
  @HomeDefaults(label = {owner.displayName + " East"})
  Window east
}
```

## ignoreUnknownFields

If the field target by a default values annotation's member is not existant, an exception is thrown. This can be prevented
by setting `DefaultValues.ignoreUnknownFields` to true.

## valueTarget

`@DefaultValues` has an optional `valueTarget` member that can be used to map the `value` member of the targeted annotation to a different field. This allows for nice single-value annotations (like `@DisplayName`).

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.FIELD])
@DefaultValues(valueTarget = "displayName")
@interface DisplayName {
    String value()
}

@DSL class Home {
    @DisplayName("Living Room")
    LivingRoom livingRoom
}
```

Note that if the targeted field is actually named `value`, the `valueTarget` member of the control annotation must still be set (to 'value'), or the validation will fail.

## DefaultApply

The `@DefaultApply` annotation is a special case for DefaultValues. It applys the given apply annotation to the object of the target field in 
the default phase. 

Note that there are two caveats to this annotation:

1. Since there is no way to notify the IDE about the closures delegate, there is no code completion for the annotation's closure (however, the compiler will correctly check the contents of the closure).
2. As with Default-methods, any checks whether the fields are already set must be done manually. 

```groovy
@DSL
class Foo {
    @DefaultApply({
        if (!name) name "defaultName"
        if (!age) age 42
    })
    Bar bar
}

@DSL class Bar {
    String name
    int age
}

when:
def foo = Foo.Create.With {
    bar()
}

then:
foo.bar.name == "defaultName"
foo.bar.age == 42
```
