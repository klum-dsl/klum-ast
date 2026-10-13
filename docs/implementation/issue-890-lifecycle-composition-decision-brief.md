# #890: default-only annotation propagation design

Date: 2026-10-13. Status: **API and semantic scope confirmed; implementation architecture proposed and feasibility-probed.**
Base: master `dea21a7cfa9b26630cacc5cdbc1087e756e59ce6`, verified against remote master during investigation.
Scope: proposed design and opt-in executable characterization only; no production implementation.
Publication checkpoint: the maintainer subsequently authorized a draft design PR. Hive selected
`Related: #890`, leaving implementation/acceptance/release admission open and curation/release indexes unchanged.

Formal design: [proposed ADR 0029](../adr/0029-default-only-annotation-propagation.md).
Execution outline: [dedicated ADR 0029 implementation plan](adr-0029-default-only-annotation-propagation.md).
These documents consolidate the current confirmed contract; the earlier alternatives below remain history.

## Current design checkpoint

The maintainer selected a retained, additive `@PropagateAnnotation(AutoCreate)` mechanism, independent
of LifecycleParticipant. The repeatable single-annotation shape and semantic scope below are confirmed.
The earlier sentinel and built-in-meta-annotation recommendations are superseded comparison history.
An isolated global pre-discovery pass demonstrates the proposed compiler architecture on Groovy 3/4/5;
third-party compatibility and full artifact/generated/IDE qualification remain implementation gates.
Production implementation remains unauthorized. The later draft-design-PR authorization does not
authorize implementation, issue edits, merge or release.

Maintainer refinement: replacing Required's converter with direct handling in the validation phase is
permitted as a design option. Required's current timing failure is consequently a removable dependency,
not a reason to reject propagation. No production implementation is authorized by this investigation.

**Confirmed conflict decision:** duplicate annotation types involving propagation fail, including
propagated versus explicit field annotations and duplicates contributed by multiple propagation sources,
even when their defaults are identical. Do not suppress, merge or prioritize them. Existing same-phase
creator conflicts remain errors. Ordinary existing repeatable declarations are not changed by this decision;
propagation must not silently use repeatability to merge conflicting contributions.

**Confirmed parameter boundary:** propagation uses annotation defaults only. No forwarding of domain
annotation members or configured annotation instances is included. A selected annotation with required
members has no usable default-only representation and must fail validation. Customized AutoCreate
key/type/value remains explicit alongside a mutation-only domain annotation. This scope is sufficient
for the maintainer's current need. The maintainer clarified that this was scope confirmation, not a
request to pause; the design interview continues. Production implementation remains unauthorized.

**Confirmed propagation depth:** expand one level only. Selecting an annotation that itself requests
propagation is rejected, rather than recursively expanded or silently left ineffective.

**Confirmed consumer scope:** the propagator may decorate a domain DefaultValues annotation directly,
even when it declares no LifecycleParticipant marker or handler. Such an annotation can request AutoCreate
on its applied field and then supply its existing DefaultValues members to the resulting child. A domain
lifecycle-participant annotation may use the same propagator independently or in combination.
Expansion produces ordinary field annotations for existing consumers; do not create separate propagation
vocabularies or per-consumer expansion. Default-only applies to the copied annotation types, not to the
domain annotation members already interpreted by DefaultValues or passed to external handlers.

**Confirmed declaration scope:** propagation applies to classes, fields and methods. Domain annotations
may carry Builder.Query or Cluster as well as AutoCreate; this explicitly includes compile-time behavior
and generated contracts, not just runtime annotation consumers. Each copied annotation must be legal on
its actual target and satisfy its ordinary semantic constraints. No parameter/package/constructor scope
is inferred. General propagation onto classes does not itself introduce #888's AutoCreate class defaults.

**Confirmed API shape:** make the propagation meta-annotation repeatable and let each occurrence select
one `Class<? extends Annotation>`, rather than one occurrence carrying a class array. The maintainer
suggested this shape to leave room for per-annotation customization later and accepted the resulting
`@PropagateAnnotation` proposal. The final internal architecture remains proposed. Default-only behavior
and duplicate rejection still apply; repeatable propagation declarations do not make their copied
annotations repeatable. No customization or forwarding members are added in this version.

**Confirmed compatibility requirement:** consuming Schemas must be recompiled when a domain annotation's
propagation declarations change. The maintainer explicitly accepted this as safe; runtime retrofit is
not required. ScHelm's fit review is integrated below; the next step is Hive review of the proposed ADR
and dedicated implementation plan before production implementation.

## External ScHelm consumer-fit qualification

Date: 2026-10-13. Source: ScHelm's concrete review, relayed by KlumAST Hive. This is external consumer-fit
evidence supplied to the design task; no ScHelm source inspection or ScHelm build was performed locally.

| Consumer question | Reported outcome |
| --- | --- |
| Current fields that could imply structural creation | OrderApplication.messaging, database, workload and api; OrderKafka.orderTopic and resultTopic |
| Field shape | Every listed case is a concrete, unkeyed direct DSL relationship |
| AutoCreate customization or coexistence | No current case needs key/type/value; no explicit AutoCreate must coexist after migration |
| FactBinding meaning | Fixed structural domain in current ScHelm; a future bind-if-present use can have a separate binding-only annotation |
| DefaultValues consumers | KafkaTopicDefaults, ResourceClassDefault and HttpEndpointDefaults fit; their members remain existing DefaultValues inputs while propagation adds bare AutoCreate independently |
| Nesting / forwarding | No demonstrated requirement; default-only, one-level propagation is sufficient |
| Conflicts | Explicit/propagated duplicates remain deterministic errors |

This resolves the requested consumer-fit check without adding a provider policy, fallback creator,
customization API or nested resolution. The review accepts the narrow design, not feature implementation,
publication, a ScHelm migration commit or a release assignment. Formal local ADR/plan preparation is
authorized at that review boundary; its original local-only restriction was later superseded only by
the maintainer's explicit draft-design-PR request. Production implementation and issue changes remain excluded.

## Historical recommendation among the original two options (superseded)

Prefer a narrowly defined omitted handler in `LifecycleParticipant.Creator`: only `phase = AutoCreate`
may omit it, meaning the existing AutoCreate field action with its default parameters. Represent omission
with the existing `LifecycleParticipant.CreationHandler.class` interface as a documented token. This needs
no public no-op handler or internal-handler exposure. Do not infer built-ins for AutoLink, Default or PostTree.

Keep **one creator claim per field/phase**, including composed claims; reject rather than silently prioritize
explicit field AutoCreate, a second domain built-in claim, or an external AutoCreate creator. Preserve ordinary
explicit AutoCreate behavior when no composed creator is present. Default-only composition is useful for
concrete, unkeyed structural children; exclude member forwarding deliberately. Keyed/abstract/configured
children keep the repeated mutation-only-domain + explicit AutoCreate workaround.

These were recommendations, not decisions. If strict conflicts make that narrow subset too inconvenient for
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

The real-DSL collector probe passes: the field ends up with AutoCreate and FactBinding, its handler receives
the configured domain member, and StructuralFactBinding is absent. Collector alias replacement and
argument mapping are documented Groovy behavior; the original domain annotation is retained here
because it is an item in the bundle, not the alias. That differs from decorating FactBinding itself
with a collector. The repository's Required converter contains an old comment that AnnotationCollector
did not work there; it does not establish a general current limitation, and this focused probe passes.

Latest checks: **12 investigation cases per Groovy 3/4/5 lane**, zero failures/errors/skips, in 27s.
These add three cases to the original nine; the earlier 168-case focused run remains the baseline evidence.

Additional contract decisions for a reusable propagator:

- Preserve the domain annotation and default-only class list. **Confirmed:** one level only, rejecting
  nested propagation; shared consumer scope includes DefaultValues and lifecycle participants.
  **Confirmed:** class, field and method placement. Do not automatically execute domain members.
- **Confirmed:** reject duplicate annotation types involving propagation, including propagated+explicit AutoCreate.
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

### Permitted Required refinement

The maintainer permits moving Required interpretation into validation rather than depending on its
RequiredToValidateTransformation. Source confirms that the converter only adds an empty Validate with
Required.value mapped to message and Required.level mapped to level. The existing field validator owns
Groovy truth, the boxed-Boolean non-null exception, field location/default message and severity reporting.
A direct Required branch in that existing validator, reached through ValidationPhase, can reuse those
rules without another traversal, phase or public lifecycle handler.

This removes Required as a dependency on local-transform discovery for propagated runtime validation.
It does not itself prove equivalence for arbitrary third-party AST annotations. Retain Schema diagnostics
independently of the converter: Required versus explicit Validate is currently forbidden, and primitive
boolean rejection currently arrives through Validate's compiler check. Preserve static/transient/Owner
exclusions, inherited layers, Deprecated explicit-validation behavior, class-level Validate, collected
issue suppression and Verify thresholds. Do not accidentally interpret Required as null-only for strings,
numbers or containers; false is valid for boxed Boolean but primitive boolean remains rejected.

Compatibility controls must include an old compiled Schema retaining **both Required and synthetic
Validate**: direct handling must not report the same violation twice. Treat an existing Validate as the
already normalized legacy path, or otherwise demonstrate equivalent single dispatch. Also record that
newly compiled fields would no longer expose synthetic Validate to reflection; source compatibility
alone does not preserve that annotation-presence contract. Required's runtime retention already exists.

If chosen, make this a small prerequisite slice with parity tests for the above cases, before qualifying
Required propagation. Until then the no-Validate probe remains accurate current-behavior evidence. The
leading design candidate is now retained, additive, default-only compile-time propagation. Duplicate failure
is confirmed; broad third-party transform scheduling remains an explicit maintainer decision.

### Shared DefaultValues and lifecycle consumer acceptance

The propagation marker is independent of LifecycleParticipant. A domain DefaultValues annotation may
request ordinary AutoCreate on its applied Schema field without any participant metadata or handler:

```groovy
@DefaultValues
@PropagateAnnotations([AutoCreate])
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MessagingDefaults { String name() default '' }

@DSL class Application {
    @MessagingDefaults(name = 'orders') Kafka messaging
}
```

Default-only copying does not prohibit the already supported `MessagingDefaults.name` consumption:
DefaultValues reads its explicitly supplied members using its existing mapping/empty-target rules. If
the domain annotation also opts into a participant, that handler receives the original annotation as
usual. No member is forwarded into AutoCreate. No new consumer-specific propagation SPI or effective-
annotation lookup is required for the physical-copy candidate.

The new probe compiles the validated domain annotation/handler before related Schema types, then builds
created, explicitly supplied and omitted-member children, both with and without a participant marker.
AutoCreate supplies missing children independently of participants; optional AutoLink mutation receives
the domain member; DefaultValues fills an empty child name without overwriting manual
input. An omitted domain member retains existing DefaultValues behavior (annotation defaults are excluded
by getNonDefaultMembers). The same original annotation remains available on the Schema field. Initial
fixture repair kept related Schema types in one compilation after a generated-Builder lookup failed when
splitting them across the in-memory test loader; no production change was made.

Do not confuse this with copying bare Default: Default's member defaults are syntactically optional, but
on a field its semantic validation requires exactly one of field/delegate/code. A default-only list does
not make that otherwise invalid field use meaningful. Existing consumer validation remains authoritative.
The DefaultValues clarification specifically permits the propagator **on a DefaultValues annotation**
without participants. The subsequent declaration-scope decision separately accepts class, field and
method propagation; no #888 class-level AutoCreate policy follows from either decision.

Earlier shared-consumer probe qualification: **13 investigation cases per Groovy 3/4/5 lane**, zero
failures/errors/skips. Groovy 3 passed in 9s and the Groovy 4/5 run passed in 17s. XML results and local
Markdown links were inspected; diff whitespace checks passed. Only design evidence and opt-in fixtures changed.

Final clarified scope qualification: **14 investigation cases per Groovy 3/4/5 lane**, zero
failures/errors/skips, in 28s. This includes DefaultValues-only and combined-participant domain annotations;
the test asserts participant metadata is absent in the independent case. No production change is included.

### Class, method and repeatable-shape feasibility

The newer isolated probe registers a real temporary global AST transformation via Groovy's service
discovery at SEMANTIC_ANALYSIS. Unlike a late CompilationCustomizer, Groovy schedules that global pass
after resolution and before local-transform discovery on the qualified lanes. The pass snapshots original
class/field/method declarations and adds ordinary default-valued annotations, retaining the domain markers.
It is not production registration and does not implement the proposed conflict/nesting defenses.

The probe demonstrates all of the following through generated factories and runtime Model results:

- A class domain annotation carries DSL, producing the normal Factory/Builder machinery.
- A method domain annotation carries Builder.Query; the query runs against current Builder state and the
  completed Model. Original Query purity/type checks still belong to the regular compiler pipeline.
- A field domain annotation carries Cluster, whose existing early local transform turns the field into
  a projection getter. The generated cluster factory can configure children normally.
- A method domain annotation carries Cluster, generating the expected projection getter.
- A field domain annotation carries Required; its existing converter is discovered and adds Validate.
  Direct Required runtime handling remains permitted but is no longer a prerequisite for this approach.

This resolves the earlier late-customizer scheduling counterexample for the demonstrated KlumAST
annotations; arbitrary third-party transforms, global-transform ordering interactions and native IDE
source recognition remain qualification work. A default-only copied Cluster on a class must still
satisfy its ordinary class-placement semantics; generic target support does not waive member constraints.

The proposed singular repeatable API is:

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Repeatable(PropagateAnnotation.List)
@interface PropagateAnnotation {
    Class<? extends Annotation> value()

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface List { PropagateAnnotation[] value() }
}
```

Example domain declaration:

```groovy
@DefaultValues
@PropagateAnnotation(AutoCreate)
@PropagateAnnotation(Required)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MessagingDefaults { String name() default '' }
```

Each request has one future customization location, without an index-based correspondence between
annotation classes and separate configuration arrays. It does not make arbitrary annotation-instance
values expressible in Java annotation members; any later fixed-value or forwarding API still requires
its own design. The marker/container are repeatable on domain annotation declarations; applied domain
repeatability is a separate existing contract. Two requests for AutoCreate still fail under the confirmed
duplicate rule, even if repeated propagation marker syntax is legal.

The repeatable-shape probe compiles the domain annotation first, then its Schema use. Both repeated
markers and explicit List containers expand AutoCreate/Required, retain the DefaultValues annotation
and its explicit member, and create/default the child normally. The source AST path and reflected
container metadata both require deliberate marker/container extraction.

Final checks: **17 investigation cases per Groovy 3/4/5 lane**, zero failures/errors/skips, in 31s.
The earlier class/method-only addition also passed all lanes (15 cases each). These are feasibility
fixtures, not completed duplicate validation, third-party compatibility, generated-descriptor parity,
artifact-only consumer or IDE acceptance. No production source/build/service-registration change.

### Current generic implementation-plan outline (proposed)

Use a new proposed ADR after final confirmation (classification C); ADR 0028's runtime dispatch is
unchanged. Place PropagateAnnotation and its repeatable container in klum-ast-annotations; keep the
global expansion in klum-ast, without a runtime interpreter or module dependency reversal.

1. **AP-1 — one pre-discovery vertical slice.** Introduce the singular marker/container and one global
   expansion pass before local-transform collection. Cover class/field/method targets, retained original
   annotations, source and separately compiled domain declarations. Tests and production change belong
   together in a focused commit. Demonstrate AutoCreate and a domain DefaultValues annotation without
   requiring LifecycleParticipant. Preserve source positions for diagnostics.
2. **AP-2 — bounded declaration defenses.** Fail duplicate contributions (direct/propagated/multiple
   sources), nested propagation, missing required members, illegal annotation targets and ordinary
   semantic violations. Support source repeats and explicit/precompiled containers. Make processing
   idempotent per original declaration without dropping legitimate repeatable mutations. Existing
   same-phase creator diagnostics remain authoritative after expansion. No member forwarding.
3. **AP-3 — generated behavior.** Query, Cluster field and method, class DSL, and existing Required
   conversion; purity/shape/target negative controls, inherited declarations, original runtime metadata
   and generated Builder/Factory/source-mirror parity with explicit annotation input. Class/field/method
   selection copies annotations, not methods or inherited declarations. No lifecycle or validation rewrite
   unless independent evidence requires one; Required runtime handling is an allowed alternative.
4. **AP-4 — compatibility and authoring qualification.** Java/Groovy domain-annotation libraries and
   separate Schema/consumer artifacts, compiler-free runtime execution, Groovy 3/4/5 and applicable JPMS.
   Qualify a foreign local transform and imported/aliased nested annotation classes; characterize global
   ordering and native IDE behavior. Expanded Schema annotations are compiled snapshots: changing a
   library's requests requires Schema recompilation. No runtime retrofit or old-compiler promise.
5. **AP-5 — public decision and guidance.** Documentary tests linking Default-Values, Model-Phases,
   Builder.Query and Cluster guidance as applicable; CHANGES, migration/minimum-compiler guidance,
   accepted ADR/public inventory and final implementation evidence. Keep #888 class-default policy
   separate. Final review/required checks and separately authorized publication follow implementation.

## Remaining qualification and delivery boundaries

No major API/semantic question remains in the agreed current scope: repeatable single requests,
retained domain annotation, class/field/method targets, annotation defaults only, duplicate failure,
one level with nested requests rejected, ordinary target/semantic validation, DefaultValues-only and
participant domain annotations, and compile-time Query/Cluster behavior.

Before production delivery:

- Review the now-prepared proposed ADR 0029 and dedicated implementation plan. Its AP-1–AP-5 slices
  consolidate the current work; the older lifecycle-specific slices are comparison history.
- Implement complete diagnostics, repeatability and inheritance controls, separately compiled Java/Groovy
  libraries and compiler-free consumers, generated-contract/source-mirror parity, third-party local-transform
  and global-ordering checks, applicable JPMS and native IDE qualification. The 17-case-per-lane fixture
  establishes feasibility; it does not replace these gates.
- Confirm 4.1 release placement and authorize production implementation separately. The current publication
  authority covers a draft design PR only; issue changes and release work remain outside this task.

Confirmed compatibility consequence: propagated annotations are compiled into Schema declarations. Changing a domain
annotation's propagation requests requires recompiling consuming Schemas. It does not retrofit existing
Schema binaries at runtime. Preserve ordinary explicit-annotation behavior and document the minimum compiler
version rather than promising new propagation on older compilers. Required's runtime refactor is optional:
the demonstrated early pass discovers its existing converter, so that refactor need not expand this feature.

## Validation and handoff

Final focused check passed in 1m 15s with JDK 17: **56 cases per lane on Groovy 3/4/5**, zero failures,
errors or skips (168 total). Per lane: nine investigation cases, eight composition, 26 phase,
eight existing AutoCreate and five replaced-binary declaration controls. XML results were inspected.
The Java representation probe compiles real annotation classes with the JDK compiler; candidate runtime
dispatch remains unimplemented. Diff whitespace and local Markdown file-link checks passed.
Initial probe-run repairs were confined to fixtures: skip buildSrc in the initializer and isolate the
annotation target error from same-source KlumCast validation. No production fix was attempted.
No full check, live IDE, new feature JPMS, SonarCloud or remote CI qualification is claimed for this design.

Formalization continuation: only the brief, proposed ADR 0029 and dedicated implementation plan changed.
Their 18 relative file links/heading anchors, fenced blocks, CommonMark/GFM rendering and diff whitespace
checks passed; scope inspection confirmed no production/build/probe edits. The ScHelm review is recorded
as externally reported consumer-fit evidence. No Groovy lanes were rerun for these prose-only changes,
following docs/agents/testing.md; the historical probe results above remain bounded feasibility evidence.

Hive callback: the external ScHelm fit report is integrated, and the formal proposed ADR 0029 and dedicated
plan are prepared locally for Hive review. API/semantic decisions are confirmed; full compiler architecture
qualification, canonical issue-scope reconciliation and release placement remain pending. Production
implementation needs separate authorization. Retain the worktree and evidence; do not archive this worker
as reconciled. The subsequent authorized draft PR carries Related: #890 and leaves the issue open; no
curation/release-index change is included. The final callback identifies its head, validation and CI state.
No release/run IDs or external evidence-store authorization were supplied for this investigation; no
external operational event stream was created. The local report and reproducible probes are the evidence boundary.
