# KlumAST alongside Sundrio and related approaches

KlumAST, [Sundrio](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md),
and [Lombok's builder annotations](https://projectlombok.org/features/Builder) generate construction APIs. For a Model
Writer, the immediate difference is the code used to describe a graph: nested Groovy closures or fluent Java builder
calls. The examples below make that authoring experience the main comparison.

Overlapping tools are not automatically substitutes. Similar construction syntax can carry different runtime contracts;
this page compares the authoring forms and their boundaries without ranking products or promising equivalent features.

External sources were retrieved on **2026-11-23**. The main comparison is the generated Model-authoring surface:
KlumAST's Groovy closure DSL alongside Sundrio and Lombok's fluent Java APIs. Validation, YAML, and the related Pkl
configuration-language approach are supporting considerations. Follow the linked product contracts when adopting a
particular version. KlumAST terminology follows the [Terms and role guide](Terms.md).

## Writing a Model with the generated API

The first question is what Model Writers actually write. The examples below describe the same small graph: a
Deployment with an environment and one Component with a name and image. Each approach generates an authoring API from
its own declarations; the snippets illustrate those APIs, not source-compatible types or equivalent runtime behavior.

### KlumAST: nested Groovy closures

A Schema Developer declares the structure with `@DSL` ([Basics](Basics.md)). For this example:

```groovy
import com.blackbuild.klum.ast.DSL

@DSL
class Deployment {
    String environment
    Component component
}

@DSL
class Component {
    String name
    String image
}
```

The Model Writer then uses the generated DSL:

```groovy
def deployment = Deployment.Create.With {
    environment 'production'
    component {
        name 'catalog'
        image 'example/catalog:1.0'
    }
}
```

`component { ... }` opens a configuration scope for the child Builder. Inside it, `name` and `image` are calls on that
Builder; the closing brace returns to the surrounding scope. Groovy permits these calls without dots or parentheses.
There is one root factory call, and the Model Writer does not call `build()` on each child.
See [factory construction](Basics.md#factory-construction) and [Model Phases](Model-Phases.md) for the underlying contract.

### Sundrio: a fluent Java chain with nested builders

For corresponding Java POJOs, Sundrio's `@Buildable` on a class or constructor generates Builder/Fluent APIs. The
properties must have getters and either setters or matching constructor arguments; both types in this example need
builder generation. With that setup, a Model author can write the following adapted example of the documented
[nested-builder pattern](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders):

```java
Deployment deployment = new DeploymentBuilder()
    .withEnvironment("production")
    .withNewComponent()
        .withName("catalog")
        .withImage("example/catalog:1.0")
    .and()
    .build();
```

The chain changes its receiver to the nested builder at `withNewComponent()`. Here, `and()` returns to the parent
builder, where `build()` produces the result. Nesting is represented by generated Java method calls and return types;
indentation helps the reader but does not create the scope
([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)).

### Lombok: compose ordinary builder expressions

With Lombok `@Builder` on both corresponding Java classes, each class receives its own builder API. The parent call
accepts the child object produced by the inner expression ([builder guide](https://projectlombok.org/features/Builder)):

```java
Deployment deployment = Deployment.builder()
    .environment("production")
    .component(Component.builder()
        .name("catalog")
        .image("example/catalog:1.0")
        .build())
    .build();
```

This uses a fluent API as well, with a separately built value passed to `component(...)`. The example follows
Lombok's documented parameter setters and `build()` invocation; it does not assume Sundrio-style nested-builder
navigation or KlumAST-style closure scopes ([builder guide](https://projectlombok.org/features/Builder)).

### What changes for the author?

| Approach | Authoring form | How the example enters and leaves a child |
| --- | --- | --- |
| KlumAST | Groovy calls inside closures over typed, generated Builders ([Basics](Basics.md)). | `component { ... }`; braces delimit child configuration, and the root factory returns the Model. |
| Sundrio | Fluent Java method chain ([nested builders](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders)). | `withNewComponent()` enters; `and()` returns to the parent; root `build()` finishes. |
| Lombok | Composed Java builder expressions ([builder guide](https://projectlombok.org/features/Builder)). | `Component.builder()...build()` supplies a value to the parent builder's `component(...)`. |

The distinction is the generated language the author uses to express the graph. The examples do not establish a
readability ranking, equivalent lifecycle semantics, or a reason to replace an existing API solely because it is fluent.

## How Sundrio relates to KlumAST

The useful overlap is generating typed construction APIs from declarations so authors can express object graphs in
code. Sundrio generates separate Fluent and Builder classes and supports nesting, visitors, and builders for third-party classes
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

## When each authoring approach fits

**KlumAST fits when** Model Writers should express a reusable domain Schema as nested Groovy configuration, with child
scopes such as `component { ... }`. Its construction lifecycle can also supply ownership, linking, defaults, and
validation before client code consumes the completed Model ([Basics](Basics.md), [Model Phases](Model-Phases.md)).

**Another approach fits when** the required authoring surface is primarily:

- A generated fluent Java API with parent/child builder navigation around POJOs: investigate
  [Sundrio builders](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md).
- Builder expressions at Java call sites, with construction delegated to a class, constructor, or method: investigate
  [Lombok `@Builder`](https://projectlombok.org/features/Builder).
- A fluent Java grammar controlling allowed method sequences: investigate Sundrio's separate
  [DSL generator](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/dsl/readme.md).

These can be alternatives for a bounded authoring task or complementary stages connected by application-owned mapping.
For example, choosing Groovy to author a domain Model does not require changing an existing Java API used to construct
its eventual target objects.

## Supporting concerns

### Construction and extension boundaries

KlumAST's root factory owns one Construction session: configuration and graph-wide Builder phases precede
materialization and verification ([Model Phases](Model-Phases.md)). Completed Models are structurally immutable through
their public API; arbitrary Simple Values are not deeply immutable ([materialization](Behind-the-Curtain.md#builder-first-materialization)).
Sundrio's generated builders construct their target POJOs, whose constructors/properties determine the target contract
([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)).
Lombok's `build()` invokes its annotated target; defaults and immutable `@Singular` collections are specific documented
features, not a whole-object immutability guarantee ([builder guide](https://projectlombok.org/features/Builder)).

KlumAST extensions use Schema-authored Builder methods, [Lifecycle Participants](Lifecycle-Participants.md), and
[state-typed plugin phases](Behind-the-Curtain.md#plugin-phase-actions). Sundrio offers a Java code model, adapters, and
generation tools ([overview](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/readme.md));
Lombok accepts custom builder elements alongside generation ([customization](https://projectlombok.org/features/Builder)).
These are separate integration contracts.

### Validation

- **KlumAST:** [validation](Validation.md) runs on completed Models after materialization; earlier Builder findings carry
  forward. Verification applies the configured failure level. Schema code can express domain rules across the Model.
- **Sundrio:** an additional validation processor provides selectable Jakarta constraints, custom-method and
  programmatic validation paths ([validation guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/validation/readme.md)).
  This should be evaluated as its own contract, rather than inferred to be KlumAST's phase system.
- **Lombok:** `@NonNull` generates null checks in the documented method/constructor cases
  ([null-check contract](https://projectlombok.org/features/NonNull)); custom builder elements and the invoked target
  can supply application checks ([builder customization](https://projectlombok.org/features/Builder)). This page makes
  no claim that `@Builder` supplies a general domain-validation lifecycle.

These checks establish different things. A valid domain Model or configuration value still needs the target-specific
tests and integration checks appropriate to its eventual use; see [Testing Models and Schemas](Testing-Models-and-Schemas.md).

### YAML, external input, and projection

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

Pkl is a related approach when the authoring task is configuration modules and rendered documents. Its immutable,
lazily evaluated objects and type constraints belong to configuration evaluation, rather than the generated Java/Groovy
builder surfaces above ([objects](https://pkl-lang.org/main/0.32.2/language-reference/index.html#objects),
[type constraints](https://pkl-lang.org/main/0.32.2/language-reference/index.html#type-constraints)). Pkl's standard-library
[JSON](https://github.com/apple/pkl/blob/0.32.2/stdlib/json.pkl) and
[YAML parsers](https://github.com/apple/pkl/blob/0.32.2/stdlib/yaml.pkl),
[module output renderers](https://pkl-lang.org/main/0.32.2/language-reference/index.html#module-output), and
[Java integration](https://github.com/apple/pkl/blob/0.32.2/docs/modules/java-binding/pages/index.adoc) address external data
and host integration. Consider Pkl when those configuration-language concerns are the main requirement; this page does
not qualify an integration between these approaches or recommend a general technology stack.
