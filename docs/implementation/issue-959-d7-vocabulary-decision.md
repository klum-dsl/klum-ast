# #959 D7: vocabulary and semantics decision brief

Date: 2026-10-26. Status: **Vocabulary direction accepted; field projection slice subsequently implemented.**

The [first implementation slice](issue-959-d7-constraint-projection.md) and
[ADR checkpoint](../adr/0031-structured-schema-input-artifacts.md#d7-field-constraint-projection-slice--959)
settle the enum, retention, declaring-field semantics and required transport only. The historical open questions below
retain their decision-time wording; naming, examples and broader D7 semantics remain deferred.
The next independent [external naming/exclusion decision brief](issue-959-d7-external-naming-decision.md)
records the confirmed type-profile/literal authoring contract, binding inheritance and bounded binary probes required
before a separately assigned implementation.
Audited baseline: `6ec0a0404515722deccc3fc68ca4a8d9325c2170`, merged [PR #972](https://github.com/klum-dsl/klum-ast/pull/972).
Authority: [ADR 0031 accepted vocabulary direction](../adr/0031-structured-schema-input-artifacts.md#d7-vocabulary-direction--959).
The [earlier D7 record](issue-959-d7-naming-profiles.md) retains historical probes and broader alternatives; its proposed algorithms, precedence and annotation names are not decisions.

## Accepted maintainer decision — 2026-10-26

The maintainer selected **B: a small family of focused Klum annotations**, keeping language shaping separate from advisory projection and allowing compatible Jackson annotation inputs. The following decisions supersede this brief's original examples-first recommendation and single suppression-marker proposal:

- Preserve the existing optional `JsonProperty`/`JsonIgnore` compatibility. Equivalent Klum/Jackson facts may coexist; conflicting declarations should diagnose rather than depend on annotation order. Broader `JsonNaming` support requires a separate bounded compatibility decision. Neither Jackson nor Bean Validation owns the canonical contract or becomes a required dependency.
- **Defer examples.** Reconsider authoring through AnnoDocimal when structured Javadoc tags can be retained as separate annotations. Existing structured `Documentation.getTags()` access is distinct from that transport capability. Scalar examples authored as simple strings remain a possible fallback, not an accepted example syntax or type-conversion rule. Structured JSON examples, placement/profile rules and the annotation/tag bridge below remain deferred alternatives.
- Use the singular author annotation name **`ConstraintProjection`**, with an enum-valued policy rather than the earlier `NoConstraintProjection` marker. The enum type name, constant names, final value set and default are **still to be decided**. Normal assertion projection, text-only constraint descriptions and omission of constraint-derived output are the candidate behaviors discussed; the provisional spellings `PROJECT`, `TEXT_ONLY` and `SKIP` do not constitute an accepted enum contract.
- Apply projection policy to the field's supported effective constraint contributions, including approved propagated origins, without enabling arbitrary composed-constraint projection. Preserve ordinary documentation, standard deprecation, structural types/machine bounds, naming/admission, key/null/omission semantics and runtime validation. This is artifact policy only.

No package, other annotation spelling, naming profile/scope precedence, generated metadata format or implementation assignment is accepted. The always-on faithful numeric baseline from #972 remains intact. This records the maintainer's decision in this conversation; it does not authorize production work, tracker changes or release placement.

## Problem, goal and exclusions

Schema Developers need to describe the structured input they publish, including useful examples, without learning compiler transport or depending on Jackson/Bean Validation to define their language. Names affect both interpretation and generated JSON Schema; examples and projection policy affect the artifact only. Putting all these concerns into one annotation is not automatically a smaller interface.

Seek a **deep module**: a small author interface whose source-associated facts give both consumers leverage through the existing compiled-contract seam. Keep declaration matching, inheritance, collision resolution, metadata decoding and JSON placement in its implementation. Advisory facts should not acquire interpreter behavior merely to share that seam.

This brief records the accepted design direction without an implementation assignment. Numeric projection is the stable, always-on, faithful baseline, including contradictory representable bounds. No public descriptor framework, generic annotation DSL, registry/SPI, required Jackson/Bean dependency, WIRE-carrier revival, new admission grammar, release placement or tracker change follows.

## Audit: facts versus unproved extensions

| Current fact, inspected at the baseline | Evidence and limit |
| --- | --- |
| Public Builder declarations carry native field/key identity, literal rename/ignore, selected population groups, exclusions, direct constraint records and source deprecation. | [GeneratedInput](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java), [compiler enrichment](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java). Authored operations retain selected exact annotations. There is no canonical example or profile fact. |
| Both consumers use the internal compiled Builder view. Names are identity fallback, nonempty `JsonProperty`, or exclusion by `JsonIgnore(true)`. | [CompiledBuilderSchema](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java). It traverses declaring interfaces explicitly, reads selected annotation bytes, and prunes collisions during hierarchy collection. Its resolved entries are too late for arbitrary new profiles: previously pruned candidates may become distinct. |
| The interpreter has two `readRoot` forms and active `applyToBuilder`; none selects a profile. | [KlumStructuredInput](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumStructuredInput.java), [key-position decision](../adr/0031-structured-schema-input-artifacts.md#explicit-key-member-extension--954). Self-contained and externally keyed bodies have different construction-key admission. Naming cannot erase that distinction. |
| Generation places descriptions/deprecation on effective uses and projects the supported direct numeric subset unconditionally. It does not emit typed examples. | [JsonSchemaProjection](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java). Machine bounds precede independent declaration assertions; colliding assertion keywords conjoin. Required numeric completeness remains separate from structural interpretation. |
| `@Deprecated` is delivered separately; field/key/operation provenance is distinct from framework aliases. | [Deprecation evidence](issue-959-deprecation-projection.md), [binary generator tests](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy). Do not add a D7 deprecation flag or infer replacement semantics. Tests retain API/base/derived producers with deleted sources and initialization traps; this audit did not rerun them. |
| Existing annotations already separate construction, validation and source composition. | [Field](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Field.java) controls Builder behavior, not external names. [PropagateAnnotation](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/PropagateAnnotation.java) requests one-hop bare annotations using defaults; it is not arbitrary Bean composition. [Constraint capture](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputConstraintMetadata.java) reads field annotation facts; [copy exclusions](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AnnotationCopyExceptions.java) deliberately keep Bean constraints off executable Builder sites. |
| Direct Jackson interoperability owns a richer, independent binding contract. | [ADR 0009](../adr/0009-jackson-interoperability.md), [current customization tests](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonCustomizationSpec.groovy), [YAML documentary test](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonYamlInteroperabilityDocumentaryTest.groovy). Mixins, aliases, access, codecs and coercion do not follow from the interpreter's narrow literals. The [Bean adapter](../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/internal/JSR380Validator.java) executes instance validation; it is not a metadata projection interface. |
| Groovy field and authored-operation `@example` text survived on generated public declarations in the earlier characterization. | [Retained test at e41a70fff](https://github.com/klum-dsl/klum-ast/blob/e41a70fff/klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/NamingProfileD7CharacterizationTest.groovy), [KlumDocumentation](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/KlumDocumentation.java). That historical G3/4/5 result proves retained strings, not a typed example grammar, Java processing, exact multiline preservation, or a source-deleted separate-JAR example contract. |
| Package annotations can survive binary publication, but Klum package shaping is unproved. | The [historical package probe](https://github.com/klum-dsl/klum-ast/blob/e41a70fff/docs/implementation/fixtures/issue-959-d7/probe_package_metadata.py) uses only `@Deprecated`, JDK 17 and Java/G3/4/5 producers. It does not establish a Klum compilation hook, artifact-conflict handling or JPMS shaping semantics. |

**Inference:** the shared seam earns its keep; a shared public umbrella does not yet. The earlier stakeholder record observed camelCase YAML and no verified alternate-profile requirement. That is historical consultation, not newly verified consumer demand. A nondefault naming algorithm, package precedence and unlimited example size remain hypotheses.

## Four competing public shapes

These alternatives concern authoring; all can use minimal compiler-folded facts internally. A common internal path does not require a public base annotation, descriptor type or generic policy object.

| Criterion | A — one narrow umbrella | B — focused family | C — third-party annotations + minimal Klum layer | D — separate language and advisory vocabularies |
| --- | --- | --- | --- | --- |
| Concrete shape | Field/method `@Input(name, exclude, examples, projectConstraints)`; no package/profile members initially. | Separate name, ignore, example and optional suppression annotations; add each only when needed. | Existing Jackson literals, Bean constraints, retained Javadoc examples; Klum supplies missing projection policy/strict bridges. | Language annotations concern names/exclusion; independent example/projection annotations, standard deprecation and retained prose. No common public namespace/type required. |
| Groovy ergonomics | One import and compact use; mixed intent and invalid member combinations grow quickly. Type naming would require more target-specific rules. | More imports, short self-explanatory uses; no unrelated defaults to learn. | Least new naming syntax for existing users; different annotation libraries and comment grammar become required knowledge. | Clear effects, more names; authors must learn which facts describe language versus artifacts. |
| Source-free transport | Compiler splits members onto exact public declarations; copying the umbrella wholesale is insufficient. | Compiler folds each fact at the same declaration seam, without a public common descriptor. | Capture only literal/whitelisted facts while producer dependencies exist; raw foreign reflection cannot establish completeness. | Separate facts can share the existing source identity internally. Two independent discovery systems would duplicate work. |
| Binary evolution | Every new member expands one contract; defaults/sentinels and target restrictions accumulate. | Each annotation evolves independently; more types, smaller contracts. | Foreign versions/semantics can change independently; the Klum adapter must freeze its supported interpretation. | Language compatibility can be strict while missing advisory facts remain omittable. No requirement to version them together. |
| Inheritance/composition | One inherited-looking annotation can misleadingly combine exclusion, example merging and suppression. Must resolve each member separately. | Each fact follows declaring source identity; example alternatives need not inherit like name overrides. | Jackson merge behavior and Bean composition are not Klum inheritance rules; explicit normalization still necessary. | Clean separation, but both sides must reuse declaration/use identity to avoid mismatched placement. |
| Interpreter–generator consistency | Good if both resolve the same language members; dangerous if interpreter starts enforcing projection members. | Good through one name/admission implementation; interpreter ignores advisory family. | Existing narrow literals work; mapper configuration/custom naming cannot silently govern parsed Maps. | Good with shared internal language resolution; poor if “separate” means unrelated name resolvers. |
| Jackson/Bean bridges | Optional producers of the same bounded facts; umbrella cannot promise importer parity. | Optional per-fact adapters; supported Bean assertions remain independent. | Natural reuse, but making these primary contradicts the Klum-owned contract direction and excludes dependency-free authors. | Natural optional bridges, with explicit conflict rules for language facts and no runtime validation coupling. |
| Explainable without transport | “Describe this input” is simple until unrelated switches interact. | “Name it, omit it, illustrate it, or suppress its projected assertions” explains each effect. | Easy only for users already familiar with several foreign semantics. | “These change accepted tokens; these document/project them” is concise; shared machinery stays hidden. |

**Selected direction:** B for authoring, with D's semantic separation and optional Jackson compatibility. Do not require a common public namespace or meta-annotation. The table retains the alternatives assessed before selection; A and C are not selected as the canonical authoring shape.

C can also offer dependency-free authoring if its Klum override expresses every required fact without foreign imports; that version converges on B with optional adapters. Its useful economy is reusing existing author declarations, not delegating language ownership to another library.

## Vocabulary matrix

Accepted direction is distinct from an installed feature; unresolved details remain explicit.

| Capability | Accepted direction / retained baseline | Defer / unresolved | Reject / no evidence |
| --- | --- | --- | --- |
| External names | Focused family, identity baseline and optional compatible Jackson literals; diagnose conflicting facts. | Canonical name/exclusion spellings, nondefault profiles, type/package/root precedence, aliases and include overrides. | Exporter-only renaming; automatic discovery of mapper strategies; treating identity as a camel normalizer. |
| Examples | Advisory only when revisited; deferred from the initial direction. | Separate AnnoDocimal tag annotations; possible scalar/string fallback; all syntax, placement and profile rules. | Groovy expression/closure evaluation; parsing free prose as data; constructing Models to obtain samples. |
| Constraint projection | Field-level `ConstraintProjection` with an enum-valued policy over supported effective contributions. | Enum name/constants/final value set/default, retention and metadata completeness details, relationship-depth extension, selective suppression. | Removing runtime validators or machine/type restrictions; global opt-in restoration; pretending arbitrary Bean composition is supported. |
| Deprecation/descriptions | Standard `@Deprecated`; existing retained documentation. | `since`/`forRemoval` policy and richer description authoring only with demand. | Duplicate D7 deprecation flag. |
| Package metadata | Keep outside the initial interface. | Reconsider only for a concrete repeated-family convention and an artifact-owned compile-time rule. | Ambient package lookup, subpackage inference, first-on-classpath resolution. |
| Transport/adapters | Minimal inert facts on declaring public Builder declarations; optional literal compatibility/enrichment. | Companion only after one named fact demonstrably exceeds faithful/economic annotation transport. | WIRE carrier, annotation graph descriptors, registry/SPI, provider execution or required Jackson/Bean linkage. |

## Naming: smallest scope and unresolved compatibility choices

Initial **author scope**, if approved, is an exact Schema field/key or an already admitted authored operation: literal external name or exclusion only. No member-level naming profile is needed when a literal suffices. Existing empty rename means fallback, ignore means exclusion and rename adds no alias. Exclusion cannot accidentally remove a required construction-key obligation or re-admit a role/payload the compiler excludes.

Identity preserves every character (`URLValue` stays `URLValue`); ordinary `serviceName` is already camelCase by convention. A real camel transform would need its own rule for acronyms, initial capitals, separators, digits and Unicode. Kebab/snake labels alone are likewise incomplete: the [Jackson 2.20 implementation](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/util/NamingStrategyImpls.java) and retained probe distinguish `URLValue → urlvalue` in snake from `URLValue → url-value` in kebab. The earlier ASCII algorithm is one candidate, not an accepted standard.

If alternate profiles acquire a concrete use case, evaluate **one explicit root-document selection shared by both consumers** first. It avoids an unevidenced member > type > package > caller ladder. Type defaults create a real choice about inherited declarations and nested dependencies; package defaults add a second ownership problem. Do not add either merely because retention is possible. Profile selection must preserve literal renames and literal Map/instance keys, values, enums and key values; resolve before collision pruning and before any execution/output replacement. Construction-key member names remain position-sensitive.

The accepted compatibility direction allows equal facts to coexist and diagnoses incompatible Klum/Jackson declarations rather than selecting by annotation order. Canonical metadata absent means today's narrow Jackson literals continue to work; adding a canonical annotation should not silently change direct Jackson binding. Diagnostic severity and exact handling of contradictory includes/exclusions still need the bounded naming contract. No mapper/mixin parity follows. [JsonNaming selects a strategy class](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/annotation/JsonNaming.java); core must not instantiate it.

Package metadata does **not** earn initial scope. JDK [Package metadata](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html) is tied to class loader/code source, not a model-family hierarchy. Any later scheme must bind an exact producer artifact's package declaration at compilation, fold the result onto that producer's Builder, track recompilation inputs, and reject unresolved duplicate origins. It must not consult an arbitrary consumer-classpath package-info or imply subpackage inheritance.

## Examples: advisory input values, not prose or defaults

**Deferred alternative retained for later review; none of this section's example contract is accepted.** Prefer revisiting separately retained AnnoDocimal tags before adding a Klum example annotation. A scalar/string fallback would still need to decide whether `'42'` means a JSON String or a numeric literal; do not infer conversion from the field type.

The original alternative was one annotation with a constant String array: **each string encodes one complete JSON value**. This uses ordinary [annotation element types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.1) rather than a nested annotation DSL. Strings require JSON quotes, numbers remain exact, and arrays/objects are genuine structured values. For example, one encoded `["a","b"]` yields `examples: [["a","b"]]`, not two examples. JSON Schema [examples](https://json-schema.org/draft/2020-12/json-schema-validation#section-9.5) is an array of illustrative values, with no added admission assertion.

| Authoring input | Assessment |
| --- | --- |
| Klum annotation | Explicit machine intent and exact target. Recommend field/method targets and runtime retention consistent with existing author annotations, compiler-folded once; consumers never need Model reflection. Escaping and size require the bounded probe below. |
| Retained Javadoc `@example` | Attractive for readability; ordinary comments alone disappear. AnnoDocimal retention is real but limited evidence. A supported bridge must define strict tag grammar, multiline handling and exact source provenance before tags become machine examples. |
| Annotation + Javadoc bridge | Viable later into the same facts. Proposed conflict rule: annotation supplies that source/target's machine examples; tags remain prose there; absent annotation, bridge parses only the explicitly supported tag. No duplicate examples from generated getter/setter prose. This is unaccepted; additive merging is an alternative, not an automatic consequence. |

Initial placement is the **whole supplied payload**, determined by declaration identity, never guessed from JSON shape:

- Field scalar/list/map: the effective property's value schema. Collection item examples are a separate deferred target.
- Authored one-payload operation: its `properties` value; a Map example remains one argument, not named-argument spreading or inferred field effects.
- Owned relationship field: a sibling of that use's `$ref`; a whole keyed-map example belongs on the outer field. Neither mutates shared child `$defs` or nonannotated sibling uses.
- Type/root, item and owned-map-value examples: deferred authoring sites. A future type example must distinguish self-contained/keyless bodies; a parent's relationship-use example must never become a shared type example. An excluded member has no example placement.

Examples follow the selected source declaration: the same inherited declaration contributes once; an overriding operation supplies a new declaration, with no automatic example union. No new user-defined meta-annotation composition is implied. Public Builder interface inheritance is traversed explicitly, not treated as automatic annotation inheritance.

Object examples are authored in the **actual current external grammar**, including literal renames and construction-key position. Never recursively rename their keys: some identify input members, others are literal Map/instance data. Initial examples assume identity fallback plus existing explicit renames; there is no profile selector or translator. A future nonidentity generator must omit incompatible structured examples with an origin diagnostic unless explicitly qualified for that grammar. Scalar examples can survive a profile change if their payload meaning is unchanged. Arrays containing objects are profile-sensitive too.

Recommend generation failure for malformed explicit machine JSON/metadata, with source and target, preserving previous artifacts; the interpreter ignores advisory examples. Reject duplicate object keys, interpolation, expressions, NaN/infinity and uncontrolled size/depth; preserve exact representable numbers without double conversion. Exact limits/parser remain probe outputs. Do not execute initializers, factories, defaults, Templates, lifecycle, validator/provider discovery or user annotation code. A syntactically valid example need not be universally valid: any static incompatibility diagnostic stays advisory, and must not introduce numeric satisfiability analysis. Adding/removing examples changes no admission, requiredness, null behavior, runtime validation or construction result.

## ConstraintProjection: accepted shape, unresolved enum contract

Keep **direct source facts**, **supported effective contributions**, and **emitted assertions** distinct. Today the generator supports a bounded direct Jakarta numeric subset; custom/meta/composed/repeatable constraints can be retained as descriptions without becoming hard assertions. A supported contribution can originate from a source annotation produced by approved propagation; suppression must not depend only on the spelling physically written at the field. Future composed mappings require their own acceptance.

The accepted shape is a field-level **`ConstraintProjection` annotation with an enum-valued policy**, replacing the proposed `NoConstraintProjection` marker. Its candidate behavioral distinctions are:

| Candidate behavior | Declaration-derived assertions | Constraint-derived descriptions |
| --- | --- | --- |
| Normal projection | Emit the supported subset | Keep fallback descriptions for unsupported constraints |
| Text only | Omit | Describe constraints, including those otherwise projectable |
| Omit constraint projection | Omit | Omit |

The enum type, final values, names and default remain open; this table preserves the discussed semantics for that decision without freezing `ProjectionType` or `PROJECT`/`TEXT_ONLY`/`SKIP`. An absent policy preserves today's always-on supported projection. No annotation or enum is installed.

The policy covers **all supported declaration-derived contributions for that effective field** and follows declaring source identity through inherited public contracts. It applies after supported contribution selection, before assertion/constraint-description placement. A child source's own constraints inside a referenced definition remain independent; no recursive subtree erasure is implied. Exact inherited-policy override, retention and completeness rules still require a bounded contract. Initially qualify ordinary scalar fields; do not promise relationship-depth behavior without a concrete mapping.

All-versus-selective is consequential. All is explainable without exposing annotation class names, JSON keywords or contribution registries, and includes newly supported declaration contributions later; that forward effect must be documented. Selective suppression needs stable identities for direct/propagated/composed contributions and conflict rules, with no demonstrated need yet. Do not remove every `minimum`/`maximum` keyword from the final schema: machine bounds may use those same keywords.

Preserve numeric type/machine limits, enum/shape restrictions, admission, naming/exclusion, construction-key requiredness, null/omission rules and all runtime validation. Ordinary source documentation and standard deprecation remain independent of every mode. Text-only output should identify that assertions were not projected and runtime constraints remain; omission concerns constraint-derived output, not ordinary prose. Retaining source facts, required completeness checks and malformed-fact diagnostics is the metadata recommendation, still to be qualified with the enum contract. No Model/setter mutation, validator disabling, normalization/repair promise, general filtering layer or new global option follows.

## Historical Groovy authoring sketches for taste

**These predecision sketches retain the alternatives evaluated, not current implementation scope.** Their `NoConstraintProjection` marker and examples-first direction are superseded by the accepted decision above. Other new names remain placeholders, and the sketches are unavailable and not executable as written. Existing `DSL`, `Builder.Method` and Jakarta annotations retain their current meaning. No package for the proposed annotations is chosen. Profile/type/package syntax is intentionally absent.

A — bounded umbrella (one annotation, mixed concerns):

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Min

@DSL class Service {
    @Input(name = 'service-name', examples = ['"catalog"']) String serviceName
    @Input(exclude = true) String internalNote
    @Input(projectConstraints = false) @Min(0L) Integer percentage
}
```

B — focused family (selected shape; example and marker spellings below are historical):

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Min

@DSL class Service {
    @InputName('service-name') @InputExample('"catalog"') String serviceName
    @InputIgnore String internalNote
    @NoConstraintProjection @Min(0L) Integer percentage
}
@DSL class Deployment {
    @InputExample('{"service-name":"catalog"}') Service primary
}
```

C/D — existing naming bridge plus a possible strict documentation bridge:

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Min

@DSL class Service {
    @JsonProperty('service-name') String serviceName
    /** @example {"service-name":"catalog","labels":{"Team/Blue":"ops"}} */
    @Builder.Method void configure(Map<String, Object> payload) { /* authored logic */ }
    @NoConstraintProjection @Min(0L) Integer percentage
}
```

The operation example illustrates its Map payload, not a Service object export. Its `Team/Blue` key is data. This sketch does not make existing tags machine-readable today.

## Remaining bounded decisions and compatibility consequences

The original examples-first increment is superseded. The next bounded design question is the `ConstraintProjection` enum contract and its minimal source-free metadata, independently of examples and broader naming profiles. Selecting the annotation family is not an implementation assignment or approval of every possible family member.

Prefer minimal compiler-folded policy facts on the declaring public Builder field declaration, reusing source/selection identity. Policy must control supported contributions, not annotation scanning or removal of final JSON keywords. No new fact format/version is selected here. Existing strict generated-annotation readers reject unknown members; qualify any additive route rather than claiming annotation defaults establish compatibility. Keep the transport implementation hidden.

A future language rename/exclusion cannot safely be ignored by an old interpreter: it needs an explicit capability/completeness contract, matching consumer versions and source-aware recompile/package failure, without disturbing #972's numeric markers. Projection-policy compatibility likewise needs producer/consumer qualification rather than silent loss of the author's choice. Annotation defaults are applied dynamically ([JLS](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.2)); freeze intended facts at compilation. Missing optional examples and missing required language/policy facts are different cases.

If examples are revisited, String payloads face modified-UTF-8 constant-pool limits ([JVMS](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4.7)); impose modest measured limits before considering overflow machinery. No current fact demonstrates a companion is necessary. No public runtime methods, projection SPI or dependency on a JSON mapper/provider follows from this direction.

Remaining maintainer decisions:

1. **Projection enum:** choose its type name, final value set, constant spellings and default; settle the exact descriptions and omitted constraint-source diagnostics. `ConstraintProjection` is the accepted annotation name, not an enum name.
2. **Policy transport:** settle retention, inherited-policy override and completeness/version behavior, with source-free binary qualification. No selective-filter vocabulary by default.
3. **Later language contract:** choose canonical name/exclusion annotation spellings and conflict diagnostics; require a concrete nonidentity document before settling algorithms or root/type/package precedence. Jackson naming strategies remain a separate adapter decision.
4. **Deferred examples:** revisit separate retained AnnoDocimal tags; decide whether a scalar/string fallback is needed and what its values mean before selecting any syntax or placement rules.

Before accepting concrete policy transport, a bounded isolated test-only probe should qualify direct/propagated and inherited field policy on Groovy 3/4/5 public Builder binaries, including old/missing facts and unchanged runtime validation. It must distinguish machine bounds from declaration assertions and ordinary prose from constraint descriptions. No production hooks or new annotation artifact is authorized by this record. The earlier finite example/tag/JAR/size probe remains relevant only if examples are revisited; it is not a prerequisite for recording today's direction. Neither probe is run or scheduled here.

Recommend separate follow-ups for **constraint projection policy**, **language naming/exclusion/profiles**, and deferred **input examples** when selected. No umbrella issue should block the delivered numeric baseline. Exact issue relationships/release placement remain maintainer-owned; create no issues yet. The ADR now accepts the bounded vocabulary direction while leaving implementation and the remaining contracts open.

## Delivery and verification

The local documentation record now includes this brief, the accepted ADR checkpoint and supersession pointers in the historical D7 note. Production code, executable tests, installed public interfaces, current user documentation, migration navigation and `CHANGES.md` are unchanged. Tracker impact: related #959; no curation/release-gate change, issue update or implementation assignment. Required checks are Markdown/fences, relative targets/anchors and diff checks; Groovy lanes are unnecessary for this document-only change. Historical qualification cited above is explicitly not a new test result.
