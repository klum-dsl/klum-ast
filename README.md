[![CI-Groovy 2.4](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-2.4.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-2.4.yml)
[![CI-Groovy 3](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-3.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-3.yml)
[![CI-Groovy 4](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-4.yml/badge.svg?branch=master)](https://github.com/klum-dsl/klum-ast/actions/workflows/ci-groovy-4.yml)

Welcome to KlumAST
==================
Turn your models into supermodels!



[![klum logo](img/klumlogo.png)](https://github.com/klum-dsl/klum-ast)

# Breaking changes

I decided to release 1.2.0 prior to doing some extensive refactorings including splitting into runtime and compile
time dependencies.

Thus 1.2.0 is simply a step up to 2.0.0, mainly designed to replace various rc-version in use.

Some new features being introduced in 1.2.0 need KlumAST to
be present on the classpath during runtime.

Closure are now all `DELEGATE_ONLY`, this means methods of an outer object cannot be
directly accessed. This is cleaner and somewhat closer to the behaviour of
xml or other structured languages. Accessing outer methods should be a corner case.
Take a look at [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration) if you encounter errors.

Factory methods on DSL classes are deprecated in favor of a single `Create` class field which encapsulates all
relevant factory methods.

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
