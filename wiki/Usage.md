KlumAST is a Library for enhancing quasi static models with DSL methods. This is done using a Groovy Compile Time AST 
Transformations which are activated by annotation the model classes with the `@DSL` annotation, the AST transformation
 is then automatically picked up and applied during compilation.
 
So the only necessary step is to include KlumAST in the classpath during compilation. Since the modifications are
done directly during the compilation, the KlumAST does not need to be present during runtime, thus the dependency
can either be marked as `optional` for Maven or be part of the `compileOnly` configuration for Gradle.

Use the following snippet to include KlumAST in your project:
 
## Maven
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

Of course, you need to set up compilation for Groovy and the Groovy dependency as well (for example using the GMavenPlus
plugin). See the example projects for a more detailed setup.


## Gradle
For Gradle, setup is similar:  

```groovy
dependencies {
  compileOnly 'org.codehaus.groovy:groovy-all:2.4.12'
  compileOnly 'com.blackbuild.klum.ast:klum-ast:<version>'
  implementation 'com.blackbuild.klum.ast:klum-ast-runtime:<version>'
  ...
}
```
Note that in most cases, the setup can be made a lot easier with the help of the Klum Gradle Plugins, see [[Gradle Plugins]].

# Project setup

In a typical scenario, you will have two or three disjunct projects.

## Schema - Model - Consumer

This is a typical scenario for Unifying DevOps Models. In this scenario, three distinct projects are created:

### Schema

The schema contains the 'Schema' of the model (comparable to an XSD file), i.e. all the classes annotated with `@DSL`.
Usually, this model will also contain unit tests. It will end by uploading the complete schema as a jar file into some
kind of artifact repository.

It is possible to split the schema into multiple projects, but in order for some advanced features to work correctly,
dependencies on other schemas should be on the source level, i.e. if MainModel depends on classes from AuxModel, MainModel 
should _not_ use the MainModel.jar, but instead use MainModel-sources.jar and recompile all classes together.

One goal of the Klum project is to eventually create Gradle / Maven plugins to make that more convenient.


### Model

The schema usually consists of one or more script files containing either a `Model.Create.With` statement or, even more 
 convenient, only the content of the `Create.With` closure (see [[Convenience Factories#delegating-scripts]]).
 
The model is than packed into a jar file (for convenience a shadowed jar containing the schema as well), which can 
 be used as the single dependency for all consumers.

If the model has single entry points, i.e. instances of classes that are only present once in the
model (which is usually the case), the model can make use of the new `Create.FromClasspath` feature, see
[[Convenience Factories#classpath]] for details.
 
### Consumer

The consuming projects (Docalot, Jenkins Pipeline, etc.) in itself have a dependency on the model jar. The can instantiate
the main model by using `<MainSchemaClass>.Create.FromClasspath()` or `<MainSchemaClass>.Create.From(<ConfigurationModelClass>)`.

## Schema - Consumer

This scenario is similar to the above scenario, however, schema and consumer are in the same project. 

A typical usage of this scenario is configuration files. For example, Docalot consists of a configuration schema as well
as action classes using a given model.

A document generation project itself consists of a model (the configuration file), which makes use of the the schema.

## All-in-One

In the all-in-one scenario, all parts of the system are in one project. While this is useful for kickstart scenarios,
the main disadvantage is missing IDE support, which currently does not work (due to a bug in IntelliJ : see 
 [IDEA-162019](https://youtrack.jetbrains.com/issue/IDEA-162019), [IDEA-171012](https://youtrack.jetbrains.com/issue/IDEA-171012)
 and [IDEA-171017](https://youtrack.jetbrains.com/issue/IDEA-171017), and an open task for eclipse: 
 [#14](https://github.com/klum-dsl/klum-ast/issues/14))
 
 
## Schema - Model - Decorator/Consumer

This approach keeps the model nice and generic. The basic idea is that the consumer does not use the model directly,
but instead a decorated version of it providing consumer specific functionality (and thus embracing the _Interface 
Segregation Principle_). In this setup, decorator and consumer are usually in the same project.

Decorators in Groovy can be implemented using the `@Delegate` annotation, which unfortunately does not provide proper
support for decorated collections. To allow nicer delegates is the goal of the Klum-Wrap project.

## Layer3 structure: API - Schema - Model

This approach is similar to the above approach, but adds an additional layer of abstraction. The API layer contains a generic API that is directly consumed, while the Schema makes the modelling easier. See [[Layer3]] for details.
