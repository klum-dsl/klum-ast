# Tracer-bullet issues for architecture hardening

This file summarises the small, focused issues created as part of the architecture hardening plan (see ADR 0002).

1. enforce-phase-contracts — Add runtime guards and tests to prevent illegal mutations outside allowed phases.
2. builder-model-separation — Implement explicit `RWBuilder` generation and immutable `Model` types with migration shims.
3. transient-field-contract — Formalize `@Field(TRANSIENT)` handling and remove mutators for non-transient fields on final models.
4. phase-order-validation — Provide a phase-registration DSL and startup ordering validator for plugins.
5. breadcrumb-source-locations — Capture file:line source locations in breadcrumbs and improve error messages.

Each tracer-bullet has a draft under `.scratch/tracer-bullets/` with a description, acceptance criteria and a small plan. Use these for issue creation or to drive small PRs.

