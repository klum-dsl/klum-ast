# #867 LP-1: direct-field lifecycle participant tracer

Date: 2026-10-09. Base: master `24aeb26a` (merged ADR 0028 / PR #869).
Authority: explicit maintainer LP-1 implementation delegation and subsequent reuse-boundary review.
Scope: AutoLink only; direct retained Schema DSL fields. Related: #867; the issue remains open.
Release impact: none; conditional 4.1 classification is unchanged and full qualification remains pending.

## Exact provisional public seam

All six types are in runtime's existing exported `com.blackbuild.klum.ast.runtime` package.

| Type | Contract |
| --- | --- |
| `LifecycleCreator` | Runtime meta-annotation on annotation types; `phase(): Class<? extends Annotation>`, `handler(): Class<? extends LifecycleCreationHandler>` |
| `LifecycleMutator` | Separate runtime meta-annotation; same phase representation, separate `LifecycleMutationHandler` class bound |
| `LifecycleCreationHandler<A extends Annotation>` | `KlumBuilder<?> create(LifecycleFieldContext<A> context)` |
| `LifecycleMutationHandler<A extends Annotation>` | `void mutate(LifecycleMutationContext<A> context)` |
| `LifecycleFieldContext<A extends Annotation>` | `A getAnnotation()`; `<B extends Annotation> Optional<B> getAnnotation(Class<B>)`; `KlumBuilder<?> getContainingBuilder()`; `String getFieldName()`; `Class<?> getDeclaredType()`; `FieldType getFieldType()` |
| `LifecycleMutationContext<A extends Annotation>` | Extends field context; `KlumBuilder<?> getTargetBuilder()` |

Annotation handler class bounds necessarily erase the domain parameter at the class-literal boundary;
compiler/runtime checks enforce its exact resolved annotation identity through generic inheritance.
No public context constructor/setter, completed-Model creator result, reflection Field/list/path/expiry
API, new KlumBuilder operation or generated public contract change. Handler/context instances are local
invocation state, with fresh public no-arg handlers and no caching/DI.

## Reuse evidence and its limits

`LifecycleParticipantTest#'reuses typed fact binding across Application and Domain Schemas before child field actions'`
uses one `@CompileStatic BindFactsHandler` for Orders/KafkaDomain and Notifications/QueueDomain.
Domain Schemas contain no binding lifecycle methods or Owner backlinks. Domain annotations select the
behavior. Parent-field dispatch passes the actual containing subtype and existing target; exact domain
annotation plus singular original-field `RelationshipRole` lookup, incoming name and declared type are
asserted, including an inherited parent field. A Domain.facts participant observes the parent mutation
in the same AutoLink traversal. Configured child values remain intact.

The mechanism is **consumer-defined common Schema bases plus generated public Builder interfaces and
factory-token narrowing**: ApplicationBase owns the typed Environment relationship, DomainBase owns
the typed Facts relationship. Handler code calls only public APIs. There is no dynamic property access,
reflection, reconstructed ownership or path lookup. The Java binary consumer separately names public
`Knowledge_DSL.Builder` / `Domain_DSL.Builder` and uses the same narrowing mechanism.

This proves reusable cross-Schema behavior when a shared typed consumer contract exists. It does not
prove that unrelated existing ScHelm Application/Domain hierarchies already supply that contract, nor
that their technology-specific Facts can all use this shared relationship shape. It does not replace
Order-specific plumbing with an Order-specific handler. A consumer without a common contract needs
its own contract/convention or LP-6 provider-expression investigation. No generic property-access API,
consumer policy or Closure helper is added. Names/signatures remain provisional until later review.

## Qualification and validation

The creator documentary case covers existing versus absent fields, null results, creator-before-mutator,
and fresh handlers across multiple occurrences/sessions, with dynamic and static Groovy handlers.
Negative compilation covers mismatch, raw, wildcard and unresolved annotation parameters; invalid
constructor/abstract handlers; scalar/container/static/Builder-only fields; unqualified phases and
Schema type/method placement. Runtime binary consumers replace a valid handler after Schema compilation
with mismatched/raw/unresolved variants and require a declaration error before invocation.
Checked assignment retains foreign-session and fresh-LINK rejection. Invocation diagnostics preserve
cause, annotation/handler/AutoLink/field context; a subsequent lifecycle works after failure.
Existing AutoLink and parent-before-child characterization tests are retained.

Validation commands/results and final-head review/CI evidence are recorded below after execution.

## Remaining gates

- LP-2: direct creator conflicts, composition breadth and compiled mutation-order qualification;
  only unconditional creator-before-mutator is delivered here.
- LP-3: AutoCreate/Default/PostTree insertion points and cluster/callback sequence qualification.
- LP-4: explicit FAIL/SKIP, sealed/graph/Template/import/session breadth; optional HANDLE probe.
- LP-5: optional type mutation, without any type dispatch API in LP-1.
- LP-6: optional Closure/provider evaluation, potentially important without shared consumer contracts.
- LP-7: mandatory support-or-reject release decision for containers; support remains optional.
- LP-8: complete diagnostics/existing validation examples, Java/Groovy/JPMS qualification,
  final naming/public inventory/documentation and release/Hive reconciliation.

LP-1 intentionally rejects unqualified shapes/phases rather than silently ignoring them. Builder-only
fields have no retained original Model Schema declaration at this baseline and are rejected for LP-1;
this is recorded for later field/compatibility qualification, not a final feature decision.
