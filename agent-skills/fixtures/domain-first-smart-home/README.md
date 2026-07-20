# Domain-first smart-home journey

This executable 4.0-preview fixture is the shared reference for the `build-domain-first-schema` skill and the [Domain-first modeling](../../../wiki/Domain-First-Modeling.md) guide. It starts from a backend-agnostic home domain, not a target document.

The four modules make the Layer 3 boundary concrete:

- `api` defines generic `Home`, `Area`, `Window`, and device/provider types. It exposes the Schema floorplan through Cluster projections.
- `schema` defines one `CityFlat` floorplan: Kitchen, LivingRoom, MainBedroom, garden/street Windows, fixed device slots, and Schema-owned display-name defaults.
- `model` consumes the finished Schema. Its registered `CityFlatModel` script selects Tado/Homematic device Builders and records durable target values and connection identities.
- `client-demo` compiles against `api` only. `WindowStateClient` iterates generic Cluster windows and asks a speculative external service for their state.

A Model Writer uses the shared `author-klum-model` workflow in `SmartHomeJourneyDocumentaryTest`. The end-to-end test loads the registered Model through `CityFlat.Create.FromClasspath`, verifies Schema defaults and validation, and runs the API-only client.

Run the fixture from this repository checkout:

```shell
./gradlew -p agent-skills/fixtures/domain-first-smart-home test
```

The baseline is Groovy 3. Run the same fixture under Groovy 4 or 5 with `-PfixtureGroovyVersion=4` or `-PfixtureGroovyVersion=5`.

Read [ADOPTER-DRY-RUN.md](ADOPTER-DRY-RUN.md) for the role-by-role journey and [FIELD-TEST.md](FIELD-TEST.md) for the later field-test prompt and starting artifact. The fixture illustrates the established API–Schema–Model pattern; it does not settle the open Layer 3 policy questions owned by [#454](https://github.com/klum-dsl/klum-ast/issues/454).

The client demo is not an OpenHAB integration or a runtime state model. A later showcase could compose provider behavior with live readings or generate OpenHAB Things/devices from this configuration, but those are deliberately outside this fixture.
