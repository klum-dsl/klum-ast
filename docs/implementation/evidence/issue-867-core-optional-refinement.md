# #867 core/optional planning refinement

Date: 2026-10-09
Authority: maintainer's focused refinement request for PR #869.
ADR: [0028](../../adr/0028-annotation-driven-lifecycle-participants.md).
Plan: [LP-0 through LP-8](../adr-0028-annotation-driven-lifecycle-participants.md).

## Accepted architecture and release classification

Core delivers Application → @Binding Domain → Facts first: separate field creator/mutator,
creator-before-mutator, actual containing-field dispatch, parent-before-child, four existing
phase visitors and public Builder typing. Preserve ownership, checked assignment, sessions,
materialization, Templates/imports and generated contracts. No ScHelm policy, second whole-tree
action, child-owned dispatch, completed-Model creator result, setter, new phase SPI or collector.

Attempt optional type mutation, Closure evaluation, HANDLE and Collection/Map support where
straightforward. Substantial difficulty permits documented independent deferral without
blocking core. Omit their unqualified public contracts. Explicit container support/reject with
diagnostics is required before release. LP-1 alone does not qualify the whole cohesive feature.

## Ordering disposition

No new probe has run in this documentation-only refinement. LP-2 covers Java/Groovy annotations,
repeatable meta-annotations and separately compiled libraries/Schemas on Groovy 3/4/5.
Recoverable declaration order becomes documented/regression-tested; otherwise execution order
is unspecified and handlers must be order-independent. Between different domain annotations
on a field, deterministic order is not established. Optional type-before-field placement is
distinct, with parent-field work already preceding child visit. Creation-before-mutation
always holds. No priority/sorting/SPI machinery.

## Closure source assessment and early probe

DSLASTTransformation.convertValidationClosureOnSingleField calls toStronglyTypedClosure
before assertion conversion. retargetBuilderAnnotationClosures handles Model/Builder annotation
expression mapping. ClosureHelper already constructs Closure classes and supplies delegates/
arguments but uses DELEGATE_FIRST. These are concrete reuse seams, not proof that every
handler-selected context delegate is already inferred.

LP-6 starts before public helper signature freeze: Validate strong typing/IntelliJ inference
baseline, inline Groovy and Java Closure classes, Schema-to-Builder mapping, fixed delegate/
single argument, expected result check, DELEGATE_ONLY, static compilation and Groovy 3/4/5.
Perfect inference is unnecessary. Modest reuse supports delivery; substantial changes support
independent deferral without a new general Closure framework.

Source links: [annotation Closure transformation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java)
and [existing Closure invocation](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/ClosureHelper.java).

## Existing validation support and LP-8 example

The maintainer refers to KlumValidationSupport. At this PR baseline the delivered equivalent
is KlumSchemaSupport returning KlumValidationReporter; no class has the former name.
This observation does not reopen architecture or authorize a rename/new facade.

Evidence: [KlumSchemaSupport](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumSchemaSupport.java),
[KlumValidationReporter](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumValidationReporter.java),
[existing documentary reporter tests](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/KlumValidationReporterTest.groovy)
and [user guidance](../../user/Validation.md#custom-validation-reporting).

- getKlumValidation supplies current-object reporting and requires lifecycle instance context.
- klumValidationForObject(target) selects an explicit Builder/Model, with no default member.
- errorAt/issueAt supplies a member; existing severity, suppression and fail level apply.
- KlumValidationReporterTest covers lifecycle helper calls, early findings, child targets,
  suppression and fail level. LP-8 uses this infrastructure.

Concise user guidance and a runnable participant documentary example belong to LP-8, not this
planning-only PR. Proposed handler body using the existing reporter and a narrowed public
Domain Builder (participant API remains unimplemented):

~~~groovy
import com.blackbuild.klum.ast.runtime.KlumSchemaSupport

if (domain.facts == null) {
    KlumSchemaSupport.klumValidationForObject(domain)
        .errorAt('facts', 'No compatible facts were selected')
}
~~~

LP-8 turns this into one small @Issue("867"), @Tag("documentary"), @See test linked from user
Validation/participant guidance: create Application.domain, invoke an external mutator,
record the Domain's Facts finding, assert ordinary materialization/validation severity/member
reporting. No executable participant test or production code is introduced by this refinement.
Current-object reporting relies on phase context; explicit target reports that target's path
and needs an At operation for member location. Use inside framework-managed lifecycle, not as
new out-of-lifecycle permission. Unexpected handler defects still fail with original cause.

## Independent gates and remaining maintainer input

| Capability | Include when | Defer when | Core effect |
| --- | --- | --- | --- |
| Type mutation | Small extension after field dispatch | Significant compiler/runtime work | No core block; no type API |
| Closure evaluation | Validate/Builder mechanisms reusable | Substantial architectural change | No core block; no helper API |
| HANDLE | Small dispatch extension with immutable guards | Interception/lifecycle/read-only framework needed | FAIL/SKIP stay core |
| Collection/Map | Accepted bounded existing-traversal mechanics | Context/ownership/enumeration complexity | Reject clearly before release |

Remaining input follows evidence: final names/signatures, optional deferral recommendations,
container support/reject and release placement. No immediate new architectural decision or
validation mechanism is required. #868 remains separately scoped; #867 stays untargeted,
a conditional 4.1 candidate. Implementation requires separate authorization.

## Validation scope

This refinement changes Markdown only; preserve traversal characterization tests byte-for-byte.
Check links, Markdown fences/structure and diff hygiene. No full Groovy rerun, no claimed new
ordering/Closure/HANDLE probe evidence. Historical test/CI success is not proof of implemented API.
