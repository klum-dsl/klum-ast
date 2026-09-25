# Conservative named-map safety across compiler metadata, runtime diagnostics, and IDEs

Date: 2026-09-25

Status: Accepted

Target release: 4.1 for the bounded metadata, runtime-diagnostic, and native-IntelliJ work; later 4.x for Eclipse/DSLD exploration

Tracking issue: [#487 — Investigate compile-time safety for named parameters](https://github.com/klum-dsl/klum-ast/issues/487)

Implementation plan: [ADR 0022 implementation plan](../implementation/adr-0022-conservative-named-map-safety.md)

Successor proposals: [Issue #487 successor proposals](../implementation/issue-487-successor-proposals.md)

Parent decisions:

- [ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md)
- [ADR 0005 — Generated DSL support API](0005-generated-dsl-support-api.md)
- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)

## Context

Named-map configuration is established syntax for a sequence of ordinary one-argument calls on an active Builder. Each
`key: value` entry means “invoke `key(value)` through normal Builder dispatch”; it is not a property assignment and does
not imply that `key` names a Model field. Root `Foo.Create.With(name: value)` calls and generated relationship creators
both implement that semantic through `InternalKlumBuilder.applyNamedParameters`, preserving map order and Groovy method
selection.

The resulting contract intentionally includes every public Builder operation callable with exactly one supplied value:
generated field configurators, explicit `setX` aliases, relationship operations and adders, converters, `copyFrom`,
inherited operations, and explicit one-argument `@Builder.Method`s. An explicit same-named Builder method wins through
normal method resolution. The `setX` spelling remains the direct-storage escape hatch when a same-named method
intentionally overrides field configuration.

This makes `copyFrom` useful intentional named-map syntax rather than an incidental implementation leak:

```groovy
outer {
    inner(copyFrom: somethingElse) {
        // additional configuration
    }
}
```

Here `copyFrom: somethingElse` invokes the child Builder's ordinary `copyFrom(somethingElse)` operation. The following
closure continues configuring that same child through the established relationship-creator semantics.

Static Groovy callers currently see only `Map<String, ?>`, so literal keys and values receive no schema-specific check.
Dynamic Model and Grape scripts are intentionally not type checked; an unknown key currently escapes as a low-level
`MissingMethodException` from the generated Builder implementation.

Groovy 3.0.25, 4.0.32, and 5.0.6 all understand repeatable `groovy.transform.NamedParam` annotations on the first `Map`
parameter. The annotations survive in class files, and separately compiled `@CompileStatic` consumers reject unknown
literal keys and incompatible literal values. The same probe also exposes an important limitation: all three generations
report computed keys and spread-map entries as unexpected named arguments. The metadata slice must therefore prove its
compatibility boundary before any annotation is added to the supported generated contract.

The primary user value is IDE assistance while editing the overwhelmingly common literal-map form: completion and
immediate, source-local feedback for unknown keys and incompatible values. Compiler checking establishes the portable
contract behind that assistance and remains a useful fallback, but it is not the main product objective. Runtime dispatch
continues to catch invalid keys outside the bounded static form, albeit later and with less precise source diagnostics.

## Decision

### Split the umbrella into independently releasable concerns

#487 remains the umbrella. Its implementation work is split into four successors:

1. **NAMED-META** — a conservative native compiler-metadata spike;
2. **NAMED-DIAG** — a Klum-specific runtime diagnostic for unknown dynamic named-map keys;
3. **NAMED-IDE** — native IntelliJ acceptance against source mirrors and binary contracts, with GDSL only for a proved
   gap; and
4. **NAMED-DSLD** — later, independent Eclipse/DSLD exploration after the metadata contract is proven.

The runtime diagnostic does not depend on shipping static metadata. IDE-specific adapters do not define the compiler or
runtime contract.

### Limit the static contract to fixed literal-map targets

The first static slice covers only:

- literal maps passed to generated `Foo.Create.With` root methods; and
- literal maps passed to generated relationship creators whose concrete/default target Builder type is fixed by the
  Schema, including the corresponding single, collection, map, and Cluster factory contracts.

It excludes `Create.AsBuilder().With`, Template factories, generic/custom Factory map methods, polymorphic `Class`
selection, typed generated-Factory selection, map variables, computed or spread keys, import/`FromMap` paths, broad
custom-map analysis, `@NamedVariant` adapters, and generic static-type-checking extensions.

Literal maps are the accepted primary use case. Computed-key and spread-map entries on these annotated calls are
explicitly unsupported under static compilation, matching the behavior of native `@NamedParam` in every supported Groovy
generation. This is an accepted source-compatibility boundary rather than a reason to withhold metadata. Dynamic calls
retain normal runtime dispatch, and callers that genuinely need computed construction can pass an already-built map
variable outside the checked literal subset.

The metadata describes existing dispatch; it does not introduce required keys and does not change overload selection,
map iteration order, lifecycle phases, ownership, materialization, serialization, or runtime invocation.

### Derive keys from the public Builder contract

For each eligible target, the compiler derives a key catalog from the final public generated
`Target_DSL.Builder<Target>` contract, including inherited Builder interfaces. The rule is deliberately broad and exact:

> A valid named-map key is a public Builder operation callable with exactly one supplied value.

The catalog therefore describes supported Builder configuration semantics, not Model properties. It includes generated
field configurators, `setX` aliases, relationship operations/adders, converters, `copyFrom`, inherited operations, and
explicit one-argument `@Builder.Method`s. No separate eligibility taxonomy distinguishes “configuration” from
“infrastructure” methods: the generated public Builder contract plus normal one-argument dispatch is the single source of
truth.

Candidates with the same name are grouped. Metadata may state a precise value type only when that type accepts every
currently valid one-argument overload for the key under the supported Groovy static checker. Otherwise it uses a safe
common supertype, up to `Object`. Losing precision is acceptable; rejecting a runtime-valid supported literal is not.
Return types do not participate. Explicit Builder methods and generated overrides are observed after projection so
method-first behavior is preserved.

NAMED-META uses Groovy's native `@NamedParam`/`@NamedParams` contract. Executable Groovy 3/4/5 controls must lock in both
the supported literal behavior and the accepted rejection of computed and spread entries. It must not broaden into a
custom checker or change factory signatures to recover excluded forms.

### Preserve one generated contract from bytecode through mirrors

Metadata is attached to the source-shaped generated `Map` parameter before hidden factory/Builder methods are projected
onto `Foo_DSL`. Existing parameter-cloning seams must carry it to the hidden implementation and public interface without
substituting hidden Builder types. AnnoDocimal must then project the same annotations from the compiled public interface
into the IDEA-only source mirror.

The class file is authoritative. A source mirror must not invent keys, and GDSL must not become a second key catalog.
Public generated parameter annotations are observable contract metadata, so reflection, bytecode, separately compiled
Groovy, and the mirror must agree.

### Diagnose dynamic failures only after normal dispatch fails

NAMED-DIAG remains a runtime-only slice at `InternalKlumBuilder.applyNamedParameters`. It invokes the key exactly as
today, allowing ordinary overload resolution, `MetaClass`, and `methodMissing` behavior to succeed first. Only a terminal
`MissingMethodException` attributable to the attempted Builder/key dispatch is translated into a Klum-specific actionable
diagnostic. A `MissingMethodException` thrown from inside a successfully selected configurator is not reclassified.

The diagnostic identifies the key and Model/Builder context, explains that named-map keys call one-argument Builder
methods, and may suggest supported public names. It retains the original exception as its cause. It does not prevalidate
the map, reorder entries, consult compiler-only metadata, or alter any lifecycle or dynamic-dispatch behavior.

### Require native IntelliJ evidence before an adapter

NAMED-IDE verifies completion, value-type help, and invalid-key feedback in two consumer shapes:

- a same-project Schema/client using the generated `Foo_DSL` source mirror; and
- a clean consumer using the compiled generated contract.

The acceptance fixture covers root and fixed-target relationship calls, inherited keys, `setX`, a method-first override,
and an overloaded key whose metadata deliberately falls back to a safe supertype. If IntelliJ consumes the generated
annotations natively, no GDSL change is made. A GDSL addition is permitted only for a precisely recorded native gap and
must read the public generated contract rather than infer a parallel DSL.

This IDE experience is the primary delivery outcome. NAMED-META supplies one compiler- and bytecode-visible source of
truth so that editor assistance does not drift from runtime Builder dispatch; compile-time diagnostics are a secondary
benefit and fallback when IDE assistance is absent.

Eclipse/DSLD work starts only after this compiler/binary/native-IntelliJ contract is stable. It makes no IDE-parity claim
before its own acceptance evidence exists.

## Consequences

- Static safety is intentionally partial and follows the Builder API that runtime dispatch already uses.
- Statically compiled computed-key and spread-map entries on annotated calls become unsupported; literal maps are the
  deliberately preferred and optimized form.
- Public generated interfaces may gain runtime-visible Groovy parameter annotations; method descriptors and runtime
  implementations remain unchanged.
- Ambiguous/overloaded keys favor no false positives over maximum value-type precision.
- Dynamic scripts remain dynamically dispatched. Their later improvement is a runtime exception-quality change, not
  static checking by another route.
- No Model field, Builder state, lifecycle phase, ownership rule, serialization shape, or materialization behavior changes.
- Groovy compiler metadata and generated signatures require executable Groovy 3/4/5 evidence, plus bytecode and mirror
  parity checks.
- IntelliJ and Eclipse remain adapters over the generated contract, not architectural authorities.

## Rejected alternatives

**Generate `@NamedVariant` adapters.** They would add overloads and a new invocation path instead of describing existing
Builder dispatch.

**Install a generic static-type-checking extension.** That broadens compilation semantics beyond Klum's generated
contracts and is unnecessary unless a later ADR explicitly replaces the native-metadata approach.

**Infer keys from Model fields only.** This would omit method-first overrides, inherited Builder members, aliases,
relationship operations/adders, converters, `copyFrom`, and explicit Builder operations that the named-map contract
intentionally accepts.

**Analyze every `Map` accepted by any Factory method.** Custom factories, import maps, polymorphic selection, and map
variables have different or unresolved target contracts and are outside the bounded use case.

**Use GDSL as the primary source of truth.** IDE assistance is the primary user outcome, but a standalone GDSL catalog
would not help binary consumers and could drift from the generated Builder contract. Any necessary GDSL bridge must
consume the public metadata rather than re-derive the vocabulary.

**Prevalidate dynamic maps before invocation.** That would bypass `MetaClass`/`methodMissing`, risk different overload
selection, and change the order and failure behavior of established Builder dispatch.

**Promise IntelliJ/Eclipse parity in one slice.** Native IntelliJ consumes the existing generated-source lifecycle;
Eclipse/DSLD requires separate tooling and acceptance evidence.
