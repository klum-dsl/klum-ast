# #867 LP-3: four-phase direct-field dispatch

Date: 2026-10-10. Base: merged LP-2 PR #872, `e0617b73987aa8ae20e0d78f728ad3a6c659c30a`.
Authority: explicit maintainer LP-3 delegation. Related: #867; issue and release placement unchanged.

## Exact phase slots and contract

Direct retained DSL Schema fields now execute external creators and mutators in all four supported phases.
Runtime selects only markers matching that phase. Every creator precedes every mutation at one field slot;
existing targets skip creation, and null creation skips mutation. Checked assignment, public handler/context
interfaces and LP-2's ordered-container contract are unchanged. No new public API is introduced.

| Phase | Insertion and existing built-in sequence |
| --- | --- |
| AutoCreate | Extend the existing ClusterModel direct-property stream, retaining its order. For each eligible field: built-in/external creation then external mutations. Finish all direct fields, run cluster AutoCreate, then lifecycle methods and Closure callbacks. |
| AutoLink | Retain the existing ClusterModel-to-HashMap field enumeration, including LinkTo's unset test. Built-in/external creation then mutations, followed by lifecycle methods and Closures. |
| Default | Retain owner-provided defaults → containing-field DefaultValues → type DefaultValues → direct-field HashMap slot. Extend that last slot with external creation then mutation, followed by lifecycle methods and Closures. Built-in construction-only scalar defaults remain on Builder declarations. |
| PostTree | Previously callback-only: enumerate eligible fields through the existing ClusterModel property stream immediately before the original lifecycle methods and Closures. |

PostTree's precise insertion point refines the plan's previously unqualified field slot. It is part of the
same containing-Builder visit, without another whole-tree phase action. ClusterModel's stream overload becomes
accessible to sibling runtime-internal phase classes; it is not an exported client extension API.

Each field has at most one direct creator per phase, including built-in AutoCreate, LinkTo (AutoLink),
and direct Default. Compile-time and binary declaration defenses count claims per phase. Creators in different
phases coexist. Repeatable mutation order within one annotation and explicit-container array order remain
qualified; singular/container mixtures and different domain annotation order remain unspecified. No sorting,
priority mechanism, type dispatch or cross-field order guarantee is added.

Cluster AutoCreate sees resulting direct-field state, preserves supplied/existing/mutated targets and fills
remaining null fields, including null creator returns. It does not rerun direct-field participants. A mutator
therefore does not run on a target filled only by the later cluster step in that AutoCreate visit.

The existing composition traversal collects children after the containing visit. Parent-field mutation can
create grandchildren on an existing child, and a creator can supply the annotated child itself. Child field
participants, methods and Closures observe that work in the current phase. Earlier phases do not replay for
late-created children. Ownership/session/path and traversal mechanics are unchanged.

## Executable evidence

`LifecycleParticipantPhaseTest`, class-level @Issue('867'), uses root factories and observable Model values:

- Documentary `creates then mutates direct fields before callbacks in #phase`: all four phases, supplied,
  existing and null targets; two deliberately nonalphabetical mutations; methods before Closures. Its @See
  points to [user guidance](../user/Model-Phases.md#field-participants-in-four-phases-lp-3).
- Parent field work before child field/method/Closure work, all four phases × existing/created child. An
  AutoCreate mutator creates a grandchild on an existing child; AutoLink can create the annotated child.
  An early-phase marker proves that later-created grandchildren receive no AutoCreate replay.
- Cluster fallback after direct external creation/mutation and built-in creation/mutation; a null-return
  field is filled by the cluster without receiving a second direct-field mutation.
- One annotation with independent creators/mutators across all four phases; null/missing/current target
  handling proves phase selection rather than merely accepting declarations.
- Same-phase external conflicts in all four phases and built-in conflicts in AutoCreate/AutoLink/Default.
- Built-in Default creation followed by external mutation, pure built-in owner/containing/type/direct
  default precedence, construction-only scalar Default, and pure built-in LinkTo unset preservation.

`LifecycleParticipantConsumerTest` retains LP-1/LP-2 Java/static/dynamic/binary replacement probes and extends
separate Java annotation-library/Schema/consumer JAR composition to AutoCreate, Default and PostTree.
Runtime executes after source/class directories are removed and without the compiler on its classpath.
The prior unsupported PostTree source control now rejects Validate, which remains outside the four-phase set.

Test-first record: the first four-phase tracer passed AutoLink and failed the three newly supported phases
with the original unsupported-phase diagnostic. The initial integration made all four pass. A further built-in
construction-only Default regression failed after integration and drove preservation of its Builder-only path.
Existing cluster/traversal/precedence behavior is guarded by regressions through the same factory seam.

## Qualification and delivery

Focused Groovy 3 phase, composition and existing AutoCreate/AutoLink/Default/owner-default checks pass.
Artifact matrix, complete Groovy 3/4/5 checks, Standards/Specification reviews, final-head CI and SonarCloud
are recorded here or in the draft PR after qualification; no pending check is claimed successful.

Tracker impact: Related #867, no closing keyword and no issue-state/label/milestone or curation update.
Draft publication is explicitly requested. Hive owns delivery/release reconciliation and archival decisions.

## Remaining gates

LP-4 sealed FAIL/SKIP and graph/Template/import compatibility; optional type mutation (LP-5), Closure helpers
(LP-6), sealed HANDLE and collection/map support (LP-7); LP-8 full diagnostics, existing validation, JVM/JPMS,
final API names and release qualification. A container support-or-reject decision remains mandatory before
release. No ScHelm policy, provider selection, general extension machinery, or ownership/session/materialization
redesign is delivered or authorized by this slice.
