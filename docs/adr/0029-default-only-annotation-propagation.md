# Default-only annotation propagation on DSL Schema declarations

Date: 2026-10-13. Architectural refinement: 2026-10-14.

Status: Proposed. The public propagation contract is accepted in principle. DSL-only scope is confirmed;
compiler architecture and KlumCast integration still require maintainer admission.

Implementation status: ADR and bounded, opt-in feasibility probes only. **AP-1 is blocked by
[KlumCast #58](https://github.com/klum-dsl/klum-cast/issues/58) and is not authorized.** Its supported API
must be agreed, delivered and qualified, and the cross-project development artifact route approved,
before production AP-1 can be considered for authorization.

Release: Conditional 4.1 candidate, subject to Hive review; no milestone or curation change is made here.

Tracking issue: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Merged design investigation: [PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
Blocking cross-project prerequisite: [KlumCast #58 — Support effective-declaration validation after
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

Recommend **approach B**, blocked on D5's supported KlumCast integration through KlumCast #58. One internal
global entry at semantic analysis checks DSL eligibility, discovers propagation requests and attaches
one complete authorized default-only batch per declaration before Groovy's local-transform discovery.
After the batch, KlumAST must invoke the future narrow, idempotent effective-declaration validation
operation. KlumCast owns constraint discovery and evaluation against that effective state, including
affected existing constraints. Neither insertion nor an independent global pass's relative scheduling
qualifies validation. Keep these operations separate and testable. The intended sequence is:

```text
existing DSL eligibility
  → collect requests and check contribution legality
  → append complete default-only batch with provenance
  → invoke supported KlumCast #58 effective-declaration handoff
      (KlumCast owns validation state, constraint recheck and diagnostic aggregation)
  → Groovy local collection/execution (existing Required, Optional, Cluster, DSL)
```

This is not a public preprocessing SPI, configurable priority system, plugin registry, reflective ordering
scheme, second DSL framework or runtime lifecycle. Source/generated declaration snapshots and per-node
bookkeeping prevent another expansion on projections. No repeated whole-compilation scan is required.

| Concern | A: generic global baseline | B: DSL-guarded propagation | C: guarded Required/Optional coordinator |
| --- | --- | --- | --- |
| Scope | Violates revised DSL-only requirement | Matches it | Matches it if correctly guarded |
| Phase/discovery | Early semantic insertion discovers local triggers | Same proven scheduling seam | Same seam; direct calls order only coordinator-owned operations |
| Required/Optional ordering | Existing semantic locals precede canonical DSL | Already sufficient; no mutual dependency found | Fixed order callable, but no additional dependency demonstrated |
| Validation | Same global-order bypass risk | Requires D5 integration | Requires D5 plus validation-before-lowering; otherwise false Required/Validate conflicts |
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
KlumCast constraint correctness must come from the supported post-batch handoff, regardless of whether
its independent global validation pass ran earlier. Positive Required/Cluster scheduling and generated
behavior probes are feasibility evidence only; they do not qualify effective-declaration validation.

### D5 — KlumCast correctness is a prerequisite, not an assumed consequence of copying

KlumCast 0.4.0 has a global semantic pass. It discovers validation bindings transitively through actual
meta-annotations (KlumCastValidated, KlumCastValidator, CheckBinding); it does not follow Class-valued
propagation requests. The engine validates the actual annotation use and explicitly supplied members,
uses compiled annotation reflection metadata, and records VALIDATED on the AnnotationNode. Constraint
annotation definitions are skipped as use sites when they carry validation bindings. Selected-type
metadata and the original domain annotation have distinct validation roles.

The probes demonstrate two release-blocking failure modes:

1. KlumCast first, propagation second: a copied bare Default misses its configuration constraint.
2. Validating only copies: an existing Key already marked VALIDATED misses a subsequently copied
   Field conflict. Complete effective-declaration validation must recheck affected existing constraints.

Calling the existing ValidationHandler engine proves applicable checks can be preserved, with provenance
and original source coordinates. Experimental metadata invalidation demonstrates the second gap can be
closed, but is not a supported production protocol and can duplicate diagnostics already reported by the
first pass. **KlumAST must not clear, set or otherwise manipulate KlumCast VALIDATED metadata.** The
test-only experiment is not an implementation option. Do not publish propagation using only the new-copy
check or an assumed relative global-transform order.

| Integration | Assessment |
| --- | --- |
| A: copy KlumCast meta-annotations onto the domain annotation | Reject as primary solution. Source ClassNode mutation cannot alter precompiled reflection metadata; member constraints would address domain members instead of the effective selected annotation. Definition-time and use-site constraints can be confused. |
| B: KlumCast resolves propagation transitively | Possible with a separately agreed integration seam, but requires reading a KlumAST-specific request graph or a new protocol and still handling effective target/member semantics. Not currently supported; unnecessary library coupling for this bounded need. |
| C: preprocessing requests validation through KlumCast's existing engine | Recommend a supported, narrow effective-declaration validation entry/handoff. KlumAST owns expansion, target/default checks and origin; KlumCast owns constraint discovery, checks, validation state and diagnostic deduplication. No duplicate validator implementation. |

**KlumCast #58 is the explicit blocking cross-project prerequisite**, owned and implemented independently.
It must establish a supported compiler operation for validating a declaration against its complete
effective annotation set after the caller's authorized preprocessing batch, despite prior validation.
Repeated requests against unchanged effective state must be no-ops. It must preserve structured diagnostics,
composition paths, source/related locations, coannotation constraints and at-most-once reporting. Any
internal state transition or invalidation is owned by KlumCast, never performed by the caller.
The current engine class and metadata are public Java symbols, but not an established mutation protocol.
Current KlumCast artifacts are automatic modules exporting their packages; this does not establish a
future encapsulation contract. Any explicit descriptor must export/qualify the agreed integration seam.

Ownership is fixed: KlumAST owns DSL eligibility, propagation request discovery, default-only expansion,
duplicate/nested errors, introduced-annotation provenance, the post-batch call and final DSL/generated
compatibility. KlumCast owns effective validation state, constraint discovery/evaluation, affected-constraint
rechecking and diagnostic aggregation/deduplication. Neither library duplicates the other's validation logic.
KlumCast #58 remains framework-neutral: no KlumAST/DSL dependency, consumer annotation knowledge,
propagation engine or preprocessing scheduler belongs there.

KlumAST's subsequent AP-1 consumes only the supported contract agreed and delivered through #58, using a
released artifact or an explicitly approved cross-project development artifact route. API agreement,
qualified delivery and artifact-route approval are gates before production AP-1, not follow-up cleanup.
The [plan's KC-0 gate](../implementation/adr-0029-default-only-annotation-propagation.md#kc-0--supported-effective-declaration-validation-prerequisite)
records the dependency. Current public engine symbols do not waive it. No KlumCast production code or
dependency version is changed here.

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
their transformation paths. The validation prerequisite is necessary even if C is rejected.

No forwarding, configured copies, merge rules, priorities, recursion, public SPI, ScHelm-specific policy,
new runtime machinery or general validation redesign. Issue #888 remains separate; this ADR does not
make AutoCreate legal on a class, imply defaults for unannotated fields or add an opt-out.

## Acceptance and decision boundary

The [evidence](../implementation/evidence/issue-890/schema-preprocessing.md) distinguishes observations,
negative probes, proposals and remaining production gates. The [plan](../implementation/adr-0029-default-only-annotation-propagation.md)
maps the contract to executable acceptance and reasoned slices. The historical generic probes remain a
technical baseline, not the recommended scope or evidence that KlumCast correctness is already satisfied.

Pending maintainer decisions: admit B over C; confirm interface exclusion matches the existing Schema
boundary; agree and qualify the supported integration under blocking KlumCast #58 and approve its
cross-project artifact route. Until those gates are met,
**not ready for AP-1 implementation authorization**. Publishing this design revision does not authorize
production code, tracker/milestone/curation changes, merge or release. Hive retains reconciliation ownership.
