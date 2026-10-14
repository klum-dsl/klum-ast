# Default-only annotation propagation on DSL Schema declarations

Date: 2026-10-13. Architectural refinement: 2026-10-14.

Status: Proposed. The public propagation contract is accepted in principle. DSL-only scope and the neutral
contributor architecture are confirmed; compiler feasibility and integration admission remain outstanding.

Implementation status: ADR and bounded feasibility evidence only. **KlumCast #58 is a hard AP-1
prerequisite; neither production implementation has started or is authorized.** Its neutral contributor
contract and mandatory Groovy 3/4/5 local-discovery probe must be qualified, then its API/artifact delivery
admitted, before AP-1 can be considered for explicit implementation authorization.

Release: Conditional 4.1 candidate, subject to Hive review; no milestone or curation change is made here.

Tracking issue: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Merged design investigation: [PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
Hard cross-project AP-1 prerequisite: [KlumCast #58 — Support effective-declaration validation after
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

The maintainer has now confirmed a neutral contributor contract for KlumCast #58: KlumCast owns semantic-
phase declaration traversal and invokes the contributor immediately before validating that declaration's
final effective annotation set once. KlumAST supplies one complete default-only propagation batch and
origin/provenance, and performs no mutation after handoff. This supersedes the earlier conditional #58 /
manual-validation proposal for the broad Schema class/field/method contract. Narrow manual-metadata probes
remain historical feasibility evidence, not an admission route or fallback for that contract.

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

### D4 — KlumCast owns traversal and validation; KlumAST contributes one batch

The confirmed compiler architecture uses KlumCast's semantic-phase declaration traversal as the owner.
Immediately before KlumCast validates a declaration, it invokes a framework-neutral contributor. For an
eligible DSL Schema declaration, the KlumAST contributor discovers requests, checks contribution legality,
and supplies exactly one complete default-only batch plus origin/provenance. Contribution completion is
the handoff: the KlumAST contributor performs no mutation after it. Ordinary DSL/local transformations
retain their existing phases and responsibilities. KlumCast then discovers and evaluates constraints
against the final effective set once, including the original domain and existing explicit annotations.

```text
KlumCast semantic-phase declaration traversal
  → invoke neutral contributor immediately before declaration validation
      → KlumAST DSL eligibility and request discovery
      → complete default-only batch; duplicate/nested diagnostics and provenance
      → contribution completes; no KlumAST mutation after handoff
  → KlumCast validates the final effective declaration once
      (state, constraint discovery/evaluation, filters, diagnostics/dedup/rendering)
  → normal Groovy local-transform discovery/execution
      (mandatory admission probe must establish this boundary on Groovy 3/4/5)
```

Separate propagation operations remain understandable and testable. No independently scheduled KlumAST
global propagation pass may establish validation ordering. The KlumAST implementation adds no public
preprocessing SPI, configurable priorities, generic transformation registry, reflective processor ordering,
second validation engine or runtime lifecycle machinery. The narrow neutral contributor contract belongs
to KlumCast #58 and contains no KlumAST/DSL types or consumer-specific semantic policy. No reverse or
consumer-specific cross-library dependency is introduced.

The earlier architectural comparison remains technical history:

| Approach | Evidence and disposition under the confirmed contract |
| --- | --- |
| A: generic global propagation | Historical scheduling baseline; arbitrary Groovy semantics and carried DSL remain outside scope |
| B: DSL-guarded independent global propagation | Early insertion can trigger local transforms, but independent validation ordering is insufficient; replaced by the KlumCast-owned contributor boundary |
| C: DSL-guarded Required/Optional coordinator | Actual converter calls can be ordered, but retained registrations double-lower and later validation can see false conflicts; no demonstrated mutual dependency warrants migration |

Required and Optional remain semantic-analysis local transformations, before canonical DSL processing.
Required maps explicit value/level to Validate.message/level; Optional adds Validate.Ignore. Both append
Validate without idempotency or source position. Cluster field conversion is semantic; Cluster projection
and DSL generation are canonical. Query is consumed by KlumCast and DSL/BuilderQuerySupport rather than
having its own independently registered Query transform. Moving these implementations is not part of this
contract. Normal local discovery, not direct coordinator calls, must consume contributor-added triggers.

The controlled order is contributor completion before KlumCast constraint discovery/evaluation on that
declaration. There is no universal ordering promise for independent global or same-phase local transforms,
third-party AST rewriting, or annotations introduced after handoff. The existing positive global-pass and
manual-metadata probes qualify neither the new contributor boundary nor effective validation.

**Mandatory admission probe.** Across Java 17 / Groovy 3/4/5, an annotation introduced through the actual
neutral contributor must remain discoverable by normal Groovy local transformations. The concrete KlumAST
sentinels are propagated Required, AutoCreate, Builder.Query and Cluster. Required must undergo its normal
local conversion exactly once; AutoCreate must create through the ordinary lifecycle; Query must preserve
Builder/Model behavior and generated contracts; Cluster must retain field/method projection and generated
factory contracts. Query/AutoCreate consumer behavior is required even though not every sentinel has its
own local-transform registration. Include a separately compiled foreign local transform as a direct
collection control, and direct-annotation comparisons, rather than only annotation-node inspection.

If that probe fails on a supported lane, **AP-1 must narrow under a new explicit scope decision or stop**.
Do not substitute an independently ordered global pass, late rescan, manual validation route, direct calls
to local transforms or another workaround. The admission probe is outstanding; this ADR does not claim
that KlumCast's proposed traversal position already meets Groovy's local collection boundary.

### D5 — KlumCast #58 is a hard prerequisite with a fixed ownership boundary

Current KlumCast 0.4.0 uses a global semantic pass, traverses actual validation meta-annotations, requires
compiled annotation reflection metadata and records VALIDATED on AnnotationNodes. Copying annotations
alone can miss a new constraint when KlumCast runs first; validating only copies can miss an affected
constraint on an already validated explicit annotation. Those negative probes explain the prerequisite,
not a consumer-side state protocol. **KlumAST must not clear, set or otherwise manipulate VALIDATED.**

The confirmed #58 contract prevents the ordering gap structurally: KlumCast owns traversal, invokes the
neutral contributor immediately before validation, and validates the complete effective set once after
one complete contribution batch. KlumCast owns validation state, constraint discovery/evaluation, filters,
diagnostics, aggregation/deduplication and rendering. Preserve normal classification/severity, annotation
and constraint identities, source/related locations, binding/composition paths and introduced-annotation
origin. Contribution diagnostics and constraint diagnostics must remain understandable together without
KlumAST duplicating the validation engine or rendering KlumCast's errors itself.

KlumAST owns DSL eligibility, propagation discovery, default-only expansion, duplicate/nested errors,
source/origin provenance and final DSL/generated compatibility. Its completed contribution must not mutate
the declaration later or depend on observing KlumCast's internal state. The original domain annotation
remains available to its ordinary consumers and independent constraints. Definition-time checks must not
be applied as if domain definitions were Schema fields or methods. Existing precompiled-library requirements,
normal retention/target legality and Java javac's ordinary library-only role remain unchanged.

The contributor contract stays framework-neutral: no KlumAST/DSL dependency, consumer annotation names,
propagation algorithm, generic transformation scheduler or consumer-specific validation logic belongs in
KlumCast. It is not a general plugin framework. KlumAST integrates only through the supported neutral
contract; public engine classes or automatic-module exports are not an alternative protocol.

Manual metadata on domain annotations can check narrow API shapes and may remain useful as ordinary
API-authored constraints. The temporary 48-case experiments also expose member/default mismatches,
peer-alias conflicts and repeated diagnostics. They do not qualify broad class/field/method propagation and
are no longer an alternative admission route. No runtime fallback or manual compatibility mode is added.

Before production AP-1: qualify the contributor/local-discovery admission probe on all three Groovy lanes;
agree and deliver the supported #58 contract with validation/diagnostic/module qualification; approve its
released or development artifact route; then obtain explicit AP-1 implementation authorization. The
[plan's gate](../implementation/adr-0029-default-only-annotation-propagation.md#contributor-admission-gate-before-production-ap-1)
records the dependency and stop condition. No implementation has started or is authorized by this design
update, and no issue, dependency version, release or curation state is changed here.

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
their transformation paths. The contributor/validation prerequisite is necessary even though C is rejected.

No forwarding, configured copies, merge rules, priorities, recursion, public KlumAST preprocessing SPI,
ScHelm-specific policy, new runtime machinery or general validation redesign. Issue #888 remains separate; this ADR does not
make AutoCreate legal on a class, imply defaults for unannotated fields or add an opt-out.

## Acceptance and decision boundary

The [evidence](../implementation/evidence/issue-890/schema-preprocessing.md) distinguishes observations,
negative probes, proposals and remaining production gates. The [plan](../implementation/adr-0029-default-only-annotation-propagation.md)
maps the contract to executable acceptance and reasoned slices. The historical generic probes remain a
technical baseline, not the recommended scope or evidence that KlumCast correctness is already satisfied.

Outstanding admission work: prove contributor-added local-transform discovery and sentinel behavior on
Groovy 3/4/5, qualify/deliver KlumCast #58's neutral contract, approve the cross-project artifact route and
obtain explicit AP-1 authorization. The confirmed contributor architecture replaces the earlier manual/
independent-global alternatives. **AP-1 remains blocked and unauthorized**; neither library's production
implementation has started or is authorized here. Hive retains reconciliation ownership.
