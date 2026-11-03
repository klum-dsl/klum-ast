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

The later source-free child initialization traps and all 597 Struct regressions pass, together with affected license checks. Full root qualification, documentation render/crawl and the exactly two read-only local review axes remain
required before publication. No local SonarCloud upload is performed; hosted CI/Sonar on a published head is separate.

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
directly. The final corrected production tree still requires the fixed-head full root and documentation checks below.
