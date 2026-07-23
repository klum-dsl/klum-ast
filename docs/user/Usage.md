# Usage

KlumAST enhances quasi-static models with DSL methods at Groovy compile time. Annotate Schema classes with `@DSL` and
the transformation is picked up during compilation. For a new 4.0 project, use the Gradle plugin setup below; it supplies
the compatible KlumAST, Groovy, and test dependencies. [[Terms]] defines the Schema, Model, and Consumer responsibilities
used in the project shapes below.

## Gradle setup (supported)

Apply the schema plugin to a Schema project. It imports the matching KlumAST BOM and configures the compiler/runtime
dependencies and Groovy convention:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<matching-klum-version>'
}

klumSchema {
    groovyVersion = 3
}
```

For a new project, Groovy 3 is the baseline. Keep an existing supported Groovy line instead. See [[Getting Started|Gradle onboarding]] for
the complete first Schema and test, [[Gradle Plugins]] for plugin details, and the model plugin when a separate configured
Model artifact is required.

## Project setup

In a typical scenario, you will have two or three disjunct projects.

### Schema - Model - Consumer

This is a typical scenario for Unifying DevOps Models. In this scenario, three distinct projects are created:

#### Schema

The schema contains the 'Schema' of the model (comparable to an XSD file), i.e. all the classes annotated with `@DSL`.
Usually, this model will also contain unit tests. It will end by uploading the complete schema as a jar file into some
kind of artifact repository.

It is possible to split a Schema into multiple projects. Some advanced features require source-level dependencies: if
`MainSchema` needs classes from `AuxSchema`, compile the relevant Schema sources together rather than depending on
`AuxSchema.jar`. Until plugin support exists, this is an advanced manual build arrangement, for example by supplying the
Auxiliary Schema's source JAR to the joint compilation. [#548](https://github.com/klum-dsl/klum-ast/issues/548) tracks a
supported Gradle workflow for this case.


#### Model

The schema usually consists of one or more script files containing either a `Model.Create.With` statement or, even more 
 convenient, only the content of the `Create.With` closure (see [[Convenience Factories#delegating-scripts]]).
 
The model is than packed into a jar file (for convenience a shadowed jar containing the schema as well), which can 
 be used as the single dependency for all consumers.

If the model has single entry points, i.e. instances of classes that are only present once in the
model (which is usually the case), the model can make use of the new `Create.FromClasspath` feature, see
[[Convenience Factories#classpath]] for details.
 
#### Consumer

The consuming projects (Docalot, Jenkins Pipeline, etc.) in itself have a dependency on the model jar. The can instantiate
the main model by using `<MainSchemaClass>.Create.FromClasspath()` or one of the `<MainSchemaClass>.Create.From(...)`
variants for a script class, URL, text or classloader-backed source.

### Schema - Consumer

This scenario is similar to the above scenario, however, schema and consumer are in the same project. 

A typical usage of this scenario is configuration files. For example, Docalot consists of a configuration schema as well
as action classes using a given model.

A document generation project itself consists of a model (the configuration file), which makes use of the the schema.

### All-in-One

In the all-in-one scenario, Schema, configured Model, and Consumer live in one Gradle project. This is a valid approach
when the project benefits from a single build and does not need separately published Schema or Model artifacts. For an
IntelliJ project, apply the Schema plugin and keep the Schema and consumer sources in the usual Gradle source roots:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<matching-klum-version>'
}

klumSchema {
    groovyVersion = 3
}
```

After changing a Schema, refresh the generated `Foo_DSL` source mirrors and reload the Gradle project:

```shell
./gradlew createKlumDslSourceMirrors
```

The mirrors provide completion metadata and are not build or publication inputs. [AnnoDoc Support for IntelliJ IDEA](https://github.com/blackbuild/annodoc-intellij)
provides the complementary Quick Documentation view for compiled declarations; install it according to its current
release instructions when you choose to use it. This 4.0 onboarding route remains a preview until a real-project field
test after the first RC confirms it; [#469](https://github.com/klum-dsl/klum-ast/issues/469) owns that evaluation.
 
 
### Schema - Model - Decorator/Consumer

This approach keeps the model nice and generic. The basic idea is that the consumer does not use the model directly,
but instead a decorated version of it providing consumer specific functionality (and thus embracing the _Interface 
Segregation Principle_). In this setup, decorator and consumer are usually in the same project.

Decorators in Groovy can be implemented using the `@Delegate` annotation, which unfortunately does not provide proper
support for decorated collections. To allow nicer delegates is the goal of the Klum-Wrap project.

### Layer3 structure: API - Schema - Model

This approach is similar to the above approach, but adds an additional layer of abstraction. The API layer contains a generic API that is directly consumed, while the Schema makes the modelling easier. See [[Layer3]] for details.

## Manual dependencies

Use direct dependencies only when the Gradle plugins cannot be used, for example while maintaining an existing custom
build. `klum-ast` is needed at compile time for the transformation; the completed model needs `klum-ast-runtime` at
runtime. This is an advanced setup path, not a second onboarding route.

### Gradle

```groovy
dependencies {
  compileOnly 'org.apache.groovy:groovy:<groovy-version>'
  compileOnly 'com.blackbuild.klum.ast:klum-ast:<klum-version>'
  api 'com.blackbuild.klum.ast:klum-ast-runtime:<klum-version>'
}
```

This needs the Java Library plugin (or an equivalent public `api` configuration) so schema consumers receive the runtime
types. Groovy 3 uses `org.codehaus.groovy` coordinates; Groovy 4 and 5 use `org.apache.groovy`. Prefer the schema plugin
above because it selects matching KlumAST, Groovy, and Spock dependencies through the BOM.

### Maven (auxiliary)

```xml
<dependencies>
 <dependency>
  <groupId>com.blackbuild.klum.ast</groupId>
  <artifactId>klum-ast</artifactId>
  <version>...</version>
  <optional>true</optional>
 </dependency>
 <dependency>
  <groupId>com.blackbuild.klum.ast</groupId>
  <artifactId>klum-ast-runtime</artifactId>
  <version>...</version>
  <scope>runtime</scope>
 </dependency>
 ...
</dependencies>
```

Maven builds also need Groovy compilation and a matching Groovy dependency, for example through GMavenPlus. Consult the
example projects for the complete custom-build setup.
