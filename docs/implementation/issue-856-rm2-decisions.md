# #856 RM-2: Template and serialization decision brief

Date: 2026-10-08

Status: D2/D3 explicitly approved by the maintainer on 2026-10-08; bounded implementation delivered locally.
Validation and remaining gates: [RM-2 evidence](issue-856-rm2-evidence.md).

Authority: the RM-2 assignment, [#856](https://github.com/klum-dsl/klum-ast/issues/856),
[ADR 0027](../adr/0027-owning-relationship-metadata.md), and its
[implementation plan](adr-0027-owning-relationship-metadata.md).
D1, D4=4.1, and D6 are already approved and are not reopened.

## Evidence and checkout

- Current master/base: `4b4c85c00bfbeb79add37ddd4e97b3589f89510e`, the verified merge of
  [PR #861](https://github.com/klum-dsl/klum-ast/pull/861).
- Dedicated branch: `codex/issue-856-rm2-composition-policy`.
- Isolated worktree: `/Users/stephan/.codex/worktrees/da93/klum-ast`.
- RM-1 delivery/limits: [RM-1 evidence](issue-856-rm1-evidence.md).
- Retained RM-0 report and 33 characterization cases: local commit
  `6c7d2efab4b10bbc6ee92d3932c8731a6c46cd5a`, branch `codex/issue-856-rm0-proof`.
  The report was read through Git; neither its tests nor its baseline assertions were cherry-picked.

The original brief inspected source at the base above and changed only this planning file. The later approval authorized
the bounded implementation and tests, recorded separately in the evidence report.

## D2: internally retain definition declarations and recapture recipient declarations

Approved decision: retain a nullable `SchemaRelationshipDeclaration` in the internal Template companion
for each existing accepted composition claim. The root has no owning declaration. Preserve the original declaring Schema
Class/member for inherited fields. Applying or copying a Template creates fresh Builders and captures their accepted
recipient edges; source metadata must never be copied as an instruction to adopt the source owner.

The current `InternalKlumBuilder.claimComposition` already records the declaration for accepted Template attachments.
`$createCompanion` passes that record to an ordinary Model companion through ModelState, but constructs the distinct
`KlumTemplateProxy` without it. Passing the existing record into that internal constructor is the proposed narrow seam;
the generated `$createCompanion` descriptor and public/generated contracts need not change.

The payload remains declaring Class plus member name. Retention adds no Owner instance, Builder, session, path, reflection
Field, Optional, annotation proxy, callback, or facade reference. Existing Template recipe closure rules remain in force.
The new field may change the Template companion's computed serialVersionUID; D3 approves same-version persistence only.

Schema/Model syntax exercised by `RelationshipTemplateCopyTest#'a Template child is recaptured under the recipient field on each application'`:

```groovy
@DSL class Node { String value }
@DSL class Definition { Node child }
@DSL class Recipient { Node actual }

def recipe = Definition.Create.Template.With {
    child { value 'configured' }
}
def result = Recipient.Create.With {
    actual { copyFrom recipe.child }
}
```

The recipe child's internal definition declaration would be `Definition.child`. The fresh ordinary result's child must
report `Recipient.actual` through `KlumObjectSupport`; two applications create separate recipient graphs. A standalone
copy root has no owning relationship, even if its donor was a child. Existing ordinary LINK targets preserve identity and
their original declaration.

Public Template rejection remains intact for marked roots and owned nodes: completed-object support rejects them,
Template-definition Builders have no valid Construction session for a public metadata read, Templates remain invalid
relationship values including LINK, and normal Jackson export still rejects Templates. No public Template inspection
operation or guard bypass follows from internal retention. Value-only Template imports must retain accepted definition
edges without relying on OWNER callbacks, because they run no ordinary lifecycle.

Rejected alternative during the decision: defer internal Template retention. This avoids changing the Template companion's serialized form now, while
existing accepted recipient claims can still supply ordinary result metadata. It leaves A12 and the Template portion of
A15 unresolved and requires the ADR/plan to state that deferral. Direct public Template inspection is outside either option.

## D3: same-version serialized-stream promise

Approved decision: qualify Java serialization within the same KlumAST version, with the required Schema
classes available and compatible. This follows [ADR 0004](../adr/0004-asbuilder-composition-protocol.md). It adds no Schema
evolution promise, arbitrary cross-version compatibility, or JSON/YAML persistence format.

| Choice | Qualification and cost | Risk and commitment |
| --- | --- | --- |
| Same-version only (recommended) | Round-trip ordinary graphs, linked cycles, a subtree without an Owner, and supported Template recipes; audit actual serialized fields/UIDs and retained-state exclusions | Bounded to the delivered representation; older streams may fail before metadata lookup and need recreation |
| Specifically named historical streams | Maintainer names producer versions/revisions and ordinary/Template fixtures; generate immutable bytes using those artifacts and Schemas, then qualify the full old graph against the new runtime | Higher cost: changed companion UIDs, generated Schema forms, recipes and field descriptors may need compatibility code; each accepted fixture becomes a maintained promise |

RM-0 reported computed UIDs `6027968170821577189` for KlumModelProxy and `332494386423258445` for KlumTemplateProxy.
RM-1 reported computed Model companion UID `-1472569276039395355`; its Template companion was unchanged. Those are prior
audits, not new RM-2 measurements. The new declaration record alone declares UID `1`. RM-1 intentionally did not pin the
old Model UID or promise historical loading. Adding D2 retention can change the Template UID too. Pinning an old UID is
not sufficient evidence that an old graph or recipe can be read correctly.

An absent declaration yields Optional.empty only when the object/stream is otherwise readable. A same-version fixture
with absent internal metadata can test that behavior; it cannot prove that an older stream is compatible. A retained but
unresolvable explicit declaration remains a Schema error. Serialization must retain graph identity and declarations
without pulling an old owner graph in through the metadata payload. Normal relationship/Owner/recipe references retain
their existing serialization behavior; payload isolation is not a promise to erase those references.

If historical compatibility is selected, implementation waits until the exact producer/fixture set and its acceptance
contract are named. No such historical stream has been identified or approved in this assignment.

## D5 constraint: copied-container and alias repairs remain excluded

The assignment explicitly excludes D5 repairs. RM-0 characterized the following paths, and current-master source still
uses the same direct insertion and per-copy IdentityHashMap:

| Route | Evidence | RM-2 boundary |
| --- | --- | --- |
| Single replacement | `replaceValue` calls `setInstanceAttribute`, which normalizes the new child and accepts a composition claim | Qualify fresh recipient declarations for ordinary Model, Map, Template, and same-session Builder donors |
| Nested merge into an already claimed child | `copyNested` retains that child's accepted placement | Qualify the existing declaration without adopting donor metadata |
| List/set insertion and map new/replaced entries | `addCollectionValues`, `addMapValues`, `addMissingMapValues`, and new-key `mergeMapValues` branches insert directly | No capture/normalization repair and no new declaration guarantee for bypassed placements |
| Same recipe repeated within/across containers | One IdentityHashMap reuses a Builder; a single-field placement may be its only claim despite further aliases | No first/last traversal winner, duplication, conflict-rejection policy, or claim repair |
| Ordinary LINK copy | Target returned unchanged | Preserve exact target identity and original declaration |
| Copied OPTIONAL_LINK containers | RM-0 found unclaimed rehydrated entries excluded from traversal and materialized as null; mixed aliases can follow another claim | Record the qualification limit; do not reinterpret these inputs as composition or repair their identity/materialization |

Normal generated list/set/map construction and managed imports are separate routes and remain candidates for metadata
qualification when they use accepted attachment. They must not be conflated with CopyHandler insertion. Tests for bounded
copy cases must avoid the excluded aliases/bypasses and must not enshrine the null-entry defect as a desired contract.

The original full RM-2 matrix includes D5-dependent coverage. Completing this bounded assignment cannot be reported as
full RM-2/A24 or feature-release acceptance; copied-container/alias qualification remains open for a later explicit decision.
If a permitted case requires such a repair, stop that case with concrete evidence and retain the limitation.

## Approved bounded implementation

1. Qualify accepted direct/list/set/map attachments, LINK/OPTIONAL_LINK mixed entries, repeated aggregate identities,
   claim transfer/rejection, cycles, and late attachment through both state-specific views. Reuse the approved D6 guard;
   preserve #847/#855 sealed-Builder mutation behavior.
2. Under approved D2, extend only internal Template companion transfer, and qualify definition claims, public
   rejection, fresh recipient fields, repeated applications, standalone-root absence, and the permitted copy-source routes.
3. Under the chosen D3 promise, qualify same-version graph/declaration/annotation identity, detached subtrees, recipe replay,
   metadata absence/corruption, serialized field/UID audits, and exclusion of construction state from the new payload.
   Historical fixtures are included only if specifically named and approved.
4. Qualify Jackson root, active-session Builder, apply-to-Builder, reference, and value-only Template inputs through existing
   importer seams. Capture new owned declarations, preserve linked originals, and add no companion metadata to export.

Each permitted behavior step needs focused Spock coverage with `@Issue("856")`, green reasoned commits, and relevant
Groovy 3/4/5 qualification. Synchronize the ADR/implementation status and directly affected current user/migration/release
guidance with exactly the approved subset; RM-4's overall release acceptance remains pending. Review before draft delivery.
Preserve RM-1 generic/Optional/error contracts and consumer-owned Link selection. No RM-3 binary/JPMS expansion, RM-4
acceptance claim, LinkTo selection algorithm, ownership overhaul, or arbitrary historical compatibility is authorized.

Tracker impact: partial slice, `Related: #856`; no issue closure, targeting change, curation update, or D5 approval.
Push/draft PR only after the explicitly accepted bounded scope is complete, validated, and reviewed.

## Decision record

| Gate | Current disposition | Required next input |
| --- | --- | --- |
| D1 | Approved | None; preserve RM-1 descriptors/equality/errors |
| D2 | Approved | Retain existing accepted definition declarations internally and recapture recipient edges |
| D3 | Approved | Same-version only; no historical producer/fixture promise |
| D4 | Approved, 4.1 | None; no new release-readiness claim |
| D5 | Repair excluded by assignment; broader policy unresolved | No repair in this task; retain the acceptance gap |
| D6 | Approved | None; preserve per-operation active-session and phase >15 reads |

The maintainer explicitly followed both recommendations and instructed continuation. This authorizes the bounded
implementation above, preserving the D5 exclusion. Hive owns final delivery/archive reconciliation.

Decision-brief validation: four local Markdown destinations exist, code fences are balanced, and staged whitespace and
file-scope checks pass. Groovy lanes are not required for this planning-only change. Master was fetched successfully at the recorded base and refreshed successfully before implementation; no new master
commit was observed at that refresh. Recheck it before delivery reconciliation.
