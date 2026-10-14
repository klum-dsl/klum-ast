# Annotation Propagation

`@PropagateAnnotation` lets a domain annotation request a bare annotation on the DSL Schema class,
field or method carrying it. Each selected annotation uses its own declared defaults. Domain member
values remain on the original annotation and are never forwarded to the selected annotation.

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

This feature uses KlumCast `0.5.0-rc.2` consistently across annotations, SPI and compiler dependencies.
Class-level AutoCreate defaults remain separate work under [#888](https://github.com/klum-dsl/klum-ast/issues/888).
