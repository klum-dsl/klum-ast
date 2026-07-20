# Minimal KlumAST onboarding fixture

This direct-schema fixture is the executable output expected from `start-klum-project` and `author-klum-model`.

`Deployment` demonstrates a keyed root, owned child, default, validation, and `Create.With` construction. The replayable pre-adjustment state is in `advisor-cases/before/`:

- Add an image validation to a Schema that permits incomplete services.
- Replace repeated configured-service values with a scoped Template when those values are intentionally shared.

Those cases are not preselected changes. A review-only feature-advisor run starts by inspecting that directory and must explain and rank them without editing it. When the adopter selects both findings, apply the documented changes to `src/`; it is the checked-in after-state and `DeploymentTest` verifies it. [`ADOPTER-DRY-RUN.md`](ADOPTER-DRY-RUN.md) records the discovery, selection, and verification flow.

Run `./gradlew test` from this directory when the fixture is inside this repository. The composite build supplies the checked-out KlumAST plugin and artifacts.

The default verifies Groovy 3. To exercise another supported line, add `-PfixtureGroovyVersion=4` or `-PfixtureGroovyVersion=5`; the Gradle plugin selects the matching Groovy coordinate and Spock variant.
