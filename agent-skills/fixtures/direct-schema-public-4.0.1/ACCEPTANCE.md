# Direct-schema public adoption check — 2026-09-24

Related: #469. This is one repository-owned acceptance run for the bounded `start-klum-project` mission, not a Catwalk baseline or a release gate.

| Identity | Observed value |
| --- | --- |
| Product | KlumAST `v4.0.1`, annotated tag `e77057fdf65b79c2c7f92eaaec051d5e8c393950`, target `4d85ec2ed7e0a737d71b421af2c0cf597f6830e4`; Schema plugin `com.blackbuild.klum-ast-schema:4.0.1` |
| Documentation | Tagged 4.0.1 `Basics`, `Builder-First-Migration`, `Validation`, `Gradle-Onboarding`, and `Gradle-Plugins` blobs pinned in [`references/klum-4.0.1.md`](../../start-klum-project/references/klum-4.0.1.md) |
| Skill source | [`start-klum-project`](../../start-klum-project/SKILL.md) at branch commit `7e8580f1e005d97376c18541e03ab11682628f47` |
| Runtime | Zulu JDK `17.0.3+7-LTS`; Gradle `8.14.4`; resolved `org.codehaus.groovy:groovy:3.0.25`, `org.spockframework:spock-core:2.4-groovy-3.0`, KlumAST BOM/runtime/annotations `4.0.1` |

Only the fixture directory, including its own Gradle wrapper, was copied to a temporary directory outside the KlumAST checkout. No wrapper files or other inputs were borrowed from the repository. With a new empty `GRADLE_USER_HOME`, `./gradlew --no-daemon test --console=plain` downloaded Gradle 8.14.4, resolved the public product through the Gradle Plugin Portal and Maven Central, and passed: two tests, zero failures. The build has no included build, Maven Local repository, local product JAR, or project dependency. One test checks the completed `Deployment` returned by `Create.With`; the other catches `KlumValidationException` and asserts only the explicit `environment is required` fragment. An offline dependency report after the run showed the exact runtime versions above.

The standalone run passed in 21 seconds. Gradle reported deprecated features incompatible with Gradle 9; this check used the fixture's bundled Gradle 8.14.4 wrapper. This run does not test Groovy 4/5, IDE projection, Javadoc, Catwalk, or a separate agent's interpretation of the skill.
