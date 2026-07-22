---
name: build-target-contract-schema
description: Build a KlumAST authoring Schema for an authoritative external contract. Use when an adopter is replacing or simplifying Helm values, another configuration document, or a target-specific API contract while keeping that contract authoritative.
---

# Build a target-contract Schema

Use the documentation for the KlumAST version being adopted as the authority. This workflow creates a convenient authoring layer for one external target; it does not define a universal import/export format.

1. Collect two or more representative target artifacts and name the target, its owning team, supported versions, and the values that are intentionally stable. For Helm, retain the chart name/version and values files. Treat the target contract—not its incidental YAML formatting—as authoritative.
2. Identify the repeated concepts, defaults, validity rules, and awkward combinations in those artifacts. Propose a small authoring vocabulary that reduces real repetition. Do not mechanically copy every map, list, or spelling from YAML into classes.
3. Decide independently whether the project is direct-schema or Layer 3. Choose direct-schema when the Schema Developer also owns the consumer-facing types and no separate stable Domain API is needed. Choose Layer 3 only when clients must compile against a distinct Domain API; do not introduce it for hypothetical flexibility. Record the rationale in the project README or architecture note.
4. Create `@DSL` Schema types that express the chosen authoring vocabulary. Add meaningful defaults, validation, and at least one higher-level convenience only where it reduces a demonstrated target-contract burden. Keep generated Builders inside one root construction lifecycle.
5. Implement a narrow renderer or adapter that maps completed models to the target contract. The adapter may intentionally expand one authoring concept into several target fields. It must make every non-obvious mapping visible in tests or documentation.
6. Add representative configured models and generate target artifacts from them. Compare the generated artifacts semantically with golden target-contract files; keep the golden files human-readable. Do not make byte ordering, YAML formatting, Klum metadata, or a YAML/KlumAST round trip the compatibility promise.
7. Keep current capabilities separate from future migration conveniences. Resource-backed defaults remain [#79](https://github.com/klum-dsl/klum-ast/issues/79); ordered base/override composition remains [#304](https://github.com/klum-dsl/klum-ast/issues/304). If the target cannot be expressed without one, record a focused follow-up rather than broadening this onboarding workflow.
8. Run the fixture's normal Gradle test with the chosen Groovy line, then exercise its supported Groovy 4 and 5 variants before handoff. Refresh generated source mirrors only when IntelliJ completion is useful; they are not build inputs.

The executable [Helm target-contract fixture](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/fixtures/helm-target-contract) demonstrates the direct-schema decision, two service values contracts, defaults, validation, a convenience mapping, and semantic golden-file checks. Follow [Target Contract Modeling](https://klum-dsl.github.io/klum-ast/4.0/Target-Contract-Modeling/) for the task-oriented explanation, and use the [field-test starting artifact](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/field-tests/target-contract-helm) when applying this preview to a real project.
