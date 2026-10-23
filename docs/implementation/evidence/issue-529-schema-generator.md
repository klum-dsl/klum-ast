# First standalone JSON Schema generator slice

Date: 2026-10-23. Status: **SCHEMA-1 standalone slice validated and reviewed; dedicated plugin and consumer acceptance pending**.
Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529), [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[SCHEMA-0](../adr-0031-schema-0.md), [SCHEMA-T](issue-529-schema-t.md), and the explicit standalone-slice assignment.
Exact stacked base: PR [#942](https://github.com/klum-dsl/klum-ast/pull/942),
`4ac815714c78f34f44fd7a010ef0891d9b25980c`. Its two commits are preserved.

## Delivered boundary

`klum-ast-json-schema` is an optional Java 17 standalone tool with ASM as its only production dependency. It reads current
private WIRE carrier constants and exact selected public Builder signatures from explicitly supplied class directories/JARs.
No Schema artifact is placed on the extraction JVM's classpath. No runtime reader, descriptor/codec/registry, parser,
Jackson behavior, compiler ABI change or ScHelm policy is introduced. Production classes other than the command's main
entry point are package-private.

The command is useful through standard Gradle application `run`/`installDist` tasks. The dedicated optional Schema plugin,
tracked multi-root generation, cache/configuration-cache/stale-output lifecycle qualification and consumer-owned real
ScHelm acceptance remain immediate subsequent work. This slice does not claim overall #529 production completion.
The command replaces one output after projection, atomically where the filesystem supports it. Projection failure exits
nonzero and preserves an existing last-successful file; output I/O errors are reported. Plugin stale-output policy is
deliberately not invented here. Consumers own publication and editor integration.

## Executable coverage

[`JsonSchemaGeneratorTest`](../../../klum-ast-json-schema/src/test/groovy/com/blackbuild/klum/ast/schema/JsonSchemaGeneratorTest.groovy)
reuses the authoritative SCHEMA-T API/Schema/trap/17-instance fixture for the production command, then adds:

- every admitted numeric scalar/wrapper, signed integral bounds and exact-number validator instances;
- field List/Set/SortedSet/NavigableSet/EnumSet and Map/SortedMap/NavigableMap shapes, recursive ordinary List/Map operation payloads;
- recursive owned references, repeated/same-simple-name nested classes, Unicode names and closed child definitions;
- explicit compiler rejections, inherited collisions, byte-equivalent duplicates and conflicting binary identities;
- empty-enum rejection (rather than an invalid Draft enum assertion), current carrier corruption, eleven-token rejection, payload/signature disagreement and missing optional-child artifacts;
- repeated generation, reordered/relocated artifacts and output changes following separately compiled API-byte changes;
- retained description attribution and fixed direct-child keys remaining absent from input;
- extraction from an explicitly closed named Schema module, with no exports, opens, module flags or runtime classpath;
- initialization-trap controls and a separate deliberate WIRE-1 phase for the same representative instance corpus.

The named-module test adds a real Java-17 module descriptor to the compiled Schema JAR and confirms it with ModuleFinder;
the tool still reads the JAR as data. It does not claim that arbitrary consumer module graphs have been executed.
The fresh tool JVMs carry the lane's JaCoCo agent only for coverage; their production classpath contains the tool and ASM.
Schema/API binaries remain data arguments.

Test-first checkpoints: the initial command test failed because the main class did not exist, then passed with extraction;
the admitted-payload/signature corruption case failed before exact population-signature checking was added.
Independent Specification review identified a relationship payload/selected-contract mismatch gap. Both owned-object
and keyed-map corruption tests reproduced incorrect acceptance before the correction. Selected public contracts now
must reference the exact recorded child Builder (owned return or keyed population array), and keyed child exclusion
must agree with retained `@Key` bytes. Standards review found no documented-standard breaches; its one optional process
invocation duplication was removed by reusing the existing test helper. The fixed-key
fixture also exposed that child key identity is retained on the Model field, not the nested payload's unused key slot;
classfile annotation reading now validates it without loading annotation/Schema classes.
A final specification audit also confirmed that name origin must be retained, not merely recognized: private entries
and coverage diagnostics now retain the compiler's IDENTITY/JSON_PROPERTY category. The old command failed the added
rename-origin assertion before this correction; inherited identity and explicit rename origins are now tested.

## Validation and review

The generator module check and installed-command build pass: **37 features/cases in each Groovy 3/4/5 lane**,
zero failures/errors/skips, plus licenses and test-lane isolation. The pinned validator passes **10 documents and
8 accepted / 24 rejected instances per lane**, with byte-identical schemas across lanes. The shared SCHEMA-T corpus
still agrees with WIRE-1 in its deliberately separate execution phase. The installed application also runs successfully
on Java 17 with only its own JAR and ASM; its output is byte-identical to the binary-only test document.

The rebased-head repository-wide `check` plus installed-command build passes in **11 minutes**
(154 actionable tasks: 59 executed, 95 up-to-date), including documentation-renderer verification, all module checks,
licenses, test-lane isolation and all three aggregate coverage reports. The unchanged compiler suites pass 2266 / 2268 /
2268 tests on Groovy 3 / 4 / 5, with zero failures/errors and 15 existing skips per lane. All 37 new generator cases run
without skips in every lane. Existing deprecation/unknown-extension warnings remain non-failing and were not bypassed.

Independent Standards and Specification reviews finish with **zero unresolved findings**. Reviews covered original
`8baf65c1aba8632008d9fbe097004363ea93bc9c`, the additive relationship correction
`a4660efc105e898c2843ccab5c69ea941455b78b`, and final origin retention
`d01f39ec0c790b82237af654d15677123ae3071b`. The rebase preserves their complete source tree and patch series.
History review confirms one usable vertical slice followed by two bounded, green specification corrections; reviewed
commits were preserved until the maintainer explicitly authorized the one-time rebase. Changed Markdown fences,
new/changed local links and `git diff --check` pass; pre-existing stale architecture-map links remain outside this change.
Groovy 5 JSON test assertions use explicit `Map.get('properties')` to avoid LazyMap property introspection; emitted
production JSON does not differ across Groovy generations.
The pinned [Draft 2020-12 validator](../../../klum-ast-json-schema/src/test/resources/validate_schema.py) is an explicit
evidence command, outside Gradle's check lifecycle. It checks every emitted document and accepted/rejected instances;
its exact Decimal integer type check preserves mathematical JSON integer semantics without double rounding.

```shell
./gradlew :klum-ast-json-schema:check :klum-ast-json-schema:installDist --console=plain --no-daemon
/private/tmp/structured-input-schema-validator/bin/python klum-ast-json-schema/src/test/resources/validate_schema.py
./gradlew check --console=plain --no-daemon --max-workers=2
git diff --check
```

The example Python environment is external test tooling with `jsonschema==4.23.0`; create an equivalent virtualenv when
reproducing elsewhere. No Python or validator dependency enters production or the consumer's build.

## Delivery conditions

PR [#942](https://github.com/klum-dsl/klum-ast/pull/942) was verified merged with successful build/JUnit/SonarCloud checks.
The explicit follow-up authorization was then executed: **one** conflict-free rebase onto current `origin/master`
`de76710dfc649f17857215d5c86cbd88165ab85c`, using the original stacked head as the replay boundary.
Pre-rebase `d01f39ec0c790b82237af654d15677123ae3071b` and rebased
`9864e1a29691b671ee3bdb0987f5c97465569a3b` have the identical full tree
`1b4c25ef68457ab91eb9c3e06e2c9d7257f2b421`; `range-diff` marks all three patches unchanged.
The full root check, pinned validator, cross-lane output comparison and installed Java-17 command were validated on that
rebased source head. This final qualification update changes only this evidence document.

Draft publication is directly authorized after that unchanged-tree validation. No merge authorization is implied.
Tracker impact: **Related: #529**, **Related: #932**; both remain open. Curation/release placement impact: **none**.
The dedicated plugin/output lifecycle and real ScHelm acceptance remain immediate subsequent work; this delivery does
not complete the overall #529 issue. No GitHub issue state is changed. Actual branch-push/PR channel outcomes and live
revision checks are recorded in the delivery handoff; source-tree equality is not a claim of new-PR CI success.
Request Hive reconciliation of execution and delivery; an open draft is not archive-safe.
