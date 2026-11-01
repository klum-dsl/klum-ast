# INPUT-ROUND-PLAN — 4.2 structured-input implementation plan

Date: 2026-10-31. #953 public API clarification: 2026-11-01. Status: **Planning complete; `Struct.*` and direct internal rename accepted; documentation delivery subject to review.**
This plan records the accepted design; V0 implemented the vocabulary rename and S1 implemented ordered traversal.
[S1 evidence](input-round-s1-evidence.md), [S2 evidence](input-round-s2-evidence.md) and
[S4 evidence](input-round-s4-evidence.md) record the merged traversal, String-selection and zero-argument command slices.
[S3a evidence](input-round-s3a-evidence.md) records converter-origin parity, merged through
[PR #1004](https://github.com/klum-dsl/klum-ast/pull/1004) at `f71d0cea1ddd100d24b8106b7f64dc37e819c988`.
S6 Template Factory `FromStruct` is merged in [PR #1007](https://github.com/klum-dsl/klum-ast/pull/1007)
at `97a6775e580f3164a5ec18019312bf871512ac65`; other remaining public entry-point slices are not qualified as merged here. See
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) for the accepted semantic direction and annotation vocabulary.

## Authority and baseline

The maintainer's sixteen accepted INPUT-ROUND-PLAN decisions govern this plan. Earlier issue bodies, ADR checkpoints
and SCHEMA-ROUNDNESS recommendations are evidence, not authority to undo those decisions. This is category B: a
successor partially supersedes ADR 0031 because ordered per-operation failure changes its explicit preflight guarantee.
ADR 0031 remains the authority for retained generated-contract, key, naming and projection architecture; ADR 0009 stays.
The 2026-11-01 accepted #953 clarification supersedes ADR 0032's earlier public reader/Template-facade premise and
its deferral of generated Factory entry points. The exact Factory/Builder API is now decided; implementation remains a
separate assignment. Older #953 issue-body investigation questions do not reopen this accepted boundary.

Inspected base: `0c6f401b2a561a7767eca369f5aacab33dad0962`, verified against fetched `origin/master` on 2026-10-31.
Worktree: `/Users/stephan/.codex/worktrees/2696/klum-ast`; branch: `codex/input-round-plan-4-2`.
Source and executable specifications were inspected, not rerun. All test requirements below are future slice gates.
The planning change is restricted to ADR/implementation documents; current user docs remain descriptions of current code.

For this clarification, source and test seams were reinspected at `5e06b1545` on 2026-11-01. The local `v4.1.0` and
`v4.1.1` source trees contain neither `KlumStructuredInput` nor `StructuredInputSupport`, consistent with the maintainer's
release-artifact finding that these APIs are development-only; this task did not independently download release JARs.
[#953](https://github.com/klum-dsl/klum-ast/issues/953) was open without a milestone when read. S6
[PR #1007](https://github.com/klum-dsl/klum-ast/pull/1007), inspected at `b1a1905504e0e56a8b65de1581a8dcb6d10b1afe`, was open;
its former public `readTemplate` addition implemented the superseded premise. The Hive subsequently lifted the hold for retargeting.
Its internal `FactoryHelper.doCreateTemplate` access, Template-position traversal and lifecycle suppression for reused
Template children, plus `StructuredInputTemplateDocumentaryTest`, are reusable work requiring retargeting and revalidation.

Read-only original issue snapshot (2026-10-31), with the #953 mapping clarified on 2026-11-01:

| Canonical issue | Live state | Reconciliation for this plan; no GitHub write |
| --- | --- | --- |
| [#529](https://github.com/klum-dsl/klum-ast/issues/529) | Closed | Delivered JSON Schema foundation. New grammar projection must accompany the corresponding #952 slice; do not reopen the delivered parent. |
| [#932](https://github.com/klum-dsl/klum-ast/issues/932) | Closed | Delivered v1 interpreter. Preserve its dated preflight evidence and migrate its behavior explicitly under #952. |
| [#952](https://github.com/klum-dsl/klum-ast/issues/952) | Open | Grammar coordination: scalar selection, zero-argument commands, known scopes, conditional relationships and CollectionFactory proof. Its broad deferred list is not blanket authorization. |
| [#953](https://github.com/klum-dsl/klum-ast/issues/953) | Open | Construction-entry/body-schema coordination. Accepted common/keyed/Template Factory `FromStruct` and inherited void Builder `applyStruct`, each with optional trailing `sourceName`; see A0–A3/S6 and the migration table below. Preserve `FromMap` and ADR 0009. S6/#1007 is retargeted after this clarification merged. File/URL loading stays deferred. |
| [#954](https://github.com/klum-dsl/klum-ast/issues/954) | Closed | Retain positional key authority and merged Stage A/B evidence. The body schema is additive; no reopening or changed key derivation. Its stale body still mentions pending delivery. |
| [#959](https://github.com/klum-dsl/klum-ast/issues/959) | Open | Retain delivered naming, exclusion, descriptions, numeric/Pattern/composed projections and documentary strings. Defaults, Size, typed examples and broader constraints do not block admission. Historical issue text understates delivered work. |
| [#971](https://github.com/klum-dsl/klum-ast/issues/971) | Open | Explicitly Future/untargeted. No executable parameter validation or new 4.2 placement. Provider inheritance and Groovy 4/5 annotation failures remain future proof gates. |
| [#981](https://github.com/klum-dsl/klum-ast/issues/981) | Open | C1 implementation is current; C2 quantity Pattern extension is deferred. Preserve exact selected-parameter authority for existing C1 assertions, without using its inspection restrictions to prohibit new structural-only scalar routes. |

All eight issue milestones were null when read. The accepted 4.2 direction and separate 4.3 composition track do not
authorize changing those fields, labels, dependencies or release placement. Hive owns any later issue reconciliation.
Suggested tracker impact is **Related** to #952/#953/#959/#981, with #529/#932/#954/#971 as contextual references;
no closing syntax or issue creation is proposed. Hive chooses final PR relationships before publication.

## Current behavior and seams

| Owning seam | Confirmed behavior at the base; intended use |
| --- | --- |
| `klum-ast`: `StructuredInputAdmissionPlan.classifyField/classifyOperation` | Separates properties/owned relationships from authored `@Builder.Method`; authored operations require one payload. Reuse compiler projection and exclusion/name facts, then normalize eligible operation candidates. |
| `ConverterBuilder`, `StructuredInputConverter` | Compiler knows the exact converter-to-generated-method association. Current C1 records exact String routes, requires one eligible separately compiled non-DSL route, and rejects competing/overloaded families. Field closures/CharSequence and broader origins do not currently gain admission. |
| `klum-ast-runtime`: `BuilderContractReader`, `InputContractResolver` | Exact public declaration bytes/completeness are separate from inherited naming, role/selection and key resolution. Preserve this split and the descendant converter-shadow rejection. |
| `CompiledBuilderSchema` | Immutable internal result shared by runtime and generator. Extend only proven missing role/selection facts; no public descriptor or second serialized plan. |
| `StructuredInputSupport.Preparation`, `Domain`, `Step` | Builds a full ordered payload snapshot, recursively checks ownership contracts, then executes. Direct/Map children use Consumer construction seams. S1 removes whole-input payload preparation, retaining contract integrity and local key prerequisites. |
| `KlumStructuredInput` | Development-only: two `readRoot` overloads and identity-returning `applyToBuilder`; no merged structured Template entry point. Apply requires a same-thread, unsealed ordinary Builder. A0–A3 migrate callers to the accepted capabilities and remove the public reader surface. |
| `FactoryHelper`, `InternalKlumBuilder`, `TemplateManager` | Existing root/nested Consumer allocation, child reuse/key checks and Template definition/materialization own lifecycle and ownership. Template creation omits lifecycle; its root key is not copied to recipients. |
| `klum-ast-gradle-plugin`: `JsonSchemaProjection`, `BuiltinConstraintCatalog`, `GenerateKlumJsonSchema` | Private structural projection and closed constraint catalog, explicit roots/classpath, isolated no-execution inspection, deterministic files and retained previous output on failure. Default root schemas are self-contained. |

Source links: [compiler admission](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java),
[converter projection](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/ConverterBuilder.java),
[C1 selection](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputConverter.java),
[runtime traversal](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java),
[contract resolver](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InputContractResolver.java),
[Template/root mechanics](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java),
[schema projection](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java).

Retain [architecture map](issue-curation/architecture-map.md),
[WIRE runtime history](adr-0031-wire-runtime-v1.md), [compiled-contract migration](evidence/issue-529-wire-remove.md),
[key evidence](evidence/issue-954-stage-b.md), [S1 extraction](issue-959-schema-s1-contract.md),
[S2 catalog](issue-959-schema-s2-catalog.md), and [C1 evidence](issue-981-c1-evidence.md).
The older [overall plan](adr-0031-structured-schema-input-artifacts.md) and
[converter plan](issue-981-converter-projection-plan.md) contain historical pre-delivery wording; they do not override
the current code or the dated issue snapshot above.

Important regression seams are `StructuredInputAdmissionPlanTest`, `CompiledBuilderSchemaReaderTest`,
`StructuredInputRuntimeTest`, `StructuredInputConstructionTest`, `StructuredInputKeyTest`,
`StructuredInputKeyBoundaryTest`, `CompiledConverterInputTest`, `StructuredInputDocumentaryTest`,
`StructuredInputKeyDocumentaryTest`, `TemplatesDocumentaryTest` and packaged `KlumJsonSchemaGenerationTest`.
The former runtime test `complete preflight leaves an active Builder unchanged: #input` has been replaced by
partial-state and first-failure coverage under S1. Static malformed-contract controls remain separate.

## Development API migration — #953

The [ADR 0032 API table](../adr/0032-builder-oriented-structured-input.md#public-factory-creation-and-builder-application--953)
is the exact signature authority: `Map<?, ?>` input, optional trailing `String sourceName` for all four forms, and void
Builder application. These are planned replacements for development callers, not deprecated aliases or a released 4.x
migration bridge. `StructuredInputSupport` stays internal like `FactoryHelper`; no public reader replacement is introduced.
In this table `Service` denotes the selected Schema and `source` is nonempty opaque diagnostic text.

| Development-only call | Accepted public replacement | Invariant / acceptance owner |
| --- | --- | --- |
| `KlumStructuredInput.readRoot(Service.class, document, source)` | `Service.Create.FromStruct(document, source)` | A0: self-contained keyed roots require the actual externally named Schema key; unkeyed roots need no key. |
| `KlumStructuredInput.readRoot(Service.class, rootKey, body, source)` for a keyed root | `Service.Create.FromStruct(rootKey, body, source)` | A1: explicit key is authoritative; reject an inner root key even if equal; one root `PostApply`. |
| `KlumStructuredInput.readRoot(Service.class, null, body, source)` for an unkeyed root | `Service.Create.FromStruct(body, source)` | A0: do not add a null-key overload to unkeyed Factories. |
| `KlumStructuredInput.applyToBuilder(builder, body, source)` | `builder.applyStruct(body, source)` | A2: void common capability; same receiver/session, keyless body, ordinary target restriction, one `PostApply`, partial failure retained. |
| Caller uses the returned generic `B` from `applyToBuilder` | Call `applyStruct`, then return the original `B` in a caller helper | A2: retain exact typed receiver identity without a fluent public return or cast. See ADR 0032 Java example. |
| Proposed `KlumStructuredInput.readTemplate(Service.class, body, source)` in #1007 | `Service.Create.Template.FromStruct(body, source)` | S6: marked keyless Template recipe, no definition lifecycle, fresh recipient graph with recipient identity. |
| Direct development use of `StructuredInputSupport` | Select the corresponding generated Factory or public Builder operation above | A3: no client dependency on the internal reader/helper; no Class-to-Factory registry. |

Each replacement also has an overload omitting `source`; it supplies a generated diagnostic label. Explicit null/empty
labels reject. Path-like, URL-like and punctuation-bearing labels remain text: no I/O, normalization or identity inference.
The public root type is Map even though nested values can be Lists/scalars under the admitted grammar. A caller holding
`Object parsed` must establish it is a Map before calling; this does not admit coercion or change strict member validation.
Generic root callers receive the intended Factory from their caller rather than looking it up in a framework registry.

`FromMap` is deliberately absent from this migration: preserve its copy/type-hint (`@type`)/String-conversion/custom-Factory
semantics and nested `FromMap` delegation, including `AsBuilder().FromMap`. No deprecation or automatic redirection is
accepted. Structured input is preferred for Schema-owned configuration; caller-owned foreign-format mapping remains
managed Jackson's durable role, with ordinary Jackson serialization for export. ADR 0009 signatures, modes, mapper
customization, input ownership and diagnostics are unchanged. Neither path promises import/export round trips.

Additional owning seams are `KlumFactory` and `DSLASTTransformation`/`GeneratedDslSupport` for common/keyed Factory
projection; `KlumBuilder` and `InternalKlumBuilder` for inherited application; `TemplateMethods` and
`GeneratedTemplateFactorySupport` for Template Factory projection. Root creation uses
`FactoryHelper.createWithConfiguration` with one interpreter configuration callback; wrapping public `applyStruct` inside
`With` would add a second `PostApply` and is not an equivalent implementation. Retain existing direct-child/keyed-Map
Consumer seams. Factory roots must not be projected into relationships, and `AsBuilder.FromStruct` is excluded.

## Positive support matrix

This is the proposed documentation content model at the inspected planning base. “Accepted” means planned implementation
at that base; the ordered slice rows and linked evidence below track subsequent implementation.

| Shape / use | Support at inspected planning base | Target and admission boundary |
| --- | --- | --- |
| String, boolean, supported numeric, exact enum | Yes | Retain strict payloads and existing numeric limits; no general coercion. |
| One typed List/Map argument; ordinary scalar field collections/maps | Yes, bounded | Retain one argument versus generated element population; literal String Map keys. |
| Concrete direct child / Map of keyed concrete children | Yes | Preserve direct scope entry, normal callbacks, attachment/reuse and all #954 key positions. |
| Unambiguous String / CharSequence scalar operation | C1/existing authored subset only | Accepted S2/S3: one fixed public operation; String payload, origin-independent after projection. |
| Authoritative mark for competing candidates | No | Accepted `Struct.Operation` selection semantics; S2 follows V0. Zero marks requires uniqueness, one wins, multiple fail. |
| Field-declared String converter / same-compilation or adapter route | No general admission | Accepted S3 when an exact ordinary Builder projection exists. No separate constraint proof required. |
| Converter-backed scalar List/Map values | No general admission | Follow-on L1 after single scalar routes; no automatic blanket recursion. |
| String to concrete owned DSL target | No | Conditional F1 proof, then a separately admitted implementation slice. |
| Known nested Builder scope | Concrete relationships only | Accepted S5 where a fixed scope contract and direct entry exist; no dynamic/untyped delegation or synthetic closures. |
| Zero-argument command | S4 implemented; integrated onto merged S2 | S4: `operation: {}` only. |
| Factory creation / Builder application | Development reader calls only | Accepted A0–A3: common/keyed `FromStruct`, inherited void `applyStruct`, diagnostic overloads; no parser or registry. |
| Structured Template definition | No merged implementation | S6 through generated Template Factory `FromStruct`; #1007 retargeted for review. Existing Template semantics; no root lifecycle or implicit mode. |
| Explicit keyless root-body schema | Private positional definitions only | Accepted useful additive S7; caller supplies identity, editor association and source parsing. |
| CollectionFactory scope | No | Bounded 4.2 F2 feasibility only; existing closed alternatives and key contracts, not generic subtype selection. |
| One-Map NamedParams/NamedParam shape | Ordinary typed Map only | Later contained L2: declared keys and proven required/type facts only. |
| Owned DSL List/Set, custom key mapping | No | Deferred/conditional R1 revisit after higher-tier acceptance. |
| Descriptions and documentary Struct.Examples strings | Yes | Retain; do not parse examples or infer enforceable defaults. |
| Managed Jackson | Independent supported path | ADR 0009 mapper/property semantics; generated input schema does not describe Jackson/export. |

## Ordered slices and owners

Owners below are responsibilities for future assignment, not workers started by this plan. “KlumAST” means the assigned
compiler/runtime/plugin implementer; “Schelm” means the adopter owner; “maintainer/Hive” owns decisions and delivery.
Every grammar-changing slice includes matching structural schema output; API-only slices preserve the grammar and
include focused docs. No temporary runtime-only
grammar should ship while generation still misdescribes it. Cross-cutting changes stop at local review before publication.

| ID / category | Depends on | Outcome, affected modules and reasoned commit boundary | Acceptance evidence / owner |
| --- | --- | --- | --- |
| V0 — implemented | Maintainer `Struct.*` decision | [Annotation decision](input-round-annotation-vocabulary.md): directly rename existing author vocabulary and update internal tests/Schelm qualification. No migration, cutover, aliases or compatibility bridge. | Consistent compiler/reader/generated type references, equivalent behavior; G3/G4/G5 and binary fixture/JPMS gates. Later KlumAST implementation; Schelm qualification owner. |
| D0 — docs-only | This plan | Reconcile present-tense delivery wording and publish the positive matrix/import-path guide. One commit for current capability/status reconciliation, another only if future-syntax guidance is separately labelled. | Links, current-source checks, no prospective API advertised as shipped. KlumAST docs; Hive owns issue updates. |
| S1 — implemented | ADR 0032 | Replace whole-input `Preparation` with per-operation traversal in runtime; retain contract verification, root/child identity prerequisites and existing Consumer dispatch. One cohesive behavior/test commit, then migration/docs. No new grammar in this slice. | Earlier operation persists before bad later member/type/child; later operation never runs; root returns no Model; active Builder may be partial. Key order/cycles/diagnostics/session cleanup controls. KlumAST runtime. |
| S2 — implemented locally; integrated onto merged S1 | V0; S1 | Normalize one scalar candidate selection on public Builder contracts. Add author marker and minimal generated selection/completeness facts across annotations/compiler/runtime/plugin. One vertical String-selection commit with tests; second compatibility/docs commit only when independently useful. | Unique unmarked, one marked, multiple marked, zero eligible, exclusion, renamed/inherited candidates, competing String/CharSequence, actual dispatch equality, shadowing and stripped facts. Selected String schema agrees. KlumAST compiler/runtime/plugin. |
| S3a — accepted implementation | S2 | Admit exact String scalar routes from field closures, same-compilation values and existing external adapters without C1-origin restrictions. Keep current C1 constraint authority when still applicable. One origin-parity behavior/test commit plus docs. | Same effective Builder shape works across origins; typed failure and thrown converter cause preserved; converter not invoked by generation. Collision with missing generated method fails; no duplicate factory scan. KlumAST compiler/runtime/plugin. |
| S3b — accepted implementation | S3a | Admit deterministic CharSequence scalar routes (e.g. existing projected `Duration.parse`). One narrow behavior/schema/test commit plus docs. | A supplied String reaches the exact recorded CharSequence signature; String/CharSequence competition needs authority; numeric/null/GString/custom mutable CharSequence input rejects; no StringBuilder construction. KlumAST compiler/runtime/plugin. |
| S4 — implemented; integrated onto merged S2 | S2 | Zero-argument command role, empty-mapping runtime dispatch and empty-object schema. One vertical commit plus documentary example/docs. | Empty Map invokes once; null/blank YAML result, nonempty Map, List/scalar reject; no later call after failure; unknown name remains rejected. KlumAST compiler/runtime/plugin. |
| S5 — accepted implementation | S2; retain S1 order | Admit known nested Builder scopes with mapping payloads and direct scope entry. One proven scope family per vertical commit; do not bundle CollectionFactory. | Concrete contract, direct Consumer entry, same session/key/ownership, callback counts, nested failure after earlier sibling, accurate closed schema. Untyped/dynamic delegate and closure-only method without direct entry rejected. KlumAST compiler/runtime/plugin. |
| A0 — accepted Factory contract | This clarification merged; S1 | Add common generated `FromStruct(Map<?, ?>)` and trailing `String sourceName` overload using the internal self-contained root path. One runtime/compiler/generated-contract/test commit, then docs when scope is assigned. | Java/static/dynamic Groovy and binary callers; unkeyed/keyed root, named/inherited key before allocation, strict Map payload, ordinary/generated labels, source/path/cause failures, normal lifecycle. KlumAST compiler/runtime. |
| A1 — accepted explicit-key Factory | A0 | Add keyed-only `FromStruct(String rootKey, Map<?, ?> body)` and trailing `String sourceName` overload through the single root configuration seam. One behavior/contract/test commit plus docs. | One `PostApply`, key visible at `PostCreate`, same ownership/materialization as old explicit-key reader; inner equal/different/null key rejected; absent from unkeyed/relationship/AsBuilder contracts. KlumAST compiler/runtime. |
| A2 — accepted Builder capability | This clarification merged; S1 | Add both void `applyStruct(Map<?, ?> body[, String sourceName])` methods to `KlumBuilder`, implemented by runtime Builder base and inherited by generated contracts. One capability/dispatch/test commit plus docs. | Exact void descriptors, inherited Java/static/dynamic Groovy calls, helper returns identical typed B; prepared context before early callbacks; same-thread active unsealed ordinary targets only, one apply callback/no completion, key/body and ordered partial-failure controls. KlumAST runtime/compiler qualification. |
| S6 — implemented; merged #1007 | This clarification merged; A0 diagnostic/Factory pattern; S1; S3/S5 for newly admitted forms | Rebase and expose both generated Template Factory `FromStruct(Map<?, ?> body[, String sourceName])` forms. Reuse internal Template mechanics/tests from #1007; replace its public `readTemplate` facade and reader-based documentation. One coherent runtime/generated-contract/test commit, then docs. | Marked recipe, no definition lifecycle including reused children, same values/actions as ordinary Template, fresh recipients, root key forbidden/not copied, nested key/ownership/serialization controls; no ordinary apply-to-Template escape. Failure returns no Template. Java/Groovy and generated Template signatures. KlumAST compiler/runtime; Hive controls delivery. |
| A3 — development caller migration | A0; A1; A2; retargeted S6 | Migrate development tests/fixtures, generic caller helpers and current user examples, then remove the development public reader surface. One repository migration/removal commit plus final API/docs consistency check. Schelm adaptation/qualification is consumer-owned. | No production/user-facing dependency on a public reader; all four modes tested through supported capabilities; no deprecated alias/registry, `FromMap` and ADR 0009 unchanged; matched compiler/runtime/Schema artifacts. KlumAST; Schelm; Hive delivery. |
| S7 — implemented; see [evidence](input-round-s7-evidence.md) | #954 retained; S1 | Explicit Builder-body schema root variant, with distinct output and unchanged default root artifact. One plugin configuration/projection/packaged-test commit plus docs. No runtime Factory/File/URL API. | Body forbids root key; self-contained root requires it; same child key positions; roots of same type in both modes coexist; recursive definitions, names, cache/manifest/root-removal/failure-preservation controls. KlumAST plugin. |
| Q0/Q1 — consumer qualification | Q0 baseline now; Q1 S1–S7 and A0–A3 higher-tier result | Qualify one explicitly matched artifact graph against the three real Schelm YAML documents and active-Builder context. Consumer evidence commit records coordinates/hashes/commands/results; parser/numeric adoption are separate Schelm changes. | Fresh generated/packaged/editor schema provenance, three inputs, four renders and current harness, negative corpus below; no stale-output acceptance. Schelm; Hive reconciles outcome. |
| F1 — bounded feasibility | S3b; ordinary DSL producer contracts | Compare one String-to-concrete-DSL Builder route with ordinary Groovy, including one direct child. Test-only probe/evidence in a later assignment, no production admission in this probe. | Same-session unsealed producer, key/attachment/reuse, callbacks/owner visibility and diagnostics equivalent; completed-Model/nested-root control rejected. Failure records smallest missing fact and retains rejection. KlumAST; maintainer admits any next slice. |
| F2 — bounded 4.2 feasibility | S5; after higher-tier priority | One generated CollectionFactory with an existing literal alternative/key route. Probe public nested contracts and direct entry; record representable mapping and negative controls. No implementation promise. | Alias/element/key facts are compiler-owned, no subtype scan, no generic Class/Factory selector, no synthetic closure; binary/no-execution parity and direct Groovy trace. Missing seam ends probe. KlumAST; maintainer decides follow-up. |
| L1 — accepted follow-on direction | S3b and Q1; R1 triage | Converter-backed scalar List elements/Map values, preserving normal element population and String keys. Separate List then Map vertical changes if feasible; no owned-DSL collection claim. | Each raw String uses selected element route, element order and indexed/keyed causes, no whole-container replacement/coercion; schema items/values reflect same String grammar. KlumAST; scope readmitted by Hive. |
| L2 — later/contained | Higher-tier results; explicit later assignment | One-Map NamedParams/NamedParam projection using existing named-map safety. Facts-only proof first, implementation only when required/type authority is proven. | Declared keys, actual required flags, inherited binary facts; no required inference from final validation; reject/describe unknown facts truthfully. No arbitrary Map semantics or argument spreading. KlumAST; maintainer controls re-entry. |
| R1 — deferred quick-win checkpoint | Q1 and higher-tier review | Revisit owned DSL List/Set, custom key mapping, L1 and F1/F2 results individually. Documentation-only disposition unless new work is explicitly assigned. | Concrete Groovy/input example, need/workaround, bounded effort, existing seam, evidence and migration impact per item. Maintainer/Hive with Schelm. |

D0 and Q0 can proceed independently. S1 → S2 → S3a → S3b is the primary implementation chain. S4 and S5 branch after S2;
S7 can use the retained grammar after S1. A0 → A1 and A0 → retargeted S6 establish Factory creation; A2 can proceed
independently after this clarification merges. A3 follows all four public forms. S6/#1007 is rebased and retargeted after Hive assignment; the later forms remain separate. Recheck all
entry modes against newly admitted S3/S5 forms before Q1. F1/F2
are probes, not dependencies forcing a broader engine into S1–S7. Do not start follow-ons just because a probe finishes.

### Acceptance details that must not disappear inside a slice

S1's smallest distinguishing case is a supplied insertion-ordered Map with a valid mutating operation, then an invalid
name/type, then a second valid operation. Assert the first effect, the second failure and absence of the third effect.
Repeat inside a direct child, a keyed Map and a population List. Verify a late construction key is obtained before that
object's allocation without prevalidating other members. A missing required key stops before allocating that object;
previous siblings can already have run. Contract corruption can still fail before execution; test it separately from
payload failure. Do not promise rollback of user side effects or force normal completion after an exception.

S2 implementation and bounded evidence are recorded in [the S2 evidence](input-round-s2-evidence.md).
S2 must enumerate candidates from actual public projections, not hypothetical source methods. An overload omitted by
ordinary generation cannot become callable merely by marking it. Preserve #991's non-overridden binary inheritance
controls and descendant override rejection unless the new exact selection proof deliberately re-establishes equivalence.
Annotation target/retention/enrichment tests must verify that author intent reaches the exact public declaration across
Groovy 3/4/5; generated source mirrors remain IDE-only. A one-to-one marker placement is a technical gate, not permission
to introduce an overload-selector DSL. Existing exclusion and incompatible literal-name errors remain authoritative.

S3 must test at least a field `@Field(converters = [{ String value -> Duration.parse(value) }])` route, a same-compilation
non-DSL value and an already-generated external adapter route. A converter executes only during invocation. `type: string`
plus truthful prose is sufficient when C1 parameter facts do not apply; never attach destination-field constraints to
the raw String or run converters to infer a grammar. Retained C1 routes keep their exact selected-parameter policies and
missing/corrupt metadata failures. “Constraint projection later” does not remove existing supported output.

S5 begins with retained direct-child/keyed-Map traces and one additional statically known scope only if its generated
contract proves direct entry. Reading `@DelegatesTo` and manufacturing a closure is not implementation of this decision.
If no additional family has the required seam, report the proof boundary and return to the maintainer instead of making
the supported matrix dishonest. F2 owns the separate CollectionFactory question.

S6 treats the root as a Template body: no serialized root identity; recipients supply their own keys. Preserve ordinary
Template nested key and recipe rules, including keyed-Map identity. Compare existing `Create.Template.With` behavior,
not Jackson's separate value-only import mode. Definition may invoke admitted operations/converters to configure the
recipe; the no-lifecycle promise must not be misstated as no execution. Public `applyStruct` keeps its ordinary target
restriction; the internal Template seam is separate. Use the internal Template configuration seam, not a temporary completed Model copied back to Builders.

A0–A3 must exercise all eight exact overload descriptors and default/explicit diagnostic labels; null/empty labels,
non-String domain keys, invalid payloads and URL/file-looking diagnostic text are negative/opacity controls. Preserve
normal lifecycle exception attribution and original causes. Assert exact concrete Model/Template results, not just
successful invocation. Compare explicit-key root traces with the old reader and detect accidental double `PostApply`.
Retain `StructuredInputConstructionTest`, `StructuredInputKeyTest`, binary generated-Factory and inherited-Builder tests;
add Java and static/dynamic Groovy 3/4/5 consumers, mirror truthfulness and applicable JPMS coverage. Verify new root APIs
are absent from `AsBuilder` and relationship projections, while inherited `applyStruct` remains a framework capability
excluded from admitted input operations. Preserve `FromMap` custom overrides, nested delegation, type hints and conversion
regressions, and existing managed Jackson tests. Reader removal must not strand tests or examples between slices; A3 is a
release gate. No mixed development-toolchain or serialized Builder/input-plan compatibility promise is introduced.

S6 implementation evidence: `StructuredInputTemplateDocumentaryTest` compares a parsed recipe with
`Create.Template.With`, including definition-time configuration, lifecycle silence, recipient keys, fresh owned graphs,
keyed-Map identity and rejection controls. The existing Template and serialization regression suites remain applicable.
S3/S5 forms admitted later still require reconciliation before Q1.

S7's configuration/output spelling is an additive implementation proposal to review with its tests, not a new product
question about whether filenames should supply keys. They must not. Retain a distinct explicit purpose in Gradle inputs
and output identities without publishing private definition suffixes. This body variant does not silently become a
Template schema mode or validate every kind of independently keyed consumer input.

## Decision-to-evidence ledger

Rows 1–16 match the original accepted assignment; the #953 row records the later public API clarification. Every row names an acceptance owner; no row is qualified by this plan.

| Decision | Slice / evidence obligation | Owner |
| --- | --- | --- |
| 1. Builder-contract authority regardless of origin | S2/S3 same public operation across authored/generated/converter origins; exact source attribution retained. | KlumAST compiler/runtime |
| 2. One scalar String/CharSequence route, no coercion | S2/S3b positive String calls, competing-route negatives, exact argument/dispatch traces. | KlumAST |
| 3. Exactly one authoritative mark | S2 zero/one/multiple `Struct.Operation` marks, excluded and inherited candidates. | KlumAST proof |
| 4. Field converters; containers follow; DSL conditional | S3a fixture, L1 separate element tests, F1 ownership/lifecycle equivalence gate. | KlumAST; Hive admits follow-ons |
| 5. Known mapping scopes, direct entry | S5/F2 Consumer path and concrete delegate evidence; synthetic/dynamic closure controls reject. | KlumAST |
| 6. Zero-arg empty mapping | S4 empty object schema/runtime; null/nonempty/scalar controls; parser supplies null for blank YAML. | KlumAST; Schelm parser control |
| 7. Supplied traversal order | S1 reversed-order and nested/List traces, no parser portability claim. | KlumAST; consumer owns Map order |
| 8. No preflight/rollback, stop first | S1 partial Builder/no-root-result evidence; no later invocation; diagnostic and cleanup parity. | KlumAST runtime |
| 9. Structured Template and body schema | S6 ordinary Template comparison; S7 root/body key contrast, output/cache controls; no core filename inference. | KlumAST; consumer identity |
| 10. Structural schema, no execution/success promise | All new grammar slices paired with projection, initialization traps and actual emitted Draft 2020-12 controls. Existing #959/#981 assertions retained, new constraints later. | KlumAST plugin |
| 11. NamedParams one Map later | L2 closed declared keys and proven required/type facts; no broad Map reinterpretation. | KlumAST later assignment |
| 12. Fail closed and exclusions | Each slice negative corpus: unknown names, null, raw Object, exact enums, non-String keys, references, selectors/repeated-call syntax. | KlumAST; parser duplicates Schelm |
| 13. Owned List/Set and keyMapping conditional | R1 per-shape disposition after Q1; no current support claim. | Maintainer/Hive |
| 14. CollectionFactory bounded probe | F2 generated alternatives/key/direct-entry evidence and stop conditions; no composition prerequisite. | KlumAST probe; maintainer |
| 15. Jackson separate | D0 reciprocal import-path table, unchanged ADR 0009 modes, language versus export examples. | KlumAST docs |
| #953. Factory/Builder public boundary | A0 common root, A1 explicit key/one PostApply, A2 void inherited capability, S6 Template Factory, A3 development migration; eight signatures/labels, unchanged FromMap/Jackson and retargeted #1007. | KlumAST; Hive retarget/delivery; Schelm qualification |
| 16. Composition deferred | R1 records separate 4.3-oriented track; no composition production dependency or redesign. | Maintainer/Hive |

## Schelm evidence and consumer acceptance

SCHEMA-ROUNDNESS (assessment on 2026-10-31) received adopter evidence for Schelm
`553fdd42fb3cf82ba66c9588df28005ff58a7c84`, pinned to `4.2.0-dev.114+82222c7`. Its reported October 24 result was
110 tests and four renders. The adopter confirmed all four inspected schema JARs and their seven embedded schema entries
match `qualification/klumast-dev114.json`. Separately materialized loose generated files were not verified. Dev.263 was
source-inspected, not consumer-qualified. These are attributed prior results, not tests run by INPUT-ROUND-PLAN.

Retained positives: literal Map-key identity, naming/exclusion, source/path diagnostics, active-Builder environment
injection prepared by the caller, direct children and keyed-Map relationships. Keep the three current YAML files as
the real corpus and record their identities/hashes in Q0/Q1; do not substitute synthetic examples for adopter acceptance.
The report described nine builds in the consumer graph; qualification must record the actual current matched graph
rather than treating that count as a permanent product contract.

| Finding | Acceptance action and owner |
| --- | --- |
| A raw YAML duplicate `replicas` becomes the last value before KlumAST receives a Map | Schelm rejects duplicate raw keys with parser source context. KlumAST cannot recover discarded tokens. No parser framework in core. |
| Stale schema can survive failed generation | Schelm verifies successful generation and exact packaged/editor artifact provenance. Prior Dev.114 hashes cannot qualify a new coordinate; loose files require their own check. |
| Percentage fields are currently unannotated Integers | Schelm chooses numeric constraints and runtime-validation adoption. Test 0/100 acceptance, -1/101 rejection only after that consumer change. Existing numeric schema projection alone does not enforce runtime values. |
| Resource quantities remain Strings; wider Pattern unsupported | S3 scalar admission is independent from C2. Schelm keeps body validation/documented grammar; no invented Kubernetes mapping. |
| Duration uses CharSequence | S3b proves an existing Builder route; a library method alone is insufficient evidence of projection. |
| Relationship-specific partition defaults differ (8, 2, 4) | Keep truthful prose; no default execution or generalized contextual-default engine. |
| Filename-keyed bodies differ from self-contained roots | S7 is explicit; consumer supplies identity. Current three roots are unkeyed, so no present modeline defect is asserted. |
| Struct.Examples are documentary strings | Check emitted strings and documentation; do not promise typed object/number examples or validate them as input. |

Q1 adds bounded negative cases for unknown/excluded names, strict scalar/null/enum shape, all key authorities, nested
first-failure order, and source/path attribution. Schema validation and runtime results are reported separately. Retain
the environment-before-early-callback scenario from `StructuredInputDocumentaryTest`; assigning LINK context later is
not an alternative for early callbacks. No generic reference, merge, naming or YAML framework is requested by this evidence.

## Compatibility and validation gates

- Public/generated contract: preserve released Builder/Factory callable descriptors and `FromMap` semantics. Add the
  eight accepted Factory/Template/Builder overloads and remove development-only reader calls through A3; no reader
  compatibility bridge. Preserve ADR 0009. No edits to IDE mirrors as authority.
- Binary facts: exact declaration/resource identity, inherited/overridden operation selection, missing/stripped/corrupt
  facts, counterpart completeness and classpath failures. Match compiler/runtime/plugin and participating Schema/domain
  artifacts; recompile affected hierarchies. Development protocol marker 1 alone proves no mixed-artifact compatibility.
- No-user-code inspection: positive initialization traps for Schema/domain/enum/static state, defaults, constructors,
  converters, validators, lifecycle and annotation closures. Source-free public contracts and controlled C1 resolution
  only; no hidden implementation inspection, generic discovery, source/mirror fallback or new runtime dependencies.
- Runtime: ordinary Groovy versus structured traces for lifecycle, key/owner attachment, exceptions, Template recipes,
  active-session/thread/sealed restrictions and partial failure. Do not use only “did not throw” assertions.
- Serialization: preserve completed-Model and Template/recipe serialization and existing identity behavior. S6 retains
  the applicable existing serialization regressions; it adds no serialized Builder, importer or execution-plan state,
  persistence format or import/export round-trip promise.
- Tooling: validate actual emitted Draft 2020-12 output and accepted/rejected instances independently. Preserve explicit
  roots/classpath, isolated closeable loaders, cache/configuration-cache/relocation and stale-output controls where affected.
- Every future new test has driving `@Issue` (normally #952 or #953 by slice), `Test` class suffix; each user-visible
  feature has `@Tag("documentary")`, `@See` and reciprocal user/issue references. Reuse existing fixtures and scenarios;
  no new cache infrastructure without controlled evidence. Pending/ignored cases require actionable reasons.
- Focused Groovy 3 first; affected modules' baseline suites and Groovy 4/5 at final implementation handoff, including
  binary Java/static/dynamic Groovy consumers and applicable JPMS checks for generated ABI. Broaden to root checks when
  shared ABI changes warrant them. This planning-only diff requires Markdown/link/diff checks, not Groovy test lanes.

## Documentation and delivery

D0 consolidates entry-point guidance in [Structured Input](../user/Structured-Input.md) and
[Jackson Integration](../user/Jackson-Integration.md), linked from [JSON Schema](../user/Json-Schema.md) and
[Factory Classes](../user/Factory-Classes.md). Explain compiler-admitted operations
versus mapper-owned properties; naming annotations do not configure Jackson, schema checking is not automatic runtime
validation, and neither input path defines completed-object export. Preserve separate Jackson Template semantics.

Each behavioral slice updates its relevant current user pages, [Builder migration](../user/Builder-First-Migration.md),
[migration navigation](../user/Migration.md), sidebar if a page changes, and `CHANGES.md`. S1 must replace the current
preflight guarantee when implemented, not beforehand. S3 updates Converters; A0–A3 update Structured Input, Convenience
Factories and development migration examples; retargeted S6 updates Templates; S7 explains explicit
self-contained versus body editor selection. Hive owns later linked-issue/Sonar/delivery reconciliation. Do not advertise
accepted but unimplemented annotation/API spelling as current or edit historical `wiki/` authoring trees.

The [annotation decision](input-round-annotation-vocabulary.md) accepts `Struct.*`, including `Struct.Operation`.
The maintainer confirms usage was limited to internal tests and Schelm qualification; no annotation migration or cutover
is needed. V0 directly updates those uses and type references. No vocabulary or compatibility-policy decision remains.
Scope-entry availability, DSL-target equivalence and CollectionFactory feasibility are
bounded evidence gates with stop outcomes, not unresolved product choices to reopen now. New requirement or absent
equivalence returns to the maintainer; it does not authorize redesign. Factory/Builder Map entry points are now settled;
file/URL/parser convenience, runtime parameter validation,
new constraint families, typed examples and composition remain separate decisions.

This #953 clarification changes ADR/plan guidance only. User pages and `CHANGES.md` wait for accepted implementation
scope and delivery; the API sketches here must not be advertised as currently shipped. Issue #953 status/milestone stays
unchanged. No issue comment or #1007 mutation is part of this documentation handoff.

Publication boundary: the assignment permits a reviewed draft documentation PR when the plan is complete and no unresolved
maintainer decision makes publication premature. The annotation blocker is settled. Conditional feasibility slices are
explicit future evidence gates, not publication blockers. No issue creation/closure, milestone changes, implementation
worker, production code, Gradle configuration or runtime behavior belongs to this assignment.

R1 is a single explicit revisit checkpoint after higher-tier implementation and matched Schelm qualification. For each
deferred candidate, record needed/useful/nice-to-have/unnecessary/not feasible, the smallest concrete example, viable
workaround, evidence, owner and bounded next action. Promote only an independently reviewable quick win preserving the
accepted contracts. A failed proof or costly redesign remains deferred. Composition stays on its separate 4.3-oriented
track regardless of checkpoint results. Hive reconciliation is required; execution completion is not archive permission.
