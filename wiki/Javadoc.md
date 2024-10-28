# JavaDoc for models

By using the [AnnoDocimal](https://github.com/blackbuild/anno-docimal) library, natural javadocs for the generated models can be created. For the created RW instances and factory methods these are taken from the base classes of klum-ast-runtime, partially adapted and extended.

For self created methods, the javadoc is taken from the method itself.

## Templating

The javadoc of KlumInstanceProxy in particular uses templates to reduce boilerplate code, which can be use to further customize the generated javadoc.

Example:

The javadoc of the createSingleChild method in KlumInstanceProxy is used for all generated methods to add single new DSL object to a collection:

```java
    /**
     * Creates a new '{{singleElementName}}' {{param:type?with the given type}} and adds it to the '{{fieldName}}' collection.
     * The newly created element will be configured by the optional parameters values and closure.
     * @param namedParams the optional parameters
     * @param fieldOrMethodName the name of the collection to add the new element to
     * @param type the type of the new element
     * @param key the key to use for the new element
     * @param body the closure to configure the new element
     * @param <T> the type of the newly created element
     * @return the newly created element
     */
public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, String key, Closure<T> body)
```

For a single field , more than on method is created, i.e.:

```groovy
import java.lang.reflect.Field

@DSL
class MyModel {
    @Field(members = "child")
    List<Child> children
}
```

would create the following adder methods for the child field (provided child is keyless):

- child(Map<String, Object> namedParams, Closure<Child> body)
- child(Map<String, Object> namedParams)
- child(Closure<Child> body)
- child()
- child(Map<String, Object> namedParams, Class<T extends Child) type, Closure<Child> body)

Using annodocimal, all param tags of the unused parameters of the respective method will be dropped. The main text of the method java doc will be used as a template, where the `{{..}}` placeholder will be replace by the actual values:

- `{{singleElementName}}` will be replaced by the name of a single element, which defaults to the field name with a trailing 's' removed (which would be wrong in this chase). Since we have an explicit `Field.members` set, this is used instead.
- `{{param:type?with the given type}}` is an optional text block, it will only be set, if the parameter type is present in the actual method.
- `{{fieldName}}` will be replaced by the name of the field.

For more details, see AnnoDocimal documentation.