# SCHEMA-T — binary-only JSON Schema feasibility

Date: 2026-10-23. Status: **A/B/C and affected-module checks pass on Groovy 3/4/5; draft publication pending**.
Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529),
[#932](https://github.com/klum-dsl/klum-ast/issues/932).
Authority: [SCHEMA-0/T](../adr-0031-schema-0.md#executable-feasibility-proposal--schema-0t),
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md).
Base: `d4724f85991aa856afe1a42eca28bf0f98bc2ac2` (merged #941, after WIRE-1 #940).

## Retrospective architecture status — 2026-10-23

Merged [PR #942](https://github.com/klum-dsl/klum-ast/pull/942) preserves the successful A/B/C evidence below.
The later [ADR 0031 revision](../../adr/0031-structured-schema-input-artifacts.md#generator-architecture-and-fidelity)
and [SCHEMA-R next slice](../adr-0031-schema-0.md#next-required-slice--schema-r-reflection-versus-enrichment) supersede
its carrier-first/classfile-reader architecture and the instruction to proceed without reexamining enrichment. This tracer
did not prove reflective loading, `@AnnoDoc` extraction or that all carrier facts were irreducible. Its original commands,
results and publication checkpoint below remain historical evidence; no tests were rerun for the documentation revision.

## Outcome and proof boundaries

The disposable tracer reads the current private twelve-token WIRE-PLAN/B carrier and selected public generated Builder
signatures directly. It emits an actual Draft 2020-12 document for one explicit synthetic root. No new descriptor
architecture, production generator, public API, Gradle task/plugin, runtime dependency, parser, publication mechanism or
Schema source/mirror input is introduced. No issue state, curation, release placement or consumer policy changes.

| Gate | Executable evidence |
| --- | --- |
| A — binary extraction without Schema execution | `SchemaTracerTest` compiles `ApplicationApi.region` into an API JAR, then compiles concrete `Application` and keyed `Component` into a separate Schema JAR using that API binary. It verifies that archives contain classfiles only and deletes compiler output directories. A fresh JVM receives only the Java test helper and lane-specific Groovy JAR on its classpath. API/Schema JARs are data arguments, absent from that classpath. ASM reads Model, public Builder/Factory and private carrier bytes; recorded public signatures, inherited Builder interface and child identities are checked without loading Schema types or calling WIRE-1. |
| B — actual schema | Root `$ref` plus two reusable `$defs` use fully qualified generated Builder binary names. `Component` is referenced from both direct `primary` and keyed `components` properties. Inherited region, String/int/boolean/double, enum, literal data maps, exactly-one-Map-payload operation, rename and ignore are projected. The pinned `jsonschema==4.23.0` Draft202012Validator checks the metaschema and representative JSON instances. |
| C — bounded structural alignment | The same fixture then deliberately runs WIRE-1 with execution enabled in a separate phase. Five accepted and twelve rejected inputs are recorded with actual runtime outcomes; the Python validator checks exactly those instances against the emitted schema. It also asserts root/child closure, definition reuse, enum field names, inherited source identity, exclusion and the single map payload. |

The accepted corpus covers omission, false/zero/empty, rename, small floating-point input, inherited property, enum,
direct nesting, keyed child input and one operation payload. Empty/punctuated instance keys and literal labels retain
their spelling. Negatives cover wrong scalar/enum/nested/keyed/data shapes, unknown/excluded/raw renamed names,
child-key duplication, null and wrong operation payload/value shapes. The successful full example checks the completed
nested/keyed graph and operation effects; this is representative runtime alignment, not full semantic equivalence.

The fixture contains traps for API/Schema/child/enum/factory initialization, factory construction, field initialization,
default and validation closures, direct-child key closure, converters, lifecycle and operation execution. Extraction has
no executable route to these artifacts. Intentional initialization controls independently demonstrate that API, Schema,
enum, child and factory initialization fail when execution is disabled. The runtime phase explicitly enables execution
and demonstrates that the ordinary construction path executes user code. Generated constructors are not reflected upon
or invoked during extraction.

## Fidelity and deliberately bounded helper

The emitted root `$comment` states the structural limit: no guarantee of binding, operation execution, lifecycle,
materialization or completed validation; no Jackson/full-runtime equivalence. Defaults and validation code are not
executed. Missing prose is explicitly unavailable. No `required` or `default` is inferred from `@Required`, initializers
or `@Default`. Integer bounds are ordinary int bounds; double uses the approved `number` approximation with Java
conversion/representability limits stated. Enum names come from classfile enum fields, without enum reflection.

The validator's additional fidelity controls accept structurally valid `port: -1` and a string operation port that cannot
be parsed as an integer. Those controls assert schema behavior only, not successful runtime execution. Mathematical
`1.0` is an integer under JSON Schema; one out-of-int-range negative is checked. No exhaustive numeric matrix is claimed.

Compiler rejection diagnostics retain source and reason, including explicit ignore. Admitted kinds/scalars outside this
helper's measured subset throw rather than emitting permissive placeholders. The helper projects every admitted member
of this fixture. Coverage/facts JSON is temporary test diagnostic output, never an input protocol or public format.
Current carrier signatures mix reflected array binary names and generated Builder array source names; the helper
normalizes the measured object-array spelling solely to compare exact classfile methods. It does not select overloads.

Comprehensive inheritance/recursion, duplicate/corrupt carrier hardening, named-module tooling access, full container and
numeric matrices, failure/stale-output handling, deterministic Gradle outputs, prose attribution and real ScHelm acceptance
remain in the [production inventory](../adr-0031-schema-0.md#production-acceptance-inventory--deferred-from-schema-0t).
No full-runtime-reader equality, legacy-carrier decoding, interpreter dependency or production-readiness claim follows.

## Reproduce and validate

Test sources:

- [SchemaTracerTest](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/SchemaTracerTest.groovy)
- [SchemaTracerProjector](../../../klum-ast/src/test/java/com/blackbuild/klum/ast/schema/SchemaTracerProjector.java)
- [Pinned-validator script](../../../klum-ast/src/test/resources/schema-tracer/validate_schema.py)

```shell
./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.schema.SchemaTracerTest --console=plain --no-daemon
./gradlew :klum-ast:check --console=plain --no-daemon
python3 -m venv /private/tmp/schema-t-validator
/private/tmp/schema-t-validator/bin/python -m pip install jsonschema==4.23.0
/private/tmp/schema-t-validator/bin/python klum-ast/src/test/resources/schema-tracer/validate_schema.py
git diff --check
```

The Python validator is an explicit test-evidence command, outside the Gradle check lifecycle. It uses the library's
bundled Draft 2020-12 metaschema, and requires existing outputs instead of silently skipping a missing tracer run.
Outputs remain under `klum-ast/build/test-classes/<Groovy lane>/SchemaTracerTest/`: API/Schema JARs, `schema.json`,
`facts.json`, and `instances.json`. No generated output is committed as an authoritative sample.

Focused Groovy 3: two Spock features pass with no skips. The final `:klum-ast:check` passes in 10m 36s
(53 actionable tasks: 13 executed, 40 up-to-date), including module license and test-lane isolation checks.

| Compiler suite | Tests | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 | 2266 | 0 | 15 |
| Groovy 4 | 2268 | 0 | 15 |
| Groovy 5 | 2268 | 0 | 15 |

Each lane includes both tracer features without skips. The pinned validator independently passes metaschema validation,
five accepted and twelve rejected instances for **each** lane's output. Changed Markdown fences, 22 local link targets
and `git diff --check` pass. Existing unknown-extension/deprecation warnings are non-failing and were not bypassed.
The repository-wide root check was not rerun: this change affects only compiler-module test sources/resources and
implementation evidence; no production source, dependency, build configuration or public/generated contract changes.

Independent Standards and Specification reviews of `d4724f8...9f67add9` each returned **zero findings**. The implementation
commit `9f67add9bc5dfe1ebe16f8beedc1453ba556f8f6` is preserved; the final qualification record is an additive documentation
commit. History review confirms a cohesive vertical proof followed by its final evidence. No unrelated changes remain.

This test-only change introduces no user feature, so no documentary tag, user-page/migration/changelog
entry is warranted under SCHEMA-0/T.

## Delivery and next decision

The assigned outcome is only SCHEMA-0/T feasibility. Success returns the production-generator decision to the maintainer;
it does not start production work or reopen metadata representation design. Both related issues remain open.
Worktree: `/private/tmp/klum-ast-529-schema-t`; branch: `codex/529-schema-t-binary-tracer`.
Refreshed `origin/master` still equals the assigned base above; no rebase is needed.
Tracker impact: **related issue** (#529 and #932); curation impact: **none** (no release gate, owner or placement change).
The assignment explicitly authorizes a draft PR with these non-closing relationships. GitHub CLI repository capability
is authorized; the actual branch-push/PR outcomes and live revision checks are reported in the delivery handoff.
Hive owns final delivery/archive reconciliation; the worker must retain `(PR:open)` after draft publication and must not merge.
