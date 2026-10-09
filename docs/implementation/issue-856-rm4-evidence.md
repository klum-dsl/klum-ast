# #856 RM-4: owning Schema metadata acceptance

Date: 2026-10-09 (Europe/Berlin)

Status: Delivered behavior qualified; release-documentation corrections and delivery evidence belong to this RM-4 branch.
Hive retains parent-issue, release and archive reconciliation after delivery.

Authority: the RM-4 assignment, [#856](https://github.com/klum-dsl/klum-ast/issues/856),
[ADR 0027](../adr/0027-owning-relationship-metadata.md), its
[implementation plan](adr-0027-owning-relationship-metadata.md), and the
[revised D5 decision](https://github.com/klum-dsl/klum-ast/issues/856#issuecomment-6067212507).

Base: current master `e2b56aa9cb9042d3e6ded5a24c624609fe6c913a`.
Branch: `codex/issue-856-rm4-acceptance`.
Worktree: `/Users/stephan/.codex/worktrees/ea2b/klum-ast`.
Master was refreshed again on 2026-10-09 and remains at this base. The following merges are verified ancestors:

| Slice | Merged PR | Merge commit | Final PR revision |
| --- | --- | --- | --- |
| RM-1 | [#861](https://github.com/klum-dsl/klum-ast/pull/861) | `4b4c85c00bfbeb79add37ddd4e97b3589f89510e` | `106134d09e445e221f286b56d0eef6ad8c2e2155` |
| Bounded RM-2 | [#862](https://github.com/klum-dsl/klum-ast/pull/862) | `df433708830a57cf562775b8a3eb8fb2b94a63b8` | `fc2d4b6e77afd3564fdea4f02965a817e78f0934` |
| RM-3 | [#863](https://github.com/klum-dsl/klum-ast/pull/863) | `276c749c328d28f1d7d9e6432b6d7655133890b6` | `f7119b827bc8a976c3e4a7aa3b88c6635ca4ef49` |
| Revised D5 | [#864](https://github.com/klum-dsl/klum-ast/pull/864) | `e2b56aa9cb9042d3e6ded5a24c624609fe6c913a` | `77a862c30641c5d4151f4fe2b94be7311bfc0eb1` |

Each final PR revision has successful build, JUnit, SonarCloud and SonarCloud Code Analysis checks. Earlier slice reports
retain their historical delivery boundaries; this report is the consolidated acceptance record, rather than retroactively
rewriting their original evidence.

## Accepted contract and decisions

Both `KlumBuilderSupport.of(builder).getStructure()` and `KlumObjectSupport.of(model).getStructure()` expose owning
relationship/annotation queries through immutable `KlumSchemaRelationship`, with declaring Class identity plus field name.
These queries expose only authoritative accepted declaration metadata, independently of Owner values and graph location.
They add no operation to the Builder marker, generated Builder interfaces, Model properties or wire format.

`Optional.empty()` covers roots, missing annotations and valid unavailable/non-authoritative declarations, especially
direct copied-container placements. A unique occurrence does not establish authority. Accepted copied aliases remain
valid and preserve identity; no traversal/Map/Owner tie-breaker, contextual descriptor, normalization or new claim is added.
An existing authoritative claim remains readable through its aliases. Invalid explicit records remain Schema errors.

Every live Builder ownership query rechecks the current thread's active same-session context and phase strictly after
OWNER(15), including requests that return empty. Normal allocation/sealing and LINK wrappers remain eligible in that
context; wrappers describe the completed target's original declaration. Completion/abort, foreign threads and sessions
reject live queries. Detached immutable descriptors remain usable; this supplies no late Builder traversal, mutation or
Model-extraction operation. Later Model callbacks consume completed Models.

Marked Templates and their owned nodes retain public Object-support rejection. Accepted definition declarations are
retained internally and recipient claims recapture declarations during application/single-field copy. Same-version Java
serialization with compatible available Schema definitions preserves declaration and graph/recipe identity, without
retaining an old owner or construction state. Readable absent metadata is empty; no historical stream, Schema evolution
or arbitrary historical Schema/runtime ABI compatibility is promised.

Normal LINK/OPTIONAL_LINK aggregation preserves exact completed targets and original declarations; fresh same-session
OPTIONAL_LINK attachments can be composition. OPTIONAL_LINK recipe copying is a different existing route: unclaimed
copied container entries can materialize as null. Revised D5 qualifies and preserves that behavior; fixing it is outside
this metadata capability. No CopyHandler redesign or rejected-graph policy is required for acceptance.

KlumAST supplies no provider-selection policy. Binding/default/ambiguity/fallback decisions in the examples belong to the
consuming Schema, which explicitly handles Optional absence and uses existing typed relationship operations.

| Decision | Final disposition |
| --- | --- |
| D1 | Approved exact generic, equality, absence/error and descriptor contract; implemented in RM-1 |
| D2 | Approved internal accepted Template-definition retention and recipient recapture; public rejection preserved |
| D3 | Approved same-version-only ordinary/Template serialization; UID/field inventory and absence/corruption qualified |
| D4 | Approved placement in 4.1; Hive retains release/tracker reconciliation |
| D5 | Revised decision approved; authoritative claims or truthful absence, preserving copy identity/aliases/materialization |
| D6 | Approved per-operation same-session/phase >15 lifetime, including normal sealing and LINK wrappers |

## A01–A26 acceptance evidence

All rows below pass in the merged artifact set. D3 and revised D5 define the accepted limits rather than leaving the
superseded historical-stream/alias-rejection proposals as missing implementation. Test files are under
`klum-ast/src/test/groovy/com/blackbuild/klum/ast/`, except the Jackson fixture under
`klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/`.

| Criteria | Executable evidence and bounded result |
| --- | --- |
| A01/A02/A03/A05/A17 | `OwningSchemaRelationshipTest`, `BuilderRelationshipLifetimeTest`, `OwningRelationshipTracerTest`: direct/inherited declarations, Owner independence, Optional/null/error contract, Class/member identity and detached descriptors |
| A04/A06/A07/A08/A09/A19 | `RelationshipCompositionTest` plus lifetime/tracer: List/Set/Map containment, repeated LINK identity, per-entry OPTIONAL_LINK, permitted claim transfer and rejection preservation, cyclic validation and late attachment |
| A10/A11/A26 | `BuilderRelationshipLifetimeTest`: both query methods, root/missing annotations, ≤15/16/later phases, ordered before/after phase-40 actions, normal sealing, original LINK records, exit/abort/thread/session rejection |
| A12/A13/A14 | `RelationshipTemplateCopyTest`, `CopyCompositionCharacterizationTest`: accepted definition retention, public Template rejection, recipient recapture/fresh applications, single-field/nested/standalone copies and claimless container absence |
| A15/A16 | `RelationshipSerializationTest` and RM-1/RM-2 representation audits: same-version graphs/recipes and detached subtrees, readable absence versus explicit corruption, no metadata retention of owner/Builder/session/Field/Optional |
| A18 | Jackson `RelationshipImportTest`: root/in-session/apply imports, value-only Templates, recipient claims, reference identity/cycles, Template export rejection and no metadata wire field |
| A20/A21/A22 | `RelationshipMetadataConsumerTest`, classloader checks in `OwningSchemaRelationshipTest`, tracer and Jackson fixtures: Java 17/static/dynamic G3/G4/G5 JAR consumers, G4/G5 named modules, exact Optional/annotation identity, private-field lookup without extra opens, generated marker/Model/mirror/signature and wire boundaries |
| A23/A25 | Three documentary entry points below plus the consumer tracer: aligned Java/Groovy guidance, inherited callback and completed annotation reads, exact selected target/explicit override, consumer-owned policy and absent-authority handling |
| A24 | `CopyCompositionCharacterizationTest` (45 cases), `CopyOwnershipConsumerDocumentaryTest` (2 cases): Model/Template/Map/Builder donors, List/Set/Map recipients, overwrite/merge modes, claims/aliases/LINK/OPTIONAL_LINK, preserved identity/lifecycle/materialization and truthful absence |

Slice-level details and representation inventories remain in [RM-1](issue-856-rm1-evidence.md),
[RM-2](issue-856-rm2-evidence.md), [RM-3](issue-856-rm3-evidence.md) and
[D5](issue-856-d5-conflict-evidence.md).

## Documentary and release reconciliation

Existing tests provide all required documentary evidence; no new feature, test, suppression or production change is needed:

- `OwningRelationshipTracerTest#'inherited AUTO_LINK selects a completed provider through consumer-owned annotations'`
  ↔ [Owning Schema declarations](../user/Completed-Object-Support.md#owning-schema-declarations).
- `RelationshipTemplateCopyTest#'a Template child is recaptured under the recipient field on each application'`
  ↔ [Templates, copies, and imports](../user/Completed-Object-Support.md#templates-copies-and-imports).
- `CopyOwnershipConsumerDocumentaryTest#'inherited AUTO_LINK skips providers without authoritative declarations and preserves selected identity'`
  ↔ [Copied providers and absent authority](../user/Completed-Object-Support.md#copied-providers-and-absent-authority).

Each test carries `@Issue('856')`, `@Tag('documentary')` and the matching absolute `@See` source link; each user example
names its executable test immediately before its Groovy block. Existing `_Sidebar.md` navigation reaches all affected
pages; no page is added, renamed or removed. Templates and Builder-first migration already describe recipient recapture,
Template rejection and same-version persistence accurately, so they need no further edits.

RM-4 corrects Migration's stale copied-alias-repair gate, CHANGES' initial-tracer/pending-gate wording, current ADR/plan and
CONTEXT status, and the owning-declaration introduction. It makes the preserved OPTIONAL_LINK copy limitation explicit
in Completed Object Support and Copy Strategies, and links lifecycle/Layer 3 guidance to the supported seam. No new
provider policy, ownership/copy semantic change, API, Template inspection or compatibility promise is introduced.
README and the 4.0 curation inventory do not assert a conflicting #856 contract; this assigned 4.1 acceptance does not
change 4.0 scope or undertake a global release sweep.

## Final qualification and delivery boundary

On clean merged base `e2b56aa9cb9042d3e6ded5a24c624609fe6c913a`, before documentation edits:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 check --console=plain
```

**BUILD SUCCESSFUL in 9m 4s**, 130 actionable tasks (104 executed, 26 up-to-date), with Java 17.0.3,
Gradle 8.14.4 and Groovy 3.0.25 / 4.0.32 / 5.0.6 with matching Spock 2.4 lanes. The full run includes all documentary/
metadata tests, Java/binary/JPMS consumers, licenses, lane isolation and documentation-renderer checks. No edits occurred
during this build. The initial sandbox attempt failed before Gradle execution on its cache lock; the authorized run above
completed normally. This is an environment boundary, not a test failure.

| Module | G3 tests / skips | G4 tests / skips | G5 tests / skips |
| --- | ---: | ---: | ---: |
| AST | 1608 / 15 | 1608 / 15 | 1608 / 15 |
| Runtime | 73 / 1 | 73 / 1 | 73 / 1 |
| Jackson | 77 / 0 | 75 / 0 | 75 / 0 |
| Bean validation | 10 / 0 | 10 / 0 | 10 / 0 |
| Test support | 7 / 0 | 6 / 0 | 6 / 0 |
| Annotations | 20 / 0 | Not configured | Not configured |
| Gradle plugin | 151 / 0 | Not configured | Not configured |

All reports have zero failures/errors. The focused acceptance subset inside these full runs is **93 AST + 6 Jackson
cases per lane**, zero failures/errors/skips, including all three documentary entry points. Existing full-suite skips are
unchanged. Final branch changes are prose only; production, test sources/resources and build inputs remain identical to
this qualified base. No repeat of the Groovy suites or root check is required for those documentation-only corrections.

Committed-document rendering/crawl passed on `033970bb` in 20s using `renderLocalDocumentation` with
`-PdocumentationVersion=4.1.0-tracer`; documentary-link/fence/diff and existing navigation checks also pass. Standards
review identified premature delivery-result wording, corrected here. Final review disposition and exact-head CI/Sonar
checks must be recorded in the RM-4 PR and handoff during delivery. Any PR remains draft for maintainer/Hive review; no
ready-for-review, merge, issue closure or archive action is authorized by this report.

## Hive recommendation and remaining action

Recommend accepting the delivered owning-Schema metadata capability for 4.1. No feature implementation or semantic
decision remains under the accepted D1–D6 contract. Preserve the OPTIONAL_LINK recipe-copy limitation as an explicitly
qualified existing boundary, not a #856 blocker or a claim that it was repaired.

After the RM-4 documentation PR is merged and that merge is verified, Hive can reconcile and close #856 for its accepted
metadata scope. Before closure, replace the superseded LinkTo-selection title and D1-open body with the delivered facade/
Optional/lifetime contract, approved D1–D6 dispositions, merged PRs and this acceptance evidence. Add the three exact
documentary test/section links above so issue → test → user documentation traceability is complete. A suitable issue title
is **Read authoritative owning Schema relationship metadata through Structure**. Do not imply a KlumAST provider-selection
algorithm or leave superseded copied-alias rejection/repair work as an accepted requirement.

Tracker impact of this branch: **Related: #856**; parent state, labels, milestone and 4.0 curation are unchanged. Outstanding
delivery actions are documentation merge verification and Hive's tracker/release/archive reconciliation. This worker does
not close the issue, mark its own task `(arch)` or archive itself.
