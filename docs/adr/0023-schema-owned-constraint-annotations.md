# Schema-owned constraint annotations on completed DSL Objects

Date: 2026-09-29

Status: Accepted

Implementation status: S0 compatibility proof passed; annotation member encoding selected; production implementation pending

Target: candidate 4.1 quality-of-life feature, gated by S0; no release commitment yet

Tracking issue: [#799 — Add constraint meta-annotations analogous to @DefaultValues](https://github.com/klum-dsl/klum-ast/issues/799)

Implementation plan: [ADR 0023 implementation plan](../implementation/adr-0023-schema-owned-constraint-annotations.md)

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0006](0006-completed-object-support.md), [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
and [ADR 0014](0014-groovy4-jpms-boundary.md).

## Context

`@DefaultValues` lets a Schema Developer place domain-specific, runtime-retained annotations on a `@DSL` class or an
owned child field. Its field use configures a constructed child DSL Object. Its current implementation applies *every
nondefault annotation member* as a default. A single domain annotation containing both `slots` and
`minSlots`/`maxSlots` would incorrectly attempt to write the bounds as defaults. The
maintainer has clarified that a constraint annotation on a Schema field is the required outcome; a combined defaults and
constraints annotation is desirable but optional. Two domain annotations on the same field are acceptable.

`@Validate` already offers a typed field-value closure, model methods, and validation classes. Those rules are written on
the model declaration and cannot receive attributes of a distinct domain annotation instance. The optional Jakarta Bean
Validation adapter has its own constraint vocabulary and dependency. Neither gives #799 a Klum-owned metadata source for
domain annotations without an added model property. ADR 0003 fixes the relevant lifecycle: defaults and `@PostTree` run
on Builders, materialization follows, and `VALIDATE` runs on completed DSL Objects. The core field validator already
iterates each completed Model's declared fields and reads their resolved values by reflection. In contrast, composition
traversal skips `LINK` fields, so a child-visit-only design would miss a primary use case. Validation issues already store
object path, member, message, and level.

## Decision

### Keep the annotation on the Schema

Add a public, runtime-retained meta-annotation (working name `@ConstraintValues`) in the annotations artifact. A Schema
Developer places it on a runtime-retained domain annotation used on a DSL relationship field: owned composition,
`LINK`, or `OPTIONAL_LINK`. The *concrete domain annotation instance* and the completed, resolved field value are inputs
to one rule. Collections and maps are evaluated per non-null DSL Object element. A null or unresolved optional value
has no target and causes no constraint evaluation; presence is a separate `@Required`/`@Validate` concern. Scalar fields,
methods, packages, class declarations, and arbitrary nested annotation chains are outside the first contract. The
compiler should reject unsupported placements rather than produce a runtime surprise. Class-declaration support may be
considered after the field contract is proven; it is not a condition for the first 4.1 slice.

The selected meta-annotation member encoding is `Class<? extends Closure> value()`, authored with a typed two-parameter
Groovy closure receiving `(domainAnnotation, completedModel)`. The public marker name remains to be chosen. This class
literal preserves Groovy's annotation closure syntax; a `BiFunction` or other SAM class literal would require a named
rule class, and its generic arguments would not retain the concrete annotation and relationship types at runtime.
The compiler must inspect the authored closure's two parameters. It must accept either a single Groovy-truth expression
or an assertion, as `@Validate` does: turn the former into an assertion while leaving the latter's assertion semantics
intact. Normal completion of an assertion closure succeeds; a failed assertion retains its message. A runtime helper
would invoke the closure under the current validation context and convert a failed assertion or exception to a normal
`KlumValidationIssue` on the **source owner Model's** result,
with its existing path and the annotated field as member. The message identifies the concrete constraint annotation and
the failing assertion; for collections/maps it also identifies the entry index/key. This is relationship-local validation:
two annotated fields pointing to the same completed object are evaluated independently and produce issues under their
respective source fields. The rule reads the linked target without mutating, owning, or rerunning its lifecycle or
validators. Keep the existing validation reporter available inside the callback for advanced messages, and preserve
`@Validate` assertion/exception result semantics. A focused Groovy 3 probe of existing `@Validate` on a
`LINK` field compiled a typed closure to accept the target's **Builder**, then passed a completed Model at validation,
causing a method-signature issue. The S0 meta-annotation probe proves completed-Model parameter typing independently;
wrapping the existing field-closure path would not provide that guarantee.

The schema shape below is domain-neutral. It illustrates the selected callback encoding with a provisional public
marker name. Separate domain annotations satisfy the accepted behavior without changing `@DefaultValues`:

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

    @Field(FieldType.LINK)
    @PoolBounds(minSlots = 5, maxSlots = 10)
    Pool sharedPool
}
```

The constraint annotation has no corresponding `Plan` or `Pool` validation-only property. The names are illustrative.
This preserves the #799 shape without making its Kafka example the Klum API.

### Gate callback typing on the declared relationship target

The callback's target parameter is checked against the **declared relationship target type**, not a subtype that happens
to be present when validation runs. For an owned `Pool pool` or a `@Field(FieldType.LINK) Pool pool` whose completed value
is a `KafkaPool extends Pool`, a callback accepting `(PoolBounds, Pool)` must receive that completed `KafkaPool` instance.
The same Schema must reject a callback requiring `(PoolBounds, KafkaPool)` at Schema compilation: a `Pool` relationship
could later resolve to a different subtype. S0 also proves that an appropriate supertype parameter such as `Object`
is valid. The assignability check is against the
declared type (`callbackTargetType.isAssignableFrom(declaredTargetType)` for ordinary nominal types), independent of the
observed runtime value. The first parameter must accept the concrete domain annotation type, and the callback must have
exactly two parameters.

The S0 proof exercises these positive and negative cases for owned and `LINK` relationships in authored source and
in a separately compiled consumer across Groovy 3/4/5 and JPMS where applicable. This is an acceptance
rule for validating the selected callback encoding, not a freeze of the provisional `@ConstraintValues` name above.

### S0 proof and compiler boundary

The non-shipping `ConstraintCallbackProbeTest` demonstrates the selected encoding across Groovy 3, 4, and 5. At
`VALIDATE`, its owner reads completed `KafkaPool` values through owned, external `LINK`, same-root `LINK`, and
`OPTIONAL_LINK` fields. The typed closure receives the concrete bounds annotation and `Pool`, not `Pool$Builder`.
An `Object` target parameter also accepts a declared `Pool`; a `KafkaPool` parameter is rejected against that declared
type for both owned and `LINK` fields. A test-only semantic-analysis guard demonstrates this rejection in authored source
and in a separately compiled annotation consumer. External target identity, root model path, stored validation result,
and validation count stay unchanged; absent optional values skip the callback.

The extended `JpmsPackageBoundaryTest` separately compiles a Java consumer against the Schema and invokes the reflected
callback on the classpath in all three Groovy lanes and in named modules in Groovy 4 and 5. The generated closure,
concrete annotation, and Model share the Schema module and classloader. The named-module fixture needs no additional
package opening to Groovy or the consumer. These tests prove feasibility, not the production marker, compiler check,
evaluator, failure attribution, or reporter behavior.

For S1, implement placement and signature diagnostics through the existing KlumCast validation SPI where possible:
`@KlumCastValidated`, `@KlumCastValidator`, and a KlumAST-owned `Check` using `CheckContext`, following
`DefaultValuesCheck`. The test-only S0 guard is not the production implementation. Do not change KlumCast or create a
KlumCast issue unless S1 demonstrates that the SPI's generic context is insufficient for a required check. The
single-expression-to-assertion conversion must preserve the authored `(domainAnnotation, completedModel)` parameter
types and must not reuse the `@Validate` field closure's Builder projection.

### Keep combined annotations optional

The required 4.1 contract does not change `@DefaultValues`. A future single domain annotation or nested-annotation
wrapper may be designed separately if a concrete user case justifies its added mapping rules. Do not use
`ignoreUnknownFields` as an implicit way to mix default and constraint attributes: it can hide a typo and depends on
incidental Model field names. A selective `@DefaultValues` member allowlist is one possible follow-up, but its spelling
and semantics are undecided and do not gate the field constraint feature.

### Run in the existing validation lifecycle

The constraint evaluator belongs in the existing `VALIDATE` action, after all ordinary/default/external configuration and
materialization. The narrow first seam is the existing `KlumFieldAnnotationsValidator`: it receives each completed source
Model through `InstanceValidator`, walks the declared fields in each DSL hierarchy layer, and can read the final field
value directly. This works for composition, `LINK`, and `OPTIONAL_LINK` without widening composition traversal or the
public `InstanceValidator` signature. Constraint processing must be independent of `@Validate.Ignore`/`@Optional`; those
controls govern ordinary `@Validate`, not a separate domain annotation. Keep validator memoization per **source Model**.
Add issues to that source Model's stored `KlumValidationResult`; `VERIFY` keeps the existing fail level and skip policy.

Read each annotated relationship value without changing it. Evaluate each distinct source field independently, including
two fields or container entries that point to one completed target. For a collection/map, retain the field name as the
issue member so existing `suppressOn(field)` behavior remains intelligible, and put the entry index/key in the message.
If a collection contains the same target twice at different positions, entry context keeps both failures distinct in the
result set. A null field or null entry is skipped; an unresolved optional link receives no special synthetic failure.
No constraint evaluator should call the linked target's factory, lifecycle, `InstanceValidator`s, or ownership APIs.
An ephemeral Groovy 3 probe using an owner `@Validate` method confirmed that a completed external target was readable
through two `LINK` fields and an `OPTIONAL_LINK` field during validation; three issues landed under the source owner with
distinct field members, the external target's stored result stayed unchanged, and an absent optional value did not cause
an issue when treated as optional. S0/S1 must prove the new callback path, same-root links, and Groovy 4/5/JPMS cases.
If that path cannot use the owner field validator narrowly, report the smallest required seam and its cost before
implementation. Avoid promising an order between independent annotations or validators beyond the phase boundary;
results are collected as usual.

### Keep metadata inspectable without exposing companion internals

The marker and concrete annotation are runtime-visible Schema metadata. Tooling can inspect the annotation type's
marker and the concrete annotation instance on the Schema field, including its bounds, through ordinary Java reflection.
This is the accepted initial tooling contract; it needs no generated `Foo_DSL` method, domain Model field, companion
metadata Map, wire-format property, or new `KlumObjectSupport` method. An explicit discovery facade would require a
consumer and separate design evidence. AnnoDocimal source mirrors describe generated Builder/Factory contracts, not
these source-owned annotations;
their generation should stay unchanged, with a test proving the annotation remains on compiled Schema declarations.

## Consequences and acceptance boundary

- One public meta-annotation is added to Schema vocabulary; direct annotation reflection is the metadata contract.
  Existing default annotations keep their semantics.
- A failed rule reports the source Model's normal validation path and field member, with the concrete constraint name,
  entry context when relevant, message, and level. It contributes to stored results and obeys `VERIFY` without changing
  the phase order. The linked target's stored result remains untouched.
- No public Model property, generated Builder signature, or serialization format is added. Ordinary Schema annotations
  remain on the class/field in bytecode; validation results continue to serialize as ADR 0003 specifies.
- Groovy 3 classpath and Groovy 4/5 classpath plus named-module consumers must pass the same source and separately
  compiled binary cases. The annotation artifact must not acquire a runtime-module dependency.
- The planned field scope includes owned, `LINK`, and `OPTIONAL_LINK` DSL relationships. Direct scalar constraints remain
  covered by `@Validate` or optional Bean Validation; class-declaration support and a broader general field-constraint
  framework need further evidence.

## Rejected alternatives

- Add `min`/`max` fields to each public Model: exposes Schema validation knobs to Client Developers.
- Encode property names or expressions as strings: duplicates a path language and loses typed model access.
- Reuse `ignoreUnknownFields` to mask bounds in `@DefaultValues`: a future same-named field would receive an accidental
  default, and misspellings become invisible.
- Start a new validation phase or rewrite the optional Jakarta adapter: the required lifecycle and issue aggregation
  already exist in core.
- Expose raw Model companion metadata or generate support-interface members: conflicts with ADR 0006 and adds an
  unneeded client contract.

## Implementation gate

The maintainer accepts owned, `LINK`, and `OPTIONAL_LINK` relationship fields; source-owner path and field-member
attribution; independent checks for each annotated source field or entry; null optional values skipping constraint
evaluation; and no mutation, reownership, or target lifecycle rerun. Runtime annotation reflection is sufficient initial
tooling metadata, and separate default and constraint annotations satisfy #799's core.

S0 has established the closure encoding and completed-Model boundary across Groovy 3/4/5, separately compiled binaries,
and Groovy 4/5 JPMS. S1 must implement and test the public marker and KlumCast compiler check, both accepted closure
forms, reporter compatibility, source attribution, repeated references, optional-null behavior, and lifecycle/identity
boundaries end to end. The public marker name and production diagnostics remain open within that implementation slice.
Acceptance of this ADR does not mean those implementation gates have passed or that a 4.1 release is committed.
