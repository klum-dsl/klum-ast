# Published showcase consumer verification

Date: 2026-09-14

Status: Proposed

Tracking issue: [#484 — Create a KlumAST showcase project for examples and demonstrations](https://github.com/klum-dsl/klum-ast/issues/484)

Implementation plan: [ADR 0019 implementation plan](../implementation/adr-0019-published-showcase-consumer-verification.md)

Related decisions:

- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)
- [ADR 0013 — Versioned user documentation and Javadocs](0013-versioned-documentation-and-javadocs.md)
- [ADR 0018 — Domain API contract-test packaging](0018-domain-api-contract-test-packaging.md)

## Context

KlumAST needs a small independently built consumer that makes published-artifact integration observable. The current
`release/consumer` fixture is intended to resolve all Maven modules and Gradle Plugin Portal markers, but deliberately
does not compile a real Schema, run AnnoDocimal source projection, render that Schema's Javadocs, or execute its tests.
The 2026-09-14 tracer also found that its current source unconditionally asks Maven Central for
`klum-ast-test-support:4.0.0`, an artifact introduced after 4.0.0; that exact public resolve therefore fails. In-repository
TestKit proves local plugin behavior, but it can use checkout build logic and does not prove a downstream client receives
the same product through public coordinates.

This realizes #484's examples-and-demonstrations idea at the same published-consumer boundary. Documentation examples
need readable stories and pedagogical scope; release evidence needs deterministic, diagnosis-friendly builds. They can
share one repository and a common release-line pin, provided the repository distinguishes a deliberately selected
verification baseline from ordinary presentation material. That lets a real showcase become a regression baseline for a
specific product defect without making every example edit a release-sensitive change.

The evidence is concrete. AnnoDocimal [#99](https://github.com/blackbuild/anno-docimal/issues/99) was exposed by a
KlumAST schema whose generated signature refers to an external nested type: projection requires the Schema compile
classpath. AnnoDocimal [#100](https://github.com/blackbuild/anno-docimal/issues/100) then showed that generated Javadoc
can fail at the consumer's `javadoc` task. KlumAST's Schema plugin supplies the projection task's referenced classpath,
enables source/Javadoc artifacts, and excludes IDE-only `*_DSL` mirrors from ordinary Javadocs. A published consumer is
the missing cross-product proof.

Layer 3 needs a second topology. ADR 0018 assigns an application-owned Domain API fixture and explicit binary
`testFixtures(project(":domain-api"))` wiring to the consumer build. It neither depends on nor implies #548's deferred
source-level Schema composition.

## Decision

Create a separate repository named **`klum-catwalk`**, with three deliberately distinct but coordinated responsibilities:

1. It is the canonical home for curated runnable showcases and documentation links.
2. It owns a small executable **published-consumer verification program**. Its two topology fixtures are
   production-like compatibility evidence, not a general user-project template.
3. It owns migration rehearsals that exercise a supported prior release's adoption path against the next line.

The first delivery is the direct verification program. It establishes the repository, the coordinate-manifest contract,
and the policy for promoting an existing showcase into a retained verification baseline. Broad demos and presentation
material may follow independently, but do not enlarge a release-required task graph merely by existing in the repository.

### Release-line baselines

Each supported release line has an explicit, versioned baseline manifest. It selects the direct fixture and any promoted
showcases or migration rehearsals that prove a real compatibility commitment for that line. A new minor release must pass
the retained baselines for its supported predecessor lines as well as its own current-line baseline. A newly added
showcase is release evidence only after a reviewed manifest change promotes it; otherwise it remains a runnable example.

The baseline manifest records the historical consumer contract: fixture revision/topology, expectations, task graph,
compatibility commitment, and that line's released KlumAST pin. A historical/public revalidation resolves that recorded
pin, so it continues to prove the release line exactly as published. Candidate qualification is different: it rebinds the
same predecessor-line fixture contract to the newer candidate coordinates. Thus qualifying 4.1 runs the retained 4.0
consumer baseline against the 4.1 candidate, proving that the supported 4.0-era consumer contract still works with 4.1.
The rebinding never weakens the candidate boundary: marker, plugin implementation, BOM/product modules, and manifest must
all resolve from the same exact candidate repository and version.

This preserves a compact default gate while allowing a complex real-world reproduction to become durable regression
evidence. Compatibility is expressed by pinned coordinates, fixture revision, and manifest—not by copying the project
or synchronizing commits with KlumAST.

### Two topology fixtures

The first fixture is a **direct Schema consumer**. It applies the published Schema plugin to one small Schema, compiles
it, runs a completed-model test, refreshes its IDE-only source projection, renders Javadocs, and runs the ordinary full
build. It asserts that generated `Foo_DSL` mirrors are projection/IDE metadata only: they are neither compiled nor
packaged nor fed back to a downstream classpath.

The second fixture is a **Layer 3 API + Schema multi-project consumer**. It has a Domain API project, concrete Schema
project, and only the minimum model/test code needed to prove the generic client boundary. It owns ADR 0018's thin
application decisions:

- CT-1: the Domain API's `java-test-fixtures` generic contract mentions Domain API types only;
- CT-2: one Schema explicitly consumes `testFixtures(project(":domain-api"))` and supplies the realization hook; and
- CT-3: every participating API, Schema, and Model project selects one matching Groovy/Spock pair. A later claimed
  Groovy 3/4/5 matrix recompiles and runs the complete fixture per pair; it never shares compiled fixture output.

The direct topology is the release-gate minimum. The Layer 3 fixture follows it and is the canonical executable consumer
for CT-1 through CT-3. Neither fixture declares source-level Schema dependencies; that remains #548, post-4.1, and needs
a separate decision.

### Coordinate-only gate

Each fixture resolves KlumAST by declared external coordinates. It must never use an included build, composite
substitution, `files(...)`, a project dependency to a KlumAST checkout, `mavenLocal()`, or an implicit Gradle cache as a
provider. Dependency verification records the resolved group:name:version, repository origin, plugin marker, Gradle
version, JVM, and AnnoDocimal version in retained evidence.

There are exactly two evidence modes:

| Mode | Inputs and repositories | Purpose |
| --- | --- | --- |
| Public-release | Immutable released KlumAST version; Maven Central and Gradle Plugin Portal only; fresh isolated Gradle home. | Proves the public product as a client obtains it. |
| Candidate-maintenance | Explicit coordinate manifest and isolated temporary Maven repository containing the candidate Maven modules, Gradle plugin marker, and plugin implementation; fresh isolated Gradle home. | Proves the exact next maintenance or release candidate before it is trusted as public. |

The candidate repository is an intentionally configured repository, not `mavenLocal()`. The manifest names every
candidate KlumAST Maven coordinate, its Gradle plugin ID and marker coordinate, the matching plugin implementation
coordinate, repository URL, Gradle wrapper, and SHA; it records an AnnoDocimal coordinate when that input is intentionally
varied. Every KlumAST candidate entry carries one candidate version. The consumer's `pluginManagement` resolves the
KlumAST plugin marker normally from that candidate repository, and the marker resolves the implementation from the same
manifest/version. It must not use a public Plugin Portal marker while calling the run complete candidate evidence.

Candidate preparation and consumer execution use separate clean directories with no source checkout on the consumer
classpath. A run is complete candidate release evidence only when the retained resolution evidence binds the marker,
plugin implementation, BOM/product modules, and manifest to that one candidate version and candidate repository. A
missing or public-origin marker/implementation, a mixed version, or an incomplete module set fails the candidate gate. A
failing resolution, projection, Javadoc, compilation, or test is likewise a failing gate; no local-classpath fallback is
permitted.

The bootstrap/test harness may publish the marker, implementation, and product artifacts to `mavenLocal()` to exercise
normal `pluginManagement` resolution and its failure modes before the isolated repository wiring exists. Such a run is
labelled test infrastructure, records that source, and can never qualify as public or candidate release evidence. The
production/public-release mode and the final candidate-maintenance mode both continue to forbid `mavenLocal()`.

The required direct graph is `clean`, source-mirror refresh, `javadoc`, and `check`, using the selected release line's
actual default plugin configuration. The release manifest defines its product set: a 4.0.x run must not demand the later
test-support coordinate, whereas a line that provides that test support must prove it resolves. The Layer 3 fixture adds
its API/Schema contract-test execution. This detects interaction among published KlumAST modules, resolved AnnoDocimal,
Gradle/JDK, generated source projection, Javadoc, and normal test execution. It does not claim IntelliJ behavior,
source-level Schema composition, or a complete documentation catalog.

### Cadence and release placement

Catwalk pull requests run their affected pinned fixtures against public coordinates. They are the only routine PRs
blocked by the catwalk gate. Ordinary KlumAST development PRs do not call the external project and are not delayed by
example churn or an unavailable external runner.

A scheduled/manual public-revalidation run resolves each supported pin cleanly and reports dependency drift or service
failure without changing product state. A manually dispatched candidate-maintenance run is required release evidence for
the exact coordinate manifest, including its staged plugin marker and implementation. For a minor release, it runs every
retained predecessor-line baseline rebound to that candidate and the selected current-line baseline. It does not
re-run predecessor historical pins as candidate evidence; those pins remain the target of public revalidation. It runs
after candidate artifacts exist and before protected release approval; it complements, rather than replaces, KlumAST's
`release/consumer` resolver and `REL-2` public proof. After publication, `REL-2` still resolves only real public
endpoints, as required by `RELEASING.md`.

Admit the direct Schema minimum to **release/4.0.x** when that line first has an accepted maintenance release candidate.
This is justified despite not being a product bugfix: it tests the public maintenance product as a Schema consumer and is
bounded release-reliability work. The smallest safe scope is one direct fixture, line-aware coordinate manifest, isolated
public and candidate runs, and retained evidence. Before using it as release evidence, repair or version-scope the current
`release/consumer` assertion so historical 4.0.x proof does not request the later test-support artifact. This prerequisite
must not be hidden by a composite, a local artifact, or an invented 4.0.x coordinate. The admission must not add Layer 3,
new published APIs, a shared convention, or a normal KlumAST PR requirement to the first maintenance delivery.

### Branch and version policy

The catwalk has current-development `main` and explicit compatibility branches such as `release/4.0.x` when support is
admitted. The latter is a showcase compatibility branch, not creation of KlumAST's maintenance branch; KlumAST still
follows #522 and creates its `release/4.0.x` only with the first accepted maintenance fix. A showcase branch starts at a
reviewed compatibility baseline, pins an explicit tested KlumAST release, and changes only for reviewed compatibility
fixes, candidate proof, or deliberate support updates.

Each showcase compatibility tag records the exact coordinate manifest and fixture revision. Dependency pins, not matching
Git commits, express compatibility. `main` tracks the next intended KlumAST release line and advances pins only after the
corresponding proof. A KlumAST maintenance fix's forward-promotion under #522 does not require a showcase commit;
showcase fixture or documentation evolution does not require a KlumAST commit. This is explicit compatibility mapping,
not perpetual commit-for-commit synchronization.

### Ownership

KlumAST owns released coordinates, plugin contract, release workflow, and whether candidate evidence is required. The
catwalk owns fixtures, pins, consumer-only build logic, retained evidence, baseline promotion, migration rehearsals, and
documentation/demo evolution. AnnoDocimal
owns projection and parsing behavior (#99/#100); a showcase failure is a reproducible integration report, not permission
to patch AnnoDocimal or override its version. Engineering Baseline owns cross-repository policy vocabulary and
evidence/authorization boundaries, not fixture topology or release execution. `gradle-conventions` may consider a future
reusable convention only after repeated independent consumers establish a smaller portable interface; it does not own
KlumAST coordinate selection, Domain API mappings, or this first showcase build.

## Consequences

- The first published-client proof covers behavior the resolver-only release consumer deliberately does not.
- A small verification baseline stays failure-diagnostic while complex showcases can be promoted deliberately for a
  concrete regression or release-line compatibility promise.
- Minor releases prove retained compatible showcase/migration baselines of supported predecessor lines **against the
  new candidate**, rather than merely re-running historical pins or relying solely on newly authored fixtures.
- Candidate proof has a complete coordinate boundary, preventing a successful composite, local-classpath build, or
  released-plugin/candidate-module mixture from being mistaken for published-consumer evidence.
- The cost is one small repository, coordinate-manifest maintenance, fresh-cache CI time, and release-run coordination.
  The direct fixture is intentionally kept small to contain that cost.
- Supporting a new release line requires an explicit branch/pin decision, not an automatic mirror of every KlumAST line.

## Rejected alternatives

### Keep all demonstrations in KlumAST tests or documentation

Those remain valuable source-level evidence but resolve local projects and build logic. They cannot distinguish a bad
published coordinate, transitive projection regression, or downstream Javadoc failure from an in-repository success.

### Make every showcase a release gate

This couples release reliability to a large changing educational surface and makes routine examples unnecessarily hard to
evolve. The catwalk instead promotes a showcase into a versioned baseline only when it captures a supported compatibility
or regression commitment.

### Use a composite build, `mavenLocal()`, direct classpath, or released plugin marker for candidate proof

Each can hide incomplete metadata, publication, plugin-marker, plugin-implementation, or transitive dependency failures.
A candidate repository is acceptable only because it resolves every candidate coordinate, including the marker and plugin
implementation, from an isolated normal Maven repository. `mavenLocal()` remains useful only as labelled bootstrap/test
infrastructure and is never final release evidence.

### Put CT-1 through CT-3 into KlumAST plugins or a shared Gradle convention

ADR 0018 keeps Domain API fixture dependencies and matching test-framework selection application-owned. One showcase is
only one topology, which fails Engineering Baseline's repeated-consumer requirement for extraction.
