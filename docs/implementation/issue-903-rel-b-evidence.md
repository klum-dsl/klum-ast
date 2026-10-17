# #903 REL-B — protected Gradle operation prerequisites

Date: 2026-10-16. Status: implementation validated; focused signing review complete in draft
[PR #916](https://github.com/klum-dsl/klum-ast/pull/916); final-head remote checks pending. Related: #903.
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
A real signatory in a project configured for protected destinations or the Portal, or with the production
`signingKey` property, is treated as protected even for a local request of the shared signature.
This deliberately includes independent disposable signers in the same project; the focused review below
qualifies that restriction and its rationale.
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

## Focused signing-classification review

The maintainer requested a narrow follow-up to PR #916. The original 45 REL-B cases remain intact;
five focused cases extend the same TestKit fixture. Production classification is unchanged.

| Scenario | Evidence and chosen behavior |
| --- | --- |
| Production `signingKey` interface, direct signing or Maven Local | Two new cases use a keyless interface sentinel and a real Gradle publication-signing task. Both reject before any signature/local publication marker, even with only local repositories. No key is loaded or parsed. |
| Signing task shared by Maven Local and Sonatype | Existing direct/shared-local/protected publication cases stay guarded, including authorized complete-product success. |
| Detached file-signing task sharing the publication signatory | One new case reuses the exact synthetic signatory object from the protected publication signer. Direct invocation stays guarded without relying on publication-task naming. |
| Independent disposable signer, Sonatype configured | One new case has no publications and signs only a fixture payload. Adding the Sonatype repository alone causes release prerequisites to be required. This confirms conservative overguarding. |
| Independent disposable signer, Plugin Publish plugin applied | One new case applies the actual pinned Plugin Publish 1.2.1 implementation from the production build's classpath, with only a local Maven repository and no production-key property. The detached payload signer is guarded. A graph assertion rejects selection of the real Portal client. |
| Local-only publication/signing without a production key | Existing unsigned Maven Local/file publication and isolated disposable-signing cases remain unguarded. |
| Mixed local/protected graph and failed prerequisites | Existing parallel/continue cases permit isolated disposable signing while blocking every protected signature/action after failed authorization, version, docs or verification. |

The initial minimized probe expected the independent Sonatype-project signer to run locally. It failed
at `verifyReleaseVersion` with the missing release-version prerequisite, before the signing marker.
The command was `:klum-ast-gradle-plugin:test --tests '*ProtectedPublicationTest.independent disposable signing*'`.
The final characterization asserts that deliberate rejection. All fixture signing actions remain marker
writers; their synthetic signatories throw if real cryptography is attempted. TestKit remains offline
with a restricted credential-free environment. The added plugin classpath is a declared test input,
not a new fixture dependency resolution or production signing configuration.

Inspection used the pinned Gradle 8.14.4 source distribution (`Sign`, `Signature`, `SigningExtension`,
`Signatory`) and the pinned Plugin Publish 1.2.1 source archive (`PublishPlugin`). `SigningExtension.sign`
returns the tasks it creates, but `Sign` retains signatures rather than a public collection of publication
consumers. Its `sign` overloads also accept files, tasks and configurations. Gradle attaches derived
signature artifacts through `PublicationInternal`, and `Signature.getSource()` is package-private.
The Portal plugin creates/reuses publication signers and attaches their task dependencies; that does
not identify arbitrary detached file signers or their downstream consumers.

Publication membership alone would miss detached tasks sharing a protected signatory. Comparing the
default signatory by object identity covers only that object: Gradle permits per-task signatories, and
separate instances need not represent different keys. Key IDs/names do not prove disposable provenance.
File comparison alone does not establish that a signature cannot later feed a publication. A complete
consumer analysis or a new signing-policy abstraction would exceed this follow-up's scope.

Decision: retain the current conservative rule. A non-null signatory in a protected-capable project,
or a project with the production `signingKey` property, requires successful release prerequisites.
The independent disposable-signing restriction is an intentional tradeoff, not an unresolved defect.
Use an isolated local-only project for disposable development signing; unsigned local publication
remains available in protected-capable projects. No names, bypass flags or caller-selected graph
exemptions are introduced. All accepted REL-B gates and release semantics remain unchanged.

Follow-up validation on Java 17 / Gradle 8.14.4 passed all 50 REL-B cases (the original 45 plus five
new cases), then the complete Gradle-plugin module check: 359 tests, zero failures/errors/skips.
`verifyVersionedDocumentationRenderer`, the module license checks, changed documentation links and
diff checks passed. The unchanged read-only production probe again qualified 27 protected operations
(11 signing, 11 Maven uploads and five staging/Portal entries) and 33 unsigned development entries.
Manual scope/standards review confirmed that the root release wiring, signing convention, production
probe and all workflows are byte-identical to the reviewed revision. The follow-up changes only focused
tests, their declared Portal-plugin test input and necessary documentation. The previously validated
full Groovy 3/4/5 production matrix remains recorded below; this retained-behavior follow-up uses the
requested focused/module/documentation checks rather than another local core matrix run. Current-head
remote CI/SonarCloud will be inspected after the additive review commit is pushed.

## Validation and review

Completed on Java 17 / pinned Gradle 8.14.4:

- Focused Gradle-plugin `ProtectedPublicationTest`: 45 passing cases, zero failures/skips, including
  both corrected URI boundary regressions.
- Read-only offline production probe: 27 protected operations (11 signing, 11 Maven uploads and
  five staging/Portal entries); 33 unsigned development entries without release authorization.
- `verifyVersionedDocumentationRenderer`: passed, including real graph assertions and module Javadocs.
- The actual renderer also passed with keyless synthetic signatories configured on all production
  signing tasks. Its graph selected no signing, publication or authorization task, proving that the
  input projection does not introduce a release cycle or require authorization for ordinary docs.
- The final clean aggregate passed all 354 Gradle-plugin cases and 15 buildSrc cases, the complete
  core Groovy 3/4/5 suites, coverage, lane isolation, license/documentation checks and all three
  explicit artifact qualification lanes. The new tests have zero failures/skips; pre-existing core
  skips remain unchanged. An earlier aggregate caught the new `.gradle` resource's header opener:
  the pinned plugin defaults to `JAVADOC_STYLE` for that extension. The resource now uses the
  required `/**` opener, with no configuration, exclusion, extension or file-type change.
- Scoped actionlint 1.7.7 with shellcheck for REL-1 and CI, helper Node syntax and diff checks: passed.
  An all-workflow lint also reported pre-existing shellcheck style finding SC2129 in unchanged
  `verify-public-release.yml:171`; no unrelated workflow edit was made.

Local Markdown targets and standards/spec review passed. All new tests use the normal CI suite,
`Test` naming, `@Issue('903')`, imported simple type names and the required license text. Review found
and corrected the URI classification boundary and the resource's header format. There are no remaining
local review findings. The four implementation/documentation reasoning steps and final compliance
correction were reviewed against the fixed base; no reviewed commits were rewritten or unrelated
source changes included. Review was manual under the explicit prohibition on child workers.

The final acceptance command, run from a clean committed tree before handoff, is:

```sh
./gradlew check buildSrc:check \
  :klum-ast:annotationPropagationArtifactsGroovy3 \
  :klum-ast:annotationPropagationArtifactsGroovy4 \
  :klum-ast:annotationPropagationArtifactsGroovy5 --no-parallel --max-workers=2
```

This aggregate succeeded at `aca3959f336da30d8609c3cb0e7aadc352cb7aa1` in 15 minutes 20 seconds
(140 tasks). The committed checkout remained stable throughout parent and nested builds. The
production ancestry probe also passed at that tip. The subsequent publication follow-up updates
only this evidence record; executable sources and test inputs remain at the validated revision.

The mandatory overengineering review found no new proof authority, publishing framework, custom
receipt/manifest, report inventory, retention store or Sonar integration. GitHub/environment authority
and native Gradle dependency failure propagation retain their distinct responsibilities.

The initial root check completed the core Groovy 3/4/5 suites, then failed 11 existing mirror integration
cases: the parent had fixed a clean development version, while this worker's concurrent documentation
edits caused the nested fixture publisher to infer the `.uncommitted` version. The temporary repository
therefore lacked the parent's exact BOM coordinate. This was a validation-process error; no mirror
test/product change is required. Final verification uses a committed checkout kept stable throughout
parent and nested builds.

## Delivery boundary

The original assignment stopped at `(ready:PR)`, with local commits only. The maintainer subsequently
requested a PR, authorizing the dedicated branch push and draft PR creation. The delivery capability
audit recorded Git SSH: `authorized`; GitHub CLI: `authorized`. Before first publication, the branch
had no existing PR and the refreshed `origin/master` remained the accepted base. Final-head remote CI/Sonar must be inspected
after publication; local checks cannot claim remote results. No release dispatch, registry
publication, secrets access, issue mutation or environment/protection change is included. No real
protected-environment or registry operation is rehearsed.

Tracker impact remains `Related: #903`; Hive owns release-gate
curation and full acceptance. Current user DSL/API/migration pages are unaffected. Historical
investigation and Sonar feasibility reports remain unchanged. Request Hive reconciliation and retain
the isolated branch/worktree for review; the worker does not mark itself archive-safe.

No per-run runtime-evidence IDs/location or store authorization were supplied with this assignment;
no external runtime-evidence store was created. This repository evidence record describes only the
assigned source change, safe validation and delivery boundary.
