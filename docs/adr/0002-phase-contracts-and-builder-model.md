# ADR 0002: Phase contracts, Builder→Model separation, and diagnostics improvements

Date: 2026-07-07

Status: Superseded by ADR 0003

## Context

During a deep code review and grilling session the project revealed five high-impact areas that will reduce user footguns and make the codebase easier to maintain:

1. Phase contracts are currently informal — custom phases and lifecycle methods can run at unpredictable times and may rely on internal ordering implicitly.
2. The current RW (read-write) class pattern mixes builder-style mutation methods with the model object; the final model is technically mutable through proxies and transient fields.
3. Transient fields are conceptually different from schema fields but the contract is only lightly enforced by `@Field(TRANSIENT)` conventions.
4. There is no declarative validation of phase ordering or plugin phases; ServiceLoader registration is brittle for third-party integrations.
5. Breadcrumbs provide good contextual error traces but lack source-location (line/column) information from user-written Groovy model files which would greatly improve diagnostics.

## Decision

We will take a coordinated incremental approach:

- Define explicit phase contracts and enforce simple runtime checks (ADRs and unit tests). Add a small runtime guard that throws an informative exception when a lifecycle action mutates state in a forbidden phase (configurable enforcement level: warn/throw/disabled).
- Implement a Builder→Model separation: make RW an explicit Builder type that holds mutable state during creation phases and produce a final immutable model object at the end of `POST_TREE`. Migration shims keep the current generated API for one major release; add an opt-in strict mode in v4.1.
- Strengthen the transient-field contract by using an explicit annotation and code generation that removes mutators for non-transient fields; document serialization behavior and make `@Field(TRANSIENT)` the canonical marker (keep behaviour but make it explicit in generated code).
- Add a phase-registration DSL for third-party phases/plugins that declares `dependsOn`/`runsAfter` dependencies. Validate ordering at startup and reject cycles.
- Enhance breadcrumb diagnostics with optional source-location capture by integrating with the Groovy parser where feasible; fall back to best-effort filename/line information for scripts loaded from files.

## Consequences

- Short-term: Add ADR, tests and small runtime guards; create tracer-bullet issues to implement the work incrementally. Existing users will continue to work until we flip opt-in strict mode.
- Medium-term: v4 will include a non-breaking opt-in for strict Builder/Model separation; v5 will make the separation default after a deprecation period.
- Long-term: Improved diagnostics, clearer plugin model, fewer phase-ordering bugs, and more robust public API stability.

## Implementation plan (high-level)

1. Add runtime enforcement flags and unit tests for forbidden mutations after `POST_TREE` and for illegal object creation in late phases. (tracer-bullet: enforce-phase-contracts)
2. Implement Builder type as a generated RWBuilder and a Model type; add a compatibility layer that maps existing generated entrypoints to the new Builder where necessary. (tracer-bullet: builder-model-separation)
3. Formalize `@Field(TRANSIENT)` handling in generation so non-transient fields do not have exposed setters at runtime; document serialization implications. (tracer-bullet: transient-field-contract)
4. Implement a minimal phase-registration DSL and startup validator for plugin phases; document how to author safe custom phases. (tracer-bullet: phase-order-validation)
5. Prototype Groovy source-location capture for BreadcrumbCollector and add tests to show improved error messages in typical user scripts. (tracer-bullet: breadcrumb-source-locations)

## Next steps

- Create tracer-bullet issues for each item above and assign owners.
- Implement the runtime guard and unit tests first (low-risk, high-value).
- Start an experimental branch implementing Builder→Model separation with migration shims and compatibility tests.
