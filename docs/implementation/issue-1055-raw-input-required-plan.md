# #1055: raw-input field presence before 4.2 RC

Date: 2026-11-08. Status: **Design and implementation plan only; no production change or feature qualification**.
Category B extension of [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#raw-input-field-presence--1055).
Authority: [#1055](https://github.com/klum-dsl/klum-ast/issues/1055) and the maintainer's explicit reopening of 4.2 RC
preparation for this bounded feature. Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959).
Source inspection base: `85acbb45b` (master). Implementation below is proposed, not evidence of delivered behavior.

## Contract and syntax

Recommend adopting the provisional spelling `@Struct.Required` as a FIELD-only, `@Documented`, RUNTIME-retained marker
in `com.blackbuild.klum.ast.Struct`. No members, propagation machinery, validation transform or annotation collector.
One already admitted field opts into raw-input presence in generated JSON Schema. Example for the future documentary test:

```groovy
@DSL
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
class Service {
    @Struct.Required
    @Struct.Name('service-name')
    String serviceName = 'local'

    @Struct.Required
    boolean publicAccess
}
```

The Service object definition would have `required: ['public-access', 'service-name']`. Its property schemas remain
unchanged. An editor/explicit validator rejects omission even though an initializer can supply `serviceName` during
construction. `Service.Create.FromStruct([:])` and ordinary `Service.Create.With { }` retain their current behavior.
`publicAccess: false`, numeric zero and empty String are present values; no truth test is involved.

The marker does not change `FromStruct`, active Builder `applyStruct`, automatic JSON Schema validation, null admission,
normal Groovy DSL behavior, default/Template/lifecycle behavior, `@Required`, Bean Validation or Jackson mapping.
It neither admits new fields nor expands Builder method/operation grammar. A complete matching contract may import
input which its generated schema rejects; validation is deliberately editor/client owned.

## Confirmed current representation and seams

These observations come from current source and executable specifications, not a new build or a binary experiment.
The [unified-protocol evidence](issue-959-unified-metadata-protocol.md) remains a historical qualification record;
S1's [contract separation](issue-959-schema-s1-contract.md) identifies today's reader/resolver responsibilities.
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) governs ordered execution, superseding payload preflight.

| Stage | Current exact representation and failure path | Smallest planned change |
| --- | --- | --- |
| Source vocabulary | [Struct.java][struct] has Name/Ignore/Operation/Naming/Examples/ConstraintProjection/ConstraintDescription; no raw presence marker. Existing `@Required` is a separate completed-model concern. | Add only the marker above, without a runtime validator. |
| Compiler classification | [StructuredInputAdmissionPlan][compiler] `classifyField` resolves effective exclusion and current field roles; `excludedField` skips key/Owner/transient/internal fields. `enrichPublicContract` emits explicit `Schema.version=1`; `enrichField` attaches Field to the exact getter or setter fallback, not a Model field scan by consumers. | Capture source presence before excluded-field early returns; diagnose a marker on a non-admitted declaration. Emit true for marked fields; omission means false. |
| Existing field bytes | [GeneratedInput.Field][generated] carries `value`, name origin/rename/ignored, sourceDeprecated, examples, constraints/count/policy, converter and literal-alternative counts, optional relationship/key/exclusion facts. Public types and signatures remain reflected. Key and authored Operation are separate facts, including when an operation shares an accessor. | Add `boolean rawInputRequired() default false` as internal linkage. No mandatory explicit false value or Key/Operation member is added. |
| Exact byte gate | [BuilderContractReader][reader] `Metadata`/`Literals`, mutable decode state and freeze; `validateMetadata` requires exactly version 1 plus current facts, `validateFieldFacts` checks field completeness. `MethodAnnotationVisitor.fieldFact` rejects unknown field members. Annotation reflection defaults do not repair absent required bytes. | Decode an optional boolean with canonical default false; reject wrong-type/duplicate supplied values. Check true against exclusion and source association before any exclusion/policy filtering. Use the existing visitor's seen-state pattern for duplicate detection, without an absent-fact rejection branch. |
| Normalization | [InputContractResolver][resolver] `collectField`/`literalAlternative` select actual admitted field uses; `admitted` resolves name groups; `profileEntry` reconstructs entries after KEBAB_CASE. [CompiledBuilderSchema.Entry][schema] is immutable and distinguishes source from externalName. | Preserve validated presence on field entries in both constructors, inheritance and profile copies. Operations always have false through their explicit constructor paths. Check that every true fact survives as its exact selected field property, rather than attaching it to a same-named authored operation. |
| Runtime consumer | [StructuredInputSupport][runtime] prepares static contract/owned closure before allocation/mutation; reached values then execute in supplied order. Omission is not field validation. | Consume the shared contract, including omitted owned contracts; never branch execution on the presence bit or scan supplied Maps for it. |
| Generator | [JsonSchemaProjection][projector] `definition` creates a closed object, inserts a required member-position construction key, then puts effective entry names in properties. `property` owns payload/constraint rendering. Definitions are keyed by Builder and key position. | In `definition`, union existing key requirement with selected true field names, sort/deduplicate, omit the keyword when empty. Keep property generation unchanged. |
| Gradle boundary | [GenerateKlumJsonSchema][task] resolves explicit roots in an isolated loader; projects all documents before replacing outputs and its manifest. Classpath/tool bytes are inputs. | Retain existing configuration, classpath isolation, cache inputs and failure-before-output-replacement behavior; no new option or dependency. |

The compiler must reject `Struct.Ignore` plus Required deterministically at the source declaration. Apply the same
no-phantom-property rule to effective Jackson exclusion and other non-admitted fields; do not override an exclusion,
invent a property or widen admission to satisfy the marker. Key fields already use the separate construction-identity
contract and remain outside this field policy. An invalid marker placement needs existing compile/contract diagnostics,
not a new key semantics decision. Missing-accessor declarations must be caught before becoming only invisible exclusions.
For binary/tampered contracts, the shared reader/resolver must reject true-plus-excluded or true-without-the-selected
field before construction/mutation/output replacement, with Schema#field, Builder/artifact and recompile guidance.

An inherited field retains the original declaring fact and resolves its external name normally. The current compiler
rejects field shadowing; this feature adds no inheritance override/clearing rule. A descendant naming profile can change
the effective token of an inherited native field; explicit Struct.Name wins. Native aliases must not leak into required.
Use the final selected field identity, including existing collision/operation-selection behavior, rather than matching
source annotations to property names after projection. A required field displaced by an operation is a contract error,
not a requirement on that operation. ConstraintProjection TEXT_ONLY/OMIT does not suppress presence.

An owned relationship's marker requires the relationship member in its containing object, not its contents or items.
An optional relationship containing a child-required field remains optional; once a child body exists its own definition
requires the child field. Map data keys, outer instance keys and literal aliases stay unchanged. Preserve all existing
member/keyless positions: self-contained roots/direct children/keyed sequence items keep their current key requirement;
caller-pre-keyed body roots, fixed/derived and outer-keyed Map bodies omit that key. Field requirements remain in both
positions. Do not overwrite the key requirement, mutate a shared child definition from its parent or impose cardinality.

## Protocol recommendation and RC boundary

**Retain development `GeneratedInput.Schema.version=1`; add optional field presence before the final freeze.**

The [maintained v1 disposition of 2026-11-08](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6718811463)
already requires coordinated Schema recompilation, final complete required facts and matched candidate qualification.
Maintainer clarification, 2026-11-08: v1 has never been released, so previous development-v1 artifacts/readers do not
need support, migration handling or a compatibility qualification matrix. This supersedes this plan's initial mandatory
true/false proposal and the earlier disposition's development-artifact accommodation. Preserve those dated records as
history; include #1055 before recording the final v1 contract and qualifying the first public candidate.

| Current-contract case | Required result and reason |
| --- | --- |
| Matching producer + reader | Marked fields carry true. Unmarked fields may omit the member; omission and explicit false normalize identically. Presence affects only schema required output. |
| Malformed or contradictory supplied presence fact | Nonboolean, duplicate, true-plus-excluded or inconsistent source facts reject regardless of profile or constraint policy. |
| Missing required metadata elsewhere | Existing required facts, source associations and participating parent/owned contracts must still be complete. Optional presence adds no fallback for them. |
| Missing, older, future or malformed Schema version | Existing rejection remains unchanged; no negotiation or feature markers. |

One exact version and one optional boolean are sufficient. Define omission as false in the current byte contract;
the reader does not need annotation proxies/default reflection, historical producer detection or a new completeness
marker. An omitted true fact cannot be distinguished from an unmarked field using generated bytes alone; compiler
emission tests verify that marked source produces true. Do not add redundant metadata solely to detect that mutation.
A numerical transition or mandatory false facts would serve an unreleased-artifact distinction outside the supported
contract. There is no general 4.x binary compatibility promise.

Rebuild every participating Schema/API/base/derived/owned artifact with the matching 4.2 compiler, annotations, runtime
and Schema-plugin inputs, then regenerate JSON Schema. Rebuilding only the final application leaves old field bytes.
No release provenance enforcement, stale-artifact detector or broader runtime-floor framework is added. Existing
generated provenance is independent. A post-freeze incompatible change requires a new decision and a further RC.

## Thin implementation slices and reasoned commits

All slices belong to #1055; no separate issue creation is required by this plan. Each code commit includes its passing
focused tests. Keep the shared protocol change coordinated across compiler/runtime rather than publishing a producer
which the matched runtime cannot read. The names below describe proposed commits, not existing work.

### S1 — Capture and validate one complete field-presence fact

One commit: `Capture raw-input field presence in the shared Builder contract`.
Add Struct.Required, source admission/conflict diagnostics, true emission for marked fields, optional boolean decoding,
immutable Entry transport and exact selected-field validation. Existing unmarked handwritten/canonical field fixtures
need no explicit false fact. Preserve all existing required-metadata checks. No importer enforcement.
This is a preparatory matched-toolchain commit; schema projection follows in S2, so it is not a releasable feature alone.
S1 implementation and focused qualification are recorded in [the S1 evidence](issue-1055-s1-evidence.md);
that record does not claim S2 projection or S3 release qualification.

Acceptance: A1–A3 and A7 below. Extend [StructuredInputAdmissionPlanTest][compiler-test],
[CompiledBuilderSchemaTest][schema-test] and [CompiledBuilderSchemaReaderTest][reader-test] at their existing compiler,
byte-mutation and immutable-reader seams. A cohesive new `StructRequiredTest` in the existing runtime-internal test
package may own new behavior; mark every new test `@Issue('1055')`. Keep original issues on unrelated tests.

### S2 — Project effective containing-object requirements without changing import

One commit: `Project explicit raw-input presence into containing JSON Schema objects`.
Implement the deterministic required union in definition, retaining all field-role payloads and key positions.
Extend existing source-free plugin fixtures, paired import/DSL controls and the independent Draft 2020-12 oracle
only where needed to establish the new presence behavior.
Acceptance: A2–A6 and A8 below. Extend [KlumJsonSchemaGenerationTest][generator-test], reusing its independently
compiled API/schema/owned artifacts, positive initialization traps and old-output preservation assertions.
Use [InputNamingProfileTest][naming-test] and [StructuredInputRuntimeTest][runtime-test] for existing naming/ordered
failure controls; [StructuredFactoryTest][factory-test] covers public Factory/Builder entry points.

### S3 — Qualify the matched contract and document the release boundary

One commit: `Qualify and document raw-input presence for the 4.2 candidate`.
Qualify focused malformed/contradictory current-contract controls through the existing G3/G4/G5 producer/reader and
G4/G5 named-module consumer harnesses without extra access flags. Reuse [BinaryOwnedRelationshipsTest][binary-test]
and the packaged schema matrix, preserving source deletion and independently compiled parent/owned artifacts.
Capture exact coordinates/heads and results.
Acceptance: A7–A9. Add the documentary feature `documents raw-input presence without enforcing import` to
`StructRequiredTest`, or use a dedicated `StructRequiredDocumentaryTest` if extraction improves readability.
Use `@Issue('1055')`, `@Tag('documentary')` and `@See` to the implemented user example, not this plan.

Synchronize `docs/user/Json-Schema.md`, `Structured-Input.md`, `Migration.md` navigation/recompile guidance and
`CHANGES.md` when the feature actually lands. Explain presence versus null, final `@Required`/Jakarta validation,
independent ConstraintProjection policy, defaults and client-owned schema validation. User example and documentary
test must reference each other; link both from #1055 during implementation delivery. Existing pages suffice, so no new
sidebar entry is expected. Reconcile the final v1 contract/freeze evidence with the Hive before candidate qualification.
This plan-only PR does not add shipped-feature prose to user pages or the changelog.

## Focused executable acceptance matrix

The acceptance matrix defines behavioral guarantees, not mandatory test counts. Each listed scenario need not have
a separate test method or fixture, and one focused case may establish several guarantees. Reuse existing compiler,
metadata-reader, JSON Schema, binary-consumer and lifecycle tests as evidence for unchanged behavior. Add focused cases
only where the new presence bit crosses a genuinely distinct compiler, reader, normalization or projection boundary.

Avoid combinatorial negative testing: exercise each distinct malformed-metadata failure mechanism once. Use representative
root, inherited or owned-field cases only where those boundaries introduce different behavior; do not cross-product
malformed facts with every Schema shape, naming profile or relationship configuration. Preserve Groovy 3/4/5,
independently compiled Schema consumers and supported Groovy 4/5 JPMS qualification through established harnesses,
without creating parallel consumer matrices.

The independent oracle uses [Draft 2020-12 required semantics](https://json-schema.org/draft/2020-12/json-schema-validation#section-6.5.3):
the object lists each required name; property assertions remain independent of that presence check.

| ID / slice | Setup and decisive assertions |
| --- | --- |
| A1 / S1 | Direct admitted String field with/without marker: marked source emits true; omission and explicit false both normalize to false. Annotation retention/target are correct. Getter and suppressed-getter setter fallback retain the same source association. No new public Builder/Factory signatures or Operation/Key facts. Method/parameter/type placement rejects. |
| A2 / S1–S2 | Non-overridden inherited field in a separate parent JAR, native name, Struct.Name literal and descendant KEBAB_CASE. Required uses only final tokens, once each; explicit literal wins and parent source identity is retained. An unmarked binary parent field with omitted presence remains optional. |
| A3 / S1 | Struct.Ignore + Required rejects at the declaration, including inherited binary contradiction under the reader. Effective JsonIgnore(true), excluded Owner/transient/unsupported field, missing accessor and an operation displacing the required field cannot yield phantom required names. Existing collisions still reject, and ordinary excluded unmarked fields remain excluded. |
| A4 / S2 | Root has a key plus multiple required fields: sorted unique union preserves the key. Paired bodyRootTypes excludes only the key. Direct/keyed-sequence versus externally keyed/fixed child bodies retain existing key rules. An optional owned child has required inner fields only in its own definition. A required collection/map member adds no minItems/minProperties or required aliases/data keys. |
| A5 / S2 | Validate an emitted required String property with an explicit external Draft 2020-12 validator: absent fails required; empty String succeeds absent other constraints; supplied null satisfies presence but fails the unchanged String type. Compare annotated/unannotated property fragments byte-for-byte. A required-only object fragment with no property assertion accepts a present null, proving presence independently. Boolean false and integer zero pass appropriately typed required properties. |
| A6 / S2 | Paired marked/unmarked schemas: FromStruct with omitted fields still uses normal initializer/default behavior; active applyStruct may omit the field; ordinary DSL omission behaves identically. Explicit Struct null still rejects at the same reached path with the same earlier effects. Exactly one normal lifecycle completion, unchanged callback order/default/Template recipe and Jackson import behavior. Plain @Required/@NotNull without the new marker never creates a raw field requirement; their existing final validation still runs normally. |
| A7 / S1, S3 | Exercise each distinct rawInputRequired failure mechanism (wrong type, duplicate, true+ignored or wrong selected source) once at the same valid version, using representative root, binary base or omitted/transitive owned cases where behavior differs. Reject before root initialization and active Builder mutation; previous schema files and output manifest stay intact. Omitted/explicit-false controls pass; IDENTITY, TEXT_ONLY and OMIT cannot hide malformed supplied facts or missing existing required metadata. These guarantees do not require every mutation in every contract position or profile. |
| A8 / S2 | PROJECT/TEXT_ONLY/OMIT affect only constraint-derived content, never the independent presence requirement. Property type/bounds/null rules, examples, descriptions and deprecation stay unchanged. Repeat generation is deterministic; changing only marker metadata in a dependency JAR invalidates task/cache output. |
| A9 / S3 | Source-free matched producer/reader and generator/import consumers for G3/G4/G5; binary parent/derived/owned closure, recursive ownership and excluded unrelated targets. Positive initialization traps prove inspection stays inert. Qualify only the complete current contract; no previous-development-v1 reader/artifact matrix. G4/G5 named-module consumers pass without add-opens/add-exports; exported annotation addition and descriptor inventory are checked. |

Do not add pending/ignored tests for this plan. At implementation, newly introduced executable classes use the Test
suffix; documentary examples carry the tag and an absolute user-doc `@See` URL. Spock compile-success controls use
explicit no-exception assertions, not side-effect-only expect blocks.

## Validation and evidence boundaries

This PR changes Markdown planning documents only. Run `git diff --check`, balanced-fence/Markdown structure checks and
relative link/anchor validation over both changed pages; verify new issue/decision/spec links. Gradle `test`,
`groovy4Tests`, `groovy5Tests` and root `check` are omitted under the documentation-only risk tier. User-doc rendering
does not consume these ADR/implementation pages, and no executable/generated example input changes here.
Planning reviews are independent Standards and Specification subagents; neither is implementation or RC qualification.

For implementation, the shared generated contract is the highest risk tier: focused G3 compiler/reader/runtime tests,
affected suites and G4/G5 lanes for affected multi-Groovy modules, then root `./gradlew check`, TestKit/plugin consumers,
binary/JPMS checks and the independent schema corpus above. Include relevant Jackson/Bean Validation behavior controls.
Select validation by the shared generated-contract risk, using existing affected suites and qualification evidence
where their inputs remain valid. Full root qualification remains required; it does not require redundant fixtures,
repeated equivalent permutations or unrelated validation machinery.
No local result substitutes for exact-head PR CI/SonarCloud. Record all failures/omissions rather than reclassifying a
focused pass as full qualification. Before RC acceptance, qualify the exact public candidate with aligned marker/BOM/
compiler/runtime coordinates and regenerated schemas; repository tests alone do not establish publication or adoption.

## Decisions, risks and handoff

No genuine product blocker remains under the supplied maintainer direction. Recommend adopting the provisional spelling
as proposed and retaining v1 for the reopened pre-freeze contract; the draft PR makes those choices reviewable.
If the maintainer changes the spelling, adjust the single marker before implementation. Do not reopen completed-model
requiredness, null semantics, importer enforcement or operation grammar as implied follow-up questions.

The principal risk is a marked field's bit lost during compiler emission or inherited/profile entry copying.
Source-to-byte assertions, current-contract validity controls and the source-free binary matrix address it. A second risk is losing the
existing required construction key or leaking parent requirements into reused definitions; A4 guards both.
Full matched-candidate qualification remains an implementation evidence obligation, not a claimed result.

Tracker impact: `Related: #1055`; issue completion, labels/milestone and broader #959 work remain unchanged. This feature
is explicitly admitted to pre-RC preparation, but the plan does not qualify/freeze/publish a candidate. The Hive owns
release-gate reconciliation and implementation scheduling. Publish this documentation as a draft PR when both reviews
and document checks are clear; do not merge. Request Hive delivery/archive reconciliation after the handoff.

[struct]: ../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Struct.java
[compiler]: ../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java
[generated]: ../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java
[reader]: ../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/BuilderContractReader.java
[resolver]: ../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InputContractResolver.java
[schema]: ../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java
[runtime]: ../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java
[projector]: ../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java
[task]: ../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/GenerateKlumJsonSchema.java
[compiler-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlanTest.groovy
[schema-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/CompiledBuilderSchemaTest.groovy
[reader-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchemaReaderTest.groovy
[generator-test]: ../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy
[naming-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/InputNamingProfileTest.groovy
[runtime-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputRuntimeTest.groovy
[factory-test]: ../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredFactoryTest.groovy
[binary-test]: ../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/BinaryOwnedRelationshipsTest.groovy
