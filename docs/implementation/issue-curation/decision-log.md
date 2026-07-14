# Issue-curation decision log

This log records the reasoning rules used during the 2026-07-13 read-only curation pass. It is not a product decision record and does not replace an ADR or maintainer decision.

## D-01 — Evidence hierarchy

**Decision:** Rank evidence in this order:

1. accepted ADRs and current release/migration statements;
2. current source plus executable tests;
3. current issue body/comments and merged PR/commit history;
4. current wiki feature documentation;
5. historical Roadmap statements only as historical context.

**Reason:** `wiki/Roadmap.md` still describes old 2.x/3.x milestones and calls 4.0 “unplanned,” while README, CHANGES, ADR 0003/0004, and current source establish Builder-first 4.0. Treating the Roadmap as current policy would contradict implemented and accepted design.

## D-02 — One source inspection per cluster

**Decision:** Inspect shared source seams once and apply that evidence to every issue in the cluster, reopening source only for contradictions.

**Clusters inspected:**

- schema/key/collection/inheritance generation;
- generated Builder/model/proxy layout;
- factories, converters, scripts, Templates, and ADR 0004 composition;
- copy/overwrite strategies;
- phases, lifecycle, validation, breadcrumbs/model paths, and traversal;
- IntelliJ GDSL and generated documentation;
- Jackson binding/deserialization;
- Gradle plugins/BOM/scenarios.

**Reason:** Most older issues describe symptoms of the same generator/runtime seam. This avoids inconsistent conclusions caused by inspecting a shared implementation repeatedly in isolation.

## D-03 — What counts as “implemented”

**Decision:** Recommend “close as completed” only when current source and tests/docs demonstrate the requested behavior or a stronger behavior that removes the problem.

Examples:

- #147 is completed by repeated-key Builder merging, even though it chose merge rather than a new rejecting Map implementation.
- #165 is completed more strongly because completed models no longer retain Builders at all.
- #358 is completed by the Builder-backed deserializer, while its policy question remains separately #428.
- #430 is completed for root `FromMap`; nested Builder composition is separately #431.

**Non-example:** A related helper or partial implementation is not completion. `deepFind(type)` does not complete #344's typed `visit`, and raw-map Jackson binding does not complete #251.

## D-04 — Builder-first conflict handling

**Decision:** Recommend obsolete closure for old requests that directly contradict accepted ADR 0003, rather than carrying them into a future release bucket.

Applied to:

- #83 completed-model `apply` mutation;
- #88 optionally writable completed models;
- #182 concrete/custom collection declarations;
- #218 dynamic-proxy RW implementation;
- #331 removing the generated factory interface in favor of a global facade.

**Reason:** These are not merely low-priority; their requested direction conflicts with the accepted 4.0 architecture. A future reconsideration would require a new design decision that explicitly supersedes ADR 0003.

## D-05 — 4.0 blocker test

**Decision:** Mark an issue `4.0 must` only if it closes an explicitly provisional/undecided public boundary introduced by Builder-first, or restores accepted compatibility intentionally deferred from PR #429.

Applied to:

- #394 generated Builder layout and compatibility marker;
- #390 supported completed-model companion/facade API;
- #428 deserialization/lifecycle semantics;
- #431 accepted `AsBuilder` composition and Template companion protocol.

**Reason:** This yields a narrow, coherent release gate. Other useful fixes remain nice-to-have unless current evidence demonstrates correctness failure in the core Builder-first contract.

## D-06 — Java/Groovy compatibility assessment

**Decision:** Treat every AST/generated-signature issue as requiring Groovy 3/4/5 coverage even when no version-specific behavior is predicted. Treat Java 17 as the fixed baseline; flag Java-visible signature/layout changes as `J17/API`, not as a request to raise the Java version.

**Reason:** `docs/agents/testing.md` states that passing one Groovy generation is not evidence for the others. The main compiler-version seams are in generated AST/signatures, precisely where #394/#431 operate.

## D-07 — Duplicate threshold

**Decision:** Call issues duplicates only when one canonical issue can retain the other's complete requirement without broadening scope. Otherwise record overlap and keep both.

**Consequences:**

- #343 is a duplicate of #431 because ADR 0004 and #431 explicitly include every Template-marking requirement.
- #358 and #428 are not duplicates: implementation and policy can close independently.
- #251 and #428 are not duplicates: property name mapping and lifecycle persistence are orthogonal.
- #375 → #184 is marked provisional because both bodies are under-specified.

## D-08 — Provisional closure of #411

**Decision:** Recommend #411 as completed, but explicitly ask whether a public visitation/context-path abstraction is still desired.

**Evidence:** `KlumBuilder`/`KlumModelProxy` store separate `breadcrumbPath` and `modelPath`; `DslHelper` exposes both; `StructureUtil` passes traversal paths; `wiki/Exception-Handling.md` distinguishes script breadcrumb from model-tree location; path tests cover both.

**Uncertainty:** The issue's “context path” bullet may mean a separately named API rather than the existing traversal callback path. That requirement cannot be inferred.

## D-09 — Provisional closure of old IDE reports

**Decision:** Recommend #23/#30 obsolete, not completed.

**Evidence:** They name IntelliJ 2016-era classes/bugs and an old GDSL implementation that no longer exists. The repository still has current IDE risk—`PolymorphicMethods.gdsl` and #394 naming—but that is not evidence that the 2016 bugs remain reproducible.

**Consequence:** If current IntelliJ support fails, file or rewrite a current issue with IDE version, current generated Builder spelling, and a minimal schema/script reproduction rather than retaining the old stack trace.

## D-10 — Read-only GitHub boundary

**Decision:** During this phase, use GitHub only to list/read open issues and use local history for related PR/commit evidence. Do not normalize labels or post triage notes.

**Evidence:** The user explicitly prohibited edits, closures, labels, and assignment. `docs/agents/issue-tracker.md` also says external PRs are not a request surface.

## D-11 — #79 and #314 are related, not duplicates

**Decision:** Keep both issues. Schedule them provisionally for later 4.x and share implementation machinery only where the eventual designs justify it.

**Evidence and rationale:** The maintainer confirmed that #79 is a true default for Simple Values: a schema author supplies a URI, and resolution occurs only if the model implementer has not supplied the target. It enables incremental, type-safe migration from existing `* as Code` sources, such as retaining selected Helm values from YAML. Raw content and keyed properties are examples, not a fixed resolver contract; URI syntax, selectors, formats, and missing-resource behavior remain implementation decisions. #386 is analogous for DSL Object Templates/recipes, and #430 remains the distinct whole-map/tree import path.

For #314, the model implementer supplies a seed value, such as a Vault secret path, and the provider resolves a separate target. The target remains readable through the normal model API and should not serialize by default. No seed means no resolution; an unresolvable supplied seed fails construction with provider/target context. Syntax and storage—transient field, generated accessor, Model companion, or plugin companion—remain implementation decisions.

**Lifecycle recommendation, not decision:** #314 may resolve during creation or in a separate phase. A separate phase must follow `AUTO_CREATE` and `AUTO_LINK`. Placement after `POST_TREE` and before `INSTANTIATE` is preferred because it has the complete Builder graph, exposes sensitive values late, and lets validation inspect the result. Its cost is that `POST_TREE` cannot consume the resolved value; a concrete need for that access could justify earlier placement. Final timing is deliberately left to implementation design.

**Follow-up authorization:** After the read-only inventory, the maintainer explicitly confirmed these conclusions and authorized updates to the two issue descriptions and the local curation documents. No issues were closed, labeled, or assigned.

## Targeted maintainer questions from the initial inventory

These questions are deliberately narrower than “please provide more information.”

1. **#394:** Should the stable generated Builder be top-level or nested, what is its client-visible name, and should either it or `KlumBuilder` implement any marker after `KlumRwObject` removal?
2. **#390:** Which completed-model operations are supported client API in 4.0: breadcrumb path, model path, technical metadata read/write, validation results, phase access, or none beyond existing utilities? Should clients ever call `KlumModelProxy.getProxyFor` directly?
3. **#428:** Choose one persisted-state contract: configured-only plus recomputation; all fields plus lifecycle recomputation; or all fields without mutating lifecycle callbacks. How should validation and owner/link reconstruction behave under the choice?
4. **#142:** Is arbitrary generated-annotation injection still wanted after the Jackson module removed its original need? If yes, list the target elements (model field/getter, Builder field/getter/setter, factory method, parameters) and required retention policy.
5. **#391:** Which concrete packages/modules should move, and is binary/source compatibility required within 4.x?
6. **#399:** What current 4.0 schema and `PostCreate` callback produce the wrong breadcrumb, what is observed, and what exact breadcrumb is expected?
7. **#205:** Does a typo in a current `Create.With` or collection-factory closure still report model-class candidates rather than Builder DSL candidates? Supply one current exception if yes.
8. **#240:** Does a current DSL Object with user-supplied `@EqualsAndHashCode` include `$proxy`, owner, or transient fields under Groovy 3, 4, or 5? If not, can the old issue close as obsolete?
9. **#251:** Is renamed Jackson property support required for 4.0 while the module remains beta, or should 4.0 document it as a known limitation and schedule it for 4.1?
10. **#411:** Is the existing separation of breadcrumb/model/traversal paths sufficient, or is a separately named public “context path” required?

## Follow-up after maintainer review

Questions 1–3 and 9 were answered in the 2026-07-14 grilling session and are superseded by D-12 through D-17 below.
The remaining questions stay open. GitHub mutations still require the repository normalization workflow.

## 2026-07-14 architecture grilling decisions

The maintainer answered questions 1–3, 9, and the phase-registration follow-up. ADRs 0004–0008 and their implementation
plans are authoritative; the concise curation consequences follow.

### D-12 — One top-level generated DSL namespace

**Decision:** #394 uses `Foo_DSL` with nested public `Factory`, `Builder`, Builder Collection factories, and Builder Cluster
factories. `Foo.Create` is typed to that factory. Clients may name but not implement the interfaces. IntelliJ completion is
served by truthful AnnoDocimal IDE source mirrors. `KlumBuilder<T>` narrows to a public capability interface;
`KlumRwObject`/`$_RW` leave the canonical API. `@DelegatesToBuilder` replaces deprecated `@DelegatesToRW`.

**Adoption constraint added 2026-07-14 and refined after the DSL-G prototype:** The AnnoDocimal sources are IDE-only
mirrors of AST-generated interfaces. The Gradle plugin exposes the mirror directory as an IDEA generated source root and
provides an explicit refresh task that compiles the real contract first. Automatic refresh during clean IDE import is not
required. Mirrors remain absent from compilation, packaging, publication, classpaths, and downstream build inputs.

### D-13 — Completed-object support, not companion access

**Decision:** #390 provides `KlumObjectSupport.of(object)` for any completed root or subtree, with direct object/breadcrumb/
model-path access and grouped Java-first structure and stored-validation helpers. `KlumModelProxy` and raw metadata become
strictly internal. A generic plugin extension accessor and model-specific generated support methods are deferred.

### D-14 — ADR 0004 implementation choices are closed

**Decision:** Use an opaque `ConstructionSession`; infer generated Builder projection types from generics; generate
`$klum$asBuilder$<name>` twins linked through AST metadata; omit opaque projections from public stubs with catalog-backed
dynamic diagnostics; generate projection-specific documentation; and return concrete Collection/Map containers of
Builders. No compiler hint, owner sink, or `PhaseDriver.Context` capability is introduced.

### D-15 — Template identity is narrow; copy sources are broad

**Decision:** A sealed internal `KlumObjectCompanion` has Model and Template variants. Template identity is persistent and
graph-aware, recipe state is immutable and Java-serializable, ordinary models retain no deferred actions, and Jackson
rejects Templates. Copy operations accept ordinary models as value-only sources, marked Templates as value-plus-recipe
sources, and same-session unsealed Builders as ephemeral value-plus-pending-action sources without converting them into
Templates. Sealed/cross-session Builders are rejected.

### D-16 — applyLater stops at materialization

**Decision:** The lowest scheduler immediately throws `KlumModelException` for every phase number at or after
`INSTANTIATE` (40), including Template replay. There is no warning, clamping, or compatibility exception.

### D-17 — Jackson persists configuration intent

**Decision:** #428 chooses public Builder configuration plus one normal lifecycle, not completed state. Binding uses resolved
Jackson property definitions, makes present values authoritative, recursively populates owned Builders, and requires
explicit identity/reference handling for `LINK`. This absorbs #251's renamed-property requirement into the 4.0 contract.
Templates are rejected and breaking JSON changes are acceptable.

### D-18 — Keep a narrow declarative phase SPI

**Decision:** #305 remains desired for later 4.x as a state-typed `PhaseRegistration` ServiceLoader SPI with stable IDs,
fresh actions, numeric phase authority, equal-phase dependencies, deterministic validation, and a deprecated legacy action
adapter. A schema `@CustomPhase` annotation is deferred rather than assumed from the old issue title or ADR 0002.

## 2026-07-14 confirmed GitHub normalization

After exact-body review and maintainer confirmation, milestone 4.0 was created and the canonical issues were normalized:
#394/ADR 0005, #390/ADR 0006, #431/ADR 0004, #428 plus #251/ADR 0007, and #305/ADR 0008. The following implementation-ready
tracer bullets were created:

- #433 — DSL-1 generated namespace and IDE source mirror;
- #434 — DSL-G IDE-only Gradle mirror lifecycle;
- #435 — OS-1 completed-object provenance and structure;
- #436 — AB-1 active-session `AsBuilder`;
- #437 — AB-2 projections and hidden twins;
- #438 — AB-3 Template companion and materialization boundary;
- #439 — JSON-1 resolved-property configuration replay;
- #440 — JSON-2 identity-safe `LINK` and Jackson customization;
- #441 — PH-1 state-typed phase registration and dependency-graph foundation.

No issues were closed or assigned, and no projects or GitHub dependency relationships were changed. Independently created
#432 was discovered during the refresh and recorded as untriaged rather than folded into ADR 0006 or #402 without a new
maintainer decision.
