[![CI](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci.yml)

Welcome to KlumAST
==================
Turn your models into supermodels!


[![klum logo](img/klumlogo.png)](https://github.com/klum-dsl/klum-ast)

# Breaking changes

I have finally released 2.0.0 and 2.1.0. Both are based on the same major changes, including runtime dependencies, modularization, etc.

Users of 1.2.0 (or lower) should take a look at [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration).

2.1.0 does not introduce new features but drops all methods that were deprecated in 2.0.0.

2.x will be the last version of KlumAST that supports Groovy 2.x. The next major version will require Groovy 3.x and Java 17.

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

Find more details on in our [wiki](https://github.com/klum-dsl/klum-ast/wiki)
