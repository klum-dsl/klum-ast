# ADR 0009 implementation plan: Jackson interoperability

This plan implements [ADR 0009](../adr/0009-jackson-interoperability.md) for canonical parent
[#428](https://github.com/klum-dsl/klum-ast/issues/428). It retains the compatible JSON-1/#439 and JSON-2/#440 groundwork
while replacing ADR 0007's cancelled JSON-3 round-trip closure.

## Current behavior and failure paths

`KlumAstModule` currently replaces the root deserializer for DSL types. `KlumDeserializer` buffers an object, resolves
effective Jackson properties, preallocates owned Builders, runs `PostCreate`, binds present values, runs `PostApply`, then
starts one root lifecycle. Raw `ObjectMapper.readValue(DslType)` is the only public entry point.

That implementation already proves renamed/aliased properties, naming strategies, mixins, views, unknown-property policy,
formats, Simple Value codecs, polymorphic owned types, same-session identities, Template rejection, and explicit
type-level deserializer opt-out. It also rejects inline `LINK` input and output lacking identity/custom property handling.

The public seam is nevertheless incomplete for the confirmed use case:

- a Schema Developer cannot explicitly import a child Builder or apply structured input to an existing Builder;
- raw DSL reads inside an active lifecycle can attempt a nested root instead of naming the correct import mode;
- import I/O and mapping errors remain Jackson-first and do not contribute a source breadcrumb;
- the wiki and release artifacts describe persistence and round trips rather than external interoperability;
- current round-trip-oriented tests do not prove the primary read/enrich/write workflow;
- `@JsonCreator` and foreign Builder declarations are currently prevented from taking over construction, but the 4.0
  contract requires an actionable failure rather than silent non-use;
- the module reports an unknown Jackson module version;
- no Klum wire metadata is emitted, but that absence is not yet documented as the deliberate compatibility contract.

## Affected seams

- `klum-ast-jackson`: public `KlumJacksonImporter`, import request/context, `KlumDeserializer`, construction-override
  diagnostics, module version, parser/tree/Map handling, and Jackson path capture.
- `klum-ast-runtime`: only narrow public Builder/session and breadcrumb hooks justified by the importer; no Jackson
  dependency enters runtime.
- generated API: no generated import creator in 4.0. A later annotation-driven creator must delegate to the importer.
- tests: existing `ConfigurationReplaySpec`, `ConstructionOverrideSpec`, `JacksonCustomizationSpec`, `LinkIdentitySpec`,
  and `JsonExportSpec`, plus focused importer and YAML interoperability specifications.
- user guidance: `wiki/Jackson-Integration.md`, role vocabulary, Builder-first migration, migration navigation, and
  `CHANGES.md`.

## Compatibility and migration constraints

- Preserve JSON-1 property/value customization and one-lifecycle Builder semantics where they agree with ADR 0009.
- Preserve JSON-2 same-session identity and import-side inline-`LINK` rejection. Update its rationale: explicit output
  projection may be reference, omission, scalar, custom structure, or deliberate inline serialization.
- Keep explicit type-level serializers/deserializers as complete opt-outs.
- Owner/Role output customization, rich external `LINK` resolution, generated import creators, YAML multi-document
  convenience, and generic schema-version migrations are not 4.0 requirements.
- Do not remove or deprecate existing `Create.FromMap` in this work. Document that it is value-copy convenience rather than
  the canonical foreign-format mapping API.
- The importer public signatures become a 4.x compatibility surface. The exact mapper/reader binding and source overload
  matrix require explicit maintainer review within JSON-3 before production implementation begins.

## Tracer-bullet slices

### [JSON-3 — Explicit importer modes and diagnostics](https://github.com/klum-dsl/klum-ast/issues/463)

Introduce the central, data-format-neutral importer around a caller-owned configured mapper/reader. Finalize and test its
public signatures before implementing the four distinct semantic modes:

1. root import returning a completed DSL Object after one lifecycle;
2. value-only Template import without lifecycle processing;
3. Builder-producing import requiring the current Construction session;
4. import applied to an existing unsealed Builder.

Support parser, tree, and in-memory Map input plus ordered repeated application. Do not infer the operation from ambient
state. Reject applying to completed models and raw DSL root reads inside an active session with guidance to the appropriate
mode; leave raw non-DSL reads untouched.

The importer establishes a source breadcrumb and translates syntax, mapping, and I/O failures into `KlumModelException`
while preserving causes. Append usable Jackson property/index path segments to the breadcrumb. Preserve lifecycle
exceptions without double wrapping.

Construction-taking annotations fail with focused guidance. Managed property/value annotations remain supported, while an
explicit type-level deserializer remains the full opt-out. Template import, null Collection assignment, root wrapping,
unknown-property policy, views, mixins, polymorphism, and mapper merge configuration retain their agreed Jackson behavior.

#### Acceptance coverage

- Root import binds a foreign name such as `public`, runs `PostCreate`/bind/`PostApply` and the graph lifecycle once, and
  returns the enriched completed model.
- Builder import joins an existing session; applying two explicit inputs is ordered and missing/present/null/merge behavior
  is deterministic.
- Template import returns a marked value-only Template and runs no lifecycle until ordinary Template application.
- Parser, `JsonNode`, and Map inputs use the caller's mapper configuration, including root wrapping and naming/mixin/view
  settings.
- Raw DSL `readValue` in an active session, completed-model application, `@JsonCreator`, and foreign Jackson Builder
  construction fail with actionable messages. Raw DTO/Map/tree reads in that session continue to work.
- Nested mapping failure is a `KlumModelException` whose cause is the Jackson exception and whose breadcrumb identifies
  import source, owned-object location, and available Jackson path.
- Type-level deserializer opt-out and property-level completed-owned-object rejection remain explicit and tested.
- No payload gains Klum metadata; `KlumAstModule.version()` is non-wire module information only.

#### Reasoned commit boundaries

1. Pin the reviewed public importer API with focused compile/runtime tests and no hidden context routing.
2. Implement root, Template, Builder-producing, and apply-to-Builder modes on the existing resolved-property engine.
3. Add breadcrumb/error translation and construction-override diagnostics without changing non-DSL Jackson behavior.
4. Add parser/tree/Map, repeated-input, root-wrapper, null/merge, and Groovy 3/4/5 compatibility coverage.

### [JSON-4 — Asymmetric interoperability compatibility closure](https://github.com/klum-dsl/klum-ast/issues/464)

Replace persistence/round-trip guidance with role-oriented interoperability documentation and executable examples. Add a
documentation-referenceable YAML scenario that imports at least two explicit foreign inputs, enriches the Builder graph
through lifecycle/linking/validation, and writes intentionally different YAML through ordinary Jackson serialization.

Import fixtures cover foreign naming, aliases, owned composition, polymorphism, unknown fields, null/merge policy, LINK
failure/custom resolution, Templates, and breadcrumb-rich failures. Export fixtures independently cover ordinary POJO
serialization, lifecycle-derived output, custom type serializers, explicit LINK projections, Template rejection, and the
absence of Klum metadata. No test asserts that exported YAML must be accepted as input.

Organize guidance by Domain API Developer, Schema Developer, Client Developer, and Model Writer. Document converters and
`FieldType.BUILDER` staging before the discouraged type-level custom deserializer escape hatch. Explain that external
version fields and compatibility adapters are Schema-owned, and that `Create.FromMap` is not a Jackson mapping substitute.

#### Acceptance coverage

- The documentary YAML test is executable, referenced from the wiki, and visibly demonstrates input/output asymmetry.
- Import and export fixtures are separate; no Klum envelope, reserved property, producer field, or round-trip promise
  appears.
- LINK input and output choices, Template import/export asymmetry, custom serializer/deserializer opt-outs, ordinary
  configurable fields, and public `KlumObjectSupport` use are documented consistently with tests.
- `wiki/Jackson-Integration.md`, `wiki/Migration.md`, `wiki/Builder-First-Migration.md`, `wiki/Terms.md`, `CHANGES.md`, ADR
  links, and issue relationships agree.
- The Jackson module passes Groovy 3 `test`, `groovy4Tests`, `groovy5Tests`, and the appropriate aggregate build.

#### Reasoned commit boundaries

1. Add the asymmetric JSON/YAML fixtures and documentary read/enrich/write scenario.
2. Rewrite role-oriented Jackson and migration guidance against the executable examples.
3. Reconcile release notes, ADR/issue links, and all Groovy compatibility evidence.

## Acceptance map

| ADR contract | Slice |
|---|---|
| explicit root, Template, in-session Builder, and apply modes | JSON-3 |
| caller-owned Jackson configuration and parser/tree/Map input | JSON-3 |
| source breadcrumbs, Jackson path, and `KlumModelException` boundary | JSON-3 |
| construction takeover rejection and type-level opt-out | JSON-3 |
| ordinary POJO export with no Klum wire metadata | JSON-4 |
| independent import/export fixtures and documentary YAML workflow | JSON-4 |
| role-oriented wiki, migration, CHANGES, and Groovy 3/4/5 closure | JSON-4 |

## Risks and open questions

- The exact public importer method names, mapper-versus-reader binding, source overload matrix, and public Builder return
  type remain the only pre-implementation API decision. JSON-3 must present the proposed signatures for maintainer review
  before production code lands.
- Jackson path-to-breadcrumb translation must avoid duplicating owner/field segments while retaining array indices and
  external property names.
- Reusing the existing deserializer through per-reader attributes is preferable to an ambient ThreadLocal public protocol;
  implementation must prove that nested ordinary Jackson reads cannot inherit an import request accidentally.
- YAML test dependencies remain test-only unless a separately approved convenience module is introduced.
- A deliberate inline custom `LINK` serializer can recurse or create cycles. That behavior belongs to the Schema Developer;
  KlumAST should document the risk without imposing a universal projection.

## Issue-to-slice mapping

- #251 and #439: implemented/closed resolved-property groundwork retained by JSON-3.
- #440: implemented identity/customization groundwork retained with corrected export rationale.
- #447: completed no-metadata decision recorded by ADR 0009; no production metadata slice exists.
- #428: remains the canonical 4.0 parent until #463 and #464 complete.
- #430: completed existing `FromMap` convenience remains separate and is not extended into the canonical import seam.
