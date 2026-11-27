# #1114 — bounded YAML qualification and consumer sizing

Date: 2026-11-27. **Recorded private evidence; no parser adoption, production implementation or public-candidate claim.**
Related: [#1114](https://github.com/klum-dsl/klum-ast/issues/1114),
[ADR 0034](../../adr/0034-bounded-structured-authoring.md),
[interface/implementation plan](../issue-1114-yaml-integration.md).

## Scope and provenance

The 2026-11-26 private proof executed only `org.snakeyaml:snakeyaml-engine:2.10` with a thin fixed-policy adapter.
This is a qualified comparison baseline, not a claim about the newest release or a choice to ship it. The 270-line
package-private adapter and assertion runner are not production code or part of this documentation PR. Its original
source/report manifest hashes were checked before recording this evidence. No full reproduction harness is published
here; future production acceptance must turn the retained cases into repository tests and rerun against the authorized
actual candidate. Aggregate private results cannot substitute for that qualification.

- [Official candidate JAR](https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/2.10/snakeyaml-engine-2.10.jar),
  300,020 bytes, SHA-256 `c99d9fd66c7c251d881a9cd95089b7c8044c29a1b02983d7036981bd4354ec37`.
- [Published POM](https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/2.10/snakeyaml-engine-2.10.pom),
  SHA-256 `56142ca298988cf64da65ae2bdc19834921d738d75ab23761a8fac4b5f583149`.
- [Exact source archive](https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/2.10/snakeyaml-engine-2.10-sources.jar)
  and [YAML 1.2.2 Core resolution](https://yaml.org/spec/1.2.2/#1032-tag-resolution) supplied source/oracle context.
- Adapter SHA-256 `275a90208ab96e49bbba358233fc1fac67c72035db4eca1ec30ac7f5cafdd334`;
  original report SHA-256 `f400a2a3827cf7de6c91b47aaa680b4bf470365b6316a5f7fbef04258536e29f`.

## Recorded executable results

| Evidence | Result and scope |
| --- | --- |
| Java assertion corpus | 204 named invocations, zero failures; compiled --release 17, executed OpenJDK 25.0.5.1 |
| Named-module repetition | Same 204, zero failures, Zulu 17.0.3; exported public library seams only, no reflection/add-opens/add-exports or split package |
| Separately compiled Schema/Factory integration | Three checks, zero failures; Groovy 3.0.25 / Zulu 17.0.3 using matched local compiler/runtime/annotations from #1121 test revision c4ac3c05e |

The Java runner is not a Spock/JUnit-engine run; Spock's Issue annotation supplies traceability only. No ignored/pending
cases, full root check, hosted CI, Sonar, Groovy 4/5 integration, released artifact or real adopter/decoder matrix is
established. The named-module run supplies Java codec feasibility, not Groovy Model named-module qualification.

The cases cover Core scalars/tags, exact integer/decimal category/value, String-only decoded keys and recursive duplicate
rejection, anchors/aliases/literal non-expanding <<, full single-document consumption, directives/empty documents,
Unicode and ambiguous Strings, recursive Map/List order, deterministic fixed-version bytes, cycles/repeated placements,
unsupported Number/value rejection and finite parse/emit budgets at N/N+1. The integration separately demonstrates root
Map/null restrictions and missing Struct.Required without an automatic schema gate.

Seven red controls demonstrate why library defaults are insufficient: default JSON versus Core boolean resolution,
rounded Double construction, overwritten duplicates, unknown directives disappearing in parser events, the library's ENV
extension, explicit null-tag single-space acceptance and alias graph identity. The fixed adapter uses public scanner/
parser/resolver/events/emitter/writer seams before information is lost, with exact BigInteger/BigDecimal construction.
It neither subclasses library internals nor recreates a custom YAML parser/resolver or constructs domain/JVM objects.

Two initial expectation failures were corrected, not waived. One malformed tail already failed through library syntax;
the other incorrectly expected NEL normalization. Exact Engine 2.10 execution preserves literal NEL inside a double-quoted
scalar, as well as escaped NEL. This supersedes the earlier source-only blanket normalization claim. Source folding,
style and lexical recovery remain outside the value-preservation contract.

The proof's finite 65,536-byte/32,768-code-point, depth32, nodes4096, entries2048, scalar4096, numeric-token256,
integer512-bit, decimal128-digit/scale1000 and output65,536-byte settings were test parameters, not consumer-derived
defaults. Successful 401-bit and ±900-exponent stress cases are mechanism evidence, not an obligation for default limits.
The scanner can allocate a token up to the input cap before scalar checks, and emission may allocate a chunk before the
writer rejects it. There is no library-native preallocation scalar-cap, precise heap/RSS/CPU or deadline guarantee.

The inspected POM has only test-scoped dependencies. Base bytecode is Java8; its multi-release JAR has an explicit Java11
module `org.snakeyaml.engine.v2` requiring java.base and exporting the used packages. Future Klum production baseline is
proposed as Java17; the parser's older bytecode establishes no wider Klum support. A separate Java17 dependency-edge
control compiled a client against an exported library class without its non-transitive parser dependency at compile time;
running without that dependency failed, and adding it succeeded. This supports proposed implementation/runtime metadata,
not a general Gradle/Maven publication result.

## Consumer sizing corpus

Measured 2026-11-27 through the existing private adapter using the proposed default envelope in the plan. Fifteen consumer
files were tracked and unchanged against their recorded revisions; one further literal fixture transcribes the selected
values from #1121's Native-values-1113. All sixteen parsed/emitted/reparsed under the proposed defaults. Sizing equality
used ordinary values and repeated fixed-call emission; the 204-case corpus separately checks typed recursive order.

Consumer commits: KlumAST `9c52a77dbffe62a802a60c4bb237d0e418587087` and Schelm
`608e23c7daa4f414f0d7c1453b66305d801c67f5`. Schelm was read locally without contact or mutation. The audience is small
service/environment/package descriptors and Helm values. Build copies, OpenAPI/CRD definition catalogs, native Kubernetes
manifests and adversarial dialect fixtures are excluded. These are representative development fixtures, not deployed
workload maxima, qualification of actual Schelm Models/codecs, or evidence that every future client fits the defaults.

In the table, source links pin the original repository revision. Root depth is1; nodes include keys; entries count all Map
entries and List elements. SHA-256 identifies the exact measured bytes. All consumer inputs are ASCII; the last literal
has 217 UTF-8 bytes and 210 code points.

| Fixture | Input bytes | Depth | Nodes / entries | Largest scalar points | Output bytes | Input SHA-256 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| [klum-ast: agent-skills/fixtures/helm-target-contract/src/test/resources/helm/representative/billing.values.yaml](https://github.com/klum-dsl/klum-ast/blob/9c52a77dbffe62a802a60c4bb237d0e418587087/agent-skills/fixtures/helm-target-contract/src/test/resources/helm/representative/billing.values.yaml) | 253 | 4 | 35 / 17 | 20 | 309 | `30c6027ff11fcc40fc628ed6b14484eeab4029a8605707e29803605faa4d1a5a` |
| [klum-ast: agent-skills/fixtures/helm-target-contract/src/test/resources/helm/representative/catalog.values.yaml](https://github.com/klum-dsl/klum-ast/blob/9c52a77dbffe62a802a60c4bb237d0e418587087/agent-skills/fixtures/helm-target-contract/src/test/resources/helm/representative/catalog.values.yaml) | 352 | 7 | 47 / 24 | 20 | 412 | `5a89662577783b32426ac819c1699118551b826f02bc5c7bccd40b6d41b9bb4f` |
| [schelm: schelm-skeleton/keycloak-app/gitops/models/application.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/keycloak-app/gitops/models/application.yaml) | 40 | 2 | 5 / 2 | 13 | 54 | `7e02a30c5918d37fdfb136b831e8b87d8b05734a61c7cfa263420d6c0fe69da7` |
| [schelm: schelm-skeleton/keycloak-app/gitops/models/environments.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/keycloak-app/gitops/models/environments.yaml) | 813 | 6 | 67 / 33 | 26 | 923 | `de9e378eb72aa0286e9fae8bf128ea54f6175bf9ae05119ae3f11136dbe41847` |
| [schelm: schelm-skeleton/keycloak-app/gitops/schelm.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/keycloak-app/gitops/schelm.yaml) | 424 | 3 | 27 / 13 | 45 | 478 | `bca40919d5e7e3cb8fe526c43d6b2a34377f4339567f7524bee89b595a96c0f3` |
| [schelm: schelm-skeleton/operator/environment-model/src/test/resources/fixtures/environment.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/environment-model/src/test/resources/fixtures/environment.yaml) | 1933 | 7 | 137 / 68 | 35 | 1996 | `0f73adc5e9e0641980ff3338eea35b3eb820b9023e084ab1508b5fc53429a56d` |
| [schelm: schelm-skeleton/operator/order-model/models/application.acme-order-application.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/order-model/models/application.acme-order-application.yaml) | 207 | 3 | 13 / 6 | 18 | 123 | `28f3afaab265a03f4a0bae29d051bc4c258b58ea8f1af753c9bfd3e3861b9f33` |
| [schelm: schelm-skeleton/operator/order-model/models/environments.acme-order-environments.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/order-model/models/environments.acme-order-environments.yaml) | 935 | 7 | 57 / 28 | 32 | 910 | `e24fdb0cdd7fd23b50b0e7af708fa10aa2db4ec409fc46139e57c7d5e36c5e9a` |
| [schelm: schelm-skeleton/operator/order-model/src/test/resources/fixtures/application.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/order-model/src/test/resources/fixtures/application.yaml) | 97 | 3 | 13 / 6 | 18 | 121 | `cd7002b20459a8f6bb0ba1c6f725795a6514e07ae3b27d00edba612e83e9e42c` |
| [schelm: schelm-skeleton/operator/worker-model/models/application.acme-worker-application.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/worker-model/models/application.acme-worker-application.yaml) | 149 | 3 | 7 / 3 | 8 | 55 | `c3d817015ec8c42b59f9825c473cefd3af14c300bab1de90704cd4e177277b0d` |
| [schelm: schelm-skeleton/operator/worker-model/models/environments.acme-worker-environments.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/operator/worker-model/models/environments.acme-worker-environments.yaml) | 488 | 7 | 29 / 14 | 29 | 427 | `7c7d482e019099e373f034855ecf68bb8c054e858a931c12025ecb108460e3ac` |
| [schelm: schelm-skeleton/order-app/order-schema/src/main/resources/fixtures/package.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/order-app/order-schema/src/main/resources/fixtures/package.yaml) | 153 | 3 | 9 / 4 | 51 | 175 | `18131e003cd034c4930d4bcd3a845645ab0643da1066ce895f424f31c5384d09` |
| [schelm: schelm-skeleton/schelm-core/cli/fixtures/order/schelm.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/schelm-core/cli/fixtures/order/schelm.yaml) | 684 | 3 | 33 / 16 | 53 | 672 | `646f27dc688dae4684e5179e28d74d514875c4489f8a112be1c6a9d1877fe087` |
| [schelm: schelm-skeleton/schelm-core/cli/fixtures/worker/schelm.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/schelm-core/cli/fixtures/worker/schelm.yaml) | 578 | 3 | 27 / 13 | 53 | 556 | `d16d1e7a03a0db2679732290b5512e8e9d2e7efea09a7c593d481eca16ab0506` |
| [schelm: schelm-skeleton/worker-app/worker-schema/src/main/resources/fixtures/package.yaml](https://github.com/klum-dsl/schelm/blob/608e23c7daa4f414f0d7c1453b66305d801c67f5/schelm-skeleton/worker-app/worker-schema/src/main/resources/fixtures/package.yaml) | 150 | 3 | 9 / 4 | 47 | 172 | `cdd02cf120ef062d0602357875aeb6c14b23d47313abeecfdc9b5f4cf3251513` |
| [Native-values-1113 literal](#native-values-literal) | 217 | 3 | 21 / 11 | 40 | 267 | `44f840fe1d85e066b3b7989c183fb6bff8c3ad6521281daed27b9d516af2b544` |

### Native values literal

Selected values from the merged
[#1121 qualifier](https://github.com/klum-dsl/klum-ast/blob/c06ac96af94de0bbe650be4a41f6e95850550368/klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredModelTreeQualificationTest.groovy),
method “a native scalar and container subset preserves effective values on re-entry”. This literal adds no new Model
projection claim and is documentary evidence, not an executable test input owned by the build:

```yaml
enabled: false
count: 0
text: "a\Nb-世界-😀"
total: 9007199254740993123456789
amount: 12345678901234567890.1234567890123456789
samples: [0.00000000000000000000001, 2.50]
bins: {z: 9007199254740993123456789, a: 0}
```

Across all sixteen fixtures, maxima are 1,933 input bytes/points, 1,996 output bytes, depth7, nodes137, entries68,
scalar53 code points, numeric-token40 characters, integer83 absolute-value bits, decimal precision39, absolute scale23,
precision−scale magnitude20, lexical exponent0. The named numeric fixture is a selected-value test case, not an observation
of large numbers in deployed consumer configurations. Growth allowances and derived defaults belong to the plan;
client overrides were subsequently requested by the maintainer and do not change these measurements.

## Consequences for acceptance

The local proof was sufficient to proceed to a separately authorized public-interface decision. It supplies no shipped
parser choice or integration, and #1113's qualified in-memory projection subsets remain independent. Reuse the positive,
negative and boundary cases in the eventual public Java/Groovy/JPMS/metadata matrix; add the selected interface's test
substitution, immutable configuration, lower/higher independent overrides and concurrent-call isolation coverage.
Library upgrade, public API/dependency qualification, actual consumer decoders and production implementation remain
future work. No issue state/milestone or release placement changes follow from this evidence record.
