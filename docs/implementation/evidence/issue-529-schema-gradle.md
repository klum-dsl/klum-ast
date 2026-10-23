# SCHEMA-GRADLE — explicit-root JSON Schema generation

Date: 2026-10-23. Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Base: current master `39f9c830284213c1f87d23c2b6391dc705e3fbf1` (merged WIRE-REMOVE #948).
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md), its
[implementation plan](../adr-0031-structured-schema-input-artifacts.md), and the explicit SCHEMA-GRADLE assignment.

## Delivered surface and placement

The existing Schema plugin registers `generateKlumJsonSchema`; only an explicit task request selects generation.
`klumSchema.jsonSchema` exposes `rootTypes` (a set of binary-name strings), `classpath` (a configurable file collection)
and `outputDirectory` (default `build/generated/klum-json-schema`). A Schema name resolves its exact generated Builder
name without loading the Model as an inspection target; explicit `Foo_DSL$Builder` names are also accepted. Roots are
never discovered. Default classpath inputs are main runtime outputs/dependencies plus the convention's Groovy selection,
which otherwise remains compile-only for consumers. The extension can replace them with a complete binary-only classpath.

`topLevelSchemaClass` was assessed against `klumModel.topLevelScript` and `klumSchema.gdsl` vocabulary. The Model property
selects one construction script. JSON Schema generation supports several independent explicit roots and accepts public
Builder names without loading Schema classes. The nested `klumSchema.jsonSchema.rootTypes` follows the existing
feature-specific `gdsl` block and avoids suggesting one global top-level Schema or requesting a configuration-time Class.
No alias or competing singular property is added. This is the implementation's naming choice for draft review, with no
unresolved naming decision blocking this slice.

`JsonSchemaProjection` is package-private plugin implementation in the facade's internal package. The plugin has a
compile-only runtime project dependency solely to link this class to package-private `CompiledBuilderSchema`; no runtime
artifact is added to the plugin's published runtime dependencies or to consumer runtime dependencies by this feature.
The inspected runtime/annotations/Groovy come only from the declared task classpath. The private projection class and
facade share one closeable URL loader whose parent is the platform loader. Only JDK string/collection values cross the
boundary; no Schema Classes, annotation proxies, reflection objects or loaders enter configuration-cache state.
Accessible reflection targets only the plugin's own private constructor. The facade retains public-contract reflection
and exact selective annotation-resource reading; no carrier, source mirror, directory/class-file discovery, duplicated
admission extractor, Schema implementation inspection or runtime interpreter is introduced.

The binary dependency-invalidation test exposed a JVM JAR-resource cache issue when replacing the same dependency path.
`Module.getResourceAsStream` bypasses `ClassLoader.getResourceAsStream`, so the isolated loader supplies uncached resource
connections from its module-resource lookup. This is local to that loader, with no global cache setting. Repeated reads
now use fresh dependency bytes, preserving resource-origin checks and deterministic output.

## Projection and output contract

The task emits Draft 2020-12 documents with closed admitted object properties, reusable/recursive fully qualified Builder
`$defs`, URI-escaped `$ref`, literal data/instance map keys, scalar/enum/container payloads and one-payload operations.
Descriptions retain the facade's exact source attribution. Integral bounds use exact BigInteger arithmetic; float/double
remain the accepted structural `number` approximation. No raw-input required/default assertion, lifecycle execution,
converter, polymorphic policy or arbitrary validation translation is added. Unsupported declarations remain absent with
source/reason diagnostics in `x-klum-unsupported`; malformed contracts and unrepresentable roots fail with context.

Inputs are explicit root names, complete `@Classpath` dependency bytes (including annotations/prose), private tool bytes
and tool version. Projection has no configurable policy switches in this slice. Every root produces one ordinary file
named after its canonical generated Builder. Successful output replacement deletes only stale filenames recorded in the
previous `.klum-json-schema-outputs` manifest; unrelated files are preserved. Contract/projection failure happens before
output mutation. Consumers must keep the manifest and use a dedicated directory. I/O failures are reported; no multi-file
transaction or preservation guarantee for partial filesystem failure is claimed.

No public generator API/module, executable, descriptor/registry, artifact variant/classifier, automatic embedding,
publication, parser or Jackson/YAML behavior is added. #932 remains independent. Real ScHelm root/output qualification
is explicitly pending, consumer-owned, and does not block this bounded Gradle slice or complete #529.

## Executable evidence and validation

[`KlumJsonSchemaGenerationTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy)
has **12 passing focused cases**, with no skips. The documentary case is tagged and linked to
[the user page](../../user/Json-Schema.md#generating-files). Coverage includes:

- separate API and Schema JARs resolved transitively from a file Maven repository after source deletion;
- inherited API prose, recursive child references, keyed maps, literal keys and one-payload Map/List operations;
- Groovy 3/4/5 schema compilation, with Groovy 5 also loading the packaged plugin JAR instead of plugin class directories;
- positive initialization controls and silent initializer/default/lifecycle/operation/annotation-enum traps in extraction;
- default classpath/local compilation, missing isolated runtime without daemon fallback, missing/invalid/empty/abstract roots;
- up-to-date execution, same-coordinate API documentation changes, root deletion, safe stale cleanup and failure preservation;
- standalone configuration-cache reuse, build-cache restoration and project relocation; ordinary assembly does not generate.

The pinned external validator (`jsonschema==4.23.0`) accepts all three emitted Draft metaschemas and checks **6 accepted /
14 rejected** representative instances per generation. All three documents are byte-identical. The reproducible
[validator script](../../../klum-ast-gradle-plugin/src/test/resources/validate_json_schema.py) is evidence tooling and adds
no production/consumer dependency or mandatory Python build requirement.

The initial full plugin suite ran **361 cases**, with all 12 new cases passing and 19 existing scenario/dependency cases
failing while external repositories were unavailable (Maven Central/Plugin Portal DNS and incomplete resolution).
The first root check completed all compiler/runtime/Bean Validation Groovy 3/4/5 lanes successfully (compiler
2,303 / 2,305 / 2,305 cases; runtime 73 per lane). Jackson compatibility lanes also passed 119 per lane.
It failed at 11 existing mirror fixtures because commits changed the inferred version during that run, plus two
Jackson external-consumer cases and one published-test-support case with repository TLS failures. No production
behavior finding was reported. The evidence validator's Python comment-header delimiters were corrected to the
repository's existing style; license-plugin configuration is unchanged. Stable-tip requalification is pending.
Independent Standards and Specification reviews at `fc3c847d2` both report **zero findings** against `39f9c8302`.
The reviewers ran no builds and made no source changes.

```shell
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest'
./gradlew :klum-ast-gradle-plugin:test :klum-ast-gradle-plugin:validatePlugins :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest
python klum-ast-gradle-plugin/src/test/resources/validate_json_schema.py klum-ast-gradle-plugin/build/schema-validation
./gradlew check --continue --no-parallel --console=plain --no-daemon --max-workers=2 --offline
git diff --check
```

## Delivery boundary

Tracker impact: **Related: #529**; no issue closure, milestone, release-placement or curation-scope change.
The older standalone SCHEMA-1 branch is untouched. No merge is authorized. Independent Standards/Specification review,
commit-history review, final branch/base verification and remote CI/SonarCloud inspection precede the final handoff.
An open draft remains `(PR:open)`; request Hive reconciliation and retain the active worktree while delivery is pending.
