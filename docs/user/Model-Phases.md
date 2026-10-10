# Model phases

Model creation goes through several phases in one Builder lifecycle. The phases are local to the current Thread. Owned
submodels are created through the root's generated Builder methods and share that lifecycle; a nested root factory starts
an independent lifecycle and cannot be adopted as composition.

This page is an explicit documentary-test exception: rather than repeat an abbreviated lifecycle example at every heading,
[`ModelPhasesDocumentaryTest#'runs a deployment lifecycle on Builders before completing its model'`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ModelPhasesDocumentaryTest.groovy)
is one end-to-end example for the lifecycle headings. The test retains exact links to each heading; its abbreviated form is
shown in [Complete lifecycle example](#complete-lifecycle-example).

## Lifecycle annotations

Many lifecycle phases have a designated annotation. Methods and/or fields annotated with these annotations are handled in the
corresponding phase. Lifecycle annotations are annotations marked with the meta annotation `@WriteAccess(LIFECYCLE)`. Those 
methods must be parameterless and not be private. Their visibility is downgraded to `protected` and mutating lifecycle
methods are moved to the generated Builder.

In addition to methods, fields of type `Closure` can also be annotated with lifecycle annotations (including `@Owner`).
Before `INSTANTIATE`, these closures execute with the Builder as their delegate.

The lifecycle annotations `@PostCreate` and `@PostApply` are special cases. These are not run as separate phases, but
instead are part of the creation phase, and run for each object separately. 

Note that lifecycle methods and closures are called unconditionally, regardless of the state of the object (for example,
a `@Default` field will only be handled if the field is not set yet, while a `@Default` method or Closure will always be called). Thus those methods need to check for themselves if they should do anything.

## Creation

The creation phase starts with the first factory call in a thread. It creates and configures the root Builder and its owned
Builder graph. Creating a Builder includes applying [Templates](Templates.md), then calling `@PostCreate`, explicit configuration, and
`@PostApply` methods and closures. No completed DSL Object exists yet.

Before the initial create methods return, control is passed to the PhaseDriver that is responsible to execute all
later phases.

## PhaseActions

PhaseActions are the main execution point for phases. Actions before materialization use
`BuilderVisitingPhaseAction`; actions after materialization use `ModelVisitingPhaseAction`. The deprecated untyped
`VisitingPhaseAction` must not be used across the boundary.

PhaseActions are usually registered by using the Java ServiceLoader mechanism, so plugins can extend the functionality.

Each phase has an ordinal defining the execution order of those phases. Main phases are defined in the `DefaultKlumPhase` enum, but 
there ordinals are spaced to allow for plugins to insert phases in between.

## External field participants (LP-1)

Domain annotations select external creators and mutators on direct DSL relationship fields during `AutoCreate`,
`AutoLink`, `Default` and `PostTree`. They let Schema developers reuse domain behavior through public Builder contracts.
[Final acceptance and API inventory](../implementation/issue-867-lp8-evidence.md) records the qualification for
[#867](https://github.com/klum-dsl/klum-ast/issues/867); the LP labels below link the executable examples to that record.

A domain annotation carries `@LifecycleCreator(phase = AutoLink, handler = ...)`,
`@LifecycleMutator(phase = AutoLink, handler = ...)`, or both. These meta-annotations, handlers and contexts
live in `com.blackbuild.klum.ast.runtime`; a domain annotation library depends on runtime.
Use `@Retention(RUNTIME)` and field placement; mutators also support Schema TYPE placement as described below.
Field participants reject scalars, containers, static fields, method placement, other phase markers, and `FieldType.BUILDER` fields whose original declaration is absent from the Model Schema.
Collection/Map field participants are rejected for this release; their element type does not make the field eligible.
Use a containing lifecycle callback, a direct DSL wrapper, or child type mutation where its semantics fit. Ordinary
composition traversal can still reach type annotations on container children; that does not dispatch the container field
annotation or supply index/key context. Future container support belongs to [#879](https://github.com/klum-dsl/klum-ast/issues/879).

### Reusing a typed consumer contract

The participant context supplies the containing and target Builders. A consumer still owns the typed contract
that exposes its Environment and Facts relationships. This example uses common consumer Schema bases,
`ApplicationBase.environment` and `DomainBase.facts`, and their generated public Builder interfaces.
Factory-token narrowing works for their concrete subtypes without dynamic property lookup, reflection,
Owner backlinks, or per-Application binding handlers. It does not provide a typed bridge between unrelated
Schemas. Heterogeneous consumers can use ordinary Groovy dynamic dispatch with `InvokerHelper.getProperty` and
`InvokerHelper.invokeMethod`, under their own domain convention. KlumAST supplies neither generic Builder property
access nor provider-selection policy. Neither approach requires Owner backreferences or Domain binding callbacks.

This tracer reads an Environment in active construction. A completed Model supplied through `LINK` has a
different read path: direct generated relationship getters on its Builder wrapper can return `null` from
empty wrapper storage. Existing dynamic Groovy property reads, including `InvokerHelper.getProperty`, forward to
the completed Model and return completed values. An external consumer successfully selects and links an
existing Fact this way, using its own dynamic convention. There is no public typed completed-value unwrap
operation in this participant contract. See the
[external qualification evidence](https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/issue-867-lp1-evidence.md#external-schelm-qualification-completed-link-reads).

The Java extension contract is:

```java
public class BindFactsHandler implements LifecycleMutationHandler<BindFacts> {
    public void mutate(LifecycleMutationContext<BindFacts> context) {
        ApplicationBase_DSL.Builder<ApplicationBase> application =
                ApplicationBase.Create.narrowBuilder(context.getContainingBuilder());
        DomainBase_DSL.Builder<DomainBase> domain =
                DomainBase.Create.narrowBuilder(context.getTargetBuilder());
        domain.facts(Facts.Create.AsBuilder().With(
                Map.of("source", application.getEnvironment().getFactSource()
                        + ":" + context.getAnnotation().value())));
    }
}
```

The equivalent statically compiled Groovy handler can use `domain.facts { source ... }` through the same
public generated contract. The documentary tracer uses one such handler for both of these domain-vocabulary
placements, including an inherited field occurrence:

(See: `LifecycleParticipantTest#'reuses typed fact binding across Application and Domain Schemas before child field actions'`.)

```groovy
@Retention(RUNTIME)
@Target(FIELD)
@LifecycleMutator(phase = AutoLink, handler = BindFactsHandler)
@interface BindFacts { String value() }

@DSL abstract class ApplicationBase { Environment environment }
@DSL abstract class DomainBase { Facts facts }
@DSL class KafkaDomain extends DomainBase {}
@DSL class QueueDomain extends DomainBase {}
@DSL class OrdersBase extends ApplicationBase {
    @BindFacts('messaging') KafkaDomain messaging
}
@DSL class Orders extends OrdersBase {}
@DSL class Notifications extends ApplicationBase {
    @BindFacts('delivery') QueueDomain delivery
}

def orders = Orders.Create.With {
    environment { factSource 'production' }
    messaging {}
}
def notifications = Notifications.Create.With {
    environment { factSource 'staging' }
    delivery {}
}
assert orders.messaging.facts.source == 'production:messaging'
assert notifications.delivery.facts.source == 'staging:delivery'
```

The executable tracer also queries a separate `RelationshipRole` annotation on each original Schema field and
records its incoming name, declared Domain type, and actual containing Application subtype. A second participant
on `DomainBase.facts` observes the assigned source in the same AutoLink traversal. Domain classes have no
binding lifecycle methods or Owner fields. This demonstrates a reusable common-contract consumer shape,
without introducing technology types or selection policy into KlumAST.

### Creating the annotated relationship

`LifecycleCreationHandler<A>.create(LifecycleFieldContext<A>)` returns `KlumBuilder<?>` or `null`.
It runs only for an unset field. A non-null result goes through ordinary checked assignment; ownership,
Construction-session and FieldType rules still apply. It cannot return a completed Model.
`LifecycleMutationHandler<A>.mutate(LifecycleMutationContext<A>)` returns `void` and runs only with a
non-null target. Creation always precedes mutation within that field visit; a null result leaves the field
unset and skips mutation. Repeated mutations follow the qualified composition contract below.

(See: `LifecycleParticipantTest#'creates missing relationships before mutation and leaves null results unset (#mode)'`.)

```groovy
@Retention(RUNTIME)
@Target(FIELD)
@LifecycleCreator(phase = AutoLink, handler = SupplyDomain)
@LifecycleMutator(phase = AutoLink, handler = ConfigureDomain)
@interface Supplied { boolean enabled() default true }

class SupplyDomain implements LifecycleCreationHandler<Supplied> {
    KlumBuilder<?> create(LifecycleFieldContext<Supplied> context) {
        context.annotation.enabled() ? Domain.Create.AsBuilder().With(value: 'created') : null
    }
}
// ConfigureDomain narrows context.targetBuilder and appends '-mutated' to its value.
@DSL class Application {
    @Supplied Domain first
    @Supplied(enabled = false) Domain absent
}
def application = Application.Create.One()
assert application.first.value == 'created-mutated'
assert application.absent == null
```

### Context and qualification boundary

`LifecycleFieldContext<A>` exposes `getAnnotation()`, singular typed
`getAnnotation(Class<B>): Optional<B>`, `getContainingBuilder(): KlumBuilder<?>`, `getFieldName()`,
`getDeclaredType(): Class<?>` and effective `getFieldType(): FieldType` from the original Schema declaration.
`LifecycleMutationContext<A>` additionally exposes `getTargetBuilder(): KlumBuilder<?>` and default `isType(): boolean`
(false for fields). For fields, the target is the relationship Builder; declared type, field name, FieldType and lookup
describe the original Schema field. Type invocation changes lookup and root context as described under LP-5 below.
There is no setter, reflective Field, annotation list, path API, or expiry operation. Contexts are for the
invocation only; retaining them grants no new Builder rights.

Each invocation constructs a fresh public concrete handler with a public no-arg constructor. Its annotation
parameter must resolve exactly to the domain annotation, including generic inheritance. Raw, wildcard,
unresolved and mismatched parameters fail Schema compilation; runtime independently defends precompiled
inputs before invocation. All four phases and sealed FAIL/SKIP are qualified below, together with
bounded Template/import/graph coverage. Type mutation is qualified under LP-5 below. Java and dynamic/static Groovy
3/4/5 consumers are covered; Groovy 3 uses the classpath, and Groovy 4/5 also support named modules. A separate
handler module must export its public handler package to runtime for no-arg construction; Schema packages retain the
ordinary [qualified opens](Migration.md#named-modules-and-groovy). No extra JVM access flags are needed.
For ordinary cross-module Groovy annotation-Closure construction, the existing named fixture uses an exported Schema
package, accessible to Groovy as well as runtime; keep that access when separating annotation libraries.
HANDLE and the annotation-Closure helper remain deferred without APIs. See the
[LP-1 evidence](../implementation/issue-867-lp1-evidence.md) and
[ADR 0028 plan](../implementation/adr-0028-annotation-driven-lifecycle-participants.md).

### Participant composition (LP-2)

A domain annotation can combine creation with several mutations. During the field's selected phase visit,
creation always precedes every mutation, including mutations from other annotations. Existing Builders
are preserved; a null creator result leaves the field unset and skips its mutations. At most one direct
creator may claim the same field and phase. Two external creators, repeated creators in one domain
annotation, or `@LinkTo` plus an external `AutoLink` creator fail Schema compilation. Multiple mutations
remain legal, including alongside built-in creation. Built-in creation in a different phase (for example
`@AutoCreate`) may coexist with an external `AutoLink` creator/mutator.

(See: `LifecycleParticipantCompositionTest#'combines creation and repeated mutations on supplied and existing Builders (#container)'`.)

```groovy
@Retention(RUNTIME) @Target(FIELD)
@LifecycleCreator(phase = AutoLink, handler = Supply)
@LifecycleMutator(phase = AutoLink, handler = ZFirst)
@LifecycleMutator(phase = AutoLink, handler = ASecond)
@interface Configured { boolean enabled() default true }

@DSL class Domain { String value }
@DSL class Application {
    @Configured Domain supplied
    @Configured Domain existing
    @Configured(enabled = false) Domain absent
}

def application = Application.Create.With { existing { value 'configured' } }
// Supply returns a Domain Builder with value 'created', or null when disabled.
// ZFirst appends ':first'; ASecond appends ':second'.
assert application.supplied.value == 'created:first:second'
assert application.existing.value == 'configured:first:second'
assert application.absent == null
```

Within one domain annotation, repeated `@LifecycleMutator` declarations execute in declaration order.
The equivalent explicit `@LifecycleMutator.List([@LifecycleMutator(...), ...])` executes in its `value`
array order (Java uses `{...}`). This narrow contract is qualified for Java- and Groovy-authored annotation
libraries, separately compiled Schemas and binary consumers under Groovy 3/4/5. It follows the emitted
repeatable container's ordered array, without alphabetical sorting or priorities.

Mixing a singular marker with an explicit container on the same domain annotation has unspecified relative
order. Order between different domain annotations on one field also remains unspecified; their handlers
must be correct independently of that order. Avoid mixing singular markers and explicit containers when order matters.
Neither case weakens creator-before-mutator. The same composition contract applies in all four supported phases.
Type mutation precedes the visited Builder’s own fields, while parent-field work precedes child type mutation.

See [LP-2 evidence](../implementation/issue-867-lp2-evidence.md) for the precise ordering probe matrix.

### Field participants in four phases (LP-3)

A domain annotation may choose `AutoCreate`, `AutoLink`, `Default` or `PostTree` independently for each
creator or mutator declaration. Only handlers for the current phase execute. One annotation may compose
creators in different phases; each runs only if the field is still null in its phase. Competing direct
creators in the same phase fail compilation, including external creation versus `@AutoCreate`, `@LinkTo`
or direct `@Default`. Built-in creation may coexist with external mutation at the same slot.

| Phase | Field slot inside the containing Builder visit |
| --- | --- |
| AutoCreate | Direct built-in/external creation, then field mutations; all direct fields finish before cluster AutoCreate, then lifecycle methods and Closures |
| AutoLink | Existing LinkTo field enumeration: built-in/external creation, then field mutations, then lifecycle methods and Closures |
| Default | Owner-provided defaults, containing-field defaults, type defaults, then direct built-in/external creation and field mutations, then lifecycle methods and Closures |
| PostTree | Existing property enumeration supplies a field step immediately before lifecycle methods and Closures |

Field enumeration keeps the existing phase mechanics; it introduces no sorting or cross-field order guarantee.
Cluster AutoCreate sees the resulting direct-field values. It can fill remaining nulls (including a creator's
null result), but does not rerun direct-field creators or mutators. Thus a cluster-created target does not receive
the earlier direct-field mutation in that AutoCreate visit.

(See: `LifecycleParticipantPhaseTest#'creates then mutates direct fields before callbacks in #phase'`.)

```groovy
@Retention(RUNTIME)
@Target(FIELD)
@LifecycleCreator(phase = AutoCreate, handler = Supply)
@LifecycleMutator(phase = AutoCreate, handler = ZFirst)
@LifecycleMutator(phase = AutoCreate, handler = ASecond)
@interface Configured {}

class Supply implements LifecycleCreationHandler<Configured> {
    KlumBuilder<?> create(LifecycleFieldContext<Configured> context) {
        Service.Create.AsBuilder().With([value: 'created'])
    }
}
class ZFirst implements LifecycleMutationHandler<Configured> {
    void mutate(LifecycleMutationContext<Configured> context) {
        def service = Service.Create.narrowBuilder(context.targetBuilder)
        service.value(service.value + ':first')
    }
}
class ASecond implements LifecycleMutationHandler<Configured> {
    void mutate(LifecycleMutationContext<Configured> context) {
        def service = Service.Create.narrowBuilder(context.targetBuilder)
        service.value(service.value + ':second')
    }
}
@DSL class Service { String value }
@DSL class Application { @Configured Service service }

assert Application.Create.One().service.value == 'created:first:second'
```

Parent-field work completes before traversal reads and visits children in that same phase. An AutoCreate
mutator can add a grandchild to an existing child; an AutoLink creator can supply the annotated child itself.
Those children receive the current phase's field work, methods, then Closures through ordinary traversal.
An AutoLink-created child receives AutoLink processing, not AutoCreate. Children created in Default or PostTree
have not run earlier callbacks, defaults or automatic creation; handlers must initialize what they require. An Owner
field can still be null on a late-created child. Earlier phases never replay; future catch-up policy belongs to
[#874](https://github.com/klum-dsl/klum-ast/issues/874).

[LP-3 evidence](../implementation/issue-867-lp3-evidence.md) records the exact slots and regressions.

### Sealed participant targets (LP-4)

`LifecycleMutator.onSealed` selects `LifecycleMutator.SealedPolicy.FAIL` (the default) or `SKIP`
for that mutator occurrence. FAIL rejects a sealed target before constructing the handler, retaining
participant annotation, handler, phase, original declaring Schema field and the rejection cause.
SKIP omits both handler construction and invocation. Unsealed targets still execute normally under either
policy, including polymorphic values and aliases; each invocation receives a fresh handler.

(See: `LifecycleParticipantSealedTest#'skips completed LINK targets before constructing handlers in #phase'`.)

```groovy
@Retention(RUNTIME)
@Target(FIELD)
@LifecycleMutator(phase = AutoLink, handler = Configure,
    onSealed = LifecycleMutator.SealedPolicy.SKIP)
@interface Configured {}

@DSL class Service { String value }
@DSL class SpecializedService extends Service {}
@DSL class Application {
    @Configured @Field(FieldType.LINK) Service service
    @Configured @Field(FieldType.LINK) Service alias
}
def completed = SpecializedService.Create.With { value 'completed' }
def application = Application.Create.With {
    service completed
    alias completed
}
assert application.service.is(completed)
assert application.alias.is(completed)
// Configure is neither constructed nor invoked for these completed targets.
```

Creators have no separate sealed policy. Their Builder results use ordinary checked assignment:
completed wrappers may attach through LINK/OPTIONAL_LINK; composition, ownership and Construction-session
checks still apply. A following mutator independently applies its own FAIL/SKIP policy to that result.

Value-only Template definitions execute no participants. Applying a Template dispatches participants in
the recipient lifecycle with fresh handler state. Existing FromMap and Jackson root imports use their
ordinary root lifecycle. Jackson Template imports remain value-only; in-session Builder imports and
apply-to-Builder execute through the enclosing root. Late-created children retain ordinary session and
declaration authority and join the current traversal without replaying earlier phases. Completed Java
serialization preserves graph identity without retaining handlers, contexts or Builders.

HANDLE is deferred: current completed LINK wrappers support dynamic property reads, but generated typed
relationship getters read empty wrapper storage. Wrapper-targeted validation reports are not transferred
to the existing completed target. A dispatch-only opt-in would therefore expose an inconsistent read and
validation contract. LP-4 adds no completed-target access or reporting framework and never unseals targets.
[LP-4 evidence](../implementation/issue-867-lp4-evidence.md) records the bounded probes and route matrix.

### Type mutation (LP-5)

A domain annotation with `@Target(TYPE)` may select mutating participants for the visited Schema Builder.
Creators remain field-only. The same `LifecycleMutationHandler<A>` is used for field and type placement;
`LifecycleMutationContext.isType()` distinguishes them. Type mutations run at the start of that Builder's
existing AutoCreate, AutoLink, Default or PostTree visit, after parent-field dispatch and before its own
fields, clusters, defaults, lifecycle methods and Closure callbacks. No extra traversal or phase replay occurs.

(See: `LifecycleParticipantTypeTest#'type mutation follows parent fields and precedes own fields and callbacks in #phase (#existing)'`.)

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.FIELD])
@LifecycleMutator(phase = AutoLink, handler = Configure)
@interface Managed { String value() }

class Configure implements LifecycleMutationHandler<Managed> {
    void mutate(LifecycleMutationContext<Managed> context) {
        def target = Service.Create.narrowBuilder(context.targetBuilder)
        target.region(context.annotation.value())
    }
}

@Managed('eu')
@DSL class Service { String region }
assert Service.Create.One().region == 'eu'
```

For type invocation, the target is the visited Builder and `getDeclaredType()` is its concrete Schema type.
Containing Builder and incoming field name come directly from traversal and are both null at root.
`getFieldType()` reads the incoming original Schema field when traversal has one and returns null otherwise.
Incoming traversal is not authoritative ownership. For owning Schema declarations, use
[Structure metadata](Completed-Object-Support.md#owning-schema-declarations) from #856, observing its active-session,
after-OWNER lifetime and absence semantics; an absent descriptor does not identify a root. Singular `getAnnotation(Class)` queries the Schema type,
with Java inheritance semantics, rather than the incoming field. Field invocation retains its original declaration
lookup and actual containing subtype receiver. Context lifetime remains invocation-only.

Only annotations marked `@Inherited` propagate from superclasses. A subclass annotation of the same type
replaces that inherited annotation; interface annotations and unmarked annotations do not propagate.
Discovery uses ordinary `Class.getAnnotations()` and lookup uses `Class.getAnnotation()`, with no plural
expansion. A repeatable domain annotation's container is a separate annotation type: repeated uses alone do
not dispatch unless that container itself has a mutator. A subclass container can coexist with an inherited
singular annotation; that singular annotation still dispatches. An unannotated subclass inherits the container
itself, while a local container replaces the inherited container, without implicit per-entry expansion. Java and Groovy authored libraries qualify
this behavior. This is separate from repeated **meta-mutators** within one domain annotation, whose LP-2
container/declaration order remains supported. Ordering between distinct type annotation types, or a
singular meta-marker mixed with its explicit container, remains unspecified. Handlers must be independent
of that order; no priorities or sorting are introduced.

Sealed aggregation targets retain the current traversal skip and receive no type invocation, regardless
of their annotation's sealed policy. Fresh handlers, active sessions, checked assignment, ownership,
Template value-only definitions, recipient lifecycle and materialization retain their existing boundaries.
See [LP-5 evidence](../implementation/issue-867-lp5-evidence.md) for the inheritance matrix.

### Annotation Closure members

A handler can evaluate an ordinary Groovy annotation Closure under its own contract. For a literal mapping, the existing
pattern is sufficient:

(See: `LifecycleParticipantClosureProbeTest#'ordinary annotation map configures a relationship without a Klum Closure API'`.)

```groovy
@DSL class Application {
    Map<String, String> catalog
    @FactBinding({ [messaging: 'facts'] }) Domain domain
}
// The consumer's FactBinding handler constructs the Closure and calls it to obtain the mapping.
```

No KlumAST Closure helper is supplied. Generic `Class<? extends Closure<...>>` bounds do not validate arbitrary inline
results or infer a runtime-selected delegate. Explicit typed Schema arguments and dynamic delegation are distinct
consumer choices; enclosing-class IDE inference is not static compiler proof. See [LP-6 evidence](../implementation/issue-867-lp6-evidence.md)
and deferred [#878](https://github.com/klum-dsl/klum-ast/issues/878) for those limits. Closure evaluation does not repair
completed-LINK typed reads or wrapper-targeted validation transfer, and `DELEGATE_ONLY` is not a sandbox.

### Participant validation

Report domain findings with the existing `KlumSchemaSupport` / `KlumValidationReporter` capability. Use an explicit target
for the nested Domain: the current-object reporter uses the framework's current instance/member context, which is not a
promise to select the participant's target. Explicit targets have no default member; `issueAt`/`errorAt` supplies one.

(See: `LifecycleParticipantValidationDocumentaryTest#'reports missing Facts on the explicit Domain and transfers findings to the completed Model'`.)

```groovy
@CompileStatic
class CheckFactsHandler implements LifecycleMutationHandler<CheckFacts> {
    void mutate(LifecycleMutationContext<CheckFacts> context) {
        def domain = Domain.Create.narrowBuilder(context.targetBuilder)
        if (!domain.facts)
            KlumSchemaSupport.klumValidationForObject(domain)
                .issueAt('facts', 'facts are required', Validate.Level.WARNING)
    }
}
@Retention(RUNTIME) @Target(FIELD)
@LifecycleMutator(phase = AutoLink, handler = CheckFactsHandler)
@interface CheckFacts {}
@DSL class Facts {}
@DSL class Domain { Facts facts }
@DSL class Application { @CheckFacts Domain domain }

def application = Application.Create.With { domain {} }
def issue = KlumObjectSupport.of(application.domain).validation.result.issues.first()
assert issue.member == 'facts'
assert issue.level == Validate.Level.WARNING
```

Normal materialization transfers the Domain Builder's findings to that completed Domain. Existing suppression applies to
later findings on the selected target; `klum.validation.failOnLevel` controls Verify (ERROR by default). At WARNING,
the example fails in Verify with `KlumValidationException`, rather than a participant execution error. Use `errorAt` for
an ERROR-level finding. See [Validation](Validation.md#custom-issues) for reporting, suppression and thresholds.
This does not extend validation transfer to sealed wrappers of already completed LINK targets.

### Participant diagnostics

Configuration defects fail Schema compilation or binary declaration checks (`KlumSchemaException`): unsupported
placement/phases, invalid handler generics/constructors and competing creators identify the relevant declaration and
handler where applicable. Participant construction, invocation and checked-assignment failures retain the original
cause and add annotation, handler, phase and original Schema field or visited type context (`KlumModelException`).
Domain validation findings use the reporter, retain their target/member/severity through materialization and are
assessed in Verify. Throwing from a handler aborts execution; it does not collect a domain validation finding.
[Final acceptance](../implementation/issue-867-lp8-evidence.md) links the source, binary and exception regressions.

## Phase Details

## ApplyLater (1)
The ApplyLater phase is the first phase after the initial creation of the model. It executes all closures registered using the `applyLater` method without a phase argument outside of any running phase.

(See: `ModelPhasesDocumentaryTest#'applies a deferred deployment setting before automatic lifecycle work'`.)

```groovy
def deployment = Deployment.Create.With {
    applyLater {
        environment 'production'
    }
}

assert deployment.environment == 'production'
```

## Early Validation (5)

The early validation phase is used to validate everything model supplied (as opposed to auto created, which are supplied by the schema),
i.e., everything provided by a user-provided script or code. This includes checks for deprecated fields or explicit notifications using the `@Notify` annotation.

## AutoCreate (10)

The AutoCreate phase will create objects that are marked with `@AutoCreate` and have not been created yet. It also runs
any lifecycle methods and Closures that are marked with `@AutoCreate`.

## Owner (15)

The Owner phase is a special variant of the AutoLink phase in that it links objects together, in that case fields
annotated with the `@Owner` annotation. This is done before the AutoLink phase since AutoLink makes usually makes
heavy use of the owner field.

Also resolves `@Role` fields and methods, which are technically special case `@Owner` elements.

See [Ownership and `@Owner`](Basics.md#ownership-and-owner) for a relationship visual showing that this phase establishes
framework-managed backlinks after Builder configuration and before materialization.

## AutoLink (20)

The AutoLink phase is bound to set field with references to existing objects somewhere in the model tree. This is done
by annotating fields with `@LinkTo`. Also, regular lifecycle methods and Closure fields can be annotated with `@AutoLink` to be executed.

An inherited callback may read its accepted owning Schema declaration through
[Builder Structure](Completed-Object-Support.md#owning-schema-declarations). Every such query requires the current
thread's active Construction session and a phase strictly after OWNER(15), including queries that would return empty.
Normal sealing at INSTANTIATE does not end this read lifetime; session completion or abort does. Retained immutable
descriptors remain readable afterwards. Later Model callbacks normally use completed Object support. This adds no late
Builder callback, traversal, mutation, or Model-extraction operation.

## Default (25)

The Default phase is used to set default values. See [Default Values](Default-Values.md) for details. Owner-provided
defaults run as the first ordered action inside this phase, after the Owner phase has selected their donor and before
`@DefaultValues`, `@Default` field/delegate/code defaults, and `@Default` lifecycle callbacks. As with all lifecycle
annotations, methods and Closure fields annotated with `@Default` will also be executed during this phase.

## PostTree (30)

The PostTree phase allows executing actions on a completely configured Builder tree. This can be used
to create interlinking between objects that are too complex for AutoLink/AutoCreate.

## Instantiate (40)

The Instantiate phase materializes the complete composition graph. It first allocates every completed DSL Object and then
assigns relationship fields, preserving cycles and self-links. Non-relationship state is copied as immutable model state;
Collections become independent read-only snapshots. After this phase, the PhaseDriver root is the completed DSL Object.

The relationship visual in [Ownership and `@Owner`](Basics.md#ownership-and-owner) places `INSTANTIATE` after Owner
establishment so it does not imply that relationship configuration immediately assigns an Owner field.

(See: `ModelPhasesDocumentaryTest#'materializes a release plan into an independent completed snapshot'`.)

## Validation (50)

Validates the correctness of completed DSL Objects according to the presence of the `@Validate` annotation. See
[Validation](Validation.md) for details. Validation must not mutate the model. Provisional issues collected from Builders
are transferred during materialization, and each `InstanceValidator` runs at most once per completed object. The validation
phase and custom validation phases only collect problems; the Verify phase throws. The ordinal band 51-60 is free for
plugin-provided validation phases.

## Verify (80)

Verifies that previous phases have raised no validation problems of the fail level or higher (ERROR by default). Throws an exception otherwise.

## Completion (100)

Deletes registered template objects.

Plugins can register actions to be executed after the model has been created and validated.
This could, for example, be used for logging purpose or to register the model in some kind of external registry.

Note that the lifecycle methods for AutoCreate, AutoLink and PostTree are technically identical, the difference being
more of a semantic nature. So AutoCreate methods should actually create objects, AutoLink methods should link existing objects.

## Error handling

Exceptions during a phase are wrapped in `KlumException` or a subclass and retain the relevant phase and, where
available, a construction path. See [Exception Handling](Exception-Handling.md) for the hierarchy and path details.

## `applyLater` Methods

Builders provide `applyLater` methods that register deferred construction actions. If no phase is specified,
the action is executed directly after the current phase if called from within a phase; otherwise it is executed in the `ApplyLater` phase. 

ApplyLater closures on Templates are not executed on the Template. They are detached as recipe state and replayed against
each fresh recipient Builder. Captured Builders are rejected, and other captured values must be serializable.

Every `applyLater` and `scheduleApplyLater` overload rejects phase 40 or later immediately. Deferred actions mutate
Builders, so they must run before `INSTANTIATE`. Move the action to a phase below 40, or implement completed-model work as
a `ModelVisitingPhaseAction`. For example, phase 40 fails with:

```text
Cannot schedule applyLater for phase 'instantiate' (40): deferred Builder actions must run before materialization at phase 40. Use a phase below 40, or a ModelVisitingPhaseAction for completed-model work.
```

This guard is the materialization boundary. It does not add the broader past/current-phase checks tracked separately.
This is especially useful for test cases, where the model needs specific, non-trivial values to be set (e.g., for validation), but these values are irrelevant for the actual test.

(See: `TemplatesDocumentaryTest#'creates an unkeyed reusable template without lifecycle callbacks'`.)

```groovy
class PersonText extends Specification {

    def template = Person.Create.Template.With {
        applyLater {
            // this closure will be executed for all objects created from this template
            street "Main Street " + name
            city "City of " + name
        }
    }

    def "a testcase with person objects"() {
        given:
        def person = Person.Template.With(template) {
            PersonText.Create.With(name: "Hans")
        }

        when:
        def result = service.doSomething(person)

        then:
        //... check something
    }
}
```

## Methods that modify datastructures

In some (usually migration related) cases, a lifecycle methods trying to modify the list or map containing itself. This would
lead to a concurrent modification exception. To avoid this, the code of the lifecycle method can be wrapped in an applyLater closure,
causing it to run directly after the current phase has finished.

```groovy
@AutoLink moveLegacySiblings() {
    if (this.hasLegacySiblings()) {
        // this closure will be executed after the current phase has finished
        applyLater {
            this.legacySiblings.each {
                parent.removeChild(it)
                this.addLegacy(it)
            }
        }
    }
}
```

Note that this example could have been better realized in a parent's autolink method, removing the need for the applyLater closure.

## Complete lifecycle example

(See: `ModelPhasesDocumentaryTest#'runs a deployment lifecycle on Builders before completing its model'`.)

```groovy
@DSL
class Deployment {
    String environment
    Component component

    @PostCreate
    void beginConfiguration() {
        // Builder-specific initialization
    }

    @PostApply
    void finishConfiguration() {
        // explicit DSL configuration is now available
    }

    @AutoCreate
    void chooseEnvironment() {
        environment ?= 'production'
    }

    @PostTree
    void finishTree() {
        // every owned Builder is configured
    }

    @Validate
    void validateCompletedModel() {
        assert component.environment == environment
    }
}

@DSL
class Component {
    @Owner Deployment deployment
    String environment

    @AutoLink
    void inheritEnvironment() {
        environment = deployment.environment
    }

    @Default
    void useInheritedEnvironment() {
        environment ?= deployment.environment
    }
}

def deployment = Deployment.Create.With {
    component {}
}

assert deployment.component.deployment.is(deployment)
assert deployment.component.environment == 'production'
```
