# Issue #954 — Stage B acceptance

Date: 2026-10-24. Base: `28f2d7ed6` (merged Stage A [PR #961](https://github.com/klum-dsl/klum-ast/pull/961)).
Authority: [issue #954](https://github.com/klum-dsl/klum-ast/issues/954),
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md#explicit-key-member-extension--954) and
[accepted implementation plan](../issue-954-explicit-key-members.md).

## Behavior and seams

The private Gradle projector consumes Stage A's `CompiledBuilderSchema.Key`, native key presence, direct `schemaKey`
and fixed-child facts. It adds no compiler/runtime key redesign or public API. Definition identity is the fully
qualified Builder plus member/keyless position; unkeyed types retain one definition. Every closed object is reserved
before recursive ownership, and references encode the JSON Pointer/URI identity. Required String members are justified
by construction identity. Fixed/derived/Cluster-key and Map bodies exclude inner identity while retaining ordinary
property admission. The default root artifact describes three-argument self-contained input; no external-rootKey or
active-Builder generation mode is added.

Ignored/colliding required keys fail with selected root, declaring/relationship source, public Builder and position.
Optionality does not permit dropping an admitted direct child. Missing key/authority metadata gives recompile/package
guidance. Compiler exclusions retain `x-klum-unsupported`; no new categorical omission is introduced. Projection still
precedes output mutation, preserving every last-successful file and the manifest if any selected root fails.

## Acceptance mapping

| Accepted criterion | Executable evidence |
| --- | --- |
| Same keyed Builder as root, direct child, fixed/derived/Cluster child and Map body | `KlumJsonSchemaGenerationTest` generation example on Groovy 3/4/5; binary inherited renamed `ServiceApi#name` |
| Recursive/mixed-position reuse, closed objects and escaped external key names | Direct `nested`, keyed `children`, `service/id~` key, `id/~` suppressed-getter fallback; external Draft 2020-12 validation |
| Ignore, collisions and independently representable keyless use | `unrepresentable required keys fail closed before replacing successful outputs: #model`; `KeylessUses`, `IgnoredOperationCollision`, empty rename/ignore-false fixtures |
| Missing key and direct relationship authority | `missing key facts cannot silently project a keyed document: #model`; bounded old-contract fixture, no heuristic fallback |
| Compiler rejection versus admitted unrepresentable input | Ignored direct field and keyMapping Map remain diagnosed/absent; ignored/colliding admitted direct children fail |
| No Schema/enum/provider execution | Existing static/initializer/operation/lifecycle/enum traps, new throwing key/keyMapping providers, explicit initialization positive control |
| Output/cache/classpath behavior | Existing TestKit root/stale manifest, changed binary API, isolation, relocation, configuration/build-cache and packaged Groovy 5 plugin cases; `BinaryOwnedRelationshipsTest` checks closed keyless bodies from binary dependencies |
| Public Java/static Groovy and module path | Extended `JpmsPackageBoundaryTest` real consumer fixture exercises root/direct/Map identity; Groovy 3 classpath and Groovy 4/5 named modules |
| Runtime regression and documentary behavior | Existing Stage A key tests plus new `StructuredInputKeyDocumentaryTest`; full qualification includes Jackson |

## User documentation and traceability

`StructuredInputKeyDocumentaryTest` is marked `@Issue('954')`, `@Tag('documentary')` and links to
[Structured Input — Construction keys](../../user/Structured-Input.md#construction-keys). Its features are:

- `self-contained documents carry root and direct-child keys while Maps supply outer keys`
- `explicit-rootKey payloads omit root identity and still supply direct-child identity`

The user page refers back to both features. [JSON Schema](../../user/Json-Schema.md#construction-key-definitions),
[Migration](../../user/Migration.md#explicit-structured-construction-keys),
[Builder-first migration](../../user/Builder-First-Migration.md#opt-in-structured-builder-input) and `CHANGES.md` record
both deliberate compatibility effects: supplied caller-key direct children now require identity before allocation;
generated keyed-root schemas now require an inner key and describe self-contained documents. Structural validity
cannot establish provider execution, existing-child key compatibility, lifecycle/materialization/validation success or
raw parser duplicate handling. Existing navigation already links these pages; no new user page is introduced.

## Validation checkpoint

The first tracer was red on all three Groovy generations before position-specific projection. Ignored/colliding root
and optional direct-child cases were red before fail-closed projection. Focused generator, documentary and baseline
consumer checks pass. The pinned external `jsonschema==4.23.0` tool checks six document families per generation,
including **20 accepted / 45 rejected** recursive/mixed-position instances per generation and all 18 metaschemas;
each emitted family is byte-identical across Groovy 3/4/5. The validator passed again after the full check. Python
remains evidence tooling, without a production dependency.

Final full qualification at code/test revision `f71303119998a27f5ba7030a81197c2f84ed631d` passes
`check --continue --no-parallel --max-workers=2 --offline` in **14m 46s** (137 tasks: 50 executed, 87 up-to-date).
It covers compiler 2,366 / 2,368 / 2,368 cases; runtime 73 per lane; Jackson 121 / 119 / 119; Bean Validation 10 per
lane; test support 8 / 6 / 6; and the full **385-case Gradle plugin suite**. All reported failures/errors are zero.
Existing compiler/runtime skips retain their existing reasons; no new test is suppressed. Java/static Groovy classpath
and Groovy 4/5 named-module consumers, licenses, Gradle task validation, lane isolation, renderer and coverage checks
all pass. Local Markdown links/anchors, fences and diff checks pass.

The first full run found three old binary projection assertions still naming the former shared definition. Those now
check `::keyless`, closed properties and absence of an inner key/requirement, with issue traceability. Editing that test
while the first check ran also changed the inferred Git version: eleven source-mirror fixtures requested the clean
version while nested publication supplied the uncommitted version. The focused binary/mirror controls pass, followed
by the untouched clean-tip full check above; no unrelated mirror or dependency behavior was changed.

The issue branch history was reviewed without rewriting. Independent Standards/Specification review and remote delivery
are separate required handoff steps; this local validation checkpoint makes no review, CI, SonarCloud or merged-state
claim. Subsequent evidence-only documentation changes require documentation/diff checks rather than another Groovy run.

Reproduction commands:

```shell
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest'
./gradlew :klum-ast:test --tests '*StructuredInputKeyDocumentaryTest' --tests '*JpmsPackageBoundaryTest.a real schema*'
python klum-ast-gradle-plugin/src/test/resources/validate_json_schema.py klum-ast-gradle-plugin/build/schema-validation
./gradlew check --continue --no-parallel --max-workers=2 --offline
```

Stage B has no curation/release-scope change beyond completing this accepted extension. It makes no claim of real
ScHelm root/output acceptance for #529 and implements none of #952, #953 or #959. Merge and archive reconciliation
remain with the maintainer/Hive.
