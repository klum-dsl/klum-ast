# Issue #805 — GDSL-4 native IntelliJ evidence

Date: 2026-10-02. Outcome: actual source and binary Gradle imports resolve the mapped public Builder, with negative and
retirement controls. **Qualification remains partial**: the complete clean-profile/restart/conflict matrix below has
not passed. No editor-registration defect was found and no production code changed. GDSL-5 user documentation and
release qualification remain separate.

Authority: [ADR 0025](../../adr/0025-portable-schema-gdsl-metadata.md),
[implementation plan](../adr-0025-portable-schema-gdsl-metadata.md),
[repeatable fixture/procedure](../fixtures/portable-gdsl-ide/README.md).
Related: [#805](https://github.com/klum-dsl/klum-ast/issues/805); this slice does not complete that issue.

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
  is limited to the existing profile; cache/first-use activation and full restart are still unqualified.

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

## Remaining native gate and delivery boundary

The current run proves actual discovery in both topologies on the pinned IDEA build, but leaves these exact conditions
unfilled:

1. Repeat in a dedicated clean trusted profile without AI/custom plugins and record first-use descriptor activation.
2. Full IDEA restart in that profile for both positive and retired states; no whole-product restart was performed here.
3. Native imported project-wide managed conflict/version-failure run with stale output explicitly rejected as current.
4. Native supported nested Model-name lookup (if supported by the generated contract), and an independent non-DSL guard
   control with unrelated mirrors available. No pinned native PSI automation harness was added.

These are evidence gaps, not a reproduced production defect or a new design decision. The fixture makes the missing
checks reviewable and repeatable. Keep GDSL-4 and the feature release gate pending; do not widen claims to other IDEA
builds, Eclipse, VS Code, Quick Documentation, or runtime filename dispatch. The dedicated branch stops at the local
`ready:PR` boundary for Hive reconciliation; no draft PR is opened as if the requested native gate were complete.
