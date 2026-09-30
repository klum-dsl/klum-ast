# Convenience factories

Convenience factory methods load a configuration directly from scripts, text, files, URLs, maps, or a classpath marker.

## Script classes

`MyConfig.Create.From(Class<Script>)` runs the given `Script` and returns the result. The script must return the
proper type, for example:

(See: `ConvenienceFactoriesDocumentaryTest#'loads a completed deployment from a script class'`.)

```groovy
MyConfig.Create.With {
  value("bla")
}
```

## Delegating Scripts

If the target script is a subclass of `DelegatingScript`, its body is considered the content of the creation closure.
For keyed classes, the key value is the script class's simple name.

To create a delegating script, include it explicitly with an annotation:

(See: `ConvenienceFactoriesDocumentaryTest#'uses a DelegatingScript class as keyed configuration content'`.)

```groovy
@BaseScript DelegatingScript base

name 'Klaus'
...
```

or configure `GroovyClassLoader` / `GroovyShell` with a `BaseScript` (see the Javadoc of `DelegatingScript` for details).

A `DelegatingScript` executes its bare configuration calls against the target's Builder at runtime. IntelliJ currently
does not infer that concrete Builder for bare calls in the script body, so completion and navigation there are incomplete.
This improvement is tracked in [#805](https://github.com/klum-dsl/klum-ast/issues/805).

### Optional IntelliJ completion for one script family

If your project owns a filename convention for scripts that configure one Schema type, you can add an IntelliJ GDSL file
under `src/main/resources` in either the Schema or Model project. This is optional Schema- or Model-owned IntelliJ
configuration; KlumAST does not install this contributor for every `DelegatingScript`. For example, given
`src/main/groovy/example/Environment.groovy`:

```groovy
package example

import com.blackbuild.klum.ast.DSL

@DSL
class Environment {
    String region
}
```

Put this **`src/main/resources/environment.gdsl`** in the chosen project and enable it if IntelliJ prompts you.
IntelliJ needs the GDSL on its classpath for reliable discovery. Choose its owner according to the convention:

- **Schema-owned:** keep it in the Schema project's resources when all consuming Models should use the same
  filename-to-Builder mapping. Gradle's standard resource processing normally packages it in the Schema JAR, making it
  available to Model projects that consume that Schema. That propagation is intentional: the mapping becomes part of
  the Schema's effective editor contract.
- **Model-owned:** keep it in the Model project's resources when the naming convention belongs only to that Model
  project. Resource processing may package it in the Model JAR, but it does not propagate with the reusable Schema.

Replace `example.Environment_DSL.Builder` with the real generated Builder contract for your Schema. Use a stable,
intentional suffix, update a Schema-owned contributor when its Schema type or convention changes, and avoid a suffix that
could match scripts for another target type:

```groovy
contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('.environment.groovy'))
        delegatesTo(findClass('example.Environment_DSL.Builder'))
}
```

Then write **`catalog.environment.groovy`** as the `DelegatingScript` recipe:

```groovy
package example

import groovy.transform.BaseScript
import groovy.util.DelegatingScript

@BaseScript DelegatingScript base

region 'eu'
```

For example, `Environment.Create.From(new File('catalog.environment.groovy'))` runs that file as an Environment recipe.
This `Environment` is deliberately unkeyed. For a keyed Schema (for example, after adding `@Key String name`), the
creation route matters: `Create.From(File)` uses `catalog.environment` as the default key because it removes only the
final `.groovy` extension, while `Create.From(scriptClass)` uses the compiled script class's simple name,
`catalog_environment`. The same class-derived key applies when `Create.FromClasspath()` loads that script class.
To choose `catalog` for the file route, supply the existing key provider explicitly:

```groovy
Environment.Create.From(new File('catalog.environment.groovy'), { File ignored -> 'catalog' })
```

If you compile the recipe and use `Create.FromClasspath()`, put a marker at
`META-INF/klum-model/example.Environment.properties` with `model-class: example.catalog_environment`.
The marker names the **actual compiled script class**, not `catalog.environment.groovy`; the dotted filename produced
`example.catalog_environment` in Groovy 3, 4, and 5. Keep the marker in the classpath that loads that script.

IntelliJ uses the GDSL only to offer and resolve operations from the existing `Environment_DSL.Builder`; it does not
change Groovy compilation or `DelegatingScript` runtime dispatch. The generated contract must be visible to the IDE,
either through refreshed Schema source mirrors or compiled Schema classes. After renaming the Schema or changing its
operations, refresh the mirrors and update this Schema- or Model-owned GDSL as needed.

Check the boundary in IntelliJ: `catalog.environment.groovy` should offer `region` and navigate to the generated Builder
contract, while `catalog.other.groovy` should not gain that completion from this contributor. A script with the matching
suffix that actually runs against another Schema type would receive misleading suggestions; use a separate suffix and
contributor for that type. This example is specific to IntelliJ GDSL and does not claim editor support elsewhere.

This manual recipe is an interim option under [#805](https://github.com/klum-dsl/klum-ast/issues/805). A future
KlumAST Gradle-plugin facility could generate, materialize, and register a contributor from an explicit Schema
declaration, but its design needs a separate decision under that issue.

For the currently supported IntelliJ completion and navigation path, an ordinary Model script uses a typed factory call:

```groovy
Deployment.Create.With {
    environment 'production'
}
```

That call exposes the generated Builder contract through refreshed source mirrors in a Schema project or compiled Schema
classes in a separate consumer. It creates a completed Model; it is not interchangeable with a `DelegatingScript` recipe
that configures a Builder inside an active Construction session.

For a [Usage#schema---model---consumer](Usage.md#schema---model---consumer) setup, the most convenient solution is to configure the Model project with a
compiler customizer.

See the example projects for details.

## Script and delegating script for collections and maps

For DSL element maps and collections, there is also a convenience method for creating multiple elements
from a couple of scripts, each element in a single script. The generated method has the form
`<fieldName>(Class<? extends Script>...)` for both maps and collections.

`Element.Create.AsBuilder().From(MyDelegatingScript)` now applies a `DelegatingScript` recipe to an unsealed Builder in the
active root Construction session. It is intended for owning relationship machinery and does not start or complete a nested
lifecycle. A regular Script that returns a completed model is an opaque materializing program and remains top-level-only.

The generated collection/map overloads shown below route `DelegatingScript` classes through that active-session primitive,
attach each Builder to the owner, and preserve keyed-map behavior. Regular Scripts that return completed models remain
opaque and are rejected with migration guidance. See
[ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md).

The intended `DelegatingScript` behavior allows splitting a bigger model into separate files:

(See: `ConvenienceFactoriesDocumentaryTest#'applies DelegatingScript recipes to list and map relationship factories'`.)

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

Materializing regular Script programs remain deliberately covered by the focused rejection tests in
`ConvenienceFactoriesSpec`.


## Text
`MyConfig.Create.From(text)` or `MyConfig.Create.From(key, text)` handles the given text as the content of the creation
closure.

For example (see: `ConvenienceFactoriesDocumentaryTest#'loads keyed configuration from text'`):

```groovy
given:
def content = '''
    value("blub")
'''

when:
def config = Config.Create.From(content)

then:
assert config.value == "blub"
```

`Create.From()` can also take an optional class loader as its last parameter:

```groovy
Config.Create.From(content, Config.class.classLoader)
```

If no class loader is given, the current context class loader is used.


## File or URL

Instead of text, a `File` or `URL` can be given; for a keyed object, the key is derived from the filename
(the first segment, in the example above, the key would be "bla"). The same
[`DelegatingScript` IDE limitation](#delegating-scripts) applies to these scripts.

This allows splitting configurations into different files, which might be automatically resolved by something like:

(See: `ConvenienceFactoriesDocumentaryTest#'derives a keyed configuration name from a file or URL'`.)

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

(See: `ConvenienceFactoriesDocumentaryTest#'discovers a deployment entry point from its classpath marker'`.)

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

By including a separate properties file in the JAR of [Usage#model](Usage.md#model), this Model can automatically be instantiated. The
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

Builder-producing extension paths can use `Create.AsBuilder().FromMap(map)` during an active root Construction session. The
result must be attached to an owned relationship in that same session; the outer graph performs ownership, materialization,
and validation.

Library-specific features such as renamed fields can be simulated by overriding `FromMap` in a custom factory and
adjusting the effective `Map` before calling the superclass method.

Creation of inner objects delegates to their respective `FromMap` methods.

(See: `ConvenienceFactoriesDocumentaryTest#'adapts external map keys in a custom factory'`.)

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
- existing [Converters](Converters.md) are used to convert the string to the target type
