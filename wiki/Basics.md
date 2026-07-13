KlumAST consists of a number of Annotations: 

- `@DSL` annotates all domain classes, i.e. classes of objects to be generated via the DSL.
- `@Key` annotates the optional key field of a dsl object (see below).
- `@Owner` annotates the optional owner field of a dsl object.
- `@Field` is an optional field to further configure the handling of specific fields (esp. naming).
- `@Validation` and `@Validate` provide automatic validation of model values.
- `@PostCreate` and `@PostApply` can be used to designate lifecycle methods.

# @DSL
DSL is used to designate a DSL/Model object, which is enriched using the AST transformation.

The DSL annotation leads to the creation of a couple of useful methods.

## Factory construction

Each instantiable DSL class gets a static field `Create` of either a subclass of `KlumFactory.Keyed` or
`KlumFactory.Unkeyed`, which provides methods to create instances of the class; abstract classed get an
implementation of `KlumFactory` instead.

```groovy
@DSL
class Config {
}

@DSL
class ConfigWithKey {
    @Key String name
}
```
        
allows to create instances with the following calls:
    
```groovy
Config.Create.One()
Config.Create.With(a: 1, b: 2)
Config.Create.With(a: 1, b: 2) { c 3 }
Config.Create.With { c 3 }

ConfigWithKey.Create.One('Dieter')
ConfigWithKey.Create.With('Dieter', a: 1, b: 2)
ConfigWithKey.Create.With('Dieter', a: 1, b: 2) { c 3 }
ConfigWithKey.Create.With('Dieter') { c 3 }
```
The optional closure to the `With` method configures the generated Builder. The `One` method is a shortcut for
`With` without any given values, which makes a nicer syntax (`Config.Create.With()` seems a bit strange, 
`Config.Create.One()` looks better). The factory materializes the completed DSL Object graph before returning it.

__Note that pre 2.0 versions of KlumAST did create the methods directly as static methods of the model class. These methods 
are now deprecated in will be removed in a future version.__

If the class contains an static inner class named 'Factory' of the appropriate type or the member `factory` points
to such a class, this class is used as a base
for the generated factory instead. This allows adding additional methods to the factory.

Completed DSL Objects do not expose a generated `apply` method. All configuration belongs in a factory callback,
a Template recipe, or a lifecycle callback before `INSTANTIATE`.

`Create.With` supports named parameters, allowing values to be set concisely. Every map element of
the method call is converted in a setter call (actually, any method named like the key with a single argument will be called):


```groovy
Config.Create.With {
    name "Dieter"
    age 15
}
```

Could also be written as:
```groovy
Config.Create.With(name: 'Dieter', age: 15)
```

Of course, named parameters and regular calls inside the closure can be combined ad lib.

There are also a couple of [[Convenience Factories]] to load a model into client code.

## Lifecycle Methods

Lifecycle methods can are methods annotated with [Lifecycle](Model-Phases.md) annotations like `@PostCreate` and `@PostApply`.
These methods will be called automatically
after Builder creation (**after templates have been applied**) and after explicit Builder configuration, respectively.

Other lifecycle methods will be executed in the corresponding phase.

Lifecycle methods must not be `private`. They are moved to the generated Builder. Mutating lifecycle work through
`POST_TREE` receives Builders; validation receives completed DSL Objects after `INSTANTIATE`.

## copyFrom() method

Each Builder gets a `copyFrom()` DSL method. This method copies fields from a DSL Object recipe into the current Builder,
excluding key, owner and `@Role` fields, as well as fields marked `FieldType.TRANSIENT`
or `FieldType.IGNORED`. Copying is further governed by `@Overwrite` / the configured `OverwriteStrategy`. This is done
recursively: nested Template composition is rehydrated into fresh Builders. Completed ordinary models are not adopted as
owned composition.

## equals() method

If not yet present, the `equals()` method is generated using the default `@EqualsAndHashCode` ASTTransformations. You
can customize it by using the original ASTTransformation.

## hashCode()
A barebone hashCode is created, with a constant 0 for non-keyed objects, and the hashcode of
the key for keyed objects. While this is correct and works with changing objects after
adding them to a HashSet / HashMap, the performance for Sets of non-Keyed objects is severely
reduced.

## Field setter
### Field setter for simple fields

For each Simple Value field, the Builder gets an accessor named like the field and taking the field type as parameter.
These methods are available only during factory, Template, or lifecycle configuration. Completed DSL Objects expose
read-only accessors.

```groovy
@DSL
class Config {
 String name
}
```

creates the following method:

```groovy
def name(String value)
```

Used by:
```groovy
Config.Create.With {
   name "Hallo"
}
```

### Setter for simple collections
    
for each simple collection, two/three methods are generated:

-   two methods with the collection name and a Iterable/Vararg argument for Collections or a Map argument for maps. These methods
    *add* the given parameters to the collection 

-   an adder method named like the element name of the collection and containing the element type 


```groovy
@DSL
class Config {
    List<String> roles
    Map<String, Integer> levels
}
```
    
creates the following methods:

```groovy
def roles(String... values)
def roles(Iterable<String> values)
def role(String value)
def levels(Map levels)
def level(String key, Integer value)
```

Usage:
```groovy
Config.Create.With {
    roles "a", "b"
    role "another"
    levels a:5, b:10
    level "high", 8
}
```

If the collection has no initial value, it is automatically initialized.

### keyMapping for simple maps

Instead of directly providing the key in the adder call, it can also be derived
from the value itself. This is done by using the `keyMapping` attribute of the `@Field` annotation.

This attribute accepts a closure that gets a single parameter of the value type and must return a value
of the key type.

If a keyMapping is set for a simple type, adder methods only have a value parameter (instead of key and value),
the map adder is replaces with a collection adder.

```groovy
@DSL class Foo {

    @Field(keyMapping = { it.toLowerCase() })
    Map<String, String> values
}

Foo.Create.With {
    value "bla"
    value "BLUB"
    values "bla", "blub"
    values(["bli", "blu"])
} 
```

### Setters and closures for DSL-Object Fields
    
For each DSL Object composition field, a Builder closure method is generated. If the field is keyed, this method has an
additional key parameter. The relationship field holds a child Builder during construction; the closure method returns
that Builder so further construction-time configuration can be composed.
  
```groovy
@DSL
class Config {
    UnKeyed unkeyed
    Keyed keyed
}

@DSL
class UnKeyed {
    String name
}

@DSL
class Keyed {
    @Key String name
    String value
}
```
    
conceptually creates the following Builder methods (the concrete generated Builder type and location are not public API):

```groovy
def unkeyed(@DelegatesTo(/* UnKeyed Builder */) Closure closure)
def keyed(String key, @DelegatesTo(/* Keyed Builder */) Closure closure)
```

Usage:
```groovy
Config.Create.With {
    unkeyed {
        name "other"
    }
    keyed("klaus") {
        value "a Value"
    }
}
```

The closure methods return the child Builder during construction:

```groovy
Config.Create.With {
    def childBuilder = unkeyed {
        name "other"
    }
    childBuilder.name "final"
}
```

An already completed DSL Object cannot be adopted as owned composition. Existing completed objects are accepted only by
`FieldType.LINK` fields, where they remain aggregation targets and are not re-owned or mutated.

#### Polymorphic DSL members

To create subclasses of the requested element (the field is of type `Element`, but we want the value to be of
type `SubElement`), there are several options:

##### Typed factory methods

For non final field types, a polymorphic setter is created that takes the requested type as first parameter:

```groovy
Config.Create.With {
  main(SubElement) {
    ...
  }
}
```

This keeps the subclass in the parent's Builder lifecycle. IDE presentation of the generated concrete Builder type is
still being finalized together with its public name and placement.

##### Existing completed values

Starting `SubElement.Create.With` inside an active parent factory starts an independent lifecycle and cannot produce a
newly owned child. Create polymorphic composition through the parent's typed child method instead. If the relationship is
aggregation rather than composition, annotate it with `@Field(LINK)` and pass the independently completed object as its
existing target.

### Virtual Fields

In addition to fields, setter like methods (i.e. methods with a single parameter) can also be annotated with `@Field`,
making them 'virtual fields'. For virtual fields, the same dsl methods are generated as for actual fields. The name
of the methods is the same as the method name (this is different to KlumAST 1.2, where the name was derived).

The annotated method is automatically converted into a Mutator method.

```groovy
@DSL class Foo {
    String value

    @Field
    void addBar(Bar bar) {
        this.value = bar.name
    }
}

@DSL class Bar {
    String name
}

def foo = Foo.Create.With {
    bar {
        name "Hans"
    }
}

assert foo.value == "Hans"
```

Note that, as in the above example, this behaviour, while working with non dsl arguments as well, makes the most sense for actual DSL arguments.

### Default Implementation

Using the `defaultImpl` attribute of the `Field` annotation, you can specify a default implementation for a field. That way,
dsl methods are created as if the field were of the specified type. This is especially useful for interface as field type.

```groovy
@DSL
class Foo {
    @Field(defaultImpl = BarImpl)
    Bar bar
}

interface Bar {
    String getValue()
}

@DSL
class BarImpl implements Bar {
    String value
} 
```

Although the field is not of an DSL type, normal DSL methods are created for it:

```groovy
Foo.Create.With {
    bar(value: "Dieter")
}
```

This allows models to use interfaces defined elsewhere, by providing a dslified implementation.

The defaultImplementation can also be set on a DSL class, providing the default implementation for all fields of that type:

```groovy
@DSL
class Foo {
    Bar bar
}

@DSL(defaultImpl = BarImpl)
interface Bar {
    String getValue()
}

Foo.Create.With {
    bar(value: "Dieter")
}
```

Usually, default implementation is only used on interfaces or abstract classes, but this is not enforced, since there
might be some corner cases where it is useful.

`defaultImpl` can also be used on collections, maps and virtual fields.


### Collections of DSL Objects

Collections of DSL-Objects are created using a nested closure. The name of the (optional) outer closure is the field name, the
name of the inner closures the element name (which defaults to field name minus a trailing 's'). The syntax for adding
keyed members to a list and to a map is identical.

Inner creators produce child Builders in the same lifecycle as the owning Builder. They return the created Builder so
construction can be delegated or composed without materializing an intermediate DSL Object. Completed DSL Objects cannot
be inserted into owned composition collections; a collection of existing aggregation targets must be marked `LINK`.

```groovy
@DSL
class Config {
    List<UnKeyed> elements
    List<Keyed> keyedElements
    Map<String, Keyed> mapElements
}

@DSL
class UnKeyed {
    String name
}

@DSL
class Keyed {
    @Owner owner
    @Key String name
    String value
}

Config.Create.With {
    elements { // optional, but provides grouping and additional convenience features
        element {
            name "an element"
        }
        element {
            name "another element"
        }
    }
    keyedElements {
        def keyedBuilder = keyedElement("klaus") {
            value "a Value"
        }
        keyedBuilder.value "final Value"
    }
    mapElements {
        mapElement("dieter") {
            value "another"
        }
    }
}

// flat syntax without nested closures:
Config.Create.With {
    element {
        name "an element"
    }
    element {
        name "another element"
    }
    keyedElement("klaus") {
        value "a Value"
    }
    mapElement("dieter") {
        value "another"
    }
}
```

#### Automatic Key determination for DSL-Map entries
In case of a keyed Map-Element, the key is automatically used as key for the map entry.
This can be overridden using `@Field.keyMapping`, which also allows using unkeyed elements in Maps.

```groovy
@DSL class Foo {
    @Field(keyMapping = { it.secondary })
    Map<String, Bar> bars
    @Field(keyMapping = { it.secondary })
    Map<String, TwoBar> twobars
}

@DSL class Bar {
    String secondary
}

@DSL class TwoBar {
    @Key String key
    String secondary
}

def instance = Foo.Create.With {
    bar {
        secondary "blub"
    }
    bar {
        secondary "bli"
    }
    twobar("boink") {
        secondary "blub"
    }
    twobar("bunk") {
        secondary "bli"
    }
}

instance.bars.blub
instance.bars.bli

instance.twobars.blub.key == "boink"
instance.twobars.bli.key == "bunk"
```


#### Polymorphic collection members

As with [Polymorphic DSL members](#polymorphic-dsl-members), members of collections can also be of subclasses of the declared types. The
same mechanisms for single members can be used for collections, too (with the same caveats as above).

Also, a more powerful approach is available using the [[Alternatives Syntax]].

## On collections

Collection declarations must use one of the snapshot-safe supported types: `List`, `Set`,
`SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, or `EnumSet`. Every custom Collection interface or class,
and every other concrete Collection type, is rejected during schema compilation.

Builders keep mutable construction collections. Materialization publishes independent read-only snapshots, so neither a
Builder collection nor an input collection can mutate the completed model afterward. Sorted collections preserve their
comparator. `EnumSet` getters return defensive copies.

**Be careful when using a simple `Set`.** Since Klum creates barebone hashcode implementations
 (constant zero for non-keyed objects, hashCode of key for keyed objects), a (non Sorted)`Set` of
 non-Keyed model objects might result in a severe degradation of performance of that Set.

# the @Key annotation

The key annotation is used to designate a special key field, making the annotated class a keyed class. This has the
following consequences:

- no setter method is generated for the key field
- the generated Builder receives the key during internal construction (currently, only String is allowed)
- factory methods get an additional key parameter
- only keyed classes are allowed as values in a Map

# The @Owner annotation

DSL-Objects can have an owners field, decorated with the `@Owner` annotation.

When the inner object is added to another dsl-object, either directly or into a collection,
all of its owner fields are automatically set to the outer object if they follow two conditions (decided for each field individually):

- The field is unset, i.e. has the value null
- The field can legally hold the owner object


```groovy
@DSL
class Foo {
    Bar bar
}

@DSL
class Bar {
    @Owner Foo outer
}

def c = Config.Create.With {
    bar {}
}

assert c.bar.outer === c
```

The owner field is set during the Owner phase, so it is not available during the inner Builder's initial configuration
closure.

If configuration needs the owner, move that code to an Owner or later Builder lifecycle method or closure. Owner methods
and closures run after owner fields have been assigned.

__Because `owner` is a property of `Closure`, it is not advisable to name the Owner field (or any other field) actually `owner`,
because it would be overshadowed in configuration closures.__

## Owner methods

Setter like methods (single parameter methods) can also be annotated with `@Owner. In that case,
all matching Owner methods are called if the object is added to another DSL object (i.e. if
the Container object ist assignable to the method parameter type). Owner methods are
mutator methods and thus moved into the Builder.


## Transitive owners

With the field `transitive` of the `@Owner` annotation, the annotated field will be set to the first matching instance
in the owner chain (for owner fields and owner methods). 

```groovy
@DSL class Parent {
    Child child
    String name
}

@DSL class Child {
    @Owner Parent parent
    GrandChild child
    String name
}

@DSL class GrandChild {
    @Owner Child parent
    @Owner(transitive = true) Parent grandParent
    String name
}

instance = Parent.Create.With {
    name "Klaus"
    child {
        name "Child Level 1"
        child {
            name "Child Level 2"
        }
    }
}

assert instance.child.child.grandParent.is(instance)
```
Transitive owner fields are ignored when determining the owner hierarchy, i.e. they are not considered actual parent objects.

## Root owners

With the field `root` of the `@Owner` annotation, the annotated field will be set to the root object of the model (if the type matches). This is useful if an object needs to access the root object, but has not direct backlink chain to it.

This can most conveniently be done using a common baseclass for interested objects:

```groovy
@DSL
abstract class ModelElement {
    @Owner(root = true)
    MyModel root
}

@DSL
class MyModel {
    ...
}

@DSL
class SomeElement extends ModelElement {
    ...
}

@DSL
class AnotherElement extends ModelElement {
    ...
}
```

## Owner converters

Owner converter can be used to convert the owner object to another type before setting the field. In that case, the parameter of the converter closure is used whe determining whether the potential owner object matches (instead of the field type or the method parameter).

```groovy
package pk

@DSL
class Parent {
    Child child
    String name
}

@DSL
class Child {
    @Owner Parent parent
    @Owner(converter = { Parent parent -> parent.name }) 
    String parentName
    
    String name
    String upperCaseParentName
    
    @Owner(converter = { Parent parent -> parent.name.toUpperCase() })
    void setUCParentName(String name) {
        upperCaseParentName = name.toUpperCase()
    }
}

when:
instance = clazz.Create.With {
    name "Klaus"
    child {
        name "Child"
    }
}

then:
instance.child.parent.is(instance)
instance.child.parentName == "Klaus"
instance.child.upperCaseParentName == "KLAUS"
```

Converting owner fields are ignored when determining the owner hierarchy, i.e. they are not considered actual parent objects.


# Field Types
The `@Field` annotation has a value of type `FieldType` where special handling of the field
can be configured. It currently supports the following values:

## PROTECTED
Fields marked as `PROTECTED` are not externally writable, all dsl methods as well as the dsl methods
are created as protected. This essentially means that they cannot be changed directly by a user of
the DSL. They can only be changed via custom mutator (or lifecycle) methods or other setters.

## BUILDER
`BUILDER` fields exist only on the generated Builder. Their DSL methods are public, but no corresponding field or getter
is generated on the completed DSL Object. They are intended for construction-only state consumed by a later Builder phase.

## TRANSIENT
`TRANSIENT` fields are similar in that they don't get dsl methods either. However, in contrast
to all other fields, they retain a public setter in the completed model, taking them effectively
out of the [[Static Models]] concept. They can be used to add transient data that is not
part of the model itself. Transient fields are ignored when checking for equality.

## IGNORED
`IGNORED` fields get not DSL accessors at all. Their setters are still moved to the
Builder. As with PROTECTED this means that these fields can effectively only be set
from inside lifecycle or mutator methods.

## LINK
`LINK` fields model aggregation. They accept existing completed DSL Objects through sealed Builder wrappers but do not
create or re-own those targets. All non-`LINK` DSL Object relationships are owned composition and must be created within
the owner's Builder lifecycle.

# DSL Interfaces
As of version 1.2.0, interfaces can me marked with `@DSL` as well. No transformation will
be done for these interfaces, however a field with an annotated interface type will get its
dsl methods generated:

```groovy
@DSL class Outer {
    Foo foo
}

@DSL class FooImpl implements Foo {
    String value
}

@DSL interface Foo {
    String getValue()
}

Outer.Create.With {
    foo(FooImpl) {
        value "name"
    }
}
```

Note that the usage of non-DSL classes implementing DSL interfaces might lead to runtime errors when instantiating a model.

# Fixed keys

Using the `key` member of `@Field` the key of a keyed member can be set
by to a fixed or derived value. This removes the key parameter from all 
creation methods.

`key` is either a closure on the owning instance or the special class
`Field.FieldName` which uses the name of the member as fixed key.

This is useful if the member is derived from some value of the owner.

For example, consider the following classes:

```groovy
@DSL
class Database {
    @Key String name
    //... more values
}

@DSL
class Server {
    @Key String name
    @Field(key = { name })
    Database database
}
```

This allows creating the server like: 

```groovy
Server.Create.With("INT") {
    database {  // instead of database("INT") {
       //...
    }
}
```

The `key`-member is only valid for single keyed fields.
