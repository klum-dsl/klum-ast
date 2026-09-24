# Testing Models and Schemas

Schema tests are focused, local tests for the completed Models your Schema constructs. They give a Schema Developer a
fast feedback loop for defaults, relationships, and domain rules without contacting a deployment target. They complement
integration and acceptance tests: keep those outer tests for target contracts, adapters, and environment behavior.

Start with the small Schema Gradle feedback loop in [Gradle Onboarding](Gradle-Onboarding.md): put the Schema in
`src/main/groovy`, its Spock tests in `src/test/groovy`, and run `./gradlew test`. The Schema plugin supplies the normal
KlumAST compiler/runtime and matching Groovy/Spock setup.

For a new one-module direct-schema project, the copyable [`start-klum-project` skill](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/start-klum-project) pins its setup and validation guidance to public 4.0.1. Its [public-coordinate mission](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/fixtures/direct-schema-public-4.0.1) exercises a completed Model and a semantic validation-message assertion with native Spock tests.

## Assert a completed Model

Construct through the generated root factory, then assert the completed Model's public state. The callback configures a
Builder; the value returned by `Create.With` is the completed Model under test.

(See: `TestingModelsAndSchemasDocumentaryTest#'constructs and asserts a completed Model'`.)

```groovy
class DeploymentSpec extends Specification {

    def 'builds the catalog deployment'() {
        when:
        def deployment = Deployment.Create.With('catalog') {
            image 'catalog:1.0'
        }

        then:
        deployment.name == 'catalog'
        deployment.image == 'catalog:1.0'
    }
}
```

Keep assertions at the public completed-model boundary. A focused model test is the right place to prove that the Schema
constructs the expected model; an integration or acceptance test still proves that a target system accepts and uses its
separate projection.

## Reuse a Domain API contract across Schema realizations

When one Layer 3 Domain API has several Schema realizations, keep reusable generic assertions with the Domain API rather
than copying them into each Schema test. In an application-owned multi-project build, enable Gradle's
`java-test-fixtures` capability in the Domain API project. Each Schema realization explicitly consumes that fixture and
supplies the concrete Model creation hook for the shared contract:

```groovy
// schema/build.gradle
dependencies {
    testImplementation(testFixtures(project(':domain-api')))
}
```

The shared contract names Domain API types only; Schema tests retain their concrete Schema assertions. Every
participating Domain API, Schema, and Model module must select the same Groovy/Spock pair. KlumAST plugins do not infer
an application's Domain API test dependency or map it to a project, so keep each fixture dependency explicit.

## Assert a validation failure

Give a domain rule an explicit message and assert its stable semantic fragment. The full exception also includes the
construction path and reporting details, which are useful diagnostics but unnecessarily brittle as a test's whole
expectation.

```groovy
@DSL
class Deployment {
    @Validate(message = 'A deployment image is required')
    String image
}
```

(See: `TestingModelsAndSchemasDocumentaryTest#'reports an explicit validation message for an invalid completed Model'`.)

```groovy
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException

when:
Deployment.Create.One()

then:
def exception = thrown(KlumValidationException)
exception.message.contains('A deployment image is required')
```

See [Validation](Validation.md) for choosing `@Required`, field validation, and cross-field validation methods.

## Reuse Templates across a Spock feature

For a Schema or Model module, the corresponding plugin already provides the published test-only `TemplateScope` artifact on
`testImplementation`. Do not add a second `klum-ast-test-support` dependency. That test-classpath convenience is
independent of the plugin's optional Spock setup, so the scope is available to Java/JUnit tests as well.

Only a direct Java or Groovy consumer that applies **neither** plugin declares the normal runtime and test-support artifacts
itself. Keep those versions aligned with the KlumAST BOM:

```groovy
dependencies {
    testImplementation platform('com.blackbuild.klum.ast:klum-ast-bom:<klum-version>')
    testImplementation 'com.blackbuild.klum.ast:klum-ast-runtime'
    testImplementation 'com.blackbuild.klum.ast:klum-ast-test-support'
}
```

`TemplateScope` receives already materialized Templates from the normal `Create.Template.With(...)` API. Declare a
non-`@Shared` instance field and let Spock close it with `@AutoCleanup`; Spock calls `close()` after `cleanup`, so template
defaults remain available to cleanup work. Create a scope with its constructor—there is no static open operation.

(See: `TemplateScopeTest#'keeps setup Templates active for the feature and owned Builder creation'`.)

```groovy
import com.blackbuild.klum.ast.testsupport.TemplateScope
import spock.lang.AutoCleanup

class DeploymentSpec extends Specification {

    @AutoCleanup
    TemplateScope templates = new TemplateScope()

    def setup() {
        templates.with(Deployment.Create.Template.With(image: 'catalog:1.0'))
    }

    def 'uses the baseline template'() {
        expect:
        Deployment.Create.One().image == 'catalog:1.0'
    }

    def cleanup() {
        assert Deployment.Create.One().image == 'catalog:1.0'
    }
}
```

Use a separate `TemplateScope` instance for each test feature. For a small lexical Java-only test, ordinary
try-with-resources with `new TemplateScope().with(template)` is equivalent. The scope accepts materialized Templates; it
does not create Templates or provide a general production construction mechanism. See [Templates](Templates.md) for
Template creation and ordinary callback-scoped application.
