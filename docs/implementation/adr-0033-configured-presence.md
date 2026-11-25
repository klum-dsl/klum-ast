# ADR 0033 — configured-presence implementation plan

Date: 2026-11-25. Design only; no production implementation or release placement authorized.

Decision: [ADR 0033](../adr/0033-opt-in-configured-presence.md).
Tracker: [#1111](https://github.com/klum-dsl/klum-ast/issues/1111), which remains open and untargeted by this work.
The [accepted boundary](https://github.com/klum-dsl/klum-ast/issues/1111#issuecomment-7169948681) permits authoritative
configured presence only under the opt-in supported mutation contract, complete producer hierarchy and declaring-field
coverage. It excludes arbitrary reflection/metaprogramming/raw alias/view tracking and any promise to detect every bypass.

This is an executable work plan: each slice specifies its prerequisites, code seams, commit reasoning, fixtures and
pass/fail acceptance. Test names below are proposed new specifications, not existing or passing tests. No prototype
implementation is promoted, no tests are added by this design PR, and no implementation task is implicitly authorized.

## Retained local evidence

These commits are local-only disposable evidence, **not production ancestors or public GitHub links**. Inspect reports
with `git show <commit>:<path>` in the retained repository. Do not merge, rebase, publish or copy their experimental
production changes. This design was branched independently from clean `origin/master` at
`d9bc6b5d27f8e7226ea38bbd12e6ca076f4b0e0e`.

| Proof | Exact local commit | Report path at that commit | Established limit |
| --- | --- | --- | --- |
| Feasibility | `3aad7474825bca1b38f36bd277cf26365835365e` | `docs/implementation/evidence/issue-1111-presence-feasibility.md` | Companion storage, selected replacement, Template and same-runtime serialization work; setter hooks miss authored writes/empties and confuse implicit work. |
| Mutation authority | `974e0403b88c334197a99f327a844bbe24ebaa9c` | `docs/implementation/evidence/issue-1111-mutation-authority-prototype.md` | Tested post-success AST writes, explicit operation entry and origin scopes work; raw/shared/stale aliases defeat transparent wrappers. |
| Capability/coverage | `3f46fc58f01762afec5b012999511f4a17409e08` | `docs/implementation/evidence/issue-1111-capability-coverage-prototype.md` | Positive declaring-field and whole-producer-chain checks preserve availability/U; external reflection still bypasses detection. |

For example:

```shell
git show 3f46fc58f01762afec5b012999511f4a17409e08:docs/implementation/evidence/issue-1111-capability-coverage-prototype.md
```

Evidence scope: the first report records 22 tracer cases per lane; the second 38 per lane; the third 63 per lane,
189 total, including earlier/extended cases and deliberate negative controls. Counts are not additive independent
qualification. Java 17 classpath, separate Schema/consumer compilation, Groovy 3/4/5, JAR-only inheritance and same-runtime
streams were exercised in bounded fixtures. Full CI/Sonar, JPMS, historical binaries/streams, mixed Groovy producer
generations and actual Schelm acceptance were not established. Negative reflection/alias tests passed by reproducing
false facts, not by detecting or supporting those writes. NULL-container snapshots remained unavailable because the
existing payload path skipped null donors. Lifecycle authorship and non-snapshot policy semantics remained unresolved.

## Current implementation and failure paths

Current master has no native public configured-presence contract. The relevant inspected seams are:

| Module / seam | Current behavior and required change boundary |
| --- | --- |
| `klum-ast-annotations`: `DSL`, `Builder`, `FieldType`, `OverwriteStrategy` | Schema vocabulary and existing strategy enums; opt-in/mutation spelling is undecided. Do not add annotation members until G0. |
| `klum-ast`: `DSLASTTransformation`, `WriteAccessMethodsMover`, `BuilderMethodProjection` | Moves Schema mutators/lifecycle methods and retargets fields to Builder storage. No authoritative post-write configured-state instrumentation. Generated coverage must follow actual transformed paths, overrides and projections. |
| `klum-ast`: `AlternativesClassBuilder`, collection/Map/converter generation | Generated bulk methods loop over members; empty operations may do no store. CollectionFactory scope entry is separate. Derive coverage for actual emitted overloads, not broad field categories. |
| Runtime `InternalKlumBuilder` | `setInstanceAttribute` normalizes then stores; member operations mutate construction storage; `applyConfiguration` runs PostApply. Initializers/default/lifecycle reuse mutation paths, so setter invocation alone cannot decide authorship. |
| Runtime `InternalKlumBuilder` materialization / Model companion | Graph completion makes read-only container snapshots; `ModelState`/`KlumModelProxy` copy internal metadata without retaining Builders. Presence needs immutable retained validity/state, not an exposed metadata Map. |
| Runtime `KlumTemplateProxy`, `TemplateRecipeState` | Template companion separately captures immutable deferred closures and replays clones. It has no ordinary Model metadata snapshot. Add independent presence and action-origin retention without changing Template identity or one-lifecycle replay. |
| Runtime `CopyHandler` | Selects payload directly by target overwrite policy; some collection/Map paths mutate storage without normal member hooks. Both return early for null donors before strategy dispatch. Marker-only transfer would lie for NULL containers or policies retaining receiver payload. |
| Runtime `DefaultPhase`, owner-provided defaults, AutoCreate, lifecycle participants | Existing value/emptiness predicates drive default eligibility. Proofs show boxed zero and empty String/containers can be replaced while false survives. Origin tracking alone does not preserve authored payload. |
| Runtime `StructuredInputSupport`, `CompiledBuilderSchema` | Ordered admitted operations populate existing Builders, sometimes through internal owned-child paths rather than public creator overloads. Empty population requires an entry fact; explicit null currently rejects. Keep admission/ordering/partial failure unchanged. |
| Runtime `KlumObjectSupport`, `KlumBuilderSupport`, generated ABI | Completed support is public; companions/internal metadata are not. Add the selected small read-only contract here or an explicitly chosen alternative. All emitted links use `runtime.generated`, not `runtime.internal`. |
| Jackson / Gradle plugin | Independent import and compiled-contract/mirror consumers. Audit touched paths; advertise only qualified import coverage. Presence adds no managed wire metadata, parser, JSON Schema requiredness or plugin DSL by default. |

Relevant existing regression seams include `CopyHandlerRuntimeTest`, `DefaultValuesSpec`, `TemplateCompanionSpec`,
`StructuredInputRuntimeTest`, Builder projection tests and generated-schema/JPMS artifact fixtures. Use existing shared
Spock fixtures and `klum-ast/src/test/scenarios/` conventions rather than independent ad hoc compilers where possible.
The architecture map is a wayfinding index; actual source and the parent ADRs own behavior.

## Semantic gates and decisions

Only the supported-mutation boundary is confirmed. The following decisions must be recorded in #1111/ADR before the
affected slice starts. Options are recommendations for review, not defaults silently accepted by this document.

| Gate | Required maintainer decision | Blocks |
| --- | --- | --- |
| G0 — public minimum | Opt-in placement/inheritance/field selection and spelling; completed reader result/facade and logical field identity; collision and unsupported-field policy. Recommend reason-bearing Known-or-Unavailable on `KlumObjectSupport`, name lookup and no enumeration. Select whether Builder/Template readers are included or deferred. | CP-1 onward |
| G1 — authored/effective semantics | Initializers/defaults/AutoCreate, lifecycle/participants/custom phases, callbacks overwriting configured values and deferred scheduling classification. Select explicit NULL/zero/empty authority and default eligibility, reset timing and any configured-payload requirement. Recommend explicit values suppress implicit defaults on opted-in fields, but this is unaccepted. | CP-2 onward |
| G2 — explicit operations | Reset/null/empty API names and admission; whole-container replacement versus existing bulk population; nullability versus required presence/element nullability; reset storage/ownership cleanup and reconfigure after NULL/reset. | CP-3; null/reset portions of CP-2/4 |
| G3 — transfer policy | NULL-container ALWAYS_REPLACE behavior; full state/payload truth tables for all other existing strategies, including unavailable donor/receiver, nested merge and Map/data donors. Select support versus explicit unavailable/rejection for each unqualified combination. | CP-4; transfer portions of CP-5 |
| G4 — compatibility promise | Historical producer/runtime support set and new generated ABI; Model/Template UID/stream format and old-stream acceptance/rejection; mixed-version transfer interpretation. Existing directional ABI promises still constrain this decision. | CP-1 ABI inventory; CP-5 retention format; CP-6 historical qualification |
| G5 — delivery | Release target, final scope/deferrals and evidence owner for Schelm acceptance. No milestone is inferred here. | CP-7 release claim only |

Before the **first implementation slice**, resolve G0, select the minimum G1 meaning of a known state (so CP-1 cannot
publish misleading state), and G4's additive-linkage/retained-format approach. Also explicitly authorize CP-1. G2/G3 may
remain open only if CP-1 withholds their unqualified fields/operations and publishes no reset/null/copy claims. G5 need
not be selected to begin implementation. Remaining G1 details must be settled before CP-2; do not ship unresolved lifecycle
facts as apparently authoritative ABSENT. Recommendations for a coherent minimum can be accepted together, but are not
selected by this plan.

## Slices and commit boundaries

Dependency graph: CP-1 → CP-2 → CP-3 → CP-4 → CP-5 → CP-6 → CP-7. CP-6's binary/JPMS fixtures and CP-7's consumer
fixture preparation may begin alongside CP-2 once G0/G4 are stable, but their acceptance depends on the final earlier
slices. CP-3 depends on G2, CP-4 on G3, and CP-5 on G4. No slice is independently a complete #1111 delivery.

### CP-1 — truthful minimal completed inspection and producer coverage

Prerequisites: first-slice decisions above. Implement one thin vertical path: opted-in scalar declaration, compiler
capability, fresh Builder facts, ordinary scalar operation, completion, selected public read-only inspector and Java
consumer of a separate Schema JAR. Every not-yet-qualified mutation/transfer path stays unavailable or rejects; no broad
capability flag for the whole field family. Unknown fields and non-opted-in Models return unavailable.

Use a private immutable coverage/state module with resolved declaring-field keys and recognized format/contract facts.
Validate actual whole producer hierarchy separately from object-retained field coverage. Derive operation manifests
from concrete generation paths and instrumentation results. Missing one producer layer invalidates all field claims;
missing one field token inside an otherwise qualified hierarchy withholds that field. Never repair unknown imported
state merely because a setter runs or a runtime allocates metadata.

Commits: (1) internal coverage/availability rules with focused runtime tests; (2) minimal compiler→completion→public-reader
scalar path with Schema-JAR Java/static/dynamic Groovy tests and untracked regression. Do not split the public operation
from its working producer or commit a red test alone. A small API inventory/design amendment may precede these commits.

Acceptance — proposed `ConfiguredPresenceCoverageTest` and `ConfiguredPresenceConsumerTest`:

- Known untouched scalar versus explicit admitted null/value without reading getters to infer state; reads are neutral.
- Tracked base/leaf, abstract base, JAR-only base/downstream leaf and both mixed tracked/untracked directions. Exercise
  base helper and leaf inherited writes. Missing/old/future/mismatched layer or superclass identity makes all unavailable.
- Remove a declared-field token, operation fact or retained producer snapshot: affected state stays unavailable through
  completion/inspection. Include unknown state values, ambiguous/shadowed fields, Builder-only/special fields, generics
  and non-Schema ancestry; either explicitly qualify or verify documented unavailable/rejection.
- Capability is distinct from one object's available state. Corrupted/partial retained facts and old-format controls
  cannot be upgraded by reads or successful setters. Producer version/provenance alone never proves capability.
- Separate-JAR Java and static/dynamic Groovy readers use only public API, no private marker names or Schema source.

Risk gate: review the generated/public ABI inventory and fail-closed table before expanding mutation coverage. Bytecode
damage fixtures are negative controls, not substitutes for historical compiler binaries in CP-6.

### CP-2 — compiler-covered writes, explicit operation entry and origin

Prerequisites: G1 and the applicable G2 choices. Extend exact generated scalar/owned-child/member/bulk/CollectionFactory
paths and compiler-qualified direct writes in `@Builder.Method`, moved helpers, nested closures and projections. Place
post-success hooks after actual normalization/store/attachment; preserve assignment expression results and evaluation
count. Empty successful operations and existing-child re-entry need facts even without a member store. Keep generation
and coverage registration adjacent so omission cannot accidentally grant ABSENT.

Origin scopes must distinguish authored work from initialization/default/automatic/lifecycle work under the selected
policy, including nested Builder initializer calls under an outer authored scope. Restore scope on exceptions and nested
operations; scheduling origin, not the eventual numeric phase, follows deferred actions. No new origin log API.

Commits: (1) supported moved-write instrumentation and conservative unsupported-form diagnostics/coverage; (2) generated
empty/member/owned operation entry facts with origin scopes and lifecycle/default integration. Split phase integration
further only when each commit remains green and advertises exactly its implemented subset.

Acceptance — proposed `ConfiguredPresenceMutationTest` and `ConfiguredPresenceOriginTest`:

- Same-value null assignment, qualified/unqualified/property/direct-attribute forms, compound assignment/result, nested
  closure/helper, inherited fields, static compilation, local shadow exclusion; test prefix/postfix, super and unknown
  receiver/index forms as supported or conservative unavailable/rejection, never presumed covered.
- RHS/coercion/attachment failure leaves that failed write neutral; an earlier successful write followed by a caught
  later exception remains recorded. Bulk failure retains coherent earlier effects, with no new transaction promise.
- Empty String, false, zero, explicit empty child and re-entry, empty bulk List/Map and actual owned CollectionFactory
  blocks; overloads, converter-backed members, literal alternatives, custom overrides and projections each advertise
  coverage only after execution tests. Returned expressions, ownership and child lifecycle remain unchanged.
- Origin matrix for initializer, nested initializer helper, Default/owner defaults, AutoCreate/AutoLink, PostCreate,
  PostApply, PostTree, participants and custom phases, including later writes to already configured fields. Assert both
  selected presence and effective payload, not marker alone. Explicit-null/zero/empty default behavior follows G1.
- Deferred actions scheduled from authored and lifecycle code at the same phase retain distinct selected origin;
  exception/nested-root/thread isolation does not contaminate later construction.
- Keep negative external-reflection/raw-alias controls demonstrating the documented unsupported limit. Do not assert
  automatic downgrade after an undetected foreign violation. Detected unknown-target escapes withhold whole coverage.

Risk gate: no public coverage until all advertised AST/generated forms pass separately in Groovy 3/4/5. A narrow hazard
pattern list is not exhaustive escape analysis. Document unsupported source forms precisely.

### CP-3 — explicit reset/null/empty operations and Struct integration

Prerequisites: G2, CP-2. Add only the selected APIs, with schema-name collision checks and public generated typing/mirrors
if generated methods are chosen. Reset removes stale logical payload, detaches ownership as required, chooses safe
declaration-based storage and permits subsequent configuration. NULL container state must not be confused with null
members. Distinguish whole-value replacement/empty from existing population semantics; preserve non-opted-in behavior.

Instrument Struct's admitted execution seams after shape checks, including scalar population, owned sequences, keyed
Maps and literal aliases. Custom Struct commands acquire presence only through their qualified internal writes.
No implicit change to general null admission, JSON Schema requiredness, or Struct grammar for reset/null commands.

Commits: (1) explicit operations with collision, normalization, ownership and reset/reconfigure tests; (2) Struct empty
execution/origin entry and documentary example. Add grammar only if separately accepted; otherwise retain null rejection.

Acceptance — proposed `ConfiguredPresenceOperationsTest`, `ConfiguredPresenceStructTest` and documentary test:

- Scalar, owned object, List and Map ABSENT/NULL/VALUE sequences; reset populated/null fields, then assignment/block/member
  addition; mutate detached old raw alias and verify new storage is independent. Do not treat detached mutation as supported.
- Empty object/List/Map versus untouched; nullable scalar/object/container versus nullable Map entry/element; required
  presence remains independent of nullability, false, zero and effective defaults. Failed reset/null/empty operations
  leave coherent state/payload according to the selected failure contract.
- Struct missing field is neutral; explicit empty child/container is configured. Empty `applyStruct` population over a
  populated receiver retains existing contents; no invented replacement/clear behavior. Existing null rejection retains
  field/source diagnostics. Ordered failure retains earlier effects and one ordinary lifecycle.
- Generated operation names, external Struct names and original declaration identity remain separate. Unknown fields
  report unavailable through inspection and retain existing Struct errors through construction.

Risk gate: demonstrate public Groovy Schema/Model syntax and Java reader before freezing names. Add `@Issue("1111")`,
`@Tag("documentary")`, `@See` and matching planned user guidance when the feature becomes executable.

### CP-4 — logical-field copy transfer and strategy qualification

Prerequisites: G3, CP-3. Pair state/availability and payload selection at each `CopyHandler` logical-field decision,
including direct container changes, nested recipe copies and unavailable donor facts. Transfer into the receiver's own
capability; do not copy a donor descriptor as proof of receiver coverage. `ALWAYS_REPLACE`'s tested availability behavior
is the starting evidence, not authority to invent other policies or treat NULL container replacement as solved.

Commits: (1) selected snapshot transfer, including the accepted NULL-container decision and unavailable controls;
(2) separately green policy-family integrations after explicit truth-table approval, or documented unavailable/rejection
for unqualified combinations. No copied donor marker after an unrelated retained receiver payload.

Acceptance — proposed `ConfiguredPresenceCopyTest` plus existing copy regressions:

- Complete-snapshot requested cases: donor ABSENT over populated receiver; NULL scalar/child/container; false/zero/empty
  String/object/List/Map. Compare both state and payload; preserve unavailable donor/partial coverage. A NULL-container
  path that leaves populated receiver storage cannot claim known NULL. State whether snapshot support is complete yet.
- G3 must supply expected results for each donor A/N/V/U × receiver A/N/V/U combination, relevant null/empty/nonempty
  effective payloads and selected strategy. Cover Single REPLACE/ALWAYS_REPLACE/SET_IF_NULL/MERGE, Collection
  ADD/REPLACE/SET_IF_EMPTY/ALWAYS_REPLACE and Map FULL_REPLACE/SET_IF_EMPTY/ALWAYS_REPLACE/MERGE_KEYS/MERGE_VALUES/
  ADD_MISSING, plus inherited policy resolution. Distinguish complete replacement from configured-values overlay.
- Map/data donors and nontracked/old Models have no retained native configured history; select explicit import semantics
  or unavailable, never infer history from getters or missing map keys in a generic object-copy path.
- Copy then reset/append/upsert/owned-child reconfigure leaves completed donor untouched. Preserve cycles, aliases,
  LINK/OPTIONAL_LINK identity and one ordinary recipient lifecycle; reject invalid live Builder sources as today.
- Reproduce nested immutable JSON-like container limitation and record whether scalar envelopes still remain necessary.
  This plan leaves that limitation separately scoped; no generic deep-copy fix or envelope-removal claim.

Risk gate: per-field coherence on failure, nested partial copy and null receiver containers need explicit assertions.
No graph transaction or ordered-composition engine (#304) is introduced. Any deferred policy leaves that portion of
#1111 acceptance incomplete even if fail-closed behavior is correct.

### CP-5 — Template replay and serialization retention

Prerequisites: G4 and CP-4 transfer behavior. Independently snapshot presence into the Template companion/recipe and
retain origin with each deferred action. Preserve definition's omitted lifecycle, fresh recipient Builders, scheduling
order, clone isolation and no relationship Template values. Ordinary Models retain no deferred actions or Builders.

Commits: (1) Template capture/replay with origin-bearing immutable actions; (2) selected Model/Template stream format,
UID/compatibility handling and retained-state validation with fixture streams. If historical streams are unsupported,
document/test that chosen boundary rather than inventing a migration reader.

Acceptance — proposed `ConfiguredPresenceTemplateTest` and `ConfiguredPresenceSerializationTest`:

- Template definition/capture/Java serialize/reload/replay preserves known A/N/V and field-local/whole-object unavailable,
  including partial/mismatched coverage. Recipient capability remains independent; new Builder allocation cannot recover
  donor history. Recipe snapshot is defensive and unchanged by recipient mutations.
- Authored and implicit/deferred actions retain G1 meaning across capture/replay, including same-phase actions and nested
  Templates. Default/reset eligibility runs only at the ordinary recipient lifecycle step; inspection runs no actions.
- Same-version ordinary Model and Template graphs preserve state and unavailability, composition cycles and LINK identity.
  Sealed LINK wrappers query the completed target's original retained state. Assert no Builder/session in serialized graph.
- Missing/unknown/corrupt retained formats fail closed if the stream can be read; incompatible streams may reject under
  G4. Do not claim all historical streams deserialize as unavailable. Historical producer/runtime tests belong to CP-6.
- Jackson Template rejection and ordinary POJO export remain unchanged; no presence wire metadata or round trip.

Risk gate: private-field changes may change default serial UIDs even without a public signature change. Review before
committing retained format; do not infer stream compatibility from the bounded proof's successful same-runtime round trip.

### CP-6 — artifact, binary, Java and module qualification

Prerequisites: G4 and final CP-1–CP-5 interfaces. Extend existing artifact scenarios and named-schema fixtures, not a
parallel publication mechanism. Compile Schema and consumers separately in every supported lane against one production
artifact set; no cross-lane Groovy test output. Inventory generated descriptors/calls and reject runtime-internal leakage.

Commits: (1) shared separate-artifact/producer hierarchy and historical ABI fixtures with expected support table;
(2) Java 17/21 and Groovy 4/5 JPMS qualification, Gradle/plugin/mirror regressions where touched.

Required qualification matrix:

| Axis | Positive acceptance | Negative/limit control |
| --- | --- | --- |
| Java | Compile/run public inspector against Schema JAR on Java 17; repeat consumer use on Java 21. | No private-field/marker access or source-Schema dependency. |
| Groovy | Independently compile/run dynamic and `@CompileStatic` consumers and Schema fixtures in `test`, `groovy4Tests`, `groovy5Tests`. | No reused baseline Groovy fixture/test outputs; verify lane isolation. |
| Hierarchy/artifacts | Tracked base JAR + downstream tracked leaf, inheritance writes, abstract/generic qualified shapes. | Both mixed tracked/untracked directions, missing/partial producer layer, unsupported special/shadowed forms. |
| Historical binary | Retain actual supported older Schema/consumer binaries on newer runtime according to G4/ADR 0015. Freeze new consumer binaries for future directional tests. | New generated code on older runtime may lack helpers: verify explicit supported/rejected combinations; no promise that an old runtime implements new inspection. Damaged metadata alone is not historical evidence. |
| Mixed producer generations | Execute each combination that G4 promises across JAR boundaries and supported Groovy runtimes. | Explicitly list unqualified combinations and unavailable/rejection; compiler version equality is not capability. |
| JPMS | Groovy 4/5 named Schema + Java/static Groovy consumer, ordinary schema exports/opens and generated-only bridges. | No `--add-exports`, `--add-opens`, `--add-reads` or patched modules; Groovy 3 remains classpath-only. |
| Serialization | G4-selected same/new/old stream matrix for Model and Template, including unavailable facts. | No historical-stream promise beyond recorded fixtures; incompatible streams reject per selected policy. |
| Other input routes | Qualify generated Factory/map/script, Struct and Template routes; audit Jackson binding, custom factories/converters and imports. | Routes not fully qualified cannot advertise complete presence coverage; do not silently broaden Jackson/Struct grammar. |

Risk gate: binaries must exercise the actual exported facade, generated linkage and materialization under real loaders/
modules. Full local root `check` and appropriate artifact/TestKit tasks are required for these cross-module changes.
Record exact commands, Java/Groovy versions, case counts, ignored reasons and final revision. Do not label CP-6 complete
while promised cells remain untested.

### CP-7 — consumer acceptance, documentation and delivery reconciliation

Prerequisites: CP-6; G5 only for placement/release claims. First demonstrate a small source-neutral serializer fixture
using the public reader, ordinary fields and generated Builders without consumer slots/state setters/list-holder wrappers
solely for presence. Then arrange consumer-owned Schelm qualification against a pinned KlumAST artifact/revision.
No Schelm/Kubernetes serialization code enters KlumAST. External acceptance need not modify Schelm in this design task.

Commits: (1) documentary native consumer/authoring examples and supported-mutation limitations; (2) user/release/migration
synchronization and exact acceptance evidence. Reuse the fixture as documentation's executable source.

Acceptance — proposed `ConfiguredPresenceDocumentaryTest` and consumer-owned report:

- Port the issue's false/zero/empty String/nullable scalar/object/container, empty block, reset/reconfigure, Map
  replacement/upsert and List append cases; known versus unavailable is explicit in the serializer. Raw getter values
  never decide omission. Required-presence checks and container-member nullability remain distinct.
- Normal generated owned List/Map construction needs no Schelm `...Items` wrapper solely for presence; uses one ordinary
  lifecycle. Separately record continued nested immutable value-tree copy limits/scalar-envelope requirement.
- Schelm wire adapter produces equal parsed Deployment, KafkaTopic and nullable-fixture documents and equal original-schema
  validation outcomes against a pinned consumer revision. Record artifact, Java/Groovy versions and commands. Native
  internals cannot substitute for consumer evidence. This is a gate to full #1111 acceptance, not core Kubernetes logic.
- Add focused `docs/user/` guidance for opt-in, supported mutators/custom methods/direct writes, unavailable handling,
  defaults/reset/copy/Template rules and compatibility limits. Update affected field/Builder/Struct/Template/copy pages,
  `Migration.md` and `_Sidebar.md` when needed, plus `CHANGES.md` in the chosen release section only when behavior ships.
- All new tests use `Test` suffix and `@Issue("1111")` (or a separately assigned slice issue). Documentary coverage uses
  `ConfiguredPresenceDocumentaryTest`, `@Tag("documentary")` and `@See` to the matching user example. Issue, test and user
  page cross-reference file/feature method; suppressed/pending cases require actionable reasons and cannot mask a gate.
- Inspect final-head CI and SonarCloud; repair findings with follow-up commits after reviewed history. Hive reconciles
  all issue criteria, deferred work and release placement before any closing relationship. Planned/delivered differ.

## Issue acceptance reconciliation

The eight issue checkboxes remain unfulfilled by this design. This maps every requested outcome without presuming open
policies accepted. An unavailable answer is safe but not completion of a requested positive case.

| #1111 acceptance criterion, in issue order | Planned slices and evidence | Remaining decision or external gate |
| --- | --- | --- |
| 1. Separate Schema library; Java/Groovy completed reader without own slots/markers/setters | CP-1 minimal JAR consumer; CP-2–3 native operations; CP-6 complete artifact matrix; CP-7 serializer | G0 public contract and G4 compatibility |
| 2. Port presence cases, empty String/nullable object/container, reset/reconfigure, neutral reads | CP-2 mutation/origin; CP-3 explicit APIs/Struct; CP-4 copy; CP-7 documentary fixture | G1/G2; scalar versus element nullability separate |
| 3. Generated owned relationships/collections, one lifecycle, no presence-only Items wrappers | CP-2 generated coverage; CP-3 blocks; CP-5 Template; CP-7 consumer fixture | No ownership or lifecycle redesign |
| 4. Snapshot A/N/V(empty) over populated receiver; coherent other strategies | CP-4 full state/payload tables and CP-5 transfer retention | G3; NULL-container repair and all non-snapshot policies unresolved |
| 5. Donor immutability including nested value trees; envelope-removal status explicit | CP-4 donor isolation and limitation control; CP-7 consumer report | Immutable nested-container copy remains separate; no removal promised |
| 6. Defaults/initializers/auto-create versus explicit values and reset eligibility | CP-2 G1 matrix; CP-3 reset; CP-5 deferred/replay tests | G1/G2; must assert effective values as well as state |
| 7. Same parsed Schelm documents and original-schema validation | CP-7 pinned external consumer evidence | Consumer-owned execution; no core Kubernetes/YAML logic |
| 8. Untracked behavior/nullability/copy/public compatibility; no silent Struct-null change | All slices include untracked regressions; CP-3 admission; CP-6 ABI/JPMS/streams; CP-7 migration | G4 and any explicitly reviewed opt-in migration |

Additional accepted boundary requirements map to CP-1 complete hierarchy/field coverage; CP-2 detectable unsupported
writes and undetected-bypass limit; CP-4 unavailable donor propagation; CP-5 unavailable Template/stream retention;
CP-6 actual producer/binary/module qualification. No release target is added to this map.

## Risks, validation and handoff

Principal risks are false ABSENT from overbroad compiler coverage, state/payload divergence after implicit writes or
policy copy, origin leaking through initialization/deferred work, declaration identity loss across JAR inheritance,
and accidentally freezing prototype metadata/serialized formats. Gates above require positive and negative evidence
before widening claims. Conservative unavailable/rejection is safe but must not be reported as feature acceptance.
Performance/storage overhead should be measured on representative opted-in and untracked models; retain class-loader
isolation and avoid speculative caches/event histories. No performance target is invented here.

For implementation, start each behavior slice with focused Groovy 3 feedback, then affected module suites and Groovy 4/5
lanes for AST/runtime compatibility. Cross-module changes require root `./gradlew check`, applicable license checks,
binary/JPMS/artifact/TestKit checks and exact-head CI/Sonar review under the repository risk matrix. Use green reasoned
commits on dedicated issue branches; review unpublished history before publication and preserve reviewed revisions.

For **this design-only PR**, run Markdown structure and local link/anchor checks, `git diff --check`, inspect only the
intended documentation diff, and independent Standards and Specification reviews. Groovy lanes are unnecessary because
no executable inputs or behavior change. Hosted CI status must be reported separately from local documentation checks.
No public user pages or changelog entries are changed by this plan. Tracker impact: Related #1111 only; no issue
closure/retargeting, curation/release-gate change or follow-up issue creation.

Handoff must identify ADR/plan paths, design PR, confirmed/open choices, slice dependencies, exact local/hosted check
scope and review outcomes. Ask the Hive to reconcile the design delivery and required G0/G1/G4 first-slice decisions;
the worker must not self-archive or mark `(arch)`. Publication of these design documents does not authorize implementation.

### Design validation record — 2026-11-25

- Independent Standards review at `ce953e30c281f7efb90c5f2879d1cb2dc59a4a00`: zero findings, no actionable heuristic
  smells. ADR conventions, plan content and test/documentary conventions were checked separately from Specification.
- Independent Specification review at the same revision: zero findings. Reviewed against the complete delegated design
  request, #1111 and its comments, and the three retained reports; no missing acceptance mapping or premature decision found.
- Local checks passed for both new Markdown documents: balanced fences, one title each, table-row structure, whitespace,
  and all 15 relative links/anchors. No repository-wide Markdown linter is configured for these files. The applicable
  structure/link checker was run locally; it is not a rendered-site or external-network link qualification.
- `git diff d9bc6b5d27f8e7226ea38bbd12e6ca076f4b0e0e...HEAD --check` passed at the reviewed revision. History/diff
  inspection confirmed one focused design commit from clean master and exactly the ADR/plan files, without proof ancestry
  or production edits. This validation record is a documentation-only follow-up; rerun the same checks at its final tip.
- Groovy/Java/build lanes and local Sonar were intentionally omitted under the documentation-only risk tier. Hosted
  exact-head CI/Sonar status is a separate delivery observation to report in the PR/handoff, not implied by these results.
