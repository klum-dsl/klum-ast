# ADR 0029 implementation plan: DSL-only annotation propagation

Date: 2026-10-13. Revised: 2026-10-14.
Status: Architectural investigation; **no production slice is authorized**. Validation correctness for
the admitted scope is the gate. [KlumCast #58](https://github.com/klum-dsl/klum-cast/issues/58) is the
preferred automatic route's conditional dependency; a qualified manual domain-check route may avoid it.

Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md).
Tracking: [#890](https://github.com/klum-dsl/klum-ast/issues/890), merged design investigation
[PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
Automatic-route enhancement / conditional prerequisite: [KlumCast #58 — Support effective-declaration validation after compiler AST
preprocessing](https://github.com/klum-dsl/klum-cast/issues/58).
Evidence: [scheduling, eligibility and KlumCast probes](evidence/issue-890/schema-preprocessing.md).
Consumer history: [decision brief](issue-890-lifecycle-composition-decision-brief.md).
Release remains conditional; no tracker, milestone or curation state changes are part of this plan.

## Current behavior and admission boundary

Explicit AutoCreate plus a domain mutator already creates before mutation and preserves supplied children.
DefaultValues consumes the original annotation's nondefault members without requiring a participant.
The ScHelm fit review accepts bare AutoCreate for current concrete unkeyed relationships; it is reported
consumer evidence, not a locally executed integration. Issue #888's class-wide defaults remain separate.

No public PropagateAnnotation or production propagation entry exists. Historical generic probes establish
that a semantic global pass can insert annotations before local collection; their carried-DSL example
is now outside the approved scope. A late semantic CompilationCustomizer misses Required discovery.
The new guarded probes characterize DSL boundaries, generated parity, foreign local effects and validation
gaps. Positive Required/Cluster scheduling and generated-behavior probes are feasibility only; they do not
qualify the future supported effective-declaration validation operation.

**Recommendation:** B with a qualified manual or automatic validation route. Required and Optional stay local.
C can call the actual converters in order, but unchanged registrations lower twice, a later KlumCast pass
can report false Required/Validate conflicts, and no ordering dependency between the converters was found.
Migration is not justified by symmetry. No runtime validation redesign is planned.

Two correctness blockers were demonstrated: KlumCast-before-propagation skips a new constraint; validating
only copies skips an affected, already-validated explicit constraint. Raw ValidationHandler metadata removal
is test-only feasibility evidence. **KlumAST must not clear, set or otherwise manipulate KlumCast VALIDATED
metadata**, or infer validation correctness from relative global-transform order. The maintainer permits
more Domain API Developer work to save Schema Developer repetition. AP-1 needs an admitted validation
route and explicit authorization; #58 delivery/artifact gates apply only if automatic integration is chosen.

## Affected seams and ownership

| Seam | Responsibility |
| --- | --- |
| klum-ast-annotations | JDK-only public repeatable marker/container; normal exports and Javadoc |
| DslAstHelper / DSLASTTransformation | Reuse existing eligibility and interface boundary; no second framework |
| New internal early entry | Fixed orchestration: eligibility → contribution plan/guards → complete insertion; call supported effective validation only for the automatic route |
| Separate internal propagation operation | KlumAST owns request discovery, genuine container normalization, defaults-only expansion, duplicate/nested errors and introduced-annotation provenance |
| Domain API Developer / manual route | Declare applicable existing/manual checks, supported targets/combinations, alias-aware conflicts and documentary/API compatibility coverage |
| KlumCast compiler / #58 | Execute ordinary checks in either route; automatic route additionally owns effective validation state, rechecking and diagnostic aggregation through the supported handoff |
| Required / Optional annotations and converters | Preserve registrations, semantic phase, message/level/Ignore behavior and non-DSL compatibility |
| Cluster field conversion and projection | Existing semantic then canonical paths; preserve field-to-getter projection and factories |
| BuilderQuerySupport / generated DSL support | Existing Query processing, public Builder/Factory parity and source mirrors |
| Runtime AutoCreationPhase / DefaultPhase / participants | Regression only; no interpreter or lifecycle changes |
| Compiler services and JPMS | Shipped discovery and normal module access; temporary fixture service files are not production registration |

KlumCast is a separate library. Its open issue #58 is a conditional integration dependency, not a blanket
blocker for propagation. With the manual route, domain annotations carry applicable checks themselves and
ordinary KlumCast validates them; no caller revalidation protocol or metadata mutation is introduced.
With the automatic route, KlumAST invokes the supported post-batch operation and KlumCast owns effective
validation/rechecking. KlumAST owns final DSL/generated compatibility in both routes and contains no
second constraint engine. Keep KlumCast free of KlumAST/DSL dependencies and consumer semantics.
Do not create or change issues, release indexes or dependency versions during this investigation.

## Dependency-ordered slices and reasoned commits

Validation-route assessment/decision → either manual M-0 or automatic KC-0 → AP-1 → AP-2 → AP-3 →
AP-4 → AP-5. All production slices remain future work. KC-0 is optional when the manual route is admitted.
Keep dedicated issue branches, small behavior-and-test commits, and reviewed history intact. Each slice
runs baseline Groovy 3 focused tests and final Groovy 4/5 checks; use current Java 17 artifacts. New tests
carry @Issue('890'), Test suffixes, and documentary @Tag/@See links for the eventual user-facing feature.
Do not introduce ignored/pending tests as a substitute for an unresolved contract.

## Validation-route gate before production AP-1

First perform a bounded KlumCast #58 feasibility/complexity assessment and report the smallest supported
operation, expected effort and remaining state/diagnostic risks. If small enough, incorporate it through
KC-0; otherwise the maintainer may defer it and admit manual checks for the initial domain API scope.
No KlumCast production implementation is authorized by that assessment.

Choose the route and its acceptance matrix before AP-1 authorization. The hard requirement is preserved
applicable validation for admitted uses, with understandable diagnostics; the dependency on a particular
library enhancement is conditional. Production/artifact qualification still accompanies implementation.
Schema Developers retain the one-domain-annotation syntax in both routes; the manual route deliberately
assigns additional setup/maintenance work to Domain API Developers.

### M-0 — Manual domain checks and documented scope

Owner: Domain API Developer, using existing KlumCast checks and ordinary validation. Declare applicable
metadata explicitly on each propagating domain annotation; restrict @Target and supported combinations
where useful. Reuse existing check implementations and add API-specific checks only where needed. Do not
copy validation metadata through AST mutation or manipulate KlumCast state.

Admission/acceptance:

- Qualify the primary DefaultValues-only domain carrying bare AutoCreate, with optional lifecycle metadata,
  without requiring Schema Developers to repeat AutoCreate or checks.
- Qualify manual Cluster field/method shape checks and other annotations included in the initial scope.
  Excluding an unqualified combination from the admitted API must be explicit in its documentation.
- Cover explicit and propagated coannotations, both domain-use orders and both independent global orders.
  A check naming a framework type may also need peer domain aliases and reciprocal declarations.
- Check the effective selected defaults. Checks on domain members do not configure the copied annotation;
  a domain member named field cannot make bare copied Default valid. No forwarding is introduced.
- Preserve domain definition/use separation, precompiled-library prerequisites, source/provenance and normal
  direct behavior. Exercise Java/Groovy annotation libraries, Groovy 3/4/5 and applicable JPMS boundaries.
- Document that propagation does not automatically transfer KlumCast checks or guarantee equivalence for
  unqualified annotations/combinations. Keep an actionable API-author recipe and durable regression cases.
- Record duplicate-reporting or editorial limitations separately. They may be assessed for deferral only
  with constraints preserved and understandable source/annotation context; no correctness gap is waived.

Temporary exploratory probes passed 16 cases per Groovy lane (48 total), including primary creation,
Cluster shape restrictions, explicit conflicts, missed peer-alias/member checks and explicit alias-aware
repairs. This is feasibility evidence, not full production/API-library qualification; durable cases and
user documentation accompany AP-1/AP-2 and release admission. No temporary probe is shipped in this PR.

Reasoned boundary: agree the initial domain scope and API-author responsibility with concrete examples,
then add behavior-and-documentary coverage with the propagation implementation. Under this route, #58
may remain open as a deferred enhancement without blocking AP-1 solely for lack of that library feature.

### KC-0 — Supported effective-declaration validation prerequisite

Conditional owner: KlumCast, under [#58](https://github.com/klum-dsl/klum-cast/issues/58) and separate
implementation authorization, if the automatic route is selected. KlumAST then consumes the result in AP-1.

Agree a narrow compiler-facing entry/handoff over existing validation machinery, accepting the final
annotation batch and contribution origin. It must re-evaluate affected existing coannotation constraints
after a prior validation pass, retain compiled-annotation prerequisites and definition/use separation,
and aggregate diagnostics without duplicate reporting. Requests against unchanged effective state are
idempotent no-ops. The operation must not depend on relative global-transform scheduling, and callers must
not manipulate KlumCast's validation metadata. No generic preprocessing SPI or propagation-specific runtime
machinery, KlumAST/DSL dependency or consumer-specific validation logic belongs in KlumCast.

Acceptance:

- Run existing validators on the actual selected annotation and actual Schema target, with ordinary defaults.
- Preserve original domain validation independently; do not validate its definition as a Schema member.
- Both global orders: bare Default fails, invalid Cluster type/generics/class configuration fail, valid
  Cluster passes, Required plus copied Validate fails. Reverse contribution order as well as global order.
- Original invalid annotation reported before expansion is not reported twice. Preserve diagnostic code,
  binding/composition path, related positions and domain → selected → qualified declaration context.
- Repeated requests against unchanged effective state are no-ops; KlumCast owns state transitions and
  diagnostic aggregation across changes. The caller supplies introduced-annotation origin/provenance.
- Existing direct Required/Optional conversion continues without false generated-Validate conflicts.
- No revalidation or metadata mutation on unrelated/non-DSL declarations or unchanged Schemas.
- Groovy 3/4/5, Java/Groovy precompiled libraries, genuine repeats/containers, and Groovy 4/5 named modules.

KlumCast #58 now records the feature request, **Support effective-declaration validation after compiler
AST preprocessing**. The motivating counterexamples are newly copied Default bypassing its constraint
when KlumCast runs first and an already-validated Key missing a later Field conflict. Its operation remains
framework-neutral: the caller supplies actual annotation uses/targets and origin, while KlumCast owns its
constraint engine and state. This documentation follow-up records the existing issue without mutating it.

Reasoned boundaries: first the engine's explicit validation-state transition with direct compiler tests;
then supported access/diagnostic provenance and compatibility qualification. Public Java visibility alone
is insufficient evidence that arbitrary node-metadata invalidation is supported.

Automatic-route gate before production AP-1:

1. Agree the narrow KlumCast #58 API/handoff and review it against ADR 0029's effective-declaration cases.
2. Deliver and qualify that supported operation in KlumCast, including idempotent state and diagnostic
   aggregation, Java 17, Groovy 3/4/5, precompiled libraries and applicable JPMS access.
3. Approve the cross-project artifact route: a released artifact or an explicitly approved development
   artifact with reproducible identity/provenance and consistent consumer dependency resolution. Do not
   assume local publication or an unreleased dependency is approved.
4. Obtain explicit AP-1 implementation authorization, then consume that artifact and qualify integration.

Constraint bypass cannot be deferred; further prose refinements may be deferred only after usable
origin/location and correctness are complete. No existing positive probe substitutes for these gates.

### AP-1 — Narrow vertical tracer and public marker

Only after the validation-route decision and explicit production authorization. Apply KC-0's API,
qualified-delivery and approved-artifact-route gates if automatic integration is selected; otherwise use
the admitted M-0 manual strategy and scope. Introduce marker/container and the minimal guarded
compiler producer together, so no standalone published marker silently does nothing. The entry owns
orchestration; contribution collection, legality and application remain separate internal operations.

Use existing direct DSL detection, exclude annotation definitions/interfaces, snapshot owner-declared
classes/fields/methods and ignore generated/inherited declarations as expansion sites. Reject DSL as a
selected target. No traversal of ordinary classes' members, unrelated units, or mutations without requests.
Attach one complete authorized batch per declaration before normal local discovery/execution. The
manual route relies on complete checks declared on the retained original domain annotation, qualified
independently of global-pass order. The automatic route calls the supported idempotent KlumCast #58
post-batch operation for effective-state discovery/rechecking. KlumAST supplies origin/provenance and
must never use validation-state metadata as a mutation protocol.

Primary tracer: DefaultValues-only domain annotation carries AutoCreate, creates a concrete unkeyed child,
and applies its original name member. Optional companion case adds a participant that receives the
retained original domain instance. Fresh/null and supplied children retain ordinary precedence.

Acceptance: runtime physical annotations, original members, SOURCE/CLASS/RUNTIME retention controls,
non-DSL field/method/class inertness, mixed units, annotation definitions, no-propagation Schemas, existing
Required/Optional and an unrelated direct local transform. Assert no extra scan/AST mutation for inert cases.
Use separately compiled annotations whenever KlumCast already requires them; do not loosen that requirement.

Reasoned commit: marker, guarded activation, selected validation route, tracer and documentary test as
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

- Actual shipped global registration and service/module visibility, not a test customizer/classpath overlay.
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
| Pre-local discovery and foreign interoperability | AP-1 phase tracer; AP-4 independently compiled observable transform |
| KlumCast constraint coverage and provenance for admitted scope | M-0 manual or KC-0 automatic route + AP-1/AP-2 direct/propagated checks |
| No double Required/Optional or false coannotation failures | Preserve registrations; AP-3 direct/propagated controls |
| Generated Builder/Factory/Cluster/Query/mirror equivalence | AP-3 generated and typed-consumer tests |
| Java 17 / Groovy 3/4/5 / applicable JPMS | AP-4 actual artifact matrix |
| Recompilation, no fallback, runtime invariants | AP-3 regressions + AP-4 binary/runtime controls |
| Consumer fit versus actual integration | Reported history now; real ScHelm run in AP-5 |

Maintainer decisions still required: admit B instead of C, confirm interface exclusion, assess #58's
complexity and select the validation route/initial domain scope. #58 API, delivery and artifact-route
gates apply if automatic integration is selected. Manual checks need their own admitted coverage and
documentation, not a blanket automatic-equivalence claim. Production service/module packaging, full binary
inheritance and editor recognition remain qualification risks. **AP-1 remains unauthorized.**
