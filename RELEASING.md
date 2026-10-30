# Releasing KlumAST

This is the KlumAST-local procedure for release candidates (RCs) and finals. It implements
[ADR 0012](docs/adr/0012-shared-prerelease-channel-policy.md); that ADR is the authority for
the shared channel vocabulary and immutability rules. This file does not authorize a release.
Only an explicitly authorized maintainer may approve the protected `release-candidate` or
`final-release` environment and dispatch [REL-1: Publish protected release](.github/workflows/release.yml).

## Product and trust map

The release is one product at one exact version. Its public Maven coordinates are:

- `klum-ast-annotations`, `klum-ast`, `klum-ast-runtime`, `klum-ast-jackson`,
  `klum-ast-bean-validation`, `klum-ast-bom`, and `klum-ast-gradle-plugin`, all in
  `com.blackbuild.klum.ast`.

The Gradle Plugin Portal exposes three declared public plugin IDs:

- `com.blackbuild.klum-ast-schema`
- `com.blackbuild.klum-ast-model`
- `com.blackbuild.convention.groovy`

The `release-candidate` and `final-release` GitHub environments are separate credential
boundaries. Each contains only maintainer-managed `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`,
`SIGNING_KEY`, `SIGNING_PASSWORD`, `GRADLE_PUBLISH_KEY`, and `GRADLE_PUBLISH_SECRET` secrets.
The workflow maps them to Gradle only in the protected job; ordinary CI, pull requests, forks,
and resolve-back receive none of them. The publication workflow retains `contents: read` and no
persisted checkout credential; it cannot push tags or create a GitHub release record.

Maven publications are signed and staged through the configured Sonatype endpoint. Plugin
markers and plugin publication use the Gradle Plugin Portal. A maintainer creates the immutable
tag and GitHub release record only after the credential-free public proof succeeds. These systems
cannot provide one cross-registry transaction: a failure after any remote operation burns the
version rather than permitting a retry in place.

The mutable `gitPublishPush` wiki path is deliberately not part of the protected artifact
workflow. [ADR 0013](docs/adr/0013-versioned-documentation-and-javadocs.md) gives #456 the
versioned documentation/Javadoc source, hosting, URL, preview, retention, and protected Pages
stage. The delivered tracer and protected Pages stage are the documentation release gates; the
mutable wiki remains migration-stub material only and is not a release destination.

The protected release, pending-Pages, public-proof, and documentation-promotion workflows pin every external action to
a reviewed full commit SHA. Maintainers must review any pin update before authorizing its first
use; this is a release gate, not permission to substitute an arbitrary action revision during
an incident.

## Before approving an RC or final

1. Confirm an explicit human authorization for this exact stage, version, and full commit SHA
   on `master` or the matching `release/<major>.<minor>.x` maintenance line. RC and final
   approvals are separate.
2. Start from a clean, non-composite checkout of the intended source commit. The build rejects
   composite builds, and the protected workflow accepts only `master` or the exact
   `release/<major>.<minor>.x` line derived from the requested version, proves the selected SHA is
   an ancestor of that line, and rejects an existing `v<version>` tag or GitHub release.
3. Verify the expected version: an RC is `X.Y.Z-rc.N` with a new increasing `N`; a final is
   exactly `X.Y.Z`. Alpha, beta, milestone, and snapshot names are not release channels.
4. Confirm `CHANGES.md` remains under the projected final `(unreleased)` heading while RCs
   are evaluated. After accepting the last RC, only the ADR 0012 documentation-only exception
   may date the exact final heading; any substantive source, dependency, signing, publication,
   or workflow change requires the next RC.
5. Ensure all release blockers and documentation/Javadocs are ready. REL-1 must find completed
   successful ordinary CI for the exact source SHA and dispatch line, including its complete Java 17
   Groovy 3/4/5 verification and trusted SonarCloud check. It does not dispatch or repeat that matrix.
   The existing local publication prerequisites still require the protected Gradle version to equal
   the approved input before publication.
6. Confirm #456 has delivered the ADR 0013 versioned documentation/Javadoc destination and
   that its protected Pages stage has produced the exact unlisted pending snapshot and manifest
   for this stage/version/SHA before artifact publication. A final additionally requires the
   branding owner's approval of the exact Season/logo manifest captured in that snapshot. Do not
   substitute the mutable wiki. Stable, line, and preview aliases remain unchanged until this
   release's public proof passes.

## 4.x maintenance branches and promotion

Create `release/4.0.x` only when the first 4.0.x maintenance fix is accepted. Its initial
history is `v4.0.0`, the explicitly approved post-release corrections `ad306416`, PR #745 commit
`3569db4` / merge `3346f81`, and the merge result of this policy/documentation change. This
record defines the creation point; it does not create the branch or claim that the first
maintenance release has occurred.

The maintenance branch has the normal repository branch protection, author, review, and protected
release process. A 4.0.x bugfix is authored and reviewed on `release/4.0.x`, and its RC/final is
released from that branch through the unchanged protected mechanism. REL-1, pending documentation,
REL-2, documentation promotion, and recovery accept only `master` or the version-matching
`release/<major>.<minor>.x` line and retain the exact-SHA check, immutable version/tag/release
record, public proof, and protected authorization controls. They do not create branches, tags, or
release records.

Forward-promote every applicable maintenance fix to `master` through a merge pull request. Resolve
any promotion conflict only in that pull request and validate its merge result. If a safe resolution
is not possible, retain the fix as maintenance-only and record that outcome. When a change is
intentionally inapplicable to `master`, use an audited `--no-ff -s ours` alignment-merge pull
request; record the excluded commits, rationale, validation, and the maintenance-only release-note
treatment. Do not use an alignment merge to hide an unresolved conflict or a fix that should be
forward-promoted.

Maintenance-only release notes stay on the maintenance line. Do not copy them into `master`'s
release notes merely to make histories look aligned; the promotion record is the durable cross-line
explanation. This policy is tracked by [#522](https://github.com/klum-dsl/klum-ast/issues/522).

### Promotion record template

Create this concise record in the promotion pull request (or in the corresponding maintenance-only
release note when no promotion is possible):

```text
Maintenance line: release/<major>.<minor>.x
Maintenance release / source commits: <version and immutable SHAs>
Promotion PR: <URL or none>
Outcome: applied | conflict resolved | maintenance-only | alignment merge
Excluded or aligned commits: <SHAs, if any>
Rationale: <why every listed change applies, conflicts, or does not apply>
Validation: <merge-PR checks and any maintenance-line release evidence>
Maintenance-only release note: <path/heading, or n/a>
```

## Protected pending documentation stage

### Bootstrap and one-time Pages rehearsal

The permanent release payload is pre-rendered static HTML; Pages must not run Jekyll over authored
Markdown. Before enabling the release gate, an authorized maintainer may use the currently empty
Pages site for one disposable presentation rehearsal: render the final candidate tree locally,
publish those generated bytes from a clearly experimental branch, inspect representative user
pages, nested links, images, tables, code, status chrome, and all six Javadoc bases, then set Pages
source to **None** and delete the complete experimental branch. Do not use `pending/<version>/<sha>/`
for this rehearsal and do not preserve it as release evidence.

After cleanup, create a fresh orphan `gh-pages` ledger containing root `.nojekyll`, configure Pages
to deploy through GitHub Actions, and protect both the `documentation-pages-writer` writer environment
and the `documentation-pages` deployment environment. The writer environment admits `master` and
the approved version-matching maintenance lines, requires maintainer review without administrator
bypass, and holds only the per-library Pages-writer
GitHub App credentials. Its short-lived App token is the sole identity allowed to update the ledger.
The deployment environment contains no writer credential. Protect `gh-pages` against creation, update,
deletion, and non-fast-forward changes; allow only that App integration to bypass the ruleset, never a
human account. Verify these protections before setting the repository variable
`DOCUMENTATION_PAGES_READY=true`. Until then the workflow fails before rendering, branch mutation,
Pages deployment, or artifact publication. Clearing the variable disables future release documentation
stages without rewriting any retained evidence.

Credential-free local checks use only Gradle and the JDK:

```text
./gradlew verifyVersionedDocumentationRenderer

./gradlew renderLocalDocumentation
```

`renderLocalDocumentation` deletes only `build/versioned-documentation/local-review`, renders the
current full `HEAD` as `4.0.0-tracer` with tracer status and the Season 4 branding manifest, then
serves and crawls the result under the real `/klum-ast/` Pages base path. The standard
`documentationRevision`, `documentationRendererRevision`, `documentationVersion`,
`documentationStatus`, `documentationBrandingManifest`, `documentationObjectDirectory`, and
`documentationOutputDirectory` Gradle properties may override those local defaults. For safe
cleanup, an overridden output must remain below `build/versioned-documentation/`. The protected
`renderVersionedDocumentation` task continues to require every release input explicitly.

To review the same generated bytes in a browser, run `./gradlew previewLocalDocumentation`, open
the printed `http://127.0.0.1:<port>/klum-ast/` URL, and stop the task with Ctrl-C. Both local tasks
use the same defaults and safe cleanup. The automated check crawls internal pages, assets,
directory indexes, and heading fragments. Neither task contacts GitHub or publishes anything.

After validating the stage/version shape, the release workflow queries GitHub's authoritative remote
tag and release state before it calls the separately permissioned **_REL-1a: Stage pending documentation (reusable)**
workflow. The reusable workflow repeats that remote check at its own staging boundary, and the
credential-bearing publication job checks it once more immediately before it receives publication
credentials. No release decision relies on runner-local Git refs. The workflow receives the exact
`candidate` or `final` stage, version, and full source SHA; it configures and verifies that exact
protected Gradle version and proves the clean SHA is on the selected allowed release line. It renders only that revision with
status `pending`; pending chrome and the `pending/<version>/<sha>/` path
state that this is unlisted release-gate evidence, never a public RC, stable page, or alias.

The caller uses `secrets: inherit` to forward its available secret set into the reusable workflow.
The called writer job resolves the Pages-writer credentials from the protected
`documentation-pages-writer` environment instead of requiring them as caller inputs; it neither
targets a release environment nor receives publication credentials.

For **REL-2: Verify public release**, the nested promotion call must use `secrets: inherit`. GitHub's
reusable-workflow secret-context handling otherwise leaves the called job's environment expressions
empty, as RC.14 demonstrated. This does not pass the Pages-writer environment secrets through the
caller: the caller cannot name that environment, and the called `promote` job still resolves
`PAGES_WRITER_APP_ID` and `PAGES_WRITER_APP_PRIVATE_KEY` only after the
`documentation-pages-writer` approval. Keep those names only as non-empty secrets in that
environment; do not add them to repository scope or as caller inputs. If the protected credential
check reports an empty value, first verify that the unified caller retains `secrets: inherit`, then
distinguish a transient failure from a workflow correction. GitHub's **Re-run failed jobs** reuses
the original reusable-workflow revision, so it cannot test a changed caller or called workflow. If
the correction is merged before any public exact tree or promotion record exists, dispatch
**REL-2: Verify public release** once again from the original allowed source line with the same stage/version/SHA. Its required
tag and GitHub release may already exist because REL-2 validates them before its public proof; the new
dispatch creates a new proof identity internally. Never copy the old proof identity into a dispatch.

The Pages job writes the rendered static HTML, local assets, exact Javadocs, `site-manifest.json`,
and a small handoff below the protected `gh-pages` branch. It refuses an existing pending path,
commits the new tree, and reads the pushed commit back. The staged render remains available until its
`site-manifest.json` is byte-identical to the manifest read from that fetched ledger commit. Only then
does it upload that exact ledger with `actions/upload-pages-artifact` and deploy it with
`actions/deploy-pages`. Its only outputs to the artifact workflow are `stage`, `version`, `sha`, exact
path, site-manifest SHA-256, immutable `gh-pages` commit, Pages run/attempt identity, and reported
page URL. The artifact job rechecks every value before it can invoke `publishCompleteKlumAstProduct`.
The Pages jobs have no artifact credentials; the artifact job has no permission to alter the Pages tree.

For a final, the selected source revision must include
`docs/branding/final-approval.json`. That approval is bound to the exact branding-manifest path
and SHA-256 and is retained in the rendered site manifest. The current `candidate` branding
manifest is not a final approval. #456 validates and records this evidence but does not choose
the Season identity or approve it.

If a valid pending-stage identity fails after validation, the Pages workflow records only a
sanitized rejection record below `pending-rejected/<version>/<sha>/`; it contains the identity
and `rejected-pending-documentation` outcome, never command output, credentials, or telemetry.
That path is also immutable. The root ledger is append-only: correct the cause and follow ADR 0012's
next-version rule rather than repairing or overwriting a rejected or pending path. A malformed,
off-allowed-line, or already tagged request does not create a pending or rejected Pages record.

## Protected publication path

Dispatch **REL-1: Publish protected release** from `master` or the matching
`release/<major>.<minor>.x` line, select `candidate` or `final`, and enter the exact version and
full source commit SHA. The matching environment approval is the irreversible-
operation checkpoint. Unprivileged validation accepts only valid candidate/final version shapes.
A separate read-only exact-SHA CI/Sonar preflight must succeed before pending documentation or
protected release approval becomes eligible. The workflow then checks out that SHA with no
persisted GitHub credential, proves it is on the dispatch's allowed source line, and executes in
its protected job:

```text
./gradlew publishCompleteKlumAstProduct \
  -Prelease.version=<exact version> \
  -Prelease.stage=<candidate|final>
```

The protected inputs configure the exact Gradle publication version without invoking Nebula's
tag-producing `candidate` or `final` tasks. `verifyReleaseVersion` rejects a version/stage
mismatch or an absent matching protected authorization before any publication task executes. Before
the Pages stage can create an immutable snapshot, the workflow has already rejected a remotely
existing tag or GitHub release for that version; the stage and publication job repeat this check to
fail closed across queue-time races. Once #456 delivers it, its protected Pages stage independently validates the same
stage/version/source-line SHA and deploys an immutable unlisted documentation/Javadoc snapshot plus
manifest before `publishCompleteKlumAstProduct`. That task remains the sole permitted public
artifact publication entry: it publishes every Maven coordinate to one Sonatype staging
repository, closes/releases it, and then publishes every Plugin Portal marker. #488's successful
public proof is the only input that may advance #456's labelled stable, line, or preview aliases.
The protected immutable proof handoff must bind the release channel, exact version, full source SHA,
and proof-run identity; a missing or mismatched field rejects promotion. This does not make
cross-registry publication reversible or claim that a final is byte-identical to its RC.

### Exact-SHA CI and Sonar preflight

[ADR 0030](docs/adr/0030-exact-sha-ci-proof-reuse.md) defines this gate. The repository-local
[helper](.github/scripts/verify-release-ci.cjs) reads only GitHub REST APIs using
`contents: read`, `actions: read`, and `checks: read`. Its reviewed identities are repository
`40505913` (`klum-dsl/klum-ast`), CI workflow `23237467` at `.github/workflows/ci.yml`, and
SonarCloud App `12526` (`sonarqubecloud`, SonarSource). These were verified against GitHub metadata
on 2026-10-16; any change needs review of the helper, CI contract tests and this runbook.

The source must be a full 40-character SHA on the live dispatch line: `master` or the exact
version-matching maintenance branch. Proof must come from a same-repository `push` run on that
line and exact SHA. The helper paginates runs, attempt-specific jobs and all exact-SHA checks;
it selects the newest eligible run by creation time, then run ID, before considering success.
Its latest attempt and sole required `build` job must have completed successfully. Required
successful steps are `Build with Gradle`, `Qualify annotation propagation artifacts`,
`Publish Test Report`, and `Archive compatibility-lane evidence`, at their reviewed step numbers.
The failure-only `Archive test classes` upload is excluded. `ReleaseCiWorkflowTest` guards the
existing single-job command/action contract; review it together with CI changes.

Exactly one relevant SonarCloud check must be completed successfully for that full SHA and
originate from the numeric approved App. A display name cannot establish origin. Same-SHA Sonar
success is sufficient; no CI-attempt or Sonar-analysis correlation is claimed. Missing, ambiguous,
malformed, duplicate, incomplete, changed or unavailable API evidence rejects the gate. Network
failures and selected transient HTTP errors receive at most three attempts per request, with
15-second request timeouts and short bounded backoff. There is no older-green fallback,
automatic dispatch, custom proof artifact or separate retention policy.

The workflow-run API caps each filtered search at 1,000 results. That count is for this workflow,
exact SHA, event and branch, not lifetime CI history. The helper also has a local 100-page budget
(10,000 jobs or checks at 100 per page); exceeding either bound rejects completeness. These are
separate service and operational limits. GitHub's numeric repository pagination URLs are accepted
only for this repository's verified ID. The reference check-runs API searches only the 1,000 most
recent suites, so a read-only suite-count probe rejects larger references before check selection.
No truncated window or older green result is accepted as a substitute. See the
[pagination review](docs/implementation/issue-903-rel-a-evidence.md#pagination-reliability-review).

Preflight exports only run ID, attempt and Sonar check ID as ordinary job outputs. After protected
approval, the workflow runs the same helper from its approved workflow revision immediately before
Gradle publication. It rechecks the clean exact checkout, live line ancestry and all API conditions,
and compares those identifiers. Changed evidence, including a newer successful run, requires a
fresh REL-1 preflight and approval flow. A newer failed or unfinished run also rejects publication.
This is a point-in-time recheck, not an atomic lock over later GitHub changes. The helper receives
no scanner, signing or publication credentials. Existing local version/documentation checks,
pending handoff, immutable version checks, RC/final approval, public resolve-back, REL-2 and
independent consumer qualification remain separate release boundaries.

This correction is master-first; do not backport it to `release/4.0.x`. Future maintenance-line
admission requires the separate review specified in ADR 0030. The Gradle protected-operation
dependency correction is the REL-B slice of [#903](https://github.com/klum-dsl/klum-ast/issues/903).

### Local protected-operation prerequisites

Every actual protected signing, Sonatype initialization, protected Maven upload, staging close/release
and Plugin Portal operation depends on `verifyProtectedReleaseAuthorization`. That task requires
successful `verifyReleaseVersion`, `verifyVersionedDocumentationRenderer` and the existing root `check`,
then enforces the `publishCompleteKlumAstProduct` entry. Dependency failure blocks the operation even
with reordered commands, parallel execution or `--continue`. Staging release also requires successful
close, and Portal publication requires successful Sonatype close/release.

Sonatype remains protected before its staging URL is initialized and when redirected to a file URL.
Other Maven tasks are classified by their actual repository URL: only file URLs without a network
authority are local; scheme comparisons ignore case. All other destinations are protected regardless
of repository name. Signing classification is deliberately conservative: any `Sign` task with a
signatory requires authorization if its project configures a protected Maven destination, applies
`com.gradle.plugin-publish`, or has the production `signingKey` property. This also protects direct
invocation and Maven Local requests for shared release signatures.

An independent disposable signer in such a project therefore also requires release authorization,
even when signing only a local file. Gradle's signing API does not provide a complete reverse mapping
from a signing task to its publication consumers or establish that a separate signatory uses a
disposable key. Narrowing the guard would require consumer traversal or additional key policy.
The project-level restriction is an intentional tradeoff to keep release keys protected without that
machinery. Keep disposable development signing in an isolated local-only project without production
key configuration. Unsigned Maven Local, file-repository publication, artifact preparation and
ordinary verification remain usable without release authorization. Local publishing does not require
a signer; the complete protected product still requires signatures.

These Gradle dependencies enforce local prerequisites. REL-A's read-only GitHub preflight and immediate
post-approval recheck remain workflow prerequisites, and protected environments remain the credential
authority. No local flag, file or successful task substitutes for those gates. Pending documentation,
remote immutable-version checks, public resolve-back, REL-2 and independent consumer qualification
retain their existing ordering and ownership. See the [REL-B evidence](docs/implementation/issue-903-rel-b-evidence.md)
for harmless execution fixtures and the separate production graph probe; never rehearse a real release.

After publication, the same protected release run proves public availability without retaining release
credentials: it resolves the three plugin markers from the Plugin Portal once, then polls the complete
Maven product from Maven Central every ten minutes for at most two hours. The Maven poll keeps one
unprivileged runner for its bounded wait; it does not start a new runner for each check. A failed Portal
check or exhausted Maven poll burns the public version and reaches no tag, GitHub release, exact public
documentation, or alias.

After that proof succeeds, the maintainer creates the release record as an explicit, separate action:
an **annotated** `v<version>` tag at the exact authorized SHA and a non-draft GitHub release targeting
that same SHA (`prerelease` for an RC). This is deliberately outside a waiting environment approval:
every environment approval remains an approval only, and the record step cannot silently require extra
work halfway through a running workflow.

Then dispatch [REL-2: Verify public release](.github/workflows/verify-public-release.yml) from the same allowed source line, with
only the exact `candidate`/`final` stage, version, and full source SHA. It first rejects a missing,
lightweight, or wrongly targeted tag and any mismatched/draft/wrong-stage GitHub release. Only then does
its credential-free `resolve` job re-prove the complete product from clean caches and retain an immutable
proof artifact. The parent passes that proof internally to the protected #456 documentation-promotion
job, which validates the matching immutable pending-stage manifest, creates the public exact tree, and
exposes only `/preview/` for an RC or `/stable/` and its maintained line for a final. Its Pages writer and
deployment authority contains no Maven, Plugin Portal, signing, or release-record credentials.

The canonical Pages root is a mutable landing page, not an immutable release tree. Promotion writes it and
each eligible alias with the selected exact version's verified local CSS and branding logo: the root selects
`/preview/` while an RC is current, and `/stable/` after a final. The one-time historical landing repair was
completed for RC.14; future promotions establish this contract directly and must not alter an existing exact
version tree.

For a transient documentation failure, use **Re-run failed jobs** on the same parent run only when GitHub
retains the already successful `resolve` job and its proof artifact. GitHub pins a failed-job retry's
reusable workflow to its original revision; if a caller or called-workflow correction is required, merge
it first and start a new input-only **REL-2: Verify public release** dispatch only while no public exact tree or
promotion record exists. Its required tag and release may already exist; the new proof has its own identity and
never repairs an earlier partial promotion.

If the previous finalizer failed after promotion (as for RC.14), manually create the exact annotated tag
and matching GitHub release first, then use **REC-1: Recover incomplete public release record** with the original
stage, version, source SHA, verification run ID, and successful proof attempt. The recovery workflow is
read-only: it accepts only the failed parent whose proof, promotion, deployment, and immutable `gh-pages`
record match exactly, then validates the manually created tag/release. It has no protected writer,
deployment, or publishing credentials. Record the tag, GitHub release, publication workflow, verification
workflow, documentation-promotion workflow, resolved coordinates, and consumer evidence. The RC validates
that source commit; do not relabel, replace, or promote it.

## Public resolve-back evidence

The protected publication run separates its first public proof into two isolated fixtures. It executes
[release/plugin-consumer](release/plugin-consumer) once with the Plugin Portal as its **only** plugin
repository, so marker resolution cannot fall back to Maven Central. Only after that succeeds does it poll
[release/maven-consumer](release/maven-consumer), whose only repository is Maven Central, for every
published Maven module. The manually dispatched `resolve` job then performs the combined
[release/consumer](release/consumer) recheck with an empty `GRADLE_USER_HOME` before documentation
promotion. All fixtures forbid composite builds, `mavenLocal`, and local repositories, and retain their
evidence as workflow artifacts. A release is incomplete until this proof and an authorized maintainer's
equivalent clean external-consumer evidence from a different machine or network pass as required by ADR 0012.

`com.blackbuild.convention.groovy` is an intentional third public marker. The fixture proves
it alongside the Schema and Model plugin IDs; this is one complete product validation, not a
new release channel.

## Failure and recovery

The maintainer-approved [4.1.1 recovery exception](docs/implementation/release-4.1.1-recovery.md) permits one fresh
final attempt from the corrected maintenance-branch source after the first attempt stopped before artifact publication.
It preserves the rejected identity and requires new exact-SHA CI, trusted SonarCloud, and protected approvals; it does
not change the general next-version recovery rule below.

| Failure point | Safe action | Retry rule |
| --- | --- | --- |
| Input, clean-checkout, version, tests, or environment approval fails | Fix before publication and obtain authorization again. | The unused version may be reused only if no tag or public artifact exists. |
| Signing or Sonatype staging fails before release | Abort/drop the staging repository using the registry's protected operation; retain the incident evidence. | Use the next RC number if a tag or any artifact was created. |
| Maven Central succeeds but Plugin Portal fails | Stop. Do not delete, overwrite, or republish Maven coordinates. Record the partial RC as failed/superseded. | Correct the cause and issue `rc.N+1`; do not repair a different registry under the old version. |
| Plugin Portal proof fails or Maven Central polling expires | Stop and record the immutable RC or final incident; do not create a tag, release, exact public documentation, or alias. | For an RC, correct the cause and use `rc.N+1`; do not repair the old version in place. |
| Manual tag or GitHub release validation fails | Stop before dispatching, correct it only if no public artifact identity has been misrepresented, and revalidate the exact annotated tag, SHA, and stage flag. | Never substitute a lightweight tag, a branch name, or a mismatched release record. |
| Documentation promotion is interrupted | For a transient failure, use GitHub's retry for the same **REL-2: Verify public release** run only when it retains the same successful proof identity and exact stage/version/SHA. For a merged workflow correction before any public exact tree or promotion record exists, dispatch a fresh REL-2 run with the same stage/version/source even when its required tag/release record already exists. | Never replace or repair a mismatched promotion record. A fresh proof is a different immutable identity and may start only an unrecorded promotion. |
| Versioned documentation/Javadoc destination is unavailable or its pending stage rejects a valid identity | Do not authorize artifact publication. Retain the sanitized `pending-rejected` evidence when the protected stage reached an accepted identity. | Correct the cause and follow the immutable next-version rule; never overwrite a pending or rejected Pages path. |
| RC consumer resolve-back fails | Mark that RC rejected/superseded and preserve the evidence. | Any substantive change requires `rc.N+1`. |
| Final remote verification fails | Treat the final as an incident; it is not an RC retry. | Do not remove published artifacts. A tag may be deleted only by explicit human decision when no artifact was ever published. |

Never print, copy, or diagnose secret values. Do not run publication tasks locally, use
`publishToMavenLocal` as consumer evidence, or trigger this workflow merely to rehearse it.
The first real RC is the authorized public validation event; its evidence and recovery record
are retained with the GitHub prerelease and release workflow run.
