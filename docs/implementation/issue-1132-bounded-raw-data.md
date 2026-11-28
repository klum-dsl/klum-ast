# #1132: bounded raw-data Struct payloads

Date: 2026-11-28. Status: **Accepted contract; proposed implementation slices; no implementation authorized.**

Authority: the confirmed [#1132 issue body](https://github.com/klum-dsl/klum-ast/issues/1132), read on 2026-11-28
(no issue comments). Decision: [ADR 0032 extension](../adr/0032-builder-oriented-structured-input.md#bounded-raw-data-payloads--1132).
[ADR 0034](../adr/0034-bounded-structured-authoring.md) governs shared-tree, parser and output separation.
This document proposes internal encoding and commit seams; those details do not broaden the accepted language.

## Decision and scope

Category B: extend ADR 0032 and add this dedicated plan. Its compiled-contract authority, ordered interpretation,
Factory/Builder/Template entry points and shared runtime/projector resolver already own this capability. ADR 0034 supplies
the bounded data vocabulary without admitting every shared tree into Struct. A new ADR would duplicate those owners;
no conflicting architectural evidence requires one. ADR 0031's source-free inspection and explicit schema validation,
ADR 0003's lifecycle/materialization and ADR 0015's generated/runtime linkage boundary continue to apply.

The primary need is a Schema-owned external configuration subtree, immediately useful to ScHelm; its issue calls 4.3 a
tentative candidate, not an assigned release. A consumer adapter/homogeneous map is viable but costly. V1 is explicit
composable `@Struct.RawData` on an exact `Map<String, Object>` field or exact one-payload `@Builder.Method`. The method
needs Builder projection, not an additional `@Struct.Operation`. Direct and transitive domain-annotation placement are
accepted. Unsupported targets and unmarked Object shapes remain outside Struct. The full usage example is in the ADR;
R1 below turns it into an executable documentary example only after implementation authorization.

Only this Markdown plan and ADR 0032 change now. No production/test/build/dependency change, implementation worker,
release/milestone/label/dependency-edge change, issue closure or adopter upgrade is part of the assignment.

## Evidence and confirmed current behavior

Source baseline: current origin/master `63b06e9076dcadcd511af61e3a5876bce9a20e20`, inspected in an isolated
issue worktree. These are source observations and existing-test seams, not a newly executed reproduction or qualification.

ScHelm [#88](https://github.com/klum-dsl/schelm/issues/88) describes the pass-through configuration need.
[PR #91](https://github.com/klum-dsl/schelm/pull/91), head `142e87a29fd4cb7318214ba1a9c1560dd9b68eab` when read,
reports a neutral public KlumAST 4.2.1 / Groovy 5.1.3 / Java 21 reproduction through real ModelFileLoader, FromStruct and
active applyStruct. It reports ordinary Builder assignment and a typed nested-map control succeeding, the raw field absent
from generated properties with `x-klum-unsupported`, and the authored heterogeneous-map method rejected too. Its local
59-case and schema-oracle results are downstream reported evidence, not independently rerun here; the PR was open and
its body said final hosted qualification was running. Its numeric/resource proposals do not override #1132. No private
EPR fixture, ScHelm source/fixture or dependency is copied into KlumAST; R1 supplies a neutral native reproduction.

Important current seams (paths below are relative to the repository):

| Owner / source | Inspected behavior and planned touch point |
| --- | --- |
| `klum-ast-annotations/.../Struct.java` | Namespace includes Required, Operation, naming and documentary annotations; RawData does not exist. Add one runtime-retained field/method/annotation-type marker; retain separate Builder.Method vocabulary. |
| `klum-ast/.../StructuredInputAdmissionPlan.java` | `payloadForValue` rejects Object, placeholders and unsupported leaves. `payloadForField` descends into Map value Object and returns no payload; `classifyProperty` records “has no admitted scalar, enum, List, or Map payload” absent a selected converter. `classifyOperation` rejects the same one-Map argument as outside the admitted grammar. This is intentional opt-in admission, not a parser failure. |
| `klum-ast/.../DSLASTTransformation.java` | `createMapOfSimpleElementsMethods` generates Map convenience population via `addElementsToMap`, plus entry adders. `classifyProperty` selects population for ordinary maps. A one-Map signature here is not a replacement setter; do not reuse it as one or change ordinary Groovy calls globally. |
| `klum-ast/.../StructuredInputConstraintMetadata.java` | `rawInputRequired` / `carriesMarker` is an existing cycle-safe transitive annotation traversal. Reuse that bounded pattern for compile-time RawData composition, including separately compiled annotation types. Do not use Bean Validation composition as runtime admission. |
| `klum-ast-runtime/.../generated/GeneratedInput.java` | Field/Selection and authored Operation facts supplement exact public signatures; Schema.operationCount and explicit facts govern completeness. Add minimal inert capability facts rather than Model reflection or a parallel descriptor resource. |
| `klum-ast-runtime/.../internal/BuilderContractReader.java` | Selective exact public declaration-byte inspection; explicit protocol 1 and required literal validation; no Schema initialization. Extend its closed facts and integrity checks. |
| `klum-ast-runtime/.../internal/InputContractResolver.java` | `leafPayload(Object)` cannot succeed; `containerPayload` therefore cannot admit the heterogeneous map. Field dispatch currently expects `(String, item)` for Map population. Extend only opted-in exact targets and produce one shared normalized result. |
| `klum-ast-runtime/.../internal/CompiledBuilderSchema.java` | Immutable payload/role/entry facts shared by interpreter and generator. Add a bounded RawData payload distinction, not arbitrary recursive Type/Object inference. |
| `klum-ast-runtime/.../internal/StructuredInputSupport.java` | `value` rejects null before its typed payload switch. Sequence/DataMap build fresh ArrayList/LinkedHashMap containers; a path-local visiting identity set rejects cycles and permits repeated acyclic values. PROPERTY MAP uses `populateMap`, making zero calls for empty maps; authored arguments are checked/materialized before one exact-signature Invocation. RawData needs its own recursive leaf policy and whole-field route. |
| `klum-ast-runtime/.../internal/InternalKlumBuilder.java` | applyStruct delegates to the same interpreter with current active same-thread, ordinary, unsealed guards; applyConfiguration owns PostApply. Existing field assignment asserts mutability. Model materialization snapshots outer collections/maps; `completedValue` is shallow for non-Builder members. Do not infer recursive Model immutability from fresh input copying. |
| `klum-ast-runtime/.../internal/CopyHandler.java`, `TemplateRecipeState.java`, `KlumTemplateProxy.java` | Copy recursively rebuilds supported containers; map donor null skips, and empty-map behavior depends on the selected Overwrite strategy. Templates retain values and deferred recipe actions for recipient construction; they do not retain a Struct command log. Preserve these meanings. Current standard-container factories and tests incorporate #1117 work; use its qualified shapes, not arbitrary custom containers. |
| `klum-ast-gradle-plugin/.../runtime/internal/JsonSchemaProjection.java` | `payload` recursively emits finite typed shapes; `property` adds source prose/deprecation/examples and currently invokes field constraint projection. Add one recursive data definition and keep outer annotations separate; skip unaccepted RawData constraint assertions. |
| `klum-ast-gradle-plugin/.../gradle/GenerateKlumJsonSchema.java` | Private projector runs with the consumer's inspected artifacts. Extend packaged-consumer qualification; no public generator API or dependency is needed. |

The `...` source paths above abbreviate package prefixes only; find exact classes under each module's `src/main/java`.
The [architecture map](issue-curation/architecture-map.md), [S1 contract extraction](issue-959-schema-s1-contract.md),
[input-round plan](input-round-plan-4-2.md), [S2 operation selection](input-round-s2-evidence.md) and
[Builder-first migration](../user/Builder-First-Migration.md) retain their respective responsibilities and dated evidence.

## Admission, execution and compatibility design

### Exact targets and frozen facts

Validate the marker on the authored declaration before generic leaf/Map classification. Exact means declared raw type Map
with exactly String and Object generic arguments: reject raw Map, Map<?, ?>, bounds/wildcards/type variables, specialized
Map declarations, List<Object>, Object, arbitrary Number/Class/DSL values, and nested typed shapes such as
Map<String, List<Object>> as RawData targets. Method placement is on the method, not its parameter; require one payload
and a real eligible public Builder projection. A marker does not admit a private/static/query/lifecycle/helper method,
remove an exclusion, create a converter, or bypass effective-name collisions. Retain existing rejection/error conventions;
malformed explicit use should identify the source and required exact form, not silently widen the grammar.

Fold direct/transitive annotation use once in the compiler. Preserve declaring field/method identity through public
projection, inheritance/overrides and restored authored facts. The runtime reads compiler-frozen facts, not arbitrary
annotation graphs or Model fields. Existing name/profile, Ignore/Jackson compatibility, Required and operation selection
rules remain. Test the RawData method without Operation; an optional Operation mark retains ordinary selection semantics,
and grants neither a union with typed overloads nor a fallback. The #1129 Integer/String family remains separate.

Proposed minimal encoding: one internal RAW_DATA payload kind; a field-specific whole-value role/selection association
separate from PROPERTY population; and a folded RawData capability on the original field/accessor or authored Operation
fact. Cross-check association, exact generic signature, source/role and capability before exposing an entry. Method
execution retains the exact recorded public descriptor. Field execution reuses guarded ordinary internal field assignment,
with one invocation-attribution event, rather than invoking the generated Map population overload or creating a new public
setter. Generated metadata records authority; it is not an extensibility API. Final member names are implementation choices.

Keep protocol 1 unless an executable mixed-artifact counterexample forces reassessment. Require matched annotations,
compiler/runtime/plugin artifacts and recompiled participating Schema hierarchies. Add explicit positive capability and
paired integrity evidence so a stripped/contradictory required fact fails before execution or schema publication. No
silent omission of an intended RawData member, fallback to unrestricted Object, or schema-only admission is acceptable.
R1 must prove old runtime/new Schema and old projector/new runtime failure paths with actionable recompile/upgrade guidance;
R3 expands packaging coverage. Existing old Schema contracts must still behave as before under the matched new stack.
If an old reader ignores additive facts, the selected new field role must still reject, and unrecognized method Object
payloads must stay rejected; prove actual behavior rather than assuming annotation evolution supplies safety. Unsupported
artifact combinations need no decoder, bridge, registry, compatibility matrix framework or speculative version bump.

### Recursive values and one reached operation

Use the current internal Value/materialize seam (or an equally narrow internal helper), reusing fresh ordered JDK Maps and
Lists. Do not build a public JSON node hierarchy, generic serializer, arbitrary Number adapter or configurable dialect.
The RawData root must be Map; descendants may be Map/List/String/Boolean/null/finite exact numbers. A nested null uses a
real null scalar leaf rather than null in a List.copyOf of internal Value nodes. Keep the existing global null rejection
for every other Struct payload and for the RawData root.

Exact String keys remain byte-for-byte decoded Strings: no GString/CharSequence coercion, normalization, dotted-path
expansion, name profile or framework meaning for keys such as '$ref', '<<' or class-like names. Such ordinary String keys
remain data, never reference/tag/execution instructions. Maps preserve supplied iteration order, not an invented order
for unordered callers; Lists preserve element order. Each encountered container is copied, including repeated acyclic
containers at multiple placements. Detect cycles with the active ancestry identity set, not a global seen set that rejects
valid repetition or preserves aliases. Reject self-cycles, Map/List mutual cycles and a back-edge into the enclosing domain
input; retain source/member/index/key diagnostics. Consider an explicit traversal stack to avoid unsafe recursion without
introducing a public depth quota. Duplicate detection and parser marks cannot be recovered after parsing into a Map.

Canonical numeric results are BigInteger/BigDecimal with exact values, no numeric-looking String conversion, arbitrary
Number execution or NaN/infinity. A narrow implementation recommendation is exact integer-wrapper to BigInteger and
BigInteger/BigDecimal preservation, with finite Float/Double conversion preserving their actual represented binary value
in BigDecimal; no claim to recover an original decimal token. Qualify that conversion explicitly (including 0.1d, large
integers/decimals, exponent/scale, signed zero and overflow boundaries), rather than reusing typed scalar conversion that
can round to float/double. This recommendation selects no new numeric text/spelling or trailing-zero promise; unsupported
representations must reject rather than round. No parser or Struct numeric-size budget is introduced here.

Check/copy the complete reached tree before its single invocation. Omitted member does nothing; any supplied Map, empty
included, replaces for a field and calls the authored method once with the fresh whole Map. Its body owns the domain
updates, as for other authored operations. An invalid descendant invokes neither the field assignment nor the method;
earlier separate operations remain, later ones do not run. Preserve original method failure causes. No document preflight,
rollback, implicit overlay, recursive merge or changed homogeneous-map population follows.

Defaults and initializers run as before. A field's supplied empty map clears its current value in that Struct operation;
it does not alter Template/copy donor rules. Template creation through FromStruct captures the resulting value/recipe;
application and copyFrom use ordinary overwrite policy and lifecycle, not replay of the original RawData command. For
example, an empty Template map under FULL_REPLACE may preserve recipient defaults, while ALWAYS_REPLACE clears them;
a null map donor still skips under current semantics. Retain copy isolation between recipients/donors without asserting
new nested completed-Model immutability. RawData admits data, not owned DSL nodes, LINK attachments, construction sessions
or retained Builders. Ordinary Builder/Groovy assignment is not retroactively validated as RawData input.

### Truthful JSON Schema

Generate Draft 2020-12 from the same resolved RawData kind. Reserve one deterministic, collision-safe `$defs` identity for
all RawData uses in the generated document; do not mutate it with member metadata. Its recursive shape is:

```json
{
  "$defs": {
    "RawDataValue": {
      "anyOf": [
        {"type": "object", "additionalProperties": {"$ref": "#/$defs/RawDataValue"}},
        {"type": "array", "items": {"$ref": "#/$defs/RawDataValue"}},
        {"type": "string"}, {"type": "boolean"}, {"type": "integer"},
        {"type": "number"}, {"type": "null"}
      ]
    }
  },
  "type": "object",
  "additionalProperties": {"$ref": "#/$defs/RawDataValue"}
}
```

This is a payload illustration, not a new root document format or a fixed public definition name. The outer member remains
object-rooted; directly referencing the all-value union would wrongly admit root Lists/scalars/null. Use anyOf because an
integer is also a JSON Schema number. Keep existing surrounding closed domain-body shapes and optional member semantics.
Multiple fields/methods share the recursive definition but keep their own Javadoc, deprecation and documentary examples
on their outer use. Existing field Struct.Required affects its surrounding domain object's required list; it does not
make nested keys required or establish runtime presence validation. It does not gain method placement.

Bypass RawData hard constraint lowering, including Size/minProperties/maxProperties and recursive/container-element
constraints. Existing documentary policy may describe unprojected constraints at the outer site; it must not alter the
recursive grammar or attach one member's prose to all others. No embedded custom schema fragment, schema-derived execution
or automatic FromStruct/applyStruct gate. JSON Schema cannot express JVM classes, cycles, alias separation, Map iteration
order, numeric wrapper canonicalization, copy/replace/lifecycle effects; qualify those with runtime tests. Validate actual
emitted documents independently, using the existing Python Draft202012Validator test-oracle pattern with exact decimal
handling rather than a second production validator or schema snapshot alone.

## Dependency-ordered slices and reasoned commits

These are separate implementation assignments after explicit maintainer/Hive authorization. Each slice has green focused
acceptance and a reviewable boundary; none alone establishes full feature delivery or release qualification.

### R1 — minimal compiler/runtime/schema vertical

Depends on the accepted contract only. Reuse the neutral fixture style from StructuredInputContractFixtures and existing
StructuredInputRuntimeTest / StructuredOperationSelectionTest / KlumJsonSchemaGenerationTest seams.

1. **Admit and execute the exact explicit payload:** reproduce the unmarked field/method rejection with typed-map and
   ordinary Builder controls; add RawData annotation, direct marker classification, frozen facts/reader/resolver and shared
   kind; implement bounded recursive copy plus whole-field assignment and exact authored invocation. Commit tests with
   the implementation that makes them pass, never a committed failing reproduction. Include minimal artifact mismatch
   guards so partial publication cannot weaken admission.
2. **Project the same grammar:** add one recursive schema definition and outer metadata handling, with plugin generation
   tests and actual-output positive/negative oracle checks. This commit completes the three-module vertical, not a new
   standalone schema language. Add a small `RawDataDocumentaryTest` (name may be refined) carrying @Issue('1132'),
   @Tag('documentary') and @See linked to the new section in Structured-Input.md, synchronized in this slice.

Focused acceptance: direct field and one method without Operation; mixed nested map/list, dotted keys, Strings including
'8080', false, zero, exact BigInteger/BigDecimal, nested null and empty containers. Assert root Map-only grammar; one
invocation/replacement for nonempty and empty; omitted field preserves initializer; caller and repeated-container aliases
are absent. Invalid deep leaf and a cycle reject before this call, with a correct path and retained earlier effects.
Generate and validate actual recursive schema (integer accepted despite number overlap), root type and two members with
different outer prose/deprecation/examples/Required facts. Verify no recursive constraint assertions or initialization.
Retain typed-map/unmarked Object/null and basic single-route/scalar-alternative controls. Run focused Groovy 3 tests for
compiler/runtime consumers plus plugin tests; defer broad compatibility and packaging verdicts to R2/R3 explicitly.

### R2 — annotation, active Builder, Template/copy and rejection boundaries

Depends on R1. Reuse StructRequiredTest's composed-marker scenarios, StructuredFactoryTest, StructuredInputRuntimeTest,
StructuredInputTemplateDocumentaryTest and StandardContainerCopyIntegrationTest/StandardContainerCopyTest. Prefer shared
scenario sources in `klum-ast/src/test/scenarios/` or the existing test fixture corpus to duplicate embedded schemas.

1. **Freeze direct/transitive declaration identity:** qualify domain annotations (including transitively composed and
   separately compiled types), inherited fields/methods, overrides, renamed/profiled members, Ignore and collisions. Cover
   explicit exact-type/placement/arity rejection and stripped/missing/contradictory facts; no marker-driven widening.
2. **Qualify construction and retained recipes:** active applyStruct repeated omission/replacement/empty calls in one
   session, FromStruct and Template.FromStruct, ordinary copy/default/overwrite behavior, donor and recipient deep-copy
   separation, and lifecycle/failure events. Keep these assertions with any narrowly required repairs; do not redesign
   CopyHandler or its strategies. Identify any genuinely separate #1117 gap rather than silently expanding that work.

Acceptance matrix runs Groovy 3/4/5 for the affected modules and scenarios:

- All exact declaration negatives above, missing Builder.Method, zero/multi/default/varargs payload shapes, ignored or
  inaccessible targets, incompatible hierarchy facts and competing names. No generic inference or Object fallback.
- Deep nesting; repeated acyclic placement; Map/List/self/enclosing-domain cycles; non-String/null/GString keys;
  GString/mutable CharSequence leaves, Set/array, custom objects, Classes, closures, arbitrary Number subclasses and
  nonfinite numbers. Keep literal '$ref'/'<<'/tag-like Strings as inert data. Prove exact finite numeric conversion and order.
- Member missing versus supplied {}, root null/List/scalar rejection versus nested null acceptance; sibling ordinary null
  rejection unchanged. Invalid trees leave that receiver value intact and stop later calls; operation-body failure keeps
  ordinary partial effects and original cause. A neighboring homogeneous Map still populates and empty input makes zero calls.
- Active same-thread/unsealed/current-session success; sealed, Template receiver, foreign-thread and expired session
  rejection. Preserve existing PostCreate/PostApply/PostTree/materialize/validate ordering with exactly one RawData event.
- Initializers/defaults, multiple Template/copy recipients, nested null/empty values and explicit overwrite policies retain
  current semantics. Check mutable and recognized JDK immutable/unmodifiable nested Maps/Lists and completed-Model donors,
  including ordinary materialization's shallow nested-container behavior; no recursive immutability claim.
- Compiler and source-free resolver produce equivalent entries; no Schema initialization during admission or projection;
  ordinary Groovy assignments/method dispatch and managed Jackson remain unchanged. Qualify absence of newly projected
  RawData Size/recursive/parameter assertions, without changing separately supported outer documentary metadata.

Each new executable class uses Test suffix and @Issue('1132'); significant amendments to older tests carry the driving
issue. New documentary features link @See and user docs to the precise test/method. No pending/ignored test is needed in
this documentation assignment; any deliberate implementation deferral needs an actionable issue/slice reason.

### R3 — source-free artifacts, JPMS, documentation and adoption qualification

Depends on R1 and R2, not on #1113/#1114 or ScHelm delivery. Reuse CompiledConverterJsonSchemaTest's domain/parent/schema
producer-consumer source removal pattern and existing Java/static/dynamic Groovy consumers, BinaryOwnedRelationshipsTest
and JpmsPackageBoundaryTest/module-path fixtures; extend existing harnesses rather than add ScHelm or a generic framework.

1. **Qualify packaged boundaries:** build producer Schemas/domain annotations and inherit across JARs; remove sources and
   producer classes; consume only artifacts for FromStruct/applyStruct and root/body schema generation. Cover all three
   Groovy producers, supported Java/static/dynamic Groovy consumers, and applicable Groovy 4/5 named modules. Test old/new
   artifact rejection paths and unchanged old Schemas with a matched new stack, no user-code inspection. Capture exact
   commands, artifact versions and actionable mismatch diagnostics. Retain normal JPMS exports/opens; no internal export,
   add-opens workaround, parser/Jackson dependency or module/public ABI expansion without an actual generated call site.
2. **Finish user and qualification records:** update Structured-Input.md, Json-Schema.md, Templates/copy guidance where
   affected, Migration.md navigation and CHANGES.md's next release section when implementation exists. Add a Sidebar link
   only if a current user page is added/renamed/removed. Keep examples aligned with documentary tests and link issue, test
   and prose. Explain opt-in, matched artifacts, nested null, replacement versus population/copy, exact values and caller
   resource responsibility. Record focused evidence, complete-build/CI/Sonar and explicit adopter qualification separately.

Run affected suites and Groovy 4/5 lanes, root `./gradlew check` for the shared cross-module contract, relevant binary/JPMS/
plugin TestKit checks, actual generated-schema oracle and user-documentation render/crawl plus diff/link checks. Hosted CI
and SonarCloud must be inspected on the final published implementation head; local success does not substitute for them.
Downstream acceptance remains ScHelm-owned: recompile its Schema with matched candidate artifacts, use real configuration
member(s) through ordinary FromStruct/active applyStruct without flatten/type-guess adapters, then qualify its loader,
packaged CLI and intended output under its own #88. KlumAST supplies a neutral evidence handoff; no consumer implementation,
version upgrade, YAML replacement or whole-Model reconstruction claim is authorized by this plan.

## Requirement-to-acceptance map

| Confirmed requirement / retained boundary | Implementation and focused acceptance |
| --- | --- |
| Composable exact field and one-payload method; Builder.Method independent of Operation | R1 direct vertical; R2 transitive/binary annotation, declaration and collision matrix; R3 source-free consumers |
| Ordered acyclic Map-rooted bounded values, literal exact keys, nested null, finite canonical numbers | R1 tree copier/schema oracle; R2 full key/value/numeric/root/cycle/repetition and order controls |
| Fresh deep copies with no caller aliases or identity | R1 nested/repeated-container identity assertions; R2 donor/multi-recipient copy and Template isolation |
| Omission preserves; every supplied Map invokes once/replaces; no merge | R1 field/method call counts and {} controls; R2 active repeated calls, defaults, neighboring population and ordinary overwrite policies |
| Existing lifecycle, ownership, order and first-failure semantics | R1 reached-argument failure; R2 active guards, exact event order, earlier/later effects and method-body failure |
| One recursive anyOf definition; object-rooted use; outer prose/deprecation/Required/documentary metadata only | R1 emitted schema and independent oracle; R2 annotation/constraint leakage and definition-sharing controls; R3 root/body artifact generation |
| Matched artifacts, fail closed, no speculative protocol/compatibility machinery | R1 minimum mismatch/required-fact tests; R2 corrupt/transitive facts; R3 packaged combinations and recompile/upgrade diagnostics |
| Groovy 3/4/5, Java, source-free and applicable JPMS qualification | R1 focused baseline; R2 all affected lanes; R3 producer/consumer/module-path, full check and hosted evidence |
| No parser policy/quota/duplicate detection, schema runtime gate, custom fragment, Jackson change, Model inverse or generic object framework | All slices inspect scope; R2 ordinary null/Groovy/Jackson controls; R3 dependency/API/docs review and qualified adopter wording |
| No ScHelm dependency/fixtures; #952/#1113/#1114/#1117 remain adjacent | Neutral fixtures R1–R3; downstream handoff only, no dependency edges or release reconciliation in this plan |

## Risks, remaining evidence and handoff

No product decision is reopened. The field route is the main trap: a generated Map argument overload currently populates,
so selecting it would violate replacement/empty-map semantics. The second trap is relaxing the global null/Object path,
which would silently broaden other operations. Shared definitions must not leak member constraints/metadata. Composition
must survive binary annotation/hierarchy projection without runtime annotation scanning or initialization. Finite binary
numbers need exact represented-value evidence, not a decimal-text guess. Very deep caller trees require safe cycle/traversal
handling without implying parser budgets. Completed nested containers keep ordinary materialization semantics; copy
qualification must not be mistaken for recursive Model immutability or #1113 reconstruction.

Internal fact/role names and helper boundaries remain implementation choices constrained above. If concrete evidence shows
protocol 1 cannot fail closed, or existing copy/lifecycle machinery cannot meet the accepted contract without a broader
change, stop that dependent slice with a neutral reproduction and request the specific decision; do not introduce a shim,
weaken acceptance or quietly bundle adjacent work. All runtime/schema/adopter acceptance in this document is future work.

Planning validation is Markdown structure, local links/fragments, contract mapping and `git diff --check`; no Groovy lane,
root build or user-site rendering is needed for these two non-executable architecture documents. Independent Standards
and Specification reviews and history review precede draft publication. Record their actual outcomes and any hosted
checks in the PR/handoff, not as completed implementation gates here.

Tracker impact: Related #1132 only; the feature remains open. No issue body/comment/state/label/milestone, release fact,
curation or native dependency changes. The requested draft PR is documentation review, never implementation authorization.
After planning delivery, maintainer/Hive must explicitly authorize an implementation slice; no worker is started now.
Review/merge and Hive delivery/archive reconciliation remain separate from execution completion.
