# Domain-first smart-home dry run

This is a 4.0-preview acceptance fixture for `build-domain-first-schema` and `author-klum-model`. The selected shape is **domain-first** plus **Layer 3**: the completed smart-home model is the domain abstraction, while a generic downstream consumer must not compile against the concrete Schema.

## Domain API Developer

`api` declares generic `Home`, `Area`, `Room`, `Window`, and `Device` contracts, including provider-specific device types where provider communication is relevant. `Home.areas` and `Area.windows` are Cluster projections. It has no concrete floorplan classes or Model script.

## Schema Developer

`schema` realizes that contract for exactly one floorplan. `CityFlat` owns auto-created Kitchen, LivingRoom, and MainBedroom fields; its concrete room and window types provide named, completion-friendly structure. Their structural field names become Cluster keys. `@DisplayName`, backed by `@DefaultValues`, assigns the human-visible labels. Fixed thermostat and smoke-detector slots are validated; Window sensors stay optional because not every physical window is managed.

## Model Writer

Follow the shared [`author-klum-model`](../../author-klum-model/SKILL.md) workflow. The separate `model` module registers `CityFlatModel` with `com.blackbuild.klum-ast-model`. It uses `CityFlat.Create.With`, provider-polymorphic calls such as `thermostat(TadoThermostat)`, and the `devices` collection factory. The documentary test loads it through `CityFlat.Create.FromClasspath`; it does not call nested root factories.

## Client Developer

`client-demo` has only a project dependency on `api` (plus the Groovy API needed to compile against Groovy DSL types). `WindowStateClient` receives generic `Home`, walks the Area/Window Cluster projections, and asks a speculative `WindowStateService` about each Window. It imports no concrete room, provider, or Builder type. The end-to-end test compiles and executes it with the registered Model.

## Direct-schema alternative

Layer 3 is appropriate here because the generic client is intentionally insulated from concrete room Schema classes. A project whose only clients are the Schema-aware model and client could choose direct-schema instead, letting the Schema Developer also own the consumer API. That is an independent decision from domain-first versus target-contract modeling.

If a real project exposes a missing role, dependency-direction, specialized-support, or generated-API policy, record it for maintainer review under [#454](https://github.com/klum-dsl/klum-ast/issues/454). Do not infer a general contract from this example.
