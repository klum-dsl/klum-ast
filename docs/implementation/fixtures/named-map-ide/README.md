# Named-map IntelliJ acceptance fixture

This fixture is the manual IDE probe for NAMED-IDE in issue #797. It has two independent Gradle builds:

- `schema/` contains the DSL classes and a statically compiled client in the same module. The Schema plugin's refreshed
  `Foo_DSL` source mirror supplies the generated public contract.
- `binary-consumer/` is a separate build that consumes only the published Schema artifact. It has no Schema plugin and
  no source mirror.

The fixture is evidence for editor behavior, not a replacement for the compiler and plugin tests. Do not claim a result
for an IntelliJ build until it has been observed in that build.

## Prepare local artifacts

Read the checkout's current version with `./gradlew properties --console=plain`, then set `klumPluginVersion` in
`schema/gradle.properties` to that exact value. From the repository root, publish the checkout's artifacts to Maven
Local:

```shell
./gradlew publishToMavenLocal
```

Then publish the fixture Schema contract to Maven Local and refresh its generated source mirror:

```shell
./gradlew -p docs/implementation/fixtures/named-map-ide/schema publishToMavenLocal
./gradlew -p docs/implementation/fixtures/named-map-ide/schema clean createKlumDslSourceMirrors
```

Set `klumVersion` in `binary-consumer/gradle.properties` to the fixture Schema version (default `797.0`). Compile the
binary consumer once to resolve its dependencies:

```shell
./gradlew -p docs/implementation/fixtures/named-map-ide/binary-consumer clean compileGroovy
```

The Schema publication is `com.blackbuild.klum.ast:named-map-ide-schema:797.0`. The consumer uses that Maven Local
artifact and does not depend on the Schema project directory.

## IntelliJ observation checklist

Record **Help → About** exactly, including product build number. In both builds, reload the Gradle project after the
commands above. For each call shape, record completion, value type help, and inspection feedback separately:

1. In `schema/src/main/groovy/ideacceptance/SameProjectClient.groovy`, invoke completion inside each literal map and
   record whether the expected keys appear. Use Quick Documentation / parameter information on `precise`,
   `overloaded`, and `mode` to record the type IntelliJ presents.
2. Temporarily replace a valid key with `unknownNamedKey` and then pass an integer to `precise`. Record whether each
   produces immediate editor feedback at that map entry. Restore the valid source afterward.
3. Check `fromMapVariable` as the exclusion control: do not infer literal-map completion or key/value inspection from
   that call.
4. Repeat steps 1–3 for `binary-consumer/src/main/groovy/ideacceptance/BinaryClient.groovy`. Confirm the module has
   only the published Schema dependency and that the inspected generated declaration is compiled, not a source mirror.
5. Record the outcome for root, fixed single-child, and fixed collection-child calls independently in each build.

Attach screenshots or a precise observation log to `docs/implementation/evidence/issue-797-intellij-named-maps.md`.
Do not add a GDSL contributor unless one of the required behaviors has a reproducible native gap. If that happens, keep
the contract-derived contributor and its tests in a separate commit.
