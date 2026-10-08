# #856 D5: authoritative copied metadata qualification

Date: 2026-10-08

Status: Revised D5 matrix and public consumer regression implemented without runtime changes; validation/delivery recorded below.
RM-4 final acceptance and parent issue reconciliation remain pending.

Authority: the [revised D5 maintainer decision](https://github.com/klum-dsl/klum-ast/issues/856#issuecomment-6067212507)
supersedes the [initial rejection rule](https://github.com/klum-dsl/klum-ast/issues/856#issuecomment-6066757844).
[ADR 0027](../adr/0027-owning-relationship-metadata.md) and its
[implementation plan](adr-0027-owning-relationship-metadata.md) now reflect the revision.

Base: current master `276c749c328d28f1d7d9e6432b6d7655133890b6`, after merged RM-1/#861, RM-2/#862 and RM-3/#863.
Master was refreshed at resume and had no newer commit. Branch: `codex/issue-856-d5-recipient-declarations`.
Worktree: `/Users/stephan/.codex/worktrees/db1a/klum-ast`.

## Preserved compatibility baseline and superseded stop gate

Local commit `0fd554da` established that this generated public operation succeeds:

```groovy
@DSL class Node { String value }
@DSL class Recipient {
    List<Node> first
    @Field(keyMapping = { it.value }) Map<String, Node> second
}
def sharedRecipe = [value: 'shared']
def result = Recipient.Create.With {
    copyFrom([first: [sharedRecipe], second: [shared: sharedRecipe]])
}
assert result.first[0].value == 'shared'
assert result.first[0].is(result.second.shared)
assert KlumObjectSupport.of(result.first[0]).structure.owningRelationship.empty
assert KlumObjectSupport.of(result.second.shared).structure.owningRelationship.empty
```

| Position | Source identity | Result identity | Semantics / authority |
| --- | --- | --- | --- |
| Donor `first[0]` / `second.shared` | Same Map recipe S | Data input | Value-only recipe |
| Recipient `first[0]` | S | Fresh Node R | Ordinary composition occurrence, no authoritative claim |
| Recipient `second.shared` | S | Exact same Node R | Ordinary composition occurrence, no authoritative claim |

The first characterization reached the initial explicit stop gate and was reported to Hive. The maintainer then accepted
this graph as a compatibility baseline: preserve success and alias identity, expose Optional.empty() rather than inventing
ownership or rejecting the graph. The first executable feature remains unchanged. Empty metadata does not identify a root.
The initial focused G3/G4/G5 result was one passing case per lane. The original evidence remains in `0fd554da`; it does
not describe the current approval/status.

## Bounded executable matrix

`CopyCompositionCharacterizationTest` has 45 cases including the preserved baseline. All observations use generated
construction/copy/relationship operations, resulting identity, public support queries and Schema lifecycle events. The
consumer fixture has two cases. Both carry class-level `@Issue('856')`; the documentary fixture carries `@Tag('documentary')`
and `@See`. No new skipped or pending case is introduced.

| Route / cases | Result identity and existing authority | Lifecycle / insertion behavior | Metadata result / safe improvement |
| --- | --- | --- | --- |
| Model, Template, Map and same-session Builder whole-object donors × List/Set/Map (12) | Fresh recipient children, distinct from donor entries; source claims not transferred; direct insertion has no claim | Each copied child runs POST_TREE once; deferred Template/Builder recipe replay runs once; normal containers/order retained | Empty in both live and completed views; unique location does not authorize a new claim |
| Collection ADD/REPLACE/ALWAYS_REPLACE/SET_IF_EMPTY × List/Set (8) | ADD retains claimed existing child and adds fresh claimless child; replacement creates fresh claimless child; populated SET_IF_EMPTY retains original | Expected value ordering and retention/replacement preserved | Existing retained child's declaration remains; new insertion empty |
| Map FULL_REPLACE/ALWAYS_REPLACE/SET_IF_EMPTY/MERGE_KEYS/MERGE_VALUES/ADD_MISSING (6) | MERGE_VALUES keeps existing Builder identity/claim; ADD_MISSING and populated SET_IF_EMPTY keep original; replacement/new keys are fresh | Key order, merge values and retained values unchanged | Existing accepted `indexed` claim retained; directly inserted values empty |
| Repeated Map/Model/Template recipe within List and across List/Set/Map (3) | One copy operation reuses one fresh child; separate copy operations produce independent children | Shared child visited once per graph; Template replay once per resulting child | Empty; no arbitrary field/index/key declaration |
| Single accepted claim plus copied List alias, either donor field order (2) | Same resulting child; single adoption establishes authoritative `direct` claim | Alias graph and one POST_TREE visit preserved | `direct` from both occurrences; independent of donor order because a real claim exists |
| Two single composition fields using one recipe (1) | Existing normal adoption rejects the second claim | Existing rejection preserved | No new rejection policy |
| Fresh / already-claimed same-session Builder child donors (2) | Fresh copied child distinct from live donor; repeated copied List entries alias; donor's original claim remains unchanged | Deferred actions replay on recipient | Copied container child empty; donor returns its existing claim or absence |
| Completed LINK copy plus normal OPTIONAL_LINK List/Map mixed fresh/claimed/completed/repeated attachment (1) | LINK keeps exact completed identity; OPTIONAL_LINK adopts fresh entry and aggregates claimed/completed entries | Existing per-entry behavior unchanged | Owned OPTIONAL_LINK declaration or original target declaration |
| OPTIONAL_LINK container copies of fresh/claimed/completed inputs (1) | Inputs are rehydrated as recipes, not normalized aggregation targets; copied Builders have no claim | Existing resulting null entries retained; no composition is introduced to materialize them | Live copied Builder metadata empty; no completed facade call on null entries |
| Empty SET_IF_EMPTY List/Map (2) | Fresh claimless insertion | Both insert into empty containers | Empty |
| OPTIONAL_LINK copied aliases with one existing recipient single claim and an unclaimed recipe (1) | Claimed aliases materialize as exact single recipient; unclaimed entries remain null | Existing POST_TREE observes both recipes, including the unclaimed one; materialization/traversal are not equated | Existing `direct` claim retained; no invented OPTIONAL_LINK claim |
| Single MERGE/REPLACE/ALWAYS_REPLACE/SET_IF_NULL (4) | MERGE and SET_IF_NULL retain existing identity; replacement adopts fresh child | Existing overwrite behavior preserved | Accepted recipient `direct` declaration in each case |
| Generated LINK fresh rejection versus claimed Builder aggregation (1) | Fresh Builder rejected; already claimed Builder preserved exactly at repeated entries | Normal relationship-input policy unchanged | Original `direct` declaration |
| Preserved Map recipe List/Map alias baseline (1) | One resulting child in two fields, no claim | Copy succeeds | Empty, as explicitly approved |
| Inherited AUTO_LINK container-provider consumer, with / without authoritative candidates (2) | Normal annotated provider List/Map entries have exact inherited declaration; copied providers have no authority | Consumer skips absent annotations explicitly and calls existing typed LINK operation only for its selected target | Exact selected completed target and original declaration; all-absent case leaves relationship unset |

Existing `CopySourceProtocolSpec`, Template/copy, OPTIONAL_LINK, composition, serialization, lifetime and RM-3 artifact
consumer regressions qualify preserved session gates/public Template rejection/D6/generics/JPMS beyond this bounded matrix.
They are selected in the final regression run, rather than represented as newly added D5 cases.

## Why no runtime correction is safe or needed

`CopyHandler` keeps one per-copy IdentityHashMap. It reuses resulting Builders for repeated recipe identities and inserts
container values directly, while single replacement goes through existing accepted relationship normalization. Nested
Map merge modifies the existing child and preserves its claim. The immutable public metadata already reports exactly
those accepted claims, independently of other graph occurrences.

Direct container insertion has no authoritative adoption boundary, even when a particular fixture has only one location.
Calling normalization or claim machinery there would introduce ownership, reject accepted aliases or change OPTIONAL_LINK
traversal/materialization. Traversal/Map order, Owner values and paths cannot establish authority. Revised D5 requires
truthful absence, so no production hook, cloning, new claim or metadata payload is added. No red-to-green runtime repair
was needed: the revised contract passes on the unchanged runtime. Fixture setup errors were corrected in tests only.

Normal OPTIONAL_LINK aggregation and OPTIONAL_LINK recipe copying are different existing routes. Completed aggregation
retains identity/declaration. CopyHandler currently rehydrates OPTIONAL_LINK copy inputs; unclaimed container entries can
materialize as null. This qualification preserves that behavior without recommending it as a new authoring pattern or
claiming it is repaired. General CopyHandler/ownership cleanup remains outside #856's metadata seam.

The public example is `CopyOwnershipConsumerDocumentaryTest#'inherited AUTO_LINK skips providers without authoritative declarations and preserves selected identity'`,
linked to [Completed Object Support](../user/Completed-Object-Support.md#copied-providers-and-absent-authority).
Selection and absent-provider fallback belong entirely to consumer policy. No ScHelm annotation or LinkTo selection rule
is introduced. ADR/plan, Templates, migration and CHANGES describe this precise compatibility boundary.

## Validation, review and delivery

Focused final matrix/consumer run: **47 cases per G3/G4/G5 lane, zero failures/errors/skips**, with Java 17.0.3,
Gradle 8.14.4, Groovy 3.0.25 / 4.0.32 / 5.0.6 and matching Spock 2.4 lanes. The fixture uses integer replay counts to
assert one deferred replay on each resulting recipient. Test filters were supplied explicitly to each lane task:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 \
  :klum-ast:test --tests '*CopyCompositionCharacterizationTest' --tests '*CopyOwnershipConsumerDocumentaryTest' \
  :klum-ast:groovy4Tests --tests '*CopyCompositionCharacterizationTest' --tests '*CopyOwnershipConsumerDocumentaryTest' \
  :klum-ast:groovy5Tests --tests '*CopyCompositionCharacterizationTest' --tests '*CopyOwnershipConsumerDocumentaryTest' \
  --console=plain
```

Repository checks and parallel Standards/Spec review are recorded before draft delivery. Tracker impact: **Related: #856**; no issue closure,
release targeting or curation changes. RM-4 final acceptance remains pending. No production, generated/public API,
serialization representation, lifecycle, module descriptor or annotation artifact is changed. The existing RM-3 binary/JPMS
and same-version persistence contract remains intact; no historical compatibility promise follows.
