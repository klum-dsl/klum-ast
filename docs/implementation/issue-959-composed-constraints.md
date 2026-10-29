# #959 / COMPOSE-PROJ: first-level composing constraints and documentary text

Date: 2026-10-29. Authority: the explicit implementation assignment following P3-GRILL and merged S1/S2.
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959); no closure or milestone change.

## Pinned scope and evidence

Exact base: `09eb73ca0a3e2a2dc1bf8ab26560da9f8711d91c`, freshly fetched and verified current master, merged S2 PR #984.
Branch: `codex/959-compose-proj`. Isolated worktree: `/Users/stephan/.codex/worktrees/959-compose-proj/klum-ast`.
The IDE-main checkout is not edited. S1/S2's contract reader/resolver/result and closed catalog remain the owning seams.

P3-GRILL (`01a183c6-275a-7eb2-9458-c26b59fee25a`) establishes the accepted boundary. Its temporary first-level probe
covers all five mappings, independent contributors, non-AND/override/nested fallbacks, three Groovy generations,
named modules, corrupt facts and 40 real Bean Validation controls. The probe is design evidence rather than production
qualification; this branch adds production compiler/reader/catalog/renderer integration and packaged regression coverage.

The accepted [ADR decision](../adr/0031-structured-schema-input-artifacts.md#accepted-first-level-composition-and-documentary-meta-annotations--959--compose-proj)
projects only first-level Jakarta composing constraints with clear AND semantics through the existing numeric and bounded
Pattern mappings. Independent and contradictory assertions remain independent. OR/ALL_FALSE, overrides, nested/uncertain
composition, unsupported constituents, repeats, groups/payload and unqualified sites/origins stay descriptive. No reducer,
new mapping, SPI/handler/discovery, validator execution, parameter facts/#971, Size, Jackson, IDE work, source fallback,
module or dependency is introduced.

## Fact and execution boundary

The compiler captures original declaration identity before annotation rewriting, then emits typed facts on the public
Builder. Every rule explicitly carries origin, outer declaration association and documentary text. Direct-origin
eligibility (`plain`) retains its meaning. Qualified composing-AND eligibility is separate; unsupported composing facts
never masquerade as direct constraints. Documentary-only annotations carry prose without being labelled runtime rules.
Only the first composition level is inspected; no recursive composition walker or attribute substitution is introduced.

The reader decodes and validates required strings, origin vocabulary, direct/composing consistency, owner association
and singleton eligibility plus explicit field rule counts (detecting stripped rows) before suppression, execution or output replacement. Existing numeric/decimal/Pattern facts
remain mandatory and validated even for unsupported composing rules and OMIT. The normalized result stays inert and
immutable. The closed catalog reuses its exact five mappings; the renderer retains policy, descriptions, independent
conjunction, structural fences and exact use placement. Runtime interpretation does not consult the plugin catalog.

The shared development protocol marker stays 1. Align compiler/runtime/plugin versions and recompile participating
Schema hierarchies and owned artifacts; no historical or mixed-toolchain compatibility promise is added.

## Documentary API and vocabulary

`InputConstraintDescription(String)` targets ANNOTATION_TYPE only. Literal prose on domain, propagation-only or Bean
composed annotations appends under PROJECT/TEXT_ONLY and disappears under OMIT. It supplies no enforcement, assertion,
example, default, handler or generic schema mutation. Explicit field examples are unchanged.

CONTEXT, ADR, user/migration pages and comments use meta-annotation, composed constraint and composing constraint.
Propagated annotation is reserved for `PropagateAnnotation`; constraint projection names conversion into artifact facts.
The previous P3 custom-handler proposal is explicitly historical and superseded for this slice.

Documentary traceability: `InputConstraintDescriptionTest#'document a domain rule without adding enforcement'`, with class-level
`@Issue('959')`, feature `@Tag('documentary')` and `@See` to the user JSON Schema section. The user page references the
same feature. Packaged coverage uses `KlumJsonSchemaGenerationTest` and source-free G3/G4/G5 producers.

## Qualification

Initial red-to-green work added compiler documentary/placement/literal tests and the packaged composition contract.
The first compiler fixture required correction to place its DSL class before its annotation declaration; the harness
assumes the first class has a Builder. The initial packaged expectations exposed the compiler's existing canonical
Max-before-Min ordering; expectations preserve that deterministic order. A positive initialization control initially used
deleted class directories and was corrected to use the compiled JAR. The first full check also exposed an overly broad original-annotation capture predicate that changed unrelated KlumCast
provenance diagnostics. Capture was narrowed to Bean composed constraints plus the existing Pattern/collector cases.
The full core G3 suite (2,509 cases, zero failures/errors, 16 existing skips), focused G4 diagnostics and source-free
G3/G4/G5 composition matrix then passed. None required weakening production diagnostics.

Full root `check annotationPropagationArtifactsGroovy3 annotationPropagationArtifactsGroovy4
annotationPropagationArtifactsGroovy5 --no-parallel --max-workers=2` passes on the clean qualified head
`9d0c09bdce5371f9cae177d3ebdb59a802c2aa6a` in **26m 37s**, with JDK 17. The root/plugin, compiler/public Builder,
JPMS, license, lane-isolation and documentation-renderer checks pass. Top-level module JUnit reports record:

| Lane / task | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| test (G3) | 3,425 | 0 / 0 | 17 |
| groovy4Tests | 2,759 | 0 / 0 | 16 |
| groovy5Tests | 2,759 | 0 / 0 | 16 |
| annotationPropagationArtifactsGroovy3 | 1 | 0 / 0 | 0 |
| annotationPropagationArtifactsGroovy4 | 2 | 0 / 0 | 0 |
| annotationPropagationArtifactsGroovy5 | 2 | 0 / 0 | 0 |

All **644** plugin cases pass, including **139** packaged schema cases. The Pattern recognizer and its proof are unchanged.
Source-free producers remove domain annotation, validator, constant-supplier and provider-marker classes before generation;
separate positive controls initialize the live validator and Model traps. Mixed direct/composing Patterns, duplicate numeric
contributors, inheritance/rename/deprecation/examples, policy routing, unsupported branches and both consumers' malformed
facts (including whole stripped rows under OMIT) have focused coverage.

The local Standards review identified missing braces and unclear positional fixture calls. A follow-up adds braces and a
composition-specific fixture helper, without changing production behavior. It also gives the ICD-only compiler test its
accurate `InputConstraintDescriptionTest` name and strengthens the retained integral/decimal external controls: their former
accepted composed out-of-range values now reject. The old controls first fail specifically on those newly supported bounds.
No failure is weakened. After this follow-up, ICD cases pass on G3/G4/G5 (3 per lane), and all 33 focused source-free
composition/corruption/catalog plugin cases pass. No new ignored test or suppression is introduced.

Independent jsonschema 4.23.0 Draft 2020-12 validation passes **234** new composition input controls and three metaschemas,
with byte-identical G3/G4/G5 outputs. The retained exact-number validators pass **456** integral and **447** decimal inputs
with three metaschemas each. All **10** retained contradictory schemas remain valid, allow omission and reject their
supplied-value corpus. The final composition oracle is
`klum-ast-gradle-plugin/src/test/resources/validate_composed_constraint_schema.py`; the existing integral/decimal scripts
retain their independent expected-value controls.

The initial Specification review reports no missing, incorrect or unrequested behavior. Both independent review axes must
be refreshed at the exact final tip before publication; their final disposition and exact published head are reported in
the draft PR and task callback. The two original commits remain preserved; local review fixes are an additive follow-up.
Markdown fences, relative-file links and `git diff --check` are verified again before publication. No local SonarCloud
analysis/upload or manual workflow dispatch occurs.

## Delivery and reconciliation

Tracker relationship: Related: #959. Curation impact: none. No issue closure, milestone change, manual workflow dispatch,
local SonarCloud analysis or upload. The assigned branch may be pushed and published as a draft; ordinary PR CI owns
SonarCloud. Review/merge and Hive reconciliation remain external. The worker retains the worktree and requests Hive
reconciliation; it does not self-archive.

Delivery preflight verifies Git SSH feature-branch delivery and GitHub CLI repository delivery separately as
**authorized**. Remote master still equals the pinned base at preflight; no refresh/rebase is needed. No credentials or
scopes are recorded. Exact final commit, draft PR and current-head CI/Sonar state are reported in the task callback.

## Publication-time master integration

While draft PR #986 opened, master advanced to `7879935bb8050549b8f475fb328a634896315f55` through merged #985.
That change adds a runtime Bean composition documentary test and advanced documentation. The branch integrates it in
merge commit `1fa5cc095` without rewriting the reviewed prefix. The advanced paragraph is synchronized with the newly
supported first-level AND projection boundary; the runtime propagation/validation example remains unchanged.

Qualification after integration passes all Bean Validation module tests on G3/G4/G5, ICD controls on all three lanes,
the source-free composing-constraint matrix, Bean Validation licenses and the documentation renderer (58 seconds,
zero failures/errors). No production behavior changes in this integration. Final review uses the actual current PR base
`7879935bb8050549b8f475fb328a634896315f55`; the original execution base remains `09eb73ca0a3e2a2dc1bf8ab26560da9f8711d91c`.
Final tip/review and CI state are reported in the PR/task callback.
