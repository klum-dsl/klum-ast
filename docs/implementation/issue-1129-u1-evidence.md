# #1129 U1: minimal Integer/String alternatives

Date: 2026-11-27. Scope: the [accepted U1 slice](issue-1129-struct-scalar-alternatives.md#u1--minimal-vertical-behavior).
Base: merged plan PR #1130, `a487e75bc2c703d8d19e108d75157da527b997e9`.
Status: implemented and locally qualified for U1; remaining issue acceptance belongs to U2/U3.

## Behavior and implementation

Exactly two explicitly marked, Schema-authored operations with public Builder projections may admit one effective
Struct member: one `int`/`Integer` and one exact `String`. The existing resolver produces a closed immutable pair of
complete arm entries. FromStruct/applyStruct and JSON Schema consume that same result. Invocation retains the recorded
signature, standard exact int32 conversion and String identity; failures never retry the other arm. JSON Schema emits
integer then String under `anyOf`, with int32 bounds and each arm's existing method documentation independently.
Unmarked collisions, single selected String routes and ordinary Builder/Model behavior remain unchanged.

Protocol 1 gains ordinary authored-operation parameter count, constraint rows and policy with unknown defaults. U1
emits explicit empty facts only for parameters with no unqualified annotations (a projection policy alone is allowed).
A compiler-only method marker freezes original empty-parameter eligibility in the existing pre-collector handoff.
Other parameter annotations remain unknown, never falsely empty, even when a collector erases them or expands to a
policy. The shared reader reuses inert constraint-row decoding;
family admission requires count zero, explicit empty rows and explicit policy on both arms. Nonempty/unknown facts
reject even for absent input or OMIT. Existing single routes need no new completeness facts. Full source-origin capture
and arm-constraint projection remain U2 work.

Two collector rejection controls reproduced false empty-fact admission before source capture and pass afterwards.
Full source rows and parameter-origin semantics remain U2 work.

A targeted risk control extends existing override removal to int/Integer in compiler and runtime: an ignored descendant
integer override must not execute through its parent's mark. Its regression reproduced wrong-code dispatch before the
guard and passes afterwards. This is the only early inheritance control; the full U2 matrix is deferred.

## Local validation

Focused Groovy 3 command (compiler/consumer/reader/interpreter coverage):

```shell
./gradlew :klum-ast:test \
  --tests '*StructuredScalarAlternativeTest' \
  --tests '*StructuredInputAdmissionPlanTest' \
  --tests '*StructuredOperationSelectionTest' \
  --tests '*CompiledBuilderSchemaTest' \
  --tests '*CompiledBuilderSchemaReaderTest' \
  --tests '*StructuredInputRuntimeTest' \
  :klum-ast-gradle-plugin:test \
  --tests '*KlumJsonSchemaGenerationTest.emits one bounded integer and String*' \
  :klum-ast-annotations:licenseMain :klum-ast-runtime:licenseMain \
  :klum-ast:licenseMain :klum-ast:licenseTest \
  :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest \
  --console=plain --max-workers=2
```

Result: passed. The new runtime tests exercise both numeric declarations, exact conversion, call counts, strict scalar
rejection, operation-body cause/no-retry, legacy selection, unknown/nonempty source annotations and missing/count/row/policy
facts. Existing contract and interpreter tests supply focused regression coverage. The plugin test checks actual emitted
boxed/primitive schemas, integer-first canonical order, separate arm prose and unchanged unmarked/single-String output.

Independent validation uses `jsonschema==4.23.0` and the actual generated files retained by that plugin test:

```shell
python klum-ast-gradle-plugin/src/test/resources/validate_scalar_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

Result: two Draft 2020-12 schemas pass schema validity and **20 accepted / 22 rejected** document assertions, including
int32 endpoints, integral JSON numbers, numeric-looking Strings, fractions, out-of-range values, null/Boolean/object/array
and unknown members. Schema-valid `13` deliberately illustrates that operation-body success remains a runtime concern.
No handwritten union substitutes for generated output. Affected license checks and `git diff --check` pass; current user
page links, anchors and reciprocal documentary references were checked.

Documentary entry: `StructuredScalarAlternativeTest#'one address accepts an integer or a String without changing its meaning'`
(`@Issue('1129')`, `@Tag('documentary')`, `@See`), mirrored by
[Structured Input](../user/Structured-Input.md#integerstring-alternatives). Capability documentation, JSON Schema,
annotation Javadoc, migration guidance and the unreleased changelog describe only the delivered slice.

The source-capture correction additionally passes focused Groovy 3 `AnnotationPropagationContributionTest`,
`AnnotationPropagationContractTest` and `AnnotationPropagationLifecycleTest`, plus the scalar-family suite and its
positive empty-OMIT control. These checks cover the touched compiler handoff; they do not claim general parameter
projection or full multi-Groovy qualification.

## Deliberate deferrals and delivery

U2 retains complete original parameter facts/constraints/policies, full effective naming/inheritance/override and malformed
metadata matrices, the four-overload ignored-helper qualifier, broader numeric/order/Template boundaries, and affected
module Groovy 3/4/5 qualification. U3 retains independent/historical artifact consumers, JPMS, root `check`, final delivery
qualification and consumer adoption. Focused local success is not complete-build, hosted CI or SonarCloud evidence.
No general union/ADT, KlumAST IntOrString, generated-converter arm, alternate resolver, protocol bump, runtime parameter
validation, parser or #1113 reconstruction claim is introduced. No ScHelm/Kubernetes source, dependency or fixture changes.

Independent reviews: Standards reported two brace-style violations and one optional fixture-readability observation;
all were addressed. Specification found the collector-origin gap above; it was reproduced with two failing controls
and repaired conservatively. Review fixes preserve the initial two reviewed commits as an additive follow-up.
The final review dispositions are recorded in the PR/worker handoff.

Hosted CI on `9ef4f29187774a656c1ab410acdfb40a4c945a98`
([run 46060891755](https://github.com/klum-dsl/klum-ast/actions/runs/46060891755)) passed the existing full pipeline,
including root checks, current compatibility lanes and annotation-propagation artifact checks. Sonar's quality gate
passed with 85.1% new-code coverage, 0% duplication and no security hotspots, but reported eight maintainability findings.
The additive follow-up extracts family execution, original-source capture and parameter-array decoding into cohesive
private helpers, and adds five missing Groovy braces. All 326 focused Groovy 3 compiler/runtime/handoff tests, the plugin
test, independent emitted-schema assertions and affected license/diff checks pass after that cleanup; both independent
reviewers found no remaining issue in the correction. Final-head hosted reanalysis is reported in the PR/worker handoff.
These existing-pipeline results do not complete the unimplemented U2/U3 acceptance matrices.

Tracker impact: **Related: #1129; curation impact none**. The issue remains open. Draft PR delivery and hosted findings are
reported in the worker/PR handoff; merge and final archive reconciliation remain Hive-owned.
