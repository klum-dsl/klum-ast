# ADR 0017 implementation plan: Published Template test support

This implementation record delivered [ADR 0017](../adr/0017-published-template-test-support.md) for [#658](https://github.com/klum-dsl/klum-ast/issues/658). Its TS-1 through TS-4 slices are implemented. It adds a 4.1 public test artifact; it does not implement a Spock/JUnit extension, change Template semantics, or publish any existing test fixture variant.

## Confirmed starting behavior and failure path

| Concern | Current seam | Confirmed behavior or limitation |
| --- | --- | --- |
| Scoped Template application | `klum-ast-runtime` `TemplateManager.doWithTemplates` | The internal thread-local manager copies the full previous map, overlays a map, restores it in `finally`, and removes an empty manager. The behavior is already nesting-safe but only callback-scoped. |
| Generated public application | `BoundTemplateHandler`, `GeneratedTemplateSupport`, `Foo_DSL.TemplateScope` | `Foo.Template.With`/`WithAll` offer callbacks only. The list overload accepts materialized Templates and the map overload creates anonymous recipes; neither can survive test setup and cleanup boundaries. |
| Template recipe mechanics | ADR 0004 `FactoryHelper`, Template companion, Builder lifecycle | A Template remains an explicitly marked recipe replayed into fresh Builders. Current-template selection must not change definition scope, materialization, lifecycle, ownership, serialization, or Jackson boundaries. |
| Internal test sharing | `klum-ast-runtime` and `klum-ast` `java-test-fixtures` | Fixture source is deliberately recompiled per Groovy lane and contains runtime test helpers. It is not a consumer artifact and must not become one. |
| Publication topology | `settings.gradle`, `klum-ast-bom`, root protected product/release fixtures | Every `java-library` subproject is constrained by the BOM and publishes through the guarded complete product. Existing public-product resolvers and exact-version Javadoc allowlist enumerate the present artifacts explicitly. |
| Compatibility contract | ADR 0011 and `klum-ast.multigroovy-conventions.gradle` | Production is one Java 17/Groovy-3 artifact; Groovy test source/fixtures must compile independently in G3/G4/G5. Published consumers choose their matching Groovy dependency. |

The 3.x-style failure is therefore not missing Template semantics. A migrated test that imports or mutates `TemplateManager` crosses the public-runtime boundary; a correct `DeliveryData.Template.WithAll([baseline]) { ... }` wrapper cannot cover a Spock instance's setup, feature, and cleanup as one lexical callback. The current workaround is safe but repetitious.

## Target contract and topology

### Consumer contract

The sole initial handwritten public type is `com.blackbuild.klum.ast.testsupport.TemplateScope`. It is a final `AutoCloseable` lifetime token with one empty public constructor, additive `with(Object...)` and `with(Collection<?>)` operations, and no static factory. This matches the existing `BoundTemplateHandler` convention for an object that owns context, rather than `KlumJacksonInput`'s static factories for immutable input values:

```java
import com.blackbuild.klum.ast.testsupport.TemplateScope;

try (TemplateScope ignored = new TemplateScope().with(baseline)) {
    DeliveryData delivery = DeliveryData.Create.With();
    assert delivery.region().equals("eu-central");
}
```

The primary Spock use is framework-managed setup/feature/cleanup lifetime, not a try-with-resources block:

```groovy
@AutoCleanup
TemplateScope templates = new TemplateScope()

def setup() {
    templates.with(baseline)
}

def "adds a feature-specific Template"() {
    given:
    templates.with(options)

    expect:
    DeliveryData.Create.With().options.enabled
}
```

`baseline` and `options` are existing materialized Templates, normally produced with `DeliveryData.Create.Template.With { ... }`. The empty constructor preserves the caller's effective mapping as a defensive snapshot. `with(Object...)` snapshots its supplied values and infers their model target types using the existing materialized-Template convention; `with(Collection<?>)` performs the same operation for a collection. In either form, later values for one inferred target replace earlier values in this scope. The token neither exposes Template identity nor retains the input collection. The API is not an anonymous-recipe factory; callers continue to use generated Template creation for that concern. Import `spock.lang.AutoCleanup`; it invokes `close` after `cleanup`, so the active Templates remain available through cleanup. Do not mark the field `@Shared`.

| Operation | Required result |
| --- | --- |
| `new TemplateScope()` | Capture the prior current-thread mapping as the frame's restoration target and make an empty frame active. |
| `with(Object...)` / `with(Collection<?>)` | Defensively snapshot and add supplied materialized Templates to this frame by inferred target type; later values in this frame replace earlier values for the same target type. |
| multiple fields/nested scopes | Recompose active frames in construction order; a later inner field wins only for repeated target types. |
| `close` in LIFO order | Restore exactly the map captured by that scope; remove the manager when that captured map is empty. |
| close after a body failure | Spock `@AutoCleanup` performs the primary restoration after cleanup; Java try-with-resources is the equivalent Java route. |
| repeated `close` | No-op after a successful close. |
| close from another thread or out of order | Throw `IllegalStateException` without changing any active mapping. |
| another thread while a scope is active | See no scope state unless it opens one for itself. |

### Artifact and module boundaries

| Layer | Owner and content | Dependency/export rule |
| --- | --- | --- |
| Public test artifact | New `klum-ast-test-support`, artifact `com.blackbuild.klum.ast:klum-ast-test-support`, package `com.blackbuild.klum.ast.testsupport`, module `com.blackbuild.klum.ast.test.support` | `java-library`, sources/Javadocs/signing/publication. Exports only `com.blackbuild.klum.ast.testsupport`; public descriptors use JDK types only. |
| Runtime bridge | New dedicated test-support bridge type in `com.blackbuild.klum.ast.runtime.internal` | Contains a minimal open/restore capability used only by support and can use same-package TemplateManager state without creating registry accessors. Runtime's existing qualified export of this package additionally admits `com.blackbuild.klum.ast.test.support`; it must not re-export `TemplateManager`. |
| Runtime internals | `TemplateManager`, `BoundTemplateHandler`, Template recipe/lifecycle code | Continue to own state and semantics. No getter/setter/add/clear registry method or Builder/session/phase API becomes reachable from test-support's public package. |
| Build/product | `settings.gradle`, BOM, Schema plugin, root release graph, public-product consumers | Include a normal project and Maven publication. The BOM adds its version constraint automatically through `java-library`; the Schema plugin adds the coordinate only to `testImplementation`; public Maven/product verification names the new coordinate explicitly. |
| Documentation product | versioned docs renderer and ADR 0013 | Render a seventh current-release Javadoc tree for the public support type. Do not modify historical release availability lists. |

`klum-ast-test-support` declares `implementation project(':klum-ast-runtime')`: the runtime is required to execute the scope but is absent from the scope's public type signatures. Consumer examples and external fixtures also declare the normal runtime artifact explicitly in test scope, aligned by `platform("...:klum-ast-bom:<klum-version>")`; this makes the normal Schema runtime dependency visible and verifies both coordinates resolve together. The POM must not publish a Groovy API dependency.

## Compatibility and non-goals

- The support artifact is Java 17 and has one production artifact. It does not create Groovy-specific variants or publish Groovy 3 transitively. Groovy 3 uses `org.codehaus.groovy`; Groovy 4/5 consumers select their own `org.apache.groovy` coordinates as ADR 0011 requires.
- Existing generated `Foo.Template.With` and `WithAll` remain supported and unchanged. They are still the only supported production callback form and the migration workaround until this artifact ships.
- `TemplateScope` accepts already-materialized Templates and infers their normal target classes. It neither adds a global mutable registry API nor claims cross-thread propagation, Template creation, framework interception, automatic teardown, Template identity inspection, explicit alternate target mapping, or new lifecycle semantics.
- The Template-definition depth remains independent. Opening test support inside Template creation must not make child Builders Template-mode by itself; Template creation inside an active support scope retains its existing semantics.
- The test artifact must not depend on `testFixtures(project(':klum-ast-runtime'))`, copy those classes, or make test fixture output part of a published or external-consumer classpath.

## Dependency-ordered tracer slices

### TS-1 — Establish the hidden restoration bridge and public lifetime token

**Seams:** `klum-ast-runtime` TemplateManager implementation, its JPMS descriptor, new test-support source/module, `settings.gradle`, and standard publication configuration.

**Work:** factor the existing snapshot/overlay/restore behavior behind one runtime-internal bridge that can create an empty frame, derive the normal target type for supplied materialized Templates, update one active frame, and close a frame without exposing manager state. Add the new Java-library module, its module descriptor, and the final public `new TemplateScope()` plus fluent `with(Object...)` and `with(Collection<?>)` operations. Make input snapshots defensive; recompute effective mappings in construction/nesting order; enforce same-thread and LIFO closure; make successful repeat close idempotent. The bridge is the only test-support reference to internal runtime code.

**Acceptance:** Java-focused tests prove an empty scope followed by either `with` form activates supplied materialized Templates, empty prior state is removed on close, nested shadowing restores the exact outer value, and a thrown body leaves no leakage. A duplicate inferred target in one varargs or collection call deterministically leaves the later Template active. A two-field fixture proves field construction order, rather than `with` call order, controls repeated target precedence. Focused negative tests prove cross-thread/out-of-order close and `with` after close fail without mutation. Source/Javadoc/bytecode and JPMS inspection prove that the public support descriptor contains no `TemplateManager`, `BoundTemplateHandler`, Builder, map, or mutable registry type; the runtime internal package is qualified only to the compiler, Jackson, and support implementation modules, never to a consumer module.

**Commit boundary:** one vertical commit containing runtime bridge, module topology, public facade, and the focused scope contract. Do not refactor unrelated Template or test-fixture code.

### TS-2 — Prove real Java and Spock lifecycle consumption

**Seams:** new module's Groovy/Spock tests, a minimal `@DSL` fixture, and existing Template behavior tests in `klum-ast` where behavior must remain guarded.

**Work:** add a Java try-with-resources convenience consumer that creates an empty scope, calls both `with` forms, creates a normal DSL root, and proves state disappears after the block, including an exception path. Make the new `TemplateScopeTest` Spock class with `@Issue("658")` the primary acceptance: a non-`@Shared` `@AutoCleanup` field creates an empty scope, `setup` installs a base Template, and a feature adds a Template for its own cluster. Prove the scope is active during cleanup and `@AutoCleanup` restores the following feature's clean state after a failure. Cover two named scope fields: a general field declared first and a specialized field declared later; Spock's reverse declaration-order cleanup must close them LIFO and the specialized frame wins on a repeated type. Do not add a KlumAST interceptor or rule; a future optional Spock extension may reject `@Shared`, while a project-local interceptor remains an optional consumer adaptation. Include nesting, same-type shadowing, duplicate targets within one call, non-propagation to a worker thread, and a scope active during ordinary root/owned Builder creation. Reuse existing `TemplatesSpec`, `BoundTemplatesSpec`, and Template-companion coverage instead of duplicating recipe/serialization/ownership cases.

**Acceptance:** Spock cleanup executes after a deliberately failing feature/body while the field remains active; `@AutoCleanup` then restores exact prior state, observed by a subsequent test. A two-field fixture proves independent template clusters, deterministic precedence, and reverse-order teardown. A focused `@Shared` documentation/extension-boundary test records that the core artifact neither promises to detect nor support it. Java's resource block has equivalent convenience proof. Existing Template recipe replay, definition-scope, lifecycle, and ownership tests remain green, showing the bridge does not redefine them. Every new Spock class/method carries #658; the user-facing happy path is a `TemplateScopeDocumentaryTest` (or an appropriately scoped documentary method) marked `@Tag("documentary")` and `@See` to the final Templates documentation section.

**Commit boundary:** real-consumer tests and any tightly coupled bridge correction land together. Documentary test and user-facing prose may be deferred to TS-4 only if it does not leave a released public feature undocumented.

### TS-3 — Lock publication, BOM, JPMS, and external Groovy consumers

**Seams:** BOM/project topology, `klum-ast.multigroovy-conventions.gradle`, Javadoc/release-product allowlists, and a clean external Gradle consumer fixture.

**Work:** apply the multi-Groovy test convention to the new module so its Groovy tests compile separately for 3/4/5 and `check` includes `verifyTestLaneIsolation`. Add a fixture that publishes the normal runtime, annotations/compiler as needed, BOM, and the new support artifact to an isolated repository (or uses a dedicated publication fixture), then runs three clean Gradle consumers. Each consumer declares the BOM, runtime, test-support, and matching Groovy/Spock version; it compiles and runs the same Java/Spock scope usage without project dependencies, `mavenLocal`, composite builds, or test fixture variants. Verify POM metadata: support depends on runtime for execution but has no Groovy selector and no fixture capability.

Add the project to `settings.gradle`; let `klum-ast-bom` constrain it through its existing Java-library convention, then assert its constraint in generated BOM/POM evidence. Make the Schema plugin add the coordinate only to `testImplementation`, with a plugin test proving the artifact/group and version-aligned dependency declaration; this convenience remains available when consumers select a non-Spock test framework. Add the coordinate to `release/consumer` and `release/maven-consumer` complete-product resolvers. Add its Javadocs to `VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS`, renderer/tracer fixture expectations, and amend the ADR 0013 implementation record/allowlist evidence for the seventh current-release API tree. Update JPMS package/module boundary tests to include the new module, its public export, and the runtime's qualified bridge export.

**Acceptance:** `:klum-ast-test-support:test`, `:klum-ast-test-support:groovy4Tests`, `:klum-ast-test-support:groovy5Tests`, and module `check` pass without cross-lane output. Each clean external consumer resolves the published-shaped support and normal runtime artifacts and executes scope restoration for its selected Groovy generation. The Schema-plugin test proves the support coordinate is present only in `testImplementation` and has no hard Spock coupling. The BOM and release resolver contain exactly the intended new artifact; the renderer has its support Javadoc tree and still has no BOM Javadocs. JPMS named-module proof sees no public access to runtime internals.

**Commit boundary:** publication topology/BOM/JPMS contract first; consumer fixture and renderer/release allowlists in a dependent commit if that keeps each reviewable and green. Never publish a test-fixture variant as an interim solution.

### TS-4 — Publish migration and release guidance

**Seams:** `docs/user/Templates.md`, `docs/user/Migration.md`, `CHANGES.md`, current navigation only if a page is added, public artifact/Javadocs, and ADR/implementation status.

**Delivered:** Templates, migration, and testing guidance now distinguish the automatic Schema-plugin
`testImplementation` provision from the explicit BOM/runtime/test-support declaration needed by direct Java/Groovy
consumers. They document the empty-field, setup-base, feature-addition Spock example and Java try-with-resources
convenience path; explain materialized recipes, current-thread scope, `@AutoCleanup`, both `with` forms, inferred target
replacement, non-`@Shared` fields, multiple field order, and independent clusters; and link the documentary test. The
4.1 changelog identifies this as test support rather than a production DSL API. User guidance replaces
`TemplateManager` migration advice while retaining `Foo.Template.WithAll` as the no-extra-artifact alternative. ADR 0017
remains Accepted, and this implementation record is Implemented because feature, publication, external-consumer,
documentation, and release-facing checks are complete.

**Acceptance:** docs render/link checks pass; every dependency coordinate is BOM-aligned and test-scoped; search finds no user instruction that treats `TemplateManager` as a supported migration API; the concise Java and Groovy examples match the documentary test. The release note, public Javadocs, documentation navigation, BOM, and product-resolver evidence all name the same support coordinate.

**Commit boundary:** documentary test, current user/migration docs, changelog, and release-facing inventory update form one final user-facing commit. Do not alter historical `wiki/` content.

## Executable acceptance map

| Contract | Primary test or fixture | Required proof |
| --- | --- | --- |
| Public facade | `TemplateScopeTest` plus Java consumer test | final `AutoCloseable`, one public empty constructor and fluent `with(Object...)`/`with(Collection<?>)`, no static factory, manager/registry methods, map parameter, or internal descriptor references |
| Exact restoration | focused scope tests | empty-frame creation, additive value snapshots, outer/inner restoration, same-type shadowing, duplicate target replacement, idempotent close, exception cleanup |
| Thread boundary | focused scope tests | another thread cannot observe the scope; wrong-thread close and non-LIFO close reject without state corruption |
| Existing Template behavior | existing Template/companion/Builder tests | recipe replay, definition scope, lifecycle, ownership, `LINK`, serialization, and Jackson results remain unchanged |
| Java consumption | try-with-resources test | convenience usage: a materialized Template is active in the block and absent after normal/exceptional exit |
| Spock consumption | lifecycle `TemplateScopeTest`, two-field fixture, and documentary test | primary non-shared AutoCleanup field supports setup base and feature additions, remains active through cleanup, then restores state; independent fields close in reverse declaration order |
| Artifact/POM/BOM | generated POM/BOM inspection and isolated publication repository | normal support coordinate resolves with runtime; no Groovy selector or fixture capability leaks |
| Groovy consumers | three clean external Gradle consumer runs | matching Groovy 3, 4, and 5 consumer compiles and runs the published-shaped artifact set |
| JPMS | package/module boundary test | only support package exports publicly; runtime bridge remains in the internal package qualified to implementation modules; consumer cannot name runtime internals |
| Docs/release | renderer, docs links, release resolver checks | seventh current API tree, support coordinate in complete product, documentation/test/changelog alignment |

## Risks and remaining decisions

| Risk | Control |
| --- | --- |
| A facade accidentally exposes a map or runtime implementation type. | Freeze the two-operation/one-token descriptor in reflection, Javadoc, bytecode, and JPMS tests; use defensive value snapshots at both facade and bridge boundaries. |
| Out-of-order close silently restores the wrong state. | Maintain a per-thread scope stack and reject non-top close before any mutation; prove outer/inner shadowing and rejection cases. |
| A Java classpath user reaches the bridge despite JPMS qualified exports. | Keep the bridge in the existing `internal` package, omit it from public docs/inventory, and treat its classpath visibility as unsupported implementation detail; the public facade has no bridge descriptor. |
| The new module pulls Groovy 3 into a Groovy 4/5 consumer. | Keep production Java-only, runtime as `implementation`, and assert support POM has no Groovy dependency; run clean selected-Groovy consumer fixtures. |
| Publication plumbing omits the artifact or its Javadocs. | Make release resolver, BOM/POM, exact-version renderer allowlist, tracer, and JPMS inventory checks explicit in TS-3. |
| A framework integration expands the API prematurely. | Do not ship extension/interceptor types. A future proposal must build on this lifecycle token and separately decide framework ownership and teardown behavior. |
| A `@Shared` field makes Template state spec-wide. | Document it as unsupported; the Java-only core preserves no false detection guarantee. A future optional Spock extension may validate the field convention without changing the core scope API. |
| Multiple scope fields produce call-order-dependent defaults. | Retain a frame per scope and recompose by construction/nesting order. Document general-before-specialized declaration and prove reverse `@AutoCleanup` teardown. |

No core product decision remains before implementation: the artifact name, initial package/type, empty-frame/additive value contract, restoration/misuse rules, runtime bridge boundary, publication shape, and Groovy-consumer evidence are settled here. An optional Spock validator/interceptor, anonymous-recipe helpers, and explicit alternate target mapping remain deliberately deferred; each requires a separate decision rather than being inferred from #658.
