# #959 D7: field constraint projection

Date: 2026-10-26. Status: implementation, full local qualification and independent reviews complete; draft publication follows.
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

Each declaring generated public Builder has the shared `GeneratedInput.Schema.version=1`; each field explicitly emits
`constraintProjection`, including its default, and complete numeric facts. The exact byte reader checks known policy and
fact completeness for both consumers without executing Schema code. Policy still controls generation, not admission or
runtime validation. Recompile participating root/inherited/owned artifacts with a matching toolchain; older or incomplete
contracts are rejected without mixed-version fallback.

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

JDK 17 `./gradlew check --no-parallel --max-workers=2` passed at stable revision `8ebe9a3bd` in **18m 52s**
(141 tasks, 76 executed). Groovy 3/4/5 totals: **3,159 / 2,637 / 2,637**, zero failures/errors, with **17 / 16 / 16**
existing skips and no new suppression. All **69** packaged schema-generator cases pass. The new policy runtime test
passes all three cases in each lane. Licenses, test-lane isolation, plugin validation and documentation renderer checks pass.
The final focused run (11 packaged policy/corruption cases plus three runtime cases) passed before this full run.

External `jsonschema==4.23.0` against newly emitted outputs passes structural **195 inputs / 18 metaschemas**, integral
**429 / 3**, decimal **426 / 3**, deprecation **96 / 6**, plus **97** retained hand-authored P2 inputs. Ten contradictory
emitted schemas pass metaschema validation, accept omission and reject their supplied-value corpus. Producer-generation
byte identity remains enforced by the retained validators.

Independent read-only Standards and Specification subagents reviewed `681aa0be...8ebe9a3bd`: **zero findings on each axis**.
The two reasoning commits separate the implementation/test cycle from the contract/user/release documentation; history
review found no need to rewrite them. The subsequent evidence-only commit changes no qualified code, test or build input.
All six changed Markdown files have balanced fences; **121** relative targets and **56** local anchors pass, as does the
branch diff check. Current user pages stay in the existing navigation; no new page/sidebar entry is required.

Delivery authorization: Git SSH branch publication **authorized** (receive-pack dry run succeeded); connected GitHub
reads **authorized**. Actual draft PR/issue delivery and CI/Sonar outcomes are recorded on the linked PR and issue to avoid
repeated evidence-only CI triggers. No local shared-project Sonar upload is performed; full PR CI/Sonar is the authoritative
remote check. At the local handoff boundary those remote checks have not run, and no remote passing result is claimed here.

Tracker relationship is **Related: #959**; no issue closure, labels, milestone, release placement or curation change.
No merge is authorized. Hive owns subsequent delivery/archive reconciliation.
