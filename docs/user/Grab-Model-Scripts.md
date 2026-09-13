# Standalone Model scripts with `@Grab`

A Model Writer can keep a small, trusted Model in one Groovy script while the Schema remains a normal compiled and
published project. The script uses Groovy's Grape dependency manager to resolve that Schema and its runtime dependencies,
then configures a completed KlumAST Model through the Schema's generated factory.

Use this route for a local proof of concept, a focused Model check, or another short-lived tool where a Model Gradle
project would add more structure than value. Build, test, and publish the Schema through the regular
[Gradle onboarding](Gradle-Onboarding.md) route. Use the `com.blackbuild.klum-ast-model` plugin when the Model itself needs
repeatable builds, IDE support, tests, or publication.

This guide covers ordinary connected use and controlled execution without public-internet access. It does not define a
standalone distribution: [#553](https://github.com/klum-dsl/klum-ast/issues/553) separately investigates whether a shaded
Schema/runtime artifact would be useful.

## Prepare and publish the Schema

Apply the Schema and Maven Publish plugins in the Schema project and publish it to a repository available to Model
Writers:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klum-version>'
    id 'maven-publish'
}

group = 'com.example.platform'
version = '1.4.2'

publishing {
    repositories {
        maven {
            url = uri('https://packages.example.test/repository/maven-releases/')
        }
    }
}
```

The Schema plugin compiles the Schema with `klum-ast`, publishes `klum-ast-runtime` as an API dependency, and aligns its
KlumAST modules through the matching BOM. Preserve that generated Maven metadata. A JAR copied without its POM does not
describe the runtime closure that Grape must resolve.

## Version and compatibility policy

Pin an exact released Schema version in every Model script. The Schema's publication metadata then selects its matching
KlumAST runtime; the script should not also grab `klum-ast` or override individual KlumAST modules. The compiler artifact
belongs in the Schema build and is not needed to execute an already compiled Schema.

Install Java 17 and a Groovy distribution from a line supported by both the Schema and its KlumAST version. KlumAST 4.1 is
tested with Groovy 3.0.25, 4.0.32, and 5.0.6. Use the Groovy line against which the Schema owner publishes and tests the
Schema unless that owner explicitly supports additional lines. `@Grab` supplies the Schema and its declared dependencies;
it does not install or select Groovy itself.

Do not use a version range, `latest`, a changing snapshot, or a development version in an operational Model script. An
unchanged script must resolve the same published Schema and runtime graph on every run.

## Run a connected local Model

Assume the published Schema contains `com.example.platform.Deployment`. Save the following as `catalog.groovy` and replace
the example coordinate with the exact Schema coordinate your Schema owner publishes.

(See: `GrabModelScriptsDocumentaryTest#'runs a standalone Model script against a separately compiled Schema'`.)

```groovy
@Grab('com.example.platform:deployment-schema:1.4.2')
import com.example.platform.Deployment

def deployment = Deployment.Create.With('catalog') {
    environment 'production'
    service {
        image 'catalog:1.0'
    }
}

assert deployment.service.image == 'catalog:1.0'
println "${deployment.name}: ${deployment.service.image}"
```

Run it with the pinned Groovy installation chosen for that Schema:

```shell
groovy catalog.groovy
```

The first connected run resolves the exact Schema dependency closure from Grape's configured repositories and stores it
in Grape's cache. A normal Grape installation uses `~/.groovy/grapes`; this is separate from Gradle's dependency cache.

## Prepare a controlled cache for disconnected use

Prepare the cache on a connected staging machine before moving it across the network boundary. Use the same account
layout, Groovy line, Model script, and exact Schema version as the disconnected target.

1. Start with a dedicated staging account whose Grape cache contains no unrelated artifacts.
2. Run `catalog.groovy` once while connected.
3. Inspect the complete resolved closure, rather than copying only the Schema JAR:

   ```shell
   grape resolve com.example.platform deployment-schema 1.4.2
   ```

4. Record and verify an integrity manifest according to the organization's artifact-transfer policy, then transfer the
   complete staged `~/.groovy/grapes` directory to the target account's same location. Keep the Ivy descriptors and cache
   metadata together with the JARs.
5. Add cache-only resolution to the disconnected copy of the Model script:

   ```groovy
   @GrabConfig(autoDownload=false)
   @Grab('com.example.platform:deployment-schema:1.4.2')
   import com.example.platform.Deployment
   ```

6. Run the script with network egress disabled. A missing Schema or transitive dependency must fail during Grape
   resolution; do not temporarily reconnect the production host to fill the cache.

`@GrabConfig(autoDownload=false)` is the GrapeIvy cache-only control used by the supported Groovy 3, 4, and 5 lines. Keep
the network boundary as an independent enforcement layer and repeat the cache preparation whenever the Groovy or Schema
version changes.

## Resolve from an internal repository

For a connected corporate network without public Maven access, publish the Schema to an approved Maven-compatible
repository and mirror the exact external closure from its POM into that repository. Let repository tooling derive and
retain the transitive closure; a manually maintained list of JARs is easy to make incomplete.

For a script-specific resolver, add the internal repository before `@Grab`:

```groovy
@GrabResolver(name='company-releases', root='https://packages.example.test/repository/maven-public/')
@Grab('com.example.platform:deployment-schema:1.4.2')
import com.example.platform.Deployment
```

`@GrabResolver` adds a resolver; it does not guarantee that other configured resolvers are absent. Enforce the repository
allowlist at the network boundary. For an installation-wide GrapeIvy policy, the deployment owner can instead provide
`~/.groovy/grapeConfig.xml` with only the approved repository:

```xml
<ivysettings>
  <settings defaultResolver="downloadGrapes"/>
  <resolvers>
    <chain name="downloadGrapes" returnFirst="true">
      <filesystem name="cachedGrapes">
        <ivy pattern="${user.home}/.groovy/grapes/[organisation]/[module]/ivy-[revision].xml"/>
        <artifact pattern="${user.home}/.groovy/grapes/[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"/>
      </filesystem>
      <ibiblio name="company-releases"
                root="https://packages.example.test/repository/maven-public/"
                m2compatible="true"/>
    </chain>
  </resolvers>
</ivysettings>
```

GrapeIvy expects the resolver names `downloadGrapes` for normal resolution and `cachedGrapes` when
`autoDownload=false`; preserve both names when replacing its configuration.

The deployment owner must verify this operational configuration with the organization's Groovy distribution, repository
manager, TLS trust, authentication, proxy, retention, and checksum policies. Do not put repository credentials in the
Model script. Run `grape resolve` and the complete script in the target environment before declaring the repository ready.

## Security boundary

Dependency resolution and Model execution are separate security decisions. A controlled cache or internal repository
controls where artifacts come from; it does not sandbox them. The Model script, Schema code, lifecycle methods, and
resolved dependencies execute in the Groovy process with that process's operating-system access.

Run only trusted Models and dependencies under a suitably restricted account or isolated process/container. The future
security model for untrusted Model sources and extension code belongs to
[#261](https://github.com/klum-dsl/klum-ast/issues/261); `@Grab`, an internal repository, and a shaded JAR are not substitutes
for that boundary.

## What KlumAST validates

`GrabModelScriptsDocumentaryTest` compiles the Schema first, publishes it with a transitive fixture dependency to a
temporary Maven repository, then executes the `@Grab` Model above against that local-only repository in the Groovy 3, 4,
and 5 test lanes. This deterministically guards the Grape annotation, published-POM dependency resolution, generated
factory call, Builder-first child construction, and completed Model result without contacting a network repository.

Publication metadata, repository resolution, proxy/TLS behavior, cache transfer, and egress enforcement depend on the
Schema project and target deployment environment, so the repository test does not claim to validate them. Validate those
operational parts with the exact published Schema coordinate, `grape resolve`, and a full Model run in each connected,
intranet, or disconnected target environment.
