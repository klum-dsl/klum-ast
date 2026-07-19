# Shared multi-Groovy compatibility contract

Date: 2026-07-19

Status: Accepted

Tracking issue: [#455 — Revisit and align the multi-Groovy build approach across Klum libraries](https://github.com/klum-dsl/klum-ast/issues/455)

Implementation owners: [KlumAST #496](https://github.com/klum-dsl/klum-ast/issues/496),
[KlumCast #13](https://github.com/klum-dsl/klum-cast/issues/13), and the existing AnnoDocimal
configuration-cache/reporting work. Future reusable convention extraction is deliberately owned by
[blackbuild/gradle-conventions#1](https://github.com/blackbuild/gradle-conventions/issues/1).

## Context

KlumAST and AnnoDocimal already verify one Java 17 production artifact against Groovy 3, 4, and 5 through local Gradle
test suites. KlumCast instead selects one Groovy version for the whole build with a property and separate CI workflows;
it must move from Java 11/Groovy 2.4–4 to Java 17/Groovy 3–5 for 0.4.

The source and binary boundary is not uniform across those Groovy versions. Groovy 3 uses
`org.codehaus.groovy` and JPMS module `org.codehaus.groovy`; Groovy 4 and 5 use `org.apache.groovy` and
`org.apache.groovy`. Groovy 4 formally changed the Maven group, so one ordinary Maven POM cannot select the right
transitive Groovy coordinate for a consumer's selected compiler generation. Gradle feature variants do not repair that
for Maven consumers because Maven POM metadata has no equivalent variant model. See the
[Groovy 4 release notes](https://groovy-lang.org/releasenotes/groovy-4.0.html) and
[Gradle Module Metadata documentation](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html).

KlumAST's main sources compile once against Groovy 3 using a `compileOnly` dependency. Its `test`, `groovy4Tests`, and
`groovy5Tests` lanes recompile Groovy test sources with the corresponding Groovy and Spock generation. Investigation
also found that the current compatibility convention adds `sourceSets.test.output`, which can expose Groovy-3-compiled
test output to a Groovy-4/5 lane. That is a reference-implementation defect, not an allowed exception; #496 owns the
correction.

## Decision

### Common compatibility contract

Every participating library preserves one Java 17 production artifact set, compiled once with Groovy 3. Groovy is a
compile-time dependency of the library or compiler integration, not a Groovy-version-selection mechanism exported by its
published POM. Where a public SPI or compiler-facing artifact exposes Groovy types, its consumer explicitly supplies the
matching Groovy 3, 4, or 5 dependency. The annotation-only artifact remains Groovy-free.

The generation mapping is fixed:

| Generation | Maven group | JPMS module |
| --- | --- | --- |
| Groovy 3 | `org.codehaus.groovy` | `org.codehaus.groovy` |
| Groovy 4 | `org.apache.groovy` | `org.apache.groovy` |
| Groovy 5 | `org.apache.groovy` | `org.apache.groovy` |

Each repository pins tested patch versions in its own catalog or equivalent local dependency definition. The contract
does not require an artificial organization-wide patch lockstep.

`test` is the fast Groovy-3 development lane. `groovy4Tests` and `groovy5Tests` are named compatibility lanes, and
`check` depends on all three. Each lane uses its matching Spock/Groovy combination and compiles the same Groovy test
source and any Groovy test fixtures for that lane. A compatibility lane may share explicit Java-only helper output and
resources, but must not receive test or fixture output compiled with a different Groovy/Spock generation. Production
classes remain the one Groovy-3-compiled artifact under every lane.

All Java-17 releases run the full three-lane matrix before publication/signing. CI must expose diagnosable result evidence
for each lane, even when jobs are consolidated. Coverage may use the Groovy-3 baseline as the non-duplicated production
coverage measure, but Groovy 4/5 execution and results must remain independently auditable. Any JPMS feasibility fixture
uses the matching module name in every lane and supplies evidence to #391 without changing that issue's descriptor design.

Build-cache reuse is expected and is part of the measured feedback strategy. Configuration-cache compatibility is an
independent requirement for a published/shared convention-plugin scenario; current root-build listener failures do not
make a repository non-releasable, but must not be copied into a shared plugin or represented as verified.

### Publication and consumer selection

Publication, signing, source/Javadoc artifacts, and release metadata run once for the single production artifact set;
there is no artifact or release per Groovy lane. A library must not publish one transitive Groovy API coordinate merely
because it was compiled against Groovy 3: that coordinate is wrong for Groovy-4/5 consumers and cannot be made
consumer-selectable in an ordinary Maven POM.

For KlumCast this supersedes #24's former expectation that `klum-cast-spi` publish Groovy as an API dependency.
`klum-cast-spi` documents its Groovy-facing API and requires custom-check/compiler-plugin consumers to select the matching
Groovy compiler dependency. `klum-cast-annotations` publishes no Groovy dependency. `klum-cast-compile` owns compiler
activation and its build-time classpath; it does not turn the SPI POM into a version selector.

### Ownership and deliberate local implementation

The immediate implementation is deliberately repository-local. KlumAST and AnnoDocimal retain local build conventions;
KlumCast implements the contract locally during #13. The published KlumAST `com.blackbuild.convention.groovy` plugin is a
consumer dependency-selection plugin and is not the repository compatibility-lane orchestration mechanism.

No local implementation becomes canonical merely by landing first. Before any extraction, #1 must define a reconciliation
owner and cadence, divergence record, central release/version policy, and controlled downstream upgrade process. Extraction
starts only after that policy and real consumer migrations establish a testable plugin boundary. This makes local changes
reviewable for public applicability without making a half-proven empty central repository a release dependency.

## Safe migration sequence

1. Record this contract and keep #455 as the cross-repository decision owner.
2. Correct KlumAST's reference lane isolation and compatibility reporting in #496. It is blocked by #455 but does not
   gate KlumCast's planning or decision handoff.
3. Apply the contract in KlumCast #13: move to Java 17 and a Java-17-capable Gradle baseline; remove Groovy 2.4 selection;
   compile production once with Groovy 3; add the three named lanes; update consumer, JPMS, CI, release, and migration
   evidence; then publish once only after the matrix passes.
4. Keep AnnoDocimal's local lane shape, while its existing #35 and #46 own configuration-cache and diagnosable CI evidence.
   Its merged module-name work remains the upstream JPMS packaging input, not a reason to redesign its build here.
5. Use the completed local migrations and their documented overlays as evidence for the reconciliation design in
   `blackbuild/gradle-conventions#1`; do not extract first and make the libraries beta consumers of an unproven plugin.

The sequence keeps every repository releasable: existing releases retain their current support until the relevant owning
issue delivers, and no published coordinate is silently changed as a side effect of a test-lane migration.

## Consequences

- A focused edit normally runs the Groovy-3 baseline quickly; `check` gives final Groovy 3/4/5 release evidence.
- The Groovy 3 versus 4/5 coordinate and module boundary is explicit in local dependency selection and JPMS fixtures.
- Consumers retain control of the Groovy compiler generation they already selected, rather than receiving a conflicting
  transitive dependency from a multi-Groovy library.
- Local duplication remains temporarily intentional. The shared extraction decision is deferred only with a tracked,
  evidence-led reconciliation path.
- #391 receives matrix evidence when its fixture needs it, but retains ownership of explicit module-descriptor design.

## Rejected alternatives

**One whole build or publication per Groovy version.** This recompiles production sources and repeats publication work
without proving one artifact works for all supported consumers. The measured KlumCast property builds already show the
feedback and maintenance cost of this shape.

**A single transitive Groovy API dependency in every POM.** It would publish a Groovy-3 `org.codehaus` coordinate to
Groovy-4/5 consumers, or vice versa, and no ordinary Maven POM can express the conditional replacement.

**Immediate shared-convention extraction.** The candidate central repository has no proven consumer boundary or
reconciliation policy. Premature extraction would move uncertainty into every release path rather than reduce it.

**Treating current KlumAST output reuse as an exception.** A Groovy-3-compiled test class is not neutral helper output.
The compatibility claim is meaningful only when Groovy/Spock code is compiled and run in its own lane.
