# #895: isolate TemplateScope nested publication

The nested publication now builds in an independent temporary checkout from captured current source inputs and the
outer build's actual version. Advancing the original checkout cannot change the requested/published coordinates or
redirect nested compilation, descriptors, JARs or Gradle state into the active build. This is a fixture correctness
change; no full-build or hosted speedup is claimed. Related: #895.

## Contract and boundary

`TemplateScopePublishedConsumerTest` originally passed the active root to `GradleRunner.withProjectDir`. Its temporary
Maven repository isolated the publication destination only. `klumVersion` was already captured during outer build
configuration, while the nested build independently recomputed its version from live Git state.

The module's `templateScopePublicationSource` task snapshots current root/buildSrc/subproject Gradle configuration,
LICENSE, README and source trees, including uncommitted source edits. It excludes build outputs, Gradle state and Git
metadata. The archive is an explicit input of the baseline Test task. Git HEAD and the actual existing outer version
are captured during configuration; source contents become fixed when the snapshot task runs before that Test task.
This does not promise transactional consistency for arbitrary edits made before source capture.

Each publication gets a new TemporaryFolder outside the checkout. A `git clone --no-hardlinks --no-checkout` gives it
independent Git objects, refs and index; `reset --mixed` selects the captured revision without installing committed
source in place of current inputs. The remote is removed. Only the captured source archive is extracted. Consequently
local edits and source deletions are represented, and no previous compiled or packaged output is copied. The nested
runner passes the outer version through the existing `-Prelease.version` override. This is the real captured version,
including development suffixes, not an invented fixture version. No release task or release-policy change is involved.

The original published-consumer feature and all its assertions remain: five real Maven publications (annotations,
compiler, runtime, support and BOM), runtime-scoped support dependency, no transitive Groovy/test-fixtures leak, BOM
constraint, and independent Java plus Groovy/Spock 3/4/5 consumers that install and restore a Template. Consumers resolve
binary coordinates from the temporary Maven repository. Existing Maven Local paths elsewhere are unchanged, as
confirmed by the maintainer. TestKit remains in the baseline lane; compatibility source exclusions and all other
classpath/JPMS/deleted-source qualification remain unchanged. No public API, user page, migration navigation, changelog,
source-release behavior, workflow, CI topology or verification-breadth change is needed.

## Deterministic red/green control

The two pre-existing commits below have identical production sources. The second adds only descriptor evidence:

- captured Git HEAD: `49cc93c85e61d6ca795c790e7d81f11e23a728d6`
- advanced Git HEAD: `aa55ed9a3c2266645cff0f53f3a06ce9c69e6f90`

[reproduce.py](reproduce.py) creates a disposable clone at the first commit, overlays the current snapshot wiring in
both controls, and uses either the original or corrected publication test. A temporary init script advances only that
clone's HEAD to the second existing commit in the outer Test task's `doFirst`, after version/revision and source capture
but before nested publication. It creates no commits and never changes the active checkout's HEAD. Both controls use
the actual outer version `4.1.0-dev.448.uncommitted+49cc93c`; no synthetic version is supplied by the harness.

With Java 17 and a full-history checkout containing those commits:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
python3 docs/implementation/evidence/issue-895-template-publication/reproduce.py \
  --mode before --output /tmp/template-publication-before
python3 docs/implementation/evidence/issue-895-template-publication/reproduce.py \
  --mode after --output /tmp/template-publication-after
```

Use a new output directory for each invocation. The underlying command is:

```sh
./gradlew -p <disposable-checkout> :klum-ast-test-support:test \
  --tests com.blackbuild.klum.ast.testsupport.TemplateScopePublishedConsumerTest \
  --init-script <advance-head-init-script> \
  --no-build-cache --no-configuration-cache --no-parallel --max-workers=2 --console=plain
```

The original test fails deterministically after nested publication succeeds: its lookup of the support POM at the
captured outer coordinate throws `FileNotFoundException`. The corrected feature publishes and consumes that exact
coordinate successfully after the same Git advancement. [results.json](results.json) retains sanitized identities,
source hashes, counts and timing; full raw logs/XML stay only in the specified temporary directories.

The new #895 regression uses existing captured HEAD/parent commits in disposable clones. It advances the original
clone's Git identity, poisons its build script, then configures the nested build and asserts the captured version,
independent project/build directories, retained source bytes and pinned Git HEAD. The original clone gains neither
Gradle state nor build outputs. Focused mutations in the harness independently remove directory isolation and the
captured version override:

```sh
python3 docs/implementation/evidence/issue-895-template-publication/reproduce.py \
  --mode after --negative-control live-root --output /tmp/template-publication-live-root
python3 docs/implementation/evidence/issue-895-template-publication/reproduce.py \
  --mode after --negative-control live-version --output /tmp/template-publication-live-version
```

Each must fail the regression. These controls alter only copied test source, not repository history or normal test
behavior. An initial stronger green attempt exposed a macOS `/var` versus `/private/var` path-alias assertion;
canonical-path comparison corrected the assertion. Its published-consumer feature had already passed. That unsuccessful
attempt is not included as a successful qualification result.

Environment: shared Apple M3 Max macOS host, Azul Java 17.0.3+7, wrapper Gradle 8.14.4, Groovy 3.0.25 / 4.0.32 / 5.0.6
and matching Spock 2.4. Existing dependency caches are retained. Timing is descriptive correctness evidence and includes
fresh isolated compilation; the controls differ in fixture work and test count, so their wall times are not a speedup
comparison.

## Final qualification and limitations

Run focused/module Groovy-3 checks, then root `check` and the three explicit annotation-propagation artifact tasks on
the frozen rebased commit. Root `check` includes the Groovy 4/5 lanes, lane isolation, coverage, renderer, Javadocs and
license checks. Standards/Spec review, verified #906 merge/rebase identity, exact final-head local/CI/Sonar results and
delivery links belong in the PR/handoff so recording them does not advance Git identity during verification.

Only this TemplateScope publication seam is corrected. Other nested-root publication fixtures remain separate work;
do not commit or rewrite HEAD during full verification while they still read live Git state. The new regression needs
the captured commit and its parent (the existing CI checkout already fetches full history). Future publication inputs
outside the captured Gradle/source trees must be added to the snapshot. Configuration-cache repair, broader release
version decisions, CI changes and controlled hosted performance comparisons remain outside this slice. Tracker impact:
related parent issue remains open; curation/release-policy impact: none. Hive reconciliation is required for archiving.
