The idea of KlumDSL is centered around static data models.

# What are static data models?

A static model is a collection of classes that fulfills a couple of constraints:

- Its completed instances are structurally immutable: they expose no generated mutation path after creation
- Its methods are side effect free, and mainly consist of getters, quasi getters and converters
- Static data models are usually rather tightly coupled, allowing to traverse in both directions
- additional functionality is provided using Decorators and Adapters (which is the aim of another KlumDSL project: KlumWrap)
- To be useful, static data models should be strongly typed

# How does KlumAST implement the static data model paradigm?

KlumAST aspires to create SDMs by using the following techniques:

- setters, generated DSL methods, and mutating lifecycle methods are moved to a generated Builder. Builders own all
  mutable construction state through `POST_TREE`; `INSTANTIATE` then creates the completed DSL Object graph before
  validation. DSL features are available during construction without polluting the completed model interface
- Other methods changing the state of the model (for instance, pseudo setters) must be marked using an annotation 
  with the meta annotation `@WriteAccess`. These methods are moved to the Builder as well. Core annotations with
  write access are `@Mutator` for manual write access methods and the lifecycle methods `@PostCreate`, `@PostApply`,
  `@PostTree` and `@AutoCreate`.
- all non-relationship, non-transient fields are final in the completed model; relationship fields are assigned only by
  internal graph materialization so cyclic links remain possible
- supported Collections are published as independent read-only snapshots; `EnumSet` is exposed through defensive copies

# Transient fields

Fields marked with `@Field(FieldType.TRANSIENT)` allow to add transient data to a model. This data
can be changed at will and will not participate in checks for equality.
