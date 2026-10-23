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

Full affected-module lanes, root checks, independent Standards/Specification review, history review and remote
delivery results are recorded here after execution.
