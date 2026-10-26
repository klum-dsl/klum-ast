# #959 D7: external naming and exclusion decision brief

Date: 2026-10-26. Status: **Proposal for maintainer review; no language decision or implementation accepted.**

Audited base: `3daea832e4e6fa9247c6ab8cb7e53f61da5772bb` (`origin/master`, merged
[PR #974](https://github.com/klum-dsl/klum-ast/pull/974)). Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959).
This is the next independent language-shaping discussion after `ConstraintProjection`. Numeric projection, field policy
and deferred examples retain their existing decisions. No implementation, tracker change or release placement follows.

## Recommendation and decision boundary

**Keep native names plus existing literal `JsonProperty`/`JsonIgnore` support as v1. Postpone additional naming annotations.**
This is sufficient for the inspected Schelm input and for occasional exact external spellings. It is not sufficient for
an author who needs external names while prohibiting Jackson annotations in the Schema producer. That requirement is
plausible, but neither the issue history nor the inspected consumer establishes it as a current blocker.

Retain the accepted **focused Klum annotation family** direction. It governs the shape of additions when justified;
it does not require implementing every family member now. If dependency-free naming authoring is required now, choose
only separate literal name and exclusion annotations as the next slice. Do not make a naming profile a prerequisite.
This recommendation narrows timing, not the accepted ownership of the contract: Jackson remains an optional input to
Klum's shared grammar, not its definition. Maintainer confirmation remains necessary.

Seek a **deep module** at the existing compiled-contract **seam**: one source-associated name/admission implementation
serves interpreter and generator. Keep selection, inheritance, collision handling and metadata decoding local to that
module; do not expose them as a public descriptor or configuration framework.

## Current facts versus hypotheses

| Verified fact at the audited revisions | Evidence / limit |
| --- | --- |
| Both consumers obtain names/admission from `CompiledBuilderSchema`. Runtime uses exact name lookup; generation emits those entries as `properties` with `additionalProperties: false`. | [Reader](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java), [preflight](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java), [projector](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java). This is common structural grammar, not equivalence of all value validation or lifecycle outcomes. |
| Existing precedence is `JsonIgnore` absent-value/true, then a nonempty literal `JsonProperty`, then the native field/method name. Empty rename falls back; false ignore does not force inclusion. | [Compiler `externalName`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java), reader's exact annotation-byte selection. Other Jackson members such as aliases/access are not this language. There is no native-name alias introduced by a rename. |
| Names attach to declaring source identity for fields, construction keys and admitted one-payload authored operations. Renaming cannot admit an otherwise unsupported declaration. | [Generated facts](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java), compiler enrichment and reader collection. Source field metadata is not inferred from generated setter spelling. |
| Ordinary collisions remove conflicting entries and retain sorted source rejections; previously rejected parent sources remain rejected in descendants. Required construction-key collisions fail in member-key positions. | Reader `admitted`/`collect`/`keyCollisions`; [compiled-contract tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/CompiledBuilderSchemaTest.groovy). Do not describe today's behavior as universal whole-root failure on any collision. |
| Self-contained roots/direct keyed children differ from externally keyed roots, existing Builders, fixed-key children and keyed Map values. | [Current construction-key grammar](../user/Structured-Input.md#construction-keys). An ignored required key makes that input position unavailable; exclusion does not remove the identity requirement. |
| Public Builder facts permit source-free inherited/cross-module inspection. Field/key literals are compiler-folded; authored operations retain selected exact annotations. | Reader, generated facts, compiled-contract tests and [packaged generator tests](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy). Tests exist for deleted producer sources/compiler outputs and initialization traps; this design task does not rerun or extend them. |
| There is no current profile argument on the three interpreter call shapes, or naming selector on the generator. Broader `JsonNaming` is deferred. | [Public interpreter](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumStructuredInput.java), [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#d7-vocabulary-direction--959). The ADR's earlier general naming paragraph is direction; its limited tracer and later checkpoints determine delivered scope. |
| Managed Jackson import independently supports mapper naming strategies and a richer binding contract. | [ADR 0009](../adr/0009-jackson-interoperability.md#jackson-customization). Its four modes, annotations/configuration and output behavior must retain independent semantics. Narrow structured-input literals do not promise round trips or importer parity. |

**Consumer evidence refreshed read-only:** the inspected Schelm checkout is clean at
`553fdd42fb3cf82ba66c9588df28005ff58a7c84`. Its `schelm-skeleton/schelm-core/api/.../ModelFileLoader.groovy` parses through
`YamlSlurper` and applies Maps to allocated Builders through `KlumStructuredInput`. Current application/environment YAML
uses native member names such as `kafkaDeployments`, `consumerGroupName`, `topicResourceNames` and `credentialSecret`.
Outer instance keys such as `test`, `prod` and `order-service`, and data keys such as `app.kubernetes.io/part-of`, coexist
with those names. No `JsonProperty`, `JsonIgnore` or `JsonNaming` usage was found in that snapshot's Java/Groovy sources.
This verifies current spelling and loader choice, not a permanent user preference or a requirement for Jackson parity.
It corroborates the [earlier consultation](issue-959-d7-naming-profiles.md#stakeholder-consultation).

| Candidate use case | Need horizon / workaround / conclusion |
| --- | --- |
| Schelm Model Writer edits existing application/environment YAML with generated completion. | Current consumer task; identity works. No demonstrated need for a new naming mechanism. Preserve Application Package, Supplier Recipe and Operations Environment ownership. Kubernetes output names are a separate renderer concern. |
| Schema Developer must match a few externally fixed tokens. | Supported by current literal bridge; Jackson annotations are a viable workaround if allowed in the producer. Annotations do not require ObjectMapper or databind in core. |
| Schema producer must publish exact names without any Jackson annotation dependency. | Identified architectural possibility; immediate demand unknown. Workaround: use native names or optional Jackson annotation input, if organizationally acceptable. If neither is acceptable, focused Klum literals have direct value. |
| Integration owner needs one alternate casing across independent binary dependencies. | Speculative for the inspected consumer. Repeated explicit literals are viable but potentially costly; cost requires a representative document rather than an assumed profile mandate. |
| Same document must work under both Jackson and structured input. | Unknown requirement. Use a bounded common subset only if explicitly requested; mapper-wide parity is not delivered by name conversion. |

Hypotheses, not requirements: a package convention is necessary; every dependency must follow the caller's casing;
camelCase means normalization rather than unchanged spelling; a Jackson strategy has the desired acronym rules; a
foreign-format input must match Kubernetes output; old metadata can safely imply absence of a new exclusion policy.

## Three compact Groovy authoring alternatives

**A — available today; recommended v1.** All imports below are existing types.

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class Service {
    @JsonProperty('service-name') String serviceName
    @JsonIgnore String internalNote
    Map<String, String> labels
    @Builder.Method @JsonProperty('configure-endpoint')
    void endpoint(String value) { serviceName = value }
}
// Model input: ['service-name': 'catalog', labels: ['Team/Blue': 'ops']]
// Or: ['configure-endpoint': 'catalog']; serviceName is not an alias.
```

The examples describe separate inputs; operation order still matters if both are supplied. `Team/Blue` remains data.
Exclusion controls this structured language; it does not prevent ordinary Groovy DSL configuration of `internalNote`.

**B — focused literals, conditional next choice.** `InputName` and `InputIgnore` are placeholder spellings with no selected
package or installed types. This sketch is unavailable, not an executable example or an annotation assignment.

```groovy
import com.blackbuild.klum.ast.DSL

@DSL class Service {
    @InputName('service-name') String serviceName
    @InputIgnore String internalNote
    Map<String, String> labels
}
// Same input grammar as A; no Jackson annotation needed by this producer.
```

Prefer separate name/exclude annotations over `@Input(name=..., exclude=...)`: the focused family is already accepted,
and a combined annotation adds a meaningless excluded-name combination. Initially cover source fields (including `@Key`)
and admitted authored methods, with applicability qualified separately. No getter/parameter/type/package shorthand,
include override or source-erasure claim. Existing exclusions and role eligibility retain authority.

**C — finite type profile, evaluated and deferred.** `InputNaming` and `Profile` are also placeholders, unavailable today.

```groovy
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonProperty

@InputNaming(Profile.KEBAB_V1)
@DSL class Service {
    String serviceName
    @JsonProperty('URL') String endpointUrl
    Map<String, String> labels
}
// Candidate grammar: ['service-name': 'catalog', URL: 'https://example.invalid',
//                     labels: ['Team/Blue': 'ops']]
```

A type profile needs a decision about inherited members and nested types. It can publish mixed conventions across JARs;
a type annotation cannot honestly claim a uniform root graph. Prefer literal B if fixed external spellings are the actual
problem. Prefer explicit root selection only if the integration owner actually owns whole-document casing.

## Compare the smallest interfaces

| Option | Benefit | Cost / recommendation |
| --- | --- | --- |
| No new annotation; retain A | Zero new author syntax, metadata migration or caller configuration. Works for inspected Schelm. | Foreign annotations remain necessary for exceptional spellings. **Recommend now**, pending maintainer confirmation. |
| Focused literal name/exclude B | Klum-owned dependency-free authoring with the same small grammar. | New source vocabulary, bridge conflicts and matched-binary qualification. **Next choice if producer dependency avoidance is required.** |
| Finite type profile annotation C | Many conventional names from one declaration; publishable producer intent. | Inheritance/child ownership and binary policy facts; mixed conventions. **Defer** without a concrete family-owned use case. |
| Explicit root/runtime + Gradle configuration | One integration-owned grammar across reachable owned binaries; no naming annotation necessary. | Matching runtime/generator selection, new call shapes/cache inputs, candidate preservation. **Conditional future profile preference** for whole-document demand, not needed now. Plugin-only renaming is invalid. |
| Package defaults | Reduce repeated type declarations in one source package. | Package-info provenance, split artifacts, compilation order and recompilation scope. **Defer**; package retention alone does not justify package semantics. |

Reject a generic annotation framework, registry/SPI, configurable case-conversion callbacks, ambient mapper/classpath
selection, WIRE companion revival, Model-byte fallback, user-code execution and a public descriptor protocol. Keep no
Jackson dependency in core and no Bean/mapper provider discovery. Broader `JsonNaming` is a separate optional compatibility
decision, not implied by either literals or profiles.

## Conditional literal contract if B is selected

This is a proposed acceptance boundary, not today's installed behavior:

1. Resolve the compiler-selected declaration and existing role eligibility first. The new names never widen admission.
2. Read exact canonical and narrow Jackson facts at that same source position. Equivalent renames/exclusions coalesce.
   Different literal names fail with both origins. Canonical `InputName` plus Jackson `JsonIgnore(true)`, or canonical
   `InputIgnore` plus an explicit Jackson rename/`JsonIgnore(false)`, fails as conflicting intent. Canonical name plus
   canonical exclude is invalid. Preserve existing Jackson-only ignore-over-rename behavior; do not retroactively reject it.
3. A canonical ignore agrees with Jackson ignore true. Jackson ignore false alone supplies no include override and cannot
   defeat structural/source exclusions. Empty Jackson rename retains existing fallback; a new canonical name must be
   nonempty and exact. Do not trim or normalize it. An excluded source cannot be resurrected by an unrelated accessor.
4. Without conflict: explicit exclusion removes the input; otherwise one literal name wins; otherwise native spelling.
   Preserve existing hierarchy/source selection, diamond deduplication and ordinary collision handling for this literal
   slice. Renaming adds no aliases. A genuine replacement declaration, if admitted today, owns its own facts; no new
   inheritance/override mechanism follows. Required-key position rules remain intact.
5. Use that one result for interpreter lookup and generator properties. Resolve constraint/deprecation/documentation
   placement by original source/use identity. A parent use must not rewrite shared child definitions.

No reader can silently ignore a newly authored language annotation. Prefer compiler-folded effective facts on declaring
public Builder fields/keys/operations; measure whether existing rename/exclusion slots suffice. Do not choose a new
public metadata format here. Authored-operation facts deserve explicit qualification because their current naming route
reads selected annotation bytes rather than the field/key folding path.

## If profiles remain viable: bounded first scope and precedence

**Not recommended for the next increment.** If whole-document demand is confirmed, retain this smaller candidate instead
of implementing C plus a package/root precedence lattice:

| Authority | First profile scope rule |
| --- | --- |
| Existing structural/source exclusion | Final. A profile cannot admit the declaration. |
| Canonical and supported Jackson literals | Resolve conflicts as above; exclusion then exact literal rename. Never case-convert a literal. |
| Explicit root selection | One immutable selection per read/apply/generation, applied to native names across all reachable owned contracts, including inherited declarations and construction-key member positions. |
| Type/package/relationship/member-profile configuration | No such feature in the first scope; no implicit override/fallback. Unsupported shaping metadata must produce an incompatibility diagnostic, not be ignored. |
| Absent root selection | Existing identity/native grammar. Explicit identity is the same spelling but retains explicit caller intent; no lower type/package default exists in this scope. |
| Jackson `JsonNaming`, mapper settings, thread context, reflection or classpath order | No authority in structured input. Preserve independent Jackson import behavior. |

Thus the strict chain is **eligibility/exclusion > compatible literal > explicit root profile > identity**. This candidate
has no member > type > package > root chain. Adding type/package later requires a new choice between root override and
caller fallback; do not smuggle that choice into metadata defaults. A root policy never cascades into ordinary data Map
keys, keyed-instance keys, fixed identity values, enum values or operation payload data. Nested declared object member
names do use it; a custom Map operation's keys remain its own payload data.

Start with only identity and one demanded separated spelling, provisionally versioned `kebab-v1`; defer snake and a
separate camel normalizer. The [earlier finite algorithm](issue-959-d7-naming-profiles.md#proposed-v1-transformation-contract)
is a candidate to approve, not Jackson equivalence: ASCII lower/digit-to-upper and acronym-final-capital boundaries,
ASCII lowercase mapping, digits attached, other Unicode/punctuation opaque, no locale/normalization callbacks.
Its illustrative vectors include `URLValue -> url-value`, `XML2Parser -> xml2-parser`, `already_snake -> already_snake`.
Freeze exact vectors, malformed-UTF-16 handling and algorithm version before implementation. No unbounded strategy
class, arbitrary `JsonNaming` class loading or user enum initialization is permitted.

Resolve candidate names **before** collision pruning at every hierarchy level. The current resolved entry map has already
lost colliding parent candidates; transforming its surviving strings is insufficient. Retain structural/exclusion reasons
separately from name collisions. Recomputing name conflicts for a new profile must not resurrect sources excluded for
other reasons. Identity must preserve current results. For a newly selected nondefault profile, recommend deterministic
failure on any collision in reachable admitted scopes before execution or output replacement, including field/operation,
overload and position-specific key collisions. This deliberately stricter nondefault failure is a separate maintainer
choice, not a claim about today's literal grammar. No first/last-wins or hidden native alias.

All three existing interpreter call shapes would need an explicit additive selection, with no mutable global/Builder
policy. The plugin would track the same inert value as a task input; caches/definitions must distinguish profile and key
position. Reject unknown profile/version rather than falling back. A generator-only option or documentation marker cannot
establish runtime parity. Public method/property names and any artifact marker are intentionally unselected.

## Compatibility and source-free cross-module implications

With recommendation A, no new metadata, source/binary ABI, runtime overload, generated output or recompile requirement
is introduced. Existing matching-tool requirements from #972/#974 remain. This does not promise that an older generator
can consume current constraint-policy facts.

For B or profiles, public Builder declarations remain the only shaping metadata authority. Inspect exact inert bytes and
signatures with class initialization disabled, never Model bytes, hidden implementations, source mirrors, defaults,
constructors, lifecycle, validation callbacks or mapper strategy objects. Preserve original source/type/artifact identity
across API/base/derived/owned-child JARs. Reflection order and whichever package-info/classpath duplicate loads first cannot
select policy. Detect ambiguous required contract/fact origins rather than deriving a grammar from them.

Adding annotation members/defaults alone is not proof of binary compatibility: current generated-fact readers reject
unknown members. Prefer reuse of effective name/exclusion facts for literals if sufficient, with explicit matched version
and completeness checks wherever new semantics cannot be detected. Required missing/unknown/malformed language facts must
fail with source/artifact and recompile/package guidance in **both** consumers. Do not infer 'no exclusion' from missing
new metadata. A legacy supported contract can retain identity only where its existing complete facts establish that
contract; a selected feature requiring unavailable provenance must fail, not guess. No blanket rejection of all old
identity artifacts, and no claim that all old artifacts are compatible, is justified before the probe.

A root transform also needs native candidate/source facts before prior collision filtering; no amount of package
annotation reflection repairs lost candidates. Literal-only support might need much less metadata. Preserve current
constraintVersion/decimalVersion/projectionVersion responsibilities; a naming decision must not weaken their checks or
couple them to a new umbrella version. Advisory deprecation absence differs from required language-fact absence.

Managed Jackson import retains mapper-owned naming/coercion/aliases and all four existing modes. A Klum-only rename would
shape structured input, not automatically teach ObjectMapper that spelling. Use the existing Jackson literal too when
both routes intentionally share one token. That duplicate annotation is acceptable interoperability; an automatic Klum
annotation adapter for Jackson would require separate demand and qualification.

## Bounded missing-evidence probes

No new executable probes were run, added or scheduled in this task. Historical test results are not fresh qualification.
Stop after the selected decision question is answered; do not build a prototype framework.

| Probe | Bound / decision it can answer |
| --- | --- |
| Consumer requirement | Review one Application Package and one Operations Environment document plus their Schema declarations with the maintainer. Identify fixed external tokens, producer dependency restrictions, desired casing and whether dual Jackson input is actually needed. Existing documents answer current spelling, not preference. No consumer changes. |
| Literal-only transport | If B is selected, one temporary test-only fixture family on G3/4/5: API/base, derived root and owned child in separate JARs. Cover field/key/operation rename/exclude, equivalent/conflicting bridge facts, inheritance/diamond, and deleted sources/mirrors/compiler outputs. Inspect only public facts without compiler/Jackson databind on the consumer path. Decide reuse versus minimal required metadata and measure classfile delta. |
| Compatibility/no execution | In that same fixture family, compare current complete identity binaries, missing/unknown new facts and malformed metadata. Use initializer/constructor/callback traps with separate positive controls. Verify both consumers fail at the stated seam, preserving active Builder state and old generated artifacts before execution/replacement. No Model fallback. |
| Profile only if demanded | One explicit root and one shared binary child reached twice, plus base collision candidates. Use identity/kebab finite vectors and literal precedence, Map/instance keys, member/keyless positions, source collisions and two isolated root selections. Compare interpreter structural name admission with generated closed-schema properties; numeric assertions remain independently stricter. Reject scope leakage and unknown selections. |

Package-info and broader Jackson strategy probes are unnecessary for A/B and the first root-only profile scope. Reopen
those probes only after their use cases are accepted. Passing a finite hand-authored naming oracle cannot qualify a real
compiler/runtime/plugin path or an arbitrary Jackson strategy.

## Exact maintainer choices before implementation

Decide in this order; no implementation work is opened by this brief:

1. **Need/timing:** accept A for v1, or identify the producer requirement that makes optional Jackson annotation authoring
   unacceptable now. Recommended answer: A now. State any immediate consumer blocker; leave release placement unchanged.
2. **If B is needed:** select final annotation names/package, field/key/authored-method targets and retention; approve the
   strict bridge conflict table, no include override, exact nonempty names and unchanged existing collision/key semantics.
   Recommended shape: separate name and ignore annotations only, runtime retention with compiler-folded facts.
3. **Interoperability:** confirm that managed Jackson remains independent and Klum literals alone need not affect it.
   If identical inputs are required, identify the exact bounded dual-route example; do not substitute 'Jackson parity'.
4. **Only if profiles are needed:** choose integration-owned root graph versus producer-owned declaring-type defaults.
   Recommended first scope for whole-document demand: root only, no type/package policy. Select the one alternate spelling,
   exact versioned algorithm/vectors, nondefault collision failure and selection on every interpreter/generator path.
5. **Transport and delivery:** after the bounded binary probe, approve minimal completeness/version handling and source-free
   acceptance matrix before a separately assigned implementation. No public descriptor, new issue, milestone or release
   commitment is selected here. Any delivered feature later needs documentary `@Issue`/`@Tag`/`@See` coverage, user/migration
   navigation and changelog updates under the existing repository rules.

## Delivery and validation boundary

This task adds a local decision brief and navigation pointers only. ADR 0031's accepted behavior is unchanged; the
proposal is not a new ADR acceptance. Tracker impact: Related #959; no issue comment/state/label/milestone or curation
change. No production code, annotations, executable fixtures or user-facing release claims are changed. No implementation
PR exists. Local committed delivery is sufficient while maintainer choices remain open; no draft PR is required.

Validation passed: all 68 relative Markdown targets/anchors across the three changed documents, balanced fences,
`git diff --cached --check`, and documentation-only scope/history review.
Groovy lanes and schema-generation runs are omitted under the documentation-only testing rule. Runtime behavior above
is source/test inspection evidence, not a claim of newly executed tests. The final callback records exact commit and
check outcomes; Hive reconciliation is requested separately from this completed design assignment.
