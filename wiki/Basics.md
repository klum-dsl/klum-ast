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

## Factory and `apply` methods

A factory method named `create` is generated, using either a single closure as parameter, or, in case of a keyed
object, using a String and a closure parameter.

```groovy
@DSL
class Config {
}

@DSL
class ConfigWithKey {
    @Key String name
}
```
        
creates the following methods:
    
```groovy
static Config create(Closure c = {})

static ConfigWithKey create(String name, Closure c = {})
```

__Note that the creation methods might be eventually moved to a separate factory class for a model.__

Additionally, an `apply` method is created, which takes single closure and applies it to an existing object.
 
```groovy
def void apply(Closure c)
```

Both `apply` and `create` also support named parameters, allowing to set values in a concise way. Every map element of
the method call is converted in a setter call (actually, any method named like the key with a single argument will be called):


```groovy
Config.create {
    name "Dieter"
    age 15
}
```

Could also be written as:
```groovy
Config.create(name: 'Dieter', age: 15)
```

Of course, named parameters and regular calls inside the closure can be combined ad lib.

There are also a couple of [[Convenience Factories]] to load a model into client code.

## Lifecycle Methods

Lifecycle methods can are methods annotated with `@PostCreate` and `@PostApply`. These methods will be called automatically
after the creation of the object (**after the template has been applied**) and after the call to the apply method, respectively.

Lifecycle methods must not be `private`. They will be automatically be made protected and moved to rw instance. 

## copyFrom() method

Each DSLObject gets a `copyFrom()` method with its own class as parameter. This method copies fields from the given
object over to this objects, excluding key and owner fields. For non collection fields, only a reference is copied,
for Lists and Maps, shallow copies are created.

Currently, it is in discussion whether this should be deep clone instead, see: ([#36](https://github.com/klum-dsl/klum-core/issues/36))

## equals() and toString() methods

If not yet present, `equals()` and `toString()` methods are generated using the respective ASTTransformations. You
can customize them by using the original ASTTransformations.

## hashCode()
A barebone hashcode is created, with a constant 0 for non-keyed objects, and the hashcode of
the key for keyed objects. While this is correct and works with changing objects after
adding them to a HashSet / HashMap, the performance for Sets of non-Keyed objects is severely
reduced.

## Field setter
### Field setter for simple fields

For each simple value field create an accessor named like the field, containing the field type as parameter. __Since 
0.98, these methods are only usable inside of an `apply` or `create` block.__

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
Config.create {
   name "Hallo"
}
```

### Setter for simple collections
    
for each simple collection, two/three methods are generated:

-   two methods with the collection name and a Iterable/Vararg argument for Collections or a Map argument for maps. These methods
    *add* the given parameters to the collection 

-   an adder method named like the element name of the collection an containing a the element type 

__Since 0.98, these methods are only usable inside of an `apply` or `create` block.__

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
Config.create {
    roles "a", "b"
    role "another"
    levels a:5, b:10
    level "high", 8
}
```

If the collection has no initial value, it is automatically initialized.

### Setters and closures for DSL-Object Fields
    
for each dsl-object field, a closure method is generated, if the field is a keyed object, this method has an additional
String parameter. Also, a regular setter method is created for reusing an existing object.
  
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
    
creates the following methods (in Config, only valid in `apply`/`create` blocks):

```groovy
def unkeyed(UnKeyed reuse) // reuse an exiting object
Unkeyed unkeyed(@DelegatesTo(Unkeyed) Closure closure)
def keyed(UnKeyed reuse) // reuse an exiting object
Keyed keyed(String key, @DelegatesTo(Unkeyed) Closure closure)
```

Usage:
```groovy
Config.create {
    unkeyed {
        name "other"
    }
    keyed("klaus") {
        value "a Value"
    }
}

def objectForReuse = UnKeyed.create { name = "reuse" }

Config.create {
    unkeyed objectForReuse
}
```

The closure methods return the created objects, so you can also do the following:

```groovy
def objectForReuse
Config.create {
    objectForReuse = unkeyed {
        name "other"
    }
}

Config.create {
    unkeyed objectForReuse
}
```

#### Polymorphic DSL members

To create subclasses of the requested element (the field is of type `Element`, but we want the value to be of
type `SubElement`), there are several options:

##### Typed factory methods

For non final field types, a polymorphic setter is created that takes the requested type as first parameter:

```groovy
Config.create {
  main(SubElement) {
    ...
  }
}
```

This approach was the default one in earlier versions of the library and is still the nicest looking, 
but since the switch to read only models, code completion in the IDE does not work anymore. The code, however still works,
but is not longer valid for static type checking.

This problem could be solved by providing an small dsld / gdsl script or a custom IDE plugin.

##### Reuse syntax

By using an actual `create` call on the target type, the target object is first created and than applied to field-method:

```groovy
Config.create {
  main SubElement.create {
    ...
  }
}
```

There is, however one small differences in the timing with this approach. With the normal approach, the owner
reference of the inner object is set __before__ the configuration closure is called, meaning that the closure can
access the owner field or methods which use it. With the second approach, the owner is set __after__ the create closure
is completed.


### Collections of DSL Objects

Collections of DSL-Objects are created using a nested closure. The name of the (optional) outer closure is the field name, the
name of the inner closures the element name (which defaults to field name minus a trailing 's'). The syntax for adding
keyed members to a list and to a map is identical (obviously, only keyed objects can be added to a map).

__Since 0.98, these methods are only usable inside of an `apply` or `create` block.__

The inner creator can also take an existing object instead of a closure, which adds that object to the collection.
In that case, **the owner field of the added object is only set, when it does not yet have an owner**.
 
This syntax is especially useful for delegating the creation of objects into a separate method.

As with simple objects, the inner closures return the existing object for reuse

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

def objectForReuse = UnKeyed.create { name "reuse" }
def anotherObjectForReuse

def createAnObject(String name, String value) {
    Keyed.create(name) { value(value) }
}

Config.create {
    elements {
        element {
            name "an element"
        }
        element {
            name "another element"
        }
        element objectForReuse
    }
    keyedElements {
        anotherObjectForReuse = keyedElement ("klaus") {
            value "a Value"
        }
    }
    mapElements {
        mapElement ("dieter") {
            value "another"
        }
        mapElement anotherObjectForReuse // owner is NOT changed
        mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
    }
}

// flat syntax without nested closures:
Config.create {
    element {
        name "an element"
    }
    element {
        name "another element"
    }
    element objectForReuse
    anotherObjectForReuse = keyedElement ("klaus") {
        value "a Value"
    }
    mapElement ("dieter") {
        value "another"
    }
    mapElement anotherObjectForReuse // owner is NOT changed
    mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
}
```

#### Polymorphic collection members

As with [Polymorphic DSL members](#polymorphic-dsl-members), members of collections can also be of subclasses of the declared types. The
same mechanisms for single members can be used for collections, too (with the same caveats as above).

Also, a more powerful approach is available using the [[Alternatives Syntax]].

## On collections

Although most examples in this wiki use `List`, basically any class implementing / sub interface of `Collection` can be 
used instead. There are a couple of points to take note, however:
 
- The default Java Collection Framework interfaces (Collection, List, Set, SortedSet, Stack, Queue) work out of the box
- When using a custom collection **class** or **interface**, in order for initial values to be provided, `List` must be 
  coerced to your custom type, i.e. the code `[] as <YourType>` must be resolvable. This can be done by
    - enhance the `List.asType()` method to handle your custom type
    - in case of a custom class, provide a constructor taking an `Iterable` (or `Collection` or `List`) argument
    
However, it is strongly advised to only take the basic interfaces. If additional functionality is needed, it might make more
sense to apply it using a decorator (for example using KlumWrap) after the object is constructed.

For maps, **only `Map` and `SortedMap` is supported**.

**Be careful when using a simple `Set`.** Since Klum creates barebone hashcode implementations
 (constant zero for non-keyed objects, hashCode of key for keyed objects), a (non Sorted)`Set` of
 non-Keyed model objects might result in a severe degradation of performance of that Set.

# the @Key annotation

The key annotation is used to designate a special key field, making the annotated class a keyed class. This has the
following consequences:

- no setter method is generated for the key field
- a constructor using a single field of the key type is created (currently, only String is allowed)
- factory and apply methods get an additional key parameter
- only keyed classes are allowed as values in a Map

# The @Owner annotation

DSL-Objects can have an optional owner field, decorated with the `@Owner` annotation.

When the inner object (containing the owner) is added to another dsl-object, either directly or into a collection,
the owner-field is automatically set to the outer instance.

This has two dangers:

- no validity checks are performed during transformation time, instead the type of the owner is verified during 
  runtime. If the type does not match, it is silently ignored.
- If an object that already has an existing owner is reused, the owner is not overridden, but silently ignored. I.e. the first
  object that an object is assigned to, is the actual owner.

```groovy
@DSL
class Foo {
    Bar bar
}

@DSL
class Bar {
    @Owner Foo owner
}

def c = Config.create {
    bar {}
}

assert c.bar.owner === c
```

