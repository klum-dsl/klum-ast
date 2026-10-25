# #959 P1: direct integral authoring profile

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
composed/repeated rules are described whole and never peeled. Source descriptions are preserved. Empty intersections
fail with the source, effective name and Builder. Missing/unsupported-version metadata adds artifact and recompilation
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

Final affected-suite, root-check, review and delivery results are recorded below when complete.
