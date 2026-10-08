# #856 RM-1: ordinary owning-declaration and consumer tracer

Date: 2026-10-08

Status: Implementation and focused acceptance complete; final compatibility/review/delivery evidence being reconciled

Authority: [#856](https://github.com/klum-dsl/klum-ast/issues/856),
[ADR 0027](../adr/0027-owning-relationship-metadata.md), its
[implementation plan](adr-0027-owning-relationship-metadata.md), and the separately authorized RM-1 assignment.

Base: current master `b57a0259999aa24620f6c2c707caabea28a631d0`.
Branch: `codex/issue-856-rm1-structure`; dedicated managed worktree.
RM-0 authority remains locally retained on `codex/issue-856-rm0-proof` at
`6c7d2efab4b10bbc6ee92d3932c8731a6c46cd5a`: its report and 33 characterization cases were read before implementation.
They are not cherry-picked into this slice, particularly the baseline copied-container and serialized-form assertions
that deliberately describe old behavior.

## Approved contract and delivered behavior

D1's exact descriptors/equality/errors, D4 placement in 4.1, and D6's read lifetime are approved. The delivered signatures
are in `com.blackbuild.klum.ast.runtime`:

```java
public static <T> KlumBuilderSupport<T> of(KlumBuilder<T> builder);
public KlumBuilderSupport.Structure<T> getStructure();
// Existing KlumObjectSupport<T>.of(T) and Structure<T> descriptors are preserved.
// Both Structure<T> views:
public Optional<KlumSchemaRelationship> getOwningRelationship();
public <A extends Annotation> Optional<A> getOwningRelationshipAnnotation(Class<A> annotationType);
// Final framework-created KlumSchemaRelationship; no public constructor:
public Class<?> getDeclaringClass();
public String getName();
public <A extends Annotation> Optional<A> getAnnotation(Class<A> annotationType);
```

Accepted composition attachment captures the actual Schema Field's declaring Class/name, including inherited fields.
The capture installs with the accepted claim and changes with the existing permitted self-OPTIONAL_LINK transfer.
Rejected claims leave the previous record intact. A typed internal record contains only Class/name and transfers through
existing ModelState into the ordinary Model companion before completed validation. No owner, Builder, session, path,
Field, Optional, annotation proxy or public facade is retained as metadata. No compiler or generated-linkage hook changes
are needed: the existing companion creation/materialization descriptors suffice.

Completed Structure reads that retained record without Owner values, path inference, or field-value accessibility.
Each Builder query rechecks current-thread active same-session membership and the current action's numeric phase >15.
There is no mutability preflight, sealed rejection, or upper phase bound. Normally sealed Builders retain their accepted
record; completed LINK wrappers read the target's original ordinary companion record. Acquiring or previously reading
a view never grants future eligibility. Detached descriptors use Class identity/name equality and direct annotation
lookup, and remain usable outside the Builder lifecycle. There is no cache or class-name-only identity table.

Eligible root/unattached/absent-record and missing-annotation results are Optional.empty. Invalid live state throws
KlumModelException before absence or resolution. Explicit unresolved records throw KlumSchemaException with Schema/member
context. Null receiver/annotation arguments throw named NullPointerException. The completed facade's null receiver changes
from KlumException to NullPointerException("object") under approved D1; other completed-object/marked-Template gates remain.

## Executable acceptance

All new/meaningful coverage carries `@Issue('856')`, uses the `Test` suffix, and has no ignored or pending cases.

| Fixture | Cases | Evidence |
| --- | ---: | --- |
| OwningSchemaRelationshipTest | 7 | Direct/inherited declaration, absent root/annotation, no/several/transitive/converted Owner values, private-field annotation, descriptor equality and classloader distinction, named null errors and explicit corrupt-record failure |
| BuilderRelationshipLifetimeTest | 8 | Early-acquired views, both queries on roots/owned/unattached/unannotated receivers at 1/10/14/15 and 16/20/25/30/41/50/80/100, ordered before/after phase-40 actions, post-phase closure, completion/abort, foreign thread/session, sealed completed LINK wrappers/original root absence, Template rejection, and late attachment at 16 |
| OwningRelationshipTracerTest | 7 | Six consumer-owned Source/default/explicit-override scenarios with dynamic/static Writers and inherited callback; separately compiled Java annotations/base Schemas; Java17 and static Groovy generic/Optional consumer execution and zero-operation marker |

The provider Environment is independently completed before consumer construction. Its Facts have no Owner backreference.
The consuming relationship and AUTO_LINK callback are inherited; the owning Application field is also inherited without
redeclaration. Policy is test-local, reads Binding/Source/default annotations through public facades and calls the existing
typed Facts method. Assertions verify exact target identity, original provider declaration, unchanged single PostTree run,
no linked-target revalidation, and explicit override preservation. No binding transport field, framework annotation or
production provider-selection resolver is added.

The tracer recompiles leaf Schemas/Writers with a fresh GroovyClassLoader against emitted base/annotation class files.
Static Writers also exercise a statically compiled inherited AUTO_LINK callback. Java annotations and the Java consumer
use `--release 17`; both facade generic shapes and typed Optional results are compiled and executed.

One tracer method is documentary (`@Tag('documentary')`, `@See` the user page). Its abbreviated guidance lives in
[Completed Object Support](../user/Completed-Object-Support.md#owning-schema-declarations), with migration guidance and
4.1 CHANGES. Existing navigation already reaches that page; no new page/Sidebar entry is required. The parent issue should
link `OwningRelationshipTracerTest#'inherited AUTO_LINK selects a completed provider through consumer-owned annotations'`
and that documentation section during Hive reconciliation.

Acceptance mapping: A01/A02/A03/A05/A17/A25, initial classpath/binary A20, core A10/A11/A26 and normalized late attachment
A19 have public-API evidence. A06's ordinary/external completed LINK identity path is covered. This does not complete
extended graph/copy/Template/persistence/import or the full RM-3 named-module/binary matrix.

## Representation audit and later gates

Java17 ObjectStreamClass audit of the new production classes:

| Type | UID | Serialized fields |
| --- | ---: | --- |
| KlumModelProxy | -1472569276039395355 (computed) | breadcrumbPath, executedValidators, metadata, model, modelPath, owningRelationship |
| InternalKlumBuilder.ModelState | 2464886360491951900 (computed) | breadcrumbPath, metadata, modelPath, owningRelationship |
| SchemaRelationshipDeclaration | 1 (new declared UID) | declaringClass, name |

The first two computed UIDs change from RM-0; no old UID is pinned. Missing-record Optional semantics apply only when an
object/stream is otherwise readable. No historical stream loading or cross-version compatibility is promised. The
Template companion is unchanged. Full graph persistence, linked cycles, older fixtures and recipient recapture remain
RM-2 under D2/D3/D5, not proved by this record audit or incidental existing serialization tests.

Explicit later gates remain: D2 Template definition/recipient retention; D3 serialization scope; D5 copied-container
capture, conflicting aliases and OPTIONAL_LINK copy repair; RM-2 extended containers/cycles/imports; RM-3 full binary/JPMS
qualification; RM-4 complete release guidance, CI/Sonar and release acceptance. CopyHandler, Template state/recipes,
serialization policy, LinkTo/LinkSource algorithms and generated public contracts are unchanged.

RM-0 noted tail ApplyLater actions execute directly without updating PhaseDriver.currentPhase. RM-1 uses the approved
existing current-action source, including its retained phase in post-phase closures; it does not repair the scheduler or
invent a new effective-phase definition. This is a later scheduler concern, not new traversal/late mutation authority.

## Validation and review

Focused red-to-green runs reproduced the absent completed query, approved null exception change, and absent Builder
facade before the corresponding implementations. All 22 focused Groovy3 cases passed after implementation.
Full affected-module/repository lane and final review results are recorded below when reconciliation finishes.

The first broad root check ran while documentation was edited. Its outer clean version and nested `.uncommitted` version
diverged, causing publication-fixture missing-coordinate failures. This run is not claimed green and will be rerun at a
stable clean commit. An independent Gradle plugin cache-outcome assertion also needs a stable rerun/baseline comparison.

## Delivery and tracker impact

RM-1 is a partial slice of #856; the parent remains open. Proposed PR relationship: `Related: #856`, with no closing
keyword. No milestone, label, closure, curation index, or later-gate approval is changed by this worker.
A draft PR is authorized only after implementation/review settle; no ready-for-review or merge action is authorized.
Hive owns final delivery/archive reconciliation and the parent issue's acceptance links.
