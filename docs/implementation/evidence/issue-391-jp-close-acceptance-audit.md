# #391 JP-CLOSE acceptance audit

**Audited baseline:** `origin/master` at `56d0ed7011988f0e3a0db129a84851e8a9348051`
(merged PR [#608](https://github.com/klum-dsl/klum-ast/pull/608)), 2026-07-29.
This is a local-only closure audit. It distinguishes the completed JP-1 through
JP-ABI and JP-1b implementation evidence from the remaining JP-5
productization work; it does not reopen the proven descriptor, generated-ABI,
or named-schema fixture boundaries.

## Evidence reconciled

- Live [#391](https://github.com/klum-dsl/klum-ast/issues/391) remains open;
  its accepted ADR is [ADR 0014](../../adr/0014-groovy4-jpms-boundary.md) and
  its executable sequence is the [ADR 0014 plan](../adr-0014-groovy4-jpms-boundary.md).
- The upstream consumer-adoption gates are closed: [#459](https://github.com/klum-dsl/klum-ast/issues/459)
  (2026-07-20) and [#461](https://github.com/klum-dsl/klum-ast/issues/461)
  (2026-07-27). Their immutable RC coordinates are present in
  `settings.gradle`. Final-coordinate release promotion remains separately
  owned by [#524](https://github.com/klum-dsl/klum-ast/issues/524), not by this
  audit or a reopened #391.
- PR #608 merged `8d1b8414`, `21369682`, and `ced0c195` on 2026-07-29. Its
  build, JUnit report, SonarCloud, and SonarCloud analysis checks succeeded.
  The audit additionally reran its acceptance test locally rather than treating
  those checks as sufficient proof.
- The existing JP-2c package map, JP-3 descriptor audit, JP-ABI inventory and
  emitted-linkage evidence, and JP-1b fixture record remain the historical
  evidence chain. The current master test below is the fresh confirmation of
  that finished chain.

## Acceptance matrix

| Criterion | Current-master evidence | Status | Owner | Exact next action |
| --- | --- | --- | --- | --- |
| Accepted module/package decision and implementation sequence | ADR 0014, its plan, and `issue-391-jp2c-package-migration.md` record the five fixed artifacts, Groovy 4/5 named boundary, classpath Groovy 3 boundary, and complete old-to-new map. | met | #391 | Retain as closure evidence. |
| #459/#461 dependency inputs are synchronized without local substitutions | Closed live issues; `settings.gradle` pins KlumCast `0.4.0-rc.2` and AnnoDocimal `1.0.0-rc.7`; PR #608 fixture resolves their module identities. Final coordinates are #523/#524 scope. | met | #459/#461; #523/#524 for release promotion | Do not reopen the closed adoption gates; consume the final-coordinate work when its owners deliver it. |
| Five explicit canonical descriptors and package ownership | The five `src/main/module-info/module-info.java` files define the ADR names. `JpmsPackageBoundaryTest` inspects their built JAR descriptors, singleton package ownership, no legacy package in the artifacts, exact exports, qualified compiler opens, and service declarations. | met | #391 | Retain the descriptor test; no descriptor redesign. |
| Generated-schema ABI and named-module linkage | JP-ABI records plus `JpmsPackageBoundaryTest` scan generated named-schema class files for runtime-internal references and prove generated factory/template and protected lifecycle shapes. | met | #391 | Do not reopen ABI bridge work without a new failing descriptor or fixture. |
| Real Groovy 4/5 named schema and Groovy 3 classpath boundary | On this baseline, all three focused commands passed: `:klum-ast:test`, `:klum-ast:groovy4Tests`, and `:klum-ast:groovy5Tests`, each filtered to `com.blackbuild.klum.ast.JpmsPackageBoundaryTest`. The test compiles a user-owned schema descriptor, runs Java and `@CompileStatic` Groovy consumers, exercises lifecycle, services, Jackson, Bean Validation, and rejects portability flags. | met | #391 | Retain the three-lane test evidence as the JP-1b closure proof. |
| Related #394/#450 boundary remains synchronized | `release-4.0.md` retains #394 as the separate generated-interface boundary and #450 as the integration audit; ADR 0014 and the issue keep both relationships explicit. Neither creates a descriptor or plugin-validation substitute. | met | #391 with #394/#450 owners | Keep the relationship references in the JP-5 handoff; do not expand this slice into generated-interface or integration-audit work. |
| Schema plugin detects named schemas and validates user-owned descriptors with copyable remediation | `KlumAstSchemaPlugin` only configures dependencies, mirrors, IDEA, and Javadoc. There is no module-source detection, descriptor parser/validator, `check` hook, publication validation, TestKit case, or Groovy-3 remediation. Searches of plugin main/test sources and release schema consumer find no such path. | gap | New smallest #391 JP-5 implementation task | Add generation-aware, read-only descriptor validation: G4/5 required modules/conditional adapters and qualified opens; G3 classpath-only diagnostic; hook it into `check` and applicable publication validation; TestKit valid/invalid/G3 cases. It must never rewrite `module-info.java` or add JVM workaround flags. |
| User guidance for modular and manual users | `docs/user/Migration.md` and `CHANGES.md` accurately give the G4/5 descriptor shape, G3 classpath rule, recompilation, and no-workaround statement. The complete import map exists only in implementation evidence, not in Migration as #391 and the ADR plan require. `Getting-Started.md`, `Gradle-Plugins.md`, and `README.md` (the available requirements entry point; no current `Requirements.md` exists) also do not explain the named-module path or plugin validation, and the current 4.x portable adopter fixtures still import the removed legacy namespace. There is no current `wiki/` authoring tree; `docs/user/` is the live 4.x source. | gap | Same JP-5 task | Publish or link the complete import map from Migration; document the plugin and equivalent manual requirements in Getting Started, Gradle Plugins, Migration, README/requirements, and navigation; update current adopter examples to `com.blackbuild.klum.ast` imports and a G4/5 named-module example. Keep IDE mirrors explicitly outside module inputs. |
| Serialization, Javadocs, CHANGES, and navigation | `CHANGES.md`, Migration, `_Sidebar.md`, and the versioned-Javadoc infrastructure are present. Migration does not explicitly say that Java-serialized 3.x graphs are not 4.0 migration inputs or that Java serialization is not a cross-version persistence format; current Javadocs/Getting Started have no JPMS/plugin remediation reference. | gap | Same JP-5 task | State both serialization limits in Migration and the relevant Javadocs; link the validated modular/manual guidance from Getting Started, plugin docs, CHANGES, and navigation. |
| Compatibility and affected build evidence | PR #608 CI is green, and the fresh three-lane JP-1b run is green. That proves the focused fixture, not the absent plugin contract. | met for implemented boundary | #391 | The JP-5 task adds focused plugin TestKit coverage and reruns the affected plugin/consumer checks with all Groovy lanes at handoff. |

## Closure decision

#391 is **not ready to close**. The only proven product gap is the accepted
JP-5 schema-plugin validation/remediation contract, together with the
documentation and serialization statements that must describe that contract.
The smallest next task is one bounded JP-5 slice owning that read-only plugin
validation, its TestKit coverage, and the directly affected user/Javadoc
guidance. It must preserve the already-proven five descriptors, generated ABI,
and JP-1b fixture unchanged unless the new coverage supplies contrary evidence.

When that task is complete, closure evidence should cite this audit, the
plugin's valid/invalid/Groovy-3 TestKit results, the applicable schema-plugin
and consumer validation, the three JP-1b lane commands above, PR #608's merged
CI, and the final documentation-render/diff checks.
