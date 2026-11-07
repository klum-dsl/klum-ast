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

A `DelegatingScript` executes its bare configuration calls against the receiving factory's Builder at runtime.
For IntelliJ completion and navigation, KlumAST 4.1 provides an explicit Schema-owned suffix mapping and a separate
editor-only metadata path. Follow [IntelliJ completion for DelegatingScripts](Portable-GDSL.md) for source and binary
consumers, refresh tasks, and the qualified IDE boundary. A bare `DelegatingScript` without that opt-in still does not
identify its concrete Builder to IntelliJ.

### Compiled root recipe overlays

`Create.From(recipeClass) { ... }` configures one ordinary root from a compiled `DelegatingScript`, then applies the
trailing closure to the same Builder before its single root `PostApply`. The closure uses the exact generated public
Builder delegate with `DELEGATE_ONLY`. A keyed root keeps the recipe class's simple name as its immutable key.

(See: `RootRecipeOverlayDocumentaryTest#'binds a selected completed environment before application validation'`.)

```groovy
@DSL class OrderEnvironment { String region }
@DSL class OrderApplication {
    @Key String name
    @Required @Field(FieldType.LINK) OrderEnvironment environment
    String service
    String image
    @Default void chooseImage() { image ?= "${service}:${environment.region}" }
    @Validate void valid() { assert image.endsWith(environment.region) }
}

// OrderApplicationScript is a separately compiled DelegatingScript with body: service 'orders'.
def selectedEnvironment = OrderEnvironment.Create.With(region: 'eu')
def application = OrderApplication.Create.From(OrderApplicationScript) {
    environment selectedEnvironment
}
assert application.environment.is(selectedEnvironment)
assert application.name == 'OrderApplicationScript'
assert application.image == 'orders:eu'
```

The same overlay is available when the [classpath marker](#classpath) selects the compiled recipe. For example,
`META-INF/klum-model/orders.OrderApplication.properties` contains `model-class=orders.OrderApplicationScript`.
Both the marker and script class are loaded with the current thread's context class loader, or the explicit loader:

(See: `RootRecipeOverlayDocumentaryTest#'binds a selected environment to a classpath recipe with explicit loader=#explicitLoader'`.)

```groovy
def selectedEnvironment = OrderEnvironment.Create.With(region: 'eu')
def application = OrderApplication.Create.FromClasspath {
    environment selectedEnvironment
}
def another = OrderApplication.Create.FromClasspath(modelLoader) {
    environment selectedEnvironment
}
assert application.environment.is(selectedEnvironment)
assert another.environment.is(selectedEnvironment)
assert application.name == 'OrderApplicationScript'
```

Marker lookup uses the Factory's effective Model type, including its default implementation. The script class's simple
name supplies a keyed root's identity; the marker filename and overlay do not change that authority. Discovery and
execution failures retain the ordinary classpath diagnostics and original causes.

Current scoped Templates and root initialization/`PostCreate` run before the recipe. The overlay therefore cannot supply
context to those operations or to recipe-created children's immediate callbacks. Root `PostApply`, graph phases,
materialization, required validation and verification see the overlay. Ordinary scalar and LINK setters replace earlier
values; collection adders append normally. Deferred actions and later lifecycle callbacks retain their ordinary authority.
The completed external LINK keeps its exact identity and is excluded from owned lifecycle traversal.

Each invocation uses a fresh script and a clone of the overlay before changing its delegate or resolution strategy.
Captured mutable state remains caller-owned. Failure aborts the ordinary construction session; later invocations remain
usable. Independent nested roots remain invalid.

Direct compiled `DelegatingScript` classes and classpath recipes accept closure-only overlays. Regular Script classes
are rejected before construction/execution; use their existing no-overlay `Create.From(scriptClass)` route or supply a `DelegatingScript`
recipe. Classpath regular Scripts likewise retain their no-overlay `Create.FromClasspath()` /
`Create.FromClasspath(loader)` routes.
Named-map, File/URL/text and `FromStruct` overlays, Template and `AsBuilder` overlays are unavailable.
Recompile existing Schemas to expose the new generated members; existing no-overlay binary calls remain supported.
Java and static/dynamic Groovy clients consume the same generated Factory contract from compiled Schema artifacts.
See [Factory contracts and packaging](Factory-Classes.md#compiled-root-recipe-overlays) and
[migration](Migration.md#compiled-root-recipe-overlays).

### Optional IntelliJ completion for one script family

Declare the intentional `.environment.groovy` suffix for `example.Environment` in the Schema's `klumSchema.gdsl`
configuration; Model consumers opt in and explicitly select that Schema's metadata. This replaces the unreleased
manual copied-resource recipe. Use the [portable mapping workflow](Portable-GDSL.md#declare-the-schema-mapping)
instead of placing a contributor in ordinary Schema or Model resources. The qualified path uses regular non-nested
Models; nested mirror/publication preparation is separately tracked in
[#826](https://github.com/klum-dsl/klum-ast/issues/826).

For example, the Schema's Model is deliberately unkeyed:

```groovy
package example

import com.blackbuild.klum.ast.DSL

@DSL
class Environment {
    String region
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

IntelliJ resolves operations from the actual public `Environment_DSL.Builder`, through refreshed source mirrors
or compiled Schema classes. The suffix is an editor hint: it does not choose the runtime factory or alter dispatch.
A matching recipe executed against another Model can still fail. See the
[project-wide mapping and failure rules](Portable-GDSL.md#project-wide-mappings-and-failures) before changing suffixes
or Schema versions.

An ordinary Model script can also use a typed factory call:

```groovy
Environment.Create.With {
    region 'eu'
}
```

That call exposes the generated Builder through source mirrors or compiled Schema classes and creates a completed
Model. A `DelegatingScript` recipe can instead configure a Builder inside an active Construction session through the
existing Builder-producing factory route below.

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
[opt-in IntelliJ suffix mapping](Portable-GDSL.md) also applies to these recipe files.

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

## Structured documents and bodies

`Create.FromStruct(document[, sourceName])` constructs a completed root from a caller-parsed Map. Keyed Factories also
accept `Create.FromStruct(rootKey, body[, sourceName])`; this body excludes the root key. The optional source name is
nonempty diagnostic text, never a filename-derived identity or resource to load. See [Factory roots](Structured-Input.md#factory-roots)
for Java/Groovy examples and lifecycle, and [active Builders](Structured-Input.md#apply-to-an-active-builder) for inherited
void `applyStruct`. Reusable keyless recipes use [Template FromStruct](Templates.md#structured-input-templates).

`FromMap` remains unchanged: its copy, type-hint, conversion and custom-Factory behavior is distinct from strict structured
operation interpretation. No parser, file/URL overload or `AsBuilder.FromStruct` is provided.
