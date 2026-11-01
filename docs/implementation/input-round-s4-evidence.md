# S4 — zero-argument structured commands

Related: [#952](https://github.com/klum-dsl/klum-ast/issues/952),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[4.2 implementation plan](input-round-plan-4-2.md).
Base: `4f68e1ec97b6a9e78e9e7eccb300dad7b55ce6d0` (S2, `codex/952-string-operation-selection`).
Worktree: `/Users/stephan/.codex/worktrees/c54e/klum-ast`.
Branch: `codex/952-zero-argument-commands`.

## Implementation boundary

Compiler admission adds COMMAND / EMPTY_OBJECT for exact public zero-argument `@Builder.Method` projections.
Every authored operation emits a required role in `GeneratedInput.Operation`, including excluded methods, retaining
S2's operation count and author-selection facts. The byte reader checks explicit presence, type and role/signature
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

## Acceptance and validation

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

Full repository `check --continue --no-parallel --max-workers=2 --console=plain` and exactly two local review axes are in
progress. Their final results will be recorded before handoff; focused results do not yet claim complete qualification.

## Delivery boundary

Local commits only; no push, PR, GitHub issue mutation, merge, SonarCloud upload or adopter dispatch.
Tracker impact: **Related: #952**, no closure. Curation impact: none.
Finish **(paused)** with a clean worktree. The sole resume condition is **S1 PR #1002 merged, then S2 rebased onto merged
S1 and itself merged; explicitly resume S4 to rebase onto master**. Hive owns subsequent delivery and reconciliation.
This worker does not self-archive.
