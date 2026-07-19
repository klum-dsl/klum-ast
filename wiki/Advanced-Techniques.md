# Delegation hints for Builder closures

Generated methods accepting configuration closures receive the appropriate `@DelegatesTo` metadata automatically, so
modern IDEs can infer the available Builder methods.

Schema-defined Mutators sometimes accept and forward their own configuration closures. The generated Builder type does
not exist when that source method is parsed. Use `@DelegatesToBuilder` in that case. The annotation points the IDE and static type
checker at the generated Builder, not at a mutable completed DSL Object.

The optional annotation value selects the DSL Object whose Builder receives the closure:

```groovy
@DSL
class Container {
    List<Element> elements

    @Mutator
    def circle(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'circle', body)
    }

    @Mutator
    def square(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'square', body)
    }
// ...
}
```

Here both Mutators execute on the `Container` Builder and delegate `body` to a newly created `Element` Builder.
`@DelegatesToBuilder` does not add a completed-model `apply` or `configure` path; APIs that need to configure a DSL Object must
participate in factory/Builder construction.

# Behaviour models: Parameter Hints

Fields can also contain dynamic behavior in the form of closure or
interface parameters. That way, the actual behavior of the model
can be injected for the model, as opposed to the schema.

Consider the following example:

```groovy
class ValueProvider {
    
    String name
      
    String getDescription(Map<String, String> environment) {
        return "Value: $name: $it.name -> $it.value"
    }
}
```

If we want the description to continue another value, we would need a
subclass of `ValueProvider` which is not very convenient.

The better solution is to make the description itself configurable 
(Strategy pattern). This can be done with either an interface / an 
abstract class or a closure:

## interface

```groovy
interface DescriptionProvider {
    String getDescription(Map<String, String> environment)
}

@DSL class ValueProvider {
    
    String name
    
    @Validate // or a convenient default value  
    DescriptionProvider descriptionProvider
      
    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

That way, in the actual model, the description algorithm can be injected. 
Note that in Groovy any single abstract method interface (functional interface)
can be replaced with a closure:

```groovy
ValueProvider.Create.With {
    name "Blub"
    
    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

The closure in this case will automatically be resolved as having a single parameter
of type Map and a return value of String, which will also be checked by
the compiler.

`DescriptionProvider` could also be changed into an abstract class, which 
would be a nice place to include [[Converters#Factory Method converters]]. 

## Closure attributes

The description provider could also be a Closure itself:

```groovy
@DSL class ValueProvider {
    
    String name
    
    Closure<String> descriptionProvider
      
    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}

ValueProvider.Create.With {
    name "Blub"
    
    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

Note that the model definition is the same as with an `Action` class.
Unfortunately, with this approach, the IDE and the type checker have no
way of knowing that the Closure parameter is of type Map, so no code completion
is done here.

In normal Groovy, this could be solved by including the `@ClosureParams` annotation
on the Closure parameter of the method, but since these methods are generated, this is not
directly possible. However, KlumAST provides a workaround: the `@ParameterAnnotation`:

The ParameterAnnotation is a meta-annotation that can be used to annotate
other annotations (of ElementType METHOD and/or FIELD). If the target 
annotation is placed on a (virtual) field of the schema, all annotation members
of the target annotation are copied to the generated setter or single element adder. They will become parameter annotations of the single method parameter. See Javadoc for details.

For our closure field, there is a pre-implemented annotation, `@ParameterAnnotation.ClosureHint`,
that contains members for both relevant annotation types. Our example 
can be improved with:

```groovy
@DSL class ValueProvider {
    
    String name
 
    @ParameterAnnotation.ClosureHint(params=@ClosureParams(value=FromString, options="Map<String,Object>"))   
    Closure<String> descriptionProvider
      
    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

Which will provide code completion and type checking for the generated methods.
 
## When to choose: SAM interface or Closure

Usually, the SAM interface approach is nicer and cleaner, especially when used 
together with factory converters.

However, SAM interfaces can not really replicate the delegate mechanism 
of Groovy closures, which can further improve the cleanliness of the generated
DSL by completely omitting the closure parameter.

As a rule of thumb: When only `@ClosureParams` is needed, one should use
SAM interfaces, when delegate mechanisms can be used to enhance the dsl,
Closures with ParameterAnnotations should be used instead.
