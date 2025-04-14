Copy Strategies handle how DSL objects are copied over to existing objects (using the `copyFrom` method). This is mainly used when creating objects from templates, but can also be used to copy objects in other scenarios (like merging multiple script files in a 'helm' line way).

When copying an object to another, there are three distinct types of strategies: single object, collections and maps. The strategy can
be configured on a class for all matching fields at once, or on a per-field basis.

Fields are marked with one of the following annotations, depending on the type:

- `@Overwrite.Single` for single object fields
- `@Overwrite.Collection` for collection fields
- `@Overwrite.Map` for map fields

Also, fields, the class or the package can be annotated with `@Overwrite` to apply the strategy to all fields in the class or package.

## Single Object

Single object strategies handle how a single object field is copied over from a donor object. The behaviour is slightly different for dsl and pojo objects. Pojo objects are always linked into the target object, i.e. target and donor share the same object instance (which is
hopefully immutable), DSL objects are copied over, i.e. a new object is created and the fields are copied over (using that object type's overwrite strategies).

The strategy is determined by checking in the following order and using the first match:

- an `@Overwrite.Single` annotation on the field itself
- a nested annotation on the field (i.e. an annotation that itself is annotated with `@Overwrite.Single`)
- an `@Overwrite` annotation (or nested annotation) on the class with the member `single` set to something other than `INHERIT`
- an `@Overwrite` annotation (or nested annotation) on the package of the class 
- in turn an `@Overwrite` annotation (or nested annotation) on the class or package of the class' superclass (and up through the hierarchy)

The strategy can be one of the following:

### INHERIT

Not a strategy in itself, should no be used directly.

### REPLACE

If the donor's field is not null, the field of the target is completely replaced by the donor's field (or a copy of the 
donor's field in case of a DSL object).

### ALWAYS_REPLACE

The target's field is replaced with the donor's field, even if the donor's field is null. This can be used to clear a field with a default value using a template. This strategy should be used only sparingly, and almost always only on specific fields as opposed to classes or packages.

### SET_IF_NULL

The target field is on set if it was null before. This can be used to set default values in the target object if they are not already set. This can be especially useful outside of templates, for example in the Default Phase.

### MERGE

Merge is the default strategy. For non-DSL fields, this behaves exactly as `REPLACE`. For DSL fields, the donor's value is copied into the targets value (or a newly created object if the target's value is null). The nested fields are copied over using the overwrite strategies of the target's field's type.

## Collections

Collection strategies handle how a collection field is copied over from a donor object. The collection itself is never linked into the target (i.e. the target and donor always have separate collections), if a collection in the target exists, it is reused (and possibly cleared depending on the strategy). This also holds true for nested collections (List of Lists).

If the collection consists of DSL objects, those objects are copied (i.e. new objects similar to those in the donor are created), simple objects are simply linked. 

The strategy is determined in exactly the same way as for single object fields, but using the `@Overwrite.Collection` annotation instead.

The strategy can be one of the following:

### INHERIT

Not a strategy in itself, should no be used directly.

### ADD

The donor's collection is added to the target's collection. The order is determined by the collection type (e.g. List preserves order, Set does not).

### REPLACE

The target's collection is replaced by the donor's collection's elements, but only if the donor's collection is not empty.

### ALWAYS_REPLACE

The target's collection is replaced by the donor's collection's elements, even if the donor's collection is empty. This can be used to clear a collection with a default value using a template. This strategy should be used only sparingly, and almost always only on specific fields as opposed to classes or packages.

### MERGE -> not implemented yet

Since we have no way of determining matching elements, this is not yet implemented.

Note that if the collection field of the donor is `null` instead of an empty collection, the target's collection is always left untouched. Also note that in order to set a collection to null, the setter syntax must be used instead of the usual methods.

```groovy
MyObject.Create.With {
    elements = null  // "element null" would add null to the collection 
}
```

## Maps

Map strategies handle how a map field is copied over from a donor object. The map itself is never linked into the target (i.e. the target and donor always have separate maps), if a map in the target exists, it is reused (and possibly cleared depending on the strategy). This also holds true for nested maps or collections (Map of Maps or Maps of Lists).

As with collections, if the values are DSL objects, those objects are copied (i.e. new objects similar to those in the donor are created), simple objects are simply linked.

The strategies are as follows:

### INHERIT

Not a strategy in itself, should no be used directly.

### FULL_REPLACE

The target's map is replaced by the donor's map (if not empty). This is the default strategy.

### ALWAYS_REPLACE

The target's map is replaced by the donor's map, even if the donor's map is empty. This can be used to clear a map with a default value using a template. This strategy should be used only sparingly, and almost always only on specific fields as opposed to classes or packages.

### SET_IF_EMPTY

The target's map is replaced by the donor's map, but only if the target's map is empty.

### MERGE_KEYS

The donor's map is added to the target's, replacing existing values with matching keys. 

### MERGE_VALUES

The donor's map is merged with the target's. New keys are added, but the values of existing keys are merged with the target's values. The merge strategy is determined by the target's field's type's overwrite strategy.

For non-DSL values, this behaves exactly as 'MERGE_KEYS', i.e. the values are replaced.

### ADD_MISSING

All entries in the donor's map whose keys are not present in the target's map are added to the target's map.

# Nested Annotations

Nested Annotations can be used to give meaningful names to a couple of strategies. For example, the `HelmOverwrite` annotation is a nested annotation that sets the strategy to `MERGE` for single object fields, `REPLACE` for collections and `MERGE_VALUES` for maps, resembling the way helm merges value files.

It is defined as follows:

```java
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Overwrite(
        singles = @Overwrite.Single(OverwriteStrategy.Single.MERGE),
        collections = @Overwrite.Collection(OverwriteStrategy.Collection.ALWAYS_REPLACE),
        maps = @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES)
)
public @interface HelmOverwrite {
}
```

Note that only one level of nesting is supported, i.e. you cannot nest annotations inside nested annotations.

# Missing field handling

The `@Overwrite` annotation as well as nested annotations can contain an additional member named `missing` of type
`OverwriteStrategy.Missing`, which controls the handling of fields in the donor that are not present in the target
object. This can either be `FAIL` (the default) or `IGNORE`.

Note that since this is checked on the target object, it does nothing when being put a field.