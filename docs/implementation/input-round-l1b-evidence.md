# #952 L1-B — exact String scalar Map-value routes

Base: `b2e53159d1e1608d9dc086fcec96f64e346374e6`, verified against live `origin/master` on 2026-11-03.
Dedicated isolated worktree: `/Users/stephan/.codex/worktrees/911c/klum-ast`.
Branch: `codex/952-map-value-string-conversion`. Tracker impact: **Related: #952**; no issue closure, labels,
milestones, curation or release-gate changes. Hive owns final reconciliation; the worker does not self-archive.

## Contract and implementation

L1-A / PR #1018 is the exact base. L1-B reuses its compiler-owned source/generated-method association, PROPERTY
selection and getter-side zero/one completeness count for exact `Map<String, T>` of non-native, non-DSL scalar values.
The selected ordinary public method must take exact `(String key, String value)` and return the declared value type.
Only exact String converter sources qualify; competing String/CharSequence attempts, missing projections and descendant
shadowing remain excluded. Custom key mapping and non-String declared keys cannot gain converter admission.

The runtime validates the reflected Map destination, key type, exact selected public signature, association, role and
count. Its immutable payload is Map-of-String shared with schema projection. The existing interpreter iterates supplied
entries, checks each literal String key and raw String value, and invokes the selected ordinary entry adder. There is
no interpreter population change, container setter/factory invocation, general Map reinterpretation or second converter
engine. A protected generated-code bridge preserves direct named-module linkage to ordinary Map conversion/population.

JSON Schema emits an object with String `additionalProperties` without initializing Schema/domain classes or running
converters. Converter parameter assertions remain scalar-C1-only; Map values carry structural grammar. Existing
version-1 metadata is reused; matched compiler/runtime/plugin artifacts and Schema hierarchy recompilation are required.

Defaults, existing-key replacement, empty input, caller Map iteration order and Builder lifecycle remain ordinary.
Reached malformed keys/values and converter failures retain earlier work; later entries/operations never run. Diagnostics
retain source, escaped per-key pointer and original cause. No List admission extension, owned DSL Map values,
CollectionFactory, NamedParams, selectors, custom key mapping, public authoring API, parser, Jackson, copy or composition
change belongs to this slice.

## Acceptance map

- `StructuredMapConverterTest`: documentary Duration values/types, ordinary Groovy parity, literal keys/order and renamed
  members, initializer/default retention, existing-key replacement, empty input and whole-container-setter trap;
  first-failure partial state, source/escaped-key diagnostics, original cause and skipped later work;
  null/numeric/boolean/GString/Object/mutable-CharSequence/Map/List value rejection and non-String input keys;
  same-compilation/external adapter/constructor origins, inherited binary dispatch and descendant shadowing;
  ambiguous/excluded/colliding/missing routes, non-String declared keys, key-mapping and unsupported destinations;
  source-free positive inspection and corrupted/incomplete association/count/signature/type failures with initialization traps.
- `StructuredListConverterTest` and `StructuredConverterOriginTest`: retain L1-A/scalar regressions and move the formerly
  excluded Map control to an unsupported key shape.
- `CompiledConverterJsonSchemaTest`: source-free packaged G3/G4/G5 producers covering closure, local, adapter,
  inherited constructor and bounded generic value origins; exact String value grammar, no C1 leakage, excluded
  CharSequence/non-String-key/key-mapping declarations, deterministic/cacheable output and no-execution traps.
- `validate_converter_schema.py`: independent Draft 2020-12 accepted/rejected instances against actual emitted schemas,
  including closed-root controls and retained scalar/C1/List corpus.
- `JpmsPackageBoundaryTest`: Java/static Groovy artifact consumers and bytecode proof of direct Map-converter hook
  linkage, classpath G3/G4/G5 and named-module G4/G5 qualification.

Documentary link: `StructuredMapConverterTest#'converts duration Map values independently under literal String keys'`
with `@Issue('952')`, `@Tag('documentary')` and `@See`. Matching user contract:
[converter-backed Map values](../user/Structured-Input.md#converter-backed-map-values).
Converters, JSON Schema, Migration, CHANGES and ADR/plan documentation are synchronized.

## Validation and reviews

The initial documentary test failed before implementation. The final focused Java 17 run passed 132 compiler/consumer
cases (57 Map, 47 retained List, 27 scalar-origin, one real binary/classpath consumer), plus eight packaged G3/G4/G5
schema producer cases, with zero failures/errors/skips. The packaged fixture includes the new inherited Map in both
self-contained and keyless schemas, retaining their exact closed member sets.

The independent actual-output oracle passed 207 Map, 198 List and 270 structural scalar String controls, plus 28 JDK
and 270 ECMA/Draft C1 controls across packaged G3/G4/G5 artifacts, using pinned jsonschema 4.23.0. Non-String JSON
object keys cannot be represented; direct runtime controls separately reject raw non-String Map keys.

Exactly two read-only local review agents reviewed base `b2e53159d1e1608d9dc086fcec96f64e346374e6` through
`ad7b7af7f95fde70627e95f19858634f8a7f0ddd`. Standards: **zero findings**. Specification: **zero findings**. Neither
reviewer edited files or ran builds. The implementation and documentation commits were reviewed for focus and dependency
order; their separation explains behavior/qualification controls first, followed by the user contract. Reviewed commits remain preserved; the final qualification commit changes prose only. No local SonarCloud upload was performed.

Qualified implementation/docs revision: `ad7b7af7f95fde70627e95f19858634f8a7f0ddd`. The stable-head Java 17 root
`check` passed in 26m 31s (140 tasks: 49 executed, 91 up to date). Only CHANGES/evidence prose differed from HEAD during
this run; code, tests, fixtures and build inputs matched the reviewed revision.

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 3,998 | 0 / 0 | 17 |
| Groovy 4 compatibility | 3,311 | 0 / 0 | 16 |
| Groovy 5 compatibility | 3,311 | 0 / 0 | 16 |
| Total | 10,620 | 0 / 0 | 49 |

The compiler contributes 2,957 / 2,959 / 2,959 cases. All 57 Map and 47 retained List controls pass in each lane without
skips. Each lane also passes all five package/JPMS boundary tests, including the real Java/static Groovy consumer;
G4/G5 qualify named-module Schemas. All 665 plugin cases pass, including source mirrors and the eight packaged
schema producer cases. Root qualification includes lane isolation, license checks and the affected shared-contract seams.

The actual-output oracle was repeated after full qualification with the same passing counts. The three packaged
converter schemas are byte-identical: SHA-256 `392ad16899b32132fe6e8cc77b74f3cce373c94672f93bd3ced3351e1035c462`.
Relative Markdown targets (227 links, including extensionless page references), documentary issue/tag/anchor alignment
and diff checks pass. The clean-revision documentation render and site crawl pass (26s; 36 tasks), using a fresh 2 GiB
Gradle daemon. The initial invocation rejected pending evidence edits before rendering; committing the record satisfied
the immutable-checkout fence. The final prose-only qualification tip is rendered again before publication.

The first root invocation was stopped after source-mirror TestKit resolution failures. The parent build had configured
its Git-derived version before the documentation commit, while the nested fixture publication observed the newer HEAD.
Its requested and published development versions therefore differed. This invocation is not final qualification; the
full check is rerun with HEAD and dirty-state held fixed. No product/build configuration change is needed.

Commands (Java 17):

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :klum-ast:test --tests '*StructuredMapConverterTest' --tests '*StructuredListConverterTest' --tests '*StructuredConverterOriginTest' --tests '*JpmsPackageBoundaryTest.*real schema*' :klum-ast-gradle-plugin:test --tests '*CompiledConverterJsonSchemaTest' --no-parallel --max-workers=2 --console=plain
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --no-parallel --max-workers=2 --console=plain
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew renderLocalDocumentation --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' --no-parallel --max-workers=2 --console=plain
PYTHONPATH=/private/tmp/l1b-jsonschema-423 /Users/stephan/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 klum-ast-gradle-plugin/src/test/resources/validate_converter_schema.py klum-ast-gradle-plugin/build/schema-validation /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java
git diff --check
```

## Review brief

Review the compiler's exact key/value signature and key-mapping exclusion in `StructuredInputConverter`, then the
reflected Map key/value checks in `InputContractResolver`. `ConverterBuilder` and `ProxyMethodBuilder` only add the direct
runtime bridge; conversion and per-entry population remain in the ordinary engine. `StructuredMapConverterTest` is the
runtime acceptance entry point; packaged schema producers and JPMS consumers cover the binary/module boundary.

The compatibility requirement is matched-artifact recompilation, with required association/count facts still on protocol
version 1. String schema validity does not prove successful conversion; no element/value-parameter assertion projection
or runtime parameter validation is introduced. The implementation leaves all deferred container and owned-DSL shapes to
their own assignments. Merge and hosted qualification remain separate from local execution completion.

## Delivery

GitHub CLI repository write capability: **authorized** (verified without recording credentials or scopes).
Authorized delivery is a draft **Related: #952** PR. Its actual transport result, PR identity/head and hosted checks are
verified in the handoff. Merge remains outside the assignment; no release, issue closure or curation/release-gate change
is claimed. Hosted CI/SonarCloud qualification is separate from local evidence. Hive reconciliation is required after
delivery; the worker does not set archive-safe state or self-archive.
