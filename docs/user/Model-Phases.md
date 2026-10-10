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

Issue [#867](https://github.com/klum-dsl/klum-ast/issues/867) currently delivers the LP-1 tracer, LP-2 composition and LP-3 field dispatch: domain annotations on direct DSL fields during `AutoCreate`, `AutoLink`, `Default` and `PostTree`. Names and signatures remain provisional.
The whole feature and its conditional 4.1 placement still require the remaining ADR 0028 gates.

A domain annotation carries `@LifecycleCreator(phase = AutoLink, handler = ...)`,
`@LifecycleMutator(phase = AutoLink, handler = ...)`, or both. These meta-annotations, handlers and contexts
live in `com.blackbuild.klum.ast.runtime`; a domain annotation library depends on runtime.
Use `@Retention(RUNTIME)` and field placement. Unsupported scalars, containers, static fields, type/method
placement, other phase markers, and `FieldType.BUILDER` fields whose original declaration is absent from the Model Schema.
This is a qualification boundary, not a final container or Builder-only-field decision.

### Reusing a typed consumer contract

The participant context supplies the containing and target Builders. A consumer still owns the typed contract
that exposes its Environment and Facts relationships. This example uses common consumer Schema bases,
`ApplicationBase.environment` and `DomainBase.facts`, and their generated public Builder interfaces.
Factory-token narrowing works for their concrete subtypes without dynamic property lookup, reflection,
Owner backlinks, or per-Application binding handlers. It does not provide a typed bridge between unrelated
Schemas; consumers without a common contract need separate design/LP-6 evidence.

This tracer reads an Environment in active construction. A completed Model supplied through `LINK` has a
different read path: direct generated relationship getters on its Builder wrapper can return `null` from
empty wrapper storage. Existing dynamic Groovy property reads, including `InvokerHelper.getProperty`, forward to
the completed Model and return completed values. An external consumer successfully selects and links an
existing Fact this way, using its own dynamic convention. LP-1 has no public typed completed-value unwrap
operation; a generated typed read contract for this case remains unqualified. See the
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
`LifecycleMutationContext<A>` additionally exposes `getTargetBuilder(): KlumBuilder<?>`.
There is no setter, reflective Field, annotation list, path API, or expiry operation. Contexts are for the
invocation only; retaining them grants no new Builder rights.

Each invocation constructs a fresh public concrete handler with a public no-arg constructor. Its annotation
parameter must resolve exactly to the domain annotation, including generic inheritance. Raw, wildcard,
unresolved and mismatched parameters fail Schema compilation; runtime independently defends precompiled
inputs before invocation. Sealed mutation targets are rejected. FAIL/SKIP, all four phases,
Template/import/graph/JPMS coverage, validation guidance and optional type,
Closure, HANDLE and container decisions remain later gates. See the
[LP-1 evidence](../implementation/issue-867-lp1-evidence.md) and
[ADR 0028 plan](../implementation/adr-0028-annotation-driven-lifecycle-participants.md).

### Participant composition (LP-2)

A domain annotation can combine creation with several mutations. During the field's `AutoLink` visit,
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
must be correct independently of that order. Neither case weakens creator-before-mutator. Optional type
mutation and its placement order remain a later gate. LP-3 applies this same field composition contract in all four supported phases.

See [LP-2 evidence](../implementation/issue-867-lp2-evidence.md) for the precise probe matrix and remaining gates.

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
Earlier phases do not rerun for children created later. No new traversal, ownership or path reconstruction is used.

[LP-3 evidence](../implementation/issue-867-lp3-evidence.md) records the exact slots and regressions.
Sealed FAIL/SKIP and Template/import qualification remain LP-4; optional type/Closure/container capabilities
and final errors, validation, JVM/JPMS and release qualification remain later gates. This is partial feature
qualification and leaves #867 and its release placement unchanged.

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
