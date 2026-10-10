# #867 LP-3: four-phase direct-field dispatch

Date: 2026-10-10. Base: merged LP-2 PR #872, `e0617b73987aa8ae20e0d78f728ad3a6c659c30a`.
Authority: explicit maintainer LP-3 delegation. Related: #867; issue and release placement unchanged.

## Exact phase slots and contract

Direct retained DSL Schema fields now execute external creators and mutators in all four supported phases.
Discovery remains phase-independent so malformed binary declarations reach validation; invocation selects only markers matching that phase. Every creator precedes every mutation at one field slot;
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
A binary-replacement control also rejects an annotation library changed to unsupported Validate after Schema compilation.
The prior unsupported PostTree source control now rejects Validate, which remains outside the four-phase set.

Test-first record: the first four-phase tracer passed AutoLink and failed the three newly supported phases
with the original unsupported-phase diagnostic. The initial integration made all four pass. A further built-in
construction-only Default regression failed after integration and drove preservation of its Builder-only path.
Independent Specification review found that initialized construction-only AutoCreate Closures must bypass
external Schema lookup, and that phase-specific discovery could silently skip unsupported binary phase declarations.
Both were reproduced as failing factory/artifact controls, then repaired by guarding external AutoCreate dispatch
and retaining phase-independent discovery with phase-selected invocation. Standards review's two clarity observations
were addressed by renaming the Default field step and repairing a prose fragment. Reviewed commits remain intact.
Existing cluster/traversal/precedence behavior is guarded by regressions through the same factory seam.

## Qualification and delivery

Stable `./gradlew check --no-parallel` passed at executable head `42089c56` in 9m 41s, including
license checks, test-lane isolation, artifact consumers and all repository modules. Git/source state stayed
unchanged throughout this run. The earlier interrupted full check is not acceptance evidence.

| Check | Result |
| --- | --- |
| Compiler Groovy 3 / 4 / 5 | 1,689 tests each; zero failures/errors, 15 unchanged skips each |
| Runtime Groovy 3 / 4 / 5 | 73 tests each; zero failures/errors, one unchanged skip each |
| Jackson Groovy 3 / 4 / 5 | 77 / 75 / 75 tests; zero failures/errors/skips |
| Bean validation Groovy 3 / 4 / 5 | 10 tests each; zero failures/errors/skips |
| Gradle plugin | 151 tests; zero failures/errors/skips |
| Published test support baseline / G4 / G5 | 7 / 6 / 6 tests; zero failures/errors/skips |
| Standards review and additive re-review | Zero remaining findings |
| Specification review and additive re-review | Both original findings reproduced and addressed; zero remaining findings |
| Commit-history, relative links and diff checks | Passed; focused reasoning steps and reviewed history preserved |

No ignored/pending test or suppression was added. Source and doc names remain provisional. This evidence-only
follow-up changes no executable inputs; its applicable local verification is documentation/diff checking.
Exact final-head remote CI and SonarCloud results are recorded in the draft PR and final handoff after publication.

Tracker impact: Related #867, no closing keyword and no issue-state/label/milestone or curation update.
Draft publication is explicitly requested. Git SSH and gh CLI repository capabilities were independently verified authorized; no GitHub App delivery channel is used. Hive owns delivery/release reconciliation and archival decisions.

## Remaining gates

LP-4 sealed FAIL/SKIP and graph/Template/import compatibility; optional type mutation (LP-5), Closure helpers
(LP-6), sealed HANDLE and collection/map support (LP-7); LP-8 full diagnostics, existing validation, JVM/JPMS,
final API names and release qualification. A container support-or-reject decision remains mandatory before
release. No ScHelm policy, provider selection, general extension machinery, or ownership/session/materialization
redesign is delivered or authorized by this slice.

## Remote review follow-up

Published head `64ea7b2bac66cf85c71e7ca1da84f63c55d038ed` passed
[CI run 38035136101](https://github.com/klum-dsl/klum-ast/actions/runs/38035136101), JUnit Test Report,
SonarCloud and SonarCloud Code Analysis. The matching
[PR analysis](https://sonarcloud.io/dashboard?id=klum-dsl_klum-ast&pullRequest=873), 2026-10-10 07:54:52 UTC,
reported quality gate OK, zero bugs/vulnerabilities/security hotspots and three maintainability findings:
java:S3776 (declaration validation complexity 20 versus 15), java:S1612 (method reference), and
groovydre:S8306 (test if braces). An additive follow-up extracts per-annotation declaration checking,
uses the method reference and braces the test control. No suppression or behavior/API change is introduced.
The same follow-up synchronizes a stale current public-inventory row and handler ordering Javadoc with
LP-2/LP-3's delivered contract. Those wording corrections were independently reviewed.
Final follow-up qualification and exact-head remote results are recorded in PR #873 and the handoff.
