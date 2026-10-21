# #896: Layer 3 binary version qualification, 4.0.1 and 4.1.0

Research date: 2026-10-21. Checkout: `3a912cdd4` (full identity in the handoff below).
No production behavior, dependency policy, release branch, or tracker state was changed.

**A real incompatible combination is demonstrated:** a published 4.1.0 Schema with `@Builder.Query` compiles into a
Model successfully but throws `NoSuchMethodError` on an explicitly selected 4.0.1 runtime. Both Model execution and a
Java consumer of the published Model fail. The same binaries pass on 4.1.0. Conversely, all seven basic Layer 3 rows
pass, including older-runtime experiments. Therefore even an exact version pair needs feature coverage; successful
resolution and a small happy path cannot justify bidirectional 4.x compatibility.

Recommend coordinated versions as the current safe practice, plus **directional qualification** of older Schema binaries
on each newer runtime. Do not reject every different version string or silently force versions. The newly demonstrated
failure makes a bounded diagnostic design worth considering for 4.2; it does not establish an approved enforcement
policy or require a later major for documentation and consumer qualification.

The [retained fixture and evidence](issue-896/) contain separate producer, Model, and Java consumer builds, exact resolved
classpaths and artifact hashes, positive controls, static rejection controls, and the feature-specific failure witness.
Artifact inspection below is distinct from those executable results.

## New public version available

Public Maven Central runtime metadata now lists stable **4.0.0, 4.0.1, and 4.1.0**, with 4.1.0 as latest/release and
`lastUpdated=20261020140408`. GitHub lists the 4.1.0 release at `2026-10-20T14:18:47Z`.
Actual 4.0.1 and 4.1.0 compiler/runtime/annotations/plugin JARs, POMs and Gradle module metadata were retrieved;
both 4.1.0 Schema/Model plugin marker POMs were retrieved too.
[Central version index](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-runtime/maven-metadata.xml),
[4.1.0 release](https://github.com/klum-dsl/klum-ast/releases/tag/v4.1.0).

The [2026-10-17 maintainer comment](https://github.com/klum-dsl/klum-ast/issues/896#issuecomment-6233641750)
deferred #896 from 4.1 to **4.2**, pending meaningfully different post-4.0 versions. That publication condition is now
satisfied. This does not itself change the issue's disposition. The earlier retained local matrix at
`e96f7e1ce7040de0ec49a8d9805feeabb56f3a6a` (`git show e96f7e1:docs/research/issue-896/README.md`)
still qualifies only its exercised 4.0.0/4.0.1 paths; their framework implementation classes were identical.

## Plugin and publication wiring

Both released plugins obtain their own implementation version and add
`api(platform("com.blackbuild.klum.ast:klum-ast-bom:" + version))`.
Despite the method name `lockDependencyVersions`, this is an ordinary platform, with no `enforcedPlatform`,
`strictly`, `force`, or comparison against the compiler provenance of imported Schema binaries.
The current checkout retains this selection mechanism.
[4.1.0 AbstractKlumPlugin](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/AbstractKlumPlugin.java),
[version resource reader](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/PluginHelper.java).

| Boundary | Published/source contract |
| --- | --- |
| Schema and Model plugin markers | Marker POM requests `klum-ast-gradle-plugin` at the marker's version. |
| Gradle plugin implementation classpath | Depends on AnnoDocimal Gradle plugin 1.0.1 in both 4.0.1 and 4.1.0; it does not supply the project's Klum compiler/runtime as plugin dependencies. |
| Schema project | Versionless `compileOnly klum-ast`, `api klum-ast-runtime`, and the plugin-version BOM. Actual compiler/runtime therefore depend on the resolved project graph. |
| Model project | Plugin-version BOM; `api` extends `schemas`. No direct compiler/runtime dependency is added by the Model plugin: the Schema's graph supplies the runtime. Model scripts compile with Groovy against the real generated Schema interfaces. |
| BOM 4.0.1 and 4.1.0 | Ordinary `version.requires: V` constraints on Klum modules in both API/runtime variants. 4.1.0 also constrains `klum-ast-test-support`; no strict range or alignment capability is published. |
| Compiler at V | Requires annotations/runtime V and AnnoDocimal global AST 1.0.1. Klum Cast SPI/compile moves from 0.4.0 to 0.5.0. |
| Runtime at V | Requires annotations V. |
| Annotations at V | Requires AnnoDocimal annotations 1.0.1; Klum Cast annotations moves from 0.4.0 to 0.5.0. |

Primary metadata:
[4.1.0 Schema marker](https://repo.maven.apache.org/maven2/com/blackbuild/klum-ast-schema/com.blackbuild.klum-ast-schema.gradle.plugin/4.1.0/com.blackbuild.klum-ast-schema.gradle.plugin-4.1.0.pom),
[4.1.0 Model marker](https://repo.maven.apache.org/maven2/com/blackbuild/klum-ast-model/com.blackbuild.klum-ast-model.gradle.plugin/4.1.0/com.blackbuild.klum-ast-model.gradle.plugin-4.1.0.pom),
[4.0.1 BOM](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-bom/4.0.1/klum-ast-bom-4.0.1.module),
[4.1.0 BOM](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-bom/4.1.0/klum-ast-bom-4.1.0.module),
[4.1.0 compiler](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast/4.1.0/klum-ast-4.1.0.module),
[4.1.0 runtime](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-runtime/4.1.0/klum-ast-runtime-4.1.0.module),
[4.1.0 annotations](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-annotations/4.1.0/klum-ast-annotations-4.1.0.module),
[4.1.0 plugin](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-gradle-plugin/4.1.0/klum-ast-gradle-plugin-4.1.0.module).
[Schema plugin source](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java),
[Model plugin source](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstModelPlugin.java).

Gradle's ordinary version conflict rule selects the greatest requested version; a normal platform permits upgrades.
`platform()` endorses strict constraints if a platform contains them, but these BOMs publish only `requires`.
The resulting default selection is independent of whether the Schema or Model dependency was declared first.
There is no corresponding Klum mismatch warning in the inspected plugin boundary; dependency reports expose selection,
but successful graph resolution is not binary compatibility evidence.
[Gradle version selection](https://docs.gradle.org/current/userguide/dependency_versions.html),
[Gradle platforms](https://docs.gradle.org/current/userguide/platforms.html).

| Schema's published BOM A | Model plugin BOM B | Expected ordinary Gradle selection, without locks/rules/strict overrides |
| --- | --- | --- |
| A = B | A | A, for modules present in the graph. |
| A older than B, patch or minor | B | Newer BOM B wins; runtime is raised to B, while the Schema bytecode remains compiled with A. |
| A newer than B, patch or minor | B | Newer Schema BOM A wins; runtime is A even though the Model plugin itself remains B. |
| Any A, runtime-only consumer without its own BOM | None | Schema transitives normally select A; another dependency can raise it. |
| Any A, explicit older runtime declaration | Any | Ordinary older request alone does not downgrade a higher requirement. |
| Any A, force/strict runtime constraint, exclusions, missing metadata or manual classpath | Any | Can produce a downgraded or mixed graph; inspect actual artifacts separately for every configuration. |

This table describes resolution expectations, not feature support. Resolution is per configuration, not a single
application-wide version lock. A plugin's implementation classpath, Schema producer compile classpath, Model consumer
compile classpath, and execution runtime classpath are separate evidence boundaries. A newer binary dependency can
also raise a Schema producer's actual compiler above its plugin version through the ordinary BOM graph.
The [user plugin page](../../user/Gradle-Plugins.md) currently says the BOM version is “enforced”; that wording
overstates the implementation. Clarification is a small candidate documentation follow-up, not authorization to force versions.

## Published generated-runtime ABI inventory

Compared exact uncompressed `.class` entries from actual Central JARs, then ran `javap -protected -s` over every
class under `com.blackbuild.klum.ast.runtime.generated` in the union of the two runtime JARs.
The runtime namespace has no class or public/protected member descriptor removals in this comparison.
This is a bounded generated-package inventory, not a complete API binary checker or execution claim.

| Generated runtime addition in 4.1.0 | JVM descriptor |
| --- | --- |
| New `GeneratedBuilderTypeSupport.$klum$isModelOrBuilder` | `(Ljava/lang/Class;Ljava/lang/Object;)Z` |
| New `GeneratedBuilderTypeSupport.$klum$isBuilder` | `(Ljava/lang/Class;Ljava/lang/Object;)Z` |
| New `GeneratedBuilderTypeSupport.$klum$narrowBuilder` | `(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;` |
| `GeneratedKlumBuilder.$klum$assertMutable`, public final | `()V` |
| `GeneratedKlumBuilder.$klum$completedModelOrNull`, protected final | `()Ljava/lang/Object;` |
| `GeneratedTemplateFactorySupport.FromClasspath` | `()Ljava/lang/Object;` |
| `GeneratedTemplateFactorySupport.FromClasspath` | `(Ljava/lang/ClassLoader;)Ljava/lang/Object;` |

`KlumFactory.BuilderFactoryProvider` also gains default `isModelOrBuilder(Object)`, `isBuilder(Object)` and
`narrowBuilder(Object)` methods. A consumer compiled to invoke them requires their availability at execution.
The older `InternalKlumBuilder` does not supply inherited `$klum$assertMutable` or `$klum$completedModelOrNull` either.
[4.1.0 runtime JAR](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-runtime/4.1.0/klum-ast-runtime-4.1.0.jar),
[4.0.1 runtime JAR](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-runtime/4.0.1/klum-ast-runtime-4.0.1.jar),
[4.1.0 factory source](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumFactory.java).

This is a meaningfully changed implementation pair: compiler class counts including module descriptors are 68 → 88
(20 added, 41 changed, zero removed); runtime 104 → 135 (31 added, 38 changed, zero removed); annotations 59 → 69
(10 added, two changed, zero removed); plugin 19 → 34 (15 added, six changed, zero removed).
Changed-byte counts do not diagnose binary incompatibility by themselves.

Specific compiler-emitted witnesses are available in the tagged 4.1.0 source:

- `@Builder.Query` without a Builder input emits a direct, explicitly targeted call to `$klum$completedModelOrNull`.
  A compiled query exercised on a 4.0.1 runtime is a strong negative-control candidate. Exact observed exception belongs
  to the executable fixture; absence of this method is the artifact finding.
  [BuilderQuerySupport](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/BuilderQuerySupport.java).
- Composition converter and collection-alternative methods emit `$klum$assertMutable` calls. Exercise those paths
  explicitly; merely loading a basic Schema does not prove they link.
  [ConverterBuilder](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/ConverterBuilder.java),
  [AlternativesClassBuilder](https://github.com/klum-dsl/klum-ast/blob/580649a033c29739df9fdff3b36c65b0756d1b40/klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AlternativesClassBuilder.java).

The additive direction supports testing old Schema bytecode on the newer runtime first. It does not prove all behavior
is compatible, nor that newer compiler output can run on an older runtime. Future runtime compatibility policy must
state directionality and feature floor explicitly.

## Architectural tension and editor-only metadata

[ADR 0015](../../adr/0015-generated-schema-runtime-linkage.md) calls the generated-runtime package a stable 4.x
binary contract while its status remains **Proposed**, including at the release tag. The inventory is consistent with
an additive old-binary-on-new-runtime direction, but neither that wording nor successful dependency resolution proves
bidirectional execution. Hive should reconcile the intended direction and the ADR status without inventing a new policy here.

AnnoDocimal remains version 1.0.1 for the minor pair, so it is no longer the changed external dependency in this pair;
Klum Cast 0.4.0 → 0.5.0 is. Source mirrors remain IDE-only outputs and must not compile or ship as downstream build inputs.
Portable Schema GDSL is absent from public 4.0.1 and present in 4.1.0: it selects metadata matching the already resolved
normal Schema GAV, not a Klum compiler/runtime compatibility range. Its catalog/version checks cannot substitute for
Schema compiler provenance checks.
[ADR 0005](../../adr/0005-generated-dsl-support-api.md),
[ADR 0025](../../adr/0025-portable-schema-gdsl-metadata.md),
[portable GDSL guide](../../user/Portable-GDSL.md).

## Inspection reproducibility and limits

Downloaded inspection files are temporary at `/tmp/issue-896-metadata-20261021`; they are not required retained evidence.
Retrieval uses the public Central paths linked above. For generated ABI inspection, run the following for each version
and each generated `.class` entry from `jar tf`, then diff outputs by class name:

```sh
javap -classpath klum-ast-runtime-4.1.0.jar -protected -s \
  com.blackbuild.klum.ast.runtime.generated.GeneratedKlumBuilder
```

Exact downloaded JAR SHA-256 values:

```text
d88b6a8ac7643d561343aaf2fcd4abbf43015f3a77cad05986f17973ca19f253 klum-ast-4.0.1.jar
b9b3476381c92db5f7751de490c6cb9e9ec9da6f36666797301ed21f6b054c2d klum-ast-4.1.0.jar
2c5e339a741f97fef07d5360c326856c466a3a0ed882fd876450d78d3bd3900c klum-ast-runtime-4.0.1.jar
6d41ec0b0971ce122f2ff31f969dd40b543f3106503d715458226fcc89775e37 klum-ast-runtime-4.1.0.jar
9a68e1a07682986f954358808af5b7e887bb05687b90ed1306d9d27a4f504925 klum-ast-annotations-4.0.1.jar
da5d45ccc60afc0857376142097b1bac109ab33a54f9f8fdc391fd5f7159b240 klum-ast-annotations-4.1.0.jar
a2e579253d7451cfb4593cf780390a604ddfd659470e5f2d7ab455fd1b279ce5 klum-ast-gradle-plugin-4.0.1.jar
1cafaab0a6a3e3bf2faf6d7e0b783ce8c0b2175f8f641c7f3782e4a045b5f3aa klum-ast-gradle-plugin-4.1.0.jar
```

All stable versions newer than 4.1.0, a changed patch pair within 4.1, and future minors remain unavailable in the
observed Central index. No pre-4.0 claim is made. Artifact inspection does not qualify JPMS execution, persistence,
all Groovy/JDK lanes, all generated features, or Maven's different dependency-selection rules. Executable matrix
results must record resolved compiler/runtime files and retain their own exact pass/fail scope before any recommendation
is promoted to a compatibility promise. Hive reconciliation remains required for policy, ADR, and issue disposition.

## Executed binary matrix

`A = 4.0.1`, `B = 4.1.0`. The Schema plugin compiles and publishes a binary Schema with its own BOM/compiler/runtime;
an independent Model build imports that external GAV and publishes a Model; a third build applies only Java/application
plugins and imports the Model GAV. There are no KlumAST project dependencies, source substitution, included builds,
Maven Local artifacts, or copied framework classes. Separate Gradle invocations keep plugin implementation classloaders
independent. A file Maven repository holds **application fixture artifacts only**; KlumAST resolves from public repositories.

The basic Schema has distinct abstract Domain API types, an `@Cluster Map<String, ApplicationApi>`, and concrete named
relationships. It tests actual Layer 3 projection. Domain API and concrete Schema are published together, bounding the
independently versioned seam here to Schema → Model; independently compiled Domain API → Schema remains an additional axis.
The statically compiled Model exercises root/factory/Builder/child calls, reads completed Cluster contents, and a Java
consumer reads Domain API types. A negative Model compile deliberately calls a nonexistent generated Builder method.
Each negative compile fails with the expected static diagnostic, proving the consumer uses real binary interfaces.

| Row | Schema plugin / resolved AST | Model plugin | Model compile runtime + annotations | Model execution runtime + annotations | Java compile / execution | Outcome |
| --- | --- | --- | --- | --- | --- | --- |
| AA | A / A | A | A | A | A / A | pass |
| BB | B / B | B | B | B | B / B | pass |
| AB | A / A | B | B | B | B / B | pass |
| BA | B / B | A | B | B | B / B | pass |
| AB-runtime-A | A / A | B | B | A | B / A | pass |
| BB-runtime-A | B / B | B | B | A | B / A | pass |
| BA-all-A | B / B | A | A | A | B / A | pass |

AA–BA use normal resolution. The last three use explicit **experimental** `strictly('4.0.1')` constraints on runtime
and annotations, either runtimeOnly or both implementation/runtimeOnly. They expose the boundary without changing plugin
defaults. The BOM still resolves to B where B is present. Plugin B does not mean a Model AST compiler B: the Model
compile/runtime classpaths contain no KlumAST AST compiler. In every positive Java run, class loading confirms its absence.
Model and Java execution report their loaded runtime JAR origins. No focused Klum mismatch warning is emitted.

For the **patch** axis, the prior retained 4.0.0/4.0.1 matrix has the same seven passing shapes; its unchanged framework
classes limit that evidence. There is no published changed 4.1 patch pair yet. For the **minor** axis, the current table
qualifies only these operations of 4.0.1/4.1.0, with the negative feature witness below preventing a broader conclusion.
Normal highest-version selection is independent of declaration order; explicit permutation of declarations was not run.
It is dependency graph resolution, not first-on-the-JVM-classpath ordering. Manually placing two runtime JARs on a
classpath is outside the tested graph and may instead depend on classloader search order.

### Changed-feature witness

The separate `query-schema` producer is compiled and published with B. It adds the following to the concrete Schema:

```groovy
@CompileStatic
@DSL
class CustomerEnvironment extends EnvironmentApi {
    String region
    @Builder.Query String regionName() { region }
    Application billing
    Application inventory
}
```

The Model calls the generated public Builder interface during normal root construction:

```groovy
CustomerEnvironment.Create.With {
    region 'eu'
    assert regionName() == 'eu'
    billing('billing') { image 'billing:1' }
    inventory('inventory') { image 'inventory:2' }
}
```

| Query row | Schema AST | Model plugin | Model compile modules | Execution modules | Model compile / publication | Model / Java execution |
| --- | --- | --- | --- | --- | --- | --- |
| query-control | B | B | B | B | pass | pass / pass |
| query-old-runtime | B | B | B | A | pass | fail / fail |
| query-all-old | B | B | A | A | pass | fail / fail |

Each failure is the same JVM linkage error:

```text
java.lang.NoSuchMethodError: 'java.lang.Object com.blackbuild.klum.ast.runtime.generated.GeneratedKlumBuilder.$klum$completedModelOrNull()'
    at example.CustomerEnvironment$Builder.regionName(EnvironmentApi.groovy)
```

The failing Java path consumes a published, already compiled Model and Schema. It has no KlumAST plugin or compiler.
Loaded-runtime origins and resolved-artifact hashes identify A on the failing path. Compilation can succeed because it
checks the public Builder method in the Schema binary; it need not resolve the helper inside that method's body.
The JVM resolves the helper when the method executes. Adding helpers preserves an old caller's potential linkage to a
newer runtime, but cannot make a newer caller find that helper in an older runtime.
[JLS binary evolution rules](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.4.12).
This is an observed feature/runtime floor, not evidence that ordinary unmodified Gradle resolution defaults to A.

The inventory also predicts missing-type/member failures for new factory narrowing, alternatives/converters, and
classpath Template helpers if exercised against A; those are **unexecuted predictions**. Other changed descriptors could
produce `NoSuchFieldError`, `AbstractMethodError`, or access errors; runtime semantic failures can occur even when all
symbols link. Neither a public-method inventory nor a single linkage witness qualifies lifecycle, validation, Template,
relationship, or third-party semantics generally.

### AnnoDocimal, mirrors, and GDSL

Independent same-version controls run `clean`, `resolutionProof`, `createKlumDslSourceMirrors`, `javadoc`, and binary/source
publication for A and B. Both pass with AnnoDocimal 1.0.1. Generated mirror files exist; binary JARs contain actual
`*_DSL` classes; binary/source archives contain no `*_DSL.java` mirrors; compile classpaths contain no mirror directory.
The fixture skips the outgoing Javadoc publication variant, so this proves Javadoc task execution and binary/source
publication, **not the default complete publication graph**. It does not reproduce the old 4.0.0 projection failure.

Portable GDSL cross-version behavior was inspected but not executed here. Existing
[`KlumModelGdslBinaryContractTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumModelGdslBinaryContractTest.groovy)
proves a published Schema's real Builder contract in Groovy 3/4/5 using local test inputs; it is not a published cross-version
product qualification. An editor successfully materializing metadata is not runtime safety. For a B Schema with GDSL,
qualify matching Schema GAV/classifier selection separately from the Klum runtime choice; an A Model plugin has no new
GDSL transport contract. Source mirrors must never repair a binary compatibility gap by being compiled downstream.

## Options and smallest truthful scope

| Option | What the evidence supports | Scope and risk |
| --- | --- | --- |
| Document coordinated versions | Smallest immediately truthful practice: choose matching Klum modules across producer and consumers; inspect actual resolved classpaths rather than trusting plugin strings. Retain named passing exceptions as qualification evidence. | Low risk 4.2 documentation/qualification slice. Correct the existing “enforced” BOM wording; no resolver change. A recommendation alone cannot protect manual/strictly downgraded runtime consumers. |
| Diagnose a proven runtime floor | A plugin can inspect resolved graph modules, but cannot infer the compiler that emitted an arbitrary Schema JAR from its plugin version or GAV alone. Runtime construction is also needed to cover Java-only consumers. | Reasonable bounded 4.2 design candidate now that a witness exists; provenance and failure timing are unresolved. Emit an actionable error naming Schema, required ABI/runtime, selected runtime, and remedy. No implementation approved here. |
| Enforce exact equality | Would reject the passing AB/BA basic cases and can overstate plugin-version significance; strict/enforced platforms alter conflict resolution and may impose policy downstream. | Not justified by this evidence; requires a maintainer compatibility decision. Do not silently replace `platform` or introduce forced substitutions. |
| Graceful defined compatibility | Older Schema → newer runtime is the natural first qualification direction; retain old entrypoints and behavior. New Schema → old runtime can only work when emitted calls are available or an explicit compatible fallback exists. | Bidirectional blanket support is disproven. Broad shims/adapters, negotiation, a new ABI-version framework, or changed module/extension contracts need separate design; a later major only if accepted changes actually break existing contracts. |

A diagnostic needs authoritative information: the actual emitting compiler/generated ABI or a conservatively documented
minimum runtime recorded by a producer. The runtime dependency in metadata is useful for normal resolution but can be
overridden; it is not immutable per-class provenance. A plugin-only check misses consumers that never apply that plugin.
A new diagnostic referencing a newly introduced runtime helper would itself not execute on older runtimes; any design
must identify a boundary already present there, or accept that the new diagnostic protects only newer consumers.
Do not add provenance machinery, runtime feature negotiation, or a release policy as part of this investigation.

### Distinct work

- [#337](https://github.com/klum-dsl/klum-ast/issues/337) concerns combined **Model source** compilation, Javadocs, and
  source-level discovery; [#548](https://github.com/klum-dsl/klum-ast/issues/548) concerns optional **Schema source** composition.
  Neither is implemented or qualified by binary GAV consumption, even when the projects share a repository.
- Structured-input/Jackson construction and generated JSON Schema describe external inputs and validation contracts.
  They do not supply missing JVM methods or select a binary-compatible runtime. No input-format or JSON-Schema decision
  follows from this evidence; Java serialization remains separately same-version-only as specified by ADR 0027.

## Catwalk recommendation and release placement

Read-only inspection used Catwalk revision `97a787ecc2696569327d42f6cf5d59999c5503ac`.
[`validations/direct-schema/build.gradle`](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/direct-schema/build.gradle)
has the 4.0.1 baseline evidence task, full mirror/Javadoc/check graph, and mirror-leak checks. It rejects other versions for
its evidence tasks; overriding `klumAstVersion` for a smoke test does not create a new release baseline.
[`validations/layer3-contracts`](https://github.com/klum-dsl/klum-catwalk/tree/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/layer3-contracts)
qualifies shared API test-fixture wiring and Groovy/Spock alignment, with no Model leaf; it does not vary Klum versions.
The smart-home showcase has independent API/Schema/Model/Client jobs and explicit binary handoffs, but uses file JARs
and explicit module wiring; those handoffs do not carry published Schema POM/GMM transitives.
[`CI and handoffs`](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/.github/workflows/ci.yml).
None of these alone covers #896's independent publication and metadata conflict-resolution seam.

Under existing [ADR 0019](../../adr/0019-published-showcase-consumer-verification.md), recommend a bounded independent
binary-version validation, reusing its coordinate-only evidence rules rather than changing existing baseline pins:

1. Retain the published predecessor Schema JAR and its exact compiler/plugin/BOM/AnnoDocimal coordinates and hashes.
   Compile the Model separately against the selected current release; execute its published binary from a Java-only leaf.
2. For each admitted release line, run a same-version control and the retained predecessor Schema on the exact candidate
   runtime, then public revalidation after release. Include the new release's generated-feature witness and an explicit
   older-runtime negative control. This negative control is a qualification success only when the expected incompatibility
   is detected; it is not an allowed production graph.
3. Preserve compile, publication, Model execution, and Java execution outcomes separately. Record plugin marker and loaded
   implementation, resolved BOM/compiler/runtime/annotations/support modules, selection reasons, hashes, JVM, Gradle,
   Groovy, mirror/Javadoc/archive boundaries, and complete diagnostics. Never equate one declaration with an actual compiler.
4. Public-release runs use a fresh Gradle home and public coordinates; candidate runs use ADR 0019's complete isolated
   candidate manifest, marker, and implementation. Candidate rebinding must not recompile the historical producer Schema.
   Keep mixed-version experiments labelled separately from the aligned candidate product gate.
5. Start with the demonstrated Groovy 3/JDK 17 seam. Before claiming Groovy 4/5 or JPMS compatibility, compile producer and
   consumer independently in each lane and qualify named-module linkage. An independently published Domain API, multiple
   Schemas with different floors, extensions, and optional runtime modules are additional explicit coverage decisions.

No Catwalk files, CI gates, pins, compatibility branches, or release approvals were changed. This is qualification design,
not a new release policy. The local probe is equivalent evidence for the bounded seam, not complete Catwalk release proof.

For provisional **next 4.x (currently issue placement 4.2)**: retain this witness, clarify coordinated-version guidance
and directional limits, add bounded Catwalk qualification when approved, and decide whether an actionable floor diagnostic
has enough value. None needs broad compatibility infrastructure. A later major is appropriate only for a separately
accepted incompatible generated/public contract redesign; it is not a reason to defer truthful docs or this qualification.

## Reproduction, limits, and handoff

From this worktree, with Python 3, JDK 17 and Gradle 8.14.4:

```sh
export JAVA_HOME=/path/to/jdk17
python3 docs/implementation/evidence/issue-896/fixture/run.py --evidence /tmp/matrix.json
python3 docs/implementation/evidence/issue-896/fixture/query_run.py --evidence /tmp/query-matrix.json
python3 docs/implementation/evidence/issue-896/fixture/projection_run.py --evidence /tmp/projection.json
python3 docs/implementation/evidence/issue-896/fixture/verify.py /tmp
```

Each runner defaults to this repository's Gradle wrapper and a newly allocated temporary Gradle home; `--gradle` accepts
an installed Gradle 8.14.4 executable and `--gradle-home` an explicitly disposable cache. The wrapper is a launcher only,
not a KlumAST build input. Repository/Gradle cache paths in evidence are normalized; GAVs, versions, and SHA-256s remain.
Ignored `fixture/results/` holds complete raw logs. Positive matrix evidence was recorded from the original temporary
fixture before relocating the unchanged sources into this worktree; query and projection probes were then replayed here.
The original probe logic/source baseline is the retained `e96f7e1` fixture; this update changes the pair and adds the
feature witness, separate projection controls, and independent evidence verification.

Observed environment: macOS ARM64, Zulu JDK 17.0.3, Gradle 8.14.4, Groovy 3.0.25; public artifacts, with initially fresh
temporary Gradle homes (query/projection replays reused only their own disposable caches). AnnoDocimal is 1.0.1; Klum Cast
varies 0.4.0 → 0.5.0. No Groovy 4/5, JPMS, other JDK, Maven resolution, historical serialization, Jackson, optional-module,
all-feature, dynamic-script, independently published Domain API, or arbitrary application compatibility is claimed.
Production Groovy test lanes were not run: no production/build configuration changed; the isolated executable probes
and evidence verifier are the relevant checks. Documentation/diff and local-link checks also apply.

Assigned investigation is complete as a **local evidence handoff**. No PR/push is required or performed; the compatibility
policy remains a maintainer decision rather than silently becoming a new product promise. Base:
`3a912cdd444e22b98e689f422be9c8bdb36d24fa`; branch:
`codex/issue-896-41-binary-qualification`. Tracker impact: related #896 only; no GitHub/curation mutation.
Rebase status: the evidence commit is based directly on that fixed checkout revision; no moving release target was supplied.
Curation impact: a bounded Hive follow-up may reconcile the new evidence; this worker made no placement/gate change.
External orchestration event stream: unavailable. No release/run IDs, authorized event-stream location, or per-run storage
grant was supplied. This is an explicit audit gap; no launch timing, runtime telemetry, or event history was invented.
Complete normalized linkage-failure output is retained under `issue-896/linkage/` (only trailing whitespace removed).

**Request Hive reconciliation:** retain or deliver this evidence, reconcile the now-satisfied distinct-release condition
and the concrete runtime-floor witness with #896's 4.2 placement, decide the support direction and ADR 0015 status,
and choose any bounded documentation/Catwalk/diagnostic follow-up. Do not infer issue completion, archive safety,
an approved compatibility range, or a release blocker from this worker's completed investigation.
