# ADR 0029 implementation plan: DSL-only annotation propagation

Date: 2026-10-13. Revised: 2026-10-14.
Status: Architectural investigation; **no production slice is authorized**.
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md).
Tracking: [#890](https://github.com/klum-dsl/klum-ast/issues/890), draft design [PR #891](https://github.com/klum-dsl/klum-ast/pull/891).
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
The new guarded probes characterize DSL boundaries, generated parity, foreign local effects and validation.

**Recommendation:** B with effective-declaration KlumCast validation. Required and Optional stay local.
C can call the actual converters in order, but unchanged registrations lower twice, a later KlumCast pass
can report false Required/Validate conflicts, and no ordering dependency between the converters was found.
Migration is not justified by symmetry. No runtime validation redesign is planned.

Two correctness blockers were demonstrated: KlumCast-before-propagation skips a new constraint; validating
only copies skips an affected, already-validated explicit constraint. Raw ValidationHandler metadata removal
is feasibility evidence only. AP-1 is not ready for authorization until the supported handoff is settled.

## Affected seams and ownership

| Seam | Responsibility |
| --- | --- |
| klum-ast-annotations | JDK-only public repeatable marker/container; normal exports and Javadoc |
| DslAstHelper / DSLASTTransformation | Reuse existing eligibility and interface boundary; no second framework |
| New internal early entry | Fixed orchestration: eligibility → contribution plan/guards → complete insertion → supported effective validation |
| Separate internal propagation operation | Genuine container normalization, defaults-only copies, one-hop/duplicate guards and provenance |
| KlumCast compiler | Own constraint discovery/checks/state and diagnostic deduplication through an agreed integration seam |
| Required / Optional annotations and converters | Preserve registrations, semantic phase, message/level/Ignore behavior and non-DSL compatibility |
| Cluster field conversion and projection | Existing semantic then canonical paths; preserve field-to-getter projection and factories |
| BuilderQuerySupport / generated DSL support | Existing Query processing, public Builder/Factory parity and source mirrors |
| Runtime AutoCreationPhase / DefaultPhase / participants | Regression only; no interpreter or lifecycle changes |
| Compiler services and JPMS | Shipped discovery and normal module access; temporary fixture service files are not production registration |

KlumCast is a separate library. Recommend a separately authorized prerequisite issue/change, not an
unapproved production edit in this repository. An agreed direct dependency on its current public engine
is an alternative maintainer decision only if state invalidation and at-most-once diagnostics are qualified.
Do not create or change issues, release indexes or dependency versions during this investigation.

## Dependency-ordered slices and reasoned commits

Prerequisite KC-0 → AP-1 → AP-2 → AP-3 → AP-4 → AP-5. All remain future work.
Keep dedicated issue branches, small behavior-and-test commits, and reviewed history intact. Each slice
runs baseline Groovy 3 focused tests and final Groovy 4/5 checks; use current Java 17 artifacts. New tests
carry @Issue('890'), Test suffixes, and documentary @Tag/@See links for the eventual user-facing feature.
Do not introduce ignored/pending tests as a substitute for an unresolved contract.

### KC-0 — Supported effective-declaration validation prerequisite

Owner: KlumCast, under separate authorization; KlumAST consumes the result in AP-1.

Agree a narrow compiler-facing entry/handoff over existing validation machinery, accepting the final
annotation batch and contribution origin. It must re-evaluate affected existing coannotation constraints
after a prior validation pass, retain compiled-annotation prerequisites and definition/use separation,
and avoid duplicate reporting. No generic preprocessing SPI or propagation-specific runtime machinery.

Acceptance:

- Run existing validators on the actual selected annotation and actual Schema target, with ordinary defaults.
- Preserve original domain validation independently; do not validate its definition as a Schema member.
- Both global orders: bare Default fails, invalid Cluster type/generics/class configuration fail, valid
  Cluster passes, Required plus copied Validate fails. Reverse contribution order as well as global order.
- Original invalid annotation reported before expansion is not reported twice. Preserve diagnostic code,
  binding/composition path, related positions and domain → selected → qualified declaration context.
- Existing direct Required/Optional conversion continues without false generated-Validate conflicts.
- No revalidation or metadata mutation on unrelated/non-DSL declarations or unchanged Schemas.
- Groovy 3/4/5, Java/Groovy precompiled libraries, genuine repeats/containers, and Groovy 4/5 named modules.

Reasoned boundaries: first the engine's explicit validation-state transition with direct compiler tests;
then supported access/diagnostic provenance and compatibility qualification. Public Java visibility alone
is insufficient evidence that arbitrary node-metadata invalidation is supported.

Gate: maintainer accepts ownership/API and prerequisite evidence. Constraint bypass cannot be deferred;
further prose refinements may be deferred only after usable origin/location and correctness are complete.

### AP-1 — Narrow vertical tracer and public marker

Only after KC-0 and explicit production authorization. Introduce marker/container and the minimal guarded
compiler producer together, so no standalone published marker silently does nothing. The entry owns
orchestration; contribution collection, legality and application remain separate internal operations.

Use existing direct DSL detection, exclude annotation definitions/interfaces, snapshot owner-declared
classes/fields/methods and ignore generated/inherited declarations as expansion sites. Reject DSL as a
selected target. No traversal of ordinary classes' members, unrelated units, or mutations without requests.
Attach the complete batch before asking KC-0 to validate, then leave normal local discovery intact.

Primary tracer: DefaultValues-only domain annotation carries AutoCreate, creates a concrete unkeyed child,
and applies its original name member. Optional companion case adds a participant that receives the
retained original domain instance. Fresh/null and supplied children retain ordinary precedence.

Acceptance: runtime physical annotations, original members, SOURCE/CLASS/RUNTIME retention controls,
non-DSL field/method/class inertness, mixed units, annotation definitions, no-propagation Schemas, existing
Required/Optional and an unrelated direct local transform. Assert no extra scan/AST mutation for inert cases.
Use separately compiled annotations whenever KlumCast already requires them; do not loosen that requirement.

Reasoned commit: marker, guarded activation, supported validation handoff, tracer and documentary test as
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
| KlumCast direct/propagated constraint parity and provenance | KC-0 prerequisite + AP-1/AP-2 final effective-declaration checks |
| No double Required/Optional or false coannotation failures | Preserve registrations; AP-3 direct/propagated controls |
| Generated Builder/Factory/Cluster/Query/mirror equivalence | AP-3 generated and typed-consumer tests |
| Java 17 / Groovy 3/4/5 / applicable JPMS | AP-4 actual artifact matrix |
| Recompilation, no fallback, runtime invariants | AP-3 regressions + AP-4 binary/runtime controls |
| Consumer fit versus actual integration | Reported history now; real ScHelm run in AP-5 |

Maintainer decisions still required: admit B instead of C, confirm interface exclusion, and approve KC-0's
ownership/access/state contract. Production service/module packaging, diagnostic deduplication, full binary
inheritance and editor recognition remain qualification risks. **Do not start AP-1 from this plan.**
