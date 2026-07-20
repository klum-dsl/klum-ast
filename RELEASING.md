# Releasing KlumAST

This is the KlumAST-local procedure for release candidates (RCs) and finals. It implements
[ADR 0012](docs/adr/0012-shared-prerelease-channel-policy.md); that ADR is the authority for
the shared channel vocabulary and immutability rules. This file does not authorize a release.
Only an explicitly authorized maintainer may approve the protected `release-candidate` or
`final-release` environment and dispatch [Publish protected release](.github/workflows/release.yml).

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
workflow. [#456](https://github.com/klum-dsl/klum-ast/issues/456) owns the required decision
on versioned documentation/Javadoc source, hosting, URLs, preview lifecycle, and retention.
Until that decision and its dedicated protected publication path exist, the current wiki is
not a release destination.

The repository currently uses major action tags in its workflows. Maintainers must review
the resolved action revisions and their update policy before authorizing the first use of the
new release workflow; this is a release gate, not permission to substitute an arbitrary
action revision during an incident.

## Before approving an RC or final

1. Confirm an explicit human authorization for this exact stage, version, and full commit SHA
   on `master`. RC and final approvals are separate.
2. Start from a clean, non-composite checkout of the intended `master` commit. The build rejects
   composite builds, and the protected workflow checks that the selected SHA is on `origin/master`
   and rejects an existing `v<version>` tag or GitHub release.
3. Verify the expected version: an RC is `X.Y.Z-rc.N` with a new increasing `N`; a final is
   exactly `X.Y.Z`. Alpha, beta, milestone, and snapshot names are not release channels.
4. Confirm `CHANGES.md` remains under the projected final `(unreleased)` heading while RCs
   are evaluated. After accepting the last RC, only the ADR 0012 documentation-only exception
   may date the exact final heading; any substantive source, dependency, signing, publication,
   or workflow change requires the next RC.
5. Ensure all 4.0 blockers, documentation/Javadocs, and the normal Java 17 Groovy 3/4/5
   `check` gate are ready. The workflow runs `check` and requires the computed Nebula version
   to equal the approved input before any publication task starts.
6. Confirm #456 has delivered the accepted versioned documentation/Javadoc destination and
   that its protected publication path has produced the exact stable or RC documentation
   snapshot required for this version. Do not substitute the mutable wiki while this gate is
   open.

## Protected publication path

Dispatch **Publish protected release**, select `candidate` or `final`, and enter the exact
version and full `master` commit SHA. The matching environment approval is the irreversible-
operation checkpoint. The workflow checks out that SHA with no persisted GitHub credential,
proves it is on `master`, and executes in its protected job:

```text
./gradlew <candidate|final> publishCompleteKlumAstProduct \
  -PreleaseExpectedVersion=<exact version> \
  -PreleaseStage=<candidate|final>
```

Nebula selects the candidate/final version during configuration. `verifyReleaseVersion` rejects
a version/stage mismatch or an absent matching protected authorization before any publication
task executes. `publishCompleteKlumAstProduct` is the sole permitted public publication entry:
it publishes every Maven coordinate to one Sonatype staging repository, closes/releases it, and
then publishes every Plugin Portal marker. Documentation/Javadoc publication intentionally
remains excluded pending #456's versioned-destination decision. This does not make cross-registry
publication reversible or claim that a final is byte-identical to its RC.

After publication, wait for every Maven coordinate and every Plugin Portal marker to be
publicly resolvable. Then dispatch [Verify public release](.github/workflows/verify-public-release.yml)
with the exact version. Only after that workflow and the independent external-consumer check pass,
create and push annotated tag `v<version>` from the accepted SHA and create the matching GitHub
release (`--prerelease` for an RC). Record the exact tag, GitHub release, publication workflow,
verification workflow, resolved coordinates, and consumer evidence. The RC validates that source
commit. Do not relabel, replace, or promote it. A final is a new `X.Y.Z` publication from the
exact commit accepted for the final RC, followed by its own remote verification.

The maintainer's post-proof record step is deliberately manual and uses no publication
credentials. From a clean checkout of the accepted commit, run:

```text
git fetch origin --tags
git checkout --detach <accepted commit>
git tag -a v<version> -m "Release <version>"
git push origin v<version>
gh release create v<version> --verify-tag --target <accepted commit> [--prerelease] --generate-notes
```

Use `--prerelease` only for an RC. Confirm the remote tag targets the accepted commit before
creating the GitHub release; never use this step to repair a partial publication.

## Public resolve-back evidence

The separately dispatched `Verify public release` workflow uses a fresh GitHub-hosted runner,
an empty `GRADLE_USER_HOME`, and no release credentials. It executes
[release/consumer](release/consumer), which has no composite build, `mavenLocal`, or local
repository and resolves all Maven coordinates through Maven Central plus all three declared
plugin markers through the Plugin Portal. It retains the resolved-coordinate and applied-marker
evidence as a workflow artifact. A release is incomplete until this proof and an authorized
maintainer's equivalent clean external-consumer evidence from a different machine or network
pass as required by ADR 0012.

`com.blackbuild.convention.groovy` is an intentional third public marker. The fixture proves
it alongside the Schema and Model plugin IDs; this is one complete product validation, not a
new release channel.

## Failure and recovery

| Failure point | Safe action | Retry rule |
| --- | --- | --- |
| Input, clean-checkout, version, tests, or environment approval fails | Fix before publication and obtain authorization again. | The unused version may be reused only if no tag or public artifact exists. |
| Signing or Sonatype staging fails before release | Abort/drop the staging repository using the registry's protected operation; retain the incident evidence. | Use the next RC number if a tag or any artifact was created. |
| Maven Central succeeds but Plugin Portal fails | Stop. Do not delete, overwrite, or republish Maven coordinates. Record the partial RC as failed/superseded. | Correct the cause and issue `rc.N+1`; do not repair a different registry under the old version. |
| Public proof fails or a tag/GitHub release record cannot be created | Stop and record the immutable RC or final incident. | For an RC, correct the cause and use `rc.N+1`; do not repair the old version in place. |
| Versioned documentation/Javadoc destination is unavailable | Do not authorize an RC or final. #456 must supply the accepted destination and protected publication path. | No artifact publication begins, so no version is consumed. |
| RC consumer resolve-back fails | Mark that RC rejected/superseded and preserve the evidence. | Any substantive change requires `rc.N+1`. |
| Final remote verification fails | Treat the final as an incident; it is not an RC retry. | Do not remove published artifacts. A tag may be deleted only by explicit human decision when no artifact was ever published. |

Never print, copy, or diagnose secret values. Do not run publication tasks locally, use
`publishToMavenLocal` as consumer evidence, or trigger this workflow merely to rehearse it.
The first real RC is the authorized public validation event; its evidence and recovery record
are retained with the GitHub prerelease and release workflow run.
