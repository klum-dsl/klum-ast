# Breaking changes in 1.2

## compileOnly vs. runtime scope
Some features now rely on a classes being present in during runtime (up to 1.1, Klum-AST
was strictly compile time). I expect to move more features into runtime helpers to reduce
complexity of the code.

So you might want to check the scope of your dependency to klum-ast and change it from
`provided` to (default) `runtime` for Maven, and from `compileOnly` / `implementationOnly` to `compile` / `api`
for Gradle.

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
Class Bar {
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