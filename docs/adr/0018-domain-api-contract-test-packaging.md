# Domain API contract-test packaging

Date: 2026-09-14

Status: Accepted

Implementation status: Implemented for CT-1 through CT-3 in the application-owned `klum-catwalk` consumer. No KlumAST
production, generated API, Schema or Model plugin, or publication change was made. CT-4 remains future extraction-only
work.

Tracking issue: [#755 — Support reusable Domain API contract tests in Layer 3 Schema modules](https://github.com/klum-dsl/klum-ast/issues/755)

Implementation plan: [ADR 0018 implementation plan](../implementation/adr-0018-domain-api-contract-test-packaging.md)

Parent decisions:

- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)
- [ADR 0017 — Published Template test support](0017-published-template-test-support.md)

## Context

A Layer 3 Domain API can have multiple concrete Schema realizations. Generic clients intentionally compile only against
the Domain API, while Schema-specific consumers, including tests, intentionally consume a concrete Schema. Those Schema
tests need an API-owned behavioral contract without copying the same generic assertions into every realization.

This is a testing and dependency-wiring concern. It does not define Layer 3 runtime behavior, generated API behavior, or
Cluster semantics. [#454](https://github.com/klum-dsl/klum-ast/issues/454) documents the Layer 3 boundary and confirms
that it is not a Gradle, package, or organizational boundary. [#753](https://github.com/klum-dsl/klum-ast/issues/753)
separately owns DSL-interface Cluster support or rejection.

[#548](https://github.com/klum-dsl/klum-ast/issues/548) is related only as a deferred, possible future source-level
Schema-composition mode. This decision uses ordinary binary project dependencies and does not require, establish, or
imply source-level composition.

The KlumAST Schema plugin currently adds only framework-owned test support,
`com.blackbuild.klum.ast:klum-ast-test-support`, to `testImplementation`. It cannot know a consumer's Domain API project,
which of several API contracts a Schema realizes, or whether that consumer has an in-build project, a published module, or
an independently selected test framework. Its Model plugin has the same framework-owned test-support boundary.

ADR 0017 is a useful contrast, not a packaging template. It rejected published `java-test-fixtures` for a KlumAST-owned,
versioned Maven product because fixture variants are not its deliberate Maven Central API and must not leak Groovy/Spock
test output. A Domain API's contract suite is application-owned test code. Gradle's native test-fixture capability is
appropriate for an aligned multi-project build, but it is not a portable cross-repository publication promise.

## Evidence

The disposable Gradle tracer for this decision used an `api` project with `java-library` and `java-test-fixtures` and a
`schema` project with the ordinary production dependency plus:

```groovy
testImplementation(testFixtures(project(':api')))
```

`EnvironmentContractTest<T extends Environment>` lived in the API's test fixtures and declared an inherited JUnit test.
`CustomerEnvironmentContractTest` in the Schema implemented the one factory hook. `:schema:test` passed and its JUnit XML
reported the inherited `exposesGenericApplications()` method. Replacing the fixture dependency with
`testImplementation project(':api')` failed `:schema-direct:compileTestJava`: the API's production artifact correctly
does not contain `EnvironmentContractTest`.

A second disposable tracer used an API fixture written in Groovy and Spock 2.4 for Groovy 4. Its Schema consumer selected
the same Groovy 4 and Spock coordinates, inherited the contract specification, and passed `:groovy-schema:test`. This
confirms that Groovy/Spock fixture reuse is technically sound between aligned Layer 3 modules; the prohibited case is a
mismatched selected version, not modular reuse itself.

The repository's multi-Groovy convention explicitly recompiles Groovy test sources and Groovy test fixtures per Groovy
3, 4, and 5 lane, and rejects another lane's compiled test output. That rule applies when one build deliberately runs
several compatibility lanes. It does not prevent a final Layer 3 project from using a single selected Groovy/Spock pair
for all of its Domain API, Schema, and Model modules. In that aligned topology, an API-owned Groovy/Spock fixture is
compiled and executed with the same pair as its Schema consumer, so it crosses no lane boundary.

`klum-catwalk` then delivered the accepted topology. [Catwalk PR #5](https://github.com/klum-dsl/klum-catwalk/pull/5),
merged as [`6233ed1c29a9b2e0ebc19c66aa1aef71c042f663`](https://github.com/klum-dsl/klum-catwalk/commit/6233ed1c29a9b2e0ebc19c66aa1aef71c042f663),
implemented CT-1 and CT-2: an API-owned Groovy/Spock fixture, explicit Schema `testFixtures(project(":domain-api"))`
wiring, inherited-contract discovery, and a retained missing-fixture compile-failure control. [Catwalk PR #6](https://github.com/klum-dsl/klum-catwalk/pull/6),
merged as [`257f8e1a3406f586c2a5f111d61b76ed9bd88112`](https://github.com/klum-dsl/klum-catwalk/commit/257f8e1a3406f586c2a5f111d61b76ed9bd88112),
implemented CT-3 by making the selected Groovy/Spock pair root-owned and verifying API-fixture and Schema-test
classpath alignment, including focused drift evidence. The Catwalk evidence claims one selected pair only; it does not
claim a Groovy 3/4/5 matrix.

`blackbuild/gradle-conventions#1` remains an open design for organization-wide multi-Groovy, publication, and release
conventions. It has no proven Domain-API coordinate-selection contract. `engineering-baseline` owns policy synchronization,
not application-specific dependency mappings. Neither is an owner for a Domain API's test suite.

## Decision

For one Domain API and its Schema realizations in a coordinated Gradle build, the Domain API project owns an opt-in
`java-test-fixtures` capability containing the reusable contract. Each Schema explicitly adds
`testImplementation(testFixtures(project(":domain-api")))` and supplies a concrete test subclass or adapter. The
contract is generic: it uses only Domain API types and asks the Schema test for the concrete completed model it should
verify. The Schema retains all Schema-specific assertions.

The initial fixture may use the final Layer 3 project's selected Groovy and Spock versions, including an abstract Spock
specification whose Schema test supplies the realization hook. The generic fixture must still avoid Schema, Builder,
generated `*_DSL`, Cluster helper, and runtime-internal types: it is a Domain API contract, not a framework extension.
Every participating Domain API, Schema, and Model module must select exactly the same Groovy/Spock pair. The ordinary
KlumAST Schema plugin configures JUnit Platform, so an inherited Java/JUnit test remains an equally valid project-local
choice; neither framework choice belongs in the KlumAST plugin contract.

If a project elects to demonstrate compatibility under Groovy 3, 4, and 5, it must run the complete Domain API/Schema
fixture separately for each selected pair. It must not compile the fixture once under Groovy 3 and use that output for a
Groovy 4 or 5 execution.

No KlumAST Schema or Model plugin change is part of this decision. No KlumAST-owned Domain API plugin or published
domain-contract artifact is part of this decision. A domain repository may later add a small local convention to apply the explicit fixture dependency
when it has several stable API-to-Schema mappings; that convention remains its owner and must be tested with its own
project topology. A shared Gradle convention is premature until multiple independent domain repositories demonstrate the
same mapping/configuration seam.

## Consequences

- Every Schema realization can execute the same API behavioral assertions while retaining local setup and Schema-only
  checks.
- The dependency direction remains explicit and compatible with Layer 3's fact that project layout is optional.
- A colocated or non-Gradle consumer has a clear manual alternative: place the contract support on its test classpath
  through that build's normal test-support mechanism. It must not expect KlumAST plugins to provision application code.
- A Groovy/Spock fixture is technically sound when every Layer 3 module uses one matched selected pair. A build that
  intentionally exercises several pairs must compile the fixture separately in each one.
- A separately published cross-repository contract artifact, a Gradle convention/plugin, and a framework extension remain
  future design questions with different ownership and publication requirements.
- Source-level Schema composition remains #548's separate, post-4.1 design space. A later source-composition mode may
  deliberately widen this fixture topology, but it is not a prerequisite or implicit extension of this decision.

## Rejected alternatives

### Depend only on the API production project

This keeps the correct production dependency but cannot expose test classes; the tracer's direct dependency failed to
compile for exactly that reason. Copying the contract into each Schema avoids the dependency but loses a single source of
truth.

### Add a Domain API mapping to the KlumAST Schema or Model plugin

KlumAST cannot infer application-owned project coordinates, mappings, contract selection, or consumer framework. Adding
such policy would make a generic framework plugin own application topology and violate the explicit Layer 3 boundary.

### Publish a KlumAST-wide test-fixture or convention plugin now

There is no common Domain API artifact or mapping to publish. ADR 0017's Maven/public-test-support constraints also show
that a fixture variant is not automatically a portable product API. The open shared-conventions investigation has not
established this seam as common across consumers.

### Share Groovy/Spock test output between mismatched selected versions

ADR 0011 rejects this: matching Groovy and Spock sources and fixtures must compile separately in each lane. A shared
Groovy-3 fixture would make a Groovy-4/5 execution invalid. It does not prohibit fixture reuse between modules that have
the same selected Groovy/Spock pair.

## Implementation boundary

The accepted contract requires one matching Groovy/Spock pair across participating modules. Catwalk provides the initial
application-owned evidence against version drift, without prescribing a KlumAST plugin mapping, a shared convention, or
a new published artifact. It retains no claim to CT-4 extraction, source composition, or a Groovy 3/4/5 matrix.
