# ADR 0027 implementation plan: owning Schema relationship metadata

Date: 2026-10-08

Status: Reviewable plan; implementation not authorized

Decision: [ADR 0027](../adr/0027-owning-relationship-metadata.md)

Tracking issue: [#856](https://github.com/klum-dsl/klum-ast/issues/856), open and untargeted

## Authority, scope, and evidence provenance

The maintainer accepts the structural-metadata investigation direction and freezes
`KlumBuilderSupport.of(builder).getStructure()` alongside `KlumObjectSupport.of(model).getStructure()`, a shared immutable
`KlumSchemaRelationship`, Optional absence, and an operation-time active-session/after-OWNER guard. Structure is the only
Builder support capability now. This plan schedules no implementation and invents no LinkTo selection algorithm.
ADR decisions D1–D5 distinguish remaining approvals from frozen direction.

Input is the completed local investigation on `codex/issue-856-link-binding-investigation`:

- `763e122dbc45b735a13170d741220c6eb0d90938`: 685-line `LinkBindingInvestigationTest`, 39 characterization/fallback cases.
- `1715eab6`: investigation report and candidate ADR 0027. This ADR supersedes that candidate spelling and conditional plan.
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

For every public Builder ownership query, perform these checks in order before any ownership read:

1. Validate supported Builder receiver; reject null, completed Model, unsupported marker implementation, and sealed Builder.
2. Require membership in the current thread's active Construction session; reject closed/aborted, foreign-session,
   off-thread, and Template-definition state.
3. Require an actual current phase number `15 < phase < 40`; construction with no executable phase does not qualify.
4. Read the current composition declaration and answer the relationship/annotation query, including valid absence.

`of`/`getStructure` may acquire a view early for a supported Builder; no metadata operation is authorized by acquisition.
Every metadata request rechecks; even a previously successful lookup cannot authorize a later lookup. Return a detached
immutable descriptor whose annotation queries remain usable after construction. Completed Structure has no live phase
requirement, but still requires a completed Object. Do not retrofit existing completed validation/path queries to Optional.

| Phase/state at request time | Owning relationship and annotation result |
| --- | --- |
| Construction/PostCreate/PostApply, no executable phase | Reject before ownership read |
| APPLY_LATER(1), AUTO_CREATE(10), OWNER(15), custom phases ≤15 | Reject, including root and missing annotation |
| Custom phase 16, AUTO_LINK(20), DEFAULT(25), POST_TREE(30), custom phases 16–39 | Read in active unsealed same-session Builder; Optional.empty for genuine absence |
| INSTANTIATE(40), custom phases ≥40, VALIDATE(50), COMPLETE(100) | Reject Builder view even if the Construction session remains active |
| Sealed completed LINK wrapper | Reject Builder view; query the completed Object through KlumObjectSupport |
| No session, aborted session, other session/thread, Template-definition Builder | Reject regardless of apparent phase/claim |
| Completed Object at VALIDATE or outside lifecycle | Read stored identity; no Builder/session requirement |
| Descriptor captured from a successful lookup | Read immutable Schema metadata at any time |

Diagnostics name operation, receiver type, phase/state and remedy; unsafe owner reads cannot be used to decorate an
early rejection. Use a counting/failing internal declaration-reader seam in guard tests, or an equivalent observable
fixture, to prove rejected calls never reach ownership lookup. A root returning empty in a premature phase is a failure.

## Tracer-bullet slices and reasoned commits

All slices are conditional on the applicable D1–D5 gates. Execute in order: **RM-0 → RM-1 → RM-2 → RM-3 → RM-4**. No slice adds a target
selection algorithm, changes #856's milestone, or independently completes the issue's wider investigation criteria.

### RM-0 — pin approved descriptors and retention qualification

Approve ADR gates and establish one smallest end-to-end fixture: runtime-retained field annotation, an inherited owning
field without redeclaration, and a completed child with no Owner. Record the exact API signatures in this plan after D1.
Audit the serialization form/computed UIDs of both companions before adding fields. If D3 names historical versions,
capture reproducible old-byte fixtures and define a supported reader migration before proceeding.
Characterize ordinary Model and Template list/map copy insertion separately from generated setters, including ADD/REPLACE
and map merge/set-if-missing strategies, repeated aliases within a field and across distinct fields, and OPTIONAL_LINK
mixed entries. Identify which entries actually reach materialization/traversal without a claim. Use this evidence to
settle D5 and select a capture hook that preserves the copy/aggregation contract; do not assume OWNER installs claims.

This is a bounded non-shipping characterization/proof step. One commit carries source-backed contract/serialization
characterization and its evidence; do not commit tests that assume absent production APIs. Prefer existing
KlumObjectSupportSpec, OptionalLinkRelationshipTest, TemplatesSpec, and JpmsPackageBoundaryTest seams. No public probe
class or guessed UID becomes production design. Exit when capture at claim/transfer and allocation works for ordinary
and Template paths without changing generated client interfaces; settle D5 from copy-path evidence and return for a
decision if the proof contradicts D1–D3.

Acceptance: A01–A04 and A24 existing-path characterization, A16 UID/old-fixture audit, A20 binary fixture feasibility. Bring over
only relevant investigation cases if needed, preserving @Issue and the original observed-contract meaning.

### RM-1 — completed metadata vertical slice

Add KlumSchemaRelationship, the typed private declaration record, exact claim capture/update, materialization transfer,
and the two completed Structure queries. Include ordinary direct/list/map/inherited/no-Owner and LINK/OPTIONAL_LINK cases
in the same passing commit. A test must compare declaring Class/name and annotation values before/after materialization,
not infer success solely from a path. Do not expose annotation proxies as serializable retained state.

One reasoned commit: **Expose completed owning Schema declarations without ownership backreferences (#856)**. Pair the
API, capture/transfer, and green end-to-end tests. If private generated linkage must change, document it under ADR 0015
and include its test in this commit; do not change generated public signatures. Exit when VALIDATE sees the exact
declaration and aliases/cycles do not change it.

Acceptance: A01–A09 and A17; regression coverage includes existing KlumObjectSupportSpec and OptionalLinkRelationshipTest.

### RM-2 — qualify templates, copies, import, and serialization

Extend typed internal retention to Template companion creation/import paths under approved D2, while preserving
KlumObjectSupport's direct Template rejection. Ensure Template application and
all supported copy sources create recipient-edge metadata, while existing linked targets preserve their original records.
Qualify ordinary/Template Java serialization, subtrees serialized without an owner backreference, and compatible absent
records under D3. Use the approved compatibility policy; do not add broad old-stream guarantees.

First reasoned commit: **Retain declaration identity across Template and copy materialization (#856)** with graph-wide
Template identity, fresh recipient metadata, ordinary value-only and same-session Builder copy tests. Second commit if
separable: **Qualify relationship metadata across import and serialization (#856)** with passing serialization/Jackson
boundary tests and any required narrow runtime fixes. Keep failing-test/fix pairs together. No Jackson wire metadata.

Acceptance: A12–A18 and A24. Use TemplatesSpec/TemplateRecipeStateTest, and existing Jackson KlumJacksonImporterSpec,
ConfigurationReplaySpec and LinkIdentitySpec seams rather than building a second import engine.

### RM-3 — active Builder support vertical slice

Add KlumBuilderSupport with only getStructure, and a metadata-only nested Structure whose public inputs/results mention
only supported types. Internally validate the marker's genuine runtime implementation; no client can gain authority by
implementing KlumBuilder. Compose the guard once and apply it independently to both query operations. Read the same
Schema descriptor as completed Structure without materializing anything. Keep existing internal phase traversal separate.

One reasoned commit: **Expose Builder relationship metadata with per-request lifecycle checks (#856)**. Pair the facade
and guard with a table-driven phase/state matrix, root/missing-annotation negative cases, early acquisition then late
success, success then expiration, multiple requests across phases, custom phase 16/15/40 boundaries, same-phase OWNER
actions, and failure-before-read proof. Include callback-on-inherited-receiver AUTO_LINK success before DEFAULT transport.
Test retained views after successful completion and exceptions, sealed wrappers, foreign sessions/threads, and
Template-definition Builders; no phase-only check is sufficient.

Acceptance: A01–A11 and A19. Exit when declaration equivalence across live/completed views holds, guards run even for
absence/cached results, and a retained descriptor remains safe while a retained live view rejects.

### RM-4 — qualify public consumers and document delivery

Compile real runtime/Schema artifacts first, then consumers against binaries in separate source sets/projects. Use Java 17,
dynamic Groovy, and @CompileStatic Groovy; annotations and inherited Schema bases must be separately compiled, including
a private field and a base declaration in a different package. Compile a Java extension/callback fixture accepting the
public generated Builder interface and using KlumBuilderSupport at an eligible phase; do not compile against the hidden
Builder implementation. Check generic return signatures, Optional types, and unchanged Foo_DSL interfaces/source mirrors.

Run classpath consumers in G3/G4/G5, and named Schema/annotation/consumer modules in G4/G5 using the existing
JpmsPackageBoundaryTest fixture. Compare module descriptors and ensure the existing public runtime export suffices.
Schema annotation resolution must respect its classloader; include separate classloaders with same-named declarations
to prevent a name-only metadata cache. No new broad exports/opens, command-line add-opens workaround, or runtime dependency
from the annotations artifact. Any actual access gap returns for a narrow architectural decision.

First reasoned commit: **Qualify Java and Groovy relationship support across binary and module boundaries (#856)**,
including any narrow fixes plus the passing consumer tests. Second commit: **Document approved relationship Structure
support and its lifetime (#856)** with a metadata-only documentary example, current user pages, migration/navigation,
CHANGES and acceptance evidence. Do not publish a consuming resolver as framework behavior. The plan/ADR status changes
only to reflect approved and actually delivered slices.

Acceptance: A20–A23 plus the full matrix regression. Review local code/commit sequence, run the final Groovy compatibility
lanes once after focused G3 work, inspect CI/Sonar at the exact PR head, and satisfy the normal publication boundary.

## Executable acceptance matrix

The expected results below are future requirements, not current passing API tests. Proposed semantics depend on the named
ADR gates. New test classes use the Test suffix and @Issue("856") or the assigned child issue; documentary tests use
@Tag("documentary") and @See to the current user-documentation source. No pending test is added by this planning PR.

| ID | Fixture/input | Expected result/assertion | Slice |
| --- | --- | --- | --- |
| A01 | Direct owned child with Binding; eligible callback and completed lookup | Same exact Schema declaring Class/member and annotation values from both views; no callback transport property | RM-1/RM-3 |
| A02 | Inherited owning field on concrete owner, child with inherited callback, no field redeclaration | Original base Schema declaration retained; callback can read its owning binding in AUTO_LINK | RM-0/RM-1/RM-3 |
| A03 | Child has no Owner; alternate fixtures with several/transitive/converted Owner values | Actual owning declaration independent of Owner values; existing getSingleOwner ambiguity unchanged | RM-0/RM-1/RM-3 |
| A04 | Owned list/set/map values including repeats and map keys requiring escaping | Containing field annotation; no index/key in declaration identity; no new unique-path contract | RM-0/RM-1 |
| A05 | Eligible root, unattached same-session Builder, missing annotation, metadata-absent completed Object | Optional.empty; absence does not imply historical object is a root | RM-1/RM-3 |
| A06 | LINK alias to same-root owned child and external completed child/root | Original declaration/empty root result preserved; no receiver-edge adoption or target copy/mutation | RM-1 |
| A07 | OPTIONAL_LINK single/list/map mixing fresh owned, claimed and completed entries | Only owned claim captures receiver declaration; aggregate entries keep originals; existing traversal filtering | RM-1 |
| A08 | Permitted self-OPTIONAL_LINK claim transfer; rejected cross-owner/cross-session composition | Final accepted declaring field wins; rejected attachment does not overwrite metadata | RM-1 |
| A09 | Self/cyclic links and two references to one target; VALIDATE callback | Graph identity retained; descriptor readable by VALIDATE; no Builder/session retained in completed state | RM-1 |
| A10 | Early-created facade; OWNER/≤15/custom 15 requests on child/root/missing annotation, then phase 16/20 | Each early call throws before declaration read; subsequent eligible call succeeds; no cached eligibility | RM-3 |
| A11 | Retained view at 40/50/100, after completion/abort, foreign session/thread, sealed wrapper, Template Builder | State/session/phase-specific rejection; active session at 50 does not permit Builder query | RM-3 |
| A12 | Marked Template root and owned nodes, including value-only imported Template; D2 policy | Internal definition-edge record for owned nodes/root absence; public completed facade still rejects Templates; no public live Builder access without session | RM-2 |
| A13 | Template applied under a different recipient field; same template applied twice | Fresh graphs each expose recipient-edge metadata, not source edge; recipe and linked identities unchanged | RM-2 |
| A14 | copyFrom ordinary Model/Map/same-session Builder; nested copy and standalone root copy | Recipient claims recaptured; no old owner adoption; standalone root empty; existing sealed/cross-session rejection | RM-2 |
| A15 | Ordinary/Template Java serialization; linked cycles; subtree without Owner | Same-version declaration/annotation and graph identities restored; no owner graph pulled in by metadata; no Field/Optional/session/Builder state serialized | RM-2 |
| A16 | Compatible absent record; invalid retained declaration; specific historical stream only if D3 authorizes | Absent yields empty; explicit bad record fails with Schema/member; UID compatibility proved for each claimed version | RM-0/RM-2 |
| A17 | Exact/missing annotation and null Class; retained descriptor after Builder expires | Typed Optional values, empty for absence, null rejected; immutable descriptor usable without live session | RM-1 |
| A18 | Jackson root/in-session/apply import and reference targets; export ordinary Objects | New owned edges recorded, references retain originals, no wire field; Template export rejection and importer lifecycle unchanged | RM-2 |
| A19 | Child attached after OWNER through existing late-subtree initialization | Accepted claim recorded immediately; eligible callback sees field; no duplicate lifecycle or guessed traversal edge | RM-3 |
| A20 | Java 17 and static/dynamic G3/G4/G5 consumers of separately compiled inherited Schema/annotations | Public facades only; no unchecked internal casts; exact generic/Optional descriptors and concrete annotation identity | RM-4 |
| A21 | G4/G5 named modules; private field annotation; separate annotation module; same names in separate classloaders | Public export sufficient, existing schema opens baseline; no extra broad opens/exports; cache respects Class identity | RM-4 |
| A22 | Inspect KlumBuilder, Foo_DSL signatures, Model properties, AnnoDocimal mirrors, wire output | Marker remains zero-operation; no added generated interface/mirror/Model/wire property; no internal type in public signatures | RM-4 |
| A23 | Documentary Builder callback and completed annotation read; release guidance | Example matches executable test; @Issue/@Tag/@See and issue/docs traceability; feature only documented as delivered when green | RM-4 |
| A24 | Ordinary Model/Template list/map copies through direct CopyHandler insertion; overwrite strategies; repeated recipe aliases and OPTIONAL_LINK entries | Characterize current claims/materialization in RM-0; after D5, capture the approved recipient declaration without arbitrary traversal tie-breaking, target duplication, or aggregation changes | RM-0/RM-2 |

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
| D1 descriptors/initial helper scope and implementation authorization | Review exact methods, generics, equality, exception categories, sealed/Template Builder policy; frozen facade/Optional/guard direction remains | Maintainer before RM-0/RM-1/RM-3 |
| D2 Template retention | Approve internal definition-edge retention and recipient recapture with public rejection; any direct inspection requires separate support/gating decision | Maintainer before RM-2 |
| D3 historical stream compatibility | No arbitrary promise; audit computed UIDs, name any supported old versions, prove them with fixtures | Maintainer/RM-0/RM-2 |
| D4 scheduling and release | Keep #856 untargeted; choose child issue/work order only after design acceptance | Maintainer/Hive |
| D5 copied-container ambiguity | Decide declaration capture for claim-bypassing insertion and conflicting owned-field aliases using RM-0 evidence; broader ownership repair needs separate approval | Maintainer/RM-0 before RM-2 |
| Claim transfer/late attachment versus traversal | Derive from authoritative claim, update atomically; do not cache a first traversal alias | RM-1/RM-3 |
| Reflection/classloader/JPMS | Direct annotation metadata needs no value access; prove private fields and Class identity, return for narrow decision on access gaps | RM-4 |
| Completed graph retention | Store declaration only; serialization must not pull in old owner graph or construction state | RM-1/RM-2 |
| Scope drift to LinkTo or query projection | Keep provider enumeration, default/null/ambiguity policy, Cluster Builder projections and subtype setter improvements separate | All slices |

| Issue/decision | Relationship to this plan |
| --- | --- |
| #856 / ADR 0027 | RM-0–RM-4 are proposed metadata delivery slices; parent remains open for maintainer reconciliation |
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
The explicit assignment authorizes pushing this decision/plan branch and opening a draft PR despite unresolved design
approvals. It authorizes no implementation, ready-for-review transition, merge, or new issue. After publication report the
PR link, D1–D5, source/evidence base and validation to Hive, request delivery/archive reconciliation, and retain `(PR:open)`
until merge is verified. A completed planning assignment is not an archive-safe repository delivery.
