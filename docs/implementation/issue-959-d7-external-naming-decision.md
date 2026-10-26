# #959 D7: external naming and exclusion decision brief

Date: 2026-10-26. Status: **Type-profile scope, inheritance, kebab boundaries and collision failure accepted; remaining semantics open; no implementation authorized.**

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
  `version2URL -> version2-url`, keeping digits attached to the preceding word. Specific literal overrides remain available.
- A nonidentity profile that produces colliding admitted entry names rejects the type, naming both declarations. It
  must not silently exclude the colliders and keep the rest. Reject before input execution or schema publication;
  a specific literal rename can resolve the conflict.
- Limit this slice to structured-input interpretation and JSON Schema generation. The current project does not require
  the same new profile under managed Jackson import. Existing Jackson naming behavior remains independently supported.
- Include focused Klum name/exclusion annotations alongside the profile annotation, retaining compatible existing
  `JsonProperty`/`JsonIgnore` inputs. The authoring vocabulary must be usable entirely through Klum annotations. The
  `InputName`/`InputIgnore` spellings remain proposals until explicitly finalized; accepting the shape does not install them.
- Equivalent Klum/Jackson declarations coalesce. Contradictory declarations on an entry are an error with both
  origins in the diagnostic; no canonical-over-adapter override or annotation-order selection. This fails contract
  resolution rather than silently reducing the admitted grammar. Exact edge combinations below remain to be reviewed.
- Defer package-info configuration. Final annotation/enum vocabulary, punctuation/Unicode details and compatibility/
  metadata policy remain open. No root/runtime selector is required by the demonstrated use case.

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

**B — focused literals, selected alongside profiles.** `InputName` and `InputIgnore` are placeholder spellings with no selected
package or installed types. This is accepted authoring shape with provisional names; it is not an available API or implementation assignment.

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

**C — finite type profile, selected design direction.** `InputNaming` and `Profile` are also placeholders, unavailable today.

```groovy
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class ServiceBase { String displayName } // no profile: introduction is open
@InputNaming(Profile.KEBAB_V1)
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
remain exact. Final annotation/enum spellings remain unselected; owned child types resolve their own profile.

## Compare the smallest interfaces

| Option | Benefit | Cost / recommendation |
| --- | --- | --- |
| No new annotation; retain A | Zero new author syntax, metadata migration or caller configuration. Works for inspected Schelm. | Insufficient for the newly confirmed class-wide use case; retain as the unchanged default for unconfigured types. |
| Focused literal name/exclude B | Klum-owned dependency-free authoring with the same small grammar. | New source vocabulary, bridge conflicts and matched-binary qualification. **Selected for this slice** with the type profile, preserving compatible Jackson inputs. Final spellings/targets remain to be settled. Equivalent bridge facts coalesce; contradictions fail with both origins. |
| Finite type profile annotation C | Many conventional names from one declaration; publishable producer intent. | **Selected direction** for the immediate project need. Explicit inherited-profile conflicts reject; owned values keep their own type policy. Binary facts still require qualification. |
| Explicit root/runtime + Gradle configuration | One integration-owned grammar across reachable owned binaries; no naming annotation necessary. | Matching runtime/generator selection, new call shapes/cache inputs, candidate preservation. **Defer**; caller-owned whole-document override is not the demonstrated type-wide requirement and could violate binding ancestor profiles. Plugin-only renaming is invalid. |
| Package defaults | Reduce repeated type declarations in one source package. | Package-info provenance, split artifacts, compilation order and recompilation scope. **Defer**; package retention alone does not justify package semantics. |

Reject a generic annotation framework, registry/SPI, configurable case-conversion callbacks, ambient mapper/classpath
selection, WIRE companion revival, Model-byte fallback, user-code execution and a public descriptor protocol. Keep no
Jackson dependency in core and no Bean/mapper provider discovery. Broader `JsonNaming` is a separate optional compatibility
decision, not implied by either literals or profiles.

## Per-entry literals and selected Klum equivalents

Existing literal overrides remain supported under profiles, and focused Klum equivalents are selected for this slice.
**Accepted general rule:** equivalent Klum/Jackson facts coalesce; contradictory declarations fail with both origins
rather than selecting by annotation order or letting Klum silently win. The detailed edge combinations below remain
proposed; acceptance of the general rule is not automatic approval of every combination or today's installed behavior:

1. Resolve the compiler-selected declaration and existing role eligibility first. The new names never widen admission.
2. Read exact canonical and narrow Jackson facts at that same source position. Equivalent renames/exclusions coalesce.
   Different literal names fail with both origins. Canonical `InputName` plus Jackson `JsonIgnore(true)`, or canonical
   `InputIgnore` plus an explicit Jackson rename/`JsonIgnore(false)`, fails as conflicting intent. Canonical name plus
   canonical exclude is invalid. Preserve existing Jackson-only ignore-over-rename behavior; do not retroactively reject it.
3. A canonical ignore agrees with Jackson ignore true. Jackson ignore false alone supplies no include override and cannot
   defeat structural/source exclusions. Empty Jackson rename retains existing fallback; a new canonical name must be
   nonempty and exact. Do not trim or normalize it. An excluded source cannot be resurrected by an unrelated accessor.
4. Without conflict: explicit exclusion removes the input; otherwise one literal name wins; otherwise the effective type
   profile (identity when none is selected).
   Preserve existing hierarchy/source selection, diamond deduplication and ordinary collision handling for a literal-only
   slice. Renaming adds no aliases. A genuine replacement declaration, if admitted today, owns its own facts; no new
   inheritance/override mechanism follows. Required-key position rules remain intact.
5. Use that one result for interpreter lookup and generator properties. Resolve constraint/deprecation/documentation
   placement by original source/use identity. A parent use must not rewrite shared child definitions.

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
| Per-entry literal rename/exclude | Retains authority over the type fallback. Never case-convert a literal. Existing Jackson-only ignore-over-rename behavior remains; optional Klum bridge conflicts follow the proposed table above if approved. |
| Any explicit ancestor profile | **Accepted:** binding on descendants. An unannotated child inherits; equal explicit repetition is allowed; a differing explicit child choice fails. Explicit `IDENTITY` is a binding choice too. |
| First explicit profile on a descendant of unannotated types | **Accepted:** permitted; applies to that effective type's inherited and local eligible entries. Standalone ancestor grammar stays unchanged. |
| No explicit profile in the class hierarchy | Existing native/identity spelling; absence is not an explicit lock. |
| Composed owned value | **Accepted:** resolve that object's own type/hierarchy independently; no profile propagation through ownership. This is not a lexical nested-class feature. |
| Package, root/caller and relationship-profile configuration | Deferred; no first-scope override or fallback. Do not add a hidden root selector that defeats ancestor binding. |
| Jackson `JsonNaming`, mapper settings, thread context, reflection or classpath order | No new authority in structured input. Preserve independent Jackson import behavior. |

The proposed chain is **eligibility/exclusion > compatible literal > inherited-or-local explicit type profile > identity**.
There is no nearest-conflicting-annotation-wins rule: incompatible explicit profile declarations make the subtype's
contract invalid. Bind provenance to the exact declaring type/artifact, distinguish absent from explicitly selected
identity, and resolve it consistently from source-free public Builder contracts. Do not rely on Java `@Inherited` or
annotation visitation order to define the semantics. Any additional supported inheritance shape with incompatible origins
must diagnose rather than select a profile by traversal order.

A profile applies to member tokens, including admitted authored-operation names and construction-key member names.
Ordinary data Map keys, keyed-instance keys, fixed identity values, enum values and custom Map-operation payload keys stay
literal. Exact target qualification remains required; naming does not broaden operation or ownership admission.

Start with identity and kebab-case; defer snake and a separate camel normalizer unless requested. **Accepted kebab
boundaries:** `serviceName -> service-name`, `URLValue -> url-value`, `XML2Parser -> xml2-parser`,
`version2URL -> version2-url`; digits remain attached to the preceding word. Specific entry overrides retain authority.
The [earlier finite algorithm](issue-959-d7-naming-profiles.md#proposed-v1-transformation-contract) supplies a proposed
complete rule: ASCII lower/digit-to-upper and acronym-final-capital boundaries, ASCII lowercase mapping, other Unicode/
punctuation opaque, no locale/normalization callbacks. Punctuation/Unicode and malformed UTF-16 rules still need review;
the four accepted vectors do not accept every historical algorithm detail or claim Jackson equivalence. Reject unknown
profiles; no arbitrary naming-strategy execution.

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

A first type profile over inherited declarations also needs native candidate/source facts before prior collision
filtering; no amount of package annotation reflection repairs lost candidates. Literal-only support might need much less metadata. Preserve current
constraintVersion/decimalVersion/projectionVersion responsibilities; a naming decision must not weaken their checks or
couple them to a new umbrella version. Advisory deprecation absence differs from required language-fact absence.

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
| Literal-only transport | If B is selected, one temporary test-only fixture family on G3/4/5: API/base, derived root and owned child in separate JARs. Cover field/key/operation rename/exclude, equivalent/conflicting bridge facts, inheritance/diamond, and deleted sources/mirrors/compiler outputs. Inspect only public facts without compiler/Jackson databind on the consumer path. Decide reuse versus minimal required metadata and measure classfile delta. |
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
input and JSON Schema only; a Jackson profile adapter is not required. Focused Klum name/exclusion annotations belong
in the same slice, retaining compatible `JsonProperty`/`JsonIgnore` input. Equivalent declarations coalesce;
contradictions fail with both origins rather than silently selecting Klum or annotation order. No release placement changes.

Remaining decisions, one branch at a time:

1. **Vocabulary/algorithm:** select annotation/package and finite profile identifiers; settle the remaining punctuation,
   Unicode and malformed-input rules around the accepted acronym/digit boundaries. Identity must be explicit as well as
   the unconfigured default; explicit identity binds descendants. No full Jackson strategy compatibility is implied.
2. **Entry authoring:** finalize the selected Klum literal annotations' names, retention and exact field/key/admitted-method
   targets; review the remaining edge combinations under the accepted coalesce/error rule. Existing Jackson literals remain supported.
3. **Transport and delivery:** qualify exact declared-profile/absence/provenance facts and old/missing/unknown metadata,
   then approve minimal completeness/version handling and source-free binary acceptance before a separately assigned
   implementation. No public descriptor, new issue, milestone or release commitment is selected here. A delivered
   feature later needs documentary `@Issue`/`@Tag`/`@See` coverage, user/migration navigation and changelog updates.

## Delivery and validation boundary

This task changes the local decision brief and navigation pointers only. ADR 0031 records accepted need, inheritance
and composed-type scope, accepted kebab boundaries, collision failure, structured-input/schema-only scope, and the
selected Klum literal authoring family with compatible Jackson inputs.
Remaining profile semantics are explicitly unresolved and installed behavior is unchanged.
Tracker impact: Related #959; no issue comment/state/label/milestone or curation change. No production code, annotations, executable fixtures or user-facing release claims are changed. No implementation
PR exists. Local committed delivery is sufficient while maintainer choices remain open; no draft PR is required.

The original local brief passed 68 relative Markdown targets/anchors, fences and whitespace/scope checks.
The inheritance update passed 69 relative Markdown targets/anchors across three changed documents, balanced fences,
`git diff --check` and documentation-only scope review. The kebab/collision/slice-scope checkpoint also passed all 69
relative targets/anchors, balanced fences, whitespace and documentation-only scope checks. The selected Klum literal/
bridge-conflict checkpoint passed those same checks. No runtime tests were run.
Groovy lanes and schema-generation runs are omitted under the documentation-only testing rule. Runtime behavior above
is source/test inspection evidence, not a claim of newly executed tests. The final callback records exact commit and
check outcomes; Hive reconciliation is not requested while this resumed maintainer discussion remains active.
