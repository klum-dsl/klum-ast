---
name: start-klum-project
description: Start or adapt a Gradle project for KlumAST. Use when an adopter wants to scaffold a KlumAST project, add KlumAST to an existing Gradle build, choose domain-first or target-contract modeling, choose Layer 3 or direct-schema structure, or verify a first schema build.
---

# Start a KlumAST Gradle project

Use the version-matched KlumAST documentation as the authority. This skill selects and applies a project shape; it does not replace the reference documentation.

1. Inspect the existing Gradle build, source layout, Java toolchain, Groovy dependencies, tests, and release version. For a new project, ask what drives the Schema:
   - **Domain-first** when the completed model is the product's domain abstraction and adapters are downstream.
   - **Target-contract** when an external contract such as Helm values remains authoritative.
2. Ask whether client consumers need a stable Domain API separate from the Schema:
   - choose **Layer 3** when clients must not depend on Schema types;
   - choose **direct-schema** when the Schema types are the consumer-facing API.
   Record both choices in the project's README or architecture note. Do not invent a Layer 3 API merely for future flexibility.
3. Preserve a supported existing Groovy line. For a new project, choose Groovy 3 unless another supported dependency requires Groovy 4 or 5; it is the smallest compatibility surface and KlumAST's baseline. Groovy 3 artifacts use `org.codehaus.groovy`, while Groovy 4 and 5 use `org.apache.groovy`. Let the Klum Gradle plugin manage the matching Groovy and Spock versions.
4. For a new project, create `settings.gradle` with a root-project name and `build.gradle` with the schema plugin, repository, and selected supported Groovy line. For an existing project, add the same configuration without replacing its repository, toolchain, or dependency policy. Apply `com.blackbuild.klum-ast-schema` to the Schema project. Use the released plugin version chosen for the adopted KlumAST documentation; it supplies the Klum BOM, compiler, runtime, Groovy conventions, and sources/Javadocs. Keep Schema types in `src/main/groovy` and executable examples in `src/test/groovy`.
5. For a separate configured-model project, apply `com.blackbuild.klum-ast-model`, declare its Schema dependency through `klumModel.schemas`, and register top-level scripts. For direct-schema projects, start with one Schema project instead.
6. Add one small `@DSL` type and one test that constructs it through `Type.Create.With`. Run `./gradlew test`; diagnose ordinary Gradle or schema errors before adding more features.
7. In IntelliJ IDEA, import the project as a Gradle project. After Schema changes, run `./gradlew createKlumDslSourceMirrors` (or its qualified subproject path) and reload the Gradle project. Mirrors provide completion for generated `Foo_DSL` interfaces only: they are not compiled, packaged, published, or downstream inputs. Compiled Quick Documentation is a separate AnnoDoc Support concern.
8. For AnnoDoc Support for IntelliJ IDEA publication and installation status, follow the current [Gradle onboarding guidance](https://klum-dsl.github.io/klum-ast/4.0.0/Getting-Started/#intellij-and-generated-dsl-support). Do not claim Marketplace availability without checking that source. Other IDEs should use normal Gradle import and compilation; this skill makes no parity claim.

Read the matching [Gradle Plugins](https://klum-dsl.github.io/klum-ast/4.0.0/Gradle-Plugins/), [Basics](https://klum-dsl.github.io/klum-ast/4.0.0/Basics/), [Layer 3](https://klum-dsl.github.io/klum-ast/4.0.0/Layer3/), and [Builder-first migration](https://klum-dsl.github.io/klum-ast/4.0.0/Builder-First-Migration/) guidance before resolving a design question.
