# Adopter dry run

This record makes the fixture a field-test surface for all three portable skills. It is a 4.0 preview, not a generic Gradle template. The exact review input is retained in `advisor-cases/before/`; `src/` is the tested after-state.

## `start-klum-project`

The skill chose direct-schema because this fixture's client consumes `Deployment` directly, and target-contract because a deployment is configured for a concrete deployment target. It created `settings.gradle`, applied the schema plugin in `build.gradle`, selected Groovy 3, and verified `./gradlew test`.

## `author-klum-model`

The Model Writer added the keyed `Deployment`, owned `Service`, `Create.With` example, and executable `DeploymentTest`. The test covers the completed configured model and missing-environment validation.

## `feature-advisor`

The review-only pass inspected `advisor-cases/before/` and found two evidence-based candidates:

| Rank | Supported feature | Fit, benefit, and trade-off | Confidence / risk / effort | Guidance |
| --- | --- | --- | --- | --- |
| 1 | `@Validate` on `Service.image` | An empty image cannot describe a deployable service; rejecting it produces an actionable model error. It makes previously accepted incomplete models fail. | High / low migration risk / small | [Validation](https://klum-dsl.github.io/klum-ast/4.0/Validation/) |
| 2 | `Template.With` for repeated configured-service values | The configured-model examples repeated the same production image. A scoped Template factors that value into one recipe while retaining each deployment's explicit environment and identity. It is unsuitable when the deployments should evolve independently. | High / low migration risk / small | [Templates](https://klum-dsl.github.io/klum-ast/4.0/Templates/) |

The adopter selected both findings. `Service.requiresAnImage` is the Schema adjustment; `Service.Template.With` is the configured-model adjustment. Applying those changes produces the `src/` after-state. `DeploymentTest` verifies the unchanged configured model, both validation failures, and the template-applied model pair.
