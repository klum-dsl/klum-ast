# #1028 — native derived-key Map value sequences

Base: fetched and verified `origin/master` at `91b803b4efbcdb44d4c75f64e82e177eb433abc3` (merged #1027).
Branch: `codex/1028-derived-map-sequences`.
Authority: [#1028](https://github.com/klum-dsl/klum-ast/issues/1028),
[#1015 K1 inventory](issue-1015-struct-capability-inventory.md),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#native-derived-key-map-sequence-extension--1028).

## Executable characterization first

Commit `b9a2edd2e` records 12 passing cases in each of Groovy 3/4/5 before production changes. Ordinary
`value 'BLUE'` on `@Field(keyMapping = { it.toLowerCase() }) Map<String, String> values` stores `[blue: 'BLUE']`.
Single-value, Collection and varargs population run mapping in caller order. Repeated derived keys replace values;
LinkedHashMap retains first insertion position. SortedMap/NavigableMap retain their ordinary sorted destination order
after the same traversal. Native values add no owned child or separate ownership step.

Mapping runs before insertion and native value-type checking. A mapping exception, null derived key (`Key is null`),
or null value stops ordinary population at that item. Prior insertions remain; later items are not mapped. A null
value with a constant non-null derived key still reaches ordinary value-type rejection. Catching a failure inside an
active Builder allows normal PostApply/PostTree completion. PostCreate precedes ordinary mapping. Template definition
maps values without lifecycle callbacks, retains resulting entries, and recipients copy those entries into independent
Maps without rerunning their mappings; recipient additions use ordinary mapping/lifecycle behavior.

The pre-change public adder had exact transient compiler source-field provenance, but no emitted input selection.
The old compiler tried `(String, valueType)` Map population, emitted a `POPULATION` exclusion, and rejected both
sequence and literal-Map input at `/values` without mapping. That historical rejection lives in the characterization
commit; final tests assert the admitted sequence and retained literal-Map rejection.

## Exact association and implementation

`DSLASTTransformation.createMapOfSimpleElementsMethods` already links the generated value-only adder to its exact
source field through `AbstractMethodBuilder.SOURCE_FIELD_METADATA_KEY`. `populationOperation` already selects through
that source link and exact public projection, excluding authored replacements. For native String-keyed derived Maps,
it now selects the single value parameter instead of a literal-key/value pair. Classification emits the existing
PROPERTY/LIST payload and enriches the exact declaration with existing `GeneratedInput.Selection(field, PROPERTY)`.
No closure body is inspected or evaluated for admission.

Runtime resolution validates the reflected String-keyed supported Map type and the one compiler-selected method's
exact native generic parameter/return type, rejecting synthetic/bridge methods and mixed selections. It normalizes to
the existing LIST payload. No annotation members, roles, metadata versions or callable signatures change; no Model
annotation reflection fallback or method search by guessed names is introduced. Missing selection still fails closed.
Execution uses the exact recorded signature to select and validate the effective Builder method, including compatible
descendant overrides. It invokes that method through the existing Groovy metaclass seam. A regression also preserves
ordinary runtime output-key behavior without adding output validation/coercion.
Matched Schema recompilation replaces the old exclusion with the exact selection.

The existing interpreter validates and invokes each item in supplied order through that method. Ordinary
`InternalKlumBuilder.doAddElementToMap` owns actual key derivation, native type/null-key checks and insertion. No new
output-key validation/coercion or generic key policy follows from the declared String input family. Existing lifecycle,
Template, ownership/session fences, indexed/source diagnostics and first-failure partial state remain authoritative.
The existing projector emits scalar/enum arrays from the same resolved payload; it runs no mapping, constructors,
lifecycle or validators. K2/K3/K4, non-String declared keys, CollectionFactory, polymorphism and composition stay excluded.

## Coverage and documentary linkage

- `StructuredDerivedMapContractTest` proves exact compiler provenance, persisted existing selection and native List
  payload with a throwing mapping body that compilation must never run.
- `StructuredDerivedMapCharacterizationTest` covers ordinary/Struct order, replacement, sorted destinations, strict
  native scalars/enums, invalid items and mapping failures, partial state, empty sequences, Templates, inherited/renamed
  custom member selection, separately compiled descendant overrides, rejected families, authored overrides and corrupt missing/extra/incompatible selections.
- Documentary method `derives Map keys from structured values through the ordinary Builder adder` carries
  `@Issue('1028')`, `@Tag('documentary')` and `@See` to
  [derived-key Map values](../user/Structured-Input.md#derived-key-map-values). The page links back to that method.
- `BinaryOwnedRelationshipsTest` extends its source-free, separately compiled Groovy 3/4/5 consumer/projection fixture
  with derived String values and inherited enum values, exact array schemas and ordinary/Struct event parity.
  Initialization traps include mapping-referenced user code; generation must leave them untouched.

## Initial delivery validation and local review — `211072695`

- Pre-change characterization: 12 cases per Groovy lane, no skips.
- Final `./gradlew check --continue --max-workers=4` passed: 11,121 cases, zero failures/errors, 49 existing skips;
  40m 28s. This includes complete Groovy 3/4/5 core/runtime/adapter suites, 665 plugin cases, lane isolation,
  license/static checks and aggregate coverage. Its exact production/test source is captured by `a31144448`.
- All 37 focused compiler/runtime K1 cases passed in each Groovy lane (111 total), with no skips. The seven
  source-free binary fixture cases passed, including actual interpretation and inert schema projection in all lanes.
- Initial-head baseline coverage reports show all lines in the then-present native-population and inherited-override helpers covered;
  their branch counts are 17/18 and 10/10 respectively. The compiler derivation-presence helper has 10/12 covered
  branches; source/inherited-candidate filtering has 18/18. These are local counters, not a SonarCloud gate result.
- Changed Markdown fences, relative file links, reciprocal documentary linkage and `git diff --check` passed.
  `renderLocalDocumentation -PdocumentationVersion=4.2.0-tracer --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2`
  passed from immutable source/renderer `c0b250578c3cdabceba5d6126d9fcc6f89cfac1a`: 447 HTML files rendered and crawled; 1m 6s.
- An initial full-root run was deliberately stopped after self-review identified the inherited-adder case;
  it is not counted as passing final qualification.
- Exactly two task-limited local review agents: Standards and Specification. Both returned zero unresolved findings
  on the final implementation, inherited-adder correction and documentation follow-ups.
  Specification identified that the existing exact Selection mechanism suffices, avoiding an unnecessary metadata
  blocker. Its mixed-selection observation is addressed by rejecting groups that combine value-only and other routes.
  Initial self-review added a K1-specific inherited override fence. The focused review below supersedes that decision.

## Focused admission and dispatch review — PR #1029

The follow-up review starts at `2110726959be81bc8644eac44bbc5ae15791af45` and follows the maintainer's bounded
correctness request, #1021 and #1006. The existing selected operation is sufficient admission authority. An ordinary
native value-only adder is generated only when the field has key mapping; `populationOperation` additionally requires
the exact source FieldNode and surviving public projection. Authored collisions do not acquire that association.
Invalid mapping declarations fail compilation. No valid compiler-generated counterexample was found. Deliberately
forged selection annotations are distinct corruption controls, not evidence that another carrier fact is required.

The inherited-override fence was overconstrained. `StructuredInputSupport.Invocation` already picks the effective
method by the recorded exact name/parameters, checks public visibility and return compatibility, and invokes it.
A same-signature descendant override has deterministic normal Builder dispatch. The correction removes only that
runtime rejection, its compiler-side exclusion and the internal declared-key marker used solely for those checks.
No new dispatch infrastructure, metadata, public API, key policy, grammar or projector change is introduced.

Executable evidence:

- The two revised same-compilation/binary inheritance expectations failed on the old implementation, then passed after
  removing the fence. The inherited field selection stays associated with its original generated public declaration.
- `StructuredDerivedMapDispatchTest` separately compiles descendants against binary parent artifacts. String and enum
  cases compare exact `adder:`/`map:` event traces with ordinary typed Builder calls, seeded Map defaults, replacement
  and insertion order. Competing overloads/converters deliberately throw, proving Struct invokes the native route.
- Mapping-failure cases preserve the original exception object, reached index `/values/1`, source label, seeded entries
  and prior replacement, while preventing later values from running. The explicit ordinary enum converter control
  preserves its original converter exception and remains outside Struct K1 admission.
- `BinaryOwnedRelationshipsTest` adds packaged String/enum overrides and matching schema shape assertions to its
  existing Groovy 3/4/5 source-free consumer fixture. Existing corruption, lifecycle, Template and exclusion tests remain.

Follow-up qualification passed in 19m 8s, with zero failures/errors:

| Check | Cases | Existing skips |
| --- | ---: | ---: |
| `:klum-ast:test` full baseline | 3,128 | 16 |
| `:klum-ast-runtime:test` full baseline | 73 | 1 |
| `:klum-ast:groovy4Tests` selected regressions | 153 | 0 |
| `:klum-ast:groovy5Tests` selected regressions | 153 | 0 |
| `:klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest'` | 7 | 0 |

The selected compatibility regressions are `StructuredDerivedMap*Test`, `StructuredInputCollectionContractTest`,
`StructuredListConverterTest` and `StructuredOperationSelectionTest`; each task receives its own test filters.
All 41 K1 cases passed in each Groovy lane (123 total, no skips). License checks passed in all three affected modules.
Reproduction command:

```shell
./gradlew :klum-ast:test :klum-ast-runtime:test \
  :klum-ast:groovy4Tests --tests '*StructuredDerivedMap*Test' --tests '*StructuredInputCollectionContractTest' --tests '*StructuredListConverterTest' --tests '*StructuredOperationSelectionTest' \
  :klum-ast:groovy5Tests --tests '*StructuredDerivedMap*Test' --tests '*StructuredInputCollectionContractTest' --tests '*StructuredListConverterTest' --tests '*StructuredOperationSelectionTest' \
  :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' \
  :klum-ast:license :klum-ast-runtime:license :klum-ast-gradle-plugin:license --max-workers=4
```

Standards review found zero actionable findings. Specification found the inherited-override overconstraint; the
follow-up review confirms it is corrected, with zero unresolved findings. Changed Markdown/link/diff checks passed.
Updated immutable documentation render/crawl: pending.

The bounded correction removes two admission exclusions and unused marker data;
it changes no public/generated signatures, metadata version, parser, payload shape or projection implementation.
Those focused and affected-module checks qualify this correction; a new full root `check` was not warranted by this
removal of redundant exclusions. No build topology, generated callable or shared payload contract changes. The initial full-root result above is historical evidence, not qualification of this follow-up.

## Delivery boundary

User documentation, #1015 inventory, ADR 0032 and `CHANGES.md` describe only this native K1 slice. Existing navigation
and public entry points remain sufficient. Delivery channels: Git SSH dedicated-branch push dry run and gh CLI repository-permission preflight both returned
`authorized`; no GitHub App write capability is inferred. Master was refreshed after validation and remains at the
original #1027 base; no rebase was required. Commit-history review retained characterization, bounded implementation,
synchronized documentation and this evidence update as distinct reasoning steps. Final evidence-only edits do not
change the qualified production/test or rendered user-page inputs.

Tracker impact is `Related: #1028`; no issue is closed or changed by the
worker. Hosted CI/SonarCloud and merge state are separate from local qualification. Hive reconciliation is requested
for final delivery and acceptance; no worker self-archival follows.
