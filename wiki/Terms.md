# Values
In this documentation, we differentiate between three kinds of values:

## DSL-Objects
DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They
can either be *keyed* or *unkeyed*. Keyed means they have a designated field of type String (currently) decorated with the
 `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`. Generated factories
configure Builders and return completed DSL Objects; completed objects expose no generated mutation API.

## Builders
Builders are the mutable construction-time counterparts of DSL Objects. They own field initializers, DSL mutators,
relationship state, and lifecycle work through `POST_TREE`. The `INSTANTIATE` phase materializes the complete Builder
graph before validation. Builders are generated implementation types and are not a stable client-facing naming contract.

## Collections
Supported declarations are `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`.
Other concrete and custom Collection declarations are rejected during schema compilation. Map keys retain their declared type;
collection and Map values can be Simple Values or DSL Objects. Collections of Collections are currently not supported.

Completed models expose independent read-only snapshots. Sorted snapshots preserve their comparator, and `EnumSet`
getters return defensive copies.

A collection field has two name properties: the collection name an the element name. The collection name defaults to
the name of the field, the element name is the name of the field minus any trailing s:

If the field name is `roles`, the default collection name is `roles` and the element name is `role`. 

If the field name does not end with an 's', the field name is reused as is (`information -> information | information`).

Only the element name can be customized via the `@Field` annotation (`members`, see below); the collection name always stays the field name.
 
*Collections must be strongly typed using generics!*
  
## Simple Values
These are everything else, i.e., simple values as well as more complex not-DSL objects.
