# Annotation Propagation

`@PropagateAnnotation` lets a domain annotation request a bare annotation on the DSL Schema class,
field or method carrying it. Each selected annotation uses its own declared defaults. Domain member
values remain on the original annotation and are never forwarded to the selected annotation.
Direct annotations remain supported, including their configured members.

## Default-only composition

Compile reusable domain annotations in an annotation library before compiling the consuming Schemas,
as required by their ordinary KlumCast constraints. No lifecycle participant or custom handler is needed
for this example.

(See: `AnnotationPropagationTest#'AutoCreate keeps retained DefaultValues members and normal lifecycle behavior'`.)

```groovy
// Annotation library
@DefaultValues
@PropagateAnnotation(AutoCreate)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Configured { String name() default '' }

// Consuming Schemas
@DSL class Service { String name }
@DSL class Application {
    @Configured(name = 'orders') Service child
}

assert Application.Create.One().child.name == 'orders'
assert Application.Create.With { child { name 'supplied' } }.child.name == 'supplied'
```

Here the copied bare `@AutoCreate` creates the child through the normal lifecycle. The retained
`@Configured(name = 'orders')` then supplies DefaultValues. Supplying the child explicitly keeps its
ordinary precedence. Customized AutoCreate `key`, `type` or `value` arguments must remain explicit;
use a domain annotation that does not also request AutoCreate for that case.

## Creating related services

Each annotated relationship makes its own creation request. AutoCreate can therefore create a missing
messaging object and its topics, while DefaultValues reads the retained domain members on each field.
These are ordinary concrete, unkeyed owned relationships; the policy does not select existing providers.

(See: `AnnotationPropagationDocumentaryTest#'creates related services while retaining domain defaults'`.)

```groovy
// Compile this domain annotation in the annotation library first.
@DefaultValues
@PropagateAnnotation(AutoCreate)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ServiceDefaults { String name() default '' }

@DSL class Topic { String name }
@DSL class Messaging {
    String name
    @ServiceDefaults(name = 'orders') Topic orderTopic
    @ServiceDefaults(name = 'results') Topic resultTopic
}
@DSL class Service { String name }
@DSL class Application {
    @ServiceDefaults(name = 'messaging') Messaging messaging
    @ServiceDefaults(name = 'database') Service database
    @ServiceDefaults(name = 'workload') Service workload
    @ServiceDefaults(name = 'api') Service api
}

def application = Application.Create.With {
    messaging { orderTopic { name 'priority-orders' } }
}
assert application.messaging.name == 'messaging'
assert application.messaging.orderTopic.name == 'priority-orders'
assert application.messaging.resultTopic.name == 'results'
assert application.database.name == 'database'
assert application.workload.name == 'workload'
assert application.api.name == 'api'
```

## Keeping customized creation explicit

A defaults-only policy can configure an existing child without requiring that child to exist. Use that
policy with explicit AutoCreate when a particular relationship needs a configured `key`, `type` or
`value`. Do not also request AutoCreate from that domain: explicit plus propagated is always a duplicate,
even when the arguments would agree.

(See: `AnnotationPropagationDocumentaryTest#'keeps optional defaults and customized creation explicit'`.)

```groovy
// Separately compiled annotation library: no propagation request here.
@DefaultValues
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ServiceDefaults { String name() default '' }

@DSL class Service { String name }
@DSL class Database extends Service { String engine = 'postgres' }
@DSL class Application {
    @ServiceDefaults(name = 'optional') Service optional
    @AutoCreate(type = Database)
    @ServiceDefaults(name = 'orders') Service database
}

def application = Application.Create.One()
assert application.optional == null
assert application.database instanceof Database
assert application.database.name == 'orders'
assert application.database.engine == 'postgres'
assert Application.Create.With { optional { name 'supplied' } }.optional.name == 'supplied'
```

These are alternative domain definitions for different creation policies. The domain's Schema Developer
chooses whether an annotation implies existence or only supplies defaults when a child is present.
A lifecycle participant is optional and continues to receive the original domain annotation; propagation
adds no handler or provider-selection behavior.

## Repeatable requests and containers

Requests are repeatable, using one selected type per request. Java and Groovy annotation libraries
may spell the requests separately or use the explicit nested `PropagateAnnotation.List` container.
Both forms have the same default-only behavior in source and compiled metadata.

(See: `AnnotationPropagationContributionTest#'repeated and explicit request containers retain domains and selected defaults (#kind, #policy)'`.)

```groovy
@PropagateAnnotation(AutoCreate)
@PropagateAnnotation(Required)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface RequiredService {}
```

The explicit spelling for those requests is:

```groovy
@PropagateAnnotation.List([
    @PropagateAnnotation(AutoCreate), @PropagateAnnotation(Required)
])
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface RequiredService {}
```

Genuine repeatable containers of applied domain annotations also contribute their nested domain uses,
including a container's default `value`. The physical container and original members remain intact.
An arbitrary annotation-array member is not a domain container: the component annotation must declare
that exact container through `@Repeatable`. Two nested uses selecting the same annotation still fail.

Other legal default-only selections include `Builder.Query` on methods and `Cluster` on its supported
fields/methods. The generated Builder/Factory contracts and source mirrors match equivalent direct
annotations. The selected annotation's normal shape, coannotation and lifecycle restrictions still apply.

## Inherited and generated contracts

An abstract DSL base expands its own declarations once. Same-unit subclasses inherit the resulting
behavior; subclasses compiled later inherit the emitted parent contract without expanding it again.
Overrides use their own annotations. DSL field shadowing remains an error.

(See: `AnnotationPropagationContractTest#'abstract bases retain public Query and Cluster contracts through #binary inheritance'`.)

```groovy
// Compiled annotation library
@PropagateAnnotation(Builder.Query)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface ReadOnly {}

@PropagateAnnotation(Cluster)
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
@interface Group {}

// Consuming Schemas
@DSL class Service { String name }
@DSL abstract class ApplicationBase {
    String name
    @Group Map<String, Service> services
    @ReadOnly String summary() { name.toUpperCase() }
}
@DSL class Application extends ApplicationBase { Service secondary }

def application = Application.Create.With {
    name 'orders'
    assert summary() == 'ORDERS' // current Builder state
    services { secondary { name 'typed' } }
}
assert application.summary() == 'ORDERS'
assert application.services.secondary.name == 'typed'
```

Query and Cluster retain the same public `Application_DSL.Builder`, `Factory` and nested cluster
factory signatures as direct annotations, including Java and statically compiled Groovy consumers.
IDE source mirrors retain the original Query domain annotation as well as the selected annotation;
those projections are output, never new propagation sites. Editor recognition is a separate qualification.

Required and Optional continue through their ordinary local converters exactly once. A propagated
Required uses an empty message and ERROR level even when the domain has similarly named configured
members; explicit Required can still customize both. Optional lowers to `Validate.Ignore`, including
under class-level Validate. Non-DSL uses of these direct shortcuts keep their existing behavior.
The current direct Required shortcut does not reject primitive boolean during compilation, unlike
direct Validate; propagation preserves this existing limitation. Prefer boxed Boolean for required flags.

AutoCreate retains normal assignment, ownership, Templates and supplied-child precedence. Direct
field creation precedes Cluster fallback; creation precedes participant mutation and callbacks.
Cluster fallback does not replay skipped field mutations, and children created in later phases do
not catch up earlier AutoCreate callbacks. Bare AutoCreate retains keyed/abstract creation failures
and same-phase Creator conflicts.

## Combining propagation with Bean Validation composition

A domain annotation can carry a configured Bean Validation constraint and also request annotation propagation.
The two mechanisms have different responsibilities: KlumAST adds the selected bare annotation during Schema
compilation, while the Bean Validation provider interprets the constraints on the retained domain annotation
during completed-Model validation. Install the optional
[Bean Validation module](Validation.md#jsr380-validation) for the latter behavior.

(See: `BeanValidationDocumentaryTest#'combines propagated presence with a composed Pattern constraint'`.)

```groovy
// Compile this domain annotation in the annotation library first.
@PropagateAnnotation(Required)
@Constraint(validatedBy = [])
@Pattern(regexp = '[A-Z]{2}', message = 'Use two uppercase letters')
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ReleaseCode {
    String message() default 'Invalid release code'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@DSL class Release {
    @ReleaseCode String code
}

def release = Release.Create.With { code 'GA' }
assert release.code == 'GA'
// code 'bad' fails the composed Pattern; omitting code fails the propagated Required rule.
```

Here `Constraint`, `Payload` and `Pattern` are Jakarta Validation types; `Required` and
`PropagateAnnotation` are Klum annotations. `@Constraint(validatedBy = [])` makes `ReleaseCode` a composed
Bean constraint without an additional custom validator. The configured Pattern stays on `ReleaseCode`;
it is not copied onto `Release.code`. The domain annotation remains on that field. The provider follows
the composition and checks its Pattern, while the propagated `Required` uses ordinary Klum validation.
Pattern accepts null, so the separate presence rule is meaningful. Neither mechanism requires a custom
lifecycle participant.

`@PropagateAnnotation(Pattern)` has a different meaning: it requests a new **bare** `@Pattern`, whose
required `regexp` member has no default, and is therefore rejected. Putting a configured Pattern beside
that propagation request does not supply its members. Propagation selects an annotation type, not a
configured annotation occurrence; it neither copies meta-annotation values nor forwards domain members.
For the example above, propagate `Required` and let Bean Validation consume the configured Pattern in place.

More generally, propagation can select a composed Bean constraint whose own members all have defaults,
whose target permits the Schema declaration, and which does not itself request further propagation.
Its composing constraints remain owned by the provider.
Klum's one-hop propagation limit does not redefine the provider's composition semantics. Avoid expressing
the same validation requirement through both a direct annotation and a composed constraint unless both
checks are intended: propagation's duplicate-type checks do not deduplicate Bean Validation's composition graph.

Runtime composition does not imply [JSON Schema projection](Json-Schema.md#conservative-pattern-projection).
The current Pattern projection supports direct eligible field declarations; composed or aliased Patterns
remain descriptive. This advanced use case changes neither that projection boundary nor raw-input validation.

## Boundaries and diagnostics

Propagation executes only on original declarations of explicitly annotated DSL Schemas, including
abstract Schemas. Annotation definitions are metadata, not execution sites. Ordinary classes, DSL
interfaces, constructors, parameters, packages and type uses do not expand requests. Inherited source
declarations expand at their declaring Schema; already compiled parents keep their emitted behavior.
`DSL` cannot be selected. Generated Builders, projections and IDE mirrors do not expand requests again.

Every selected type must be an annotation, permit the declaration's target, and have a default for every
member. A selected annotation that itself requests propagation is rejected: requests are one-hop only.
Every duplicate involving propagation is an error, including explicit-plus-propagated, repeated requests,
multiple domain contributors and repeatable selected types. There is no precedence, merge or silent
deduplication. Ordinary explicit-only repeatable annotations keep their normal behavior.

Propagation errors identify the domain annotation, selected type and Schema declaration. Duplicate
errors identify both contributions, including occurrence numbers or container `value` indexes. Defaulted
containers report the applied container's source position; downstream Required/Optional lowering and
Query/Cluster projections preserve applied annotation positions. KlumCast
validates the final effective annotation set through its supported contributor seam; its constraint
errors include contribution provenance. Ordinary local transformations retain their normal phases.
There is no general ordering guarantee for unrelated AST rewriting or annotations introduced after the
contributor handoff.

`AnnotationCollector` is not a propagation mechanism. Selecting a collector type is rejected before
local rewriting. Combining `@AnnotationCollector` and `@PropagateAnnotation` on a domain definition is
unsupported: Groovy can replace the domain use before the contributor sees it, moving the propagation
marker onto the Schema declaration. Source and compiled-library controls produce Groovy's ordinary
“PropagateAnnotation is not allowed on element FIELD” diagnostic for a field use. The original domain
identity is already unavailable at this boundary. Keep propagating domains as ordinary annotations;
do not rely on collector modes to merge or prioritize them. Other rewrites are qualified only for the
effective annotation set present at KlumCast's handoff, with no late rescan or scheduling promise.

Normal SOURCE/CLASS/RUNTIME retention applies to the original and added annotations. Runtime consumers
need no propagation interpreter or compiler. Recompile consuming Schemas after changing requests;
previous binaries retain their emitted annotations and behavior. When replacing repeated explicit
annotations with a propagating policy, remove the now-duplicate explicit annotation.

## Dependency and editor requirements

Use a matching KlumAST 4.1 compiler and annotation library that both provide this feature. Adding the
marker library to an older compiler is insufficient. The qualified dependency pairing uses KlumCast
`0.5.0` consistently across annotations, SPI and compiler, with Java 17 and Groovy 3/4/5
(Groovy 3 on the classpath; Groovy 4/5 also in named modules). This is development qualification, not
a promise that an older or mixed compiler/annotation combination works.

IntelliJ/editor recognition of a domain annotation as its selected annotation remains unqualified.
Compiled contracts, typed consumers and generated source mirrors do not establish source-editor alias
recognition. See [migration guidance](Migration.md#composing-domain-annotations) for recompilation and
explicit-annotation cleanup.

## Choosing the right mechanism

| Need | Mechanism |
| --- | --- |
| A domain use implies bare AutoCreate (or another legal default-valued annotation) | Propagation on that domain annotation |
| Configure a selected annotation's members | A direct annotation; propagation does not copy configured instances or forward members |
| Package configured Bean constraints in a domain annotation | Bean Validation composition; the optional Bean Validation module evaluates it |
| Combine a configured Bean constraint with a bare Klum annotation | A domain annotation carrying both Bean composition and a propagation request, as above |
| Defaults on fields that have no domain annotation | Separate class-level policy work under [#888](https://github.com/klum-dsl/klum-ast/issues/888) |
| Reuse Schema source declarations | Separate source composition work under [#548](https://github.com/klum-dsl/klum-ast/issues/548) |
| Execute arbitrary or recursively nested meta-annotations | Outside propagation's one-hop contract |
| Select or bind ScHelm providers | ScHelm's own policy, outside KlumAST propagation |

KlumAST supplies generic propagation. A consumer such as ScHelm owns its domain annotation meanings,
provider selection and the decision to create or only bind when present. Compiler qualification does
not establish acceptance of a particular consumer integration.

## Bean composition and documentary meta-annotations

A meta-annotation is an annotation placed on an annotation type. Jakarta Bean Validation calls the outer annotation a
composed constraint and its hosted constraints composing constraints. This differs from a KlumAST propagated annotation:
`@PropagateAnnotation` requests the existing one-hop propagation behavior; Bean composition retains the outer constraint.

`@InputConstraintDescription('literal text')` may coexist on either kind of domain annotation. It adds prose to input
artifacts under PROJECT/TEXT_ONLY and is suppressed under OMIT, without adding enforcement or examples. Qualified
first-level Bean composing constraints reuse the existing built-in JSON Schema mappings independently of propagation.
See [the supported composition boundary](Json-Schema.md#composed-constraints-and-documentary-meta-annotations).
