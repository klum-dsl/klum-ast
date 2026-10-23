# WIRE-1 keyed-map semantic stop

Date: 2026-10-22. Status: **maintainer approved compiler exclusion; correction validated**.
Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932),
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[approved runtime plan](../adr-0031-wire-runtime-v1.md).

## Reproduced contradiction

The compiler admits the following field as `KEYED_OWNED_MAP`, with String keys, the concrete nested Model/Builder identity,
and no rejection:

```groovy
@DSL class Configuration {
    @Field(keyMapping = { it.route })
    Map<String, Entry> entries
}
@DSL class Entry {
    @Key String name
    String route
    String value
}
```

The approved input would have this ordinary nested shape:

```groovy
[entries: ['a/b~c': [route: 'mapped', value: 'first'],
           second: [route: 'mapped', value: 'second']]]
```

The runtime plan requires literal outer keys and reuse of normal child creators/attachment. Actual normal creation
with the supplied keys produces only `[mapped: <second child>]`: `a/b~c` and `second` both disappear, the second child
replaces the first, and the retained child's own `name` is still `second`. This is existing Builder behavior, not a bug
in ordinary DSL construction.

`StructuredInputKeyMappingBoundaryTest` compiles/packages this Schema, reads its actual carrier, and exercises its normal
generated `entry(String, Closure)` creator twice. It confirms admission and the remapping/collision without implementing
an interpreter or adding a new attachment route. The behavior passes unchanged on Groovy 3, 4 and 5.

The existing runtime path is `addNewDslElementToMap` -> `doAddElementToMap` -> `determineKeyFromMappingClosure`.
That last helper evaluates the Schema's mapping closure even when an explicit non-null key was supplied. Consumer
configuration factoring around the existing creator would retain exactly this behavior. Evaluating mapping during
preflight violates the no-Schema-code rule; bypassing it at attachment changes ordinary Builder semantics and conflicts
with the required shared creator path. Rejecting only at runtime would silently narrow the compiler-admitted grammar.
The earlier collection clarification dealt with whole-container argument construction; it did not decide key remapping.

## Maintainer alternatives

1. **Recommended:** reject keyed owned-map fields that declare `@Field.keyMapping` in shared compiler admission, with a
   source-specific reason that WIRE-PLAN requires literal outer instance keys. Preserve ordinary DSL behavior, carrier
   format and generated signatures. Keep the existing explicit-key owned-map path for fields without keyMapping.
   The carrier is unreleased; no legacy interpretation or binary-policy machinery is proposed.
2. Explicitly revise the structured language to permit Schema-derived map keys and define how they interact with literal
   outer keys, duplicate derived keys, existing-child reuse and the #529 projection. This is a new semantic decision.

The maintainer approved alternative 1. The historical reproduction and alternatives below remain the decision record; compiler rejection coverage now replaces the earlier admission assertion. No structured-key policy is added. This evidence does not authorize key remapping, a new key encoding, a generic reflection
admission check, closure execution during preflight or bypassing ordinary attachment.

## Validation and delivery boundary

The qualification run passed 14 reader cases plus this 1 key-mapping case on each Groovy 3/4/5 lane, and 1 actual
named-module feature each on Groovy 4/5; zero failures/errors/skips. Module license and lane-isolation checks pass.
See [reader qualification](issue-932-wire-1-reader.md) for the exact command, binary provenance and scope limits.
Manual review and documentation/diff checks pass for this evidence-only boundary; production dual review remains pending.

Private reader qualification is complete as a preparatory checkpoint. Consumer factoring, preflight, dispatcher, public
facade, final user documentation and full runtime acceptance are still pending. No push, PR, issue closure or merge.
#932/#529 remain open. The task requests Hive/maintainer resolution of this concrete contradiction, not archive reconciliation.

## Accepted correction

The compiler excludes a keyed owned map when its source `@Field` declares `keyMapping`, recording
`literal structured instance keys conflict with @Field.keyMapping-derived keys` against the owning source field.
The original remapping/collision reproduction remains executable as ordinary Builder DSL coverage; its carrier
assertions now require the rejection. An adjacent ordinary keyed owned map remains admitted and preserves both
`a/b~c` and `second` literally even when their children have the same `route` value.

No runtime-only guard, generated contract or attachment change implements the policy. Numeric and container admission
rules are unaffected. After focused correction validation, resume the approved runtime slice without another decision
request. Full runtime delivery and its final qualification remain separate from this bounded policy correction.

Correction validation: the changed assertion first failed against the old compiler admission (Groovy 3), then passed
with the compiler correction. The key-mapping, admission, collection-contract and reader suites pass **69 cases per
Groovy 3/4/5 lane** (1 + 31 + 23 + 14), zero failures/errors/skips. Module license, lane isolation, Markdown links/fences
and `git diff --check` pass. The correction is committed separately before continuing runtime implementation.
