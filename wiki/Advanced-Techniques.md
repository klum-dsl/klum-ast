# Delegation Hints (Gradle extensions)
Methods accepting closures are automatically annotated with correct
`@DelegatesTo` annotations, allowing modern IDEs to infer the available
methods automatically.

There might be situtations, however, when it is needed to delegate
to an RW instance. For example, the `configure` method for a Gradle class
`Configurable` is basically the same as the Klum `apply` method.

In order to use a Klum model as a convenient syntax for extensions, one
needs simply to apply the interface to the model:

```groovy
@DSL
class AggregatorExtension implements Configurable<AggregatorExtension>{
    @Override
    AggregatorExtension configure(Closure closure) {
        return apply(closure)
    }
// ...
}
```

This works, but lacks any form of IDE support for the extension in the
build.gradle script.

Unfortunately, one can also not simply use `@DelegatesTo(AggregatorExtension._RW)`
Because the RW class is not yet existent when the class is parsed (also, the
naming of the RW class is (yet) an implementation detail).

By using the new `@DelegatesToRW` annotation, the appropriate IDE hints
are created:

```groovy
@DSL
class AggregatorExtension implements Configurable<AggregatorExtension>{
    @Override
    AggregatorExtension configure(@DelegatesToRW Closure closure) {
        return apply(closure)
    }
// ...
}
```

`@DelegatesToRW` can take an optional argument to point to a different RW class. That way it is
possible to define templates in the schema as opposed to the model:

```groovy
@DSL
class Container {
    List<Element> elements

    @Mutator
    def circle(@DelegatesToRW(Element) Closure body) {
        element(type: 'circle', body)
    }

    @Mutator
    def square(@DelegatesToRW(Element) Closure body) {
        element(type: 'square', body)
    }
// ...
}
```


