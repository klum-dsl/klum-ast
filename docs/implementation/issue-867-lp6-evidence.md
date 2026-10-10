# #867 LP-6: annotation Closure reuse and benefit probe

Date: 2026-10-10. Base: origin/master through merged LP-5 PR #876,
`a4ea604dabfc25cda28c63c838979b5bbadc47f7`.
Authority: explicit maintainer implementation-or-evidence-deferral delegation.
Related: #867; issue state, curation and conditional release placement remain unchanged.

## Disposition and actual benefit

**Evidence-defer LP-6; no public helper or partial API.** Ordinary Groovy already executes the
consumer's `@FactBinding({ [messaging: 'facts'] })` map. The new executable probe also demonstrates
an expression with an explicit statically typed Schema argument, retargeted by existing machinery
to its Builder, and explicit dynamic access to a runtime-selected delegate. These capabilities
need no KlumAST helper. See the [executed ScHelm contract](evidence/issue-867-schelm-map-closure.md).

The proposed fresh-instance / DELEGATE_ONLY / delegate-as-single-argument / expected-Class check
is modest runtime code, but only centralizes ordinary construction, property assignment, invocation
and `Class.cast`. It supplies neither compile-time result validation nor handler-specific delegate
inference. No consumer capability beyond ordinary Groovy was demonstrated. In particular it would
not repair completed-LINK generated getter storage or make heterogeneous Schema property names
statically typed. Those are separate contracts, not Closure invocation problems.

This is an independently optional deferral under ADR 0028 D7. It does not block core, qualify
#867 as complete, or change ordinary Closure behavior. Existing DELEGATE_FIRST execution and all
LP-1–LP-5 APIs, dispatch, type mutation, sessions, ownership and materialization remain unchanged.

## Source assessment and reuse limit

- `DSLASTTransformation.convertValidationClosureOnSingleField` knows the annotated field type.
  It calls `CommonAstHelper.toStronglyTypedClosure`, replacing the single explicit/implicit parameter
  and its references with that type, then converts the one-statement predicate to an assertion.
  The argument is the **field value**, not the owning Model or an arbitrary handler delegate.
  Runtime `KlumFieldAnnotationsValidator` passes the completed Model as delegate and field value
  as argument, using the existing DELEGATE_FIRST helper. Assertion conversion is Validate-specific.
- `retargetBuilderAnnotationClosures` maps explicit DSL-typed parameters to generated Builders and
  retargets captured Schema fields/property expressions and inferred Model return types. It does
  not inspect handler code to discover a delegate contract or introduce a general result checker.
  The successful static explicit-argument probe uses this existing mapping unchanged.
- Internal `ClosureHelper` already creates fresh annotation Closures with null owner/thisObject,
  invokes them and supplies delegates/arguments, but selects DELEGATE_FIRST. It is not a public
  consumer seam and cannot be reused unchanged for D7; it remains unchanged here.
- A handler-selected delegate could be the containing Builder, target Builder, lifecycle context,
  or a consumer view. The current annotation member and participant marker carry no declaration
  of that choice. Reusing field-derived Validate typing would impose the wrong type. Establishing
  general implicit typing needs an additional compiler/IDE metadata contract, outside this probe.

## Executable characterization

`LifecycleParticipantClosureProbeTest` carries class-level `@Issue('867')`. All assertions use
compiled Schema declarations, ordinary Groovy annotation instances, root factories, completed
values or compilation diagnostics; no private compiler method is called.

| Control | Observed behavior |
| --- | --- |
| Ordinary map through a real AutoLink participant | Two independent root builds select distinct values with ordinary `InvokerHelper` construction and zero-argument call; no Klum Closure API |
| Static `@Selection({ Application app -> app.source.toUpperCase() })` | Existing retargeting accepts the containing Builder as the explicit argument and produces `PROVIDER` |
| Explicit dynamic delegate read | `InvokerHelper.getProperty(delegate, 'externalValue')` works after ordinary fresh construction and DELEGATE_ONLY assignment |
| Unqualified `externalValue` | Annotation Closure compilation rejects the undeclared variable both with and without `@CompileStatic`; runtime delegate assignment cannot supply missing compile-time metadata |
| Generic `Class<? extends Closure<String>>` with `{ 42 }` | Compiles even under `@CompileStatic`, returns Integer 42; `String.class.cast` rejects it at runtime |
| Validate implicit `it.length()` | Compiles statically and validates a String field |
| Validate implicit `it.missingOperation()` | Fails compilation with a String operation diagnostic, establishing actual field-argument typing |

Test-first probe record: the initial hypothesis expected the generic annotation bound to reject
`{ 42 }`; the focused Groovy 3 test failed because compilation succeeded. The retained control
asserts the characterized behavior and the ordinary runtime cast rejection. Subsequent fixture
corrections removed conditional Spock exception assertions and replaced the unsupported unqualified
dynamic expression with explicit `InvokerHelper` access. No production implementation was needed.

The map control isolates ordinary annotation execution and participant reuse; it is not an LP-7
container participant or a new completed-LINK qualification. Explicit member choice, sentinels,
result validation and invocation policy remain consumer responsibilities. No Java Closure subclass,
bad-constructor, fallback isolation, failure-cause or JPMS helper qualification is claimed: those
remain mandatory if a helper is ever implemented, and there is no helper here to qualify.

## IntelliJ source characterization and limit

JetBrains primary source at revision `b17615904f1e5f697b7c5b657b7fe7f17183d042`:

- [functionalExpressions.kt](https://github.com/JetBrains/intellij-community/blob/b17615904f1e5f697b7c5b657b7fe7f17183d042/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/lang/psi/impl/functionalExpressions.kt):
  `doGetOwnerType` selects enclosing class, enclosing Closure or script class. Owner/delegate resolution
  uses delegate information when provided and otherwise lexical owner resolution.
- [DefaultDelegatesToProvider.kt](https://github.com/JetBrains/intellij-community/blob/b17615904f1e5f697b7c5b657b7fe7f17183d042/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/lang/resolve/delegatesTo/DefaultDelegatesToProvider.kt)
  and [grDelegatesToUtil.kt](https://github.com/JetBrains/intellij-community/blob/b17615904f1e5f697b7c5b657b7fe7f17183d042/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/lang/resolve/delegatesTo/grDelegatesToUtil.kt):
  the default provider finds a containing call and its parameter metadata/DelegatesTo declaration;
  extension providers may contribute additional information.

Inference from these sources: enclosing Schema completion is an owning-class convenience, not proof
of a handler-selected Builder/context delegate. This repository's GDSL contributes generated factory
and polymorphic method metadata, not participant annotation-member delegate metadata. Explicit types
remain a practical baseline; perfect inference is not required for deferral. This is source
characterization, **not a live IntelliJ completion/inspection acceptance run**. No IntelliJ plugin,
GDSL framework, annotation declaration vocabulary or source mirror change is introduced.

## Validation, review and delivery

Focused baseline Groovy 3: eight controls pass with zero failures/errors/skips. Final compatibility,
full-check and independent Standards/Specification results are recorded in the qualification follow-up
and PR handoff. Production inputs are unchanged. No tests are ignored, pending or suppressed.
User pages and CHANGES are unchanged because this slice delivers no new public behavior.

Tracker relationship is Related #867, with no issue-state/label/milestone or curation mutation.
Hive owns final delivery and archive reconciliation. The worker retains the open-PR state and
requests reconciliation rather than self-archiving.

## LP-8 documentation observations and remaining gates

Do not modify the Hive checklist from this worker. Carry these non-blocking observations for its owner:

- Ordinary map Closures remain the smallest documented consumer example. Do not imply that LP-6
  supplies a helper or that generic Closure result bounds validate arbitrary inline expressions.
- If showing a richer provider expression, distinguish explicit typed Schema arguments (retargeted
  to Builders during construction) from runtime-selected delegates and explicit dynamic reads.
- Enclosing-class IDE inference and static compiler proof are separate evidence; no live IDE
  qualification is claimed. DELEGATE_ONLY also is not a sandbox for arbitrary Closure code.
- Preserve the existing completed-LINK read distinction; Closure evaluation does not repair typed
  wrapper getters or wrapper-targeted validation reporting. HANDLE remains deferred under LP-4.

LP-7's support-or-reject decision/diagnostics remains mandatory; support is optional. LP-8 still owns
whole-feature errors, existing validation guidance, Java/static/dynamic/binary/JVM/JPMS qualification,
final API inventory/names, user documentation and release reconciliation. LP-6 is deferred, not a
public execution protocol. No containers/maps dispatch, validation infrastructure or ScHelm policy.
