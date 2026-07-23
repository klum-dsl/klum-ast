# FAQ

## Why an AST and not just use a ModelBuilder?

A Groovy `ModelBuilder` relies heavily on dynamic methods and properties. In comparison, KlumAST generates a statically
described construction API that can be used with `@TypeChecked` or `@CompileStatic`. It also exposes
`@DelegatesTo` metadata that modern IDEs can use for Builder-closure assistance.

## Why don't I get code completion in my IDE?

For a current IntelliJ setup, use the Gradle Schema plugin and refresh the generated source mirrors after Schema changes:

```shell
./gradlew createKlumDslSourceMirrors
```

The mirrors provide generated declaration metadata for completion; they are not compiled or published source. For Quick
Documentation on compiled declarations, install [AnnoDoc Support for IntelliJ IDEA](https://github.com/blackbuild/annodoc-intellij)
according to its release instructions. [[Usage#all-in-one]] describes the complete single-project setup. This is the 4.0
preview route; [#469](https://github.com/klum-dsl/klum-ast/issues/469) owns the first-RC real-project field test.

For a separate compiled Schema artifact, a Consumer project can also obtain normal IDE assistance from the generated public
API. The legacy GDSL/DSLD approach is not the current setup path.

## What does the name mean?

KlumAST was formerly called ConfigDSL because its main usage was configuration files. While configuration files remain
one major use case, the project's target has broadened. Hence the renaming.

Klum stands for Konfiguration Library for Unified Modelling — and for turning models into supermodels (_wink, wink_).

## Why another library? What are the differences?

Most DSL approaches fall into two categories:

- Dynamic approaches, such as Groovy's `NodeBuilder`, rely on `methodMissing` and similar features. They can be concise to
  author, but their dynamic surface limits static checks and IDE assistance. External DSL descriptors can supplement some
  IDEs, but do not provide compile-time checking or a portable generated API.

- Hard-wired DSLs define many methods with `Closure` parameters, as parts of the Gradle DSL do. They provide a strong IDE
  experience, but require authors to maintain substantial repetitive construction code.

KlumAST takes the second approach and generates type-safe, static models from annotation-controlled AST transformation,
reducing the boilerplate while retaining an IDE-friendly API.

As an additional bonus, KlumAST separates mutable construction Builders from completed DSL Objects. APIs consuming a
completed model do not see generated mutation methods, making development against a given DSL easier.
