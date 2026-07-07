# Enforce phase contracts and add runtime guards

Short: Add runtime guards and tests that enforce simple phase contracts (no object creation after `AUTO_CREATE`/`POST_TREE`, no mutation after `POST_TREE` unless field is transient). Configurable enforcement levels: warn / throw / disabled.

Why: Prevents a class of bugs where lifecycle code runs out-of-order or mutates a model after it's expected to be stable.

Acceptance criteria:
- Unit tests exist that fail when illegal mutations occur and pass when enforcement level is `disabled`.
- A runtime guard emits a clear, actionable error message pointing to the offending phase and code path (breadcrumb).
- Default enforcement level = warn; make `throw` opt-in via system property or builder flag.

Scope & plan:
1. Add `PhaseEnforcer` with enforcement levels.
2. Wire checks into `PhaseDriver` at key transition points.
3. Add tests that simulate late-phase mutations and verify behavior across enforcement levels.

Notes:
- Keep the checks lightweight to avoid runtime cost for normal models.

