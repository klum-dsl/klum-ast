# ADR 0024 deferred implementation plan: Seeded relationship creation

Status: SEED-0 through SEED-3 deferred beyond 4.1; no implementation release scheduled.

This preserves the researched implementation plan for [#342](https://github.com/klum-dsl/klum-ast/issues/342) and
[ADR 0024](../adr/0024-seeded-relationship-creation.md). No automatic runtime/compiler/generated API is delivered by this
document. The slices below are future reference, not active 4.1 work, a release commitment, or a release gate.

## Current disposition

The 2026-10-01 maintainer decision supersedes the original 4.1 implementation horizon. Overload collisions with converters
and Schema-defined Builder methods, highest-DSL-domain and ancestor/sibling policy, runtime subtype versus declared
refinement contracts, dynamic/static asymmetry, keyed Template identity, live Builder/session eligibility, and occupied
relationship replacement/lifecycle behavior require additional policy/API work beyond the intended 4.1 QoL scope. See
[ADR 0024's current disposition](../adr/0024-seeded-relationship-creation.md#current-disposition).

**SEED-0 through SEED-3 are deferred beyond 4.1.** The design and acceptance details remain available for possible future
implementation, without being rewritten as rejected decisions. #342 remains open for future reconsideration; #812 remains
a separate future domain-boundary investigation. Resuming these slices requires a new maintainer decision on the remaining
policy and compatibility work.

4.1 provides the existing Builder QoL primitives and documents
[explicit Schema-owned helpers](../user/Tips-and-Tricks.md#name-a-builder-helper-for-a-seeded-relationship) as the supported
practical route. No automatic `relationship(seed) { refinement }` overload is delivered in 4.1.

## Historical authority and evidence checkpoint

KlumAST source base: `b747422ca5a149065cf612cae749563f9d92329d`, observed 2026-09-30. The maintainer's in-task
clarification is authoritative: exactly one `relationship(seed) { refinement }` form accepts a materialized Model,
Builder, or Template; the input only seeds a fresh Builder. Earlier bounded-default scoping is superseded. The subsequent
maintainer direction fixes exactly two seed signatures at the relationship's highest DSL superclass: one Model and one
public Builder input. Ancestors, descendants, and siblings inside that selected domain are accepted; unrelated domains
are rejected statically. There is no broad marker or per-ancestor signature set. The maintainer also accepts the keyed
Template limitation: an existing Schema key provider or seed supplies any required construction key, otherwise a keyless
Template fails before allocation. Explicit-key creation plus `copyFrom(template)` remains the route for a caller-supplied
key. These choices are preserved design reference; the current deferral above governs implementation and release scope.

| Evidence | Confirmed fact / limit |
| --- | --- |
| [#342 and its history](https://github.com/klum-dsl/klum-ast/issues/342) | Relationship syntax and synthetic/superclass type questions originated here; the original Schelm demand and 4.1 horizon preceded the 2026-10-01 implementation deferral |
| [#135](https://github.com/klum-dsl/klum-ast/issues/135), `CollectionFactoryTemplateExpansionTest`, `TemplatesDocumentaryTest` | Existing collection `withTemplates` expands one owned child per marked Template, not an ambient scope or arbitrary copy source |
| ADRs 0003/0004, `CopySourceProtocolSpec` | Completed Models copy values; Templates replay recipes; live same-session unsealed Builders copy values plus pending-action snapshots |
| ADRs 0016/0017, `TemplateScopeTest` | Generated application and test-support lifetime are already defined; their registration mechanisms stay unchanged |
| `FactoryHelper`, `InternalKlumBuilder`, `CopyHandler` | Existing seams own allocation, active defaults, source validation, recipe copying, paths, ownership, and recipient lifecycle |
| [Catwalk main at `97a787ecc2696569327d42f6cf5d59999c5503ac`](https://github.com/klum-dsl/klum-catwalk/tree/97a787ecc2696569327d42f6cf5d59999c5503ac/showcases) | Smart-home Layer 3 and Helm direct-Schema journeys exist. Smart-home abstract `HeatedRoom` and concrete rooms establish a useful base-Template example; no new seeded setter is proven there |
| Schelm | Current demand is verified through #342's maintainer comment. No authoritative Schelm demo source was available in this investigation; do not claim its exact Schema, key requirements, or syntax have been exercised |

Catwalk is consumer/example evidence, not an added release gate or authority to change its repository. The smart-home
source observation was cross-checked against its existing local showcase source; no new consumer build was run. The Helm
journey uses an existing `copyFrom` path and is not itself a Template feature proof.

## Current behavior and failure paths

- `InternalKlumBuilder.createSingleChild` configures an existing Builder when the direct field is occupied.
  `addNewDslElementToMap` likewise refines an existing key. The seeded form must bypass these reuse branches.
- `FactoryHelper.prepareNestedBuilder` applies active Templates, `PostCreate`, explicit configuration, and `PostApply`.
  `prepareNestedBuilderFromTemplate` offers a narrow fresh allocation/copy path for #135, but accepts only that use case.
- `FactoryHelper.effectiveRecipeType` prefers a compatible concrete recipe type, otherwise uses the declared type's
  default implementation. It is not sufficient by itself for the new contract: field defaults, highest-DSL-domain
  membership (including siblings), invalid-source rejection, and synthetic Template normalization need explicit checking
  before allocation. `CopyHandler` defaults missing fields to `FAIL`; sibling source admissibility does not remove that
  existing copy-policy check.
- `InternalKlumBuilder.copyFrom` accepts null as no-op, checks live Builder eligibility, copies values with `CopyHandler`,
  and replays/snapshots pending actions. The new setter rejects a null seed before this existing method is called.
- `CopyHandler` ignores source Key/Owner/Role/transient/ignored fields, rehydrates composition into fresh Builders, and
  respects overwrite rules; key identity must be selected earlier through creation rather than copying.
- `TemplateManager` scopes restore thread-local state; `FactoryHelper` snapshots active Templates on created Builders and
  `BuilderVisitingPhaseAction.withCurrentTemplates` supplies them for later phases. Seed copying changes none of these.
- Completed Models and Templates cannot be directly adopted into composition. The explicit seed argument is a copy
  request; one-argument setters continue enforcing existing ownership and LINK semantics.

## Accepted descriptor and key boundary

Let `D` be the relationship's highest DSL superclass, or the relationship type itself if it has no DSL ancestor.
Let `R` be the declared relationship/element Model type. Generate exactly:

```java
R_DSL.Builder<R> relationship(D seed, Closure<?> refinement);
R_DSL.Builder<R> relationship(D_DSL.Builder<? extends D> seed, Closure<?> refinement);
```

For `Bedroom` below highest DSL ancestor `HeatedRoom`, these are
`bedroom(HeatedRoom, Closure)` and `bedroom(HeatedRoom_DSL.Builder<? extends HeatedRoom>, Closure)`, returning
`Bedroom_DSL.Builder<Bedroom>` and delegating refinement to the public `Bedroom_DSL.Builder` with `DELEGATE_ONLY`.
The Builder source wildcard is required for self-typed descendant Builder assignability; erased Builder seed type is
`HeatedRoom_DSL.Builder`. Templates use the Model input. No `Object`, `KlumModelObject`, generic `KlumBuilder`, union
marker, or per-ancestor fallback is generated. Accept siblings sharing `D`; recipient selection still respects `R`.
Choosing/skipping technical or non-domain DSL ancestors belongs to the future
[#812 domain-boundary investigation](https://github.com/klum-dsl/klum-ast/issues/812), outside #342. The highest DSL
ancestor is used without semantic filtering; #812 is not an implementation or publication dependency.

**Accepted key boundary:** only a seed key or existing Schema relationship key provider can supply a required construction
key in this form. A keyless Template without such a source fails before allocation. Use explicit-key creation followed by
`copyFrom(template)` when the caller must supply the key. No extra key or Map seed overload belongs to #342.

**SEED-0 implementation acceptance — signature/collision proof:** inventory same-arity custom/converter methods and
existing Class/Factory/Map/Closure families using the confirmed pair of descriptors. Record actual collisions and require
a maintainer disposition if preserving both meanings is impossible. The initial planning-only change had not established
collisions; subsequent investigation exposed converter/custom-method conflicts and prompted deferral. A compatibility-safe
disposition is required before any future dependent runtime implementation; the original descriptor pair remains design
reference rather than an approved resolution of those conflicts.

Do not add speculative pending tests. The #342 issue can be synchronized through its owner's normal workflow; this task
changes no issue state, curation index, or milestone. This deferred plan establishes no 4.1 release commitment or gate;
tracker reconciliation remains with the issue owner.

## Affected modules and generated seams

| Area | Seam | Intended responsibility |
| --- | --- | --- |
| Runtime | `runtime.internal.FactoryHelper`, `InternalKlumBuilder`, `CopyHandler` | One shared fresh-child path; type/key/source checks; existing copy protocol and lifecycle; attach only after successful refinement |
| Compiler | `compiler.internal.ast.DSLASTTransformation` | Generate direct-child and collection/map element seed creators with required closure; LINK omission; correct method tags/visibility/delegates |
| Collection/Cluster forwarding | `ast.AlternativesClassBuilder` and `layer3.ClusterFactoryBuilder` | Forward the same setter shape to its owning relationship; do not invent collection-wide seed batching |
| Public contracts | `GeneratedDslSupport`, generated `Foo_DSL.Builder` interfaces | Exactly two highest-DSL-domain Model/Builder input descriptors, declared child-Builder result/delegate, no implementation descriptors |
| Runtime linkage | Existing `runtime.generated` Builder bridge | If a new JVM method is necessary, expose only the generated-only linkage under ADR 0015; keep source/session mechanics internal |
| IDE/docs | Source mirrors, existing GDSL, AnnoDocimal | Match actual generated overloads, required closure, freshness, source restrictions, and declared delegate |
| User guidance | `docs/user/Templates.md`, relationship/copy guidance, Builder-first migration, `CHANGES.md` | Document delivered syntax, source distinctions, keys, freshness, scope precedence, and documentary traceability after implementation |

The original plan proposed no new annotations, serialization format, runtime registry, test-support artifact, or root
composition coordinator. A compatibility-safe API shape requires future reconsideration; this plan authorizes no new API.
Check current source names if implementation resumes; the issue-curation architecture map includes historical
pre-package-migration links and is not a substitute for the present package tree.

## Deferred dependency-ordered tracer slices and reasoned commits

All four slices below are deferred beyond 4.1. Their dependencies and acceptance criteria are preserved for future
reconsideration; none is scheduled implementation work or a 4.1 release gate.

### SEED-0 — Prove the accepted generated seam and signature compatibility

The preserved ADR and key policy are the starting reference if implementation is reconsidered. SEED-0 must first establish
compatibility-safe generation and obtain a maintainer disposition for converter/custom-method collisions before dependent
runtime work. If a minimal compiler probe is needed, retain its evidence only when it tests the selected contract; no
throwaway code becomes API. One reasoned evidence/test commit would record the verified descriptor and collision result.
Do not broaden the preserved form in response to a collision without a separate decision.

Acceptance: a descriptor table and representative calls show the same two-argument operation for all three seed states
with exactly one highest-DSL Model input and its wildcarded public Builder input. Cover no-DSL-ancestor relationships,
multi-level DSL hierarchies without intermediate overloads, ancestor/descendant/sibling positive calls, and unrelated-domain
negative static calls. No root/list/key/map variant or broad fallthrough; no reinterpretation of valid existing calls;
explicit resolution for real collisions. Technical ancestor filtering is neither implemented nor promised.

### SEED-1 — Direct owned child, all three source categories

Depends on SEED-0. One vertical runtime/compiler/test commit adds direct relationship generation and a shared runtime
helper that validates then allocates a fresh recipient, runs existing defaults/lifecycle, copies the seed, refines, and
attaches. A second focused commit adds the base/synthetic/default-implementation branch if its accepted policy needs a
separate reasoning step; otherwise keep it in the first commit. Keep implementation and green tests together.

Acceptance in a new `SeededRelationshipCreationTest` (`@Issue("342")`):

- Ordinary completed Model, marked Template, and same-session child Builder each create distinct owned children.
- Model actions do not replay; Template actions replay against the recipient; Builder pending actions snapshot and
  subsequent source field assignments do not update the recipient (Simple Values retain existing reference semantics).
  The source ownership and Template identity remain intact.
- Closure delegates to the fresh public Builder, receives no seed parameter, runs exactly once, and its result does not
  replace the Builder result. `{}` is valid; missing closure has no new seed-only overload.
- A populated direct relationship receives a fresh replacement; source/current/returned/previous Builder identities
  remain distinct. Displaced children do not unexpectedly participate as owned graph nodes in final validation.
- Active type defaults precede seed copy and refinement under existing overwrite policies; ancestor/concrete/sibling/
  synthetic seed selection and field/type defaults match the ADR matrix. An admitted sibling seeds a fresh relationship
  type/default implementation, never an incompatible sibling recipient. Cover both successful compatible sibling values
  and existing missing-field policy failure; no silent sibling-field filtering. No synthetic ordinary Model is instantiated.
- For the ADR's `Room` → `Bedroom` → `LuxuryBedroom` hierarchy and `Flat.bedroom: Bedroom`, a completed
  `LuxuryBedroom` seed creates a fresh actual `LuxuryBedroom` Builder and completed recipient. Assert identities differ
  from the seed, declared `windows` refinement succeeds, and descendant-only `sauna` seed state is copied under existing
  `CopyHandler` policy rather than truncated to `Bedroom`. Include a copy-policy override control so this proves reuse of
  existing policy, not a second clone routine. Recipient callbacks and descendant composition remain in the same session.
- Dynamic descendant refinement follows accepted option 1: `bedroom(luxurySeed) { sauna true }` succeeds on the fresh
  `LuxuryBedroom` delegate, invokes the closure once, and leaves the seed unchanged. Use a seed with `sauna: false` to
  distinguish refinement from merely copying an already true value. A companion dynamic `Bedroom` seed does not gain
  `sauna`; its unsupported call retains normal missing-method behavior and failure attachment semantics. No delegate
  wrapper or member allowlist restricts the concrete Builder to the declared static surface.
- Key provider/seed key precedence and missing keyed-Template identity match the accepted policy.
- Recipient callbacks and graph materialization/validation execute in the outer session only; ordinary root Factory
  calls are never used to allocate children. Template definition keeps its existing callback omissions.

### SEED-2 — Collection/map elements, Cluster forwarding, and errors

Depends on SEED-1; reuse the shared fresh-child path. One reasoned commit extends the existing element creator generation
and Collection/Cluster forwarding, with tests for ownership and map/key behavior. A second commit is justified only for
nontrivial error/collision coverage needing a distinct compiler diagnostic seam.

Acceptance:

- One call creates one fresh element. Repeated calls preserve invocation order and existing collection duplicate policy;
  repeated map keys replace with fresh Builders rather than invoking the old occupied-key refinement path.
- Direct collection/map element adders and their factory delegates expose identical seed semantics; #135 iterable
  Template expansion retains its exact signature and behavior.
- A genuine Layer 3 fixture has abstract Domain API room types, concrete Schema fields, and a bounded/unbounded Cluster
  projection as appropriate; an accepted highest-domain base Template and compatible sibling Model/Builder can seed
  concrete named rooms. The two seed signatures use the same domain in direct and forwarding contracts.
- `OPTIONAL_LINK` always owns the new result; ordinary source may still be an aggregation target elsewhere; `LINK` has no
  seeded creator in bytecode, mirrors, or static surface. Nested composition is fresh and LINK identity follows existing
  copy rules.
- Sealed/cross-session/inactive Builders, unrelated/non-DSL input, null seed/closure, abstract unresolved recipient, and
  missing required key fail with actionable diagnostics before refinement/attachment. Existing relationship and ambient
  Template state survive a caught failure. No global rollback of user code is promised.
- Nested seeded calls have independent fresh targets. `Template.With/WithAll` and test-support `TemplateScope` already
  active around creation restore normally after success/failure; saved Builder defaults still work in later phases.
- Existing converter, custom setter, Class/Factory-token, Map, ordinary closure, and one-argument setter calls retain
  dispatch. Accepted compiler collision diagnostics identify the relationship/signature and remedy.

### SEED-3 — Public/static/IDE parity and documentary closure

Depends on SEED-2. One focused commit proves generated descriptors, Java consumption, `@CompileStatic` Groovy, source
mirrors, and generated Javadocs; add GDSL changes only if its existing literal-property role requires them. A final
reasoned documentation commit synchronizes delivered behavior and release navigation.

Acceptance:

- `GeneratedDslSupportSpec` covers exact source/result descriptors, required closure and `DELEGATE_ONLY` public child
  delegate, exactly the two selected-domain seed inputs (and no fallthrough/intermediate-ancestor forms), no hidden
  implementation/session types, source-mirror parity, inherited relationships, and collision cases.
- For the same `LuxuryBedroom` seed case, inspect both generated `bedroom` seed overloads: erased JVM return remains
  `Bedroom_DSL.Builder`, generic return remains `Bedroom_DSL.Builder<Bedroom>`, and `@DelegatesTo` remains
  `Bedroom_DSL.Builder` with `DELEGATE_ONLY`. In this hierarchy the seed parameter pair is `Room` and
  `Room_DSL.Builder<? extends Room>`. Runtime recipient selection must not generate a Luxury-specific overload, return
  signature, or delegate hint. Java consumption assigns the result to the declared public Builder contract.
- Source mirrors, generated Javadocs, and applicable IDE metadata reproduce that declared return/delegate surface and
  offer `windows`, not descendant-only `sauna`, as seeded-refinement completion. They must not infer a more specific
  contract from either a statically typed or runtime `LuxuryBedroom` seed. Explanatory Javadocs may describe dynamic
  dispatch, but their advertised types/operations stay declared. Inspect the seeded method metadata, not the legitimate
  standalone `LuxuryBedroom_DSL.Builder` contract, which naturally contains `sauna`.
- Use separately generated Schema contracts in Java and `@CompileStatic` Groovy 3/4/5 consumers. A positive seeded
  `bedroom(luxurySeed) { windows 3 }` call compiles and returns the declared Builder. A negative consumer with
  `bedroom(luxurySeed) { sauna true }` fails static checking with a missing `sauna` operation on the declared Builder,
  even when the seed variable is statically `LuxuryBedroom`. Keep closure owner/lexical methods free of a same-named
  `sauna` helper so the negative test isolates the delegate contract; do not use dynamic escapes or casts. The equivalent
  unannotated dynamic consumer succeeds as required by SEED-1.
- A positive static consumer uses `bedroom(LuxuryBedroom.Create) { copyFrom luxurySeed; sauna true }` and compiles against
  the exact Factory-token-selected `LuxuryBedroom_DSL.Builder`. Verify its descendant refinement and fresh result. This
  is the existing statically typed route; ordinary dynamic Class selection plus `copyFrom` does not gain a new exact
  subtype completion promise. Runtime-seed-based overloads and static/IDE inference remain excluded.
- `SeededRelationshipsDocumentaryTest` carries `@Issue("342")`, `@Tag("documentary")`, and `@See` for the current
  `docs/user/` section. It shows completed Model, Template, and live Builder sources plus one genuine Layer 3 example.
- Update Templates and relevant relationship/copy pages, Builder-first guidance, Migration navigation if a new guide is
  introduced, `CHANGES.md` under the eventual implementation release, and generated public-surface inventory where applicable.
  Current documentation must distinguish this fresh setter from existing occupied-child refinement and #135 expansion.
- A later Catwalk/Schelm consumer exercise is useful demand validation if commissioned separately; it is not silently
  made a release gate or a cross-repository implementation assignment.

## Validation and delivery

If future implementation is authorized, use focused Groovy 3 tests first, then affected runtime/AST suites. Run the
Groovy 4 and Groovy 5 compatibility lanes at the final feature tip because overload dispatch, delegate metadata, and generated bytecode are
version-sensitive. Check generated mirrors/Javadocs and representative Java/static Groovy consumers. Newly added tests
need driving `@Issue`; only materially changed existing tests need traceability amendments. No ignored test without an
actionable reason. Existing Spock fixtures and copy/ownership coverage should be extended rather than duplicated.

This planning-only change needs Markdown relative-link/structure review and `git diff --check`; no Groovy lane is required
because it alters no runtime/build/test source or executable fixture. Snippets illustrate the accepted syntax and have
not been compiled as a new feature. Review the local commit history before any publication; preserve reviewed commits later.

Issue-to-slice mapping: #342 → SEED-0 through SEED-3; #135, #431, #710/#737, and #658 are preserved contracts, not reopened
implementation dependencies. #304 is separate root-layer work. No issue creation, label/milestone change, dependency edge,
or issue closure is authorized by this design task.

## Review risks and handoff

The researched design preserves dynamic descendant-only refinement through normal runtime dispatch (option 1), while
static/IDE refinement stays at the declared relationship Builder. This is future design, not delivered behavior. Remaining
policy/API work and implementation risks include accidental metadata specialization or accidental runtime narrowing of that delegate, sibling copy-policy failures,
source/overload collisions, missing construction keys, and replacing an occupied owned Builder without leaking lifecycle
participation. These are executable acceptance checks; signature/collision proof belongs to SEED-0 rather than a design
publication gate. Broader cloning, arbitrary POJO seeds, new root APIs, mutation of completed Models, Template registration
changes, and #812's future domain classification are outside the contract.

A documentation handoff states the exact worktree/branch, base/final commit, checks, tracker impact, and PR/CI state.
Use `Related: #342`; #342 remains open for future reconsideration and #812 remains a separate future investigation.
No runtime feature is delivered. Merging the disposition documentation preserves the researched design and deferred plan;
it does not deliver automatic seeded relationship overloads or make SEED-0 through SEED-3 a 4.1 commitment or gate.
