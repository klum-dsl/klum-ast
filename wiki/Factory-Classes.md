Since 2.0 Creator methods are encapsulated in a separate class which is generated as a static inner
class to the model class (named `_Factory`). This class is instantiated with a single instance that is
placed on the model class as a static field named `Create` allowing the convenient creation syntax.

This factory is by default a subclass of:

- `KlumFactory` for abstract classes
- `KlumFactory.Keyed` for keyed classes
- `KlumFactory.Unkeyed` for unkeyed classes

Note that the implementation of these factories only differ from their base classes in the setting of the generic and the adding of `@DelegatesTo` annotations to the closure parameters. This is necessary for code completion and static type checking (since Groovy does not allow to use a generic parameter of the class in the `@DelegatesTo` annotation).

# Custom Creator classes

Instead of the default base classes one can provide an own creator class extending the base class and thus adding new creator methods. This class can either be generic as well, in that case it must have a constructor with a single class parameter, or it can explicitly assign the type parameter of the base class. In that case it must have a constructor without parameters.

The creator class is either implicitly taken from a static inner class named `Factory` or explicitly set via the `factory` member of the `@DSL` annotation.

```groovy
@DSL
class MyClass {
    String name
    String job
    
    static class Factory extends KlumFactory.Unkeyed<MyClass> {
        Factory() {
            super(MyClass)
        }
        
        MyClass Baker(String name) {
            return Create.With(name: name, job: "baker")
        }
    }
}
```

This allows creating instances of `MyClass` via `MyClass.Create.Baker("Klaus")`.

# Creator methods and collection factories

When using the collection factory closure methods (as opposed to calling the element methods directly), all creator class methods are available as well. This is a more powerful alternative to the regular
[Alternatives Syntax](Alternatives-Syntax.md), especially when using abstract classes.

The 4.0 Builder-first runtime projects adaptable source-visible creator methods onto active-session Builder production.
Inside a relationship factory, single results, Collections, and Maps are attached to the owner without starting a nested
lifecycle. Recursive creator calls are rebound to their projected twins. The original creator method is unchanged, so a
direct `MyClass.Create.customMethod(...)` call still returns a completed root model.

The generated twins are synthetic implementation details. Public `Foo_DSL` contracts and IDE mirrors expose concrete
`Foo_DSL.Builder` results with composition-specific documentation instead. Opaque or precompiled model-returning creator
methods without an explicit `KlumBuilder<Foo>` contract are omitted from relationship APIs and report migration guidance
when a matching dynamic call is attempted. See
[ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md).


```groovy
@DSL class Foo {
    List<Bar> bars
}

@DSL abstract class Bar {
    String name
    
    static class Factory extends KlumFactory.Unkeyed<Bar> {
        protected Factory() {super(Bar)}

        Bar aBaz(String name, @DelegatesToRW(Baz) Closure body) {
            return Baz.Create.With(name: name, body)
        }
        
        Bar bazWithNickname(String name) {
            return Baz.Create.With(name: name, nickname: name)
        }
        
        Bar aBla(String name, @DelegatesToRW(Bla) Closure body) {
            return Bla.Create.With(name: name, body)
        }
    }
}

@DSL class Baz extends Bar {
    String nickname
}

@DSL class Bla extends Bar {
    String sickname
}
```

In this case, all the following calls are allowed:

```groovy
Foo.Create.With {
    bars { // collection factory
        aBaz("Baz") { // factory method
            nickname "Bazzy"
        }
        aBla("Bla") { // factory method
            sickname "Blabby"
        }
        bazWithNickname("Bazzy") // factory method
        From(MyScript) // default factory method
    }
}
```

Note that when in conflict, explicit factory methods take precedence over alternatives syntax methods.

This also works for Factory methods returning a collection or map of the model class:

```groovy
@DSL class Foo {
    List<Bar> bars
}

@DSL class Bar {
    String name
    String value

    static class Factory extends KlumFactory.Unkeyed<Bar> {
        protected Factory() { super(Bar) }

        List<Bar> fromProperties(File propsFile) {
            def props = new Properties()
            propsFile.withInputStream { props.load(it) }
            return props.values().collect { k, v -> Create.With(name: k, value: v) }
        }
    }
}

// usage

Foo.Create.With {
    bars {
        fromProperties(new File("my.properties"))
    }
}
```

The projected call returns the producer's original container. For a Map relationship, Map-producing methods retain their
original keys; Collection-producing methods derive keys from the keyed Builders as usual. For a Collection relationship,
Map values are attached in Map iteration order. Concrete container subtype, iteration order, comparator, and duplicate
behavior are preserved.
