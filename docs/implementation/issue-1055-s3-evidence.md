# #1055 S3: raw-input presence qualification and release boundary

Date: 2026-11-08. Status: **Speculative S3; local qualification in progress, no merge or public-candidate claim**.
Authority: [#1055](https://github.com/klum-dsl/klum-ast/issues/1055), including its
[transitive domain clarification](https://github.com/klum-dsl/klum-ast/issues/1055#issuecomment-6720271506),
[accepted plan](issue-1055-raw-input-required-plan.md) and
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#raw-input-field-presence--1055).

## Exact chain and scope

S3 starts at exact S2 `ca38933c9ac5b1b9535a743ba9a17b1d8ccce4d8`, tree
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
  G5 focused: 32 cases, no failures/errors/skips. The final root check will requalify the G4 source-deletion addition.
- `:klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest*presence*' --tests '*BinaryOwnedRelationshipsTest'`:
  26 cases, no failures/errors/skips; includes all three documentary cases, six direct/domain cases, three malformed
  presence cases, three dedicated binary-presence cases and the existing 11-case binary-owned suite.
- `/private/tmp/1026-schema-venv/bin/python klum-ast-gradle-plugin/src/test/resources/validate_required_schema.py
  klum-ast-gradle-plugin/build/schema-validation`: external `jsonschema==4.23.0` oracle passed, 24 presence schemas
  (42 accepted / 174 rejected instances) plus three documentary schemas (one accepted / four rejected instances each)
  and independent presence-only controls. Direct/domain G3/G4/G5 schemas are byte-identical.
- Changed Markdown relative targets/fences and `git diff --check`: passed.

Affected full suites/root check, exact committed documentation rendering, and independent review results remain in progress.

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
