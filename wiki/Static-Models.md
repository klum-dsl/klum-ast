The idea of KlumDSL is centered around static data models.

# What are static data models?

A static model is a collection of classes that fulfills a couple of constraints:

- Its instances are quasi readonly, meaning that they are not changed once they are created (which could be assured by
  making all fields immutable - KlumAST however takes a different approach)
- Its methods are side effect free, and mainly consist of getters, quasi getters and converters
- Static data models are usually rather tightly coupled, allowing to traverse in both directions
- additional functionality is provided using a Decorators and Adapters (which is the aim of another project of the KlumDSL
  suite: KlumWrap)
- To be useful, static data models should be strongly typed

# How does KlumAST implement the static data model paradigm?

KlumAST aspires to create SDMs by using the following techniques:

- setters and added methods, as well as lifecycle methods are moved to a special inner class that is only visible
  during apply and create methods. This means that all DSL features are readily available whenever a model instance 
  is created, but they do not pollute the interface of the model for the client (in observance of the Interface
  Segregation Principle)
- Other methods changing the state of the model (for instance, pseudo setters) must be marked using the an annotation 
  with the meta annotation `@WriteAccess`. These method will be moved to the RW class as well. Core annotations with 
  write access are `@Mutator` for manual write access methods and the lifecycle methods `@PostCreate`, `@PostApply`,
  `@PostTree` and `@AutoCreate`.

# Transient fields

Fields marked with `@Field(FieldType.TRANSIENT)` allow to add transient data to a model. This data
can be changed at will and will not participate in checks for equality.