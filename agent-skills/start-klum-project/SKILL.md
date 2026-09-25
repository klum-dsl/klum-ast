---
name: start-klum-project
description: Bootstrap one direct-schema KlumAST Gradle project from public 4.0.1 coordinates. Use when an adopter needs a first Schema, completed Model, and focused validation test without a KlumAST checkout.
---

# Start a direct-schema KlumAST project

Read [the frozen 4.0.1 authority record](references/klum-4.0.1.md) before editing. Its exact release, imports, Builder guidance, and validation contract govern this workflow. Keep the skill directory together when copying it into an agent client's skill-discovery location.

1. Inspect the adopter's Gradle build, Java toolchain, Groovy line, repository policy, and existing test framework. Confirm that Schema types are the intended consumer API. If clients need a separate stable Domain API, use the [Layer 3 guide](https://klum-dsl.github.io/klum-ast/4.0.1/Layer3/) instead of silently adding projects here.
2. For a new project, make one Gradle Schema module. Apply `com.blackbuild.klum-ast-schema` at **4.0.1**, resolve plugins through the Gradle Plugin Portal and products through Maven Central, use Java 17 or newer and Groovy 3 for the first feedback loop. Preserve a supported existing Groovy line and the project's repository policy when adapting a project. Do not add a KlumAST included build, local product JAR, or `mavenLocal()` as a substitute for public coordinates.
3. Put a small, named-package `@DSL` Schema in `src/main/groovy`. Use the authority record's canonical `com.blackbuild.klum.ast` imports. Use `@Required` for presence and `@Validate` for an actual predicate or method rule. Build owned children through the parent Builder callback.
4. Put one focused test in `src/test/groovy` using the project's established framework. The 4.0.1 Schema plugin supplies a matching Spock setup for a new project. Construct through `Type.Create.With`, then assert the returned completed Model's public state. Add one invalid case with an explicit validation message; catch `com.blackbuild.klum.ast.runtime.validation.KlumValidationException` and assert only the distinctive semantic message fragment.
5. Run the project's Gradle `test` task. Resolve ordinary compilation and test failures first. If a diagnostic concerns Builder versus completed Model state, consult the authority record's Builder-first guide and make the targeted correction. Do not preemptively migrate working code.

Record the exact KlumAST plugin version, JDK, Gradle, Groovy line, test result, and any friction in the adopter project's notes. A local KlumAST checkout or composite build is useful for product development but does not prove this public-coordinate route. The repository's [copyable public-coordinate mission](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/fixtures/direct-schema-public-4.0.1) gives a small example; it is not a required template.

After the one-module direct-schema route, a team that needs a separate Domain API for generic clients can follow the optional [domain-first smart-home Layer 3 showcase](https://github.com/klum-dsl/klum-catwalk/tree/519404ebc259e24bb24086f86c2ef6322d8bcbb7/showcases/domain-first-smart-home). Its independent Domain API, Schema, API-only Client, and Model leaves use public 4.0.1 coordinates and demonstrate their artifact handoff. KlumAST's version-matched documentation and this skill remain the authority for concepts and setup; Catwalk supplies the executable consumer example, not a prerequisite for this bootstrap.
