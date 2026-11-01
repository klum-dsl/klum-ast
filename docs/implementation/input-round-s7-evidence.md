# Structured input S7 — explicit Builder-body schema roots

Date: 2026-11-01. Initial base: `52b9e0d97d181e8f2ad2222c50ae3a176ea4bfb4` (master, merged PR #1006).
Authority: [#953](https://github.com/klum-dsl/klum-ast/issues/953),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) and the
[S7 acceptance plan](input-round-plan-4-2.md#ordered-slices-and-owners).
Retained identity contract: [#954 Stage B](evidence/issue-954-stage-b.md).
Integration base after merged PR #1007: `97a6775e580f3164a5ec18019312bf871512ac65`.
The #953 public boundary is generated Factory creation and Builder application; the reader is internal. Template
`FromStruct` is supplied by S6; common/keyed Factory and Builder application delivery remains outside S7.

## Delivered contract

`klumSchema.jsonSchema.bodyRootTypes` explicitly selects fully qualified Model binary names for caller-pre-keyed
Builder bodies. `rootTypes` keeps self-contained semantics; both are independent Gradle `@Input` sets, and the same
Model may occur in both. The task emits `<Model>.schema.json` and `<Model>.builder-body.schema.json` into the same
owned output directory. The purpose suffix contains a hyphen, which cannot occur in a valid Model binary name, so the
two artifact identities cannot collide. Either set may be empty; an entirely empty selection retains the existing error.
The body selection defaults to empty, including on directly registered generation tasks.

The private projector now receives both purposes through a JDK-only isolated-loader boundary. It enters the existing
member/keyless definition machinery at the selected root position. Default successful documents remain unchanged.
Keyed bodies omit the actual external key from a closed object; ordinary body properties retain admission, including
external-name overlap with suppressed identity. Unkeyed roots have equal contents in distinct output artifacts.
Direct caller-key children still require their keys; fixed/derived/Cluster children and keyed Map values omit theirs.
Recursive mixed-position definitions and escaped references reuse the existing rules. Private definition suffixes are
not configuration values or public output names. All roots project before any output or manifest mutation.

No runtime, compiler, generated Builder/Factory ABI, key derivation, naming/admission grammar, Template behavior or
file/URL API changes. No editor association/discovery policy is introduced. Compiled contracts remain authoritative;
no user initializer, converter, key provider, lifecycle or validator executes during inspection. Structural schema
validity still does not promise successful construction or completed validation. S6 and other structured-input slices
remain independent.

## Acceptance evidence

All Spock features below live in `KlumJsonSchemaGenerationTest` and carry `@Issue('953')` (the amended local compilation
feature also retains #529). The selection example has `@Tag('documentary')` and a reciprocal `@See` link to
[the user configuration](../user/Json-Schema.md#caller-pre-keyed-builder-bodies).

| Criterion | Evidence |
| --- | --- |
| Both purposes for the same binary root; external inherited key; mixed recursive child rules | `select self-contained and Builder-body schemas for the same binary roots with Groovy #generation`; Groovy 3/4/5 source-free JAR inputs, Groovy 5 packaged plugin |
| Ignored/colliding root key independently representable as body; ordinary overlapping property retained | Same feature: `Ignored`, `Collision`, escaped `FallbackKey` and inherited `ServiceApi#name` |
| Locally compiled roots plus binary dependency | Amended `default classpath compiles local roots and resolves their binary Schema dependencies`: keyed Local default/body and inherited region |
| Distinct purpose cache input, unchanged default output, configuration-cache reuse, build-cache restore and relocation | `Builder-body selection is a cache input with configuration reuse and relocation` |
| Body-only selection, root/purpose removal, manifest ownership and unrelated files | `removing selected purposes and roots deletes only last-successful owned outputs` |
| No partial/stale-output replacement on failure | `a failed body selection preserves every successful artifact and manifest: #model`: missing/invalid/Builder roots, unrepresentable direct children and corrupt body-root metadata |
| Independent emitted-schema semantics and parity | `validate_builder_body_schema.py`: 24 emitted artifacts, 51 accepted / 69 rejected instances; default/body key contrast, recursive/Map/fixed/derived/Cluster positions, escaped keys, ignored/colliding cases and Groovy byte parity |
| No-user-code and retained projections | Existing plugin suite retains initialization positive controls, throwing Schema/enum/default/key-provider/operation/lifecycle fixtures, constraints and compiled converter projection |

## Initial validation before integration

The affected plugin suite passed **664 tests / 27 suites**, zero failures/errors/skips, in **12m 30s**. Plugin task
validation, production/test licenses and compilation passed. The final S7/local-compilation selection passed **12 tests**
with zero failures/errors/skips after isolating its exported validation files from existing oracle globs; the same run
passed `verifyVersionedDocumentationRenderer` and test licenses in **58s**. Markdown links/anchors, fenced blocks,
Python syntax and `git diff --check` passed.

The external validator uses `jsonschema==4.23.0` as evidence tooling only. S7 passed 51 accepted / 69 rejected instances
and byte parity across Groovy 3/4/5. All six retained emitted-output oracles also passed: positional keys, integral,
decimal, advisory deprecation, composed constraints and converter parameters (including the JDK/ECMA comparison).
No pre-existing test is ignored or suppressed.

```shell
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest.*Builder-body*' \
  --tests '*KlumJsonSchemaGenerationTest.*body selection*' \
  --tests '*KlumJsonSchemaGenerationTest.*selected purposes*' --no-parallel --max-workers=2 --offline
./gradlew :klum-ast-gradle-plugin:test :klum-ast-gradle-plugin:validatePlugins \
  :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest --no-parallel --max-workers=2 --offline
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest.*Builder-body*' \
  --tests '*KlumJsonSchemaGenerationTest.*body selection*' \
  --tests '*KlumJsonSchemaGenerationTest.*selected purposes*' \
  --tests '*KlumJsonSchemaGenerationTest.default classpath*' :klum-ast-gradle-plugin:licenseTest \
  verifyVersionedDocumentationRenderer --no-parallel --max-workers=2 --offline
python klum-ast-gradle-plugin/src/test/resources/validate_builder_body_schema.py klum-ast-gradle-plugin/build/schema-validation
```

Risk tier: localized Gradle plugin generation behavior, with affected TestKit/schema/compiled/binary/packaged fixtures.
No compiler, runtime, generated ABI or JPMS surface changed, so separate runtime Groovy lanes and binary/JPMS module
checks are unnecessary; the generator fixtures directly exercise compiled Groovy 3/4/5 contracts. No blanket root
`check` or local SonarCloud upload. Hosted PR CI remains the complete-build and Sonar qualification.

## Integration after S6 and the public API clarification

The explicitly authorized rebase retains master's S3a converter-origin, S4 command, S6 Template and #953 design changes.
Conflict resolution keeps the full ADR/implementation plan and all existing changelog entries. Current user guidance
now uses the public Factory/Builder boundary and Schema/parsed-payload grammar examples; it no longer recommends the
development reader. No remaining `FromStruct` or `applyStruct` implementation is delivered by this change. Development
interpreter calls in older qualification fixtures remain internal test controls pending A3, rather than public examples.

`CompiledConverterJsonSchemaTest` additionally qualifies body/default parity for source-free S3a converter artifacts and
both root purposes for a keyed compiled Schema with S4 commands and S6-generated Template Factory contracts. Empty
command objects remain closed, source prose and String-operation selection are preserved, root keys retain their
purpose, and Factory/Template helpers do not gain grammar admission. The external S7 oracle now includes these actual
emitted command artifacts, in addition to the original recursive/positional corpus.

Fresh integration qualification on code/test revision `58ac14438da65a8982f749794ba48f1977fd7896` passed the affected
plugin module's **664 tests / 27 suites**, zero failures/errors/skips, plus task validation, production/test licenses,
compilation and `verifyVersionedDocumentationRenderer` in **11m 49s**. All seven emitted-output oracles passed; the
converter oracle additionally qualified S3a's 210 structural String controls. The S7 oracle now validates **30 artifacts,
69 accepted / 102 rejected instances**. Original recursive/positional families retain byte parity across Groovy 3/4/5;
new command families retain full schema/diagnostic parity after normalizing only temporary artifact-file locators in
`x-klum-unsupported`. Those locators intentionally identify each fixture's distinct binary artifact and are not schema
assertions. All emitted schemas are validated without removing their diagnostics.

The first added command fixture put a key below an unkeyed parent; it was corrected to declare the key on the root
Schema, preserving existing inheritance rules. The initial strict byte comparison then identified those artifact URLs;
the bounded oracle correction preserves all remaining diagnostic text and assertions. Its rerun and Markdown/link/fence,
Python syntax, license and diff checks qualify the final helper/prose follow-up; production and Spock inputs are unchanged.
The initial counts above remain historical qualification. Final-head review and hosted CI outcomes are recorded in the
PR follow-up and handoff; earlier-head CI is not reused for the rebased revision.

Integration command:

```shell
./gradlew :klum-ast-gradle-plugin:test :klum-ast-gradle-plugin:validatePlugins \
  :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest \
  verifyVersionedDocumentationRenderer --no-parallel --max-workers=2 --offline
```


## Delivery boundary

Tracker impact: **Related: #953**, S7 only; no issue closure or curation/release-gate change. #954 positional behavior
is retained. No Schelm contact or consumer qualification is performed. Independent final-head Standards/Specification
review outcomes and curated commit/draft-PR identities are reported in the worker handoff and pull request.
Merge and Hive reconciliation remain external.
