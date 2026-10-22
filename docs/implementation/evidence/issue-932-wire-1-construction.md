# WIRE-1 shared Consumer construction

Date: 2026-10-22. Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Scope: internal configuration factoring; no callable structured-input facade is delivered by this checkpoint.

`FactoryHelper.createWithConfiguration` delegates to the ordinary root `doCreate`/preparation lifecycle.
`InternalKlumBuilder.applyConfiguration` shares PostApply with existing Map/Closure application. The existing direct-child
and keyed-map creator bodies now accept a package-private Consumer configuration; their generated Map/Closure entry points
adapt to those same bodies. Target resolution, key/type checks, existing-child identity, breadcrumb quantification,
Template copying, fresh-child preparation, attachment and ownership still use their ordinary implementations.
No generated signature, export, attachment engine, lifecycle/session API or Groovy Closure synthesis was added.

`StructuredInputConstructionTest` establishes the initial failing missing-seam cases and then compares ordinary and
Consumer creation. It covers fresh/reused direct children and keyed children, defaults and Template values visible at
PostCreate, configuration visible at PostApply, one apply callback per route, no premature completion, Owner identity,
completed LINK identity, nested-root rejection before allocation, stale application rejection and root-failure cleanup.
The Consumer configuration failure retains its original exception identity. The existing boundary probes remain unchanged.

Groovy 5 deliberately excludes inherited package-private methods from dynamic lookup. Test-only statically compiled
helpers call those Java seams directly, matching production callers; no visibility was widened to accommodate the test.
An initial unqualified helper inside a delegate-first test Closure was explicitly qualified after the expected DSL
fallback exposed that harness mistake.

Validation: construction (6) and existing boundary probes (4) pass **10 cases per Groovy 3/4/5 lane**, zero failures/errors/
skips. The full runtime suite passes **73 per lane** (one existing skip each), and the full Jackson suites pass
**121/119/119** (no skips), preserving all existing importer modes. `git diff --check` passes. Final compiler/module-wide
and root qualification remain required after the restricted route is implemented. This checkpoint is not full #932 delivery.
