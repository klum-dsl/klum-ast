Since 2.0 Creator methods are encapsulated in a separate class which is generated as a static inner
class to the model class (named `_Factory`). This class is instantiated with a single instance that is
placed on the model class as a static field named `Create` allowing the convenient creation syntax.

This factory is by default a subclass of:

- `KlumFactory` for abstrct classes
- `KlumFactory.Keyed` for keyed classes
- `KlumFactory.Unkeyed` for unkeyed classes

Note that the implementation of these factories only differ from their base classes in the setting of the generic and the adding of `@DelegatesTo` annotations to the closure parameters. This is necessary for code completion and static type checking (since Groovy does not allow to use a generic parameter of the class in the `@DelegatesTo` annotation).

# Custom Creator classes

Instead of the default base classes one can provide an own creator class extending the base class and thus adding new creator methods. This class can either be generic as well, in that case it must have a constructor with a single class parameter, or it can explicitly assign the type parameter of the base class. In that case it must have a constructor without parameters.

The creator class is either implicitly taken from a static inner class named `Factory` or explicitly set via the `factoryBase` member of the `@DSL` annotation.

```groovy
@DSL
class MyClass {
    String name
    String job
    
    static class Factory extends KlumFactory.Unkeyed<MyClass> {
        Factory() {
            super(MyClass)
        }
        
        public MyClass Baker(String name) {
            return Create.With(name: name, job: "baker")
        }
    }
}
```

This allows creating instances of `MyClass` via `MyClass.Create.Baker("Klaus")`.