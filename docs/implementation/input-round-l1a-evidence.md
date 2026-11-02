# #952 L1-A — exact String scalar List-element routes

Base: `45729e625385fb59bca878cce3771391d6d727d3`, verified against fetched `origin/master` on 2026-11-02.
Dedicated worktree: `/Users/stephan/.codex/worktrees/952-l1a-list-converters/klum-ast`.
Branch: `codex/952-l1a-list-converters`. Tracker impact: **Related: #952**; issue state, labels, milestones,
curation and release gates remain unchanged. Hive owns final reconciliation.

## Contract and seam

The existing compiler pairing can carry the authoritative element route without discovery or another conversion engine.
`StructuredInputConverter` selects one existing public generated String element adder for an exact `List<T>` of non-DSL
scalar values. It retains attempted collisions and pairs the same Converter/Selection facts with explicit getter-side
zero/one completeness. The reflected getter and return type identify the destination element. No new metadata carrier,
version, public authoring API or CollectionFactory change is needed. Matched Schema recompilation remains required.

`InputContractResolver` validates List shape, element destination, exact String public method, role, association and count
before exclusion. List routes have no C1 parameter authority. The immutable compiled payload is List-of-String and the
existing interpreter invokes the selected element adder in index order. Its ordinary runtime converter/population path
owns all actual conversion; a protected generated-code bridge preserves direct JPMS linkage. Schema projection consumes
the same payload and emits an array with String items. Element parameter assertions are not projected; scalar C1 remains.

Initial/default entries, empty-input behavior and earlier successful operations survive unchanged. A reached malformed
element or converter failure retains prior work and original cause with source/index diagnostics; later work is absent.
No whole-container replacement, Map-value conversion, CharSequence-only element route, owned DSL collection, coercion,
selector, parser, Jackson, copy, composition or generic Factory registry is added.

## Acceptance evidence

- `StructuredListConverterTest`: documentary ordered Duration example; exact result types/values; ordinary Groovy parity;
  initializers and empty Lists; strict null/numeric/boolean/GString/Object/mutable CharSequence/Map/List rejection;
  first-failure partial state, original cause and skipped later operations; competing converters, ignored/excluded routes,
  generation collisions, unsupported containers and native List grammar; same-compilation/external/constructor origins;
  binary inheritance/shadowing; source-free metadata corruption and initialization traps with a positive control.
- `CompiledConverterJsonSchemaTest`: packaged G3/G4/G5 producers with sources/classes removed after JAR assembly;
  closure/local/adapter/inherited-constructor String items; Map-value and CharSequence-only exclusions; retained C1 facts,
  no-user-code traps, deterministic/cacheable output and previous-output failure preservation.
- `validate_converter_schema.py`: independent Draft 2020-12 accepted/rejected List corpus against the actual emitted
  schemas, alongside retained C1 JDK/ECMA controls. Structural String contents remain converter-owned at invocation.
- `JpmsPackageBoundaryTest`: Java and static Groovy consumers plus bytecode verification of direct generated element-hook
  linkage, classpath on G3/G4/G5 and named-module Schemas on G4/G5.

User contract: [converter-backed List elements](../user/Structured-Input.md#converter-backed-list-elements), with
`StructuredListConverterTest#'converts duration List elements independently in supplied order'` carrying `@Issue('952')`,
`@Tag('documentary')` and `@See`. Current Converters, JSON Schema, Migration and CHANGES documentation are synchronized.

## Qualification and delivery

Exactly two local read-only reviewers performed Standards and Specification review. Standards requested braces in the
ASM corruption helper and one constant for a repeated stable diagnostic; both are corrected. Specification reproduced
a compiler/resolver mismatch: an ordinary non-DSL `Value<T extends CharSequence>` was mistaken for a Builder SELF type.
The resolver now recognizes generated Builder interfaces explicitly, with runtime ordinary/structured parity and
packaged G3/G4/G5 bounded-generic List controls. Both axes report zero remaining findings in the corrected tree.
Reviewed commits are preserved; corrections are additive.

The corrected focused Java 17 run passed 120 baseline compiler cases (48 List, 27 String-origin, 44 CharSequence,
one real binary/classpath consumer) plus eight packaged-plugin cases with zero failures/errors/skips. The independent
actual-output oracle passed 28 JDK, 270 ECMA/Draft C1, 270 structural scalar String and 201 List grammar controls.
The earlier compatibility run predating the review correction was stopped and is not final qualification.

## Final local qualification

Qualified code revision: `3d4eefa50240d7e992874234acf3620cf7b36ce8`. Root `check` passed on Java 17; all final module
JUnit XML reports were inspected:

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 3,942 | 0 / 0 | 17 |
| Groovy 4 compatibility | 3,255 | 0 / 0 | 16 |
| Groovy 5 compatibility | 3,255 | 0 / 0 | 16 |
| Total | 10,452 | 0 / 0 | 49 |

The compiler contributes 2,901 / 2,903 / 2,903 cases; all 48 L1-A controls pass in each lane without skips.
The plugin contributes 665 passing baseline cases, including all eight packaged converter/schema producer cases.
Qualification includes affected module suites, Java/static Groovy artifact consumers, G4/G5 named modules, generated
bytecode linkage, source-free G3/G4/G5 producer artifacts, C1 regressions, lane isolation, plugin validation and license
checks. No local SonarCloud upload occurred; coverage-bearing hosted analysis remains separate.

Commands, with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`:

```shell
./gradlew check --no-parallel --max-workers=2 --console=plain
./gradlew renderLocalDocumentation --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' --no-parallel --max-workers=2 --console=plain
PYTHONPATH=/private/tmp/l1a-jsonschema python3 klum-ast-gradle-plugin/src/test/resources/validate_converter_schema.py klum-ast-gradle-plugin/build/schema-validation /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java
git diff --check
```

The first combined `check renderLocalDocumentation` invocation executed the full test suites but ended after 28m 38s
with Gradle's implicit-dependency validation for the renderer's repository-directory input. Separate root `check` then
passed in 3s (140 tasks; 9 executed, 131 up to date) on the unchanged code, reusing the just-produced successful reports.
The reused 512 MiB daemon subsequently exhausted its heap while snapshotting the documentation input. A fresh daemon
with the explicit 2 GiB heap completed rendering and the site crawl in 20s (36 tasks; 3 executed). The generated heap dump
was removed. No build configuration or unrelated source fix was introduced for these invocation/environment limits.

The independent oracle was rerun after full qualification: 28 JDK + 270 ECMA/Draft C1 + 270 structural scalar String +
201 List grammar checks passed with jsonschema 4.23.0, Node v26.12.0 and Java 17. All three emitted converter schemas are
byte-identical, SHA-256 `e055de518cc5368b987b42fc94951b0bda5236224b8fdf15529e843bcc2cec26`.
Markdown fences, 220 relative file links, documentary anchors, the actual rendered-site crawl and diff checks pass.

The commit sequence was reviewed: one green implementation step, synchronized documentation, then additive review
corrections. No history rewrite was needed; the final evidence-only commit changes no code, test, fixture or build input.

## External delivery condition

GitHub CLI repository delivery is authorized. Publication uses a draft PR with **Related: #952**; the actual transport,
PR identity/head and current hosted checks are verified in the handoff. No merge, release, deployment, issue closure or
curation/release-gate mutation is authorized or claimed. Hosted CI/SonarCloud must qualify the published head before
maintainer delivery reconciliation. Hive retains eventual merge and archive reconciliation; the worker does not
self-archive.
