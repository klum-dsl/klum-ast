# #867 LP-8: final lifecycle-participant acceptance

Date: 2026-10-10. Base: current origin/master through merged LP-7 PR #880,
`a9d165c46b309a62fc1dfcf2f5c80cd38956a2c5`. Branch: `codex/issue-867-lp8`.
Authority: explicit maintainer final-acceptance delegation; [ADR 0028](../adr/0028-annotation-driven-lifecycle-participants.md)
and its [plan](adr-0028-annotation-driven-lifecycle-participants.md). The Hive preselected **closure candidate #867**,
conditional on all mandatory gates. No manual issue closure, milestone, curation or release-placement mutation.

## Acceptance boundary

The delivered feature is direct-field creation/mutation in four existing Builder phases, plus qualified type mutation.
LP-8 adds acceptance/documentary tests and final guidance; it changes no dispatch, generated contract, validation,
ownership, session, materialization, Template/import or serialization algorithm. The established names/signatures are
retained after Java and Groovy consumer review. Collection/Map field rejection is the accepted release contract,
not missing implementation. Source/binary diagnostics already enforce it.

Optional capabilities are separate from #867's delivered contract: annotation-Closure helper [#878](https://github.com/klum-dsl/klum-ast/issues/878),
container participation [#879](https://github.com/klum-dsl/klum-ast/issues/879), and optional sealed HANDLE
([LP-4 evidence](issue-867-lp4-evidence.md#handle-feasibility-outcome)) are deferred without partial APIs.
Catch-up sub-lifecycles [#874](https://github.com/klum-dsl/klum-ast/issues/874) are a separate post-4.1 investigation;
late children receive the current phase without earlier replay. None is a remaining core #867 gate.
Conditional 4.1 placement remains a release-planning fact owned by the Hive; this acceptance does not assign a milestone
or promise historical source/binary/serialization compatibility.

## Mandatory acceptance map

Test names below resolve in [`klum-ast` tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/), unless another module is named.
The earlier evidence records the exact matrices and limitations; this final record does not expand them.

| ADR criterion / slice | Disposition and evidence |
| --- | --- |
| Prerequisite LP-0 / #868, concrete Model type | Delivered in merged [PR #870](https://github.com/klum-dsl/klum-ast/pull/870); #868 verified CLOSED. [`BuilderModelTypeTest`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderModelTypeTest.groovy), [`BuilderTypeNarrowingTest`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderTypeNarrowingTest.groovy), retained support/factory-token consumers. No extra KlumBuilder operation. |
| D1 direct retained DSL fields, four phase markers; field-only creators | Delivered LP-1/LP-3. `LifecycleParticipantTest`, `LifecycleParticipantPhaseTest`, `LifecycleParticipantTypeTest`; source and replaced-binary rejection. Scalar/static/Builder-only/container fields and other markers remain unsupported. [LP-1](issue-867-lp1-evidence.md), [LP-3](issue-867-lp3-evidence.md). |
| D2 separate capabilities, null behavior, checked assignment | Delivered LP-1/LP-2. Public creator returns Builder/null; mutations return void, run on existing/supplied non-null target, cannot replace through context. Foreign-session/fresh-LINK rejection, null-skip and creator-before-all-mutators controls. [LP-1](issue-867-lp1-evidence.md), [LP-2](issue-867-lp2-evidence.md). |
| D3 direct-field slot, conflicts, order, built-ins | Delivered LP-2/LP-3. `LifecycleParticipantCompositionTest`, `LifecycleParticipantPhaseTest`, compiled Java/Groovy annotation libraries and binary replacement controls. Same-phase competing direct creators rejected; different phases coexist. AutoCreate fields → cluster → methods → Closures; Default precedence and LinkTo unset behavior preserved. No sorting, priorities, second traversal or cross-field order guarantee. |
| D3 parent-before-child, late creation, cluster fallback | Delivered LP-3/LP-4. `FieldPhaseTraversalCharacterizationTest`, `LifecycleParticipantPhaseTest`, `LifecycleParticipantRoutesTest`. Child collection follows the parent visit; current-phase participation, no earlier replay; cluster fills remaining nulls without rerunning direct participants. #874 is separate. |
| D4 truthful context, original field versus visited type, singular lookup, invocation lifetime | Delivered LP-1/LP-5. `LifecycleParticipantTest`, `LifecycleParticipantTypeTest`, separate artifact consumers and `LifecycleParticipantContainerProbeTest`. No Field/list/plural/path/expiry API; incoming traversal is not owning metadata. [LP-5](issue-867-lp5-evidence.md), [Structure](../user/Completed-Object-Support.md#owning-schema-declarations). |
| D5 exact annotation generics, fresh public no-arg handlers | Delivered LP-1/LP-2/LP-4, finally qualified LP-8. Source mismatch/raw/wildcard/unresolved/constructor rejection; replaced-binary defenses; fresh handlers per field/session. `LifecycleParticipantDiagnosticsTest` adds throwing constructors for creator/field/type × four phases, preserving original cause and usable subsequent lifecycle. Separate handler-module construction passes without access flags. |
| D6 FAIL/SKIP, no mutation privilege | Delivered LP-4. `LifecycleParticipantSealedTest`, `LifecycleParticipantRoutesTest`; policy checked per mutator before handler construction; creators keep ordinary checked assignment. HANDLE explicitly deferred, no public constant. |
| D7 optional Closure disposition | Evidence-deferred LP-6; `LifecycleParticipantClosureProbeTest` and [LP-6 evidence](issue-867-lp6-evidence.md). Ordinary map pattern is documentary; no helper, compiler/IDE inference protocol, automatic member execution or generic framework. #878 owns future investigation. |
| D8 runtime package/module direction, public generated typing | Qualified LP-1–LP-8. Six public types plus nested marker containers/policy in existing runtime export; no reverse dependency or exported internal seam. `LifecycleParticipantConsumerTest` executes Java/static/dynamic Groovy against separate libraries; `JpmsPackageBoundaryTest` retains descriptor/generated-linkage checks. Exact final inventory below. |
| D8 configuration defects versus execution failure | Qualified LP-8 against existing implementation. Compile-time declaration errors or runtime `KlumSchemaException`; execution/assignment uses contextual `KlumModelException`, preserving construction/invocation/assertion/linkage causes. Existing source/binary tests plus all-phase constructor tests and LP-5 type-failure tests. Diagnostics guide distinguishes these from collected domain findings. |
| D8 existing validation, levels, suppression, fail threshold, transfer | Qualified LP-8. `LifecycleParticipantValidationDocumentaryTest` uses `KlumSchemaSupport.klumValidationForObject(domain)` / `KlumValidationReporter`; member and memberless findings transfer to completed Domain, not Application. Later INFO/member warnings are suppressed, earlier issues retained; default ERROR permits WARNING, WARNING threshold and ERROR finding fail Verify with `KlumValidationException`. No facade/collector/companion access or sealed-wrapper transfer change. |
| D8 graph/session/Template/import/serialization compatibility | Qualified LP-4 and retained in final check. `LifecycleParticipantRoutesTest`, `LifecycleParticipantSealedTest`; `klum-ast-jackson` `LifecycleParticipantImportTest`. Value-only Template definitions, recipient dispatch, FromMap and Jackson root/in-session routes, aliases/cycles, late-child declaration authority and completed serialization excluding Builders/handlers/contexts. Bounded same-version route tests, not historical compatibility promises. |
| D8 Java 17, dynamic/static G3/G4/G5, applicable JPMS | LP-8 classpath consumers on all lanes; G4/G5 real named contract → handler-library → Schema → consumer modules. `LifecycleParticipantConsumerTest#'qualifies separate handler modules and public Builder consumers (#named)'`; named Groovy compiler launch and normal exported/open packages, no add-exports/add-reads/add-opens/patch-module. G3 classpath-only, without ignored tests. Existing `JpmsPackageBoundaryTest` additionally constructs and calls a Groovy annotation Closure across modules from an exported Schema package, with normal descriptor access; metadata fixtures cover ownership. No deferred helper qualification is inferred. |
| LP-5 optional type mutation | Implemented and qualified, not deferred. Type-first own visit, parent-field-before-child-type, Java @Inherited/singular lookup/repeatable-container characterization, root nullable context, aggregation skips. [LP-5](issue-867-lp5-evidence.md). |
| LP-7 mandatory support-or-reject decision and diagnostics | Satisfied: rejection accepted and merged in [PR #880](https://github.com/klum-dsl/klum-ast/pull/880). 118 source/traversal controls plus seven artifact controls per lane; [LP-7 evidence](issue-867-lp7-evidence.md). No need to change eligibility or introduce API to accept this outcome. |
| LP-8 final user guidance, documentary trace, API review, release notes | Model-Phases consolidates current guidance and links unusual edges to evidence; Validation/Advanced-Techniques cross-links, Migration entry, consolidated CHANGES. Existing sidebar already links these pages; no page added/renamed/removed. Public inventory and ADR/plan synchronized. Review and verification outcomes below. |

## Exact final public API

All feature types live in exported `com.blackbuild.klum.ast.runtime`. Domain annotation libraries depend on runtime.
No API signature changes are introduced in LP-8.

| Type | Public contract |
| --- | --- |
| `LifecycleCreator` | `phase(): Class<? extends Annotation>`; `handler(): Class<? extends LifecycleCreationHandler>`; repeatable via nested `List.value(): LifecycleCreator[]` |
| `LifecycleMutator` | `phase(): Class<? extends Annotation>`; `handler(): Class<? extends LifecycleMutationHandler>`; `onSealed(): SealedPolicy` default FAIL; repeatable via nested `List.value(): LifecycleMutator[]`; nested enum has only FAIL and SKIP |
| `LifecycleCreationHandler<A extends Annotation>` | `KlumBuilder<?> create(LifecycleFieldContext<A>)` |
| `LifecycleMutationHandler<A extends Annotation>` | `void mutate(LifecycleMutationContext<A>)` |
| `LifecycleFieldContext<A extends Annotation>` | `A getAnnotation()`; `<B extends Annotation> Optional<B> getAnnotation(Class<B>)`; `KlumBuilder<?> getContainingBuilder()`; `String getFieldName()`; `Class<?> getDeclaredType()`; `FieldType getFieldType()` |
| `LifecycleMutationContext<A extends Annotation>` | Extends field context; `KlumBuilder<?> getTargetBuilder()`; default `boolean isType()` returns false, true for framework type invocations |

Class-literal handler bounds erase annotation identity; compiler/runtime validation checks the exact resolved domain type.
Existing generated `Foo_DSL.Builder<Foo>` and factory narrowing supply typed consumer access. `KlumBuilder` stays
zero-operation. Container getters retain their characterized raw nested Builder projection: ordinary Java/static Groovy
use passes, but parameterized nested wildcard assignments do not. No API was broadened to make a consumer compile.
Named modules export handler packages for public no-arg construction; only Schema-bearing packages need the ordinary
runtime opens in the LP-8 fixture. There is no DI, handler cache or retained context protocol.

## Hive checklist disposition

This section resolves the read-only Hive checklist at
`klum-ast-hive/project-memory/state/lp-8-acceptance-checklist.md`. Each item is **already adequate**,
**clarified in LP-8**, or **intentionally deferred**. The worker does not edit Hive memory.

| Checklist item | Disposition and concise evidence |
| --- | --- |
| LP-1 shared typing/narrowing and heterogeneous InvokerHelper | **Clarified in LP-8**: [typed consumer guidance](../user/Model-Phases.md#reusing-a-typed-consumer-contract), LP-1 tracer and artifact consumers. |
| LP-1 no generic property API/provider policy; no Owner/domain binding callback | **Clarified in LP-8**: same guidance, consumer-owned shared Schema bases or dynamic convention; no framework policy added. |
| LP-1 completed-LINK dynamic values versus typed null wrapper storage | **Already adequate**: retained typed-consumer and sealed-target guidance; LP-4 HANDLE probe. No unwrap/getter forwarding claimed. |
| LP-1 ordinary annotation map; LP-6 must add real benefit | **Clarified in LP-8**: [Closure guidance](../user/Model-Phases.md#annotation-closure-members), documentary tag/See on existing map probe. No helper. |
| LP-2 creation before all mutations at one field/phase | **Already adequate**: composition/four-phase tables, LP-2/LP-3 tests. |
| LP-2 repeated declared order and explicit container value order | **Already adequate**: composition guidance and Java/Groovy binary probes. |
| LP-2 unspecified mixed singular/container and distinct domain annotation order | **Already adequate**: retained composition boundary; no reflection enumeration guarantee. |
| LP-2 avoid mixtures when ordering matters | **Clarified in LP-8**: explicit practical advice in composition guidance. |
| LP-2 no sorting/priorities/ordering SPI | **Already adequate**: existing phase-order paragraph and LP-2 evidence. |
| LP-3 current-phase participation, no replay; AutoLink is not AutoCreate | **Clarified in LP-8**: [four-phase guidance](../user/Model-Phases.md#field-participants-in-four-phases-lp-3), LP-3 phase tracer. |
| LP-3 Default/PostTree late-child earlier callbacks/defaults/creation | **Clarified in LP-8**: same guidance; #874 explicitly separate. |
| LP-3 direct fields before cluster, null fallback, no mutation rerun | **Already adequate**: retained table and cluster paragraph; LP-3 tests. |
| LP-4 FAIL default, SKIP omits construction/invocation, per-mutator policy | **Already adequate**: [sealed guidance](../user/Model-Phases.md#sealed-participant-targets-lp-4), all-phase sealed tests. |
| LP-4 creators keep checked assignment, no separate sealed policy | **Already adequate**: same guidance and creator/link/session tests. |
| LP-4 HANDLE read/typed/reporting inconsistency | **Intentionally deferred**: LP-4 probe and retained explanation; no public HANDLE. |
| LP-4 no wrapper transfer/getter forwarding/unsealing/general inspection | **Intentionally deferred**: no such implementation/API; validation guidance explicitly excludes sealed-wrapper transfer. Separate decision needed. |
| LP-5 field context describes original Schema relationship target/declaration | **Clarified in LP-8**: [context guidance](../user/Model-Phases.md#context-and-qualification-boundary), LP-1 inherited-field assertions. |
| LP-5 isType/visited Builder/concrete Schema/incoming root-null context/singular type lookup | **Already adequate**: [type guidance](../user/Model-Phases.md#type-mutation-lp-5), LP-5 source/artifact tests. |
| LP-5 incoming traversal is not ownership; #856 Structure lifetime/absence | **Clarified in LP-8**: type guidance links authoritative Structure metadata and states active-session/after-OWNER/absence boundaries. |
| LP-5 no type creators; type before own fields; parent field before child type; aggregation skip; singular lookup | **Already adequate**: type guidance and LP-5 documentary/negative controls. |
| LP-5 local repeatable domain container versus repeated meta-mutators; inherited singular coexists; no expansion; distinct type order unspecified | **Already adequate**: retained type inheritance paragraph and LP-5 Java/Groovy container matrix. |
| LP-6 ordinary map remains smallest example; richer typed argument versus dynamic delegate | **Clarified in LP-8**: Closure guidance and linked LP-6 probes. |
| LP-6 IDE inference versus compiler proof; DELEGATE_ONLY not sandbox | **Clarified in LP-8**: Closure guidance; no live IntelliJ qualification or perfect inference claimed. |
| LP-6 helper/delegate/result framework | **Intentionally deferred**: #878; no helper API, generic bounds do not prove inline results. |
| LP-6 evaluation does not repair completed-LINK reads/reporting | **Already adequate**: sealed guidance; Closure section links the same boundary. |
| LP-7 rejected container fields versus ordinary child type traversal | **Clarified in LP-8**: introductory eligibility/alternatives paragraph; LP-7 probes. No incoming-container annotation lookup. |
| LP-7 null/empty/null entries and identity traversal do not imply per-element creation/occurrence/index/key/Set position | **Intentionally deferred**: #879; no participant container contract. [LP-7 matrix](issue-867-lp7-evidence.md#state-and-shape-matrix) remains the edge reference. |
| LP-7 late children/current phase, declaration authority versus null Owner | **Clarified in LP-8**: four-phase late-child paragraph plus LP-4/LP-7 evidence; no authority from paths. |
| LP-7 public nested generic Builder container limitation | **Already adequate** in acceptance evidence: final API section above links LP-7; ordinary consumers pass, negative assignments retained. No public typing repair. |
| LP-7 FromMap versus With(Map), typed Set input | **Already adequate** in linked [LP-7 route evidence](issue-867-lp7-evidence.md#identity-context-mutation-and-lifetime-implications); no new route promised. User guidance retains existing import boundaries. |
| LP-7 Closure/HANDLE deferral does not solve container typing/traversal | **Intentionally deferred**: #878/#879 and HANDLE remain independent optional decisions, not #867 blockers. |
| Final implemented core versus optional/deferred and all mandatory LP gates | **Clarified in LP-8**: mandatory map, exact API and verification sections of this record; no optional deferral counted as implemented capability. |

## Validation and review

Focused LP-8 tests pass on Groovy 3/4/5: 16 / 17 / 17 cases, zero failures/errors/skips.
The named cases run only on G4/G5 as data rows; G3 remains classpath-only. The first constructor test run reached all
expected preserved causes but failed an incorrect test expectation of `recovered()` versus the existing member spelling
`recovered`; fixing that assertion required no production change. The validation documentary tracer passed immediately.
This is qualification-first coverage of existing behavior, not a claimed production red/green change.

Stable executable-tree `./gradlew check --no-parallel` passed in 12m 37s: compiler G3/G4/G5 1890/1891/1891;
runtime 73 each; Jackson 81/79/79; bean validation 10 each; annotations 20; Gradle plugin 151; published test support
7/6/6. Total 6350, zero failures/errors, 48 unchanged existing skips (compiler 15 and runtime one per lane).
Repository license checks, lane isolation, coverage tasks and documentation-renderer checks passed. Git state and
executable inputs stayed stable through the run; prose/evidence edits do not change its Git-derived version.
The subsequent removal of stale provisional labels is Javadoc-only and receives runtime Javadoc compilation.
Independent Standards review of `a9d165c4...5ea76938` found one wording issue: `errorAt` does not bypass explicit
ERROR suppression. The additive follow-up says ERROR-level instead of unconditional. No other violations/actionable
smells were found. Specification review is clear with zero findings, including every mandatory criterion and checklist
item. LP-8 documentary names/methods/page links are recorded in the [#867 acceptance-evidence comment](https://github.com/klum-dsl/klum-ast/issues/867#issuecomment-6100033541). Both independent additive re-reviews are clear.

The committed `5ea76938` documentation rendered and passed the local served-site crawl in 22s with six Javadoc outputs.
The corrected committed user pages at `6bc3a5c9` then rendered and passed the served-site crawl in 8s. Runtime Javadoc compilation also passed.
Dedicated branch history is two focused reasoning steps (qualification, then final contract/guidance) plus additive
review/evidence and delivery-reference follow-ups; reviewed commits are preserved. Relative file links and diff checks pass.
Exact final-head remote CI/SonarCloud outcomes and SHA are recorded in [draft PR #881](https://github.com/klum-dsl/klum-ast/pull/881) and the final handoff after inspection; local results do not stand in for remote results. No ignored/pending test, broad suppression or access workaround is added.

Delivery authorization audit: gh CLI repository push capability verified **authorized**; Git SSH push dry-run verified **authorized**.
No GitHub App delivery channel used. Delivery is [draft PR #881](https://github.com/klum-dsl/klum-ast/pull/881), with the preselected `Closes #867` relationship after the complete mandatory acceptance audit. Request Hive reconciliation after handoff;
open-PR state is not archive-safe, and the worker does not self-archive.
