# IntelliJ completion for DelegatingScripts

KlumAST 4.1 adds an opt-in Gradle workflow for `DelegatingScript` recipes with an intentional filename suffix.
The Schema Developer declares which Model type the script family configures; KlumAST generates and delivers IntelliJ
GDSL that resolves the Model's real public Builder. Model Writers can then complete bare Builder calls and navigate
to their declarations in source mirrors or compiled Schema classes.

This guide describes the 4.1 unreleased candidate tracked by
[#805](https://github.com/klum-dsl/klum-ast/issues/805). Use matching Schema and Model plugin versions that include this
facility; it is not available in 4.0.1. The qualified path uses regular, non-nested DSL Models whose public Builders are
available through the supported source-Schema or published-binary Schema workflow. Nested DSL Models are outside this
qualification; their source-mirror and publication limitations are separately tracked in
[#826](https://github.com/klum-dsl/klum-ast/issues/826).

## Declare the Schema mapping

For example, define `src/main/groovy/example/Environment.groovy`:

```groovy
package example

import com.blackbuild.klum.ast.DSL

@DSL
class Environment {
    String region
}
```

The Schema project owns the reusable naming convention. In a Schema project named `environment-schema`, enable
metadata and declare its mapping in `build.gradle`:

(See: `KlumSchemaGdslProducerTest#'metadata generation is lazy reproducible relocatable cacheable and supports empty retirement'`.)

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klum-version>'
    id 'maven-publish' // needed only when publishing the Schema
}

group = 'org.example'
version = '1.2.0'

klumSchema {
    gdsl {
        publish = true
        mappings {
            environment {
                fileNameSuffix = '.environment.groovy'
                modelType = 'example.Environment'
            }
        }
    }
}
```

`publish` defaults to `false`. Setting it to `true` also contributes the mapping to local source authoring, even
without `maven-publish` or a separate Model project. `environment` is a stable Schema-local mapping ID: use lowercase
ASCII letters, digits, and hyphens, beginning with a letter. `modelType` names the Model, so authors do not need to
spell a generated Builder type.

`fileNameSuffix` is a case-sensitive literal suffix ending in `.groovy`. `.environment.groovy` matches
`catalog.environment.groovy`, but not `catalog.other.groovy`, `catalog.Environment.groovy`, or `catalog.environment`.
It applies to the final filename, without directory scoping. Choose a distinctive suffix for each target family.
Regular expressions, arbitrary predicates, and Model-local mapping overrides are outside this format.

With the normal Maven publication configured as described in [Gradle Plugins](Gradle-Plugins.md), publishing the
Schema adds a separate `gdsl` classifier JAR and a Gradle Module Metadata variant with a dedicated capability.
`generateKlumGdslMetadata` generates the metadata; `klumGdslJar` assembles its archive. Neither task refreshes the IDE.
Ordinary Schema JARs, sources/Javadocs, and POM dependency graphs do not carry this new metadata. Keep the normal
publication group, artifact name, and version equal to the project identity; inconsistent `mavenJava` identity overrides
are rejected.

The mapping is versioned with the Schema's normal group, artifact, and version. Change or remove a mapping in a new
Schema version and describe it in that Schema's release notes; do not replace released classifier bytes. A Model
artifact does not republish this metadata for downstream consumers.

## Consume a source Schema

In a multi-project build, the Model declares both its normal Schema dependency and its explicit metadata selection:

(See: `KlumGdslProjectWideValidationTest#'source authoring and two explicit project consumers share one root and one archive with #order evaluation'`.)

```groovy
plugins {
    id 'com.blackbuild.klum-ast-model' version '<klum-version>'
}

klumModel {
    schemas { schema project(':schema') }
    gdsl { enabled = true }
}

dependencies {
    klumGdsl project(':schema')
}
```

The selected Schema must enable its metadata. Source authoring needs the Schema's refreshed `Environment_DSL` mirror
to expose the actual public Builder. The producer contributes locally even when no Model module exists; when several
Models select the same producer, its metadata is materialized once. Refresh through the tasks below.

## Consume a published binary Schema

A separate Model-only build applies the Model plugin and uses the normal repository containing the Schema's library
and metadata publication. It needs no producer checkout, Schema plugin, generated source mirrors, or attached sources.

(See: `KlumModelGdslConsumerTest#'binary Model follows the normal Schema constraint without a second metadata version and caches refresh'`.)

```groovy
plugins {
    id 'com.blackbuild.klum-ast-model' version '<klum-version>'
}

repositories {
    maven { url = uri('https://repo.example.org/releases') }
    mavenCentral()
}

klumModel {
    schemas { schema 'org.example:environment-schema:1.2.0' }
    gdsl { enabled = true }
}

dependencies {
    klumGdsl 'org.example:environment-schema'
}
```

`enabled` defaults to `false`. Enabling it does not select metadata automatically: explicitly name each desired
Schema in `klumGdsl`. This dedicated dependency scope is non-transitive and stays outside normal compilation,
runtime, tests, resources, and Model publication dependencies. Ordinary builds and Gradle import do not run the
metadata refresh.

The preferred Gradle Module Metadata selection is versionless. BOMs, constraints, locks, substitutions, and conflict
resolution select the normal Schema dependency; metadata follows that same origin and version. A separate version on
this `klumGdsl` selection is rejected. Missing or mismatched metadata fails clearly; there is no silent fallback.

### Explicit classifier fallback

For a repository that exposes only POM/artifact metadata, replace the versionless selection with an exact classifier
request:

```groovy
dependencies {
    klumGdsl 'org.example:environment-schema:1.2.0:gdsl@jar'
}
```

A POM cannot describe the metadata capability, so this fallback intentionally repeats the exact Schema version.
It must match the normal selected Schema identity/version; dynamic or range versions are rejected. Update it when the
normal Schema selection changes. KlumAST never retries this classifier automatically. Non-Gradle consumers must
resolve, extract, and register editor content themselves; native Maven IDE integration is not qualified here.

## Write and execute the recipe

Write `catalog.environment.groovy` with a `DelegatingScript` base:

```groovy
import groovy.transform.BaseScript
import groovy.util.DelegatingScript

@BaseScript DelegatingScript script

region 'eu'
```

The mapping supplies IntelliJ context only. Execute the recipe through its actual receiving factory:

```groovy
def environment = Environment.Create.From(new File('catalog.environment.groovy'))
assert environment.region == 'eu'
```

`Environment` is deliberately unkeyed in this example. Keyed file, compiled-script, and classpath routes retain their
existing naming rules; see [Convenience Factories](Convenience-Factories.md#delegating-scripts).
An ordinary typed `Environment.Create.With { region 'eu' }` remains the direct factory alternative.
For owned-child recipes, use the existing active-session Builder-producing route described in
[Convenience Factories](Convenience-Factories.md#script-and-delegating-script-for-collections-and-maps).

The suffix does not set a runtime receiver, change script compilation, enforce runtime typing, or make a regular
`Script` into a `DelegatingScript`. Calling the recipe through another Model's factory still uses that factory's
Builder, even if IntelliJ offers Environment operations. Test the actual receiving factory and completed result;
[#269](https://github.com/klum-dsl/klum-ast/issues/269) separately tracks typed script API investigation.

## Refresh IntelliJ metadata

Import the build as a Gradle project, then run the appropriate explicit refresh:

| Authoring setup | Task run from the build root |
| --- | --- |
| Single source Schema | `./gradlew createKlumDslSourceMirrors` |
| Multiple source Schema projects | `./gradlew generateAllKlumDslSourceMirrors` |
| Binary-only Model build | `./gradlew materializeKlumDslGdsl` |

Source mirror refresh first runs the shared materializer, then projects the actual compiled public interfaces.
The binary path resolves the compiled Builder directly. One root task owns
`build/generated/klum-dsl-ide/gdsl`, containing both framework contributors and the selected Schema contributors.
Participating Schema/opted-in Model modules register that directory as generated IDEA resource content. Source mirrors
are generated IDEA sources. Both stay outside Gradle SourceSets, compilation, packaging, and downstream classpaths.

After a successful refresh, use **Sync All Gradle Projects** and **File → Reload All from Disk** in IntelliJ, then wait
for indexing. Reloading from disk matters when mirrors have been removed and recreated. No descriptor activation was
needed in the qualified clean-profile runs; if your IDEA configuration presents an activation prompt, enable the
generated descriptor. Optional file watchers or continuous mirror refresh do not replace IDE synchronization.

Refresh after mapping edits, suffix renames, removals, or Schema upgrades, then sync/reload. Removing a mapping or
upgrading to an enabled empty catalog retires its contributor on a successful refresh. If the final owner opts out and
no materializer remains, run root `./gradlew clean` to remove the previous generated IDE state, then sync/reload.
The Model plugin ensures the root has Gradle's standard Base lifecycle and cleanup even after final opt-out; this
does not create a root Java/Model project or enable metadata consumption.

## Project-wide mappings and failures

IntelliJ mappings are project-wide, even though the files have Schema-specific paths. A Model opting out does not
isolate its scripts from a mapping still supplied by another module. Keep the target Model and its public Builder
visible; a missing/non-DSL Model or missing Builder contributes no invented operations. Ordinary classes in files
with the suffix do not acquire script operations.

The refresh validates all managed mappings before changing output. It rejects conflicting Schema versions across
participating projects, including normal Schema selections in Models that did not opt into GDSL, distinct duplicate
payloads, and overlapping suffixes. `.groovy` overlaps `.environment.groovy`; identical suffixes also conflict even
when they target the same Model. There is no longest-suffix priority or module-local override. Select compatible
Schema versions and disjoint suffixes, then refresh again.

A failed refresh labels prior successful output stale and retains those previous bytes. Existing editor hints after
that failure are not evidence of accepted current metadata. Correct the reported conflict or invalid/missing archive
and obtain a successful refresh before relying on them.

Validation covers the portable managed metadata only. Arbitrary `.gdsl` files in normal sources, resources, or
dependency JARs remain user-owned IntelliJ configuration: KlumAST does not parse, transport, reject, or conflict-check
them. If you tried the unreleased copied-resource recipe from PR #809, remove those copied contributors when adopting
this workflow. There is no migration scanner or automatic deletion of external files.

## Qualified IDE boundary

Native Gradle imports were verified in IntelliJ IDEA Ultimate 2026.2.3, build `262.10968.63`, with its bundled Groovy
plugin and Gradle 8.14.4. The source and binary paths demonstrated Basic Completion, navigation to source-mirror or
compiled/decompiled Builder declarations, Parameter Info, wrong-argument diagnostics, and persistence/retirement across
restart. Independent real binary fixtures exercised the receiving factories on Groovy 3, 4, and 5.

This is version-specific IntelliJ evidence for regular non-nested Models. It does not establish unknown-method
inspection parity, Quick Documentation, Eclipse DSLD, VS Code, arbitrary GDSL support, included/composite builds,
or Gradle Isolated Projects. Quick Documentation uses its separate AnnoDocimal integration. See
[Gradle Onboarding](Gradle-Onboarding.md#intellij-and-generated-dsl-support) for ordinary generated-API assistance,
and the [native evidence and repeatable fixture](https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/evidence/issue-805-portable-gdsl-intellij.md)
for the qualification matrix.
