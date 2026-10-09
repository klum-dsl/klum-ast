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

## External ScHelm qualification: completed LINK reads

On 2026-10-09, the ScHelm spike consumed Maven Local version
`4.1.0-dev.329+codex.issue.867.lp1.ce36fb9`, published by the normal
`./gradlew publishToMavenLocal` workflow from clean KlumAST commit
`ce36fb99e85df2644ea7e687aa74c71b7528c1c4`. All product modules, the BOM and
plugin markers were installed at that version; installed JARs matched the build outputs.
Fresh participant, binary-consumer, AutoLink and traversal tests passed: 47 tests, no failures/skips.

The external consumer moved `@FactBinding` to `@LifecycleMutator(phase = AutoLink,
handler = FactBindingHandler)`, removed `@Owner OrderApplication application` and
`OrderKafka.bindFacts()`, and retained exact-instance linking and missing-Fact diagnostics.
A consumer-owned `FactBoundDomain` marker constrains the domain side while `Kafka.facts`
retains its concrete technology type. The handler receives the correct actual containing/target
Builders at `OrderApplication.messaging`. ScHelm reports 13/13 focused `OrderApplicationSpec`
tests and its full `./gradlew check` passing (24 tasks). Consumer commits `a3031b0`,
`e482819` and `c7ae6a5` were merged as `a6cb793170517401447cfa0595cd6d05060abef3`;
that merge identity was independently verified. The additional participant `KlumModelException`
required updating only the consumer diagnostic test's expected cause depth.

The initial report that completed LINK Facts could not be read was corrected by the consumer:

- `containingBuilder.getEnvironment()` supplies a Builder wrapper with the correct concrete Model type.
- Calling the generated `environmentBuilder.getMessaging()` directly reads the wrapper's empty
  relationship storage and returns `null`, although the completed Environment contains that Fact.
- `InvokerHelper.getProperty(environmentBuilder, factFieldName)` uses the existing Groovy property
  forwarding and returns the **completed Fact Model**, rather than a nested Builder. The generated
  Domain relationship method accepts this completed value and links the exact existing instance.

An independent minimal probe against the same published artifact reproduced both paths. An assertion
requiring the explicit generated getter to be non-null failed; a control using public Groovy
`InvokerHelper.getProperty` passed and asserted `application.messaging.facts.is(environment.messaging)`.
The observed output was:

```text
completedEnvironment.messaging.value=existing-fact
participant=messaging; environmentType=Environment; environmentName=production;
  nestedGetterFacts=null; nestedDynamicFacts=Facts@0
dynamic read links exact completed Environment Fact: true
```

This is successful real-consumer **LP-1 dispatch and reuse evidence through a fixed consumer
convention, dynamic Groovy property access and reflective discovery/invocation**. It does not prove
statically typed reuse over unrelated Schema hierarchies or a generated typed read contract for completed
LINK wrappers. `KlumBuilder` is an operation-free marker; `KlumBuilderSupport` provides Model type and
owning-declaration metadata, not completed-value unwrapping. `KlumObjectSupport` requires a completed
object already in hand. No public LP-1 context method exposes that completed object.

The missing typed completed-LINK read contract is evidence for **LP-6 qualification**, including the
question of whether a consumer expression can read the required completed value under the accepted fixed
delegate/`DELEGATE_ONLY` semantics. Closure evaluation itself has not been qualified. It does not justify
a generic property-access API, ScHelm policy in KlumAST, or an implementation change without Hive direction.
The earlier common-base tracer remains valid for active construction relationships; it did not exercise
this completed-LINK distinction. This evidence update changes no artifact/API, accepted ADR, issue state
or release targeting; the tested Maven Local artifact remains the exact version and commit above.

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

Local qualification repeated on maintainability follow-up head `b99f1a14`:

| Check | Result |
| --- | --- |
| Focused `:klum-ast:test` (participant, AutoLink and traversal tests) | Passed, including six creator/mutator runtime/assertion/linkage-failure regressions |
| Affected baseline module suites | Passed before review; compiler 1,639 / runtime 73 tests, zero failures/errors |
| Sequential final `./gradlew check` | Passed after review fix; all repository checks, license checks, lane isolation and compatibility coverage |
| Compiler Groovy 3 / 4 / 5 | 1,644 tests each; zero failures/errors; 15 existing skips each |
| Runtime Groovy 3 / 4 / 5 | 73 tests each; zero failures/errors; one existing skip each |
| `git diff --check`, edited relative Markdown links | Passed |
| Standards review | Zero breaches/actionable smells; diagnostic follow-up re-review clear |
| Specification review | One lost assertion-context finding addressed in `c3cc943a`; re-review clear |
| Commit-history review | Coherent implementation, documentation and additive diagnostic-review steps; no rewrite needed |

The initial full run was invalidated by overlapping local Gradle builds that replaced versioned JAR inputs;
it is not acceptance evidence. The final full run above was sequential with stable inputs. Fatal JVM errors
remain unwrapped; ordinary runtime failures, assertions and linkage failures retain their original cause
and participant/handler/phase/field context. No test suppression was added.

SonarCloud on published head `4906a129` passed its quality gate with zero bugs/vulnerabilities and
11 maintainability findings. Additive follow-up `b99f1a14` addresses redundant casts, traversal/test
style and compiler-method complexity; the three required `KlumBuilder<?>` return signatures retain
localized `java:S1452` suppressions explaining ADR 0028's Schema-neutral consumer boundary.
Both review axes found no follow-up regression, and sequential full `check` passed again in 6m 26s.

Publication is an authorized draft with `Related: #867`. The final-head PR check/status records and handoff
provide remote CI/SonarCloud evidence; local success is not claimed as remote success. The Hive must
reconcile the open PR and later gates; the worker does not set archive-safe state or close #867.

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
