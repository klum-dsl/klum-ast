# #1055 S1: raw-input field-presence contract

Date: 2026-11-08. Status: **S1 implementation; projection and release qualification remain separate**.
Authority: [#1055](https://github.com/klum-dsl/klum-ast/issues/1055),
[approved plan](issue-1055-raw-input-required-plan.md#s1--capture-and-validate-one-complete-field-presence-fact)
and [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#raw-input-field-presence--1055).
Base: `db79ccf747769b72e8a473b55a96302ef8054c72`, the merge of planning PR #1056.
Work is isolated on `codex/1055-s1-required-fact`; the original user checkout is untouched.

## Delivered S1 boundary

`Struct.Required` is a FIELD-only, documented, runtime-retained marker with no members or transform.
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

## Focused verification

The new `StructRequiredTest` carries class-level `@Issue('1055')` and covers annotation target/retention,
marked/unmarked fields, getter/setter source association, shared field/operation accessors, Groovy `eMail`
capitalization, separately compiled inheritance and descendant KEBAB_CASE, invalid source declarations,
absent/false normalization, wrong-type/duplicate facts, true-plus-exclusion and operation displacement.
Byte mutations use the existing ASM fixture pattern; initialization traps remain silent during contract reads.

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
Commit-history review keeps S1 as one coherent producer/reader/resolver change with its passing tests and evidence.
The final branch contains no unrelated changes.

Delivery authorization audit: gh CLI/API authorized; SSH git push authorized by a successful branch dry-run.
No GitHub App delivery channel is used.

Tracker impact: `Related: #1055`; the parent issue remains open. No labels, milestones or curation state are changed.
Draft PR delivery is requested; merging, final v1 freeze and release qualification remain Hive-owned.
S2 depends on this optional fact to build the sorted containing-object `required` union while preserving construction
keys and unchanged property fragments. S3 owns final binary/JPMS/candidate qualification and shipped documentation.
