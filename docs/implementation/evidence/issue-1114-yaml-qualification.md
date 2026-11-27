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
integer512-bit, decimal 128-digit/scale1000 and output65,536-byte settings were test parameters, not consumer-derived
defaults. Successful 401-bit and ±900-exponent stress cases are mechanism evidence, not an obligation for default limits.
The scanner can allocate a token up to the input cap before scalar checks, and emission may allocate a chunk before the
writer rejects it. There is no library-native preallocation scalar-cap, precise heap/RSS/CPU or deadline guarantee.

The inspected POM has only test-scoped dependencies. Base bytecode is Java8; its multi-release JAR has an explicit Java11
module `org.snakeyaml.engine.v2` requiring java.base and exporting the used packages. Future Klum production baseline is
proposed as Java 17; the parser's older bytecode establishes no wider Klum support. A separate Java 17 dependency-edge
control compiled a client against an exported library class without its non-transitive parser dependency at compile time;
running without that dependency failed, and adding it succeeded. This supports proposed implementation/runtime metadata,
not a general Gradle/Maven publication result.

## Consumer sizing corpus

Measured 2026-11-27 through the existing private adapter using the earlier proposed 16 KiB envelope. That small
envelope is superseded by the primary-consumer refinement below; these observations retain their original scope. Fifteen consumer
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
scalar53 code points, numeric-token40 characters, integer 83 absolute-value bits, decimal precision39, absolute scale23,
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


## Primary ScHelm consumer refinement

ScHelm is the primary reference consumer, with an explicit consumer review requested before finalizing the contract.
Current source was verified at main `8c1cf9d54b2d2054193bb5106688f0e1f9041476`; it is newer than the detached sizing
checkout above. No ScHelm production source was modified. The optional module/interface/immutable-factory decisions remain
accepted. Reuse established consumer solutions rather than independently inventing an emitter policy.

The [previous quoting correction in PR #47](https://github.com/klum-dsl/schelm/pull/47) is merged at
`abf7c0311ce3a0dda1a7e0fc1beea709226030f7`, final head
`9fd6dbdb5549486ae6d2afa30a6c0f1e5f283309`. It superseded its own initial quote-all-keys implementation. The current
[KubernetesOwnership](https://github.com/klum-dsl/schelm/blob/8c1cf9d54b2d2054193bb5106688f0e1f9041476/schelm-skeleton/schelm-core/kubernetes-targets/backend/src/main/java/com/blackbuild/schelm/targets/yaml/KubernetesOwnership.java)
quotes String values, requests plain style for a conservative ordinary-key subset and protects ambiguous keys. Its
[YamlScalarSpec](https://github.com/klum-dsl/schelm/blob/8c1cf9d54b2d2054193bb5106688f0e1f9041476/schelm-skeleton/schelm-core/kubernetes-targets/consumer/src/test/groovy/com/blackbuild/schelm/targets/yaml/YamlScalarSpec.groovy)
uses the String ConfigMap/oracle fixtures and kubeconform 0.7.0's sigs.k8s.io/yaml 1.4.0 decoder: it tests exact String values/
key sets, ordinary readable keys, actual numbers/booleans/null, fresh deterministic emission and byte-idempotence, with
unquoted 0o10 value/type and key-collision negatives. Those existing consumer guarantees must survive replacement.

[PR #64](https://github.com/klum-dsl/schelm/pull/64), merged `c0505814a72d8914935db6195e706044da215075`, uses fixed plain
Schema keys and quoted String values for package.yaml, ordinary configured Jackson projection and explicit reconstructed
projection comparison before file replacement. Its ProductDescriptorSpec supplies NEL, Unicode separators, CR/LF/tab,
quotes/slashes, controls/NUL, BOM, supplementary/combining Unicode and unmatched-surrogate rejection regressions.

The semantic requirement is decoded values/types/key identity/order, with readable ordinary keys. Exact markers,
indentation, wrapping and escape spelling are emitter presentation unless a concrete consumer fixture requires them.
The positive key guard is an implementation reuse point, not a new public scalar resolver or configurable dialect.

### Additional bounded sizing and numeric observations

The existing private adapter was reused with larger finite test parameters; its source and original 204-case results
were not modified. The following actual ScHelm source files passed strict parse/emit/reparse under the working 4 MiB,
depth 64 / nodes 100,000 / scalar 262,144 envelope. Numbers remain exact and the runner's existing all-quoted key output is only
sizing evidence, not qualification of the refined key policy.

| Actual input at pinned main | Input bytes | Depth | Nodes / entries | Largest scalar points | Output bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| kubernetes-targets/definitions/kafkauser-strimzi-1.3.0.yml | 13,897 | 19 | 488 / 264 | 761 | 14,624 |
| kubernetes-targets/definitions/kafkatopic-strimzi-1.3.0.yml | 6,329 | 14 | 263 / 136 | 327 | 6,732 |
| consumer/src/test/resources/yaml-scalars/strings-configmap.yaml | 1,030 | 4 | 103 / 51 | 26 | 1,114 |

The existing KafkaUser definition alone disproves depth 16 / scalar 512 as general defaults. Separately constructed
**anticipated, synthetic** shapes passed: an opaque ConfigMap carrying 131,072 characters of repeated public script text,
130,054 characters of simulated PEM and 225,280 characters of application configuration (510,134 input/510,153 output bytes);
a 40,000-entry configuration (1,988,972 input/2,108,982 output bytes; 80,011 nodes / 40,005 entries); and a nested domain shape
(depth 42). Simulated PEM tests text carriage, not certificate validity. These are feasibility/headroom observations, not
production maxima or claims that ScHelm already deploys these payloads. An RSS measurement was unavailable; no heap/CPU
ceiling follows from successful bounded data processing. Limit defaults and supported ranges remain recommendations. One at-scalar-limit 262,144-digit integer also passed exact
parse/emit/reparse on Java 17 with a 256 MiB heap setting (870,824 magnitude bits); that isolated success is neither a
combined worst-case allocation test nor a heap/RSS/CPU ceiling.

A separate Java 17-targeted private event check reused ScHelm's conservative key subset with Core String resolution and
legacy-word protection: six ordinary and 24 exceptional keys preserved exact typed values and key iteration order under
same-Core reparse. Values stayed quoted. This is 30 local cases, **not a new Kubernetes decoder run**. Downstream oracle
qualification remains required through the actual ScHelm corpus.

Sixteen finite Float/Double normal/extreme/0.1/zero cases passed exact binary-value conversion with
`new BigDecimal(number.doubleValue())`, emission through the existing adapter and exact BigDecimal reparse; six NaN/
infinity cases rejected. This was also executed on Java 17. Float widening preserves its stored value; shortest-decimal
and valueOf controls differ from the exact values. Observed maxima were precision 751, absolute scale 1074, decimal
magnitude 309 and 757 spelling characters. Negative-zero sign is not retained. These results support evaluating finite
binary values within derived scalar guards; they establish no new production Number contract or downstream decoder claim.

### Consumer verdict and adoption status

Schelm Hive supplied a written consolidated assessment on 2026-11-27: **compatible with specified adjustments for the
named codec replacement scope; not a transparent replacement of ScHelm's entire YAML pipeline**. The received assessment
has SHA-256 `a58b1baf77771c0800a1364070949ff6a1ac6f774c810eff3f83bfd80220dae6`; its reviewed working-plan snapshot
has SHA-256 `667b0c7dc0ae4642964f4aa143e6bbaac10453f9508f32fea4dfea4b040dabc5`. This was an actual
cross-project review of the revised working plan, source and existing regressions, with focused local experiments.
The plan incorporates its required quoting, finite-number, practical-limit and replacement-scope adjustments.

Consumer-side execution used unchanged ScHelm YamlDocuments/KubernetesOwnership compiled with Java 21 and its declared
SnakeYAML 2.4, the original private Engine 2.10 adapter as a comparison, and the actual cached Go YAMLToJSONStrict path
from sigs.k8s.io/yaml 1.4.0. No network download, production source/dependency/Package change, full Spock/JUnit run or
new KlumYaml artifact occurred. An exploratory 2.5 run was repeated with declared 2.4 and matched decoder input bytes;
a TSV analysis error caused by treating literal NEL as a record delimiter was corrected without changing input or results.

| Consumer observation | Established result / consequence |
| --- | --- |
| Current Ownership output | Actual decoder retained all 44 fixture String values and key identities; ordinary keys/readability and idempotence passed |
| Core-valid unquoted yes key versus quoted true key | Actual decoder collapsed the pair to one true key; output must protect legacy words even while Core input semantics stay fixed |
| Unquoted 0o10 String value | Actual decoder changed it to integer 8; original String values remain quoted |
| Six exact binary-value decimals | Float/Double 0.1, minimum and maximum values survived actual Kubernetes-decoder conversion; not arbitrary exact-decimal/integer precision |
| Existing scalar policy | yes→true, 012→octal 10, 0b10→2, 1:20→80 in current ScHelm versus String/decimal 12/String/String in Core; dates already remain Strings |
| Existing collection/tag policy | Current Set/omap/merge support differs from Core rejection/literal non-expanding <<; preserving those regressions needs a consumer-policy/scope decision |
| Real resource streams | Current Order prod fixture contains five documents; offsets/explicit-start provenance and bundle member text/comments cannot be supplied by a tree-only one-document operation |
| Limits | Existing KafkaUser and synthetic script/certificate/configuration/deep-model cases pass the larger candidate envelope; five public settings suffice, with no demonstrated independent entry-limit requirement |

The review identifies reusable replacement targets precisely: ModelFileLoader's duplicate/Slurper passes; ProductDescriptor's
low-level parse/data/yaml machinery; TargetYamlBackend/KubernetesYamlRenderer serialization; and Ownership's reusable
emission for admitted Core-like trees. ScHelm keeps trusted Schema/FromStruct and Jackson projection, 64 KiB / 12-container
product policy, sanitized errors, file/process/output-replacement orchestration, TargetWire/DTO checks, ownership mutations,
resource identity/deployment rules and stream/member provenance. The producer's descriptor reconstruction comparison is
not redundant machinery to delete. Generic stream framing/offset extraction remains real residual YAML work; it must not
be relabelled entirely as Kubernetes policy to claim consolidation.

Adoption must reuse YamlScalarSpec and its yaml-scalars input/expected/strings-configmap fixtures; ProductDescriptorSpec;
YamlDuplicateKeysSpec, YamlFileLoaderSpec, LoaderLifecycleSpec and YamlModelInputSpec; OwnershipSpec and ArtifactChecksSpec.
Concrete adoption anchors include YamlScalarSpec's “ownership emits exact Kubernetes-sensitive string values from #source”
and both octal decoder negative controls; ProductDescriptorSpec's Unicode preservation and failure-before-replacement
cases; YamlDuplicateKeysSpec's duplicate-before-authored-operation test; LoaderLifecycleSpec's one lifecycle-tail test; and
ArtifactChecksSpec's empty-document member-origin case. They cover decoder key/type/Unicode/idempotence controls and
malformed/duplicate/unsafe input, source/ownership/lifecycle
rules, failure-before-publication and stream/provenance attribution. Reuse their independent expected data rather than
replace it with same-parser equality alone. Preserve the three A/B/C slices, with C proving actual adoption of the named
seams on ScHelm's Java 21 / Groovy 5.1.3 consumer stack through owned Models, packaged descriptors, emitted resources and
independent consumer artifacts.

No actual KlumYaml artifact exists. The received conditional design verdict is not production adoption qualification,
JPMS/publication qualification, complete updated consumer-stack execution or a live-cluster result. Before implementation
authorization, accept the explicit narrower replacement scope or decide how residual stream/resolution requirements will
be owned. No new stream interface, dialect, parser SPI or silently dropped regression is selected. The implementation
criterion is replacement of the agreed reusable machinery without loss of correctness; actual artifact acceptance remains
pending implementation and consumer tests.
