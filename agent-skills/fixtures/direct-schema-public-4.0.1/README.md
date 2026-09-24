# Public 4.0.1 direct-schema adopter mission

This one-module project is a copyable acceptance surface for [`start-klum-project`](../../start-klum-project/SKILL.md). Its plugin marker resolves through the Gradle Plugin Portal and its product dependencies through Maven Central. It has no KlumAST checkout, included build, Maven Local repository, or project dependency. The plugin supplies the Groovy 3 and Spock pair. The test constructs a completed Model and checks a canonical validation exception using an explicit message fragment.

To exercise the public route, copy this directory **outside** the KlumAST checkout, use JDK 17 or newer and Gradle 8.14.4, and run `gradle --no-daemon test`. Use an empty `GRADLE_USER_HOME` for a fresh-resolution proof. Record the exact JDK, Gradle, Groovy, plugin, repository origins, and result. A run in this repository or with cached/local artifacts is only a convenience check, not fresh public-consumer evidence. This mission does not test source mirrors, Javadocs, or Catwalk's independent direct-schema baseline.

[The 2026-09-24 acceptance record](ACCEPTANCE.md) captures one clean public-coordinate run and its limits.
