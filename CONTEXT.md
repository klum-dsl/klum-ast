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

- Klum project naming

  `Klum` is a tongue-in-cheek reference to the host of *Germany's Next Topmodel*, reflecting the project's focus on models and its motto, "Turn your models into supermodels." Names for projects in the Klum namespace should loosely draw from the show or modeling vocabulary; KlumAST itself is the exception.

  Keep this theme in project, artifact, and package names rather than API vocabulary, unless a name has a useful software double meaning, as with Klum-Wrap. Klum-Cast is a poor precedent: its name refers to entertainment casting but is easily mistaken for an object-oriented cast, making the project's purpose misleading.

  Major releases may pair their canonical semantic version with a release-facing name in the form `Season <major>: <subtitle>`. The subtitle should use modeling or show vocabulary while describing the release's character, so the theme remains recognizable without obscuring the version or its purpose.

- Domain API Developer

  A Domain API Developer defines the stable, consumer-facing model contract that Client Developers compile against. In a Layer 3 model, this contract precedes and constrains the Schema without exposing Schema-specific types to clients.

- Schema Developer

  A Schema Developer defines DSL Object types, relationships, lifecycle behavior, validation, and external mappings. In a model without a separate API layer, the Schema Developer also assumes the Domain API Developer role.

- Client Developer

  A Client Developer integrates with and consumes completed DSL Objects through their public domain API. Client code may invoke construction/import APIs and downstream serialization, but does not depend on Builder implementations or Schema-only types in a Layer 3 model.

- Model Writer

  A Model Writer creates concrete configured models using Groovy DSL scripts, structured data such as YAML or JSON, Templates, or combinations of those inputs.

- Domain-first modeling

  Domain-first modeling derives the Schema from the problem domain rather than from a particular backend or artifact
  format. The resulting completed model is the canonical domain abstraction; Client Developers adapt it to target systems,
  APIs, or documents.

- Target-contract modeling

  Target-contract modeling starts from an existing technical contract, such as Helm values, and uses a Klum Schema as a
  convenient, validated authoring language for that contract. The target contract remains authoritative and generated
  artifacts must conform to it, although the Schema may add higher-level defaults and need not mirror the target one-to-one.

  Domain-first and target-contract modeling describe what drives Schema design. Both are independent of decorating model
  objects with Klum-Wrap.

- DSL-Objects

  DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They can either be keyed or unkeyed. "Keyed" means they have a designated field of type String decorated with the `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`.

- Builder

  A Builder is the generated, mutable construction-time counterpart of a DSL Object. It is normally reached only through generated factory methods and DSL scripts; client code consumes the resulting DSL Object. Builders are not used to apply changes to an already created DSL Object.

  Mutating lifecycle callbacks, including `@PostTree`, operate on the Builder. `@Validate` callbacks operate on the completed DSL Object.

  Closure-based child creation returns a child Builder. An already completed DSL Object is accepted only as an aggregation `LINK` target and appears during construction as a sealed Builder wrapper; it never becomes owned composition.

  `FieldType.BUILDER` is construction-only state: it exists on the Builder and is omitted from the completed DSL Object.

  Source field initializers execute when a Builder is created. Materialization copies their resulting values and never re-evaluates initializer code.

  Completed DSL Objects preserve cyclic relationships, including `LINK` relationships. Their object graph may therefore require internal-only assignment during materialization after the Builders have completed their lifecycle.

- Generated DSL support namespace

  `Foo_DSL` is the top-level generated namespace for the public build-time interfaces of DSL Object `Foo`. It contains
  `Factory`, `Builder`, and the Builder's Collection/Cluster factory interfaces. Clients and extensions may name these
  interfaces in signatures but must not construct, implement, or subclass them. Generated implementations remain hidden.
  AnnoDocimal source mirrors exist only for IDE completion; Gradle must not compile, package, or expose them as downstream
  build inputs.

- RW

  RW is the deprecated name for the generated mutable construction counterpart now called a Builder. Reserve RW for migration and compatibility discussions; `KlumRwObject` and `$_RW` are removed in 4.0, while `@DelegatesToRW` remains the deprecated source alias for `@DelegatesToBuilder`.

- Materialization

  Materialization is the graph-wide conversion of completed Builders into DSL Objects. It occurs in the `INSTANTIATE` phase (40), after `POST_TREE` (30) and before `VALIDATE` (50). Custom phases before materialization operate on Builders; phases after it operate on completed DSL Objects.

  Plugin phase actions use explicit traversal interfaces: `BuilderVisitingPhaseAction` before Materialization and `ModelVisitingPhaseAction` after it.

- Object companion

  An Object companion is private framework state associated with a DSL Object. Ordinary completed objects use an internal
  Model companion for construction paths, validation, and validator memoization. Marked Templates use a distinct Template companion
  for identity and immutable recipe state. The common internal abstraction exposes only object identity and paths.

  Builders may hold provisional validation issues from `EARLY_VALIDATE` and lifecycle callbacks. Materialization transfers those issues to the Model companion.

  The Model companion is serializable with its DSL Object, preserving construction paths and validation results. Raw technical
  metadata is internal rather than a client extension seam. Builder-only construction state is not serialized.

- Object support

  `KlumObjectSupport<T>.of(object)` is the supported Java-first facade for any completed DSL Object or subtree. It exposes
  the object, construction path, structural model path, and grouped composition structure traversal without exposing
  the internal companion. Its Structure helper is composition-only and identity-cycle-safe; it skips Owner and LINK edges.
  Completed-model phase traversal uses this helper directly, while Builder phases use a separate internal Builder structure
  helper. The shared internal composition walker owns only traversal mechanics and is not a client extension seam.

  Its Validation helper (`getValidation`) reads stored target/subtree results and verifies them without rerunning validators
  or mutating lifecycle issue state. Completed-model validation readers do not access the companion directly.

- Construction path

  A Construction path is the immutable, `$`-prefixed DSL invocation path retained for one object. It identifies the nested
  Builder/factory operations that constructed that object, including Template or automatic-creation steps. It is not a
  structural model path, source identity, or event history. `BreadcrumbCollector` is internal implementation vocabulary for
  assembling this value.

- Structural model path

  A Structural model path is the object's location in its owned composition graph, rooted at `<root>` and expressed with
  fields, collection indices, and map keys. Owner and `LINK` relationships do not define this path.

- Traversal path

  A Traversal path is the contextual path emitted by one composition traversal. It may be rooted or prefixed by that
  traversal's caller and is not separately retained as object identity.

- Import source

  An Import source is the external input identity contributed by a managed importer. It may augment a construction-path
  diagnostic but is not itself a construction path.

- Validation location

  A Validation location identifies the object and optional member to which a validation issue applies. It may render with a
  construction path, but it is a distinct diagnostic concept.

- Provenance and lineage

  Provenance or lineage means a richer origin, applied-recipe, or lifecycle-event history. KlumAST does not currently retain
  such a record; do not use either term for the single construction-path String.

- Construction API

  DSL Objects are constructed only by generated Builders and controlled import logic. Generated constructors are internal implementation details and are not a client construction API.

  `KlumInstanceProxy` compatibility is limited to Builders and construction-time operations. Completed DSL Objects use
  `KlumObjectSupport`; asking `KlumInstanceProxy` for a completed DSL Object is an error with migration guidance.

  Completed DSL Objects do not expose `apply`; configuration is Builder-only.

- Construction session

  A Construction session is the scope of one root Builder lifecycle. Every owned child Builder joins that session and the
  resulting composition graph is Materialized together; root Materialization is never nested inside another session.

- Root factory

  A Root factory owns a complete Construction session and returns a completed DSL Object. It is distinct from a Builder-producing factory used to add owned composition inside an active session.

- Builder-producing factory

  A Builder-producing factory creates an unsealed child Builder inside an active Construction session. `Create.AsBuilder`
  is the explicit composition protocol, while standalone root factories return completed DSL Objects.

- Jackson import

  A Jackson import maps externally owned structured data onto public Builder configuration. A root import completes one
  normal lifecycle, while an in-session import contributes Builders to an existing Construction session; neither operation
  promises that KlumAST can consume its own exported representation.

- Jackson export

  A Jackson export is ordinary Jackson serialization of a completed DSL Object as a consumer-facing POJO projection. The
  Schema and mapper own the external shape; KlumAST adds no wire-format metadata and defines no import/export round trip.

- Configuration layer

  A Configuration layer is a typed, opaque source of Builder configuration consumed by a future root composition
  coordinator. Layers apply in caller order within one Construction session; the coordinator owns cross-layer policy and
  diagnostics. A Jackson input can contribute one layer, but a Jackson importer operation does not itself compose layers.

- Template

  A Template is an explicitly designated client-facing DSL Object used as a reusable construction recipe; an ordinary
  completed model is never inferred to be a Template from context. Applying a Template copies its composition and replays
  its immutable recipe state into fresh Builders in the recipient's lifecycle. Templates cannot be relationship values,
  including `LINK` targets.

- Phase registration

  Plugin phases use state-typed Builder or Model registrations with stable IDs, numeric phases, and optional equal-phase
  before/after dependencies. Numeric order and the `INSTANTIATE` boundary remain authoritative. Schema-authored custom
  phase annotations are not currently part of the contract.

- Layer 3 model

  A Layer 3 model is a modeling pattern that separates a generic consumer-facing API layer, a domain-specific Schema
  layer, and configured Model instances. The Domain API Developer defines the API before the Schema Developer realizes it,
  Client Developers depend only on that API, and Model Writers create the configured instances. Cluster projection is
  specialized support for this pattern; lifecycle, linking, ownership, defaults, and traversal are general KlumAST
  capabilities rather than defining Layer 3 features.

- Direct-schema modeling

  Direct-schema modeling uses the Schema's DSL Object types as the consumer-facing API rather than defining a separate
  Domain API layer. The Schema Developer also assumes the Domain API Developer role, and Client Developers depend directly
  on the Schema types.

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
