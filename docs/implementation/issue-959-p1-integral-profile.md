# #959 P1: direct integral authoring profile

## Current numeric projection rule — PR #972

Supported numeric constraints are projected faithfully, including mutually contradictory bounds. JSON Schema generation does not perform numeric-domain satisfiability analysis.

This supersedes the earlier contradiction-rejection and integer-feasibility requirements, including merged #966.
Historical checks and experiments below describe their original revisions. Missing/malformed/incompatible facts
and structural projection errors retain fail-closed handling and output atomicity.

Date: 2026-10-25. Scope: bounded implementation of accepted D1/D2; issue #959 remains open.
Base: `0fe8938fcda25517a04b6082921bba589a4ada83` (current master including merged D1 and D2).
Tracker impact: **Related: #959**. No milestone, issue-state or broader release-gate change.
Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#minimal-integral-production-profile--959-p1),
[D1](issue-959-schema-projection-feasibility.md#accepted-d1-metadata-authority-and-missing-artifacts),
[D2 semantics and runtime evidence](evidence/issue-959-d2-raw-input-profile.md).

## Delivered behavior and seams

The consumer deliberately selects `klumSchema.jsonSchema.integralConstraints = true`; selecting generation alone remains
structural. The Boolean is a tracked Gradle task input. It crosses the existing isolated tool boundary as a JDK value,
without retaining loaded contracts in configuration state. Existing output names, manifest/stale handling and loader
isolation remain in place. The artifact identifies its raw-input policy and final-validation limitation.

The compiler enriches existing public Builder declarations. `GeneratedInput.Schema.constraintVersion=1` and explicit
`groupRedefined` establish coverage; explicit field `constraints` arrays establish complete field facts, including no
constraints. Inert nested facts hold annotation identity, deterministic authored attributes, and direct Min/Max Java-long
values with empty-groups/payload eligibility. Ordinary compiler annotation-constant folding handles literal or symbolic
long constants. Capture does not evaluate defaults, lifecycle, converters or validators. Bean constraints remain on their
original sites, not executable Builder methods. This is internal generated linkage, with no contributor SPI.

`CompiledBuilderSchema` retains admission, exact source association and name authority. Its existing exact-resource,
origin-checked public byte reader decodes the new strings/longs/booleans without Bean annotation proxies. Only enhanced
projection requires the version/completeness facts on reachable root, inherited and owned contracts. No Model resource
is read to supply facts. Model types can still be resolved from public signatures, as qualified by ADR 0031.

The private projector maps only one direct Jakarta Min and/or Max on admitted ordinary integral scalar properties,
intersecting inclusive bounds with machine limits. Effective hierarchy group redefinitions suppress hard projection.
Complete unsupported direct field declarations retain labelled runtime descriptions of explicitly authored attributes;
composed/repeated rules are described whole and never peeled. Source descriptions are preserved. Contradictory numeric
assertions now generate successfully, superseding the rejection delivered in #966. Missing/unsupported-version metadata adds artifact and recompilation
instructions. Failure occurs before output replacement, preserving successful files and manifest.

Omission and explicit-null admission remain structural; NotNull/Required never infer requiredness. No defaults/consts,
operation/converter argument bounds, effective-layer prediction or final Model validation equivalence is introduced.
Groups/payload extensions, composed/repeatable interpretation, decimals/Size/Pattern, quantities, relationship closures,
contextual defaults and provider configuration remain deferred. Nullable runtime validation remains #962.

## Test-first evidence

Before production edits, the focused packaged structural generator (Groovy 3/4/5 producers) and retained D2 runtime
characterization passed at the base. The first new documentary test failed on the missing `integralConstraints` property,
then passed after the first complete compiler-to-artifact implementation.

Subsequent red/green cycles exposed and corrected omitted custom constraint containers, nondeterministic nested
annotation descriptions, missing symbolic-long bounds, and incomplete source/external-name context on metadata errors.
A fixture import conflict between Klum Default and the Bean Default group was corrected with a Groovy import alias.
Those unsuccessful runs are diagnostic evidence, not qualification passes.

The packaged producer/consumer tests delete producer sources and use separate API/Schema JARs. The consumer has no
Jakarta API/provider dependency: constraint facts come from public Builders alone. Model static initializers, source
initializers/defaults, operations/lifecycle and custom validator traps remain silent. The existing structural test includes
a separate deliberate Model-initialization positive control. The retained D2 fixture uses the real structured-input path
and Bean adapter to establish later repair/invalidation/default/conversion behavior independently.

`KlumJsonSchemaGenerationTest` adds the documentary feature
`generate opt-in integral authoring bounds from binary Builder contracts with Groovy #generation`, linked to
[the user contract](../user/Json-Schema.md#opt-in-integral-constraints). It covers the off-by-default switch, compiler
versions, direct and inherited/renamed/nested/ignored inputs, all nine integral target forms, exact Java-long endpoints,
machine intersections, unsupported contexts/descriptions, unchanged requiredness, and construction non-projection.
Stripped root/base/child completeness markers, a future version and missing field facts characterize fail-closed enhanced
behavior while preserving structural output. Separate impossible integral and machine-bound fixtures verify diagnostics.

The independent external corpus is
[`validate_integral_constraint_schema.py`](../../klum-ast-gradle-plugin/src/test/resources/validate_integral_constraint_schema.py),
run with `jsonschema==4.23.0` against emitted files. Each producer generation passes its metaschema and 55 accepted /
87 rejected inputs, including 0/100/100.0, -1/101, absence, explicit null, zero, false, empty/numeric Strings, fractions,
nested inputs, renames, operations, exact long endpoints, machine limits and unsupported selection contexts. Enhanced
output is byte-identical across Groovy 3/4/5. This corpus is manually invoked external tooling, not a Gradle test claim.

## Final validation and delivery

The affected G3 run passed 49 `CompiledBuilderSchemaTest`, 32 packaged-generator cases and 40 retained D2 runtime cases,
with no failures/errors/skips. A separate cache/relocation run then passed both structural and enhanced profiles.

The first independent Standards review found no actionable issues. Specification review found two fail-closed gaps:
missing eligibility/bound members could weaken a bound to runtime text, and malformed array shapes could be discarded.
Both were reproduced with binary mutations and corrected in `22555b252`: every rule now carries explicit eligibility,
each direct Min/Max requires its bound even with unsupported group/payload context, and invalid array items reject.
Six mutation regressions cover missing eligibility, missing eligibility plus value, missing value on grouped Min, and
primitive/enum/nested-array items. The corrected focused suite passed all 14 cases (mutations, missing-artifact facts,
and the three producer generations). Both independent reviewers rechecked the correction against the original base
and reported zero remaining actionable findings.

An initial root-check run was intentionally stopped after these review findings; it is not a qualification pass.
The corrected source tip then completed the full JDK 17 root `check --no-parallel --max-workers=2` successfully
(140 actionable tasks; 16m27s). A temporary diagnostic init script changed only exception logging. This includes G3/G4/G5,
license checks, lane isolation, Gradle-plugin validation, coverage aggregation and documentation-renderer verification.
JUnit reports contain 3,042 G3 cases (17 existing skips), 2,616 G4 cases (16 existing skips) and 2,616 G5 cases
(16 existing skips), with no failures/errors. All 39 JSON Schema regression cases pass without skips; this slice adds no
ignored/pending tests.

A preceding broad run reported source-mirror integration failures and was stopped for diagnosis. The original first
case passed alone, the full source-mirror class passed, and the complete root retry passed without any production
change. The failure was not reproduced; no speculative fix or suppression was added. The successful retry, rather
than that interrupted attempt, supplies final root evidence.

The external structural/enhanced corpora pass again on the corrected emitted artifacts: 18/3 metaschemas, 195/426 input
checks, with cross-generation byte equality. Relative documentation targets and `git diff --check` pass.

The commit history was reviewed before publication: feature capture/projection, qualification, documentation, and the
focused review corrections are separate coherent steps. Reviewed local corrections remain additive; no unrelated work
is included. The final follow-up changes only documentation/evidence and therefore needs no repeated Groovy lanes.

Delivery authorization audit: Git SSH branch publication **authorized** (non-mutating push dry run); GitHub CLI repository
mutation **authorized**. Tracker relationship remains **Related: #959**; no issue/milestone/curation/release-gate change.
The assigned delivery is a draft PR, with remote CI/SonarCloud results reported separately for its actual head.

## SonarCloud review follow-up

The initial PR-head analysis reported eleven findings: one ignored numeric-conversion result, two cognitive-complexity
findings, one nested ternary, six repeated-literal findings, and one digit-regex simplification. The follow-up preserves
published history and addresses all eleven without suppressions. Property emission and bound intersection now have
focused private helpers; exact Java-long conversion supplies the projected BigInteger value. Shared constants and the
equivalent default Java digit class remove the remaining duplication/regex findings. No generated ABI or intended
projection behavior changes.

Local follow-up qualification: all 39 packaged JSON Schema regressions pass, including producer Groovy 3/4/5. All
21 emitted artifacts are byte-identical to the reviewed revision. The external validator corpora still pass 195 structural
and 426 enhanced inputs. `CompiledBuilderSchemaTest` passes all 49 cases in each of G3/G4/G5; the three affected source
license checks, Gradle plugin validation and diff check pass. Both independent reviewers report no actionable findings.
Remote CI/SonarCloud must confirm the new published head; local validation does not claim a server-side quality-gate result.
