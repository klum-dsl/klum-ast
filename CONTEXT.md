# KlumAST — Context & Domain Glossary

This document summarizes the project's domain vocabulary and a short architectural overview so agents and contributors share the same language.

## Project summary

KlumAST provides an easy way to create a complete DSL for model classes using AST transformations. Primary goals are terseness with readability and strong IDE support for model classes.

## Architectural overview (high-level)

This repository is modularised into a set of focused subprojects. The main modules are:

- `klum-ast-annotations` — annotations used by DSL model classes (`@DSL`, `@Key`, etc.)
- `klum-ast` — core compile-time helpers and entrypoints for DSL creation
- `klum-ast-runtime` — runtime library used by generated/config DSL objects
- `klum-ast-jackson` — Jackson integration for KlumAST runtime objects
- `klum-ast-gradle-plugin` — Gradle plugin(s) shipped by this project
- `klum-ast-bean-validation`, `klum-ast-bom`, `code-coverage-report` — auxiliary modules

Agents should treat the repo as a single-context project: one `CONTEXT.md` at the root and `docs/adr/` for ADRs.

## Tests

Test are done via the Spock Framework. Most important tests are in the klum-ast module, which tests the DSL transformation and runtime behavior. The other modules have their own tests for module-specific functionality.

## Domain glossary

These terms are sourced from the project wiki and consolidated here. Use these canonical terms when writing issues, ADRs or code comments.

- DSL-Objects

  DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They can either be keyed or unkeyed. "Keyed" means they have a designated field of type String decorated with the `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`.

- Builder

  A Builder is the generated, mutable construction-time counterpart of a DSL Object. It replaces the former `RW` class and is normally reached only through generated factory methods and DSL scripts; client code consumes the resulting DSL Object. Builders are not used to apply changes to an already created DSL Object.

  Mutating lifecycle callbacks, including `@PostTree`, operate on the Builder. `@Validate` callbacks operate on the completed DSL Object.

  Closure-based child creation returns a child Builder. Builder overloads may also accept an already completed DSL Object, in which case they return that completed object.

  `FieldType.BUILDER` is construction-only state: it exists on the Builder and is omitted from the completed DSL Object.

  Source field initializers execute when a Builder is created. Materialization copies their resulting values and never re-evaluates initializer code.

  Completed DSL Objects preserve cyclic relationships, including `LINK` relationships. Their object graph may therefore require internal-only assignment during materialization after the Builders have completed their lifecycle.

- Materialization

  Materialization is the graph-wide conversion of completed Builders into DSL Objects. It occurs in the `INSTANTIATE` phase (40), after `POST_TREE` (30) and before `VALIDATE` (50). Custom phases before materialization operate on Builders; phases after it operate on completed DSL Objects.

  Plugin phase actions use explicit traversal interfaces: `BuilderVisitingPhaseAction` before Materialization and `ModelVisitingPhaseAction` after it.

- Model companion

  A Model companion is the private state associated with a completed DSL Object, including its breadcrumb/model path and validation metadata. It is distinct from the Builder-side proxy and does not provide construction-time mutation. It records which Instance Validators have executed for the DSL Object, so each validator runs at most once for that completed object.

  Builders may hold provisional validation issues from `EARLY_VALIDATE` and lifecycle callbacks. Materialization transfers those issues to the Model companion.

  The Model companion is serializable with its DSL Object, preserving breadcrumbs and validation results. Builder-only construction state is not serialized.

- Construction API

  DSL Objects are constructed only by generated Builders and controlled deserialization logic. Generated constructors are internal implementation details and are not a client construction API.

  `KlumInstanceProxy` compatibility is limited to Builders and construction-time operations. Completed DSL Objects use their Model companion or public utilities; asking `KlumInstanceProxy` for a completed DSL Object is an error with migration guidance.

  Completed DSL Objects do not expose `apply`; configuration is Builder-only.

- Construction session

  A Construction session is the scope of one root Builder lifecycle. Every owned child Builder joins that session and the
  resulting composition graph is Materialized together; root Materialization is never nested inside another session.

- Builder-producing factory

  A Builder-producing factory creates an unsealed child Builder inside an active Construction session. `Create.AsBuilder`
  is the explicit composition protocol, while standalone root factories return completed DSL Objects.

- Deserialization

  Deserialization restores serializable DSL Object fields into Builders, then follows the normal lifecycle through Materialization and validation. The restored result may differ if a mutating lifecycle callback is non-idempotent; this behavior is provisional and will be revisited in [#428](https://github.com/klum-dsl/klum-ast/issues/428).

- Templates

  A Template is a client-facing DSL Object used as a reusable construction recipe. Applying a Template copies its composition into fresh Builders, which then participate in the recipient's lifecycle. This is the intentional exception to the rule that ordinary completed DSL Objects are not rehydrated.

- Collections

  Collections use the supported interfaces `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`. Map keys retain the schema's declared key type. Collection values can either be Simple Values or DSL-Objects. Collections of Collections are currently not supported.

  A collection field has two name properties: the collection name and the element name. The collection name defaults to the name of the field; the element name is the name of the field minus any trailing `s`. For example, a field named `roles` defaults to collection name `roles` and element name `role`. If the field name does not end with an `s`, the field name is reused as both collection and element name (e.g. `information -> information | information`). Collection name and element name can be customized via the `@Field` annotation.

  Collections must be strongly typed using generics.

  A Collection whose values are DSL Objects is a relationship field. Like a direct DSL Object field, it may be internally assigned while materializing a cyclic object graph, but it is read-only through the completed DSL Object's public API.

  Materialization publishes an independent, unmodifiable snapshot of each Collection, preserving order and comparators where applicable. `EnumSet` is stored defensively and exposed as a copy. Schema compilation rejects concrete or custom collection declarations that cannot be snapshot safely, with guidance to use a supported interface.

- Simple Values

  Everything else — simple values as well as more complex non-DSL objects — are considered simple values.

  A completed DSL Object guarantees structural immutability through its public API, not deep immutability of Simple Values. Simple Values are retained as supplied; they are not defensively copied.

- Relationship fields

  A relationship field is either a direct DSL Object field or a Collection whose values are DSL Objects. A relationship field may be internally assigned during Materialization to preserve cyclic relationships, regardless of whether it is annotated `LINK`. All other non-transient fields are final in the completed DSL Object.

  A Builder stores relationship values as Builders. An externally completed DSL Object is represented by an immediately sealed Builder that points one-way to that completed object.

- Composition and aggregation

  Composition means owned DSL Object subtrees built in one Builder lifecycle and Materialized together. Aggregation means a completed DSL Object is used only as an existing `LINK` target by another graph. Aggregation never re-owns, mutates, or rehydrates the completed DSL Object.

## Where to look next

- `wiki/Terms.md` — longer form notes and examples (source material for this glossary)
- `README.md` — project goals and examples
- `settings.gradle` — subproject/module list and boundaries
