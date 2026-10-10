# #867 LP-5: mutating Schema-type participants

Date: 2026-10-10. Base: current origin/master through merged LP-4 PR #875,
`f8ef1d4a2279d3f4f7ba36b93d1f65888a2943ff`.
Authority: explicit maintainer LP-5 implementation-or-evidence-deferral delegation. Related: #867;
issue state, curation and conditional release placement remain unchanged.

## Feasibility disposition and delivered boundary

**Implement: existing traversal supports a localized compatible extension.** Each of the four existing
Builder visitors invokes type mutation at its start; AutoCreate does so inside its existing Template scope.
CompositionTraversal still visits the parent before collecting/descent of children. Parent-field dispatch
therefore precedes child type mutation, followed by that child's fields, clusters, defaults, methods and
Closures. Current sealed/non-Builder skips remain authoritative. There is no second phase action, traversal,
path/ownership reconstruction, creating participant on types or earlier-phase replay.

The existing mutation handler/context boundary is retained. Additive default
`LifecycleMutationContext.isType(): boolean` returns false for existing field contexts and true for type
invocation. Target is the visited Builder. Containing Builder/incoming name come directly from traversal
and are null at root. `getDeclaredType()` returns the concrete Schema type for type invocation; field
invocation still returns the original field's declared type. `getFieldType()` reads the incoming original
Schema field when available and is null without one. Typed singular lookup queries the Schema Class for
type invocation, never the incoming relationship; original field lookup and actual containing subtype
receivers are unchanged. No owning-relationship authority is required.

Public generated Builder contracts, KlumBuilder's zero-operation boundary, module exports/dependency direction,
sessions, ownership, materialization and serialization implementations are unchanged. Handlers are fresh per
invocation. Invocation errors preserve their cause and identify annotation, handler, phase and Schema;
member diagnostic context is cleared for the type call and restored in finally. Declaration checks share
existing exact generic/constructor/phase validation, with source and runtime rejection of type creators.

## Inheritance and ordering evidence

Discovery is `Class.getAnnotations()`; singular lookup is `Class.getAnnotation()`. Java @Inherited applies:
same-type subclass override, unmarked superclass and interface non-propagation. No annotation sorting,
priorities, ordering SPI or plural lookup is introduced. Distinct type annotation order is unspecified.
LP-2's repeated meta-mutator/container value order remains intact.

The artifact tracer uses an inherited repeatable domain annotation and an inherited container without a participant marker:

| Schema placement | Singular lookup and execution |
| --- | --- |
| Base singular; unannotated subclass | Base annotation is inherited and dispatches |
| Subclass singular | Overrides the same annotation on base |
| Subclass repeated uses with base singular | Subclass container coexists with inherited base singular; only singular dispatches |
| Repeated uses without base singular | Only unmarked container is present; no singular annotation or implicit expansion/dispatch |
| Unannotated subclass of repeated base | Inherits the container, with no singular expansion or dispatch |
| Subclass repeats after repeated base | Local container replaces inherited container, with no singular expansion or dispatch |
| Annotation only on implemented interface | No propagation or dispatch |

A container may participate only by declaring its own mutator/handler for its own annotation type; no implicit
per-entry processing is provided or claimed. This characterization deliberately separates domain repetition
from LP-2's repeatable meta-markers and does not invent inheritance/ordering semantics.

## Executable evidence

All new executable coverage carries @Issue('867').

- `LifecycleParticipantTypeTest`: documentary four-phase parent-field → child-type → own-field → method →
  Closure ordering, both supplied and parent-created children; root null context; Schema-only lookup versus
  incoming-field annotation; inherited declaration and actual containing subtype. Its @See links to
  [user guidance](../user/Model-Phases.md#type-mutation-lp-5).
- The same class qualifies same-type override, unmarked/interface non-propagation and independent inherited
  annotation effects without asserting their relative order; type mutation before AutoCreate clusters;
  fresh handlers in recipient Template and FromMap sessions with ordinary Owner assignment; sealed LINK
  identity/skip in all four phases; type error causes and subsequent session usability; type creator,
  unsupported phase and mismatched handler source diagnostics.
- `LifecycleParticipantConsumerTest`: Java/Groovy authored annotation-library JARs, separate Domain and
  Schema JARs, Java plus statically/dynamically compiled Groovy consumers. Sources/classes are removed
  before runtime and compiler artifacts are excluded. Probes characterize repeated domain containers and
  repeated meta-mutators, root behavior, inheritance and field-before-type ordering. Binary replacement
  controls reject unsupported phases, mismatched handler annotation parameters and added type creators.
- Existing LP-1–LP-4 field, sealed, route, Jackson, traversal and generated-contract suites remain regressions.

Test-first record: the initial 13 type scenarios failed against master with unsupported type placement or
missing discriminator. Localized implementation enabled all four phase visits and inheritance. Subsequent
probe fixes repaired only fixture loader, Template syntax, captured LINK variable and nested-type imports.
No compatibility conflict requiring deferral was found.

## Qualification and remaining gates

Focused Groovy 3 participant coverage passed: 127 tests, zero failures/errors/skips. Focused Groovy 4/5
participant runs also each passed 127 tests. After review follow-up, the updated type/artifact-consumer Groovy 3
controls passed, followed by stable `./gradlew check --no-parallel` at executable head
`b93dc180a157a948f9b3a6f757d030dde77adba7` in 11m 46s. Source and Git state remained unchanged during
that full run. This qualification-only follow-up changes no executable inputs.

| Check | Result |
| --- | --- |
| Compiler Groovy 3 / 4 / 5 | 1,741 tests each; zero failures/errors; 15 unchanged skips each |
| Runtime Groovy 3 / 4 / 5 | 73 tests each; zero failures/errors; one unchanged skip each |
| Jackson Groovy 3 / 4 / 5 | 81 / 79 / 79 tests; zero failures/errors/skips |
| Bean validation Groovy 3 / 4 / 5 | 10 tests each; zero failures/errors/skips |
| Annotations / Gradle plugin | 20 / 151 tests; zero failures/errors/skips |
| Published test support baseline / G4 / G5 | 7 / 6 / 6 tests; zero failures/errors/skips |
| Repository license, test-lane isolation, documentation renderer | Passed |
| Independent Standards / Specification review and additive re-review | Clear on both axes |
| Commit-history, relative links and diff checks | Passed; 57 relative Markdown file links resolve |

Initial Standards review found one Spock assertion-layout violation; initial Specification review requested
inherited-container evidence. The additive follow-up separates creation/assertions, qualifies inherited and
local-override containers in both annotation-author lanes, and clarifies sealed type dispatch in Javadoc.
The optional internal validator rename is left unchanged to keep existing field-check naming stable; its
class description now covers fields and types. Reviewed commits remain intact. No ignored/pending test
or broad suppression is introduced. The localized unchecked cast suppression in type mutation documents
that runtime declaration validation precedes the generic invocation. The three-commit implementation/documentation/review sequence was inspected
against the accepted slice; this final evidence commit is documentation-only.

Git SSH and gh CLI repository delivery capabilities were independently verified authorized before draft
publication; no GitHub App channel is used. Exact final-head remote CI/SonarCloud outcomes will be recorded
in the draft PR and handoff, separately from the local results above. Hive owns final delivery reconciliation.

LP-6 Closure evaluation remains optional/unqualified. LP-7 support is optional but support-or-reject with
diagnostics remains mandatory before release. LP-8 full errors/existing-validation guidance/JVM/JPMS/final
API names and feature/release qualification remain pending. HANDLE remains evidence-deferred under LP-4.
This slice adds no Closure execution, containers/maps contract, validation framework or ScHelm policy.

Tracker impact is Related #867; no closing keyword, issue-state/label/milestone or curation mutation.
Draft publication is explicitly requested after local qualification. Hive owns delivery/release/archive
reconciliation; the worker requests reconciliation and retains its worktree while delivery remains open.
