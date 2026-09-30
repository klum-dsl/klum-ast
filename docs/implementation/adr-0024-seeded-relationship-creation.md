# ADR 0024 implementation plan: Seeded relationship creation

Planning only for [#342](https://github.com/klum-dsl/klum-ast/issues/342) and
[proposed ADR 0024](../adr/0024-seeded-relationship-creation.md). No runtime/API change is delivered by this document.

## Authority and evidence checkpoint

KlumAST source base: `b747422ca5a149065cf612cae749563f9d92329d`, observed 2026-09-30. The maintainer's in-task
clarification is authoritative: exactly one `relationship(seed) { refinement }` form accepts a materialized Model,
Builder, or Template; the input only seeds a fresh Builder. Earlier bounded-default scoping is superseded. The subsequent
maintainer direction fixes exactly two seed signatures at the relationship's highest DSL superclass: one Model and one
public Builder input. Ancestors, descendants, and siblings inside that selected domain are accepted; unrelated domains
are rejected statically. There is no broad marker or per-ancestor signature set.

| Evidence | Confirmed fact / limit |
| --- | --- |
| [#342 and its history](https://github.com/klum-dsl/klum-ast/issues/342) | Relationship syntax and synthetic/superclass type questions originated here; current maintainer comment records Schelm demand, 4.1 horizon |
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

## Confirmed descriptor and remaining gate

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
Choosing/skipping technical or non-domain DSL ancestors belongs to a future interface/domain-modeling decision, outside
#342. The highest DSL ancestor is used without semantic filtering.

**Remaining product gate — keyed Templates:** confirm that only a seed key or existing relationship key provider is
sufficient for this first form. A keyed Template without such a source must fail; arbitrary key input is a later explicit
design choice. No implementation slice is admitted while this choice remains open.

**Implementation acceptance — signature/collision proof:** inventory same-arity custom/converter methods and existing
Class/Factory/Map/Closure families using the confirmed pair of descriptors. Record actual collisions and require a
maintainer disposition if preserving both meanings is impossible; no present conflict has been established by this
planning-only change. The chosen descriptor is no longer a product question.

Do not add speculative pending tests or treat the keyed recommendation as accepted API. The #342 issue can be synchronized
after maintainer acceptance by its owner; this task changes no GitHub state, curation index, release gate, or milestone.

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

No new annotations, serialization format, runtime registry, test-support artifact, or root composition coordinator is
required. Check current source names at implementation time; the issue-curation architecture map includes historical
pre-package-migration links and is not a substitute for the present package tree.

## Dependency-ordered tracer slices and reasoned commits

### SEED-0 — Accept the contract and prove the generated seam

Depends on the keyed-Template decision and signature/collision proof. Verify the confirmed descriptor pair before
acceptance; do not publish a runtime feature to discover its product contract. Record the confirmed seed-domain and
accepted key policy in ADR 0024 and #342 through the normal maintainer/Hive workflow. One reasoned documentation commit captures the final decision and narrowed plan. If a minimal
compiler probe is needed, retain its evidence only when it tests the selected contract; no throwaway code becomes API.

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
- Java and Groovy 3/4/5 compile against separately generated Schema contracts. Runtime subtype inference never promises
  exact subtype completion from a base-typed seed; existing explicit Factory-token creator remains the exact-type path.
- `SeededRelationshipsDocumentaryTest` carries `@Issue("342")`, `@Tag("documentary")`, and `@See` for the current
  `docs/user/` section. It shows completed Model, Template, and live Builder sources plus one genuine Layer 3 example.
- Update Templates and relevant relationship/copy pages, Builder-first guidance, Migration navigation if a new guide is
  introduced, `CHANGES.md` under the 4.1 development section, and generated public-surface inventory where applicable.
  Current documentation must distinguish this fresh setter from existing occupied-child refinement and #135 expansion.
- A later Catwalk/Schelm consumer exercise is useful demand validation if commissioned separately; it is not silently
  made a release gate or a cross-repository implementation assignment.

## Validation and delivery

During implementation use focused Groovy 3 tests first, then affected runtime/AST suites. Run the Groovy 4 and Groovy 5
compatibility lanes at the final feature tip because overload dispatch, delegate metadata, and generated bytecode are
version-sensitive. Check generated mirrors/Javadocs and representative Java/static Groovy consumers. Newly added tests
need driving `@Issue`; only materially changed existing tests need traceability amendments. No ignored test without an
actionable reason. Existing Spock fixtures and copy/ownership coverage should be extended rather than duplicated.

This planning-only change needs Markdown relative-link/structure review and `git diff --check`; no Groovy lane is required
because it alters no runtime/build/test source or executable fixture. Proposed snippets are illustrative and have not
been compiled as a new feature. Review the local commit history before any publication; preserve reviewed commits later.

Issue-to-slice mapping: #342 → SEED-0 through SEED-3; #135, #431, #710/#737, and #658 are preserved contracts, not reopened
implementation dependencies. #304 is separate root-layer work. No issue creation, label/milestone change, dependency edge,
or issue closure is authorized by this design task.

## Review risks and handoff

The remaining product gate is keyed Template creation with only two arguments. Implementation risks are sibling
copy-policy failures, source/overload collisions, and replacing an occupied owned Builder without leaking lifecycle
participation. These are recorded as a gate or executable acceptance, not hidden implementation details. Fresh copy
semantics and the two highest-DSL-domain seed descriptors are confirmed; broader cloning, arbitrary
POJO seeds, new root APIs, mutation of completed Models, and Template registration changes are outside the contract.

A design handoff must state the exact worktree/branch, base/final commit, checks, remaining decision gates, no GitHub
mutation, and retained delivery condition. Request Hive reconciliation rather than self-archiving. Do not publish this
proposal as an Accepted decision or start runtime work while a substantive product choice remains unresolved.
