# KlumAST alongside Sundrio and related approaches

Overlapping tools are not automatically substitutes. Generating an object builder, defining a fluent Java API,
constructing a domain Model, and producing configuration documents can be different tasks in the same application.
This page compares those tasks, rather than ranking products or promising equivalent features.

External sources were retrieved on **2026-11-23**. The comparison is bounded to Sundrio's builder and DSL generators,
Lombok's builder annotations, and Pkl's configuration language. Follow the linked product contracts when adopting a
particular version. KlumAST terminology follows the [Terms and role guide](Terms.md).

## Purpose, authoring, and construction

| Approach | Purpose and authoring model | Construction contract |
| --- | --- | --- |
| KlumAST | Annotated Groovy Schemas generate a typed, nested Groovy Model DSL ([Basics](Basics.md)). | One root Construction session configures Builders, runs graph phases, then materializes and verifies completed DSL Objects ([Model Phases](Model-Phases.md)). |
| Sundrio | Java code-generation toolkit; annotated classes or constructors generate Java fluent and nested builders ([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)). | Generated `build()` constructs the target; target classes can be mutable or immutable ([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)). |
| Lombok `@Builder` | Generate a Java builder from a class, constructor, or method ([builder guide](https://projectlombok.org/features/Builder)). | `build()` invokes the target constructor or method; `@Builder.Default` and `@Singular` provide specific default and collection behavior ([builder guide](https://projectlombok.org/features/Builder)). |
| Pkl | Author configuration in modules and classes in a dedicated language ([introduction](https://pkl-lang.org/main/0.32.2/language-reference/index.html#modules)). | Evaluate immutable, lazy objects; amendments derive new objects ([language reference](https://pkl-lang.org/main/0.32.2/language-reference/index.html#objects)). |

KlumAST's completed Models are structurally immutable through their public API. This does not mean arbitrary Simple
Values are deeply immutable; see [materialization](Behind-the-Curtain.md#builder-first-materialization).
Likewise, Lombok's documented immutable collections from `@Singular` do not establish whole-object immutability
([builder guide](https://projectlombok.org/features/Builder)). These are distinct construction contracts.

## How Sundrio relates to KlumAST

The useful overlap is generating typed construction APIs with nested authoring. Sundrio generates separate Fluent and
Builder classes and supports nesting, visitors, and builders for third-party classes
([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)). KlumAST generates
[public Builder contracts](Advanced-Techniques.md) around its Schema and Construction session. A project needing a
convenient way to construct a Java object graph may reasonably investigate either approach, depending on its desired
authoring language and construction rules.

Sundrio also has a separate DSL generator: annotated methods describe a grammar from which it generates fluent Java
interfaces ([DSL guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/dsl/readme.md)). That is a different
meaning of DSL from KlumAST's Groovy Model configuration. Neither the shared word *Builder* nor *DSL* establishes
equivalence to KlumAST's ownership, linking, defaults, materialization, or verification phases.

For a Kubernetes-adjacent application, distinguish the application's domain Model from the resource objects it hands
to a target API. An application could construct its own Model with KlumAST, then map it to a separate Java API whose
builders were generated with Sundrio. That is a possible application architecture, not a supplied KlumAST/Sundrio
adapter or evidence that either tool performs cluster operations. See [Target Contract Modeling](Target-Contract-Modeling.md)
for the KlumAST-side boundary.

## Validation

- **KlumAST:** [validation](Validation.md) runs on completed Models after materialization; earlier Builder findings carry
  forward. Verification applies the configured failure level. Schema code can express domain rules across the Model.
- **Sundrio:** an additional validation processor provides selectable Jakarta constraints, custom-method and
  programmatic validation paths ([validation guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/validation/readme.md)).
  This should be evaluated as its own contract, rather than inferred to be KlumAST's phase system.
- **Lombok:** `@NonNull` generates null checks in the documented method/constructor cases
  ([null-check contract](https://projectlombok.org/features/NonNull)); custom builder elements and the invoked target
  can supply application checks ([builder customization](https://projectlombok.org/features/Builder)). This page makes
  no claim that `@Builder` supplies a general domain-validation lifecycle.
- **Pkl:** types and constraint expressions check configuration values; evaluation is lazy, so forcing values matters
  ([type constraints](https://pkl-lang.org/main/0.32.2/language-reference/index.html#type-constraints),
  [lazy evaluation](https://pkl-lang.org/main/0.32.2/language-reference/index.html#objects)).

These checks establish different things. A valid domain Model or configuration value still needs the target-specific
tests and integration checks appropriate to its eventual use; see [Testing Models and Schemas](Testing-Models-and-Schemas.md).

## Structured input and output

KlumAST's [Structured Input](Structured-Input.md) accepts caller-parsed Maps, Lists, and scalars for admitted Builder
operations. Parsing remains caller-owned. Generated [JSON Schema](Json-Schema.md) describes that bounded input grammar
and supported constraint projections; KlumAST does not automatically run JSON Schema validation. The optional
[Jackson integration](Jackson-Integration.md) separately imports foreign data through Builders and exports completed
Models as ordinary POJO projections, without an import/export round-trip promise.

Sundrio's builder generator documents a Java construction API, while its wider toolkit models and renders Java source
([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md),
[toolkit overview](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/readme.md)). Do not infer a YAML import contract or a
resource-document renderer from nested Java builders alone; mapping and serialization need their own integration contract.

Lombok's experimental `@Jacksonized` configures Jackson deserialization through generated builders without changing
their behavior ([Jacksonized guide](https://projectlombok.org/features/experimental/Jacksonized)). It is an integration
with a mapper, not the same input language as KlumAST Struct operations.

Pkl can parse external JSON/YAML with its standard-library parsers and render configuration with format-specific
renderers ([JSON parser](https://github.com/apple/pkl/blob/0.32.2/stdlib/json.pkl),
[YAML parser](https://github.com/apple/pkl/blob/0.32.2/stdlib/yaml.pkl),
[output formats](https://pkl-lang.org/main/0.32.2/language-reference/index.html#module-output)). This makes document
production a direct comparison point, while evaluating Pkl configuration and completing a KlumAST Model remain different operations.

## Extension and integration boundaries

KlumAST extends construction through Schema-authored Builder methods, [Lifecycle Participants](Lifecycle-Participants.md),
and [state-typed plugin phases](Behind-the-Curtain.md#plugin-phase-actions). Client code consumes completed Models through
their domain API or [Completed Object Support](Completed-Object-Support.md).

Sundrio's extension points include its Java code model, adapters, and generation tools
([toolkit overview](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/readme.md)); Lombok permits manually supplied builder
elements alongside generation ([builder customization](https://projectlombok.org/features/Builder)). Pkl supports host-side
evaluation and Java code generation ([Java integration](https://github.com/apple/pkl/blob/0.32.2/docs/modules/java-binding/pages/index.adoc)).
These integration surfaces do not establish interchangeability with KlumAST's Builder or lifecycle contracts.

## Choosing for this task

**KlumAST fits when** the application needs a reusable typed domain Schema, Groovy Model authoring (or the admitted
structured-input subset), and explicit construction phases for ownership, linking, defaults, and domain validation
before client code consumes a completed Model. Start with [Basics](Basics.md) and [Model Phases](Model-Phases.md).

**Another approach fits when** the required result is primarily:

- A nested Java construction API around existing classes, or a generated fluent Java grammar: investigate Sundrio's
  [builder](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md) or
  [DSL generator](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/dsl/readme.md).
- A builder for a class, constructor, or method with application-owned construction rules: investigate
  [Lombok `@Builder`](https://projectlombok.org/features/Builder).
- Configuration modules that evaluate to rendered documents, with types and constraints in a dedicated language:
  investigate [Pkl](https://pkl-lang.org/main/0.32.2/language-reference/index.html#modules).

These can be alternatives for a bounded construction or authoring task, or complementary stages with explicit mapping
between them. Choose that boundary from the model and output your application actually needs; this page does not qualify
a combined integration or recommend a general technology stack.
