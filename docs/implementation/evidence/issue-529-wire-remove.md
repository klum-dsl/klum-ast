# WIRE-REMOVE — final compiled Builder contract

Date: 2026-10-23. Owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Base: `de6337d434b3f46b9f0ccce478bf9c150010f8e9` (master, merged
[WIRE-REPLACE #947](https://github.com/klum-dsl/klum-ast/pull/947)).
Prerequisites: [SCHEMA-R #945](issue-529-schema-r.md), [WIRE-SCHEMA-PROBE #946](issue-529-wire-schema-probe.md),
and [WIRE-REPLACE](issue-529-wire-replace.md). Authority: separately assigned removal slice under
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md).

## Production boundary and compatibility

The source audit finds exactly one production consumer seam: `StructuredInputSupport` reads the package-private runtime
`CompiledBuilderSchema`. The compiler's `StructuredInputAdmissionPlan` still classifies source facts and emits minimal
`GeneratedInput` annotations; it no longer serializes those facts or writes companion classfiles. `GeneratedDslSupport`
and `ModelVerifierTransformation` no longer publish a carrier. No production lookup, decoder or fallback depends on it.
The facade, generated annotations, exact-resource origin checks, selective annotation access, and dispatch remain intact.

The private carrier was internal and unreleased. There is no compiled-carrier compatibility promise or need to retain
old bytes, transport versions or a decoder. Missing required public enrichment still fails with recompile/package
guidance; that current generated-linkage boundary is independently tested by removing the public Schema marker.

## Coverage migration and deletion audit

Before deletion, the migrated focused Groovy 3 corpus passes **91 cases, zero failures/errors, one existing named-module
skip**. The fixed expected values were captured from the previously qualified contracts, inspected as readable fields,
and compared with the final production facade. Tests contain no expectation writer or regeneration path. They compare
every entry/rejection, owner, source, selected descriptor group, recursive payload, naming origin, child and fixed key.

| Retired implementation or assertion | Durable replacement / reason for retirement |
| --- | --- |
| `LegacyStructuredInputPlan` and carrier-specific `StructuredInputPlanReaderTest` branches | `CompiledBuilderSchemaReaderTest` retains packaged roles, separate parent composition, collisions, fresh-JVM accepted/rejected enrichment and initialization traps against production. Encoding/header/DATA damage has no surviving transport to exercise. |
| Test-only `schema.CompiledBuilderSchema`, gap comparator and `admission-gaps.tsv` | `CompiledBuilderSchemaTest` compares production with fixed expected contracts in `CompiledInputExpectedContracts`; no expected-gap allowlist remains. Old comparator damage controls tested the retired comparator rather than a production behavior. |
| `WirePlanCarrierReader`, constant-only/encoding/linkage/lifecycle assertions | Public-contract tests assert no carrier is emitted, retain source-AST classification and in-memory compilation, and qualify actual compiled extraction. The deleted output had no runtime linkage or independent lifecycle responsibility. |
| `SchemaTracerProjector`, `SchemaTracerTest`, `schema-tracer/validate_schema.py` | Disposable carrier-first JSON Schema feasibility experiment, not a production generator or durable facade regression. Original executable proof remains pinned in merged [PR #942](https://github.com/klum-dsl/klum-ast/pull/942) and [SCHEMA-T evidence](issue-529-schema-t.md). Reflective binary extraction is covered by the final corpus; actual projection/validator acceptance must be re-established by the next generator slice. This removal implements no replacement generator. |
| `structured-input-938` source/README/JAR | Obsolete eleven-token internal transport negative. Missing public enrichment is tested directly without retaining carrier classes/resources. Original fixture provenance remains in #938/#940/#947 history. |
| Runtime-boundary probe's carrier name lookup | Reads final facade facts; retains every existing operation-order, nested/keyed traversal and complete-preflight assertion. |
| JPMS private carrier field access and encoded DATA assertions | Actual runtime module reads the facade from closed named Schema artifacts, preserving fixed-key and Java/static/dynamic Groovy qualification without carrier access. |
| SCHEMA-R carrier comparisons | Direct expected final facade facts alongside reflection, docs, enum hazard, missing-dependency and named-module controls. |

These removals do not change #932 interpretation, parser ownership, accepted language, lifecycle, managed Jackson,
or #529/#932 ownership. No public descriptor/API, plugin/generator/CLI, filesystem discovery, issue/milestone state,
user feature, migration or CHANGES entry is introduced.

## Next implementation entry point

The next separately assigned #529 slice is reflective structural projection in a private Schema Gradle-plugin
implementation, using explicit public Builder/Factory roots and an isolated resolved classpath. Start from
`CompiledBuilderSchema`'s observed contract and minimal `GeneratedInput` facts. Its package-private identity remains
internal: reuse/extraction placement must respect the plugin/runtime classloader boundary without publishing a descriptor
API. Re-establish Draft 2020-12 `$defs`/`$ref`, deterministic projection, validator/no-execution tests and subsequent
Gradle invalidation/cache gates from the revised [SCHEMA-0 plan](../adr-0031-schema-0.md). No generator work starts here;
the paused historical SCHEMA-1 branch is untouched. #932 language extensions remain separately selected work.

## Final qualification

Code qualification at `3f69d3d2e57a9fde4cf21361d640fa54df780c6b`:

| Compiler module lane | Cases | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 `test` | 2,303 | 0 | 16 |
| Groovy 4 `groovy4Tests` | 2,305 | 0 | 15 |
| Groovy 5 `groovy5Tests` | 2,305 | 0 | 15 |

The 91-case focused Groovy 3 run preceded the full affected-module command:

```shell
./gradlew :klum-ast:test :klum-ast:groovy4Tests :klum-ast:groovy5Tests --no-parallel --console=plain
./gradlew check --continue --no-parallel --console=plain
```

Both commands pass. The final serial root check passes repository-wide: all three compiler lanes, runtime/Jackson/Bean
Validation/Test Support suites, all 359 Gradle-plugin cases, generated-contract and JPMS qualification, license checks,
coverage reports and lane isolation. It reran consumers of rebuilt modular artifacts against the final reviewed source.
No test suppression was added or weakened. Markdown fences, 87 local link targets, 12 local fragments and
`git diff --check` pass. The current user guide remains accurate: packaged generated metadata is still required, without
specifying the retired transport. No user page, migration, navigation or CHANGES update is needed.

Independent cumulative review against `de6337d43...3f69d3d2e`: **Standards zero findings; Specification zero actionable
findings**. The Standards review explicitly accepts independent fixed expectations despite their repeated map structure;
the Specification review confirms the coverage migration and the historical tracer's retirement. Neither reviewer ran
builds. This follow-up records results only; the reviewed implementation commit is preserved.

History review retains one cohesive removal/expected-contract migration commit, followed by this final qualification
record. No unrelated edits or history rewrite. Refreshed master remains `de6337d434b3f46b9f0ccce478bf9c150010f8e9`;
no rebase was required. Delivery capability audit: **Git transport authorized; GitHub CLI authorized**. The explicit
assignment authorizes branch publication and a draft PR with **Related: #529**. Tracker/curation impact is otherwise
**none**: no issue, milestone, release placement or curation-index mutation. Remote CI/SonarCloud results are separate
from these local results and must be inspected at the published head. Merge and further generator work remain outside
this assignment; Hive reconciliation is requested after handoff.
