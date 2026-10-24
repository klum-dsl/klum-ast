# Constraint and default projection: feasibility and decision gates

Date: 2026-10-24. Status: **D1 accepted; P0 evidence recorded; D2–D6 and production implementation remain unapproved.**
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959), including its
[design input](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6385826650).
Release: 4.2 candidate only; no milestone assigned. Issue #959 remains open.

## Accepted D1: metadata authority and missing artifacts

Maintainer decision, 2026-10-24, following the [P0 binary metadata evidence](evidence/issue-959-p0-binary-metadata.md):

- **Option A only:** the compiler must enrich the generated public Builder contract with minimal inert,
  source-associated facts. The generator reads that contract only as its constraint/default metadata authority.
  Declaring Model class-byte reading is excluded as a production authority and as a fallback.
- **Existing structural generation stays as-is.** When a consumer explicitly enables the future enhanced
  constraint/default projection profile and a required Schema artifact lacks the new metadata, generation must fail
  closed with actionable guidance to recompile and package that Schema artifact with the required metadata.
  Missing facts must not be silently omitted. No compatibility or reporting mode is introduced now.

P0 demonstrated that inert capture can preserve the tested declaration sites and relationship-use placement without
consumer Model-byte inspection. That supports A while preserving [ADR 0031](../adr/0031-structured-schema-input-artifacts.md)'s
public-contract inspection boundary. B's ability to recover facts from current Model bytes does not justify broadening
that boundary or making incomplete enhanced output appear authoritative. Gating failure on explicit future enhanced
projection preserves the current structural generator's behavior for existing artifacts.

D1 settles metadata authority and this missing-artifact rule. It does not select the production encoding, exact generated
ABI, emission hook, profile configuration, supported mappings, raw-input assertion strength, default meaning or public SPI.
D2–D6 remain unapproved, and P1 has not started. Any future implementation must satisfy D1; this documentation update
contains no compiler, generator, runtime or test changes.

## Recommendation and evidence boundary

The remaining design discussion is **unapproved proposal** unless explicitly covered by accepted D1 above. Do not adopt
a projection SPI or start production mapping from this document. P0's bounded experiment is complete; how completed-model
constraints become optional raw-input assertions remains a separate maintainer decision under D2.

The smallest plausible production slice is opt-in projection of direct Jakarta `@Min`/`@Max` on integral properties,
plus deterministic descriptions for discovered unsupported constraints. It needs neither a provider SPI nor runtime
validator construction. Decimal bounds, cardinality, patterns and literal defaults are subsequent, separately gated
slices. This is a recommendation, not an approved feature list.

The maintainer's relationship refinement is assessed below as an additional design question: constraint and default
contributions may share placement context, but do not automatically share extraction or execution semantics. It does
not extend P1 or authorize a public contributor interface.

This inventory inspects source and existing executable specifications at KlumAST commit
`28f2d7ed6e8d4b75bd67a5b20e5a4d40ffa460d2`. The subsequent P0 experiment and its historical Groovy qualification are
recorded in the [evidence report](evidence/issue-959-p0-binary-metadata.md). This D1 reconciliation is documentation-only;
no binary experiment or Groovy lane is rerun here. Neither the inventory nor P0 implements the projection described below.
Live issue #529 is closed after #949/#951; ADR 0031 and the JSON Schema user page retain older consumer-gate status text.
That historical status is not a new dependency or authority to reopen #529.

Accessible Schelm `schelm-skeleton/DESIGN-NOTES.md` (constraint/default and custom-projection sections) and
`KLUMAST-NOTES.md` (annotation member defaults) were read at its clean local revision
`d6acc8a506773d1ea8ea55c656d7b01f4984b733`. The stable tracker entry above carries the relevant consumer input.
These notes propose percentage bounds, quantity spelling checks and contextual defaults; they do not select KlumAST
APIs, quantity policy or release scope. No Schelm build or consumer acceptance is claimed here.

## Current metadata and execution seams

| Fact | Current evidence | Consequence for #959 |
| --- | --- | --- |
| Admitted input | Private [`CompiledBuilderSchema`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java) reads public Builder/Factory signatures and exact declaration bytes. `Entry` retains external name, role, source, declaring contract, selected methods, recursive payload, key facts and documentation. | Reuse admission and names. There is no constraint/default list or arbitrary metadata accessor in this record. A one-payload authored operation is not necessarily a Model property. |
| Annotation decoding | Its `metadata`, `MetadataVisitor` and `MethodAnnotationVisitor` select generated input facts, operation/query markers, literal Jackson rename/ignore and AnnoDoc. Exact module resource and origin checks avoid unrelated enum annotation proxies. | This is not a general Bean Validation descriptor reader. Adding `getAnnotations()` everywhere could initialize enum classes and violate existing no-execution evidence. |
| Ordinary property constraints | [`TransformedBeanValidationTest`](../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/TransformedBeanValidationTest.groovy) asserts `@Min` on the completed Model field and no annotations on its hidden Builder field. [`AnnotationCopyExceptions`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AnnotationCopyExceptions.java) excludes Jakarta/legacy validation and directly constraint-marked custom annotations from Builder fields. | Runtime retention does not imply availability on a permitted generator inspection target. Do not inspect Model fields as an undocumented workaround. |
| Public methods | [`PropertyAccessors`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/PropertyAccessors.java) creates Builder accessors and documentation without copying all Model field annotations. [`GeneratedDslSupport`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java) copies implementation-method and parameter annotations to public contracts. | Authored method/parameter metadata may survive; it does not establish property-constraint coverage. Getter return constraints, setter parameters and Model fields are different declaration sites and must not be merged by spelling. |
| Relationship and type defaults | [`DefaultPhase`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DefaultPhase.java) consumes annotations on the containing Builder field and Model type hierarchy. Nonvalidation domain annotations can be copied to hidden Builder fields by `moveSingleFieldStateToBuilder`. | The runtime route is not a public Builder metadata route. Type and relationship annotations, `@DefaultValues`, their members and Java `AnnotationDefault` values need a separately qualified extraction route. |
| Relationship constraints | [`RelationshipConstraint`](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/RelationshipConstraint.java) marks a domain annotation with a closure class. [`KlumFieldAnnotationsValidator`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/validation/KlumFieldAnnotationsValidator.java) reads the completed owner's declared fields and invokes that closure for each non-null target, recording failures on the owner field. | The marker identifies runtime behavior, not a structural predicate or child-member mapping. Literal attributes such as `minSlots` cannot establish what the closure checks. The public metadata availability gate applies here too. |
| Inheritance / binary names | `CompiledBuilderSchema` resolves declaring contracts before descendants, groups by source identity, applies exclusions and diagnoses external-name collisions. Source identities include Model binary name and native field or method descriptor. | Attach facts to the original declaration, then the effective admitted entry. Do not use only a simple name or inspect a subclass's fields to reconstruct inherited facts. Binary resources need no source/Javadoc JAR. Source line numbers are not guaranteed. |
| Runtime validation | [`JSR380Validator`](../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/internal/JSR380Validator.java) constructs a ValidatorFactory and calls `validate(instance)`; its service registration is `InstanceValidator`. Payloads become Klum levels through [`Level`](../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/Level.java). | **There is no projection/description SPI.** Neither calling this adapter nor bootstrapping a provider to obtain descriptors belongs to the metadata-only path. The module uses Jakarta API 3.0.2 / Hibernate Validator 8.0.3.Final. |
| Generator output | Private [`JsonSchemaProjection`](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java) emits typed properties, exact integral bounds, descriptions, reusable definitions and unsupported-operation diagnostics. Its JSON writer currently accepts String, Map, List, Boolean and BigInteger. | No current default/constraint projection. Decimal output needs exact BigDecimal rendering; literal defaults need deliberate scalar/array support. Existing type schemas reject explicit null; properties may be absent. |

The minimum missing fact is a constraint/default declaration plus its exact source-site association. P0 compared small
compiler enrichment on existing public declarations against a narrowly scoped exact Model-class byte reader. Accepted
D1 selects public Builder enrichment only; the Model-byte route is historical comparison evidence, not a production
option or fallback. Copying actual Bean annotations onto Builder methods can change executable-validation meaning or
violate annotation targets; inert facts must preserve original placement. D1 does not select an encoding or authorize a
broad companion carrier or wider public runtime API.

The runtime relationship facade is not an extraction shortcut: an owning declaration obtained from a completed object
or active Builder requires the very construction/context the generator must avoid. Keep static relationship use,
source owner, native child member and effective input name explicit in the proposed metadata.

## Three outcomes and conservative resolution order

For a future approved projection, the proposed outcomes for each discovered effective constraint are: **hard fragment**, **runtime description**, or
**unsupported metadata diagnostic**. Structural input rejection is a separate outcome. An unsupported constraint does
not justify dropping an otherwise admitted property, and text is never a validation assertion.

1. Inventory any existing projector first. None exists here. Do not create one merely to fill this priority slot.
2. Apply a known mapping only when declaration site, attributes, raw shape and validation profile satisfy its support
   contract. Any future explicitly enabled projector would run before this step for that constraint only.
3. Append deterministic runtime-constraint text for a discovered unmapped rule, preserving all existing JavaDoc-derived
   description. Incomplete required metadata fails with location/dependency guidance rather than inventing a rule.

Proposed fallback example: `Runtime constraint (not enforced by this schema): @Range(min=5, max=10)`.
Use qualified annotation identity where short names collide, sort attributes by name and independent declarations by
source/type/canonical attributes, and preserve array order. Render strings with escaping, enum constants symbolically,
class values as binary names and nested annotations structurally. Include effective relevant defaults, groups, payloads
and raw message templates; do not interpolate bundles, evaluate expressions, invoke custom `toString`, or initialize
annotation class/enum values. Opaque closures remain symbolic references, never translated logic. Recognize constraints
through an approved finite metadata traversal (`@Constraint`, Klum validation and relationship markers), not every
annotation; recursion/cycles, missing annotation definitions and malformed repeatable containers need bounded diagnostics.
Absent required metadata is different from a discovered unsupported constraint: accepted D1 requires failure when the
future enhanced profile is explicitly enabled and its required Schema metadata is missing. Descriptions are not a
compatibility/reporting escape hatch for that failure; their semantics still require approval.

Combine independent hard fragments conjunctively (`allOf` or a proven intersection), retaining the original type and
machine bounds. A provider cannot replace another annotation's restriction, admission, names or `$defs`. Contradictory
bounds must produce a deterministic location-rich failure, not last-wins behavior or silently lost constraints. The
normalizer must distinguish one declaration copied to multiple generated methods from truly repeated constraints.

## Candidate mappings and semantic gates

The following is a proposed support matrix, not current generator behavior. The standard's
[constraint definitions and composition rules](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)
are the starting point, not a promise that runtime provider extensions are portable.

| Constraint / metadata | Candidate hard result | First-slice disposition / gate |
| --- | --- | --- |
| Direct `@Min(value)` / `@Max(value)` | `minimum` / `maximum` intersected with existing integral bounds | First production candidate: byte/short/int/long, boxed counterparts and BigInteger; empty groups and payload only. Reject invalid metadata; describe unsupported applicability. |
| `@DecimalMin(value, inclusive)` / `@DecimalMax(value, inclusive)` | Exact decimal inclusive bound, or `exclusiveMinimum` / `exclusiveMaximum` | Later: parse decimal strings exactly, never through double; test positive/negative exponents, trailing zeros, exclusive boundaries, integral intersections and contradictions. Restrict to supported integral/BigDecimal inputs; exclude numeric Strings and float/double provider approximations. |
| `@Size(min, max)` | Potential `minItems`/`maxItems` or `minProperties`/`maxProperties` | Later, only when the raw container maps without coercion/deduplication and defaults/layering are explicitly accounted for. A raw List populating a Set does not preserve cardinality. |
| String `@Size` | Potential length bounds | Description first. UTF-16 length differs from JSON character counting; supplementary characters make blind min/max translation incorrect. See [JDK String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html). |
| `@Pattern(regexp, flags)` | Potential ECMA pattern over String | Description first. Require a defined common grammar, empty or explicitly supported flags and full-match conversion. No arbitrary Java-regex copying. |
| `@NotNull`, `@Required`, `@NotBlank`, `@NotEmpty`, `@Valid` | None in first slice | Never infer raw-input `required`. Presence, cascade, Groovy truth and nullable final validation are distinct. No implicit null branch is added. |
| Custom, class/cross-field, relationship-closure, getter/return/parameter/type-use constraints | None in first slice | Deterministic description at the correct property/relationship or enclosing object; do not infer a property from a method name or lift element constraints onto containers. |

Groups, default-group redefinition/sequences, group conversion and provider XML/programmatic overrides can change
runtime applicability. The candidate first profile supports direct annotation semantics only, empty groups/payload,
and no discovered group redefinition; unsupported cases remain text. Explicit Default groups, inherited group rules,
payload-bearing ERROR and all warning/INFO/NONE/DEPRECATION/custom payloads require a separate selection policy. The
runtime adapter currently calls default validation, but that does not make every compiled annotation an unconditional
error. A generated hard assertion also does not reproduce Klum's configurable verification threshold.

For repeatable annotations, normalize recognized containers and preserve every occurrence; never take the first or
last. First slice may describe the entire repeatable declaration rather than partially asserting it. Composed
constraints require recursive composition, attribute overrides (including indexed overrides), inherited groups/payload
and reporting semantics; first slice describes the whole composition. Do not project a convenient inner `@Min` while
ignoring the containing warning/group semantics. Multiple target-specific validators and cross-parameter validators
must never be selected by reflection order. No `ConstraintValidator.initialize` or `isValid` call is a metadata query.

Even ordinary bounds are not generally equivalent before and after lifecycle: a later layer or `@PostTree` can replace
an out-of-range supplied value. Maintainer decision D2 below must define an opt-in **authoring restriction on supplied
values**, not claim equivalence to completed validation. D2 remains unapproved; description-only production projection is not authorized either. Omitted
properties, explicitly supplied null, empty String, zero and false require separate fixtures; zero is a valid endpoint
for the percentage example. Existing input types/null admission remain authoritative. This task neither consumes nor
implements [#962 nullable final-validation semantics](https://github.com/klum-dsl/klum-ast/issues/962).

JSON Schema patterns use ECMA conventions and match substrings unless constrained; see
[core regex guidance](https://json-schema.org/draft/2020-12/json-schema-core#section-6.4) and
[validation keywords](https://json-schema.org/draft/2020-12/json-schema-validation#section-6.3).
Java flags, Unicode classes, lookarounds, quoting, embedded flags and end-anchor treatment need explicit rejection or
qualification. A naive `^...$` wrapper is not proof of full matching, especially with a trailing line terminator.
Test identical corpora with Java/Hibernate and a pinned ECMA engine as well as a Draft 2020-12 validator. Existing Python
schema validation alone cannot establish ECMA regex parity.

## Optional custom projection versus declarative description

| Option | Value and cost | Recommendation |
| --- | --- | --- |
| Known mappings + generic metadata text | Small private plugin implementation; no validator/provider lifecycle | Preferred first path after the metadata gate. No new public SPI. |
| Projection callback on a custom ConstraintValidator | One author can keep domain logic and projection together, but validator selection and construction become build-time contracts | Defer. This is **deliberate build-time user-code execution**, an explicit extension to ADR 0031, not reflection-only. |
| Separate declarative description provider | Static annotation/resource declarations can explain domain intent without constructing runtime validators; no hard restrictions | Assess only if generic fallback is insufficient. Prefer literal templates with declared attribute substitution and exact classpath associations. A Java callback returning text still executes user code and needs the same explicit boundary. |

If a later decision admits callbacks, supply immutable effective annotation/descriptor data, admitted raw-input type,
declaration site, external name and relationship location. Supply **no Model or candidate values**, `isValid`, Builder/
Model construction, defaults, lifecycle, DI or production context. Define instantiation and initialization separately
from the runtime Bean Validation factory: the callback constructor/static initializer itself may execute user code.
No claim of sandboxing or determinism follows from a classloader. Selection must be exact, reject ambiguity among
validator target types, and define composition/override precedence. An explicit projection can supersede the built-in
mapping for the same constraint, but must compose with other constraints and base structure.

An admitted provider's exception, invalid fragment or unsupported dependency must fail with provider/source/location
context; do not silently fall back after failure. Provider implementations, options and declared resources become Gradle
inputs, with a cache policy for external effects and state. Undeclared environment/files/network cannot be assumed
cache-safe. A declarative resource reader has fewer execution concerns but still needs version, association, missing-
resource and substitution semantics. Neither option is necessary for numeric bounds or annotation text.

## Shared relationship context, separate semantic projectors

**Recommendation:** probe one small private placement context and typed contributions, with separate constraint and
default interpretation. Do not select a universal public contributor API. Shared placement is justified by current
evidence: [`RelationshipConstraintDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/RelationshipConstraintDocumentaryTest.groovy)
applies both `@PoolBounds(minSlots = 5, maxSlots = 10)` and `@PoolDefaults(slots = 8)` to `Plan.pool`. The former executes a
closure against the completed child; the latter supplies construction defaults. Their common owner field does not
make their semantics interchangeable.

The same conservative output seam could carry a hard fragment, runtime-constraint description and literal default at
that relationship use. Today, an arbitrary `@RelationshipConstraint` qualifies only for metadata description, once
discovery is available. No attribute-name heuristic, closure inspection or invocation can promote `minSlots` to a
`minimum` on `slots`. A future hard mapping needs a separately approved declarative association that proves its target
and portable raw-input meaning, or an explicitly enabled code-executing projector. Literal defaults need the independent
eligibility/precedence checks below. Existing opaque closures remain runtime-only in either design.

### Minimal context and contribution candidates

These are conceptual data fields for P0, not proposed Java signatures or a new runtime descriptor protocol.

| Context fact | Why both candidates need it |
| --- | --- |
| Owning declaration identity | Original declaring Schema binary name, native relationship field and annotation occurrence/site, including inherited origin. Type-only lookup would merge different relationships to the same child. |
| Effective admitted input | Parent public Builder contract, role, effective external property name and selected operation descriptor(s); input parameter position where relevant, or an explicit field/population association. An authored operation with no proven field association cannot acquire one by matching names. |
| Raw shape and target | Admitted scalar/object/container shape, raw value type, child public contract and child effective-name lookup where applicable. Preserve excluded and unresolved cases; do not reconstruct input shape from the completed Model. |
| Placement within the use | The relationship property, an admitted collection item/map value, or an explicitly resolved descendant member. Preserve the containing use when descending; no Model value, live Builder, Owner instance, concrete map key or runtime index is available. |
| Provenance and profile | Symbolic annotation values, contribution identity, support/profile decision and diagnostic source. Existing description is renderer-owned; contributors do not replace it. |

The emitter should resolve a bounded semantic target relative to the owning relationship, rather than allow arbitrary
JSON pointers or mutation of a shared schema map. A direct relationship contribution lives under the parent's effective
`properties` entry. An element rule for an admitted keyed map lives under that property's value `additionalProperties`,
not on the map container; an array's item route is available only if that relationship shape is already admitted.
Descendant names resolve through the child's admitted entries. Excluded LINK/OPTIONAL_LINK or unsupported relationship
shapes remain excluded, with source diagnostics; projection must not introduce properties to host their annotations.

Keep the reusable child `$ref` and add a local overlay. A nested contextual overlay must retain the outer use all the
way down; it must not mutate a child `$defs` object reached through that reference. Reusing the same owner contract can
reuse its declaration-local overlay, but a cache keyed only by child type is invalid. Recursive ownership needs bounded
definition/reference handling rather than expansion of every possible root-to-child path.

One possible private contribution envelope contains provenance, a relative semantic target, and separately typed
assertion, description or literal-default data. It can carry several results for one declaration without conflating
them. The renderer owns placement, composition and serialization; semantic projectors cannot change admission, names,
type identity or another contribution. A description of a default is labelled as a declared contextual fallback,
not as a runtime constraint. No public type names, registration, provider discovery or encoding are selected.

### Unified contributor versus separate seams

| Alternative | Benefit | Cost / disposition |
| --- | --- | --- |
| One unified contributor API for arbitrary annotation families | Shared context and output vocabulary; one dispatch mechanism | Risks universal annotation discovery, capability flags, broad mutable fragments and implicit callback execution. Current examples do not justify a public SPI. Defer. |
| Fully separate constraint/default projectors and renderers | Each models its own eligibility and precedence | Duplicates names, relationship traversal and overlay placement; increases the risk that one writes to global `$defs`. Avoid separate placement engines. |
| Separate private semantic projectors sharing context and renderer | Reuses proven input positions while keeping assertion conjunction and default selection distinct | Recommended probe candidate; introduce only the facts needed by the two fixtures. A common data envelope does not imply a common provider lifecycle. |

Built-in metadata interpretation and static description declarations retain the non-executing boundary. A Bean Validation
validator callback, or any relationship/default projector implemented as user code, remains deliberate build-time code
execution even if it uses this same envelope. Sharing context grants no permission to invoke validators, relationship
closures, defaults, Templates, lifecycle, Model construction, DI or owner/environment lookup. An optional callback must
meet the separate execution contract above; it cannot silently join the reflection-only path.

### Several contributions at one position

| Combination | Proposed deterministic rule |
| --- | --- |
| Several assertions | Conjoin at the same resolved target, preserving base schema restrictions. Diagnose contradictions detected by supported mappings. Do not claim a general satisfiability solver for arbitrary fragments; unsupported composition remains a diagnostic. |
| Several descriptions | Append after existing prose in stable provenance order. Suppress only duplicated extraction of the same occurrence; retain distinct repeatable declarations and disambiguate their origins. |
| Several literal defaults | Equal normalized literals at one target may share one emitted annotation while retaining origins. Conflicting values produce a diagnostic and description-only fallbacks unless an approved static precedence rule selects one. `allOf` does not resolve competing default annotations. |
| Assertion plus default | Do not weaken an assertion to accommodate a default. Check the literal against supported structural fragments; if inconsistent or compatibility is unprovable, withhold the default annotation and explain the reason. Execute neither runtime validation nor a provider to settle it. |
| Competing projectors | One authoritative selection per annotation occurrence and contribution kind. An explicitly enabled custom constraint projector replaces that constraint's built-in mapping only; ambiguous custom providers fail. Independent declarations still compose. |
| Invalid target or metadata | Never retarget to a child type or similarly named property. Malformed/incomplete required metadata fails with source context; a known unsupported form yields a visible omission/description without changing admission. |

P0 should compare two differently named parent relationships to one child contract, each bearing bounds and defaults;
then cover inherited/renamed owner fields, renamed/excluded child members, nested uses, recursion, unsupported LINK and
admitted map-value placement. Include same-target and different-target assertions, copied versus repeated annotations,
equal/conflicting defaults and assertion/default conflicts. The existing
[`RelationshipConstraintContainersTest`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/RelationshipConstraintContainersTest.groovy)
establishes runtime per-entry/source-owner semantics, not JSON Schema admission or portable closure projection. New
probe expectations must assert distinct parent overlays, unchanged shared `$defs`, deterministic output and silent
execution traps. These are evidence-bearing P0/P3/P4 candidates; none broadens P1.

## Literal defaults retain relationship context

[`AnnotationHelper.getNonDefaultMembers`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/AnnotationHelper.java)
compares `Method.getDefaultValue()` with the applied member using `equals`. For scalar members, omitted values and
explicit values equal to that Java annotation default are both skipped. Required members and unequal values survive.
Arrays use Java array equality here, not deep equality: do not infer scalar sentinel behavior for arrays. Qualify this
behavior without changing it under #959. `valueTarget` remaps member **`value`**, despite the marker's Javadoc saying
`values`; an empty/unknown target, virtual setter or required coercion is not an ordinary literal property default.

`DefaultPhase` runs type participants, owner-provided defaults, relationship annotations, type-hierarchy annotations,
field defaults and lifecycle defaults. It fills values according to actual emptiness and coercion rules. A literal
member is thus a declared contextual fallback, not proof of the eventual completed value. Multiple annotations,
pre-populated values, Template/owner state and lifecycle changes can make a unique effective default unknowable.
Field initializers, even apparently literal ones, have no supported public constant-default metadata and are excluded.

Recommend a later first default slice limited to unambiguous required/unequal scalar literal members on direct concrete
owned relationships, targeting admitted ordinary child properties with exactly compatible JSON types. Decode enum names
symbolically without enum initialization. Defer arrays, type-hierarchy defaults, coercion, arbitrary setters, conflicts
and nested contexts until characterized. Never run closures, `@DefaultApply`, Templates, owner/environment access,
AutoCreate or any lifecycle to discover a value. Unknown ignored members must not become invented input properties.

The Schelm relationships order/result/cancellation use partition defaults 8/4/2 for the same `WriteOnlyTopic`. A candidate
relationship-local overlay can keep the shared definition intact:

```json
{
  "allOf": [{"$ref": "#/$defs/example.Topic_DSL%24Builder"}],
  "properties": {"partitions": {"default": 8}}
}
```

This is a shape proposal, not generated output or an agreed global default rule. Resolve `partitions` through the child's
effective external name; omitted/excluded targets get diagnostics. Apply 4 and 2 at their own relationship uses. For
keyed maps the context would be under value `additionalProperties`, not the map itself; that extension needs separate
fixtures. Never mutate the reusable `$defs` child to record a parent's default. Do not mark a relationship required or
pretend that a child exists because AutoCreate may run later.

JSON Schema `default` is annotation metadata, not value insertion; see its
[metadata vocabulary](https://json-schema.org/draft/2020-12/json-schema-validation#section-9.2).
Maintainer D4 must decide whether a contextual declared fallback is useful enough to emit standard `default`, with clear
prose about precedence, or whether unresolved precedence warrants description-only output. Explicit 0/false/empty,
omitted member, explicit sentinel-equal member, `valueTarget`, renamed/excluded child, conflicting defaults and annotation-
default evolution are required tests. A change to Java annotation defaults must invalidate the generated artifact.

## Consumer cases without consumer policy

Percentage probe:

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

@DSL
class Capacity {
    @Min(0L) @Max(100L)
    Integer percentage
}
```

The candidate schema accepts supplied 0 and 100, rejects -1 and 101, preserves omitted-property admission and rejects
explicit null through the existing input type. A separate runtime fixture must demonstrate that bounds allow a completed null;
`initial <= max` remains object-level runtime logic. Test numbers as strings, fractional input and values outside the
Java target range without asserting that schema acceptance guarantees binding.

For quantity evidence, retain CPU examples `125m`, `250m`, `500m`, `1`, `2` and memory examples `256Mi`, `512Mi`, `768Mi`,
`1Gi`, `2Gi`, `4Gi`. These establish demand, not a grammar. Schelm must choose its subset or full Kubernetes vocabulary;
KlumAST must not embed CPU/memory annotations, suffix tables or parsing policy. A future portable-pattern probe should
classify decimal CPU, `0.5m`, exponent quantities, case-sensitive `M`/`m`, zero, negative input, whitespace and trailing
newlines before declaring acceptance. Kubernetes documents CPU precision and memory units in
[resource units](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes).
Lexical recognition cannot alone prove positivity, overflow or a relationship such as `memory <= maxMemory`.

## Conditional slices, fixtures and commit boundaries

P0's bounded metadata experiment is complete and recorded in its [evidence report](evidence/issue-959-p0-binary-metadata.md).
The table retains its probe contract for traceability. P1–P4 remain conditional proposals requiring separate decisions
and explicit scheduling; accepting D1 does not start them.

| Slice | Scope and reasoned commits | Executable acceptance / exit gate |
| --- | --- | --- |
| P0: completed bounded probe | Test-only binary metadata experiment and separate evidence report; D1 now reconciles metadata authority. No production generator behavior. | Separate Java annotation/API JAR, transformed Schema JAR and source-free consumer; inspect exact field/getter/parameter/type-use/type sites and annotation defaults. Compare small enrichment candidate versus targeted byte-reading necessity; stop if neither respects approved architecture. |
| P1: unapproved production candidate | One vertical commit for qualified inert metadata + private integral mapping/text + tests; a separate user-doc/release-note commit. No SPI. | D1 accepted; D2 approval and explicit P1 scheduling still required. `@Issue('959')` percentage fixture with boundary/type/null/omission cases, exact old machine bounds, repeatable/composed/group/payload fallback, rename/ignore/inheritance and unchanged source attribution. Preserve previous output on generation failure. |
| P2: decimal/container/pattern subsets | Independently reasoned vertical commits only for each newly approved mapping; no obligation to bundle them. | Exact decimals and inclusive/exclusive intersections; collection population/cardinality; UTF-16/supplementary length cases; Java/ECMA full-match corpus including flags and trailing newline. Unsupported variants remain visible text. |
| P3: contextual defaults | One narrow relationship-literal overlay commit + matching docs, after D4 and P0 evidence. | Shared child with 8/4/2 relationship defaults, no `$defs` leakage, aliases, sentinel equality, Java defaults, enum traps, `valueTarget`, 0/false/empty and unsupported coercion/conflict; prove JSON validation does not populate defaults. |
| P4: optional extensions | Only if D3 later selects callback or declarative provider; draft its own ADR/API before code. | Exact multi-target selection, repeatable/composed semantics, no Model/context inputs, failure diagnostics, explicit code execution and classloader/cache/resource contract. No automatic follow-on from P1. |

P0 fixtures must include local and separately compiled inherited properties, domain annotations with restricted FIELD
targets, Java annotation defaults, repeatable containers, composed overrides, unknown constraints, class/enum/nested
attributes, inaccessible/missing annotation dependencies, same-name collisions and ignored/renamed relationships.
Use static-initializer/constructor/enum/default/closure/validator traps with positive controls, not just absence of a
side-effect file. The public-method probe must distinguish method-return from input-parameter constraints. Snapshot
actual bytes/locations and expected normalized facts; absence on public contracts is a valid negative result.

Reuse [`KlumJsonSchemaGenerationTest`](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy)
for packaged API/Schema/consumer isolation, no sources/mirrors, initialization controls, deterministic outputs and Gradle
classpath invalidation. Reuse the existing `validate_json_schema.py` harness for Draft 2020-12 acceptance; add a pinned
ECMA check only for pattern work. Runtime characterization belongs alongside `TransformedBeanValidationTest`,
[`BeanValidationDocumentaryTest`](../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/BeanValidationDocumentaryTest.groovy),
[`RelationshipConstraintBeanValidationTest`](../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/RelationshipConstraintBeanValidationTest.groovy)
and [`DefaultValuesDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/DefaultValuesDocumentaryTest.groovy).
Do not turn their runtime construction into generator discovery.

Every new executable class uses `Test`, carries `@Issue('959')`, and any later public feature has a documentary example
with `@Tag('documentary')` / `@See`. Run focused Groovy 3 checks, then affected modules and Groovy 4/5 at slice completion;
qualify binary inheritance and Groovy 4/5 named modules without opening hidden implementations. Generated enrichment
must implement D1's failure policy for explicitly enabled enhanced projection and receive matching compiler/runtime/plugin
compatibility tests. Preserve
existing Builder signatures, runtime validation, lifecycle, ownership, serialization and structured-input dispatch.

Future feature delivery updates [JSON Schema](../user/Json-Schema.md), [Validation](../user/Validation.md),
[Default Values](../user/Default-Values.md), applicable [migration navigation](../user/Migration.md), `CHANGES.md` and
issue evidence together. This research changes no current feature guidance, release notes, API, production code or test
fixture. No production behavior or implementation PR is authorized by a merged feasibility document.

## Decision status and remaining gates

Only D1 is accepted. D2–D6 below remain recommendations, not delivery or implementation authorization.

| Decision | Status / proposed answer | What remains blocked |
| --- | --- | --- |
| D1: metadata authority and missing artifacts | **Accepted:** Option A only, compiler-owned minimal inert source-associated facts on the generated public Builder; generator reads that contract only. No production Model-byte authority or fallback. Existing structural generation stays as-is; explicit future enhanced projection fails closed on missing required Schema metadata, with recompile/package guidance. No compatibility/reporting mode now. | Production encoding, exact fact set, generated ABI/emission hook and compatibility qualification; D2–D6/P1 are not approved. |
| D2: raw-input strength/profile | Opt-in authoring restrictions on supplied values; initial direct integral annotations, default groups/empty payload, with deterministic runtime text for other discovered constraints. Preserve null/admission and no inferred required. | Whether lifecycle-overridable values may intentionally be rejected earlier; configuration spelling, activation default and diagnostics contract. No production projection, including description-only behavior, is authorized. |
| D3: custom extension | Defer callbacks and description SPI; first establish useful generic text. | Any build-time user-code execution or new public provider API. Later callback approval must amend the no-execution promise explicitly. |
| D4: default meaning | Probe scalar relationship literals; initially describe ambiguous precedence. | Whether unambiguous declared fallback may use standard `default`; array/coercion/type-level support and handling multiple sources. |
| D5: delivery and release | Keep #959 open and untargeted; P0 evidence is complete, while later delivery needs separate scheduling. | Milestone/4.2 commitment, further architectural decisions and implementation publication beyond this authorized D1 documentation delivery. Quantity grammar remains consumer-owned; #962 remains separate. |
| D6: shared relationship projection | Probe separate private semantic projectors over a small shared placement context and renderer; defer a unified public contributor SPI. | Exact context/contribution shape, collision policy and any hard mapping for relationship annotations. Evidence from these candidates does not authorize extending P1. |

Primary risks are false restrictions on raw input, incomplete binary metadata mistaken for no constraints, accidental
user-code execution through annotation proxies/providers, and relationship annotations leaking into shared definitions.
The gates above are deliberately smaller than a validation framework. No new module, runtime Bean Validation changes,
generic descriptor API, closure translation or Schelm policy is justified by the current evidence.
