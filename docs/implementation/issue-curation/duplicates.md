# Duplicate and overlap map

This file separates likely duplicates from merely related issues. All mappings are provisional and based on the issue bodies/comments plus the cluster evidence in [issue-index.md](issue-index.md). The initial inventory was read-only; the later maintainer-confirmed #79/#314 clarification is recorded below and in the issue bodies.

## Recommended duplicate closures

| Duplicate | Canonical issue | Evidence and retained requirement |
|---|---|---|
| #166 — move synthetic Template/proxy state | #431 — ADR 0004 composition protocol | ADR 0003 already moved construction state to `KlumBuilder` and completed state to `KlumModelProxy`. The only material remainder—Template-only deferred recipe state moving to `KlumTemplateProxy`/`TemplateRecipeState`—is explicit #431 acceptance. Evidence: ADRs 0003/0004, `KlumModelProxy.applyLaterClosures`, #431 body. |
| #338 — `CreateMultipleFromClasspath` should return a list | #207 — classpath creation for multiple components | Neither API exists. #338 only supplies the return-shape choice for the same multi-descriptor aggregation requested by #207. Preserve “return `List<T>` rather than an implicit merge” when #207 is specified. Evidence: issue bodies; `FactoryHelper.createFromClasspath` reads exactly one descriptor. |
| #343 — mark Template objects | #431 — ADR 0004 composition protocol | ADR 0004 explicitly adopted graph-aware Template identity, rejection from all ordinary relationships (including `LINK`), and a Template-specific companion. These are checklist items in #431. Evidence: ADR 0004 and #431 acceptance criteria. |
| #350 — runtime/scoped copy strategies | #310 — instance-scoped `copyFrom` strategy | #350 has no additional body; #310 already asks for strategy overrides on a specific Template/copy source/run. Evidence: issue bodies; `CopyHandler` currently resolves annotations only. |
| #375 — Ensemble/Template fields | #184 — Template methods/fields | Both request declarative schema-member/method generation from mappings and both suggest eventual extraction. Neither defines a distinct accepted contract. This mapping is **provisional**: if “Ensemble” is intended to copy only declarations while #184 generates forwarding methods, split them with explicit acceptance criteria instead. |
| #382 — logging | #402 — logs, traces and validations | #382's only refinement chooses a framework-independent trace mechanism. #402 is the broader issue for lifecycle events, validation events, path distinction, and companion storage. Retain “no SLF4J/JUL dependency unless separately justified” on #402. |

## Overlapping issues that should remain separate

| Cluster | Why these are not duplicates |
|---|---|
| #79, #314, #386, #430 | #79 is a schema-authored, URI-backed default for an otherwise-unset Simple Value. #314 resolves a separate target from a seed supplied as model data by the model implementer. #386 concerns DSL Object Templates/recipes and could reuse analogous resource resolution. #430 is the already-implemented whole-map/tree input path. Evidence: maintainer-confirmed #79/#314 issue clarifications, `DefaultPhase`, Template APIs, and `Create.FromMap` source/tests. |
| #91, #218, #394 | #91 and #218 are superseded historical designs (interface/implementation split and dynamic proxy). #394 is the live 4.0 decision about final Builder naming, top-level versus nested layout, generated interfaces, and removal of `KlumRwObject`. Close the historical alternatives as obsolete; do not merge their old requirements wholesale into #394. |
| #98, #135, #158, #342, #343/#431, #386 | All concern Templates, but at different seams: lexical scope (#98), collection-factory Template lists (#135), anonymous Template syntax (#158), Template-plus-configuration convenience (#342), Template identity/recipe storage (#431), and declarative field-scoped Templates (#386). #98/#158 appear completed; the others remain distinct. |
| #132, #209, #430, #431 | These affect factories but are separate: post-source configuration (#132), return value of a collection block (#209), root map data input (#430, already implemented), and the active-session Builder-producing protocol (#431). #431 should provide a nested `FromMap` path but need not add #132 or #209 convenience APIs. |
| #142, #251, #358, #428 | #142 is generic annotation injection; completed #251 is Jackson property-name binding; #358 is the implemented Builder deserializer; #428 is the broader interoperability/importer contract. The modern Jackson module makes the old Jackson-specific motivation of #142 obsolete without resolving generic annotation injection. |
| #201, #269, #383, #394 | All affect IDE-visible generated API, but cover parameter names, delegating-script type information, generated Javadoc, and generated type layout respectively. #394 can invalidate implementation details for the others but does not deliver them. |
| #281, #305, #420, #431 | These touch phases but have different contracts: general operation guards, schema-defined custom phases, lifecycle classes, and the narrower construction-session/materialization boundary. ADR 0004 explicitly excludes general #281 guards from #431. |
| #304, #310, #342, #349, #352, #414 | These share `CopyHandler`, but request different capabilities: multiple recipe sources, per-run policy, Template-plus-config syntax, custom strategy SPI, a new enum behavior, and owner-aware mixin defaults. Implementing one does not settle the others. |
| #207/#338 and #337 | Multi-descriptor runtime loading (#207) is not the same as Gradle source inclusion/combined compilation (#337), even though both may aggregate models. |
| #251, #358, #428 | #358's Builder deserializer and #251's resolved-name mapping completed independently; #428 remains open for the explicit importer and asymmetric interoperability contract. Historical overlap did not make the issues duplicates. |
| #357 and #344 | Flattened Cluster collection paths are a Layer 3 projection feature; typed `StructureUtil.visit` is a general traversal API. They may share traversal code but not requirements. |
| #399, #411, #402 | #399 needs a concrete PostCreate breadcrumb reproduction; #411's basic path split appears implemented; #402 proposes a much broader event model. Do not close #399 merely because #411 exists. |

## Completed-by-later-work links

These are not duplicates; later work appears to have delivered or superseded the requested behavior:

- #147 → repeated keyed child configuration now merges the same Builder (issue #325 tests).
- #158 → collection factories have anonymous map Template overloads.
- #165/#193 → ADR 0003 Builder-first separation.
- #168 → `@Field.keyMapping` supports unkeyed map values.
- #174 → `ParameterAnnotation` reflection tests.
- #226 → current Gradle plugins, BOM, descriptors, and scenarios.
- #335 → commit `fe9b3eff` removes the flaky extraction path and retry.
- #356 → commit `1a278c61` implements `@Layer3(fixedKey=true)`.
- #358 → PR #429's Builder-backed `KlumDeserializer`; policy intentionally remains #428.
- #372 → explicit and no-Spock Gradle scenarios.
- #430 → existing `Create.FromMap` implementation/docs/tests from closed #359; nested composition remains #431.
