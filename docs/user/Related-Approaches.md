# KlumAST alongside Sundrio and related approaches

KlumAST, [Sundrio](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md),
and [Lombok's builder annotations](https://projectlombok.org/features/Builder) generate construction APIs. For a Model
Writer, the immediate difference is the code used to describe a graph: nested Groovy closures or fluent Java builder
calls. Deep object nesting, Model composition across scripts, and locating construction errors make that authoring experience
the main comparison; the main adoption cost is the additional Groovy dependency.

Overlapping tools are not automatically substitutes. Similar construction syntax can carry different runtime contracts;
this page compares the authoring forms and their boundaries without ranking products or promising equivalent features.

External sources were retrieved on **2026-11-23**. The main comparison is the generated Model-authoring surface:
KlumAST's Groovy closure DSL alongside Sundrio and Lombok's fluent Java APIs. Validation, YAML, and the related Pkl
configuration-language approach are supporting considerations. Follow the linked product contracts when adopting a
particular version. KlumAST terminology follows the [Terms and role guide](Terms.md).

## Deeply nested objects: does the source show the tree?

The difference becomes clearer with five object levels: Campaign → Region → Site → Service → Endpoint. Each example
sets a name at every level and a port on the Endpoint. The declarations belong to each tool; the examples compare
authoring, not source-compatible types or equivalent runtime behavior.

### KlumAST: object nesting becomes closure-block nesting

A Schema Developer declares the structure with `@DSL` ([Basics](Basics.md)):

```groovy
import com.blackbuild.klum.ast.DSL

@DSL class Campaign {
    String name
    Region region
}
@DSL class Region {
    String name
    Site site
}
@DSL class Site {
    String name
    Service service
}
@DSL class Service {
    String name
    Endpoint endpoint
}
@DSL class Endpoint {
    String name
    int port
}
```

The Model Writer uses the generated DSL:

```groovy
def campaign = Campaign.Create.With {
    name 'autumn'
    region {
        name 'europe'
        site {
            name 'central'
            service {
                name 'orders'
                endpoint {
                    name 'public'
                    port 8443
                }
            }
        }
    }
}
```

Each owned child has its own closure block. Both the source structure and ordinary block indentation follow the
object hierarchy: the braces delimit where Endpoint configuration ends and Service configuration resumes.
Calls such as `name 'public'` configure the receiving Builder. The root factory completes the graph without a
separate child `build()` call. See [factory construction](Basics.md#factory-construction) and [Model Phases](Model-Phases.md).

### Sundrio: nested builders inside one fluent chain

For corresponding Java POJOs, `@Buildable` on a class or constructor generates Builder/Fluent APIs. Properties need
getters and either setters or matching constructor arguments, and every type in this example needs builder generation
([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)).
Using its documented [nested-builder pattern](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders):

```java
Campaign campaign = new CampaignBuilder()
    .withName("autumn")
    .withNewRegion()
    .withName("europe")
    .withNewSite()
    .withName("central")
    .withNewService()
    .withName("orders")
    .withNewEndpoint()
    .withName("public")
    .withPort(8443)
    .and()
    .and()
    .and()
    .and()
    .build();
```

`withNewRegion()` changes the receiving builder, and each `and()` returns to its parent. Those transitions create
nested objects, but they do not create corresponding source blocks or nested child argument expressions. Extra
indentation of the inner calls in a hand-formatted example is added manually; Sundrio does not generate that layout
in the author's source. Compare the [builder pattern](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders)
with Java's [method-invocation syntax](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12).

A formatter can therefore remove the manually stepped indentation. In the bounded
[google-java-format 2.0.0](https://github.com/google/google-java-format/releases/tag/v2.0.0) probe recorded in the
[research receipt](../implementation/evidence/issue-1108-related-approaches-research.md#formatting-probe), all calls in
this chain received the same continuation indentation. Other formatter settings may differ; the important distinction
is that graph depth is encoded in the method transitions, not in graph-shaped blocks that a formatter can indent.
At this depth, the reader must track the current receiver and count the returns to containing builders.

### Lombok: real expression nesting, with repeated child builders

With `@Builder` on each corresponding Java class, the parent accepts the child object produced by a nested expression
([builder guide](https://projectlombok.org/features/Builder)):

```java
Campaign campaign = Campaign.builder()
    .name("autumn")
    .region(Region.builder()
        .name("europe")
        .site(Site.builder()
            .name("central")
            .service(Service.builder()
                .name("orders")
                .endpoint(Endpoint.builder()
                    .name("public")
                    .port(8443)
                    .build())
                .build())
            .build())
        .build())
    .build();
```

This preserves more of the tree in the source than Sundrio's single chain: child construction is nested inside the
parent's argument expression ([Java invocation syntax](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12)).
The same formatter probe retained that expression nesting, although it compacted the short Endpoint expression.
At four or five levels, the repeated class names, `builder()` calls, and closing `build())` sequences become a
substantial part of the text an author must follow. Extracting child expressions into variables or helper methods can
shorten it; that is an application-authored refactoring of the builder composition
([Lombok construction contract](https://projectlombok.org/features/Builder)).

### Authoring comparison

| Concern | KlumAST | Sundrio nested builders | Lombok builders |
| --- | --- | --- | --- |
| Represent a child | A child closure block ([Basics](Basics.md)). | Child-entry and parent-return calls in a chain ([nested builders](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders)). | A child builder expression passed as an argument ([Builder](https://projectlombok.org/features/Builder)). |
| Source nesting | Blocks follow the owned object tree. | Any tree-shaped indentation of the chain is manual. | Argument expressions follow the composed tree. |
| Deep models | Additional closure blocks. | More receiver transitions and matching parent-return calls. | More nested builder expressions and `build())` endings. |
| Split configuration | Script recipes can retain Model notation ([factories](Convenience-Factories.md)). | Java methods/classes can compose the generated API ([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)). | Java methods/classes can compose the generated API ([Builder](https://projectlombok.org/features/Builder)). |

## Splitting a Model across files and scripts

KlumAST provides Model-loading and recipe composition as part of its authoring surface. A `DelegatingScript` contains
the same calls as a configuration closure; generated list/map relationship methods can apply separately compiled
recipe classes to child Builders in the active Construction session
([script composition](Convenience-Factories.md#script-and-delegating-script-for-collections-and-maps)).

For example, assume a Deployment Schema with `List<Service> services` and a Service with `String endpoint`.
Keep a child recipe in `CatalogService.groovy`; `BillingService.groovy` uses the same header and its own endpoint:

```groovy
// CatalogService.groovy
import groovy.transform.BaseScript
import groovy.util.DelegatingScript

@BaseScript DelegatingScript base

endpoint 'https://catalog.example.test'
```

With both recipe classes compiled and available to the Model, the parent script reads:

(See: `ConvenienceFactoriesDocumentaryTest#'applies DelegatingScript recipes to list and map relationship factories'`.)

```groovy
// DeploymentModel.groovy
Deployment.Create.With {
    services(CatalogService, BillingService)
}
```

This keeps the configuration language while moving a subtree into its own source file. Root factories also load text,
Files, URLs, and classpath-selected scripts ([convenience factories](Convenience-Factories.md)); reusable recipes have
[Template](Templates.md) semantics. Owned recipe composition must stay inside the active session: a regular script
returning a completed Model is not a replacement for a child Builder recipe.

Java code using either fluent API can also be split across methods and classes. Java supports separate method
declarations ([JLS methods](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4)); those helpers can return
child objects or accept builder parameters using the respective [Sundrio](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)
or [Lombok](https://projectlombok.org/features/Builder) APIs. The author must design those helper signatures and the
loading/orchestration convention. KlumAST's advantage for a distributed Model is its existing recipe/loading contract
and preservation of the Model notation across files, rather than a claim that Java cannot modularize construction.

## Locating errors in a large Model

A deeply chained or composed builder expression can put a large subtree in one Java statement. A failure at a
constructor or final `build()` then needs to be related back to the configured child and its earlier calls. Standard
Java stack frames provide method, file, and line information when available
([StackTraceElement](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StackTraceElement.html));
they do not themselves record a domain-model construction path. Breaking chains into named helpers can improve the
local source context. A multiline fluent statement does not erase compiler locations or all runtime line information.

KlumAST adds model-aware context to supported construction failures: `KlumModelException` retains the DSL construction
path, including script entry context and nested recipe/Template steps. This can identify the failing nested construction
operation even when the Model spans several files. For example, the
[exception documentary test](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ExceptionHandlingDocumentaryTest.groovy)
checks that a conflicting child key reports the second call with a path ending in `/service(green)`.
[Validation findings](Validation.md) are grouped by completed object. These diagnostics help map a failure to its
Model context, beyond following a long call chain; they are not a guarantee that every arbitrary exception identifies
an exact source line. See [Exception Handling](Exception-Handling.md) for the supported exception/path contracts.

## The main cost: the Groovy dependency

The closure DSL comes with a runtime and toolchain commitment. Schemas need Groovy compilation and a compatible Groovy
version, and the KlumAST runtime uses Groovy types even when Java code consumes a completed Model
([setup](Usage.md#manual-dependencies), [runtime factory API](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumFactory.java)).
For a Java-only application that would otherwise have no Groovy dependency, this is a substantial adoption tradeoff:
account for Groovy in the dependency graph, packaging, and version alignment. Choosing YAML input does not remove that
runtime requirement.

Lombok's documented setup, in comparison, uses compile-time annotation processing and does not require the Lombok
library at runtime ([Gradle setup](https://projectlombok.org/setup/gradle)). Sundrio's generated Java construction API
also avoids requiring Groovy merely for this authoring style; its own support dependencies depend on the chosen
features ([builder guide](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md)).
If retaining a Java-only toolchain matters more than closure-based authoring and script composition, that is a concrete
reason to prefer one of the Java approaches for this task.

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
scopes such as `region { site { ... } }`, especially for deeply nested or separately scripted Models. Its construction lifecycle can also supply ownership, linking, defaults, and
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
