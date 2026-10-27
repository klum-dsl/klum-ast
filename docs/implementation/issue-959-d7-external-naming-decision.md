# #959 D7: external naming and exclusion decision brief

Date: 2026-10-26. Status: **Design-only authoring contract confirmed; bounded binary probes precede any implementation assignment.**

Subsequent implementation scope: the assignment after merged #975 splits the confirmed family into a
[profile-only first slice](issue-959-d7-input-naming-profiles.md) and a later `InputName`/`InputIgnore` slice.
The original combined-slice statements below are retained as the design record; they no longer determine that delivery partition.

Audited base: `3daea832e4e6fa9247c6ab8cb7e53f61da5772bb` (`origin/master`, merged
[PR #974](https://github.com/klum-dsl/klum-ast/pull/974)). Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959).
This is the next independent language-shaping discussion after `ConstraintProjection`. Numeric projection, field policy
and deferred examples retain their existing decisions. No implementation, tracker change or release placement follows.

## Maintainer direction and recommendation

The original recommendation to postpone profiles is superseded by new maintainer evidence on 2026-10-26:
**another current project already preconverts kebab-case input Map keys to camelCase, and that workaround is brittle.**
A type-wide naming profile has immediate value there, independently of Schelm's current identity input. No producer
restriction on Jackson annotations is needed to justify this use case. The project is not named here, and its source
or converter has not been inspected; this is direct maintainer evidence, not a reproduced defect diagnosis.

Accepted scope direction:

- Provide a **type-wide naming profile**, retaining per-entry literal overrides. The interpreter and generator must
  describe the same external names; conversion is resolved against Schema declarations, not performed over arbitrary
  input Map keys.
- **Explicit profiles bind descendants.** An unannotated subtype inherits the profile. Repeating the same profile is
  allowed; a different explicit profile is an error, including attempts to replace an explicit `IDENTITY`.
- **Absence leaves the choice open.** A child may introduce a profile if no ancestor explicitly selected one. That profile
  governs inherited entries too, while explicit per-entry names/exclusions retain authority. An unannotated ancestor's
  standalone contract still uses native names; selecting a child does not mutate the ancestor's contract.
- Composed owned values resolve naming from their own type/hierarchy; the owner's profile does not cascade. This
  concerns relationships between ordinary separate types, not lexical nested class declarations. It adds no support
  guarantee for nested classes, which the maintainer discourages.
- Accept kebab boundaries `serviceName -> service-name`, `URLValue -> url-value`, `XML2Parser -> xml2-parser` and
  `version2URL -> version2-url`, keeping digits attached to the preceding word. Non-ASCII characters and existing
  separators stay unchanged; only ASCII boundaries and A-Z lowercasing apply. Thus `already_snake` stays unchanged and
  `ÅngströmURL -> Ångström-url`. Specific literal overrides remain available.
- A nonidentity profile that produces colliding admitted entry names rejects the type, naming both declarations. It
  must not silently exclude the colliders and keep the rest. Reject before input execution or schema publication;
  a specific literal rename can resolve the conflict.
- Limit this slice to structured-input interpretation and JSON Schema generation. The current project does not require
  the same new profile under managed Jackson import. Existing Jackson naming behavior remains independently supported.
- Include focused Klum name/exclusion annotations alongside the profile annotation, retaining compatible existing
  `JsonProperty`/`JsonIgnore` inputs. The authoring vocabulary must be usable entirely through Klum annotations. The
  accepted vocabulary is `InputNaming` with nested `Profile.IDENTITY`/`KEBAB_CASE`, `InputName('literal')` and marker
  `InputIgnore`, all in `com.blackbuild.klum.ast`. This records the public design; no annotation types are installed yet.
- Equivalent Klum/Jackson declarations coalesce. Contradictory declarations on an entry are an error with both
  origins in the diagnostic; no canonical-over-adapter override or annotation-order selection. This fails contract
  resolution rather than silently reducing the admitted grammar. Explicit exclusion wins over renaming, including
  mixed Klum/Jackson declarations; `JsonIgnore(false)` is neutral. Different explicit rename values still conflict.
- Confirm the annotation contracts: `RUNTIME` retention; type-only `InputNaming` with a required explicit profile;
  required nonempty `InputName` and marker `InputIgnore` on source fields (including keys) and admitted authored methods.
- Defer package-info configuration. Source-free compatibility/metadata qualification precedes a separate implementation
  assignment; the accepted contract does not authorize production changes. No root/runtime selector is required by the demonstrated use case.

Recommend a finite type annotation as the next design surface, starting with identity and kebab-case. Preserve the
accepted **focused Klum annotation family** direction and existing optional Jackson literal inputs. The maintainer
selected Klum-owned per-entry name/exclusion annotations for this slice as well as the type profile. Do not require
replacing existing Jackson literals merely to opt into a profile. Broader Jackson `JsonNaming` compatibility remains a
separate decision.

Seek a **deep module** at the existing compiled-contract **seam**: one source-associated name/admission implementation
serves interpreter and generator. Keep selection, inheritance, collision handling and metadata decoding local to that
module; do not expose them as a public descriptor or configuration framework. This remains design-only work.

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
| Schema Developer in another current project needs kebab-case authoring against camelCase members. | **Immediate**, reported directly by the maintainer. Existing workaround preconverts input Map keys; it works but is brittle. Type-wide profiles with entry overrides are now the primary use case. No separate source inspection or measured conversion cost is claimed. |
| Integration owner needs one caller-selected alternate casing across every independent binary dependency. | Still unconfirmed: type-wide demand does not imply root-wide override. Source-free generation remains a qualification requirement for the selected type policy. |
| Same document must work under both Jackson and structured input. | **Not required for this first slice**, confirmed by the maintainer for the current project. Existing Jackson binding remains independent; mapper-wide parity is not delivered by name conversion. |

Hypotheses, not requirements: a package convention is necessary; every dependency must follow the caller's casing;
camelCase means normalization rather than unchanged spelling; a Jackson strategy has the desired acronym rules; a
foreign-format input must match Kubernetes output; old metadata can safely imply absence of a new exclusion policy.

## Three compact Groovy authoring alternatives

**A — available today; retained compatibility baseline.** All imports below are existing types.

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

**B — focused literals, selected alongside profiles.** Names and package are accepted, but these types are not
installed yet. This is the accepted authoring design, not an executable example or implementation assignment.

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.InputIgnore
import com.blackbuild.klum.ast.InputName

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

**C — finite type profile, selected design direction.** `InputNaming` and its nested `Profile` have accepted names;
implementation remains separately unassigned.

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.InputNaming
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class ServiceBase { String displayName } // no profile: introduction is open
@InputNaming(InputNaming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase {
    String serviceName
    @JsonProperty('URL') String endpointUrl
    Map<String, String> labels
}
@DSL class DerivedService extends Service {} // inherits the binding kebab profile
// Candidate Service input: ['display-name': 'Catalog', 'service-name': 'catalog',
//                          URL: 'https://example.invalid', labels: ['Team/Blue': 'ops']]
// A different explicit profile on DerivedService would be rejected.
```

Accepted inheritance can be illustrated by extending this example: an unannotated `DerivedService` inherits
`Service`'s explicit profile; selecting identity on `DerivedService` is an error. Conversely, an unannotated base leaves
room for its first annotated child to choose kebab-case for its complete effective contract. Entry literals such as `URL`
remain exact. Owned child types resolve their own profile. The only initial profile values are
`InputNaming.Profile.IDENTITY` and `InputNaming.Profile.KEBAB_CASE`.

## Compare the smallest interfaces

| Option | Benefit | Cost / recommendation |
| --- | --- | --- |
| No new annotation; retain A | Zero new author syntax, metadata migration or caller configuration. Works for inspected Schelm. | Insufficient for the newly confirmed class-wide use case; retain as the unchanged default for unconfigured types. |
| Focused literal name/exclude B | Klum-owned dependency-free authoring with the same small grammar. | New source vocabulary, bridge conflicts and matched-binary qualification. **Selected for this slice** with the type profile, preserving compatible Jackson inputs. Names, targets and runtime retention are confirmed. Equivalent bridge facts coalesce; contradictions fail with both origins. |
| Finite type profile annotation C | Many conventional names from one declaration; publishable producer intent. | **Selected direction** for the immediate project need. Explicit inherited-profile conflicts reject; owned values keep their own type policy. Binary facts still require qualification. |
| Explicit root/runtime + Gradle configuration | One integration-owned grammar across reachable owned binaries; no naming annotation necessary. | Matching runtime/generator selection, new call shapes/cache inputs, candidate preservation. **Defer**; caller-owned whole-document override is not the demonstrated type-wide requirement and could violate binding ancestor profiles. Plugin-only renaming is invalid. |
| Package defaults | Reduce repeated type declarations in one source package. | Package-info provenance, split artifacts, compilation order and recompilation scope. **Defer**; package retention alone does not justify package semantics. |

Reject a generic annotation framework, registry/SPI, configurable case-conversion callbacks, ambient mapper/classpath
selection, WIRE companion revival, Model-byte fallback, user-code execution and a public descriptor protocol. Keep no
Jackson dependency in core and no Bean/mapper provider discovery. Broader `JsonNaming` is a separate optional compatibility
decision, not implied by either literals or profiles.

## Per-entry literals and selected Klum equivalents

Existing literal overrides remain supported under profiles, and focused Klum equivalents are selected for this slice.
The maintainer accepted coalescing equivalent declarations, rejecting conflicting literal names, exclusion-over-rename
precedence and neutral `JsonIgnore(false)`. Resolve these rules by exact selected source, not annotation visitation order:

1. Resolve the compiler-selected declaration and existing role eligibility first. Naming annotations never widen admission.
2. Gather nonempty exact `InputName` and supported `JsonProperty` names on that declaration. Equal names coalesce;
   different explicit names fail contract resolution with both origins, including when exclusion is also present.
   Empty Jackson rename retains native/profile fallback; a new `InputName` must be nonempty and exact, without trimming.
3. `InputIgnore` or `JsonIgnore(true)` excludes the entry, taking precedence over its otherwise resolved name. Combining
   a name and exclusion is valid. `JsonIgnore(false)` contributes no exclusion and cannot undo `InputIgnore` or a
   structural/source rejection. Existing Jackson-only ignore-over-rename behavior remains intact. There is no include
   override or resurrection through an unrelated accessor.
4. Otherwise use the compatible literal name; without one, apply the effective type profile (identity when none is
   selected). Preserve source selection and diamond deduplication. Existing no-profile collision behavior stays intact;
   nonidentity profile collisions reject the type under the accepted rule. Renaming adds no native alias. A genuine
   replacement declaration, if admitted today, supplies its own facts; no new field-override mechanism follows.
5. Share that one result between interpreter lookup and generator properties. Required-key positions remain intact;
   excluding an input-supplied required key does not erase identity requirements. Match constraint/deprecation/docs
   placement by original source/use identity; never rewrite shared child definitions with parent-use facts.

No reader can silently ignore a newly authored language annotation. Prefer compiler-folded effective facts on declaring
public Builder fields/keys/operations; measure whether existing rename/exclusion slots suffice. Do not choose a new
public metadata format here. Authored-operation facts deserve explicit qualification because their current naming route
reads selected annotation bytes rather than the field/key folding path.

## Type-profile scope, precedence and remaining choices

This supersedes the earlier root-only recommendation. **The accepted inheritance rule concerns the Schema class
hierarchy, not owned-object composition.** Each composed owned value resolves its profile independently from its own
type/hierarchy; a parent property does not rewrite the child contract. Lexically nested class declarations are outside
this scope and acquire no support guarantee.

| Authority | Rule / decision state |
| --- | --- |
| Existing structural/source exclusion | Final. A profile cannot admit the declaration. |
| Per-entry literal rename/exclude | Retains authority over the type fallback. Never case-convert a literal. The accepted bridge rules above coalesce equivalent names, reject different names, prioritize exclusion and treat false ignore as neutral. |
| Any explicit ancestor profile | **Accepted:** binding on descendants. An unannotated child inherits; equal explicit repetition is allowed; a differing explicit child choice fails. Explicit `IDENTITY` is a binding choice too. |
| First explicit profile on a descendant of unannotated types | **Accepted:** permitted; applies to that effective type's inherited and local eligible entries. Standalone ancestor grammar stays unchanged. |
| No explicit profile in the class hierarchy | Existing native/identity spelling; absence is not an explicit lock. |
| Composed owned value | **Accepted:** resolve that object's own type/hierarchy independently; no profile propagation through ownership. This is not a lexical nested-class feature. |
| Package, root/caller and relationship-profile configuration | Deferred; no first-scope override or fallback. Do not add a hidden root selector that defeats ancestor binding. |
| Jackson `JsonNaming`, mapper settings, thread context, reflection or classpath order | No new authority in structured input. Preserve independent Jackson import behavior. |

The accepted chain is **eligibility/exclusion > compatible literal > inherited-or-local explicit type profile > identity**;
validate contradictory explicit names before applying exclusion.
There is no nearest-conflicting-annotation-wins rule: incompatible explicit profile declarations make the subtype's
contract invalid. Bind provenance to the exact declaring type/artifact, distinguish absent from explicitly selected
identity, and resolve it consistently from source-free public Builder contracts. Do not rely on Java `@Inherited` or
annotation visitation order to define the semantics. Any additional supported inheritance shape with incompatible origins
must diagnose rather than select a profile by traversal order.

A profile applies to member tokens, including admitted authored-operation names and construction-key member names.
Ordinary data Map keys, keyed-instance keys, fixed identity values, enum values and custom Map-operation payload keys stay
literal. Exact target qualification remains required; naming does not broaden operation or ownership admission.

The two accepted initial profile constants are `IDENTITY` and `KEBAB_CASE`. Defer snake and a separate camel
normalizer. `IDENTITY` preserves native spelling exactly, including leading capitals. For `KEBAB_CASE`, inspect original
characters/code points, without feeding inserted separators back into boundary detection:

1. Insert `-` before an ASCII uppercase letter preceded by ASCII lowercase or a digit.
2. Also insert `-` before an ASCII uppercase letter preceded by ASCII uppercase and followed by ASCII lowercase.
3. Replace only ASCII A-Z with a-z. Copy non-ASCII characters, existing separators and all other characters unchanged;
   those characters break ASCII adjacency. Do not trim, collapse separators, normalize Unicode, transliterate or use locale rules.

This is the bounded algorithm described by the accepted examples: `serviceName -> service-name`, `URLValue -> url-value`,
`XML2Parser -> xml2-parser`, `version2URL -> version2-url`, `already_snake -> already_snake`, `ÅngströmURL -> Ångström-url`.
Digits stay attached to the preceding word. An exact `InputName` is never transformed. Preserve no-profile output and
reject unknown profiles. This does not claim Jackson naming-strategy equivalence or execute arbitrary strategy classes.
The [historical finite algorithm](issue-959-d7-naming-profiles.md#proposed-v1-transformation-contract) is background, not
approval of its other profile, root/API or metadata proposals. Recommend source-aware failure for malformed UTF-16 in
profile-transformed native metadata; corruption handling is part of the bounded binary probe, not Unicode repair.

Resolve names from **native candidates before collision pruning**. The current resolved entry map has already lost
colliding parent candidates; transforming its surviving strings is insufficient when a child first introduces a profile.
Keep structural/exclusion reasons separate from name collisions. Recompute conflicts under the effective type profile
without resurrecting sources excluded for other reasons. Preserve existing no-profile identity results. **Accepted:**
a nonidentity profile rejects a type with colliding admitted entry names before input execution or schema publication,
with a diagnostic naming the conflicting declarations. A literal entry override can resolve the conflict. Include field/
operation, overload and position-specific key conflicts in qualification. This stricter nonidentity failure is newly
accepted direction, not today's universal collision behavior. No first/last-wins, silently dropped colliders or native aliases.

A type-owned profile should require **no new runtime overload or plugin naming selector**: both consumers resolve it from
the same compiled contract. Cache by effective contract and key position; inherited declarations retain source identity
but may have different names when inspected through an unannotated ancestor versus its first profiled child. A composed
child's contract has the same naming regardless of which owning root reaches it. No mutable global/Builder policy or
input marker auto-detection follows.

## Compatibility and source-free cross-module implications

This task changes no installed metadata, source/binary ABI, runtime overload or generated output. Delivering type
profiles later will require explicitly retained declared profile/absence and inheritance provenance, with matched-tool
qualification. Existing matching-tool requirements from #972/#974 remain. An older generator is not promised to consume
current constraint-policy facts or future language-shaping facts.

For the selected literals and profiles, public Builder declarations remain the only shaping metadata authority. Inspect exact inert bytes and
signatures with class initialization disabled, never Model bytes, hidden implementations, source mirrors, defaults,
constructors, lifecycle, validation callbacks or mapper strategy objects. Preserve original source/type/artifact identity
across API/base/derived/owned-child JARs. Reflection order and whichever package-info/classpath duplicate loads first cannot
select policy. Detect ambiguous required contract/fact origins rather than deriving a grammar from them.

The shared protocol on declaring public Builder contracts requires exact current language and provenance facts in both
consumers. Older or incomplete contracts are rejected, including identity-only hierarchies. Recompile participating
artifacts with a matching supported toolchain. No missing exclusion, literal or naming choice is reconstructed.

A first type profile over inherited declarations needs native candidate/source facts before collision filtering.
All required numeric, policy, naming, literal and provenance facts share one supported protocol. This does not change
their individual functional semantics or permit Model-byte/companion fallback.

Managed Jackson import retains mapper-owned naming/coercion/aliases and all four existing modes. **Confirmed slice
scope:** the current project needs the new type profile only for structured input and JSON Schema, not ObjectMapper.
A Klum-only rename would shape structured input, not automatically teach ObjectMapper that spelling. Use the existing Jackson literal too when
both routes intentionally share one token. That duplicate annotation is acceptable interoperability; an automatic Klum
annotation adapter for Jackson would require separate demand and qualification.

## Bounded missing-evidence probes

No new executable probes were run, added or scheduled in this task. Historical test results are not fresh qualification.
Stop after the selected decision question is answered; do not build a prototype framework.

| Probe | Bound / decision it can answer |
| --- | --- |
| Current-project convention | Inspect one representative kebab-case input and matching Schema from the newly identified project if available. Confirm acronym/digit spelling, fixed tokens, data Map keys and whether dual Jackson input is required; do not assume Schelm is the only consumer. Need and type-wide scope are already confirmed. No consumer changes. |
| Selected literal transport | One temporary test-only fixture family on G3/4/5: API/base, derived root and owned child in separate JARs. Cover field/key/operation rename/exclude, equivalent/conflicting bridge facts, inheritance/diamond, and deleted sources/mirrors/compiler outputs. Inspect only public facts without compiler/Jackson databind on the consumer path. Decide reuse versus minimal required metadata and measure classfile delta. |
| Compatibility/no execution | In that same fixture family, compare current complete identity binaries, missing/unknown new facts and malformed metadata. Use initializer/constructor/callback traps with separate positive controls. Verify both consumers fail at the stated seam, preserving active Builder state and old generated artifacts before execution/replacement. No Model fallback. |
| Type profile and inheritance | One unannotated base, first profiled child, unannotated grandchild, equal-profile descendant and conflicting descendant in separate JARs. Include an explicit-identity base, preserved literal overrides, base collision candidates and an owned child reached twice. Compare interpreter name admission with generated closed-schema properties, key positions and identity/kebab vectors. Verify parent standalone grammar is unchanged, conflicts are deterministic and composed children use their own type policy. Numeric assertions remain independently stricter. |

Package-info and broader Jackson strategy probes are unnecessary for the selected first type-profile scope. Reopen
those probes only after their use cases are accepted. Passing a finite hand-authored naming oracle cannot qualify a real
compiler/runtime/plugin path or an arbitrary Jackson strategy.

## Exact maintainer choices before implementation

Already settled: current-project need; type-wide profiles; per-entry overrides; no package-info in the first scope;
explicit profiles bind descendants; unannotated ancestry leaves introduction open; introduced profiles cover inherited
entries; composed owned values resolve their own type policy, with no lexical nested-class support promise. These
supersede this brief's earlier profile deferral and root-only recommendation. Kebab acronym/digit boundaries and
nonidentity collision failure are also accepted, with specific literal overrides. The first slice covers structured
input and JSON Schema only; a Jackson profile adapter is not required. The accepted authoring vocabulary is
`InputNaming`/nested `Profile.IDENTITY` and `KEBAB_CASE`, `InputName` and marker `InputIgnore`, all in
`com.blackbuild.klum.ast`. Focused literals belong in the same slice, retaining compatible `JsonProperty`/`JsonIgnore`
input. Equivalent declarations coalesce;
contradictory literal names fail with both origins rather than silently selecting Klum or annotation order. Exclusion
wins over renaming, including mixed annotations; false Jackson ignore is neutral. Non-ASCII and existing separators are
preserved by the bounded ASCII kebab algorithm. No release placement changes.

Confirmed annotation contract:

| Annotation | Target | Value / retention |
| --- | --- | --- |
| `InputNaming` | Schema `TYPE` only | Required `Profile value()`; no default. Only `IDENTITY`, `KEBAB_CASE`. `RUNTIME` retention. An explicit identity remains different from absence for descendant locking. |
| `InputName` | Source `FIELD` (including selected construction keys) and admitted authored `METHOD` | Required nonempty exact `String value()`; no default or normalization. `RUNTIME` retention. No getter/parameter/package or include-override shorthand. |
| `InputIgnore` | Same field/method sites | Marker, no members; `RUNTIME` retention. Exclusion affects structured-input language, not ordinary Groovy configuration or runtime validation. |

The maintainer confirmed this design-only contract on 2026-10-26. These follow the existing small annotation style;
no production definitions or new target admission are created here. The full authoring contract above is confirmed.
No additional profile, Jackson adapter, package/root selector, example transport or generic annotation framework is included.

Before implementation, bounded technical evidence is still required:

- Qualify declaring-profile/absence/provenance facts and native candidate preservation with source-free G3/4/5 binaries,
  including inherited fields/keys/operations, explicit-identity locking, repeated equal profiles and conflicting profiles.
- Select the smallest completeness/version encoding from that evidence. Require matched compiler/runtime/plugin linkage
  wherever new facts are required; unknown/missing/corrupt language facts fail with source/artifact recompile guidance.
  Do not guess identity from missing required ancestry or revive Model-byte/companion fallback.
- Verify shared grammar, deterministic collision/bridge errors, no execution and output atomicity. Preserve existing
  constraint/decimal/projection completeness requirements and independently stricter numeric assertions.

Those probes and their result review precede a separately authorized implementation; this is not an instruction to run
them now. Final transport encoding is an evidence decision, not an open requirement for a new authoring interface.
Release placement remains unassigned. Any later delivered feature needs documentary `@Issue`/`@Tag`/`@See`, current user
pages, migration navigation and changelog updates. The design does not authorize implementation or tracker changes.
The maintainer subsequently requested publication of this documentation-only record as a pull request.

## Delivery and validation boundary

This task changes the local decision brief and navigation pointers only. ADR 0031 records accepted need, inheritance
and composed-type scope, accepted kebab boundaries, collision failure, structured-input/schema-only scope, and the
selected Klum literal authoring family with compatible Jackson inputs.
The design-only authoring contract is confirmed. Binary qualification precedes a separately assigned implementation;
installed behavior is unchanged. Tracker impact: Related #959; no issue comment/state/label/milestone or curation change.
No production code, annotations, executable fixtures or user-facing release claims are changed. The maintainer's
subsequent publication request authorizes a draft documentation-only pull request against master. It is not an
implementation PR; the bounded probes and their review remain a separate prerequisite to implementation.

Final documentation validation: all 69 relative Markdown targets/anchors across the three task documents, balanced
fences, `git diff --check`, documentation-only scope and local history review. No runtime tests were run.
Groovy lanes and schema-generation runs are omitted under the documentation-only testing rule. Runtime behavior above
is source/test inspection evidence, not a claim of newly executed tests. The final callback records exact commit and
check outcomes and the requested draft pull request. Request Hive reconciliation after delivery without self-applying
archive state. The bounded future probes are a separate assignment, not unfinished production work in this task.
