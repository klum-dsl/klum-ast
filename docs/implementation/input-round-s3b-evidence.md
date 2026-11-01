# S3b — deterministic CharSequence scalar routes

Related: #952. Authority: [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[S3b acceptance](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice),
[S2 selection](input-round-s2-evidence.md) and [S3a origins](input-round-s3a-evidence.md).

## Integration and delivery boundary

Integrated base: `97a6775e580f3164a5ec18019312bf871512ac65`, fetched from origin/master on 2026-11-01.
S3a PR #1004 is verified merged at `f71d0cea1ddd100d24b8106b7f64dc37e819c988`, an ancestor of that base.
Original stack base: `39e481e1d532c0f1a8145e79faa9ec6f8eee1f8c`.
Worktree: `/Users/stephan/.codex/worktrees/2e62/klum-ast`.
Branch: `codex/952-charsequence-scalar-routes` (renamed from `codex/952-charsequence-scalar` on explicit resume).

The S3a-merge pause prerequisite is satisfied. The authorized rebase replayed only the five S3b commits onto current
master. The resumed assignment authorizes local validation/review, pushing this branch and opening a draft PR. No merge,
unrelated public API change, issue closure or self-archive is authorized. Tracker relationship remains **Related: #952**;
Hive retains issue, release and archive reconciliation. No curation status is changed by this implementation.

## Master integration validation

The rebase preserves merged S4 commands, structured Template support and the #953 public-API decisions. Conflict
resolutions retain S3a's possessive descriptor regex, extend the compiler's existing override filter to the exact scalar
type, preserve command regressions in the named-module fixture, and keep both command and CharSequence schema properties.
`git range-diff` confirms that these integrations and slice-status prose account for the rebased patch differences.

Two newer String Duration fixtures explicitly exclude `parse` to preserve their intended route under S3b competition:
S3a's long-descriptor regression and the structured Template documentary fixture. A new S3b regression composes an exact
CharSequence conversion, an existing zero-argument command and an existing structured Template recipient. No public
entry-point or Template/command implementation was changed for that regression.

The integrated focused run passed 158 Groovy 3 cases across CharSequence, converter-origin, selection, command and Template
suites, the G4 named-module consumer case, and all 8 packaged-schema cases, without failures/errors/skips:

```shell
./gradlew :klum-ast:test --tests '*StructuredCharSequenceTest' --tests '*StructuredConverterOriginTest' --tests '*StructuredOperationSelectionTest' --tests '*StructuredCommandTest' --tests '*StructuredInputTemplateDocumentaryTest' :klum-ast:groovy4Tests --tests '*JpmsPackageBoundaryTest.a real schema and consumer*' :klum-ast-gradle-plugin:test --tests '*CompiledConverterJsonSchemaTest' --no-parallel --max-workers=2 --console=plain
```

The first focused integration run exposed the newer long-descriptor fixture's implicit `Duration.parse` competition.
Its explicit exclusion above corrected the fixture; the successful focused and root runs include that correction.

Clean integrated implementation/test head `efe6c0d083dc33892c87d98f227fe70b811c6e27` passed root `check` on
2026-11-01 in **25m 15s**: 140 tasks, 73 executed and 67 up to date. All final JUnit XML reports were inspected:

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 3,728 | 0 / 0 | 17 |
| Groovy 4 compatibility | 3,053 | 0 / 0 | 16 |
| Groovy 5 compatibility | 3,053 | 0 / 0 | 16 |

The compiler contributes 2,699 / 2,701 / 2,701 cases, and the Gradle plugin contributes 653 baseline cases. The run
includes binary and named-module consumers, packaged G3/G4/G5 schema producers, lane isolation, license checks,
plugin validation and the documentation renderer. S3b adds no suppressed test.

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --continue --no-parallel --max-workers=2 --console=plain
```

After root success, `validate_converter_schema.py` passed **28 JDK checks, 270 ECMA/Draft C1 checks and 270 structural
String checks** against the actual emitted G3/G4/G5 schemas, using jsonschema 4.23.0, Node v24.19.0 and Java 17.
All three schemas have SHA-256 `5b0e6804fd4bb63f0ced84becd8cdad587795a1a22a6fa573defb1a12a59805d`.
Markdown fences, 200 relative file links, documentary anchors and `git diff --check` passed on the integrated diff.

The same two independent read-only reviewers renewed their full reviews against integrated base
`97a6775e580f3164a5ec18019312bf871512ac65` at `efe6c0d083dc33892c87d98f227fe70b811c6e27`:
**Standards: zero remaining required findings. Specification: zero remaining findings.** They confirmed the rebase
preserves upstream command, Template, descriptor and schema behavior. The earlier optional ASM-helper extraction
remains deferred for the reasons recorded below. This final evidence update changes no production, test, fixture or
build input; the implementation's qualification remains bound to the clean head above.

The shared compiler/runtime hook and overlapping master changes require the full-check tier under merged PR #1006
(`52b9e0d97d181e8f2ad2222c50ae3a176ea4bfb4`). No local SonarCloud upload occurred. Hosted CI and SonarCloud still
require inspection on the published PR head; local success is not hosted qualification.

The reviewed local history contains the five rebased S3b commits (`9b714d705`, `01d38a4b2`, `af3cfef68`, `fb1eff772`,
`1c3b1edc0`) followed by the additive integration correction `efe6c0d08` and this evidence-only handoff update.
Delivery preflight outcomes: **Git branch push: authorized** (dry run); **GitHub CLI repository write: authorized**.
No existing PR was found for this branch. The next authorized delivery is a draft PR with **Related: #952**; no merge,
issue mutation or archive action is part of this handoff. The pre-integration results below are retained as history.

## Implementation and review focus

The existing strict scalar interpreter accepts only actual Strings for selected public CharSequence signatures.
A generic parameter merely erased to CharSequence is not a concrete CharSequence signature.
The compiler records only already-generated exact String/CharSequence converter projections, and retains their exact
source descriptor. Both compiler provisional selection and runtime selection apply S2 authority without a specificity
preference. An authored marked CharSequence method can win over a String peer. Competing generated converter routes
remain excluded; an explicitly marked wrapper or converter exclusion supplies the authoring escape.

The existing invocation path picks and checks the exact public Builder method. Generated scalar CharSequence converters
directly link a protected runtime hook, preserving named-module access. That hook passes a Groovy typed argument wrapper
around the original value to ordinary converter dispatch, so a more-specific String overload cannot replace the recorded
CharSequence route. This is a dispatch type hint, not a new mutable character sequence, generic coercion or reflective
converter discovery. Existing String converter dispatch is unchanged.

CharSequence converter associations are structural-only: C1 selected-parameter authority remains exact String only.
The runtime validates structural source descriptor parameters against the selected public signature. Schema projection
emits `type: string`; domain field constraints never become raw-input assertions. No converter or Schema code executes
for inspection/generation. The generated hook requires matched compiler/runtime/plugin and recompiled Schema artifacts;
protocol marker 1 alone is not a mixed-artifact compatibility promise.

No container routes, String-to-DSL conversion, new discovery, scopes, Templates, body schemas, factories or S1 traversal
changes are included. One deliberate migration consequence is that a String closure plus an implicit CharSequence
factory now competes. The documented Duration closure explicitly excludes `parse`; the direct Duration example needs
no closure.

## Acceptance evidence

| Requirement | Evidence |
| --- | --- |
| Actual projected Duration route, direct equivalence and exact descriptor/source | `StructuredCharSequenceTest` documentary example linked to [Converters](../user/Converters.md#structured-string-input) |
| Original String identity and exact marked signature despite String peer | Same test: authored and converter route-distinguishing controls, constructor/factory controls |
| Strict input; no generic or container expansion | Same test: numeric, boolean, null, GString, arbitrary Object, StringBuilder/StringBuffer/custom mutable CharSequence, maps/lists, Serializable and container signature negatives |
| S2 authority | Same test: unique unmarked, marked CharSequence, competing generated routes, marked wrapper, multiple marks; retained `StructuredOperationSelectionTest` |
| Inheritance and source association | Same test: separately compiled parent/child/grandchild plus renamed/ignored overrides; retained C1/structural binary tests |
| Damaged metadata | Same test: removed projection/association/authority, malformed authority/descriptor; retained S2 and C1 corruption matrices |
| Failure attribution | Same test: source/path and original DateTimeParseException |
| Truthful output, C1 unchanged, no execution | `CompiledConverterJsonSchemaTest`: packaged G3/G4/G5 source-free producers, inherited Duration and marked CharSequence operation, traps and explicit initialization positive control |
| Named modules and direct generated linkage | `JpmsPackageBoundaryTest`: Java/static Groovy consumers and bytecode linkage controls |
| Actual emitted grammar | `validate_converter_schema.py`: Draft 2020-12 structural String corpus alongside retained JDK/ECMA C1 oracle |

## Pre-integration local qualification

Clean implementation/test head `9b7fdbad74b09149b89bdacca26fa4ed63fda063` passed root `check` on 2026-11-01 in
**24m 43s**: 140 tasks, 63 executed and 77 up to date. All final JUnit XML reports were inspected:

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 3,679 | 0 / 0 | 17 |
| Groovy 4 compatibility | 3,004 | 0 / 0 | 16 |
| Groovy 5 compatibility | 3,004 | 0 / 0 | 16 |

The compiler contributes 2,650 / 2,652 / 2,652 cases, and the Gradle plugin contributes 653 baseline cases. The run
includes affected module suites, binary Java/static Groovy and named-module consumers, the packaged G3/G4/G5 schema
producer matrix, compilation, license checks, lane isolation, plugin validation and the versioned documentation renderer.
S3b adds no suppressed test. This final evidence update changes no production code, test, fixture or build input.

The merged risk-tiered policy from PR #1006 (`52b9e0d97d181e8f2ad2222c50ae3a176ea4bfb4`) governs this stacked assignment.
The new generated-code hook spans compiler/runtime ABI, requiring its shared-contract full-check tier. No local
SonarCloud upload occurred. Hosted PR CI remains the authoritative complete-build qualification after eventual delivery.

Commands used, with Java 17 selected for Gradle:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --no-parallel --max-workers=2 --console=plain
./gradlew :klum-ast:test --tests '*StructuredCharSequenceTest' --tests '*StructuredConverterOriginTest' --tests '*StructuredOperationSelectionTest' --no-parallel --max-workers=2 --console=plain
./gradlew :klum-ast:groovy4Tests --tests '*JpmsPackageBoundaryTest.a real schema and consumer*' --no-parallel --max-workers=2 --console=plain
./gradlew :klum-ast-gradle-plugin:test --tests '*CompiledConverterJsonSchemaTest' --no-parallel --max-workers=2 --console=plain
```

The final focused runtime suite passed 110 cases: `StructuredCharSequenceTest` (43), `StructuredConverterOriginTest` (25),
and `StructuredOperationSelectionTest` (42), with zero failures/errors/skips. The focused G4 named-module consumer and
all 8 packaged-schema cases passed. Root qualification above includes those controls at the final implementation head.

After root success, the independent oracle was rerun against its actual emitted output:

```shell
python klum-ast-gradle-plugin/src/test/resources/validate_converter_schema.py klum-ast-gradle-plugin/build/schema-validation /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java
```

It passed **28 JDK checks, 270 ECMA/Draft C1 checks and 270 structural String checks**. Tools: jsonschema 4.23.0,
Node v24.19.0 and Java 17. All three emitted schemas have SHA-256
`5b0e6804fd4bb63f0ced84becd8cdad587795a1a22a6fa573defb1a12a59805d`.
Markdown fences, 187 relative file links, documentary anchors and `git diff --check` passed.

## Pre-integration reviews and commit history

Exactly two local read-only review children reviewed the diff from the fixed S3a base: **Standards** and **Specification**.
Both renewed at `9b7fdbad74b09149b89bdacca26fa4ed63fda063` with zero remaining findings.

Standards found no hard violations. Its optional suggestion to share the two ASM corruption helpers was deferred:
each small helper owns a distinct fixture/signature matrix, and new fixture infrastructure is unnecessary for this slice.
Specification found a generic parameter erased to CharSequence could bypass ordinary generic rejection. Marked/unmarked
bounded-generic probes reproduced it. Concrete generic-type checks and compiler placeholder guards corrected it, with
runtime and packaged-schema negative controls. Both reviewers accepted that correction.

Validation also corrected a missing `Converters` import in the expanded named-module fixture and accounted for the new
inherited Duration property in a shared schema fixture. Earlier failed/interrupted runs are diagnostic history only;
the successful root run includes every correction.

| Commit | Reasoning step |
| --- | --- |
| `f0b3bb519` | Exact CharSequence scalar admission, dispatch, schema output and acceptance controls |
| `933e92066` | Current user/migration documentation and speculative stack boundary |
| `6a6adb19e` | Review correction: reject generic erasures; repair the named-module fixture import |
| `9b7fdbad7` | Reconcile inherited structural schema output in the shared fixture |

History was reviewed for scope and ordering. Reviewed commits are preserved; fixes are additive. The final local commit
records completed qualification only. That original worktree paused clean without publication. The explicit resume and verified integration above supersede
its pause condition; Hive retains tracker and eventual archive reconciliation.
