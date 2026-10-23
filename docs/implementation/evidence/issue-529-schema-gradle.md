# SCHEMA-GRADLE — explicit-root JSON Schema generation

Date: 2026-10-23. Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Base: current master `39f9c830284213c1f87d23c2b6391dc705e3fbf1` (merged WIRE-REMOVE #948).
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md), its
[implementation plan](../adr-0031-structured-schema-input-artifacts.md), and the explicit SCHEMA-GRADLE assignment.

## Delivered surface and placement

The existing Schema plugin registers `generateKlumJsonSchema`; only an explicit task request selects generation.
`klumSchema.jsonSchema` exposes `rootTypes` (`SetProperty<String>` of fully qualified DSL Model binary names),
`classpath` (a configurable file collection) and `outputDirectory` (default `build/generated/klum-json-schema`). A DSL Model name resolves its exact generated Builder
name internally; `CompiledBuilderSchema` verifies that the resolved contract belongs to the requested Model, without
inspecting or initializing the Model implementation. Generated `Foo_DSL$Builder` names are rejected as public root values
with guidance suggesting `Foo`. Roots are never discovered. Default classpath inputs are main runtime outputs/dependencies plus the convention's Groovy selection,
which otherwise remains compile-only for consumers. The extension can replace them with a complete binary-only classpath.

`topLevelSchemaClass` was assessed against `klumModel.topLevelScript` and `klumSchema.gdsl` vocabulary. The Model property
selects one construction script. JSON Schema generation supports several independent explicit roots selected by DSL Model
names without loading Model classes during configuration. The nested `klumSchema.jsonSchema.rootTypes` follows the existing
feature-specific `gdsl` block and avoids suggesting one global top-level Schema or requesting a configuration-time Class.
No alias or competing singular property is added. The maintainer-directed refinement retains this property name and
selects only DSL Model names; generated Builders remain internal resolution details.

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
repository's existing style; license-plugin configuration is unchanged. Stable-tip requalification at `25b89e4a9` passes the complete **371-case plugin suite**, Gradle task validation,
licenses, both Jackson external consumers and both published-test-support cases. The final unchanged-tip root
`check --continue --no-parallel --offline` then passes in **11m 58s**, with **137 tasks (32 executed / 105 up-to-date)**.
All module checks, Groovy 3/4/5 lanes, consumer fixtures, documentation renderer, licenses, lane isolation and aggregate
coverage reports pass. Jackson baseline finishes 121 cases; test support finishes 8 / 6 / 6 across the three lanes.
There are no remaining failures or new skips. Existing compiler/runtime skips retain their documented reasons.
The pinned validator is rerun after the full check and again reports all three metaschemas, 6 accepted / 14 rejected
instances per generation and byte-identical output. Markdown fences/new local links and `git diff --check` pass.
Independent Standards and Specification reviews at `fc3c847d2`, with additive review through `25b89e4a9`, both report **zero findings** against `39f9c8302`.
The reviewers ran no builds and made no source changes.

```shell
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest'
./gradlew :klum-ast-gradle-plugin:test :klum-ast-gradle-plugin:validatePlugins :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest
python klum-ast-gradle-plugin/src/test/resources/validate_json_schema.py klum-ast-gradle-plugin/build/schema-validation
./gradlew check --continue --no-parallel --console=plain --no-daemon --max-workers=2 --offline
git diff --check
```

## SonarCloud follow-up

Published revision `203540cfd` passes GitHub build, JUnit reporting, SonarCloud analysis and its quality gate
(run `39994683010`). SonarCloud reports no security hotspots and one maintainability finding, `java:S3011`,
for opening the package-private plugin projector constructor. This access is intentional: the helper remains private
without adding a public generator API, while consumer Schema contracts retain public-only access. A localized
`@SuppressWarnings("java:S3011")` with that concrete reason records the exception; executable behavior is unchanged.

The follow-up reruns all **12 focused TestKit cases**, including all three Groovy generations and the packaged plugin,
with zero failures/errors/skips, plus Gradle task validation and main/test license checks (**31 tasks, 50s**).
The pinned external validator again passes all three metaschemas and 6 accepted / 14 rejected instances per generation.
Independent Standards and Specification follow-up reviews both report zero findings. The complete root check above
remains the full-suite qualification; it is not claimed to have been repeated for this source-annotation-only follow-up.
The next remote analysis must confirm the finding is absent at the follow-up revision.

## Maintainer refinement — DSL Model roots

The public root contract is refined from the reviewed `ae783d052` revision: only fully qualified DSL Model names are
accepted in `SetProperty<String> rootTypes`, matching the GDSL export convention. No `Class<?>` configuration value or
configuration-time Model loading is introduced. The private projector appends the existing `_DSL$Builder` suffix and
uses `CompiledBuilderSchema` to compare the contract's Model name with the requested name. It never resolves the requested
Model implementation as an inspection target. A generated Builder root is rejected with a diagnostic suggesting its
corresponding DSL Model name. Builder-based filenames, `$defs` identities and references are unchanged.

The documentary fixture and all positive root selections use Model names. Two focused negative cases cover a generated
Builder name and a deliberately misdirected binary contract whose Builder/Factory agree on a different Model. The latter
has no requested Model implementation at all, qualifying the identity check without Model inspection. All **14 focused
TestKit cases pass** with no failures/errors/skips (**48s**), including the existing multiple-root, stale-output,
dependency/prose invalidation, configuration/build-cache, relocation, isolation and execution-trap cases. All three Groovy
generations retain byte-identical output; pinned external validation passes each metaschema and 6 accepted / 14 rejected
instances per generation. An initial negative-fixture iteration put a Java contract ahead of its Groovy Model dependency;
co-compiling the malformed fixture with the existing Groovy fixture corrects that test setup. No production defect was
involved. The repository-wide final-head check and live CI/SonarCloud results for this follow-up are reported in PR #949.

No facade/generated-annotation, projection-shape, task/output, runtime, loader, publication or packaging behavior changes.
This refinement does not complete the separate consumer-owned ScHelm acceptance gate or change issue/milestone state.

## Delivery boundary

Tracker impact: **Related: #529**; no issue closure, milestone, release-placement or curation-scope change.
The older standalone SCHEMA-1 branch is untouched. No merge is authorized. The local history review retains one vertical implementation/test commit, its user/architecture documentation,
the bounded validator-header/version correction, and this final evidence record; reviewed production commits are preserved.
Refreshed `origin/master` is still `39f9c8302`, so no rebase is required. The final evidence-only commit changes no executable
source from the validated/reviewed `25b89e4a9`. Delivery channels: **Git transport authorized; GitHub CLI authorized**.
The first managed location encountered unavailable automatic write approval; implementation and validation continued in
a separate worktree under an allowed temporary root. This changes no repository policy. Live PR/CI/SonarCloud state is
reported separately in the handoff and is not inferred from local success.
An open draft remains `(PR:open)`; request Hive reconciliation and retain the active worktree while delivery is pending.
