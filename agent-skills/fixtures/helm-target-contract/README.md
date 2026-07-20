# Helm target-contract onboarding fixture

This is the executable journey for `build-target-contract-schema`. It models the authoritative values contracts for the `catalog` and `billing` services with a direct-schema KlumAST authoring layer.

## Target contract

The Acme Platform deployment team owns the `acme-service` Helm chart at version `3.2.1`. Its supported values contract for that chart version is represented by the checked-in `catalog` and `billing` values files. The generated values must preserve their parsed Helm meaning; YAML comments, key order, and other formatting are not compatibility promises.

## Why direct-schema

The same team owns the Schema and the deployment configuration in this small target-specific project. There is no separate client-facing Domain API to stabilize, so Layer 3 would add ceremony without protecting a real consumer boundary. `ServiceRelease` is therefore the direct authoring type, while `toHelmValues()` is the intentional adapter to the Helm contract.

The authoring model earns that seam by deriving image repositories and public hosts from the stable service key, validating deployment-safe tags and ports, and expanding `publiclyReachable` into Helm ingress values. It models `resources` as an owned inner object with explicit `requests` and `limits` children; absent limits default to a fresh copy of the request, while a different memory limit records a warning without rejecting the release. It is not a generic Helm object model and must not be used to infer a YAML/KlumAST round trip.

## Contract evidence

`src/test/resources/helm/representative/` contains the target-owner examples for two services. `src/test/resources/helm/golden/` is the checked-in generated-contract expectation. `HelmTargetContractDocumentaryTest` renders each configured model to `build/generated-values/`, parses the human-readable YAML, and compares its meaning with both artifacts.

Run the fixture inside this repository with:

```shell
./gradlew -p agent-skills/fixtures/helm-target-contract test
./gradlew -p agent-skills/fixtures/helm-target-contract test -PfixtureGroovyVersion=4
./gradlew -p agent-skills/fixtures/helm-target-contract test -PfixtureGroovyVersion=5
```

The composite build intentionally uses the checked-out KlumAST 4.x source baseline. A real adopter should use the released plugin version selected by `start-klum-project`.

## Boundaries

This fixture does not read defaults from a YAML resource; that deferred migration convenience belongs to [#79](https://github.com/klum-dsl/klum-ast/issues/79). It also does not compose base, environment, and override files; ordered composition belongs to [#304](https://github.com/klum-dsl/klum-ast/issues/304). Record either need as a focused follow-up instead of making this onboarding example promise unavailable behavior.

See [Target Contract Modeling](https://github.com/klum-dsl/klum-ast/wiki/Target-Contract-Modeling) for the task guide and [`../../field-tests/target-contract-helm/`](../../field-tests/target-contract-helm/) for the later real-project evaluation starting point.
