# ADR 0028 implementation plan: external lifecycle participants

Status: Accepted architecture with refined core/optional plan; signatures and qualification provisional. LP-1 is implemented under subsequent explicit maintainer delegation; LP-2–LP-8 remain unqualified.
Authority: [ADR 0028](../adr/0028-annotation-driven-lifecycle-participants.md).
Issue: [#867](https://github.com/klum-dsl/klum-ast/issues/867).
Prerequisite: [#868](https://github.com/klum-dsl/klum-ast/issues/868).
Release: Conditional 4.1 candidate; do not retarget issues from this plan.

## Current behavior and affected seams

CompositionTraversal invokes a parent visitor before collecting/descent of children.
BuilderVisitingPhaseAction skips sealed/non-Builder values.
FieldPhaseTraversalCharacterizationTest proves parent mutation reaches child AutoLink and
a child supplied during the parent callback joins the same traversal.

AutoCreationPhase preserves Fields → ClusterFields → LifecycleMethods.
AutoLink resolves LinkTo then callbacks; Default applies owner/containing/type/direct defaults
then callbacks; PostTree currently executes callbacks. Preserve these algorithms while introducing
precise field-processing slots. LifecycleHelper runs methods then Closure fields and manages
current member context. ClosureHelper uses DELEGATE_FIRST and cannot be reused unchanged.

#371/PR #821 own shadowing rejection; #648 supplies public narrowing; #868 owns Model-type
discovery. Type dispatch uses traversal context, not owning relationship metadata. ADR 0008's
later phase-registration SPI is not a dependency.

Modules: runtime owns new public extension types/dispatch/helper; compiler owns declaration
checks and annotation Closure integration. Existing annotations are phase vocabulary only,
with no reverse dependency. Tests, public API inventory and user docs change when delivered.
Templates/imports retain their existing routes and require compatibility coverage.
Validation participation already uses KlumSchemaSupport/KlumValidationReporter at this baseline
(the maintainer's KlumValidationSupport capability); it is not a missing architecture/API gate.
Validate Closure strong typing and Model/Builder annotation retargeting are LP-6 reuse starting
points, not a new compiler framework.

## Core, optional capabilities and early probes

Mandatory: LP-0 prerequisite, LP-1 minimum direct-field mutator/creator vertical tracer, LP-2
composition and actual ordering contract, LP-3 four-phase integration, LP-4 FAIL/SKIP and graph/
session/Template/import checks, LP-8 errors/existing validation/public typing/Java/Groovy/JPMS/docs.
ScHelm's Application → @Binding Domain → Facts mutation case comes first.

LP-5, LP-6, LP-4 HANDLE and LP-7 container support are desirable, independently deferrable if
substantial complexity appears. Attempt straightforward compatible delivery; no public contracts
for deferred capabilities. LP-7 support-or-reject before release is mandatory, support is not.
LP-1 success alone is not release readiness. Keep one cohesive feature; separate issues/PRs only
if Hive chooses them.

Run LP-2 ordering and LP-6 Closure feasibility probes early, alongside/before LP-1 signature
freeze. Numbering expresses implementation slices, not a reason to delay feasibility assessment.
Optional probe failure cannot hold core delivery hostage.

## Thin dependency-ordered slices

Each implementation slice is a green reasoned commit with meaningful acceptance tests.
Production code and its tests travel together. Names/signatures are provisional.
New tests carry @Issue for #867 (or #868 for its own work), use Test suffix, and documentary
cases carry @Tag("documentary") and @See to current user docs. No speculative pending tests.

### LP-0 — Prerequisite and baseline (#868; D3–D4)

Deliver #868 independently: concrete polymorphic Model class, active owned and sealed LINK
Builders, Java/static Groovy, Groovy 3/4/5, public JPMS access. Keep its own lifetime contract
independent of ownership-view phase restrictions.
Characterize existing phase field order, callbacks and cluster fallback; inherited fields
retain original declaration and actual subtype receiver. Preserve shadowing rejection.

Commits: independently owned getter; separate #867 baseline characterization.
Gate: no ownership or general metadata API inferred from internal helpers.

### LP-1 — Mandatory ScHelm-style field vertical tracer (D1–D5, D8)

Implement the minimum field mutator/creator API, runtime meta-annotations/context and compiler
checks in one lifecycle phase.
Primary example: annotation on Application.domain mutates existing Domain.facts using Application
knowledge. Domain knows nothing of selection source. A second example supplies the annotated
relationship. Use only generated public contracts, Model-type support and factory narrowing.

Acceptance: direct DSL field placement; singular declaration annotation lookup; null creator
leaves unset; checked non-null Builder assignment; no completed-Model return or setter.
Resolve exact annotation generic through inheritance; reject raw/wildcard/unresolved/mismatch.
Fresh public no-arg handlers per occurrence. Java/static/dynamic Groovy consumer tests; no
reflection Field/list/path/expiry API. Reject foreign-session or inappropriate FieldType results.

Commit: real-transform tracer with public boundary, validation and focused tests together.
Review API vocabulary against consumer examples before freezing names.

### LP-2 — Mandatory composition and actual ordering contract (D2–D3)

One domain annotation combines creator and multiple mutations in same/different phases.
Creator first; mutations see supplied Builder; null skips them.
Competing direct same-phase creators (including built-in) fail; mutations and different phases
coexist. No blanket built-in/meta annotation exclusion.

Probe Java/Groovy-authored annotations, repeatable meta-annotations, separately compiled libraries/
Schemas and Groovy 3/4/5. If declaration order is reliably recovered from actual compiled
representation, document/regression-test it; otherwise explicitly state unspecified execution
order and require handler correctness independent of order.

Separate within-domain-annotation order from between-domain-annotations on one field
(unspecified unless established), and from optional type-before-field placement.
Creation-before-mutation always holds. No priorities, sorting, ordering SPI or elaborate
workaround. Unspecified mutation order is a permitted core outcome.

Commit: composition and demonstrated ordering contract with binary-lane regressions.

### LP-3 — All phases and cluster sequence (D1–D3)

Integrate AutoCreate, AutoLink, Default, PostTree at existing field enumeration slots.
Prove parent-field → child field/method/Closure sequence (type if LP-5 ships) and preserve
built-in callback order. AutoCreate may mutate an existing child to create grandchildren; AutoLink may create the
annotated target. Shapes are phase-independent. Do not promise earlier phases rerun for new children.

Exact AutoCreate trace: Fields → ClusterFields → LifecycleMethods (including Closures).
Cluster AutoCreate coexists with direct creators, sees resulting state, fills remaining nulls,
and does not rerun field participants. Preserve Default owner/containing/type precedence and
LinkTo unset behavior in builtin-only regressions.

Commits: independently green phase integrations; cluster regressions with AutoCreate.
Gate: verify existing field order rather than add sorting or a second whole-tree action.

### LP-4 — Sealed and construction-route compatibility (D6, D8)

Core FAIL rejects sealed invocation; SKIP omits it. Optional HANDLE probe covers read-only
inspection/validation. Include if a small dispatch extension suffices; defer if mutation
interception, lifecycle changes or a read-only participant framework would be required.
Publish no HANDLE API if deferred; existing immutability never changes.
Creators retain normal LINK/OPTIONAL_LINK/composition assignment checks.
Test polymorphism, alias/cycle identity, fresh handler state across fields/sessions.

Acceptance: Template application dispatches in recipient lifecycle; value-only Template creation
adds no lifecycle; imports use existing root routes; no handler/context/Builder enters completed
serialization. Validate late-created child ownership/session mechanics, not an invented phase replay.

Commit: policy/error behavior with graph/Template/import/session regressions.

### LP-5 — Optional type mutation (D1, D3–D4)

Independent and not a core/4.1 gate. Include if straightforward after field dispatch, otherwise
record complexity and defer without type-level public contracts.
Type participants run first within visited Builder, after parent-field dispatch.
Traversal supplies containing Builder/incoming field name; both null at root. Target is visited
Builder. Annotation lookup queries Schema type, not incoming relationship. No new sealed
aggregation traversal and no creating participant on types.

Acceptance: Java @Inherited base behavior, same-type subclass override, unmarked/interface
non-inheritance; standard repeatable-container inheritance characterization without plural lookup.
Do not manufacture order among different inherited domain annotation types; document unspecified
order where none is established. Within one domain annotation use LP-2’s demonstrated contract.
Inherited fields retain their original declaration and actual containing subtype.

Commit: type dispatch, placement/inheritance/root tests together.

### LP-6 — Optional Closure evaluation; early reuse probe (D7)

Probe early: DSLASTTransformation.convertValidationClosureOnSingleField, toStronglyTypedClosure,
Validate annotation Closure behavior, retargetBuilderAnnotationClosures and Model/generated
Builder mapping. Assess handler-selected Builder/context delegate typing and IntelliJ
owning-class inference; Validate is the acceptable ergonomic baseline, not perfect inference.
Reuse typed Closure mechanisms without importing Validate's assertion-specific semantics.

Implement if reuse is modest/compatible. Otherwise document limitations and defer with no partial
helper API. Qualify one Java-first helper signature taking Closure class, fixed handler-selected
delegate,
expected result class. Fresh instance; DELEGATE_ONLY; delegate also single argument.
Handler owns member choice/sentinel. No configurable owner/resolve/cache API.

Acceptance: Java Closure subclass, Groovy inline annotation Closure, typed member result,
static/dynamic delegate access, explicit argument, bad result/constructor, owner/caller fallback
failure and preserved causes. Separate Groovy 3/4/5 compilation; legacy DELEGATE_FIRST unchanged.
Generic Class bounds alone are not static typing proof. Avoid a generic execution framework,
configurable resolution strategies, caching or unrelated compiler infrastructure.

Commit: helper/compiler integration with consumer and existing Closure regressions.

### LP-7 — Independent collections/maps exploration and release decision

Core direct-field implementation cannot depend on this speculative slice.
Investigate container versus element invocation, null/empty, index/key context, mixed sealed/owned
entries, mutation during enumeration, aliases and occurrence paths. Produce evidence-backed
support-or-reject decision before release; unsupported shapes receive clear diagnostics.
No production collection/map contract is authorized by this plan.

Commit: isolated exploratory fixtures/evidence; acceptance needed before any support implementation.

### LP-8 — Mandatory errors, existing validation usage, qualification and docs (D8)

Document configuration defects versus execution failures versus domain validation findings.
Use existing validation support (baseline names: KlumSchemaSupport and KlumValidationReporter).
Current-target getKlumValidation requires lifecycle instance/member context; explicit
klumValidationForObject(target) reports on the Domain Builder, with no default member unless
errorAt/issueAt supplies one. Use existing Validate.Level, suppression and fail threshold.
Verify normal materialization issue transfer. No new validation infrastructure or API.

Add concise participant guidance and one small executable @Issue("867"), documentary/@See
example in LP-8: an external mutator reports missing Facts on its explicit Domain Builder,
then asserts member location/severity and normal final reporting. Existing #626 tests already
cover lifecycle helpers, child targets and suppression; reuse those mechanisms. See refinement
evidence for the proposed body, existing restrictions and documentation boundary.
Context restoration on exceptions must preserve cause and
identify participant, handler, phase and field/type declaration.

Run affected module baseline plus Groovy 4/5, Java/static Groovy binary consumers and Groovy 4/5
named-module fixtures with standard exports/opens; no add-exports/add-reads workaround. Groovy 3
stays classpath-only. Test separate handler-module constructors and Closure access remediation.

Finalize names/signatures after usage review. Update public SPI inventory, Model-Phases and
relevant Advanced-Techniques/user pages, migration navigation/sidebar where applicable,
documentary tests and CHANGES only when feature ships. Review CI/Sonar at delivery revision.
Use Related #867 until full issue acceptance; no closing relationship from planning completion.

Commits: bounded diagnostic/validation code if needed; final documentary/release synchronization.
Review unpublished history before remote delivery; preserve reviewed commits.

## Risks and explicit gates

Names/signatures and exact phase slots need consumer/compatibility evidence. Mutation ordering
is probe-dependent with unspecified order a documented acceptable outcome. Type mutation,
Closure evaluation and HANDLE may defer independently; container support/reject is a mandatory
release decision. Validation uses existing infrastructure and is not an architectural gate.
Context invocation-only lifetime is documentation-only by decision.
No ScHelm types/policy, ownership redesign, Model mutation, Template/import API overhaul or
runtime-internal general extension seam.

The primary case maps to LP-1/LP-3; creation and multiple mutations to LP-1/LP-2; type placement
to LP-5; sealed policy/graph compatibility to LP-4; Closure members to LP-6; optional container
mechanics to LP-7; public compatibility/errors/docs/release review to LP-8.

## Validation and execution boundary

Prior investigation: Groovy 3 and Groovy 4 module suites passed (1,610 tests each,
zero failures/errors, 15 pre-existing skips); two focused Groovy 5 characterization tests passed.
These prove traversal only, not this API. This update is documentation-only: check diff and
relative links; no new Groovy execution or feature tracer implementation.
The original planning handoff did not authorize implementation, issue retargeting or self-archival.
The later LP-1 delegation and bounded implementation evidence are recorded below; architecture acceptance
does not authorize additional slices.
See [refinement evidence](evidence/issue-867-core-optional-refinement.md).

## LP-1 execution record

The maintainer subsequently authorized LP-1 only, superseding the planning-only authorization boundary
for this slice. [LP-1 evidence](issue-867-lp1-evidence.md) records the provisional API, cross-Schema reuse
mechanism and validation. Remaining LP-2–LP-8 gates, optional capabilities, issue state and release placement
are unchanged; this record does not authorize additional slices.
