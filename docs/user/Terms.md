# Core model terms

## Schema

A Schema is annotated Groovy source containing the class definitions for a model's DSL Object types, fields,
relationships, defaults, lifecycle behavior, and validation rules. It is analogous to an XSD for an XML document or a
JSON Schema for JSON data, but is also executable source: KlumAST generates the model-construction API from it. See
[[Basics]] and [[Getting Started]].

## Model

A Model is concrete configuration that instantiates a Schema's class definitions into a graph. In its usual form, Model
source is one or more Groovy scripts using the Schema's generated factories; supported imports and Templates can also
contribute configuration to construction. It plays the role that an XML document has for an XSD or JSON data has for a
JSON Schema: it supplies concrete values rather than defining types. When construction runs, a generated factory
configures its Builder graph, materializes the completed Model, and validates it before returning it. Model Writers
create Model configuration; clients consume the resulting completed, read-only Model API.

## Roles

KlumAST documentation distinguishes four roles. One person can assume several roles in the same project.

## Domain API Developer

Defines the stable, consumer-facing model contract. In a [[Layer3|Layer 3 model]], this API is designed before the Schema and is the
only model surface on which generic clients depend.

## Schema Developer

Defines DSL Object types, relationships, lifecycle behavior, validation, and external mappings. Without a separate Layer 3
API, the Schema Developer also owns the consumer-facing model contract.

## Client Developer

Builds integrations that consume completed DSL Objects through their public domain API, including importer invocation,
validation-result handling, and downstream serialization.

## Model Writer

Creates concrete configured models using Groovy DSL scripts, YAML/JSON inputs, [[Templates]], or combinations of those
authoring forms. A [[Jackson Integration|Jackson import operation]] always consumes one external input; source composition
is not a Model Writer promise of the Jackson adapter.

## Construction lifecycle

## Lifecycle phase

A lifecycle phase is a named step in model construction, such as `POST_TREE`, `INSTANTIATE`, `VALIDATE`, or `VERIFY`.
Builder phases run while configuration remains mutable; `INSTANTIATE` materializes the completed Model, and later phases
inspect it. See [[Model Phases]].

## Lifecycle methods and closures

Lifecycle methods and closure fields are Schema members annotated for a lifecycle phase. KlumAST invokes them at that
phase, giving pre-materialization callbacks a Builder and later callbacks a completed Model. See [[Model Phases]].

## Validation

Validation is the Schema-defined check of a completed Model. It can use field, method, or inner validation-class rules;
the generated root factory records the result and `VERIFY` rejects errors at the configured failure level. See
[[Validation]].

## Values
In this documentation, we differentiate between three kinds of values:

## DSL-Objects
DSL Objects are annotated with [[Basics|`@DSL`]]. These are (potentially complex) objects enhanced by the transformation. They
can either be *keyed* or *unkeyed*. Keyed means they have a designated field of type String (currently) decorated with the
 `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`. Generated factories
configure Builders and return completed DSL Objects; completed objects expose no generated mutation API.

## Builders
Builders are the mutable construction-time counterparts of DSL Objects. They own field initializers, DSL mutators,
relationship state, and lifecycle work through `POST_TREE`. The [[Model Phases#instantiate-40|`INSTANTIATE` phase]]
materializes the complete Builder graph before validation. [[Builder First Migration|Builder-first construction]] explains
the supported construction boundary. Builders are generated implementation types and are not a stable client-facing
naming contract.

## Templates

A Template is a reusable configuration recipe. Applying it rehydrates fresh Builders with its values and recorded recipe
actions; it is not an ordinary completed Model or a relationship value. See [[Templates]].

## Relationships

An owned relationship creates part of a Model's composition graph through the parent Builder. A `LINK` relationship refers
to an existing completed object instead of adopting it as owned composition. See [[Basics]] and
[[Builder First Migration]].

## Collections
Supported [[Basics#collections-of-dsl-objects|collection declarations]] are `List`, `Set`,
`SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`.
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
