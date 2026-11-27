# #1114 — optional YAML interface and implementation plan

Date: 2026-11-27. Category B extension of [ADR 0034](../adr/0034-bounded-structured-authoring.md).
Related: [#1114](https://github.com/klum-dsl/klum-ast/issues/1114).

Status: **Public interface/factory and captured overridable limits selected; supporting contract proposed;
production adoption and implementation unassigned.** This document is a plan, not a delivered client guide.

## Authority and current behavior

The maintainer broadly agreed with the bounded proposal, requested client-overridable limits, preferred a factory-created
instance that captures those limits, and then explicitly selected **public interface `KlumYaml`**. Its builder constructs
the immutable configured implementation. Ordinary client test doubles and parser-free exported signatures are the reasons
for this selection. Interface versus concrete class is settled; static parse/emit operations are superseded.

All remaining details below are concrete working recommendations unless identified as an existing ADR requirement.
Broad agreement is not evidence of exact supporting descriptors or production qualification. The accepted interface can
be recorded and reviewed before adopting a library or implementing the module.

Today [Structured Input](../user/Structured-Input.md) accepts caller-parsed Maps through generated Factory/Builder
capabilities. Parsing is caller-owned. FromStruct requires its root Map, rejects reached nulls at every depth, executes
admitted operations in supplied order and preserves earlier effects on failure. JSON Schema is never an automatic runtime
gate. [Jackson](../user/Jackson-Integration.md) independently owns caller-mapper foreign-format binding and asymmetric
effective-Model projection. The [private proof record](evidence/issue-1114-yaml-qualification.md) covers YAML/shared trees,
not those Models or a released adapter. Its results are evidence for the plan, not public-candidate acceptance.

## Proposed placement and dependency contract

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

Recommend integer-category parsing as BigInteger and decimal-category parsing as BigDecimal. Emission accepts the four
integral wrappers, BigInteger and BigDecimal; reject every Float/Double (including finite values) and other Number types.
Preserve mathematical value and integer/decimal category, not wrapper identity, decimal scale/spelling or signed zero.
Jackson can successfully project unsupported Numbers; codec admission checks its actual tree without changing that mapper.
Finite Float/Double Schema-target conversion in Struct remains unchanged. Exact-binary float emission would need a separate
explicit contract and fixtures; there is no implicit decimal-intent recovery.

Recommend empty/whitespace/comment-only streams reject, while an explicit empty document is tree null. Reject every
directive, including `%YAML 1.2`, `%TAG` and unknown directives, before scanner information disappears. Reject a second
document even if empty. Emit null explicitly and quote/escape all Strings/keys, with LF and explicit single-document
markers; no anchors or sorting. Deterministic bytes concern fixed codec/library/configuration and ordered input only.

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

## Proposed default resource envelope and selected override ownership

The [measured corpus](evidence/issue-1114-yaml-qualification.md#consumer-sizing-corpus) covers small descriptors, service/
environment inputs and Helm values, plus selected #1113 exact numbers. Sixteen fixtures passed the existing private adapter
under this proposed default envelope. These are defaults, not universal client maxima or a workload guarantee.

| Resource | Measured maximum | Proposed inclusive default |
| --- | ---: | ---: |
| Input/output UTF-8 bytes | 1,933 / 1,996 | 16,384 each |
| Input code points | 1,933; emitted text ≤1,996 | 16,384 |
| Depth, root = 1 | 7 | 16 |
| Nodes, including Map keys | 137 | 2,048 |
| Total Map entries + List elements | 68 | 1,024 |
| Scalar/key code points | 53 | 512 |
| Numeric token characters | 40 | 256 |
| Integer absolute-value bits | 83 | 256 |
| Decimal precision | 39 | 128 |
| Absolute decimal scale | 23 | 64 |
| Decimal magnitude, precision − scale | 20 | ≤64 |
| Absolute lexical exponent | 0 | 64 |

Round up to powers of two after allowing 8× size/count/scalar maxima, 2× depth/numeric-value maxima and 4× token length.
For both text directions use the larger measured text size. Exponent's default reuses the measured scale/magnitude
envelope, not an observed exponent workload. These growth allowances are product proposals, not measurement results.

Clients may independently raise or lower every finite positive setting within supported, qualified ranges. Exponent,
decimal scale and decimal magnitude are separate limits despite equal defaults. Validate settings before use; reject
unlimited/invalid/unsupported values without clamp, overflow or silent disregard. Supported upper ranges need mechanism
qualification, especially recursive depth and Java decimal/conversion/counter ranges; do not invent arbitrary hard ceilings
from the old probe. Overrides never weaken YAML semantic policy and need no per-call limits argument.

Check supplied bytes before UTF-8 decode and String bytes/code points before scanning, scalar/numeric text before numeric
construction and exact value bounds afterward. Bound lexical exponent work independently before BigDecimal construction,
allowing leading zero spelling within the token cap. Count repeated placements on each traversal; bound output while
emitting. No partial output returns. All limits apply jointly, not as an independently attainable Cartesian product.
The scanner may allocate one token up to the whole-input cap before its token guard, and the emitter may allocate a chunk
before the writer rejects it. No exact heap/RSS/CPU/deadline or already-materialized caller-memory bound is established.

## Implementation slices and acceptance map

No slice is assigned by this documentation. Library adoption remains a separately authorized prerequisite to production
dependency declarations. Final supporting contracts and ranges must be reconciled before implementation acceptance.

| Slice / reasoned commit boundary | Modules and public/generated seams | Required acceptance |
| --- | --- | --- |
| A — usable strict parse with factory-owned limits | Proposed optional artifact, public interface/builder/limits/exception, internal scanner/event adapter; no generated code changes | Pin separately authorized candidate; parse corpus/red controls; all root kinds; strict UTF-8; exact numbers/keys/tags/directives; whole input; duplicate rejection; each parse budget N/N+1; lower/higher/invalid independent overrides; builder snapshots; client fake compiled against exported signatures without parser; Java 17 classpath/JPMS and actual Maven/Gradle metadata |
| B — usable exact ordered emission | Same artifact and limits/diagnostics; internal tree/event writer | Original versus reparsed typed values and recursive Map/List order; Unicode/ambiguous Strings; category/exact numbers; unsupported values/cycles; repeated placement counts; every emit budget N/N+1; output bounds; deterministic bytes; fresh/concurrent-call state |
| C — composed consumer contract and documentation | Existing generated Factory/active Builder and optional Jackson seams, consumer fixtures, user guidance | Matched Java and static/dynamic Groovy 3/4/5 artifact consumers; Groovy 4/5 named Schema consumers; parser-free core dependency check; root Map/null/positional-key boundaries; Struct.Required without schema gate; ordered earlier effects; original Struct/lifecycle attribution; qualified actual projection subsets only |

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

The selected public-interface choice is no longer open. Remaining supporting decisions are the exact optional artifact/
module identity, builder/limits/diagnostic descriptors, supported limit ranges and final default/value/document details
proposed above. They need final contract reconciliation, not a new parser/framework or a repeated interface interview.
No parser release, release placement or production implementation is authorized by agreeing or merging this plan.

Tracker relationship: Related: #1114. Keep issue state, labels, milestone and native dependencies unchanged. This is a
documentation/decision slice, not issue completion. Hive reconciliation remains separate from worker execution/delivery.
