# #829 — root recipe and invocation-local overlay: decision brief

Date: 2026-11-06. Status: **investigation evidence; core choices subsequently confirmed by the maintainer**.

The maintainer selected closure-only overlays and recipe → overlay → one root PostApply. The accepted contract is
recorded in [ADR 0004](../adr/0004-asbuilder-composition-protocol.md); the
[implementation plan](issue-829-root-recipe-overlays.md) is authoritative for the next slices and the separately proposed
FromStruct follow-on. The original investigation and its limits are retained below.

Authority: [current issue #829](https://github.com/klum-dsl/klum-ast/issues/829), read with its comments on this date
(open, no comments). Primary use case is ScHelm loading a compiled application recipe with a completed external
`OrderEnvironment` LINK. No ScHelm checkout, integration, or external qualification was inspected.

Refreshed inspection base: `eec0a64a5357f69082711e92bf7a0aefa93538fc` (`origin/master`).
This is a design-only investigation: no production changes, public API additions, remote mutations, or implementation
admission. This brief and its probe are design evidence, not a feature implementation or release promise.

## Result and decision matrix

The existing configuration Consumer seam can execute a compiled recipe and an overlay between one root's `PostCreate`
and `PostApply`. That suffices to bind a completed LINK before every graph phase, materialization, required validation,
and verification. It cannot supply context to that root's initializer or `PostCreate`. Recipe-first also cannot supply
context to code and immediate child callbacks that have already executed in the recipe.

| Choice | Recommendation | Evidence and limit | Decision state |
| --- | --- | --- | --- |
| Overlay form | Closure only initially | Natural trailing DSL closure, three new signatures; no primary use case requires named maps | Selected: closure only |
| Input order | Recipe, then overlay, inside one preparation | Matches issue's candidate; ordinary scalar/LINK assignment in overlay replaces earlier assignment; graph phases see context | Selected: recipe → overlay → PostApply, with the stated visibility limit |
| Conflict contract | Ordinary ordered Builder calls | No special merge, reserved context fields, or permanent priority; collection adders append and deferred actions/callbacks can run later | Retain ordinary Builder semantics under the selected ordering |
| Source family | Direct compiled DelegatingScript class and existing classpath marker | Required by assignment and issue; common runtime route exists | Bounded scope; no File/URL/text extension needed here |
| Factory naming | Explicit `From` / `FromClasspath` overloads on ordinary root Factory | Does not expose Builder/session/materialization internals | Recommended, needs generated surface qualification |
| Regular Script | Reject overlay use before running or constructing the script | A regular Script is an opaque completed-root producer under ADR 0004 | Required boundary |
| Nested independent root | Preserve rejection | `PhaseDriver.withBuilderLifecycle` rejects while a root session is active | Existing boundary; “nested invocation isolation” cannot mean admitting nested materialization |
| Shared closure | Clone the overlay before setting delegate/strategy | Existing `applyClosure` mutates caller closure; shallow clone isolates delegate state only | Recommended implementation requirement |

The first two choices were product policy and required the maintainer decision now recorded above. Under
`klum-plan-design` this is **B: extend/clarify ADR 0004's root input boundary**, preserving its active-session protocol.
ADR 0004 and the dedicated implementation plan now record the extension without reopening ADR 0003.

## Proposed public syntax (not available today)

```groovy
@DSL
class OrderEnvironment {
    String region
}

@DSL
class OrderApplication {
    @Key String name
    @Required @Field(FieldType.LINK) OrderEnvironment environment
    String image

    @Default void chooseImage() {
        image ?= "orders:${environment.region}"
    }
}

def selectedEnvironment = OrderEnvironment.Create.With(region: 'eu')
def application = OrderApplication.Create.From(OrderApplicationScript) {
    environment selectedEnvironment
}
def discovered = OrderApplication.Create.FromClasspath {
    environment selectedEnvironment
}
def explicitLoader = OrderApplication.Create.FromClasspath(modelLoader) {
    environment selectedEnvironment
}
assert application.environment.is(selectedEnvironment)
```

The separately compiled `OrderApplicationScript` is a `DelegatingScript`; its body contains ordinary configuration such
as `image 'orders:stable'`, not another `OrderApplication.Create` root. Java-facing method shapes would be:

```java
T From(Class<? extends DelegatingScript> recipe, Closure<?> overlay);
T FromClasspath(Closure<?> overlay);
T FromClasspath(ClassLoader loader, Closure<?> overlay);
```

Generated `OrderApplication_DSL.Factory` methods return `OrderApplication` and annotate each final closure parameter with
`@DelegatesTo(OrderApplication_DSL.Builder)` and `DELEGATE_ONLY`. The same overloads serve keyed and unkeyed roots; there
is no explicit key override, and the overlay cannot change an already constructed root's key. The root's effective
concrete type remains the Factory-selected type/default implementation.
The recipe does not choose a runtime subtype. Static Java/Groovy rejects a regular Script class for the direct overload;
dynamic/erased Java callers and classpath discovery still need an assignability check with an actionable diagnostic.

If named-map support is required, preserve its established meaning: each entry invokes a one-argument Builder operation
in iteration order. Candidate signatures would put `Map<String, ?>` first, before recipe/loader, with optional closure;
maps run after the recipe and before the closure. For example:

```groovy
OrderApplication.Create.From(environment: selectedEnvironment, OrderApplicationScript)
OrderApplication.Create.FromClasspath(environment: selectedEnvironment)
```

That adds map-only and map-plus-closure families and overload-resolution/metadata work. It is not `FromMap`, `copyFrom`,
structured input, or a dictionary of Model properties. ADR 0022's current static named-map metadata explicitly covers
fixed `Create.With` and relationship creators only; adding map overlays must not silently extend that policy.

## Observed root Factory contract

`KlumFactory.From(Class<? extends Script>)` accepts both routes. A DelegatingScript is freshly instantiated and dispatched
against the selected root Builder. The script class's simple name is the key for a keyed root; an unkeyed root receives
null. A regular Script runs through `InvokerHelper.runScript`, and its result must be an instance of the selected type.
It can return an appropriate completed subtype, unlike a DelegatingScript that configures the selected Builder.

`FromClasspath()` uses the current thread context class loader; its explicit-loader sibling uses the supplied loader.
Both read `META-INF/klum-model/<effective-model-binary-name>.properties`, key `model-class`, load the compiled class, and
route it through `createFrom(type, scriptType)`. A marker does not provide the root key. Preserve marker-missing,
missing-property, class-not-found, script-failure wrapping and original causes. A regular Script remains valid for these
existing no-overlay operations.

Diagnostics use the existing breadcrumb pipeline: direct class inputs set type `script` and the shortened script class
name; classpath discovery first sets type `classpath`. Pending breadcrumb fields use first-set-wins semantics, so the
subsequent script route retains the classpath type while contributing the script qualifier. Key derivation stays based
on the script class simple name, independently of those diagnostic labels. Add no caller source-name/key parameter,
synthetic source identity, or per-input provenance model in this slice.

## Exact lifecycle and ordering limits

The current root path is `doCreate` → `PhaseDriver.withBuilderLifecycle` → `prepareBuilder`:

1. Establish the ordinary Construction session; allocate the root Builder and execute source field initializers.
2. Apply current scoped Templates, then run the root's `PostCreate`.
3. Execute the configuration Consumer. The candidate runs the recipe, then `applyOnly(null, clonedOverlay)` here.
4. Run the root's `PostApply` once. Calling public/internal `apply` for the overlay would incorrectly add another
   `PostApply`; this is why the existing configuration-only seam matters.
5. Run graph phases: ApplyLater 1, EarlyValidate 5, AutoCreate 10, Owner 15, AutoLink 20, Default 25, PostTree 30.
6. Instantiate 40 materializes the owned Builder graph; Validate 50 collects completed-model findings; Verify 80 applies
   the failure threshold; Complete 100 follows on success. The session is invalidated in `finally` on success or failure.

`@Required` is converted into `@Validate` by `RequiredToValidateTransformation`; it is not an extra pre-recipe required
check. The supplied LINK is present for normal required checks and validation. Existing validation can still reject
the resulting model; context injection never skips or weakens it.

Child construction executes each child's preparation and immediate callbacks during the configuration Consumer. Thus:

```groovy
// In the compiled recipe:
def root = getDelegate()
child { seen root.environment?.name ?: 'missing' }

// A recipe-then-overlay call binds environment only after this code and child PostApply finish.
```

Recipe-first is safe for the documented graph-phase use case **only if the consumer accepts this limit**. Overlay-first
makes context visible to recipe code and its children, but the recipe may overwrite the supplied LINK and scalar fields.
Neither order makes context available in root `PostCreate`. Replaying the overlay twice, moving `PostCreate`, reserving
context fields, or adding another pre-initialization context channel would be a new contract and requires a fresh decision.

“Overlay wins” must be described narrowly: later normal setter calls replace earlier scalar/LINK assignments. Collection
adders preserve their ordinary accumulation/duplicate behavior; keyed operations retain their normal merge/reuse rules;
custom Builder methods retain method-first dispatch. Later `PostApply`, `applyLater`, Template deferred recipes, and
mutating phases retain their normal authority. An overlay is not an immutable priority layer.

## LINK, Templates, and invocation isolation

Use the ordinary generated LINK configurator. `normalizeRelationshipValue` accepts an ordinary completed DSL Object,
wraps it in an immediately sealed Builder referencing that exact object, and rejects marked Templates. No `copyFrom`,
ownership claim, deferred replay, second root, or new reference registry is involved. Builder traversal skips sealed
wrappers; materialization walks composition only, skips LINK edges, and returns the original completed LINK target.
The external target's lifecycle/validators are not rerun as owned composition. Existing Builder-wrapper read semantics,
including their typed-read limitations, remain authoritative; this issue must not advertise a Model-unwrapping API.

Existing scoped Templates are copied before root `PostCreate`. Their deferred actions run at their established phases
after explicit recipe/overlay configuration. No overlay overload is added to `Create.Template` or `Create.AsBuilder()`;
Template-definition and active-session composition semantics remain those of ADRs 0004/0016. Generated collection and
Cluster APIs must omit the new root overloads, retaining their omitted-projection diagnostic. No Builder materialization
method or synthetic closure that pretends a Script is a closure is needed.

Instantiate a fresh Script per invocation. Clone the supplied overlay before any delegate/resolve-strategy mutation,
then use existing Builder closure dispatch. A shallow closure clone isolates those execution properties; it cannot
isolate arbitrary mutable objects captured by user code, static Script fields, or user side effects. Do not promise
transactional rollback. Ordinary callback/configuration exceptions abort the root and invalidate its Builders; subsequent
invocations must work. A nested root call remains rejected, and catching that rejection must leave the outer invocation
usable. Concurrent invocations on separate threads each own their ordinary session; add no global or ThreadLocal context.
Existing lifecycle/diagnostic ThreadLocals are infrastructure, not a place to store the overlay or selected environment.

## Source seams and compatibility gates

| Seam | Relevant behavior |
| --- | --- |
| `klum-ast-runtime/.../runtime/KlumFactory.java` | Root overload declarations; existing no-overlay `From`/`FromClasspath` untouched; BuilderFactory unchanged |
| `runtime/internal/FactoryHelper.java` | Existing `createFrom`, private classpath resolver, `doCreate`, `prepareBuilder`, configuration Consumer, and regular-Script guard |
| `runtime/internal/BuilderDispatchSupport.java` | Execute the actual fresh DelegatingScript directly against the Builder with current failure correction |
| `runtime/internal/InternalKlumBuilder.java` | `applyOnly`, ordinary LINK normalization/sealing, and graph materialization; avoid global changes to existing closure semantics |
| `runtime/internal/process/PhaseDriver.java` | Existing root guard, one session, graph phase execution, failure cleanup; should require no new context channel |
| `compiler/internal/ast/DSLASTTransformation.java` | `overrideFactoryMethods` and `overrideUndelegatedClosureMethod` specialize return/delegate metadata |
| `compiler/internal/ast/GeneratedDslSupport.java` and source mirrors | Public interface, descriptors, mirrors, Java/static Groovy and IDE parity |
| `compiler/internal/ast/AlternativesClassBuilder.java` / `BuilderMethodProjection.java` | Root-only methods have no matching BuilderFactory form; confirm omission rather than projecting a new nested protocol |
| `compiler/internal/ast/TemplateMethods.java` | Explicit Template API inventory must remain unchanged |

Old generated Schemas do not automatically gain newly generated interface methods merely by upgrading runtime. New
call sites need a recompiled Schema; existing supported no-overlay binary calls must continue linking. Inspect custom
Factories for erased `(Class, Closure)` / `(Closure)` / `(ClassLoader, Closure)` collisions and preserve explicit overrides.
Adding `FromClasspath(Closure)` makes an untyped null literal ambiguous with `FromClasspath(ClassLoader)`; explicit null
loaders were not useful successful calls, but document/retest typed null and overload selection rather than claiming
absolute source compatibility. No new method name, generic context type, package export, or Factory mode is justified.

Layer 3 remains API → Schema → Model construction: generic clients consume the completed Domain API, and a Schema-specific
caller may invoke the concrete Factory. No Spring, generic registry, Cluster projection change, or external Schema
coordination follows. ADR 0018 is a test-packaging boundary, not a runtime Layer 3 construction rule.

## Original conditional next slices (superseded by the dedicated implementation plan)

The initial investigation proposed the following boundaries. The dedicated plan now maps the confirmed closure-only,
recipe-first contract to the same three slices. Neither document authorizes production implementation:

1. **RCP-1: direct compiled recipe.** One reasoned vertical commit adds the root class/closure overload, guarded recipe
   route, invocation-local closure clone, keyed/unkeyed regressions, and a documentary example. Exercise exact lifecycle
   counts, LINK identity/immutability, defaults/required/validation, scalar conflicts, recipe-time visibility, late actions,
   invalid regular Script rejection without execution, failure cleanup, repeated roots, concurrent shared closure, and
   rejected nested roots. Prove public Factory/delegate metadata and absence from AsBuilder/collection/Cluster/Template.
2. **RCP-2: classpath marker.** A vertical commit reuses the same recipe-only core with default/explicit loader overloads.
   Exercise marker/key/diagnostic parity, regular Script marker rejection before execution, missing marker/property/class,
   cause preservation, repeat/concurrency, and unchanged existing no-overlay regular-Script results.
3. **RCP-3: consumer qualification and docs.** Qualify separately compiled Java/static/dynamic Groovy 3/4/5 consumers,
   mirrors/GDSL and generated API inventory, custom Factory collisions, and Groovy 4/5 JPMS linkage. Keep relevant
   `docs/user/Convenience-Factories.md`, `Factory-Classes.md`, phase/Template boundaries, migration navigation, `CHANGES.md`,
   documentary `@Issue('829')` / `@Tag('documentary')` / `@See` links, and the eventual issue acceptance record aligned.
   Run focused compiler/consumer tests, affected module suites, Groovy 4/5 lanes, required plugin/JPMS checks and full
   root `check` for the shared generated/runtime contract; hosted CI/Sonar remains a later exact-head delivery gate.

Stop if the accepted use case needs context during root initializers/PostCreate, promises permanent caller priority,
requires nested independent root materialization, admits a regular Script as a recipe, or requires extra source families.
Return for a product decision rather than adding more hooks, lifecycle passes, or #304 composition machinery.

## Local executable evidence

The retained [probe](evidence/issue-829/probes/RootRecipeCharacterizationTest.groovy) uses only existing internal seams.
It is not in a normal test source set and does not install any public overload. Copy it temporarily to
`klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/` to reproduce, then remove that copy.

These probes establish existing seam behavior,
not a shipped #829 feature, classpath overlay implementation, complete build, hosted CI, Sonar, or ScHelm qualification.

Existing specifications inspected and exercised in the Groovy 3 selection:

- [`ConvenienceFactoriesSpec`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ConvenienceFactoriesSpec.groovy):
  direct regular/delegating scripts, keyed identity, root PostApply, classpath input, opaque nested-producer rejection.
- [`AsBuilderSpec`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/AsBuilderSpec.groovy):
  active-session ownership and recipe versus regular-Script boundary.
- [`StructuredInputConstructionTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputConstructionTest.groovy):
  existing configuration Consumer lifecycle, failure cleanup, Template/LINK identity, and nested root rejection.

Also inspected `LifecycleSpec`'s immediate child callback coverage, `FactoryConstructionTest`, `FactoryTest`,
`FactoryClosureDelegationTest`, `ClasspathTemplateTest`, and the Factory/Builder projection source. No test of a new
generated overload, classpath overlay route, or real binary/JPMS consumer was added or claimed.

Probe-development limits: initial runs failed due to probe-only Spock syntax, raw Consumer typing, and a missing AutoLink
import; those were corrected. Separating this probe's Environment/Child and Application into two dynamic Groovy loader
compilations also hit a canonicalization NPE in `DSLASTTransformation.createSingleDSLObjectFieldCreationMethods`
(`getBuilderClassOf` returned null). The final probe uses the established same-source-unit Schema fixture. The split
loader failure was not diagnosed, does not prove a published artifact defect, and must not be hidden by claiming binary
consumer qualification. No side issue or tracker mutation was made.

The initial combined Groovy 4/5 command placed `--tests` after the second task only. Groovy 4 therefore began a broader
suite; that run was interrupted and is not counted as qualification. The final command repeats the explicit filter for
each task.

Final local results, Java 17, same base, no production edits:

| Lane / selection | Cases | Failures / errors / skipped |
| --- | ---: | --- |
| Groovy 3: ConvenienceFactoriesSpec | 22 | 0 / 0 / 0 |
| Groovy 3: AsBuilderSpec | 11 | 0 / 0 / 0 |
| Groovy 3: StructuredInputConstructionTest | 6 | 0 / 0 / 0 |
| Groovy 3: RootRecipeCharacterizationTest | 8 | 0 / 0 / 0 |
| Groovy 4: RootRecipeCharacterizationTest | 8 | 0 / 0 / 0 |
| Groovy 5: RootRecipeCharacterizationTest | 8 | 0 / 0 / 0 |

Commands (after temporarily restoring the probe to the test source path):

```shell
./gradlew :klum-ast:test \
  --tests com.blackbuild.klum.ast.runtime.internal.RootRecipeCharacterizationTest \
  --tests com.blackbuild.klum.ast.ConvenienceFactoriesSpec \
  --tests com.blackbuild.klum.ast.AsBuilderSpec \
  --tests com.blackbuild.klum.ast.runtime.internal.StructuredInputConstructionTest
./gradlew :klum-ast:groovy4Tests \
  --tests com.blackbuild.klum.ast.runtime.internal.RootRecipeCharacterizationTest \
  :klum-ast:groovy5Tests \
  --tests com.blackbuild.klum.ast.runtime.internal.RootRecipeCharacterizationTest
```

At the initial evidence boundary the active probe copy was removed, leaving this brief and the retained opt-in probe.
No commit was created at that stage because the design decisions were still open; the later maintainer confirmation
authorizes the local ADR/plan record under the original assignment. Full module suites/root `check`, binary/JPMS/plugin consumers,
new API behavior, and user-site rendering were not run; no current user page or release note changed. Whitespace and
local Markdown-link checks cover the retained evidence files. Raw local logs are temporary, outside repository evidence.

## Maintainer decision recorded

The maintainer confirmed **closure-only first** and **recipe, overlay, PostApply**. The visibility and ordinary later-call
limits above therefore form the selected #829 contract. The maintainer also asked whether FromStruct should support the
same interval. Source inspection confirms that it fits the existing configuration Consumer; the dedicated plan records
that recommendation separately, without adding structured-input implementation or acceptance to #829.
