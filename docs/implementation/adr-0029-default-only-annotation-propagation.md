# ADR 0029 implementation plan: DSL-only annotation propagation

Date: 2026-10-13. Revised: 2026-10-14.
Status: AP-1 implemented locally against public KlumCast `0.5.0-rc.2`, Central-only. Current focused
Java 17 / Groovy 3/4/5 and artifact/module evidence passes; see the
[current qualification record](issue-890-ap1-rc2-evidence.md) for full-check and delivery status.

## AP-1 authorization and current implementation boundary (2026-10-14)

The maintainer authorized the production default-only contributor, then replacement of the isolated
route with normal Central-only exact RC.2 resolution. RC.2 supersedes all development/Maven-Local
artifacts and RC.1 as current qualification input. The ordinary Gradle classloader gate now passes.
The public marker and production contributor are paired, with guards and retained provenance; ordinary
local transformations keep their phases. No workaround or second validation engine was introduced.

The original slices and gate sequence below remain the approved contract. Statements describing
historical authorization/admission status are superseded by this dated status. Editor recognition,
actual ScHelm admission and final release reconciliation remain separate; issue #890 stays open.

Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md).
Tracking: [#890](https://github.com/klum-dsl/klum-ast/issues/890), merged design investigation
[PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
Hard cross-project AP-1 prerequisite: [KlumCast #58 — Support effective-declaration validation after compiler AST
preprocessing](https://github.com/klum-dsl/klum-cast/issues/58).
Evidence: [scheduling, eligibility and KlumCast probes](evidence/issue-890/schema-preprocessing.md).
Consumer history: [decision brief](issue-890-lifecycle-composition-decision-brief.md).
Release remains conditional; no tracker, milestone or curation state changes are part of this plan.

## Current behavior and admission boundary

Explicit AutoCreate plus a domain mutator already creates before mutation and preserves supplied children.
DefaultValues consumes the original annotation's nondefault members without requiring a participant.
The ScHelm fit review accepts bare AutoCreate for current concrete unkeyed relationships; it is reported
consumer evidence, not a locally executed integration. Issue #888's class-wide defaults remain separate.

No public PropagateAnnotation or production propagation entry exists. Historical generic/guarded global
probes show that early insertion can trigger local transformations; a late semantic customizer can miss
Required discovery. These probes do not qualify the new contributor contract. Manual-metadata experiments
cover narrow shapes but expose member/default, peer-alias and duplicate-diagnostic limitations; they are
historical feasibility evidence, not an AP-1 admission route for broad class/field/method propagation.

**Confirmed architecture:** KlumCast owns semantic-phase declaration traversal. Immediately before it
validates a declaration, it invokes a neutral contributor. KlumAST supplies exactly one complete default-
only propagation batch and origin/provenance, then performs no further contribution mutation after handoff.
KlumCast discovers/evaluates constraints against the final effective set once. Required and Optional stay
local; no direct converter invocation or independently scheduled propagation pass establishes the ordering.

KlumCast owns validation state, constraint discovery/evaluation, filters, diagnostics/deduplication and
rendering. KlumAST owns propagation discovery, expansion, duplicate/nested diagnostics/provenance and final
DSL/generated compatibility. No raw VALIDATED manipulation, second validation engine, consumer-specific
cross-library dependency or KlumCast/DSL dependency is allowed.

## Affected seams and ownership

| Seam | Responsibility |
| --- | --- |
| klum-ast-annotations | JDK-only public repeatable marker/container; normal exports and Javadoc |
| DslAstHelper / DSLASTTransformation | Reuse existing eligibility and interface boundary; no second framework |
| KlumCast semantic traversal / neutral contributor boundary | KlumCast invokes the contributor immediately before validating a declaration’s final effective set once |
| Separate internal propagation operation | KlumAST owns request discovery, genuine container normalization, defaults-only expansion, duplicate/nested errors and introduced-annotation provenance |
| KlumCast compiler / #58 | Own validation state, constraint discovery/evaluation, filters, diagnostics/aggregation/deduplication and rendering |
| Required / Optional annotations and converters | Preserve registrations, semantic phase, message/level/Ignore behavior and non-DSL compatibility |
| Cluster field conversion and projection | Existing semantic then canonical paths; preserve field-to-getter projection and factories |
| BuilderQuerySupport / generated DSL support | Existing Query processing, public Builder/Factory parity and source mirrors |
| Runtime AutoCreationPhase / DefaultPhase / participants | Regression only; no interpreter or lifecycle changes |
| Compiler services and JPMS | Shipped discovery and normal module access; temporary fixture service files are not production registration |

KlumCast is a separate library. Its contributor contract contains no KlumAST/DSL types, consumer annotation
names or propagation algorithm. KlumAST supplies the complete batch through that neutral contract and ends
its contribution mutation at handoff; ordinary local DSL transformations keep their existing phases.
KlumCast owns semantic traversal and validates the final effective declaration once, with no duplicated
validation inside KlumAST. Existing public engine symbols are not an alternate integration protocol.
Do not change issues, release indexes or dependency versions during this documentation update.

## Dependency-ordered slices and reasoned commits

KlumCast #58 / KC-0 contributor admission probe → supported contract qualification/delivery → approved
artifact route → explicit AP-1 authorization → AP-1 → AP-2 → AP-3 → AP-4 → AP-5.
No manual validation route substitutes for KC-0 for the current broad Schema class/field/method scope.
Keep dedicated issue branches, small behavior-and-test commits, and reviewed history intact. Each future
slice runs baseline Groovy 3 focused tests and final Groovy 4/5 checks using Java 17 artifacts. New tests
carry @Issue('890'), Test suffixes and documentary @Tag/@See links for the eventual user-facing feature.
Do not introduce ignored/pending tests as a substitute for an unresolved or failed contract.

## Contributor admission gate before production AP-1

The contributor contract and compiler-phase boundary are now qualified by the current RC.2 production
evidence. The original gate required an independently authorized probe demonstrating normal local
collection on Groovy 3/4/5 through observable generated behavior and direct controls, not only nodes.
The table remains the acceptance contract for regression and eventual-artifact requalification.

| Sentinel | Required contributor-path evidence in every lane |
| --- | --- |
| Required | Normal local converter discovered and runs exactly once; Validate/message/level and generated/runtime validation match direct use |
| AutoCreate | Copied annotation remains effective through ordinary DSL/lifecycle consumers; a child is created with normal DefaultValues/participant behavior |
| Builder.Query | Normal validation/DSL processing preserves Builder-state and completed-Model queries, public Builder/Factory signatures and source mirrors |
| Cluster | Normal semantic field conversion and canonical projection/factory generation run; field/method shape checks and generated contracts match direct use |
| Separately compiled foreign local transform | Contributor-added trigger is collected normally, yields the direct observable generated effect once, and does not alter unrelated direct uses |

Query and AutoCreate are consumer sentinels even where they do not have their own local-transform
registration; Required/Cluster and the foreign control prove collection as well as downstream behavior.
Include separately compiled Java/Groovy domain libraries, request containers, inherited declarations and
non-DSL/no-contribution controls. Preserve Groovy 3 classpath and applicable Groovy 4/5 JPMS boundaries.
The shipped contributor activation/module contract remains a separate artifact qualification gate.

**Failure condition:** if contributor-added local discovery or a required sentinel fails on any supported
lane, AP-1 must narrow under an explicit maintainer scope decision or stop. Do not substitute independently
scheduled globals, late rescans, direct transform calls, manual validation or another workaround. Earlier
positive global/manual probes do not waive this gate. The original documentation update authorized no
implementation; later authorization and exact RC.2 qualification are recorded in the current status.

### KC-0 — KlumCast-owned traversal and neutral contributor prerequisite

Owner: KlumCast, under [#58](https://github.com/klum-dsl/klum-cast/issues/58), with separate implementation
authorization. The generic contract stays framework-neutral; no KlumAST/DSL dependency or consumer policy.

KlumCast traverses declarations during semantic analysis and invokes the contributor immediately before
validating the declaration. KlumAST contributes one complete default-only batch and origin/provenance and
performs no later contribution mutation. KlumCast then discovers/evaluates all applicable constraints on
the final effective set once, retaining normal filters, library prerequisites and definition/use separation.
KlumCast owns validation state, diagnostics/deduplication/rendering; no caller manipulates internal metadata.
The contract specifies ownership and timing, not an unqualified public method/interface shape.

Acceptance:

- Direct annotations and declarations without contributions retain ordinary behavior, including non-DSL uses.
- Original domain, introduced and existing explicit annotations are checked from the final effective set;
  new Default constraints and existing Key/Field or Required/Validate conflicts cannot bypass validation.
- Contribution completion precedes constraint discovery/evaluation within KlumCast's traversal. There is
  no reliance on relative global-transform order and no mutation by the contributor after handoff.
- Preserve normal filters, classification/severity, annotation/constraint identities, source/related
  locations, composition paths and introduced-annotation origin. Aggregate/render violations in KlumCast
  and report each effective violation at most once without a second KlumAST renderer/constraint engine.
- KlumCast owns at-most-once final validation and unchanged-state behavior; repeated traversal/handoff
  must not rerun the contribution or duplicate diagnostics. The caller does not inspect/mutate VALIDATED.
- Required/Optional retain their normal local conversion without false generated-Validate conflicts.
- The mandatory contributor/local-discovery sentinels above pass across Java 17 / Groovy 3/4/5, with
  separately compiled Java/Groovy libraries and applicable JPMS access.

Reasoned boundaries: first separately authorize and execute the admission probe; then agree the supported
neutral API and implement/qualify KlumCast's traversal/state/filter/diagnostic behavior in its own focused
change. Preserve at-most-once validation against the final set rather than adding a consumer revalidation
engine. Neither that implementation nor AP-1 has started or is authorized here.

Gate before production AP-1:

1. Qualify the contributor/local-discovery admission probe and all concrete sentinels on Groovy 3/4/5;
   narrow or stop on failure, with no workaround substitution.
2. Agree, deliver and qualify KlumCast #58's supported neutral contract, including state, filters,
   diagnostics/dedup/rendering, precompiled-library behavior and normal module access.
3. Approve a released or explicit cross-project development artifact route with reproducible identity and
   consistent consumer resolution. Local publication/unreleased dependencies are not implicitly approved.
4. Obtain explicit AP-1 implementation authorization, then consume the artifact and qualify integration.

The earlier manual-route cost/deferral option is superseded for this broad contract. No dependency gate is
waived by temporary metadata-removal or manual-check experiments. Further editorial work is deferrable
only once correctness and understandable provenance meet the supported contract.

### AP-1 — Narrow vertical tracer and public marker

Only after KC-0 contributor admission, supported API qualification/delivery, approved artifact route and
explicit production authorization. Introduce the marker/container and minimal KlumAST contributor together,
so no standalone published marker silently does nothing. Keep eligibility, request normalization,
contribution checks and default-only expansion separate internal operations.

Reuse direct DSL detection and the existing interface boundary. Snapshot owner-declared classes/fields/
methods, exclude generated/inherited declarations as expansion sites and reject DSL as a selected target.
The contributor returns no contribution for unrelated declarations or no-work Schemas. It supplies exactly
one complete authorized default-only batch plus origin/provenance during KlumCast's pre-validation call;
no propagation mutation occurs after handoff. KlumCast validates the final set once. Normal Groovy local
collection/execution must discover the resulting annotations, as proved by the mandatory admission probe.
Do not register a competing global producer or directly call local converters as a substitute.

Primary tracer: DefaultValues-only domain annotation carries AutoCreate, creates a concrete unkeyed child,
and applies its original name member. Optional companion case adds a participant that receives the
retained original domain instance. Fresh/null and supplied children retain ordinary precedence.

Acceptance: runtime physical annotations, original members, SOURCE/CLASS/RUNTIME retention controls,
non-DSL field/method/class inertness, mixed units, annotation definitions, no-propagation Schemas, existing
Required/Optional and an unrelated direct local transform. Assert no extra scan/AST mutation for inert cases.
Use separately compiled annotations whenever KlumCast already requires them; do not loosen that requirement.

Reasoned commit: marker, guarded contributor integration, tracer and documentary test as
one working change; current user documentation and release note accompany the first visible behavior.

### AP-2 — Complete contribution and diagnostic contract

Normalize genuine repeated requests and explicit nested List containers in Java/Groovy source and compiled
libraries. Also normalize legitimate containers of applied domain annotations without general alias merging.
Retain originals; preserve source positions and provenance through downstream projection.

| Input | Required acceptance |
| --- | --- |
| Explicit plus propagated selected annotation | Deterministic failure identifying both contributions |
| Two requests, two domains, or repeated domain contributions | Same failure, even for repeatable selected annotations |
| Ordinary explicit-only repeatable annotation | Ordinary behavior unchanged |
| Selected annotation carries propagation, self-reference or cycle | One-hop rejection; no recursion or partial silent success |
| Missing required member/default | Early failure with domain/selected/member/target; no fabricated values |
| Non-annotation type, wrong target, DSL target | Early usable failure before local-transform casts |
| Domain members differ from selected annotation members | No forwarding; selected defaults only |
| Target/member/coannotation KlumCast violations | Same effective constraints as direct controls, with understandable origin |
| Invalid original domain annotation | Its independent existing checks still apply |
| AnnotationCollector or other rewriting interaction | Explicitly qualified or diagnosed; no accidental merge/precedence |

Reasoned commits: contribution normalization/duplicate checks with executable cases; then depth/legality
and diagnostic provenance. Do not publish a partially guarded implementation. Missing-member guards are
required even where a bare direct annotation happens to pass Groovy compilation.

### AP-3 — Generated and inherited contract parity

Keep Required/Optional as existing local transformations. Cover explicit Required messages/levels and
Optional Ignore, boolean/shape restrictions and runtime validation. Verify each conversion happens once
and ordinary non-DSL behavior remains unchanged.

Compare direct and propagated Query, Cluster field/method and AutoCreate controls. Include abstract DSL
bases, same-unit and precompiled inheritance, overrides/shadowing, static nested/inner classes, DSL interface
exclusion, existing non-DSL-subclass diagnostics and mixed units. Preserve Cluster's owner-field removal,
getter annotations, cluster factories and generic/static/type checks. Query retains purity, category,
visibility/static/result checks and Builder-state behavior.

Verify public Foo_DSL Builder and Factory signatures, typed consumers, hidden implementations and source
mirrors, not just runtime reflection or annotation-node presence. Generated projections must not expand
again. Source mirrors are output; IntelliJ's source recognition remains a separate authoring qualification.

Preserve AutoCreate FactoryHelper/assignment/ownership/Templates, direct-before-Cluster fallback,
creation-before-mutation, callback order, no catch-up phase replay and same-phase Creator conflicts.
No generated public API, serialization or runtime lifecycle change beyond explicit-annotation equivalence.

Reasoned boundaries: compiler/generated parity with direct controls; then runtime regression and inherited
binary/source-mirror evidence. Any required change outside equivalence returns to design review.

### AP-4 — Artifact, foreign-transform and JPMS qualification

Build the actual production artifact once with Java 17 and consume it across Groovy 3/4/5. Separate Java
and Groovy annotation libraries from Schema compilation, including repeated requests and explicit containers.
Java javac only compiles libraries/typed consumers; Groovy performs propagation and local transformations.

Acceptance:

- Actual shipped KlumCast traversal/contributor activation and service/module visibility, not an independent global producer or test customizer/classpath overlay.
- Groovy 4/5 named compiler, annotation-library, Schema and typed consumer modules with normal exports/opens;
  Groovy 3 classpath-only. No access-bypass JVM flags, descriptor rewrites or split packages.
- Separately compiled foreign default-valued field/method/class local transform: equivalent observable
  direct/propagated generated effects where legal, one execution, ordinary non-DSL alias ignored, direct
  non-DSL use unchanged. Do not infer independent transformation order from one run.
- Runtime-only consumer sees retained domain and physical copies without the compiler/interpreter.
- Recompile after changed requests; old Schema binaries retain emitted behavior. Document consistent minimum
  compiler/annotation versions and remove old explicit AutoCreate where it would now duplicate a request.
- IntelliJ/Schema-authoring evidence is reported independently of compiled mirrors. Qualify or diagnose
  other global-transform interactions, rather than claiming universal ordering.

The bounded module and foreign probes reduce uncertainty; they do not replace this shipping gate.
Reasoned commits: artifact-consumer fixture with cross-version/module controls; separate editor evidence
and any packaging corrections, followed by required check/SonarCloud review.

### AP-5 — Consumer admission and release-facing delivery

After production implementation, run the real ScHelm integration on admitted dependency versions. Validate
its concrete relationships and DefaultValues consumers; no ScHelm policy or new configuration is added.
Update relevant docs/user pages, Migration navigation, _Sidebar, CHANGES.md and linked documentary tests.
Do not advertise an unimplemented feature in this design PR. Hive owns issue/release/curation reconciliation
and decides closing syntax. This PR stays Related: #890.

Reasoned boundary: verified consumer/migration evidence and release-facing documentation. An open draft
PR is not implementation admission, release acceptance or archive reconciliation.

## Acceptance map and outstanding decisions

| Requirement | Ownership / decisive evidence |
| --- | --- |
| Accepted marker, defaults, retention, original annotation | AP-1 tracer and retention controls |
| DSL-only boundaries, no carried DSL, no global semantics | AP-1 inertness; AP-3 eligibility/inheritance |
| One level, duplicates, repeatable normalization | AP-2 Java/Groovy contribution matrix |
| Contributor-added local discovery and foreign interoperability | Mandatory KC-0 Groovy 3/4/5 sentinel admission; AP-4 actual artifact control |
| KlumCast effective constraints, filters, provenance and diagnostics | Hard KC-0 prerequisite + AP-1/AP-2 final-set validation through the contributor contract |
| No double Required/Optional or false coannotation failures | Preserve registrations; AP-3 direct/propagated controls |
| Generated Builder/Factory/Cluster/Query/mirror equivalence | AP-3 generated and typed-consumer tests |
| Java 17 / Groovy 3/4/5 / applicable JPMS | AP-4 actual artifact matrix |
| Recompilation, no fallback, runtime invariants | AP-3 regressions + AP-4 binary/runtime controls |
| Consumer fit versus actual integration | Reported history now; real ScHelm run in AP-5 |

Current AP-1 evidence covers the neutral contributor, normal local discovery and sentinels, the exact
public Central-only artifact route, binary-parent behavior, shipped classpath/module activation and
runtime-only consumers. Failed future artifact or lane qualification still narrows or stops publication
without a manual or independent-transform workaround. Independent editor recognition, actual ScHelm
admission and final release/issue reconciliation remain separate. Current full-check and delivery status
is recorded in the [RC.2 evidence](issue-890-ap1-rc2-evidence.md).
