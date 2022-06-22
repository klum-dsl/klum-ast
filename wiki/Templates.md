*The previous `createTemplate` method has been deprecated in favor of the new mechanism described below*

The system includes a simple mechanism for configuring default values (as part of the instance creation, not in the classes):

Templates are regular instances of DSL objects, which will usually be assigned to a local variable. Applying a template means
 that all non-null / non-empty fields in the template are copied over from template. For Lists and Maps, deep copies 
 will be created. 
 
 Ignorable fields of the template (key, owner, transient or marked as `@Ignore`) are never copied over. To make creating
 templates easier, the `createAsTemplate` method is provided, which has the following behaviour:
 
 - is always unkeyed (setting the key to null in case of a keyed class)
 - validation is turned off (since null as key might lead to an invalid object)
 - Lifecycle methods (`@PostApply`, `@PostCreate` and future PostTree/PostModel) are not called
 - provides a non-abstract implementation for abstract classes, implementing all possible methods empty or returning null
 
 ```groovy
@DSL
abstract class Parent {
    abstract int calcValue()
}

def template = Parent.createAsTemplate()
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
 
# copyFrom()

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

# withTemplate()
 
`withTemplate()` provides scoped templates. It takes a template and a closure, and the template is automatically 
applied to all instance creations within that closure.
 
 __A template is only applied inside the scope when using the `create()` method (or one of the [[Convenience Factories]]), it is NOT invoked when using the 
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

# With anonymous template
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

# withTemplate for collection factories

When using the optional collection factory (see [[Basics#collections-of-dsl-objects]]), a template can directly be
specified, either explicitly or as an anonymous template. This template is automatically valid for all elements
that are create inside this collection factory:

```groovy
Config.create {
    servers(isCluster: true) { // factory with template
        server("x") {}
        server("y") {}
    }
}
```

Since the collection factory can be called multiple times, this allows a very concise syntax:

```groovy
Config.create {
    servers(isCluster: true) { // template is only valid in this block
        server("x") {}
        server("y") {}
    }
    servers(isCluster: false) { // use different template
        server("a") {}
        server("b") {}
    }
    servers(myServerTemplate) { // use yet another template
        server("i") {}
        server("j") {}
    }
}
```

# withTemplates()

`withTemplates` is a convenient way of applying multiple templates at one. It takes one of the following arguments:

- a List of template objects, which are applied to their respective classes (templates for abstract classes are applied
to the real class)
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

# templates for abstract classes

For abstract classes, an inner class named `Template` is created with the following properties:

- all abstract methods are implemented empty
- validation is turned of

Anonymous templates automatically use the Template class.


# Order of precedence

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
