# Templates

Templates are completed DSL Objects marked as reusable construction recipes. Applying a Template copies its non-null and
non-empty values into a fresh Builder graph. Nested DSL Objects are rehydrated recursively, Collections are copied, and
Simple Values are retained. A Template is never adopted directly as owned composition.

Template identity is persistent and graph-wide. Every owned node created by `Template.Create` is marked as a Template and
keeps its breadcrumb and model path; a completed object supplied to a `FieldType.LINK` field remains the same ordinary
model. Templates cannot be assigned directly to any relationship, including `LINK`: apply them through `Template.With`,
`copyFrom`, or another Template/copy API so KlumAST can build a fresh owned graph.

Deferred `applyLater` actions live only in immutable Template recipe state, never in ordinary completed models. Recipe
closures are detached and their captured graph is checked when the Template materializes. Captured Builders and
non-serializable values are rejected. Template identity and recipe state survive Java serialization; Builders,
Construction sessions, active Template scopes, and mutable recipe collections are not serialized.
 
 Ignorable fields of the template (key, owner, transient or marked as `FieldType.Ignore`) are never copied over. To make creating
 templates easier, the `Template.Create` and `Template.CreateFrom` methods are provided, which behave like normal factory methods
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

def template = Parent.Template.Create()
```
 
Templates are also correctly applied when using inheritance: a template defined for a parent class is applied when
creating child-class instances, and child template values can override parent templates. The executable regression
coverage is in `BoundTemplatesSpec.groovy`; [#491](https://github.com/klum-dsl/klum-ast/issues/491) retains the task of selecting and linking a dedicated documentary
happy path for this page.

Template specific methods are pooled in the `Template` field of each DSL class, which points to an instance of `BoundTemplateHandler` - so similar to Type.Create.* methods, there are Type.Template.* methods described below.

As with normal factory methods, templates can be created using the `Template.Create` method by applying a map and or configuration
closure, or by using the `Template.CreateFrom` method, which take a file or URL which is parsed as a DelegatingScript, 
similar to the `Create.From` methods.

There currently four options to apply templates, all examples use the following class and template:

```groovy
@DSL
class Config {
    String url
    List<String> roles
}

def template = Config.Template.Create {
    url "http://x.y"
    roles "developer", "guest"
}
```
 
## `copyFrom()`

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

In both notations, the `copyFrom` entry should be the first, otherwise it might override values set before it. A marked
Template contributes both values and recipe actions. An ordinary completed model contributes values only. See
[[Copy Strategies#copy-source-protocol]] for the complete copy-source rules.

## Template.With()
 
`Template.With()` provides scoped templates. It takes a template and a closure, and the template is automatically 
applied to all instance creations within that closure.
 
Usage:
```groovy
def template = Config.Template.Create {
    url "http://x.y"
    roles "developer", "guest"
}

def c, d
Config.Template.With(template) {
    c = Config.Create.With {
        roles "productowner"
    }
    d = Config.Create.With {
        roles "scrummaster"
    }
}

assert c.url == "http://x.y"
assert c.roles == [ "developer", "guest", "productowner" ]
assert d.roles == [ "developer", "guest", "scrummaster" ]
```

## With an Anonymous Template
`Template.With` can also be called using only named parameters, creating a temporary, anonymous template:

```groovy
Config.Template.With(Config.Template.Create(url: "http://x.y")) {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

could be written as:

```groovy
Config.Template.With(url: "http://x.y") {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

## Templates for Collection Factories

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

## Template.WithAll()

`Template.WithAll` is a convenient way of applying multiple templates at one. It takes one of the following arguments:

- a List of template objects, which are applied to their respective classes (templates for abstract classes are applied
to the real class)
- a Map of classes to a Map. Uses the convenience syntax to create anonymous templates on the fly

Instead of writing something like this:

```groovy
Environment.Template.With(defaultEnvironment) {
    Server.Template.With(defaultServer) {
        Host.Template.With(defaultHost) {
            Config.Create.With {
                // ...                    
            }
        }
    }
}
```

One can also write:

```groovy
Config.Template.WithAll([defaultEnvironment, defaultServer, defaultHost]) {
    Config.Create.With {
        // ...                    
    }
}
```

or, using anonymous templates:
```groovy
Config.Template.WithAll((Environment) : [status: 'valid'], (Server) : [os: 'linux', arch: 'x64'], (Host) : [user: 'deploy']) {
    Config.Create.With {
        // ...                    
    }
}
```

Note that Groovy requires the key object to be in parentheses if it is not a String.

## Templates for Abstract Classes

For abstract classes, an inner class named `Template` is created with the following properties:

- all abstract methods are implemented empty
- validation is turned of

Anonymous templates automatically use the Template class.


## Order of precedence

The order of precedence is

- initialization / constructor values
- values in a custom create method
- templates of parent classes
- own templates
- explicit setter methods

I.e., given the following code:

```groovy
@DSL
class Parent {
    String name = "default"
}

@DSL
class Child extends Parent {
}

def parentTemplate = Parent.Template.Create {
    name "parent-template" // overrides default value
}

def childTemplate = Child.Template.Create {
    name "child-template" // overrides parent template value
}

Child.Template.WithAll([parentTemplate, childTemplate]) {
  def c = Child.Create.One()

  assert c.name == "child-template"
 
  def d = Child.Create.With {
     name "explicit" // overrides template value
  }
 
  assert d.name == "explicit"

}

```

Note that templates for collections **replace** lower precedence, i.e., the most specific template wins. This behavior can be altered using [[Copy Strategies]]. 

```groovy
@DSL
class Parent {
    List<String> names = ["default"]
}

@DSL
class Child extends Parent {
}

def parentTemplate = Parent.Template.Create {
    names "parent" // replaces default value
}

def childTemplate = Child.Template.Create {
    names "child" // replaces parent template value
}

Child.Template.WithAll([parentTemplate, childTemplate]) {
  def c = Child.Create.With {
    name "explicit" // replaces template value
  }

  assert c.names == ["explicit"]
}
```

## `applyLater` and Templates

As stated in [[Model Phases]], Templates can contain `applyLater` closures. These actions are not executed on the Template;
they are detached as recipe state and cloned into every fresh recipient Builder. The closure must address that fresh
Builder through its delegate. Capturing any Builder, even through a serializable holder, is rejected. Other captured values
must be serializable so the Template recipe remains serializable with its companion state.
