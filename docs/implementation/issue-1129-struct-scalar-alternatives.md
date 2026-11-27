# #1129: finite Integer/String Struct alternatives

Date: 2026-11-27. Status: **Accepted language direction; implementation plan proposed for review. No implementation authorization.**

Related: [#1129](https://github.com/klum-dsl/klum-ast/issues/1129).
Decision: [ADR 0032 extension](../adr/0032-builder-oriented-structured-input.md#finite-integerstring-operation-alternatives--1129).
Authority: [bounded v1](https://github.com/klum-dsl/klum-ast/issues/1129#issuecomment-7225119384) and
[separate annotations](https://github.com/klum-dsl/klum-ast/issues/1129#issuecomment-7225170536).
These comments supersede the issue body's earlier open-design questions. Metadata transport and commit boundaries below
are reviewable implementation proposals, not additional maintainer decisions or delivered capabilities.

## Scope and evidence

Source baseline: fetched master `d91110749618a6fdb4fa605e128236a57758d3db`. This is a category-B extension of ADR 0032,
whose generated-contract, construction and ordered-execution architecture remains authoritative. ADR 0031 retains
source-free inspection and the closed schema catalog; ADR 0034 retains separate parsing and output qualification.
The existing [input-round plan](input-round-plan-4-2.md), [S2 selection evidence](input-round-s2-evidence.md),
[source-free contract extraction](issue-959-schema-s1-contract.md) and [constraint catalog](issue-959-schema-s2-catalog.md)
identify reusable seams. Their historical qualification does not qualify this extension.

The primary need is one external member accepting integer or String without artificial arm names. ScHelm
[#82](https://github.com/klum-dsl/schelm/issues/82) and [PR #85](https://github.com/klum-dsl/schelm/pull/85) are the concrete
downstream evidence recorded on #1129, including the renamed-arm workaround. Their current delivery is not certified by
this plan. Core tests use only neutral `Destination.address` fixtures; no Kubernetes, ScHelm or IntOrString dependency.

This assignment edits only this plan and ADR 0032. It adds no production/test source, annotation, generated artifact,
build/dependency, issue/milestone, release or ScHelm change. Current user documentation describes current behavior and
stays unchanged until implementation. No runtime/schema qualification is claimed from documentation checks.

## Confirmed behavior and source seams

Paths in this table are repository-relative source locations at the pinned baseline.

| Module / seam | Confirmed behavior | Required bounded change |
| --- | --- | --- |
| `klum-ast-annotations`: `Struct.Operation` | Method marker documents one selected String route; requires a public Builder projection | Document only the finite two-mark exception during implementation; retain separate `Builder.Method` |
| `klum-ast`: `StructuredInputAdmissionPlan.classifyOperation`, `selectProvisionalGroup`, `restoreAuthoredFacts` | AST candidate authority is String-only; public `GeneratedInput.Operation.authoritative` already records the original mark for every authored projected operation, including integer; naming/role/examples/deprecation and `Schema.operationCount` are emitted | Preserve both marked native arms provisionally; final hierarchy/profile resolution remains runtime-owned; freeze original parameter facts before projection can lose their origin |
| `klum-ast-runtime`: `generated.GeneratedInput` and `internal.BuilderContractReader` | Protocol 1; exact class-resource identity and descriptor checks; explicit role/authority/marker agreement, names, examples, deprecation, operation count; no authored-operation parameter constraints | Extend existing operation enrichment only for missing arm-local constraint facts, with explicit completeness; preserve all existing integrity checks |
| Runtime `InputContractResolver` | `authoritativeString` selects one entry; multiple eligible String marks throw; `retainUnmarkedCollisions` and `overriddenOperation` are String-sensitive; exclusions and effective names precede final admission | Recognize only the exact opted-in pair; include integer overrides in effective-family resolution and avoid pruning viable inherited arms before descendant naming |
| Runtime `CompiledBuilderSchema.Entry` | One payload and dispatch handle; `methods` can also describe unrelated multi-method relationship protocols | Add a closed immutable scalar-family representation containing two complete arm entries; do not reinterpret an arbitrary `methods` list as overload alternatives |
| Runtime `StructuredInputSupport.Traversal.execute`, `scalar`, `Invocation.invoke` | Selection precedes strict scalar conversion; standard Number allowlist and `intValueExact`; exact parameter-signature dispatch is checked against the actual Builder | Select a family arm by decoded scalar kind once, then reuse conversion and exact invocation; preserve diagnostics and ordered partial effects |
| `klum-ast-gradle-plugin`: `JsonSchemaProjection.property`, `BuiltinConstraintCatalog` | Same resolved schema as runtime; one property's description/examples/deprecation; authored operations bypass `constrain`; catalog hard assertions currently target PROPERTY sites | Render two complete branches; narrowly admit supported family-parameter constraint sites without changing unrelated OPERATION/PROPERTY behavior |
| Runtime `SelectedConverterParameterReader` | Exact separately compiled static String factory/constructor authority; rejects overloaded selected families and decodes a bounded parameter vocabulary | Keep unchanged; this is not authority for authored overload parameters and must not become general converter discovery |

No new public Factory/Builder callable signatures, modules, exports, reflection escape, descriptor API, parser, invocation
engine or serialization format is required. The proposed family result remains private to resolver/interpreter/projector.
Reuse complete arm entries so each retains exact declaration, implementation dispatch, payload, source and prose identity.

## Selection and rejection contract

1. Validate each declaring contract and reachable contract graph without initializing Schema code. Resolve original
   field/operation roles, exclusions, effective names/profiles, exact signatures and descendant overrides first.
2. Keep the existing single-route algorithm unless the effective candidate group is exactly two eligible authored
   scalar operations, both marked, with one `int`/`Integer` parameter and one exact String parameter. The new pair path
   must validate both arms' required facts before returning a family. A pair does not bypass ignored roles, missing
   projections, signature-generation collisions or inconsistent facts.
3. Unmarked Integer/String overloads retain collision rejection. One marked String or CharSequence operation plus
   unmarked competitors still selects only that operation and rejects numeric input. A lone mark on an integer operation
   does not acquire new priority over an unmarked String: retain its baseline behavior. Unique native integer operations
   remain callable as before. Unsupported multiple-mark groups still reject during contract resolution.
4. V1 families have exactly two eligible candidates; a third same-name candidate does not silently join the family.
   Excluded candidates retain existing exclusion precedence. Generated PROPERTY/converter routes cannot become authored
   arms through a copied marker. Integer+Long, Integer+decimal, int+Integer, String+CharSequence, multiple Strings or
   converters and any larger marked set fail the family rule. Keep existing ordinary collision omission/diagnostics
   versus strict naming/multiple-mark errors; do not turn every legacy collision into a compilation error.
5. For a validated family, raw `input instanceof String` selects String unchanged. Otherwise only the current numeric
   allowlist (Byte, Short, Integer, Long, Float, Double, BigInteger, BigDecimal) can select the integer arm. Reuse current
   finite/exact int32 conversion, independent of parser-chosen wrapper. Integral `BigDecimal('8080.0')` succeeds;
   fractions, overflow, NaN/infinities and arbitrary Number subclasses fail with no String fallback. Preserve current
   Float/Double semantics; do not claim recovery of precision already lost before Struct receives the value.
6. Null, Boolean, Map, List, GString, mutable CharSequence and arbitrary objects reject for this family. Select once;
   invoke the recorded signature, never try each arm, run conversion bodies during inspection, or redispatch using the
   converted argument class. An operation-body exception propagates with its original cause, without trying the other arm.
7. Preserve FromStruct, active Builder application, Template construction/application, key positions, ownership, lifecycle,
   same-thread/sealing and cycle fences. Earlier ordered effects persist when a later family input fails; later operations
   do not run. No preflight, rollback, parser change or automatic JSON Schema validation is added.

Inherited/overridden Integer arms need the same source-identity discipline as String arms. An override owns its current
marker, name, exclusions, prose and constraint facts. Never retain parent facts for different descendant code. Naming
profiles may join or separate effective members; do not make provisional source-name pruning the final authority.

## Metadata and compatibility proposal

### Existing selection facts are sufficient

The public Builder already contains both exact JVM descriptors. Each authored projection has a required
`GeneratedInput.Operation` role and explicit `authoritative` Boolean, checked against the actual `@Struct.Operation`;
its declaring `GeneratedInput.Schema.operationCount` counts all operation facts, including exclusions. Each arm's names,
examples and source-deprecation facts already exist; AnnoDocimal supplies exact declaration Javadoc. A generated field
converter has different field/selection/provenance authority. These facts distinguish the opted-in pair without adding
an alternative-group annotation, group count, numeric discriminator, capability bit or schema-specific registry.

### One actual transport gap: authored parameter constraints

Operation enrichment currently has no parameter constraint array, count or policy. Reflection over a moved method, a
destination field, or the converter reader cannot establish original authored parameter facts. Proposed implementation:
extend the existing `GeneratedInput.Operation` enrichment with compiler-frozen `constraints`, explicit `constraintCount`
and parameter `constraintProjection`, using the existing inert `GeneratedInput.Constraint` rows. Capture parameter zero
from the original authored declaration with `StructuredInputConstraintMetadata`, including conservative source eligibility,
literal descriptions and supported first-level composition. Do not copy Bean Validation annotations to create executable
Builder constraints. Reuse existing catalog mappings/PROJECT, TEXT_ONLY and OMIT semantics; new constraint families and
parameter enforcement are separate work. Unsupported declarations retain truthful branch-local documentary information.

Use absent/unknown defaults (for example count `-1`) for historical operation facts. A family requires an explicitly
emitted count, even zero, and matching rows and policy on each arm. Missing, duplicate, malformed or inconsistent facts
fail before any family is admitted, even under OMIT or when its member is absent from supplied input. Existing single-route
contracts retain baseline interpretation and projection rather than requiring these new facts or gaining new assertions.
Branch constraints belong to that original parameter only; the ignored observation field supplies none.

**Keep unified protocol version 1 in this proposal.** Selection requires no new serialized fact; the missing constraint
transport is an additive extension with unknown defaults required only by the newly admitted family. No existing
version-1 single-route fact changes meaning or becomes newly mandatory. A speculative version 2 would reject unrelated
single-route artifacts without supplying additional selection evidence. Retain strict existing version checks; do not
add an annotation-specific version or legacy decoder. Before code delivery, prove these claims with byte-mutation and
source-free consumers in U1/U3. If implementation cannot preserve them, stop for a concrete compatibility revision to
this plan rather than inventing a bump or silently weakening checks.

| Artifact condition | Required result |
| --- | --- |
| Matched producer/runtime/plugin; recompiled participating Schema hierarchy with complete pair facts | Both consumers resolve the same two arms and branch facts |
| Historical version-1 single route / one marked String with unmarked integer | Baseline selection, schema and failures remain unchanged; new family facts are not required |
| Historical version-1 two-mark Integer/String artifact lacking new arm completeness | New consumers reject the family with recompile/package guidance; never silently degrade to String or assume empty constraints |
| Missing Schema/Operation enrichment, removed arm fact/marker, wrong operation count, descriptor/role/name disagreement, unsupported protocol, missing parent/dependency or wrong resource origin | Shared reader/resolver fails closed using existing integrity diagnostics; neither consumer guesses from implementation methods |
| Removed/duplicate/invalid arm constraint facts or policy, including under OMIT | Fail before use; no successful partial family or fresh partial schema output |
| Recompiled child with incompatible/unrecompiled ancestor participating in a pair | Reject the incomplete effective family and identify the declaring artifact; rebuild/package the participating hierarchy with the matched toolchain |
| New Schema on an old runtime/plugin | No support claim: old consumers cannot be retroactively taught family admission or new completeness checks; they may see only the legacy String route. Require matched artifacts, never present this as binary family qualification |

Completeness detects ordinary omission/corruption, not coordinated malicious rewriting of metadata and its counts. Equal
provenance version strings are not compatibility proof. Preserve ordinary Builder calls and completed-Model use; retain
an actual pre-change Schema JAR for unchanged single-route qualification rather than recompiling away the binary boundary.
No mixed-release, historical publication, adopter or general round-trip promise follows.

## JSON Schema and arm ownership

The sole effective member emits `anyOf` in canonical integer-then-String order, independent of declaration/reflection order.
The integer branch retains `type: integer`, minimum -2147483648 and maximum 2147483647, with supported arm assertions
conjoined using existing collision handling. String emits `type: string`. No extra property or null/Boolean/object/array
branch is created. Declaration prose, supported constraints, documentary String examples and deprecation are rendered
inside their own branch. The property wrapper holds the alternatives, without synthetic merged documentation or facts.

PROJECT emits only already supported truthful assertions for the new parameter site; TEXT_ONLY and OMIT preserve their
existing meanings and never remove structural type/range restrictions. Preserve conservative grouped/unsupported constraint
behavior and compiler-frozen source attribution. Use an integer Min/Max arm and a bounded supported Pattern String arm
to distinguish ownership. A numeric-looking String matching that Pattern remains a String. Examples remain String
documentation, including on the integer branch; they are not typed/validated samples or defaults.

Schema assertions do not become runtime parameter validation: tests must distinguish structural runtime admission from
optional authoring assertions. Operation bodies and lifecycle may reject schema-valid data. Output generation still validates
the complete reachable contract before atomically replacing files; errors retain earlier successful outputs and manifests.
Excluded legacy collisions remain absent with `x-klum-unsupported`, not unrestricted placeholders.

## Thin vertical slices and reasoned commits

All slices require a later explicit implementation assignment. U2 depends on U1; U3 depends on U2. Do not publish an
intermediate grammar expansion whose runtime and schema candidate sets disagree. Every new executable test uses the
`Test` suffix and `@Issue('1129')`; existing tests gain issue annotations only for significant behavioral changes.

### U1 — One neutral family from authored Schema to invocation and emitted schema

One cohesive feature/test commit establishes the smallest complete path: retain two marked AST candidates, emit explicit
empty parameter facts, validate them, resolve one immutable two-arm result, select raw numeric/String input at execution,
and render the actual two-branch property. Keep the scope exactly Integer/String. A companion documentation commit
updates `Struct.Operation` Javadoc, the selection section of `docs/user/Structured-Input.md`, `docs/user/Json-Schema.md`,
applicable migration guidance/navigation and the next unreleased `CHANGES.md` section, without inventing a release target.

Focused coverage: new `StructuredScalarAlternativeTest`, compiler `StructuredInputAdmissionPlanTest`, resolved-contract
`CompiledBuilderSchemaTest`, and new packaged `CompiledScalarAlternativeJsonSchemaTest`. Demonstrate Integer and primitive
int producers separately, `8080`, `'8080'`, `'http'`, integral BigDecimal and int32 endpoints. Assert exact selected call
counts/types and zero unselected calls; schema validation consumes the actual emitted artifact. Check absent/empty new
facts, unmarked and one-mark controls before admitting the happy path. This establishes the version-1 additive/default
decision, rather than postponing contract compatibility until after runtime admission.

Qualification for this slice: focused Groovy 3 compiler/runtime/plugin cases, affected compiler/runtime suites and their
Groovy 4/5 lanes; packaged G3/G4/G5 Schema producers and source-free generation through the existing TestKit harness.
Compile an independent public-API client against each Schema JAR, remove source/class directories and execute against
matched runtime artifacts. No new public descriptors/exports; assert that and run applicable binary/JPMS controls.
Include a documentary happy path (`StructuredScalarAlternativeDocumentaryTest`, `@Tag('documentary')`, `@See` to the
current user-page section) from ADR 0032's Destination example. State any U2 boundaries explicitly while this slice is local.

### U2 — Preserve arm facts and all rejection boundaries

One feature/test commit completes original-parameter constraint capture, immutable per-arm binding and branch-only
catalog projection. Include different descriptions, examples, source deprecation and policies on the two arms, and verify
they never leak to the wrapper, sibling arm, destination field or unrelated input use. Extend existing compiler fact and
reader corruption seams; leave converter constraints and existing single routes unchanged. A separate reasoned test
commit may cover hierarchy and failure regressions once their behavior is green; do not commit deliberately failing tests.

Focused coverage: `StructuredOperationSelectionTest`, new scalar-family tests, `InputNamingProfileTest`,
`CompiledBuilderSchemaReaderTest`, `StructuredInputRuntimeTest`, Template documentary seams and packaged schema tests.
Exercise same-compilation and parent/child JAR inheritance, overridden integer/String arms with new/removed marks/names,
ignored candidates, KEBAB_CASE collisions/separation, reversed declarations, missing projections and generated converter
controls. Include every unsupported family and input in the acceptance matrix below. Add initialization/validator traps
with deliberate positive controls, malformed constraint rows/counts/policies and suppressed output. Assert source name,
escaped pointer, exact declaring arm and original exception cause; earlier effects survive and later calls stay absent.

Qualification: focused G3 tests and affected suites, G4/G5 compatibility, packaged source-free G3/G4/G5 projection and JAR
client cases for nonempty parameter facts. Repeat binary/JPMS checks where emitted facts or dispatch changed. Validate
actual generated schemas with branch-distinguishing positive/negative documents and preserve output-on-failure controls.
Extend the documentary example and `Structured-Input.md`/`Json-Schema.md` with arm-local assertions, policies, rejection and
partial-failure limits; synchronize migration guidance and the existing unreleased note without a new release commitment.

### U3 — Qualify artifacts and finalize user delivery

One acceptance/test commit extends the established `RootRecipeConsumerTest` / `GeneratedSchemaProvenanceConsumerTest`
independent-process pattern and plugin TestKit fixtures, reusing rather than duplicating those harnesses. Retain separate
parent/child Schema JARs, matched candidate libraries and the pre-change single-route JAR. Independently compile Java,
static Groovy and dynamic Groovy clients that call FromStruct and ordinary public Builder methods. Run on Groovy 3/4/5
classpath lanes and applicable Groovy 4/5 named-module lanes without compiler/plugin at application runtime, with sources
and loose producer classes removed. Exercise new and unchanged contracts, primitive/boxed descriptors, hierarchy facts,
missing parents and malformed metadata. Record exact producer/consumer artifacts, hashes, commands and results.

Run packaged schema generation on those same JAR contracts and feed the emitted documents into an independent Draft
2020-12 validator; a hand-authored union or a JSON map-shape assertion is not acceptance evidence. Keep a checked-in neutral
data corpus and fixed assertions independent of the renderer. The existing fixture mechanism exposes generated files under
`klum-ast-gradle-plugin/build/schema-validation`; use that seam. No core runtime validator/dependency or ScHelm fixture is
introduced. Family membership must agree between runtime and schema; schema-only constraints are asserted separately.

Finish with root `./gradlew check` for the cross-module generated-contract change, required Gradle plugin/TestKit and
binary/JPMS checks, all affected Groovy 3/4/5 lanes, documentation checks, history review and independent Standards/Spec
review. Add a final documentation/evidence commit only where needed: update implementation status and all feature/test/user
links, migration navigation, unreleased note and issue acceptance pointers under the later assignment's authority. Check
hosted CI and SonarCloud on the exact final PR head; local success is not hosted or downstream delivery proof.

## Requirement-to-acceptance matrix

| Confirmed requirement / retained boundary | Slice and executable acceptance |
| --- | --- |
| Both authored arms explicitly marked; Builder/Struct admission separate | U1 compiler + runtime + emitted schema: both marks/projections required; marker alone does not create a Builder method; annotations stay distinct |
| One Integer or int plus String; no owned IntOrString/ADT | U1 neutral Destination fixture for both numeric declarations; U2 unsupported Boolean/Map/List families and arbitrary Object payload reject |
| Unmarked collisions and old one-mark behavior | U1 unmarked pair absent/rejected, String-only mark accepts `'8080'` but rejects 8080; U2 lone numeric mark and existing String/CharSequence selected controls; U3 retained old JAR |
| Authored only; generated converter admission unchanged | U2 ordinary generated Integer/String converter controls retain current single String route or existing collision outcome; neither joins a family; U3 source-free equivalent |
| Exact numeric conversion, actual Strings stay Strings | U1 values/types/call counts; U2 every standard Number wrapper, integral/scaled BigDecimal, int32 endpoints, fractions/overflow/nonfinite/custom Number failures; no retry; emitted-schema corpus validates corresponding JSON numbers/Strings |
| No overlapping or larger family | U2 marked Integer/Long, int/Integer, integer/decimal, String/CharSequence, duplicate renamed Strings, converters and third-candidate controls reject without method-order dependence |
| Shared effective candidate set | U1 resolved two-arm result consumed by both; U2 names/profiles/exclusion/override/inheritance matrix with runtime calls and actual schemas from the same fixtures; U3 same JAR inputs |
| Arm Javadoc, constraints, examples, deprecation | U2 exact compiled parameter provenance and differing branch facts, policies, unsupported/grouped forms; assert absent wrapper/sibling leaks and no destination-field facts; actual validation distinguishes branch assertions |
| Null/Boolean/Map/List excluded; strict Strings | U2 null, Boolean, mappings/sequences, GString, mutable CharSequence and arbitrary object cases; generated schema rejects JSON null/Boolean/object/array |
| Exact invocation and no Schema execution during inspection | U1/U2 selected signature/call counters, primitive versus boxed, thrown body cause/no retry; initializer, constructor, lifecycle and validator traps with positive controls in source-free projection |
| Missing, old or malformed contracts fail closed | U1/U2 ASM mutations: missing Operation/marker/count/descriptor/constraint rows/policy/protocol; U3 missing parent, resource shadow/mismatch, recompiled child with incomplete parent; both consumers reject before family use, even OMIT/absent payload |
| Single-route and construction behavior retained | U2 order/partial effect, unknown names, keys, active-thread/unsealed and post-failure cleanup, Template definition/replay controls; U3 binary single-route clients and existing suites |
| Actual generated-schema validity and truthful scope | U1/U2/U3 Draft 2020-12 validator on emitted artifacts, int32 bounds, branch ownership, deterministic order and atomic retained outputs; schema-valid body failure and schema-only constraint distinction |
| Groovy/JAR qualification and docs | Every slice: applicable G3/G4/G5 and source-free producer checks; U3 Java/static/dynamic clients and applicable JPMS; documentary `@Issue`/`@Tag`/`@See`, user/test/issue cross-links, migration/navigation and unreleased note |
| #1113 output and downstream work separate | U3 delivery brief states no reconstruction/adoption claim; no ScHelm/Kubernetes dependency, source edit or test in any slice |

## Readiness, risks and delivery boundary

Language decisions are settled. Proposed internal representation and additive constraint transport need technical review,
not a new product interview. The main risks are premature candidate pruning, inherited integer override attribution,
confusing family selection with existing multi-method relationships, widening catalog sites for unrelated operations,
and accepting historical pair metadata as complete. U1/U2 prove those boundaries before final artifact qualification.
No unresolved product choice is hidden as an implementation prerequisite; any contradictory source/qualification result
returns for a concrete design revision. No speculative metadata bump or future family machinery is authorized.

Planning validation: `git diff --check`, Markdown structure and relative file/heading links, issue-decision/source-seam
consistency and independent two-axis review. These architecture-only Markdown files are outside current user-site
rendering inputs; Groovy lanes and runtime/schema validation are deliberately not run for this documentation delivery.
All executable checks above are future implementation acceptance, not claimed results of this plan.

Review/change brief: ADR 0032 records the accepted finite exception and unchanged boundaries; this plan supplies source
evidence, compatibility/failure rules, three dependent vertical slices and full acceptance mapping. Implementation, tests
and user-facing documentation changes: none in this PR. Auxiliary changes: none. Review focus is the proposed arm-local
constraint completeness extension under protocol 1 and exact inherited candidate handling.

Tracker impact: **related issue #1129; curation impact none**. Use `Related: #1129` only. The issue remains open for later
implementation and acceptance; this plan changes no release gate, dependency, owner, issue state/label/milestone or
published release fact. A documentation-only draft PR is authorized after completed local review/checks; merge is separate.
Report exact branch/base/head, validation, review and hosted-check state in the handoff. Request Hive reconciliation;
do not self-archive while the PR or its reconciliation remains open.
