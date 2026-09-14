# Published Template test support

Date: 2026-09-13

Status: Accepted

Tracking issue: [#658 — Provide published Template test support without exposing TemplateManager](https://github.com/klum-dsl/klum-ast/issues/658)

Implementation plan: [ADR 0017 implementation plan](../implementation/adr-0017-published-template-test-support.md)

Implementation status: Planned. No implementation slice is delivered by this decision record.

Parent decisions:

- [ADR 0004 — AsBuilder composition and Builder-producing factory projections](0004-asbuilder-composition-protocol.md)
- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)
- [ADR 0016 — Template creation and scoped application](0016-template-creation-and-scoped-application.md)

Amends [ADR 0013 — Versioned user documentation and Javadocs](0013-versioned-documentation-and-javadocs.md): after this feature is released, its exact-version API tree includes the new public test-support module in addition to the existing behavioral modules. Historical release trees remain unchanged.

## Context

KlumAST 3.x tests could arrange ambient Template state through `TemplateManager`. In 4.x that manager, its current-template map, and its set/get operations are runtime internals. A Schema Developer migrating a large Spock suite nevertheless needs materialized Templates to remain active through `setup`, a feature, and `cleanup`; wrapping each body in `Foo.Template.WithAll(...)` is correct but repetitive.

The runtime already owns the necessary semantics. `TemplateManager.doWithTemplates` uses a thread-local manager, snapshots the full preceding map, overlays supplied values, restores the exact snapshot in `finally`, and deregisters an empty manager. `BoundTemplateHandler` and generated `Foo_DSL.TemplateScope` deliberately expose only callback-scoped application. They cannot hold a scope across test-framework lifecycle methods. The local `java-test-fixtures` variants contain runtime test helpers and are neither a suitable public contract nor a Maven Central delivery shape.

The test need is not a request to change Template definition, recipe replay, Builder lifecycle, ownership, serialization, or ordinary application behavior. ADR 0004 remains authoritative for those semantics, and ADR 0016 remains authoritative for generated `Foo.Template.With`/`WithAll` application.

## Decision

### Publish one dedicated test-support artifact

Add a `klum-ast-test-support` Java-library subproject and publish it as `com.blackbuild.klum.ast:klum-ast-test-support`. It is a normal Maven product with sources, Javadocs, signing, BOM alignment, release-product verification, and an isolated exact-version API page. It is not a `java-test-fixtures` capability and it publishes none of the runtime's test fixture classes.

The module owns one public package, `com.blackbuild.klum.ast.testsupport`, and one initial public type with an explicit empty scope constructor and additive value operations:

```java
public final class TemplateScope implements AutoCloseable {
    public TemplateScope();

    public TemplateScope with(Object... templates);

    public TemplateScope with(Collection<?> templates);

    @Override
    public void close();
}
```

Construction opens an empty current-thread frame and snapshots the preceding effective Template mapping. `with` defensively snapshots materialized Templates and adds their inferred model target types to that frame, returning the same scope for ordinary Java chaining. `with(Collection<?>)` is equivalent to supplying the collection's elements to `with(Object...)`; it is deliberately separate so a list is never mistaken for one Template. Splitting lifetime from mapping makes a Spock field useful: `setup` can install a base set, while a feature can add or override its own Templates without opening a second top-level test fixture. The public constructor, rather than a static `open` factory, makes lifetime visible at each declaration and works directly with Java's resource syntax and a Spock field. The API neither creates anonymous Templates nor exposes registry keys, values, or mechanics. Callers create materialized recipes through the existing `Foo.Create.Template.With(...)` API, whose model type supplies the ordinary target selection. The public signature contains only JDK types, so using the scope does not expose runtime implementation types or force a Groovy version through the support artifact's interface.

This is a test-support API, not a new production-runtime construction API. Its Javadocs and user guidance keep it in a test dependency configuration. The support artifact has an `implementation` dependency on `klum-ast-runtime` for execution. A consumer that uses only the ordinary Schema plugin receives the support coordinate automatically in `testImplementation`, alongside the plugin's existing test baseline; it is never added to a production configuration. Direct Java/Groovy consumers that do not apply the Schema plugin declare the BOM, normal runtime, and test-support coordinate together so the supported runtime remains explicit. The Schema-plugin provision is independent of whether the consumer keeps the default Spock support: the scope is also usable from Java/JUnit tests. The BOM constrains the new coordinate just as it constrains every Java-library subproject.

### Make the scope restoration contract explicit

Opening a scope captures the complete preceding Template mapping for the calling thread. Each scope retains its own additive frame; the effective mapping is recomposed from active frames in construction/nesting order, with a later frame winning for one repeated target type. Within one `with` invocation and across successive calls on one scope, a later supplied Template for the same inferred target type wins. A `with` call on an outer frame therefore does not accidentally leapfrog an already-active inner frame. Closing the still-active top-of-stack scope restores that exact preceding mapping. Nested scopes therefore restore their direct parent on inner close and the pre-existing state on outer close. Scope state never propagates to another thread.

`close` is idempotent after a successful close. `with` after close, closing on a different thread, or closing a still-active scope out of nesting order fails with `IllegalStateException` and leaves the calling thread's Template state unchanged. This turns misuse that could corrupt a thread-local stack into an observable test failure. Spock `@AutoCleanup` is the primary lifecycle teardown: it runs after `cleanup`, so the scope remains active for cleanup work even when a feature fails. Java try-with-resources remains an equivalent Java convenience. A failed constructor or `with` leaves no partial registration.

Spock's supported [`@AutoCleanup`](https://spockframework.org/spock/docs/2.4/extensions.html#_autocleanup) closes an annotated field after the specification's `cleanup` method. The documented pattern uses a non-`@Shared` field; `@Shared` is invalid guidance because a Template scope is thread-local and must have per-feature lifetime. The core Java artifact deliberately does not take a Spock dependency merely to inspect annotations, so it cannot reliably reject every same-thread `@Shared` use. A later optional Spock extension may validate that convention; no interceptor or rule belongs in the first artifact. Projects that need shared fixture plumbing can own one on top of this narrow lifetime token.

The implementation must delegate frame creation, scoped overlay, and restoration to a narrow runtime-internal test-support bridge. That bridge may use same-package `TemplateManager` state, but only behind the constructor, `with`, and `close` facade operations. It has no consumer-facing `TemplateManager`, registry getter/setter/clear/add API, Builder/session/phase handle, or public-facade descriptor leak. The runtime's existing qualified export of its internal package additionally admits the named test-support module; consumers read only the test-support module. Classpath encapsulation remains conventional rather than a promise to support runtime-internal imports.

### Preserve existing Template behavior and compatibility selection

An active test-support scope supplies the same current-template mapping that existing scoped application supplies. Roots and owned Builder creation continue to apply Templates through the existing runtime path; recipe replay, Template identity, lifecycle ordering, `LINK` rejection, serialization, and Jackson behavior are unchanged. The scope is not a Template definition scope and it must not alter the thread-local Template-definition depth.

The artifact is one Java 17 production artifact and publishes once. It selects no Groovy dependency in its POM. Its Groovy/Spock tests are recompiled and run in the repository's Groovy 3, 4, and 5 lanes under ADR 0011; published-consumer fixtures select their matching Groovy dependency themselves. Spock lifecycle use is the primary test-support contract, while Java try-with-resources is the portable Java convenience pattern.

## Consequences

- Migrated test suites can hold ordinary materialized Templates through a framework lifecycle without importing runtime internals or duplicating callback wrappers.
- The public surface remains deep: `TemplateScope` describes lifetime only, while recipe construction and all mutable registry mechanics remain behind the support/runtime boundary.
- A seventh behavioral Maven artifact becomes part of each 4.1 product release, BOM, public-product resolver, Javadoc renderer, and release evidence. It does not change historic 4.0 documentation or artifacts.
- Schema modules receive the support artifact on their test classpath without a manual coordinate; production classpaths and published Schema metadata remain unchanged.
- Consumers retain Groovy 3/4/5 selection control. The support artifact's POM must not make its Groovy-3 compilation baseline a transitive consumer choice.
- The first release establishes only value-based, already-materialized Template scopes. Interceptors, JUnit extensions, Spock extensions, anonymous-map creation helpers, explicit alternate target mapping, and cross-thread propagation remain project-local or future separately designed work.

## Rejected alternatives

### Publish `java-test-fixtures`

Gradle test fixtures are an internal test-sharing mechanism, not a deliberately versioned Maven Central API. Publishing them would expose fixture helpers, produce a variant rather than the required coordinate, and risk pulling test outputs compiled for a different Groovy/Spock lane. It is therefore rejected.

### Make `TemplateManager` or its registry operations public

This would turn a mutable thread-local implementation detail into a client extension seam. It would invite unbalanced mutation, state inspection, and lifecycle coupling while bypassing the restoration contract. It is rejected in favor of one AutoCloseable lifetime capability.

### Add a general runtime `TemplateScope` API

The primary user is test infrastructure and the feature belongs in a test dependency. Putting this name in the runtime's ordinary public API would imply a production configuration/lifecycle capability and blur the existing generated scope APIs. The runtime retains only a qualified internal bridge.

### Add a callback overload or framework-specific extension first

`Foo.Template.WithAll` already provides callback scoping. A callback cannot cross `setup`/feature/`cleanup`, while a Spock/JUnit extension would commit KlumAST to a framework integration surface before its minimal lifecycle capability is proven. Both are rejected for the first slice.

## Acceptance boundary

Implementation is complete only when a separately published support coordinate resolves with the normal runtime through the BOM and is automatically available in a Schema module's `testImplementation` configuration; its public/Javadoc/JPMS surface contains only the scope type; Java and Spock use restore exact nested state after success and failure; the existing Template behavior remains covered; and clean external Groovy 3, 4, and 5 consumers resolve and execute the artifact without internal imports or cross-lane test output.
