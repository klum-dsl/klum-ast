# Gradle plugins

KlumAST provides some Gradle plugins to make the setup of a Klum project easier. They consist of the following plugins:

## `com.blackbuild.convention.groovy`

This plugin is not specific to Klum and might be extracted to a separate project in the future. It basically sets the necessary dependencies for Groovy as well as a matching version of the Spock Framework.

The version can be set directly via a String or int property:

```groovy
plugins {
    id 'com.blackbuild.convention.groovy' version '<version>'
    id 'groovy'
}

groovyDependencies {
    groovyVersion = 3 // or "5.0.5"
}
```

Note that the plugin does **not** apply the groovy plugin; it only reacts to its presence.

Applying the plugin (provided the Groovy plugin is also applied) does the following thing:

- Create two additional configurations, `groovy` and `spock`, which are used to declare the dependencies on Groovy and Spock, linking those configurations to the `compileOnly` and `testImplementation` configurations
- Setting the matching Groovy BOM as 'platform' as well as the main Groovy module as dependencies in `groovy` and the matching spock dependency in `spock` 
- Configure all test tasks with `useJunitPlatform()` 
- Spock dependencies can be skipped with "skipSpock = true"

If the plugin is applied to a child project, it will inherit the configured Groovy versions from the root project, if applicable (even if the Groovy plugin is not applied to the root project). That way, the Groovy version can be set in a single place. In a klum project, this is usually the only situation where the convention plugin needs to be used directly, as the other two plugins will apply it automatically.

## `com.blackbuild.klum-ast-schema`

This plugin is used in schema projects (as well as `api` as defined by [Layer3]). It does the following things:

- applies AnnoDocimal's Groovy plugin for generating documentation
- applies Groovy and JavaLibrary plugins
- activates source code and javadoc jars
- registers `createKlumDslSourceMirrors` to refresh AnnoDocimal source mirrors for generated `Foo_DSL` support
  namespaces through AnnoDocimal's cacheable, configuration-cache-safe projection task. Run this task after schema changes in a single-project build. In a multi-project build, use the root
  `./gradlew generateKlumDslSourceMirrors` aggregate instead; it lazily runs each participating Schema project's task,
  including `api` and `schema` in a Layer 3 layout. It compiles the real generated interfaces first when necessary and
  exposes the mirror directory to IntelliJ as a generated source root. The aggregate has no payload of its own and the mirrors are IDE metadata;
  they are not compiled, packaged, published, or added to downstream classpaths.
- if `maven-publish` plugin is applied, configures the publication
- adds the `com.blackbuild.convention.groovy` plugin, which configures Groovy and spock dependencies, version can be set via the `klumSchema` extension (defaulting to Groovy 3). If the configured project is not the root project **and** the root project has the `com.blackbuild.convention.groovy` plugin applied, the version will be inherited from the root project instead.
- adds the necessary dependencies for KlumAST itself:
  - `klum-ast` as compileOnly dependency
  - `klum-ast-runtime` as api dependency
  - both dependencies are added without explicit versions; the version is enforced by importing the `klum-ast-bom` platform at the plugin's version

### Named Schema modules

The Schema plugin validates a user-owned `src/main/java/module-info.java` through
`validateKlumSchemaModule`. It is included in `check` and, when `maven-publish`
is applied, before Maven publication. The task never edits the descriptor.

Groovy 4 and 5 may use a named Schema module. Its descriptor requires the
annotations and runtime modules, `requires static com.blackbuild.klum.ast.compiler`,
and `org.apache.groovy`; when the project declares the Jackson or Bean Validation
adapter, it must also require that adapter and open each Schema package to its
reflection target. See [Migration#named-modules-and-groovy](Migration.md#named-modules-and-groovy) for a complete
descriptor example.

Groovy 3 is classpath-only for KlumAST Schema projects. If it finds a descriptor,
the validation task fails with a copyable remediation: remove `module-info.java`
and use the ordinary classpath, or move the Schema to Groovy 4 or 5. Do not add
`--add-reads`, `--add-exports`, `--patch-module`, or similar workaround flags.
Generated `Foo_DSL` mirrors remain IntelliJ metadata, never module sources.

This means that a fully working schema project can be set up with the following minimal build.gradle:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klum-version>'
    id "maven-publish"
}

publishing {
    repositories {...}
}
```
## `com.blackbuild.klum-ast-model`

This plugin is used in model projects. It does the following things:

- applies Groovy and JavaLibrary plugins
- activates source code jars (no javadoc, since this would make no sense)
- if `maven-publish` plugin is applied, configures the publication
- adds the `com.blackbuild.convention.groovy` plugin, which configures Groovy and spock dependencies, version can be set via the `klumModel` extension (defaulting to Groovy 3). If the configured project is not the root project **and** the root project has the `com.blackbuild.convention.groovy` plugin applied, the version will be inherited from the root project instead.
- Adds a `schemas` configuration, inherited by the api configuration, that can be use to declare dependencies on schema projects. 
- Creates a model descriptor for every entry in klumModel.topLevelScripts to be consumed by Create.FromClasspath.

A simple model project can look like:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-model' version '<klum-version>'
    id "maven-publish"
}

klumModel {
    groovyVersion = "3" // default
    schemas {
        schema "my-group:my-schema:1.0"
    }
    topLevelScript "my.group.schema.Configuration", "model.Configuration"
    topLevelScript "my.group.schema.server.Target", "model.server.Targets"
}
```

## Multi module

Schema and model can be combined in a multimodule project (with the pre mentioned problem of missing IDE support):
Because the root project applies the shared convention plugin at the matching KlumAST version, its child Schema and
Model projects can use the packaged plugin IDs without repeating that version.

Root:

```groovy
plugins {
    id 'com.blackbuild.convention.groovy' version '<klum-version>'
}

groovyDependencies {
    groovyVersion = "3"
}
```

Schema:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema'
}
```
Model:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-model'
}

klumModel {
    schemas {
        schema project(":schema")
    }
    topLevelScript "my.group.schema.Configuration", "model.Configuration"
    topLevelScript "my.group.schema.server.Target", "model.server.Targets"
}
```
