[![CI](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml)

Welcome to KlumAST
==================
Turn your models into supermodels!


[![klum logo](img/klumlogo.png)](https://github.com/klum-dsl/klum-ast)

# Breaking changes and version overview

4.0 is currently in development as a breaking Builder-first release. Generated factories now configure Builders and
materialize structurally immutable completed DSL Objects before validation. See the
[Builder-first migration guide](docs/user/Builder-First-Migration.md).

3.0 dropped support for Groovy 2.x and Java 11; the minimum Java version is 17, with Groovy 3, 4, and 5 supported.

Users of 1.2.0 (or lower) should take a look at the historical [Migration](https://klum-dsl.github.io/klum-ast/3.0.1/Migration/) guidance.

2.2 was the final 2.x feature release and introduced the current validation model. See
[Validation](https://klum-dsl.github.io/klum-ast/3.0.1/Validation/) and the historical sections of the migration guide.

# What is KlumAST?

KlumAST turns annotated model classes into concise, statically checked Groovy DSLs.

## Why models as code?

A useful model-as-code approach should be easy to author, clear to change, and safe to verify:

- **Automate model construction.** Generated Builders, factories, and DSL mutators remove repetitive implementation work
  while retaining statically checked Groovy source.
- **Support the authoring experience.** Generated Builder documentation and IDE mirrors make the model's construction
  surface discoverable without hand-maintained DSL stubs.
- **Validate and test the actual model.** A Schema can declare constraints, and each generated root factory runs
  validation as it materializes a completed model. Model-specific scenarios are ordinary unit tests: construct a model,
  assert its completed state or validation result, and run the same tests locally and in a pull-request build. They
  complement, rather than replace, integration tests against the eventual target.

Typical validation output names the rule that emitted it:

```text
- ERROR #ConnectivityChecks.portMustBeInRange(): port must be between 1 and 65535
```

Construction paths retain the source context, which is especially useful when a model is split across scripts. For
example, an illustrative failure from `models/production.groovy` could read:

```text
<root>.service($/Deployment.From:file(models/production.groovy)/service):
- ERROR #port: Field 'port' must be set
```

These capabilities make KlumAST especially useful for GitOps and other `*aC` (anything-as-code) workflows. Git can record a
configuration's structure, but a checked-in structure is not necessarily a verified model. Read
[why `*aC` needs model-level tests](docs/user/Why-aC-is-not-enough.md) for the rationale.

## Example

Given the following config classes:

```groovy
@DSL
class Config {
    Map<String, Project> projects
    boolean debugMode
    List<String> options
}

@DSL
class Project {
    @Key String name
    String url
    MavenConfig mvn
}

@DSL
class MavenConfig {
    List<String> goals
    List<String> profiles
    List<String> cliOptions
}
```

A config object can be created with the following dsl:

```groovy
def github = "http://github.com"


def config = Config.Create.With {

    debugMode true
    
    options "demo", "fast"
    option "another"
    
    projects {
        project("demo") {
            url "$github/x/y"
            
            mvn {
                goals "clean", "compile"
                profile "ci"
                profile "!developer"
                
                cliOptions "-X -pl :abc".split(" ")
            }
        }
        project("demo2") {
            url "$github/a/b"
            
            mvn {
                goals "compile"
                profile "ci"
            }
        }
    }
}
```

Find more details in the current [user documentation](docs/user/Home.md).
