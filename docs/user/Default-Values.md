# Default Values

Non-DSL fields can be annotated with `@Default` to designate a default value, which is set in the
[[Model Phases#default-25|Default phase]] when the value is not Groovy Truth. Booleans are the exception: a `false`
boolean is not treated as empty and is therefore not re-defaulted. For DSL-object fields, use `@AutoCreate` to create an
owned child or `@AutoLink`/`@LinkTo` to resolve an existing target instead. `@Default` supports three mutually exclusive
members; each produces a value that is coerced to the field's type.

The values can be set using the following strategies:

## Other Fields (`field`)
The default value is taken from the value of the target field (of the same instance):

```groovy
given: // Schema
@DSL
class Config {
 String name
 @Default(field = 'name') String id
}
```

Usage:

```groovy
when: // Model
def config = Config.Create.With {
    name 'Hans'
}

then: // Assertions
assert config.id == 'Hans' // defaults to name
```

For example, a release can derive an identifier from its configured name during the default phase:

```groovy
given: // Schema
@DSL
class Release {
    String name

    @Default(field = 'name')
    String identifier
}

when: // Model
def release = Release.Create.With {
    name 'spring-catalog'
}

then: // Assertions
assert release.identifier == 'spring-catalog'
```

The same happy path is executable in `DefaultValuesDocumentaryTest.groovy`, feature
`defaults a release identifier from its configured name`.

## Delegate Fields (`delegate`)

The default value is taken from a property with the same name on the targeted delegate. This is especially
useful in object hierarchies together with an `@Owner` field.

```groovy
given: // Schema
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
when: // Model
def container = Container.Create.With {
    name 'cont'
    element {}
}

then: // Assertions
assert container.element.name == 'cont' // defaults to owner.name
```

Note that since the default phase runs after `Owner` as well as `AutoLink` and `AutoCreate` phases, the Default
annotation can make use of fields set in those phases.

## Arbitrary Code (`code`)

The `@Default` annotation can also include a closure to be executed if the annotated field is empty. The result of that
closure is set as the value of that field.

```groovy
given: // Schema
@DSL
class Config {
 String name
 @Default(code={name.toLowerCase()}) String lower
}
```

Usage:

```groovy
when: // Model
def config = Config.Create.With {
    name 'Hans'
}

then: // Assertions
assert config.lower == 'hans' // defaults to lowercase name
```

## Default as Lifecycle Annotation

As with other annotations, `@Default` can also annotate parameterless methods or Closure fields that run in the
Default phase. See [[Model Phases]] for more information.

## `@DefaultValues` Annotation

Another option is an annotation that is itself annotated with `@DefaultValues`. This is primarily useful with inheritance
and [[Layer3]].

Use such an annotation either on a class or on a field.

### Class Annotation

Consider a Layer 3 home-automation architecture. The API layer defines an abstract `Room`, which Schema classes inherit
for each room in a house. Each `Room` needs default display values. Instead of an abstract getter or a `@Default` method,
use an `@DefaultValues` annotation:

```groovy
// Retentention/Target
@DefaultValues // makes this annotation a default-value provider
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

This is more concise than abstract getters or `@Default` methods and avoids repeated code. The compiler does not require a
subclass of `Room` to use the annotation. In this Layer 3 style, related room classes commonly live together, so reviewers
can assess that choice locally.

### Field Annotation

Default-value annotations can also target a field to configure that field's object. Unlike the class annotation, this is
more useful for non-singleton instances.

Staying with the home-automation example, a `Room` can contain several similar windows. Rather than introducing a subtype
for each direction, configure the window through a field annotation:

```groovy
@DSL 
class Bathroom extends Room {

  @HomeDefaults(shortLabel = "N")
  Window north

  @HomeDefaults(shortLabel = "E")
  Window east
}
```

### Closure and Coercion

KlumAST coerces the default value to the target type on a best-effort basis. If an annotation member has type `Class` and
contains a closure, that closure runs against the target object and its result becomes the default. If the target field is
itself a closure, the closure instance is used as the default instead.

```groovy
@DSL 
class Bathroom extends Room {

  @HomeDefaults(label = {owner.displayName + " North"})
  Window north
    
  @HomeDefaults(label = {owner.displayName + " East"})
  Window east
}
```

### `ignoreUnknownFields`

If a member of a default-value annotation targets a field that does not exist, KlumAST throws an exception. Set
`DefaultValues.ignoreUnknownFields` to `true` to suppress it.

### `valueTarget`

`@DefaultValues` has an optional `valueTarget` member that maps the target annotation's `value` member to a different
field. This enables concise single-value annotations such as `@DisplayName`.

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

If the targeted field is named `value`, the control annotation must still set `valueTarget = 'value'`, or validation fails.

## `@DefaultApply`

`@DefaultApply` is a special case of `@DefaultValues`. It applies its closure to the target field's object during the
Default phase.

Note that there are two caveats to this annotation:

1. The IDE has no generated delegate metadata for the annotation closure, so it cannot offer code completion there. The
   compiler still checks the closure contents.
2. As with `@Default` methods, the closure must check whether fields are already set.

```groovy
given: // Schema
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

when: // Model
def foo = Foo.Create.With {
    bar()
}

then: // Assertions
assert foo.bar.name == "defaultName"
assert foo.bar.age == 42
```
