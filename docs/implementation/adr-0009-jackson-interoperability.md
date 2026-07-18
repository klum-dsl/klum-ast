# ADR 0009 implementation plan: Jackson interoperability

This plan implements [ADR 0009](../adr/0009-jackson-interoperability.md) for canonical parent
[#428](https://github.com/klum-dsl/klum-ast/issues/428). It retains the compatible JSON-1/#439 and JSON-2/#440 groundwork
while replacing ADR 0007's cancelled JSON-3 round-trip closure.

The JSON-3 public interface and behavior were confirmed on 2026-07-17. Production implementation remains pending.

## Current behavior and failure paths

`KlumAstModule` currently replaces the root deserializer for DSL types. `KlumDeserializer` buffers an object, resolves
effective Jackson properties, preallocates owned Builders, runs `PostCreate`, binds present values, runs `PostApply`, then
starts one root lifecycle. Raw `ObjectMapper.readValue(DslType)` is the only public entry point.

That implementation already proves renamed/aliased properties, naming strategies, mixins, views, unknown-property policy,
formats, Simple Value codecs, polymorphic owned types, same-session identities, Template rejection, and explicit
type-level deserializer opt-out. It also rejects inline `LINK` input and output lacking identity/custom property handling.

The public seam is nevertheless incomplete for the confirmed use case:

- a Schema Developer cannot explicitly read a root or Template through a Klum exception/diagnostic seam;
- a Schema Developer cannot import a child Builder or apply structured input to an existing Builder;
- raw DSL reads inside an active lifecycle can attempt a nested root instead of naming the correct import mode;
- import I/O and mapping errors remain Jackson-first and do not contribute import-source or construction-path context;
- construction-taking annotations do not yet fail with the complete actionable managed-import contract;
- the wiki and release artifacts still describe persistence and round trips rather than external interoperability;
- the module reports an unknown Jackson module version; and
- no Klum wire metadata is emitted, but that absence is not yet documented as the deliberate compatibility contract.

## Affected seams

- `klum-ast-jackson`: public `KlumJacksonImporter` and `KlumJacksonInput`, caller-owned reader capture, four managed modes,
  `KlumDeserializer`, construction-override diagnostics, module version, parser/tree/Map adaptation, and Jackson path
  capture.
- `klum-ast-runtime`: only the narrow Builder/factory/session and diagnostic hooks justified by the importer; no Jackson
  dependency enters runtime and `ConstructionSession` remains opaque.
- generated API: `KlumFactory.BuilderFactory<T, B>` and the ADR 0005 `Foo_DSL.Builder` projection provide the precise
  Java return type. No generated import creator is added in 4.0.
- tests: existing `ConfigurationReplaySpec`, `ConstructionOverrideSpec`, `JacksonCustomizationSpec`, `LinkIdentitySpec`,
  and `JsonExportSpec`, plus focused importer, Java-consumer, static-Groovy, endpoint, and TestKit specifications.
- user guidance: `wiki/Jackson-Integration.md`, migration navigation, and `CHANGES.md` as split between JSON-3 and JSON-4.

## Confirmed JSON-3 public interface

The Jackson 2 adapter adds these two final types in `com.blackbuild.klum.ast.jackson`:

```java
public final class KlumJacksonImporter {
    public static KlumJacksonImporter using(ObjectMapper mapper);
    public static KlumJacksonImporter using(ObjectReader reader);

    public <T> T readRoot(Class<T> type, KlumJacksonInput input);
    public <T> T readTemplate(Class<T> type, KlumJacksonInput input);
    public <T, B extends KlumBuilder<T>> B readBuilder(
            KlumFactory.BuilderFactory<T, B> factory,
            KlumJacksonInput input);
    public <B extends KlumBuilder<?>> B applyToBuilder(
            B builder,
            KlumJacksonInput input);
}

public final class KlumJacksonInput {
    public static KlumJacksonInput parser(JsonParser parser);
    public static KlumJacksonInput tree(JsonNode node);
    public static KlumJacksonInput map(Map<?, ?> values);
    public KlumJacksonInput named(String source);
}
```

There are no public constructors, checked exceptions, mode enum, request type, target-bound importer, or convenience
overloads for strings, readers, streams, files, URLs, arbitrary objects, or multiple inputs.

The public call shapes are Java-first. Root and Template calls infer completed `T` directly. In an active session,
`readBuilder(Child.Create.getAsBuilder(), input)` infers the generated `Child_DSL.Builder`; Groovy may use the equivalent
`Child.Create.AsBuilder`. `applyToBuilder` returns the same Builder identity with its precise type. Issue #467 owns final
framework factory/accessor naming without changing these importer descriptors.

### Generated Builder capability prerequisite

The descriptors above remain fixed. Their `B extends KlumBuilder<T>` bound requires #394 DSL-2 to make
`KlumBuilder<T>` a zero-operation public interface and to make each generated `Foo_DSL.Builder` extend that interface;
runtime implementation moves behind an internal base. This preserves precise Java and static-Groovy Builder inference
without exposing hidden implementations or widening the importer to erased, reflective, or `Object`-typed alternatives.

#463 is natively blocked by #394 until DSL-2 is delivered. JSON-3 must then compile the exact Java 17 and static-Groovy
consumer shapes against Jackson 2.14.2 and 2.21.x. The 4.0 package migration already requires schema recompilation, so
no pre-4.0 concrete `KlumBuilder` or RW-marker binary contract is retained; the delivered importer and generated Builder
descriptors are 4.x source and binary commitments.

## Confirmed behavior and compatibility

### Importer and input ownership

- `using(mapper)` captures `mapper.reader()` exactly once. It does not retain a mutable configuration relationship, mutate
  the mapper, or register `KlumAstModule`.
- `using(reader)` accepts only an untyped reader with no value-to-update target. It preserves views, attributes,
  injectables, features, root wrapping, and other reader configuration; typed or updating readers fail immediately with
  `IllegalArgumentException`.
- The importer and inputs are immutable. Independent root/Template imports are reusable and thread-safe subject to the
  captured reader/input safety. Builder modes remain bound to the current thread/session.
- `parser` borrows a single-pass parser and never closes it. `tree` and `map` do not mutate their source. `named` adds an
  opaque diagnostic identity; unnamed inputs have stable `parser`, `tree`, and `map` identities.
- Null public arguments fail with `NullPointerException` naming the argument.

### Four modes and lifecycle ownership

- `readRoot`: for a managed DSL type, allocate the Builder graph, run initializers and `PostCreate`, bind exactly one input,
  run `PostApply`, graph phases, Materialization, validation, and verification once, and return completed `T`.
- `readTemplate`: bind values and owned Template composition, mark the resulting `T` as a Template, and run no lifecycle
  until ordinary Template application.
- `readBuilder`: use the supplied Builder-producing factory in the active Construction session, bind exactly one input,
  and return the precise unsealed generated Builder without starting nested materialization or validation.
- `applyToBuilder`: require an unsealed Builder in the active session, bind exactly one input, and return that identical
  Builder. Applying to a completed model, sealed Builder, wrong/completed session, or no session fails actionably.
- Each call owns one input. Repeated `applyToBuilder` calls remain separate operations and promise no layering semantics.
  Ordered heterogeneous composition and overwrite/null/list/map policy belong to source-neutral issue #304.

### Raw Jackson and customization

- Standalone raw `ObjectMapper.readValue(DslType)` remains a supported but discouraged 4.x root compatibility path.
- A managed raw DSL root read or managed `readRoot` inside an active Construction session fails before allocation and points
  to Builder modes. Raw DTO, Map, tree, and other non-DSL reads remain ordinary Jackson operations.
- Managed import preserves names, aliases, ignore/access rules, naming strategies, mixins, views, unknown-property policy,
  formats, Simple Value codecs, null/content policy, merge configuration, polymorphism, and Collection/Map element
  conversion.
- `@JsonCreator`, direct completed-model mutation, and foreign `@JsonDeserialize(builder = ...)` construction fail
  actionably. An explicit type-level custom deserializer is a full `readRoot` opt-out with no added Klum lifecycle;
  Template and Builder modes reject it.
- A property codec/object-id resolver may contribute an existing completed DSL Object only to `LINK`; it becomes a sealed
  Builder wrapper, retains identity, and does not join mutating lifecycle phases. Same-session Builder identity also
  remains valid. Completed values for owned composition and inline objects that silently create `LINK` ownership fail.
- Managed import adds no wire metadata. Export remains ordinary Jackson POJO serialization with the ADR 0009 Template and
  `LINK` safeguards.

### Exception and diagnostic contract

- Existing `KlumModelException` and established lifecycle `KlumException` instances pass through by identity. Internal
  Jackson adapters avoid wrapping them; the importer unwraps an unavoidable Jackson wrapper.
- Jackson syntax/mapping/I/O/custom-binding and unexpected non-Klum binding failures become exactly one
  `KlumModelException` whose direct cause is the top-level original failure. JVM `Error`s pass through.
- Session, sealed-Builder, construction-takeover, and missing managed-module configuration failures are
  `KlumModelException`. Module configuration is checked on the first managed operation rather than by mapper mutation.
- The stable path shape is `$/Order.readRoot:jackson(config.yaml)/input(#/services/2/public)`. It uses the public method
  name, DSL target type, explicit or stable source identity, and an RFC 6901 pointer with external property names and
  indices. Binding replaces overlapping generated crumbs; later lifecycle errors use the normal Klum Builder path.
- The stable message prefix is `Jackson readRoot import of com.acme.Order failed: <concise cause> at <path>`. Operation,
  target type, source/path grammar, and prefix are compatible; Jackson cause wording is not. Syntax location includes
  line/column when available, without sensitive source excerpts or duplicated Jackson reference chains.

### Jackson version boundary

- The existing artifact remains a Jackson 2 adapter with 2.14.2 as the resolved baseline and 2.21.x as the upper endpoint
  to validate for 4.0.
- The interface concepts and lifecycle behavior have been audited against Jackson 3.1. Jackson 3 package and descriptor
  changes prevent one binary from exposing mapper/reader/parser/tree types for both majors.
- A future Jackson 3 artifact must compile a separate adapter against the same data-format-neutral lifecycle engine. It
  must not weaken the public interface to reflection or `Object` types, and JSON-3 does not perform a Jackson upgrade.

## Tracer-bullet slices

### [JSON-3 — Explicit importer modes and diagnostics](https://github.com/klum-dsl/klum-ast/issues/463)

Implement the confirmed public interface around the existing resolved-property engine. Keep one input per operation and
hide Jackson/source/lifecycle coordination behind the importer. No API decision remains open; internal adapter layout,
reader attributes/context handoff, and parser/tree/Map normalization are implementation choices as long as they cannot
leak ambient import state into ordinary nested Jackson reads.

Implementation is blocked by #394 DSL-2, not by an unresolved JSON-3 product decision. Do not start with a weakened
generic bound or an implementation-specific Builder type; first deliver the generated Builder capability prerequisite.

#### Acceptance coverage

- Exact Java descriptors compile from an external Java 17 consumer and preserve generated Builder inference. Static Groovy
  call shapes compile under Groovy 3, 4, and 5.
- Mapper snapshot, reader preservation/rejection, importer reuse, module non-mutation, parser-open ownership, and tree/Map
  non-mutation have focused identity/state tests.
- Root import binds a foreign name such as `public`, runs `PostCreate`/bind/`PostApply` and the graph lifecycle once, and
  returns the enriched completed model.
- Template import returns a marked value-only Template and runs no lifecycle until ordinary Template application.
- Builder-producing import joins an active session and returns the generated Builder type. Application returns the same
  unsealed Builder identity. No mode starts nested materialization.
- Exactly one parser, `JsonNode`, or Map input is accepted per operation. Root wrapping, naming/mixin/view settings,
  unknown-property handling, present/missing/null/merge behavior, and polymorphism use captured reader configuration.
- Raw managed reads inside an active session, sealed/completed/wrong-session Builder use, `@JsonCreator`, and foreign
  Jackson Builder construction fail actionably. Raw non-DSL reads remain unchanged.
- Completed explicit `LINK` targets retain identity through sealed wrappers; completed owned values and silent inline LINK
  ownership fail. Type-level deserializer root opt-out and Builder/Template rejection are explicit.
- Nested failures prove the exact exception identity/wrapping rules, stable message prefix, source identity, external JSON
  Pointer path, syntax location, and non-duplication of Klum/Jackson path segments.
- No payload gains Klum metadata; `KlumAstModule.version()` remains non-wire module information only.

#### Compatibility matrix

- Development: focused importer tests, then the Jackson module's Groovy 3 suite against Jackson 2.14.2.
- Release: the full six cells Groovy 3/4/5 × Jackson 2.14.2/2.21.x.
- External Java 17 consumer compilation against both Jackson endpoints verifies the exact public signatures.
- Static Groovy consumer compilation runs in all six cells.
- Two Gradle TestKit consumer scenarios exercise one endpoint each, including module discovery/configuration and generated
  Builder call shape.
- Jackson 3.1 has no binary test in JSON-3; the recorded API audit is design evidence for the future adapter.

#### Documentation ownership

JSON-3 supplies complete Javadocs, executable Java-first examples for all four modes, concise Groovy examples where syntax
differs, and exact setup, configuration ownership, input ownership, error, version-support, and raw-compatibility guidance.
It adds the narrow importer API reference and initial `CHANGES.md` entry. JSON-4 owns the role-oriented interoperability
story, migration guidance, foreign YAML example, and final release reconciliation.

#### Reasoned commit boundaries

1. Pin the two public types and exact descriptors with Java/static-Groovy consumer compilation and ownership tests.
2. Implement root and value-only Template modes on the existing resolved-property engine.
3. Implement active-session Builder creation/application with generated-type and identity coverage.
4. Complete raw-read guards, construction/customization/LINK boundaries, and exception/path translation.
5. Add the two Jackson endpoint matrix, TestKit coverage, Javadocs, API examples, wiki reference, and release note.

### [JSON-4 — Asymmetric interoperability compatibility closure](https://github.com/klum-dsl/klum-ast/issues/464)

Replace persistence/round-trip guidance with role-oriented interoperability documentation and executable examples. Add a
documentation-referenceable YAML scenario that imports exactly one foreign input, enriches the Builder graph through
lifecycle/linking/validation, and writes intentionally different YAML through ordinary Jackson serialization.

Import fixtures cover foreign naming, aliases, owned composition, polymorphism, unknown fields, null/merge policy, `LINK`
failure/custom resolution, Templates, and diagnostic-path failures. Export fixtures independently cover ordinary POJO
serialization, lifecycle-derived output, custom type serializers, explicit `LINK` projections, Template rejection, and
the absence of Klum metadata. No test asserts that exported YAML must be accepted as input.

Ordered multi-source composition, YAML multi-document convenience, generated import annotations, configurable Owner/Role
export, and richer Layer 3 terminology remain deferred. Multiple sources may populate independent Builders, but JSON-4
must not introduce Jackson-owned layering or cross-input overwrite policy; issue #304 owns the source-neutral coordinator.

#### Acceptance coverage

- The documentary YAML test is executable, referenced from the wiki, and visibly demonstrates one-input/output asymmetry.
- Import and export fixtures are separate; no Klum envelope, reserved property, producer field, or round-trip promise
  appears.
- `LINK` input and output choices, Template asymmetry, custom serializer/deserializer opt-outs, ordinary configurable
  fields, and public `KlumObjectSupport` use are documented consistently with tests.
- `wiki/Jackson-Integration.md`, `wiki/Migration.md`, `wiki/Builder-First-Migration.md`, `wiki/Terms.md`, `CHANGES.md`, ADR
  links, and issue relationships agree.
- The Jackson module passes Groovy 3 `test`, `groovy4Tests`, `groovy5Tests`, and the appropriate aggregate build.

#### Reasoned commit boundaries

1. Add the asymmetric JSON/YAML fixtures and documentary one-input read/enrich/write scenario.
2. Rewrite role-oriented Jackson and migration guidance against the executable examples.
3. Reconcile release notes, ADR/issue links, and all Groovy compatibility evidence.

## Acceptance map

| ADR contract | Slice |
|---|---|
| exact importer/input public descriptors and ownership | JSON-3 |
| explicit root, Template, in-session Builder, and apply modes | JSON-3 |
| caller-owned Jackson configuration and one parser/tree/Map input | JSON-3 |
| raw compatibility, customization, construction rejection, and type-level opt-out | JSON-3 |
| import source, construction path, exception identity, and `KlumModelException` boundary | JSON-3 |
| Jackson 2 endpoint matrix and Jackson 3.1-compatible internal seam | JSON-3 |
| ordinary POJO export with no Klum wire metadata | JSON-4 |
| independent import/export fixtures and one-input documentary YAML workflow | JSON-4 |
| role-oriented wiki, migration, CHANGES, and Groovy 3/4/5 closure | JSON-4 |

## Risks and remaining implementation choices

- Internal import context must be scoped so nested ordinary Jackson reads cannot inherit a managed request accidentally.
  Reader attributes or another explicit internal handoff are preferable to an ambient public protocol.
- Jackson path conversion must avoid duplicating owner/field segments while retaining external names, arrays, map keys,
  and syntax locations.
- Parser/tree/Map adapters must share one binding engine without changing parser ownership or mutating in-memory values.
- A custom inline `LINK` serializer can recurse or create cycles. That remains Schema Developer behavior and is documented
  without imposing a universal projection.
- API naming, source abstraction, lifecycle ownership, error behavior, support range, and documentation split are decided;
  no product-level JSON-3 question remains.

## Issue-to-slice mapping

- #251 and #439: implemented/closed resolved-property groundwork retained by JSON-3.
- #440: implemented identity/customization groundwork retained with corrected export rationale.
- #447: completed no-metadata decision recorded by ADR 0009; no production metadata slice exists.
- #304: future source-neutral ordered composition; JSON-3 and JSON-4 each use one input per operation.
- #428: remains the canonical 4.0 parent until #463 and #464 complete.
- #430: completed existing `FromMap` convenience remains separate and is not extended into the canonical import seam.
- #467 and #468: consume the confirmed importer shape during naming review and the final 4.0 public-surface inventory.
