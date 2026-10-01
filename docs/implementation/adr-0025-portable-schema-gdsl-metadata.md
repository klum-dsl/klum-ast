# ADR 0025 implementation — portable Schema GDSL

Date: 2026-10-01. Status: planning only; no API or executable fixture introduced by this document.

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

**New names below are concrete proposals, not confirmed public spellings.** No new plugin ID/module is proposed.
Confirm the bounded mapping format and this API together at GDSL-0; do not advertise them as available now.

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
                builderType = 'example.Environment_DSL.Builder'
            }
        }
    }
}
```

```groovy
// Separate Model build: ordinary Schema binary and metadata are explicit.
plugins { id 'com.blackbuild.klum-ast-model' version '<klum-version>' }
klumModel {
    schemas { schema 'org.example:environment-schema:1.2.0' }
    gdsl { enabled = true }
}
dependencies {
    klumGdsl('org.example:environment-schema:1.2.0') {
        capabilities { requireCapability('org.example:environment-schema-gdsl') }
    }
}
```

Repository configuration is the consumer's normal responsibility. In one build replace both dependencies with
`project(':schema')`, retaining the required metadata capability; do not name another project's outgoing configuration
or reach into its task. For a POM-only/lossy repository, use **instead of** the capability declaration:

```groovy
dependencies {
    klumGdsl 'org.example:environment-schema:1.2.0:gdsl@jar'
}
```

Proposed managed extension types in `com.blackbuild.klum.ast.gradle`: `KlumSchemaGdslExtension` (`publish`, default
false; named `mappings`), `KlumModelGdslExtension` (`enabled`, default false), and `KlumGdslMapping` (`fileNameSuffix`,
`builderType`). Expose nested `gdsl(Action)` on the existing extensions; avoid an unrelated Groovy-version API change.
Empty enabled producer metadata is intentional and retires mappings. Opted-in producers also contribute their own
archive to local root refresh for source authoring, even without a Model module or Maven Publish. Model opt-in adds
only explicit dependencies. Repeated local/project resolution of the same producer is deduplicated by origin/hash.

The proposed deterministic contributor delegates only after resolving the real public contract:

```groovy
contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('.environment.groovy')) {
        def builder = findClass('example.Environment_DSL.Builder')
        if (builder != null) delegatesTo(builder)
    }
}
```

Keep `scriptScope()` and the final-name suffix test: `extension: 'environment'` describes a different filename family.
This generates editor metadata only; it adds no method catalog, Script class, or runtime dispatch rule.

## Proposed publication and payload contract

| Surface | Proposed exact contract |
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
| Envelope | `META-INF/klum-ide/gdsl/v1/mappings.json` plus `<mapping-id>.gdsl` in that directory. Sorted catalog entries contain ID, suffix, Builder type, payload SHA-256. |
| Root destination | `schema-owned/<UTF-8-hex-group>/<UTF-8-hex-artifact>/<mapping-id>.gdsl`; version absent so refresh replaces rather than accumulates. |

Canonical catalog serialization, generator/template compatibility, and portable ID rules must be frozen at GDSL-0.
Recommend ASCII `[a-z][a-z0-9-]*` IDs and NFC-normalized case-sensitive suffixes; forbid separators/control characters
and require a nonempty suffix ending in `.groovy`. Preserve Builder qualified names as data and escape generated
strings. Consumer verifies envelope, identity, catalog/hash/entry correspondence, and that payload bytes match the v1
generator template. No unchecked sidecar assertions beside arbitrary executable GDSL. Unsupported formats fail with
origin and upgrade guidance. An empty catalog is valid; extra executable entries, duplicate ZIP entries, absolute/
traversal paths, malformed/missing fields, and unsupported predicates fail before sync.

Attach using `AdhocComponentWithVariants.addVariantsFromConfiguration`, not merely `publication.artifact(...)`.
If Maven mapping is required, use optional/runtime mapping; empty dependencies mean the POM graph stays unchanged.
`.module` gains only the opted-in metadata variant/capability/artifact. Proposed initial publication policy rejects
`mavenJava` GAV overrides inconsistent with project identity before publication, rather than emitting misleading
capabilities/manifests; supporting customized artifact IDs requires a separately verified identity provider.
Test both Maven-Publish/plugin application orders. No external publication is needed for implementation tests.

## Alignment, conflicts, lifecycle, and migration

1. Resolve normal Schema identities lazily for validation, without compiling them. The isolated GMM resolver may use
   `shouldResolveConsistentlyWith(compileClasspath)`; this is a **mechanism to prove**, not an accepted guarantee.
   Verify custom-capability selection, application BOM upgrades, locks, exclusions, project substitution, and selected
   origin equality. Missing normal Schema identity or unequal selected versions fails. Do not import `api`/BOM
   dependencies into metadata or silently select another version. Classifier mode requires an exact GAV matching the
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
   inputs and selected normal Schema/Model archives for known legacy copies (matching payload or documented contributor
   structure/target/suffix); reject duplicates and report both locators. Arbitrary code cannot be semantically compared:
   unclassifiable custom GDSL affecting this family requires explicit removal and migration, not a false validation pass.
   Freeze the precise detection boundary at GDSL-0 and document user responsibility for unrelated external contributors.
   Do not silently exclude/delete user files or extract mappings from normal library JARs as a fallback.

## Thin slices and reasoned commit boundaries

All slices implement #805; no new issue or GitHub mutation is part of this plan. Each slice has a green local commit
with meaningful coverage, then a focused evidence/docs commit if necessary. No intermediate producer-only release.

### GDSL-0 — Confirm the enforceable mapping boundary

Dependency: none. Maintainer review of proposed bounded suffix catalog, generation rather than raw-file transport,
exact API/payload spellings, and legacy detection boundary. This is the remaining decision; portable-first, binary
first-delivery, isolation, opt-in, and conflict/overlap rejection are already confirmed. Commit the confirmed refinement
to this ADR/plan; do not create pending tests or product API before it is settled. If arbitrary predicates must be
supported, return with an enforceable alternative; do not weaken the accepted gate to author responsibility alone.

### GDSL-1 — One Schema mapping to one selectable metadata artifact

Depends on GDSL-0. One reasoned implementation commit adds managed declarations, catalog/generator validation,
reproducible archive, custom outgoing variant and publication integration, with producer TestKit coverage. Tests prove
off-by-default, empty opt-in, deterministic bytes/relocation/cache, safe provider wiring, local suffix overlap rejection,
no code execution, exact GAV/capability/attributes, and enabled/disabled `.module`/POM/archives. Attribute-poor ordinary
default-capability consumers must never select metadata; sources/Javadocs retain their own variants. This slice is a
producer boundary only and cannot qualify delivery.

### GDSL-2 — Binary Schema metadata to a Model-only editor root

Depends on GDSL-1. One vertical commit adds explicit Model opt-in, both isolated resolvers, alignment/origin validation,
root validated sync, IDEA registration, and an isolated Maven-repository producer-to-Model TestKit fixture. No Schema
plugin, producer project, mirror, attached contributor source, or compilation fallback in that consumer. Verify GMM
capability consumption, explicit classifier against POM-only metadata sources, missing variant/classifier errors,
wrong origin/version rejection, normal graph isolation, and configuration-cache reuse of resolution/materialization.
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
| Isolation | Run clean compile/test/JAR/source/Javadoc/publication and inspect inputs, task graph, all resolvable/output configurations. | No new metadata resolution/materialization on ordinary paths; no envelope/contributor/root/mirror in normal artifacts/classpaths/module path or exported Model deps. Enabled Schema publication builds metadata archive only as its intentional addition. |
| Integrity/conflicts | Inject duplicate entries, path traversal, wrong manifest/hash/template, conflicting versions, matching suffixes, legacy resource copies. | Error identifies origins before sync; previous output is not reported current; no silent EXCLUDE or fallback. Normal non-opted-in Model version conflict is included. |
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
| Explicit declarations, metadata capability/classifier fallback | Contract confirmed; exact new names proposed | GDSL-0/1/2; selection and missing-artifact controls |
| Normal artifact/classpath isolation | Confirmed | All slices; isolation/downstream gates |
| Schema-versioned project-wide mappings | Confirmed; consistent-resolution mechanics require proof | GDSL-2/3; BOM/lock/version/nonconsumer conflict matrix |
| Overlap/duplicate rejection | Confirmed; bounded suffix catalog/generation needs maintainer confirmation | GDSL-0/1/3; witness, payload/template and union controls |
| Raw #809/custom predicates cannot be proved disjoint | Remaining maintainer refinement: accept bounded initial format and explicit migration/detection limits | GDSL-0/3/5; exact legacy recipe failure/migration control |
| Publication customization and capability identity | Proposed initial reject-on-mismatch policy; broader customization not claimed | GDSL-0/1; publication identity test |
| Native importer ignores generated root under build/ | Technical release risk, not permission to pollute resources | GDSL-4; actual Gradle import, stop for supported registration fix |
| Included builds / Isolated Projects | Not claimed; future acceptance if required | GDSL-3 topology boundary |
| Runtime suffix does not constrain receiving factory | Existing runtime boundary; #269 remains distinct | GDSL-4 runtime wrong-receiver control |
| Eclipse/VS Code | #14/#808 distinct, no parity promise | GDSL-5 truthful user guidance |

Further maintainer decisions remain on the enforceable predicate/API refinement, not on portable versus local delivery.
Technical unknowns (alignment, import discovery, activation/cache behavior) must be resolved by the specified tests before
publication. This plan neither asks the reader to trust unexecuted examples nor presents proposed mechanisms as results.

## Planning validation and handoff boundary

This change adds only this plan and its ADR. Planning validation checks whitespace, relative links/heading anchors,
the requirement-to-slice matrix, scope/history, and current remote base. Groovy lanes, TestKit and native IntelliJ were
not run: no executable/build input changes are made. The user-doc renderer does not render these engineering-only pages;
use bounded Markdown checks here and run user-site generation when GDSL-5 changes user pages.

Public primary sources re-read for planning: [Gradle 8.14.4 custom publication](https://docs.gradle.org/8.14.4/userguide/publishing_customization.html),
[consistent resolution](https://docs.gradle.org/8.14.4/userguide/dependency_resolution_consistency.html), and
[pinned IntelliJ GDSL index](https://github.com/JetBrains/intellij-community/blob/4aee219fbbbc89d8497ebb02a7db8d61db3d99f6/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/GroovyDslFileIndex.java).
They support the architectural direction, not a passed custom-variant or native-import experiment.

Delivery is a reviewed local commit in an isolated worktree. No implementation, issue mutation, push, PR, release,
publication, child task, or delegated review belongs to this assignment. The Hive receives a commit-addressable handoff
and performs reconciliation; the worker retains `(done)` and does not self-archive.
