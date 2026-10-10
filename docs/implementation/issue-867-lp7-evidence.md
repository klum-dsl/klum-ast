# #867 LP-7: container participant exploration

Date: 2026-10-10. Base: origin/master through merged LP-6 PR #877,
`19c27198f82f7b39705786da54c43a3201443fb1`.
Authority: bounded evidence-only exploration, followed by explicit maintainer acceptance of
retaining rejection for this release on 2026-10-10; no production container support or public API authorized.
Related: #867. Issue state, milestone, curation and conditional release placement are unchanged.

## Accepted disposition

**Retain rejection of Collection/Map field participants for this release.** The maintainer
accepted this recommendation on 2026-10-10 after reviewing the exploration. LP-7 investigation
and its mandatory pre-release support-or-reject decision are complete. LP-8 qualification and
full issue acceptance remain open; this decision does not complete #867.

Smallest honest current options are a containing Builder lifecycle callback, an LP-5 type
mutator on the child Schema when behavior belongs to that type, or an explicit DSL wrapper
with a direct-field participant. Each keeps selection/enumeration policy in consumer code.
Type dispatch cannot stand in for an annotation on one container relationship: the declaration
lookup is on the child type and aliases visit once. No ScHelm assumptions are needed.

The accepted release boundary retains the existing clear source/binary direct-field rejection
and adds no container API or dispatch. LP-8 records this boundary in user guidance and whole-feature
qualification. Future support requires a separately accepted design. The maintainer authorized
recording and committing this decision locally; push and PR creation remain unauthorized.

## State and shape matrix

[LifecycleParticipantContainerProbeTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/LifecycleParticipantContainerProbeTest.groovy) supplies 118 @Issue('867') executable cases.
The new feature in [LifecycleParticipantConsumerTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/LifecycleParticipantConsumerTest.groovy) adds seven artifact cases, also under
its class-level @Issue('867'). The matrix distinguishes field participation from ordinary traversal.

| Shape/state | Annotated field creator or mutator | Existing traversal / type participation |
| --- | --- | --- |
| Direct DSL field | Accepted in AutoCreate, AutoLink, Default, PostTree (eight declaration success controls) | Existing LP-1–LP-5 contract |
| List / Set / Map of DSL values | Rejected at compilation for both roles in all four phases | Composed children receive type callbacks; context has containing Builder and field name, concrete child declared type, no index/key |
| Raw List / Set / Map; wildcard List / Map; DSL array | Same direct-DSL-field diagnostic in all four phases and both roles | No participant container typing contract; traversal of every such shape is not qualified by this probe |
| Null container | Declaration still rejected independent of value | No child visit; ordinary null survives |
| Empty container | Declaration still rejected; an empty container is not an absent direct Builder | No child visit; ordinary default containers are empty |
| Null element/value | No participant element contract | Skipped; retained as null in completed List, Set and Map controls |
| Owned DEFAULT composition | No annotated-container dispatch | One type visit per child identity, normal materialization and accepted owning declaration |
| Completed value in DEFAULT composition | No annotated-container dispatch | Ordinary checked assignment rejects completed input |
| LINK container | No annotated-container dispatch | Traversal skips the relationship; active aliases retain their actual owning declaration; completed targets keep identity; fresh unclaimed Builders are rejected |
| Mixed OPTIONAL_LINK owned/sealed values | No annotated-container dispatch | Owned identities visit once, sealed targets skip; completed identity and owned self-LINK cycle survive materialization |
| Duplicate List/Map aliases | No per-occurrence participant call | Identity deduplication; multiple placements materialize to the same Model |
| Duplicate Set alias | No per-occurrence participant call | Set deduplicates identity in these fixtures; no stable positional identity promised |
| Parent PostTree callback adds entry | No annotated-container dispatch | Entry receives current PostTree type visit and declaration claim; AutoCreate does not replay and Owner backlink remains null |
| Child callback grows live List/Set/Map | No new mutation policy | Current fail-fast iterator rejects with ConcurrentModificationException in all three two-entry controls |
| Template definition / recipients | No handler or context recipe state | Value-only definition has zero type calls; each copied recipient child runs once; copied placements may have no owning declaration |
| FromMap | No alternate dispatch surface | Typed input containers construct children and run their type participant; Set input must be Set (the attempted List input was rejected before lifecycle) |

All supported field-role declaration controls use identical annotations/handlers to the negative
controls, changing only the field shape. A diagnostic cannot be attributed to an invalid handler
or phase. Source negatives cover 4 phases × 2 roles × 9 shapes = 72 rejections. They characterize
field shape, not Java annotation-author or JPMS coverage for every combination.

## Declaration rejection and runtime boundary

Current source diagnostic (includes field name and annotation source location):

```text
Lifecycle participant on children requires a non-static direct DSL field retained on the Schema (LP-1)
```

`LifecycleParticipantFieldCheck.checkRole` requires the original field itself to be DSL-typed;
its element/value generic does not satisfy that check. It rejects both roles before Builder
projection. Removing this guard would not implement containers.

`LifecycleParticipantDeclaration.checkParticipantAnnotation` independently requires a non-static
direct DSL field at runtime. Six artifact cases compile an unmarked Java annotation library and
Schema first, then replace only the annotation JAR with a creator or mutator marker on exactly
one of List, Set or Map. Runtime rejects with KlumSchemaException naming the actual container
field, before either handler's deliberate AssertionError can execute. Sources/class directories
are deleted and the runtime classpath excludes the Klum compiler. These binary controls use
AutoLink; source controls cover all four phases and the shape guard is phase-independent.

The existing message clearly identifies the supported boundary; a specialized container error
is not required to stop silent acceptance. With rejection accepted, propose an optional LP-8
wording refinement: remove the obsolete '(LP-1)' suffix and explicitly say Collection/Map
fields are unsupported, with guidance to a containing lifecycle callback or child type participant
when appropriate. Do not imply that a type annotation reads the incoming container annotation.
No production diagnostic changes are made by this exploration.

Dispatch itself cannot receive a collection: creation returns KlumBuilder<?> and uses
`setSingleField`; mutation casts the field's value to InternalKlumBuilder and applies FAIL/SKIP
to that one target. A Collection/Map is neither a Builder nor a sealed Builder. Empty-versus-null
creation, insertion versus replacement, per-entry role conflicts and per-entry FAIL/SKIP are
new decisions; changing compiler eligibility alone would expose invalid dispatch.

## Identity, context, mutation and lifetime implications

`CompositionTraversal` enumerates current collections/maps, then visits objects through an
identity seen-set. It carries the containing Builder and field name into each child visit;
index/key appears only in its diagnostic path. Collections use current iteration position,
including Sets. Maps use a GPath-escaped key; the `a.b[0]` failure control retains
`children.'a.b[0]'`. The participant failure names annotation, handler, AutoLink and Child,
and preserves the original IllegalStateException. List failure similarly retains children[0].
Those diagnostics do not establish a retained occurrence, a stable Set index, or a public
index/key context. No path is parsed to recover ownership or participant inputs.

A List insertion/reorder changes later positions. A Set has iteration positions, not domain
indices; mutable element equality/hash state adds its ordinary collection hazards. A Map key
is unique only within that map at that moment and can be replaced or renamed. One Builder can
appear under multiple keys/indices and via aggregation cycles. The current traversal's single
identity visit does not determine which occurrence an element participant should represent.
A per-container field slot and a per-identity child visit have different cardinalities and
ordering; neither supplies per-occurrence authority. The tests deliberately assert identity
and declaration, not an inferred path-based owner.

Parent field work and callbacks finish before child descent. Adding children there is visible
to current traversal; earlier phases never replay. Growing the live container while a child is
being visited fails in the characterized controls. There is no general snapshot, retry, queued
insertion or deterministic mutation ordering contract. A hypothetical snapshot could avoid this
particular iterator exception but would leave removed/replaced entries, same-phase new entries,
repeated mutators and next-phase behavior to define. Such machinery is outside the accepted scope.

Normalized container attachment invokes existing per-value session/ownership checks. DEFAULT
rejects completed values; LINK rejects a fresh unclaimed Builder; OPTIONAL_LINK may claim fresh
same-session Builders and retain active or completed aliases. A creator returning a container
would bypass the current KlumBuilder return contract and direct assignment. Reusing checked
insertion without claiming from traversal is necessary for any accepted design. Foreign-session
and cross-owner restrictions remain the existing mechanisms, covered by LP-4 and #856
`RelationshipCompositionTest`; this probe adds no new concurrency qualification or privileges.

Before OWNER, owning-declaration reads are not a valid substitute for containing-field dispatch.
The AutoCreate type controls deliberately do not request that authority. After OWNER, normalized
attachments can supply declarations; copied container placements can be traversed and mutated
without authoritative claims. Template recipients in this probe demonstrate that absence while
still receiving type mutation. No declaration is manufactured from a container path or an Owner
backlink. Late PostTree entries demonstrate immediate declaration authority with no OWNER replay.

Materialization preserves completed LINK identities, duplicate aliases and owned self cycles.
Templates remain value-only and apply in recipient sessions; FromMap retains its existing typed
copy/import boundary. `With(Map)` is named DSL invocation and does not deep-import arbitrary
collection element Maps; the Java control uses FromMap. LP-4's Jackson root/Template/Builder/apply
qualification is inherited, not rerun or broadened here. No serialization, import or phase code
changes; no universal Jackson/container contract is inferred.

## Public typing evidence

The artifact control separates Domain and Java annotation-library JARs from compiled Schema and
consumer bytecode. It compiles Java against Parent_DSL.Builder<?> and the actual generated
List, Set and Map getters; Java then mutates entries through Child_DSL.Builder. Static/dynamic
Groovy Model Writers use generated child creation operations and their returned Builders.
Completed results from Java FromMap and both Groovy writers retain those changes.

A negative Java source, `ContainerTypedRead.java`, deliberately assigns those getters to
List<? extends Child_DSL.Builder<?>>, Set and Map equivalents. All three fail compilation:
the emitted getter types nest **raw** Child_DSL.Builder, not Builder<?>. The positive source
names that actual raw nested contract. This is existing generated output, not an LP-7 regression
or authorization to repair generic projection. It limits claims of fully parameterized container
Builder typing and is relevant if support is later designed. No generated interface changes.

## Smallest alternatives and compatibility risks

| Alternative | Capability and cost | Compatibility / honesty boundary |
| --- | --- | --- |
| A. Retain rejection (accepted for this release) | Existing diagnostics; use containing callback, child type mutation, or a direct DSL wrapper | No dispatch/API change. Type mutation has identity/type semantics, not incoming-field annotation semantics. LP-8 must state the accepted rejection clearly |
| B. Mutator-only element snapshot, no index/key API | Potential bounded future design: explicitly opt in, take non-null Builder identities from one actual field slot, invoke existing Builder mutation operations | Still needs accepted duplicate versus identity cardinality, removed/new entries, mixed sealed FAIL/SKIP, original container versus element declared type, field ordering and raw/wildcard rules. No creator capability. A silent loop cannot preserve direct-field semantics; cannot claim support now |
| C. One container-level handler call | Can inspect empty state or choose consumer-managed edits once per field | Current target and creator result are Builder-typed. Needs a separate coherent context/result/assignment API and generated typing qualification; not a small use of the current SPI and no partial API is justified |
| D. Full element creation/replacement and index/key context | Could address individual missing entries and diagnostics | Needs null/empty interpretation, map-key authority, Set identity, alias/cycle cardinality and mutation scheduling. Contextual per-occurrence metadata, path reconstruction, traversal-order ownership inference and ordering machinery are excluded; reject this approach within the accepted boundaries |

B is a design candidate, not a proposed signature or authorized implementation. Even a restriction
to owned composition and non-null mutators must specify how it rejects mixed OPTIONAL_LINK values
and preserves direct-field/type phase ordering. A direct DSL wrapper is a current consumer model
choice under A, not a new Klum container API. No evidence demonstrates a container requirement
that overrides these compatibility costs. The maintainer selected A for this release; B–D remain
unimplemented alternatives, with any future support subject to a separate accepted design.

## Validation and local delivery

The executable tree was validated before its local commit `cc615b12` and remained unchanged
after that run; the later evidence/ADR/plan update changes Markdown only. Final explicit filtered
verification passed in 28 seconds: G4 reran, G3/G5 reused their matching passing results.

| Check | Result |
| --- | --- |
| Focused Groovy 3 / 4 / 5 | 125 cases each (118 source/traversal + seven artifact cases); zero failures/errors/skips |
| Java 17 public contract and negative nested-generic read | Passed as controls within each Groovy lane |
| Six binary-added container creator/mutator declarations | Correct runtime rejection in every lane |
| Test license and test-lane isolation | Passed |
| Local Standards / Specification self-review | No actionable findings; no independent sub-agent review claimed |
| Relative Markdown file links and diff checks | Passed |
| Local history | Two reasoned steps: executable characterization, then recommendation/ADR clarification; no history rewrite |

Exact final command (each lane is explicitly filtered):

```shell
./gradlew :klum-ast:test \
  --tests com.blackbuild.klum.ast.LifecycleParticipantContainerProbeTest \
  --tests '*LifecycleParticipantConsumerTest.qualifies existing public container*' \
  :klum-ast:groovy4Tests \
  --tests com.blackbuild.klum.ast.LifecycleParticipantContainerProbeTest \
  --tests '*LifecycleParticipantConsumerTest.qualifies existing public container*' \
  :klum-ast:groovy5Tests \
  --tests com.blackbuild.klum.ast.LifecycleParticipantContainerProbeTest \
  --tests '*LifecycleParticipantConsumerTest.qualifies existing public container*' \
  :klum-ast:licenseTest :klum-ast:verifyTestLaneIsolation --no-parallel
```

An initial lane-isolation invocation pulled in an unfiltered G4 module run after focused G5
completed. That follow-on was interrupted and is not qualification evidence. The command above
replaced it with explicitly filtered tasks and the XML totals were checked again before commit.
No broad release qualification is requested or claimed: production sources and generated contracts
are unchanged. No push, PR or issue mutation is performed. The later maintainer decision is
recorded above;
the worker did not select the support-or-reject outcome.

Fixture discovery corrected invalid hypotheses/setup rather than implementation: Map-only keyMapping,
OWNER read timing, fresh self-LINK attachment, existing Set FromMap type requirements, sequential
Schema/Java compilation and explicit dynamic linkage to the separately compiled Java inspector.
Java parameterized-getter failure is retained as evidence rather than worked around and claimed
as full generic support. No ignored/pending test or production adaptation is added.

## LP-8 observations and remaining gates

Carry these non-blocking documentation observations to the Hive; its future checklist is untouched:

- Distinguish rejected annotations on List/Set/Map fields from type participants reached through
  ordinary composed containers; type lookup does not inspect the incoming field annotation.
- Null/empty/null-entry traversal and one call per identity do not establish per-element creation
  or occurrence semantics. No index/key context or stable Set position is supplied by this SPI.
- Late children receive the current phase only; a PostTree-created entry may have retained
  declaration authority while its @Owner field remains null. Paths cannot supply missing authority.
- Whole-feature typing claims should account for raw nested Builder container projection; ordinary
  Java and static Groovy use works, while parameterized nested wildcard assignments do not.
- Preserve FromMap versus named With(Map) distinctions and typed Set input. Neither is a new
  participant import route. No new documentation-only issue is proposed.
- Retain LP-6 helper deferral and LP-4 HANDLE deferral; neither solves container typing or traversal.

LP-7 investigation and the maintainer support-or-reject release decision are complete: retain rejection.
LP-8 still owns whole-feature errors, existing validation example, Java/Groovy/binary/JVM/JPMS
qualification, final API inventory/names, user guidance, migration/CHANGES and release reconciliation.
LP-1–LP-5 remain delivered and LP-6/HANDLE evidence-deferred. #867 remains open and untargeted.
Tracker impact: related issue #867. The LP-7 decision gate is satisfied; the Hive owns reconciliation
of that acceptance fact and selects any curation treatment before publication. No curation file,
tracker state, milestone or release placement is changed by this worker.

The acceptance follow-up changes only ADR/plan/evidence Markdown. Diff and relative-link checks
apply; the Groovy lanes are not repeated for prose-only decision recording. The existing focused
125-case G3/G4/G5 evidence remains applicable. Local delivery now comprises the two investigation
commits plus one additive acceptance commit; prior commits are preserved. Hive handoff and delivery
reconciliation remain pending, with no push or PR authorized.
