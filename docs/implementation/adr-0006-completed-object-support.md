# ADR 0006 implementation plan: completed Object support

This plan implements [ADR 0006](../adr/0006-completed-object-support.md) for canonical issue
[#390](https://github.com/klum-dsl/klum-ast/issues/390).

## Original behavior and failure

`KlumModelProxy.getProxyFor` is publicly callable and exposes breadcrumb/model paths, raw metadata, validation state, and
Template-era deferred closures. `DslHelper`, `StructureUtil`, and validation handlers expose only fragments of the intended
completed-object surface. The framework has no stable Java entry point for a completed subtree, and arbitrary metadata can
become an accidental plugin protocol.

## Affected seams

- `klum-ast-runtime`: facade, companion lookup, structure/validation helpers, utility delegation and deprecation.
- generated model field/access: keep companion storage private and typed to ADR 0004's internal common abstraction.
- serialization: serialize only the model/companion relationship, never facade state.
- wiki/Javadoc/migration: Java-first examples and explicit removal of raw proxy/metadata access.

## Tracer-bullet slices

### [OS-1 — Provenance and structure facade](https://github.com/klum-dsl/klum-ast/issues/435) — Implemented

Add `KlumObjectSupport.of(object)` with `getObject`, both path getters, and `getStructure`. Implement direct owners, owner
hierarchy, composition-only cycle-safe visit/find-all, and relative paths. Make existing `StructureUtil` delegate where
compatible. Demonstrate Java use against both a root and subtree without exposing the companion.

### OS-2 — Stored validation facade and proxy lockdown — Implemented

Add `getValidation()` with target/subtree result access and non-rerunning `verify` operations. Move supported callers off
`KlumModelProxy`, internalize its lookup and raw metadata, and add migration diagnostics/deprecations where source
compatibility is feasible. Verify facade serialization does not create a second companion reference.

### OS-3 — Documentation and compatibility closure

Publish Java-first Javadoc/wiki examples, distinguish breadcrumb from model path, document the absence of generic extension
metadata, update migration navigation and `CHANGES.md`, and run runtime plus serialization coverage.

## Compatibility

Direct `KlumModelProxy` and metadata use is intentionally unsupported in 4.0. Static structure utilities may remain as
deprecated delegating adapters. No `extension(Class<?>)`, generation log, or model-specific facade generation belongs in
these slices.

## Acceptance map

| ADR contract | Slice |
|---|---|
| facade for any completed root/subtree and path provenance | OS-1 |
| grouped Java-first structure traversal | OS-1 |
| stored-only validation results and verification | OS-2 |
| strictly internal companion and metadata | OS-2 |
| migration and public documentation | OS-3 |

## Risks

Owner traversal must preserve current composition semantics and cycle guards. Validation access must not accidentally rerun
`InstanceValidator`s or mutate transferred issues. Generic helper signatures need Java compilation tests, not Groovy-only
property syntax.
