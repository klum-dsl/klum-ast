# ADR 0025 implementation — portable Schema GDSL

Date: 2026-10-01. Status: GDSL-0/1 and GDSL-2 binary Model resolver/root implemented; GDSL-3+ and release acceptance pending.

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
recognized-legacy-copy detection boundary, and standard-GAV-first publication policy, with `modelType` as the public
mapping target. No product decision remains; the technical proof gates below remain unexecuted.
The consumer-UX clarification makes the normal Schema dependency authoritative for component/version selection;
GDSL-2 must prove metadata consumption for that result without a second user-maintained GMM version choice.

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
require users to spell or override the Builder name when resolution fails. Missing/non-DSL Models, missing Builders,
and nested model names where supported are technical negative/compatibility controls.

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

## Alignment, conflicts, lifecycle, and migration

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
5. Migration acceptance starts with the exact #809 `src/main/resources/environment.gdsl`. Remove the old source copy
   and rebuild a new normal Schema/Model artifact before enabling the declaration. Check participating source/resource
   inputs and selected normal Schema/Model archives for recognized legacy copies (matching generated payload or the
   documented #809 contributor structure/target/suffix); reject duplicates and report both locators. This accepted
   automatic detection boundary does not cover arbitrary executable predicates. Users must remove custom external
   contributors affecting the family; the new transport admits only generated catalog payloads. Test recognized-copy
   detection in GDSL-3 and document the boundary without implying global semantic validation of external GDSL.
   Do not silently exclude/delete user files or extract mappings from normal library JARs as a fallback.

## Thin slices and reasoned commit boundaries

All slices implement #805; no new issue or GitHub mutation is part of this plan. Each slice has a green local commit
with meaningful coverage, then a focused evidence/docs commit if necessary. No intermediate producer-only release.

### GDSL-0 — Accepted contract baseline (complete)

Dependency: none. This ADR/plan records the accepted bounded literal suffix catalog and generated contributor,
`modelType` declaration with internal real-Builder resolution, `gdsl` Gradle vocabulary, recognized-legacy-copy
migration boundary, and standard-GAV-first identity policy. Portable-first binary delivery, isolation, opt-in, and
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
root union validation across two Schemas/two Models, and lifecycle/migration controls. Acceptance includes plugin
application order, one physical root, no second sync writer, equal basenames, idempotent shared artifact requests,
distinct duplicate registrations/entries, hash/version/overlap failures before sync, BOM/lock/substitution cases,
disabled nonconsumer conflict, and edit/rename/remove/empty/last-disable cleanup. Execute migrated #809 copies as
failure controls. Framework GDSL still materializes once. Included/composite-build compatibility remains unclaimed
unless a variant-based separate-owner fixture passes; never couple the two root tasks.

### GDSL-4 — Real generated contracts and native source/binary gate

Depends on GDSL-3. Retain a minimal real Schema/runtime fixture and native contributor tests with negative controls;
then record the native Gradle-import runs below. A separate reasoned commit stores reproducible fixture instructions
and actual outcomes, including failures/limits. The native importer must be fixed through editor-only registration if
needed before acceptance. Runtime tests use existing public root and owned-child factories; no compiler/runtime change.

### GDSL-5 — Document migration and qualify the complete candidate

Depends on GDSL-4. One final commit synchronizes `docs/user/Gradle-Plugins.md`, `Gradle-Onboarding.md`,
`Convenience-Factories.md`, `FAQ.md`, Builder migration/navigation, and `CHANGES.md` with the confirmed surface,
Schema versioning, explicit fallback, project-wide effects/conflicts, refresh/activation, #809 removal, and editor limits.
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
| Integrity/conflicts | Inject duplicate entries, path traversal, wrong manifest/hash/template, conflicting versions, matching suffixes, recognized legacy resource copies. | Error identifies origins before sync; previous output is not reported current; no silent EXCLUDE or fallback. Normal non-opted-in Model version conflict is included; arbitrary external GDSL is outside automatic detection. |
| Cache/lifecycle | Repeat, edit, rename, remove, empty publication, opt-out, relocate, restore cache; configuration-cache store/reuse. | Expected SUCCESS/UP_TO_DATE/FROM_CACHE, stale paths removed, framework kept, root physical identity unique; last-owner `clean` removes leftovers. |
| Normal downstream | Publish the Model, resolve it in ordinary Java/Groovy and Model consumers. | No editor deps propagated; ordinary schemas/API unchanged; fresh later Model needs explicit opt-in. |
| Runtime truth | Real transformed Environment plus Deployment child map; root `Environment.Create.From`, owned-child `AsBuilder().From`, ordinary `Create.With` control. | Expected region and one normal materialization/session; matching filename changes no runtime dispatch. Wrong receiver control retains existing failure rather than acquiring fictional operations. Keyed class/File/key-provider and classpath-marker defaults retained. |

Focus development on `:klum-ast-gradle-plugin:test --tests <focused-class>` with Groovy 3; finalize the affected plugin
suite and external real Schema/Model scenarios selected separately for Groovy 3/4/5 (Java 17 baseline). Run the affected
repository modules' actual `test`, `groovy4Tests`, and `groovy5Tests` lanes for real transform/runtime coverage; do not
invent plugin compatibility task names or share compiled fixtures between generations. `git diff --check`, local
links/anchors, documentary links, and the applicable user-doc renderer/crawl complete documentation checks.

### Native IntelliJ: two kinds of proof, neither substituting for the other

**Automated native PSI contributor test:** use a pinned IntelliJ Platform/Groovy plugin fixture with real generated
Builder classes or refreshed mirrors, execute the actual generated GDSL, assert completion at `reg` and resolution of
`region('eu')` to `Environment_DSL.Builder.region(String)`. Nonmatching suffix, same-suffix ordinary class, missing
Builder and wrong method/argument are controls. A GroovyShell stub proves only generator dispatch logic.
Assert the catalog and Schema DSL accept `modelType = 'example.Environment'`, and that editor resolution reaches its
real generated Builder without any author-supplied Builder name. Missing/non-DSL Model and supported nested-type lookup
controls must not produce phantom methods.

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

Release is blocked if either native topology lacks discovery after the documented lifecycle. Pin the tested editor
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
| Raw #809/custom predicates cannot be proved disjoint | Accepted recognized-legacy-copy detection boundary; arbitrary external GDSL remains user-managed | GDSL-0/3/5; exact legacy recipe failure/migration control |
| Publication customization and capability identity | Accepted standard-GAV-first reject-on-mismatch policy; broader customization outside delivery | GDSL-0/1; publication identity test |
| Native importer ignores generated root under build/ | Technical release risk, not permission to pollute resources | GDSL-4; actual Gradle import, stop for supported registration fix |
| Included builds / Isolated Projects | Not claimed; future acceptance if required | GDSL-3 topology boundary |
| Runtime suffix does not constrain receiving factory | Existing runtime boundary; #269 remains distinct | GDSL-4 runtime wrong-receiver control |
| Eclipse/VS Code | #14/#808 distinct, no parity promise | GDSL-5 truthful user guidance |

No product or maintainer decision is a prerequisite to starting implementation. Technical unknowns (least repetitive
safe GMM consumer syntax, Model-to-Builder resolution, alignment, catalog/template integrity, import discovery,
activation/cache behavior, and recognized-copy detection) must be resolved by the specified tests before feature
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

No consumer plugin API, metadata resolver, Model configuration, root-union lifecycle, migration detection, user page,
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
version conflicts, and legacy-copy handling remain GDSL-3. Archive tests cover empty metadata, duplicate ZIP names, unsafe
paths, absent/extra entries, malformed JSON/fields, wrong format/origin/normal version, and hash/template tampering.

The real binary-contract fixture compiles and publishes a real `@DSL Environment` independently with Groovy
3.0.25, 4.0.32, and 5.0.6, then compiles and executes a separate Model with only the published Schema and framework
binary libraries. Each generation has its own fixture/Gradle home and compiler outputs. The materialized contributor
runs in a GroovyShell dispatch stub using reflection on the actual compiled Model/annotation/public generated Builder,
checks `Environment_DSL.Builder.region(String)`, and executes `Environment.Create.From` with a DelegatingScript recipe
plus the ordinary `Create.With` control. This proves the adapter spelling and binary contract, not native PSI or
IntelliJ Gradle import. Native discovery, activation, navigation, negative PSI controls, and owned-child runtime coverage
remain GDSL-4. Source-project union/conflicts/removal and recognized legacy-copy detection remain GDSL-3.

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
