# Values
In this documentation, we differentiate between three kinds of values:

## DSL-Objects
DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They
can either be *keyed* or *unkeyed*. Keyed means they have a designated field of type String (currently) decorated with the
 `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`.

## Collections
Collections are currently either any subtype of `Collection` (e.g. `List`, `Set`, `SortedSet`, `Queue`) or maps.
Maps are limited to `Map` / `SortedMap`. Map keys are always Strings, collection values and Map values can either be
simple types or DSL-Objects. Collections of Collections are currently not supported.

A collection field has two name properties: the collection name an the element name. The collection name defaults to
the name of the field, the element name is the name of the field minus any trailing s:

If the field name is `roles`, the default collection name is `roles` and the element name is `role`. 

If the field name does not end with an 's', the field name is reused as is (`information -> information | information`).

Only the element name can be customized via the `@Field` annotation (`members`, see below); the collection name always stays the field name.
 
*Collections must be strongly typed using generics!*
  
## Simple Values
These are everything else, i.e., simple values as well as more complex not-DSL objects.

