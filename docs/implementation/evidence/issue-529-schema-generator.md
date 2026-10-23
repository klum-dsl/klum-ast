# First standalone JSON Schema generator slice

Date: 2026-10-23. Status: **implementation complete; validation/review in progress; unpublished stack**.
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
The command atomically replaces one output after projection (with a normal replacement fallback where atomic moves are
unavailable); projection failure exits nonzero and preserves an existing last-successful file. Output I/O errors are reported;
replacement is atomic where supported by the filesystem. Plugin stale-output
policy is deliberately not invented here. Consumers own publication and editor integration.

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
the admitted-payload/signature corruption case failed before exact population-signature checking was added. Independent Specification review identified a relationship payload/selected-contract mismatch gap. Both owned-object
and keyed-map corruption tests reproduced incorrect acceptance before the correction. Selected public contracts now
must reference the exact recorded child Builder (owned return or keyed population array), and keyed child exclusion
must agree with retained `@Key` bytes. Standards review found no documented-standard breaches; its one optional process
invocation duplication was removed by reusing the existing test helper. The fixed-key
fixture also exposed that child key identity is retained on the Model field, not the nested payload's unused key slot;
classfile annotation reading now validates it without loading annotation/Schema classes.

## Validation and review

The generator module check and installed-command build pass: **37 features/cases in each Groovy 3/4/5 lane**,
zero failures/errors/skips, plus licenses and test-lane isolation. The pinned validator passes **10 documents and
8 accepted / 24 rejected instances per lane**, with byte-identical schemas across lanes. The shared SCHEMA-T corpus
still agrees with WIRE-1 in its deliberately separate execution phase. The installed application also runs successfully on Java 17 with only its own JAR and ASM; its output is byte-identical
to the binary-only test document. Root checks and final independent re-review remain pending.
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

Do not publish, create a PR or rebase while #942 is unmerged. After verified successful CI and merge, rebase exactly once
onto then-current `origin/master`, compare trees, validate the rebased head and create the authorized draft PR when the
result is unchanged or conflicts are mechanical. No merge authorization is implied.
Tracker impact: **Related: #529**, **Related: #932**; both remain open. Curation/release placement impact: **none**.
Request Hive reconciliation of execution and delivery; an unpublished stack or open draft is not archive-safe.
