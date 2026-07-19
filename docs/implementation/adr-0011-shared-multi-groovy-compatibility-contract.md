# ADR 0011 implementation plan: shared multi-Groovy compatibility contract

This plan applies [ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md) for
[#455](https://github.com/klum-dsl/klum-ast/issues/455). It records executable ownership and migration order; it does
not implement KlumCast #13 in this repository.

## Evidence inventory

| Concern | KlumAST | AnnoDocimal | KlumCast before #13 |
| --- | --- | --- | --- |
| Production baseline | Java 17; Groovy 3 `compileOnly` | Java 17; Groovy 3 `compileOnly` | Java 11; a global `groovyVersion` selects Groovy 2.4, 3, or 4 for the whole build |
| Compatibility lanes | `test`, `groovy4Tests`, `groovy5Tests`; `check` includes all | The same named test suites and `check` wiring | One full build per Groovy-version CI workflow; no Groovy 5 |
| Test-source handling | Recompiles `src/test/groovy` and Groovy fixtures in 4/5, but `sourceSets.test.output` can leak G3 output | Recompiles test Groovy; Java-only fixture boundary is simpler | Recompiles all production/test sources for each property-selected version |
| Publication | One Java 17 artifact set; main Groovy is compile-only | One Java 17 artifact set | Signed/published once per release, but the selected global Groovy version leaks into dependency metadata |
| Reporting/cache | Baseline coverage plus G4 report; G5 report absent; root config cache blocked by release-plugin listener | aggregated JaCoCo data; root `help` cache reuse succeeds, while published-plugin scenario remains #35 | no Sonar lane reporting; Gradle 6.9 listener/file-encoding cache failures |
| JPMS evidence | #391 fixture must use `org.codehaus.groovy` for G3 and `org.apache.groovy` for G4/5 | merged automatic module-name work | automatic module fixtures currently distinguish G3/G4; G2 has no usable module path |

| Build/release concern | KlumAST | AnnoDocimal | KlumCast before #13 |
| --- | --- | --- | --- |
| Local convention | 63-line `buildSrc` convention; fixture recompilation and extra runtime/test dependencies are real local overlays | 51-line `buildSrc` convention; 47 lines are identical to KlumAST, with a Java-only fixture boundary | root-property selection is repeated through all subprojects rather than a test-lane convention |
| Catalog/dependencies | G3 uses core + BOM; G4/5 Apache group + matching Spock 2.4 | same version generations, using its local `groovy-all` shape | group switches only for G4; no G5 branch |
| CI/matrix | one Java-17 `check`/Sonar run; JUnit publication currently globs only baseline `test` XML | Java-17 build/JaCoCo/Sonar run; no lane-result publisher | separate G2/G3/G4 full-build workflows on Java 11; no Sonar gate |
| Signing/release | standard `maven-publish`/signing once | standard `maven-publish`/signing once | Nebula/signing release flow once, but its selected Groovy changes metadata |

### Reproducible measurements

All timings used Java 17, warm dependency caches, clean task output, and `--no-daemon`:

| Repository / command | Observed time | What it establishes |
| --- | ---: | --- |
| KlumAST `test` | 44 s | focused Groovy-3 baseline |
| KlumAST `groovy4Tests groovy5Tests` | 77 s | final compatibility work without recompiling production per lane |
| KlumAST all lanes warm | 6 s | incremental feedback with existing task outputs |
| AnnoDocimal `test` | 18 s | focused baseline |
| AnnoDocimal `groovy4Tests groovy5Tests` | 15 s | inexpensive additional compatibility lanes in that build |
| AnnoDocimal all lanes warm | 4 s | incremental feedback |
| KlumCast full builds on Java 11: G2/G3/G4 | 10 s / 12 s / 12 s | existing approach repeats whole-build work; G5 is absent |

The dry-run task graphs were 70 tasks for KlumAST `test` versus 153 for `check`, 66 versus 142 for AnnoDocimal, and 58
tasks for each KlumCast property-selected full build (174 across the existing G2/G3/G4 workflows). This confirms the
intended trade-off: developers get a focused baseline while release evidence runs every generation; the build cache avoids
needless repeated work without sharing incompatible compiled Groovy output.

After a forced KlumAST build-cache population, a clean `compileGroovy4TestsGroovy` restored six tasks from cache in four
seconds. AnnoDocimal demonstrated the same six-task restoration. The contract therefore keeps lane-local recompilation
but treats the build cache as a feedback mechanism, not a reason to share cross-generation compiled Groovy output.

## Confirmed behavior and failure path

`buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle` creates G4/G5 suites, maps their dependencies from the
version catalog, and re-adds Groovy test-fixture sources to every test compile task. It also adds `sourceSets.test.output`
to those suites. Because the baseline test source set can contain Groovy classes compiled by Groovy 3, that classpath edge
invalidates the claim that the G4/G5 lanes are independent Groovy test executions. #496 replaces it with a documented
version-neutral sharing boundary and adds auditable G4/G5 result/coverage evidence.

KlumCast's root `groovyVersion` property maps Groovy 2/3 to `org.codehaus.groovy` and Groovy 4 to
`org.apache.groovy`. The property controls all subprojects and full CI builds. Its generated SPI POM contains whichever
Groovy API coordinate happened to build it. A local POM generation check showed `org.codehaus.groovy:groovy:3.0.17` for
Groovy 3 and `org.apache.groovy:groovy:4.0.12` for Groovy 4, proving that one published POM cannot represent all three
consumer generations.

## Contract and seams

| Seam | Required implementation shape |
| --- | --- |
| Production source | Java 17, one Groovy-3 compile; `compileOnly` Groovy where production source needs compiler APIs; publish once. |
| Test sources/fixtures | G3 baseline in `test`; recompile Groovy sources/fixtures in G4/G5; share only explicit Java/resource output. |
| Dependency selection | Local catalog/convention maps G3 to `org.codehaus` and G4/G5 to `org.apache`; matching Spock generation follows each lane. |
| Public metadata | No one Groovy API dependency as a proxy for consumer selection. Groovy-facing consumer SPI documents an explicit selected-Groovy prerequisite. |
| CI/release | Java 17 matrix passes before signing/publishing; lane results are individually discoverable; production coverage is not triple-counted. |
| JPMS | Classpath and module-path fixtures use the generation-matching module name. #391 owns descriptor conclusions. |
| Reuse | Repository-local convention remains authoritative for its build until `gradle-conventions#1` defines reconciliation and publishes a tested extraction. |

## Migration slices and acceptance coverage

### MG-1 — KlumAST reference correction (#496, blocked by #455)

**Seams:** `buildSrc` multi-Groovy convention, `code-coverage-report`, root GitHub Actions result publishing, and Groovy
test-fixture source layout.

**Work:** remove the cross-lane Groovy output edge; make a Java/resources-only sharing seam explicit where needed;
recompile all Groovy fixtures per lane; add `verifyTestLaneIsolation` to reject mismatched dependencies, cross-lane
source-set/Groovy compile/runtime output, wrong test classes, or non-lane JUnit result locations; archive G4/G5 JUnit and
JaCoCo evidence while Sonar uses only baseline coverage. Preserve the one Groovy-3 production artifact and Java 17
toolchain.

**Acceptance:** inspect the G4/G5 compile/runtime classpaths; run `test`, `groovy4Tests`, `groovy5Tests`, and `check` on
Java 17; exercise the build cache; verify CI result paths/report tasks include all lanes. If the #391 fixture is touched,
run it under G3/G4/G5 with matching module names only.

**Commit boundary:** one behavior-and-test/fixture commit, followed by one CI/report documentation commit if that produces
a clearer independently green history.

### MG-2 — KlumCast 0.4 migration (#13)

**Seams:** Gradle wrapper/plugins and Java toolchain; root dependency selector; all `java-library` subprojects; test
sources/fixtures; publication/signing; CI/release documentation; classpath/module-path consumer fixtures.

**Work:**

1. Upgrade to a Gradle baseline that supports Java 17 and the chosen toolchain. Remove the Groovy-2.4 property branch and
   Java-11 release gate while leaving 0.3 support documentation intact until 0.4 is delivered.
2. Add repository-local `test`, `groovy4Tests`, and `groovy5Tests` suites. Compile production once with Groovy 3;
   recompile Groovy test sources in each compatibility lane; create a deliberate Java/resource fixture seam if sharing is
   needed. `check` must include all lanes.
3. Map dependencies and JPMS fixtures as G3 `org.codehaus.groovy`/`org.codehaus.groovy`, G4/G5
   `org.apache.groovy`/`org.apache.groovy`, with matching Spock versions.
4. Change `klum-cast-spi` from a transitive Groovy API dependency to documented consumer-provided Groovy. Keep annotations
   Groovy-free; keep compiler activation with `klum-cast-compile`. Add Gradle and Maven consumer fixtures for each
   supported Groovy generation so publication metadata and compiler activation are both proved.
5. Replace version-specific full-build workflows with Java-17 lane evidence, publish each lane's JUnit results, retain
   risk-based packaging/module-path smoke fixtures, and permit signing/publication only after `check` and those fixtures.
   Publish one artifact set once.
6. Update contributor/release migration material: 0.4 requires Java 17/Groovy 3–5, Groovy 2.4/Java 11 support ends at
   0.3, and custom-check authors declare the matching Groovy compiler dependency.

**Acceptance:** clean and cached `test`, `groovy4Tests`, `groovy5Tests`, and `check` runs on Java 17; Gradle and Maven
published-consumer fixtures for G3/G4/G5; annotation POM has no Groovy; SPI POM has no forced Groovy selector; compiler
activation, classpath, and module-path fixtures pass in all lanes; signing/publication dry run produces one coordinate set.

**Commit boundaries:** (1) wrapper/toolchain and G3 baseline, (2) compatibility suites and fixtures, (3) publication/
consumer smoke tests, (4) CI reporting and release documentation. Each commit remains independently buildable where the
Gradle upgrade permits it.

### MG-3 — AnnoDocimal evidence closure (existing #35 and #46)

**Seams:** local multi-Groovy convention, GitHub Actions reporting, JaCoCo/Sonar configuration, published plugin functional
fixture.

**Work:** retain its current local lane shape; make compatibility-lane result evidence diagnosable through #46 and verify
the published convention scenario's configuration-cache behavior through #35. No new extraction or module-name redesign
is implied.

**Acceptance:** G3/G4/G5 lane result evidence, non-duplicated coverage policy, and a documented configuration-cache result
for the published-plugin scenario.

### MG-4 — Central reconciliation design (`blackbuild/gradle-conventions#1`)

**Seams:** potential published convention-plugin boundary, TestKit fixtures, version/release documentation, downstream
upgrade control.

**Work:** compare MG-1/MG-2/MG-3 local conventions; first decide the reconciliation owner/cadence and divergence record,
then design the smallest plugin family and test fixtures. Keep consumer Groovy dependency selection separate from internal
test-lane orchestration.

**Acceptance:** #1's stated reconciliation, release/version, consumer-fixture, and controlled-upgrade criteria are met
before the first central release. No local repository is switched to an unproven central plugin merely to validate it.

## Documentation and release policy

ADR 0011 is the shared decision record. Each owning repository updates its contributor/testing instructions and release
notes as its migration ships; KlumCast also updates its 0.4 migration guide for custom-check authors. This decision-only
KlumAST change does not alter a released user artifact and therefore needs no `CHANGES.md` or wiki entry.

## Risks and deliberately unresolved work

- The exact central-plugin API, reconciliation schedule, and release cadence remain with `gradle-conventions#1`; ADR 0011
  intentionally does not select them.
- Existing root configuration-cache failures are measured constraints, not a license to omit a cache-safe published-plugin
  verification. AnnoDocimal #35 and local release-plugin cleanup own the follow-up evidence.
- #496 must avoid replacing one hidden cross-lane Groovy output dependency with another; its acceptance must inspect
  compile and runtime classpaths.
- #391 retains module-descriptor design. This plan requires only matching G3/G4/G5 fixture evidence where a migration
  affects that boundary.
