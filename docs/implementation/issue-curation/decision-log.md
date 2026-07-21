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
- #430 is completed for root `FromMap`; ADR 0009 makes Jackson the canonical foreign-format mapping path without
  deprecating that existing value-copy convenience.

**Non-example:** A related helper or partial implementation is not completion. `deepFind(type)` does not complete #344's typed `visit`.

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
- #428 Jackson interoperability and importer semantics;
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
- #251 and #428 are not duplicates: resolved property-name mapping completed independently, while #428 owns the broader
  importer and interoperability contract.
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

**Evidence and rationale:** The maintainer confirmed that #79 is a true default for Simple Values: a schema author supplies a URI, and resolution occurs only if the model implementer has not supplied the target. It enables incremental, type-safe migration from existing `* as Code` sources, such as retaining selected Helm values from YAML. Raw content and keyed properties are examples, not a fixed resolver contract; URI syntax, selectors, formats, and missing-resource behavior remain implementation decisions. #386 is analogous for DSL Object Templates/recipes. Completed #430 remains distinct Map value-copy convenience; ADR 0009 owns Jackson-mapped foreign input.

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
Question 5 was answered in the 2026-07-15 grilling session and is superseded by D-19 below.
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

### D-17 — Jackson configuration replay (superseded by D-21)

**Decision:** #428 chooses public Builder configuration plus one normal lifecycle, not completed state. Binding uses resolved
Jackson property definitions, makes present values authoritative, recursively populates owned Builders, and requires
explicit identity/reference handling for `LINK`. This absorbs #251's renamed-property requirement into the 4.0 contract.
Templates are rejected and breaking JSON changes are acceptable.

This persistence framing was accepted on 2026-07-14 and implemented as JSON-1/JSON-2 groundwork. The maintainer's
2026-07-16 interoperability clarification supersedes it through D-21 and ADR 0009.

### D-18 — Keep a narrow declarative phase SPI

**Decision:** #305 remains desired for later 4.x as a state-typed `PhaseRegistration` ServiceLoader SPI with stable IDs,
fresh actions, numeric phase authority, equal-phase dependencies, deterministic validation, and a deprecated legacy action
adapter. A schema `@CustomPhase` annotation is deferred rather than assumed from the old issue title or ADR 0002.

### D-19 — Finalize Java modules and handwritten packages in 4.0

**Decision:** #391 is a 4.0 release requirement. Remove `com.blackbuild.groovy.configdsl` without compatibility aliases,
eliminate split packages, consolidate schema vocabulary under `com.blackbuild.klum.ast`, and export only a positive
runtime allowlist centered on `com.blackbuild.klum.ast.runtime` and `.runtime.validation`. Layer 3 remains the canonical
API–Schema–Model modeling pattern but is not a package/module boundary. No general third-party extension SPI is promised;
shipped adapters may use qualified exports.

Keep the existing artifact set, Maven coordinates, BOM, and Gradle plugin IDs. The stable module names are
`com.blackbuild.klum.ast.annotations`, `.runtime`, `.compiler`, `.jackson`, and `.validation.bean`; the Gradle plugin remains
a build adapter outside JPMS. Classpath use remains supported. Modular schema packages use documented qualified `opens`;
their `module-info.java` remains user-owned, while the schema plugin validates it and provides actionable remediation.

**Feasibility gate:** Before mass movement, one candidate artifact set must compile and run a named schema with Groovy 3,
4, and 5, including AST generation, lifecycle, service loading, Jackson, Bean Validation, published descriptor inspection,
and separate classpath coverage. Groovy's module name changes from `org.codehaus.groovy` in 3 to `org.apache.groovy` in
4/5, so failure returns the module design for maintainer review rather than authorizing `--add-reads` or broad export
workarounds. #450 and `blackbuild/anno-docimal#36` coordinate upstream prerequisites.

**Compatibility:** This is an intentional source/binary break requiring schema and consumer recompilation. No legacy
package aliases, reflection-free generated access redesign, artifact split, or Java-serialization compatibility shim is
included. Java-serialized 3.x graphs are not 4.0 migration inputs. The proven prototype must feed a dedicated ADR,
tracer-bullet implementation plan, and complete import migration map before implementation.

**Follow-ups:** #453 investigates whether any supported extension mechanisms are justified; #454 sharpens Layer 3
documentation and reconciles open #356. Neither issue broadens #391 by default.

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

## 2026-07-15 package/module normalization

After evidence-led grilling and exact-body confirmation, #391 was retitled, assigned to milestone 4.0, labelled as an
enhancement and breaking change, and rewritten with the D-19 contract and prototype gate. #453 and #454 were created as
unmilestoned `needs-triage` investigations for extension mechanisms and Layer 3 documentation. Upstream
`blackbuild/anno-docimal#36` was created and linked through #450. No issue was closed or assigned, and no project or native
dependency relationship changed.

## D-20 — Classify the KlumCast and AnnoDocimal integration audit

**Decision:** Treat the merged owning-repository records as authoritative over the earlier KlumAST inventory's open
questions. The [final #450 audit](issue-450-integration-audit.md) records ten mutually exclusive release classifications.

- KlumCast 0.4 artifact/package/module adoption is a 4.0 blocker through #459 because #391 cannot accept the 0.3.1 split
  package and unstable automatic names. Migrating the eight checks from 0.4's deprecated adapter to its durable SPI is
  separately pre-4.0 desirable through #460, not a release blocker.
- Final AnnoDocimal 1.0 is a hard KlumAST 4.0 prerequisite through #461. Its tracker #47 owns the API, task, projection,
  capture, module, publication, documentation, CI, and release gates; KlumAST owns only downstream adoption and its
  Builder/IDE-specific policy.
- No beneficial ownership move was found for KlumAST validation semantics, supported name-based KlumCast bindings,
  AnnoDocimal protocol dependencies, IDEA-only source-root wiring, `_DSL` selection, Builder-specific documentation, or
  repository-local governance. Do not create duplicate upstream issues for those conclusions.

**Issue normalization:** #459, #460, and #461 are native sub-issues of #450 with their independent release placements.
#455 retains the multi-Groovy design and #456 retains versioned documentation; #450 supplies facts but decides neither.

**State correction:** PR #457's negated prose contained a mechanical issue-action keyword before the #450 reference, so
GitHub changed the issue state when that partial inventory merged. #450 was reopened and remains open through review of
the final audit. PR and commit text must use neutral issue links whenever automatic state changes are not intended.

## D-21 — Jackson is asymmetric external-format interoperability

**Decision:** ADR 0009 supersedes ADR 0007. KlumAST imports externally owned JSON/YAML into Builder construction and
exports completed DSL Objects through ordinary Jackson POJO serialization. It defines no wire format, round trip,
envelope, reserved metadata field, producer field, version comparison, or generic migration adapter.

Managed import must use a caller-configured, data-format-neutral `KlumJacksonImporter` with explicit root, value-only
Template, active-session Builder-producing, and apply-to-existing-Builder modes. It preserves ordinary Jackson
property/value configuration while rejecting construction takeover. Source/mapping/I/O failures become breadcrumb-rich
`KlumModelException`s with their original causes. Raw non-DSL Jackson reads remain ordinary operations.

Export has no Klum facade. LINK input never becomes owned composition; LINK output must choose an explicit Schema-owned
projection and may deliberately inline through a custom serializer. Normal Template output remains rejected, while
explicit type-level serializers/deserializers are complete opt-outs. Owner/Role export overrides, richer LINK resolution,
generated import creators, YAML multi-document convenience, and model version/diff architecture are deferred.

**Compatibility:** Public importer signatures and managed mapping/lifecycle/customization semantics are 4.x commitments.
Byte output, ordering, formatting, a universal schema, and import/export symmetry are not. Existing `Create.FromMap`
remains current value-copy API but is not the canonical foreign-format mapping seam.

**Vocabulary:** Domain API Developer, Schema Developer, Client Developer, and Model Writer are project-wide roles. Layer 3
separates all four explicitly; #454 owns a later grilling session for variants and examples.

## 2026-07-16 Jackson interoperability normalization

After exact-body presentation and maintainer authorization, ADR 0009 and its implementation plan superseded ADR 0007's
cancelled JSON-3 round-trip closure. GitHub normalization:

- rewrote open parent #428 around ADR 0009 and retained its 4.0 milestone;
- created #463 JSON-3 (`ready-for-human`) for importer API review/implementation;
- created #464 JSON-4 (`ready-for-agent`) for asymmetric compatibility closure and made it natively blocked by #463;
- completed #447 with the no-metadata decision and #251 with #439 implementation evidence;
- completed #430 as already implemented while explicitly preserving `FromMap` until a separate compatibility decision;
- added successor-architecture notes to completed #439/#440; and
- updated open #454 with the four roles and required future Layer 3 grilling.

## D-22 — Preserve Layer 3 composition-or-aggregation relationships

**Decision:** Add `FieldType.OPTIONAL_LINK` as the relationship mode that may contain either owned composition or
aggregation. `@LinkTo` selects `OPTIONAL_LINK`; `@Field(FieldType.LINK) @LinkTo` remains an aggregation-only fallback.
Relationship classification is per single value, collection element, or map entry: `LINK` entries are aggregation,
while `OPTIONAL_LINK` entries may mix composition and aggregation.

A fresh unclaimed Builder from the active construction session is composition and receives an internal write-once
composition claim. An already claimed Builder, or a completed model, is aggregation. A fresh Builder is invalid for
`LINK`, because it would never be reached by composition materialization. Composition-only relationships reject a
claimed Builder and a completed model; Templates, sealed Builders, and cross-session Builders remain invalid everywhere.

Generated Java and Groovy APIs expose Builders for composition and valid aggregation. Only `LINK` and `OPTIONAL_LINK`
retain completed-model target overloads; composition-only model overloads are removed. Completed-model batch APIs use
`link…` names to avoid Java generic erasure. A narrow dynamic Builder `link(fieldName, target)` capability remains
available to custom `@AutoLink` methods/closures, but records aggregation only and fails rather than overwriting a set
single field or non-empty collection/map.

**Compatibility:** This is the intentional 3.x-to-4.0 migration path for `@LinkTo`: mixed fields migrate directly,
aggregation-only fields use `LINK`, and composition-only fields stay ordinary. Existing dynamic Auto-Link code that
overwrites configured values must adopt explicit non-destructive fallback behavior.

**Follow-up ownership:** #474 owns this behavior and its ADR 0003/0004 clarification, coverage, migration/docs, and
generated method shapes. #431 retains `AsBuilder`/composition protocol completion; #467 classifies the dynamic
framework-owned capability only; #468 inventories and freezes the resulting public surface.

## 2026-07-17 Layer 3 relationship normalization

After evidence-led grilling and maintainer confirmation, #474 was created as the canonical 4.0 issue with
`enhancement`, `breaking change`, and `ready-for-agent` labels and milestone 4.0. Triage comments on #431, #467, and
#468 record the ownership boundary. No implementation, ADR amendment, issue closure, dependency relation, project,
assignment, branch, or commit was changed.

The same inventory refresh reconciled the local open-issue count with GitHub (109): #469–#472 were already-live 4.0
onboarding issues omitted from the local table. They were recorded as their own documentation/onboarding cluster; no
GitHub state or scope was changed.

## 2026-07-17 Construction-path terminology normalization

After evidence-led terminology grilling and maintainer confirmation, **construction path** is the canonical name for the
immutable `$`-prefixed DSL invocation String retained for an object. **Structural model path** names its owned-composition
location, **traversal path** is contextual traversal output, **import source** identifies external input, and **validation
location** identifies a target/member. **Provenance** and **lineage** are reserved for a richer future origin/event record
and must not name the current String.

#390 / ADR 0006 owns the pre-freeze replacement of the temporary public
`KlumObjectSupport.getBreadcrumbPath()` with `getConstructionPath()` without an alias. Existing exception/helper JVM
descriptors remain unchanged in 4.0. `BreadcrumbCollector` remains generated/runtime implementation vocabulary, not a
client or extension seam. #467 records the convention, #468 freezes the member classification, and #463 adopts
construction-path/import-source diagnostics. #432 remains separate pending #402's future lineage/event placement.

GitHub normalization rewrote #390, retitled/commented #463, added consumer records to #467/#468, documented #432's
deferral, and completed #411 because no separate public context-path type is required. Labels and milestones were unchanged.

## 2026-07-17 Framework public-interface convention

ADR 0010 resolves #467 without changing a feature implementation. Framework types use UpperCamelCase and lower-camel
methods; `of` wraps a known completed value, `using` configures an adapter from caller-owned infrastructure, and a public
constructor is reserved for conventional direct construction such as `new KlumAstModule()`. Root operations return completed
DSL Objects while explicit `Create.AsBuilder` produces an active-session Builder. The record classifies the narrow dynamic
`KlumBuilder.link(fieldName, target)` capability as framework-owned construction support only; #474 retains its shape and
behavior. `KlumObjectSupport.getConstructionPath()` is the sole future public construction-string getter, while
`BreadcrumbCollector` remains implementation vocabulary. #390, #394/#431, #463, and #474 retain their delivery work, and
#468 remains the final JVM inventory gate.

## 2026-07-17 Ordered configuration composition refinement

After evidence-led grilling and maintainer confirmation, #304 is the canonical 4.1 source-neutral configuration
composition coordinator. Its public direction is generated `Create.Compose(KlumConfigurationLayer<T>...)`, with typed
opaque layer values created by `KlumLayers` for copy recipes, Maps, DelegatingScripts, and Groovy text/files/URLs. The
coordinator owns one root Builder lifecycle, layer order, transient source diagnostics, and atomic publication; it neither
rolls back the private in-session Builder nor persists a layer ledger on a completed DSL Object.

Schema `@Overwrite` remains the default policy. #310 (with #350 duplicate intent) owns layer-local runtime policy and
precedence; #132 and #342 retain final-closure and Template-plus-configuration conveniences and must delegate to #304.
#352 remains a separate later-4.x strategy extension. #432 remains the narrow applied-Template provenance request, while
#402 owns any durable applied-layer/event history.

ADR 0009, #463, and #464 use exactly one `KlumJacksonInput` per importer operation. It retains Jackson
mapping/null/merge behavior for that input but does not sequence multiple inputs or define cross-layer
overwrite/null/list/map rules. Future heterogeneous composition is #304 work. JSON-3 and JSON-4 remain 4.0 Jackson work
and are not blocked by #304.

## D-23 — Season 4 visual branding is coordinated, visual-only finalization

**Decision:** Adopt **Season 4: The Makeover** as the release-facing identity paired with semantic `4.0`. It defines a
coordinated KlumAST/KlumCast visual family for current and prospective Java/Groovy DSL users, while retaining distinct
product marks and role subtitles. AnnoDocimal remains an independent peer with policy-level alignment only.

**Technical identity and compatibility:** Retain `klum-cast` through 0.4/4.0. The already-published KlumCast RC and the
current coordinated RC train continue unchanged. A product, repository, coordinate, package, automatic-module, or module
rename is substantive work requiring separately authorized compatibility, migration, and affected-RC validation; it is not
Season 4 final polish.

**Manifest and ownership:** Stephan is the branding owner. He approves the final Season/logo manifest and any later
correction record. The minimum evidence is one shared visual system, distinct accessible KlumAST/KlumCast marks, a manifest
with Season identity/local asset path/digest/alternative text, owner approval, and rendered product-local release/documentation
evidence. ADR 0013/#456 validate and capture the manifest; #488 and the product-local owners retain publication authority.
After publication, corrections preserve the original identity and digest rather than rewriting an immutable snapshot.

**Release placement:** #483 is the canonical cross-product decision and acceptance owner. It is a visual-only 4.0
finalization concern, not a native RC-train blocker or release dependency. The approved final manifest is nevertheless
required before #456's protected final documentation render. No new ADR or branding tracer is needed; #456's existing
manifest/render fixtures cover the KlumAST proof, while KlumCast delivery stays local.
