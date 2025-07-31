KlumAST provides some Gradle plugins to make the setup of a Klum project easier. They consist of the following plugins:

# com.blackbuild.convention.groovy

This plugin is not specific to Klum and might be extracted to a separate project in the future. It basically sets the necessary dependencies for Groovy as well as a matching version of the Spock Framework.

The version can be set directly via two properties, or - more conveniently - via a single enum property:

```groovy
import com.blackbuild.klum.ast.gradle.convention.GroovyVersion

plugins {
    id 'com.blackbuild.convention.groovy' version '<version>'
    id 'groovy'
}

groovyDependencies {
    groovyVersion = GroovyVersion.GROOVY_24 // or GROOVY_3 or GROOVY_4
}
```

Note that the plugin does **not** apply the groovy plugin, it only reacts to its presence.

Applying the plugin (provided the Groovy plugin is also applied) does the following thing:

- Create two additional configurations, `groovy` and `spock`, which are used to declare the dependencies on Groovy and Spock
- Adding default dependencies dependening on the value of groovyVersion (or specifically 'groovy' and 'spock' parameters).
- linking those configurations to the `compileOnly` and `testImplementation` configurations
- Configure all test tasks with `useJunitPlatform()` if the Groovy version is different from 2.4

If the plugin is applied to a child project, it will inherit the configured Groovy versions from the root project, if applicable (even if the Groovy plugin is not applied to the root project). That way, the Groovy version can be set in a single place. In a klum project, this is usually the only situation where the convention plugin needs to be used directly, as the other two plugins will apply it automatically.

# com.blackbuild.klum-ast-schema

This plugin is used in schema projects (as well as `api` as defined by [Layer3]). It does the following things:

- applies the annodocimal plugin for generating documentation
- applies Groovy and JavaLibrary plugins
- activates source code and javadoc jars
- if `maven-publish` plugin is applied, configures the publication
- adds the `com.blackbuild.convention.groovy` plugin, which configures Groovy and spock dependencies, version can be set via the `klumSchema` extension (defaulting to Groovy 3). If the configured project is not the root project **and** the root project has the `com.blackbuild.convention.groovy` plugin applied, the version will be inherited from the root project instead.
- adds the necessary dependencies for KlumAST itself:
  - `klum-ast` as compileOnly dependency
  - `klum-ast-runtime` as api dependency
  - both dependencies use the same version as the plugin itself

This means that a fully working schema project can be set up with the following minimal build.gradle:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema'
    id "maven-publish"
}

publishing {
    repositories {...}
}
```
# com.blackbuild.klum-ast-model

This plugin is used in model projects. It does the following things:

- applies Groovy and JavaLibrary plugins
- activates source code jars (no javadoc, since this would make no sense)
- if `maven-publish` plugin is applied, configures the publication
- adds the `com.blackbuild.convention.groovy` plugin, which configures Groovy and spock dependencies, version can be set via the `klumModel` extension (defaulting to Groovy 3). If the configured project is not the root project **and** the root project has the `com.blackbuild.convention.groovy` plugin applied, the version will be inherited from the root project instead.
- Adds a schema configuration, inherited by the api configuration, that can be use to declare dependencies on schema projects. 
- Creates a model descriptor for every entry in klumModel.topLevelScripts to be consumed by Create.FromClasspath.

A simple model project can look like:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-model' version "2.0.0"
    id "maven-publish"
}

klumModel {
    groovyVersion = GroovyVersion.GROOVY_3 // default
    schemas {
        schema "my-group:my-schema:1.0"
    }
    topLevelScript "my.group.schema.Configuration", "model.Configuration"
    topLevelScript "my.group.schema.server.Target", "model.server.Targets"
}
```

# Multi module

Schema and model can be combined in a multimodule project (with the pre mentioned problem of missing IDE support):

Root:

```groovy
import com.blackbuild.klum.ast.gradle.convention.GroovyVersion

plugins {
    id 'com.blackbuild.convention.groovy' version '2.0.0'
}

groovyDependencies {
    groovyVersion = GroovyVersion.GROOVY_24
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
