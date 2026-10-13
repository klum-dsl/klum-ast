# #890: composing built-in lifecycle field creation

Date: 2026-10-13. Status: **Investigation complete; recommendation proposed, no architecture accepted.**
Base: master `dea21a7cfa9b26630cacc5cdbc1087e756e59ce6`, verified against remote master during investigation.
Scope: local design and executable characterization only. No production change, GitHub mutation or publication.

## Recommendation for maintainer review

**Updated after the propagation proposal:** investigate compile-time composition before extending
LifecycleParticipant. The existing Groovy AnnotationCollector already handles the immediate use case
through a bundle alias containing AutoCreate and a separate retained domain annotation. If the named
domain annotation itself must remain on the field, an additive `@PropagateAnnotations([AutoCreate])`
is a stronger third candidate. Keep it default-only, one hop, and preserve normal annotation validation.
The new probes demonstrate the field/runtime path but expose unresolved scheduling for annotations
with their own AST transforms. No architecture is accepted, and no new propagator should be published
with an unqualified promise of arbitrary annotation equivalence.

**Earlier recommendation among the original two options:** prefer a narrowly defined omitted handler in `LifecycleParticipant.Creator`: only `phase = AutoCreate`
may omit it, meaning the existing AutoCreate field action with its default parameters. Represent omission
with the existing `LifecycleParticipant.CreationHandler.class` interface as a documented token. This needs
no public no-op handler or internal-handler exposure. Do not infer built-ins for AutoLink, Default or PostTree.

Keep **one creator claim per field/phase**, including composed claims; reject rather than silently prioritize
explicit field AutoCreate, a second domain built-in claim, or an external AutoCreate creator. Preserve ordinary
explicit AutoCreate behavior when no composed creator is present. Default-only composition is useful for
concrete, unkeyed structural children; exclude member forwarding deliberately. Keyed/abstract/configured
children keep the repeated mutation-only-domain + explicit AutoCreate workaround.

These are recommendations, not decisions. If strict conflicts make that narrow subset too inconvenient for
the primary use case, defer #890 or choose fixed meta-annotation configuration explicitly. Do not quietly
add override precedence or forwarding to make the example work.

[#890](https://github.com/klum-dsl/klum-ast/issues/890) records immediate Schema Developer demand from active
ScHelm authoring, with a viable but repetitive workaround. The secondary general-composition angle does not
justify other built-ins. [#888](https://github.com/klum-dsl/klum-ast/issues/888) remains separate: class-wide
defaults cover fields with no domain annotation and need their own eligibility/override policy. No class
setting, opt-out, Required/Optional reinterpretation, or ScHelm policy is proposed here.

## Current behavior and executable evidence

Authority: [ADR 0028 D1–D3, D5 and D8](../adr/0028-annotation-driven-lifecycle-participants.md),
[LP-3 phase evidence](issue-867-lp3-evidence.md), [LP-8 final acceptance](issue-867-lp8-evidence.md).
#867 is closed; its issue body's older remaining-LP-8 paragraph is historical and superseded by its final
acceptance comment and the accepted ADR. #883 already renamed the public API to LifecycleParticipant.
The older curation map/index contain no #890/#888 entry; no curation or release classification is changed.

The opt-in [investigation test](evidence/issue-890/probes/LifecycleCompositionInvestigationTest.groovy)
uses the existing AbstractDSLSpec/factory seam. It establishes:

- `@AutoCreate @FactBinding Child child` creates before an AutoCreate domain mutator. Explicitly configured
  Builders retain their input, inherited fields dispatch against their original Schema declaration, and
  mutation is visible to parent callbacks before child AutoCreate callbacks. A mutation-only null field stays null.
- The original AutoCreate annotation is available through singular field lookup; an annotation on another
  annotation is not physically present on the field. Effective behavior must not silently change that lookup.
- Current handler omission fails (`handler` required/class-literal validation); choosing CreationHandler as
  an ordinary handler fails the public-concrete/exact-domain checks. Current AutoCreate on ANNOTATION_TYPE
  fails compilation. With that declaration and its Schema in one source unit, KlumCast first reports
  `[klum-cast.unresolved-annotation] Validated annotation must have already been compiled`; the isolated
  annotation declaration reveals the target error. Target expansion alone is not sufficient qualification.
- Bare AutoCreate on a keyed field fails with `missing a 'key'`; abstract fields fail with `missing a 'type'`,
  **including an abstract DSL type with a DSL defaultImpl**. AutoCreationPhase checks instantiability before
  FactoryHelper can select a concrete implementation. Do not claim defaultImpl fixes bare AutoCreate.
- Isolated candidate annotations (not installed in KlumAST) compile and recover an omitted/explicit interface
  token equivalently. A one-hop meta lookup recovers defaults without reading same-named domain members;
  a wrapper two hops away is invisible. Java compilation/reflection also demonstrates that repeated domain
  annotations become a container requiring explicit expansion; ordinary declared-annotation enumeration
  does not recover their inner creator claims.

Existing focused controls remain authoritative: LifecycleParticipantCompositionTest and
LifecycleParticipantPhaseTest cover same-phase built-in/external and external/external creator conflicts,
repeated Creator markers/explicit containers, different-phase coexistence, creator-before-all-mutators,
and cluster fallback. LifecycleParticipantConsumerTest's replaced-binary cases qualify runtime rejection
of added competing creators and malformed handlers/phases. AutoCreateDSLTest covers explicit values,
keys, concrete subtype selection, Cluster filters and Template defaults.

Reproduce the bounded probe/check set with JDK 17:

```shell
./gradlew -I docs/implementation/evidence/issue-890/probes.gradle \
  :klum-ast:test --tests '*LifecycleCompositionInvestigationTest' \
  --tests '*LifecycleParticipantCompositionTest' --tests '*LifecycleParticipantPhaseTest' \
  --tests '*AutoCreateDSLTest' \
  --tests '*LifecycleParticipantConsumerTest.runtime rejects a precompiled*' --no-parallel
```

Repeat with `:klum-ast:groovy4Tests` and `:klum-ast:groovy5Tests`. The initializer adds only an opt-in evidence
source directory and skips buildSrc; no normal source set or build file is edited. These probes establish
current behavior and representation feasibility, **not an executable implementation of either candidate**.
Future candidate runtime/compiler behavior and generated/IDE parity still need the acceptance slices below.

## API sketches: alternatives remain open

Recommended omission form (proposed, does not compile with current KlumAST):

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@LifecycleParticipant.Creator(phase = AutoCreate)
@LifecycleParticipant.Mutator(phase = AutoLink, handler = BindFacts)
@interface StructuralFactBinding {}

@DSL class Application {
    @StructuralFactBinding OrderKafka messaging
}
```

Creator's existing handler member would gain `default CreationHandler.class`; explicit
`handler = LifecycleParticipant.CreationHandler` is equivalent but verbose. Compiler/runtime must classify
this exact token **before** concrete-handler/generic checks and allow it only for AutoCreate. Other explicit
handlers retain every existing check and fresh-instance invocation rule. The token is never instantiated.
Omission is an explicit creation claim, not a low-priority default policy. A dedicated new sentinel type is
possible but adds public vocabulary and, with the current generic bound, tends to masquerade as a handler.
Changing handler to `Class<?>` merely to admit a marker weakens the existing public contract unnecessarily.

Alternative built-in meta form (also proposed):

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@AutoCreate
@LifecycleParticipant.Mutator(phase = AutoLink, handler = BindFacts)
@interface StructuralFactBinding {}
```

| Dimension | Omitted handler / interface token | Built-in on ANNOTATION_TYPE |
| --- | --- | --- |
| Public change | Add default and token semantics to Creator.handler; clarify Creator now includes a built-in role | Widen AutoCreate target; define narrowly controlled composed behavior |
| Discovery | Existing field → domain → Creator marker path, including Creator.List | New field → domain → AutoCreate lookup in compiler and runtime |
| Scope control | Permit token only in AutoCreate; reject other phases | Whitelist AutoCreate, one hop only; no generic recursive annotation execution |
| Parameters | Naturally default-only; no key/type/value members added to Creator | Annotation syntax naturally admits fixed key/type/value; default-only requires rejecting non-default meta values explicitly |
| Validation cost | Special-case token, preserve external validation | Also audit KlumCast validated-annotation discovery, WriteAccess/lifecycle interpretation, source and precompiled domain libraries |
| Reflection | Original Schema annotations preserved; effective action internal | Same; field.getAnnotation(AutoCreate) remains absent for composed use |
| Future expressiveness | New decision required for parameters/other actions | Fixed parameters are an obvious future extension, but are not member forwarding |

They produce equivalent default-only field actions if normalized identically. They are not equivalent public
contracts: the meta form suggests the broader semantics of AutoCreate (methods, Closures, Clusters and fixed
parameters), whereas Creator already means direct-field creation. The latter is the smaller accepted seam
to extend. A dedicated `LifecycleParticipant.AutoCreate` marker would be narrower still semantically but adds
another public annotation for the same claim; no demonstrated need justifies that third vocabulary yet.

## Proposed semantic boundary and compatibility

The table below describes the original lifecycle-specific candidates. Additive propagation instead
materializes ordinary field annotations, so their existing placement rules apply; it need not inherit
the narrower lifecycle-specific eligibility subset. All new rules remain **proposed**.

| Case | Proposed rule / existing constraint |
| --- | --- |
| One composed default creator | Reuse the existing AutoCreationPhase field operation once, before all matching field mutations |
| Composed + explicit field AutoCreate, even identical defaults | Compile-time conflict; equivalent runtime schema defense for separately compiled inputs. No silent deduplication/override |
| Two composed claims, or composed + same-phase external Creator | Same conflict, including two claims on one domain annotation or Creator.List |
| External creator in another phase | Existing coexistence; it runs only if still null then. A prior AutoCreate result normally makes later creation skip |
| Supplied value / null result | Existing non-null input skips built-in creation; mutator still follows ordinary phase/sealed rules. Configuration conflict must fail even if input would bypass creation |
| Phase ordering | Existing type mutations → direct fields → Cluster AutoCreate → methods/Closures → child descent. No earlier-phase replay, second traversal, new sorting or inter-domain ordering |
| Key/type/value | Default means DEFAULT_KEY → null, Object.class → declared type, None → empty map. Preserve current validation/failure timing; no key synthesis, subtype/defaultImpl inference, values mapping or domain-member evaluation |
| Eligible composed placement | Non-static retained direct DSL relationship; propose rejecting Owner, LINK, OPTIONAL_LINK, Builder-only, scalar, Closure, collection/Map, Cluster and method/type placements for the built-in claim |
| Explicit existing AutoCreate on other shapes | Do not tighten its behavior as incidental cleanup. In particular, direct lifecycle Closures and Cluster projection retain their existing path |
| Abstract/keyed direct field | Same missing-type/missing-key failure as bare explicit AutoCreate when unset. Default-only API cannot configure them; document and use mutation-only + explicit AutoCreate instead |
| Cluster fallback | Coexists with direct composition, fills still-null values, never reruns field participants. A field mutator in AutoCreate misses a child supplied only by that later step |
| Inheritance | Original inherited field retains its annotation/claim; shadowing rejection remains. @Inherited on a domain annotation does not propagate field annotations or annotation-type decoration |
| Repeatability | Preserve repeated Creator/Mutator marker/container semantics. Do not add repeated-domain-container expansion implicitly; propose rejecting repeated-domain built-in composition (including a single use of a repeatable domain) initially with a clear diagnostic |
| Type-level domain mutation | Existing LP-5 remains. A domain with a built-in creation claim is field-only; separate annotations when type mutation is also desired |
| Context / metadata | Return actual domain annotation and physical original field annotations. No synthetic public AutoCreate, changed annotation lookup, reflective Field/list API or generated metadata requirement |

The narrower Owner/link exclusion is a proposed eligibility decision for **new composed built-in claims**,
not a statement that #867 currently excludes those direct DSL fields. Existing external participants use
checked assignment and sealed FAIL/SKIP rather than a blanket link prohibition. Cluster properties are
generated Map projections; extending their special path would be a separate feature.

Existing generated public Foo_DSL.Builder/Factory interfaces, child overloads, source mirrors and normal
field visibility must stay unchanged. Normalize internally rather than copying synthetic annotations to
Schema/Builder/mirror declarations; compare public descriptors and original annotations in separate-artifact
controls. Composition supplies no Model-side method, property, context or serialization state. Reuse
FactoryHelper.createNestedBuilder, setSingleField, current Templates scope and the existing breadcrumb
suffix `:@AutoCreate`; retain the owning session/materialization path. Diagnostic text should add domain
declaration provenance where helpful without publishing a new path or provenance API.

Adding a default preserves Creator.handler's descriptor and all existing explicit-handler annotations.
New omitted-handler annotation libraries require the new compiler/runtime; an old annotation definition
without the default can throw IncompleteAnnotationException, while old validators reject the explicit
interface token. Target widening is additive, but new meta-decorated libraries on old runtimes may silently
lose implied creation. Neither form promises forward compatibility with an old runtime. Replacing an already
deployed mutation-only domain library with one that implies creation changes behavior and can turn existing
explicit field AutoCreate uses into source/runtime conflicts: prefer a new domain annotation or coordinated
Schema migration. Existing schemas not opting in retain their behavior.

## Proposed ADR and implementation-plan outline

For the original lifecycle-specific candidates, classification **B: extend ADR 0028** narrowly, since its dispatch/conflict rules remain authoritative.
After maintainer acceptance, add a D2/D3 built-in-composition subsection and update its public inventory,
plus a dedicated implementation plan. If recursive/general built-in composition is chosen, use a successor
ADR; do not retroactively present that as #867's accepted contract.

1. **LC-1 — representation and declaration validation.** One reasoned commit for the chosen annotation
   contract and source/binary defenses. Resolve source defaults and reflected defaults consistently;
   count every claim before checking current field values. Cover explicit+implicit, external+implicit,
   two-domain and repeated-meta conflicts, invalid phase/token, retention, inherited fields, eligibility,
   and rejected repeated domains. For the meta option also qualify compiled/uncompiled annotation libraries
   through KlumCast and enforce the chosen fixed-parameter boundary. No public no-op/custom handler.
2. **LC-2 — one field-slot vertical tracer.** Reuse/extract only the internal AutoCreate operation as
   necessary, keeping explicit and composed configuration equivalent. Tests and production change together:
   concrete unkeyed child, present/null input, default values, typed binding into grandchildren, callbacks,
   original annotation lookup, current Templates scope, paths, ownership, phase order and Cluster fallback.
   Pair keyed/abstract/defaultImpl negative controls with the explicit baseline. Keep different-phase
   external creators and all mutator/sealed rules unchanged.
3. **LC-3 — qualification and guidance.** Separate annotation-library/Schema/consumer JARs in Java and
   static/dynamic Groovy 3/4/5; binary replacement conflicts; applicable Groovy 4/5 JPMS with ordinary
   exports/opens. Compare generated public descriptors, field annotations, source mirrors and DSL child
   method availability. Template/copy/FromMap/Jackson/serialization controls reuse LP route fixtures.
   Add an @Issue('890'), @Tag('documentary'), @See happy path; update docs/user/Model-Phases.md,
   migration navigation if needed and CHANGES.md, then ADR/inventory/plan evidence. Final repository
   checks and required review precede any separately authorized publication.

No release assignment, #888 implementation, member forwarding, custom phases, containers, late-phase
catch-up, HANDLE or new Closure evaluator belongs in these slices.

## Third candidate: default-only additive annotation propagation

Proposed syntax retains the applied domain annotation and adds ordinary field annotations:

```groovy
@PropagateAnnotations([AutoCreate])
@LifecycleParticipant.Mutator(phase = AutoLink, handler = BindFacts)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface FactBinding { String provider() default 'orders' }

@DSL class Application {
    @FactBinding(provider = 'billing') OrderKafka messaging
}
```

An annotation member `Class<? extends Annotation>[]` identifies annotation **types**, not configured
instances. Expansion creates a fresh AnnotationNode for each selected type, uses its defaults, and
retains FactBinding and its members. No forwarding follows from the array. A propagated type with required
members cannot be used this way and should produce an ordinary missing-member diagnostic. An illegal
FIELD target should likewise fail; selecting arbitrary types cannot waive their declared contracts.
This avoids widening AutoCreate's target or inventing a built-in Creator token. The existing runtime
sees a physical AutoCreate on the original Schema field, and singular annotation lookup can return it.
Normal key/type/value, Cluster, ownership and phase behavior then follow the expanded annotations.

The opt-in customizer probe executes during SEMANTIC_ANALYSIS, before DSL's CANONICALIZATION transform.
It copies AutoCreate plus an unrelated default-valued marker, preserves a domain member, and successfully
creates/mutates a child through the existing runtime. It is not a registered transform or completed
generic implementation. A negative control adds Required at the same point: the resulting field has
Required but **no Validate**, proving that RequiredToValidateTransformation was not discovered. Groovy's
local-transform collection also occurs during SEMANTIC_ANALYSIS; a general propagator must insert
before that discovery or explicitly integrate with it. Merely running before DSLASTTransformation
is insufficient. Conversion-phase resolution of imported/precompiled/source annotations, repeated expansion
and transform registration all require separate evidence.

Existing [Groovy AnnotationCollector](https://docs.groovy-lang.org/docs/groovy-3.0.19/html/api/groovy/transform/AnnotationCollector.html)
can already express a bundle without a new KlumAST mechanism:

```groovy
@AnnotationCollector([AutoCreate, FactBinding])
@interface StructuralFactBinding {}

@DSL class Application {
    @StructuralFactBinding(provider = 'billing') OrderKafka messaging
}
```

The new real-DSL probe passes: the field ends up with AutoCreate and FactBinding, its handler receives
the configured domain member, and StructuralFactBinding is absent. Collector alias replacement and
argument mapping are documented Groovy behavior; the original domain annotation is retained here
because it is an item in the bundle, not the alias. That differs from decorating FactBinding itself
with a collector. The repository's Required converter contains an old comment that AnnotationCollector
did not work there; it does not establish a general current limitation, and this focused probe passes.

Latest checks: **12 investigation cases per Groovy 3/4/5 lane**, zero failures/errors/skips, in 27s.
These add three cases to the original nine; the earlier 168-case focused run remains the baseline evidence.

Additional contract decisions for a reusable propagator:

- Preserve the domain annotation, default-only class list, field-only expansion and one hop initially.
  Do not recursively propagate nested bundles or execute domain members.
- Reject duplicate non-repeatable annotations initially, including propagated+explicit AutoCreate.
  Do not silently suppress declarations or merge annotation members; repeatable normalization needs
  its own deterministic rule. An external same-phase Creator still conflicts with the added AutoCreate.
- Run early enough for KlumAST, KlumCast and each supported local AST transform to see equivalent input.
  A core pre-pass is the smaller qualification surface if only KlumAST field annotations are promised;
  an arbitrary-annotation promise requires discovery-aware integration. Consider Groovy collector
  infrastructure/custom processing before building independent transform scheduling.
- Persist propagation metadata in precompiled domain libraries; qualify Java-authored declarations
  consumed by Groovy Schemas. Java compilation itself does not acquire Groovy expansion behavior.
- Treat expanded annotations as compile-time Schema state. Updating a domain library's class list
  after Schema compilation does not retrofit copied annotations: recompile Schemas. A new annotation
  library on an old compiler may silently lack expansion, so document/enforce minimum compiler support.
- Compare generated Builder/Model/mirror annotations and signatures. Physical expansion intentionally
  changes original-field annotation presence relative to runtime-only composition; do not claim that
  metadata is unchanged. Existing downstream propagation rules should see ordinary input.

If existing bundle aliases are acceptable, the next slice should qualify separately compiled libraries,
duplicate/creator conflicts and generated surfaces with AnnotationCollector; a new framework annotation
may be unnecessary. If preserving the applied domain annotation is essential, prototype discovery-aware
additive expansion next, including Required and a third-party local transform. A broad reusable propagator
warrants a separate proposed ADR (classification C) while referencing ADR 0028's unchanged runtime rules.
Its generic annotation-placement scope should not be presented as already accepted under #890.

## Exact maintainer decisions still required

Resolve these in dependency order, one question at a time:

0. **New prerequisite:** is a Groovy bundle alias containing the retained domain annotation acceptable,
   or must the exact applied domain annotation itself survive expansion? Recommendation: use the
   existing collector when aliases suffice; otherwise investigate retaining additive propagation before
   accepting the earlier sentinel recommendation. Separately decide whether its scope promises ordinary
   KlumAST field annotations or arbitrary third-party AST-transform annotations.
1. **Meaning and minimum use case:** should a distinct structural domain annotation always imply
   existence, while a mutation-only annotation continues to mean bind-if-present? Is concrete unkeyed
   default-only coverage useful enough now? Recommendation: yes; retain separate domain vocabulary.
2. **Public representation:** accept omitted handler backed by CreationHandler.class, select a dedicated
   explicit sentinel, or choose one-hop AutoCreate meta composition? Recommendation: omitted handler;
   do not implement both spellings in 4.1 merely for symmetry.
3. **Conflict policy:** reject composed+explicit AutoCreate even with identical defaults, or deliberately
   revise ADR 0028 to normalize/coalesce/override? Recommendation: reject every second claim. This means
   explicitly customized AutoCreate requires a mutation-only domain annotation.
4. **Parameter boundary:** default-only with no forwarding, fixed key/type/value on a meta declaration,
   or justified future forwarding work? Recommendation: default-only; defer forwarding. Fixed parameters
   are a separate possible scope choice, not synonymous with forwarding.
5. **Eligibility and repeats:** accept the new claim's retained owned direct-field subset and initial
   repeatable-domain rejection? Recommendation: yes; preserve existing explicit annotations and external
   participant eligibility. Decide whether keyed/abstract missing configuration remains at construction
   time (recommended parity) rather than introducing a new eager-validation contract.
6. **Compatibility and release:** accept new compiler/runtime minimum, coordinated/new domain-annotation
   migration and conditional 4.1 placement, with implementation separately authorized? Recommendation:
   additive opt-in, no aliases or generated API change; defer if the above choices broaden the feature.

The first question gates an architectural interview; this evidence draft is not permission to implement.

## Validation and handoff

Final focused check passed in 1m 15s with JDK 17: **56 cases per lane on Groovy 3/4/5**, zero failures,
errors or skips (168 total). Per lane: nine investigation cases, eight composition, 26 phase,
eight existing AutoCreate and five replaced-binary declaration controls. XML results were inspected.
The Java representation probe compiles real annotation classes with the JDK compiler; candidate runtime
dispatch remains unimplemented. Diff whitespace and local Markdown file-link checks passed.
Initial probe-run repairs were confined to fixtures: skip buildSrc in the initializer and isolate the
annotation target error from same-source KlumCast validation. No production fix was attempted.
No full check, live IDE, new feature JPMS, SonarCloud or remote CI qualification is claimed for this design.

Hive callback: retain this local branch/evidence for maintainer review; #890 stays open with architecture
and release placement unresolved. Resume only for explicit maintainer decisions/design continuation;
implementation needs separate authorization. Do not archive this worker as reconciled or deliver a PR.
No release/run IDs or external evidence-store authorization were supplied for this investigation; no
external operational event stream was created. The local report and reproducible probes are the evidence boundary.
