# Domain-first modeling (4.0 preview)

> This is a 4.0 preview pending field testing. Use the documentation that matches the KlumAST version you adopt; #456 owns versioned documentation and Javadocs.

Domain-first modeling starts from the domain a completed model must represent. The model is the canonical abstraction, and adapters translate it for a dashboard, automation hub, report, or other target. That differs from target-contract modeling, where an existing technical contract remains authoritative.

## Choose the consumer boundary

Choose this independently from domain-first versus target-contract:

- **Layer 3** fits when a Domain API Developer defines a stable consumer-facing contract before a Schema Developer realizes it. Client Developers compile against that API, while Model Writers configure the concrete Model.
- **Direct-schema** fits when Schema types are the appropriate consumer contract. The Schema Developer also assumes the Domain API Developer role.

Layer 3 is an API–Schema–Model pattern, not a package or Java-module boundary. It is useful when a generic client should not depend on concrete Schema types; it is not a requirement for every domain-first project. The wider terminology, variants, and policy remain under [#454](https://github.com/klum-dsl/klum-ast/issues/454), so this guide does not treat an example as a new contract.

## Smart-home journey

The executable fixture at [`agent-skills/fixtures/domain-first-smart-home`](../../agent-skills/fixtures/domain-first-smart-home) follows one complete journey:

1. The Domain API Developer defines generic `Home`, `Room`, `Window`, and device/provider types. Cluster projections make the fixed floorplan available without exposing its Schema classes.
2. The Schema Developer realizes one CityFlat floorplan with named Kitchen, LivingRoom, MainBedroom, and garden/street Window fields. `@DefaultValues` supplies the Schema-owned visible labels; validation distinguishes mandatory installed-device categories from optional sensors.
3. The Model Writer configures installed devices in a separate registered Model script through `CityFlat.Create.With`, selecting provider-specific Builder types such as `TadoThermostat` and `HomematicThermostat`.
4. The Client Developer's `client-demo` accepts only generic `Home`, walks the Cluster projections, and asks a speculative service for Window state.

The fixture's `SmartHomeJourneyDocumentaryTest` is the documentary test of that complete path. Its `loads a floorplan-specific Model and lets a generic client inspect every window` feature loads the registered Model and exercises the API-only client. Run it from this repository checkout:

```shell
./gradlew -p agent-skills/fixtures/domain-first-smart-home test
```

Use the portable [`build-domain-first-schema`](../../agent-skills/build-domain-first-schema/SKILL.md) skill to adapt the workflow. It points to the fixture rather than copying its large example. `author-klum-model` remains the shared Model Writer workflow; use it when creating the separate registered Model script and its executable test.

The fixture stops at durable Model configuration and an API-only client boundary. A later showcase could compose provider classes with live readings, or generate OpenHAB Things/devices without a backchannel to the Model. Neither runtime behavior nor OpenHAB integration is a contract of this Layer 3 journey.

## Field test

For a real-project evaluation, begin with the fixture's [field-test brief](../../agent-skills/fixtures/domain-first-smart-home/field-test/SMART-HOME-BRIEF.md) and use its [prompt](../../agent-skills/fixtures/domain-first-smart-home/FIELD-TEST.md). Record the project’s two architectural choices, its completed-model and API-only client evidence, and one concrete friction point. A newly exposed Layer 3 behavior or vocabulary question belongs in a focused maintainer decision, not in an accidental extension of this journey.
