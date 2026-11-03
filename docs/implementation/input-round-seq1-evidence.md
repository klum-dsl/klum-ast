# #952 SEQ-1 — ordinary owned child-body sequences

Base: exact master `097b0d5619a5f3cac0c05a5956fda530ff34bf0d`.
Isolated worktree: `/Users/stephan/.codex/worktrees/08e3/klum-ast`.
Dedicated branch: `codex/952-owned-sequence-struct`. Tracker impact: **Related: #952**; no closure, labels,
milestones, curation or release-gate changes. Hive owns delivery/archive reconciliation. No merge is authorized.

## Accepted contract and implementation

One supplied List of child-body Maps configures ordinary owned concrete nongeneric unkeyed DSL children in exact
`List<T>`, `Set<T>`, `SortedSet<T>` and `NavigableSet<T>` fields. The compiler selects only the complete ordinary generated
public creator family associated with that field: `(Map, Closure)`, `(Closure)`, `(Map)` and `()`. The two closure-omission
projections derive from existing generated creators. Authored same-Schema replacements and descendant overrides of
any spelling exclude sequence admission, rather than silently bypassing the effective ordinary creator. Existing Selection facts and schema completeness counts
carry the role; no new metadata carrier, public API, parser or construction engine is introduced.
The resolver validates destination, child/key/generic facts and exact creator signature before interpreting input or
projecting schema, without user initialization. Matched compiler/runtime/plugin and Schema hierarchy recompilation
remain required; the existing development protocol marker alone does not establish mixed-artifact compatibility.

`InternalKlumBuilder.addNewDslElementToCollectionWithConfiguration` extracts the existing ordinary Consumer interval:
`FactoryHelper.prepareNestedBuilder` owns initialization, child Template copying, PostCreate, body configuration and
PostApply. Ordinary `addElementToCollection` still owns claim normalization, insertion and placement. Both ordinary
Groovy and Struct use this sequence. There is no returned-Builder reconfiguration, synthesized Closure, preflight,
rollback, comparator policy or placement-path repair. Input diagnostics use the supplied index, independent of Set size.
Materialization retains its normal lifecycle attribution.

Every reached mapping is an operation in supplied order. List duplicates remain; plain Sets can collapse equal
completed Models after every fresh Builder ran preparation; sorted comparator-equivalent insertion can discard a
configured Builder before later graph lifecycle. Target collection and comparator objects remain authoritative,
including ordinary natural-order/Model-typed comparator failures and comparison failure during materialization.
An insertion failure can leave the failed Builder claimed without a placement path; prior work remains and later
entries/operations are absent. Fresh roots after failure prove the tested session cleanup.

Template definition uses the same preparation interval without ordinary lifecycle. Application copies the retained
recipe into fresh recipients with normal ownership/lifecycle. Entries collapsed at definition are not replayed.
List, Set and successful comparator-backed SortedSet/NavigableSet recipients are qualified without changing Template
semantics. JSON Schema emits an array of the closed child-body grammar with no uniqueness, cardinality, final-order,
or inferred comparator assertions. It describes Struct grammar, not every Groovy expression or Model output.

## C3 provenance and acceptance map

C3 source/evidence commit: `4b6e84c334ef6081fbbae7bde89c63fe4319dcec`, files
`klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/OwnedSetOperationStreamProbeTest.groovy` and
`docs/implementation/input-round-c3-set-probe.md` (read with `git show <commit>:<path>`).
R1 source: `2f437422136dcbd63ff73c012f18b0aa8f5ef356:docs/implementation/input-round-r1-review.md`.
Neither source worktree was edited or merged. The decisive C3 fixture/assertions are folded into
`StructuredOwnedSequenceTest`, using the real public FromStruct route rather than retaining the test-only Consumer.
Its returned-Builder timing counterexample is retained. No duplicate probe framework is added.

- `StructuredOwnedSequenceTest`: ordinary/Struct callback and attachment parity; List duplicates and all proved Set
  variants (default/LinkedHashSet/HashSet, comparator-backed sorted/navigable); equality/representatives/comparator
  identity; natural-order/typed-comparator/materialization failures; ownership, placement and partial state;
  initial children, empty sequences, initial/default and child Template values; Template definition/fresh recipient
  parity; malformed containers/bodies, source/escaped supplied-index diagnostics and skipped later operations;
  excluded keyed/abstract/aggregation families; source-free positive inspection and stripped/corrupt creator, selection,
  role and getter controls before Schema initialization.
- `BinaryOwnedRelationshipsTest`: source-free separately packaged G3/G4/G5 APIs/Schemas and consumers, inherited sequence
  fields, renamed child member bodies, runtime ownership/order/equality parity and actual schema generation.
  Sources/classes/mirrors are unavailable to the consumer; initialization traps distinguish inspection from execution.
- `JpmsPackageBoundaryTest`: real Java/static Groovy artifact consumers exercise FromStruct List/Set bodies, alongside
  existing classpath and named-module controls. Compatibility lanes qualify named-module Schemas on G4/G5.
- `validate_owned_sequence_schema.py`: independently validates actual emitted Draft 2020-12 schemas, accepting duplicate
  body operations and rejecting null, scalar, wrong-container, unknown-member, owner, invented-key and subtype payloads;
  explicitly rejects uniqueness/cardinality/default keyword promises on all five packaged relationship properties.

Documentary regression: `StructuredOwnedSequenceTest#'creates ordinary owned children from supplied body sequences'`,
with `@Issue('952')`, `@Tag('documentary')` and `@See` linked to
[owned child sequences](../user/Structured-Input.md#owned-child-sequences). JSON Schema, Basics collections, Templates,
Migration, CHANGES, ADR 0032 and the 4.2 plan are synchronized. No new user page/navigation entry is needed.

## Boundaries and review focus

Excluded: keyed children/member handling, maps, abstract/subtype alternatives, CollectionFactory creators,
custom/specialized collection declarations, EnumSet, LINK/OPTIONAL_LINK, scalar converter elements, generic Factory or
selector semantics, comparison/initializer repairs, parser/Jackson/copy/composition redesign and arbitrary recursive
collection grammar. Nested declared child members retain their independently admitted grammar. This is a Related slice
of #952; no broader grammar, adopter release qualification or issue completion follows.

Review the exact generated creator selection and reflected integrity before exclusion, then the shared preparation
interval, indexed exception wrapper and unchanged target insertion. For Sets, separate operation order from retained
representatives, graph lifecycle and completed iteration. Check Template retained-recipe behavior rather than promising
replay of original input. Existing ordinary size-based placement behavior is deliberately preserved.

## Validation and review status

The initial public List documentary scenario failed at admission before implementation and passed afterward.
The first combined focused run passed 57 sequence cases, one real binary/module-consumer case and seven source-free
packaged cases, with zero failures/errors/skips. Independent actual-output validation passed **45 accepted and 225
rejected** instances against three emitted schemas using pinned jsonschema 4.23.0.

The intermediate source-free child initialization traps and 597 Struct regressions passed with affected licenses.
The completed final qualification below supersedes that intermediate scope. No local SonarCloud upload was performed;
hosted CI/Sonar on a published head remains separate.

## Initial local review corrections

Exactly two declared read-only children reviewed base `097b0d5619a5f3cac0c05a5956fda530ff34bf0d` through
`4602c99334013491fe0d3b0432ecced08acbd056`. Standards requested a `when`/`then` block for the Template's multiple
assertions. Specification found inherited creator overrides could be bypassed by the internal callback. All four
replacement spellings reproduced the missing exclusion before the correction.

The compiler now proves both original closure-bearing creators and their existing public closure-omission projections,
records all four exact selections, and excludes authored same-Schema replacements. The resolver requires this complete
family and rejects descendant overrides through inherited descendants. The ordinary override remains callable while
Struct rejects its route. Same-Schema Map/no-argument collisions retain ordinary default-argument compilation errors;
no compiler or runtime creation semantics are repaired. An absent convenience projection also fails integrity checks.
The initial full-root attempt at the prior head was intentionally stopped for this correction; it is not qualification.
Reviewed commits are preserved, with one additive correction before a fresh fixed-head root run.

The corrected focused run passes 606 Struct cases plus the real binary/module consumer and seven packaged G3/G4/G5
cases, with zero failures/errors/skips. License checks pass. The documentary example now shows the public Factory call
directly. The fixed-head full root and documentation checks below qualify the corrected production tree.

The next root attempt found one stale fixed rejection reason in `CompiledInputExpectedContracts`: despite its
`unkeyedChildren` field name, that fixture's Child has a Key. It remains excluded; its reason now follows owned
relationship classification. Compiler-plan and source-free reader expectations are synchronized. The focused compiler/
reader checks and sequence controls pass on G3/G4/G5. That failed root attempt is not counted as qualification; the clean
fixed-head root run below follows the expectation-only correction. No production behavior changed in this follow-up.


## Completed local qualification

Qualified implementation, tests and user documentation: `0f5f03ab5108bd549be4d3ef59f454da4f1f40d0`.
The clean, fixed-head Java 17 root `check` passed in **28m 51s**, 140 tasks (82 executed, 58 up to date).
No source, test, build-input, HEAD or dirty-state change occurred during that run.

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 4,064 | 0 / 0 | 17 |
| Groovy 4 compatibility | 3,377 | 0 / 0 | 16 |
| Groovy 5 compatibility | 3,377 | 0 / 0 | 16 |
| Total | 10,818 | 0 / 0 | 49 |

All **66 owned-sequence cases per lane** pass without skips. Full compiler suites contribute 3,023 / 3,025 / 3,025
cases; all 665 plugin cases pass, including seven source-free binary/projection cases and the existing packaged producer
matrix. Qualification covers affected module suites, real Java/static Groovy binary consumers, G4/G5 JPMS Schemas,
source mirrors, license checks, plugin validation and lane isolation. No new ignored/pending tests were introduced.

The repeated actual-output oracle passes **45 accepted / 225 rejected** sequence instances against G3/G4/G5 emitted
schemas with pinned jsonschema 4.23.0. Retained virtual-scope (12 / 33) and literal-alternative (24 / 75) oracles also pass.
All three owned-sequence schema files have SHA-256
`64d806ddde252c12d3f4a16f1f2e6b51dfdfc5909e0337a82d3cf07127f79abe`.
The clean-revision documentation render and site crawl pass (28s, 36 tasks). All 251 relative targets in changed
Markdown files, balanced fences, documentary annotation/heading alignment and `git diff --check` pass.

Exactly two local read-only children cumulatively reviewed the full base-to-qualified-head diff:
**Standards: zero remaining findings. Specification: zero remaining findings.** They also checked both additive
corrections; neither edited files, ran builds/tests, spawned children, performed remote actions or changed task lifecycle.
The final qualification-record commit changes prose only; its final documentation render/diff check is verified before
publication. Commit-history review preserves the original implementation/docs and additive review/expectation fixes,
ordered by reasoning and dependency. No reviewed commit is rewritten.

Commands (Java 17):

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --no-parallel --max-workers=2 --console=plain
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew renderLocalDocumentation --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' --no-parallel --max-workers=2 --console=plain
PYTHONPATH=/private/tmp/l1b-jsonschema-423 /Users/stephan/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 klum-ast-gradle-plugin/src/test/resources/validate_owned_sequence_schema.py klum-ast-gradle-plugin/build/schema-validation
PYTHONPATH=/private/tmp/l1b-jsonschema-423 /Users/stephan/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 klum-ast-gradle-plugin/src/test/resources/validate_virtual_scope_schema.py klum-ast-gradle-plugin/build/schema-validation
PYTHONPATH=/private/tmp/l1b-jsonschema-423 /Users/stephan/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 klum-ast-gradle-plugin/src/test/resources/validate_literal_alternative_schema.py klum-ast-gradle-plugin/build/schema-validation
```

## Delivery boundary

GitHub CLI repository delivery capability was verified **authorized**, without recording credentials or scopes.
The authorized delivery is a dedicated-branch draft PR with **Related: #952**, with documentary/issue traceability.
Actual transport, PR identity/head and hosted check state are verified in the worker callback. Hosted CI/SonarCloud,
maintainer review/merge and Hive delivery/archive reconciliation remain separate; no release or merge is claimed.
The worker retains the open-PR state and requests Hive reconciliation rather than self-archiving.

## SonarCloud follow-up — PR #1020

Hosted analysis of published head `3e692d2bc803cae7f185079b77e1037613b264a0` reported four open findings.
The build/JUnit checks passed, while the SonarCloud analysis gate failed only its new-code reliability rating;
new-code coverage was 89.1% and the other gate conditions passed.

- `javabugs:S2259`: retain the collection element ClassNode in a local variable and reject a missing type before
  calling `redirect()`. This makes the private classifier's fail-closed ownership boundary explicit.
- `java:S1872`: compare the reflected destination Class directly with imported `EnumSet.class`, rather than its name.
- `java:S2589`: remove `map || list` after the earlier container-shape guard has already established it.
- `groovydre:S1481`: remove the unused `childType` local in the ordinary/Struct test runner.

All four findings are addressed in source; none is intentionally retained or suppressed. No issue dismissal or
Sonar status mutation is used. Public/generated signatures, metadata, grammar, ownership, lifecycle, Template and
collection semantics are unchanged. Existing reviewed/published commits remain frozen; this correction is additive.

The focused Java 17 run passes 175 compiler/reader/sequence cases and all 73 runtime baseline cases (one existing skip).
The subsequent affected-module checks and targeted packaged consumer run pass in 14m 37s:

| Scope | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Compiler, Groovy 3/4/5 | 9,073 | 0 / 0 | 46 |
| Runtime, Groovy 3/4/5 | 219 | 0 / 0 | 3 |
| Packaged binary/schema consumers | 7 | 0 / 0 | 0 |
| Total | 9,299 | 0 / 0 | 49 |

All 66 sequence cases pass per lane. The complete compiler lanes include binary/JPMS consumers; module checks include
licenses and lane isolation. Repeated actual-output oracles pass 45/225 owned-sequence, 12/33 virtual-scope and 24/75
literal-alternative accepted/rejected controls. Diff and evidence-document checks pass.

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :klum-ast:test --tests '*StructuredOwnedSequenceTest' --tests '*StructuredInputAdmissionPlanTest' --tests '*CompiledBuilderSchemaTest' :klum-ast-runtime:test --no-parallel --max-workers=2 --console=plain
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :klum-ast:check :klum-ast-runtime:check :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' --no-parallel --max-workers=2 --console=plain
```

The affected-module tier is appropriate for this local guard/identity/tautology/test cleanup. No generated ABI, build
configuration, module descriptor or user grammar changes require another full root run. The prior root result remains
historical evidence for its exact qualified revision; it is not claimed for this follow-up. User-facing pages require
no behavioral update; only this implementation evidence changes. Local SonarCloud upload is not performed. The
consolidated PR follow-up records review disposition and the new revision's hosted CI/SonarCloud state separately.
