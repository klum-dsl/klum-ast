# #1113 — executable Jackson tree and reconstruction qualification

Date: 2026-11-26. Related: [#1113](https://github.com/klum-dsl/klum-ast/issues/1113).
Authority: the maintainer's bounded realization assignment under merged
[ADR 0034](../../adr/0034-bounded-structured-authoring.md) /
[PR #1116](https://github.com/klum-dsl/klum-ast/pull/1116).
Base: `98ce6137036cdd0189cebc22c4b506bdc2c85d76`, which includes ADR 0034 merge
`1b195019bc2696c06befcb5dde2edeb4b141417d` and the separate presence-design merge. No presence implementation is assumed.

## Outcome

Existing public Jackson `convertValue` and `valueToTree` can produce ordinary ordered typed values without JSON text
for the selected fixtures. **Two explicitly named fixture/projection subsets reconstruct effective values through the
existing `FromStruct` Factory.** This establishes bounded executable evidence, not a generic Model dump/input inverse,
a new serializer/API or production qualification of every ScHelm Package descriptor.

All execution is in
[`StructuredModelTreeQualificationTest`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredModelTreeQualificationTest.groovy),
class-level `@Issue('1113')`. Production source, dependency declarations, managed importer behavior and ADRs are unchanged.
The earlier report-only triage remains on its retained local branch `codex/1113-structured-output-triage` at `f3c73b0ef`;
this executable assignment starts independently from the accepted strategy baseline.

## Public seams and red/green evidence

- All Model construction/observation uses generated Factory/Builder capabilities and completed getters. Binary fixture
  classes are loaded from their ordinary compiled output so Struct reads packaged declarations. No internal descriptor,
  companion or construction engine is called by the qualifier.
- `direct` calls `mapper.convertValue(model, Map)`; `via-node` calls
  `mapper.convertValue(mapper.valueToTree(model), Map)`. A test-only `JsonFactory` rejects both Writer and byte-stream
  generator creation. Its explicit `writeValueAsString` and `writeValueAsBytes` controls throw. Passing projection cases
  therefore cannot be explained by a hidden JSON text encode/decode fallback through that factory.
- The first numeric observation passed: the selected large BigInteger/BigDecimal values survived both in-memory routes
  with the default Jackson number policy. No precision-loss claim was inferred from textual JSON defaults.
- The first descriptor reconstruction was **red** without its caller enum serializer: Struct rejected `/kind` because
  Jackson emitted `APPLICATION`, while the Schema accepts `application`. Registering a test-local `SimpleModule` serializer
  on that caller mapper made the case **green**. No Klum behavior changed. The final parameterized positive case remains
  sensitive to removal of that mapping.
- Further negative controls remain green tests: they assert rejection, changed effective results or lost precision
  where appropriate. Incidental test-authoring errors (a shadowed helper name, unsupported Map subtraction and an alias
  assumption before completion) were corrected without changing production behavior.

The tests create/configure each mapper before use. A naming-policy comparison uses separate mapper instances and proves
that the native mapper is unchanged. There is no global module registration or modification of a caller's existing mapper.

## Named reconstructible subsets

### Descriptor-1113

**Exact fixture:** `descriptorqualification.ReleaseDescriptor` and concrete unkeyed `TypeDeclaration`.
Fields are `id`, `version`, `kind`, `jars`, `root`, `note`; the owned declaration contains `type`, `jar`, and recomputed Owner.

- Inputs use literal external `package-id` via the Schema's compatible `JsonProperty` spelling.
- The caller chooses `NON_NULL`, explicit descriptor/child property order through mixins, and a serializer for this
  closed `PackageKind` enum: `APPLICATION`/`OPERATOR` map to `application`/`operator`.
- The fixture covers both enum values, both no-text routes, absent/null-valued and non-null optional notes, a concrete
  owned declaration and ordered String Lists. The declaration order is checked separately from Map equality.
- No seeded collections, state-transforming lifecycle, LINK, abstract target, converter, arbitrary Object payload,
  Template or presence-dependent field belongs to this named subset. The fixture has no external construction context.
- Comparison includes every selected effective field, enum meaning, exact Strings, optional-note value, List order,
  nested ordered mapping entries and fresh child ownership. Reconstructed identity differs from the original.
  `P(FromStruct(P(m))) == P(m)` is checked and a second reconstruction is projected and compared again.

This is representative of the descriptor-shaped use case; it does not import ScHelm code or qualify its real Schema,
archive policy, schema resource, Java/Groovy/mapper versions, or YAML output. Consumer acceptance remains consumer-owned.

### Native-values-1113

**Exact fixture:** `scalarqualification.Values`: boolean, int, String, BigInteger, BigDecimal, List of BigDecimal and
literal String-keyed Map of BigInteger. No defaults, mutation callbacks or custom codecs; no external context.

Both routes preserve selected false/zero, exact large integer/decimal values, a tiny decimal, a two-element decimal List,
ordered data keys and Unicode including NEL and a supplementary character, and re-enter through `FromStruct`.
Exact decoded String/numeric values and List/Map iteration order are comparison criteria; original numeric spelling,
BigDecimal scale/trailing-zero preservation, original authoring omission and source text are not.
This is a finite fixture corpus, not exhaustive machine-range or arbitrary Number/custom-codec qualification.

## Capability and limit matrix

Each row names the executable feature method by its leading phrase; the test source is the owning evidence.

| Concern | Observed result | Qualification consequence |
| --- | --- | --- |
| Ordinary no-text paths | `a named descriptor subset...`, `in-memory routes...` and `the qualifier rejects accidental text encoding...` exercise direct and tree-based conversion with format output disabled. | Existing public APIs suffice for these fixtures. No new public tree facade is justified. |
| Names | `Struct naming is independent...`: Struct-only `service-name` does not rename Jackson `serviceName`; reconstruction rejects the native name. A caller mixin aligns this one field without altering the native mapper. | Struct annotations are not output directives. |
| Null/inclusion | `caller inclusion preserves supplied null...`: default output contains `host: null` for both omission and explicit-null construction; Struct rejects it. A fresh NON_NULL mapper omits it and reconstruction yields the same effective null. | Present-null versus absent output remains real data, but original configured presence is not recovered. Omitting null cannot generically clear defaults. |
| Property/Map/List order | `fixed caller ordering...`: explicit root order, insertion order of nested LinkedHashMaps and List order are asserted separately; a second mapper's map-key sorting changes only chosen Map order. | Fixed fixture/configuration ordering is established; no global Klum ordering guarantee or portable unordered-container order. |
| Construction keys/ownership | `root and direct keys survive...`: raw output includes keyed Map child's `name` and Struct rejects `/services/api/name`. Deliberate caller removal on that one relationship restores re-entry; direct-child/root keys remain. Explicit-key root separately forbids an inner root key. | Positional adaptation is an additional caller decision, not generic serializer repair. This keyed case is outside Descriptor-1113. |
| Defaults/population | `effective defaults and seeded collection population...`: omission produces default host `local` and `['base']`; re-entering full output produces `['base','base']`. Empty population retains the seed. | Effective output can be valid operation input while still not reproducing effective state. |
| Derived/lifecycle | `derived output is effective state...`: protected summary is exported but cannot be rebound through Struct; removing it still transforms `catalog!` to `catalog!!`. | Dropping computed fields alone does not prove inversion; normal lifecycle runs again. |
| Converters | `a converter value projection...`: String input normalizes to a Slug; ordinary output is a nested object, which the String operation rejects. | No automatic inverse converter. |
| Commands | `effective scalar equality does not replay...`: final name reconstructs, but an authored `activate: {}` action is absent and is not rerun. | Equal effective values do not reconstruct authored control flow/actions. |
| LINK | `LINK output requires a caller choice...`: unconfigured non-null LINK output fails. Caller property codec emits an external id, which Struct rejects rather than resolving a reference. | Explicit output choice and aggregation identity remain outside generic reconstruction. |
| Polymorphism | `Jackson polymorphic output remains distinct...`: output has `kind: dog`, which an abstract owned Struct field cannot consume. Existing managed Jackson tree import still creates the right concrete subtype. | Jackson subtype IDs do not extend Struct grammar; managed interoperability is preserved. |
| Template | `in-memory output retains...`: both routes retain ordinary marked-Template rejection. | Value projection does not serialize executable recipes. |
| Exact native numbers | `in-memory routes preserve...` and `a native scalar and container subset...` preserve selected BigInteger/BigDecimal values through both routes. | No text-path rounding for the named fixtures; compare actual numeric values, not only parse success. |
| Preexisting Float/Double | `preexisting finite binary floats...`: selected Float 0.1 has numeric double value 0.10000000149011612; Double 0.1 remains its selected binary value. | No decimal-intent or source-spelling recovery; no assertion of wrapper identity across routes. |
| Nonfinite | `successful in-memory conversion does not reject...`: NaN and both infinities survive projection as Double values; Struct rejects them. | Conversion alone is not ADR 0034's finite-tree admission gate. No new validator is silently inserted. |
| Codec precision | `caller codecs can silently lose precision...`: a deliberately rounding BigDecimal codec converts successfully to Double while changing the mathematical value. | Each actual codec/Number path needs qualification; caller policy is preserved, not overridden. |
| Structural reuse | `repeated acyclic values...`: a completed getter returns the same container as another property; output repeats equal structural values with no shared identity. Nested null/false/zero/empty String survive. | Shared identity is not promised. Arbitrary cycles are outside this corpus and must not be assumed accepted or safely rejected by these APIs. |

## Boundaries and next decision

[ADR 0009](../../adr/0009-jackson-interoperability.md) remains authoritative for managed Jackson.
[ADR 0032](../../adr/0032-builder-oriented-structured-input.md) remains authoritative for Struct's ordered operation
execution, keys, population, lifecycle and null rejection. JSON Schema is never invoked as an automatic runtime gate;
generated input schemas are not output/inverse schemas. No explicit schema-validation acceptance of this new fixture
is claimed. No YAML text, parser/emitter, dependency, module, public API, production serializer, source migration or
presence behavior was added. No issue state/milestone or curation release/dependency/owner fact changes.

A meaningful bounded subset exists, so a parallel serializer or public tree API is not the next necessary decision.
The next consumer step is **qualification of its actual mapper, codecs, Model subset and comparison values**. If a
shared strict-tree adapter is later proposed, its separate decision must address unsupported/nonfinite numbers,
acyclicity, budgets and custom-codec output; success of the existing conversion API cannot serve as its admission proof.
#1114 owns YAML/tree policy qualification independently. Do not make the current ScHelm delivery depend on this fixture.

## Validation and delivery record

The focused Groovy 3 qualifier passed before each test commit. On test revision `c4ac3c05e`:

- `./gradlew --offline :klum-ast-jackson:check` passed with resolved Jackson **2.14.2** (verified by dependencyInsight).
  Groovy 3: **162** cases; Groovy 4: **160**; Groovy 5: **160**; **482 total**, zero failures/errors/skips.
  The qualifier contributes **41 cases per lane / 123 total**. License tasks and test-lane isolation passed.
- `./gradlew --offline -PjacksonVersion=2.22.2 :klum-ast-jackson:test --tests '*StructuredModelTreeQualificationTest'
  :klum-ast-jackson:groovy4Tests --tests '*StructuredModelTreeQualificationTest'
  :klum-ast-jackson:groovy5Tests --tests '*StructuredModelTreeQualificationTest'` passed: **41 per lane / 123 total**,
  zero failures/errors/skips. This is focused second-endpoint evidence, not the full module suite at 2.22.2.
  The existing version override and cached dependencies were used; declarations were not changed.
- All three changed Markdown documents rendered with cached CommonMark **0.28.0** plus GFM tables. Their **43** relative
  links/fragments, fences and `git diff --check` passed. No external link crawl or user-site rendering is claimed.
- Local review and final delivery state are reconciled in the task/PR handoff. No full root
`check`, real ScHelm execution, Jackson 3, source reconstruction, YAML fidelity, arbitrary graph or historical ABI/stream
qualification is claimed by this test-only change. Hosted CI and Sonar are separate exact-revision evidence.

Tracker relationship: **Related: #1113**. The issue remains open; this bounded qualification is not its closure or a
milestone assignment. Hive reconciliation is requested for this slice and any subsequent consumer qualification.
