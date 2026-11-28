# Retained pre-change scalar controls (#1129)

The six `historical*-g[345].jar` files were compiled with the unmodified KlumAST compiler/runtime/annotations at
`d91110749618a6fdb4fa605e128236a57758d3db`, before the #1129 extension. Each uses its own Groovy generation:
3.0.25, 4.0.32 or 5.0.6, Java 17 and AnnoDocimal 1.0.1. They are binary controls, never recompiled by the consuming test.
They are local historical-source builds, not a claim about published historical coordinates or mixed Groovy generations.

`Historical.groovy` supplies an ordinary one-mark String route, unique integer route, unmarked collision and generated
converter control. `Parent.groovy` supplies a two-mark parent without the new parameter completeness facts: a freshly
compiled child must reject it. The sources here document provenance; tests copy only these historical JARs for consumers.
The current parent/child fixture is compiled separately, packaged, and stripped of loose source/classes before clients.

[U3 evidence](../../../../../docs/implementation/issue-1129-u3-evidence.md) owns hashes, commands and qualification results.
To regenerate deliberately, create a clean worktree at the exact revision above, then run there with Java 17:

```shell
./gradlew -I /absolute/path/to/scalar-alternative-artifacts/rebuild-historical.init.gradle \
  -PscalarFixtureDirectory=/absolute/path/to/scalar-alternative-artifacts \
  rebuildScalarHistoricalFixtures --console=plain --max-workers=2
```

Review changed binaries and hashes; ordinary test execution must not regenerate these controls with the new compiler.
The opt-in task asserts the source revision. Its separate compilation and fixed JAR dates retain an auditable binary
boundary rather than stripping metadata from a freshly compiled artifact and calling it historical.
