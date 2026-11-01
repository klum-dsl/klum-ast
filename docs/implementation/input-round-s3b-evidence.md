# S3b — deterministic CharSequence scalar routes

Related: #952. Authority: [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[S3b acceptance](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice),
[S2 selection](input-round-s2-evidence.md) and [S3a origins](input-round-s3a-evidence.md).

## Stack and delivery boundary

Base: `39e481e1d532c0f1a8145e79faa9ec6f8eee1f8c`, the assigned S3a PR #1004 head.
Worktree: `/Users/stephan/.codex/worktrees/2e62/klum-ast`.
Branch: `codex/952-charsequence-scalar`.
This is speculative local work stacked behind S3a. No push, PR creation, GitHub issue edits, merge or self-archive is
permitted. Tracker relationship is **Related: #952**, never closure. Hive owns later tracker/documentary-link and delivery
reconciliation. No curation status is changed by this implementation.

Single resume condition: **explicit resume after S3a PR #1004 is verified merged, to rebase only S3b once onto master and
rerun checks invalidated by integration**. Until then retain this clean worktree and local commits.

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

## Local qualification

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

## Reviews and commit history

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
records completed qualification only. The worktree pauses clean, without publication or self-archive; Hive retains
integration, tracker and eventual archive reconciliation under the single resume condition above.
