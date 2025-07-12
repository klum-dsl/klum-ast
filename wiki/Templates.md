*The previous methods `createTemplate` and `createAsTemplate` has been deprecated in favor of the new mechanism described below*

The system includes a simple mechanism for configuring default values (as part of the instance creation, not in the classes):

Templates are regular instances of DSL objects, which will usually be assigned to a local variable. Applying a template means
 that all non-null / non-empty fields in the template are copied over from template. For Lists and Maps, deep copies 
 will be created. 
 
 Ignorable fields of the template (key, owner, transient or marked as `FieldType.Ignore`) are never copied over. To make creating
 templates easier, the `Create.Template` and `Create.TemplateFrom` methods are provided, which behave like normal factory methods
 with the following differences:
 
 - the result is always unkeyed (setting the key to null in case of a keyed class)
 - Lifecycle methods (`@PostApply`, `@PostCreate`) are not called
 - The template does not participate in KlumPhases (especially: no validation is performed)
 - provides a non-abstract implementation for abstract classes, implementing all possible methods empty or returning null
 
 ```groovy
@DSL
abstract class Parent {
    abstract int calcValue()
}

def template = Parent.Create.Template()
```
 
Templates are also correctly applied when using inheritance, i.e. if a template is defined for the parent class,
it is also applied when creating child class instances. Child template values can override parent templates. For examples
see the test cases in TemplateSpec.

As with normal factory methods, templates can be created using the `Create.Template` method by applying a map and or configuration
closure, or by using the `Create.TemplateFrom` method, which take a file or URL which is parsed as a DelegatingScript, 
similar to to `Create.From` methods.

There currently four options to apply templates, all examples use the following class and template:

```groovy
@DSL
class Config {
    String url
    List<String> roles
}

def template = Config.Create.Template {
    url "http://x.y"
    roles "developer", "guest"
}
```
 
# copyFrom()

Using `copyFrom`, one can explicitly apply a template to a single Object to be created:

```groovy
def c = Config.Create.With {
    copyFrom template
    url "z"
}

// more convenient using the named parameters syntax
def c2 = Config.Create.With(copyFrom: template) {
    url "z"
}
```

In both notations, the `copyFrom` entry should be the first, otherwise it might override values set before it. 

# withTemplate()
 
`withTemplate()` provides scoped templates. It takes a template and a closure, and the template is automatically 
applied to all instance creations within that closure.
 
 __A template is only applied inside the scope when using the `Create.*` methods (or one of the [[Convenience Factories]]), it is NOT invoked when using the 
 constructor directly!__ 

Usage:
```groovy
def template = Config.Create.With {
    url "http://x.y"
    roles "developer", "guest"
}

def c, d
Config.withTemplate(template) {
    c = Config.Create.With {
        roles "productowner"
    }
    d = Config.Create.With {
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
Config.withTemplate(Config.Create.With(url: "http://x.y")) {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

could be written as:

```groovy
Config.withTemplate(url: "http://x.y") {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

# withTemplate for collection factories

When using the optional collection factory (see [[Basics#collections-of-dsl-objects]]), a template can directly be
specified, either explicitly or as an anonymous template. This template is automatically valid for all elements
that are created inside this collection factory:

```groovy
Config.Create.With {
    servers(isCluster: true) { // factory with template
        server("x") {}
        server("y") {}
    }
}
```

Since the collection factory can be called multiple times, this allows a very concise syntax:

```groovy
Config.Create.With {
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
- a Map of classes to a Map. Uses the convenience syntax to create anonymous templates on the fly

Instead of writing something like this:

```groovy
Environment.withTemplate(defaultEnvironment) {
    Server.withTemplate(defaultServer) {
        Host.template(defaultHost) {
            Config.Create.With {
                // ...                    
            }
        }
    }
}
```

One can also write:

```groovy
Config.withTemplates([defaultEnvironment, defaultServer, defaultHost]) {
    Config.Create.With {
        // ...                    
    }
}
```

or, using anonymous templates:
```groovy
Config.withTemplates((Environment) : [status: 'valid'], (Server) : [os: 'linux', arch: 'x64'], (Host) : [user: 'deploy']) {
    Config.Create.With {
        // ...                    
    }
}
```

Note that Groovy requires the key object to be in parentheses if it is not a String.

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

def parentTemplate = Parent.Create.Template {
    name "parent-template" // overrides default value
}

def childTemplate = Child.Create.Template {
    name "child-template" // overrides parent template value
}

Child.withTemplates([parentTemplate, childTemplate]) {
  def c = Child.Create.One()

  assert c.name == "child-template"
 
  def d = Child.Create.With {
     name "explicit" // overrides template value
  }
 
  assert d.name == "explicit"

}

```

Note that templates for collections **replace** lower precedence, i.e. the most specific template wins. This behavior can be altered using [[Copy Strategies]]. 

```groovy
@DSL
class Parent {
    List<String> names = ["default"]
}

@DSL
class Child extends Parent {
}

def parentTemplate = Parent.Create.Template {
    names "parent" // replaces default value
}

def childTemplate = Child.Create.Template {
    names "child" // replaces parent template value
}

Child.withTemplates([parentTemplate, childTemplate]) {
  def c = Child.Create.With {
    name "explicit" // replaces template value
  }

  assert c.names == ["explicit"]
}
```

