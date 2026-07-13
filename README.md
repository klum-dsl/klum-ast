[![CI](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml)

Welcome to KlumAST
==================
Turn your models into supermodels!


[![klum logo](img/klumlogo.png)](https://github.com/klum-dsl/klum-ast)

# Breaking changes and version overview

4.0 is currently in development as a breaking Builder-first release. Generated factories now configure Builders and
materialize structurally immutable completed DSL Objects before validation. See the
[Builder-first migration guide](https://github.com/klum-dsl/klum-ast/wiki/Builder-First-Migration).

3.0 dropped support for Groovy 2.x and Java 11; the minimum Java version is 17, with Groovy 3, 4, and 5 supported.

Users of 1.2.0 (or lower) should take a look at [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration).

2.2 was the final 2.x feature release and introduced the current validation model. See
[Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation) and the historical sections of the migration guide.

# What is KlumAST?

KlumAST is the first part of the KlumDSL suite. It provides and easy way to create a complete DSL for a model classes.
 
There are two main objectives for this project:

- be as terse as possible while still being readable and using almost no boilerplate code

- Offer as much IDE-based assistance as possible.
  Since KlumAST uses AST transformations, this works out of the
  box for all major IDEs (as long as the model classes are separated from
  the actual configuration)

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

Find more details in our [wiki](https://github.com/klum-dsl/klum-ast/wiki).
