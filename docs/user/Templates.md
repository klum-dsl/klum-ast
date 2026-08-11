# Templates

Templates are completed DSL Objects marked as reusable construction recipes. Applying a Template copies its non-null and
non-empty values into a fresh Builder graph. Nested DSL Objects are rehydrated recursively, Collections are copied, and
Simple Values are retained. A Template is never adopted directly as owned composition.

Template identity is persistent and graph-wide. Every owned node created by `Create.Template.With` is marked as a Template and
keeps its breadcrumb and model path; a completed object supplied to a `FieldType.LINK` field remains the same ordinary
model. Templates cannot be assigned directly to any relationship, including `LINK`: apply them through `Template.With`,
`copyFrom`, or another Template/copy API so KlumAST can build a fresh owned graph.

Deferred `applyLater` actions live only in immutable Template recipe state, never in ordinary completed models. Recipe
closures are detached and their captured graph is checked when the Template materializes. Captured Builders and
non-serializable values are rejected. Template identity and recipe state survive Java serialization; Builders,
Construction sessions, active Template scopes, and mutable recipe collections are not serialized.

## Creating Templates

Ignorable fields of the template (key, owner, transient, or marked as `FieldType.Ignore`) are never copied over. Root
creation lives below `Create`: use `Create.Template.With` for a map and/or configuration closure, and
`Create.Template.From` for a DelegatingScript file or URL. The result behaves like a normal factory result with these
differences:
 
 - the result is always unkeyed (setting the key to null in case of a keyed class)
 - Lifecycle methods (`@PostApply`, `@PostCreate`) are not called
 - The template does not participate in KlumPhases (especially: no validation is performed)
 - provides a non-abstract implementation for abstract classes, implementing all possible methods empty or returning null
 
(See: `TemplatesDocumentaryTest#'creates an unkeyed reusable template without lifecycle callbacks'`.)

```groovy
@DSL
class ServiceConfiguration {
    @Key String name
    String region
}

def template = ServiceConfiguration.Create.Template.With {
    region 'eu-central'
}

assert template.name == null
```

Templates are also correctly applied when using inheritance: a template defined for a parent class is applied when
creating child-class instances, and child template values can override parent templates. The focused regression coverage
is in `BoundTemplatesSpec.groovy`.

Template-specific methods are pooled in the static `Template` field of each DSL class. Its public generated contract is
`Foo_DSL.TemplateScope`. `Template` is the scoped-application handler: `Template.With` and `Template.WithAll` apply one or
more recipes while the supplied body creates ordinary models. The deprecated 4.x compatibility aliases `Template.Create`
and `Template.CreateFrom` still forward to the canonical creation operations, but new code must use
`Create.Template.With` and `Create.Template.From`.

For final 4.0 RC users that explicitly declared the generated handler type, rename `Foo_DSL.Template` to
`Foo_DSL.TemplateScope` and recompile. There is intentionally no compatibility alias; `Foo_DSL.Factory.Template` remains
the separate type of the `Foo.Create.Template` root-creation field.

(See: `TemplatesDocumentaryTest#'creates a template from a DelegatingScript file'`.)

```groovy
def template = ServiceConfiguration.Create.Template.From(new File('service-template.groovy'))
```

## Nested Builder composition

Defining a Template is its own nested composition scope. A source-visible converter may therefore use a normal
`Child.Create.With(...)` implementation while the generated Builder converter twin creates a Template-owned child.
Those children carry Template identity and run no lifecycle or validation until the Template is rehydrated into an
ordinary root creation. This scope takes precedence when a Template is defined inside an active Construction session;
after it exits, ordinary `Create.AsBuilder` calls continue in that original session. `Template.With` and
`Template.WithAll` only apply recipes and do not open Template-definition composition.

(See: `TemplatesDocumentaryTest#'defines a Template through a fluent child converter'`.)

```groovy
@DSL
class Parent {
    Child child
}

@DSL
class Child {
    String value

    static Child fromString(String value) {
        Child.Create.With(value: value)
    }
}

def template = Parent.Create.Template.With {
    child 'template-child'
}

Parent.Template.With(template) {
    def registry = Parent.Create.One()
    assert registry.child.value == 'template-child'
}
```

There are currently four options to apply templates; all examples use the following class and template:

```groovy
@DSL
class Config {
    String url
    List<String> roles
}

def template = Config.Create.Template.With {
    url "http://x.y"
    roles "developer", "guest"
}
```
 
## `copyFrom()`

Using `copyFrom`, one can explicitly apply a template to a single Object to be created:

(See: `TemplatesDocumentaryTest#'copies a template into one completed service configuration'`.)

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
[Copy Strategies#copy-source-protocol](Copy-Strategies.md#copy-source-protocol) for the complete copy-source rules.

## Template.With()
 
`Template.With()` provides scoped templates. It takes a template and a closure, and the template is automatically 
applied to all instance creations within that closure.
 
Usage:

(See: `TemplatesDocumentaryTest#'applies one scoped template to multiple service configurations'`.)

```groovy
def template = Config.Create.Template.With {
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
Config.Template.With(Config.Create.Template.With(url: "http://x.y")) {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

could be written as:

(See: `TemplatesDocumentaryTest#'applies named values through an anonymous scoped template'`.)

```groovy
Config.Template.With(url: "http://x.y") {
    c = Config.Create.With {
        roles "productowner"
    }
}
```

## Templates for Collection Factories

When using the optional collection factory (see [Basics#collections-of-dsl-objects](Basics.md#collections-of-dsl-objects)), a template can directly be
specified, either explicitly or as an anonymous template. This template is automatically valid for all elements
that are created inside this collection factory:

(See: `TemplatesDocumentaryTest#'applies one collection-factory template to every created server'`.)

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

(See: `TemplatesDocumentaryTest#'applies templates for multiple configuration types in one scope'`.)

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

(See: `TemplatesDocumentaryTest#'creates a template implementation for an abstract configuration type'`.)

```groovy
@DSL
abstract class RetryPolicy {
    String name
    abstract int retries()
}

def template = RetryPolicy.Create.Template.With {
    name 'resilient'
}
```


## Order of precedence

The order of precedence is

- initialization / constructor values
- values in a custom create method
- templates of parent classes
- own templates
- explicit setter methods

The following example shows a child template overriding parent defaults, with an explicit configuration value taking
highest precedence.

(See: `TemplatesDocumentaryTest#'lets child templates and explicit configuration override parent defaults'`.)

```groovy
@DSL
class Parent {
    String name = "default"
}

@DSL
class Child extends Parent {
}

def parentTemplate = Parent.Create.Template.With {
    name "parent-template" // overrides default value
}

def childTemplate = Child.Create.Template.With {
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

Collection values from Templates and DSL adder methods are added in declaration order. To replace inherited collection
values, assign the collection directly; `Copy Strategies` can also alter the behavior.

(See: `TemplatesDocumentaryTest#'lets explicit collection assignment replace inherited template values'`.)

```groovy
@DSL
class Parent {
    List<String> names = ["default"]
}

@DSL
class Child extends Parent {
}

def parentTemplate = Parent.Create.Template.With {
    names "parent" // replaces default value
}

def childTemplate = Child.Create.Template.With {
    names = ["child"] // replaces parent template values
}

Child.Template.WithAll([parentTemplate, childTemplate]) {
  def c = Child.Create.With {
    names = ["explicit"] // replaces template values
  }

  assert c.names == ["explicit"]
}
```

## `applyLater` and Templates

As stated in [Model Phases](Model-Phases.md), Templates can contain `applyLater` closures. These actions are not executed on the Template;
they are detached as recipe state and cloned into every fresh recipient Builder. The closure must address that fresh
Builder through its delegate. Capturing any Builder, even through a serializable holder, is rejected. Other captured values
must be serializable so the Template recipe remains serializable with its companion state.

(See: `TemplatesDocumentaryTest#'replays a template applyLater recipe for each completed configuration'`.)

```groovy
def template = ServiceConfiguration.Create.Template.With {
    applyLater {
        identifier name.toUpperCase()
    }
}

def catalog, billing
ServiceConfiguration.Template.With(template) {
    catalog = ServiceConfiguration.Create.With { name 'catalog' }
    billing = ServiceConfiguration.Create.With { name 'billing' }
}

assert catalog.identifier == 'CATALOG'
assert billing.identifier == 'BILLING'
```
