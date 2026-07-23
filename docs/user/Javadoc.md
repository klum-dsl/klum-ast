# Javadoc for models

KlumAST uses the [AnnoDocimal](https://github.com/blackbuild/anno-docimal) library to generate natural Javadocs for
factories and generated Builders. Their base documentation comes from the corresponding classes and methods in
`klum-ast-runtime`, especially `KlumBuilder`, and is adapted to each generated method.

For schema-defined methods, the Javadoc is taken from the method itself.

## Templating

The Javadocs of Builder base methods use templates to reduce boilerplate and customize the generated overloads.

Example:

For example, `KlumBuilder.createSingleChild` supplies documentation for generated methods that create or configure a
single child Builder:

```java
    /**
     * Creates a new '{{fieldName}}' Builder {{param:type?with the given type}} or configures the existing Builder.
     * The resulting Builder is configured by the optional values and closure.
     * @param namedParams the optional parameters
     * @param fieldOrMethodName the name of the field to set or Builder method to call
     * @param type the model type of the new Builder
     * @param key the key to use for the new Builder
     * @param body the closure used to configure the Builder
     * @param <T> the generated Builder type
     * @return the newly created or existing Builder
     */
public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type,
                               boolean explicitType, String key, Closure<T> body)
```

For a single field, more than one method is generated. For example:

```groovy
import java.lang.reflect.Field

@DSL
class MyModel {
    @Field(members = "child")
    List<Child> children
}
```

would create the following adder methods for the child field (provided child is keyless):

- `child(Map<String, Object> namedParams, Closure body)`
- `child(Map<String, Object> namedParams)`
- `child(Closure body)`
- `child()`
- `child(Map<String, Object> namedParams, Class<? extends Child> type, Closure body)`

The closure delegate and return type are the generated child Builder. Its public spelling is the generated
`Foo_DSL.Builder` interface; [issue #394](https://github.com/klum-dsl/klum-ast/issues/394) establishes this contract.

AnnoDocimal drops parameter tags for arguments omitted by a generated overload. The main text is a template in which
`{{...}}` placeholders are replaced with actual values:

- `{{fieldName}}` is replaced by the field name.
- `{{param:type?with the given type}}` is an optional text block, it will only be set, if the parameter type is present in the actual method.

Collection and map element methods additionally use `{{singleElementName}}`, which defaults to the field name with a
trailing `s` removed. An explicit `Field.members` value overrides that default.

For more details, see the AnnoDocimal documentation.
