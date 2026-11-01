# S4 — zero-argument structured commands

Date: 2026-11-01. Local qualification; draft publication authorized after integration checks.

Related: [#952](https://github.com/klum-dsl/klum-ast/issues/952),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[4.2 implementation plan](input-round-plan-4-2.md).
Original stacked base: `4f68e1ec97b6a9e78e9e7eccb300dad7b55ce6d0` (S2, `codex/952-string-operation-selection`).
Worktree: `/Users/stephan/.codex/worktrees/c54e/klum-ast`.
Branch: `codex/952-zero-argument-commands`.

## Integration onto merged S2

Current base: `325cfda9d373b235caaa7590475ecde01c33fdb7`, fetched and verified as `origin/master`.
S1 #1002 and S2 #1003 are merged. The maintainer explicitly resumed S4 independently of S3a and authorized rebasing,
validation, final local review, branch push and a draft PR targeting master with **Related: #952**. Merge remains unauthorized.

The four S4 commits were replayed directly onto that exact master. Genuine overlaps were resolved by retaining S2's
extracted operation-metadata validation helper (including its explicit boolean comparison) and adding S4 role checks there;
the packaged schema test retains both S2's surviving inherited String candidates and S4's command assertions.
S2 already contains the wrong-Model fixture's explicit zero-operation count, so S4 adds no duplicate fixture change or
annotation-only tracker churn. S2's inherited-candidate correction and S1 traversal/Sonar cleanup remain intact.
No S3a code or dependency is introduced.

Rebased implementation commits: `9142a88ee`, `306ffd9b4`, `48c494d0f`, `5b8769673`.
Focused and aggregate integration validation and the repeated Standards/Specification review are pending below; the dated
stacked results are retained as historical evidence and do not substitute for the integration gate.

## Implementation boundary

Compiler admission adds COMMAND / EMPTY_OBJECT for exact public zero-argument `@Builder.Method` projections.
Every authored operation emits a required role in `GeneratedInput.Operation`, including excluded methods, retaining
S2's operation count and author-selection facts. A dual-annotated lifecycle declaration carries an explicit excluded
LIFECYCLE role; all lifecycle markers are checked regardless of their source order. The byte reader checks explicit presence, type and role/signature
agreement before use. Matching compiler/runtime/plugin and recompiled Schema hierarchies are required; development
protocol 1 remains, with no legacy decoder or mixed-toolchain promise.

The resolver preserves exclusions, names/profiles and exact inherited command overrides. A renamed or ignored override
removes the parent's command, including through descendants. Colliding command/payload candidates do not use payload
shape to select a winner. S2's String selection remains unchanged. Framework, query and lifecycle methods do not gain
admission through having zero parameters.

Runtime validates only the reached command's immediate payload: an empty Map invokes the one recorded zero-argument
public signature once, with no arguments. Null (including the caller parser's blank-YAML result), nonempty Maps, Lists
and scalars reject. Unknown names reject. S1 iteration order, first-failure and partial-state guarantees remain.
Earlier commands can execute before a later failure; later commands stay uncalled. Original causes and escaped pointers
remain. There is no implicit reset/clear behavior beyond the domain method body.

JSON Schema uses `type: object`, empty `properties` and `additionalProperties: false`. It reads the same contract without
executing command bodies or initializing Schema classes. No scopes, Templates, body-schema variant, converters, parser,
command suffix, null call marker, repeated-call carrier or generic command framework is added.

## Initial stacked acceptance and validation (historical)

- `StructuredCommandTest` covers once-only documentary/direct parity, all rejected payload kinds, normal lifecycle counts,
  active Builder partial state, unknown/helper/query/lifecycle names, original cause/escaped pointer/root cleanup,
  naming/inherited overrides and stripped/malformed/inconsistent command roles. Class-level `@Issue('952')` covers every
  feature; the documentary feature has `@Tag('documentary')` and `@See` for the current user page.
- Existing compiler/compiled-contract/literal fixtures now expect the admitted zero-argument role and retain independent
  field facts. Changed behavioral specifications carry #952.
- `CompiledConverterJsonSchemaTest` extends its packaged G3/G4/G5 parent/descendant matrix with inherited and renamed/
  ignored commands and throwing no-execution controls.
- `JpmsPackageBoundaryTest` compares structured command dispatch with direct Java and static Groovy Builder calls using
  packaged artifacts: G3 classpath; G4/G5 classpath and named modules.

Focused Groovy 3 compiler/admission/runtime/selection/literal/compiled-contract suites and the packaged JPMS classpath
consumer controls passed. The complete `CompiledConverterJsonSchemaTest` suite passed, including G3/G4/G5 producer
artifacts and no-execution commands. Command: `./gradlew :klum-ast:test --tests '*StructuredCommandTest'
--tests '*StructuredInputAdmissionPlanTest' --tests '*StructuredOperationSelectionTest' --tests '*InputLiteralTest'
--tests '*CompiledBuilderSchemaTest' --tests '*JpmsPackageBoundaryTest' :klum-ast-gradle-plugin:test
--tests '*CompiledConverterJsonSchemaTest' --console=plain --no-parallel --max-workers=2`.

Exactly two read-only local children reviewed `761bc51589a6bac957fc0053ac33f29a41e85809`: Standards reported no findings;
Specification found that a method combining `@Builder.Method` and `@PostCreate` could be called both as a callback and
as structured input. The additive correction records compiler-owned LIFECYCLE exclusion, examines every marker rather
than the first, and adds both annotation orders across PostCreate/PostApply/PostTree plus inherited-alias and packaged
schema controls. Focused command and G3/G4/G5 packaged schema controls passed after the correction. The initial full
check was stopped during the Groovy 4 AST suite to incorporate that valid finding; it does not count as a passing gate.
The same two children accepted the correction at `165daa81162a8da6f5a8794862b31071bc3f9e62`:
Standards has zero findings; Specification has no remaining findings and independently confirmed its original probe rejects.

The complete 140-task check then passed every compiler/runtime/Jackson/Bean Validation/test-support lane and 652 of 653
plugin cases. Its sole failure was the existing hand-written `Misdirected_DSL.Builder` fixture, which omitted S2's required
`operationCount=0` and therefore failed metadata completeness before its intended Model-identity assertion. That omission
is also present at the pinned S2 base. The fixture now supplies that inert fact; the original assertion is unchanged and
passed in an isolated rerun. No production behavior changed for this fixture correction. The final aggregate gate passed:

- `./gradlew check --continue --no-parallel --max-workers=2 --console=plain`: **passed**, 140 actionable tasks
  (35 executed, 105 up-to-date), 25m 50s. This includes affected baseline suites, Groovy 4/5, real binary Java/static Groovy
  and applicable JPMS consumers, full plugin/schema coverage, documentation renderer, licenses and lane isolation.
- Compiler: 2,614 G3 / 2,616 G4 / 2,616 G5 cases; runtime: 73 per lane; Jackson: 121 / 119 / 119; Bean Validation: 148 per
  lane; test support: 8 / 6 / 6. All have zero failures. Existing conditional skips remain; this slice adds none.
- Full Gradle plugin suite: **653 cases, zero failures**, including the corrected Model-identity fixture and packaged
  producer G3/G4/G5 command schema controls.
- New/changed local Markdown links and anchors: **10 checked, zero failures**. Final `git diff --check` passed.
- No local SonarCloud upload or hosted CI/consumer qualification is claimed.

Both review axes accepted final production head `165daa81162a8da6f5a8794862b31071bc3f9e62`. The enclosing qualification
commit adds only the inert test-fixture completeness correction and this final evidence; its production source is unchanged.
The same two review children also inspect that enclosing final local head before handoff.

## Local history

The following original stack was reviewed and preserved until the maintainer explicitly authorized the integration rebase above.

- `55e6157449a1ca1a7df361b2986435edb468ba2a` — vertical compiler/runtime/schema implementation and tests.
- `761bc51589a6bac957fc0053ac33f29a41e85809` — current user docs, migration, changelog and evidence.
- `165daa81162a8da6f5a8794862b31071bc3f9e62` — additive lifecycle exclusion review correction.
- The enclosing qualification commit completes the binary fixture and validation/review record.

The documentary feature, current user page and issue links are prepared locally. No GitHub issue body or state mutation is
part of S4 delivery; the draft PR will link the documentary test, user contract and evidence under Related: #952.

## Current delivery boundary

The original pause condition has been satisfied and explicitly revoked by the resume instruction. After integration checks
and final local review, publish the dedicated branch and open a draft PR against master. Do not mark it ready or merge it.
No local SonarCloud upload or adopter dispatch is authorized; report hosted CI/SonarCloud state against the exact PR head.
Tracker impact: **Related: #952**, no closure. Curation impact: none. The worker does not self-archive.
