# Lifecycle Participants

Schema Developers can attach reusable domain behavior to a Schema through a domain annotation. A creator supplies a
missing relationship and a mutator configures a Builder through its generated public contract. See
[Model Phases](Model-Phases.md) for the lifecycle sequence.

## Declaring participants

Import `com.blackbuild.klum.ast.runtime.LifecycleParticipant`. Give the domain annotation runtime retention and
mark it with `@LifecycleParticipant.Creator`, `@LifecycleParticipant.Mutator`, or both. Each marker selects a handler
and one of four phase annotations: `AutoCreate`, `AutoLink`, `Default`, or `PostTree`.

Use field participants on a direct DSL relationship declared in the Model Schema. Scalar, static, and Builder-only
fields and method placement are outside this contract. Collection and Map fields are rejected even when their element
type is a DSL type. Consider a containing lifecycle callback or a direct DSL wrapper for those shapes. Ordinary
composition traversal can still reach [type mutators](#type-mutation) on owned collection or Map children. This
supplies no container-field dispatch or key/index context. See [#879](https://github.com/klum-dsl/klum-ast/issues/879)
for the separate container investigation.

## Creation and mutation

- `CreationHandler<A>.create(InvocationContext<A>)` returns a `KlumBuilder<?>` or `null`. It runs when the field is null.
  A null result leaves it unset. A returned Builder goes through the normal FieldType, ownership, and Construction-session
  checks. Use `Create.AsBuilder()` to make an owned child in the active session.
- `MutationHandler<A>.mutate(MutationContext<A>)` returns `void`. It runs on a non-null Builder, whether already present
  or supplied by a creator in the same visit. The context supports configuring the target and provides no field setter.

Each field can have one direct creator per phase. A second creator for the same slot is a declaration error, including
competition with a built-in `@AutoCreate`, `@LinkTo`, or direct `@Default` creator in its phase. Different-phase creators
can coexist. A creator and multiple mutators can share a slot, including built-in creation followed by external mutation.

Within one domain annotation, repeated `@LifecycleParticipant.Mutator` markers run in declaration order. An explicit
`@LifecycleParticipant.Mutator.List` runs in its `value` array order. Relative order is unspecified across different
domain annotations, or between a singular marker and an explicit container on the same annotation. Keep those
handlers independent of each other's ordering.

This example configures a supplied Service, appends to the value of an existing Service, and leaves a disabled Service
unset. The handler names illustrate that the declaration order governs the result.

(See: `LifecycleParticipantCompositionTest#'combines creation and repeated mutations on supplied and existing Builders (#container)'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.layer3.AutoLink
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.LifecycleParticipant

import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.FIELD
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Retention(RUNTIME)
@Target(FIELD)
@LifecycleParticipant.Creator(phase = AutoLink, handler = Supply)
@LifecycleParticipant.Mutator(phase = AutoLink, handler = ZFirst)
@LifecycleParticipant.Mutator(phase = AutoLink, handler = ASecond)
@interface Configured {
    boolean enabled() default true
}

class Supply implements LifecycleParticipant.CreationHandler<Configured> {
    KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Configured> context) {
        context.annotation.enabled() ? Service.Create.AsBuilder().With([value: 'created']) : null
    }
}

class ZFirst implements LifecycleParticipant.MutationHandler<Configured> {
    void mutate(LifecycleParticipant.MutationContext<Configured> context) {
        def service = Service.Create.narrowBuilder(context.targetBuilder)
        service.value(service.value + ':first')
    }
}

class ASecond implements LifecycleParticipant.MutationHandler<Configured> {
    void mutate(LifecycleParticipant.MutationContext<Configured> context) {
        def service = Service.Create.narrowBuilder(context.targetBuilder)
        service.value(service.value + ':second')
    }
}

@DSL class Service { String value }

@DSL class Application {
    @Configured Service supplied
    @Configured Service existing
    @Configured(enabled = false) Service absent
}

def application = Application.Create.With {
    existing { value 'configured' }
}

assert application.supplied.value == 'created:first:second'
assert application.existing.value == 'configured:first:second'
assert application.absent == null
```

## Phase ordering

Only declarations for the current phase execute. The table describes the containing Builder's visit. In each direct
field slot, eligible creation precedes mutation. Lifecycle methods precede Closure callbacks.

| Phase | Visit order after any type mutators |
| --- | --- |
| AutoCreate | Direct field slots, then cluster AutoCreate, then callbacks |
| AutoLink | Direct field slots for LinkTo and participants, then callbacks |
| Default | Owner-provided defaults, containing-field defaults, type defaults, direct field slots, then callbacks |
| PostTree | Direct field slots, then callbacks |

Field enumeration supplies no ordering guarantee across different fields. Cluster AutoCreate can fill a field left null
by a direct creator. It does not re-run that field's participants, so an AutoCreate mutator does not run on a target
first created by the later cluster step during that visit.

Descent to owned children follows containing Builder work. A child added during that visit can join the current phase.
Earlier phases do not replay. For the implications for late initialization, see
[Ordering, late children, and existing links](Model-Phases.md#ordering-late-children-and-existing-links).

See `LifecycleParticipantPhaseTest#'creates then mutates direct fields before callbacks in #phase'`.

## Type mutation

An annotation that permits TYPE placement can declare mutators for a Schema type. Creators require field placement.
Type mutators run on the visited Builder before its own field work and after the containing Builder's visit. A type
mutator's target is always the visited Builder.

Java `@Inherited` semantics apply. A subclass annotation of the same type replaces the inherited one. Unmarked
annotations and annotations on interfaces do not propagate. Each discovered annotation type is considered as a single
annotation, including a repeatable domain annotation's container. The container only dispatches its own mutators and
its entries are never expanded into separate visits. A subclass container can coexist with an inherited singular
annotation. These rules are separate from [repeated mutator markers on one domain annotation](#creation-and-mutation).

Sealed aggregation targets are outside owned traversal and receive no type invocation.

See `LifecycleParticipantTypeTest#'type mutation follows parent fields and precedes own fields and callbacks in #phase (#existing)'`.

## Handler context and access

The handler must be a public concrete class with a public no-argument constructor. Each invocation receives a fresh
instance. Its type parameter must resolve to the exact domain annotation, including when using generic inheritance.
Raw, wildcard, unresolved, and mismatched parameters are rejected.

`InvocationContext<A>` exposes the annotation, containing Builder, field name, declared type, and effective
`FieldType`. `MutationContext<A>` adds the target Builder and `isType()`. Use `getAnnotation(Class<B>)` for singular
typed lookup, with an `Optional<B>` result.

| Context | Field invocation | Type invocation |
| --- | --- | --- |
| Declared type and typed annotation lookup | The original Schema field, including inherited fields | The visited concrete Schema type, with Java inheritance |
| Containing Builder and field name | The Builder holding the field and the field's name | The incoming traversal occurrence, both null at the root |
| `FieldType` | The effective type of the original field | The incoming field's effective type when available, otherwise null |

Type invocation's incoming traversal context describes how the visit arrived. Use
[owning Schema declarations](Completed-Object-Support.md#owning-schema-declarations) to query authoritative ownership.
Use contexts during the invocation. Retaining a context grants no rights to a Builder outside its session.

A domain annotation library depends on the KlumAST runtime. For named modules, export the public handler package to
the runtime for construction and use the usual [qualified opens for Schema packages](Migration.md#named-modules-and-groovy).
For cross-module Groovy annotation Closures, the Closure's Schema package also needs to be accessible to Groovy.

## Using public Builder contracts

Use [factory-token narrowing](Advanced-Techniques.md#narrowing-a-builder-by-model-type) to access generated public
Builder interfaces. A shared consumer Schema base can expose one typed contract reused by several concrete Schemas.
Handler code owns the domain behavior, including provider selection. A shared handler across unrelated Schemas can
use ordinary dynamic Groovy access under a consumer-owned convention.

On completed LINK wrappers, dynamic reads can reach completed Model properties, while generated typed relationship
getters can return null. See [Builder-backed providers](Behind-the-Curtain.md#builder-backed-linkto-providers) for the
read contract.

## Sealed targets

For each field mutator, `onSealed` defaults to `LifecycleParticipant.Mutator.SealedPolicy.FAIL`. Set
`onSealed = LifecycleParticipant.Mutator.SealedPolicy.SKIP` to omit the handler when a target is sealed. Both policies
act before handler construction for that target. Unsealed targets run normally under either policy.

Creators use checked assignment. A wrapper for a completed Model can attach through `LINK` or `OPTIONAL_LINK` where
the relationship admits it. A subsequent mutator applies its own policy to the resulting target. Use
[the normal LINK read mechanisms](Behind-the-Curtain.md#builder-backed-linkto-providers) to inspect an existing Model.

Value-only Template definitions do not dispatch participants. Applying a Template dispatches them in the recipient
session. Root imports that use the ordinary lifecycle dispatch there, and in-session imports participate in their
enclosing root's phases.

Suppose an Application Schema declares two LINK fields, `service` and `alias`, both with a mutator configured for
`SKIP`. A Model Writer can pass the same completed Service to both. The handler is omitted, and both links preserve
the identity of the existing Service.

(See: `LifecycleParticipantSealedTest#'skips completed LINK targets before constructing handlers in #phase'`.)

```groovy
def completed = Service.Create.With { value 'completed' }
def application = Application.Create.With {
    service completed
    alias completed
}

assert application.service.is(completed)
assert application.alias.is(completed)
```

## Annotation Closure members

A handler can evaluate an ordinary Groovy annotation Closure under the domain library's own contract. For example,
a `FactBinding` handler can construct and call the Closure in `@FactBinding({ [messaging: 'facts'] })` to obtain a
literal mapping. The handler supplies the interpreter and any delegate. See
`LifecycleParticipantClosureProbeTest#'ordinary annotation map configures a relationship without a Klum Closure API'`.

## Diagnostics

Declaration defects are reported during Schema compilation or as a `KlumSchemaException` when checking binary
declarations. Participant construction, invocation, and assignment failures use `KlumModelException` with the original
cause and the annotation, handler, phase, and field or Schema type context.

For domain findings, use the [validation reporter with an explicit target](Validation.md#participant-validation).
Throwing from a handler aborts the lifecycle. Reporting a finding on an owned Builder carries it through
materialization to the completed Model for Verify.

[ADR 0028](../adr/0028-annotation-driven-lifecycle-participants.md)
records the accepted contract. The [final acceptance record](../implementation/issue-867-lp8-evidence.md)
links the qualification, tests, and deferred capabilities.
