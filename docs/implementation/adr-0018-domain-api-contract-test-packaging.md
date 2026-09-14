# ADR 0018 implementation plan: Domain API contract-test packaging

This is the dependency-ordered plan for accepted [ADR 0018](../adr/0018-domain-api-contract-test-packaging.md) and
[#755](https://github.com/klum-dsl/klum-ast/issues/755). It is not authorization to change KlumAST production behavior,
generated APIs, Cluster semantics, or its Gradle plugins. The application-owned fixture work remains future and
unmilestoned.

## Confirmed starting behavior and failure paths

| Concern | Confirmed seam | Result |
| --- | --- | --- |
| Layer 3 boundary | `docs/user/Layer3.md`, #454 | Domain API is generic; Schema tests can intentionally consume concrete Schema types. Layer 3 is not a Gradle-project convention. |
| KlumAST plugin test dependencies | `KlumAstSchemaPlugin`, `KlumAstModelPlugin` | Plugins provision only `klum-ast-test-support` on `testImplementation`; they have no Domain API mapping input. |
| Local fixture reuse | Gradle `java-test-fixtures` tracer | A Schema `testImplementation(testFixtures(project(':api')))` compiles an API fixture and executes its inherited JUnit contract test. |
| Ordinary API dependency | Gradle tracer | `testImplementation project(':api')` cannot compile an import of an API test class; production output correctly excludes it. |
| Test-lane compatibility | `klum-ast.multigroovy-conventions.gradle`, ADR 0011 | Groovy and Spock tests/fixtures are independently compiled for Groovy 3, 4, and 5 only when a build runs those lanes. A final Layer 3 project instead selects one matched Groovy/Spock pair for all participating modules; Groovy/Spock fixture reuse is safe within that pair. |
| Existing published support | ADR 0017 / `klum-ast-test-support` | Framework-owned, Maven-published test support is a separate, intentionally narrow artifact; it does not establish a packaging rule for application-owned Domain API tests. |

The tracer used no KlumAST production source and no new plugin. Its passing command was:

```shell
./gradlew -p /private/tmp/klum-755-contract-tracer.b7xwUA :schema:test --console=plain --warning-mode=none
```

The passing report contained one inherited `CustomerEnvironmentContractTest#exposesGenericApplications()` case. Its
direct-dependency contrast intentionally failed `:schema-direct:compileTestJava` because
`EnvironmentContractTest` was not part of the API's main artifact.

The refined tracer also compiled an API `src/testFixtures/groovy` abstract Spock contract under Groovy 4 / Spock
2.4-groovy-4.0 and inherited it from a Schema test compiled with exactly those coordinates. The command
`./gradlew -p /private/tmp/klum-755-contract-tracer.b7xwUA :groovy-schema:test --console=plain --warning-mode=none`
passed. This is the accepted Layer 3 topology; its safety depends on matching selection, not on avoiding
Groovy/Spock fixtures.

## Accepted target contract

The Domain API project owns the reusable contract as a test fixture. The Schema makes the dependency visible and supplies
only realization-specific construction. The final Layer 3 build declares one selected Groovy/Spock pair at its root (or
an equivalent central location), and every Domain API, Schema, and Model project consumes that same selection:

```groovy
// domain-api/build.gradle
plugins {
    id 'java-library'
    id 'java-test-fixtures'
}

// schema/build.gradle
dependencies {
    implementation project(':domain-api')
    testImplementation(testFixtures(project(':domain-api')))
}
```

```groovy
abstract class EnvironmentContractTest<T extends Environment> extends Specification {
    protected abstract T environment()

    def "exposes generic applications"() {
        expect:
        environment().applications.shipping.name == 'shipping'
    }
}
```

```groovy
final class CustomerEnvironmentContractTest extends EnvironmentContractTest<CustomerEnvironment> {
    @Override
    protected CustomerEnvironment environment() {
        CustomerEnvironment.Create.With(/* Schema-specific setup */)
    }
}
```

The code is intentionally a test shape, not a public KlumAST API proposal. The generic base mentions only `Environment`
and other Domain API types. It does not assert or expose concrete Schema classes, generated `*_DSL` types, Builders,
Cluster transformations, lifecycle, serialization, or runtime internals.

The inherited Java/JUnit form exercised by the tracer remains a valid alternative. The maintainer's selected topology also
makes the inherited Groovy/Spock form above safe: its fixture and every consuming Schema compile against one shared
Groovy/Spock pair. If the build elects to test several versions, it runs this complete fixture separately for each pair;
it never puts a fixture compiled for one pair on another pair's classpath.

This is ordinary binary project test-fixture wiring. It neither depends on nor establishes the deferred source-level
Schema-composition workflow in #548. A later #548 decision may deliberately widen the topology, but no source dependency
is inferred from this fixture edge.

## Thin implementation slices

### CT-1 — Add a Domain API-local contract-test fixture

Create the `java-test-fixtures` capability in the selected Domain API repository/module. Add one generic contract for an
existing, meaningful API-only client behavior and one minimal test input hook. The fixture may use the build's selected
Groovy/Spock pair. Do not modify KlumAST plugins or publish a new artifact.

Acceptance: the fixture compiles with the Domain API's selected Groovy/Spock pair; its domain-facing signatures contain
Domain API types only; the API main artifact does not acquire test dependencies or Schema types.

Commit boundary: `Add API-owned contract-test fixture` with the fixture and its direct fixture compilation check.

### CT-2 — Execute the fixture from one Schema realization

Add the explicit `testImplementation(testFixtures(project(":domain-api")))` dependency and one concrete contract
subclass/adapter in a Schema project. Keep the Schema's own behavior tests separate. Verify inherited discovery and that
the Schema resolves the same Groovy/Spock pair as the Domain API fixture.

Acceptance: a focused Schema test identifies the shared assertion; deleting the fixture dependency makes the intended
compile failure observable in a dedicated wiring check, not as a permanent failing test.

Commit boundary: `Run Domain API contract in Customer Schema` with the dependency and executable realization coverage.

### CT-3 — Keep the Layer 3 test pair aligned

Keep one Groovy/Spock selection aligned across every participating Domain API, Schema, and Model module and provide
proportionate test/build evidence that catches a mismatch. The exact local mechanism remains the owning build's concern.
If the project claims compatibility with several Groovy generations, execute the complete API/Schema fixture once per
generation in isolated builds; never reuse compiled Groovy/Spock fixture output between them.

Acceptance: all participating modules resolve the same Groovy and Spock coordinates; the focused shared-contract test
passes. Any optional 3/4/5 matrix compiles and runs fixtures independently per generation. The production artifact
remains compiled once and its published metadata has no lane-specific test dependency.

Commit boundary: `Align Domain API contract-test Groovy selection` with the selection evidence and tests together.

### CT-4 — Decide whether a domain-local convention earns extraction

Only after at least two API-to-Schema mappings repeat the exact dependency/configuration shape, decide whether the owning
domain repository should add a small convention. Its inputs must be explicit API project/capability references; it must
not infer them from KlumAST types. A cross-repository Gradle convention requires evidence from independent consumers and
an accepted `blackbuild/gradle-conventions#1` plugin boundary.

Acceptance: a TestKit fixture proves opt-in wiring and non-selection leaves a Schema unchanged. No KlumAST Schema/Model
plugin changes are made unless a separate issue demonstrates framework-owned behavior.

Commit boundary: separate owner-specific convention work; it is not part of #755's first delivery.

## Compatibility, documentation, and release placement

- The public/runtime/generated API surface is unchanged. Therefore no KlumAST migration page, user documentation, or
  `CHANGES.md` entry is required by this plan.
- The Domain API repository documents the selected test contract beside its own development guidance. It should call out
  fixture consumption and the chosen JUnit/Spock adapter shape without representing it as a KlumAST feature.
- #755 remains a future, unmilestoned, ready-for-agent application-owned testing item. It is non-blocking for #454 and
  #753.
- A published Domain API fixture or artifact introduces Maven metadata, version alignment, and consumer compatibility
  questions; it requires a follow-up design rather than silently extending this local-build plan.
- #548 is a related, deferred post-4.1 source-level Schema-composition design. It is not a blocker for binary project
  test-fixture wiring and this plan does not infer its contract.

## Risks and open questions

| Risk or question | Containment / decision needed |
| --- | --- |
| A Schema needs several API contracts. | Keep every `testFixtures(project(...))` declaration explicit; do not infer an arbitrary primary API. |
| The Domain API and Schema are in different repositories. | Start with a tested, versioned test-support artifact only after a separate publication decision; Gradle project fixtures do not cross repository boundaries by themselves. |
| Teams configure different Groovy/Spock versions by module. | Reject or diagnose the mismatch before fixtures compile. A shared Groovy/Spock fixture is supported only when all participating modules select the same pair. |
| One contract needs application lifecycle or Cluster behavior. | Split it: generic completed-model assertions may be reusable, but lifecycle and Cluster semantics remain owned by their existing issues and are outside #755. |
| A new plugin appears convenient. | Require repeated mappings plus TestKit evidence first. `blackbuild/gradle-conventions#1` and engineering-baseline are not currently authorized owners. |

## Issue-to-slice map

| Item | Relationship |
| --- | --- |
| #755 | Governs the accepted fixture-wiring decision and CT-1 through CT-3. |
| #454 | Documents the Layer 3 role/dependency boundary; no implementation dependency. |
| #753 | Owns interface Cluster support/rejection; no implementation dependency. |
| #548 | Deferred source-level Schema composition; related only, not a blocker or implied dependency. |
| ADR 0011 / #455 | Governs all Groovy 3/4/5 isolation and evidence. |
| ADR 0017 / #658 | Contrast for KlumAST-owned public test support; it does not own this application fixture. |
| `blackbuild/gradle-conventions#1` | Future shared convention decision only; not a blocker for explicit local fixture wiring. |
