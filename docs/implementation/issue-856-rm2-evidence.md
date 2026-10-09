# #856 RM-2: bounded Template, copy, serialization and import qualification

Date: 2026-10-08

Status: Approved bounded RM-2 implementation and local qualification complete; draft delivery and CI are recorded by the attached PR/handoff.

Authority: the RM-2 assignment, the maintainer's explicit approval of both recommendations in
[the D2/D3 decision record](issue-856-rm2-decisions.md), [ADR 0027](../adr/0027-owning-relationship-metadata.md),
and its [implementation plan](adr-0027-owning-relationship-metadata.md).

Base: `4b4c85c00bfbeb79add37ddd4e97b3589f89510e`, verified master merge of
[PR #861](https://github.com/klum-dsl/klum-ast/pull/861). Master was refreshed before implementation without a newer commit.
Branch: `codex/issue-856-rm2-composition-policy`.
Isolated worktree: `/Users/stephan/.codex/worktrees/da93/klum-ast`.
Executable implementation/review-fix tip: `8e9e01bd`; subsequent status/evidence edits are documentation only.

## Delivered policy and implementation

D1, D4=4.1 and D6 remain fixed. D2 approves retaining existing accepted Template-definition declarations internally,
with fresh recipient claim capture during application/copy. D3 approves Java serialization within the same KlumAST
version with compatible available Schema definitions; no historical streams or Schema evolution are promised.
D5 copied-container/alias/OPTIONAL_LINK copy repairs are excluded by the assignment and remain an acceptance gap.

The only production change is passing `InternalKlumBuilder.owningRelationship` into the existing internal
`KlumTemplateProxy` constructor and retaining that nullable declaration there. Its payload remains declaring Schema
Class and member name. The generated `$createCompanion` descriptor, common companion interface, ModelState, compiler,
generated/public Builder interfaces, and RM-1 public facades are unchanged. No new runtime lookup or selection algorithm
is installed. Public Template inspection, Template relationship-value rejection, and normal Jackson Template export
rejection remain intact. Template-definition Builders still have no valid session for public live metadata reads.

Fresh recipient attachments already capture their actual declaration; no donor metadata is copied into an owning claim.
Single-field Model/Map/Template copies and same-session Builder merges are qualified at those existing accepted seams.
Standalone copy roots remain empty. Completed LINK identities and their original declarations are preserved. Retained
declaration state contains no owner object, Builder, session, path, Field, Optional, annotation proxy, callback or facade.
Template recipe capture/replay and #847/#855 mutation preflight are unchanged.

## Executable acceptance

All new cases carry class-level `@Issue('856')`; all new executable classes use the Test suffix. No ignored/pending case
or new suppression is introduced. Test-local internal inspection qualifies the otherwise private Template record; it
does not add a public Template inspection seam.

| Fixture | New cases | Boundary exercised |
| --- | ---: | --- |
| RelationshipCompositionTest | 9 | Inherited normal List/Set/Map attachments through live and sealed reads, escaped map keys, repeated aggregate identities, mixed OPTIONAL_LINK entries, self-claim transfer, same/different-owner and active foreign-session rejection without record changes, cyclic LINK validation, late container attachment |
| RelationshipTemplateCopyTest | 7 | Direct/inherited definition records and root absence, normal definition containers, two fresh recipient applications under a different field, recipe replay, public rejection, Model/Template/Map single-field donors, standalone child-copy root absence, same-session nested merge and closed-source rejection |
| RelationshipSerializationTest | 6 | Ordinary cyclic and repeated LINK graph identity, detached ordinary and Template subtrees excluding old owner instances, Template definition identity/recipe replay and cyclic external ordinary LINK retention, readable missing metadata versus corrupt record, serialized field/UID inventory |
| RelationshipImportTest (Jackson) | 6 | Managed root inherited single/List/Set/Map declarations and no wire metadata, in-session Builder plus apply-to-Builder placement, value-only Template definition retention without callbacks, Template public/export rejection, fresh recipient application, both reference directions/cycles and external completed codec targets |

Total: **28 new cases**, zero failures/errors/skips in each G3/G4/G5 lane. The final focused run also re-runs all 22 RM-1
cases: **44 AST + 6 Jackson cases per lane**, all passing without skips.

The Template/copy happy path is documentary, with `@Tag('documentary')` and `@See` linked to
[Completed Object Support](../user/Completed-Object-Support.md#templates-copies-and-imports).
Templates, Builder-first migration, CHANGES and ADR/plan status describe exactly this approved subset. Existing user
navigation already reaches the modified pages. Hive should reconcile the parent issue's documentary references to
`RelationshipTemplateCopyTest#'a Template child is recaptured under the recipient field on each application'` and that
documentation section; this worker does not update or close the parent issue.

Acceptance mapping: A04/A06–A09 normal attachment/aggregation qualification; A12/A13 accepted Template definitions and
recipient application; A14 single-field/nested-merge copy subset; A15 same-version ordinary/Template graphs and payload
isolation; A16 otherwise-readable absence/corruption and current representation audit; A18 managed imports; A19 late
container attachment; extended A10/A11/A26 plus unchanged RM-1 lifetime regressions. A24/D5 copied-container insertion,
conflicting aliases and OPTIONAL_LINK copy materialization are deliberately not completed. RM-3 full binary/JPMS and
RM-4 final feature/release acceptance are not started. This is not complete RM-2 or parent #856 acceptance.

## Serialization representation

Java 17 `ObjectStreamClass` audit on the delivered production classes:

| Type | UID | Serialized fields |
| --- | ---: | --- |
| KlumModelProxy | -1472569276039395355 (computed, unchanged from RM-1) | breadcrumbPath, executedValidators, metadata, model, modelPath, owningRelationship |
| KlumTemplateProxy | -5705584554211443869 (computed, changed) | breadcrumbPath, modelPath, object, owningRelationship, recipeState |
| InternalKlumBuilder.ModelState | 2464886360491951900 (computed, unchanged from RM-1) | breadcrumbPath, metadata, modelPath, owningRelationship |
| SchemaRelationshipDeclaration | 1 (declared, unchanged) | declaringClass, name |

The Template UID was `332494386423258445` in RM-0/RM-1. No old UID is pinned. Tests inspect fields and exercise actual
serialization; they do not assert a computed UID as a permanent contract. A test stream rejects Builder/session/Field/
Optional/annotation objects and, for isolated subtree cases, actual old-owner instances. Declaring Class descriptors
are allowed: declaration identity must remain resolvable. Normal explicit relationship/Owner/recipe references retain
their existing serialization semantics; metadata isolation does not erase such references.

The absent-record fixture is produced/read using the same current representation, with test-local metadata removal.
It proves Optional absence when readable, not historical compatibility. Explicit corrupt declarations still throw the
approved Schema/member error. Regenerate older serialized Models/Templates from source configuration/recipes on upgrade.

## Validation and local review

Environment: Java 17.0.3, Gradle 8.14.4, Groovy 3.0.25 / 4.0.32 / 5.0.6, matching Spock 2.4 lanes.

Repository-wide `check` passed on stable committed tip `d102aa91` before the two test-only review fixes. It includes
the 26 initial new cases and all configured lanes, license/lane-isolation checks, plugin validation, documentation
renderer checks and the remaining aggregate checks:

| Module | G3 tests / skips | G4 tests / skips | G5 tests / skips | Failures/errors |
| --- | ---: | ---: | ---: | --- |
| klum-ast | 1557 / 15 | 1557 / 15 | 1557 / 15 | 0 / 0 |
| klum-ast-runtime | 73 / 1 | 73 / 1 | 73 / 1 | 0 / 0 |
| klum-ast-jackson | 77 / 0 | 75 / 0 | 75 / 0 | 0 / 0 |
| klum-ast-bean-validation | 10 / 0 | 10 / 0 | 10 / 0 | 0 / 0 |
| klum-ast-test-support | 7 / 0 | 6 / 0 | 6 / 0 | 0 / 0 |

All 151 Gradle-plugin G3 tests passed without skips. After review, the serialization audit uses explicit when/then blocks,
and two new cases qualify different-owner and active foreign-session rejection. The final focused G3/G4/G5 runs verify
all 50 RM-1/RM-2 metadata cases per lane plus AST license/lane isolation on the executable sources now committed at
`8e9e01bd`. Production code did not change after the broad check. The complete AST suite is not reported as having
been re-run with those two added cases; its broad count remains 1557. Documentation-only evidence/status changes receive
Markdown/diff checks and require no further Groovy lanes.

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 check --console=plain
# Final focused run selects all three RM-1 AST fixtures, the three RM-2 AST fixtures,
# and RelationshipImportTest for each of test/groovy4Tests/groovy5Tests.
# It also runs :klum-ast:licenseTest and :klum-ast:verifyTestLaneIsolation.
git diff 4b4c85c0...HEAD --check
```

Parallel local Standards and Spec reviews used fixed base `4b4c85c0` through `d102aa91`. Standards identified the audit
block formatting; Spec requested the two rejection cases. Both were addressed in `8e9e01bd` and rechecked before
publication. Initial fixture/setup failures were corrected without changing ownership, import or compilation policy.
Commit-history review retains the decision brief, normal composition qualification, Template/serialization behavior,
import/documentary qualification and review fix as reasoned steps. No rewrite is needed. Changed-document fences and
local Markdown destinations, documentary links and whitespace are checked before the final evidence commit.

## Delivery and remaining gates

Partial slice relationship: **Related: #856**. No issue closure, release targeting, curation update, historical fixture
promise or D5 policy approval accompanies this branch. Parent tracker/acceptance reconciliation remains with Hive.
Git transport dry-run and GitHub CLI repository capability audits returned `authorized`; no GitHub App channel is used.
The assignment permits draft delivery after this accepted bounded scope is complete, validated and reviewed. CI/Sonar
state must be inspected on the actual PR revision; it is not inferred from local passes. No ready-for-review or merge
action is authorized. Retain `(PR:open)` after draft creation; Hive owns delivery/archive reconciliation.
