# #959 D7: field constraint projection

Date: 2026-10-26. Status: implementation and focused qualification; final checks/reviews pending.
Base: current `origin/master` at `681aa0be48ae520638d97c613a52442e0ad0e3e2`, after merged #972 and #973.
Branch: `codex/959-constraint-projection`. Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959), which remains open.
Authority: accepted first D7 slice assignment, [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#d7-field-constraint-projection-slice--959)
and [vocabulary direction](issue-959-d7-vocabulary-decision.md). No release/milestone/curation change is proposed.

## Discovery and contract

Inspected the current public `GeneratedInput` annotations, compiler `StructuredInputConstraintMetadata`,
`StructuredInputAdmissionPlan`, `AnnotationPropagationContributor`, runtime `CompiledBuilderSchema` and plugin
`JsonSchemaProjection`. Constraint capture runs after effective propagation/Required lowering and before source state
is moved. Numeric facts are original-field-associated, never copied validation annotations on executable Builders.

Hard mappings remain the #972 subset: single eligible Jakarta Min/Max on integral scalars and DecimalMin/Max on
integral/BigDecimal scalars, empty groups/payload, no relevant group sequence/redefinition. Structural machine ranges
are emitted independently before declaration bounds. Colliding assertions conjoin; contradictions are not solved.
Unsupported captured constraints remain descriptions; arbitrary composition/repeatable/provider mappings do not exist.
Approved one-hop propagation uses annotation defaults only: NotNull can be contributed; Min/Max and DecimalMin/Max
cannot because their value member is required. The slice does not broaden those rules or evaluate defaults.

Selected author contract: FIELD-only, RUNTIME-retained, documented `ConstraintProjection`, nested `Policy` enum with
`PROJECT` (default), `TEXT_ONLY`, `OMIT`. Nesting avoids an unrelated top-level policy namespace; three values cover
exactly the accepted behavior without selector lists, profiles or extension hooks. `OMIT` is explicit about all
constraint-derived output, unlike the earlier ambiguous `SKIP` candidate. Absence and an empty annotation are equivalent.
The field policy applies to every captured supported contribution, including approved propagated origins. Unsupported
captured facts follow the same description policy. Future supported contributions inherit the same field choice.

## Transport and authority

Each declaring generated public Builder has `projectionVersion=1`; each field has an explicitly emitted
`constraintProjection` enum, including the default. Groovy's ordinary compiler folds the typed annotation expression,
including static imports. The shared ASM reader retains only the descriptor and name; generation validates the known
policy and completeness. `constraintVersion=1` and `decimalVersion=1` stay unchanged. Structural/runtime reading does
not consult policy for admission. Matching compiler/runtime/plugin versions and recompilation of every reachable
root/inherited/owned contract are required; older strict readers reject newer facts and no mixed-version fallback is added.

The original declaring field identity determines policy, including inherited/renamed sources. Existing hierarchy,
collision and exclusion authority remains unchanged. An independently admitted replacement declaration uses its own
policy/default. A relationship policy never recurses into a shared child definition. Missing/unknown policy or supported
malformed facts fail before output replacement even under `OMIT`; numeric parsing is still performed before routing output.
Source/artifact diagnostics include recompile/package guidance. No Model bytes or authored code supply missing facts.

## Implementation and test-first sequence

1. Add the source-free documentary behavior to existing packaged generator fixtures and run it red on Groovy 3/4/5:
   the annotation was unresolved in each lane, before any production implementation.
2. Add annotation/compiler facts/shared decoder and policy routing; preserve machine bounds/prose/deprecation and
   independently validate supported facts. Add corrupt metadata/atomicity controls and runtime validation/admission tests.
3. Qualify focused tests, the affected module suites and all compatibility lanes; run root checks, documentation/diff checks,
   then independent Standards and Specification reviews. Review commit history before publishing a draft PR.

`KlumJsonSchemaGenerationTest#'choose field constraint projection without changing structural facts with Groovy #generation'`
is the documentary example linked to the [user contract](../user/Json-Schema.md#field-constraint-projection). Separate API
and Schema producer JARs have deleted sources; a third consumer has no source. Groovy 5 also uses the packaged plugin.
The fixture covers explicit/default PROJECT equivalence, exact decimal TEXT_ONLY, OMIT, direct/propagated NotNull,
inherited rename, cross-module owned child, ignore, ordinary prose/deprecation and initialization traps.

`field policy metadata fails closed before replacing output` corrupts root/inherited/owned completeness, future versions,
missing field policy (including OMIT), unknown constants and malformed suppressed decimal facts; previous output survives.
`ConstraintProjectionTest` checks real Schema compilation, static enum imports, runtime validation, construction-key
requirements and unknown-input admission across all three policies. Existing packaged corpus supplies collision,
exclusion, key/null/omission and no-execution regressions without new mapping scope.

## Deferred work

Naming profiles/scopes/precedence, broader JsonNaming, all examples and Javadoc-tag machine data (pending separate
AnnoDocimal structured-tag retention), selective policy, arbitrary composed/repeatable hard mappings, Size/Pattern/defaults,
setter validation, provider SPI, runtime/interpreter behavior and Gradle task surface remain deferred. Jackson and Bean
Validation remain optional adapters. No WIRE carrier, registry, callback or generic filtering infrastructure is added.

## Qualification and delivery

Pending final test totals, independent review dispositions and draft PR/CI evidence. No merge or #959 closure is authorized.
