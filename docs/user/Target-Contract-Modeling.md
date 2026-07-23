# Target-contract modeling (4.0 preview)

> This onboarding route is a 4.0 preview pending field testing. It targets one authoritative external contract at a time; use documentation matched to the KlumAST version you adopt.

## Choose the authoring seam

Use target-contract modeling when an existing system such as a Helm chart owns the compatible external values shape. The Klum Schema is then a better authoring language for that contract: it may reduce repeated configuration, introduce validated defaults, and expand a convenient concept into several target values. It does not turn YAML into KlumAST persistence or make every Helm field a public object-model property.

Make the consumer-shape decision independently:

- Choose **direct-schema** when the Schema Developer also owns the authors who consume the types, and there is no separate stable Domain API to protect.
- Choose **[[Layer3|Layer 3]]** only when client developers must compile against a distinct Domain API rather than Schema types.

Do not add Layer 3 merely as insurance for a future client. Record the selected shape and the reason in the project architecture note.

## Executable Helm journey

[`agent-skills/fixtures/helm-target-contract`](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/fixtures/helm-target-contract) is the common executable example for this guide and the portable `build-target-contract-schema` skill. It starts with the `catalog` and `billing` Helm values contracts and uses one direct-schema `ServiceRelease` type:

```groovy
ServiceRelease.Create.With('catalog') {
    imageTag '1.4.0'
    publiclyReachable true
}
```

The fixture derives `ghcr.io/acme/catalog` and `catalog.example.test`, validates a deployable image tag and port, and expands `publiclyReachable` into the Helm ingress map. Its owned `resources` object has explicit `requests` and `limits` children; absent limits default to a fresh copy of requests, while different memory values store a warning without rejecting the release. This is direct-schema because the same deployment team owns the authoring types and its target values; a separate Domain API would add no real client boundary.

`HelmTargetContractDocumentaryTest`, tagged `documentary` and linked to this page, writes human-readable YAML under `build/generated-values/`. It parses each generated file and compares its meaning to both representative target inputs and golden expected artifacts. Run the established fixture baseline and compatibility variants:

```shell
./gradlew -p agent-skills/fixtures/helm-target-contract test
./gradlew -p agent-skills/fixtures/helm-target-contract test -PfixtureGroovyVersion=4
./gradlew -p agent-skills/fixtures/helm-target-contract test -PfixtureGroovyVersion=5
```

This is a target-contract conformance check, not a promise that KlumAST can import arbitrary Helm YAML, preserve YAML formatting, or round trip its own model through YAML.

## Agentic use

### Apply the workflow

Install `build-target-contract-schema` from the portable `agent-skills/` distribution. It guides the Schema Developer to inventory representative artifacts, identify stable concepts and intentional mappings, make the direct-schema-versus-Layer-3 decision explicit, and compare generated output to readable golden values files. The workflow links back to this fixture instead of copying a larger example.

If a migration needs a value read from an external resource, keep that need with [#79](https://github.com/klum-dsl/klum-ast/issues/79), resource-backed defaults. If it needs base, environment, and override documents to apply in order, keep it with [#304](https://github.com/klum-dsl/klum-ast/issues/304), ordered configuration composition. Neither capability is implied by this journey.

### Field-test starting point

When applying this preview to a real project, use the fixture's [`field-tests/target-contract-helm`](https://github.com/klum-dsl/klum-ast/tree/master/agent-skills/field-tests/target-contract-helm) prompt and inventory. Record the chart/version, target-contract owner, representative values, authoring decisions, golden evidence, and any smallest unmet capability. The prompt intentionally does not prescribe a later skill: field-test findings should become focused follow-up issues only when a real project needs them.

The executable evidence is `agent-skills/fixtures/helm-target-contract/src/test/groovy/onboarding/helm/HelmTargetContractDocumentaryTest.groovy`, feature `generates human-readable #release.name Helm values that conform to the golden contract`.
