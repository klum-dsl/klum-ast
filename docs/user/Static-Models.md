# Static Models

KlumAST is centered around static data models.

## What are static data models?

A static data model is a group of classes with these characteristics:

- Completed instances are structurally immutable: they expose no generated mutation path after creation.
- Methods are side-effect free and mainly consist of getters, quasi-getters, and converters.
- Classes are usually tightly coupled, so relationships can be traversed in both directions. See [Relationship graph](#relationship-graph).
- Put consumer-specific behavior in adapters or decorators rather than reopening a completed model for mutation.
- Models should be strongly typed.

## How does KlumAST implement the static data model paradigm?

KlumAST supports this style with the following techniques:

- Setters, generated DSL methods, and mutating lifecycle methods move to a generated Builder. Builders own mutable
  construction state through `POST_TREE`; `INSTANTIATE` then creates the completed DSL Object graph before validation.
  See [Model Phases](Model-Phases.md) for the lifecycle boundary. DSL features remain available during construction without polluting
  the completed-model interface.
- Other state-changing methods, such as pseudo-setters, must be marked with an annotation meta-annotated with
  `@WriteAccess`. Those methods also move to the Builder. Built-in write-access annotations include `@Mutator` for
  manual write-access methods and the lifecycle annotations `@PostCreate`, `@PostApply`, `@PostTree`, and `@AutoCreate`.
- All non-relationship, non-transient fields are final in the completed model. Internal graph materialization assigns
  relationship fields so cyclic links remain possible.
- Supported Collections are published as independent read-only snapshots; `EnumSet` is exposed through defensive copies.

## Relationship graph

Owned DSL Object relationships are a single-rooted composition tree. One or more `@Owner` fields may provide
framework-managed backlinks for upward navigation; they do not create additional ownership. `LINK` fields can add side
connections to existing completed DSL Objects without re-owning a target or changing the composition root. See
[Ownership and `@Owner`](Basics.md#ownership-and-owner) for the Builder-phase timing of those backlinks.

## Transient fields

Fields marked with `@Field(FieldType.TRANSIENT)` are the explicit exception: they add transient data to a model. This
data can be changed at will and does not participate in equality checks. `FieldType.TRANSIENT` is a KlumAST model
classification, not the Java/Groovy `transient` modifier: it is serialized by default.
