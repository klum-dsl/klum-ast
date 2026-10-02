# Portable GDSL native acceptance fixture — issue #805

This engineering fixture exercises ADR 0025 against real generated Schema contracts. It is not a user onboarding guide
or a release claim. See [the recorded native run](../../evidence/issue-805-portable-gdsl-intellij.md) for observed results
and outstanding gates. Source and binary builds are independent; do not import their common parent as an IDEA project.

## Stage one candidate in an isolated Maven repository

Use this checkout's Gradle wrapper (8.14.4) with a supported Gradle JVM and a Java 17 toolchain. Set `JAVA_HOME` to your
installed JDK 17 if the host default is newer than the wrapper supports. These commands run from the repository root:

```sh
fixture_root="$PWD/docs/implementation/fixtures/portable-gdsl-ide"
./gradlew -I "$fixture_root/stage.gradle" \
  -Prelease.version=4.1.0-gdsl4.805 \
  -PgdslAcceptanceRepository="$fixture_root/repository" \
  publishAllPublicationsToGdslAcceptanceRepository
./gradlew -p "$fixture_root/source-authoring" \
  generateAllKlumDslSourceMirrors :schema:publishMavenJavaPublicationToFixtureRepository :model:test
./gradlew -p "$fixture_root/binary-model" materializeKlumDslGdsl test
```

`stage.gradle` adds only a local repository; it does not change Maven Local or normal release repositories.
The candidate version is a local acceptance identifier, not a released KlumAST version. Producer coordinates are
`org.example.gdsl4:schema:805.1`; the same publication supplies its optional `gdsl` classifier and GMM variant.
The fixture defaults resolve `../repository` from each independent build root, including the source subprojects.
To use a different directory or candidate version, change `klumRepository` (prefer an absolute path) and
`klumPluginVersion` in **both** builds' `gradle.properties` before command-line execution and native import.
Do not rely on transient `-P` options for native import: IDEA must read the same persistent settings.

For fresh scratch directories, copy `source-authoring` and `binary-model` separately, excluding `.idea`, `.gradle`,
`build`, and `*.iml`. Set the absolute staged repository path in both copies. Copy the checkout's `gradlew`,
`gradlew.bat`, and `gradle/wrapper/` into each build if IDEA should use that wrapper automatically. Never copy mirrors,
GDSL output, or a producer checkout into the binary build. Run the source refresh/publication and binary refresh again
in those copies. A Schema-only import uses `schemaOnly=true` in the source build's properties and excludes `model`.

Both Model builds retain `RecipeRuntimeTest` with `@Issue("805")`. The recipe gives `region == 'eu'` through
`Environment.Create.From(File)` and through an owned child built from the recipe Class:

```groovy
def deployment = Deployment.Create.With {
    environment(Environment.Create.AsBuilder().From(recipeClass))
}
assert deployment.environments*.region == ['eu']
```

`Deployment.Create.From` retains `MissingMethodException` for `region`. The filename mapping supplies editor context;
it does not change the runtime receiving Builder. Restore recipe files after editor probes before running `test`.

## Native import and positive probes

Use a dedicated clean IDEA profile with its bundled Groovy plugin. Record the IDEA edition/build, Groovy plugin build,
Gradle JVM, Java toolchain, fixture commit, staged coordinates, repository, and activation/indexing observations.
Trust only the freshly created fixture projects. Open each independent build through File → Open as a Gradle project.
Wait for import/indexing. Do not hand-mark directories, alter `.iml`, add generated directories to SourceSets, or attach
Schema source/mirror artifacts to the binary dependency.

1. In source-authoring, verify generated Java mirrors are imported as generated source roots and the single root
   `build/generated/klum-dsl-ide/gdsl` is a resource root, despite the ordinary `build/` exclusion. Repeat with
   `schemaOnly=true` and fresh source refresh. In binary-model, verify only normal Schema classes are available and
   the materialized root is imported; no source-generation task or mirror is required.
2. Open `catalog.environment.groovy`, containing `@BaseScript DelegatingScript script` and `region 'eu'`.
   Replace only `region` with `reg`, place the caret immediately after it, and explicitly invoke Code → Code Completion
   → Basic (or Find Action → Basic, description “Complete code”). Record the native proposal/signature. Do not accept
   an AI inline suggestion as evidence. Accept the native `region` proposal, restore valid `region 'eu'` syntax,
   and navigate to its declaration. Source must reach the refreshed `Environment_DSL.Builder`; binary must reach
   compiled `Environment_DSL.Builder.region(String)`. Parameter Info should expose the String argument.
3. Run the typed `control.groovy` control and `RecipeRuntimeTest`. Compare `catalog.other.groovy` and add
   `reg 'eu'` inside `OrdinaryControl.probe()` in `ordinary.environment.groovy`. Neither may acquire the mapped Builder
   operation. General fuzzy class-name completions may still exist; record absence of the Builder operation specifically.
4. Probe `region 42` and `definitelyMissing 'eu'` in the mapped script. Record inspections and declaration navigation
   separately: inability to navigate an unknown method is not evidence of an unknown-method inspection. Restore files.
5. Repeat binary import independently with `classifierFallback=true`. The repository then uses only POM/artifact
   metadata and the explicit exact-GAV `:gdsl@jar` fallback. It must reach the same compiled public Builder.

Opening a generated descriptor may produce an activation prompt in some IDEA configurations. If present, record the
prompt and activate that generated descriptor. No prompt appeared in the recorded existing-profile run. This does
not establish first-use behavior in a clean profile or other IDEA versions.

## Negative targets and lifecycle matrix

Apply each state in `gradle.properties`, refresh on the command line, then use **Sync All Gradle Projects** and
**File → Reload All from Disk** in IDEA. Wait for indexing and repeat the Basic/navigation probe. Disk reload is needed
when mirrors are recreated after a missing-Builder control; Gradle Sync alone did not rediscover that file in the
recorded run. Do not report output retained after a failed refresh as current accepted metadata.

| State | Setup and refresh | Native expectation |
| --- | --- | --- |
| Missing Model | Source-only: `mappingModel=gdslacceptance.Absent`; aggregate refresh | No phantom `region` |
| Non-DSL Model | Source-only: `mappingModel=gdslacceptance.PlainModel`; aggregate refresh | No phantom `region` |
| Missing Builder | Normal Model target; refresh mirrors first, move only `Environment_DSL.java` outside the project; run only `materializeKlumDslGdsl` | Model remains; mapped Builder operation disappears. Restore with aggregate refresh |
| Renamed suffix | Source-only: `mappingSuffix=.renamed.groovy`; aggregate refresh; create `catalog.renamed.groovy` with the same recipe | New suffix completes; old suffix does not |
| Removed mapping / empty upgrade | Remove source suffix/target overrides; set `emptyMappings=true`, `schemaVersion=805.2`; publish Schema. Binary: `schemaVersion=805.2`; refresh | Only framework descriptors remain; old suffix operation disappears |
| Last owner disabled / dependency removed | Binary: `disableGdsl=true` excludes both opt-in and metadata request; run `clean` | Root output removed; reimport retires mapped operation |
| Restart | Repeat positive and retired states after a full IDEA restart in the dedicated profile | Same resolution/retirement, record any reactivation |
| Managed overlap | `managedConflict=true` includes `clash-schema` with `.groovy`; refresh | Fail before sync, with both origins/suffixes, witness filename, and stale-output warning |
| Normal version conflict | Stage empty Schema `805.2`; `managedVersionConflict=true` includes a non-opted-in `version-model` selecting it beside local Schema `805.1`; refresh | Reject both normal versions and label previous output stale |

Remove presence-based flags entirely to re-enable them (`flag=false` still counts as present). After each control restore
normal properties, mirror/metadata refresh, disk reload, valid recipe text, and the runtime tests. Do not remove or
reinterpret arbitrary external GDSL; these fixtures exercise only managed portable metadata.

The two conflict modules are optional retained fixtures, enabled only by their presence-based properties. Use them
one at a time and keep the source root's normal `schemaVersion` at its default `805.1` for the version conflict.
Stage the other normal Schema version before enabling that control:

```sh
./gradlew -p "$fixture_root/source-authoring" -PschemaVersion=805.2 -PemptyMappings \
  :schema:publishMavenJavaPublicationToFixtureRepository
```

In IDEA, first import the optional module with Gradle Sync, then run root `materializeKlumDslGdsl` from its Gradle tool
window and record the failure/stale-output message. Gradle Sync alone need not run the materializer. A failed refresh
leaves previous descriptors on disk; existing editor hints are stale, not a silently accepted conflict. Remove the
control property, refresh successfully, and resync/reload IDEA to restore the positive state.

## Nested-Model preparation boundary

The retained optional `schema/src/nested/groovy/gdslacceptance/Outer.groovy` contains the real supported runtime Model:

```groovy
class Outer {
    @DSL static class Inner { String region }
}
```

On a separate scratch copy, enable `nestedModel` to include that source directory. Reproduce the current editor and
publication preparation boundary through normal tasks:

```sh
./gradlew -p "$fixture_root/source-authoring" -PschemaOnly -PnestedModel \
  -PmappingModel=gdslacceptance.Outer.Inner -PschemaVersion=805.3 generateAllKlumDslSourceMirrors
./gradlew -p "$fixture_root/source-authoring" -PschemaOnly -PnestedModel \
  -PmappingModel=gdslacceptance.Outer.Inner -PschemaVersion=805.3 \
  :schema:publishMavenJavaPublicationToFixtureRepository
```

The real public `Outer$Inner_DSL$Builder` compiles, but the mirror task's `**/*$*` exclusion produces no source mirror.
Normal publication currently fails in `createClassStubs` projecting Outer (`'$I'` argument error). These are preparation
limits, not native lookup results. Do not bypass projection/publication or supply a hand-written Builder mirror to
claim a native pass. Remove the flag and run clean/refresh to return that scratch copy to the ordinary fixture.

The repository has no pinned headless native PSI test harness for this contribution. This manual importer procedure is
the native seam. The real binary TestKit contract covers Groovy 3/4/5 and runtime dispatch, while its GroovyShell adapter
remains a non-native control. The recorded guided clean-profile/restart/conflict checks passed; native nested lookup
remains unqualified at the preparation boundary. Full ADR release qualification remains pending.
