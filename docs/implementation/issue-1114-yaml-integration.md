# #1114 — optional YAML interface and implementation plan

Date: 2026-11-27. Category B extension of [ADR 0034](../adr/0034-bounded-structured-authoring.md).
Related: [#1114](https://github.com/klum-dsl/klum-ast/issues/1114).

Status: **Public interface/factory, optional module and captured overridable limits selected; supporting contract proposed;
production adoption and implementation unassigned.** This document is a plan, not a delivered client guide.

## Authority and current behavior

The maintainer broadly agreed with the bounded proposal, requested client-overridable limits, preferred a factory-created
instance that captures those limits, and then explicitly selected **public interface `KlumYaml`**. Its builder constructs
the immutable configured implementation. Ordinary client test doubles and parser-free exported signatures are the reasons
for this selection. Interface versus concrete class is settled; static parse/emit operations are superseded.

The refinement treats ScHelm as the primary reference consumer: the goal is to replace recurring reusable YAML machinery
with one qualified KlumAST codec. Reuse its implementation and regressions before devising alternatives. Its explicit
consumer verdict and the replacement scope below gate final contract acceptance; they create no KlumAST dependency on
ScHelm. Optional-module existence, the public interface and immutable factory-created implementation are settled.

All remaining details below are concrete working recommendations unless identified as an existing ADR requirement.
Broad agreement is not evidence of exact supporting descriptors or production qualification. The accepted interface can
be recorded and reviewed before adopting a library or implementing the module.

Today [Structured Input](../user/Structured-Input.md) accepts caller-parsed Maps through generated Factory/Builder
capabilities. Parsing is caller-owned. FromStruct requires its root Map, rejects reached nulls at every depth, executes
admitted operations in supplied order and preserves earlier effects on failure. JSON Schema is never an automatic runtime
gate. [Jackson](../user/Jackson-Integration.md) independently owns caller-mapper foreign-format binding and asymmetric
effective-Model projection. The [private proof record](evidence/issue-1114-yaml-qualification.md) covers YAML/shared trees,
not those Models or a released adapter. Its results are evidence for the plan, not public-candidate acceptance.

## Selected optional module and proposed dependency details

Recommend optional `com.blackbuild.klum.ast:klum-ast-yaml`, module `com.blackbuild.klum.ast.yaml`, exporting only
`com.blackbuild.klum.ast.yaml`; put implementation in its unexported `.internal` package. Production uses Java 17 and
JDK values, with no runtime/annotations/compiler/Groovy/Jackson production dependency. Existing core/runtime artifacts
stay parser-free. Applications opt in; Schema/Model plugins do not inject it.

Use existing Java, publication and descriptor conventions. The BOM's Java-library enumeration can align the optional
artifact without selecting it. An eventually authorized parser dependency should use Gradle `implementation`, Maven
runtime scope and non-transitive JPMS `requires`. Its types must remain absent from every exported signature. Qualify
the actual Gradle metadata, POM and named-module consumers; a local Java 17 dependency-edge control supports this shape
but is not published-build proof. Do not shade, add a parser-wide BOM constraint, or create a Maven/plugin framework.

No parser release is selected. After separate adoption authority, keep one exact version in the existing settings catalog
and record the actually resolved coordinates/hash, Java baseline, transitives, license and exports. The inspected Engine
2.10 POM has no runtime transitives; that is evidence about that release, not a future dependency guarantee.

## Selected factory shape and proposed instance surface

The public interface is intended to be implemented by consumer test doubles. This differs deliberately from generated
`Foo_DSL` contracts, which clients must not implement. The built-in factory returns only the module-owned implementation;
there is no parser registry, library-settings input, dialect SPI or generic codec framework.

Proposed instance members (not production declarations):

```java
public interface KlumYaml {
    Object parse(String text, String sourceName);
    Object parse(byte[] utf8, String sourceName);
    String emit(Object tree, String sourceName);
}
```

`KlumYaml.builder()` creates a module-owned builder; recommend a final nested `KlumYaml.Builder` with non-public
construction, `withLimits(KlumYamlLimits)` returning that builder, and `build()` returning `KlumYaml`. Recommend immutable
`KlumYamlLimits` with a builder that inherits unmentioned settings from measured defaults. No generic factory type or
additional reader/writer interfaces are justified. Those supporting names/member details remain proposals.

Java usage sketch, with caller-configured `limits` and an existing generated Service Factory:

```java
import com.blackbuild.klum.ast.yaml.KlumYaml;
import java.util.Map;

KlumYaml yaml = KlumYaml.builder().withLimits(limits).build();
Object tree = yaml.parse("name: catalog\nhost: yes\n", "service.yaml");
if (!(tree instanceof Map<?, ?> document)) {
    throw new IllegalArgumentException("Service input must be a mapping");
}
Service service = Service.Create.FromStruct(document, "service.yaml");
String output = yaml.emit(document, "service-output");
```

Equivalent Groovy with Schema/Model syntax:

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.yaml.KlumYaml

@DSL class Service { String name; String host }
def yaml = KlumYaml.builder().withLimits(limits).build()
def tree = yaml.parse('name: catalog\nhost: yes\n', 'service.yaml')
if (!(tree instanceof Map)) throw new IllegalArgumentException('Service input must be a mapping')
def service = Service.Create.FromStruct((Map) tree, 'service.yaml')
assert service.host == 'yes'
```

`KlumYaml.builder().build()` selects defaults. Build snapshots configuration; later builder reuse cannot alter a codec.
The built-in instance is reusable and thread-safe for independent calls. Scanners/parsers/emitters, counters, cycle guards
and diagnostics are per operation. Each configured limit applies afresh, not as a shared quota. Consumers may inject
`KlumYaml` directly; their fakes need no parser-specific types or actual parsing calls. Factory use is not mandatory for
test-double construction. This does not remove the artifact's transitive runtime dependency: named-module startup with
the proposed plain requires edge still resolves the parser module, even when only a fake is used. No parser-free module-
path runtime is promised. No thread-safety claim is imposed on arbitrary client implementations.

Require opaque non-null/nonempty diagnostic source names per call; never open, normalize or derive keys from them.
Parsing takes supplied text/strict UTF-8 bytes and returns fresh mutable LinkedHashMaps/ArrayLists and immutable scalar
values. Return Object because ADR 0034 admits all tree roots, including null; a generic or Map-only result would be false.
Caller owns the result, with no parser nodes, source buffers or retained locations. Emission borrows a stable caller tree
for the call, mutates/retains nothing and returns only complete output. Repeated acyclic placements expand structurally;
cycles reject. File/URL/Reader/Writer/stream loading and closing, domain construction and Model projection stay separate.

## Proposed remaining value and diagnostic policy

Keep ADR 0034's Core resolution, String-only decoded keys, duplicate rejection before insertion, anchor/alias rejection,
literal non-expanding `<<`, tag allowlist, complete single-document consumption, order and typed-value fidelity.

Recommend integer-category parsing as BigInteger and decimal-category parsing as BigDecimal. Emission accepts integral
wrappers, BigInteger/BigDecimal and **finite Float/Double**. Convert each binary value with
`new BigDecimal(number.doubleValue())`: widening a Float to double is exact, so this preserves the stored mathematical
binary value. It recovers no decimal intent. Do not use shortest-string/valueOf conversion as an exact-value substitute.
Reject NaN, infinities and unsupported Number implementations. Signed-zero sign, JVM wrapper identity and original numeric
spelling are not retained. The result is a decimal-category tree value, including when its mathematical value is integral.

ScHelm's [TargetValues.freeze](https://github.com/klum-dsl/schelm/blob/8c1cf9d54b2d2054193bb5106688f0e1f9041476/schelm-skeleton/schelm-core/kubernetes-targets/support/src/main/java/com/blackbuild/schelm/targets/TargetValues.java)
already admits finite Float/Double, so blanket rejection would require a consumer adapter.
This removes that friction from its existing finite binary-number paths. Exact output can be long: private extrema
checks observed precision 751, scale 1074 and 757 numeric characters for Double.MIN_VALUE. Numeric admission must allow such
values within the scalar/input/output envelope rather than silently round them. Same-Core reparse preserves the exact
BigDecimal. Schelm Hive's focused six-value test also passed through its actual sigs.k8s.io/yaml 1.4.0 decoder; that
decoder can reduce decimals to binary numbers. This is evidence for those selected values, not universal downstream
precision or schema-target reconstruction qualification. No selectable numeric mode.

Recommend empty/whitespace/comment-only streams reject, while an explicit empty document is tree null. Reject every
directive before scanner information disappears and a second document even if empty. These single-document rules do not
replace ScHelm's multi-document framing or Kubernetes-specific input semantics.

**Emission follows ScHelm's corrected policy:** quote String values, emit ordinary String mapping keys plain, and quote
exceptional keys to preserve decoded String identity/type and YAML syntax. Ordinary fields such as apiVersion, metadata,
app.kubernetes.io/name and schelm.io/deployment-id must remain readable. Reuse ScHelm PR #47's corrected key evidence;
do not restore blanket key quotation or introduce a configurable quoting strategy.

Keys that resolve as Core bool/null/int/float require quotes. Empty keys, leading/trailing whitespace, controls, multiline
and YAML-syntax-sensitive cases need safe quotation/escaping. Keep ScHelm's legacy-decoder-sensitive String keys protected too:
y/n/yes/no/on/off and date/numeric-looking keys, including the tested 0o10 versus 8 collision. This does not alter Core
input resolution. The existing positive key subset `[A-Za-z_][A-Za-z0-9_./-]*`, checked with Core String resolution plus
legacy-word protection, is a reusable conservative implementation starting point. It is not a claim that every other
printable key is intrinsically invalid plain YAML or a permanent promise about its exact quoting style.

Require exact decoded values, integer/decimal category, recursive Map/List order, no generated anchors/aliases, and
fixed-version/configuration determinism. Document markers, indentation, line wrapping and quote/escape spelling are
implementation presentation choices, not cross-version compatibility commitments. Preserve necessary single-document
syntax and separation when ScHelm composes streams. Any actual dependency on an end marker or byte-idempotence must be
proved by the consumer fixtures; there is no formatting selector or new canonical-YAML promise.

Recommend final module-local `KlumYamlException extends IllegalArgumentException`, with non-public construction,
operation/source getters, nested Reason (`SYNTAX`, `ENCODING`, `POLICY`, `LIMIT`, `VALUE`), Optional String pointer and
OptionalInt line/column. Import those JDK types in eventual declarations. Use RFC 6901 pointers; root is present empty
String, unknown is Optional.empty. A duplicate points to the second member; an invalid key points to its containing Map.
Only available supplied-text marks become 1-based Unicode-code-point line/column. Predecode/emission errors have no text
location. Do not invent a syntax leaf or a later Struct location from a plain tree.

Preserve original library causes without exposing them in method signatures; library cause wording is not stable.
Wrapper messages omit source excerpts and escape controls in rendered labels/pointers. JVM Errors pass through; unexpected
adapter bugs are not syntax failures. Null text/bytes/source arguments use NPE naming the parameter; empty source is
IllegalArgumentException. Emitting tree null is valid. No runtime dependency is needed solely for KlumModelException.

## Working defaults and a small public limits value

Recommend five independently configurable consumer limits captured at codec construction:

| Public setting | Working default | Consumer reason |
| --- | ---: | --- |
| Input UTF-8 bytes | 4 MiB (4,194,304) | A practical single-document envelope for application/resource configuration |
| Output UTF-8 bytes | 4 MiB | Explicit output allocation/publication allowance; independent of input because escaping can expand text |
| Depth, root = 1 | 64 | Nested domain/configuration structures; actual ScHelm KafkaUser definition already reaches 19 |
| Nodes, including Map keys | 100,000 | Bounds tree traversal/allocation work independent of text size |
| Scalar/key code points | 256 Ki (262,144) | Embedded scripts, certificates and application configuration as opaque String data |

These are recommendations under consumer review, not accepted production values. The original sixteen fixtures are
minimum coverage, not a justification for caps that ordinary consumers immediately override. The earlier 16 KiB / depth 16 /
scalar 512 envelope is superseded: ScHelm's existing KafkaUser definition is 13,897 bytes, depth 19 and scalar 761. New bounded
anticipated fixtures cover roughly 500 KiB of script/simulated-PEM/application-text payloads, a 40,000-entry/~2 MiB configuration
and depth 42. They demonstrate feasibility, not deployed maxima. The evidence record distinguishes actual and synthetic
inputs. Confirm the envelope with ScHelm before finalizing; do not infer a 4 MiB memory ceiling from a 4 MiB input limit.

Keep the remaining guards internal and derived, rather than expose implementation-specific numeric settings:

| Internal guard | Derivation / enforcement |
| --- | --- |
| Whole-input code points | At most the configured UTF-8 byte ceiling; strict decode and pre-scan checks remain |
| Cumulative entries | At most nodes−1; count before insertion. With 100,000 nodes, a mapping-only root permits 49,999 entries and a sequence-only root 99,999. The suggested separate 50,000-entry public setting is unnecessary unless a consumer establishes an independent requirement. |
| Numeric token / decimal precision | At most the scalar-code-point allowance; check lexical size before construction, never use unlimited expansion |
| Absolute lexical exponent / decimal scale / magnitude | Each bounded by the scalar allowance with overflow-safe comparisons before conversion/expansion. They remain distinct checks, not independently configurable properties. |
| Integer magnitude | At most 4×scalar allowance in bits (hex needs up to 4 bits per digit); use wide arithmetic and bounded conversion |

Thus scripts can use a generous scalar allowance while numeric work remains finite; there is no new resource-budget
framework. The default derived decimal envelope includes every tested finite Float/Double exact expansion. Very large
numeric tokens can still be costly: qualify at-limit/adversarial conversion under the combined input/node/scalar caps
before accepting these derived ranges. If that fails, return a concrete internal-guard decision supported by measurements,
not a public precision/scale/exponent mode matrix or silent rounding. No unbounded pow/toPlainString allocation is allowed.

Clients can lower or raise the five relevant limits within supported, qualified ranges. Validate positive finite values
and reject unsupported settings without clamp, overflow or silent disregard. The node-derived entry allowance follows
node overrides; numeric guards follow scalar overrides. Whole-input checks precede scanning, token checks precede numeric
construction, and exact value checks precede admission/emission. Count repeated structural placements each time. Limits
are joint, per-operation allowances, not a promise that every combination fits every other cap. Escaping can exhaust the
output cap even when input fits; return no partial result.

The scanner may allocate a token up to the whole-input cap before scalar inspection; the emitter may allocate a chunk
before writer rejection. Many small nodes, large scalar copies and simultaneously active calls also consume memory.
No precise heap/RSS/CPU/deadline or already-materialized caller-memory bound is established. Bound and measure supported
ranges, especially recursive depth and large numeric tokens, in the eventual implementation; no unlimited sentinel.

## ScHelm replacement scope and consumer acceptance

The target is reusable text/tree machinery, not ScHelm's resource/rendering/deployment policy. ScHelm's current source at
main 8c1cf9d is the reference; the earlier detached-checkout sizing revision is historical only. Schelm Hive's written assessment is **compatible with specified adjustments for the named codec replacement scope**.
Its focused local/actual-decoder results are recorded in the existing evidence document; no real KlumYaml artifact has
been qualified. The adjustments below are required for that conditional design verdict.

| Existing ScHelm seam | Intended replacement | ScHelm retains / condition |
| --- | --- | --- |
| ProductDescriptor.read/data and yaml | Strict UTF-8/text-to-tree, duplicate/policy checking and tree emission; remove direct low-level YAML traversal/Representer machinery once qualified | Trusted PackageSchema selection, FromStruct, String-only descriptor grammar, 64 KiB / 12-container product profile, intrinsic checks and sanitized source errors; codec node depth includes scalar/key nodes and is not the same counter |
| ModelFileLoader.readYaml | Replace duplicate-detection pass plus YamlSlurper decoding with one checked tree parse | File selection/I/O, root Map check, contextual labels and Factory/active-Builder/copy workflows; all existing inputs must prove intended Core meanings |
| ProductDescriptor.yaml / PackageDescriptorWriter | Reuse codec emission/parsing instead of local emitter settings/key hooks | Caller-configured Jackson projection, descriptor-specific reconstruction comparison and failure-before-file-replacement/publication ordering |
| TargetYamlBackend.render and KubernetesYamlRenderer.render | Replace YAMLMapper/YamlBuilder serialization with emission of TargetWire.document(target) / resource.asDocument() | Target presence/wire validation, DTO projection and contribution creation; renderAll stream assembly stays caller-owned and must not rely on incidental codec markers |
| KubernetesOwnership single-document YAML emission | Replace reusable String/key escaping, exact-number emission and typed-value reparse mechanics for Core-compatible trees | Ownership mutations, reserved annotations, resource checks, media types and actual Kubernetes decoder guarantees; no assumption that every legacy tree is admissible |
| YamlDocuments, YamlBundle and KubernetesDuplicateCheck | No whole-sale replacement by a single-document Core codec | Multi-document offsets/explicit-start provenance, comment-preserving bundling/boundary checks, Kubernetes identity validation and legacy decoder interpretation remain; set/omap/merge/legacy booleans differ materially. Stream framing/offset extraction includes residual reusable YAML mechanics, not just domain policy |

**Adoption criterion:** the qualified implementation must let ScHelm replace the intended reusable machinery above without
losing established correctness guarantees, demonstrated through its existing positive/negative regressions and real target/
Package paths on ScHelm's Java 21 / Groovy 5.1.3 stack and independent consumer artifacts. A generic same-parser round
trip is insufficient. Preserve descriptor writer reconstruction/failure-before-replacement and ownership idempotence. If ScHelm must retain duplicate generic machinery inside
that agreed scope, the feature has not met this criterion. A decision on a required stream seam or intentional narrower
replacement scope must be explicit; do not quietly broaden Core to a Kubernetes dialect or claim full replacement.

## Implementation slices and acceptance map

No slice is assigned by this documentation. Library adoption remains a separately authorized prerequisite to production
dependency declarations. Final supporting contracts and ranges must be reconciled before implementation acceptance.

| Slice / reasoned commit boundary | Modules and public/generated seams | Required acceptance |
| --- | --- | --- |
| A — usable strict parse with factory-owned limits | Proposed optional artifact, public interface/builder/limits/exception, internal scanner/event adapter; no generated code changes | Pin separately authorized candidate; parse corpus/red controls; all root kinds; strict UTF-8; exact numbers/keys/tags/directives; whole input; duplicate rejection; each public and derived guard N/N+1; lower/higher/invalid public overrides and derived relationships; builder snapshots; client fake compiled against exported signatures without parser; Java 17 classpath/JPMS and actual Maven/Gradle metadata |
| B — usable exact ordered emission | Same artifact and limits/diagnostics; internal tree/event writer | Original versus reparsed typed values and recursive Map/List order; ScHelm ordinary/exceptional keys and actual Kubernetes decoder negatives; Unicode/ambiguous Strings; exact binary-float conversion and category/exact numbers; unsupported values/cycles; repeated placement counts; each public/derived emit guard N/N+1; output bounds; deterministic bytes; fresh/concurrent-call state |
| C — composed consumer contract and documentation | Existing generated Factory/active Builder and optional Jackson seams, consumer fixtures, user guidance | Matched Java and static/dynamic Groovy 3/4/5 artifact consumers; Groovy 4/5 named Schema consumers; parser-free core dependency check; root Map/null/positional-key boundaries; Struct.Required without schema gate; ordered earlier effects; original Struct/lifecycle attribution; qualified actual projection subsets; explicit ScHelm replacement/adoption criterion above, with consumer verdict and named regression corpus |

Keep production changes and their tests together per slice. A separate final documentation commit may follow the tested
consumer work. Input-only slice A does not satisfy the accepted bidirectional product responsibility. Reuse repository
Spock/scenario/consumer seams; every added test needs @Issue('1114'), executable classes use Test suffix, documentary
coverage links user pages through documentary tags/@See where applicable. No speculative pending tests are added now.

The new artifact/publication topology requires root check plus targeted artifact/metadata/Maven/Gradle/JPMS tests before
publication, followed by exact-head CI/Sonar inspection. Groovy 3 stays classpath-only for Model consumers; Groovy 4/5
named consumers follow ADR 0014. The standalone Java codec must run without Klum/Groovy/Jackson, reflection, descriptor
patches or add-opens/add-exports. Preserve test-lane isolation and matched Schema/compiler/runtime/annotations/plugin/BOM.

An upgrade repeats semantic/resource/consumer/dependency qualification on the resolved release. Reduced limits or narrower
admission need behavioral compatibility review; higher caps need resource requalification. Byte changes across upgrades
are reviewed explicitly, not prohibited by an invented canonical format. No arbitrary resolution override gains support
automatically. Keep sources/Javadocs/third-party notices and publication inventories aligned when the module is implemented.

## Documentation, compatibility and remaining work

This plan changes no source/runtime behavior or delivered interface, so current user pages, migration navigation and
CHANGES remain accurate. When implemented, document the optional codec and policy in docs/user, link sidebar and affected
Struct/Jackson pages, update migration navigation if needed and record actual delivered behavior in CHANGES. Turn the
examples into compiled/documentary consumer tests; the sketches here must not become a claim that the type exists today.

No Model/Template serialized state, retained provenance, lifecycle phase, ownership rule, compiler-generated ABI or metadata
protocol changes. Existing caller parsers and managed Jackson remain compatible. #1113/#1121 effective-Model subsets,
#953 resource loading, #1085 active-child ergonomics, #955 trusted migration and #971 enforcement retain independent owners.
Generated input JSON Schema is neither output/reconstruction schema nor an automatic codec/Struct validation gate.

The public interface, builder-created immutable implementation and optional-module decision remain accepted. Remaining
supporting decisions are exact artifact/module naming and supporting descriptors, the practical default/supported ranges
and derived numeric guards, finite binary-value emission and the precise ScHelm replacement/stream boundary. Finalization
uses the received conditional ScHelm verdict. Remaining scope decisions must be accepted explicitly; no new interface
interview, framework or blanket replacement claim is warranted.
No parser release, release placement or production implementation is authorized by agreeing or merging this plan.

Tracker relationship: Related: #1114. Keep issue state, labels, milestone and native dependencies unchanged. This is a
documentation/decision slice, not issue completion. Hive reconciliation remains separate from worker execution/delivery.
