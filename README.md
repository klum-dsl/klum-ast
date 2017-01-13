[![Build Status](https://travis-ci.org/blackbuild/config-dsl.svg?branch=master)](https://travis-ci.org/blackbuild/config-dsl)

# ConfigDSL is now KlumDSL
For now artifact coordinates and github url remain unchanged

# Targeting 1.0
We are slowly approaching the 1.0 release, this means a lot of API clean up, which sadly means some incompatible changes. See
the CHANGES.md and the Issues for more details.

The goal is to stabilize the API and prune experimental features before releasing 1.0. Backward compatibility for pre 1.0
versions is in our focus, but not our main goal.

Config-DSL will also be renamed a get some additional companion tools. Whether this renaming also means a coordinate
(likely) and / or a package name change (possible) remains to be decided.


# Breaking changes since 0.17

the following features were dropped:
- pre using existing `create` and `apply` methods is no longer supported, this has been replaced by a lifecycle mechanism 
  ([#38](https://github.com/blackbuild/config-dsl/issues/38))
- named alternatives for dsl collections
- shortcut named mappings
- under the hood: the inner class for dsl-collections is now optional (GDSL needs to be adapted)
- member names must now be unique across hierarchies (i.e. it is illegal to annotate two collections with the same
  members value)
- the implicit template feature is deprecated and will eventually be dropped (see [#34](https://github.com/blackbuild/config-dsl/issues/34)), 
  it basically uses global variables, which is of course bad design
  
  The suggested way to use templates would be to explicitly call copyFrom() as first step in a template using configuration
  or using the new named parameters (`Model.create(copyFrom: myTemplate) {..}`)
  
  Alternatively, the new `withTemplate(s)` mechanism can be used (see [Template Mechanism])

# KlumDSL Transformation for Groovy
Groovy AST Transformation to allow easy, convenient and typesafe dsl configuration objects. There are two main objectives
for this project:

- be as terse as possible while still being readable and using almost no boilerplate code

- Offer as much IDE-based assistance as possible. 
    - In IDEA this works by including a custom gdsl file (mostly working)
    - In Eclipse, inclusion of a custom dsld file is planned

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

Note that since 0.17.0, the `projects` closure is only optional syntactic sugar, `project` entry could also be out
directly under config.

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

#Details

## Conventions
In the following documentation, we differentiate between three kinds of values:

### DSL-Objects
DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They
can either be keyed or unkeyed. Keyed means they have a designated field of type String (currently) decorated with the
 `@Key` annotation, acting as key for this class. DSL classes are automatically mafe `Serializable`.

### Collections
Collections are (currently either List or Map). Map keys are always Strings, List values and Map values can either be
simple types or DSL-Objects. Collections of Collections are currently not supported.

A collection field has two name properties: the collection name an the element name. The collection name defaults to
the name of the field, the element name is the name of the field minus any trailing s:

If the field name is `roles`, the default collection name is `roles` and the element name is `role`. 

If the field name does not end with an 's', the field name is reused as is (information -> information | information).

Collection name and element name can be customized via the @Field Annotation (see below).
 
*Collections must be strongly typed using generics!*
 

### Simple Values
Are everything else, i.e. simple values as well as more complex not-DSL objects.

## Basic usage:

KlumDSL consists of a number of Annotations: 

`@DSL` annotates all domain classes, i.e. classes of objects to be generated via the DSL.

`@Key` annotates the optional key field of a dsl object (see below).

`@Owner` annotates the optional owner field of a dsl object.

`@Field` is an optional field to further configure the handling of specific fields (esp. naming).

`@Validation` and `@Validate` provide automatic validation of model values.

`@PostCreate` and `@PostApply` can be used to designate lifecycle methods.

### @DSL
DSL is used to designate a DSL/Model object, which is enriched using the AST Transformation.

The DSL annotation leads to the creation of a couple of useful methods.

#### factory and apply methods

A factory method named `create` is generated, using either a single closure as parameter, or, in case of a keyed
object, using a String and a closure parameter.

```groovy
@DSL
class Config {
}

@DSL
class ConfigWithKey {
    @Key String name
}
```
        
creates the following methods:
    
```groovy
static Config create(Closure c = {})

static ConfigWithKey create(String name, Closure c = {})
```

If create method does already exist, a method named `_create` is created instead.

Additionally, an `apply` method is created, which takes single closure and applies it to an existing object. As with 
`create`, if the method already exists, a `_apply` method is created.
 
```groovy
def void apply(Closure c)
```

Both `apply` and `create` also support named parameters, allowing to set values in a concise way. Every map element of
the method call is converted in a setter call (actually, any method named like the key with a single argument will be called):


```groovy
Config.create {
    name "Dieter"
    age 15
}
```

Could also be written as:
```groovy
Config.create(name: 'Dieter', age: 15)
```

Of course, named parameters and regular calls inside the closure can be combined ad lib.

#### Lifecycle Methods
Lifecycle methods can are methods annotated with `@PostCreate` and `@PostApply`. These methods will be called automatically
after the creation of the object (**after afhe templatee has been applied**) and after the call to the apply method, respectively.

Lifecycle methods must not be `private`.


#### Convenience Factories

since 0.16, config-dsl supports convenience factory methods that allow reading a configuration directly from a file or
String:

`MyConfig.createFromScript(Class<Script>)` runs the given Script and returns the result. The script must return the
proper type, for example:

```groovy
MyConfig.create {
  value("bla")
}
```

`MyConfig.createFrom(text)` or `MyConfig.createFrom(key, text)` handles the given text as the content of the create 
closure and is usually used with a File or an URL as argument.

For example

```groovy
def config = Config.createFrom(new File("bla.groovy"))
```

and the file bla.groovy
```groovy
value("blub")
```

result in the following:

```groovy
assert config.value == "blub"
```

In case of a keyed object, the key is derived from the filename (the first segment, in the example above, the key would 
be "bla"). By using a small dsld-snippet in your IDE, you even get complete code completion and syntax highlighting an 
specialized config files.

This allows splitting configurations into different files, which might be automatically resolved by something like:
 
```groovy
Config.create {
    environments {
        new File("envdir").eachFile { file -> 
            environment(Environment.createFrom(file)) 
        }    
    }
}
```
 
__Note__: Currently, `createFrom` does not support any polymorphic creation. This might be added later,
 see: ([#43](https://github.com/blackbuild/config-dsl/issues/43))


#### copyFrom() method

Each DSLObject gets a `copyFrom()` method with its own class as parameter. This method copies fields from the given
object over to this objects, excluding key and owner fields. For non collection fields, only a reference is copied,
for Lists and Maps, shallow copies are created.

Currently, it is in discussion whether this should be deep clone instead, see: ([#36](https://github.com/blackbuild/config-dsl/issues/36))

#### equals() and toString() methods

If not yet present, `equals()` and `toString()` methods are generated using the respective ASTTransformations. You
can customize them by using the original ASTTransformations.
    
#### Field setter for simple fields

For each simple value field create an accessor named like the field, containing the field type as parameter 

```groovy
@DSL
class Config {
 String name
}
```

creates the following method:

```groovy
def name(String value)
```

Used by:
```groovy
Config.create {
   name "Hallo"
}
```

#### Default Values

Fields can be annotated with `@Default` to designate a default value, which is returned in case the value is not
Groovy true. Default values can either be simple values delegating to a different property, or complex closures.

```groovy
@DSL
class Config {
 String name
 @Default('name') String id
 @Default(code={name.toLowerCase()}) String lower
}
```

creates the following methods:

```groovy
String getId() {
    id ?: getName()
}

String getLower() {
    lower ?: name.toLowerCase() // actually a closure is called, including setting of delegate etc...
}
```

Usage:

```groovy
def config = Config.create {
    name 'Hans'
}

assert config.id == 'Hans' // defaults to name 
assert config.lower == 'hans' // defaults to lowercase name
```

Note that default values do work with DSL fields and collections as well.

#### Setter for simple collections
    
for each simple collection, two methods are generated:

-   a method with the collection name and a List/Vararg argument for list or a Map argument for maps. These methods
    *add* the given parameters to the collection 

-   an adder method named like the element name of the collection an containing a the element type 

```groovy
@DSL
class Config {
    List<String> roles
    Map<String, Integer> levels
}
```
    
creates the following methods:

```groovy
def roles(String... values)
def role(String value)
def levels(Map levels)
def level(String key, Integer value)
```

Usage:
```groovy
Config.create {
    roles "a", "b"
    role "another"
    levels a:5, b:10
    level "high", 8
}
```

If the collection has no initial value, it is automatically initialized.

#### Setters and closures for DSL-Object Fields
    
for each dsl-object field, a closure method is generated, if the field is a keyed object, this method has an additional
String parameter. Also, a regular setter method is created for reusing an existing object.
  
```groovy
@DSL
class Config {
    UnKeyed unkeyed
    Keyed keyed
}

@DSL
class UnKeyed {
    String name
}

@DSL
class Keyed {
    @Key String name
    String value
}
```
    
creates the following methods (in Config):

```groovy
def unkeyed(UnKeyed reuse) // reuse an exiting object
Unkeyed unkeyed(@DelegatesTo(Unkeyed) Closure closure)
def keyed(UnKeyed reuse) // reuse an exiting object
Keyed keyed(String key, @DelegatesTo(Unkeyed) Closure closure)
```

Usage:
```groovy
Config.create {
    unkeyed {
        name "other"
    }
    keyed("klaus") {
        value "a Value"
    }
}

def objectForReuse = UnKeyed.create { name = "reuse" }

Config.create {
    unkeyed objectForReuse
}
```

The closure methods return the created objects, so you can also do the following:

```groovy
def objectForReuse
Config.create {
    objectForReuse = unkeyed {
        name "other"
    }
}

Config.create {
    unkeyed objectForReuse
}
```

#### Collections of DSL Objects

Collections of DSL-Objects are created using a nested closure. The name of the (optional) outer closure is the field name, the
name of the inner closures the element name (which defaults to field name minus a trailing 's'). The syntax for adding
keyed members to a list and to a map is identical (obviously, only keyed objects can be added to a map).

The inner creator can also take an existing object instead of a closure, which adds that object to the collection.
In that case, **the owner field of the added object is only set, when it does not yet have an owner**.
 
This syntax is especially useful for delegating the creation of objects into a separate method.

As with simple objects, the inner closures return the existing object for reuse

```groovy
@DSL
class Config {
    List<UnKeyed> elements
    List<Keyed> keyedElements
    Map<String, Keyed> mapElements
}

@DSL
class UnKeyed {
    String name
}

@DSL
class Keyed {
    @Owner owner
    @Key String name
    String value
}

def objectForReuse = UnKeyed.create { name "reuse" }
def anotherObjectForReuse

def createAnObject(String name, String value) {
    Keyed.create(name) { value(value) }
}

Config.create {
    elements {
        element {
            name "an element"
        }
        element {
            name "another element"
        }
        element objectForReuse
    }
    keyedElements {
        anotherObjectForReuse = keyedElement ("klaus") {
            value "a Value"
        }
    }
    mapElements {
        mapElement ("dieter") {
            value "another"
        }
        mapElement anotherObjectForReuse // owner is NOT changed
        mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
    }
}

// flat syntax without nested closures:
Config.create {
    element {
        name "an element"
    }
    element {
        name "another element"
    }
    element objectForReuse
    anotherObjectForReuse = keyedElement ("klaus") {
        value "a Value"
    }
    mapElement ("dieter") {
        value "another"
    }
    mapElement anotherObjectForReuse // owner is NOT changed
    mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
}


```

### the @Key annotation

The key annotation is used to designate a special key field, making the annotated class a keyed class. This has the
following consequences:

- no setter method is generated for the key field
- a constructor using a single field of the key type is created (currently, only String is allowed)
- factory and apply methods get an additional key parameter
- only keyed classes are allowed as values in a Map
- keyed objects in collections get a special (experimental) shortcut creator syntax.

### The @Owner annotation

DSL-Objects can have an optional owner field, decorated with the `@Owner` annotation.

When the inner object (containing the owner) is added to another dsl-object, either directly or into a collection,
the owner-field is automatically set to the outer instance.

This has two dangers:

- no validity checks are performed during transformation time, leading to runtime ClassCastExceptions if the owner
  type is incorrect
- If an object that already has an existing owner is added using the `_use()` method, an IllegalStateException is thrown.
  Thus, an object can only be _used_ once (either directly or using the `_use()` method). In other words,
  `_use()` can only be used for objects created outside of the configuration structure.
- if an object is _reused_ instead (using `_reuse()`, the owner field will not be overridden.

```groovy
@DSL
class Foo {
    Bar bar
}

@DSL
class Bar {
    @Owner Foo owner
}

def c = Config.create {
    bar {}
}

assert c.bar.owner === c
```


## Template Mechanism

*The previous `createTemplate` method has been deprecated in favor of the new mechanism described below*

The system includes a simple mechanism for configuring default values (as part of the instance creation), not in the classes:

Templates are regular instances of DSL objects, which will usually be assigned to a local variable. Applying a template means
 that all non-null / non-empty fields in the template are copied over from template. For Lists and Maps, shallow copies 
 will be created. 
 
 Ignorable fields of the template (key, owner, transient or marked as `@Ignore`) are never copied over. To make creating
 templates easier, the `makeTemplate` method is provided (actually, `createTemplate` would be more convenient, unfortunately 
 it is already used by the deprecated global template mechanism), which has the following behaviour:
 
 - is always unkeyed (setting the key to null in case of a keyed class)
 - validation is turned off (since null as key might lead to an invalid object)
 - provides a non-abstract implementation for abstract classes, implementing all possible methods empty or returning null
 
 ```groovy
@DSL
abstract class Parent {
    abstract int calcValue()
}

def template = Parent.makeTemplate()
```
 
Templates are also correctly applied when using inheritance, i.e. if a template is defined for the parent class,
it is also applied when creating child class instances. Child template values can override parent templates. For examples
see the test cases in TemplateSpec.

 
There currently four options to apply templates, all examples use the following class and template:

```groovy
@DSL
class Config {
    String url
    List<String> roles
}

def template = Config.create {
    url "http://x.y"
    roles "developer", "guest"
}
```
 
### copyFrom())

Using `copyFrom`, one can explicitly apply a template to a single Object to be created:

```groovy
def c = Config.create {
    copyFrom template
    url "z"
}

// more convenient using the named parameters syntax
def c2 = Config.create(copyFrom: template) {
    url "z"
}
```

In both notations, the `copyFrom` entry should be the first, otherwise it might override values set before it. 

### withTemplate()
 
`withTemplate()` provides scope templates. It takes a template and a closure, and the template is automatically 
applied to all instance creations within that closure.
 
 __A template is only applied inside the scope when using the `create()` method, it is NOT invoked when using the 
 constructor directly!__ 

Usage:
```groovy
def template = Config.create {
    url "http://x.y"
    roles "developer", "guest"
}

def c, d
Config.withTemplate(template) {
    c = Config.create {
        roles "productowner"
    }
    d = Config.create {
        roles "scrummaster"
    }
}

assert c.url == "http://x.y"
assert c.roles == [ "developer", "guest", "productowner" ]
assert c.roles == [ "developer", "guest", "scrummaster" ]
```

### With anonymous template
`withTemplate` can also be called using only named parameters, creating a temporary, anonymous template:

```groovy
Config.withTemplate(Config.create(url: "http://x.y")) {
    c = Config.create {
        roles "productowner"
    }
}
```

could be written as:

```groovy
Config.withTemplate(url: "http://x.y") {
    c = Config.create {
        roles "productowner"
    }
}
```

### withTemplates()

`withTemplates` is a convenient way of applying multiple templates at one. It takes one of the following arguments:

- a List of template objects, which are applied to their respective classes (templates for abstract classes are applied
top the real class)
- a Map of classes to template instances. Can be used to explicitly define which class is used
- a Map of classes to a Map. Uses the convenience syntax to create anonymous templates on the fly

Instead of writing something like this:

```groovy
Environment.withTemplate(defaultEnvironment) {
    Server.withTemplate(defaultServer) {
        Host.template(defaultHost) {
            Config.create {
                // ...                    
            }
        }
    }
}
```

One can also write:

```groovy
Config.withTemplates([defaultEnvironment, defaultServer, defaultHost]) {
    Config.create {
        // ...                    
    }
}
```

or

```groovy
Config.withTemplates((Environment) : defaultEnvironment, (Server) : defaultServer, (Host) : defaultHost) {
    Config.create {
        // ...                    
    }
}
```

or, using anonymous templates:
```groovy
Config.withTemplates((Environment) : [status: 'valid'], (Server) : [os: 'linux', arch: 'x64'], (Host) : [user: 'deploy']) {
    Config.create {
        // ...                    
    }
}
```

Note that Groovy requires the key object to be in parantheses if it is not a String.

### templates for abstract classes

For abstract classes, an inner Class name `Template` is created with the following properties:

- all abstract methods are implemented empty
- validation is turned of

Anonymous templates automatically use the Template class.


### Order of precedence

The order of precedence is

- initialization / constructor values
- values in a custom create method
- templates of parent classes
- own templates
- explicit setter methods

I.e. given the following code:

```groovy
@DSL
class Parent {
    String name = "default"
}

@DSL
class Child extends Parent {
}

Parent.createTemplate {
    name "parent-template" // overrides default value
}

Child.createTemplate {
    name "child-template" // overrides parent template value
}

def c = Child.create {}

assert c.name == "child-template"

def d = Child.create {
    name "explicit" // overrides template value
}

assert d.name == "explicit"
```

Note that templates for collections **add** to lower precedence values, they do **not** replace them: 

```groovy
@DSL
class Parent {
    List<String> names = ["default"]
}

@DSL
class Child extends Parent {
}

Parent.createTemplate {
    names "parent" // adds to default value
}

Child.createTemplate {
    names "child" // adds to parent template value
}

def c = Child.create {
    name == "explicit" // adds to template value
}

assert c.names == ["default", "parent", "child", "explicit"]
```

If you want child template to replace the parent, you have to do so explicitly by not using the generated setter:

```groovy
@DSL
class Parent {
    List<String> names = ["default"]
}

@DSL
class Child extends Parent {
}

Parent.createTemplate {
    names "parent" // adds to default value
}

Child.createTemplate {
    names = ["child"] // explicitly override parent template
}

def c = Child.create {
    name == "explicit" // adds to template value
}

assert c.names == ["child", "explicit"]
```


## DSL Object Inheritance

DSLObjects can inherit from other DSL-Objects (but the child class *must* be annotated with DSL as well). This
allows polymorphic usage of fields. To allow to specify the concrete implementation, setter methods are generated
which take an additional Class parameter.

These typed methods are not generated, if the declared type is final. Likewise, if the declared type is abstract, 
*only* the typed methods are generated.

```groovy
@DSL
class Config {
    Project project 
}

@DSL
class Project {
    String name
}

@DSL
class MavenProject extends Project{
    List<String> mvnOpts
}

Config.create {
    project(MavenProject) {
        name "demo"
        mvnOpts "a", "b"
    }
}
```

This works identically with keyed objects.

```groovy
@DSL
class Config {
    Project project 
}

@DSL
class Project {
    @Key String name
}

@DSL
class MavenProject extends Project{
    List<String> mvnOpts
}

Config.create {
    project(MavenProject, "demo") {
        mvnOpts "a", "b"
    }
}
```

Note that it is illegal to let a keyed class inherit from a not keyed class. The topmost dsl class in the hierarchy
decides whether the whole hierarchy is keyed or not. 

### Alternatives syntax

__The Alternatives syntax has currently been removed. It will be reimplemented later (post 1.0)__


~~There is also a convenient syntax for declaring base classes directly, using the `alternatives` attribute of the 
`Field` annotation. `alternatives` takes a list of classes for which shortcut methods will be created. See the 
following example:~~
 

~~*Future plans for this are to automatically strip the base class name from the method names (i.e. when all subclasses
end with the name of the parent class - as in the example above - the base class name could be removed,
leading to `maven`, `gradle` and `project` as the allowed names.*~~

## Validation

Resulting objects can be automatically be validated. This is controlled via two annotations `@Validate` and `@Validation`.

### `@Validation`
`@Validation` controls validation on Class level. Using the `option` value, the handling of unmarked fields can
be configured. With `IGNORE_UNMARKED`, the default setting, only those fields are validated that have been marked
with the `@Validate` annotation. With `VALIDATE_UNMARKED`, all non-annotated fields are validated against Groovy truth
 (i.e. numbers must be non-zero, collections and Strings non-empty and other objects not null).
 
 `mode` controls _when_ validation should happen. The default is `AUTOMATIC` which automatically validates objects at the 
 end of the `create()` method. By setting this to `MANUAL` validation must be manually initiated using the `validate()`
 method. This can be used to defer the validation if the final objects is to be assembled in multiple steps.
 
 The same effect as `Option.MANUAL` can be achieved for single instances by using the `manualValidation(boolean)` method.
   
### `@Validate`
The `@Validate` annotation controls validation of a single field. If the annotation is not present, the `@Validation.mode` 
 controls whether this field will be evaluated. If present, the `value` field contains the actual validation criteria. 
 This can be one of the following:
 
 * `Validate.GroovyTruth` (default), to validate this field against Groovy Truth
 * `Validate.Ignore` excludes this field from validation, this can be necessary when the validation option is set
  to `VALIDATE_UNMARKED`
 * A closure that takes a single argument, the value of the field. The result of this closure is evaluated according
 to Groovy Truth
```groovy
@DSL
class Figure {
 @Validate({ it > 2})
 int edges
}
```
  
 The annotation can also contain an additional `message` value further describing the constraint, this is included in
 the error message.
 
### `doValidate()`
If present, a parameter less `doValue()` method is called during validation to allow performing multi value validation:

```groovy
@DSL
class Person {
 String name
 int age
 List<String> beers
 
 private doValidate() {
    if (age < 18 && beers)
      throw new IllegalStateException("Minors are not allowed to drink beer")
 }
}
```

Any exception or `AssertionError` thrown during validation is wrapped in an `IllegalArgumentException`. This allows
 the convenient use of Groovy's Power Assertion.
 

Future plans:

- Map keys should not be restricted to Strings (esp. enums would be useful)
- Eclipse dsld
