# #896: generated Schema provenance and runtime-floor design

Date: 2026-11-07. Status: local design proposal; no implementation or gate admission.

Tracking issue: [#896](https://github.com/klum-dsl/klum-ast/issues/896).
Parent decisions: [ADR 0015](../adr/0015-generated-schema-runtime-linkage.md),
[ADR 0019](../adr/0019-published-showcase-consumer-verification.md), and
[ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md).

## Authority and outcome

The maintainer's planning direction is directional support within admitted 4.x lines:

- Each newer supported runtime qualifies older compiled Schema/API artifacts, retaining the producer binaries.
- A newer Schema may use an older Domain API when it stays within that API's contract. This needs a separate test.
- An older runtime need not execute later generated features. Diagnose a known floor where the checking runtime can run.
- At minimum, emit authoritative provenance into generated artifacts. Do not force equality, adapt bytecode, or select
  replacement dependencies.

This is a **4.2 candidate, not a hard release blocker**; Schelm's release timing does not create an exception to the
general contract. This proposal clarifies ADR 0015 (classification B), preserving its Proposed status. It does not turn
all of 4.x into an already qualified compatibility range. Source composition, structured import, JSON Schema, historical
serialization, and a ScHelm-specific runtime policy remain outside #896.

Recommended minimum: an inert per-Schema marker carrying actual compiler provenance. Reserve an explicit unknown floor
from the start. Follow it with a conservative stable-release floor and a runtime construction check after acceptance
coverage proves those facts. Marker delivery can stand alone if runtime-floor qualification is deferred.

## Refreshed evidence and present behavior

Source baseline is `651f072fd25e3680cf3daf2dd2065e39c442ed7b`, verified against `origin/master` on 2026-11-07.
The live issue remains open in milestone 4.2 and contains the directional planning target above. The
[retained executable investigation](evidence/issue-896-binary-version-compatibility.md) is historical evidence, not a
fresh execution in this design task:

- Seven basic 4.0.1/4.1.0 Layer 3 rows passed. A basic passing row cannot predict all generated-feature linkage.
- A separately published 4.1.0 `@Builder.Query` Schema compiled/published into a Model but failed on an explicitly
  downgraded 4.0.1 runtime: `NoSuchMethodError` for `GeneratedKlumBuilder.$klum$completedModelOrNull()`. Both Model and
  Java-only execution failed; the same binaries passed on 4.1.0.
- That experiment used Groovy 3/JDK 17. It did not qualify Groovy 4/5, JPMS, arbitrary dependencies, or independently
  published Domain API and Schema layers. The existing negative control remains valuable even after a marker ships.

Current source seams (paths are relative to the repository; `...` abbreviates
`src/main/java/com/blackbuild/klum/ast`):

| Seam | Observed behavior and design implication |
| --- | --- |
| `klum-ast/.../compiler/internal/ast/DSLASTTransformation.java` | `visit` skips interfaces, generates abstract/concrete Schema class families, then completes Builder/support/structured metadata. Emit provenance on the transformed class, not the source `@DSL` annotation or IDE mirror. |
| `DSLASTTransformation.createBuilderConstructors` | Existing protected `(Class, String)` constructor forwards the actual selected model type through generated parents to `GeneratedKlumBuilder(Class)`. Keep these descriptors. |
| `klum-ast-runtime/.../runtime/internal/FactoryHelper.createBuilder` | Resolves the ordinary/Template Builder class, obtains its constructor, invokes it, then attaches the Construction session and Templates. The primary check belongs before generated Builder resolution. |
| `klum-ast-runtime/.../runtime/generated/GeneratedKlumBuilder.java` | Existing `(Class)` constructor delegates to `InternalKlumBuilder`. A body check can reject before Schema Builder field initializers and callbacks without an extra emitted call. |
| `klum-ast-runtime/.../runtime/internal/InternalKlumBuilder.java` | Base constructor currently stores the model type. Its Java field initializers run before the generated-base body; do not promise zero framework allocation. |
| `klum-ast-runtime/src/main/module-info/module-info.java` | `runtime.generated` is already generally exported. Metadata adds no module, broad internal export, or consumer JVM flag. |
| `klum-ast-gradle-plugin/.../gradle/AbstractKlumPlugin.java` | `lockDependencyVersions` adds an ordinary `platform`, not an enforced platform. It neither checks provenance nor locks configurations to the plugin version. |
| `klum-ast-gradle-plugin/.../gradle/PluginHelper.java`, plugin `build.gradle` | The plugin has a generated own-version resource. Compiler/runtime lack an equivalent resource in their module build files; add their own provenance independently of plugin identity. |
| `klum-ast-bom/build.gradle` | Constraints guide resolution; they do not attest to which compiler emitted a Schema or which runtime a JVM loaded. |

[`RootRecipeConsumerTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/RootRecipeConsumerTest.groovy)
builds separate Schema/consumer JARs, deletes sources/classes before downstream use, removes the Klum compiler from
runtime, and exercises Java/static/dynamic Groovy plus Groovy 4/5 named modules. Its reconstructed pre-addition Factory
surface is **not historical producer bytecode** and cannot substitute for retained release artifacts.
`JpmsPackageBoundaryTest` provides generated-internal-reference and real module-boundary seams.
`KlumModelGdslBinaryContractTest` and `BinaryOwnedRelationshipsTest` use local test inputs; they are not public
cross-version qualification.

`release/consumer` resolves Maven modules/markers; `release/plugin-consumer` proves public plugin application. Neither
executes a cross-version Schema feature. The former still unconditionally requests `klum-ast-test-support`; historical
4.0 evidence needs a line-aware product set, as ADR 0019 already requires. No release fixture is changed here.

### Current Catwalk capability

Read-only inspection of Catwalk `main` on 2026-11-07 found revision
[`97a787ecc2696569327d42f6cf5d59999c5503ac`](https://github.com/klum-dsl/klum-catwalk/tree/97a787ecc2696569327d42f6cf5d59999c5503ac),
the same revision as the retained investigation:

- [Direct Schema](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/direct-schema/build.gradle)
  retains public 4.0.1, normal plugin-marker/product resolution, mirrors/Javadoc/check, and mirror-leak assertions.
  Evidence tasks explicitly reject another version; a property override alone gives smoke coverage.
- [Layer 3 contracts](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/layer3-contracts/build.gradle)
  verifies API-only fixture packaging, explicit project test-fixture wiring, and a shared Groovy/Spock pair. It has API
  and Schema projects, no independently published Model/Java runtime leaf or Klum version matrix.
- [CI](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/.github/workflows/ci.yml)
  runs these fixtures and independent smart-home/Helm leaves. Smart-home handoffs are file JARs, so they do not prove
  application POM/Gradle-module-metadata propagation. No complete candidate-maintenance workflow is present.

These are source capability findings, not newly executed Catwalk results. ADR 0019's isolated complete-candidate mode
and retained historical-producer requirement need explicit delivery before this seam becomes release evidence.

## Proposed marker: one annotation, three explicit values

Working name: `GeneratedSchema` in `com.blackbuild.klum.ast.runtime.generated`. It is generated-only metadata, not a
handwritten Schema option or extension SPI. Use runtime retention, TYPE target, no inheritance, and only these values:

| Value | Meaning and authority |
| --- | --- |
| `format = 1` | Marker encoding revision only. Neither a feature level nor a runtime ABI epoch. |
| `compilerVersion` | Exact emitting `klum-ast` artifact version, read from its own build resource bound to the defining compiler artifact. Never derive it from consumer plugin/project version, BOM selection, or imported Schema GAV. |
| `minimumRuntime` | Explicit canonical stable tuple `major.minor.patch`, or empty string meaning **unknown**. A qualified conservative runtime floor; never calculate it just by copying/parsing `compilerVersion`. |

Emit all values explicitly in bytecode. Preserve old members and their meanings; future optional members need defaults
that leave historical metadata unknown rather than changing its meaning. No feature bitset, separate ABI numbering,
JAR manifest authority, or generated check method is needed.

Place a declared marker on every transformed abstract/concrete Schema class. It covers that class's generated Model,
Builder, Factory, Template, and `_DSL` family; do not duplicate it on each generated class. A plain Java Domain API or
interface skipped by the transformation has no emitted Schema ABI to mark. A transformed abstract API class does.
Reject handwritten/conflicting provenance. Ensure annotation propagation does not copy the parent's marker as the
child's own provenance or remove/overwrite the compiler's declared marker.

Per-class metadata survives normal JAR packaging and independent API/Schema publication without a classpath-wide
resource index. Repackagers/shrinkers must retain the annotation, including through class renaming. Stripping it loses
the check; absence cannot be distinguished from a genuine pre-marker binary. It is not a tamper-proof attestation.

### Compiler fact, ABI fact, and floor are different

Compiler version explains origin. Actual generated ABI is the referenced classes/descriptors/calls and behavioral
assumptions. A floor is an accepted conservative bound for that output. Equal version strings do not prove compatible
Groovy, Domain API, adapters, or user dependencies; different strings do not prove incompatibility. Format is not ABI.

Start with one conservative floor per released compiler build, not feature-sensitive analysis. For a future released
4.2.0 compiler, **4.2.0 is the proposed initial floor only after its output is qualified on that runtime**. A simple
Schema can consequently require 4.2.0 under the declared support policy even if it happens to link on 4.1. That trades
older-runtime experimentation for a bounded guarantee; it does not reject older Schema on newer runtimes or newer Schema
on an older Domain API. Lowering a floor later needs dedicated coverage. Patch compilers deliberately retain or raise
the floor; adding emitted linkage must not leave a floor whose runtime lacks it. Qualify behavior as well as symbols.

RF-1 emits an explicit unknown floor. Missing compiler provenance fails Schema compilation rather than borrowing an
unrelated version. Development/RC coordinates remain verbatim provenance; this proposal does not infer their final
stable floor, compare their strings lexically, or advertise an eventual final release as already present. They can emit
unknown floors and qualify an exact immutable candidate graph separately. A stable runtime cannot turn unknown metadata
into proof of compatibility. Automatic floor diagnostics within/across pre-release builds need a separate ordering or
capability decision and are deferred; marker-only delivery does not depend on it.

Bind build resources to the code source of the actual compiler/runtime definition, including the known exploded-build
layout in local tests. A class-relative resource name alone can still follow a parent loader's resource delegation and
pick another copy. Reject ambiguous provenance rather than using the first global resource. Resource generation takes
that module's own build version as input; the prohibition above concerns a consuming application's project version.

## Runtime check and narrow diagnostic

The checker is internal runtime code. Java-only construction retains its existing form, followed by Groovy convenience:

```java
ServicePlan plan = ServicePlan.Create.With(Map.of("name", "worker"));
```

```groovy
def plan = ServicePlan.Create.With(name: 'worker')
```

No new client API, DSL syntax, plugin invocation, or user marker configuration is introduced.

1. In `FactoryHelper.createBuilder`, check the actual selected Schema before `GeneratedBuilderSupport` resolves or
   instantiates its Builder. Cover ordinary and Template creation; retain existing abstract-type rejection.
2. Also check in the existing `GeneratedKlumBuilder(Class)` constructor, before Schema subclass initializers. This
   covers direct generated construction without changing a descriptor or emitting a call to a new runtime helper.
   Framework base allocations/initializers can already have occurred.
3. Read declared markers on the selected class and Schema superclass chain. Highest known floor controls; an unmarked
   ancestor remains unknown. A child cannot hide a higher-floor parent. Check nested selected Schemas at their own
   creation boundary; do not preload the complete reachable Schema graph.
4. Anchor runtime identity to the loaded runtime class's own build resource, not the compiler/plugin or first global
   classpath resource. Compare numeric stable tuples within the supported 4.x contract. A development runtime's ordering
   is unknown in this first slice. If cached, use actual `Class` identity and unload-safe storage, not a class-name map.
5. A known floor above a known stable runtime throws `KlumModelException` before the rejected Schema Builder's
   initializers, configuration callbacks, attachment, and lifecycle work. Earlier parent/sibling effects may exist;
   this is not a transaction. Preserve the focused cause through `InvocationTargetException` on direct construction
   and ordinary factory/script wrappers.

Illustrative diagnostic for future marker-aware releases, not a claim about 4.0.1:

```text
Schema example.ServicePlan requires KlumAST runtime >= 4.3.0; loaded runtime is 4.2.0.
Emitting compiler: 4.3.1. Required by declared marker on example.ServicePlan.
Upgrade the selected runtime/BOM, or rebuild the Schema against the older supported contract.
```

Name the selected Schema, declaring ancestor when relevant, compiler, floor, loaded runtime version, available
code-source/module identity, and remedy. Sanitize local origins in retained evidence. Never rewrite dependencies.

| Input | Behavior |
| --- | --- |
| Valid marker, known stable runtime below known floor | Focused incompatible-runtime exception. |
| Runtime at/above known floor within supported 4.x scope | Allow construction; a passed floor check is not universal compatibility certification. |
| Unmarked legacy type, unknown floor, or development runtime ordering | Preserve execution; no inferred equality, default warning, or compatibility claim. Record unknowns in qualification. Known higher-floor ancestors can still reject on a known stable runtime. |
| Malformed marker, unsupported format, unreadable required runtime identity | Separate metadata/diagnostic-unavailable error on marker-aware checks; do not mislabel as a proven version mismatch or substitute a runtime. |
| Another major | Outside this 4.x proposal; larger numbers do not grant major-version compatibility. Apply that major's policy. |

The boundary requires sufficient JVM linkage to reach runtime construction. Missing supertypes, unsupported class-file
versions, eager custom Factory initializers, and earlier linkage errors can precede it. Passive Model reads,
deserialization, ordinary API calls, and arbitrary user class initialization do not all traverse it. No startup-wide scan
or instrumentation is proposed.

Most importantly, 4.0/4.1 runtimes will not interpret a newly added marker. A marked newer Schema there can still produce
the original linkage error. A new generated helper call would add a missing-member/type failure, not solve this. Keep
dependency guidance and historical negative controls; maintenance retrofits would be separately approved work.

## Compatibility and migration consequences

- Preserve all generated public/protected descriptors and runtime entry points. Historical Schema binaries need no
  mandatory recompilation on the newer runtime, subject to qualification. Rebuilt Schemas acquire provenance.
- Add one generated-only metadata type in an already exported package. Internal packages stay confined. No changes to
  Factory interfaces, `KlumBuilder`, session ownership, Materialization, copy/LINK identity, or Template semantics.
- Add no serialized instance fields or historical Model/Template stream guarantee.
- Independently compile Groovy 3/4/5 producer/consumer inputs; preserve ADR 0011's single production-artifact policy.
  Groovy 3 is classpath-only; Groovy 4/5 also prove JPMS without workaround exports/opens/reads flags.
- Keep the ordinary BOM/platform. Document coordinated versions plus actual resolved/loaded artifacts. Correct
  `docs/user/Gradle-Plugins.md`'s “enforced” wording during implementation without changing resolution policy.
- Retain an older API binary for newer-Schema qualification and compile generic Java clients against API types only.
  Schema provenance does not encode the application's Domain API version.
- A compiled Model/client may call newer runtime APIs independently or need other dependencies. The Schema marker
  describes neither arbitrary Model/client bytecode nor every optional adapter module.

## Qualification for each admitted release

Before claiming directional support, run the following for explicit supported lines. This proposal does not itself
admit a 4.2 gate. KlumAST owns support/admission and release approval; Catwalk owns independent fixtures/baselines.

| Axis | Required inputs and acceptance |
| --- | --- |
| Same-version | Independently publish API/Schema/Model and execute Java/static/dynamic Groovy. Cover new emitted features, lifecycle, keys, inheritance, relationships, converters, Templates and helper linkage. |
| Older Schema/API → candidate runtime | Retain exact historical JARs, application POM/GMM, hashes and toolchains. Consume those binaries without recompiling producers. Preserve semantic expectations as well as linkage. |
| Newer Schema → older API | Publish API separately; compile new Schema against its retained older contract, then run an old API-only Java client. Include a new/missing-API-member rejection control. |
| Diagnostic | Marker-aware below-floor runtime rejects; at/newer-floor controls pass. Include inherited mixed floors, unrelated Schema JARs, legacy/unknown/malformed metadata, classloaders, Template/direct construction and no rejected Schema initializer/callback effects. |
| Historical negative | Retain the real 4.1 Query/4.0.1 runtime linkage failure. Expected incompatibility passes a negative test, never a supported production graph. |
| Groovy/modules | Independently compiled Groovy 3/4/5 producers and consumers, Java leaf without compiler/plugin, classpath all lanes, Groovy 4/5 JPMS and Groovy-3 named-module rejection. No cross-Groovy bytecode promise. |
| Resolution/projection | Resolved marker/implementation, BOM/compiler/runtime/annotations, AnnoDocimal/KlumCast, selection reasons and loaded origins; mirror refresh/Javadoc/archive controls, with no mirrors as downstream inputs. |

Use ADR 0019's two modes:

- **Public-release:** immutable Maven Central/Plugin Portal coordinates and fresh isolated Gradle homes; retain
  application publication metadata too. Revalidate against public endpoints after publication.
- **Candidate-maintenance:** complete manifest and one exact candidate version/repository for candidate marker,
  implementation, BOM/product modules; separate clean preparation/consumer directories and fresh Gradle homes.
  Historical application binaries retain their historical compiler provenance. Explicit candidate-runtime rebinding
  must be evidenced and labelled, never hidden substitution or a historical producer rebuild.

Keep deliberately mixed-version experiments alongside, and distinct from, aligned candidate-product proof. Historical
compiler inputs are not current candidate modules. No `mavenLocal`, included build, checkout classpath, file-only Schema
dependency, public-marker/candidate-implementation mixture, or implicit cache provider qualifies the coordinate-only run.
Local source-based tests remain local evidence.

Keep compilation/publication/resolution/Model-execution/Java-execution outcomes separate. Retain fixture/baseline
revision, candidate SHA, manifest/repository digest, exact GAVs/SHA-256s, actual emitting compiler and marker/floor
(including unknown), loaded runtime, Groovy/Spock/JDK/Gradle, task graph, and complete sanitized diagnostics.

## Thin implementation slices and reasoned commits

### RF-1 — Authoritative marker only

Add the annotation, compiler build provenance resource and Schema emission with unknown floor. Cover keyed/unkeyed,
abstract/concrete, inheritance, duplicate/conflicting provenance, separate compiler loader and source/JAR round-trip.
Assert no new emitted check call, descriptor change, broad export or mirror input. All new tests carry `@Issue("896")`.

One vertical commit: marker/resource/emission with focused artifact tests. A separate guidance commit can explain
availability without enforcement. Acceptance: a binary-only Java reader identifies the actual producer even when
consumer plugin/BOM differ; historical unmarked binaries are unchanged. No floor decision blocks this minimum.

### RF-2 — Stable floor and construction diagnostic

Assign a stable floor after generated-output qualification; add anchored runtime identity and both checks. Controlled
stable-identity fixtures test comparison without pretending development artifacts are released. Real development/RC
markers retain unknown floors and exact candidate graphs are qualified separately. Add below/at/newer-floor witnesses,
ancestor/loader/Template cases, focused exception propagation and no rejected Schema callback/initializer effects.

One vertical commit: floor/checker/boundaries and executable acceptance tests. A separate Java leaf must reach the
diagnostic with no compiler/plugin. Preserve descriptors. Merely comparing plugin strings or using only rewritten
current bytecode does not establish the historical binary direction.

### RF-3 — Retained release qualification

In a separately admitted Catwalk assignment, extend the #896 topology with separately published API and Java-only
leaves. Preserve historical hashes. Two commits: public controls/mixed-version matrix with evidence verifier, then
complete candidate manifest/rebinding with missing/mixed-coordinate rejection. Do not repin an existing baseline as
a shortcut. Acceptance: exact binaries, publication metadata and loaded origins for every row; producer recompilation
or omitted marker/implementation proof fails verification. Select predecessor releases explicitly, not “all 4.x”.

### RF-4 — Release-facing adoption

Once the relevant slice passes, update `docs/user/Gradle-Plugins.md`, `docs/user/Layer3.md`, migration navigation,
`CHANGES.md`, governing issue, and relevant Sonar findings/CI evidence. Add a Java-first diagnostic example and Groovy
documentary construction example with `@Issue("896")`, `@Tag("documentary")` and `@See` linked to user guidance.
One final guidance/adoption commit. Explicitly reconcile ADR statuses; this proposal's delivery does not accept them.
Admitting a release gate is a decision, not an incidental documentation edit.

RF-1/2 require focused Groovy-3 tests, affected module suites, Groovy-4/5 lanes, binary/JPMS and applicable plugin/TestKit
checks; the shared generated/runtime/build contract also requires root `check`. RF-3 runs its independent coordinate
graph and evidence verifier. This design-only change needs documentation/link/diff checks, not Groovy lanes, and claims
no product qualification.

## Adoption decisions and limits

| Decision | Recommendation |
| --- | --- |
| Marker shape/home | One generated-runtime annotation with explicit format/compiler/floor, declared per transformed Schema. |
| Minimum 4.2 delivery | RF-1 can stand alone; RF-2 needs stable-floor and binary diagnostic evidence. No hard release blocker. |
| Floor precision | Conservative stable per-compiler floor; unknown initially, proposed 4.2.0 for released 4.2.0 after qualification. Defer feature-sensitive lowering. |
| Pre-release diagnostics | Exact provenance, unknown ordering. Reconsider only for a concrete need; no ABI/capability framework now. |
| Support lines/gates | Maintainer explicitly selects predecessors and Catwalk promotion/admission. No universal range or showcase promotion inferred. |
| ADR status | Keep Proposed; acceptance and historical implementation-status reconciliation remain explicit decisions. |

All #896 requirements map to RF-1 provenance, RF-2 diagnostics, RF-3 directional/matrix evidence, and RF-4 migration and
adoption. No evidence blocks completion of this proposal; adoption choices are follow-up decisions. No production,
build, test, user-guidance, dependency-policy, GitHub, or Catwalk file changes are included. Related: #896; no closure claim.

## Design validation

`git diff --check` and all 17 relative Markdown links across this plan and the two ADRs passed. Fence/whitespace
structure was checked. `python3 docs/implementation/evidence/issue-896/fixture/verify.py` passed against the retained
JSON evidence: seven positive rows, seven static rejection controls, three query rows (four expected execution linkage
failures), and two projection/archive controls. This verifies the retained record's consistency; it does not replay its
Gradle builds or generate current product/Catwalk results. Groovy lanes were omitted for this documentation-only proposal.
