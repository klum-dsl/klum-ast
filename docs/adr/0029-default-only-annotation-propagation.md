# Default-only annotation propagation on DSL Schema declarations

Date: 2026-10-13. Architectural refinement: 2026-10-14.

Status: Proposed. The public propagation contract is accepted in principle. DSL-only scope is confirmed;
compiler architecture and KlumCast integration still require maintainer admission.

Implementation status: ADR and bounded, opt-in feasibility probes only. **AP-1 is not authorized.**
Its gate is demonstrated validation correctness for the admitted domain-annotation scope, through either
qualified manual API metadata/checks or the supported automatic KlumCast route. KlumCast #58 is a
conditional dependency of the automatic route, not an unconditional prerequisite for propagation.

Release: Conditional 4.1 candidate, subject to Hive review; no milestone or curation change is made here.

Tracking issue: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Merged design investigation: [PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
Preferred automatic-validation enhancement / conditional dependency: [KlumCast #58 — Support effective-declaration validation after
compiler AST preprocessing](https://github.com/klum-dsl/klum-cast/issues/58).
Implementation plan: [ADR 0029 plan](../implementation/adr-0029-default-only-annotation-propagation.md).
Current evidence: [scheduling and validation investigation](../implementation/evidence/issue-890/schema-preprocessing.md).
Historical alternatives and consumer fit: [decision brief](../implementation/issue-890-lifecycle-composition-decision-brief.md).

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0005](0005-generated-dsl-support-api.md), [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md), [ADR 0020](0020-explicit-shared-model-builder-capabilities.md),
and [ADR 0028](0028-annotation-driven-lifecycle-participants.md).
Related: [ADR 0023](0023-schema-owned-constraint-annotations.md), [#888](https://github.com/klum-dsl/klum-ast/issues/888).

## Context and authority

Schema Developers repeat AutoCreate beside domain annotations that select DefaultValues or lifecycle
participants. ScHelm's reported examples are concrete, unkeyed direct relationships: OrderApplication's
messaging/database/workload/api and OrderKafka's orderTopic/resultTopic. Its current use needs bare
AutoCreate, retained domain members for defaults/handlers, and deterministic duplicate failure. No
forwarding, nesting or customized AutoCreate configuration is required. This is Hive-relayed consumer-fit
evidence; no ScHelm integration build was run locally.

The maintainer selected a repeatable, retained propagator carrying one selected annotation type per request.
The 2026-10-14 refinement limits execution exclusively to existing DSL Schemas. The former proposal to
propagate onto arbitrary Groovy classes, including propagation of DSL itself, is superseded. A global
compiler registration may be needed for scheduling; it must not grant global propagation semantics.

The maintainer proposed coordinating early transformations and made preservation of KlumCast constraints
an explicit acceptance requirement. The new probes demonstrate that pre-local-discovery insertion alone
is insufficient: an independently scheduled KlumCast global pass can run before insertion. Correctness
must cover the complete resulting declaration, including constraints on existing explicit annotations.

The maintainer subsequently permits additional work for Domain API Developers to save Schema Developers
repeated annotation syntax. Manual KlumCast checks on domain annotations are an admissible alternative
if qualified for their documented scope. The automatic #58 route should first receive a bounded complexity
assessment: incorporate it if small enough; otherwise it may be deferred while a qualified manual route
serves the initial use cases. This supersedes the earlier blanket #58 blocker, without waiving correctness.


## Decision

### D1 — Public marker and default-only semantics remain accepted in principle

Introduce `com.blackbuild.klum.ast.PropagateAnnotation` in klum-ast-annotations, with runtime retention,
ANNOTATION_TYPE target, documentation and repeatability through nested `List` with matching metadata.
The only member is `Class<? extends Annotation> value()`. The annotation artifact stays Groovy-free.

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Documented
@Repeatable(PropagateAnnotation.List)
@interface PropagateAnnotation {
    Class<? extends Annotation> value()

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @Documented
    @interface List { PropagateAnnotation[] value() }
}
```

Retain the original domain annotation and append fresh selected annotations with no supplied members.
The selected annotations use their normal defaults; domain annotation arguments are not forwarded.
Normal SOURCE/CLASS/RUNTIME retention and existing projection rules apply to both original and copies.

```groovy
@DefaultValues
@PropagateAnnotation(AutoCreate)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MessagingDefaults { String name() default '' }

@DSL class Kafka { String name }
@DSL class Application {
    @MessagingDefaults(name = 'orders') Kafka messaging
}

assert Application.Create.One().messaging.name == 'orders'
```

No LifecycleParticipant is required. The same marker may accompany participants or DefaultValues, alone
or combined. DefaultValues continues to consume nondefault domain members. Customized AutoCreate
key/type/value remains explicit with a domain annotation that does not also request AutoCreate.

### D2 — Execution is restricted to eligible DSL Schema declarations

Use `DslAstHelper.isDSLObject` and the main DSL transformation's declaration boundaries; do not create
another inheritance-based eligibility model. The proposed rules, supported by the bounded probes, are:

| Declaration | Eligibility and existing restrictions |
| --- | --- |
| Explicitly annotated concrete or abstract DSL class | Eligible; abstractness does not prevent preprocessing or inherited contracts |
| DSL interface | Excluded from expansion, matching DSLASTTransformation's early interface return; no new interface DSL surface |
| Domain annotation definition | Metadata only; never an execution site |
| Original declared Schema field or method | Eligible subject to ordinary target, static, shape and DSL checks; eligibility does not legalize invalid uses |
| Inherited declaration | Expand at its source owner only; a compiled parent contributes its already emitted behavior |
| Subclass without DSL | Do not infer eligibility from annotation inheritance; retain the existing non-DSL-subclass hierarchy error |
| Static nested or nonstatic inner DSL class | Evaluate that class independently, using existing DSL checks; its enclosing class grants no eligibility |
| Ordinary enclosing, nested or sibling class | Inert, including in a mixed source unit |
| Generated getter, Builder twin or source mirror | Projection/output, never a new expansion site |

Do not expand constructors, parameters, packages or type uses. **DSL is not a supported selected type**;
reject that request at an eligible execution site. Ignore propagation metadata on non-DSL uses without
adding copied annotations, generated members, propagation errors or altered local-transform discovery.
Do not scan unrelated source units. Avoid member traversal after a class fails eligibility, and avoid AST
mutation when no request contributes work. Existing unrelated compiler checks continue normally.

### D3 — One level and duplicate failure

Expand requests from actually applied domain annotations once. Reject a selected annotation that itself
requests propagation, including self-reference and cycles. No recursion or silently ineffective nested
requests. Recognize genuine repeatable requests and explicit containers in source and precompiled metadata,
and genuine containers of applied domain annotations. Container normalization does not add another level.

Reject every duplicate contribution involving propagation, even if identical or the selected type is
repeatable: explicit plus copied, repeated requests, repeated domain uses, or different contributing domains.
No merge, deduplication, precedence or priority. Ordinary explicit-only repeatable annotations are unchanged.
Retain existing downstream conflicts such as competing Creators and Query/Builder.Method categories.

Before scheduling local transformations, reject non-annotation types, illegal targets and members without
defaults, with domain → selected annotation → declaration provenance. Do not rely on late Groovy verification:
wrong Required placement can reach its field cast, and bare annotations with missing members can escape
Groovy compilation. This guard does not redefine existing direct-annotation behavior or runtime AutoCreate
keyed/abstract failures.

### D4 — Prefer guarded propagation, with an explicit validation boundary

Recommend **approach B**, with one of D5's qualified validation routes. One internal global entry at
semantic analysis checks DSL eligibility, discovers propagation requests and attaches one complete
authorized default-only batch per declaration before Groovy's local-transform discovery. Keep contribution
operations separate and testable. The intended sequence is:

```text
existing DSL eligibility
  → collect requests and check contribution legality
  → append complete default-only batch with provenance
  → automatic route only: supported KlumCast #58 post-batch validation
  → Groovy local collection/execution (existing Required, Optional, Cluster, DSL)
```

With the manual route, original domain annotations declare their own complete applicable KlumCast checks
and remain visible to ordinary validation independently of propagation. The preprocessor does not promise
that every copied annotation's checks are automatically discovered/evaluated. Domain API Developers must
qualify equivalent validation for the admitted uses and combinations in either global order. With the
automatic route, the future narrow, idempotent operation owns validation against the complete effective
state after the batch. Neither route infers validation correctness from independent global-pass ordering.

This is not a public preprocessing SPI, configurable priority system, plugin registry, reflective ordering
scheme, second DSL framework or runtime lifecycle. Source/generated declaration snapshots and per-node
bookkeeping prevent another expansion on projections. No repeated whole-compilation scan is required.

| Concern | A: generic global baseline | B: DSL-guarded propagation | C: guarded Required/Optional coordinator |
| --- | --- | --- | --- |
| Scope | Violates revised DSL-only requirement | Matches it | Matches it if correctly guarded |
| Phase/discovery | Early semantic insertion discovers local triggers | Same proven scheduling seam | Same seam; direct calls order only coordinator-owned operations |
| Required/Optional ordering | Existing semantic locals precede canonical DSL | Already sufficient; no mutual dependency found | Fixed order callable, but no additional dependency demonstrated |
| Validation | Same global-order bypass risk | Requires a qualified D5 manual or automatic route | Requires equivalent validation plus validation-before-lowering; otherwise false Required/Validate conflicts |
| Generated parity | Historical positive examples, including now-disallowed carried DSL | Query/Cluster, Builders/Factories and mirror probes match direct controls | Not qualified after migration; duplicate lowering observed with current registrations |
| Maintenance / double execution | Broad semantics and compatibility burden | Smallest change; original local registrations preserved | Converter adapters/retirement, idempotency, source positions and non-DSL compatibility needed |
| Foreign local transforms | Technical baseline | Separately compiled foreign field transform discovered once | No stronger ordering against unrelated transforms |
| Java/Groovy libraries | Both can supply metadata | Both exercised, including request containers | Same library encoding; converter scheduling adds no benefit |
| Groovy 3/4/5 / JPMS | Temporary classpath baseline only | Cross-version probes and bounded module probe; shipped packaging still a gate | Cross-version negative ordering probes; no shipped coordinator qualification |
| Diagnostics | Provenance needed | Target/configuration guards plus KlumCast provenance | Same, plus converter-produced Validate lacks source positions today |

C is technically callable, not intrinsically reliable. Both actual converters are semantic-analysis locals;
Required maps explicit value/level to Validate.message/level, Optional adds Validate.Ignore. Both append
Validate without idempotency or source position. They already precede canonical DSL processing. Cluster's
field conversion is also semantic; Cluster projection and DSL generation are canonical. Query is consumed
by DSL support and KlumCast, rather than being an independently registered Query transform.

Calling converters under C while retaining their annotation registrations duplicates Validate. Removing
registration from Required/Optional would also remove existing non-DSL behavior; safe migration would
need compatibility adapters or an explicitly approved restriction. Groovy discovers registrations from
the compiled annotation class's reflection metadata, so removing an AST meta-annotation is not retirement.
C also lets a later independent KlumCast pass see Required plus converter-generated Validate as an illegal
explicit combination. A validation handoff and skip protocol would be necessary. No demonstrated benefit
justifies those changes now. **Move neither Required nor Optional.** A general validation redesign or
Required runtime-phase rewrite is outside this ADR.

The guarantee is insertion before Groovy's local collection on the qualified compiler versions, not
universal ordering. There is no promise about relative order among independent global transformations,
independent same-phase locals, third-party code that rewrites declarations, or annotations created after
this preprocessing boundary. Unsupported interactions must be qualified or diagnosed, not silently widened.
KlumCast constraint correctness must come from the qualified route, regardless of which independent
global pass ran first. Positive Required/Cluster scheduling and generated-behavior probes alone are
feasibility evidence; they qualify neither full manual constraint coverage nor automatic effective validation.

### D5 — Validation correctness is the gate; KlumCast #58 is conditional

KlumCast 0.4.0 has a global semantic pass. It discovers validation bindings transitively through actual
meta-annotations (KlumCastValidated, KlumCastValidator, CheckBinding); it does not follow Class-valued
propagation requests. The engine validates the actual annotation use and explicitly supplied members,
uses compiled annotation reflection metadata, and records VALIDATED on the AnnotationNode. Constraint
annotation definitions are skipped as use sites when they carry validation bindings. Selected-type
metadata and the original domain annotation have distinct validation roles.

The probes demonstrate two correctness gaps that the chosen route must cover for admitted uses:

1. KlumCast first, propagation second: a copied bare Default misses its configuration constraint.
2. Validating only copies: an existing Key already marked VALIDATED misses a subsequently copied
   Field conflict. Automatic validation must recheck affected existing constraints; manual checks must
   independently cover the corresponding explicit and domain-alias conflicts.

Calling the existing ValidationHandler engine proves applicable checks can be preserved, with provenance
and original source coordinates. Experimental metadata invalidation demonstrates the second gap can be
closed, but is not a supported production protocol and can duplicate diagnostics already reported by the
first pass. **KlumAST must not clear, set or otherwise manipulate KlumCast VALIDATED metadata.** The
test-only experiment is not an implementation option. Do not publish propagation using only the new-copy
check or an assumed relative global-transform order.

| Integration | Assessment |
| --- | --- |
| Automatic copying of KlumCast meta-annotations onto domains | Not selected. Mutating source ClassNodes does not amend precompiled reflection metadata, and member checks may address the wrong annotation values. |
| Manual domain-annotation metadata/checks | Permitted for qualified scope. Domain API Developers explicitly declare appropriate existing checks, restrict targets, and supply alias-aware/custom checks where necessary. No automatic metadata-copy or general equivalence guarantee is implied. |
| KlumCast resolves propagation transitively | Not selected; it introduces consumer-specific coupling without resolving the effective-state contract. |
| Supported post-batch effective-declaration operation | Preferred automatic route through #58 if its bounded assessment supports a small implementation; otherwise deferrable while manual coverage is qualified. |

**Manual route.** A Domain API Developer may repeat applicable KlumCast metadata on a domain annotation.
The original annotation remains on the Schema declaration, so its checks run through ordinary KlumCast
validation. Reuse the existing check implementations; do not add a second validator inside KlumAST.
For example, a field/method-only Cluster alias can declare its shape restrictions explicitly:

```groovy
import com.blackbuild.klum.ast.PropagateAnnotation
import com.blackbuild.klum.ast.layer3.Cluster
import com.blackbuild.klum.cast.KlumCastValidated
import com.blackbuild.klum.cast.checks.NeedsGenerics
import com.blackbuild.klum.cast.checks.NeedsReturnType
import com.blackbuild.klum.cast.checks.NeedsType
import java.lang.annotation.*
import java.util.Map

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@KlumCastValidated
@NeedsType(Map)
@NeedsReturnType(Map)
@NeedsGenerics
@PropagateAnnotation(Cluster)
@interface DomainCluster {}
```

This example is not a universal recipe. Checks on annotation members see the domain annotation's members,
not the copied annotation's defaults; members are still never forwarded. Coannotation checks must cover
explicit annotations and applicable domain aliases, including two different domains that contribute
conflicting types. Reciprocal checks may be needed. A bare Default cannot be made configured by setting a
same-named domain member. Narrow @Target declarations and documented supported combinations are valid
ways to bound an API; unqualified combinations cannot be advertised as validation-equivalent.

Exploratory temporary probes passed 16 cases per Groovy 3/4/5 lane (48 total): the primary AutoCreate/
DefaultValues case, Cluster shape restrictions and explicit/alias-aware conflict checks work in both
orders. They also reproduce missed member/default and peer-alias constraints when checks are incomplete.
These observations support feasibility only; durable API-specific tests and documentation remain admission
gates. Duplicate diagnostics can occur when both domain and copied checks run. Assess and document those
editorial limits separately; they do not justify bypassing a constraint or obscure source/provenance.

**Automatic route.** [KlumCast #58](https://github.com/klum-dsl/klum-cast/issues/58) is the conditional
cross-project prerequisite if this route is chosen. The supported operation validates the complete
effective annotation set despite prior validation, rechecks affected constraints and makes unchanged-state
requests no-ops. KlumCast owns state transitions and diagnostic aggregation/deduplication. API agreement,
qualified delivery and an approved released/development artifact route are required before integrating it.
Public engine symbols do not waive that supported contract. No caller may manipulate VALIDATED metadata.

Ownership: KlumAST owns eligibility, request discovery, default-only expansion, duplicate/nested errors,
introduced-annotation provenance and final DSL/generated compatibility. Under the manual route, Domain API
Developers own their domain check declarations, supported combinations, compatibility tests and guidance;
KlumCast executes those checks normally. Under the automatic route, KlumAST calls the supported post-batch
operation and KlumCast owns effective validation state, discovery/evaluation, affected-constraint rechecking
and diagnostic aggregation. KlumCast remains framework-neutral with no KlumAST/DSL dependency or propagation
engine. Neither route duplicates validation machinery inside KlumAST or introduces a runtime fallback.

The [plan's validation-route gate](../implementation/adr-0029-default-only-annotation-propagation.md#validation-route-gate-before-production-ap-1)
requires a bounded #58 complexity assessment and route decision before production authorization. #58 may
remain open as an enhancement when qualified manual checks meet the admitted initial scope. This ADR does
not change that issue's state, assign a release or authorize KlumCast production changes.

Correctness gaps block every affected capability, not just Cluster. Basic origin/selected/declaration
context is an implementation acceptance requirement. Further editorial improvements may be deferred only
once constraints and understandable locations/provenance are preserved. Same-source validated domain
annotations retain KlumCast's precompiled requirement. Java javac emits libraries; it does not run Groovy
AST transformations or Groovy use-site validation.

### D6 — Generated, runtime and compatibility contracts

Copied Query and Cluster affect generated contracts exactly as equivalent explicit annotations do. Verify
public Foo_DSL Builder/Factory surfaces, inherited capabilities, hidden implementations, cluster factories
and IDE source mirrors; mirrors are not another execution site or proof of editor alias recognition.
Do not change generated public APIs, Model/Builder separation or runtime construction/lifecycle machinery.
AutoCreate keeps its ordinary FactoryHelper, assignment, ownership, Templates and lifecycle ordering;
DefaultValues and handlers still consume the retained original domain annotation. No compiler state is
serialized into Models or Templates.

Changing propagation declarations requires recompiling consuming Schemas; this requirement is accepted.
Previously compiled Schemas keep their emitted annotations/code. Normal member-default evolution follows
ordinary annotation rules. No runtime interpreter, compatibility fallback or retrofit is introduced.

Qualify Java 17 and Groovy 3/4/5 with consistent annotation/compiler dependencies. Groovy 3 remains
classpath-only; named-module support is Groovy 4/5 under ADR 0014, without access-bypass JVM flags.
The temporary external service overlay cannot access the compiler-internal DslAstHelper from an unnamed
module on Groovy 4/5; the independent KlumCast engine access probe tests its automatic-module boundary.
Production must keep the helper call within the compiler module rather than widen exports. These probes
are not proof of production service/module packaging or a named Schema consumer. AP-4 must qualify shipped activation, library modules and runtime-only consumers. A real ScHelm
integration follows authorized production implementation, not this investigation.

## Consequences and exclusions

The feature is narrow Schema preprocessing. It is not general-purpose Groovy annotation composition.
Neither eligibility nor propagation touches unrelated declarations, and existing direct annotations keep
their transformation paths. Validation qualification is necessary even if C is rejected.

No forwarding, configured copies, merge rules, priorities, recursion, public SPI, ScHelm-specific policy,
new runtime machinery or general validation redesign. Issue #888 remains separate; this ADR does not
make AutoCreate legal on a class, imply defaults for unannotated fields or add an opt-out.

## Acceptance and decision boundary

The [evidence](../implementation/evidence/issue-890/schema-preprocessing.md) distinguishes observations,
negative probes, proposals and remaining production gates. The [plan](../implementation/adr-0029-default-only-annotation-propagation.md)
maps the contract to executable acceptance and reasoned slices. The historical generic probes remain a
technical baseline, not the recommended scope or evidence that KlumCast correctness is already satisfied.

Pending maintainer decisions: admit B over C; confirm interface exclusion; assess #58's complexity and
select/qualify the manual or automatic validation route for the initial admitted scope. API delivery and
artifact-route approval are conditional on selecting automatic integration. **AP-1 remains unauthorized**;
this design revision makes no implementation readiness, tracker/milestone/curation, merge or release claim.
Hive retains reconciliation ownership.
