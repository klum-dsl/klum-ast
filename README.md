[![Build Status](https://travis-ci.org/pauxus/config-dsl.svg?branch=master)](https://travis-ci.org/pauxus/config-dsl)

# ConfigDSL Transformation for Groovy
Groovy AST Tranformation to allow easy, convenient and typesafe dsl configuration objects. There are two main objectives
for this project:

- be as terse as possible while still being readable and using almost no boilerplate code

- Offer as much IDE-based assistance as possible. 
    - In IDEA this works by including a custom gdsl file (mostly working)
    - In Eclipse, inclusion of a custom dsld file is planned

## Example

Given the following config classes:

```groovy
@DSLConfig
class Config {
    Map<String, Project> projects
    boolean debugMode
    List<String> options
}

@DSLConfig(key = "name")
class Project {
    String name
    String url
    MavenConfig mvn
}

@DSLConfig
class MavenConfig {
    List<String> goals
    List<String> profiles
    List<String> cliOptions
}
```

A config object can be created with the following dsl:

```groovy
def github = "http://github.com"


def config = Config.create {

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

Since complete objects are created, using the configuration is simple:

```groovy
if (config.debugMode) println "Debug mode is active!"

config.projects.each { name, project ->
    println "Running $name with '${project.mvn.goals.join(' ')}'"
}

def projectsWithoutClean = config.projects.findAll { !it.value.mvn.goals.contains("clean")}.values()

if (projectsWithoutClean) {
    println "WARNING: The following projects do not clean before build:"
    projectsWithoutClean.each {
        println it    
    }
}
```

We can also use a simple syntax for different subclasses of Project

```groovy
@DSLConfig
class Config {
    @DSLField(alternatives=[MavenProject, GradleProject])
    Map<String, Project> projects
    boolean debugMode
    List<String> options
}

@DSLConfig(key = "name")
abstract class Project {
    String name
    String url
}

@DSLConfig
class MavenProject extends Project{
    List<String> goals
    List<String> profiles
    List<String> cliOptions
}

@DSLConfig
class GradleProject extends Project{
    List<String> Tasks
    List<String> options
}
```

And use the alternatives syntax in our dsl:

```groovy
def github = "http://github.com"

def config = Config.create {

    projects {
        mavenProject("demo") {
            url "$github/x/y"
            
            goals "clean", "compile"
            profile "ci"
            profile "!developer"
            
            cliOptions "-X -pl :abc".split(" ")
        }
        gradleProject("demo2") {
            url "$github/a/b"

            tasks "build"
        }
    }
}
```

In this approach, the `@DSLField(alternatives=[MavenProject, GradleProject])` annotation lists all possible subclasses
of Project and creates appropriate closure methods.

#Details

## Conventions
In the following documentation, we differentiate between three kinds of values:

### DSL-Objects
DSL Objects are annotated with "@DSLConfig". These are (potentially complex) objects enhanced by the transformation. They
can either be keyed or unkeyed. Keyed means they have a designated field of type String acting as key for this class.

### Collections
Collections are (currently either List or Map). Map keys are always Strings, List values and Map values can either be
simple types ord DSL-Objects. Collections of Collections are currently not supported.

A collection field has two name properties: the collection name an the element name. The collection name defaults to
the name of the field, the element name is the name of the field minus any trailing s:

If the field name is `roles`, the default collection name is `roles` and the element name is `role`. 

If the field name does not end with an 's', the field name is reused as is (information -> information | information).

Collection name and element name can be customized via the @DSLField Annotation (see below).
 
*Collections must be strongly typed using generics!*
 

### Simple Values
Are everything else, i.e. simple values as well as more complex not-DSL objects.

## Basic usage:

ConfigDSL consists of two Annotations:

### @DSLConfig
DSLConfig is used to designate a DSL-Configuration object, which is enriched using the AST Transformation.

It creates the following methods:

#### factory and apply methods

A factory method named `create` is generated, using either a single closure as parameter, or, in case of a keyed
object, using a String and a closure parameter.

```groovy
@DSLConfig
class Config {
}

@DSLConfig(key = "name"
class ConfigWithKey {
    String name
}
```
    
        
creates the following methods:
    
```groovy
static Config create(Closure c)

static ConfigWithKey create(String name, Closure c)
```

If create method does already exist, a method named `_create` is created instead.

Additionally, an `apply` method is created, which takes single closure and applies it to an existing object. As with 
`create`, if the method already exists, a `_apply` method is created.
 
```groovy
def void apply(Closure c)
```
    
#### Field setter

- For each simple value field create an accessor named like the field, containing the field type as parameter 

   ```groovy
    @DSLConfig
    class Config {
      String name
    }
    ```

creates the following method:

    ```groovy
    def name(String value)
    ```


-   for each simple collection, two methods are generated:

    -   a method with the collection name and a List/Vararg argument for list or a Map argument for maps. These methods
        *add* the given parameters to the collection 
  
    -   an adder method named like the element name of the collection an containing a the element type 

    ```groovy
    @DSLConfig
    class Config {
        List<String> roles
    }
    ```

        
    creates the following methods:

    ```groovy
    def roles(String... values)
    def role(String value)
    ```

TODO: continue


Future plans:

- automatic validation of generated objects
- Map keys should not be restricted to Strings
- Eclipse dsld
- owner references
- strip common suffixes from alternative names
- allow custom names for alternatives
