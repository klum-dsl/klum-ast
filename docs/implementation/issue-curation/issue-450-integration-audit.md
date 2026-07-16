# Issue 450: KlumCast and AnnoDocimal integration audit

Date: 2026-07-16

Tracking issue: [#450 — Audit klum-cast and AnnoDocimal integration opportunities before 4.0](https://github.com/klum-dsl/klum-ast/issues/450)

Inputs: [KlumAST consumer and governance inventory](issue-450-consumer-and-governance-inventory.md),
[KlumCast PR #25](https://github.com/klum-dsl/klum-cast/pull/25), and
[AnnoDocimal PR #48](https://github.com/blackbuild/anno-docimal/pull/48).

## Outcome

The upstream investigations confirm two different release relationships:

- **AnnoDocimal 1.0 is a hard prerequisite for KlumAST 4.0.** Development may use a pre-release, but KlumAST 4.0 must
  not release against provisional AnnoDocimal 0.x APIs. This is an explicit AnnoDocimal decision, recorded in its
  [architecture baseline](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#module-path-correctness-release-gate),
  [release plan](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/1.0-release-plan.md#cross-project-sequencing),
  and [tracker #47](https://github.com/blackbuild/anno-docimal/issues/47).
- **KlumCast 0.4 is the intended breaking migration line, but the merged record does not make the complete KlumAST SPI
  migration a 4.0 release prerequisite.** The first 0.4 release will introduce the new SPI/artifact/package contracts with
  migration bridges; 1.0 will remove those bridges. The 0.4 artifact/module/package adoption is independently blocking
  because KlumAST [#391](https://github.com/klum-dsl/klum-ast/issues/391) requires it in the named-module tracer bullet,
  while migrating the eight checks off the deprecated bridge and onto the durable SPI is separately desirable before 4.0.
  See the
  [KlumCast architecture baseline](https://github.com/klum-dsl/klum-cast/blob/4053e73419931905a89a70545a452d1d06659d18/docs/implementation/architecture-and-agent-baseline.md#release-and-migration-line)
  and [umbrella #24](https://github.com/klum-dsl/klum-cast/issues/24).

The local workarounds remain valid while those releases are unfinished. This audit does not authorize dependency or
production-code changes.

## Evidence verification

- KlumCast merge commit
  [`4053e73419931905a89a70545a452d1d06659d18`](https://github.com/klum-dsl/klum-cast/commit/4053e73419931905a89a70545a452d1d06659d18)
  is contained in the repository's current `origin/main`. Its first parent is the former main tip and its second parent is
  the reviewed architecture branch. The merge adds repository governance, `CONTEXT.md`, 24 ADRs, the architecture plan,
  and an isolated binding prototype; it does not change production source, build behavior, or published artifacts
  ([PR #25 impact](https://github.com/klum-dsl/klum-cast/pull/25)).
- AnnoDocimal merge commit
  [`7dd470cc38752f10a70e2558b1f3800e6df7917d`](https://github.com/blackbuild/anno-docimal/commit/7dd470cc38752f10a70e2558b1f3800e6df7917d)
  is the current tip of the repository's default `master` branch. The merge adds governance, `CONTEXT.md`, 52 ADRs,
  architecture/release/curation plans, and issue normalization; it does not change production APIs, build behavior, or
  release artifacts ([PR #48 impact](https://github.com/blackbuild/anno-docimal/pull/48)).
- Consequently, current behavior remains the KlumAST-inventoried KlumCast **0.3.1** and AnnoDocimal **0.7.1** behavior.
  The upstream documents define successor contracts and issue ownership, not already-available library fixes.

## Finding matrix

`4.0 blocker`, `pre-4.0 desirable`, and `post-4.0 follow-up` are mutually exclusive classifications. For rows that record
an explicit no-change conclusion, the classification states when a future revisit is permitted; it does not manufacture
an implementation requirement.

| ID | Finding and evidence | Current KlumAST impact / workaround | Recommendation and owner | Compatibility implications | Dependency / order | Minimum upstream version or condition | Release classification |
|---|---|---|---|---|---|---|---|
| KC-1 | Consumer-authored checks are a supported KlumCast capability, but the durable contract moves from the mutable `checks.impl.KlumCastCheck` base class to a stateless interface and immutable context in new artifact/package `klum-cast-spi`. Diagnostics become structured, multi-valued, source-positioned data, with technical failures kept separate. Name-based and typed bindings both remain supported ([KlumCast baseline](https://github.com/klum-dsl/klum-cast/blob/4053e73419931905a89a70545a452d1d06659d18/docs/implementation/architecture-and-agent-baseline.md#consumer-authored-checks), [#16](https://github.com/klum-dsl/klum-cast/issues/16), [#17](https://github.com/klum-dsl/klum-cast/issues/17)). | Eight KlumAST checks currently extend the 0.3.1 base class; two throw `ValidationException`. Public annotations bind them by class-name strings to avoid an annotation-to-compiler dependency. The current code remains functional on 0.3.1. | Adopt the 0.4 SPI when available; migrate the eight check implementations and their diagnostic assertions through [KlumAST #460](https://github.com/klum-dsl/klum-ast/issues/460). **No beneficial change was found in either the name-based binding or dependency-placement strategy:** name binding is the supported form for split/cyclic artifacts, annotation consumers still need lightweight metadata, and schema compilation still needs the compiler artifact because dependency placement is the supported activation API. Do not convert KlumAST annotations to typed bindings or hide the compiler artifact behind an internal/compile-only edge. Owner: KlumCast #24/#16/#17 for the SPI; KlumAST #460 for the downstream migration. | KlumCast 0.4 is intentionally source-breaking. KlumAST implementation imports/base classes and some asserted diagnostics change. The public annotation/compiler artifact separation can remain; the compiler artifact supplies annotations and SPI transitively. Deprecated 0.4 bridges are temporary and must not become KlumAST's long-term target. | #16 and #17 implement the contracts coordinated by #24. Their upstream fixtures own isolated nested typed and split name-bound consumers, while KlumAST retains end-to-end integration diagnostics. KlumAST can test a 0.4 pre-release after those contracts exist, then migrate before any optional 1.0 bridge removal. | First published 0.4.x containing the #24 SPI, binding, diagnostic, and migration contracts; no exact version is published yet. | **pre-4.0 desirable** |
| KC-2 | The current two JARs have a split `checks.impl` package and filename-derived automatic modules. The accepted target has three artifacts, one package owner each, and stable automatic names `com.blackbuild.klum.cast.annotations`, `.spi`, and `.compiler`. Explicit descriptors remain gated on Groovy 3/4/5 feasibility ([baseline](https://github.com/klum-dsl/klum-cast/blob/4053e73419931905a89a70545a452d1d06659d18/docs/implementation/architecture-and-agent-baseline.md#jpms-identity), [#12](https://github.com/klum-dsl/klum-cast/issues/12)). | KlumAST #391 cannot complete a portable named-module tracer bullet with split upstream packages or unstable module names. Ordinary classpath compilation works today. | Require the stable names, split-package elimination, classpath service loading, and module-path feasibility evidence as inputs to #391. Owner: KlumCast #12/#24; KlumAST #391 owns only its consumer descriptors and tracer. | Adds one artifact and moves SPI classes/packages. Automatic names are new compatibility commitments. Explicit descriptors are not required unless the upstream feasibility gate accepts them. | Upstream packaging must be available before #391 finalizes KlumAST module descriptors. Do not hide failure with `--add-reads`, broad exports, or a KlumAST-local repackaging workaround. | A KlumCast 0.4.x artifact set satisfying #12/#24's stable-name, no-split-package, service-loading, and consumer-fixture gates. | **4.0 blocker** |
| KC-3 | KlumCast explicitly keeps `FieldAstValidator`'s DSL semantics in KlumAST. Only generic node-kind dispatch is an optional upstream investigation, gated on a second independent use case. `LinkHelper`'s annotation invariants should become KlumAST custom checks, while runtime link resolution remains KlumAST lifecycle behavior ([ownership decisions](https://github.com/klum-dsl/klum-cast/blob/4053e73419931905a89a70545a452d1d06659d18/docs/implementation/architecture-and-agent-baseline.md#klumast-fieldastvalidator-ownership), [#23](https://github.com/klum-dsl/klum-cast/issues/23)). | The source TODOs are misleading about ownership, and some `@LinkTo` failures are still discovered during runtime resolution. No missing KlumCast API blocks current behavior. | **No beneficial upstreaming of KlumAST rules was found.** Keep `FieldAstValidator`, `AutoLinkPhase`, and link-resolution semantics in KlumAST. Upstream #23 needs no API if a second use case does not appear. Curate a separate KlumAST cleanup only if moving the remaining static invariants earlier is still valuable after 4.0 work. | A local move from runtime failure to compile-time diagnostic can change failure timing/text and needs focused compatibility notes. No public KlumCast compatibility change is justified by the TODOs themselves. | Independent of the 0.4 SPI except that any new static check should target the final SPI rather than the deprecated adapter. | No upstream release required for the ownership conclusion; use the final KlumCast SPI if a later local check is implemented. | **post-4.0 follow-up** |
| AD-1 | AnnoDocimal 1.0 will stabilize explicit supported-API allowlists. The current `ASTExtractor`, formatter/builders, `AnnoDocGenerator`, visitors/parsers, and other public 0.7.1 helpers are provisional until #37 designs, #38 implements, and #39 narrows the projection contract. KlumAST is the known migration consumer ([baseline](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#api-compatibility-boundary), [#37](https://github.com/blackbuild/anno-docimal/issues/37), [#38](https://github.com/blackbuild/anno-docimal/issues/38), [#39](https://github.com/blackbuild/anno-docimal/issues/39)). | KlumAST directly consumes six helper families in compiler code, calls `AnnoDocGenerator` from its mirror task/tests, and publishes/applies the Gradle plugin. Those calls work on 0.7.1 but cannot be treated as a stable post-1.0 contract. | Upgrade to AnnoDocimal 1.0 and migrate every mapped call site to the finalized transformation-author and projection APIs through [KlumAST #461](https://github.com/klum-dsl/klum-ast/issues/461). Owner: AnnoDocimal #37/#38/#39 for API design/delivery; KlumAST #461 for adoption. Do not pre-empt #37 by designing facade names here. | A clean 0.x-to-1.0 source break is explicitly allowed for provisional helpers. `@AnnoDoc`, `@InlineJavadocs`, and documentation-properties behavior remain protocol commitments; helper shims are retained only when independently useful and cheap. KlumAST must compile/test against the final allowlist. | #37 precedes #38/#39; contract suites and the compatibility baseline follow the final APIs; #40 documents the migration. A pre-release may be used for development, but final KlumAST 4.0 waits for final AnnoDocimal 1.0. | **AnnoDocimal 1.0.0 final**, with #37/#38/#39 and the 1.0 tracker gates complete. | **4.0 blocker** |
| AD-2 | `CreateClassStubs` is confirmed as a supported, independently registerable Gradle task. #35 owns arbitrary inputs/output, declared filtering, documentation-sensitive input tracking, stale cleanup, deterministic/build-cache behavior, strict configuration-cache reuse, and supported-Gradle TestKit evidence; it must consume the projection service from #39 ([ADR/baseline](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#reusable-gradle-task-contract), [#35](https://github.com/blackbuild/anno-docimal/issues/35)). | KlumAST's local `CreateKlumDslSourceMirrors` duplicates the task action, uses full `@Classpath` sensitivity, cleans stale output, and owns strict configuration-cache limitation evidence. It is a safe temporary workaround and currently proves IDE/build/publication isolation. | After #35/#39 ship, replace only the duplicated task implementation with a configured upstream task. Keep the `**/*_DSL.class` top-level selection and IDEA generated-source-root wiring in KlumAST. Owner: AnnoDocimal #35/#39 upstream; KlumAST #394/ADR 0005 adoption downstream. | Task type/configuration changes affect the published KlumAST Gradle plugin and its TestKit behavior, but must not add mirrors to source sets, classpaths, archives, or variants. Strict configuration-cache behavior improves. | #39 finalizes projection service first; #35 delivers the task; KlumAST then ports its executable tests before deleting the local task class. | AnnoDocimal 1.0.0 containing completed #35 and #39. | **4.0 blocker** |
| AD-3 | AnnoDocimal assigns stable automatic module names to five Java-facing artifacts, keeps the Gradle plugin outside JPMS, and moves the global AST provider adapter into the artifact that declares it. This exact packaging correction is tracked by #36 ([baseline](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#initial-artifact-module-classifications), [#36](https://github.com/blackbuild/anno-docimal/issues/36)). | AnnoDocimal 0.7.1 has filename-derived names and a cross-artifact service declaration that prevents automatic-module derivation. KlumAST #391's named schema/module fixture cannot accept that as a portable dependency contract. | Consume #36's stable names and packaged-provider evidence in #391. Owner: AnnoDocimal #36; KlumAST #391 owns its descriptors and named-consumer checks. **No explicit descriptor is required from AnnoDocimal for this release.** | Stable automatic names become 1.0 compatibility API; provider class location changes internally while classpath service discovery must remain. The same coordinates must work with Groovy 3/4/5. | #36 can proceed without deciding #455. It must complete before KlumAST #391 freezes module descriptors and before AnnoDocimal #44 publication smoke tests. | AnnoDocimal 1.0.0 with #36 complete; a compatible 1.0 pre-release is acceptable only for KlumAST tracer development. | **4.0 blocker** |
| AD-4 | Projection correctness is an upstream contract: inherited generic signatures must produce compilable Java (#32), annotation members must be deterministic across Groovy generations (#33), and #41 must compile a representative projection matrix. KlumAST supplies consumer evidence but is not the upstream test oracle ([#32](https://github.com/blackbuild/anno-docimal/issues/32), [#33](https://github.com/blackbuild/anno-docimal/issues/33), [#41](https://github.com/blackbuild/anno-docimal/issues/41)). | KlumAST tests its own nested `Foo_DSL` shape, AnnoDoc, overload/tag remapping, and cache invalidation, but those tests cannot establish the library-wide Java/Groovy fidelity contract. Current mirrors remain coupled to 0.7.1 behavior. | Keep KlumAST-specific Builder wording, overload/tag policy, nested namespace expectations, and hidden-twin policy in KlumAST. Rely on AnnoDocimal for general declaration fidelity and recompilation. Retain narrow downstream regression assertions after upgrading. | Fixes may normalize textual output without promising whitespace/import layout. Valid Java, selected declaration meaning, and determinism are the supported compatibility boundary. | #39 policy/API precedes #41; #32/#33 feed #41. All are AnnoDocimal 1.0 gates coordinated by #47. | AnnoDocimal 1.0.0 with #32/#33/#39/#41 complete. | **4.0 blocker** |
| AD-5 | AnnoDocimal owns the documentation protocol and capture semantics. `@AnnoDoc` is the current embedded carrier and `@InlineJavadocs` the opt-in marker; Java APT, local Groovy, and global Groovy capture are supported behavioral paths. Runtime GroovyDoc interoperability, property mapping, and capture conformance are owned by #19, #9, and #43. Structured protocol issue #30 is deferred to 2.0 ([CONTEXT](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/CONTEXT.md), [release plan](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/1.0-release-plan.md#confirmed-gates)). | KlumAST exposes the annotations artifact, uses the APT on `annotationProcessor`, and reads `@AnnoDoc` at runtime for deprecation validation. Its generated Builder documentation policy is consumer-specific. | **No beneficial narrowing of the annotation or processor edges was established.** Keep the annotations dependency available wherever generated/runtime `@AnnoDoc` is read and keep APT as the Java capture path. Verify the finalized 1.0 dependency metadata during adoption; do not duplicate #9/#19/#43 locally or pull #30 into KlumAST 4.0. | Current textual carrier readability persists; future structured protocol changes are additive and outside 1.0. Capture fixes may affect which equivalent carrier wins, but KlumAST should consume the resolved documentation contract rather than carrier internals. | Upstream #9/#19/#43 finish within the AnnoDocimal 1.0 tracker. KlumAST only needs targeted consumer verification after the upgrade. | AnnoDocimal 1.0.0 final; no additional KlumAST-side upstream version condition. | **4.0 blocker** |
| AD-6 | Both AnnoDocimal plugin IDs remain an intentional supported base/opinionated pair. Top-level class-file selection and IDE model wiring belong to the consuming build; KlumAST-specific generated Builder wording is consumer policy ([plugin decision](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#layered-gradle-plugins), [product boundary](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#product-boundary)). | KlumAST applies `AnnoDocimalPlugin` by class from its published schema plugin, chooses only top-level `_DSL` classes, exposes mirrors to IDEA alone, and rewrites docs for Builder semantics. | **No beneficial ownership or dependency-scope move was found** for plugin layering, IDEA wiring, `_DSL` naming/selection, Builder-specific text, overload tag remapping, or hidden-twin policy. Keep the upstream plugin on the KlumAST plugin's runtime/API graph, because consumers must have the class that KlumAST applies. Do not ask AnnoDocimal to merge plugin layers or make source mirrors automatic project source. | Preserves the boundary that mirrors are metadata, not a second compilable/published API. A future consumer-neutral IDE plugin would require new evidence and a separate contract. | Revisit only after 4.0 if another consumer demonstrates the same IDE policy. The reusable task/API work in AD-2 is sufficient for current integration. | No additional upstream release beyond AD-1/AD-2. | **post-4.0 follow-up** |
| GOV-1 | Both upstream repositories adopted repository-owned context, ADR, issue, testing, commit, and release guidance modeled on the same KlumAST baseline ([KlumCast baseline](https://github.com/klum-dsl/klum-cast/blob/4053e73419931905a89a70545a452d1d06659d18/docs/implementation/architecture-and-agent-baseline.md), [AnnoDocimal baseline](https://github.com/blackbuild/anno-docimal/blob/7dd470cc38752f10a70e2558b1f3800e6df7917d/docs/implementation/architecture-and-agent-baseline.md#governance-baseline-established)). | Cross-repository findings now have owning-repository ADRs and issue trackers rather than only KlumAST speculation. | **No beneficial KlumAST governance change was found.** Keep each repository's policies local; cross-link decisions and issues rather than copying their plans into KlumAST. | None for product/runtime compatibility. | Future synchronization is ordinary repository maintenance, not a #450 release deliverable. | No upstream release condition. | **post-4.0 follow-up** |

For the downstream side of the matrix, [#459](https://github.com/klum-dsl/klum-ast/issues/459) owns KC-2 adoption and
[#461](https://github.com/klum-dsl/klum-ast/issues/461) owns the consolidated AD-1 through AD-5 adoption. References to
#391 and #394 in those rows identify the consuming release gates, not substitute owners. KC-1 is independently owned by
[#460](https://github.com/klum-dsl/klum-ast/issues/460).

## Dependency and action sequence

### KlumAST can do now

1. Keep KlumCast 0.3.1 and AnnoDocimal 0.7.1 workarounds unchanged while upstream implementations are unfinished.
2. Link KC-2 and AD-3 into #391's module-path tracer prerequisites; do not freeze consumer descriptors against current
   filename-derived or split-package behavior.
3. Keep the local source-mirror task and its TestKit contract until AnnoDocimal #35/#39 are available.
4. Use [#461](https://github.com/klum-dsl/klum-ast/issues/461) for AnnoDocimal 1.0 adoption and migration; upstream #47
   blocks it, and it carries the direct helper/task/plugin/dependency map from the step-1 inventory.
5. Use [#459](https://github.com/klum-dsl/klum-ast/issues/459) to adopt the first KlumCast 0.4.x artifact set satisfying
   #12/#24, including the new dependency graph, package ownership, and stable module identities required by #391.
6. Use [#460](https://github.com/klum-dsl/klum-ast/issues/460) for the separately classified migration of the eight custom
   checks from the deprecated 0.4 adapter to the durable #16/#17 SPI and diagnostic model. Do not combine the two KlumCast
   classifications, the AnnoDocimal migration, or #455 in one issue.

### Work that requires upstream delivery

1. KlumCast must publish a 0.4 artifact set satisfying #24/#12/#16/#17. KlumAST first adopts its dependency/module/package
   contract for #391, then can migrate the eight checks to the final SPI as the separately classified follow-up. The exact
   first acceptable patch version is not yet known.
2. AnnoDocimal must finish tracker #47 and publish **1.0.0 final** before KlumAST 4.0 can release. KlumAST may validate
   pre-releases earlier, but must retest the final artifacts.
3. After AnnoDocimal 1.0 exists, KlumAST migrates helper APIs first, ports the mirror task to the supported task/projection
   API, then runs its focused Gradle/IDE isolation tests, Groovy 3/4/5 lanes, #391 module fixture, and publication checks.

## Issue ownership and relationship map

| Finding | Owning issues | KlumAST relationship action |
|---|---|---|
| KlumCast SPI/diagnostic migration | [klum-cast #24](https://github.com/klum-dsl/klum-cast/issues/24), [#16](https://github.com/klum-dsl/klum-cast/issues/16), [#17](https://github.com/klum-dsl/klum-cast/issues/17), [#22](https://github.com/klum-dsl/klum-cast/issues/22) | [KlumAST #460](https://github.com/klum-dsl/klum-ast/issues/460) owns the pre-4.0-desirable eight-check migration without copying upstream plans. |
| KlumCast dependency/module/package contract | [klum-cast #12](https://github.com/klum-dsl/klum-cast/issues/12), [#24](https://github.com/klum-dsl/klum-cast/issues/24) | [KlumAST #459](https://github.com/klum-dsl/klum-ast/issues/459) owns the 4.0-blocking adoption and links #391/#450. It remains separate from the check migration. |
| Optional node-kind dispatch | [klum-cast #23](https://github.com/klum-dsl/klum-cast/issues/23) | No KlumAST issue is required unless a second use case justifies the helper or a local post-4.0 cleanup is accepted. |
| AnnoDocimal release/API migration | [AnnoDocimal #47](https://github.com/blackbuild/anno-docimal/issues/47), [#37](https://github.com/blackbuild/anno-docimal/issues/37), [#38](https://github.com/blackbuild/anno-docimal/issues/38), [#39](https://github.com/blackbuild/anno-docimal/issues/39), [#40](https://github.com/blackbuild/anno-docimal/issues/40) | [KlumAST #461](https://github.com/klum-dsl/klum-ast/issues/461) owns the 4.0-blocking adoption and links #450/#394/#391. |
| Reusable deterministic mirror task / configuration cache | [AnnoDocimal #35](https://github.com/blackbuild/anno-docimal/issues/35), [#39](https://github.com/blackbuild/anno-docimal/issues/39) | Link the adoption issue to #394/ADR 0005; keep IDEA wiring in KlumAST. |
| AnnoDocimal module contract | [AnnoDocimal #36](https://github.com/blackbuild/anno-docimal/issues/36) | Existing links to #391/#450 are correct; consume its result in the named-module tracer. |
| Projection/capture correctness | [AnnoDocimal #9](https://github.com/blackbuild/anno-docimal/issues/9), [#19](https://github.com/blackbuild/anno-docimal/issues/19), [#32](https://github.com/blackbuild/anno-docimal/issues/32), [#33](https://github.com/blackbuild/anno-docimal/issues/33), [#41](https://github.com/blackbuild/anno-docimal/issues/41), [#43](https://github.com/blackbuild/anno-docimal/issues/43) | Treat as #47 prerequisites; keep only KlumAST-specific regression assertions. |

No additional upstream issues are needed: the library-native plans already cover every accepted upstream change. The
three downstream work items now exist as native #450 sub-issues: blocking KlumCast 0.4 dependency/module/package
adoption [#459](https://github.com/klum-dsl/klum-ast/issues/459), desirable KlumCast SPI/check migration
[#460](https://github.com/klum-dsl/klum-ast/issues/460), and blocking AnnoDocimal 1.0 adoption
[#461](https://github.com/klum-dsl/klum-ast/issues/461).

## Explicit boundaries for issues 455 and 456

### Multi-Groovy redesign (#455)

The upstream evidence contributes facts but makes no design decision for
[#455](https://github.com/klum-dsl/klum-ast/issues/455):

- AnnoDocimal promises one Java 17 artifact set across Groovy 3, 4, and 5 and makes this evidence part of its 1.0 gates.
- KlumCast's current build tests Groovy 2.4, 3, and 4; its accepted successor contract coordinates the supported matrix
  with #455 and treats dropping Groovy 3 only as a last-resort cross-library decision.
- Stable automatic-module names are the immediate upstream answer. Neither library uses #450 to choose shared convention
  plugins, Gradle test-suite topology, CI layout, or a Groovy-version variant design.

### Versioned documentation (#456)

AnnoDocimal's repository decides that release tags are its current immutable documentation snapshots and that detailed
documentation is repository-owned. That is evidence for, not a decision on,
[#456](https://github.com/klum-dsl/klum-ast/issues/456). #450 does not select KlumAST's hosting, version selector, stable
URLs, wiki migration, branding, or Javadoc publication mechanism. AnnoDocimal IDE source mirrors remain distinct from
published Javadocs and user documentation.

## Closure check

The prior closed state was accidental rather than a completion decision. GitHub mechanically interpreted PR #457's negated
phrase `does not ... close #450` as an issue-closing instruction when the PR merged. At that point the issue body still
showed every deliverable checkbox unchecked and linked only the earlier AnnoDocimal #36 finding.

#450 is open again and must remain open until this audit, all three downstream adoption issues, the #391/#394
relationships, and every accepted upstream issue are discoverable from its body or comments. Future PR bodies and commit
messages must avoid GitHub auto-action keywords immediately before issue references when the intended relationship is
informational or negated.
