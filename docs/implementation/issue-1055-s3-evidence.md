# #1055 S3: raw-input presence qualification and release boundary

Date: 2026-11-08. Status: **S3 rebased onto fixed S2; additive Sonar follow-up validated locally; final-head hosted status is recorded in #1060; no public-candidate claim**.
Authority: [#1055](https://github.com/klum-dsl/klum-ast/issues/1055), including its
[transitive domain clarification](https://github.com/klum-dsl/klum-ast/issues/1055#issuecomment-6720271506),
[accepted plan](issue-1055-raw-input-required-plan.md) and
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#raw-input-field-presence--1055).

## Original exact chain and scope

S3 originally started at exact S2 `ca38933c9ac5b1b9535a743ba9a17b1d8ccce4d8`, tree
`4fa838f6a4ea580b9c43e583bf86898abadcbaa9`, on dedicated branch
`codex/issue-1055-s3-docs-qualification`, worktree `/private/tmp/klum-ast-1055-s3`.
S2 is draft [#1058](https://github.com/klum-dsl/klum-ast/pull/1058), based on amended S1
`14454d4fe0ee4b3825b362121771d26aefb1eded`, draft [#1057](https://github.com/klum-dsl/klum-ast/pull/1057).
The native GitHub stack is #1059: S1 → S2 → S3. S1/S2 branches are not edited by S3.

S1 captures effective field presence at the existing inert Schema annotation-composition seam, transports it through
exact-byte validation and immutable selected entries; see [S1 evidence](issue-1055-s1-evidence.md).
S2 changes only the containing-object required union in `JsonSchemaProjection.definition`, preserving property payloads
and construction-key positions. Its final source-free fixtures cover direct, domain and transitive domain markers in
API/base/derived/owned artifacts; the six form/generation combinations emit byte-identical schemas. S3 retains those
fixtures and adds the release-facing user documentation, documentary example, binary/JPMS and failure qualification.

No production implementation, importer grammar, parser dependency, automatic validation, version bump, generic
requiredness framework or unrelated refactoring is added by S3. User documentation distinguishes presence from null
admission, defaults, Templates, lifecycle, completed `@Required` and Jakarta validation, and operation/key policies.
`GeneratedInput.Schema.version=1` remains the complete current development contract, with optional boolean presence:
marked fields emit true; omission and explicit false normalize to false. All other required facts remain mandatory.
Earlier development-v1 contracts/readers remain unsupported; no historical or cross-version matrix is claimed.

## Qualification seams

- `KlumJsonSchemaGenerationTest` retains independently compiled API parents/domain annotations, derived and owned JARs
  with producer sources and loose classes deleted. Direct/domain/transitive fields exercise inheritance, explicit naming,
  KEBAB_CASE, key positions, shared child definitions and independent constraint policies.
- Its documentary feature `documents raw-input presence without enforcing import with Groovy #generation`, tagged
  `documentary` with `@Issue('1055')` and `@See` to [the user example](../user/Json-Schema.md#raw-input-field-presence),
  demonstrates a direct marker and an independently compiled domain marker together. Ordinary DSL, root import and
  active `applyStruct` accept omission and retain initializer/false values. The companion external
  `validate_required_schema.py` loads the exact exported schemas using `jsonschema==4.23.0` Draft 2020-12, rejects
  omission and distinguishes presence from null/type checks. This two-part oracle follows the existing external-validator
  harness; ordinary Gradle test success alone is not an external-validator result.
- Packaged malformed current presence cases use root wrong-type, binary-parent duplicate and omitted-owned contradictory
  facts. Import rejects before root/child initialization, active owned import rejects before its earlier member mutates,
  and generation preserves both previous schema bytes and its output manifest. S1's focused reader controls retain
  true-plus-ignored, accessor/source mismatch and operation-displacement checks. Existing missing/incompatible-version
  and missing mandatory-fact tests remain part of the affected suite; the optional boolean does not bypass them.
- `BinaryOwnedRelationshipsTest` adds a bounded matching presence variant to its established G3/G4/G5 packaged API,
  Schema and compiled-client harness. Direct/domain parent and owned facts appear at the correct definitions/key positions;
  ordinary import still succeeds when raw schema requirements are omitted. Its unmarked fixtures and existing external
  grammar oracles retain their prior behavior.
- `JpmsPackageBoundaryTest` carries direct and domain fields in its real Schema, checks true/false entries from inside the
  runtime module and retains Java/static Groovy construction consumers. G4/G5 delete Schema sources and loose classes
  after packaging, before binary consumers; no new add-opens/add-exports or access workaround is used. G3 remains
  classpath-only. Package/descriptor inventory remains checked by the same suite.

## Results and exact omissions

Initial local qualification (Java 17, matching branch-built artifacts; no published candidate):

- G3 focused compiler/presence/package checks: 32 cases, no failures/errors/skips.
- `:klum-ast:groovy4Tests :klum-ast:groovy5Tests --tests '*StructRequiredTest' --tests '*JpmsPackageBoundaryTest'`:
  G4 ran the full compiler suite (the filters apply to the last task): 3,434 cases, no failures/errors, 15 existing skips;
  G5 focused: 32 cases, no failures/errors/skips. The final sequential root check below requalifies source deletion
  in both named-module lanes.
- `:klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest*presence*' --tests '*BinaryOwnedRelationshipsTest'`:
  26 cases, no failures/errors/skips; includes all three documentary cases, six direct/domain cases, three malformed
  presence cases, three dedicated binary-presence cases and the existing 11-case binary-owned suite.
- `/private/tmp/1026-schema-venv/bin/python klum-ast-gradle-plugin/src/test/resources/validate_required_schema.py
  klum-ast-gradle-plugin/build/schema-validation`: external `jsonschema==4.23.0` oracle passed, 24 presence schemas
  (42 accepted / 174 rejected instances) plus three documentary schemas (one accepted / four rejected instances each)
  and independent presence-only controls. Direct/domain G3/G4/G5 schemas are byte-identical.
- Changed Markdown relative targets/fences and `git diff --check`: passed.

Final sequential qualification at `d5f686200941a32d9196d4a5389da32d27822beb`, tree
`d469c33869fd5a3c5e62154a354b91fc63331329`:

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --console=plain`:
  **12,062 cases, zero failures/errors, 49 existing skips; 18m 5s**. Full affected compiler/runtime/adapter suites,
  all three isolated Groovy lanes, the complete 684-case plugin/TestKit suite, package/JPMS checks, license,
  lane-isolation, renderer contract and aggregate coverage tasks pass. The compiler reports 3,430 G3 cases (16 existing skips),
  and 3,434 G4/G5 cases each (15 existing skips each), with zero failures/errors. No new ignored/pending tests were introduced.
- Exact matching local artifact version: `4.2.0-dev.503+codex.issue.1055.s3.docs.qualification.d5f6862`; Java Zulu 17.0.3, Gradle 8.14.4,
  Groovy 3.0.25 / 4.0.32 / 5.0.6, Spock 2.4 Groovy-specific lanes, KlumCast 0.5.0. These are branch-built artifacts,
  not published candidate coordinates.
- `renderLocalDocumentation --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2 --console=plain`:
  exact source/renderer `071632a4fe95a8accac565e56d27821cc47957d7`, tracer `4.2.0-tracer`, Season 4.2 manifest,
  fresh local render and full site crawl pass. User-doc/renderer/build inputs are unchanged by the subsequent
  one-line catalog fixture repair; no release/deployment was performed.
- Independent Standards and Specification reviews: **0 actionable findings each**, including the catalog repair follow-up.
  Final history retains the reviewed implementation, mechanical matched-contract fixture repair and evidence-only
  reconciliation as separate reasoned commits. The evidence follow-up changes no qualified code/test/user-doc/build/CI input.
- Final external Draft 2020-12 required, structural, Builder-body, owned-sequence, literal-alternative, virtual-scope and
  composed-constraint oracles pass against the current emitted corpus. Commands are the corresponding
  `klum-ast-gradle-plugin/src/test/resources/validate_*_schema.py` scripts with
  `klum-ast-gradle-plugin/build/schema-validation`, using `/private/tmp/1026-schema-venv/bin/python` (`jsonschema==4.23.0`).
  The required oracle retains the 24+3 document counts above; body oracle: 30 schemas, 75 accepted/114 rejected;
  composed constraints: 234 controls and byte-identical G3/G4/G5 output.

The first full run found 21 catalog fixture failures at constructor selection: its synthetic Entry still used the
pre-S1 16-argument shape. S3 appends false for unmarked presence, without changing assertions or production behavior;
all 21 focused cases then pass. The concurrent repair build also changed compiler version markers while that first run
was active, invalidating a provenance assertion. That run was stopped and is not a successful qualification claim.
The final full run above is sequential on the committed repair and supersedes both failures. Initial pre-commit root
startup was also stopped/restarted at the first committed revision; it is not counted as qualification.

Logs/evidence: `/private/tmp/1055-s3-root-final.log`, `/private/tmp/1055-s3-root-counts.json`,
`/private/tmp/1055-s3-doc-render.log`, `/private/tmp/1055-s3-external-results.json`, and the corresponding
`/private/tmp/1055-s3-validate_*_schema.py.log` files. Dedicated annotation-propagation artifact shipping tasks are
not part of root check and are omitted locally; hosted CI runs its separate shipping matrix. No local SonarCloud upload,
manual workflow dispatch, historical development-v1 matrix, publication/adopter or exact public-candidate qualification
is claimed. The final cumulative PR's hosted CI/SonarCloud state remains a separate live acceptance gate.

Delivery authorization audit: gh CLI/API authorized; SSH git push authorized by dedicated-branch dry run. No GitHub App
write authority is inferred or used. Delivery target is a draft PR based on `codex/issue-1055-s2-schema-required`, appended
to native stack #1059. Exact PR head/base, draft state, native registration and initial hosted-CI result are recorded in
that PR's delivery body and the task handoff; no merge/readiness/issue closure is part of S3.

Initial G3 focused run:
`:klum-ast:test --tests '*StructRequiredTest' --tests '*JpmsPackageBoundaryTest'`: 32 passed, zero failures/errors/skips.
The first plugin attempt exposed duplicated unrelated facts in the test mutation helper; corrected. The next attempt
proved the parent/owned failures and documentary cases but missed the intended root accessor; corrected to ordinary
`count` selection. No production repair was required by either fixture correction.

## Pre-RC and exact public-candidate obligations

These branch-local matched-contract results do not freeze v1, publish artifacts, qualify an adopter or qualify a public RC.
Before RC acceptance the Hive/maintainer must:

1. Reconcile/merge the draft chain. At a suitable boundary after predecessor merges, rebase onto current master and compare
   relevant source/generated trees, dependency/toolchain/build/CI inputs and exact heads before retaining earlier evidence.
2. Record the final v1 contract including field presence, complete mandatory facts and the final freeze decision.
3. Recompile every participating Schema/API/base/derived/owned artifact with the exact matching public candidate's
   annotations/compiler/runtime/BOM/Schema-plugin coordinates; rebuilding only the final application is insufficient.
4. Regenerate JSON Schemas with those exact artifacts and qualify the exact candidate through G3/G4/G5 source-free
   consumers, binary parent/owned contracts, G4/G5 JPMS, malformed/incompatible failure controls and external Draft
   2020-12 validation, plus exact-revision hosted CI/SonarCloud. Record coordinates, heads, commands, results and omissions.
5. Reconcile user/release evidence and decide candidate acceptance. An incompatible change after final freeze requires
   another decision and a further RC. No stale-artifact detector or general binary-compatibility promise is implied.

Tracker impact: `Related: #1055`. The issue remains open; no label/milestone/curation or issue-closure mutation is made.
Draft stack delivery does not mark ready, merge or authorize self-archive. Request Hive delivery/archive reconciliation
with exact-head CI status after publication; public-candidate/recompilation/freeze obligations remain explicitly open.


## Ordered rebase onto fixed S2 — 2026-11-08

Hive explicitly authorized rebasing the reviewed/published S3 history once the replacement S2 head was published,
then updating #1060 with an exact force-with-lease. S1/S2 remain externally owned; this action neither edits nor merges them.

- Preserved S3: `7d5ffdccb571ed57bb50730cd1911fa3c7fd8994`, tree
  `a3a2c489d80afe3dd19b939f9049deaead6790e8`; backup ref
  `refs/codex/issue-1055-s3-before-s2-rebase`.
- New published S2: `806fdc9c0c0734e720adb2a3ff3798544a091287`, including fixed S1
  `d7a840dceedb66294917cf930cdc619210c529c0`. S2 is still based on `codex/1055-s1-required-fact`, not merged master.
- `git rebase --onto 806fdc9c0c0734e720adb2a3ff3798544a091287 ca38933c9ac5b1b9535a743ba9a17b1d8ccce4d8
  codex/issue-1055-s3-docs-qualification` completed without conflicts. The duplicate S3 catalog-fixture commit
  `d5f686200` dropped because its exact patch is already upstream. No conflict resolution or production change was needed.
- Replayed pre-evidence S3: `6140bd3e1758e5c40899083f0cb52ee80b1573e4`, tree
  `36a7f526e31565a8fd3feddd7404dabeb21ee935`. Range-diff maps `071632a4f` to `a1f585582` and `7d5ffdccb` to
  `6140bd3e1` unchanged; only the upstream duplicate is removed.
- Whole-tree comparison from preserved S3 to rebased S3 changes only 16 added lines in
  `docs/implementation/issue-1055-s1-evidence.md`. All production/compiler/runtime/annotation/plugin code, executable tests
  and fixtures, user documentation, build/dependency/toolchain configuration, workflow/scripts and other tracked inputs
  are byte-identical. The current evidence reconciliation is documentation only. Git ancestry/head and inferred artifact
  version/provenance identity do change, so final-head hosted CI/SonarCloud must run afresh.
- `git diff --check` and changed Markdown fence/relative-target checks pass. No functional change invalidates the prior
  complete local 12,062-case run, documentary/external-validator results or named-module/source-free controls as dated
  evidence for those unchanged code inputs. They are not presented as a fresh root check or hosted pass on the rebased head.
  Local Groovy/TestKit reruns are omitted for this integration-only replay; the new cumulative hosted run supplies the
  exact new-head full build, coverage and shipping qualification.

Older hosted evidence is invalidated for current delivery: S2 run `42472986126` failed on the known catalog-fixture
omission and does not identify an independent S2 defect; pre-rebase S3 run `42477002757` targets the replaced
`7d5ffdccb` head and is not the current gate, whatever its eventual result. No earlier S1 run is reused for fixed S1.
The new S3 head/base, force-with-lease result and its own cumulative CI/SonarCloud run are recorded in #1060's current
body/follow-up and task handoff after publication. Native stack #1059 and S3's base branch
`codex/issue-1055-s2-schema-required` must remain intact. No readiness/merge/issue closure is authorized.

After predecessor merges, any further rebase onto current master still requires a fresh tree/CI-input comparison and
appropriate requalification. Pre-RC recompilation, final v1 freeze and exact public-candidate obligations above remain open.


## Additive SonarCloud follow-up — 2026-11-08

Rebased-head run [42477833340](https://github.com/klum-dsl/klum-ast/actions/runs/42477833340) on
`15e3e28798fd2d3d3d4d7f55c83554c26db106db` completed **SUCCESS**, with build job duration **1h 16m 8s**.
The main full build, separate G3/G4/G5 annotation-propagation artifact qualification, JUnit report and compatibility
artifact publication passed. Both SonarCloud check runs completed SUCCESS; direct quality-gate API status was OK,
with zero security hotspots. This is a new-head hosted result, not reused older S2/S3 evidence.

Direct inspection of unresolved PR issues nevertheless found two `groovydre:S8306` maintainability findings:
`AaG7OSvF_mnc5PvfRl7l` and `AaG7OSvF_mnc5PvfRl7m`, on the duplicate/excluded presence mutation branches in
`KlumJsonSchemaGenerationTest`. The additive repair puts braces around those two existing single-statement if bodies.
Conditions, emitted values, call ordering, return behavior and assertions remain identical. No suppression, analyzer
exclusion, global style-policy change or production/API/protocol change is introduced.

Validation of the repair:

- `:klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest*malformed presence*'`: 3 cases, no failures/errors/skips.
- Full `:klum-ast-gradle-plugin:test`: **684 cases, no failures/errors/skips; 12m 28s**.
- All seven external Draft 2020-12 required/structural/body/owned-sequence/literal-alternative/virtual-scope/composed-constraint
  oracles pass on the regenerated local corpus, using the same pinned `jsonschema==4.23.0` environment and commands above.
- Independent Standards and Specification follow-up reviews: **0 actionable findings each**. Both confirm the repair is
  mechanically equivalent and preserves the current-contract failure controls. No test is added, ignored or weakened.
- `git diff --check` and evidence Markdown target/fence checks pass. The source diff is six added/two removed lines,
  wrapping the two if bodies; the accompanying evidence update is documentation only.

Compiler/runtime/annotation code, producer fixture strings, user documentation, dependency/build/CI inputs and operation
semantics remain unchanged by this repair. The affected plugin suite, including G3/G4/G5 producer cases, was rerun locally;
local compiler/runtime lane and full-root repeats are omitted for this test-only formatting change. A fresh exact-head
hosted full build, SonarCloud and artifact qualification is required after the additive push; `15e3e2879`'s positive run
is dated pre-repair evidence, not the final follow-up gate. The final pushed head/run and all finding dispositions are
recorded in #1060's current body and one consolidated follow-up, avoiding a self-invalidating CI-result-only commit.

Logs: `/private/tmp/1055-s3-sonar-braces-focused.log`, `/private/tmp/1055-s3-sonar-braces-plugin-full.log`,
`/private/tmp/1055-s3-sonar-braces-validation.json` and `/private/tmp/1055-s3-sonar-braces-validate_*_schema.py.log`.
Sonar/hosted snapshot files are `/private/tmp/1055-s3-sonar-1060-issues.json`,
`/private/tmp/1055-s3-sonar-1060-qualitygate.json`, `/private/tmp/1055-s3-sonar-1060-hotspots.json` and
`/private/tmp/1055-s3-ci-42477833340-terminal.json`. No predecessor, readiness, merge, issue-state or release action is taken.
