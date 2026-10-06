# Schema Style Guide

Use these conventions when starting or reviewing a Schema. **Framework constraints** describe enforced behavior;
**recommendations** are readability preferences you can adapt to your project. Read the documentation matching your
KlumAST release. These conventions work with direct Schemas and Layer 3, and with domain-first and target-contract
modeling; choose the architecture separately in [Gradle Onboarding](Gradle-Onboarding.md#choose-the-model-shape-first).

## Name domain types, leave implementation names to KlumAST

**Recommendation:** give manually declared nested types meaningful names such as `CapacityChecks` or `Recipe`, without
a leading underscore. KlumAST uses deterministic underscore-prefixed nested implementation names, including `_Factory`
and, for abstract Template implementations, `_TemplateModel`. Avoiding that pattern makes authored helpers easier to
recognize and reduces collision risk as a Schema evolves.

**Framework constraint:** an exact collision with a type actually generated inside that DSL class is a compilation
error with a rename diagnostic ([#837](https://github.com/klum-dsl/klum-ast/issues/837)). This is not a blanket prohibition:
unrelated names such as `_Recipe` remain legal. The generated nested `Builder` implementation also has an exact reserved
name despite having no underscore. Use public generated contracts when needed; do not depend on hidden implementation
types. See [Factory Classes](Factory-Classes.md) and its [public factory and Builder contracts](Factory-Classes.md#creator-methods-and-collection-factories).

## Use a conventional package and source layout

**Recommendation:** use a named package in the project's namespace and mirror it in the source directory. For example,
`package example.catalog` belongs under `src/main/groovy/example/catalog/`; put its tests under
`src/test/groovy/example/catalog/`. Keep a Schema type in a clearly named file, such as `Deployment.groovy`, unless
grouping small related types improves readability. Import referenced types and use their simple names.

The Schema and test source roots follow the documented [Gradle setup](Gradle-Onboarding.md#create-the-gradle-project).
The package and file organization are conventions, not additional KlumAST compiler restrictions. Introduce separate
modules only when the project's consumer or artifact boundaries warrant them.

## Choose the smallest validation form that expresses the rule

**Recommendation:** use `@Required` for presence, a field `@Validate({ ... })` for a one-expression field rule, and an
`@Validate` method for cross-field, reusable, or multi-step rules. Prefer a short, named method over a dense closure.
Group independent concerns in meaningfully named inner validation classes when that helps navigation; do not introduce
one for every rule. The details and reusable forms belong in [Validation](Validation.md#choose-a-validation-form).

**Framework constraints:** `@Required` is the concise alias for default field validation, not a universal non-null check.
It uses Groovy truth: empty Strings/collections and zero numbers fail; a boxed `Boolean` is checked for non-nullness,
so `false` is valid. It cannot annotate a primitive `boolean`, or accompany `@Validate` on the same field. A validation
closure accepts the field value and must be one expression or an `assert`; handle `null` deliberately. Validation methods
run on completed Models. See [field validation](Validation.md#on-fields) and [`@Required` and `@Optional`](Validation.md#required-and-optional).

This existing example keeps the rule beside its field and names the cross-field rule:

```groovy
@DSL
class Deployment {
    @Required String image
    @Validate({ it in 1..20 }) int replicas
    int minimumReplicas

    @Validate
    void replicasMeetMinimum() {
        assert replicas >= minimumReplicas : 'replicas must meet the configured minimum'
    }
}
```

Imports and the full explanation are in [Choose a validation form](Validation.md#choose-a-validation-form). Give failures
concise domain messages that explain the violated rule. For constraints shared across relationship fields, consider the
supported [domain-defined relationship constraints](Validation.md#domain-defined-relationship-constraints) rather than
copying checks or adding validation-only Model properties.

## Test completed Models and domain outcomes

**Recommendation:** reuse the project's established test framework. For a new Schema-plugin project, use its matching
Spock setup and a small `./gradlew test` feedback loop. Construct through the generated factory and assert the returned
completed Model's public state, including relevant defaults, relationships, and invalid cases. Use native conditions in
Spock `then:` and `expect:` blocks; redundant `assert` adds noise there. An `assert` inside a validation method, as above,
serves a different purpose and remains appropriate.

For validation failures, expect `KlumValidationException` and check the distinctive semantic fragment of an explicit
message. Avoid asserting the complete diagnostic text, construction path, ordering, or power-assert rendering unless
that diagnostic itself is the contract under test. Copy the executable patterns from
[Testing Models and Schemas](Testing-Models-and-Schemas.md#assert-a-validation-failure); keep target-system acceptance
tests alongside these focused tests where the project needs them.

## Document the meaning, not the obvious spelling

**Recommendation:** add concise GroovyDoc/JavaDoc where it explains domain meaning, units, accepted values, or a
non-obvious rule. Keep the description close to the property or method; omit comments that merely repeat its name.

**Framework behavior:** property documentation is projected verbatim to generated Model and Builder accessors, while
an explicit accessor comment is authoritative. Schema-defined methods use their own documentation. Choose wording that
reads well at those use sites; see [Javadoc for models](Javadoc.md) for the projection and templating details.
