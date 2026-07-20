# Gradle onboarding (4.0 preview)

> This is a 4.0 preview pending field testing. Read the documentation that matches the KlumAST version you adopt; #456 owns versioned documentation and Javadocs.

## Choose the model shape first

Before creating a Schema, answer two independent questions.

1. What drives the Schema?
   - **Domain-first**: the completed model is the product's domain abstraction; adapters to target systems remain downstream.
   - **Target-contract**: an external contract such as Helm values is authoritative; the Schema provides validated authoring and useful defaults without claiming to replace that contract.
2. Do client consumers need a distinct stable Domain API?
   - **Layer 3**: a Domain API Developer defines that contract before a Schema Developer realizes it; Client Developers do not compile against Schema types.
   - **Direct-schema**: Schema types are the consumer-facing API, and the Schema Developer also owns that role.

Record the choices near the project architecture. Layer 3 is a modeling pattern, not a requirement for every Gradle project.

## Create the Gradle project

Apply the schema plugin to a Schema project. It supplies the KlumAST BOM, compiler/runtime dependencies, Groovy convention, and sources/Javadocs.

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<matching-klum-version>'
}

klumSchema {
    groovyVersion = 3
}
```

For a new project, Groovy 3 is the justified default: it is KlumAST's baseline and keeps the first build small. Keep an existing supported Groovy line instead. Groovy 3 uses `org.codehaus.groovy`; Groovy 4 and 5 use `org.apache.groovy`. The plugin selects matching Groovy and Spock dependencies.

Place Schema classes in `src/main/groovy`, add one root `@DSL` type, and write a test in `src/test/groovy` that constructs a completed model through `Create.With`. Run `./gradlew test` before expanding the model. Use the model plugin only when a separate configured-model artifact is needed; see [[Gradle Plugins]].

```groovy
@DSL
class Deployment {
    @Key String name
    String environment
    Service service
}

@DSL
class Service {
    String image
}

def deployment = Deployment.Create.With('catalog') {
    environment 'production'
    service { image 'catalog:1.0' }
}
```

`deployment` is the completed model. The callback configures its Builder; owned children must be created through the parent callback. See [[Builder First Migration]] for the construction boundary and [[Validation]] for domain invariants.

## IntelliJ and generated DSL support

Import the project as a Gradle project. After changing a Schema, run the explicit mirror refresh task, then reload Gradle:

```shell
./gradlew createKlumDslSourceMirrors
```

In a multi-project build, use the qualified task path such as `./gradlew :schema:createKlumDslSourceMirrors`. The generated `Foo_DSL` mirrors provide IntelliJ completion only. They are not compiled, packaged, published, or added to downstream classpaths.

Quick Documentation for compiled declarations is separate from these mirrors. [AnnoDoc Support for IntelliJ IDEA](https://github.com/blackbuild/annodoc-intellij) is currently a locally installable `0.1.0-alpha.1` release candidate; Marketplace publication is pending explicit maintainer approval. Follow its current installation instructions only when you choose to install it. Other IDEs should use their ordinary Gradle import and compilation support; KlumAST makes no unverified IDE-parity claim.

## Portable adopter skills

The repository's [`agent-skills/`](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills) distribution contains portable, task-oriented workflows for `start-klum-project`, `author-klum-model`, and `feature-advisor`. Copy selected standard skill directories into the discovery location supported by your agent client; do not copy repository-maintainer skills from `.agents/skills/`.

`feature-advisor` is a KlumAST-specific, evidence-based improvement review. It stays read-only unless you request selected changes, then explains the supported feature, fit, benefit, trade-offs, confidence, migration risk, effort, documentation source, and validation result.

The minimal fixture at `agent-skills/fixtures/minimal-gradle-project` exercises the baseline locally against the 4.0 sources. Its `ADOPTER-DRY-RUN.md` captures the setup, Model Writer, and feature-advisor field-test flow, including a deliberately improvable Schema case.
