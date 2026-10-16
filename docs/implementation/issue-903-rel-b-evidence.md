# #903 REL-B — protected Gradle operation prerequisites

Date: 2026-10-16. Status: local implementation; final repository validation in progress. Related: #903.
Base: `cfa5e87dd9a088d9163b36e7c6fc1f39708fc111`, current `origin/master` after merged
[REL-A PR #915](https://github.com/klum-dsl/klum-ast/pull/915).
Branch: `codex/issue-903-protected-operations`; isolated worktree, IDE-main checkout untouched.
Authority: [ADR 0030](../adr/0030-exact-sha-ci-proof-reuse.md), the
[accepted plan](issue-903-ci-proof-reuse.md) and canonical
[#903](https://github.com/klum-dsl/klum-ast/issues/903).

## Implemented boundary

The [root build](../../build.gradle) attaches successful dependency ancestry to actual protected
operations, through `verifyProtectedReleaseAuthorization`. The prerequisite requires
`verifyReleaseVersion` (including the existing stage authorization),
`verifyVersionedDocumentationRenderer` and the existing root `check`, then requires the complete
`publishCompleteKlumAstProduct` entry. Its lack of outputs makes it execute on each selected invocation.
It introduces no local proof receipt or substitute for GitHub/environment authority.

Protected operations are Sonatype initialization, destination-bound Maven uploads, staging close,
staging release and Portal publication, plus actual protected signing. Staging close retains the
complete Maven-product dependency. Release now depends on successful close; Portal publication
depends on successful close/release. Thus failed prerequisites/predecessors block their descendants
under `--continue`; sibling selection or `mustRunAfter` cannot claim successful prerequisite execution.

The inspected production task bindings are:

| Operation | Actual pinned implementation | Wiring |
| --- | --- | --- |
| Sonatype initialization | Nexus Publish 1.3.0 `InitializeNexusStagingRepository` | Direct authorization dependency, before its task action |
| Protected Maven uploads | Gradle 8.14.4 `PublishToMavenRepository` | Destination-sensitive dependency on every concrete upload |
| Staging close/release | Nexus Publish 1.3.0 `CloseNexusStagingRepository` / `ReleaseNexusStagingRepository` | Direct authorization dependencies; successful upload/close predecessors |
| Plugin Portal | Plugin Publish 1.2.1 `PublishTask`, `:klum-ast-gradle-plugin:publishPlugins` | Direct authorization and successful Sonatype close/release dependencies |
| Signing | Gradle 8.14.4 `Sign` tasks shared with Maven Local | Conditional successful authorization ancestry when an actual protected signatory is configured |

The locally cached source archives for the pinned Nexus and Portal plugins were inspected. Nexus
registers one actual initialization/close/release action per configured destination, gives every
concrete upload an initialization dependency, and otherwise uses ordering constraints for several
staging predecessors. Its Sonatype upload URL does not exist until initialization. Portal registers
one actual `publishPlugins` action and shares publication-signing tasks. No plugin replacement,
publication hierarchy or generic publishing framework was introduced.

Sonatype is protected before URL materialization and remains protected if redirected to a file URL.
Other Maven tasks use the actual configured destination rather than local-looking names/properties.
Only authority-free file URLs are local, with case-insensitive scheme handling; network file URLs
remain protected. Both the unsafe network-file exemption and the uppercase-local rejection were
observed as failing execution regressions before correcting the destination predicate.
A real signatory in a project serving protected destinations or the Portal, or using the production
`signingKey` interface, is treated as protected even for a local request of the shared signature.
Unsigned Maven Local and file-repository publication select no release authorization. A disposable
signatory in an isolated local-only fixture without production key configuration also remains usable.
The [base signing convention](../../buildSrc/src/main/groovy/klum-ast.base-conventions.gradle) now
requires signatures for the complete protected product, allowing unsigned ordinary local publication.

REL-A's workflow preflight remains a prerequisite of pending documentation and the protected job.
Its post-approval recheck remains immediately before the existing Gradle command. Gradle consumes
no new GitHub proof property and performs no API calls. The CI/workflow/helper files remain
byte-identical to the base. The existing root `check` selection is preserved without selecting a
repeated full matrix for release dispatch. Separate RC/final environments, pending documentation,
remote version availability, public resolve-back, REL-2 and independent consumers remain intact.

## Test-first acceptance

[ProtectedPublicationTest](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/ProtectedPublicationTest.groovy)
has class-level `@Issue('903')` and runs in the ordinary Gradle-plugin test suite. Its isolated
TestKit build extracts the actual release block and signing convention. It uses Gradle's real
Maven/signing task types and substitutes staging/Portal tasks at their inspected operation seam.
Every irreversible action is a marker writer. Synthetic signatories contain no keys and throw if
real signing is attempted. Maven/Portal/staging clients cannot run; no external plugins or dependencies
are resolved by the fixture. It runs `--offline` with an isolated shared TestKit directory and a
restricted synthetic environment containing no scanner, signing or publication credentials.

The initial staging test failed because direct initialization executed its marker without any
prerequisite; adding the real dependency made it pass. Expanded execution cases cover:

- Direct initialization, signing, concrete Maven upload, existing/alternative aggregates, close,
  release, combined close/release and Portal publication.
- Independently failed authorization, version, documentation and root verification, each in serial,
  parallel, `--continue`, and parallel-plus-continue execution, with reordered low-level/complete requests.
- Successful final and RC complete-product entries; every protected operation checks successful
  prerequisite markers and writes its own operation marker.
- Unsigned Maven Local, file-repository publication, compilation, JAR/POM preparation, ordinary
  verification and disposable local-only signing without release properties or authorization.
- A remote destination behind a development repository name, redirected Sonatype, protected-key
  requests through shared local signatures, and mixed local/protected requests.
- Failed uploads or staging close preventing later staging release and Portal markers under `--continue`.

The [production graph probe](../../klum-ast-gradle-plugin/src/test/resources/release-publication/graph.init.gradle)
separately inspects the real configured root build. It first confirms ordinary unsigned development
entries have no authorization ancestor, then installs a keyless, nonfunctional synthetic signatory
to exercise the actual signing dependency branch. It verifies transitive version, documentation,
root verification and authorization ancestry for each actual protected operation. It rejects any
selection containing another task, so no publication or signing action can execute.

The existing documentation contract also checks actual configured direct dependencies at protected
operations and staging/Portal predecessors. Its model projection is a normal Gradle task input,
preserving the existing configuration-cache-safe documentation contract.

## Validation and review

Completed on Java 17 / pinned Gradle 8.14.4:

- Focused Gradle-plugin `ProtectedPublicationTest`: 45 passing cases, zero failures/skips, including
  both corrected URI boundary regressions.
- Read-only offline production probe: 27 protected operations (11 signing, 11 Maven uploads and
  five staging/Portal entries); 33 unsigned development entries without release authorization.
- `verifyVersionedDocumentationRenderer`: passed, including real graph assertions and module Javadocs.
- Scoped actionlint 1.7.7 with shellcheck for REL-1 and CI, helper Node syntax and diff checks: passed.
  An all-workflow lint also reported pre-existing shellcheck style finding SC2129 in unchanged
  `verify-public-release.yml:171`; no unrelated workflow edit was made.

Full repository Groovy 3/4/5, isolation, coverage/license/documentation and affected-module checks,
separate annotation-artifact qualification, final Markdown targets, standards/spec and history review
are in progress. Their completed outcomes belong in the final handoff and this record before delivery.

The initial root check completed the core Groovy 3/4/5 suites, then failed 11 existing mirror integration
cases: the parent had fixed a clean development version, while this worker's concurrent documentation
edits caused the nested fixture publisher to infer the `.uncommitted` version. The temporary repository
therefore lacked the parent's exact BOM coordinate. This was a validation-process error; no mirror
test/product change is required. Final verification uses a committed checkout kept stable throughout
parent and nested builds.

## Delivery boundary

Stop at `(ready:PR)`: local commits only; no push, PR, release dispatch, publication, secrets access,
issue mutation or environment/protection change. Final-head remote CI/Sonar are unavailable until a
later authorized publication and must be inspected then; local checks cannot claim remote results.
No real protected-environment or registry operation is rehearsed.

Tracker impact is proposed `Related: #903`; Hive owns the final issue relationship, release-gate
curation and full acceptance. Current user DSL/API/migration pages are unaffected. Historical
investigation and Sonar feasibility reports remain unchanged. Request Hive reconciliation and retain
the isolated branch/worktree for review; the worker does not mark itself archive-safe.

No per-run runtime-evidence IDs/location or store authorization were supplied with this assignment;
no external runtime-evidence store was created. This repository evidence record describes only the
assigned source change, safe validation and delivery boundary.
