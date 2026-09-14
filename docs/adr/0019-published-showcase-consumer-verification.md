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

This overlaps #484's examples-and-demonstrations idea but is a different responsibility. Documentation examples need
readable stories and pedagogical scope. A release gate needs a tiny stable build whose failure identifies a product
integration regression. Combining them would make example editing release-sensitive and release proof demo-dependent.

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

Create a separate repository, provisionally named **`klum-showcase`**, with two independently valuable responsibilities:

1. It is the canonical home for curated runnable examples and documentation links. These are human-facing material and
   remain optional to release proof.
2. It owns a small executable **published-consumer verification program**. Its two topology fixtures are
   production-like compatibility evidence, not reference documentation or a general user-project template.

The repository starts with only the verification program. Broad demos, presentation material, and a public example catalog
are later #484 work and must not enlarge the first release gate.

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
| Candidate-maintenance | Explicit coordinate manifest and isolated temporary Maven repository populated by the candidate publication; released Plugin Portal markers unless matching candidate markers are staged by coordinate; fresh isolated Gradle home. | Proves the exact next maintenance or release candidate before it is trusted as public. |

The candidate repository is an intentionally configured repository, not `mavenLocal()`. The manifest names every
candidate KlumAST and, when intentionally varied, AnnoDocimal coordinate, repository URL, Gradle wrapper, and SHA. The
fixtures resolve only those coordinates through normal Maven/Plugin-Portal metadata. Candidate preparation and consumer
execution use separate clean directories with no source checkout on the consumer classpath. A failing resolution,
projection, Javadoc, compilation, or test is a failing gate; no local-classpath fallback is permitted.

The required direct graph is `clean`, source-mirror refresh, `javadoc`, and `check`, using the selected release line's
actual default plugin configuration. The release manifest defines its product set: a 4.0.x run must not demand the later
test-support coordinate, whereas a line that provides that test support must prove it resolves. The Layer 3 fixture adds
its API/Schema contract-test execution. This detects interaction among published KlumAST modules, resolved AnnoDocimal,
Gradle/JDK, generated source projection, Javadoc, and normal test execution. It does not claim IntelliJ behavior,
source-level Schema composition, or a complete documentation catalog.

### Cadence and release placement

Showcase pull requests run their fixtures against pinned public coordinates. They are the only routine PRs blocked by the
showcase gate. Ordinary KlumAST development PRs do not call the external showcase and are not delayed by example churn or
an unavailable external runner.

A scheduled/manual public-revalidation run resolves each supported pin cleanly and reports dependency drift or service
failure without changing product state. A manually dispatched candidate-maintenance run is required release evidence for
the exact coordinate manifest. It runs after candidate artifacts exist and before protected release approval; it
complements, rather than replaces, KlumAST's `release/consumer` resolver and `REL-2` public proof. After publication,
`REL-2` still resolves only real public endpoints, as required by `RELEASING.md`.

Admit the direct Schema minimum to **release/4.0.x** when that line first has an accepted maintenance release candidate.
This is justified despite not being a product bugfix: it tests the public maintenance product as a Schema consumer and is
bounded release-reliability work. The smallest safe scope is one direct fixture, line-aware coordinate manifest, isolated
public and candidate runs, and retained evidence. Before using it as release evidence, repair or version-scope the current
`release/consumer` assertion so historical 4.0.x proof does not request the later test-support artifact. This prerequisite
must not be hidden by a composite, a local artifact, or an invented 4.0.x coordinate. The admission must not add Layer 3,
new published APIs, a shared convention, or a normal KlumAST PR requirement to the first maintenance delivery.

### Branch and version policy

The showcase has current-development `main` and explicit compatibility branches such as `release/4.0.x` when support is
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
showcase owns fixtures, pins, consumer-only build logic, retained evidence, and documentation/demo evolution. AnnoDocimal
owns projection and parsing behavior (#99/#100); a showcase failure is a reproducible integration report, not permission
to patch AnnoDocimal or override its version. Engineering Baseline owns cross-repository policy vocabulary and
evidence/authorization boundaries, not fixture topology or release execution. `gradle-conventions` may consider a future
reusable convention only after repeated independent consumers establish a smaller portable interface; it does not own
KlumAST coordinate selection, Domain API mappings, or this first showcase build.

## Consequences

- The first published-client proof covers behavior the resolver-only release consumer deliberately does not.
- A small verification fixture stays failure-diagnostic as documentation examples grow independently.
- Candidate proof has a real coordinate boundary, preventing a successful composite or local-classpath build from being
  mistaken for published-consumer evidence.
- The cost is one small repository, coordinate-manifest maintenance, fresh-cache CI time, and release-run coordination.
  The direct fixture is intentionally kept small to contain that cost.
- Supporting a new release line requires an explicit branch/pin decision, not an automatic mirror of every KlumAST line.

## Rejected alternatives

### Keep all demonstrations in KlumAST tests or documentation

Those remain valuable source-level evidence but resolve local projects and build logic. They cannot distinguish a bad
published coordinate, transitive projection regression, or downstream Javadoc failure from an in-repository success.

### Make the showcase a broad demo catalog before it proves a consumer

This couples release reliability to a large changing educational surface and delays the small integration signal.

### Use a composite build, `mavenLocal()`, or direct classpath for candidate proof

Each can hide incomplete metadata, publication, plugin-marker, or transitive dependency failures. A candidate repository
is acceptable only because it is resolved by explicit coordinates from an isolated normal Maven repository.

### Put CT-1 through CT-3 into KlumAST plugins or a shared Gradle convention

ADR 0018 keeps Domain API fixture dependencies and matching test-framework selection application-owned. One showcase is
only one topology, which fails Engineering Baseline's repeated-consumer requirement for extraction.
