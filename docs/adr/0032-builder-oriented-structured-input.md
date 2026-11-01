# Builder-oriented structured input for 4.2

Date: 2026-10-31.

Status: **Accepted; vocabulary V0, ordered-traversal S1, exact String-selection S2, String-origin parity S3a and zero-argument commands S4 implemented. Other slices remain planned.**
Implementation status: S1 replaces whole-document payload preflight with ordered per-operation execution, retaining
contract integrity and construction-key prerequisites. No new grammar is admitted. See the
[S1 evidence](../implementation/input-round-s1-evidence.md). S2 adds the method marker and shared exact String selection;
see [S2 evidence](../implementation/input-round-s2-evidence.md). S3a admits exact generated String converter origins; see
[S3a evidence](../implementation/input-round-s3a-evidence.md). S4 admits zero-argument Builder commands with empty mappings;
see [S4 evidence](../implementation/input-round-s4-evidence.md). CharSequence invocation remains planned.

Authority: the maintainer's INPUT-ROUND-PLAN decisions, reconciled in the
[implementation and issue plan](../implementation/input-round-plan-4-2.md). The 4.2 horizon records that direction;
it does not change GitHub milestones or establish release qualification.

Partially supersedes [ADR 0031](0031-structured-schema-input-artifacts.md): whole-document preflight, the narrow
origin-dependent scalar-admission boundary, and deferral of the specific operations accepted below. Its reflection-first
architecture, generated-contract authority, naming, key authority, no-user-code inspection and delivered projections
remain. [ADR 0009](0009-jackson-interoperability.md) remains independent and unchanged.

Parent decisions: [Builder-first construction](0003-builder-first-materialization.md),
[AsBuilder](0004-asbuilder-composition-protocol.md), [public generated contracts](0005-generated-dsl-support-api.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md), and
[named-map safety](0022-conservative-named-map-safety.md).

## Context and successor boundary

The delivered interpreter has useful concrete-child, keyed-Map, scalar and authored-operation support. However, an
ordinary Builder can expose a useful String operation that structured input excludes because it came from a field
converter, an adapter, or a CharSequence factory. Converter constraint inspection has inadvertently become a stronger
admission boundary than the intended Builder language. A String operation is useful even when its schema says only
`type: string` and its body owns validation.

The accepted direction also changes a visible failure guarantee: v1 rejects a bad later payload before any operation,
whereas the new interpreter resolves and executes operations in order. A successor makes that compatibility change
auditable without rewriting v1 evidence or adding another contradictory checkpoint to ADR 0031. This is a category B
revision of the existing architecture, not a replacement construction engine.

The SCHEMA-ROUNDNESS assessment established that Schelm's existing three YAML documents do not demonstrate a missing
core capability. Subsequent maintainer direction nevertheless accepts broader Builder-oriented scalar completeness.
These are different claims. Neither the older adopter pass nor this plan proves the new language implemented.

## Decision

### One admitted operation after compiler projection

The compiler projects Schema declarations into the public Builder contract. Structured admission operates on that
contract: authored method, generated setter, field converter or ordinary converter/factory origin is irrelevant to
eligibility once the operation's role, exact callable shape and semantics are established. Retain source association for
diagnostics, ownership, naming and independently qualified constraint facts; origin independence does not erase those
facts or admit every public method. Framework helpers, lifecycle/query controls and open dynamic selectors remain out.

Primary 4.2 scalar scope is one unambiguous admitted operation taking an exact String payload. A public String parameter
or a deterministic CharSequence parameter that accepts that same String may serve it. Do not convert numbers, booleans,
GStrings, arbitrary objects or mutable CharSequence instances into Strings, construct StringBuilder arguments, or treat
Object/Serializable as a new generic scalar route. Preserve existing exact numeric, boolean and enum input support.

Selection is fixed from the compiled contract, never from runtime value guessing, method enumeration order, method-name
preferences or “try each converter.” String and CharSequence candidates competing for one external input do not acquire
an implicit specificity preference. One authoritative mark wins among otherwise eligible operations; more than one mark
fails; without a mark, admission requires exactly one candidate. A mark cannot override exclusion, an unsupported role,
missing public projection or a signature collision that prevented the compiler from generating that operation.

Selection applies to an effective external member after established exclusion/name/profile resolution. A renamed
operation retains only its effective name. Inherited candidates and overrides must resolve to the exact callable
implementation; a descendant may not silently execute different code while retaining the parent's converter assertions.
Conflicting literal names, ignored construction keys and other malformed metadata still fail under existing rules.

**Accepted vocabulary: `@Struct.Operation`.** The maintainer selected `Struct` as the outer namespace for the shared
external-document contract. The [annotation inventory and naming decision](../implementation/input-round-annotation-vocabulary.md)
record Name, Ignore, Naming, Examples, Operation and constraint projection/description members. The maintainer also
confirmed that existing names were used only by internal tests and Schelm qualification: use a direct internal rename,
with no migration, cutover, aliases, deprecation window or compatibility bridge. This supersedes the earlier standalone
`InputOperation` and `StructuredInput.*` proposals. `Builder.Input` and ordinary converter vocabulary retain their meanings.
The following syntax selects the String operation; CharSequence invocation remains deferred:

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct

@DSL
class Service {
    String host

    @Struct.Operation
    @Builder.Method void endpoint(String value) { host = value }
    @Builder.Method void endpoint(CharSequence value) { host = value.toString() }
}

// Model syntax already expresses the intended selected operation:
def model = Service.Create.With { endpoint 'api.internal' }
```

The first authoring target is a Schema method with a public Builder projection. Generated candidates carry the
equivalent compiler selection fact; users never edit generated source. Any additional source placement must identify
exactly one generated operation. A field annotation must not silently choose among several converter overloads.
An authored marked wrapper is a viable escape from an otherwise ambiguous generated family.

### Scalar converters, scopes and commands

Field-declared String converters are included, using their existing generated Builder operations. Same-compilation
values and third-party adapters are not excluded merely because they lack C1's separately compiled domain-parameter
inspection path. They still need a real, exact, unambiguous public projection. Do not extend general converter discovery,
default/varargs expansion or generic inference to manufacture one. Converter-backed scalar List elements and Map values
are follow-on work after scalar qualification, not an automatic recursive extension.

A String-to-concrete-DSL route is conditional: ordinary projected Builder semantics must already preserve same-session
unsealed ownership, key authority, callback ordering, attachment/reuse and diagnostics. A factory returning a completed
Model is not evidence of equivalence. If the proof fails, retain rejection; do not add nested root creation or conversion
through Jackson. The plan assigns the bounded proof separately from implementation.

Known nested Builder scopes take mapping payloads and enter the existing scope directly through framework construction
callbacks. Existing direct children and keyed Maps are the reference cases. No synthetic Groovy closure is created from
input. A closure delegate annotation alone does not prove an arbitrary method exposes a direct entry seam. Dynamic or
untyped delegation is excluded; a missing direct seam is a feasibility stop, not permission to execute user closures
during inspection or introduce a generic scope framework.

A zero-argument operation uses `operation: {}` in YAML: one external name with an empty mapping. A blank YAML value or
explicit null is not a call marker. Nonempty mappings, sequences and scalars reject for a zero-argument command. This
adds no implicit reset/clear semantics; an explicitly admitted domain command retains only its existing Builder behavior.

### Ordered execution and failure

Preserve each supplied Map's iteration order and each supplied List's element order recursively. No sorting, scheduling,
dependency resolution, repeated-call carrier or parser-order guarantee is added. Deterministic schema artifact ordering
and a Builder's normal sorted-container storage do not change interpreter traversal order.

There is **no whole-document payload preflight and no rollback**. Resolve the next admitted operation and validate its
own immediate payload before invoking it; descend through child scopes and population operations as reached. A typed
List/Map that is one authored method argument can be checked as that single argument before the one invocation. An
element-population route checks and invokes elements in supplied order. Stop on the first encountered failure. Later
names, child bodies and values are not checked ahead to promise an unchanged caller Builder.

Structural entry checks (source, root Map, active same-thread unsealed target, current contract completeness) still
precede use. In a self-contained keyed root or newly entered direct child, read only the required construction identity
before allocation; this prerequisite is not whole-body preflight and does not reorder its remaining operations. Preserve
all #954 key positions: actual key for self-contained roots/caller-key children; external key for explicit-root, active
Builder, fixed/derived/Cluster and keyed-Map positions. Never mutate or rebind supplied identity.

Contract integrity checking remains non-executing and fail closed. Complete static contract-graph verification may reject
malformed metadata before construction, including omitted referenced contracts; that is distinct from traversing supplied
payloads. Preserve that integrity fence while removing the prepared whole-input execution tree. Generation still checks
the entire emitted contract before replacing files. Neither integrity rule provides input transactionality.

On failure `readRoot` returns no Model, but earlier initializers, callbacks, operations or external effects may have run.
`applyToBuilder` may leave the caller's Builder partially configured. Do not auto-complete or roll it back; existing
session/exception cleanup remains authoritative. Preserve source identity, escaped input pointer, external operation,
declaring contract and original cause. Lifecycle failures retain normal lifecycle attribution, not an invented last leaf.
Keep cycle rejection and support for repeated acyclic data; no whole-document immutable snapshot is promised.

### Construction modes and schema fidelity

Structured `readTemplate()` is needed in 4.2. Use the existing Template definition, materialization and recipe-copy
semantics: the explicit result is a marked Template, definition runs no lifecycle phases/callbacks, and later application
uses fresh recipient Builders and their normal lifecycle. Admitted configuration/converter bodies can execute while
defining values; no-user-code inspection is not a prohibition on actual configuration. Preserve existing Template key,
ownership and recipe behavior rather than inferring Template mode from ambient state or routing through `readRoot`.
The smallest proposed facade is `readTemplate(Class<T>, Object parsed, String sourceName)`; existing facade methods stay.
Its root is a keyless Template body, with recipient identity supplied on application; ordinary nested Template key rules
remain, including keyed-Map identity. It does not require a self-contained root key that would later be discarded.
No structured `readBuilder`, Factory convenience or change to `Create.FromMap` is needed for this slice.

A separately selected keyless Builder-body schema variant is useful for caller-pre-keyed roots. It forbids the root key
while retaining the normal positional child rules. Default generated root schemas stay self-contained. Select the variant
explicitly with a Model root and purpose; private `::keyless` definition names never become a public API. Implementation
must choose a small additive Gradle configuration/output spelling and qualify coexistence and cache invalidation.
Neither core nor the generator infers identity from filenames/folders or chooses which file an editor should associate.

JSON Schema describes the structural grammar of the admitted operations where representable: String for scalar routes,
closed mappings for known scopes, and an empty object for zero-argument calls. Runtime/declaration facts may supply
truthful descriptions. No defaults, lifecycle, constructors, converters, validators or other user code run during
inspection/generation; structural validity never guarantees materialization or runtime success. New constraint
projection is later work. Retain the already qualified #959 mappings and #981 C1 parameter facts without transferring
them to a different selected operation or making parameter enforcement (#971) a prerequisite for admission.

`Struct.Examples` remains documentary strings, not parsed/typed examples. Unsupported structure must not be published as
an unrestricted object: omit compiler-excluded operations with diagnostics; fail generation for admitted shapes without
a truthful representation. Preserve all-or-nothing output replacement and warn consumers through documentation that
retained previous output is not evidence of fresh successful generation.

### Positive boundary and deferrals

The [support matrix](../implementation/input-round-plan-4-2.md#positive-support-matrix) distinguishes current support,
accepted implementation, conditional proof and deferred work. Unknown operation names fail closed. Dynamic member keys
belong only inside declared Maps, not in domain operation namespaces. Enums retain exact declared spelling.

Later contained NamedParams/NamedParam projection may describe one Map operation using only declared keys and proven
required/type facts. Arbitrary Map payloads retain their own typed data semantics; they are not inferred named arguments.
No multi-argument spreading or invocation redesign follows.

Explicit null, raw Object containers, generic File/URL loading, generic LINK/reference registries, repeated-call grammar,
non-String Map keys and broad polymorphism remain excluded/deferred. Owned DSL List/Set and custom key mapping remain
conditional/deferred, with a quick-win revisit after higher-tier implementation and Schelm qualification. A bounded 4.2
CollectionFactory probe may inspect existing generated contracts, literal alternatives and keys; it may not design
generic subtype selection or composition. Composition remains a separate 4.3-oriented track.

Managed Jackson continues to own caller-mapper property binding, aliases, codecs, mixins, null/merge policy and its
explicit import modes. Structured input owns compiler-admitted operations and their grammar. Generated schemas describe
structured input, not arbitrary Jackson inputs or completed-object output. Shared construction mechanics do not imply
the same language, automatic JSON Schema validation, a round trip, or Jackson deprecation.

## Consequences and rejected alternatives

- The execution-time failure contract changes and needs migration documentation and explicit partial-state tests.
  Keeping preflight as an unadvertised compatibility mode would contradict the accepted semantics and duplicate engines.
- Scalar admission becomes independent from optional constraint extraction. More input routes can be useful while
  preserving strict payloads. General coercion, reflection-based converter search and retrying overloads are rejected.
- Generated metadata remains ABI: preserve callable signatures, require complete selection facts, retain binary
  inheritance and exact dispatch checks, and qualify matched compiler/runtime/plugin/domain/Schema artifacts on
  Groovy 3/4/5 and applicable JPMS lanes. No old carrier decoder or mixed-toolchain promise is introduced.
- Preserve completed-Model and Template serialization, including existing recipe state and identity semantics. No
  Builder, input execution plan or importer state becomes serialized; no new persistence or import/export round trip
  follows. Retain applicable serialization regression coverage when adding structured Template construction.
- The reader, resolver, immutable compiled schema and closed projection catalog keep their existing responsibilities.
  No public descriptor API, registry, plugin SPI, parser dependency or new module is justified by this plan.
- Template construction and conditional DSL conversion reuse existing ownership/lifecycle machinery. Synthetic closures,
  completed-Model attachment and transactional rollback are rejected as different semantics.
- `Struct.*` and the direct internal rename are accepted. Update compiler/runtime/generated type references and internal
  tests/Schelm qualification consistently. No old-name compatibility or migration mechanism is required. Conditional
  feasibility results remain engineering evidence gates, not permission to silently broaden scope when a proof fails.
