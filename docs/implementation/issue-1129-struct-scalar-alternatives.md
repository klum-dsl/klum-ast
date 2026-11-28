# #1129: finite Integer/String Struct alternatives

Date: 2026-11-27; reconciled 2026-11-28. Status: **U1 merged in PR #1131; U2 merged in PR #1134
(`71dedcf980486a1de08a258b9baa827ef4b9b941`); U3 artifact qualification and delivery documentation implemented.
Local full-build/review and remote delivery results are recorded in the [U3 evidence](issue-1129-u3-evidence.md).**

Related: [#1129](https://github.com/klum-dsl/klum-ast/issues/1129).
Decision: [ADR 0032 extension](../adr/0032-builder-oriented-structured-input.md#finite-integerstring-operation-alternatives--1129).
Authority: [bounded v1](https://github.com/klum-dsl/klum-ast/issues/1129#issuecomment-7225119384) and
[separate annotations](https://github.com/klum-dsl/klum-ast/issues/1129#issuecomment-7225170536).
These comments supersede the issue body's earlier open-design questions. Metadata transport and commit boundaries below
are reviewable implementation proposals. The subsequent maintainer review requires generic authored-parameter transport,
family-specific completeness and qualified projection sites (implemented through U2), followed by consolidated U1–U3 qualification.

## Scope and evidence

Source baseline: master `d91110749618a6fdb4fa605e128236a57758d3db`. This category-B extension retains ADR 0032 construction
and ordering, ADR 0031 source-free inspection/catalog and ADR 0034 parsing/output boundaries. Reuse the
[input-round plan](input-round-plan-4-2.md), [S2 selection](input-round-s2-evidence.md),
[contract extraction](issue-959-schema-s1-contract.md) and [constraint catalog](issue-959-schema-s2-catalog.md) seams;
their historical qualification does not qualify this extension.

ScHelm [#82](https://github.com/klum-dsl/schelm/issues/82) is closed and
[PR #85](https://github.com/klum-dsl/schelm/pull/85) merged at `446c9b2a7949b60d167c55181123e41f23b7f084` (accepted head
`ba47e7fd54dcaf4bcdb7386257b8962df46ad0a8`). It removed artificial arm aliases and deferred Struct scalar-union input.
The 2026-11-27 Schelm Hive consumer handoff confirms that the five actual uses—`ServicePort.targetPort`,
`HTTPGetAction.port`, `TCPSocketAction.port`, `RollingUpdateDeployment.maxSurge` and `maxUnavailable`—fit int32/String;
percentages are Strings. Its TargetField/presence/wire metadata supplies no KlumAST admission authority. The refinement
sharpens excluded-helper handling and consumer acceptance; it changes no accepted language or generated metadata scope.

The planning assignment (PR #1130) edited only this plan and ADR 0032; it added no production/test/build, issue/milestone, release, user-guide
or ScHelm change. Its documentation checks established no runtime/schema qualification.
The later, explicitly assigned implementations are recorded in [U1 evidence](issue-1129-u1-evidence.md) and
[U2 evidence](issue-1129-u2-evidence.md). Final artifact/compatibility qualification is consolidated in
[U3 evidence](issue-1129-u3-evidence.md); earlier records remain slice-dated evidence.

## Consumer acceptance and neutral fixture

With matched candidate artifacts and recompiled participating target Schemas, ScHelm can expose its existing authored
Integer/String Builder pair through FromStruct and applyStruct under the real field name: `targetPort: 8080`,
`targetPort: 'http'` and `targetPort: '8080'`. Exactly one arm runs and preserves Integer/String wire value. No artificial
arm names, new Struct-only forwarding adapters, consumer type-dispatch switch or extra envelope is needed. The existing
immutable IntOrString holder, storage/presence machinery, copy/omit behavior and wire serializer remain consumer-owned.
Additional Number/helper overloads stay ordinary-Builder-only; schema emits one int32/String property with arm-local facts.
Unsupported inputs/families reject without fallback or order-dependent alias selection. Adoption qualification remains
Schelm-owned, with no ScHelm/Kubernetes fixture or dependency in core tests and no #1113 reconstruction claim.

The [real overloads](https://github.com/klum-dsl/schelm/blob/446c9b2a7949b60d167c55181123e41f23b7f084/schelm-skeleton/schelm-core/kubernetes-targets/library/src/main/groovy/com/blackbuild/schelm/targets/pinned/ServicePort.groovy#L42)
currently ignore all four setters. Future adoption marks Integer/String only, removes their Ignore markers and keeps
Number/IntOrString helpers ignored; it authors arm prose on methods/parameters instead of borrowing ignored-field prose.
The removed aliases are not an active API to migrate. Existing Struct conversion may admit exactly integral decimal/float
wrappers that ScHelm's Number helper rejects, including `1.0d`: document this frontend distinction, not equal coercion.
See the [downstream review record](https://github.com/klum-dsl/schelm/blob/446c9b2a7949b60d167c55181123e41f23b7f084/doc/research/issue-82-maintained-schema-review.md).

Use ADR 0032's existing neutral `Destination.address` fixture for U2: marked Integer/String arms plus same-effective-name
Number and opaque-helper operations explicitly excluded by `@Struct.Ignore`. Excluded helpers do not invalidate the pair,
receive Struct calls or contribute schema properties/branches. Adding an eligible candidate rejects without fallback.
This is one extension of the shared fixture, not a new fixture framework or a separate resolver.

## Confirmed behavior and source seams

Paths in this table are repository-relative source locations at the pinned baseline.

| Module / seam | Confirmed behavior | Required bounded change |
| --- | --- | --- |
| `klum-ast-annotations`: `Struct.Operation` | Method marker documents one selected String route; requires a public Builder projection | Document only the finite two-mark exception during implementation; retain separate `Builder.Method` |
| `klum-ast`: `StructuredInputAdmissionPlan.classifyOperation`, `selectProvisionalGroup`, `restoreAuthoredFacts` | AST candidate authority is String-only; public `GeneratedInput.Operation.authoritative` already records the original mark for every authored projected operation, including integer; naming/role/examples/deprecation and `Schema.operationCount` are emitted | Preserve both marked native arms provisionally; final hierarchy/profile resolution remains runtime-owned; freeze original parameter facts before projection can lose their origin |
| `klum-ast-runtime`: `generated.GeneratedInput` and `internal.BuilderContractReader` | Protocol 1; exact resource/descriptor checks; role/authority/marker agreement, names, examples, deprecation, operation count; no authored-parameter constraints | Add reusable authored-parameter facts and reading; require completeness for newly admitted families, preserving existing integrity checks |
| Runtime `InputContractResolver` | `authoritativeString` selects one entry; multiple eligible String marks throw; `retainUnmarkedCollisions` and `overriddenOperation` are String-sensitive; exclusions and effective names precede final admission | Recognize only the exact opted-in pair; include integer overrides in effective-family resolution and avoid pruning viable inherited arms before descendant naming |
| Runtime `CompiledBuilderSchema.Entry` | One payload and dispatch handle; `methods` can also describe unrelated multi-method relationship protocols | Add a closed immutable scalar-family representation containing two complete arm entries; do not reinterpret an arbitrary `methods` list as overload alternatives |
| Runtime `StructuredInputSupport.Traversal.execute`, `scalar`, `Invocation.invoke` | Selection precedes strict scalar conversion; standard Number allowlist and `intValueExact`; exact parameter-signature dispatch is checked against the actual Builder | Select a family arm by decoded scalar kind once, then reuse conversion and exact invocation; preserve diagnostics and ordered partial effects |
| `klum-ast-gradle-plugin`: `JsonSchemaProjection.property`, `BuiltinConstraintCatalog` | Same resolved schema as runtime; one property's description/examples/deprecation; authored operations bypass `constrain`; catalog hard assertions currently target PROPERTY sites | Render two complete branches; narrowly admit supported family-parameter constraint sites without changing unrelated OPERATION/PROPERTY behavior |
| Runtime `SelectedConverterParameterReader` | Exact separately compiled static String factory/constructor authority; rejects overloaded selected families and decodes a bounded parameter vocabulary | Keep unchanged; this is not authority for authored overload parameters and must not become general converter discovery |

No new public Factory/Builder callable signatures, modules, exports, reflection escape, descriptor API, parser, invocation
engine or serialization format is required. Extend the existing resolver and immutable result for both consumers; no
separate family-resolution pipeline. The proposed family result remains private to resolver/interpreter/projector.
Reuse complete arm entries so each retains exact declaration, implementation dispatch, payload, source and prose identity.

## Selection and rejection contract

1. Validate the reachable contract graph without initializing Schema code; resolve roles, exclusions, effective
   names/profiles, exact signatures and descendant overrides first. Ignored same-name Number/opaque helpers neither
   count, receive Struct calls nor contribute schema branches/properties. Inconsistent required metadata still rejects.
2. Before legacy selection, classify multiple nonexcluded authored marks as an attempted family. Admit only an entire
   eligible group of two marked authored scalar operations: `int`/`Integer` and exact String, with complete facts.
   Reject invalid families or an extra eligible candidate without falling back to the legacy String winner. Marks cannot
   bypass unsupported roles, missing projections or compiler signature collisions; generated converters cannot join.
3. Zero/one-mark groups keep baseline selection: unmarked Integer/String collides; one marked String/CharSequence wins
   and rejects numeric input; a lone integer mark gains no priority over String; unique integer routes remain callable.
   Integer+Long, integer+decimal, int+Integer, String+CharSequence, duplicate Strings/converters and larger marked groups
   fail the family rule. Preserve ordinary collision omission/diagnostics versus strict naming/multiple-mark errors.
4. For a validated family, raw `input instanceof String` selects String unchanged. Otherwise only the current numeric
   allowlist (Byte, Short, Integer, Long, Float, Double, BigInteger, BigDecimal) can select the integer arm. Reuse current
   finite/exact int32 conversion, independent of parser-chosen wrapper. Integral `BigDecimal('8080.0')` succeeds;
   fractions, overflow, NaN/infinities and arbitrary Number subclasses fail with no String fallback. Preserve current
   Float/Double semantics; do not claim recovery of precision already lost before Struct receives the value.
5. Null, Boolean, Map, List, GString, mutable CharSequence and arbitrary objects reject for this family. Select once;
   invoke the recorded signature, never try each arm, run conversion bodies during inspection, or redispatch using the
   converted argument class. An operation-body exception propagates with its original cause, without trying the other arm.
6. Preserve FromStruct, active Builder application, Template construction/application, key positions, ownership, lifecycle,
   same-thread/sealing and cycle fences. Earlier ordered effects persist when a later family input fails; later operations
   do not run. No preflight, rollback, parser change or automatic JSON Schema validation is added.

Inherited/overridden Integer arms need the same source-identity discipline as String arms. An override owns its current
marker, name, exclusions, prose and constraint facts. Never retain parent facts for different descendant code. Naming
profiles may join or separate effective members; do not make provisional source-name pruning the final authority.
Include compiler `StructuredInputAdmissionPlan.overriddenInputCandidate`, whose scalar override check is currently
String/CharSequence-only, as well as runtime `overriddenOperation` when preserving integer-arm override identity.

## Metadata and compatibility proposal

### Existing selection facts are sufficient

The public Builder already contains both exact JVM descriptors. Each authored projection has a required
`GeneratedInput.Operation` role and explicit `authoritative` Boolean, checked against the actual `@Struct.Operation`;
its declaring `GeneratedInput.Schema.operationCount` counts all operation facts, including exclusions. Each arm's names,
examples and source-deprecation facts already exist; AnnoDocimal supplies exact declaration Javadoc. A generated field
converter has different field/selection/provenance authority. These facts distinguish the opted-in pair without adding
an alternative-group annotation, group count, numeric discriminator, capability bit or schema-specific registry.

### Authored parameter transport, completeness and projection are distinct

Operation enrichment has no parameter constraint array, count or policy. A moved method, destination field or converter
reader cannot establish original authored parameter facts. Keep three responsibilities separate:

- **Generic capture/transport/reading:** extend ordinary `GeneratedInput.Operation` enrichment and its existing reader
  with compiler-frozen parameter facts, reusing `GeneratedInput.Constraint` rows and `StructuredInputConstraintMetadata`.
  Retain exact authored declaration/parameter identity and policy, conservative eligibility, literal descriptions and
  supported composition. The current one-payload seam uses parameter zero; the infrastructure must not depend on a union
  flag, arm kind or family membership. Do not build an unused multi-parameter framework or copy executable Bean constraints.
- **Family completeness:** use absent/unknown defaults (such as count `-1`) for historical facts. Newly admitted families
  require explicit count, even zero, matching rows and policy on both arms. Missing/duplicate/malformed/inconsistent facts
  reject before admission even under OMIT or absent input. Existing single-route artifacts retain their interpretation;
  reusable transport does not impose new completeness requirements or assertions there.
- **Qualified JSON Schema projection:** only the newly qualified family parameter sites gain projection in #1129. Reuse
  the closed catalog and PROJECT/TEXT_ONLY/OMIT handling; keep unsupported forms documentary and branch-local. Generic
  transport does not authorize general parameter projection, new constraint families or #971 enforcement. No field or
  converter facts transfer to an authored parameter.

**Keep unified protocol version 1.** Selection needs no new serialized fact; additive parameter facts have unknown defaults,
with completeness newly required only for families. Existing single-route facts keep their meaning. Preserve strict version
checks without an annotation-specific version, group carrier, capability registry or legacy decoder. U1/U2 missing/default
and byte-mutation checks plus U3 source-free consumers must establish compatibility. Only contradictory implementation
evidence justifies returning for a concrete revision; no speculative bump or weaker checks are authorized.

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
The matrix below describes guarantees, not individual mandatory tests or a Cartesian product. Reuse shared neutral
fixtures, existing regression coverage and artifact-consumer infrastructure. Run source-free, binary or cross-lane checks
before U3 only for a demonstrated risk; record that reason. U2 owns affected-module Groovy 3/4/5 qualification, U3 the
independent artifact/JPMS/full-build gate. Repeat checks only when changed inputs invalidate earlier evidence.

### U1 — Minimal vertical behavior

One cohesive feature/test commit retains two marked AST candidates, uses the generic authored-parameter transport to emit
explicit empty facts, resolves one validated family through the existing resolver, selects the exact numeric/String arm
and renders both branches. Keep unsupported/nonempty parameter-fact cases fail-closed until U2; do not mislabel them empty.

Use focused Groovy 3 compiler/runtime/plugin tests at `StructuredInputAdmissionPlanTest`, `CompiledBuilderSchemaTest`,
`StructuredScalarAlternativeTest` and the existing schema-generation seam. Demonstrate integer and String identity,
exactly one signature/call, int/Integer declarations, basic unmarked/one-mark and absent/empty-fact controls. Validate
actual emitted schema with an independent Draft 2020-12 validator, not a hand-authored union. No complete independent
consumer, multi-Groovy or JPMS matrix is mandated here. Keep U1 local through qualification; if a slice is independently
delivered, supply its capability documentation at that boundary instead of repeating it in later slices.

### U2 — Arm facts, effective hierarchy and rejection boundaries

One feature/test commit completes generic original-parameter capture/reading, per-family completeness and explicitly
qualified branch projection. Reuse the neutral four-overload fixture for ignored-helper acceptance and eligible-extra
rejection. Cover arm-local prose/constraints/examples/deprecation/policies, naming, integer/String overrides, inheritance,
unsupported families, malformed facts, exact conversion and ordered failures. No separate resolver or general parameter
projection follows. A second green regression commit is useful only if it isolates a distinct reasoning step.

Extend `StructuredOperationSelectionTest`, `InputNamingProfileTest`, `CompiledBuilderSchemaReaderTest`,
`StructuredInputRuntimeTest` and existing projection/Template seams where their coverage is relevant; avoid duplicate
scenarios. Qualify affected modules on Groovy 3/4/5, including source/default/count corruption and no-execution controls.
Validate changed emitted branch assertions against the shared neutral corpus. Independent JAR/historical/JPMS coverage
belongs in U3 unless a concrete U2 risk needs an earlier targeted case. Evolve the same readable Destination example
into documentary coverage; do not maintain a second example or repeat migration/evidence edits for each slice.

### U3 — Artifact qualification and delivery documentation

One acceptance/test commit reuses `RootRecipeConsumerTest` / `GeneratedSchemaProvenanceConsumerTest` and plugin TestKit
infrastructure. Retain parent/child Schema JARs, matched libraries and an actual pre-change single-route JAR. Qualify
independent Java/static/dynamic Groovy public-API clients across Groovy 3/4/5 classpaths and applicable Groovy 4/5 JPMS,
without compiler/plugin at application runtime or loose producer source/classes. Include historical single-route and
missing-parent/incomplete-family controls. Generate schemas from the same JAR contracts and validate actual output using
the shared Draft 2020-12 corpus (`klum-ast-gradle-plugin/build/schema-validation` is the existing output seam). Record exact
artifacts/hashes, commands and results once; no core runtime validator dependency or consumer fixture is introduced.

Run root `./gradlew check`, applicable plugin/TestKit/binary/JPMS checks and any final changes that invalidate U2's lane
evidence. Complete history and two-axis review; inspect hosted CI/Sonar on the final head separately from local evidence.

One delivery-doc commit synchronizes `Struct.Operation` Javadoc, `docs/user/Structured-Input.md`, `docs/user/Json-Schema.md`,
applicable migration guidance/navigation, the next unreleased `CHANGES.md` section and implementation status. Use the one
`StructuredScalarAlternativeDocumentaryTest` example with `@Issue`, `@Tag('documentary')` and `@See`, with reciprocal
user/test/issue links under the later assignment's authority. State the frontend numeric distinction and consumer-owned
adoption gate; do not claim ScHelm qualification or select a release. Current planning delivery changes none of these files.

## Requirement-to-acceptance matrix

| Confirmed requirement / retained boundary | Slice and executable acceptance |
| --- | --- |
| Real consumer pair needs no new Struct adapter/dispatch | U2 neutral four-overload guarantee; U3 handoff to Schelm-owned FromStruct/applyStruct adoption under real field names, preserving wire type and existing holder/presence machinery |
| Both authored arms explicitly marked; Builder/Struct admission separate | U1 compiler + runtime + emitted schema: both marks/projections required; marker alone does not create a Builder method; annotations stay distinct |
| One Integer or int plus String; no owned IntOrString/ADT | U1 neutral Destination fixture for both numeric declarations; U2 unsupported Boolean/Map/List families and arbitrary Object payload reject |
| Unmarked collisions and old one-mark behavior | U1 unmarked pair absent/rejected, String-only mark accepts `'8080'` but rejects 8080; U2 lone numeric mark and existing String/CharSequence selected controls; U3 retained old JAR |
| Authored only; generated converter admission unchanged | U2 ordinary generated Integer/String converter controls retain current single String route or existing collision outcome; neither joins a family; U3 source-free equivalent |
| Exact numeric conversion, actual Strings stay Strings | U1 values/types/call counts; U2 standard numeric inputs, integral/scaled BigDecimal, int32 endpoints, fractions/overflow/nonfinite/custom Number failures; no retry; emitted-schema corpus validates corresponding JSON numbers/Strings |
| No overlapping or larger family; ignored helpers harmless | U2 marked Integer/Long, int/Integer, integer/decimal, String/CharSequence, duplicate Strings/converters and eligible-extra rejection; ignored Number/opaque helpers do not count, execute or form schema branches |
| Shared effective candidate set | U1 resolved two-arm result consumed by both; U2 names/profiles/exclusion/override/inheritance matrix with runtime calls and actual schemas from the same fixtures; U3 same JAR inputs |
| Generic authored facts, family completeness, qualified branch projection | U1 generic empty/unknown transport; U2 original parameter provenance, branch Javadoc/constraints/examples/deprecation/policies, unsupported/grouped forms and no wrapper/sibling/field leaks; unrelated single-route projection unchanged |
| Null/Boolean/Map/List excluded; strict Strings | U2 null, Boolean, mappings/sequences, GString, mutable CharSequence and arbitrary object cases; generated schema rejects JSON null/Boolean/object/array |
| Exact invocation and no Schema execution during inspection | U1/U2 selected-signature/call counters, primitive/boxed and thrown-cause/no-retry guarantees; U2 inert-inspection traps with positive controls, U3 source-free qualification |
| Missing, old or malformed contracts fail closed | U1/U2 ASM mutations: missing Operation/marker/count/descriptor/constraint rows/policy/protocol; U3 missing parent, resource shadow/mismatch, recompiled child with incomplete parent; both consumers reject before family use, even OMIT/absent payload |
| Single-route and construction behavior retained | U2 order/partial effect, unknown names, keys, active-thread/unsealed and post-failure cleanup, Template definition/replay controls; U3 binary single-route clients and existing suites |
| Actual generated-schema validity and truthful scope | U1/U2/U3 Draft 2020-12 validator on emitted artifacts, int32 bounds, branch ownership, deterministic order and atomic retained outputs; schema-valid body failure and schema-only constraint distinction |
| Groovy/JAR qualification and docs | U1 focused G3, U2 affected-module G3/G4/G5, U3 independent Java/static/dynamic JAR clients, historical binaries, applicable JPMS/full build and consolidated documentary/user/migration/release-note links |
| #1113 output and downstream work separate | U3 delivery brief states no reconstruction/adoption claim; no ScHelm/Kubernetes dependency, source edit or test in any slice |

## Readiness, risks and delivery boundary

Language decisions are settled; the consumer refinement leaves no new maintainer decision. Proposed internal representation
and additive constraint transport need technical review. The main risks are premature candidate pruning, inherited integer override attribution,
confusing family selection with existing multi-method relationships, widening catalog sites for unrelated operations,
and accepting historical pair metadata as complete. U1/U2 prove those boundaries before final artifact qualification.
No unresolved product choice is hidden as an implementation prerequisite; any contradictory source/qualification result
returns for a concrete design revision. No speculative metadata bump or future family machinery is authorized.

Planning validation: `git diff --check`, Markdown structure and relative file/heading links, issue-decision/source-seam
consistency and independent two-axis review. These architecture-only Markdown files are outside current user-site
rendering inputs; Groovy lanes and runtime/schema validation are deliberately not run for this documentation delivery.
That planning-only validation established no executable result. U1/U2/U3 implementation evidence above now owns the
actual results; this section retains the original planning delivery boundary.

Tracker impact: **related issue #1129; curation impact none**. Use `Related: #1129` only. The U3 assignment requests no
issue-state change. GitHub reports #1129 **closed** on 2026-11-28, contrary to the assignment's open-issue expectation; this worker leaves that state unchanged and requests Hive reconciliation. No release
gate, dependency, owner, issue state/label/milestone or published release fact is changed by U3. The separately assigned
U3 draft PR follows completed local review/checks; merge is separate.
Report exact branch/base/head, validation, review and hosted-check state in the handoff. Request Hive reconciliation;
do not self-archive while the PR or its reconciliation remains open.
