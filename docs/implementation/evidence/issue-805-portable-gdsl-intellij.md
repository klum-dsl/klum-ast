# Issue #805 — GDSL-4 native IntelliJ evidence

Date: 2026-10-02. Outcome: actual source and binary Gradle imports resolve the mapped public Builder. The guided clean-profile
completion, navigation, restart, retirement, non-DSL guard, and managed-conflict/version controls passed.
**GDSL-4 is qualified on the supported source/binary Model topologies represented by the acceptance fixture.**
No accepted GDSL-4 criterion remains outstanding. The adjacent nested-Model discovery is tracked canonically in
[#826](https://github.com/klum-dsl/klum-ast/issues/826), outside #805 and the portable-GDSL release gate.
No editor-registration defect was found and no production code changed. GDSL-5 user documentation, migration, and
release integration remains the next #805 slice.

Authority: [ADR 0025](../../adr/0025-portable-schema-gdsl-metadata.md),
[implementation plan](../adr-0025-portable-schema-gdsl-metadata.md),
[repeatable fixture/procedure](../fixtures/portable-gdsl-ide/README.md).
Related: [#805](https://github.com/klum-dsl/klum-ast/issues/805); this slice does not complete that issue.

## Tracker reconciliation (2026-10-02)

The original [PR #824 handoff](https://github.com/klum-dsl/klum-ast/blob/0cf650184461b1a3bde92824a2dc16c536c38c7c/docs/implementation/evidence/issue-805-portable-gdsl-intellij.md)
transferred the nested discovery to [#825](https://github.com/klum-dsl/klum-ast/issues/825). That locator is historical:
#825 is superseded by the single canonical [#826](https://github.com/klum-dsl/klum-ast/issues/826), which preserves its
investigation scope and exact retained reproduction. This annotation and the current tracker pointers change no run
identity, native observation, runtime result, or support decision. No additional native run was performed.

## Reproduction identity

- Production checkout: `433adfd8` (remote `master`, merged GDSL-3 PR #822). The dedicated branch is
  `codex/805-gdsl-native-gate`; the commits introducing this report and fixture identify the retained reproduction.
- Native product: IntelliJ IDEA Ultimate 2026.2.3, build `262.10968.63`; bundled Groovy plugin build `262.10968.63`.
  Version/build were read from the installed product metadata and native startup log.
- Gradle wrapper 8.14.4; real Schema/runtime compilation Java 17; native Groovy lane 3.0.25.
  Source import selected the IDE's Temurin 21 Gradle JVM after rejecting host-default Java 25; its compile toolchain
  remained Temurin 17. Binary import used the fixture's configured Temurin 17 Gradle JVM.
- Locally staged Klum plugin markers, implementation, BOM and libraries: `4.1.0-gdsl4.805`, built from `433adfd8`.
  Schema normal/GDSL publication: `org.example.gdsl4:schema:805.1`, then an empty `805.2` upgrade.
  Isolated local Maven repository `/private/tmp/klum-gdsl4-repository`; no Maven Local or external publication.
- Separate scratch projects `/private/tmp/gdsl4-source-authoring` and `/private/tmp/gdsl4-binary-model`.
  Each was freshly opened/trusted through native File → Open/Gradle import. No root was hand-marked, no `.iml` edited,
  and no SourceSet registration workaround applied. Binary had no producer project, source attachment, or mirrors.
- Existing user IDEA profile, with unrelated plugins including AI/Copilot. Native Basic Completion was invoked
  explicitly; Enter accepted its proposal, declaration navigation and Parameter Info checked the actual Builder.
  Inline AI ghost text was not counted as a native proposal. Fresh projects do **not** establish a clean-profile run.
- The materialized descriptors appeared in the imported root, with no activation banner observed. This observation
  is limited to the initial existing-profile run; the later guided clean-profile/restart evidence appears below.

## Actual native observations

| Native state / control | Observed result |
| --- | --- |
| Source Schema plus in-build Model, project-capability metadata request | Basic at `reg` inserted `region()`; declaration navigation reached refreshed `Environment_DSL.java`, public `Builder<SELF extends Environment>` with `String region(String value)` |
| Schema-only source authoring | After mirror refresh, Gradle Sync **and disk reload**, `.renamed.groovy` probe inserted `region()` from the source Builder |
| Binary Model, normal GMM capability request | Basic at `reg` inserted `region()`; declaration navigation reached decompiled `Environment_DSL.class`, public Builder; Parameter Info displayed `String value` |
| Binary Model, explicit POM-only classifier request | Separate POM/artifact repository mode materialized the descriptor; Basic inserted `region()` and navigation reached compiled `Environment_DSL.Builder.region(String)` |
| Imported roots under normally excluded `build/` | Native project tree identified GDSL as a resource root and mirrors as a generated sources root. Opening the descriptor showed the current suffix payload |
| Nonmatching filename | `catalog.other.groovy` did not complete the Builder `region`; accepting the native default chose an unrelated fuzzy class-name result |
| Same-suffix ordinary class | `reg` inside a method in `ordinary.renamed.groovy` selected an unrelated class, not the Builder operation; `scriptScope()` remained effective |
| Missing Model | Source-only `gdslacceptance.Absent` mapping produced no mapped `region` proposal |
| Missing Builder | With the real annotated Model present and only its mirror withheld, no mapped `region` proposal appeared; aggregate refresh restored the mirror |
| Non-DSL Model | `gdslacceptance.PlainModel` mapping produced no mapped `region` proposal. The Environment mirror was also withheld during this probe; this is a guard observation, not an independent available-Builder isolation test |
| Wrong argument | Current-file Problems reported `'region' cannot be applied to '(java.lang.Integer)'` for `region 42` |
| Unknown method | Navigation at `definitelyMissing` reported “Cannot find declaration to go to”. No unknown-method inspection was observed; do not claim inspection parity |
| Rename | New `.renamed.groovy` completed `region`; previous `.environment.groovy` no longer supplied it after refresh/import/reload |
| Empty metadata upgrade | Binary selected Schema `805.2`; root retained only framework GDSL, no `environment.gdsl`; old filename no longer supplied `region` |
| Last binary owner disabled and request removed | `disableGdsl=true`, root `clean`, native Sync and disk reload removed the mapping; native Basic chose an unrelated class instead of `region` |

The automated UI surface did not reliably expose the lookup popup itself. The recorded positives are accepted native
completion text plus actual declaration/signature navigation; negatives compare the same invocation and do not assert
that all general completion suggestions vanish. Source/binary positive recipes were restored after probes.

A lifecycle detail matters: recreating `Environment_DSL.java` after the missing-Builder test was not enough for Gradle
Sync alone to restore source completion in this run. File → Reload All from Disk indexed the recreated mirror; native
completion then passed. The procedure includes both actions rather than interpreting pre-reload absence as a plugin bug.

## Durable automated validation

`KlumModelGdslBinaryContractTest` already uses actual transformed and published Schema bytes in separate Model builds.
This slice adds a real Deployment child contract and verifies root File recipes, owned-child `AsBuilder().From(Class)`,
and exact wrong-receiver `MissingMethodException.method == 'region'` across independent Groovy 3/4/5 fixtures. Its
GroovyShell contributor adapter verifies dispatch/real contract shape; **it is not native PSI proof**.

- `:klum-ast-gradle-plugin:check`: 151 tests, zero failures/errors/skips; license and Gradle plugin validation passed.
- Final focused `KlumModelGdslBinaryContractTest`: all three Groovy generations passed after narrowing the wrong-receiver
  exception assertion. No plugin `groovy4Tests`/`groovy5Tests` task exists; these TestKit lanes compile each real fixture.
- Retained source-authoring and binary Model `RecipeRuntimeTest`: two tests each passed on Groovy 3, using the
  documented local staging and default repository paths. The scratch classifier-mode binary copy also passed both tests.
- Original native scratch runtime controls passed root `region=eu`, child `region=eu`, wrong receiver rejected on
  Groovy 3, independently in source and binary topology.

No production compiler/runtime/generated API changed. Normal-artifact, metadata graph, root union, and conflict
coverage remains in the merged GDSL-1/2/3 suites; successful transport tests are not substituted for missing native runs.

## Guided clean-profile run (maintainer-reported, 2026-10-02)

Fresh copies of the retained fixture at `b1402f62` were prepared under
`/private/tmp/gdsl4-manual-32w3xfh6`, with separate IDEA configuration, system, plugins, and log directories.
The source and binary runtime tests passed before native import. The launcher points to the same pinned IDEA installation;
Gradle uses the configured Temurin 17 JVM and staged local candidate repository.

The maintainer reports the binary GMM first-import checks all passed: native Basic Completion supplied the mapped
`region` operation, and Command-B opened the compiled DSL class in the decompiler. No descriptor activation was necessary.
Source artifacts were not attached, so decompiler navigation is the expected positive result for this binary topology;
it does not establish source-mirror navigation. This is human-observed native evidence, separate from the earlier
computer-use observations. The maintainer subsequently confirmed native binary completion and compiled Builder
navigation still worked after fully quitting and relaunching the isolated IDEA instance; no reactivation was needed.
The maintainer also confirmed retirement in the binary topology: after `disableGdsl=true`, root `clean`, Gradle Sync,
and disk reload, the mapped operation disappeared from native completion and declaration navigation. It remained absent
after fully quitting and relaunching the isolated IDEA instance.

For source authoring, a separate fresh `source-profile` was prepared through `idea-source.properties`
under the same manual fixture directory. The maintainer confirmed the source-plus-in-build-Model checks passed: native completion,
Command-B navigation to the generated `Environment_DSL.java` mirror, imported generated-source/resource roots, and
completion/navigation after fully quitting and relaunching. No descriptor activation or reactivation was necessary.
The maintainer then confirmed Schema-only authoring and rename retirement: after excluding the Model module and
refreshing the `.renamed.groovy` mapping, the new filename completed/navigated to the generated mirror, while the old
`.environment.groovy` filename no longer supplied the mapped operation. Both outcomes persisted across a full restart.
No activation was reported for the guided source run.

The first non-DSL guard attempt is not counted as a failure: the maintainer reported that
`mappingModel` was absent and the effective target remained Environment. The prepared manual copy's properties and
materialized descriptor were checked on disk and both target PlainModel; UI inspection exposed the older
`/private/tmp/gdsl4-source-authoring` copy, whose properties have no override. Confirming the active project path is a
prerequisite to repeating this guard control. After selecting the prepared manual project and confirming its PlainModel
override/descriptor, the maintainer confirmed that the mapped operation disappeared while the real Environment mirror
remained available. The independent non-DSL guard control therefore passed.

The maintainer confirmed the imported managed-overlap scenario: the valid `.renamed.groovy` Environment mapping was
restored, then `managedConflict=true` included a second Schema (`org.example.gdsl4:clash-schema:805.1`) declaring
`.groovy`. Running root `materializeKlumDslGdsl` from IDEA's Gradle tool window failed with both Schema origins/mapping
IDs/suffixes, witness filename `recipe.renamed.groovy`, and `previous IDE output is stale`. The failed refresh was not
counted as accepted current metadata. The maintainer also confirmed the normal-version control after replacing the
clash Schema with `version-model`: the local Schema stayed at `805.1`, while the new Model normally selected published
Schema `805.2` without GDSL opt-in. Native Gradle execution rejected `[org.example.gdsl4:schema:805.1,
org.example.gdsl4:schema:805.2]` and labelled previous output stale. This verifies the root union includes the normal
Schema selection of a non-opted-in Model. The positive fixture properties/recipes were subsequently restored and
command-line refresh/runtime tests were rerun; a final native resync is needed to show the restored baseline in IDEA.

The retained fixture now includes optional `clash-schema`, `version-model`, and nested source controls. Final local
verification passed normal source mirror refresh/publication and both Model runtime fixtures (two tests each, Groovy 3),
reproduced both intended managed-conflict rejections with stale-output warnings, and reproduced the exact nested
class-stub failure. The ordinary source fixture was restored after that negative probe and again passed publication
and runtime tests. Relative links and `git diff --check` passed. No additional plugin/core production code changed;
the earlier plugin check and real Groovy 3/4/5 contract results remain the module validation for this branch.

## Adjacent nested-Model discovery — historical transfer to #825

Nested Models are not an established KlumAST compatibility guarantee. This discovery is independent of #805 and
outside the portable-GDSL release gate; it is not evidence of a failed GDSL-4 acceptance criterion. Issue
[#826](https://github.com/klum-dsl/klum-ast/issues/826) now owns investigation and definition of the supported boundary
before any implementation decision. No commitment to nested-Model support or required 4.1 work is made.

A separate scratch Schema at `/private/tmp/gdsl4-nested-gmu6iq8e` compiled a real
`gdslacceptance.Outer.Inner` Model (`@DSL static class Inner { String region }`) and produced the public
`Outer$Inner_DSL$Builder` class. A runtime probe returned `nested-runtime=eu` through `Outer.Inner.Create.With`.
The existing mirror task explicitly excludes `**/*$*`, so it emitted no nested namespace source mirror.
Normal Schema publication for the unique candidate `805.3` failed before publication in `:schema:createClassStubs`:
`SourceProjectionException: Could not project selected declaration gdslacceptance.Outer`, caused by
`IllegalArgumentException: index 1 for '$I' not in range (received 0 arguments)`.

This prevents preparing the native nested lookup check through the current source-mirror or normal publication path.
No source mirror was hand-written, projection task bypassed, or public generated contract changed to manufacture a pass.
The observed working runtime/generated binary behavior and unsupported or unqualified mirror/publication behavior
are recorded separately; native nested lookup is unqualified. Reproduction uses the normal fixture plus the Outer
class, `mappingModel=gdslacceptance.Outer.Inner`,
and `schemaVersion=805.3`; run aggregate mirrors and normal `:schema:publishMavenJavaPublicationToFixtureRepository`.

## Qualified acceptance and remaining delivery boundary

Portable GDSL must work for a Model whose public Builder is available through the normal supported source-Schema or
published-binary Schema topology. GDSL does not independently guarantee Model shapes/topologies that those prerequisite
mechanisms do not support.

The recorded matrix qualifies GDSL-4 on those supported topologies: clean-profile source/binary discovery, Basic
Completion, source-mirror and compiled/decompiled Builder navigation, Parameter Info, restart persistence, source rename
and binary opt-out retirement, nonmatching filename, script-scope guard, missing Model/Builder, independent non-DSL
guard, wrong-argument diagnostics, managed suffix-overlap and normal Schema-version rejection, stale-output behavior,
explicit classifier fallback, and runtime receiver controls. No descriptor activation was necessary in the guided runs.

The optional nested reproduction remains useful evidence for canonical #826, independent of this qualification.
No pinned native PSI automation harness was added; the repeatable manual procedure is the native seam. No compiler, source-projection,
or publication fix is included. Claims remain limited to the pinned IDEA build and fixture topologies; they do not
extend to Eclipse, VS Code, Quick Documentation, unknown-method inspection parity, or runtime filename dispatch.
PR #824 retains the completed GDSL-4 qualification. #805 stays open for GDSL-5 user documentation, migration, and
release integration; that is the remaining feature delivery boundary.

The 2026-10-02 scope reconciliation changes only ADR/plan/evidence/fixture prose. Local documentation links/anchors
and `git diff --check` passed; no test, fixture code, or production behavior changed. The previously recorded plugin
check, independent Groovy 3/4/5 contract lanes, runtime fixtures, and native observations remain the validation evidence.
