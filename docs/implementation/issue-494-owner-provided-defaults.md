# Issue 494 — Owner-provided defaults

Status: Confirmed design; ready for implementation planning.

Tracking issue: [#494 — Add owner-provided defaults for shared contracts](https://github.com/klum-dsl/klum-ast/issues/494)

Related issue: [#414 — Mixin annotation](https://github.com/klum-dsl/klum-ast/issues/414) remains the broader, speculative generic-mixin exploration. Its original request is deliberately not reinterpreted by this document.

## Decision source and scope

The maintainer confirmed this later-4.x capability for a current modeling need: an owned child receives conservative defaults from its owner through a shared JavaBean contract while retaining values configured on the child.

```groovy
interface ShipmentDetails {
    String getRepository()
    List<Customer> getCustomers()
}

@DSL
@OwnerProvidedDefaults(ShipmentDetails)
class ProductRelease implements ShipmentDetails {
    @Owner Product product
    String repository
    List<Customer> customers
}
```

`Product` also implements `ShipmentDetails`. `ProductRelease` inherits an unset `repository` and empty `customers` from its owner; explicitly configured release values remain authoritative.

This is not a new ADR. It implements the already accepted Builder-first boundary from [ADR 0003](../adr/0003-builder-first-materialization.md), active-session ownership and recipe rules from [ADR 0004](../adr/0004-asbuilder-composition-protocol.md), and the existing ordered Builder lifecycle. It introduces neither a public lifecycle phase nor a generated client API.

## Confirmed contract

### Schema surface and compile-time checks

- Add public, runtime-retained, repeatable, type-level `@OwnerProvidedDefaults(Class<?> value)` in `klum-ast-annotations`.
- The annotation has no generated Builder, factory, mutator, or completed-model API. Existing unannotated schema bytecode and behavior remain unchanged.
- A KlumCast check rejects:
  - a contract with no JavaBean getter;
  - a recipient that does not implement the contract;
  - no compatible declared `@Owner` field or more than one compatible declared donor field;
  - a contract property that the donor cannot read or the recipient cannot configure with a compatible type; and
  - incompatible repeated-contract declarations for one participating property.
- Exactly one owner is a compile-time donor-selection rule. It does not change the runtime owner model: an object can retain multiple `@Owner` fields when their values are null or point to the same object.
- Only JavaBean properties declared by the selected contract and its superinterfaces participate. Same-named fields outside the contract and non-property interface methods are untouched.
- Repeat declarations are allowed. Participating properties are deduplicated before application.

### Lifecycle, merge, and identity

- Extend the existing `DEFAULT` phase internally: owner-provided defaults run first, after `OWNER` (15) and before the existing annotation/field/lifecycle default mechanisms, `POST_TREE` (30), and `INSTANTIATE` (40). There is no numeric `MIXIN`/`OWNER_DEFAULTS` phase and no new plugin ordering seam.
- Use one fixed, conservative policy in v1:
  - a Simple Value or direct object field is populated only when its recipient value is `null`;
  - an absent owned DSL Object is rehydrated into a fresh recipient Builder; an existing owned DSL Object is recursively mixed;
  - a collection or map is populated only when empty; a map adds only missing keys and recursively mixes matching owned DSL values;
  - no owner value clears, replaces, or otherwise overwrites an explicitly configured recipient value.
- Owned relationship values are fresh composition in the recipient Construction session. A completed `LINK` remains an aggregation reference and retains identity; it is never re-owned or rehydrated as composition.
- This feature is value-only: it must not replay Template or live-Builder `applyLater` actions from its owner donor.

### Absence and diagnostics

- `null` is absent for scalar/direct fields; an empty collection or map is absent; primitives are already set.
- V1 does not preserve whether a caller explicitly supplied `null` or an empty aggregate. A `@Default` lifecycle closure remains the supported workaround for richer policy.
- If a schema-valid recipient has no actual compatible donor at runtime, skip owner-provided defaults and record a standard non-fatal `Validate.Level.WARNING` issue that identifies the annotation contract and donor field. The Builder-phase issue must survive Materialization and be visible through normal completed-model validation support.
- A global `LINKAGE` validation level and its failure/suppression policy are intentionally separate work.

## Current seams and failure paths

| Seam | Current behavior | Planned responsibility |
| --- | --- | --- |
| `klum-ast-annotations` | Defines runtime schema annotations and names KlumCast validators without compiler implementation dependencies. | Publish the annotation and its repeatability container/validator metadata. |
| `klum-ast` | `FieldAstValidator` and similar checks extend `KlumCastCheck`; transformation generates Builders but no API is required here. | Compile-time contract/donor/property validation and focused diagnostics. |
| `OwnerPhase` | Binds direct/transitive/root owners to Builders at phase 15 without overwriting already-set fields. | Remains the source of the selected donor; no change to general multiple-owner behavior. |
| `DefaultPhase` | Runs at phase 25 and applies current default mechanisms. | Make owner-provided defaults its first internal action. |
| `CopyHandler` | Copies recipes into Builders; rehydrates owned DSL recipes and retains completed `LINK` identity, but `KlumBuilder.copyFrom` can replay deferred actions. | Extract or add a value-only conservative contract-property path; do not route owner defaults through action-replaying `copyFrom`. |
| Builder validation metadata | Early Builder phases can attach provisional validation issues which Materialization transfers to the completed Model companion. | Store and transfer the missing-donor warning. |

## Tracer bullets

### OPD-1 — Schema vocabulary and KlumCast contract checks

**Modules:** `klum-ast-annotations`, `klum-ast`, `klum-ast` tests.

1. Add `@OwnerProvidedDefaults`, its repeatability support, Javadoc, and KlumCast validator registration.
2. Implement the validator using the established `KlumCastCheck` pattern. Resolve inherited JavaBean getters, compatible declared owner fields, recipient configuration properties, repeat declarations, and type compatibility.
3. Add compilation tests, all annotated `@Issue("494")`, for the valid `ShipmentDetails` case and each rejected schema shape.

**Commit boundary:** `Add owner-provided-defaults schema contract` — annotation, validator, and passing compiler tests together.

**Acceptance:** The valid schema compiles; every unsupported or ambiguous contract fails during compilation with an actionable message naming the annotation, contract, property, or owner field.

### OPD-2 — Conservative DEFAULT-phase application

**Modules:** `klum-ast-runtime`, `klum-ast` runtime/lifecycle tests.

1. Add the ordered `DefaultPhase` step that resolves the statically selected donor from the Builder state.
2. Add a dedicated value-only copier for contract properties, reusing safe `CopyHandler` recipe/relationship primitives where practical without importing non-contract fields or deferred actions.
3. Implement conservative recursion, map/collection rules, duplicate-property suppression, fresh owned Builder rehydration, and completed `LINK` identity retention.
4. On an unavailable runtime donor, skip copying and attach the standard warning to provisional Builder validation metadata.

**Commit boundary:** `Apply owner-provided defaults conservatively` — runtime behavior and focused lifecycle/identity tests together.

**Acceptance:** A nested `ProductRelease` obtains owner values, preserves configured fields, recursively fills a missing owned subtree, does not share owned identity, preserves a completed `LINK`, and runs no donor deferred action. A standalone recipient completes with the expected warning and no lifecycle exception.

### OPD-3 — Compatibility, documentary evidence, and guidance

**Modules:** affected test fixtures and `docs/user/`.

1. Add a dedicated `OwnerProvidedDefaultsDocumentaryTest` with `@Issue("494")`, `@Tag("documentary")`, and `@See` pointing to the user-facing documentation.
2. Document the current use case, defaults timing, conservative absence rule, fresh-owned-versus-`LINK` identity behavior, standalone-warning behavior, and the boundary with #414/generic mixins. Update default/copy guidance and navigation as appropriate.
3. Add `CHANGES.md` only when the feature lands. Do not advertise the feature before implementation.
4. Run the focused Groovy 3 tests, the affected module test suite, `groovy4Tests`, and `groovy5Tests`, then perform documentation and diff checks.

**Commit boundary:** `Document owner-provided defaults` — documentary test and user-facing documentation together after the executable contract is green.

**Acceptance:** The documented Groovy example compiles and verifies the stated values/identity. Groovy 3, 4, and 5 establish the annotation, AST, and generated-DSL compatibility; no generated API surface is introduced.

## Compatibility and migration

- This is additive for unannotated schemas and does not alter constructors, generated factories, Builders, completed-object APIs, serialization, or Jackson behavior.
- Annotated schemas add a public annotation dependency and lifecycle behavior; their source must be compiled with the annotation/runtime versions that implement #494.
- Generated signatures do not change, but the annotation/KlumCast transformation must be exercised across Groovy 3, 4, and 5.
- No migration entry is needed until delivery. User documentation must describe this as an owner-default mechanism, not a general mixin API.

## Deferred work and risks

- #414 remains the eventual generic-mixin design; it may select non-owner donors and needs its own lifecycle, policy, and API decisions.
- Configurable overwrite policies, explicit presence/clear tracking, a public plugin ordering seam, and a `LINKAGE` validation level are not part of #494.
- The value-only copy path must not accidentally inherit Template/live-Builder deferred-action replay from `KlumBuilder.copyFrom`.
- The plan must verify that a Builder-phase warning is retained after Materialization and exposed by `KlumObjectSupport.Validation` without rerunning lifecycle code.
