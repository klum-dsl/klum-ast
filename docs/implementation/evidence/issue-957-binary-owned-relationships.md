# Binary owned relationship normalization

Date: 2026-10-24. Related: [#957](https://github.com/klum-dsl/klum-ast/issues/957).
Base: `b2ad49bce` (merged #951). Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md).

## Reproduction and diagnosis

The separately resolved fixture publishes concrete `Facts` and keyed `Workload` DSL types in an API JAR, resolves that
coordinate while compiling a Schema JAR, and resolves both artifacts transitively in a source-free consumer. Each
producer module is deleted after its JAR is published, before compiling the next module. A separately compiled client
runs the parser-free interpreter in a fresh JVM using the consumer's binary runtime classpath. The generator receives
only the Schema/API/runtime/annotation/Groovy classpath; no compiler, Schema source, IDE mirror or Javadoc input is
available. The fixture never contacts or modifies Schelm.

Before the correction, the six projection/interpretation cases fail across Groovy 3/4/5. Projection omits `facts` and
`workloads`; interpretation rejects `binary.yaml` at `/facts` with `has no admitted scalar, enum, List, or Map payload`.
The ordinary DSL construction control succeeds using the same binaries. A minimized two-compilation reader regression
also fails: only the local relationship is admitted, while direct and inherited binary fields/maps are unavailable.

Reflection identifies the exact distinction: a Schema relationship to an API binary has return type
`Class<Facts_DSL.Builder>`, while its local equivalent retains `ParameterizedType` `LocalFacts_DSL.Builder<LocalFacts>`.
Map values have the same raw-versus-parameterized distinction. Both shapes retain compiler-selected relationship roles
and generated input enrichment. The shared `CompiledBuilderSchema.payload` recognizes nested relationships only in its
parameterized branch; dependency resolution and compiler admission metadata are intact.

## Correction and boundaries

The shared reader additionally recognizes a raw field Builder when it has exactly the one generated self parameter,
an interface name with the generated Builder suffix, and a concrete Model self bound. Its existing nested-payload
normalization is shared with the parameterized path. Compiler selections and child enrichment remain mandatory; there
is no signature, annotation ABI, compiler emission, dispatch, construction or public API change.

A raw generic Model Builder carries user parameters in addition to the generated self parameter and remains rejected.
Abstract targets and authored Builder payloads remain rejected. The existing parameterized handling is preserved.
No generic argument inference, abstract/polymorphic selection, grammar extension, parser, descriptor/registry or second
construction engine is introduced. Compatible already-compiled contracts need no new metadata or source fallback.

## Executable coverage

- [CompiledBuilderSchemaTest](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/CompiledBuilderSchemaTest.groovy),
  `raw binary child contracts normalize without admitting raw generic or abstract targets`: exact raw/local signatures,
  direct/inherited ownership, keyed Map roles and excluded child key, plus raw generic/abstract/authored-payload negatives.
- [BinaryOwnedRelationshipsTest](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/BinaryOwnedRelationshipsTest.groovy):
  six separately resolved projection/interpretation cases across Groovy 3/4/5. Direct/inherited binary fields and Map
  values, local controls, literal data/instance keys, child rename and ignore, keyMapping exclusion, ownership, both
  interpreter entry points, lifecycle traces compared with ordinary DSL construction, complete preflight without
  callback execution, escaped nested diagnostics, and silent initialization during projection are qualified.

The first focused green checkpoint passes all **49 reader cases** and all **six binary consumer cases**, with no
failures/errors/skips. The new regression was observed failing before the production change. Reproduction commands:

```shell
./gradlew :klum-ast:test --tests '*CompiledBuilderSchemaTest.raw binary*'
./gradlew :klum-ast:test --tests '*CompiledBuilderSchemaTest'
./gradlew :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest'
```

User pages clarify binary/local parity under existing v1 limits; `CHANGES.md` records the correction. There is no new
syntax, migration navigation or documentary feature. Full module/root qualification, independent Standards and
Specification reviews, and current CI/SonarCloud results are recorded in the PR/handoff for the final revision.
Tracker impact is **Related: #957**; no closure, release placement, curation state, Schelm mutation or merge is implied.
Hive reconciliation is requested at delivery, and the worktree is retained while the draft PR remains open.
