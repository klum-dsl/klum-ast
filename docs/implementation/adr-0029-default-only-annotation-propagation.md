# ADR 0029 implementation plan: default-only annotation propagation

Date: 2026-10-13. Status: Local plan for Hive review; no production implementation authorized.
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md), with confirmed maintainer semantics
and the [decision brief](issue-890-lifecycle-composition-decision-brief.md).
Tracking issue: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Release: Conditional 4.1 candidate; Hive owns tracker/release reconciliation.

Design base: master `dea21a7cfa9b26630cacc5cdbc1087e756e59ce6`, unchanged when this plan was drafted.
The existing evidence branch contains only documentation and opt-in investigation fixtures. Do not
promote its temporary global transform directly into production or treat its positive probes as complete
diagnostic qualification. Future implementation starts from the admitted base with normal branch checks.

## Current behavior and evidence limits

Explicit AutoCreate plus a domain mutator already creates before mutation and preserves supplied children.
ADR 0028 source and binary defenses reject competing direct creators per field/phase. Cluster fallback
runs after direct-field participants without rerunning mutations. DefaultValues reads nondefault members
of the retained domain annotation; no lifecycle handler is needed for that consumer.

No production PropagateAnnotation exists. AutoCreate cannot currently decorate a domain annotation,
Creator requires a concrete handler, and a late copying customizer misses Required local-transform
discovery. A temporary global transform registered before local collection resolves the demonstrated
scheduling seam: class DSL, method Query, field/method Cluster and Required conversion pass on all lanes.
Repeated single propagation markers and explicit containers in separately compiled domain metadata also
pass. The fixture has no production duplicate/nesting defenses or shipping service/module registration.

ScHelm review relayed by Hive accepts the narrow contract for its present concrete unkeyed direct children:
OrderApplication.messaging/database/workload/api and OrderKafka.orderTopic/resultTopic. It reports no
need for AutoCreate parameters, coexistence with explicit AutoCreate, forwarding or nesting. FactBinding
currently implies fixed structure; a future bind-if-present case can use a binding-only domain annotation.
KafkaTopicDefaults/ResourceClassDefault/HttpEndpointDefaults retain their existing DefaultValues inputs.
This is externally reported consumer-fit evidence, not execution of ScHelm's tests by this worker.

## Affected seams

| Module / seam | Planned responsibility |
| --- | --- |
| klum-ast-annotations | JDK-only PropagateAnnotation and nested repeatable List; normal exports/Javadoc |
| klum-ast compiler | New global pre-discovery pass; class/field/method discovery, source and binary marker/container normalization, declaration diagnostics and internal provenance |
| Compiler service/module packaging | Add the required global discovery entry and qualify named-module discovery/access; do not assume a temporary classpath service file proves shipped activation |
| DSLASTTransformation / BuilderQuerySupport | Consume copied DSL/Query through the normal pipeline; verify generated parity and existing purity/category checks |
| ClusterFieldTransformation / ClusterTransformation | Preserve early field-to-getter conversion, method projection, cluster factories and shape diagnostics |
| KlumCast annotation validation | Observe ordinary copied annotations after insertion; preserve target/member/type/retention and semantic failures |
| Runtime AutoCreationPhase / DefaultPhase / LifecycleParticipants | Regression coverage only; ordinary physical annotations and original domain instances supply existing behavior |
| GeneratedDslSupport / source mirrors | Compare with explicit controls; no separate alias metadata vocabulary or public generated API |
| Existing fixtures / user documentation | Reuse compiler, artifact, JPMS, route and documentary seams; final user guidance under docs/user |

No runtime interpreter, dependency reversal, public handler token or new lifecycle phase is planned.

## Implementation rules

- Each slice is a thin vertical change with meaningful tests in the same reasoned commit. Avoid a
  standalone published annotation that silently does nothing before its compiler path exists.
- New executable classes use the Test suffix and @Issue('890'). Documentary cases carry @Tag('documentary')
  and @See links to current docs/user sources. No pending/ignored test is introduced to hide an undecided
  or unimplemented public contract.
- Use Groovy 3 for focused feedback. Run Groovy 4/5 when exercising ordering/AST/version seams and at each
  slice's final boundary. Final qualification includes the required repository check and normal reviews.
- Preserve accepted ADR 0020/0028 semantics. A necessary runtime or generated-contract change beyond
  equivalent explicit annotation input requires a named design exception, not incidental refactoring.

## Dependency-ordered slices

AP-1 → AP-2 → AP-3 → AP-4 → AP-5. Early foreign-transform/IDE exploration may inform AP-1 architecture,
but does not waive AP-4's artifact and authoring evidence. No implementation slice is authorized here.

### AP-1 — Public marker and minimal early compiler producer (D1, D2, D4)

Scope: PropagateAnnotation.value(): Class<? extends Annotation>, documented runtime-retained repeatable
List, and one global producer operating after resolution and before local-transform collection. Discover
original class/field/method declarations without requiring an existing DSL annotation. Preserve domain
instances/arguments, copy fresh default-valued annotation nodes and source positions, and record per-node
processing so repeated compiler visits do not manufacture additional contributions.

Do not expand a domain annotation definition merely because it declares requests, or rescan generated
Builder twins/getters/source mirrors. No parameter/package/constructor/type-use support. Integrate the
basic rejection path from AP-2 before exposing any invalid input as a silent success.

Primary tracer: a DefaultValues-only domain annotation carries AutoCreate; an unset concrete unkeyed
child is created and receives an explicit domain default, while supplied child values survive. A second
case adds a participant and observes its original domain annotation, not a reconstructed one.

Use previously compiled annotations where ordinary KlumCast validation already requires them. A simple
domain annotation without that prerequisite supplies the same-source expansion control; do not broaden
validated-annotation compilation prerequisites as part of propagation.

Acceptance: fresh/null and supplied inputs; original and copied runtime annotation presence; omitted
domain-member behavior unchanged; source versus previously compiled domain metadata; class/field/method
producer activation; marker/container reflection descriptors without Groovy dependencies.

Include SOURCE/CLASS/RUNTIME domain-retention controls: preserving a domain annotation does not upgrade
its declared retention. Runtime consumers keep their existing runtime-retention checks.

Commit boundary: public contract, global activation, minimal guards and factory-based tracer together.
Gate: both compiled-domain and source-domain paths execute ordinary consumers. This is not an independently
releasable feature until AP-2–AP-5 pass.

### AP-2 — Complete deterministic declaration diagnostics (D2, D3)

Scope: normalize repeated singular requests and explicit List containers before expansion; count all
contributions before mutation of the target declaration. Diagnose duplicate, nested, invalid-type/target
and unrepresentable default-only requests with domain, selected annotation and actual declaration context.
Retain ordinary semantic validators for copied annotations rather than reimplementing their policies.

| Input | Required outcome |
| --- | --- |
| Explicit AutoCreate + propagated AutoCreate, even identical | Compilation failure; identify duplicate type and target |
| Two requests or two domain annotations selecting the same type | Same failure; no reflection-order winner |
| Propagated duplicate of a repeatable selected annotation | Failure; propagator repeatability is not a copied-annotation duplicate exception |
| Explicit-only ordinary repeatable annotations | Existing behavior unchanged |
| Selected annotation itself declares propagation / self or cycle | Nested propagation failure, with no recursion |
| Required annotation member has no default | Compilation failure naming the missing member/type; no synthetic value |
| Selected non-annotation class, wrong target or unsupported placement | Usable ordinary or propagation diagnostic; no compiler crash |
| Copied AutoCreate + external AutoCreate Creator | Existing competing-creator failure, including supplied field inputs |
| Copied Query + explicit Builder.Method | Existing incompatible-category failure |
| Copied bare Default on a field | Existing field/delegate/code requirement failure |
| Propagated marker combined with ordinary annotation aliases | No silent precedence/merging that defeats duplicate failure; qualify the combination or diagnose it |

Exercise all three declaration targets and both source and compiled domain libraries. Recognize legitimate
repeatable applied-domain containers to count their contributions; two occurrences of a propagating domain
cannot disappear behind an implicit or explicit container. Container normalization is not nested propagation,
and does not add runtime participant/default-value support beyond existing consumers.

Commit boundaries: declaration guards and negative tests as one focused step; an additive container/provenance
step only if it makes the reasoning clearer. Every intermediate feature boundary rejects unsupported cases.
Gate: no no-op/crash/precedence case in the matrix; diagnostics do not depend on eventual runtime field values.

### AP-3 — Query, Cluster and lifecycle/generated equivalence (D2, D4, D5)

Scope: qualify the stated class/field/method contract through the ordinary KlumAST generation path.

- Class annotation carrying DSL: normal root factories/public Builders; invalid ordinary DSL shapes still fail.
- Method annotation carrying Builder.Query: reads current Builder state and completed Model; keep purity,
  result typing, conflicting-category diagnostics and public projected signatures.
- Field and method annotations carrying Cluster: projection getters, generated cluster factories, generics,
  null inclusion and ordinary shape/static/interface restrictions. A field-to-getter rewrite must not trigger
  another expansion or lose its retained domain/copy annotations under the existing projection rules.
- Required: existing converter is discovered; message/level, primitive-boolean diagnostics and runtime
  truth semantics remain unchanged. Do not add its optional runtime refactor to this slice without evidence.
- Direct AutoCreate/participant/Cluster controls: supplied values, inherited fields, parent-before-child,
  callback sequence, cluster fallback and same-phase conflicts. Keyed/abstract/defaultImpl failures and
  explicit key/type/value behavior remain the existing baseline, not inferred new features.
- Preserve unchanged normal class-level DefaultValues/owner-provided/field precedence and sealed FAIL/SKIP.

Compare each propagated case with equivalent explicit input: generated public descriptors, child/cluster
operations, original runtime metadata, source-mirror annotations and Model-versus-Builder method presence.
At inheritance seams, process declared source nodes once; inherited members are not recopied into subclasses.
Different inherited annotation types retain their ordinary annotation-type rules rather than a fabricated
propagation inheritance policy. Existing shadowing rejection remains authoritative.

Suggested existing seams: BuilderQueryTest, ClusterTransformationTest, GeneratedDslSupportSpec,
DefaultValuesSpec, LifecycleParticipantPhaseTest, FieldPhaseTraversalCharacterizationTest and
AutoCreateDSLTest. Add new focused tests without broad fixture duplication.

Commit boundaries: generated Query/Cluster/type parity and tests; lifecycle/default regression qualification
may follow as a separate green evidence/test step. Gate: ordinary explicit behavior and generated API agree.

### AP-4 — Binary, Groovy/JPMS, routes and authoring qualification (D4–D6)

Scope: a real domain-library → Schema → consumer topology. Compile Java and Groovy marker/domain libraries,
source and separately compiled source consumers, and Java/static/dynamic Groovy runtime consumers.
Execute after removing source/classes where existing artifact fixtures do so; runtime classpath excludes
klum-ast compiler. Reflection and runtime defaults/handlers must see copied and retained annotations.

Required gates:

1. Groovy 3/4/5 lane isolation and ordinary imported/aliased nested types, source repeats and binary containers.
2. Shipped global discovery, including Groovy 4/5 named modules using normal exports/opens and matching
   Groovy module identities. Preserve the Groovy-free annotations module and runtime exports.
3. A separately compiled non-Klum local transform using a default-valued field/method annotation. Prove
   discovery and effect; characterize other global-transform interaction before claiming generic ordering.
   The documented guarantee is pre-local-discovery expansion, not arbitrary ordering against every global pass.
4. Binary-change control: compile Schema with a request, change its domain library's request metadata,
   execute unchanged Schema to establish no runtime retrofit, then recompile Schema and observe the new
   copies/generated behavior. Newly introduced duplicates fail on recompilation. Ordinary existing #867
   runtime defenses still handle independently changed participant metadata; propagation does not replace them.
5. Template/copy/FromMap/Jackson route and completed serialization controls against explicit equivalents.
   Existing ownership/session/materialization and handler/context nonretention remain intact.
6. Native IDE evidence for source and binary Schema topologies, Query and Cluster authoring/completion and
   generated source mirrors. Do not equate generated descriptors with uncompiled source PSI recognition or
   add a broad GDSL alias framework to make the claim true. Record any authoring limitation for Hive review
   before final acceptance or publication.

Suggested fixtures: LifecycleParticipantConsumerTest and RoutesTest, JpmsPackageBoundaryTest, the existing
artifact/scenario infrastructure, and the portable IDE/source-mirror evidence seams. ScHelm's current fit
report guides the examples but does not substitute for these framework compatibility tests.

Commit boundaries: artifact and compatibility coverage plus any minimal packaging corrections; separate
native IDE/evidence reconciliation when no production change is needed. If evidence requires changing
scope or contracts, stop for a maintainer decision rather than broadening this ADR silently.

### AP-5 — Documentary contract and delivery readiness (D1–D6)

Scope: a focused AnnotationPropagationDocumentaryTest with @Issue('890'), @Tag('documentary') and @See.
Include DefaultValues + AutoCreate without participants; retained participant composition; method Query
and field/method Cluster examples. Choose readable hypothetical domain vocabulary; ScHelm-specific provider
selection is not framework API. Demonstrate duplicate/nesting/default-only limits in adjacent focused tests.

User guidance belongs in docs/user: Default-Values.md, Model-Phases.md and the existing Builder.Query/Cluster
pages as applicable. A standalone propagation page is appropriate if it improves navigation; then update
_Sidebar.md. Link feature documentary tests with the existing conventions. Synchronize CHANGES.md, relevant
Migration.md navigation/minimum-compiler guidance, final ADR/public inventory and implementation evidence.

Gate: local focused/module checks plus Groovy 4/5 and final required repository checks; standards/specification
review under repository policy; history review before any first publication. Remote CI/SonarCloud qualification
is a later authorized publication gate and must identify the final published revision. No claim that the
current documentation branch has satisfied those feature-delivery checks.

Commit boundary: final user guidance/evidence may be a separate commit after green implementation/test steps.
Hive selects tracker relationship and release treatment before any authorized draft PR; this plan does not
close #890, update its body or choose a milestone.

## Requirement-to-slice map

| Confirmed requirement | Slice / acceptance |
| --- | --- |
| Generic repeatable single marker; JDK-only public interface | AP-1 descriptors and domain-library controls |
| Retained original annotation, default-only copies, no forwarding | AP-1 factory tracer; AP-2 missing-member diagnostics |
| DefaultValues-only domain can imply AutoCreate; optional participants | AP-1; AP-3 ordinary precedence and participant regressions; AP-5 documentary |
| Class/field/method targets; Query and Cluster | AP-1 activation; AP-3 generated parity; AP-4 binary/IDE |
| One level; nested requests rejected | AP-2 source and binary-domain matrix |
| Duplicate failure, explicit or multiple sources | AP-2 contribution matrix and semantic-conflict controls |
| Existing AutoCreate / Creator / key/type/value behavior | AP-3 baseline and lifecycle controls |
| Recompile Schemas after propagation metadata changes | AP-4 binary-change/recompile control; AP-5 migration guidance |
| ScHelm current fit; no extra customization/nesting | External review recorded in brief; AP-1/AP-5 concrete-unkeyed examples |
| Preserve generated/runtime/module/serialization contracts | AP-3 descriptor parity; AP-4 routes, modules and runtime-only consumers |
| #888 separate | No class-wide creation/default/opt-out in any slice; AP-5 guidance names the distinction |

## #888, risks and open gates

[#888](https://github.com/klum-dsl/klum-ast/issues/888) remains an independent class-level default policy.
No slice creates unannotated fields, changes AutoCreate's legal targets, adds a class-wide child filter or
opt-out, or couples Required/Optional validation to structural existence. Class-level annotation expansion
in #890 is ordinary copying subject to target validation, not class-level AutoCreate semantics.

Principal technical risks are global discovery/module packaging, annotation processing by other compiler
passes, repeated-container normalization, projection idempotence and source IDE recognition. They have
explicit AP-2/AP-4 gates. The existing positive probes cover neither all foreign transforms nor these
negative/authoring/artifact cases.

Before implementation admission, Hive reviews this proposed ADR/plan and reconciles #890's older candidate
wording with the now-confirmed generic propagation scope. Release placement and production/remote authority
remain outside this document. No substantive current ScHelm API requirement is reported unresolved.

## Validation of this documentation draft

This continuation changes only the ADR, plan and decision brief. Applicable checks are diff whitespace,
relative file links/heading anchors, scope/requirement reconciliation and Markdown rendering sanity.
Existing 17-case-per-lane feasibility and 56-case-per-lane focused baseline results are retained historical
evidence; no Groovy lane or full repository check is claimed for these prose-only edits.

Result: all 18 local file links/anchors and fenced blocks passed. All three documents rendered through
CommonMark with GFM tables. A scope check found exactly the three authorized Markdown documents;
requirement/consumer-evidence review and diff whitespace checks passed. No executable fixture changed.
