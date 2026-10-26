# #959 D7: vocabulary and semantics decision brief

Date: 2026-10-26. Status: **Recommendation for maintainer review; D7 remains deferred.**
Audited baseline: `6ec0a0404515722deccc3fc68ca4a8d9325c2170`, merged [PR #972](https://github.com/klum-dsl/klum-ast/pull/972).
Authority: [ADR 0031 D7 checkpoint](../adr/0031-structured-schema-input-artifacts.md#d7-checkpoint--canonical-shaping-vocabulary-deferred).
The [earlier D7 record](issue-959-d7-naming-profiles.md) retains historical probes and broader alternatives; its proposed algorithms, precedence and annotation names are not decisions.

## Problem, goal and exclusions

Schema Developers need to describe the structured input they publish, including useful examples, without learning compiler transport or depending on Jackson/Bean Validation to define their language. Names affect both interpretation and generated JSON Schema; examples and projection policy affect the artifact only. Putting all these concerns into one annotation is not automatically a smaller interface.

Seek a **deep module**: a small author interface whose source-associated facts give both consumers leverage through the existing compiled-contract seam. Keep declaration matching, inheritance, collision resolution, metadata decoding and JSON placement in its implementation. Advisory facts should not acquire interpreter behavior merely to share that seam.

This brief makes no implementation assignment or accepted ADR change. Numeric projection is the stable, always-on, faithful baseline, including contradictory representable bounds. No public descriptor framework, generic annotation DSL, registry/SPI, required Jackson/Bean dependency, WIRE-carrier revival, new admission grammar, release placement or tracker change is proposed here.

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

**Recommendation:** B for authoring, with D's semantic separation. Do not require a common public namespace or meta-annotation. D is equally viable if “no common vocabulary” means independent public concerns; reject only the interpretation that also duplicates internal source/name resolution. A is a credible fallback if maintainers prefer a single import and accept a permanently bounded target/member set. C remains an optional compatibility path, not canonical authority.

C can also offer dependency-free authoring if its Klum override expresses every required fact without foreign imports; that version converges on B with optional adapters. Its useful economy is reusing existing author declarations, not delegating language ownership to another library.

## Vocabulary matrix

“Recommend now” means a proposal for approval, never an accepted or installed feature.

| Capability | Recommend now | Defer | Reject / no evidence |
| --- | --- | --- | --- |
| External names | Preserve identity and existing literal bridges; focused member literal name/exclusion if canonical spellings are wanted. | Nondefault profiles, type/package/root precedence, aliases and include overrides. | Exporter-only renaming; automatic discovery of mapper strategies; treating identity as a camel normalizer. |
| Examples | First-class advisory full-payload JSON examples on fields and admitted one-payload operations; annotation as the initial machine input. | Javadoc bridge, type/root/item targets, profile-qualified variants and cross-source merging. | Groovy expression/closure evaluation; parsing free prose as data; constructing Models to obtain samples. |
| Constraint projection | Separate field-local all-supported-contributions suppression as a candidate. | Exact marker name, inclusion in a later increment, relationship-depth extension, selective suppression. | Removing runtime validators or machine/type restrictions; global opt-in restoration; pretending arbitrary Bean composition is supported. |
| Deprecation/descriptions | Standard `@Deprecated`; existing retained documentation. | `since`/`forRemoval` policy and richer description authoring only with demand. | Duplicate D7 deprecation flag. |
| Package metadata | Keep outside the initial interface. | Reconsider only for a concrete repeated-family convention and an artifact-owned compile-time rule. | Ambient package lookup, subpackage inference, first-on-classpath resolution. |
| Transport/adapters | Minimal inert facts on declaring public Builder declarations; optional literal compatibility/enrichment. | Companion only after one named fact demonstrably exceeds faithful/economic annotation transport. | WIRE carrier, annotation graph descriptors, registry/SPI, provider execution or required Jackson/Bean linkage. |

## Naming: smallest scope and unresolved compatibility choices

Initial **author scope**, if approved, is an exact Schema field/key or an already admitted authored operation: literal external name or exclusion only. No member-level naming profile is needed when a literal suffices. Existing empty rename means fallback, ignore means exclusion and rename adds no alias. Exclusion cannot accidentally remove a required construction-key obligation or re-admit a role/payload the compiler excludes.

Identity preserves every character (`URLValue` stays `URLValue`); ordinary `serviceName` is already camelCase by convention. A real camel transform would need its own rule for acronyms, initial capitals, separators, digits and Unicode. Kebab/snake labels alone are likewise incomplete: the [Jackson 2.20 implementation](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/util/NamingStrategyImpls.java) and retained probe distinguish `URLValue → urlvalue` in snake from `URLValue → url-value` in kebab. The earlier ASCII algorithm is one candidate, not an accepted standard.

If alternate profiles acquire a concrete use case, evaluate **one explicit root-document selection shared by both consumers** first. It avoids an unevidenced member > type > package > caller ladder. Type defaults create a real choice about inherited declarations and nested dependencies; package defaults add a second ownership problem. Do not add either merely because retention is possible. Profile selection must preserve literal renames and literal Map/instance keys, values, enums and key values; resolve before collision pruning and before any execution/output replacement. Construction-key member names remain position-sensitive.

For a future canonical/legacy conflict, recommend equal facts coalesce and incompatible facts at the same source fail with both origins. Canonical metadata absent means today's narrow Jackson literals continue to work; adding a canonical annotation should not silently change direct Jackson binding. Whether canonical facts instead override a foreign rename/ignore is a maintainer choice, especially for conflicting includes/exclusions. No mapper/mixin parity follows. [JsonNaming selects a strategy class](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/annotation/JsonNaming.java); core must not instantiate it.

Package metadata does **not** earn initial scope. JDK [Package metadata](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html) is tied to class loader/code source, not a model-family hierarchy. Any later scheme must bind an exact producer artifact's package declaration at compilation, fold the result onto that producer's Builder, track recompilation inputs, and reject unresolved duplicate origins. It must not consult an arbitrary consumer-classpath package-info or imply subpackage inheritance.

## Examples: advisory input values, not prose or defaults

Recommend one annotation with a constant String array: **each string encodes one complete JSON value**. This uses ordinary [annotation element types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.1) rather than a nested annotation DSL. Strings require JSON quotes, numbers remain exact, and arrays/objects are genuine structured values. For example, one encoded `["a","b"]` yields `examples: [["a","b"]]`, not two examples. JSON Schema [examples](https://json-schema.org/draft/2020-12/json-schema-validation#section-9.5) is an array of illustrative values, with no added admission assertion.

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

## Field suppression: one possible capability within D7

Keep **direct source facts**, **supported effective contributions**, and **emitted assertions** distinct. Today the generator supports a bounded direct Jakarta numeric subset; custom/meta/composed/repeatable constraints can be retained as descriptions without becoming hard assertions. A supported contribution can originate from a source annotation produced by approved propagation; suppression must not depend only on the spelling physically written at the field. Future composed mappings require their own acceptance.

Recommend a separate FIELD-only marker, provisionally `@NoConstraintProjection`, suppressing **all supported declaration-derived hard assertions contributed for that effective field**. This policy follows the declaring source through inherited public contracts. It applies after supported contribution selection, before assertion placement. A child source's own constraints inside a referenced definition remain independent; no recursive subtree erasure is implied. Initially qualify ordinary scalar fields; do not promise relationship-depth behavior without a concrete mapping.

All-versus-selective is consequential. All is explainable without exposing annotation class names, JSON keywords or contribution registries, and includes newly supported declaration contributions later; that forward effect must be documented. Selective suppression needs stable identities for direct/propagated/composed contributions and conflict rules, with no demonstrated need yet. Do not remove every `minimum`/`maximum` keyword from the final schema: machine bounds may use those same keywords.

Preserve numeric type/machine limits, enum/shape restrictions, admission, naming/exclusion, construction-key requiredness, null/omission rules and all runtime validation. Retain source facts, required completeness checks and malformed-fact diagnostics even when assertions are suppressed; this is not a metadata escape hatch. Keep descriptions with an explicit “projection suppressed; runtime constraint remains” explanation, examples and standard deprecation. No Model/setter mutation, validator disabling, normalization/repair promise, general filtering layer or new global option follows.

## Three Groovy authoring sketches for taste

**All new names below are placeholders; none is accepted, available or executable as written.** Existing `DSL`, `Builder.Method` and Jakarta annotations retain their current meaning. No package for the proposed annotations is chosen. Profile/type/package syntax is intentionally absent.

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

B — focused family (recommended direction; only examples in the first increment):

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

## Recommended first increment and decision gate

**Smallest first increment:** only the focused advisory example annotation on full field payloads and admitted one-payload operations, in today's grammar. It adds a missing capability with retained evidence and no new naming precedence. The common seam already supplies exact input positions; reuse that locality rather than making examples justify new interpreter options. Literal name/exclusion annotations are the next language candidate if dependency-free authoring is wanted; profiles, package/type scope, suppression and bridges are independently reviewable later capabilities.

Minimal transport preference: fold explicit JSON literals onto the declaring public Builder's exact field-associated getter or authored-operation declaration, once per source. Retain target/source association and advisory format version; types/signatures, selected groups and inheritance already supply the rest. Do not copy whole annotations/AST graphs or materialize a per-profile resolved descriptor. Prefer a distinct optional fact over casually adding members to existing strict generated annotations: the reader rejects unknown members. Probe the chosen route rather than claiming additive annotation defaults ensure compatibility.

Old producers can omit examples without weakening language; malformed known example facts should diagnose. Old consumers may ignore a separate advisory fact only after qualification. A future language rename/exclusion cannot safely be ignored by an old interpreter: it needs an explicit capability/completeness contract, matching consumer versions and source-aware recompile/package failure, without disturbing #972's numeric markers. Annotation defaults are applied dynamically ([JLS](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.2)); freeze intended facts at compilation. A missing optional advisory fact and a missing required language fact are different compatibility cases.

String payloads face modified-UTF-8 constant-pool limits ([JVMS](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4.7)); impose modest measured limits before considering overflow machinery. No current fact demonstrates a companion is necessary. No public runtime methods, projection SPI or dependency on a JSON mapper/provider belongs to this increment.

Maintainer decisions, in bounded order:

1. **Shape and priority:** B with D's semantic separation, and examples first; or A's bounded umbrella / naming or suppression first? Accepting the direction does not accept every later annotation.
2. **Example contract:** choose the public name; approve whole-payload field/method scope, JSON-string array, no Javadoc bridge initially, malformed-generation failure and unchanged runtime semantics. Confirm the placement/profile rules above rather than just a tag spelling.
3. **Later language contract:** are dependency-free literal naming/exclusion needed now? Choose canonical-versus-legacy conflict behavior before naming annotations; require a concrete nonidentity document before settling algorithms or root/type/package precedence.
4. **Later projection contract:** approve or decline field-local all-supported suppression separately; choose its exact name and confirm descriptions/completeness/forward-effect rules. No selective-filter vocabulary by default.

Before an ADR decision on the example contract, one **isolated, finite test-only probe** should compare a local annotation surrogate with strict retained tags: Groovy 3/4/5 API/base/derived JAR producers, deleted sources/mirrors, independent no-Jackson/no-Bean consumer, scalar/object/array and operation/relationship uses, multiline/escaping, exact numbers and duplicate/malformed JSON, two relationship uses of one child, and small/near-limit payload sizes. Snapshot exact public metadata and byte sizes; keep throwing initializers silent and compare hand-authored schema acceptance with examples stripped. Limit to these cases; no production hooks, annotation artifact changes or full implementation. Java Javadoc processing is needed only if the tag bridge remains a contender. Existing historical probes are not that missing binary qualification. This brief records the probe need and does not run or schedule it.

After approval, recommend separate issues for **input examples** and **language naming/exclusion/profiles**; split **field projection suppression** only if selected. No umbrella issue should block the delivered numeric baseline. Exact relationships/release placement remain maintainer-owned; create no issues yet. Keep the existing ADR deferral unchanged until a recommendation is accepted and its necessary probe is resolved.

## Delivery and verification

This is one local decision-brief document only. Production code, executable tests, public interfaces, accepted ADR text, current user documentation, migration navigation and `CHANGES.md` are unchanged. Tracker impact: related #959; no curation/release-gate change. No feature or decision PR is warranted before maintainer acceptance. Required checks are Markdown/fences, relative targets/anchors and diff checks; Groovy lanes are unnecessary for this document-only change. Historical qualification cited above is explicitly not a new test result.
