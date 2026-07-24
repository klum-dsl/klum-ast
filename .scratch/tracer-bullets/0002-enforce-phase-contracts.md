# Tracer-bullet: Enforce phase contracts (0002)

Related ADR: docs/adr/0002-phase-contracts-and-builder-model.md

Summary
- Implement a small, test-first tracer-bullet to add runtime guards and unit tests that enforce phase contracts described in ADR 0002.

Acceptance criteria
- Unit tests that detect forbidden mutations after POST_TREE phase.
- Runtime guard that errors when illegal object creation occurs in late phases.
- Tests run in CI and are green.

Owner: @stephan

Plan (small vertical slice)
1. Add unit tests that assert forbidden mutations are prevented (phase enforcement tests).
2. Implement minimal runtime guard and a feature-flag for opt-in strict mode.
3. Add documentation and link tests to ADR 0002.

Notes
- This is intentionally small: tests + a minimal runtime guard so the team can iterate.
