# Issue #1108 — KlumAST and related approaches

Retrieved: 2026-11-23. Related: [#1108](https://github.com/klum-dsl/klum-ast/issues/1108).
Documentation research, including a syntax-only formatter probe; no benchmark, generated-API execution,
interoperability qualification, or release placement.
This receipt follows the issue-named research-note convention in this evidence directory, including
[the #805 investigation](issue-805-delegating-script-ide-research.md).

## Authoring focus

The main user-facing comparison is what authors write with the generated APIs: KlumAST's nested Groovy closures,
Sundrio's fluent Java nested-builder navigation, and Lombok's composed builder expressions. The page uses the same
illustrative five-level Campaign/Region/Site/Service/Endpoint shape with corresponding declarations for each tool; it does not imply shared
classes, source compatibility, or runtime parity. Sundrio's documented [nested-builder](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md#nested-builders)
`withNew...().and()` pattern and Lombok's
per-target `builder()...build()` pattern supply the external syntax evidence. These are adapted documentation examples,
not compiled interoperability fixtures. YAML, validation, lifecycle details, and Pkl are supporting context.

## Selection and claims used

**Sundrio** is mandatory and directly relevant to generated typed construction and nested Java builders.
The inspected repository revision is `9365b206d3326a8f21b6e597771b6578d0e27d3a` (GitHub commit API, retrieved above).

- [Overview](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/readme.md): Java code model,
  adapters and code generation are the toolkit's scope; this is broader than object-builder generation.
- [Builder generator](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/builder/readme.md):
  `@Buildable` on a class or constructor generates Builder/Fluent APIs, including nested construction, editing via new
  builders and visitors. The target type's constructors/properties matter; the presence of a builder alone does not
  establish immutability. The old `validationEnabled` flag is deprecated and ineffective.
- [Validation module](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/validation/readme.md):
  an additional processor supplies selectable Jakarta, custom-method and programmatic validation paths, with
  `ValidationResult` handling. Do not equate this with KlumAST's root Construction session and phase contract.
- [DSL generator](https://github.com/sundrio/sundrio/blob/9365b206d3326a8f21b6e597771b6578d0e27d3a/annotations/dsl/readme.md):
  annotated methods describe grammar transitions for generated Java interfaces. This is a separate surface from its
  nested builders and from KlumAST's Groovy Model authoring.

**Lombok `@Builder`** supplies a useful narrower comparison: generated Java object-construction convenience.

- [`@Builder`](https://projectlombok.org/features/Builder): type, constructor or method annotation; generated `build()`
  invokes the target. Custom builder members, defaults and `@Singular` collection handling are documented seams.
- [`@NonNull`](https://projectlombok.org/features/NonNull): generated null checks are a concrete validation capability;
  they do not establish a domain-validation lifecycle.
- [`@Jacksonized`](https://projectlombok.org/features/experimental/Jacksonized): configures Jackson to deserialize using
  generated builders without changing builder behavior. Parsing/serialization policy belongs to that integration.

**Pkl** supplies a supporting configuration-language angle: typed configuration authoring and rendered data output;
it is not presented as a generated Java/Groovy DSL peer.
Use the explicit 0.32.2 documentation/source snapshot; no claim that it is the latest release.

- Language reference: [objects](https://pkl-lang.org/main/0.32.2/language-reference/index.html#objects) are immutable and
  lazily evaluated; [type constraints](https://pkl-lang.org/main/0.32.2/language-reference/index.html#type-constraints)
  validate values at runtime; [module output](https://pkl-lang.org/main/0.32.2/language-reference/index.html#module-output)
  selects values/renderers, including JSON/YAML and custom renderers. These semantics differ from a mutable Builder
  phase sequence. These fragment IDs were checked against the retrieved HTML.
- [JSON parser](https://github.com/apple/pkl/blob/0.32.2/stdlib/json.pkl) and
  [YAML parser](https://github.com/apple/pkl/blob/0.32.2/stdlib/yaml.pkl): external text parses into Pkl values; parsing alone
  does not select or instantiate an application domain Schema.
- [Java integration](https://github.com/apple/pkl/blob/0.32.2/docs/modules/java-binding/pages/index.adoc) and
  [configuration binding](https://github.com/apple/pkl/blob/0.32.2/docs/modules/java-binding/pages/pkl-config-java.adoc):
  embedding, code generation and conversion from evaluated configuration are documented integration boundaries.

## Formatting probe

Retrieved/run: 2026-11-23. [google-java-format 2.0.0](https://github.com/google/google-java-format/releases/tag/v2.0.0),
using its official `google-java-format-2.0.0-all-deps.jar` release asset (SHA-256
`c78feb48c549fa7114b7ea11db3b456808f7b371ffe84e6643a44dd2f9588639`), with Temurin JDK 25.0.5 and
`java -jar google-java-format-2.0.0-all-deps.jar --cleanup=false FormatProbe.java`.
The tool's [usage](https://github.com/google/google-java-format#using-the-formatter) describes Java-source formatting.

Input: Java methods containing the page's five-level builder shapes, with hand-stepped child indentation in the single
Sundrio-shaped chain and nested child arguments in the Lombok-shaped expression. Observed output: every Sundrio-shaped
chain call had the same continuation indentation; the Lombok-shaped arguments remained nested, while the shortest
innermost expression was compacted. A repeat formatting pass was idempotent. The parent-return spelling was also
checked as the documented `.and()` pattern. The examples are syntax-only; the generated APIs were not compiled.
This supports one formatter/version observation, not a universal rule for every IDE/formatter.

[Java invocation syntax](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12) distinguishes receivers
from argument expressions. Do not call a chained expression's AST flat: it has recursive receivers. The claim is that
Sundrio's graph-child transitions do not introduce corresponding lexical blocks or nested child argument expressions.
Deep readability and repeated endings are authoring observations about these examples, not a measured product ranking.

## Composition, diagnostics, and dependency cost

- KlumAST [script composition](../../user/Convenience-Factories.md#script-and-delegating-script-for-collections-and-maps)
  and its existing `ConvenienceFactoriesDocumentaryTest` establish separately compiled DelegatingScript recipes in
  active list/map relationship construction. Root File/URL loading is distinct; do not insert a completed root as owned
  composition. [Templates](../../user/Templates.md) provide reusable-recipe semantics.
- [Java methods](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4) and the respective builder guides
  support Java helper-based composition. Compare extra application-owned signatures/loading convention with KlumAST's
  supplied recipe contract; do not claim Java models must be one file or statement.
- [StackTraceElement](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StackTraceElement.html)
  provides method/file/line context. It does not establish that a fluent statement loses all source locations.
  KlumAST's [exception contract](../../user/Exception-Handling.md) and `ExceptionHandlingDocumentaryTest` establish DSL
  construction-path context, including nested recipes/Templates. Do not promise exact source lines for every exception,
  full provenance, or absence of diagnostics in either comparator.
- KlumAST [setup](../../user/Usage.md#manual-dependencies) and the public runtime `KlumFactory`'s Groovy signatures
  establish the Groovy compilation/runtime commitment. Lombok's [Gradle setup](https://projectlombok.org/setup/gradle)
  explicitly distinguishes compile-time use from runtime deployment. Sundrio's source-backed Java APIs do not require
  Groovy merely for fluent Java authoring; no claim of zero other Sundrio runtime dependencies is made.

## Exclusions and limits

- Jackson as a standalone comparator is excluded: this pass uses only its documented Lombok integration boundary.
- Fabric8 Kubernetes Client is excluded as an additional comparator; the comparison does not evaluate Kubernetes API
  clients or infer their behavior from Sundrio's Pod example.
- Xtext, CUE, Jsonnet, Terraform and Pulumi are excluded to keep the pass bounded; no capability or suitability finding
  about these products is made.
- Lombok `@SuperBuilder` is unnecessary for the selected construction comparison; inheritance support is not assessed.

No general YAML/JSON import contract for core Sundrio was established by the selected sources; leave such integration
application-owned rather than claim it impossible. Sundrio's validation README has inconsistent fine-grained method-return
wording, so omit signature-level claims. Comparator versions/integrations require their own adoption checks. Any suggested
KlumAST-to-target projection is an application design possibility, not an existing adapter or interoperability promise.

KlumAST claims should follow the current [Basics](../../user/Basics.md), [Model Phases](../../user/Model-Phases.md),
[Validation](../../user/Validation.md), [Structured Input](../../user/Structured-Input.md),
[JSON Schema](../../user/Json-Schema.md), [Jackson Integration](../../user/Jackson-Integration.md) and
[Lifecycle Participants](../../user/Lifecycle-Participants.md) contracts; no new runtime claim is introduced here.
