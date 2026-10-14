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

Requests are repeatable, using one selected type per request:

```groovy
@PropagateAnnotation(AutoCreate)
@PropagateAnnotation(Required)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface RequiredService {}
```

Other legal default-only selections include `Builder.Query` on methods and `Cluster` on its supported
fields/methods. The generated Builder/Factory contracts and source mirrors match equivalent direct
annotations. The selected annotation's normal shape, coannotation and lifecycle restrictions still apply.

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

Propagation errors identify the domain annotation, selected type and Schema declaration. KlumCast
validates the final effective annotation set through its supported contributor seam; its constraint
errors include contribution provenance. Ordinary local transformations retain their normal phases.
There is no general ordering guarantee for unrelated AST rewriting or annotations introduced after the
contributor handoff.

Normal SOURCE/CLASS/RUNTIME retention applies to the original and added annotations. Runtime consumers
need no propagation interpreter or compiler. Recompile consuming Schemas after changing requests;
previous binaries retain their emitted annotations and behavior. When replacing repeated explicit
annotations with a propagating policy, remove the now-duplicate explicit annotation.

This feature uses KlumCast `0.5.0-rc.2` consistently across annotations, SPI and compiler dependencies.
Class-level AutoCreate defaults remain separate work under [#888](https://github.com/klum-dsl/klum-ast/issues/888).
