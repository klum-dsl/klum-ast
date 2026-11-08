# #1055 S1: raw-input field-presence contract

Date: 2026-11-08. Status: **S1 implementation; projection and release qualification remain separate**.
Authority: [#1055](https://github.com/klum-dsl/klum-ast/issues/1055),
[approved plan](issue-1055-raw-input-required-plan.md#s1--capture-and-validate-one-complete-field-presence-fact)
and [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#raw-input-field-presence--1055).
Base: `db79ccf747769b72e8a473b55a96302ef8054c72`, the merge of planning PR #1056.
Work is isolated on `codex/1055-s1-required-fact`; the original user checkout is untouched.

## Delivered S1 boundary

`Struct.Required` is a FIELD/ANNOTATION_TYPE, documented, runtime-retained marker with no members or transform.
The [maintainer clarification](https://github.com/klum-dsl/klum-ast/issues/1055#issuecomment-6720271506) supersedes the
initial FIELD-only target. The established `StructuredInputConstraintMetadata` compiler projection-capture seam resolves
direct and transitively domain-contributed markers, with cycle detection, into the same boolean fact. It neither copies
annotations onto fields nor changes the existing `PropagateAnnotation`/annotation-collector mechanisms.
Compiler admission rejects the marker on ignored, effectively Jackson-excluded, key, Owner, transient,
static and unsupported fields, including declarations without public accessors. It emits `rawInputRequired=true`
only on the exact existing `GeneratedInput.Field` accessor association, including setter fallback.
The marker does not admit a new field or change generated callable signatures.

The exact-byte reader rejects a supplied nonboolean/duplicate value and true combined with ignored/excluded facts.
Omission and explicit false normalize identically. Existing required facts and protocol version checks remain intact;
`Schema.version` stays 1. There is no legacy reader, fallback, version negotiation or mandatory false emission.
The immutable `CompiledBuilderSchema.Entry` retains the bit through inherited and naming-profile copies.
Final resolution verifies every true declaration survives as its exact selected field, so an authored operation,
override, collision or wrong accessor association cannot turn it into a phantom requirement. Operations remain false,
even when field and operation metadata share one public accessor.

The importer reads the shared contract but does not enforce presence. No JSON Schema projection, null admission,
lifecycle/default/Template behavior, runtime validation, Jackson mapping or constraint projection changes.
This preparatory matched-toolchain slice is not a releasable feature alone. Shipped user documentation,
CHANGES and documentary coverage remain deferred with S2/S3 as explicitly assigned.

## Focused verification — initial S1 checkpoint

At the initial checkpoint, `StructRequiredTest` carried class-level `@Issue('1055')` and covered annotation target/retention,
marked/unmarked fields, getter/setter source association, shared field/operation accessors, Groovy `eMail`
capitalization, separately compiled inheritance and descendant KEBAB_CASE, invalid source declarations,
absent/false normalization, wrong-type/duplicate facts, true-plus-exclusion and operation displacement.
Byte mutations used the existing ASM fixture pattern; initialization traps remained silent during contract reads.

The focused filter set is:

```text
*StructRequiredTest
*StructuredInputAdmissionPlanTest
*CompiledBuilderSchemaReaderTest
*CompiledBuilderSchemaTest
*InputNamingProfileTest
*StructuredInputRuntimeTest
*StructuredFactoryTest
*JpmsPackageBoundaryTest
```

Each lane is invoked separately as `./gradlew :klum-ast:<lane> --tests '<filter>' ... --console=plain`,
repeating `--tests` for each filter. This exercises the new compiler/reader/schema seam and existing naming,
importer/Factory and artifact-package controls, using Java 17 test workers.

| Check | Result |
| --- | --- |
| Focused G3 `test` | 239 cases passed, no failures or skips |
| Focused G4 `groovy4Tests` | 239 cases passed, no failures or skips |
| Focused G5 `groovy5Tests` | 239 cases passed, no failures or skips |
| Affected license checks and test-lane isolation | Passed |
| Final `git diff --check` | Passed |

Test-first evidence: the initial focused run failed without the annotation/fact; it passed after implementation.
The independent Specification review then found Groovy's special capitalization rule for `eMail`; a focused
regression reproduced the rejection before switching the accessor check to compiler-equivalent `BeanUtils.capitalize`.
One earlier combined G4/G5 invocation accidentally left G4 unfiltered because Gradle applies test filters to the
preceding task. It was stopped and is not counted as qualification; both final lanes use separate filtered invocations.
The isolation task also schedules full test prerequisites by default; that invocation was stopped and rerun with
`-x :klum-ast:test -x :klum-ast:groovy4Tests -x :klum-ast:groovy5Tests` to reuse the completed focused lane evidence.

Per the bounded S1 assignment, full affected module suites, root `check`, long TestKit/plugin suites and the full
binary/JPMS/schema-validator qualification are omitted locally. Existing source-free compiled-contract and package
controls above are focused evidence, not full named-module or release-candidate qualification. S3 and exact-head hosted
CI/Sonar remain required before release acceptance. No local Sonar upload or workflow dispatch is performed.

## Review and delivery

Independent Standards review initially reported two multiline guards lacking braces; both are corrected.
Independent Specification review reported the capitalization mismatch; the regression and implementation correction
address it. Both reviewers rechecked the follow-up and reported zero remaining actionable findings.
Initial commit-history review kept S1 as one coherent producer/reader/resolver change with its passing tests and evidence.
The subsequent clarification is an additive follow-up; the reviewed original is preserved.
The final branch contains no unrelated changes.

Delivery authorization audit: gh CLI/API authorized; SSH git push authorized by a successful branch dry-run.
No GitHub App delivery channel is used.

Tracker impact: `Related: #1055`; the parent issue remains open. No labels, milestones or curation state are changed.
Draft PR delivery is requested; merging, final v1 freeze and release qualification remain Hive-owned.
S2 depends on this optional fact to build the sorted containing-object `required` union while preserving construction
keys and unchanged property fragments. S3 owns final binary/JPMS/candidate qualification and shipped documentation.

## Maintainer clarification follow-up — domain annotation composition

The original published commit `ba89ea38f2289088fb9aba560ddcdac069e34dd9` is preserved.
An additive follow-up implements the canonical issue clarification at the existing compiler projection seam.
`StructRequiredTest` now verifies direct, one-hop and transitive domain contributions, existing explicit propagation,
cycle termination and fields inherited/renamed from a separately compiled parent. The child also consumes a separately
compiled domain annotation. A contributed marker does not bypass field exclusion, create an operation requirement,
change defaults or enforce missing values during ordinary DSL/FromStruct construction.

Test-first result: 24 focused presence cases ran against the prior implementation; four failed for the new target,
domain, exclusion and binary-inheritance controls. All 24 passed after the change.
Revised validation uses the eight original filters above plus `InputConstraintDescriptionTest`,
`ConstraintProjectionTest` and `AnnotationPropagationTest`. Each lane runs separately with those eleven filters.
G3 `test`, G4 `groovy4Tests` and G5 `groovy5Tests` each passed **254 cases** (762 total), with no failures, errors or skips.
Affected annotation/compiler license checks, lane isolation with full test prerequisites excluded, Markdown
relative-target/fence checks and `git diff --check` passed. Independent Standards and Specification reviews each have
zero remaining actionable findings after correcting the distinction between the original and follow-up evidence.
The earlier 239-per-lane checkpoint above remains dated evidence for the original published commit, not a claim
about the new head. Full module/root, long TestKit and full binary/JPMS/schema-validator qualification remain omitted
under the same bounded assignment. No unfiltered suite was scheduled for this follow-up.
S2 JSON Schema output and S3 release qualification remain separate. The follow-up adds no runtime, reader, protocol
or generated callable change. Shipped user documentation and CHANGES remain deferred as assigned.

## Hosted CI fixture follow-up

The first completed hosted run on `14454d4fe0ee4b3825b362121771d26aefb1eded`
([run 42471547886](https://github.com/klum-dsl/klum-ast/actions/runs/42471547886)) reported 12,038 test cases:
11,968 passed, 49 skipped, 21 failed. All 21 failures were in `BuiltinConstraintCatalogTest` and shared one cause:
its inert reflected Entry fixture still supplied the pre-S1 constructor arguments, omitting `rawInputRequired`.
The changed runtime contract itself was consistent; the plugin catalog fixture needed an explicit false argument.
No production API, projection behavior or compatibility fallback is added for this test-data correction.

`./gradlew :klum-ast-gradle-plugin:test --tests '*BuiltinConstraintCatalogTest' --console=plain`
reproduced all 21 failures before the correction (7 seconds) and passed all 21 afterward (6 seconds).
The affected test license check and `git diff --check` passed. This plugin test seam has one Groovy 3 test lane;
no compiler/runtime code or multi-Groovy test input changes, so the previous G3/G4/G5 production-tree evidence remains
applicable. Long plugin/full-root suites are left to the next hosted CI run. Sonar had not run before this CI failure;
no Sonar cleanliness is inferred. The correction is an additive commit preserving both published S1 commits.
