---
name: build-domain-first-schema
description: Build a domain-first KlumAST Schema. Use when a Domain API Developer and Schema Developer want to model a domain independently of downstream targets, decide between Layer 3 and direct-schema, define DSL Object boundaries, and prove the resulting completed model with an API-only client and executable test.
---

# Build a domain-first KlumAST Schema

Use the version-matched KlumAST documentation as the authority. This workflow helps a team make one domain model; it does not define a target format or a new Layer 3 contract.

1. Start with the problem domain. Name the root, its owned concepts, their stable identities, and the invariants that make a completed model useful before naming a backend, document, or deployment platform. Choose **domain-first** only when that completed model is the canonical domain abstraction; otherwise use the target-contract workflow.
2. Decide the consumer boundary explicitly. Choose **Layer 3** when Client Developers must compile only against a stable Domain API defined by a Domain API Developer. Choose **direct-schema** when Schema types are the appropriate consumer contract and the Schema Developer also owns the Domain API role. Layer 3 is an API–Schema–Model pattern, not a package or Java-module requirement; do not introduce it merely for future flexibility.
3. For Layer 3, write the smallest Domain API first: completed-model values and relationships that a generic Client Developer needs, with no Builder implementation or concrete Schema types. Keep API device/provider types when their communication behavior is a consumer concern. Have the Schema Developer realize that API with idiomatic `@DSL` types, and keep generic client code in a module that depends only on the API.
4. Design DSL Object boundaries around ownership. Give stable domain identities `@Key`, create owned children inside their parent callback, declare supported collection interfaces, and make aggregation an explicit relationship decision. Do not start nested root factories inside parent configuration.
5. Put domain invariants in validation and use lifecycle callbacks only for domain-derived behavior such as a default presentation value. Use `@DefaultValues` when a fixed Schema element supplies its human-visible label. State the invariant and lifecycle order before encoding it. Keep the completed model immutable through its public API.
6. Ask a Model Writer to author one representative configured Model in a separate model module through the shared `author-klum-model` workflow. Register its top-level script and load it through `Create.FromClasspath` in an executable end-to-end test. Verify the fixed Schema structure, one validation behavior, and a generic client that consumes the Domain API only.
7. Record the choices, boundaries, and test seam in the project documentation. Run the normal Gradle test task; for compatibility-sensitive changes, exercise every Groovy line supported by the project.

## Smart-home reference journey

The executable reference is [`agent-skills/fixtures/domain-first-smart-home`](../fixtures/domain-first-smart-home). It models one fixed `Home` floorplan through separate API, Schema, Model, and `client-demo` modules. The API exposes generic Areas and Windows through Cluster projections, the Schema supplies the fixed rooms and defaults, and the Model selects provider-specific devices. Read its `README.md`, `ADOPTER-DRY-RUN.md`, and `FIELD-TEST.md` before adapting the journey to a real project.

For the authoritative concepts, read [Getting Started](https://klum-dsl.github.io/klum-ast/4.0/Getting-Started/#choose-the-model-shape-first), [Layer 3](https://klum-dsl.github.io/klum-ast/4.0/Layer3/), [Basics](https://klum-dsl.github.io/klum-ast/4.0/Basics/), [Validation](https://klum-dsl.github.io/klum-ast/4.0/Validation/), and [Builder-first migration](https://klum-dsl.github.io/klum-ast/4.0/Builder-First-Migration/). Issue [#454](https://github.com/klum-dsl/klum-ast/issues/454) owns unresolved Layer 3 terminology and policy; capture a newly exposed contract question for maintainer review instead of letting this example decide it.
