# Releasing KlumAST

This is the KlumAST-local procedure for release candidates (RCs) and finals. It implements
[ADR 0012](docs/adr/0012-shared-prerelease-channel-policy.md); that ADR is the authority for
the shared channel vocabulary and immutability rules. This file does not authorize a release.
Only an explicitly authorized maintainer may approve the protected `release` environment and
dispatch [Release](.github/workflows/release.yml).

## Product and trust map

The release is one product at one exact version. Its public Maven coordinates are:

- `klum-ast-annotations`, `klum-ast`, `klum-ast-runtime`, `klum-ast-jackson`,
  `klum-ast-bean-validation`, `klum-ast-bom`, and `klum-ast-gradle-plugin`, all in
  `com.blackbuild.klum.ast`.

The Gradle Plugin Portal exposes three declared public plugin IDs:

- `com.blackbuild.klum-ast-schema`
- `com.blackbuild.klum-ast-model`
- `com.blackbuild.convention.groovy`

The `release` GitHub environment is the sole credential boundary. It must contain only
maintainer-managed `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `SIGNING_KEY`,
`SIGNING_PASSWORD`, `GRADLE_PUBLISH_KEY`, and `GRADLE_PUBLISH_SECRET` secrets. The workflow
maps them to Gradle only in the protected job; ordinary CI, pull requests, forks, and
resolve-back receive none of them. The normal `GITHUB_TOKEN` is scoped to `contents: read`;
the protected job alone receives `contents: write` for Nebula's tag and the immutable GitHub
release record.

Maven publications are signed and staged through the configured Sonatype endpoint. Plugin
markers and plugin publication use the Gradle Plugin Portal. A GitHub release records the
tag and notes after those artifact publications. These systems cannot provide one
cross-registry transaction: a failure after any remote operation burns the version rather
than permitting a retry in place.

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

1. Confirm an explicit human authorization for this exact stage, version, and commit. RC and
   final approvals are separate.
2. Start from a clean, non-composite checkout of the intended commit. The build rejects
   composite builds, and the protected workflow rejects an existing `v<version>` tag or
   GitHub release.
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

Dispatch **Release** from the exact accepted commit, select `candidate` or `final`, and enter
the exact version. The environment approval is the irreversible-operation checkpoint. The
workflow executes, in its protected job:

```text
./gradlew <candidate|final> publishToSonatype closeAndReleaseSonatypeStagingRepository \
  :klum-ast-gradle-plugin:publishPlugins \
  -PreleaseExpectedVersion=<exact version>
```

Nebula selects the candidate/final version during configuration; `verifyReleaseVersion`
rejects a mismatch before the lifecycle task or any publication task executes. The command
therefore publishes the Maven product, closes/releases its staging repository, and publishes
the Plugin Portal product from one Gradle configuration. Documentation/Javadoc publication
is intentionally excluded pending #456's versioned-destination decision. It does not make
cross-registry publication reversible or claim that a final is byte-identical to its RC.

After a successful RC, record the exact tag, GitHub prerelease, workflow run, resolved
coordinates, and consumer evidence. The RC validates that source commit. Do not relabel,
replace, or promote it. A final is a new `X.Y.Z` publication from the exact commit accepted
for the final RC, followed by its own remote verification.

## Public resolve-back evidence

The `resolve-back` job uses a fresh GitHub-hosted runner and an empty `GRADLE_USER_HOME`. It
executes [release/consumer](release/consumer), which has no composite build, `mavenLocal`,
or local repository and resolves all Maven coordinates through Maven Central plus all three
declared plugin markers through the Plugin Portal. A release is incomplete until this job
passes and an authorized maintainer records equivalent clean external-consumer evidence from
a different machine or network as required by ADR 0012.

`com.blackbuild.convention.groovy` is an intentional third public marker. The fixture proves
it alongside the Schema and Model plugin IDs; this is one complete product validation, not a
new release channel.

## Failure and recovery

| Failure point | Safe action | Retry rule |
| --- | --- | --- |
| Input, clean-checkout, version, tests, or environment approval fails | Fix before publication and obtain authorization again. | The unused version may be reused only if no tag or public artifact exists. |
| Signing or Sonatype staging fails before release | Abort/drop the staging repository using the registry's protected operation; retain the incident evidence. | Use the next RC number if a tag or any artifact was created. |
| Maven Central succeeds but Plugin Portal or GitHub release fails | Stop. Do not delete, overwrite, or republish Maven coordinates. Record the partial RC as failed/superseded. | Correct the cause and issue `rc.N+1`; do not repair a different registry under the old version. |
| Plugin Portal succeeds but GitHub release metadata fails | Stop and record the partial immutable RC. | Use `rc.N+1`; do not repair the old version in place. |
| Versioned documentation/Javadoc destination is unavailable | Do not authorize an RC or final. #456 must supply the accepted destination and protected publication path. | No artifact publication begins, so no version is consumed. |
| RC consumer resolve-back fails | Mark that RC rejected/superseded and preserve the evidence. | Any substantive change requires `rc.N+1`. |
| Final remote verification fails | Treat the final as an incident; it is not an RC retry. | Do not remove published artifacts. A tag may be deleted only by explicit human decision when no artifact was ever published. |

Never print, copy, or diagnose secret values. Do not run publication tasks locally, use
`publishToMavenLocal` as consumer evidence, or trigger this workflow merely to rehearse it.
The first real RC is the authorized public validation event; its evidence and recovery record
are retained with the GitHub prerelease and release workflow run.
