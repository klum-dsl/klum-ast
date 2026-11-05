# #1015 — Struct capability inventory and documentation reconciliation

Original inventory: 2026-11-04. Inspected master: `912f56682` (merged #1024).
Related: [#1015](https://github.com/klum-dsl/klum-ast/issues/1015),
[#952](https://github.com/klum-dsl/klum-ast/issues/952) and
[ADR 0032](../adr/0032-builder-oriented-structured-input.md).
The original inventory was prepared for the maintainer's decision round; the #1026 branch reconciliation below records
the separately authorized keyed-sequence follow-up.
**The original dispositions below are proposals; #1026 and #1030 separately authorize their bounded follow-ups.**
Issue #1015 remains open for Hive reconciliation and the maintainer decision round.

## Authority and reconciliation

The completed R1 source was recovered with
`git show 2f437422136dcbd63ff73c012f18b0aa8f5ef356:docs/implementation/input-round-r1-review.md`.
Its original C/K/F/L identifiers are retained below so each recommendation can be traced without republishing the
historical report as a current support claim. The ordinary Groovy DSL is the parity reference. Natural syntax and
bounded semantic cost determine eligibility; absence of an adopter request alone does not justify deferral.
Complexity assessments are source-based, relative estimates, not feasibility proofs or qualification results.

Current source, not the dated status prose in ADR 0032, determines what this pass documents:

| R1 item | Current reconciliation and evidence |
| --- | --- |
| C1 concrete unkeyed List; C3 unkeyed Set and sorted Set variants | Delivered by SEQ-1. `List`, `Set`, `SortedSet` and `NavigableSet` accept child-body sequences for concrete nongeneric unkeyed children. Remove these from the missing-feature list. [SEQ-1 evidence](input-round-seq1-evidence.md) and `StructuredOwnedSequenceTest` cover operation order, equality/comparators, partial failure and Template recipes. The separately authorized #1026 follow-up now adds concrete nongeneric keyed variants; see the reconciliation row below. |
| C2 / keyed C3 | Implemented on the #1026 branch for concrete nongeneric owned List/Set/sorted Set child sequences. Each item requires its actual externally named String construction key before allocation and uses normal fresh-child preparation/attachment. `StructuredKeyedSequenceTest` and `BinaryOwnedRelationshipsTest` cover naming, duplicates, lifecycle, Templates, source-free projection and interpretation. Merge/hosted qualification remain separate; see [#1026 evidence](issue-1026-keyed-sequence-evidence.md). |
| F2.0 single explicit literal alternative | Delivered only for the exact abstract keyed `Map<String, Element>` family. `StructuredLiteralAlternativeTest` and [F2-B evidence](input-round-f2b-evidence.md) retain the one-alias, concrete nongeneric subtype and inherited String-key restrictions. |
| F2.1 multiple explicit literal alternatives | Implemented on the #1030 branch for the same qualified abstract keyed Map family. Complete generated associations and concrete bodies remain mandatory; supplied Map traversal order is independent of canonical metadata order. `StructuredMultipleLiteralAlternativeTest`, multi-alias corruption coverage, source-free plugin and JPMS consumers qualify the bounded groups. Merge/hosted qualification remain separate; see [#1030 evidence](issue-1030-multiple-literal-evidence.md). |
| L1-A/B exact String converter List/Map | Delivered for exact `List<T>` and `Map<String, T>` of non-DSL values. `StructuredListConverterTest`, `StructuredMapConverterTest` and [L1-B evidence](input-round-l1b-evidence.md) retain literal Map keys and exclude CharSequence-only container converters. |
| Direct children, keyed Maps and C1/C3 admission machinery | #1021 cut 3 removes unused public creator-overload requirements for direct concrete owned children and String-keyed owned Maps. Like sequences, these use normal internal relationship preparation/attachment. `StructuredOwnedRelationTest` and ADR 0032's [cleanup](../adr/0032-builder-oriented-structured-input.md#owned-field-relationship-admission-cleanup--1021-cut-3) supersede R1's broad public-creator premise for these relationships. Executable scalar/operation selections, virtual sinks and literal CollectionFactory provenance still need their exact associations. |
| Remaining R1 candidates | No admission found in current `StructuredInputAdmissionPlan`, `StructuredInputConverter`, `InputContractResolver` or their tests. Retained and split where different semantics need different decisions below. This inventory pass itself authorizes no implementation; separately authorized #1026 and #1030 branch results are recorded explicitly. |

Implementation was inspected together with `CompiledBuilderSchema`, `StructuredInputSupport` and the plugin's
`JsonSchemaProjection`. Compiler classification, runtime interpretation and generated schema must agree. A structurally
valid String need not convert successfully; schema projection is not runtime parameter validation. Current master
is an implementation checkpoint, not proof of a release or renewed consumer qualification.

## Supported examples and page coverage

Use one consistent pattern: a representative Groovy call and its caller-parsed Map/YAML, or a direct link to the
canonical example when repeating it would add no explanation. The actual Schema selects the language; there is no
universal Groovy-to-YAML translation. All current user prose stays under `docs/user/`.

| User pages reviewed | Documentation treatment and existing executable authority |
| --- | --- |
| [Basics](../user/Basics.md), [Convenience Factories](../user/Convenience-Factories.md), [Factory Classes](../user/Factory-Classes.md) | Link Factory/Builder forms, distinguish named-map calls and `FromMap`, and expose key-mapping boundaries. `StructuredFactoryTest` covers common/explicit-key roots and void active-Builder application. |
| [Structured Input](../user/Structured-Input.md) | Canonical linked-example guide, operation Groovy/YAML pair, positional construction keys, native/converter collections, concrete virtual scopes, finite explicit literal alternatives, zero-argument commands and ordered failure. `StructuredInputDocumentaryTest`, `StructuredInputKeyDocumentaryTest`, `StructuredOwnedSequenceTest`, converter tests and `StructuredCommandTest` already make these guarantees executable. |
| [Templates](../user/Templates.md) | Pair the existing keyless recipe with ordinary Template creation and YAML; retain recipient/lifecycle semantics. `StructuredInputTemplateDocumentaryTest` already compares the ordinary and structured recipes. Template objects and `Template.With` orchestration are not YAML selectors. |
| [Converters](../user/Converters.md), [Alternatives Syntax](../user/Alternatives-Syntax.md) | Retain exact scalar and bounded collection/literal examples; explicitly distinguish owned producers, extra alternatives and automatic names. Existing converter-origin, CharSequence, List/Map and literal-alternative tests supply authority. |
| [Advanced Techniques](../user/Advanced-Techniques.md), [Layer 3](../user/Layer3.md), [Inheritance](../user/Inheritance.md) | Link supported Builder operations; explain closure/query/result and Cluster-scope boundaries. Inheritance changes the compiled contract rather than adding a YAML keyword. `StructuredOperationSelectionTest`, `StructuredInputRuntimeTest` fixed-Cluster-key case and binary relationship tests supply authority. |
| [Copy Strategies](../user/Copy-Strategies.md), [Default Values](../user/Default-Values.md), [Model Phases](../user/Model-Phases.md), [Validation](../user/Validation.md) | No duplicated YAML for Schema policies or phases. Normal defaults, validation, ownership and lifecycle still apply; Struct is not `copyFrom`, a reset language or callback scheduling. Canonical boundaries are linked from Structured Input. |
| [JSON Schema](../user/Json-Schema.md), [Jackson Integration](../user/Jackson-Integration.md) | Keep schema projection tied to admitted input and Jackson foreign-data binding/export separate. Add a reciprocal route pointer, without promising parser behavior, codecs, reference lookup or round trips for Struct. |

No new user page or API is added, so sidebar/migration changes are unnecessary. No new feature/fix is delivered, so
`CHANGES.md` keeps its existing grammar entries. Per the maintainer's clarified acceptance contract, five dedicated
`StructuredYamlDocumentaryTest` examples parse plain inline YAML with caller-owned `YamlSlurper` before invoking public
Struct Factory/Builder entry points. They cover keyed root/explicit-key/active-Builder bodies, an authored Map operation,
direct and Map child identity, owned sequence order/duplicates and reusable Templates. Each links its relevant user
section through `@See`; the class carries `@Issue('1015')` and `@Tag('documentary')`. Existing grammar fixtures remain
unchanged except that the JPMS fixture now selects only the shipped adapter's Jackson core/databind/annotations modules
from its production runtime classpath, instead of sweeping in a YAML backend or newer Jackson versions from the parser's
test dependencies. Lane-matched `groovy-yaml` dependencies are
test-only; its transitive Jackson backend is parser implementation, not Klum managed import. No production loader or
parser API is added.

## Remaining collection and key forms

**YAML forms in the remaining inventory are unavailable comparisons unless a row explicitly records a separately authorized branch implementation.**
An “implement in 4.2” proposal still requires a separate maintainer decision and implementation/qualification.
“Defer” means retain the option pending the stated contract/proof, not reject it for lack of Schelm demand.
Groovy fragments assume the Schema declaration described in the same row.

| Item and ordinary Groovy form | Missing/rejected Struct/YAML form | Natural representation | Complexity and viable workaround | Proposed disposition |
| --- | --- | --- | --- | --- |
| K1: `@Field(keyMapping = { it.toLowerCase() }) Map<String, String> values`; `value 'BLUE'` | `values: [BLUE]` invokes the exact native value-only Builder adder. | Yes: a value sequence avoids duplicated derived keys. | Delivered in the #1028 implementation branch using existing field/PROPERTY selection; ordinary mapping owns keys, replacement and failures. | **Implemented for 4.2**, pending hosted/merge reconciliation. See [#1028 evidence](issue-1028-derived-map-evidence.md). K2/K3/K4 remain deferred. |
| K2: converter-backed derived-key `Map<String, Duration>`; `wait 'PT1S'` | `waits: [PT1S]` is not L1-B's literal-key Map shape. | Yes: sequence of converter inputs. | Medium: combine K1 with an exact value-only converter association; prove order, derivation and failures. Groovy or an authored List operation works. | **Defer** until K1's value-sequence contract and the converter association are proven; eligible for a bounded 4.2 decision. |
| K3: `@Field(keyMapping = { it.secondary }) Map<String, Bar> bars`, unkeyed `Bar`; `bar { secondary 'blue' }` | `bars: [{secondary: blue}]` is unavailable. | Yes: configure each body, then derive the stored key. | Medium: normal fresh-child/replacement behavior differs from outer-keyed reuse; prove null keys, ownership and diagnostics. Groovy works without a Schema change. | **Defer** pending acceptance/proof of the value-sequence semantics. |
| K4: same derived-key Map with keyed `TwoBar`; `twobar('boink') { secondary 'blue' }` | `twobars: [{key: boink, secondary: blue}]` is unavailable (`key` denotes the actual child key member in this example). | Yes, with two distinct identities. | Medium beyond K3: child identity precedes configuration; derived Map key follows it. Prove repeated identities and replacement. Groovy works. | **Defer** until the two-identity contract is selected; never reinterpret the derived key as construction identity. |
| Additional typed keys: `Map<Integer, String> labels`; `label(7, 'blue')` | `labels: {7: blue}` and stringified-key coercion are not admitted. | A data sequence or encoded key could work, but neither is selected; YAML/JSON key rules differ. | Medium/high generic key conversion and collision policy. Use String-keyed Schema input plus an authored operation, or Groovy. | **Defer** typed-key grammar; retain literal String keys for current Maps. |

## Remaining alternatives and producers

F2.0 is delivered and omitted here. A producer's result cardinality alone is not a reason for deferral: F2.5–F2.7 can
each have a compact single-input form. Each still needs a separately selected exact producer family and lifecycle proof.

| Item and ordinary Groovy form | Missing/rejected Struct/YAML form | Natural representation | Complexity and viable workaround | Proposed disposition |
| --- | --- | --- | --- | --- |
| F2.1 (authorized by #1030): explicit `fixed` and `rolling` alternatives on the F2-B keyed Map family; `elements { fixed('a') {}; rolling('b') {} }` | `elements: {fixed: {a: {}}, rolling: {b: {}}}` is implemented on the #1030 branch. | Yes: closed alias groups, without claiming arbitrary repeated/interleaved alias calls. | Medium: complete finite associations, counts, collisions, group order and concrete keyless schemas. Groovy works; one alias reduces expressiveness. | **Implemented on #1030 branch**; final delivery and issue disposition remain with Hive. |
| F2.2: alternatives on owned List/Set; `elements { fixed('a') { value 'x' } }` | Possible `elements: [{fixed: {name: a, value: x}}]` is not admitted. | Yes, but per-item wrappers versus grouped arrays need a decision; groups lose cross-alias order. | Medium: concrete body/identity selection, sequence order and Set outcomes. Groovy works. | **Defer** pending wrapper/identity contract; unkeyed SEQ-1 does not supply subtype selection. |
| F2.3a: concrete declared keyed base with literal subtype; `elements { fixed('a') {} }` | An alias mapping for that base is outside F2-B's abstract-base restriction. | Yes: existing alias shape, but whether base bodies coexist is undecided. | Low–medium admission relaxation plus collision/schema checks. Groovy or a concrete direct relationship works. | **Defer** until base-versus-alias language is selected; a bounded 4.2 option. |
| F2.3b: unkeyed or keyMapping alternative target; `elements { fixed { secondary 'a' } }` | Literal outer-key bodies cannot supply this contract; a value sequence is unadmitted. | Yes, contingent on K3/K4 and F2.2 choices. | Medium: derived key versus child identity and alias envelope. Use Groovy. | **Defer** with the corresponding derived-key/sequence contract. |
| F2.4: automatic shortName/stripSuffix creators; `elements { subby('a') {} }` | `elements: {subby: {a: {}}}` is not admitted merely because the Groovy method exists. | Yes if frozen as a finite compiler-owned vocabulary. | Medium: completeness across inheritance/artifacts and name collisions. Explicit supported literal alternatives or Groovy work. | **Defer** the finite-vocabulary decision; **reject** runtime subtype scanning/class-name inference. |
| F2.5: one fixed String-to-owned-Builder producer; `elements { fromProfile('small') }` | `elements: {fromProfile: small}` is unavailable. | Yes: one input, exact producer. | Medium/unproven inert association, dispatch, same-session result and attachment/callback proof. Groovy or an eligible Schema-authored adapter works. | **Defer** pending selection/proof of this exact producer family; possible 4.2 bounded slice. |
| F2.6: fixed Collection-of-Builders producer; `elements { fromProfile('small') }` | Same possible single-input mapping, not an input array based on result cardinality. | Yes. | Medium: association plus iteration, empty/null/invalid elements, ownership and first-failure partial attachment. Groovy works. | **Defer** pending this result/destination family's proof, independently of F2.5. |
| F2.7: fixed Map-of-Builders producer; `elements { fromNamedProfile('small') }` | `elements: {fromNamedProfile: small}` is unavailable. | Yes. | Medium: exact association plus result-key/child-identity/derived-key distinction, order, invalid values and partial attachment. Groovy works. | **Defer** pending exact result/destination selection; do not infer F2-B input-key authority. |
| F2.8: overloaded/multi-input factory calls; `elements { fromProfile('small', 3) }` | No argument-spreading/call-envelope grammar. | A domain-specific mapping could be natural for one factory. | High for generic defaults/overload/argument binding. Wrap one admitted Map/List payload or use Groovy. | **Defer** generic factory binding; this does not defer fixed single-input producers merely for returning collections. |
| F1: owned DSL String producer; `registry 'uri://blue'` via `Registry.fromString` | `registry: uri://blue` is not scalar S3a/L1 support; a concrete `registry: {uri: uri://blue}` body may already work. | Yes: useful scalar shorthand. | Medium/unproven producer association, unsealed same-session result, key/reuse/owner/callback and source attribution. Use the admitted body or Groovy. | **Defer** pending the bounded F1 proof/decision; **reject** attaching completed owned Models or nested root materialization. |

R1's generic F2.3 tail is retained as an **unproven research boundary**, not an established ordinary-versus-Struct
parity gap. `StructuredInputAdmissionPlan.recordLiteralAlternative` explicitly excludes generic declared/selected targets,
but this pass found no executable ordinary generic-alternative example to establish the corresponding Groovy guarantee.
No supported Groovy form or candidate YAML shape is asserted for that tail. Proposed disposition: **defer** until a
concrete ordinary supported substitution is identified and its exact Builder/body/key authority is proven across
artifacts. A supported nongeneric concrete Schema route remains the workaround; this boundary does not justify a
general generic-polymorphism engine.

## Remaining payloads, operations and orchestration

| Item and ordinary Groovy form | Missing/rejected Struct/YAML form | Natural representation | Complexity and viable workaround | Proposed disposition |
| --- | --- | --- | --- | --- |
| L1 tails, Set converters: `Set<Duration> waits`; `wait 'PT1S'` | `waits: [PT1S]` is unavailable for converter-backed Sets. Native scalar/enum Sets and owned unkeyed Sets are different admitted families. | Yes. | Medium exact association/population and equality/path proof. Groovy or an eligible typed List operation works. | **Defer** pending Set converter proof; not a blanket Set exclusion. |
| L1 tails, CharSequence container converters: `List<Duration> waits` using only `Duration.parse(CharSequence)`; `wait 'PT1S'` | Same String sequence/Map syntax is unavailable without an exact String converter. Scalar CharSequence admission does not extend to elements/values. | Yes. | Low–medium exact generated-hook/association and projection proof. Supply an unambiguous exact String converter or use Groovy. | **Defer** pending the exact container association proof; eligible for bounded 4.2 consideration. |
| Other converter inputs: `Date birthday` with a field converter taking `long`; `birthday 123L` | `birthday: 123` is not admitted merely because Groovy has that converter. Multi-input converters likewise have no argument-list form. | A primitive input is natural for one fixed converter; generic coercion is not selected. | Medium exact converter association, type/range and selection rules; multi-input binding adds further cost. Use an admitted String converter, an eligible domain operation, or Groovy. | **Defer** additional exact converter input types; no inference from String-origin support. |
| L2 NamedParams/NamedParam: authored `endpoint(host: 'api', port: 8080)` | No closed heterogeneous named-key/required/type projection is supplied just by named-parameter annotations. An eligible typed Map payload already works as one argument. | Yes: object members are natural; admission versus projection must be distinguished. | Medium inert named-key facts, required flags, heterogeneous types and inheritance under ADR 0022. Use a typed Map operation with body validation. | **Defer** pending L2 facts/contract proof; do not describe all Map operations as missing. |
| General multi-argument/default/varargs call: `storage('uri://a', 'uri://b')` | No YAML argument list/envelope selects and spreads positional arguments. | Sometimes natural as a domain mapping, not universally. | High generic overload/default/binding semantics. One admitted Map/List operation or Groovy works. | **Defer** general binding. |
| Repeated/interleaved calls: `tag 'a'; reset(); tag 'b'` | No generic YAML event sequence; duplicate Map member names cannot encode repeated calls. | A sequence of operation envelopes is possible but unselected. | Medium/high language and schema expansion. Use a domain List operation or Groovy; parser duplicate rejection remains caller-owned. | **Defer** event grammar; ordinary owned sequences are not command streams. |
| Implicit reset/clear/replace: a typed ordinary setter or authored `clearRoutes()` | `routes: null`, `routes!: []` and implicit replacement are unavailable. Eligible authored `clearRoutes: {}` is already supported. | Explicit domain commands are natural and delivered; generic reset tokens require a policy. | Medium semantic decision for omission/default/replacement and ownership. Expose an eligible command/one-payload operation. | **Defer** implicit field reset/replace; **reject** interpreting null as a call marker under the current grammar. |
| Virtual-scope tails: keyed/abstract/generic/customized/overloaded virtual fields; `repository('id') { ... }` | No mapping body for these excluded families. Bare concrete unkeyed nongeneric S5 scopes already work. | Possible for a selected fixed family, not arbitrary Closure adaptation. | Medium–high unique sink/creator association, identity, pre-PostApply interval and binary proof. Use ordinary Groovy or an eligible fixed domain operation. | **Defer** bounded virtual extensions; **reject** synthesizing arbitrary closures from YAML. |
| Arbitrary objects/callables: `handler { ... }`, `consume(existingObject)`, `configure(Set)` | No caller-object, Closure/SAM, broad Iterable/Collection, custom-container or Object payload grammar. | No faithful generic JSON-compatible representation; data-only typed List/Map wrappers can be natural. | High generic object construction and invocation policy. Use an eligible typed data operation or Groovy. | **Reject** generic object/callable transport; reconsider only a separately bounded data contract. |
| Explicit reference/type/factory selection: `link existing`, `child(SubType) { ... }`, `child(SubType.Create) { ... }` | No `$ref`, class name, Factory token or caller-object selector. LINK/OPTIONAL_LINK input is excluded, including mixed owned/reference collections. | A domain identifier is natural only with Schema/caller-owned lookup policy. | High generic registry/ownership semantics. Provide context through ordinary Groovy before the phases that need it, or use separately designed managed Jackson binding where appropriate. | **Reject** generic Struct lookup/type selectors; this does not reject fixed literal alternatives or ordinary AutoLink/LinkTo behavior. |
| Closure-only grouping/Cluster/query/result calls: `clusters { field { ... } }`, Builder queries and result chaining | No generic Cluster wrapper/query invocation/result-binding syntax. Independently admitted concrete fields (including fixed Cluster keys) remain addressable directly. | Direct fields are natural; arbitrary executable grouping has no faithful generic form. | High closure adaptation/capability expansion. Address admitted field names or use Groovy. | **Reject** generic execution/group wrappers; no inference that fixed-key relationships are unavailable. |
| Template/copy/deferred orchestration: `Template.With(t) { ... }`, `copyFrom(donor)`, `applyLater { ... }` | No YAML Template selector, donor transport or callback/phase keyword. `Create.Template.FromStruct` already defines recipes. | Data recipes are natural and delivered; object identity/executable scheduling is caller orchestration. | High generic object/lifecycle transport. Use caller-managed Template scopes/copying/deferred operations with admitted bodies. | **Reject** these generic Struct directives; retain ordinary Template/default/lifecycle semantics. |
| Composition: caller applies several configuration layers | No merge/reset/layer envelope or precedence policy; repeated `applyStruct` is not a composition coordinator. | Yes, but a separate contract owns identity, precedence and failures. | Cross-cutting coordinator work. Caller orchestration/Groovy remains viable. | **Defer** to the separate composition track (4.3 direction in ADR 0032), without assigning a milestone here. |

## Maintainer decision round and delivery boundary

Native derived-key population (K1) is implemented by #1028. #1030 separately authorizes and implements multiple
explicit literals (F2.1) on this branch. The maintainer selected keyed sequences first through #1026; its implementation
is reconciled above. The other bounded candidates remain selectable independently after their listed contract/proof;
none is excluded solely because current Schelm documents have a workaround. The generic mechanisms marked reject
would force awkward syntax or disproportionate machinery for parity. Maintainer acceptance/revision remains outstanding.

No production/compiler/runtime/plugin changes, new issues, issue closure, labels, milestones or curation changes
belonged to the original #1015 documentation pass. The #1026 follow-up changes only keyed-sequence admission and its tests/docs. Documentary tests and their lane-specific parser dependencies are the only executable additions.
The draft PR relationship is **Related: #1015**. Hive must reconcile the maintainer decisions and
merged delivery before closing the issue; this inventory alone establishes neither completion nor archive safety.

Validation includes the focused YAML documentary examples, the repository's required checks for executable tests and
build dependencies, documentation rendering and rendered link crawl, Markdown relative file/heading links and balanced
fences, plus `git diff --check`. Exact results and scope are reported in the delivery handoff. The same two independent
review subagents, Standards and Specification, review the follow-up; no additional review agents are introduced.
No new consumer run, release qualification or local Sonar analysis is claimed.
