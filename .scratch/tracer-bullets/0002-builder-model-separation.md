# Tracer-bullet: Builder / Model separation (0002)

Related ADR: docs/adr/0002-phase-contracts-and-builder-model.md

Summary
- Begin separating generated Builder (RWBuilder) types from immutable Model types. Provide a small compatibility adapter so existing call sites keep working while generation migrates.

Acceptance criteria
- Tracer-bullet file describing the change and owner.
- Minimal runtime prototype: KlumBuilder interface and an adapter that wraps existing RW instances.
- Small unit test that verifies the adapter behavior.

Owner: @stephan

Plan (small vertical slice)
1. Add KlumBuilder interface and KlumBuilderAdapter that wraps KlumRwObject.
2. Add a Spock unit test asserting adapter returns underlying RW instance.
3. Create tracer-bullet issue/PR for follow-up work to implement real generated builders and compatibility layer.

Notes
- This is intentionally tiny: compatibility adapter + test to show feasibility.
