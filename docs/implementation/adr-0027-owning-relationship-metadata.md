# ADR 0027 implementation plan: owning Schema relationship metadata

Date: 2026-10-08

Status: RM-1 ordinary tracer implemented under approved D1/D4/D6; RM-2–RM-4 pending

Decision: [ADR 0027](../adr/0027-owning-relationship-metadata.md)

Tracking issue: [#856](https://github.com/klum-dsl/klum-ast/issues/856), open, target 4.1

## Current execution boundary

D1's exact descriptors/equality/errors, D4 placement in 4.1, and D6 lifetime are approved. Separate authorization delivers
RM-1's direct/inherited ordinary tracer, documented in [the RM-1 evidence](issue-856-rm1-evidence.md).
Retained RM-0 authority is local `6c7d2efab4b10bbc6ee92d3932c8731a6c46cd5a`, with 33 characterization cases.
D2 accepted-definition Template retention/recipient recapture and D3 same-version serialization are explicitly approved
for bounded RM-2 in [the decision record](issue-856-rm2-decisions.md). D5 repairs remain excluded and unresolved. The source-audit tables below retain their historical planning bases; the RM-1 report
identifies which acceptance rows now have executable public-API evidence. Initial guidance is delivered with this seam;
full RM-4 documentation/qualification remains pending.

## Authority, scope, and evidence provenance

The maintainer accepts the structural-metadata investigation direction and freezes
`KlumBuilderSupport.of(builder).getStructure()` alongside `KlumObjectSupport.of(model).getStructure()`, a shared immutable
`KlumSchemaRelationship`, Optional absence, and an operation-time active-session/after-OWNER guard. Structure is the only
Builder support capability now. RM-1 implements that narrow metadata seam and invents no LinkTo selection algorithm.
ADR decisions D1–D6 distinguish remaining approvals from accepted direction. The follow-up accepts the public facade and
consumer-selection separation, requests source-backed reconsideration of the unsealed/phase-before-40 restriction, and
puts the ScHelm vertical tracer first. D6 is now approved; the separate RM-1 assignment supplies implementation authorization.

Input is the completed local investigation on `codex/issue-856-link-binding-investigation`:

- `763e122dbc45b735a13170d741220c6eb0d90938`: 685-line `LinkBindingInvestigationTest`, 39 characterization/fallback cases.
- `1715eab6`: investigation report and candidate ADR 0027. This ADR supersedes that candidate spelling and conditional plan.
- PR #860 original planning tip: `16ebdcd9`; this planning follow-up preserves that published history and adds a focused
  revision. Lifecycle source below was rechecked at unchanged `513cfcdd`; no new API test is claimed.
- Investigation/master base: `5a04605d27e678ca69814861665823650780ff67`.
- Final refreshed planning base on 2026-10-08: `513cfcddb2ca7b7dc652c9a57fa1b20be35a7ee4`. Master advanced during review
  through merged PR #857/#855. The planning branch was rebased before publication. That change adds sealed mutability
  preflights and reserved generated linkage; it changes no declaration capture, CopyHandler, companion, phase, or completed
  Structure seam. Relevant source facts below were rechecked against this final base; prior investigation test results
  still describe `5a04605d`, not the new base.

Those local-only investigation commits and tests are not included in this documentation PR. Their report records full
`:klum-ast:test`, `:klum-ast:groovy4Tests`, and `:klum-ast:groovy5Tests` passing with 1,475 tests/lane, zero failures/errors,
15 existing skips, and 39 new cases passing without skips, plus `:klum-ast:licenseTest`. Versions were 3.0.25/4.0.32/5.0.6.
These are prior investigation results, not executions on this planning branch and not proof of the proposed API.
Hive retains the local evidence branch; maintainers can inspect it without treating an unpublished hash as a GitHub URL:

```shell
git show 763e122d -- klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/layer3/LinkBindingInvestigationTest.groovy
git show 1715eab6:docs/implementation/issue-856-link-binding-investigation.md
```

The durable source-backed summary below is sufficient to review the decision without access to that local branch.
Future implementation should bring over only relevant characterization cases, with provenance and @Issue("856"), if
they are not independently delivered. It must not cherry-pick the old candidate ADR or carry an application resolver
into production.

## Confirmed current behavior and failure paths

| Finding at the refreshed base | Evidence | Design implication |
| --- | --- | --- |
| Current LinkTo supports explicit field, fieldId, receiver-local selector, and field/owner-name inference; AUTO cardinality counts declarations including nulls | [LinkHelper](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/layer3/LinkHelper.java), investigation cases | Preserve resolution; metadata lookup has no candidate policy |
| Already configured targets are not replaced by declarative AutoLink | [AutoLinkPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/layer3/AutoLinkPhase.java) | No change to explicit overrides |
| Inherited LinkTo defaults use the relationship field's declaring class | [LinkToWrapper](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/layer3/LinkToWrapper.java) | Concrete receiver class is not declaration identity |
| OWNER_PATH matches an immediate single-field identity, not list/map paths or arbitrary ancestor expressions | [StructuralPath](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/layer3/StructuralPath.java), investigation cases | Never parse a path to recover annotations |
| AUTO_LINK(20) precedes DEFAULT(25); owning DefaultValues are applied before Default callbacks | [DefaultKlumPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/DefaultKlumPhase.java), [DefaultPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DefaultPhase.java) | Direct metadata enables the earlier callback without transported state |
| The fallback's inherited Default callback selects a completed subtype Model by identity through its generated relationship method | Investigation existing-API cases | Viable workaround, no need for a new generic link operation |
| Static direct completed Cluster access on a Builder and subtype-Builder argument to the inherited base setter fail compilation | Investigation compiler probes; [ADR 0020](../adr/0020-explicit-shared-model-builder-capabilities.md) | Query/projection and subtype typing remain independent |
| Completed inferred targets on sealed providers and explicit projected-Builder type checks have current gaps | LinkHelper/ClusterModel and investigation | #853 owns declarative compatibility; do not repair it here |
| Completed child can have `<root>.primary` model path but no direct Owner | Investigation; [KlumObjectSupport](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumObjectSupport.java) derives owners from Owner members | New metadata cannot depend on explicit Owner availability |
| Builder claims retain private compositionOwner/compositionFieldName; OPTIONAL_LINK claim is per entry, with a permitted self-claim transfer | [InternalKlumBuilder](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java), [OptionalLinkRelationshipTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/OptionalLinkRelationshipTest.groovy) | Capture exact declaration with accepted claims, update on permitted transfer |
| CopyHandler collection/map copying inserts rehydrated Builders directly and reuses recipe identities, bypassing claim normalization used by single-field copies | [CopyHandler](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CopyHandler.java).addCollectionValues/addMissingMapValues/mergeMapValues/addMapValues | Qualify owned-copy placement as a distinct capture seam; probe aliases and OPTIONAL_LINK before selecting a mechanism |
| Composition visits provide container/member and contextual path; identity-cycle safety avoids repeat visits | [CompositionTraversal](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/layer3/CompositionTraversal.java) | Use as corroborating context, not persisted path identity |
| ModelState exports paths/metadata; Model/Template companions do not retain owning declaration | InternalKlumBuilder.exportModelState/$createCompanion; [KlumModelProxy](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/KlumModelProxy.java), [KlumTemplateProxy](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/KlumTemplateProxy.java) | Add typed private declaration state, no public metadata Map |
| Template definition and value-only Template import materialize without normal OWNER/session phases | [ADR 0004](../adr/0004-asbuilder-composition-protocol.md), InternalKlumBuilder.materializeTemplateForImport | Retention cannot depend solely on OWNER traversal |
| KlumObjectSupport.of rejects marked Templates through the ordinary Model companion gate | [InternalKlumObjectSupport](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumObjectSupport.java).requireCompletedModel → KlumModelProxy.getProxyFor | Internal Template retention does not authorize a new public Template facade path |
| Session membership and phase number are independent live facts; allocation seals Builders in INSTANTIATE | [PhaseDriver](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/PhaseDriver.java), [InstantiatePhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InstantiatePhase.java) | Guard each request, including late phases while the session still exists |

The existing [architecture map](issue-curation/architecture-map.md) and
[Builder-first migration guide](../user/Builder-First-Migration.md) corroborate these state boundaries. Read
[ADR 0006](../adr/0006-completed-object-support.md), [ADR 0014](../adr/0014-groovy4-jpms-boundary.md),
[ADR 0015](../adr/0015-generated-schema-runtime-linkage.md), and
[ADR 0023](../adr/0023-schema-owned-constraint-annotations.md) before implementation. ADR 0023's relationship constraints
read source annotations during completed validation, not live Builder owner selection. This is an additive discovery
seam, not a redesign of constraint evaluation. Proposed [ADR 0026](../adr/0026-inherited-generic-schema-relationships.md)
is not an implemented generic specialization dependency.

## Modules and seams

| Module/seam | Planned change | Boundary to preserve |
| --- | --- | --- |
| `klum-ast-runtime`, public runtime package | KlumBuilderSupport and nested Structure; shared KlumSchemaRelationship; additive completed Structure queries | No public raw Field, internal Builder, companion, session, or Model extraction |
| InternalKlumBuilder relationship normalization/claim, late attachment, ModelState and companion creation | Private declaring-class/member identity; transfer final claim into completed state | LINK never owns; per-entry OPTIONAL_LINK semantics; no retained Builder/owner graph pointer |
| CopyHandler single/collection/map insertion and rehydrated recipe identity map | Capture qualified recipient owning declaration for copied owned entries, including direct collection/map insertion | Preserve LINK identity, overwrite strategies and alias identity; no blanket normalization that changes OPTIONAL_LINK behavior |
| Internal Model/Template companions and InternalKlumObjectSupport | Typed internal read/transfer seam and serialization audit | No arbitrary public metadata; no Optional/annotation/Field serialization |
| PhaseDriver and existing Builder state checks | Compose receiver/session checks with operation-time numeric phase guard | Never create a session, reorder phases, or cache lifetime eligibility |
| CompositionTraversal/BuilderStructureSupport/OwnerPhase | Reuse declaration resolution/traversal context only as needed, including late subtree initialization | Existing traversal filtering, Owner semantics, and paths stay unchanged |
| `klum-ast` tests/generated hooks | End-to-end source/binary/lifecycle tests; adjust private transfer linkage only if required | No generated public interfaces or source-mirror change; retain ADR 0015 ABI classification |
| `klum-ast-jackson` import tests | Qualify owned import and linked reference identity/metadata | No new format fields or importer mode; annotation module has no runtime dependency |
| Named-module consumer fixtures | Public package access and annotation read proof | Groovy 3 classpath only; Groovy 4/5 JPMS; no wider internal exports/opens |

## Retention and lifecycle contract to implement after approval

Resolve from the owner's Schema type and exact accepted field, not the child's concrete class or first traversal alias.
Record the declaring Class and field name so an inherited field stays identifiable even on a concrete subclass. A cache,
if useful, must respect Class/classloader identity and must not retain whole owner graphs or use a global name-only key.
Do not expand this into the generic-specialization model under #180.

The record is absent for an eligible root/unattached Builder and a completed Object without known ownership metadata.
An explicit retained record that fails resolution throws with declaring Schema/member context. It does not silently fall
back to a path or Optional.empty(). Runtime annotation lookup does not require making a field value accessible; field
visibility and JPMS access must be qualified, including a private annotated owning declaration.

At normalized attachment, capture from the claim that normalization accepts. Rejected composition inputs cannot install metadata.
For direct CopyHandler collection/map insertion, qualify a separate owned-placement hook or reconcile accepted edges before
snapshotting; current code does not guarantee a claim for these entries. RM-0/D5 must establish the supported mechanism
and behavior for reused recipe identities across distinct fields. Do not choose an arbitrary first visit, duplicate a
copied target to make metadata simpler, or change aggregate entries into composition. Same-field repeated entries share
the same declaration. If the existing graph has genuinely conflicting owned declarations, return for D5 rather than
silently promising unique ownership. A generic ownership repair outside this metadata seam remains separately scoped.
A permitted self-OPTIONAL_LINK-to-composition transfer updates the claim/declaration together. Late attachment after
OWNER must work through the existing ownership initialization seam without changing when ordinary Owner callbacks run.
The final snapshot is taken before or during internal allocation/companion creation and is available by VALIDATE. Two-pass
allocation and relationship assignment retain cycles/self-links, immutable collection snapshots, and target identities.

## Lifecycle evidence and approved read-only contract

This is source-backed analysis of existing lifecycle behavior, not an execution of the proposed support API. The phase-40
cutoff in the original plan followed mutable traversal/materialization boundaries; the audit does not show a metadata
invalidation at that number. Approved **D6** changes that restriction, retaining the lower bound.

| Inspected source at `513cfcdd` | Observed behavior | Authority implication |
| --- | --- | --- |
| [InternalKlumBuilder](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java).allocateModel/materializeGraph | Allocation sets completedModel and sealed, then a second pass assigns relationships; claim fields are not cleared | Mutation stops on sealed receivers, while known declaration identity survives; partially allocated Models are not a complete graph |
| InternalKlumBuilder.$completeConstructionSession/$isInActiveConstructionSession | Completion sets an active flag false; sealing does not | Active context and mutability are independent |
| [PhaseDriver](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/PhaseDriver.java).withBuilderLifecycle/executeIfReady/leave/completeConstructionSession | Registered Builders remain session members through phase execution; finally deactivates them and removes the thread-local driver on return/exception | Retained references can be same-session receivers during/after 40, but must reject after completion/abort or on a different thread/session |
| [InstantiatePhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InstantiatePhase.java).doExecute | Replaces the Builder root only after both materialization passes return | A phase-40 action before it sees a Builder root; one after it sees a completed root; the same number cannot determine Model availability |
| [CleanupPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CleanupPhase.java).doVisit | No Builder ownership cleanup; runs before lifecycle finally | COMPLETE(100) does not by itself deactivate the session |
| [BuilderVisitingPhaseAction](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/BuilderVisitingPhaseAction.java) / [ModelVisitingPhaseAction](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/ModelVisitingPhaseAction.java) | Builder traversal rejects phase ≥40/skips sealed values; Model traversal requires >40 and receives Models | Read policy must not add late Builder traversal/callbacks; metadata receiver can instead be a retained Builder |
| InternalKlumBuilder.assertMutable / [AsBuilderSpec](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/AsBuilderSpec.groovy) | Mutations reject sealed/closed-session state; existing tests retain Builder references after factory return | Physical retention is real; mutation rejection is not proof of declaration loss |
| [FactoryHelper](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java).wrapCompletedModel/createBuilder and InternalKlumBuilder.sealTo | A wrapper created in a root lifecycle joins that session and is immediately sealed around the existing Model | Wrapper metadata source must be the completed target's original stored declaration, never its importing LINK edge |
| [InternalKlumObjectSupport](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumObjectSupport.java).requireCompletedModel | Checks ordinary Model companion presence; does not certify two-pass graph completion | Individual allocated Models may pass the facade gate during allocation; supported graph work waits for InstantiatePhase to finish |

Before 40, accepted ordinary composition claims can still change through existing permitted transfer/late attachment;
read the current authoritative declaration, not a cached early result. Normal allocation/sealing leaves the accepted
claim unchanged. The proposed capture record must match the completed snapshot; these fields are not erased at 40 or
COMPLETE. Source presence after session exit does not grant read authority. Copy paths without a qualified declaration
remain D5 cases; the lifetime proposal does not bless them or repair ownership.

Approved per-request checks under D6:

1. Validate a genuine supported Builder receiver; reject null, completed Model and unsupported marker implementations.
   Do not call mutation preflight or reject merely because sealed.
2. Require membership in the current thread's active Construction session; reject closed/aborted, foreign-session,
   off-thread and Template-definition state.
3. Require current numeric phase `phase > 15`; construction with no phase and custom phases ≤15 reject. No upper bound.
4. Resolve authoritative metadata: current accepted/qualified declaration for normal Builders (including normally sealed
   ones), original completed-target record for LINK wrappers; return empty only for valid absence. Fail explicit invalid
   records/ownership conflicts. Never use LINK alias, Owner members, Role or paths as substitutes.

Acquisition can happen early, including for a genuine sealed wrapper, but every query rechecks eligibility and source
identity. A cached successful read cannot authorize another call. Preserve the detached immutable descriptor's independent
lifetime; no public Model extraction is added. After success/cleanup use KlumObjectSupport on the returned Model; abort
provides no publishable Model guarantee, even if allocation had started. Never change existing mutation/scheduling limits.

| Phase/state at request time | Approved metadata result under D6 | Model availability / qualification |
| --- | --- | --- |
| Construction/PostCreate/PostApply, no phase; APPLY_LATER(1), AUTO_CREATE(10), OWNER(15), custom ≤15 | Reject before declaration read, including root/missing annotation | External completed provider Models can already be queried through their own facade |
| Custom 16, AUTO_LINK(20), DEFAULT(25), POST_TREE(30), custom 16–39 | Same-session authoritative declaration read; genuine absence is empty | Current graph remains Builder-based; completed external candidates are independent |
| Action at 40 before InstantiatePhase | Read authoritative Builder record while session valid | Current graph has not yet materialized; test actual same-number ordering |
| During normal allocation/sealing and relationship assignment | No sealing/number-based rejection; metadata identity is stable | Individual companions may exist; no public promise of complete Model graph until both passes finish |
| Action at 40 after InstantiatePhase; custom >40; VALIDATE(50), VERIFY(80), COMPLETE(100) | Retained same-session Builder view reads authoritative record even when sealed | Completed root is available; ordinary phase callbacks use Model support; no late Builder visitor introduced |
| Session exit after COMPLETE or successful factory return | Live Builder view rejects; detached descriptor still readable | Returned ordinary Model support is the consumer path |
| Exception before/within/after materialization, once lifecycle finally runs | Retained view rejects after abort; no resurrection in a later session | Allocated objects do not constitute promised successful factory output |
| Same-session sealed LINK wrapper, phase >15 | Read completed target's original retained declaration, including valid empty | Target Model facade was already available; never report the importing field as ownership |
| No session, foreign session/thread, Template-definition Builder | Reject regardless of number, sealing or retained fields | No new public Template inspection |
| Completed ordinary Object at VALIDATE or outside lifecycle | Read retained declaration with no Builder session requirement | Keep current ordinary-Model gate and absence/error semantics |

Custom 40 actions before and after InstantiatePhase need ordered low-level PhaseAction fixtures: BuilderVisitingPhaseAction
cannot be used at that number and ModelVisitingPhaseAction requires >40. The ordinary boundary probe must observe
allocation/assignment state without adding a production callback. Also characterize driver phase sources:
PhaseDriver.getCurrentPhase tracks currentPhase (last action), while AbstractPhaseAction clears Context.phase on exit.
Post-phase closures retain the last action number. Guard fixtures must record and define the existing phase semantics
instead of treating Context.instance as session membership or inventing a new scheduler/phase API.

Diagnostics name operation, receiver type, phase/session or invalid-declaration reason; unsafe owner reads cannot decorate
an early rejection. Use a counting/failing internal declaration-reader seam or equivalent observable fixture to prove
rejected calls do not reach ownership lookup. A premature root returning empty is a failure. There is no production
metadata API at that planning base. Current public-API results are recorded separately in the RM-1 report.

## Tracer-bullet slices and reasoned commits

Revised dependency order: **RM-0 → RM-1 (ScHelm vertical tracer) → RM-2 (extended graph/persistence/import qualification)
→ RM-3 (full binary/JPMS qualification) → RM-4 (delivery documentation and final gate)**. RM-1 combines the previous
completed-metadata and live-Builder slices so the motivating use case runs before the extended matrix. These milestones
are conditional plans, not authorization to implement or create child issues.

D1/D4 approve bounded RM-0/RM-1 work; D6 approves the read-only lifetime change before the corresponding guard is built.
D2/D3/D5 gate their later qualification work, not the ordinary direct-field tracer. RM-0 identifies representation risks
and copy-path ambiguity, but does not require resolving every Template or historical stream case before RM-1. No milestone
alone is release-ready; all approved final acceptance remains required. Consumer provider-selection policy is never
production KlumAST work.

### RM-0 — prove authoritative capture and identify representation risks

Use the smallest direct owned-field/inherited-declaration fixture with no Owner backreference to prove the exact Schema
Class/member can be captured at accepted attachment, observed after OWNER, and transferred through ordinary allocation.
Audit normal sealing/session cleanup and same-number phase-40 ordering from the lifecycle section. Prove declaration
identity can survive materialization without keeping Builder/session/owner graph pointers or changing public generated
interfaces. Pin exact descriptor implementation details under D1; do not reopen the accepted facade shape.

Investigate CopyHandler list/map direct insertion, recipe identity reuse, overwrite strategies, OPTIONAL_LINK mix and
same-field versus conflicting-field aliases. Record which routes bypass claims and what D5 must settle. Audit both
companions' current serialization form/computed UIDs as representation risks, with historical guarantees still controlled
by D3. A risk inventory and safe ordinary-path record boundary suffice here; complete Template handling, serialization
round trips, imports and JPMS are later gates. If the ordinary representation itself cannot be sound without a D2/D3/D5
choice, explain that concrete dependency to the maintainer; never add a blanket dependency speculatively.

One bounded non-shipping characterization/proof commit carries the source-backed capture/lifetime/copy-seam evidence.
Do not commit tests against absent public APIs or a test-only provider resolver as framework design. Prefer existing
KlumObjectSupportSpec, AsBuilderSpec, OptionalLinkRelationshipTest, TemplatesSpec and JpmsPackageBoundaryTest seams;
bring over only relevant investigation cases with their @Issue provenance. Exit with a viable direct-field record design,
D6 evidence, and a precise deferred-risk list. No public probe class, guessed UID or ownership repair is introduced.

Acceptance: ordinary-path characterization for A01/A02/A03; lifecycle boundary evidence for A10/A11/A26; A16 UID audit,
A24 copy-path inventory. Complete A16/A24 qualification remains with RM-2.

### RM-1 — first executable ScHelm vertical tracer

This is the first meaningful implementation milestone after bounded RM-0, once implementation is separately authorized.
Use neutral executable fixture vocabulary for the ScHelm scenario. Declare consumer-owned runtime field annotations
Binding, Source and a default marker in the test consumer, never in KlumAST. A receiver base declares its Facts LINK once
and owns an inherited AUTO_LINK callback. A concrete Application field owns the concrete receiver and carries Binding;
an inherited owning field on a concrete Application subclass is a second fixture without redeclaration. A provider
Environment is completed independently beforehand and owns Facts candidates on annotated Schema fields. At least one
Facts child has no Owner backreference. No DefaultValues transport or receiver binding property is used.

Implement/test this exact public route as one coherent vertical milestone:

1. Completed provider candidate → KlumObjectSupport.of(candidate).getStructure() → exact declaring Schema field and
   Source/default annotation. Add KlumSchemaRelationship, the typed private ordinary record and materialization transfer.
2. Owned callback receiver at AUTO_LINK → KlumBuilderSupport.of(receiver).getStructure() → exact owning field and Binding.
   Add only Builder Structure metadata operations and per-request checks under D6; normal root/missing annotation and
   phase/session/retained-view tests accompany the behavior rather than waiting for final qualification.
3. Test-local consumer policy reads those annotations, selects a completed Facts value, and calls the existing typed
   Facts relationship operation. Assert identity with the independently completed target, unchanged target ownership,
   no target mutation/revalidation/copy, explicit override preservation and no generated interface change.

Illustrative callback fragment, with completedCandidates supplied by the consuming fixture's existing provider-access
mechanism; this sketches a future test, not executable shipped code or a new framework candidate API:

```groovy
@AutoLink void bindFacts() {
    if (facts != null) return
    def binding = KlumBuilderSupport.of(this).structure
        .getOwningRelationshipAnnotation(Binding).orElseThrow()
    Facts selected = ConsumerPolicy.choose(binding, completedCandidates) { candidate ->
        KlumObjectSupport.of(candidate).structure.getOwningRelationshipAnnotation(Source)
    }
    facts selected
}
```

The policy/default reader is application-owned and can read a consumer default marker through the same Optional query.
It is not a LinkTo/AutoLink algorithm, a public enumeration helper or a mandatory precedence standard. Selection here
exists solely to prove that metadata composes with the existing generated relationship method and exact target identity.
Use the investigation's viable completed-Model access route; do not fix static Cluster projection/subtype-Builder typing.

Suggested reasoning commits within RM-1: **Expose ordinary completed owning declarations for provider metadata (#856)**
with the minimal completed lookup/capture tests, then **Complete the inherited AUTO_LINK metadata tracer through public
Builder support (#856)** with the live facade/guard and consumer acceptance. Keep each behavior and passing tests together;
the completed-only preparatory commit is not the RM-1 success milestone. Any private generated linkage adjustment needs
ADR 0015 evidence in its behavior commit and cannot change public Builder signatures.

Run the inherited callback and owning-declaration examples in G3/G4/G5. Separately compile consumer annotations and base
Schema types, then the leaf/Writer, using existing fixtures where feasible; include static Java/Groovy facade signature
smoke checks. If a particular binary harness needs later work, record that exact gap and fixture rather than blocking
all use-case feedback on complete JPMS/classloader coverage. The full binary contract still gates RM-3/release readiness.

Exit only when the two public facade paths, consumer selection, identity preservation and operation-time guards all
work together. Acceptance: A01/A02/A03/A05/A06/A17/A25 plus initial A20 and core A10/A11/A26. No Template/copy/import or
serialization completion is required to demonstrate this milestone; those remain mandatory before final qualification.

### RM-2 — extended graph, Template/copy, persistence and import qualification

Apply the RM-1 ordinary record to direct/list/set/map graphs, repeated identities, self/cyclic links, OPTIONAL_LINK mixed
entries, accepted claim transfer and existing late attachment. Do not infer declaration from paths/Owner values or
change the ownership engine. Qualify the D6 lifetime across these already-supported construction routes.

Under D2, retain Template definition declarations internally while preserving direct public Template rejection, and
recapture recipient fields on application. Under D5, qualify CopyHandler direct container insertion and conflicting
recipe aliases using the approved bounded mechanism; do not normalize aggregation into ownership or duplicate targets
for convenience. Ordinary Model/Map and same-session Builder copy paths retain their existing semantics.

Under D3, qualify same-version ordinary/Template serialization, linked cycles, subtrees without owner backreferences and
compatible absent metadata. Particular old-byte fixtures are required only for specifically approved historical support;
no arbitrary compatibility promise or guessed UID. Jackson root/in-session/apply/reference and value-only Template imports
capture new owned edges while preserving original linked targets and export policy; no companion wire metadata.

Reasoned boundaries, each green with its needed behavior fixes: **Qualify extended composition declaration metadata (#856)**,
**Qualify Template/copy declaration retention under the approved policies (#856)**,
**Qualify declaration serialization under the approved version boundary (#856)** and
**Qualify owning declarations across managed import (#856)**. Split or combine only when it clarifies the behavior dependency.
Use TemplatesSpec/TemplateRecipeStateTest and existing KlumJacksonImporterSpec/ConfigurationReplaySpec/LinkIdentitySpec.

Acceptance: complete A04/A06–A09/A12–A16/A18/A19/A24 and extended A10/A11/A26. D2/D3/D5 cannot be bypassed by a successful
RM-1 tracer. No feature-release claim is made before these persistence/graph qualifications pass.

### Authorized RM-2 subset after D2/D3 approval

The maintainer accepted internal declaration retention for existing Template-definition claims and same-version-only Java
serialization. Qualify accepted normal container construction/imports, direct single-field copies and nested merges into
already claimed recipients. Copied-container direct insertion, conflicting cross-field recipe aliases, and OPTIONAL_LINK
copy repairs are excluded. No historical byte fixture is promised. The full original RM-2/A24 contract remains open at D5;
only the bounded subset may be reported delivered. Preserve the source-audit evidence and distinguish normal container
attachment from CopyHandler bypasses. No RM-3/RM-4 work follows from this authorization.

### RM-3 — complete Java/Groovy binary and JPMS qualification

Compile real runtime/Schema artifacts before their separate consumers. Expand initial RM-1 binary checks to Java 17 and
static/dynamic G3/G4/G5, inherited bases/private annotated fields in separate packages, separately compiled annotation
types and concrete callbacks using public generated Builder contracts only. Verify generic/Optional descriptors,
unchanged Foo_DSL signatures/AnnoDocimal mirrors and no internal type in public signatures.

Run G4/G5 named Schema/annotation/consumer modules through JpmsPackageBoundaryTest. G3 remains classpath-only. Existing
runtime export/schema opens must suffice; no broad exports/opens or add-opens workaround. Exercise separate classloaders
with same-named declarations to prevent name-only caches. No runtime dependency from the annotations artifact. Any access
or linkage gap returns for a narrow decision rather than expanding module/ownership boundaries implicitly.

One reasoned qualification commit pairs the real consumers and any required bounded fixes:
**Qualify relationship support across Java/Groovy binary and module boundaries (#856)**.
Acceptance: full A20–A22 and lifetime/tracer regression in the qualified modes. Initial separately compiled tracer proof
is retained; same-source Spock alone or G3 classpath never substitutes for this gate.

### RM-4 — delivery documentation and final acceptance

Add the metadata-only documentary example using the RM-1 route, lifecycle/Template/persistence guidance, migration and
navigation, CHANGES and exact evidence. Keep consumer selection illustrative and consumer-owned. Link @Issue/@Tag/@See,
issue and current user source to one another. Mark only delivered/approved slices as implemented in ADR/plan status.

One reasoned final documentation commit: **Document approved relationship Structure support and its lifetime (#856)**.
Acceptance: A23 and all A01–A26 approved requirements green, D1–D6 dispositions explicit, focused baseline plus final
G3/G4/G5 qualification, exact-head CI/Sonar review and normal publication authorization. A successful RM-1 tracer does
not imply release readiness, parent issue completion or authorization to target/close #856.

## Executable acceptance matrix

The table below is the full eventual acceptance contract; RM-1 results are mapped in its report. Proposed semantics depend on the named
ADR gates. New test classes use the Test suffix and @Issue("856") or the assigned child issue; documentary tests use
@Tag("documentary") and @See to the current user-documentation source. No pending test is added by this planning PR.

| ID | Fixture/input | Expected result/assertion | Slice |
| --- | --- | --- | --- |
| A01 | Direct owned child with Binding; eligible callback and completed lookup | Same exact Schema declaring Class/member and annotation values from both views; no callback transport property | RM-1 |
| A02 | Inherited owning field on concrete owner, child with inherited callback, no field redeclaration | Original base Schema declaration retained; callback can read its owning binding in AUTO_LINK | RM-0/RM-1 |
| A03 | Child has no Owner; alternate fixtures with several/transitive/converted Owner values | Actual owning declaration independent of Owner values; existing getSingleOwner ambiguity unchanged | RM-0/RM-1 |
| A04 | Owned list/set/map values including repeats and map keys requiring escaping | Containing field annotation; no index/key in declaration identity; no new unique-path contract | RM-2 |
| A05 | Eligible root, unattached same-session Builder, missing annotation, metadata-absent completed Object | Optional.empty; absence does not imply historical object is a root | RM-1 |
| A06 | LINK alias to same-root owned child and external completed child/root | Original declaration/empty root result preserved; no receiver-edge adoption or target copy/mutation | RM-1/RM-2 |
| A07 | OPTIONAL_LINK single/list/map mixing fresh owned, claimed and completed entries | Only owned claim captures receiver declaration; aggregate entries keep originals; existing traversal filtering | RM-2 |
| A08 | Permitted self-OPTIONAL_LINK claim transfer; rejected cross-owner/cross-session composition | Final accepted declaring field wins; rejected attachment does not overwrite metadata | RM-2 |
| A09 | Self/cyclic links and two references to one target; VALIDATE callback | Graph identity retained; descriptor readable by VALIDATE; no Builder/session retained in completed state | RM-2 |
| A10 | Early-created facade; OWNER/≤15/custom 15 requests on child/root/missing annotation, then phase 16/20 and later phases | Each early call throws before declaration read; eligible calls recheck session/phase/source on every request, including cached-result paths | RM-1/RM-2 |
| A11 | Retained views after completion/abort, foreign session/thread, Template-definition Builder; fresh lookup in a different lifecycle | Reject before declaration read despite retained fields/old success; no session resurrection; completed ordinary Model lookup and detached descriptor remain independent | RM-1/RM-2 |
| A12 | Marked Template root and owned nodes, including value-only imported Template; D2 policy | Internal definition-edge record for owned nodes/root absence; public completed facade still rejects Templates; no public live Builder access without session | RM-2 |
| A13 | Template applied under a different recipient field; same template applied twice | Fresh graphs each expose recipient-edge metadata, not source edge; recipe and linked identities unchanged | RM-2 |
| A14 | copyFrom ordinary Model/Map/same-session Builder; nested copy and standalone root copy | Recipient claims recaptured; no old owner adoption; standalone root empty; existing sealed/cross-session rejection | RM-2 |
| A15 | Ordinary/Template Java serialization; linked cycles; subtree without Owner | Same-version declaration/annotation and graph identities restored; no owner graph pulled in by metadata; no Field/Optional/session/Builder state serialized | RM-2 |
| A16 | Compatible absent record; invalid retained declaration; specific historical stream only if D3 authorizes | Absent yields empty; explicit bad record fails with Schema/member; UID compatibility proved for each claimed version | RM-0/RM-2 |
| A17 | Exact/missing annotation and null Class; retained descriptor after Builder expires | Typed Optional values, empty for absence, null rejected; immutable descriptor usable without live session | RM-1 |
| A18 | Jackson root/in-session/apply import and reference targets; export ordinary Objects | New owned edges recorded, references retain originals, no wire field; Template export rejection and importer lifecycle unchanged | RM-2 |
| A19 | Child attached after OWNER through existing late-subtree initialization | Accepted claim recorded immediately; eligible callback sees field; no duplicate lifecycle or guessed traversal edge | RM-1/RM-2 |
| A20 | Java 17 and static/dynamic G3/G4/G5 consumers of separately compiled inherited Schema/annotations | Initial binary tracer in RM-1 where feasible, then full consumer gate: public facades only, exact generic/Optional descriptors and concrete annotation identity | RM-1/RM-3 |
| A21 | G4/G5 named modules; private field annotation; separate annotation module; same names in separate classloaders | Public export sufficient, existing schema opens baseline; no extra broad opens/exports; cache respects Class identity | RM-3 |
| A22 | Inspect KlumBuilder, Foo_DSL signatures, Model properties, AnnoDocimal mirrors, wire output | Marker remains zero-operation; no added generated interface/mirror/Model/wire property; no internal type in public signatures | RM-3 |
| A23 | Documentary Builder callback and completed annotation read; release guidance | Example matches executable test; @Issue/@Tag/@See and issue/docs traceability; feature only documented as delivered when green | RM-4 |
| A24 | Ordinary Model/Template list/map copies through direct CopyHandler insertion; overwrite strategies; repeated recipe aliases and OPTIONAL_LINK entries | Characterize current claims/materialization in RM-0; after D5, capture the approved recipient declaration without arbitrary traversal tie-breaking, target duplication, or aggregation changes | RM-0/RM-2 |
| A25 | ScHelm tracer: inherited receiver Facts relationship/AUTO_LINK, owning Binding; independently completed owned provider candidates with Source/default markers; separately compiled bases/annotations where feasible | Both public facades expose exact owning declarations and typed Optional annotations; test-local consumer policy calls existing typed relationship operation; exact completed-target identity/ownership and explicit override preserved; no transport or internal API | RM-1 |
| A26 | D6 lifetime: normal allocation/sealing, phase-40 actions before/after InstantiatePhase, custom >40, 50/80/100; same-session LINK wrapper; repeated calls then exit/abort | Authoritative declaration remains readable while context valid, even sealed; wrapper yields original target declaration/absence; no mutation or partial-graph promise; exit/abort rejects; both query methods recheck every call | RM-0/RM-1/RM-2 |

## Compatibility qualification and documentation delivery

Focused work uses Groovy 3 in the touched modules; at each completed behavioral slice run the Groovy 4/5 lanes. The final
qualification uses the same production artifact under Java 17 with isolated version-specific test/fixture outputs, as
[ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md) requires. G3 named modules are explicitly outside
[ADR 0014](../adr/0014-groovy4-jpms-boundary.md); do not report G3 classpath as module proof. Runtime, AST, and Jackson
tests together cover the behavior; Gradle plugin tests are needed only if an evidenced generated/mirror change is approved.

Suggested final implementation commands (not executed for this docs-only PR):

```shell
./gradlew :klum-ast-runtime:test :klum-ast:test :klum-ast-jackson:test
./gradlew :klum-ast-runtime:groovy4Tests :klum-ast:groovy4Tests :klum-ast-jackson:groovy4Tests
./gradlew :klum-ast-runtime:groovy5Tests :klum-ast:groovy5Tests :klum-ast-jackson:groovy5Tests
./gradlew :klum-ast:licenseTest :klum-ast-runtime:licenseTest :klum-ast-jackson:licenseTest
git diff --check
```

Extend the existing binary/JPMS fixture in each lane; a single same-source Spock test is not binary qualification.
Record exact commands, versions, results, fixture linkage/module mode, any existing skips, and exact CI/Sonar head when
slices ship. Every new ignore/pending condition needs an actionable issue/removal reason; do not skip a failed guard case.

RM-4 owns changes to [Completed-Object-Support](../user/Completed-Object-Support.md),
[Model-Phases](../user/Model-Phases.md), [Layer3](../user/Layer3.md),
[Builder-First-Migration](../user/Builder-First-Migration.md), [Templates](../user/Templates.md), and relevant
[Migration](../user/Migration.md)/[_Sidebar](../user/_Sidebar.md) navigation if a new page/section requires it.
Explain valid empty lookup versus state errors, actual composition versus explicit Owner values, LINK preservation,
Template/copy recapture, and immutable descriptor versus live facade lifetime. Add a neutral owning-field annotation
example at AUTO_LINK and a completed candidate example; use no framework provider/default selection marker.
Update [CHANGES](../../CHANGES.md) only for delivered behavior in the then-current release section. No current user page,
changelog, release curation, or GitHub milestone is changed by this planning PR.

## Risks, unresolved decisions, and issue-to-slice mapping

| Risk/decision | Bound or approval required | Evidence owner |
| --- | --- | --- |
| D1 bounded implementation/details approval | Accepted facade/shared descriptor/Optional and consumer-policy separation stay fixed; finalize generics/equality/errors and authorize bounded work | Approved; RM-0/RM-1 separately authorized |
| D2 Template retention | Approved internal accepted-definition retention and recipient recapture with public rejection; direct inspection remains separately gated | Approved for bounded RM-2 |
| D3 historical stream compatibility | Approved same-version-only ordinary/Template scope; no named historical streams or guessed UID | Approved for bounded RM-2 |
| D4 scheduling and release | Approved for 4.1; RM-1 delivered separately, later slices need explicit authorization | Approved; Hive owns release reconciliation |
| D5 copied-container ambiguity | RM-0 inventories bypasses/conflicts; decision gates RM-2 copy qualification, not direct-field RM-1; broader ownership repair needs separate approval | Maintainer before RM-2 |
| D6 read-only lifetime revision | Approve active same-session + phase >15 + authoritative source, removing unsealed/<40 checks; include normal sealing and LINK wrappers, preserve post-exit/abort rejection | Approved; RM-1 guard implemented |
| Claim transfer/late attachment versus traversal | Derive from authoritative claim, update atomically; do not cache a first traversal alias | RM-1/RM-2 |
| Reflection/classloader/JPMS | Direct annotation metadata needs no value access; prove private fields and Class identity, return for narrow decision on access gaps | RM-3 |
| Completed graph retention | Store declaration only; serialization must not pull in old owner graph or construction state | RM-1/RM-2 |
| Scope drift to LinkTo or query projection | Keep provider enumeration, default/null/ambiguity policy, Cluster Builder projections and subtype setter improvements separate | All slices |

| Issue/decision | Relationship to this plan |
| --- | --- |
| #856 / ADR 0027 | RM-1 is implemented; RM-2–RM-4 remain gated; parent remains open for maintainer reconciliation |
| #390 / ADR 0006 | Extend completed Structure without weakening its object/companion boundary |
| #431 / ADR 0004 | Preserve active-session composition, Template/copy protocol and serialization scope |
| #391 / ADRs 0014/0015 | Reuse module and generated-linkage acceptance; no new public internal access |
| #799 / ADR 0023 | Existing runtime annotation vocabulary remains inspectable; no constraint-engine redesign |
| #180 / proposed ADR 0026 | Generic specialization independent, not a prerequisite for declaration identity |
| #853 | Completed declarative target compatibility independent; characterization is not a fix |
| #850/#855 | Sealed mutation hardening independent; support adds only read-state rejection |

## Planning-PR validation and delivery boundary

This branch contains only this plan and ADR 0027, based on refreshed master. Validation checks local Markdown destinations
and anchors, balanced fenced code blocks, the frozen-constraint/acceptance mapping, diff whitespace, and the docs-only
file scope. Local review covers both repository standards and the maintainer assignment. The planning PR records exact
validation results; previous investigation test counts are never reported as new tests on this branch. Groovy lanes are
not required for planning documents that affect no compilation, runtime, or test execution, per
[testing policy](../agents/testing.md).

Tracker impact: **Related: #856**, no closing syntax; no issue edits, release targeting, or curation status changes.
The original assignment authorized this draft decision/plan PR; the follow-up authorizes updating it with an additive
review-fix commit and one consolidated response despite unresolved implementation approvals. It authorizes no implementation,
ready-for-review transition, merge, or new issue. After publication report the
PR link, D1–D6, source/evidence base and validation to Hive, request delivery/archive reconciliation, and retain `(PR:open)`
until merge is verified. A completed planning assignment is not an archive-safe repository delivery.
