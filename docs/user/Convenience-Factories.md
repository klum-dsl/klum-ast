# Convenience factories

Convenience factory methods load a configuration directly from scripts, text, files, URLs, maps, or a classpath marker.

## Script classes

`MyConfig.Create.From(Class<Script>)` runs the given `Script` and returns the result. The script must return the
proper type, for example:

```groovy
MyConfig.Create.With {
  value("bla")
}
```

## Delegating Scripts

If the target script is a subclass of `DelegatingScript`, its body is considered the content of the creation closure.
For keyed classes, the key value is the script class's simple name.

To create a delegating script, include it explicitly with an annotation:

```groovy
@BaseScript DelegatingScript base

name 'Klaus'
...
```

or configure `GroovyClassLoader` / `GroovyShell` with a `BaseScript` (see the Javadoc of `DelegatingScript` for details).

For a [[Usage#schema---model---consumer]] setup, the most convenient solution is to configure the Model project with a
compiler customizer.

See the example projects for details.

## Script and delegating script for collections and maps

For DSL element maps and collections, there is also a convenience method for creating multiple elements
from a couple of scripts, each element in a single script. The generated method has the form
`<fieldName>(Class<? extends Script>...)` for both maps and collections.

`Element.Create.AsBuilder.From(MyDelegatingScript)` now applies a `DelegatingScript` recipe to an unsealed Builder in the
active root Construction session. It is intended for owning relationship machinery and does not start or complete a nested
lifecycle. A regular Script that returns a completed model is an opaque materializing program and remains top-level-only.

The generated collection/map overloads shown below route `DelegatingScript` classes through that active-session primitive,
attach each Builder to the owner, and preserve keyed-map behavior. Regular Scripts that return completed models remain
opaque and are rejected with migration guidance. See
[ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md).

The intended `DelegatingScript` behavior allows splitting a bigger model into separate files:

With the DSL

```groovy
@DSL class Container {
    List<Element> elements
}

@DSL class Element { ... }
```

And the model:

```groovy
Container.Create.With {
    elements(AScript, AnotherScript, ThirdScript)
}
```


## Text
`MyConfig.Create.From(text)` or `MyConfig.Create.From(key, text)` handles the given text as the content of the creation
closure.

For example

```groovy
def config = Config.Create.From(new File("bla.groovy").text)
```

and the file bla.groovy
```groovy
value("blub")
```

result in the following:

```groovy
assert config.value == "blub"
```

`Create.From()` can also take an optional class loader as its last parameter:

```groovy
Config.Create.From(content, Config.class.classLoader)
```

If no class loader is given, the current context class loader is used.


## File or URL

Instead of text, a `File` or `URL` can be given; for a keyed object, the key is derived from the filename
(the first segment, in the example above, the key would be "bla"). By using a small dsld-snippet in your IDE, you even 
get complete code completion and syntax highlighting an specialized config files.

This allows splitting configurations into different files, which might be automatically resolved by something like:
 
```groovy
Config.Create.With {
    environments {
        new File("envdir").eachFile { file -> 
            environment(Environment.Create.From(file)) 
        }    
    }
}
```
 
__Note__: `Create.From` does not support polymorphic creation. This might be added later,
 see: ([#43](https://github.com/klum-dsl/klum-core/issues/43))

As with `Create.From(text)`, `Create.From(File|URL)` supports an additional class-loader parameter as well.

## Classpath

Classpath discovery instantiates a model automatically from a properties file in the Model library. The Model needs one
or more single entry points: instances that are usually present only once, such as the encompassing `Config` object.

By placing a marker on the classpath, a consumer can instantiate this class without knowing the configuration class name.
This moves the dependency from code into JAR orchestration or the build script.

Given the following classes:

`Model.groovy`:
```groovy
package pk
@DSL class Model {
    // ... definitions, inner elements etc.
}

```

`Configuration.groovy`:
```groovy
package impl
Model.Create.With {
  // regular dsl code
}
```

By including a separate properties file in the JAR of [[Usage#model]], this Model can automatically be instantiated. The
file must be named `/META-INF/klum-model/<schema-classname>.properties` and contain the single `model-class` property,
whose value is the fully qualified class name of the entry-point script (either a regular `Script` or a
`DelegatingScript`):

`/META-INF/klum-model/pk.Model.properties`
```properties
model-class: impl.Configuration
```

This allows the code consuming the model to simply obtain it via:

```groovy
def model = Model.Create.FromClasspath()
```

Using this technique, the same consumer can work with different Models (often from different packages) without changing
or injecting the Model class name.

## Map

Using `FromMap`, an object can be created from a `Map`. This is a form of “poor man's deserialization,” where each entry
in the `Map` is mapped to a similarly named field of the new object. The object's class can be overridden with the
special `@type` key in the `Map`, either as a fully qualified class name or as a name relative to the base type's
package. The type can also use the stripped name defined by `@DSL.stripSuffix()`.

`@Owner` and `@Role` fields are not set during creation. Because `FromMap` is a regular creator method, objects created
by it undergo the regular lifecycle phases, including owner, role, and default-value handling.

Builder-producing extension paths can use `Create.AsBuilder.FromMap(map)` during an active root Construction session. The
result must be attached to an owned relationship in that same session; the outer graph performs ownership, materialization,
and validation.

Library-specific features such as renamed fields can be simulated by overriding `FromMap` in a custom factory and
adjusting the effective `Map` before calling the superclass method.

Creation of inner objects delegates to their respective `FromMap` methods.

```groovy
@DSL class Person {
    String firstName
    String lastName
 
    static class Factory extends KlumFactory.Unkeyed<Person> {
        protected Factory() { super(Person) }

        @Override
        Person FromMap(Map<String, Object> map) {
            Map<String, Object> transformedMap = map.collectEntries { k, v ->
                // transform key from kebap to camel case
             [(k as String).tokenize('-').collect { it.capitalize() }.join('').uncapitalize(), v]
            }
            return super.FromMap(transformedMap)
        }
    }
} 

def person = Person.Create.FromMap(['first-name': 'Klaus', 'last-name': 'Müller'])

assert person.firstName == 'Klaus'
assert person.lastName == 'Müller'
```

For String values, some simple transformations are applied:

- enums are resolved by name
- primitive types are converted via `asType`
- existing [[Converters]] are used to convert the string to the target type
