# Issue #1108 — KlumAST and related approaches

Retrieved: 2026-11-23. Related: [#1108](https://github.com/klum-dsl/klum-ast/issues/1108).
Documentation research only; no execution, benchmark, interoperability qualification, or release placement.
This receipt follows the issue-named research-note convention in this evidence directory, including
[the #805 investigation](issue-805-delegating-script-ide-research.md).

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

**Pkl** supplies the configuration-language comparison: typed configuration authoring and rendered data output.
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
