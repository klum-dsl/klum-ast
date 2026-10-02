# ADR 0025 implementation — portable Schema GDSL

Date: 2026-10-01. Status (reconciled 2026-10-02): GDSL-0 through GDSL-4 complete on supported source/binary
Model topologies; GDSL-5 user documentation/migration/release integration remains.

Decision: [ADR 0025](../adr/0025-portable-schema-gdsl-metadata.md).
Primary issue: [#805](https://github.com/klum-dsl/klum-ast/issues/805), immediate 4.1 QoL intent; release placement is
not a tracker mutation or a backport commitment. #269, #14, and #808 remain separate.

## Authority, baseline, and verified seams

Remote `master` was independently fetched and verified at `3a62c4fa4be72e4a07379face414715243c49c27` on 2026-10-01.
The input report is `docs/implementation/evidence/issue-805-local-versus-portable-gdsl-plan.md` at local commit
`9f137a748018ac36c759acc0f87c31052774fdc6`. The supplied locator had an extra trailing `b`; the named local evidence
branch and report identify this commit unambiguously. Its proposals are inputs, not implemented APIs or native proof.
Live read-only issue/PR checks confirmed #805/#269/#14 open and #809 merged. The maintainer's confirmed contract governs
over the report's optional local-first path and its deferral of semantic overlap checks.
The maintainer subsequently accepted the bounded suffix catalog/generated contributor, `gdsl` vocabulary,
managed-transport validation boundary, and standard-GAV-first publication policy, with `modelType` as the public
mapping target. The executable boundaries below define the plan; the progress records document completed slices.
The maintainer's 2026-10-02 scope correction removes migration analysis for the unreleased manual-resource approach
and for copied generated contributors outside the portable transport. The current contract below reflects that boundary.
The consumer-UX clarification makes the normal Schema dependency authoritative for component/version selection;
GDSL-2 must prove metadata consumption for that result without a second user-maintained GMM version choice.
The maintainer's 2026-10-02 qualification correction bounds portable GDSL to existing supported normal Schema delivery:
a Model's public Builder must be available through the normal supported source-Schema or published-binary Schema
topology. GDSL does not independently guarantee shapes/topologies those prerequisites do not support. Nested Models
are not an established compatibility guarantee. Their adjacent mirror/publication limitations are transferred to
[#825](https://github.com/klum-dsl/klum-ast/issues/825), outside #805 and its release gate, with no support or 4.1 commitment.

| Current seam | Verified behavior / failure path | Planned responsibility |
| --- | --- | --- |
| [Schema plugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java) | `klumSchema` is `KlumExtension`; applies IDEA, registers mirror and shared GDSL resource roots, contributes main compile classpath. | Explicit producer declarations, variant, local editor contribution. Keep mirror lifecycle intact. |
| [Model plugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstModelPlugin.java) / [extension](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumModelExtension.java) | `schemas` extends into `api`; Model marker files feed `processResources`; no IDEA/GDSL hook. | Separate explicit metadata resolution and opt-in IDEA integration, including Model-only builds. Do not reuse `schemas` for metadata. |
| [Base plugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/AbstractKlumPlugin.java) | BOM added to `api`; Maven Publish adds `mavenJava` from `components.java`. | Attach enabled metadata variant once; preserve normal dependencies and plugin application order. |
| [Root helper](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumDslGdslMaterializationPlugin.java) / [task](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumDslGdslMaterializationTask.java) | `materializeKlumDslGdsl` sync extracts framework `com/blackbuild/klum/ast/gdsl/**/*.gdsl`; duplicates use `EXCLUDE`. A plain file is treated as ZIP. | One validated sync for additional metadata providers. Retain framework compatibility; never apply `EXCLUDE` to Schema payload conflicts. |
| [Aggregate](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsAggregationPlugin.java) | `generateAllKlumDslSourceMirrors` depends on local mirrors; mirrors depend on root materialization. | Preserve manual refresh. Binary-only consumers run root materialization; no external mirror task. |
| [TestKit suite](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy) / [dsl-g fixture](../../klum-ast-gradle-plugin/src/test/fixtures/dsl-g/build.gradle) | Cache, publication, downstream isolation, and IDEA model assertions; synthetic generated contracts. | Reuse isolation assertions, add real Schema and separate fresh-cache Model consumer. `.iml` is not native import proof. |

The architecture map, CONTEXT, ADRs 0005/0011/0015/0019/0022, Builder migration, agent testing/commit rules, and current
Gradle guides were read. Implementation belongs in `klum-ast-gradle-plugin`; compiler/runtime changes are unnecessary.
Current wrapper is Gradle 8.14.4; plugin toolchain is Java 17. The plugin module has no `groovy4Tests`/`groovy5Tests` tasks.

## API ledger and authoring example

**Existing names retained:** `com.blackbuild.klum-ast-schema`, `com.blackbuild.klum-ast-model`, `klumSchema`, `klumModel`,
`schemas { schema ... }`, `mavenJava`, `materializeKlumDslGdsl`, `generateAllKlumDslSourceMirrors`,
`createKlumDslSourceMirrors`, and root `build/generated/klum-dsl-ide/gdsl`.

**The producer and binary consumer `gdsl` APIs are implemented; the complete IDE feature is not yet released.** No new plugin
ID/module is introduced. The binary GMM syntax is versionless explicit module selection; it follows the normal selected Schema identity. GDSL-0 records the completed decision
baseline; do not advertise this facility as available now.

| `gdsl` role | Owner and purpose |
| --- | --- |
| Mapping declarations | Schema Developer declares suffix-to-`modelType` mappings. |
| Metadata variant/archive | Schema publication exposes the optional, same-GAV editor payload. |
| Consumption/dependency configuration | Model explicitly opts in and selects metadata for the normal selected Schema; no independent version authority. |

```groovy
// Schema build: Schema Developer owns and versions the reusable convention.
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klum-version>'
    id 'maven-publish'
}
group = 'org.example'
version = '1.2.0'
klumSchema {
    gdsl {
        publish = true
        mappings {
            environment {
                fileNameSuffix = '.environment.groovy'
                modelType = 'example.Environment'
            }
        }
    }
}
```

```groovy
// Separate Model build: the normal Schema dependency selects the version.
plugins { id 'com.blackbuild.klum-ast-model' version '<klum-version>' }
klumModel {
    schemas { schema 'org.example:environment-schema:1.2.0' }
    gdsl { enabled = true }
}
dependencies { klumGdsl 'org.example:environment-schema' }
```

The versionless `klumGdsl` declaration explicitly selects the Schema metadata. `enabled = true` alone
does not identify a Schema or trigger arbitrary dependency scanning. GMM declarations reject supplied versions;
the normal dependency is the sole version authority. Repository configuration is the consumer's normal
responsibility. In one build the normal Schema dependency uses `project(':schema')`; explicit metadata selection must
follow that selected component through its capability, without reaching into its outgoing configuration or task.
For a POM-only/lossy repository, the explicit fallback is:

```groovy
dependencies {
    klumGdsl 'org.example:environment-schema:1.2.0:gdsl@jar'
}
```

This fallback intentionally repeats the exact Schema GAV because a POM cannot describe the capability relationship.
It must match the normal selected identity/version, rejects dynamic/range versions, and never silently retries a
classifier or falls back automatically. It is not the conceptual preferred GMM consumer workflow.

Managed extension types in `com.blackbuild.klum.ast.gradle`: `KlumSchemaGdslExtension` (`publish`, default
false; named `mappings`), `KlumModelGdslExtension` (`enabled`, default false), and `KlumGdslMapping` (`fileNameSuffix`,
`modelType`). Expose nested `gdsl(Action)` on the existing extensions; avoid an unrelated Groovy-version API change.
Empty enabled producer metadata is intentional and retires mappings. Opted-in producers also contribute their own
archive to local root refresh for source authoring, even without a Model module or Maven Publish. Model opt-in adds
only explicit dependencies. Repeated local/project resolution of the same producer is deduplicated by origin/hash.

Schema authors name only the Model. The generated contributor resolves that Model and then its real public Builder
using the generated-contract convention inside the adapter. This illustrative v1 contributor makes that separation clear:

```groovy
contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('.environment.groovy')) {
        def model = findClass('example.Environment')
        if (!model?.modifierList?.findAnnotation('com.blackbuild.klum.ast.DSL')) return
        def builder = model ? findClass("${model.qualifiedName}_DSL.Builder") : null
        if (builder != null) delegatesTo(builder)
    }
}
```

Keep `scriptScope()` and the final-name suffix test: `extension: 'environment'` describes a different filename family.
This generates editor metadata only; it adds no method catalog, Script class, or runtime dispatch rule.
Verify the internal Model-to-Builder lookup against actual supported generated names and source/binary PSI; do not
require users to spell or override the Builder name when resolution fails. Missing/non-DSL Models and missing Builders
are technical negative controls. Model-shape compatibility is bounded by the supported prerequisite delivery paths;
nested-Model qualification is not a required portable-GDSL control.

## Publication and payload contract

| Surface | Accepted contract |
| --- | --- |
| `generateKlumGdslMetadata` | Cacheable generator of catalog and deterministic script-scoped GDSL, no supplied code execution. |
| `klumGdslJar` | Reproducible metadata-only JAR, classifier `gdsl`; built from generator providers, not main output. |
| `klumGdslElements` | Consumable only, no dependencies/constraints or `extendsFrom`; attached to Java component only when opted in. |
| Capability | Only `<schema-group>:<schema-artifactId>-gdsl:<schema-version>`; omit default library capability. |
| Attributes | Category `documentation`, DocsType `klum-gdsl`, Usage `klum-ide-metadata`, Bundling `external`; artifact type `jar`. No JVM/Groovy/library-elements compatibility. |
| `klumGdsl` | Declarable dependency scope only; classifier notation explicitly routed separately from variant requests. |
| `klumGdslClasspath` | Resolvable only, non-transitive, matching metadata attributes/capability requests; GMM mode. |
| `klumGdslClassifierClasspath` | Resolvable only, non-transitive, artifact-only mode; no capability requirement. |
| Archive identity | Schema GAV and format `1` in manifest (`Klum-Schema-Coordinates`, `Klum-Gdsl-Format`); no independent editor version. |
| Envelope | `META-INF/klum-ide/gdsl/v1/mappings.json` plus `<mapping-id>.gdsl` in that directory. Sorted catalog entries contain ID, suffix, `modelType`, payload SHA-256; no author-supplied Builder name. |
| Root destination | `schema-owned/<UTF-8-hex-group>/<UTF-8-hex-artifact>/<mapping-id>.gdsl`; version absent so refresh replaces rather than accumulates. |

Use canonical sorted catalog serialization, a fixed v1 generator template, ASCII `[a-z][a-z0-9-]*` IDs, and
NFC-normalized case-sensitive suffixes; forbid separators/control characters and require a nonempty suffix ending in
`.groovy`. Verify canonical serialization/template compatibility in GDSL-1. Preserve Model qualified names as data
and escape generated strings. Consumer verifies envelope, identity, catalog/hash/entry correspondence, and that payload bytes match the v1
generator template. No unchecked sidecar assertions beside arbitrary executable GDSL. Unsupported formats fail with
origin and upgrade guidance. An empty catalog is valid; extra executable entries, duplicate ZIP entries, absolute/
traversal paths, malformed/missing fields, and unsupported predicates fail before sync.

Attach using `AdhocComponentWithVariants.addVariantsFromConfiguration`, not merely `publication.artifact(...)`.
If Maven mapping is required, use optional/runtime mapping; empty dependencies mean the POM graph stays unchanged.
`.module` gains only the opted-in metadata variant/capability/artifact. The accepted standard-GAV-first policy rejects
`mavenJava` GAV overrides inconsistent with project identity before publication, rather than emitting misleading
capabilities/manifests; broader customized artifact-ID support is outside this delivery.
Test both Maven-Publish/plugin application orders. No external publication is needed for implementation tests.

## Alignment, conflicts, lifecycle, and managed validation

1. The normal Schema dependency is the authoritative component/origin/version selection. Metadata is editor content
   for that result, not a second version choice. Model opt-in and explicit identification/selection of the desired
   Schema metadata are both required; no arbitrary dependency scanning. Prefer safely selecting the capability from
   the already selected normal Schema component over repeating its version. Prove the least repetitive safe GMM syntax
   in GDSL-2 rather than freezing a declaration here. Resolve normal Schema identities lazily, without compiling them.
   The isolated GMM resolver may use
   `shouldResolveConsistentlyWith(compileClasspath)`; this is a **mechanism to prove**, not an accepted guarantee.
   Verify custom-capability selection, application BOM upgrades, locks, exclusions, project substitution, and selected
   origin equality. BOMs, constraints, locks, substitutions, upgrades, and conflicts select the normal Schema;
   metadata follows that result without establishing another version decision. Missing normal Schema identity or
   unequal selected versions fails. Do not import `api`/BOM dependencies into metadata or silently select another
   version. If a safe nonduplicated declaration cannot be proved with this capability/variant model, record evidence
   and return for a maintainer UX decision instead of silently settling on duplicated version declarations.
   Classifier mode permits exact-GAV repetition as a lossy fallback limitation, requires it to match the
   selected binary Schema; reject dynamic/range versions and do not auto-upgrade its classifier.
2. Root union covers all active producer/consumer mappings and normal Schema versions in imported projects where
   project-wide GDSL can affect scripts, including a non-opted-in Model with a conflicting normal Schema version.
   Fail on two versions of one group/artifact, different hashes for one GAV, duplicate payload entries/registrations,
   or duplicate semantic mappings. The same resolved artifact requested by two Models is one input, not an error.
   Equal basenames across distinct non-overlapping Schemas are safe because output is namespaced.
3. For literal suffixes, reject `a.endsWith(b) || b.endsWith(a)` for every pair, including equal targets and suffixes.
   Disjoint `.environment.groovy` and `.deployment.groovy` pass; `.groovy` with either fails. Never resolve by priority.
   Error names both origins, versions, mapping IDs, suffixes, and a witness filename. No per-module scoping claim.
4. Pass files and immutable origin/catalog values into cacheable tasks; no `Project`, `Configuration`, or resolution
   objects in task actions. Inputs include payload bytes, origin/version, and normalized catalog, not just GDSL files.
   Preserve provider task dependencies. One sync removes stale contributions on add/edit/rename/remove/empty/disable
   refresh; root `clean` owns root output. A disabled last Model with no other root owner requires `clean` to remove
   leftover output. Validate before sync and label failed refresh output stale. Configuration-cache reuse does not
   establish Isolated Projects support.
5. KlumAST's project-wide validation covers only mappings delivered through the portable managed GDSL metadata
   mechanism. Arbitrary `.gdsl` resources in source/resource roots or normal dependency artifacts remain user-owned
   IntelliJ configuration and are not parsed, transported, rejected, or conflict-checked by KlumAST. Copied generated
   contributors outside the managed transport are external resources too. Do not scan normal sources/resources/JARs,
   infer mappings from executable contributors, or require removal/republication of normal artifacts. Existing
   framework GDSL materialization is preserved independently of the Schema metadata transport.

## Thin slices and reasoned commit boundaries

All slices implement #805; no new issue or GitHub mutation is part of this plan. Each slice has a green local commit
with meaningful coverage, then a focused evidence/docs commit if necessary. No intermediate producer-only release.

### GDSL-0 — Accepted contract baseline (complete)

Dependency: none. This ADR/plan records the accepted bounded literal suffix catalog and generated contributor,
`modelType` declaration with internal real-Builder resolution, `gdsl` Gradle vocabulary, managed-transport
validation boundary, and standard-GAV-first identity policy. Portable-first binary delivery, isolation, opt-in, and
conflict/overlap rejection are settled. Implementation starts at GDSL-1; no further maintainer confirmation is a
prerequisite. Do not expand into arbitrary predicates or weaken rejection during implementation.

### GDSL-1 — One Schema mapping to one selectable metadata artifact (implemented)

Depends on GDSL-0. One reasoned implementation commit adds managed declarations, catalog/generator validation,
reproducible archive, custom outgoing variant and publication integration, with producer TestKit coverage. Tests prove
off-by-default, empty opt-in, deterministic bytes/relocation/cache, safe provider wiring, local suffix overlap rejection,
no code execution, exact GAV/capability/attributes, and enabled/disabled `.module`/POM/archives. Attribute-poor ordinary
default-capability consumers must never select metadata; sources/Javadocs retain their own variants. The catalog
preserves `modelType`, and the generator template owns the internal Builder lookup. Actual source/binary resolution
and missing/non-DSL target controls are proved at GDSL-4. This slice is a producer boundary only and cannot qualify delivery.

### GDSL-2 — Binary Schema metadata to a Model-only editor root

Depends on GDSL-1. One vertical commit adds explicit Model opt-in, both isolated resolvers, alignment/origin validation,
root validated sync, IDEA registration, and an isolated Maven-repository producer-to-Model TestKit fixture. No Schema
plugin, producer project, mirror, attached contributor source, or compilation fallback in that consumer. Verify GMM
capability consumption, explicit classifier against POM-only metadata sources, missing variant/classifier errors,
wrong origin/version rejection, normal graph isolation, and configuration-cache reuse of resolution/materialization.
The binary fixture must also prove the consumer-UX contract:

- One authoritative normal Schema version selection, explicit GDSL opt-in, and explicit selection of its metadata;
  the metadata resolves to exactly the selected normal Schema origin/version.
- The least repetitive safe GMM syntax follows the normal selected component; users do not independently maintain
  a Schema-GDSL version. Application BOM/constraint upgrades select matching metadata without a stale metadata version
  declaration where the proven mechanism permits it. Opt-in does not scan other Schema dependencies.
- Locks, substitutions, and version-conflict resolution preserve the same origin/version invariant. Deliberately
  mismatched metadata fails clearly before materialization rather than supplying another Schema version's editor hints.
- POM-only classifier mode is tested separately with repeated exact GAV, dynamic/range rejection, mismatch failure,
  and no silent retry/automatic fallback.

If Gradle cannot safely provide the nonduplicated GMM declaration, retain the executable evidence and return for a
maintainer UX decision. Do not count duplicated version declarations as an accepted normal-workflow substitute.
This is the first complete automated transport path; native release proof remains pending.

### GDSL-3 — Source authoring and project-wide conflicts/removal

Depends on GDSL-2. One commit adds the producer's local archive-provider path and project dependency integration,
root union validation across two Schemas/two Models, and lifecycle/isolation controls. Acceptance includes plugin
application order, one physical root, no second sync writer, equal basenames, idempotent shared artifact requests,
distinct duplicate registrations/entries, hash/version/overlap failures before sync, BOM/lock/substitution cases,
disabled nonconsumer conflict, edit/rename/remove/empty/last-disable cleanup, and external-resource boundary controls.
Framework GDSL still materializes once. Included/composite-build compatibility remains unclaimed
unless a variant-based separate-owner fixture passes; never couple the two root tasks.

### GDSL-4 — Real generated contracts and native source/binary gate

**Complete on the supported source/binary Model topologies represented by the acceptance fixture.**

Depends on GDSL-3. Retain a minimal real Schema/runtime fixture and native contributor tests with negative controls;
then record the native Gradle-import runs below. A separate reasoned commit stores reproducible fixture instructions
and actual outcomes, including failures/limits. The native importer must be fixed through editor-only registration if
needed before acceptance. Runtime tests use existing public root and owned-child factories; no compiler/runtime change.
The retained manual native procedure and recorded clean-profile runs provide native qualification; automated
GroovyShell dispatch coverage is complementary and is not native PSI evidence. The adjacent nested-Model experiment
is retained as the #825 reproduction, outside #805 and this gate.

### GDSL-5 — Document migration and qualify the complete candidate

Depends on GDSL-4. One final commit synchronizes `docs/user/Gradle-Plugins.md`, `Gradle-Onboarding.md`,
`Convenience-Factories.md`, `FAQ.md`, Builder migration/navigation, and `CHANGES.md` with the confirmed surface,
Schema versioning, explicit fallback, project-wide effects/conflicts, refresh/activation, the managed-validation boundary,
and editor limits.
Examples distinguish Schema mapping declarations, outgoing metadata variant/archive, and Model consumption configuration.
Document the GMM syntax proved in GDSL-2 and exact-GAV repetition as a lossy fallback limitation.
Add reciprocal documentary references. Reconcile issue/release documentation later under normal authorized delivery;
this task changes neither tracker nor release state. Candidate verification uses one staged plugin marker/implementation/
BOM version and Schema binary plus metadata, then a clean coordinate-only Model build. Release gate is complete transport
plus native proof, not merely an archive on disk. No ordinary PR acquires a new external showcase dependency.

## Executable acceptance boundaries

New executable classes use `Test` suffix and `@Issue('805')`. Proposed cohesive classes:
`KlumGdslMetadataPublicationTest`, `KlumGdslBinaryConsumerTest`, `KlumGdslProjectWideValidationTest`, and
`DelegatingScriptGdslDocumentaryTest` (the last also `@Tag('documentary')` and `@See` to the delivered user page).
Reuse existing ProjectBuilder/TestKit fixtures rather than duplicating their harness. These names are planning targets;
no test added here is implied to have passed.

| Gate | Executable action | Required assertions / failure control |
| --- | --- | --- |
| Producer | Publish `mavenJava` to a temporary repository, with opt-in off/on/empty and both plugin orders. | Only on adds `gdsl`/GMM variant; ordinary POM dependencies and binary/source/Javadoc entry sets unchanged; archive/capability/origin versions agree; incompatible publication customization fails. |
| Binary Model | Fresh consumer directory and Gradle home; capability request, then separate POM-only classifier request; run `materializeKlumDslGdsl`. | Correct archive/root bytes, matching normal Schema identity, no source checkout/mirrors; both modes pass independently; absent/wrong/unsupported archive fails usefully. |
| GMM consumer UX | Use one authoritative normal Schema selection with explicit metadata opt-in/selection; upgrade via application BOM/constraint, then test locks, substitution and version conflicts. | Metadata follows exactly the selected normal origin/version without an independently maintained stale metadata version; mismatches fail clearly. Prove the least repetitive safe syntax or return evidence for a maintainer UX decision. |
| Lossy fallback UX | Request the explicit exact-GAV `gdsl` classifier in a POM-only fixture, including dynamic/range and mismatched requests. | Exact GAV repetition is a fallback limitation; selected normal Schema identity/version matches; dynamic/range and mismatch requests fail; no silent retry or automatic fallback. |
| Isolation | Run clean compile/test/JAR/source/Javadoc/publication and inspect inputs, task graph, all resolvable/output configurations. | No new metadata resolution/materialization on ordinary paths; no envelope/contributor/root/mirror in normal artifacts/classpaths/module path or exported Model deps. Enabled Schema publication builds metadata archive only as its intentional addition. |
| Integrity/conflicts | Inject duplicate entries, path traversal, wrong manifest/hash/template, conflicting versions, matching suffixes among managed mappings. | Error identifies origins before sync; previous output is not reported current; no silent EXCLUDE or fallback. Normal non-opted-in Model version conflict is included; arbitrary external GDSL is outside managed validation. |
| Cache/lifecycle | Repeat, edit, rename, remove, empty publication, opt-out, relocate, restore cache; configuration-cache store/reuse. | Expected SUCCESS/UP_TO_DATE/FROM_CACHE, stale paths removed, framework kept, root physical identity unique; last-owner `clean` removes leftovers. |
| Normal downstream | Publish the Model, resolve it in ordinary Java/Groovy and Model consumers. | No editor deps propagated; ordinary schemas/API unchanged; fresh later Model needs explicit opt-in. |
| Runtime truth | Real transformed Environment plus Deployment child map; root `Environment.Create.From`, owned-child `AsBuilder().From`, ordinary `Create.With` control. | Expected region and one normal materialization/session; matching filename changes no runtime dispatch. Wrong receiver control retains existing failure rather than acquiring fictional operations. Keyed class/File/key-provider and classpath-marker defaults retained. |

Focus development on `:klum-ast-gradle-plugin:test --tests <focused-class>` with Groovy 3; finalize the affected plugin
suite and external real Schema/Model scenarios selected separately for Groovy 3/4/5 (Java 17 baseline). Run the affected
repository modules' actual `test`, `groovy4Tests`, and `groovy5Tests` lanes for real transform/runtime coverage; do not
invent plugin compatibility task names or share compiled fixtures between generations. `git diff --check`, local
links/anchors, documentary links, and the applicable user-doc renderer/crawl complete documentation checks.

### Native IntelliJ: actual import qualification and complementary automation

**Contributor automation:** real generated contracts and a GroovyShell adapter prove contract shape and generator
dispatch across Groovy 3/4/5; the adapter does not prove native PSI behavior. A pinned IntelliJ Platform/Groovy plugin
harness would complement the retained manual native procedure, but was not added and is not an outstanding GDSL-4
criterion. Native completion at `reg`, resolution of `region('eu')` to `Environment_DSL.Builder.region(String)`,
Parameter Info, nonmatching suffix, same-suffix ordinary class, missing/non-DSL Model, missing Builder, and wrong
argument controls are qualified through actual imported projects below. Unknown-method navigation and inspection
are recorded separately; inspection parity is not claimed. Authors supply `modelType`, never a Builder name.

**Native Gradle-import gate:** retain `docs/implementation/fixtures/portable-gdsl-ide/` with source-authoring,
Schema-producer, and separate binary-Model builds plus a README/run matrix. Import through IntelliJ's Gradle importer
into a clean trusted project/profile, without hand-marking roots or using old resources. Record fixture SHA, product
coordinates/repository, IDEA build, bundled Groovy plugin, JDK/Gradle, and activation/indexing state.

1. Source run: opted-in Schema, real mirrors refreshed through `createKlumDslSourceMirrors`/aggregate, and in-build
   Model selecting its metadata by project capability. Also run Schema-only source authoring. Confirm actual imported
   generated resource/source roots despite `build/` exclusion; no manually edited `.iml` or SourceSet workaround.
2. Binary run: publish normal Schema and metadata to an isolated repository; open only the Model build. Confirm real
   compiled Builder resolution, no producer sources/mirrors/attached library GDSL, then explicit consumer root refresh,
   Gradle reload/file refresh and activation if prompted. Repeat POM-only classifier mode independently.
3. Open `catalog.environment.groovy` using `@BaseScript DelegatingScript` and bare `region 'eu'`; compare ordinary
   typed factory control, `catalog.other.groovy`, same-suffix non-script class, wrong method/value, and missing Builder.
   Record completion signatures and navigation targets; report inspections as observed, separately from runtime
   rejection. No unknown-method proposal or phantom declaration is acceptable; inspection parity is not invented.
4. Change/remove/rename mapping, upgrade to an empty metadata version, disable/remove dependency, refresh/reimport and
   restart. Old completions disappear; record any IDE reactivation requirement. Two conflicting mappings/versions
   fail refresh and are not reported as accepted current metadata; project-wide effects are explicitly checked.
5. Run the real recipe via root and owned-child paths and record configured values alongside editor evidence.
   An existing #809 native PSI result or #797 `.iml`/editor evidence is a control, not proof of this new import path.

Release is blocked if either supported native topology lacks discovery after the documented lifecycle. Model shapes
whose mirrors or normal publication are unsupported do not independently expand this gate; #825 retains the nested
discovery for a separate supported-contract decision. Pin the tested editor
versions; do not extrapolate to Eclipse, VS Code, future IDEA versions, or Quick Documentation.

## Risks, decisions, and requirement traceability

| Requirement / risk | Owner / decision state | Slice and acceptance |
| --- | --- | --- |
| Portable opt-in includes binary Model first delivery | Confirmed maintainer contract | GDSL-1/2/4/5; fresh binary and native gates |
| Explicit declarations, metadata capability/classifier fallback | Accepted `gdsl` vocabulary; `modelType` names the Model, adapter resolves its Builder | GDSL-0/1/2/4; selection, missing-target and native resolution controls |
| Normal artifact/classpath isolation | Confirmed | All slices; isolation/downstream gates |
| Schema-versioned project-wide mappings | Confirmed; consistent-resolution mechanics require proof | GDSL-2/3; BOM/lock/version/nonconsumer conflict matrix |
| Authoritative normal Schema selection / consumer UX | Accepted invariant; least repetitive safe GMM syntax requires proof, not a frozen duplicate version declaration | GDSL-2; explicit selection, BOM/constraint upgrades, locks/substitution/conflicts and mismatch controls |
| POM-only version repetition | Accepted lossy fallback limitation; exact selected GAV only | GDSL-2; dynamic/range/mismatch rejection, no retry/automatic fallback |
| Overlap/duplicate rejection | Accepted bounded literal suffix catalog/generated contributor | GDSL-0/1/3; witness, payload/template and union controls |
| External executable GDSL cannot be proved disjoint | Validation owns only the portable managed transport; external resources remain user/IDE-owned | GDSL-0/3/5; external resources neither interpreted nor transported; managed conflicts still fail |
| Publication customization and capability identity | Accepted standard-GAV-first reject-on-mismatch policy; broader customization outside delivery | GDSL-0/1; publication identity test |
| Native importer ignores generated root under build/ | Technical release risk, not permission to pollute resources | GDSL-4; actual Gradle import, stop for supported registration fix |
| Included builds / Isolated Projects | Not claimed; future acceptance if required | GDSL-3 topology boundary |
| Runtime suffix does not constrain receiving factory | Existing runtime boundary; #269 remains distinct | GDSL-4 runtime wrong-receiver control |
| Eclipse/VS Code | #14/#808 distinct, no parity promise | GDSL-5 truthful user guidance |

No product or maintainer decision is a prerequisite to starting implementation. Technical unknowns (least repetitive
safe GMM consumer syntax, Model-to-Builder resolution, alignment, catalog/template integrity, import discovery,
activation/cache behavior, and managed-union validation) must be resolved by the specified tests before feature
publication. Failure to prove the consumer UX invariant requires a maintainer decision supported by evidence;
duplicated GMM version declarations must not become the default silently. Accepted design does not establish passed tests.

## Planning validation and handoff boundary

This change adds only this plan and its ADR. Planning validation checks whitespace, relative links/heading anchors,
the requirement-to-slice matrix, scope/history, and current remote base. Groovy lanes, TestKit and native IntelliJ were
not run: no executable/build input changes are made. The user-doc renderer does not render these engineering-only pages;
use bounded Markdown checks here and run user-site generation when GDSL-5 changes user pages.

Public primary sources re-read for planning: [Gradle 8.14.4 custom publication](https://docs.gradle.org/8.14.4/userguide/publishing_customization.html),
[consistent resolution](https://docs.gradle.org/8.14.4/userguide/dependency_resolution_consistency.html), and
[pinned IntelliJ GDSL index](https://github.com/JetBrains/intellij-community/blob/4aee219fbbbc89d8497ebb02a7db8d61db3d99f6/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/GroovyDslFileIndex.java).
They support the architectural direction, not a passed custom-variant or native-import experiment.

Planning delivery is a draft PR from an isolated issue branch, as authorized after maintainer acceptance. No product
implementation, issue mutation, release, artifact publication, child task, or delegated review belongs to this
assignment. The Hive receives the PR/commit-addressable handoff and performs reconciliation; an open PR remains
`(PR:open)` until delivery is reconciled, and the worker does not self-archive.


## GDSL-1 engineering contract and evidence

The Schema extension is now `KlumSchemaExtension`, a subtype of the existing `KlumExtension`, with nested
`KlumSchemaGdslExtension` and named `KlumGdslMapping` declarations. `publish` defaults to false. Disabled declarations
attach no variant or publication artifact and register no metadata tasks. Enabled declarations are frozen and validated
after all projects have been evaluated, before attaching the outgoing variant. This observes late project-GAV
configuration as well as late publication overrides; it does not claim Isolated Projects support. Each mapping declares a Model qualified name;
no compiled Model inspection, source scanning, or Schema compilation is needed to generate metadata. Declaration-shape
validation rejects malformed names and public Builder names; actual missing/non-DSL Model and Builder PSI controls
remain GDSL-4, as planned.

`GenerateKlumGdslMetadata` accepts scalar maps of suffixes and Model names plus Schema coordinates as task inputs.
Neither this task nor the publication-identity validator accesses Project or resolution objects in its action.
`KlumGdslJar` is a cacheable Jar specialization with reproducible entry ordering and timestamps. Its source provider
retains the generator dependency. It packages only the envelope and manifest; normal build tasks do not depend on it.
The separate generator directory is `build/generated/klum-gdsl-metadata`, outside all SourceSets. No local contribution
is wired into the root editor refresh in this producer slice.

The exact canonical v1 catalog is a UTF-8 JSON object with one `mappings` array, terminated by a single LF. Entries are
sorted by ASCII mapping ID, with keys in this fixed order: `id`, `fileNameSuffix`, `modelType`, `payloadSha256`.
Suffixes use NFC and case-sensitive literal comparison. JSON escapes quotes and backslashes; the generated Groovy
single-quoted strings escape quotes and backslashes. SHA-256 is lowercase hexadecimal over the generated payload's
UTF-8 bytes. An empty catalog is exactly `{"mappings":[]}\n` (where `\n` denotes the terminal LF).
`KlumGdslMetadataFormat` owns the fixed script-scoped template; there is no supplied-code or contributor-file input.
It looks up the Model, checks its `@DSL` annotation, then looks up the real public `model.qualifiedName + '_DSL.Builder'`
and delegates only when that class exists. This records template behavior, not native IntelliJ proof.

`klumGdslElements` is consumable only, has only the custom `<group>:<artifact>-gdsl:<version>` capability and the
attributes frozen above, and has no parents, dependencies, constraints, or library/JVM attributes. It is attached to
`components.java` using optional/runtime Maven mapping. GMM contains the separate metadata variant; Maven receives the
classifier archive with no additional POM dependency. `validateKlumGdslPublication` compares the final configured
`mavenJava` GAV to the Schema identity before POM/GMM generation or publication, including late `afterEvaluate`
publication overrides and late Schema identity changes. The producer requires an explicit Schema group and version.

Executable evidence is in `KlumGdslMetadataFormatTest` and `KlumSchemaGdslProducerTest` (all new tests carry #805).
The producer's documentary feature `metadata generation is lazy reproducible relocatable cacheable and supports empty
retirement` is linked to this engineering plan while the complete user-facing feature remains unavailable.
Coverage includes both plugin application orders, off/on artifact and metadata behavior, normal POM graph isolation,
attribute-poor default-capability and sources/Javadoc selection, explicit custom-capability selection, malformed/duplicate
IDs and Models, separator/control rejection, NFC overlap, same-target overlap, case sensitivity, literal code-shaped
suffix data, empty retirement, byte identity across independent relocated regeneration, build-cache restoration of both
tasks, configuration-cache reuse, all three publication-GAV override fields, late Schema identity changes with/without inconsistent publication overrides,
and publication configuration-cache reuse.

No consumer plugin API, metadata resolver, Model configuration, root-union lifecycle, user page,
release note, or native IDE evidence is added. The issue remains open and the release gate is unchanged.


## GDSL-2 engineering contract and evidence

`KlumModelExtension.gdsl { enabled = true }` enables binary metadata consumption, with explicit selections in the
new declarable `klumGdsl` scope. GMM selections use `klumGdsl 'group:artifact'` and reject independent version
constraints. Exact `group:artifact:version:gdsl@jar` requests take the separate POM-only path. There is no metadata
lookup without an explicit selection and no classifier retry. Disabled consumption adds no resolver, root task, or
IDEA registration. Both resolvers are non-transitive and inherit only their private routed dependency scopes.
No normal compile/runtime/test scope inherits editor dependencies; metadata is not exported by Model publications.

GMM uses `shouldResolveConsistentlyWith(compileClasspath)` and `requireFeature('gdsl')`, available in the repository's
Gradle 8.14.4. A plain Gradle module substitution resets capability selectors; the consumer therefore restores the
metadata selection last, deriving the exact binary module identity from the selected normal dependency edge. This
preserves same-module version changes and renamed-module substitutions without an author-maintained GDSL version.
The normal graph supplies BOM/constraint/conflict/lock selection. Absent or excluded normal selections fail. Project
substitution and local producer contribution remain GDSL-3. Classifier selection must preserve its exact authored GAV
and match the selected normal Schema; dynamic/range requests are rejected rather than upgraded.

The root task takes immutable archive/selected-GAV pairs and normal coordinates as inputs. Each
`KlumGdslArtifactInput` contains only a `File` and coordinate string; the nested file input uses `PathSensitivity.NONE`
and the paired coordinates are a scalar input. Resolver providers sort each mode's pairs by coordinates, without
keying registration or lookup by archive basename. Resolution is lazy and outside the task action; the action accesses
no Project, Configuration, or resolution objects. Each explicit selected origin must supply exactly one archive;
zero or multiple advertised payloads fail before sync.
It checks manifest identity/format,
ZIP names, canonical catalog serialization, hashes, and exact v1 template bytes before sync. Output is only payloads
under the ADR's hexadecimal group/artifact paths, with no catalog or envelope. The shared root keeps framework GDSL.
IDEA generated-resource registration uses the existing root directory outside SourceSets. A failed refresh labels prior
output stale; a previously successful root remains untouched by validation failure. Gradle may clean unowned output
before a task's first execution, so this is not a promise to retain arbitrary manually planted files.

Executable coverage lives in `KlumModelGdslConsumerTest`, `KlumGdslArchiveTest`, and
`KlumModelGdslBinaryContractTest`, all carrying `@Issue('805')`. The consumer's happy path is marked documentary and
linked here: `KlumModelGdslConsumerTest#'binary Model follows the normal Schema constraint without a second metadata
version and caches refresh'`. It uses separate producer/Model directories and an isolated temporary Maven repository.
Controls cover GMM and POM-only selection, BOMs/constraints/conflicts, dependency locks, same/renamed-module substitution,
configuration-cache reuse, unrelated Schema metadata not being scanned, missing/empty/multiple variant payloads or classifier, excluded normal
Schema, stale independent GMM versions, dynamic/range classifier rejection, mismatched classifier versions, ordinary
build laziness, and publication/classpath isolation. The focused feature `binary Schemas with identical archive
basenames retain independent identities in #mode mode` independently publishes `org.one:shared-schema:1.0` and
`org.two:shared-schema:1.0`, both named `shared-schema-1.0-gdsl.jar`, with different Models and suffixes but the same
mapping ID. GMM, POM-only classifier, and mixed selections prove both normal GAVs, both paired metadata inputs,
manifest/catalog validation and exact contributor bytes under separate coordinate-derived paths. Each mode reuses
the configuration cache and restores the root `FROM_CACHE` after relocating both the Model and resolved archive
locations into a copied repository and fresh Gradle home. This hardens binary identity only; project-wide overlap,
and version conflicts remain GDSL-3. Archive tests cover empty metadata, duplicate ZIP names, unsafe
paths, absent/extra entries, malformed JSON/fields, wrong format/origin/normal version, and hash/template tampering.

The real binary-contract fixture compiles and publishes a real `@DSL Environment` independently with Groovy
3.0.25, 4.0.32, and 5.0.6, then compiles and executes a separate Model with only the published Schema and framework
binary libraries. Each generation has its own fixture/Gradle home and compiler outputs. The materialized contributor
runs in a GroovyShell dispatch stub using reflection on the actual compiled Model/annotation/public generated Builder,
checks `Environment_DSL.Builder.region(String)`, and executes `Environment.Create.From` with a DelegatingScript recipe
plus the ordinary `Create.With` control. This proves the adapter spelling and binary contract, not native PSI or
IntelliJ Gradle import. Native discovery, activation, navigation, negative PSI controls, and owned-child runtime coverage
remain GDSL-4. Source-project union/conflicts/removal and lifecycle isolation remain GDSL-3.

This engineering slice changes no independently released user workflow: current user pages still describe the incomplete
DelegatingScript IDE path. User documentation, migration guidance, and CHANGES stay with GDSL-5; no release claim is
added here and issue #805 remains open.

Local validation for GDSL-2 (2026-10-01): `:klum-ast-gradle-plugin:check` passed with 129 tests, license checks, and
`validatePlugins`. This includes isolated real Groovy-3/4/5 binary fixtures, GMM/classifier configuration-cache reuse,
and root `FROM_CACHE` restoration after deletion. Independent required/strict/preferred/rejected GMM version
constraints are rejected so metadata cannot override normal selection. The repository's `:klum-ast:test`, `:klum-ast:groovy4Tests`, and `:klum-ast:groovy5Tests`
passed their full 1,165-test suites, followed by `:klum-ast:verifyTestLaneIsolation`. Root `check` was not run for
this plugin-only slice. `git diff --check` and ADR/plan local link checks passed. Native IntelliJ was not run.

Local two-axis review: Standards reported no actionable violations or material smells. Spec found that a GMM variant
with the right capability but zero files could silently succeed. The consumer now requires exactly one archive per
explicit selected origin in both modes; focused zero/two-payload controls pass, and the independent offline reproduction
now fails before materialization. The follow-up Spec review reported no remaining findings. Commit-history review keeps
the complete vertical implementation and its engineering evidence as separate reasoning steps before first publication.

PR #820 identity-hardening follow-up (2026-10-01): the new equal-basename fixture reproduced rejection in both
GMM and classifier modes and origin overwrite/mismatched-manifest failure in mixed mode before the change.
The paired nested inputs pass all three modes, including configuration-cache reuse and relocation build-cache
restoration. `:klum-ast-gradle-plugin:check` passed with 132 tests, license checks, `validatePlugins`, and the real
Groovy-3/4/5 binary-contract fixtures. Reviewed GDSL-2 commits remain intact; this is an additive binary-identity
follow-up with no GDSL-3 scope or tracker/release-state change.
Independent Standards and Spec reviews of the identity follow-up reported no findings. The full core
Groovy-3/4/5 suites were rerun successfully (1,165 tests per lane), followed by `verifyTestLaneIsolation`.
Git push and GitHub CLI repository-mutation channels were independently verified authorized for the existing PR.


## GDSL-3 engineering contract and evidence

Opted-in source Schemas now register their local `klumGdslJar` provider with the existing root materializer, including
Schema-only authoring without Maven Publish. Models use the same explicit metadata scope as binary consumers:

(See: `KlumGdslProjectWideValidationTest#'source authoring and two explicit project consumers share one root and one archive with #order evaluation'`.)

```groovy
klumModel {
    schemas { schema project(':schema') }
    gdsl { enabled = true }
}
dependencies { klumGdsl project(':schema') }
```

The Model selects the public metadata capability; it never reaches into the producer's task or outgoing configuration.
Module-to-project substitutions follow the selected normal component too. The normal component identifier supplies
origin equality, and its selected module version supplies the manifest GAV; neither archive basename nor a second
user-maintained version identifies a source Schema. Origin data crosses the deferred artifact provider as scalar maps.
Selected archive collections use `ArrayList` because Gradle 8.14.4 cannot reliably restore deferred immutable lists of
records through its configuration-cache codec. Explicit producer/variant artifact dependencies preserve task ordering
when those providers are converted to nested immutable `KlumGdslArtifactInput` values.

The sole root sync validates all canonical managed envelopes, then the whole managed catalog union. Repeated selections
of the same File/GAV are idempotent. Distinct files registering one GAV fail for duplicate
payload identity or different payloads, even if they could have produced the same destination. Different active versions
of one Schema fail against normal selections from every participating Schema/Model, including Models with GDSL disabled
and local Schemas with metadata disabled. Case-sensitive suffix overlap fails regardless of targets, with both GAVs,
mapping IDs, suffixes, archive locators, and a witness filename. Output order is deterministic and no priority applies.
All validation precedes sync; a failed refresh labels previously successful output stale and retains those bytes.

Participants contribute normal selected component coordinates for cross-project Schema-version checks, including
nonconsuming Models. They do not contribute normal source/resource files or classpath artifacts for migration or
conflict inspection. Refresh does not compile/package source Schemas or run normal resource generators. Ordinary
build/publication paths do not run the root materializer, and normal SourceSets, classpaths, and archives remain unchanged.

### Root Base lifecycle ownership

A build containing the KlumAST Model plugin ensures that its root project has Gradle's standard Base lifecycle through
`project.getRootProject().getPluginManager().apply(BasePlugin.class)`. This intentionally adds the normal root lifecycle
and cleanup surface (`assemble`, `check`, `build`, and `clean`), even for a plain root whose Model subproject has never
enabled GDSL. It does not apply the Java plugin, add SourceSets or Java compilation tasks, add compile/runtime
dependencies, or otherwise turn the root into a Java or Model project. Ordinary Model build, classpath, and publication
behavior remains unchanged.

Base lifecycle ownership does not itself enable GDSL consumption, create metadata selections, introduce a metadata
resolver or `materializeKlumDslGdsl` task, resolve metadata, or run materialization. The disabled Model's declarable
`klumGdsl` dependency scope remains inert, including when the build author explicitly declares a metadata dependency.

The shared generated IDE state is deliberately root-owned at `<root>/build/generated/klum-dsl-ide/gdsl/` and therefore
needs a stable root lifecycle owner. In one invocation an enabled Model generates editor metadata; in a later invocation
the final Model opts out, so the GDSL materializer/resolvers no longer participate while previous output still exists.
Root `clean` must still remove that stale metadata. Model plugin application establishes this ownership deterministically,
independently of stale filesystem state; individual Model subprojects do not own cleanup of the shared root directory.

The never-enabled compatibility control is
`KlumGdslProjectWideValidationTest#'a never-enabled Model beneath a plain root adds only the root Base lifecycle'`.
It checks root lifecycle availability without a Java model, absence of materializer/resolvers and metadata resolution,
and ordinary Model build/classpaths/publication with an intentionally unavailable metadata dependency. The separate
`#'binary-only Model subproject retains root cleanup after its final opt-out'` control retains the two-invocation cleanup
proof after metadata was generated.

### Managed validation boundary and coverage

KlumAST's project-wide validation covers only mappings delivered through the portable managed GDSL metadata
mechanism. Arbitrary `.gdsl` resources in source/resource roots or normal dependency artifacts remain user-owned
IntelliJ configuration and are not parsed, transported, rejected, or conflict-checked by KlumAST. Byte-identical copied
generated contributors outside the managed transport have the same external boundary. Only canonical archives selected
through the dedicated transport are admitted to the catalog union. Existing framework contributors still materialize
from KlumAST's packaged framework namespace; that independent transport does not inspect external contributors for
migration or conflicts.

The never-released #809 recipe establishes no compatibility obligation. Development-snapshot users who tried it
should remove copied contributors when adopting the portable mechanism; no migration scanner, parser, or normal-artifact
republication rule implements that history note.

Executable coverage is in `KlumGdslProjectWideValidationTest` and `KlumGdslProjectCatalogTest`, each marked `@Issue('805')`.
TestKit proves source-only authoring, two source Schemas/two
Models, source plus binary consumption, equal payload basenames, shared source archives, application/evaluation order,
module-to-project substitution, disabled-Model version conflict, atomic overlap failure, edit/rename/remove/empty/opt-out
cleanup, root clean, ordinary JAR/source-JAR/IDE model laziness, external source/resource/JAR isolation, and preservation
of custom resources in normal Model artifacts. External source edits do not invalidate managed refresh or its configuration
cache, and normal resource-generation tasks do not participate. Root refresh reuses the configuration
cache and restores `FROM_CACHE` after deletion and relocation, retaining framework GDSL once.

GDSL-4 native IntelliJ discovery/activation/PSI and GDSL-5 end-user migration/release documentation are explicitly outside
this slice. No generated/public/runtime contract or initial normal-GAV publication policy changes. Tracker impact is
`Related: #805`; the issue and native release gate remain open. No curation or release-placement mutation is required.


### GDSL-3 local review

Standards: the initial two-axis review reported no documented violations or material smells. New tests have #805
traceability, `Test` names, and the engineering documentary link required for this explicitly unreleased slice.

Spec: the initial review reproduced a binary-only Model subproject's loss of root cleanup after final GDSL opt-out.
The Model plugin retains the root Base lifecycle after disablement, with executable opt-out and never-enabled controls.
The evaluation-order fixture uses `evaluationDependsOn` to reverse Schema/Model configuration rather than relying on
settings include order. The earlier review also covered migration-source provider dependencies; that entire subsystem
and its controls were subsequently removed by the maintainer's 2026-10-02 managed-transport-only scope correction.

Commit-history review retains the vertical implementation, engineering contract, root lifecycle review fixes, and final
evidence as separate green reasoning steps. No unrelated work, public/generated/runtime contract change, new normal-GAV
policy, tracker mutation, native IntelliJ claim, or broad release/user documentation is included.

Both follow-up review axes reported no remaining findings on `0c0350b2`; no additional builds were run by reviewers.


### GDSL-3 validation and delivery boundary

Local validation on 2026-10-01: `:klum-ast-gradle-plugin:check` passed with 161 tests, license checks, `validatePlugins`,
and the existing independent real Groovy-3/4/5 binary-contract fixtures. Focused source/provider/cleanup controls passed.
The core `ConvenienceFactories*` and `AsBuilderSpec` selections passed 40 tests per `test`, `groovy4Tests`, and
`groovy5Tests`, followed by `verifyTestLaneIsolation`; independent Script/migration controls also passed in all three
lanes. Root `check` and full core suites were not repeated for this plugin-only slice. `git diff --check` and local ADR/
plan links passed. The stable-worktree rerun supersedes a fixture-publication mismatch caused by changing repository
clean/dirty version identity during an earlier verification run; no production failure was reproduced by that run.

Git push and GitHub CLI repository-mutation channels were independently verified authorized for draft delivery.
The assigned implementation is complete; draft publication remains related to #805 and does not deliver its native
release gate. The Hive owns subsequent merge/archive reconciliation. This worker retains the worktree and never
self-archives an open pull request.

PR #822 root-lifecycle contract hardening on 2026-10-02 preserves the existing deterministic Base plugin application
and both final-opt-out cleanup controls. The new plain-root/never-enabled control passed alongside those cleanup
controls. `:klum-ast-gradle-plugin:check` passed with 162 tests, license checks, `validatePlugins`, and the independent
real Groovy-3/4/5 binary-contract fixtures. ADR link/anchor and test references plus `git diff --check` passed. Core
compiler/runtime suites and root `check` were not repeated for this documentation, comment, and Gradle contract-test
follow-up; production behavior remains unchanged.

### GDSL-3 managed-transport scope correction

The maintainer's explicitly authorized 2026-10-02 correction removes the unreleased #809/canonical-copy migration
subsystem: recognizer/parser, migration diagnostics, normal source/resource/JAR scanning, task inputs, generated-resource
provider plumbing, and migration-only tests/fixtures. Managed envelope/catalog validation, source/binary contribution,
normal-coordinate version checks, deterministic sync, root Base ownership, cleanup, and isolation remain intact. The
binary classpath view remains necessary for the existing framework GDSL transport and excludes project artifacts so
refresh does not compile/package source Schemas; it no longer feeds any migration or conflict scanner.

The external-resource boundary control proves copied generated and opaque external contributors remain untouched while
the managed source/binary union materializes. Normal resource generators do not run, and external source edits preserve
configuration-cache reuse and an up-to-date managed refresh. Focused GDSL-3/materialization validation passed 22 tests;
`:klum-ast-gradle-plugin:check` passed 151 tests with no failures/skips, license checks, `validatePlugins`, and independent
real Groovy-3/4/5 binary-contract fixtures. Local ADR/plan links and anchors plus `git diff --check` passed. Core suites and
root `check` were not repeated for this Gradle-plugin-only scope correction. Earlier test counts above describe historical
revisions, including the subsequently removed migration tests.

## GDSL-4 qualification complete on supported topologies (2026-10-02)

[Native source/binary observations](evidence/issue-805-portable-gdsl-intellij.md) and a
[retained real Schema/Model fixture](fixtures/portable-gdsl-ide/README.md) now record Gradle-import discovery, public
Builder navigation, wrong arguments/receivers, target guards, rename/empty/opt-out retirement, and explicit classifier
fallback. Real binary runtime coverage includes root File and owned-child recipe paths on Groovy 3/4/5. No production
registration change was needed. The maintainer subsequently confirmed clean-profile source/binary import and
restart, source rename and binary opt-out retirement across restart, the independent non-DSL guard, and native
managed overlap/normal-version rejection with stale-output warnings. No descriptor activation was necessary.
All accepted GDSL-4 criteria have evidence for the supported source/binary topologies represented by the fixture.
The adjacent nested-Model discovery is transferred to #825: the existing mirror task excludes nested namespace names,
and normal Schema publication fails in class-stub projection of the outer class despite observed runtime/generated
binary behavior. The reproduction remains in the evidence and optional fixture. Nested Models are not an established
compatibility guarantee; this is outside #805 and its portable-GDSL release gate, not a failed GDSL-4 criterion.
No nested mirror/source-projection fix or support commitment is included in PR #824.
GDSL-5 user documentation, migration, and release integration is the remaining #805 slice; complete feature delivery
still requires that slice.
