# Schema-owned constraint annotations on completed DSL Objects

Date: 2026-09-29

Status: Proposed — maintainer contract decision and compatibility tracer required

Target: candidate 4.1 quality-of-life feature; no release commitment yet

Tracking issue: [#799 — Add constraint meta-annotations analogous to @DefaultValues](https://github.com/klum-dsl/klum-ast/issues/799)

Implementation plan: [ADR 0023 implementation plan](../implementation/adr-0023-schema-owned-constraint-annotations.md)

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0006](0006-completed-object-support.md), [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
and [ADR 0014](0014-groovy4-jpms-boundary.md).

## Context

`@DefaultValues` lets a Schema Developer place domain-specific, runtime-retained annotations on a `@DSL` class or an
owned child field. Its field use configures the **child DSL Object**, including each element of a composition collection or
map. Its current implementation applies *every nondefault annotation member* as a default. A single domain annotation
containing both `slots` and `minSlots`/`maxSlots` would incorrectly attempt to write the bounds as defaults. The
maintainer has clarified that a constraint annotation on a Schema field is the required outcome; a combined defaults and
constraints annotation is desirable but optional. Two domain annotations on the same field are acceptable.

`@Validate` already offers a typed field-value closure, model methods, and validation classes. Those rules are written on
the model declaration and cannot receive attributes of a distinct domain annotation instance. The optional Jakarta Bean
Validation adapter has its own constraint vocabulary and dependency. Neither gives #799 a Klum-owned metadata source for
domain annotations without an added model property. ADR 0003 fixes the relevant lifecycle: defaults and `@PostTree` run
on Builders, materialization follows, and `VALIDATE` runs on completed DSL Objects. The completed-model traversal already
has the owning container and field name for each owned child; validation issues already store object path, member, message,
and level.

## Proposed decision, subject to the gates below

### Keep the annotation on the Schema

Add a public, runtime-retained meta-annotation (working name `@ConstraintValues`) in the annotations artifact. A Schema
Developer places it on a runtime-retained domain annotation used on an owned DSL relationship field. The *concrete domain
annotation instance* and the completed child DSL Object are inputs to one rule, once per child for a collection or map.
A missing child has no target and is left to `@Required`/`@Validate`. `LINK` and `OPTIONAL_LINK` fields, scalar fields,
methods, packages, class declarations, and arbitrary nested annotation chains are outside the first contract. The
compiler should reject unsupported placements rather than produce a runtime surprise. Class-declaration support may be
considered after the field contract is proven; it is not a condition for the first 4.1 slice.

The narrow syntax probe is a typed Groovy closure on the meta-annotation, receiving `(domainAnnotation, completedModel)`;
its assertion message supplies the failure reason. A runtime helper would invoke it under the current validation context
and convert a failed assertion or exception to a normal `KlumValidationIssue`, using the target object's path and the
concrete constraint annotation's name as the member. The existing reporter can remain available inside the callback for
advanced messages. This reuses the present `@Validate` closure/result semantics. The closure encoding, self-referential
annotation type, classloader behavior, and parameter typing must pass the S0 Groovy 3/4/5 source-and-binary probe before
the API name or signature is accepted. If that probe fails, a typed rule class in the existing public runtime validation
package is the bounded fallback; an expression language is not.

The proposed schema shape is intentionally domain-neutral and provisional. Separate domain annotations satisfy the
required behavior without changing `@DefaultValues`:

```groovy
@DefaultValues
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface PoolDefaults {
    int slots() default 0
}

@ConstraintValues({ PoolBounds bounds, Pool pool ->
    assert pool.slots in bounds.minSlots()..bounds.maxSlots() :
        "slots must be within ${bounds.minSlots()}..${bounds.maxSlots()}"
})
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface PoolBounds {
    int minSlots() default 0
    int maxSlots() default Integer.MAX_VALUE
}

@DSL class Pool { int slots }
@DSL class Plan {
    @PoolDefaults(slots = 8)
    @PoolBounds(minSlots = 5, maxSlots = 10)
    Pool pool
}
```

The constraint annotation has no corresponding `Plan` or `Pool` validation-only property. The names are illustrative.
This preserves the #799 shape without making its Kafka example the Klum API.

### Keep combined annotations optional

The required 4.1 contract does not change `@DefaultValues`. A future single domain annotation or nested-annotation
wrapper may be designed separately if a concrete user case justifies its added mapping rules. Do not use
`ignoreUnknownFields` as an implicit way to mix default and constraint attributes: it can hide a typo and depends on
incidental Model field names. A selective `@DefaultValues` member allowlist is one possible follow-up, but its spelling
and semantics are undecided and do not gate the field constraint feature.

### Run in the existing validation lifecycle

The constraint evaluator belongs in the existing `VALIDATE` action, after all ordinary/default/external configuration and
materialization. It must consume the traversal's owner/field context for field annotations; `InstanceValidator` currently
receives only `(instance, result)` and cannot discover the owning field reliably. Preserve its public signature and
memoization behavior. Add issues to the completed target's stored `KlumValidationResult`; `VERIFY` keeps the existing fail
level and skip policy. The evaluator must not mutate the completed model or retain a Builder in model metadata.

For owned collections/maps, the normal composition traversal supplies the child path and source field for each visited
element. Field-sourced rules must check that source field is an eligible owned-composition declaration. Avoid promising
an order between independent annotations or validators beyond the phase boundary; results are collected as usual.

### Keep metadata inspectable without exposing companion internals

The marker and concrete annotation are runtime-visible Schema metadata. Tooling can inspect the annotation type's
marker and the concrete annotation instance on the Schema class or field, including its bounds, through ordinary Java
reflection. The first slice needs no generated `Foo_DSL` method, domain Model field, companion metadata Map, wire-format
property, or new `KlumObjectSupport` method. An explicit discovery facade would require a consumer and separate design
evidence. AnnoDocimal source mirrors describe generated Builder/Factory contracts, not these source-owned annotations;
their generation should stay unchanged, with a test proving the annotation remains on compiled Schema declarations.

## Consequences and acceptance boundary

- One public meta-annotation is added to Schema vocabulary; direct annotation reflection is the metadata contract.
  Existing default annotations keep their semantics.
- A failed rule reports the completed target's normal validation path, the concrete constraint name, message, and level.
  It contributes to stored results and obeys `VERIFY` without changing the phase order.
- No public Model property, generated Builder signature, or serialization format is added. Ordinary Schema annotations
  remain on the class/field in bytecode; validation results continue to serialize as ADR 0003 specifies.
- Groovy 3 classpath and Groovy 4/5 classpath plus named-module consumers must pass the same source and separately
  compiled binary cases. The annotation artifact must not acquire a runtime-module dependency.
- The planned field scope mirrors `@DefaultValues` child semantics. Direct scalar constraints remain covered by
  `@Validate` or optional Bean Validation; class-declaration support and a broader general field-constraint framework
  need further evidence.

## Rejected alternatives for this bounded proposal

- Add `min`/`max` fields to each public Model: exposes Schema validation knobs to Client Developers.
- Encode property names or expressions as strings: duplicates a path language and loses typed model access.
- Reuse `ignoreUnknownFields` to mask bounds in `@DefaultValues`: a future same-named field would receive an accidental
  default, and misspellings become invisible.
- Start a new validation phase or rewrite the optional Jakarta adapter: the required lifecycle and issue aggregation
  already exist in core.
- Expose raw Model companion metadata or generate support-interface members: conflicts with ADR 0006 and adds an
  unneeded client contract.

## Decisions still required before implementation

1. Confirm the intentionally narrow 4.1 field scope: owned DSL relationship fields, with each owned child as the rule
   target; scalar, class, and LINK targets remain outside the first contract.
2. After S0, accept the typed closure as the rule surface and choose its public name and error-member spelling. If S0
   disproves cross-Groovy viability, decide whether a typed rule-class fallback still fits 4.1.
3. Confirm that runtime-visible annotation reflection satisfies the initial tooling requirement; a public catalog API
   is deferred until a concrete documentation/tooling consumer needs it.

The maintainer has settled field annotation priority and allowed separate annotations for defaults and constraints. The
public rule surface and exact bounded targets remain proposed. This ADR remains **Proposed** until those decisions and the
S0 proof are recorded.
