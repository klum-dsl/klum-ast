Migration Guide
---------------

# to 2.0

## Validation now throws KlumValidationException

which wraps single `KlumVisitorExceptions` for each failed validation. Previously, an AssertionError was thrown, so calling code might need to be adjusted.

## multiple inner create calls on the same field (or key in a map field) now stack instead of replacing

Previously, multiple calls to the same inner create method would replace the previous value. 

```groovy
Foo.Create.With {
    bar {
        value = 1
        anotherValue = 2
    }
    bar {
        anotherValue = 3
    }
}
```

Previously, the second call of "bar" would result in a new object, i.e. the object created by the first call would be
replaced, resulting in:

```groovy
foo.bar.anotherValue == 3
foo.bar.value == null
```

Now those calls stack, so the result would be:

```groovy
foo.bar.anotherValue == 3
foo.bar.value == 1
```

This is especially useful for using deep templates to set the first object:

```groovy
def template = Foo.Create.Template {
    bar {
        value = 1
        anotherValue = 2
    }
}

Foo.withTemplate(template) {
  Foo.Create.With {
      bar {
          anotherValue = 3
      }
  }
}
```

If the existing object does not match the new object (either because a different key is provided or specific type is given that is
different from the existing type), an Exception is thrown. In that case, the behaviour can be explicitly overridden by using
either apply or a setter (to explicitly merge or replace):

```groovy
Foo.withTemplate(template) {
  Foo.Create.With {
      bar.apply { // explicitly force merge
          anotherValue = 3
      }
  }
}
```

or

```groovy
Foo.withTemplate(template) {
  Foo.Create.With {
      bar = Bar.Create.With { // explicitly force overwrite
          anotherValue = 3
      }
  }
}
```

## Default Values are actually set, not only returned

This makes objects used in copyFrom behave differently. Previously, the copyFrom methode explicitly ignored default values,
now they would be copied as well if already set. This can lead to different results if the copy source a) is not a template
object and b) was created outside the current phase run.

If the object was created outside the phase run, it will most likely be a template, so using the `Create.Template()` 
creator method (or the deprecated `createAsTemplate()` method) will lead to the same result as before.

## Owners are now set in the owner phase

Previously, they have been set before apply was called, so `apply` had already access to the owner, which could be
used in separate scripts. Any logic accessing the owner must be placed in a later phase (for example AutoLink or PostTree).

This also holds true for methods using default values populated by the owner.

`PostApply` methods accessing the owner must also be move to a later phase (or split).

## Deprecation: Validation annotation -> Validate

`@Validation.mode()` is replaced by phases and thus ignored. `Validation.Option.IGNORE_UNMARKED` is default anyway, so
the only useful variation of the annotation is `@Validation(option=VALIDATE_UNMARKED)`, which is replaced by `@Validate`
on class level.

## Deprecation: Factory methods -> Factory class

All static factory methods on DSL classes are deprecated in favor of a single `Create` class field which encapsulates all
relevant factory methods.

The following factory calls should be renamed:

| Old                            | New                             |
|--------------------------------|---------------------------------|
| `Foo.create()`                 | `Foo.Create.One()`              |
| `Foo.create(...)`              | `Foo.Create.With(...)`          |
| `Foo.createFrom(...)`          | `Foo.Create.From()`             |
| `Foo.createAsTemplate(...)`    | `Foo.Create.Template(...)`      |
| `Foo.createFromClasspath(...)` | `Foo.Create.FromClasspath(...)` |


NOTE that in addition to `.One()` for empty factory calls, `.With()` is also working, but since it makes for a strange
sounding call is deprecated and only present to allow a simple search and replace.

## Dependency changes
For 2.0, the single klum-ast dependency is replaced by two KlumAST is split into three distinct jars:

### klum-ast-annotations

Does not usually need to be addressed directly except in very special cases, since it is a dependency of both of
the other jars.

### klum-ast

Contains the actual AST transformations, i.e. the core of KlumAST. These need to be present during compile-time only
and need not be present on runtime (usually it should be safe if they are).

### klum-ast-runtime

Contains classes needed during runtime.

### compileOnly vs. runtime scope

Since klum-ast now relies on a runtime component, a schema now should have two separate dependencies, `klum-ast` as 
`compileOnly` (`provided` for Maven) and and `klum-ast-runtime` as `api`  (`runtime` for Maven), i.e.:

```groovy
dependencies {
  compileOnly 'com.blackbuild.klum.ast:klum-ast:<version>'
  implementation 'com.blackbuild.klum.ast:klum-ast-runtime:<version>'
}
```

or

```xml
<dependencies>
  <dependency>
    <groupId>com.blackbuild.klum.ast</groupId>
    <artifactId>klum-ast</artifactId>
    <version>...</version>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>com.blackbuild.klum.ast</groupId>
    <artifactId>klum-ast-runtime</artifactId>
    <version>...</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

# to 1.2

## DelegateOnly Strategy for closures

Closures are all `DelegateOnly` instead of the previous `DelegateFirst`. This means that you cannot access
methods of an outer object directly (which would not be very intuitive). If you need this functionality,
you need to access the outer object directly, using the `owner` property of `Closure`, an `@Owner` field
of the outer instance or a local variable pointing to the targeted instance.

Instead of:

```groovy
Foo.create {
    bar {
        methodInFoo()
    }
}
```

Write instead:

```groovy
Foo.create {
    bar {
        owner.methodInFoo() // owner is property of Closure
    }
}
```

Note that naming an `@Owner` field actually `owner` leads to the field being overshadowed
by the owner field of the closure. While this is usually not a problem, it might cause failures
when used inside a Collection-Factory:

```groovy
@DSL
class Bar {
  @Owner
  Foo owner
}

Foo.create {
    bars {
        bar {
            // owner points to the owner of the closure, i.e. the collection factory, so this will fail:
            owner.doSomething()
        }
    }
}
```

Calling the owner field (or any other field) actually `owner` leads to a compiler warning (which might
eventually be replace with a compiler error). Consider using a more domain specific field name like
`graph` or `parent` is advisable.


# Breaking changes since 0.98

- Models are now read-only. That means changes to fields can only be done:

  - inside an apply or create block
  - inside a lifecycle method
  - Note that it is still possible to some extend to change code from methods inside the class, however this
    is strongly discouraged. It is planned to include a way to mark mutation methods with an annotation. Such methods
    should be automatically moved to RW as well.
  If you access mutators from outside of these methods, you will get compiler errors upon updating to KlumAST 0.98+. In 
  that case, surround the offending code with an apply closure.
    
  Before:
  ```groovy
  model.value('bla')
  model.name = 'Hans'
  ```

  After:
  ```groovy
  model.apply {
    value('bla')
    name = 'Hans'
  }
  ```

# Breaking changes since 0.17

the following features were dropped:
- pre using existing `create` and `apply` methods is no longer supported, this has been replaced by a lifecycle mechanism 
  ([#38](https://github.com/klum-dsl/klum-core/issues/38)), see [[Basics#lifecycle-methods]]
- named alternatives for dsl collections
- shortcut named mappings
- under the hood: the inner class for dsl-collections is now optional (GDSL needs to be adapted)
- member names must now be unique across hierarchies (i.e. it is illegal to annotate two collections with the same
  members value)
- the implicit template feature is deprecated and will eventually be dropped (see [#34](https://github.com/klum-dsl/klum-core/issues/34)), 
  it basically uses global variables, which is of course bad design
  
  The suggested way to use templates would be to explicitly call copyFrom() as first step in a template using configuration
  or using the new named parameters (`Model.create(copyFrom: myTemplate) {..}`)
  
  Alternatively, the new `withTemplate(s)` mechanism can be used (see [Template Mechanism](wiki/Template-Mechanism))