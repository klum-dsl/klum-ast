# #959 D7: explicit structured-input naming profiles

Date: 2026-10-25. Status: **deliberately deferred; no D7 implementation contract accepted**.
Characterization base: `9504604fb` (merged P2 characterization). Documentation reconciliation base: `ac9e73bdc`. Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959).
Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md), with
[ADR 0009](../adr/0009-jackson-interoperability.md) unchanged.

## Maintainer reconciliation — D7 deferred

The [ADR 0031 checkpoint](../adr/0031-structured-schema-input-artifacts.md#d7-checkpoint--canonical-shaping-vocabulary-deferred)
records a Klum-owned vocabulary as promising, with Jackson/Bean Validation as optional bridges rather than contract
owners. **No public annotations, naming profiles, package mechanism or examples semantics are accepted.** D7 is
intentionally deferred and **does not block the active #959 DecimalMin/DecimalMax slice** or change its assignment.
The alternatives and proposed rules below remain evidence for a later decision, not accepted requirements.

The original executable characterization is retained at
[`e41a70fff`](https://github.com/klum-dsl/klum-ast/tree/e41a70fff), including the two finite probes and the Jackson-module
characterization class. This documentation delivery adds none of those files to the current source/test trees; their
historical results below are not current production-feature qualification. The existing evidence commits remain in
branch history. No extra implementation-plan record is needed: this checkpoint and the ADR establish the deferral,
while the decision bundle below retains the unresolved choices without duplicating a new plan.

## Decision brief

Maintainer extension, 2026-10-25: assess a small Klum-owned, dependency-free structured-input/schema-shaping vocabulary.
Its canonical facts drive the interpreter and generator; Jackson/Bean Validation metadata may optionally enrich those
facts but cannot independently define the language. Assess package/type/member/root configuration and advisory examples.
This direction settles the required ownership boundary, **not** the annotation/API, precedence, storage or example design.

Recommend **C within that boundary: one explicit, versioned structured-input naming contract supplied to both interpretation and generation**.
Keep today's identity fallback as the default. Offer opt-in kebab and snake strategies with frozen Klum rules; optional
Jackson compatibility is a separate adapter concern. This recommends an authority boundary, not a public API or release.
Maintainer approval of the decision bundle below is required before any production work or implementation assignment.

The motivating problem is a YAML authoring grammar, not a completed Model's serialization convention. A generator-only
case switch makes an editor accept names that the interpreter rejects. Conversely, class-wide Jackson configuration
can encode foreign property binding that says nothing about admitted Schema-authored Builder operations. It cannot
silently become the grammar for independently parsed Maps.

| Alternative | Strength | Evidence and consequence | Disposition |
| --- | --- | --- | --- |
| A: source/class Jackson annotations are primary | Familiar Schema authoring; convenient for a Jackson-owned external property format | `JsonNaming` names a databind strategy class. Its configuration/mixins, property collection and user strategies belong to Jackson. Current public Builder facts do not retain class-wide naming intent; Model inspection or new inert compiler facts would be needed. It also leaves operation/factory scope and multi-module precedence undecided. | Viable for ADR 0009 property interoperability, or a deliberately bounded optional adapter. Incompatible as primary with the maintainer's Klum-owned authority direction; retain only independent Jackson interoperability or optional compatibility input. |
| B: exporter-only selection | Small Gradle change, useful if merely documenting an unrelated foreign format | Actual `KlumStructuredInput` has no matching option and rejects transformed input. Independent parsers do not repair that. Changing YAML keys outside the interpreter requires a second grammar-aware adapter, including keys and operations. | Reject for #959's schema of admitted structured input. An explicitly unrelated foreign-schema exporter is another product. |
| C: explicit shared naming contract | Same declaration/name resolution for runtime and schema, parser-independent and source-free | Native field/key names, literal rename/ignore facts and authored method names already survive public Builder metadata. A selected profile can operate on those facts without Jackson discovery or executing Schema code. Raw candidate preservation and compatibility still need binary qualification. | Recommended, subject to the gates below. Costs include caller configuration, versioning and cross-module collision handling. |

The original A/B/C comparison supports C for more than reuse alone. The retained test proves A's present divergence from structured
input, and Jackson's differing acronym rules rule out treating case labels as a universal algorithm. Existing
`CompiledBuilderSchema` is the common seam, but currently resolves identity names while collecting each hierarchy;
its already-resolved `entries()` map is too late for an arbitrary profile. This requires a deliberate future internal
change, not a projector-only string replacement.

## Current evidence and what it does not prove

- [#932](https://github.com/klum-dsl/klum-ast/issues/932) and
  [#529](https://github.com/klum-dsl/klum-ast/issues/529) are delivered independent capabilities. Their overlapping
  naming/admission contract is shared; parser ownership remains with the caller.
- `StructuredInputAdmissionPlan.enrichPublicContract` writes field/key native names, literal rename and ignore facts
  into [`GeneratedInput`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java).
  Public authored methods retain exact annotations. It enriches before its local collision rejection. The runtime
  [`CompiledBuilderSchema`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java)
  collects parents, source identities and literal annotations; it currently prunes name collisions per hierarchy.
- [`JsonSchemaProjection`](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java)
  uses those external names for `properties` and construction-key `required`. The current Gradle surface offers roots
  and `integralConstraints`, with no naming choice. Keyed member/keyless definitions are distinct positions.
- [`KlumStructuredInput`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumStructuredInput.java)
  exposes self-contained and explicit-rootKey `readRoot`, plus active `applyToBuilder`, with no naming option.
  [#953](https://github.com/klum-dsl/klum-ast/issues/953) is still a Factory convenience design, not an installed API.
- [P0/D1](evidence/issue-959-p0-binary-metadata.md) requires inert public Builder metadata rather than Model-byte
  fallback. [P1](issue-959-p1-integral-profile.md) adds narrowly activated integral assertions.
  [P2](evidence/issue-959-p2-candidates.md) distinguishes source identity from effective placement. Neither supplies a
  naming extension or a license to interpret annotation prose as facts.
- [`NamingProfileD7CharacterizationTest`](https://github.com/klum-dsl/klum-ast/blob/e41a70fff/klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/NamingProfileD7CharacterizationTest.groovy)
  checks actual class `JsonNaming` through the existing Jackson importer, native names through the existing interpreter,
  literal rename, ignored inputs, inherited members, authored operations and literal Map keys. Its compiled output is
  read through a resource-backed loader; it is **not** the separate-JAR/source-deleted consumer qualification.
- The [finite design oracle](https://github.com/klum-dsl/klum-ast/blob/e41a70fff/docs/implementation/fixtures/issue-959-d7/characterize_naming.py) checks proposed transforms/name indices and
  hand-authored Draft 2020-12 candidates. It imports neither Klum nor Jackson and never executes operations. It is not
  generated output, an interpreter implementation, or evidence that nondefault runtime profiles exist.

## Small canonical shaping vocabulary: proposal, not a new framework

Treat a **shaping fact** as an inert, source-associated statement about an admitted input position. A **naming profile**
is one such fact, not a Jackson strategy object. Distinguish language facts (names/exclusion/admitted positions) from
advisory projection facts (examples, source deprecation, constraint descriptions). Both consumers resolve the same
language facts; the interpreter never executes or enforces advisory examples. The term is provisional here, so this
proposal does not insert unresolved vocabulary into the root glossary.

| Minimal candidate fact | Canonical meaning | Existing optional compatibility input |
| --- | --- | --- |
| Native source/group/role and payload | Existing admitted operation/position identity | Compiler-proven Builder facts; never arbitrary property discovery |
| Literal external name and exclusion | Exact spelling or absence from structured input | Existing narrow JsonProperty/JsonIgnore literals, normalized once |
| NamingProfile declaration | Versioned built-in transform identifier, origin and scope | Whitelisted JsonNaming adapter only if explicitly selected and qualified |
| Advisory example | Literal JSON value(s), exact target/source and profile applicability | Strict Javadoc tag bridge or optional compatibility annotations |
| Existing narrow assertion/description/deprecation facts | Preserve D1/D2/P1 and separately approved P2 semantics | Optional Bean Validation/source annotations; no validator/provider linkage |

This is a minimum inventory to assess, not approval for annotations for every row, a descriptor protocol, public SPI,
or constraint/default framework. No annotation definition, production API or new module is created by this task; characterization uses existing annotations only. "Dependency-free"
means independent of Jackson, Bean Validation and parsers for the core language; it does not remove Klum's existing
runtime/Groovy contracts. An example is advisory data, never an instruction to construct, validate or initialize a Model.

Prefer small facts on the **declaring public Builder** and exact source-associated public method/getter. It already has
native/rename/exclusion/selection facts; do not duplicate types/signatures, inherited copies, full ASTs, annotation graphs
or resolved names for every profile. Retain declared profile scope/provenance and resolve caller-dependent fallback later.
A Model annotation remaining on hidden Model bytes is not sufficient for source-free consumers under D1.

Assess generated ABI/class-size cost before choosing transport: repeatable examples can exceed useful annotation sizes;
UTF-8 annotation constants and classfile limits are real, and repeated large inherited copies are avoidable. Measure small,
large and inherited examples/profile declarations in the minimal probe. A small exact-name companion is a fallback only
if a named fact cannot fit truthfully/economically on public declarations. Its version/origin/resource completeness and
missing-artifact behavior must be specified first. P0 proves no companion necessity for its corpus; it does not prove
unlimited examples fit. Do not reintroduce the retired WIRE carrier or a classpath-scanning registry.

Canonical-vs-adapter conflict needs an explicit rule. Candidate: an explicit canonical fact outranks an optional adapter
at the **same source position**, with a source-aware disagreement diagnostic; absent canonical facts may use the adapter.
Do not silently OR exclusion, pick annotation visitation order or parse descriptions to resolve conflict. Whether a
a future canonical include may override JsonIgnore, and whether contradictory adapter facts fail or merely diagnose, remain
maintainer choices. Until then preserve the existing narrow JsonProperty/JsonIgnore behavior. New code must not require
their classes at runtime or mutate Jackson's independently configured binding; existing P1 already avoids Bean linkage
at the consumer. Compiler-only adapters can capture descriptor/string literals without making foreign types core authority.

## Package/type/member/root scope: feasible retention, unresolved precedence

The [retained package probe](https://github.com/klum-dsl/klum-ast/blob/e41a70fff/docs/implementation/fixtures/issue-959-d7/probe_package_metadata.py) uses only the existing JDK Deprecated
annotation; it creates **no new annotation**. Java 17 and Groovy 3.0.25/4.0.32/5.0.6 package-info producers each emit
`family/package-info.class`. Separate Java consumers read the annotation from a JAR after producer sources/classes are
deleted, without a Groovy/compiler dependency. `family.child` does not inherit it, and a throwing Member initializer is
not triggered. This proves ordinary package-annotation authoring/retention, not a Klum package transformation hook,
custom shaping annotation propagation, duplicate package-info resolution or named-module support.

The [JDK Package contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html) associates
runtime package metadata with a class loader/code source. It is not an inheritance or cross-JAR model-family protocol.
A future Schema compiler could capture a selected exact package fact onto each declaring public Builder, including when
an annotated package-info comes from a binary dependency. This is preferable to ambient Package reflection at consumer
time, but changing package-info would require recompiling that producer and invalidating Gradle inputs. Actual Klum
AST hook/compilation-unit order and separate Java-vs-Groovy package-info discovery need the minimal probe. An unrelated
package-info from another artifact must not be adopted because its package string happens to match.

| Scope | Plausible meaning | Unsettled inheritance/ownership question |
| --- | --- | --- |
| Member | Exact canonical rename/exclude; possibly naming override for that member token | Is a member naming strategy useful beyond literal rename? It must not implicitly choose a child's entire profile. |
| Type | Default for declarations owned by that type | Does a derived type alter inherited declarations or only its own? Recommend original declaration ownership unless explicitly choosing a root-wide override. |
| Package | Default for types declared in that exact package | No implicit subpackage inheritance; a whole family spanning packages/modules needs explicit repeated defaults or root configuration. Reject conflicting duplicate package-info origins rather than classpath-order selection. |
| Caller/root | Document-wide profile or fallback | Is it a uniform override or lowest-priority fallback? Those are different public promises. |

Candidate precedence **member rename/exclude > type > package > caller/root** is coherent only when caller/root is a
fallback. It allows a model family to publish conventions, but a caller selecting kebab can still get camel nested
contracts. A simple root marker saying "whole graph kebab" would then be false. Across-module dependencies would own part
of the grammar, so reproducibility requires inert declaring-scope provenance and per-contract resolution.

Compare three models before choosing:

| Model | Benefit | Tradeoff / required rule |
| --- | --- | --- |
| Uniform explicit root override; member literal names/exclusions remain final | One predictable document grammar across modules; matches the initial C recommendation | Type/package settings can only supply defaults when no root override exists; root absence and explicit identity must be distinguishable |
| Type/package defaults override caller fallback (candidate chain) | Schema family can retain intended conventions while callers provide a base | Mixed profiles are expected; marker must report scope policy and effective per-contract/profile origins; inherited source policy must be frozen |
| Locked family convention; incompatible caller request errors | Makes an externally owned grammar explicit | Adds locking/conflict vocabulary and binary propagation complexity; no Schelm requirement currently justifies it |

Do not decide by annotation proximity alone. Need fixtures where root/package/type all disagree, a derived class lives
in a different package/JAR, one child is reached by two differently configured relationships and package metadata is
missing/duplicated. No annotation on a Java interface is automatically inherited merely because generated Builders form
an interface hierarchy. Decide scope using compiler-recorded declaration identity, not reflection's getAnnotations order.
The remainder's root-wide selection describes the **first model as a candidate**, not accepted precedence. The type/package
alternative changes that section and its proposed marker; it is not an extra implicit override layered on top.

## Examples as first-class advisory facts

JSON Schema's [examples keyword](https://json-schema.org/draft/2020-12/json-schema-validation#section-9.5) contains an
array of example values. It does not insert values, create defaults or assert validity. Canonical examples should describe
the admitted input at an exact position, not a completed Model export. They must not execute validators, initializers,
closures, Groovy evaluation, factories, provider discovery, Templates or lifecycle.

| Authoring choice | Source-free availability | Cost / assessment |
| --- | --- | --- |
| Small canonical annotation with constant literal payload | Java/Groovy can author constant String values; compiler can freeze FIELD-only facts onto public Builder with provenance | Strong explicit target/grammar; requires new reviewed annotation/ABI and class-size qualification. Candidate preferred for machine facts, not implemented here. |
| Strict `@example` Javadoc tag | Ordinary comments are not classfile metadata; AnnoDocimal can embed retained text | Familiar and readable; tag grammar/escaping/order/malformed handling and Java compiler setup must be proven. Prose alone is not safe machine data. |
| Bridge both into one canonical fact | One projector/placement policy, optional documentation convenience | Needs deterministic duplicate/conflict/provenance rules; do not build two competing renderers or make JavaDoc a runtime prerequisite. |

The D7 characterization confirms `@example "catalog"` on a Groovy Schema field and `@example {"rawKey":"catalog"}`
on an authored operation survive on their **public generated declarations** as AnnoDocimal strings in G3/G4/G5. It does
not prove Java source Javadoc processing, all type/relationship/array sites, exact multiline preservation, or a supported
tag parser. `KlumDocumentation` already uses an immutable documentation model with arbitrary tags; the current projector
retains prose, not typed examples. Generated prose duplication must not be counted as distinct authored examples.

Minimal candidate literal grammar: one complete strict JSON value per example, UTF-8 text, finite exact JSON numbers,
booleans/null/strings/arrays/objects only, duplicate object keys rejected, bounded length/nesting and no interpolation.
Prefer String/constant-expression authoring over Java annotation primitive unions that cannot represent object/array/null
uniformly. Parse inside optional tooling or at compile time with an approved non-executing reader; core can carry inert
literals and provenance without a JSON/YAML parser or arbitrary Groovy evaluation. Compiler constant folding is not
permission to read runtime static fields. Preserve exact numeric semantics; never convert large numbers through double.
Java/Groovy annotation and Javadoc authoring need literal escaping tests before API selection.

An annotation example and a Javadoc example can be either additive ordered examples or canonical annotation replacing
tags at the same target. Recommend additive source-order examples with exact normalized-JSON deduplication and retained
origins; conflicting example values are alternatives, not competing defaults. Contradictory target/profile declarations
still diagnose. The maintainer must choose the merge rule. Invalid literal syntax is a metadata diagnostic; decide
fail-vs-omit for malformed optional examples explicitly, without disabling ordinary interpretation or manufacturing data.

| Placement | Proposed target / guard |
| --- | --- |
| Scalar/property or one-payload operation | Its effective external `properties` value schema; operation example is one payload, not inferred effects or named-argument spreading |
| Type/root object | That contract's appropriate member/keyless body definition; a required-key example cannot be reused blindly for keyless positions |
| Whole array vs element | Array-value example `[["a","b"]]` belongs in array `examples`; element example `["a"]` belongs under `items.examples`. An explicit target fact is needed; no guessing from JSON shape. |
| Parent relationship use | Sibling of its `$ref` for whole child payload; never add a parent's example to shared child `$defs` or ordinary siblings |
| Keyed owned Map | Whole map examples at outer member, child examples under value `additionalProperties`; literal instance names remain data |
| Excluded/unsupported member | No invented input position; report omission with original source rather than moving it to a similar name |

Object examples are profile-sensitive. Do **not** recursively rename their keys: some keys are declared members and others
are literal map/instance data. Candidate: scalar examples are profile-independent; an object example records its authored
profile and key position, and emits only for a matching effective grammar (otherwise source-aware advisory omission).
An alternative is native-source-key examples resolved structurally, but that needs a grammar-aware translator and more
metadata; no current evidence justifies it. Decide this before claiming examples work across profiles. Optional static
schema checks may flag incompatible examples without executing user code; such checking is not runtime validation and
cannot prove lifecycle success. Example removal/addition must leave instance admission unchanged.

## Proposed v1 transformation contract

Candidate canonical profile identifiers are `identity-v1`, `kebab-v1`, `snake-v1`. These are proposed identifiers, not current
configuration. UI spellings `identity`/`camelCase` may select the same identity profile; there is no separate camel
normalizer. Identity preserves `URLValue`, `PascalCase`, underscores and Unicode exactly; it does not lowercase a first
letter or infer JavaBean naming. Both separated strategies start from the **native Schema declaration name**, never the
getter, generated population method, already external name or completed Model property inferred by Jackson.

For kebab/snake, inspect original Unicode scalar values left to right, with ASCII predicates only:

1. Before an ASCII uppercase letter, insert `-` or `_` when its predecessor is an ASCII lowercase letter or digit.
2. Also insert before an ASCII uppercase letter whose predecessor is ASCII uppercase and successor is ASCII lowercase.
   This makes the last capital in an acronym the start of the following word.
3. Replace ASCII `A..Z` with `a..z` by the fixed numeric offset. Copy everything else unchanged.

Digits remain attached to the preceding token; no letter-to-digit or digit-to-lowercase split. Non-ASCII letters,
combining marks, supplementary characters, `$`, `_`, `-`, slash and tilde are copied as opaque characters and break ASCII
adjacency. Do not trim, collapse separators, transliterate, normalize NFC/NFD, apply locale rules, or perform Unicode case
folding. An unpaired surrogate is invalid profile input and must fail with declaration context rather than replacement;
normal compiler identifiers do not contain it. Explicit literal external names retain existing handling. This deliberately
limited rule is reproducible across JDK/Groovy Unicode table versions. A future fully international case strategy would
need a distinct version and fixtures, not an in-place algorithm update.

| Native name | identity-v1 / camel | kebab-v1 | snake-v1 |
| --- | --- | --- | --- |
| `serviceName` | `serviceName` | `service-name` | `service_name` |
| `URLValue` | `URLValue` | `url-value` | `url_value` |
| `myURLValue` | `myURLValue` | `my-url-value` | `my_url_value` |
| `XML2Parser` | `XML2Parser` | `xml2-parser` | `xml2_parser` |
| `version2URL` | `version2URL` | `version2-url` | `version2_url` |
| `_privateURL` | `_privateURL` | `_private-url` | `_private_url` |
| `already_snake` | `already_snake` | `already_snake` | `already_snake` |
| `naïveURL` | `naïveURL` | `naïve-url` | `naïve_url` |
| `ÅngströmURL` | `ÅngströmURL` | `Ångström-url` | `Ångström_url` |
| `İDValue` | `İDValue` | `İd-value` | `İd_value` |
| `e` + combining acute + `URL` | unchanged | `e` + combining acute + `url` | same as kebab |

The Unicode restriction is a maintainer choice, not a claim of universal linguistic correctness. Literal renames remain
the escape hatch for precise externally owned spelling.

### Jackson reuse and compatibility

[Jackson's 2.20 implementation](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/util/NamingStrategyImpls.java)
has different snake/kebab algorithms. The actual resolved test dependency confirms:

| Input | Jackson snake | Jackson kebab |
| --- | --- | --- |
| `URLValue` | `urlvalue` | `url-value` |
| `myURLValue` | `my_urlvalue` | `my-url-value` |
| `_privateURL` | `private_url` | `_private-url` |

These observations do not establish all Jackson versions. Its
[`JsonNaming`](https://github.com/FasterXML/jackson-databind/blob/2.20/src/main/java/com/fasterxml/jackson/databind/annotation/JsonNaming.java)
selects a strategy class; instantiating a custom strategy may execute user code. Core cannot directly invoke that API
without databind linkage. Copying its algorithm would require license review and a frozen, separately named semantic
contract; it would not establish ordinary Jackson property-resolution parity. Prefer the small independently specified
ASCII rule here, with no copied Jackson implementation.

Optional Jackson integration may later supply a Jackson naming strategy that delegates to the frozen Klum rule, or map a
specifically qualified built-in Jackson strategy to a **distinct** compatibility profile. Neither loads arbitrary strategy
classes in the non-executing generator. No automatic class annotation/mixin/mapper detection, scanning or provider SPI.
Full importer parity remains unclaimed: aliases, access rules, coercion, codecs and property discovery differ even if
names happen to match. Existing JsonNaming/JsonProperty/JsonIgnore handling in ADR 0009 remains independently supported.

## Resolve once, then place by source and position (root-wide candidate)

For existing optional Jackson literal compatibility inputs, proposed precedence preserves current behavior: explicit `JsonIgnore(true)` excludes; otherwise nonempty literal `JsonProperty` wins **unchanged**;
otherwise transform the native name with the selected profile. Empty JsonProperty means fallback, JsonIgnore(false)
does not exclude, and a rename adds no alias. No naming setting can re-admit a source excluded for role, unsupported
payload, ownership or explicit ignore. Keep current narrow handling of these annotation literals; do not acquire
JsonProperty access, JsonAlias, views or other Jackson behavior accidentally.

| Contract position | Naming policy |
| --- | --- |
| Generated property, population overload group, owned relationship | One native field, one external name; never transform adders/getters separately |
| Authored eligible `Builder.Method` | Transform exact authored method name unless explicitly renamed; one Map/List remains one payload |
| Ordinary Map/operation-payload keys; keyed-map instance names | Literal data, never transformed, including nested ordinary Maps |
| Enum values, scalar strings, rootKey value, fixed/derived child key values | Literal values, never transformed |
| Actual construction-key member name | Same strategy/rename/ignore resolution as other declarations, but preserve position-specific admission |
| Binary inherited field/key/operation | Use declaring source facts under this root's selected profile; no nearest-class override |
| Nested owned contract, recursive reference | Same root profile at every reachable admitted contract |
| CollectionFactory/custom Factory/zero- or multi-argument scope | Still outside current grammar; naming does not admit it. Future #952 scope grammar must name admitted operation tokens with the same contract, leaving selectors/data literal |
| Native Factory Java/Groovy methods, root binary names, artifact file names, `$defs` contract identities | No case conversion |

For keys, the self-contained root and caller-key direct children require the resolved key-member name; explicit-rootKey,
active apply target, keyed-map child and Schema-provider bodies forbid the actual key member just as today. Do not rename
a provider's key value, derive identity from a filename, or allow an alias to bypass forbidden-key detection. An ordinary
operation with the old native key spelling remains an independent operation if admitted; spelling alone is not identity.
Member/keyless `$defs` remain separate; a collision matters only where both entries are admitted in the same input scope.

The resolver needs stable declaration identity, native token, literal rename, exclusion cause, public signature/group and
payload facts **before** name-collision filtering. Resolve names per selected profile and then build an immutable exact
external-name index, retaining reverse provenance from source to resolved token for overlays/diagnostics. No inverse
case conversion, case-insensitive lookup, retry under identity, or pre-parser recursive key rewriting. Many-to-one
transforms are not reversible. Input order and complete preflight retain their current semantics.

Check collisions after ignore/rename/strategy and inherited merge: field/field, operation/operation, field/operation,
explicit/fallback and construction-key/operation. Grouped population methods for the same field are one candidate;
overloaded authored methods retain existing ambiguity policy. Name equality is exact, with no Unicode normalization.
For **nondefault profiles**, recommend failing the affected selected root's contract before any allocation/invocation
or artifact replacement on any collision in reachable admitted scopes, rather than silently publishing a smaller grammar.
Identity keeps today's exclusions and diagnostics unchanged. This stronger nondefault failure policy needs maintainer approval.

An identity-name collision is a name-resolution result, not an unsupported-source role. Re-evaluate candidates under the
selected profile: a literal rename equal to another source's native name can become distinct when only the fallback is
transformed. Do not let cached parent/identity collision pruning hide those candidates, and do not resurrect unrelated
role exclusions. Qualify current artifacts for all required provenance; if they are insufficient, explicit nondefault
selection must fail with source/artifact and recompile guidance. No Model-byte recovery or guessing from a rejection's prose.

Diagnostics include root/profile/version, opaque source name and escaped input pointer where available, resolved token,
native token, rename/strategy origin, declaring Model/source and public Builder signature, both colliding declarations,
and artifact/recompile advice for missing facts. Sort collision participants deterministically, without reordering input.
Unknown-profile failure occurs at configuration/preflight; no fallback to identity. Parser duplicate keys remain the
parser's responsibility; a Map has already lost duplicate spellings.

## Root-wide selection candidate and optional modules

Under the uniform-root candidate, the integration owner selects one immutable profile per root document / application call. The Schema owns explicit local
rename/exclusion; a dependency does not select the root's format. One profile applies consistently to all reachable owned
contracts, including those from independent JARs and inherited declarations. No child class setting silently overrides it.
Consumers needing a fixed foreign spelling use literal Schema renames or the independently configured Jackson importer.

Proposed Gradle location: `klumSchema.jsonSchema.namingProfile = 'kebab-v1'`, alongside `rootTypes` and independently of
`integralConstraints`. This would be an execution-time tracked input passed as an inert JDK string across the isolated
loader boundary. One task profile covers its roots; generating the same root in two profiles requires separately
configured output ownership/tasks, not silent overwrite. Exact multi-task convenience is deferred. Profile/algorithm
changes invalidate caching. A new incompatible profile/version must fail on old tooling rather than weaken output.

For runtime, recommend additive overloads on **all three existing call shapes**, taking an explicit immutable profile
or options value as a final parameter. Existing overloads delegate to identity. The exact type/name remains a maintainer
API decision; no ambient system property, thread-local, parser/extension inference or annotation-derived default. Both
readRoot key modes and applyToBuilder must carry the same selected grammar. The latter returns the same active Builder;
profile selection describes this application, not mutable state stored on the Builder. Repeated calls can deliberately
select profiles but gain no layering/alias/merge promise.

Illustrative Java-first client shape, **not an installed API**:

```java
// Proposed overload; InputNaming and its spelling are decision placeholders.
Deployment model = KlumStructuredInput.readRoot(
        Deployment.class, parsed, "deployment.yaml", InputNaming.KEBAB_V1);
KlumStructuredInput.applyToBuilder(activeBuilder, parsed, "overlay.yaml", InputNaming.KEBAB_V1);
```

Corresponding proposed Groovy call:

```groovy
def model = KlumStructuredInput.readRoot(Deployment, parsed, 'deployment.yaml', InputNaming.KEBAB_V1)
```

For the Schema's feel, existing syntax remains sufficient:

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class Deployment {
    String serviceName
    @JsonProperty('httpAPI') String publicEndpoint
    @Builder.Method void configureHTTP(Map<String, String> payload) { serviceName = payload.nativeMapKey }
}
```

Under proposed kebab, operation tokens are `service-name`, literal `httpAPI`, and `configure-http`; `nativeMapKey` remains
a literal Map payload key. No change to authored Groovy DSL names or Model API follows.

Any eventual #953 FromStruct convenience must pass an explicit profile to this same interpreter and preserve identity
for omitted configuration; it must not infer naming from a YAML extension, use a separate resolver or acquire a parser
dependency. Core/runtime contains only the small frozen rules and its private name-resolution seam. Jackson/databind,
YAML parsers and optional adapters remain outside core; the generator requires neither parser nor Jackson adapter.
No public profile SPI or arbitrary callbacks are justified for v1.

## JSON Schema identification and metadata placement

For explicitly selected nondefault profiles, propose a top-level annotation:

```json
"x-klum-input-naming": {"profile": "kebab-v1", "scope": "root-graph"}
```

Include a human description of the chosen grammar. The version is in the profile identifier. If type/package precedence wins instead, replace `root-graph` with the approved scope policy plus effective per-contract profile/provenance; a single root profile claim would be misleading. This is advisory metadata
for tooling that chooses to inspect it; JSON Schema does not convert instance keys or configure a reader. Validation is
performed by actual `properties`, closed-object rules and construction-key `required`, not this extension. The standard
[extension rules](https://json-schema.org/draft/2020-12/json-schema-core#section-6.5) allow unknown keywords; do not claim a
new validation vocabulary, use `format` for renaming, change `$schema`, or put a profile marker into the input document.

Keep identity/default artifacts byte-for-byte unchanged, including absence of this marker. Tools treat absent marker
as the existing identity contract for Klum artifacts; arbitrary third-party schemas have no such inference. The marker
does not attest parity to a runtime configuration. Callers must select the same profile; an unknown runtime profile
fails. Removing the marker must leave standard validation results unchanged. `$defs` names remain public Builder binary
identities within each root artifact; any internal definition/contract caches include profile and key position so one
root's choice cannot contaminate another root or generation run.

Names are resolved before P1/P2 metadata is placed. Bounds/text/deprecation attach by original declaring source and
input position, never by searching for a string that resembles a property. `integralConstraints` remains orthogonal;
naming cannot enable decimal/Size/Pattern/default mappings or change null/omission/machine-bound semantics. Authored
operation payloads do not acquire bounds from effects on similarly named Model fields.

For relationship-local defaults/constraints/deprecation, resolve both parent-use and child target through the selected
profile, retain literal child renames, and report excluded/missing targets. Parent metadata belongs beside that use's
`$ref`, under its own nested property, or under keyed-map value `additionalProperties` as appropriate; never mutate the
shared child `$defs` with a parent's metadata. A deprecated source remains advisory under any spelling. P2's deprecation
proposal is historical at the characterization base. The reconciliation baseline includes merged #968 and its separately
approved advisory deprecation implementation. D7 neither extends that work nor settles D3–D6/default policy.

## Executable fixture matrix and evidence boundaries

Every future executable class uses `Test` and `@Issue('959')`; a delivered DSL feature additionally needs documentary
`@Tag('documentary')` and `@See`, matched user pages, migration navigation and changelog. This design accepts no public
feature and does not revise those release-facing files.

| Fixture / executable owner | Retained characterization / existing fixture | Required future nondefault acceptance |
| --- | --- | --- |
| `NamingProfileD7CharacterizationTest` in Jackson module, G3/G4/G5 | Current class JsonNaming diverges from structured names; inherited/explicit/ignored names, operations, data keys; actual Jackson acronym comparison | Qualify optional adapter in its own module; retain independent importer behavior and no core databind dependency |
| `characterize_naming.py` with pinned jsonschema | 21 exact vectors across all three profiles, precedence, finite collisions, unknown profile, hand-authored closed-schema acceptance, source index, key names and parent-local deprecation | Same vectors against production resolver and emitted artifacts; include Turkish/default locale variants, supplementary and malformed UTF-16 JVM strings |
| `CompiledBuilderSchemaTest` + new D7 binary fixture | Existing native contract/metadata binary tests; no nondefault feature claim | Separate API/base/derived/child JAR producers for G3/G4/G5, delete sources/mirrors/compiler outputs before consumer extraction; no compiler/Jackson/Bean provider on consumer runtime; native/rename/ignore, diamond inheritance, source identity and pre-collision candidate preservation |
| `StructuredInputDocumentaryTest`, key documentary tests + D7 runtime tests | Existing identity readRoot/apply paths | All three profile choices across self-contained/explicit-key readRoot and apply; same Builder identity, nested/root key positions, maps/fixed keys, preflight trap controls, operation order and cause/path preservation |
| `KlumJsonSchemaGenerationTest` packaged consumer | Existing G3/G4/G5 binary generator and cache fixtures | Same three profiles emitted from independent binary producers; validate emitted schema with existing external harness, exact agreement on named structural inputs and expected stricter opt-in P1 bounds, byte-identical outputs per producer generation |
| Collision/alias fixture | Finite candidate name-index assertions only | URLValue/urlValue; inherited field/custom operation; explicit rename/fallback; key/operation by member/keyless position; no native alias, no last-wins, no source exclusion resurrection, deterministic failure before execution/output replacement |
| P1/P2 placement fixture | Candidate child bound and parent deprecation assertions; historical P0–P2 evidence | Integral on/off × naming profile, source-deprecation facts when delivered, two parent relationships to one child, rename/exclusion, Map value placement and no global `$defs` leakage; preserve future default gates |
| Gradle/JPMS/no-execution fixture | Existing binary and loader contract evidence only | Profile input invalidation, relocation/configuration-cache/build-cache reuse, stale output removal, unchanged old default snapshots, unknown version/missing metadata failures preserving output; G4/G5 public named-module inspection without opening hidden implementations; static/enum/constructor/callback traps plus separate positive controls |

The proposed public overloads must also compile for Java and static/dynamic Groovy 3/4/5 callers, preserving every old
method descriptor. Run focused G3 iterations first, then all affected G3/G4/G5 lanes at completion. The plugin's single
Spock runner must exercise independently compiled producers for each Groovy version; merely running one algorithm test
under three runtimes is not binary interoperability evidence. Schema validation does not promise lifecycle or final
validation success, and enhanced P1 rejection intentionally need not equal interpreter structural admission.

## Small staged plan, conditional on decisions

These are design stages, **not opened implementation slices or automatic follow-up assignments**.

1. **Freeze semantics and prove metadata adequacy:** settle the canonical fact inventory, scope precedence and examples decisions, then approve D7 vectors, collision/default/Unicode choices and stakeholder requirements. Probe source-free public contracts before and after hierarchy name filtering; add only
   irreducible inert provenance if required, with an explicit missing-version policy. Exit: binary/no-execution evidence
   proves the selected contract can be resolved without source, Model bytes or Jackson.
2. **One vertical profile path:** after separate assignment and the two minimal decision probes below, add frozen rule/resolution internally and the approved
   additive runtime selection, then pass the identical inert profile through Gradle to projection. Deliver one nested
   operation/key/binary example end-to-end before broadening cases. Preserve old defaults, lifecycle and parser ownership.
3. **Qualify and document:** complete the matrix, emitted-schema validator corpus, cache/ABI/error cases, G3/G4/G5 and
   matching documentary/user/migration/changelog evidence. Review the source-to-input placement before publication.
   Optional Jackson adapters and FromStruct convenience require their own justified assignment; neither blocks core parity.

## Maintainer decision bundle before code

- **Canonical boundary/storage:** maintainer direction requires Klum-owned facts; approve the minimal fact inventory, optional-adapter conflict policy and public-Builder placement after size/ABI probing. A companion needs demonstrated necessity.
- **Strategy semantics:** approve identity/camel as unchanged spelling, ASCII-only kebab/snake, acronym/digit/punctuation
  rules and opaque Unicode; otherwise select another explicit versioned algorithm before coding.
- **Precedence/root ownership:** choose uniform root override versus member > type > exact package > caller fallback, declaring-type versus effective-type inheritance, explicit identity versus absence, and duplicate/missing package facts. Preserve literal data/instance keys and decide canonical-vs-Jackson rename/exclusion conflicts.
- **Failure/compatibility:** approve fail-closed nondefault collision handling, per-profile collision re-evaluation,
  required-metadata gate if needed, unchanged identity output/ABI and separate key-position checks.
- **Examples:** select annotation/tag/bridge, strict literal syntax, merge/error policy, exact type/member/array/relationship targets and profile/key-position applicability. Approve advisory-only semantics; no parser or evaluator enters core.
- **Selection and metadata:** choose immutable runtime parameter/type and exact Gradle property; approve truthful advisory marker,
  no input envelope, no profile auto-detection and no custom callback/SPI in v1.
- **Stakeholder fit and scheduling:** obtain explicit maintainer confirmation after Schelm evidence; decide whether/when
  to assign the bounded vertical stage. This task commits to no release, milestone or implementation work.

## Stakeholder consultation

Schelm Hive was found and consulted directly on 2026-10-25. It reports native camelCase in observed YAML, with
ModelFileLoader using YamlSlurper + KlumStructuredInput rather than ObjectMapper. Preserve identity by default; there is
no verified requirement yet for alternate root naming profiles or Jackson input/output parity. Do not transform literal
map/instance keys or values. Its recommendation favors shared optional profile selection across interpreter/generator and
module boundaries with explicit renames retained. These are stakeholder observations/recommendations, not universal policy
or maintainer approval. The maintainer explicitly asked this design to retain that feedback.

If alternate-profile or precedence demand needs confirmation: please supply one representative Application Package YAML and one environment
YAML, identify any fixed Supplier/target-contract spellings, and say whether Jackson interoperability is intentionally
asymmetric. Keep Application Package, Supplier Recipe and Operations Environment ownership distinct. Confirmation remains
pending until the maintainer answers; no consumer-specific naming default is chosen here.

## Minimal future probes before an API decision

1. **Canonical facts and precedence:** use existing annotation surrogates/test-only fixtures until annotation design is
   approved; exercise package-info Java/Groovy, transformed type/member facts, API/base/derived/child JARs, deleted sources,
   same-package different-artifact conflicts and an absent package-info. Snapshot exact public metadata and measure
   classfile size. Compare both precedence models with explicit identity and omission, without changing admission.
2. **Examples:** characterize Java plus Groovy AnnoDocimal annotation/tag retention at type/member/parameter/relationship
   and array/item sites, binary inheritance and large/multiline literals. Hand-build candidate examples schemas, check
   metaschemas and unchanged admission when examples are stripped; test exact numbers, malformed JSON/duplicate keys,
   profile mismatch and member/keyless positions. No user-code invocation or new annotation/API without approval.

These are bounded decision probes, not implementation issues or authorized follow-on work.

Historical reproduction (JDK 17, repository root at `e41a70fff`, which contains the retained fixtures):

```shell
./gradlew :klum-ast-jackson:test --tests '*NamingProfileD7CharacterizationTest' \
  :klum-ast-jackson:groovy4Tests --tests '*NamingProfileD7CharacterizationTest' \
  :klum-ast-jackson:groovy5Tests --tests '*NamingProfileD7CharacterizationTest' \
  :klum-ast-jackson:licenseTest --no-parallel --max-workers=2
python3 -m venv /tmp/klum-d7-venv
/tmp/klum-d7-venv/bin/python -m pip install jsonschema==4.23.0
/tmp/klum-d7-venv/bin/python docs/implementation/fixtures/issue-959-d7/characterize_naming.py
python3 docs/implementation/fixtures/issue-959-d7/probe_package_metadata.py \
  /path/to/jdk17 /path/to/groovy-3.0.25.jar /path/to/groovy-4.0.32.jar /path/to/groovy-5.0.6.jar
```

The package probe uses a temporary directory and deletes it on completion; it uses only existing JDK annotations.
The candidate oracle requires the pinned validator and produces no checked-in schema output.

## Historical validation and documentation delivery

At the retained characterization revision, focused JDK 17 tests passed ten cases per G3/G4/G5 lane. The full affected
Jackson module passed 131/129/129 cases respectively, with no failures/errors/skips; its license check passed. The
finite naming/schema/example oracle passed 137 checks with jsonschema 4.23.0. Java/G3/G4/G5 package-info binary probes
passed with deleted producer sources/classes, silent inspection and separately activated initialization controls.
These results do not qualify future profile APIs, canonical annotations or a generator change.

The resumed delivery changes only this evidence record and ADR 0031 relative to `ac9e73bdc`. Current production code,
tests, JSON Schema output, interpreter behavior, user documentation, release notes and release/milestone policy are
unchanged. Markdown/link/diff checks apply; Groovy lanes are not required for this documentation-only reconciliation.
Independent worker-owned Standards and Specification review results and publication links belong to the PR handoff.

Tracker relationship: **Related: #959**; issue #959 remains open. The tracker comment records D7 deferral and explicitly
leaves the active decimal-bounds assignment unblocked. Curation impact: none; no release gate, owner, dependency or
acceptance state is changed. The only accepted D7 result is deliberate deferral, not adoption of any proposal above.
Hive reconciliation is requested after delivery; no merge or archive action is authorized here.
