# Provisional 4.0 issue slate

This release view is derived from the complete [open issue index](issue-index.md), not from the outdated version plan in
`wiki/Roadmap.md`. The policy baseline is README/CHANGES, the Builder-first migration guide, accepted ADRs 0003–0009, and
current source/tests. ADR 0007 is superseded by ADR 0009, while ADR 0008 remains a later-4.x target. ADRs 0004–0006 and
0009 define the remaining accepted 4.0 boundaries.

## Release thesis

4.0 is the breaking Builder-first release. Its minimum coherent scope is to finish and freeze the public boundaries introduced by PR #429:

1. owned composition stays in one Construction session and materializes once;
2. completed DSL Objects have no construction-time mutation path;
3. the generated Builder layout and stable client entry points are intentional rather than accidental;
4. completed-model companion and Jackson interoperability behavior are documented contracts rather than provisional implementation details;
5. the handwritten package surface and Java module identities are intentional rather than accidental;
6. Java 17 and Groovy 3/4/5 remain supported.

## 4.0 must

| Issue | Why it blocks 4.0 | Required evidence before release | Dependencies / ordering |
|---|---|---|---|
| #391 — Java modules and final packages | Shipping the legacy `configdsl` namespace, split packages, or broad accidental exports would freeze the wrong handwritten interface and force another major break later. | One artifact set proven as named modules with Groovy 3/4/5 plus classpath fixtures; stable module names; qualified schema opening; no split/legacy packages; positive export allowlists; schema-plugin validation; ADR, tracer-bullet plan, import map, migration/CHANGES. | [#459](https://github.com/klum-dsl/klum-ast/issues/459) supplies KlumCast 0.4's stable artifact/module contract and [#461](https://github.com/klum-dsl/klum-ast/issues/461) supplies AnnoDocimal 1.0/#36. The prototype precedes the ADR and mass package movement; #394 remains the separate generated-interface boundary. |
| #394 — ADR 0005 generated DSL namespace | Decision is complete, but shipping current `$_RW`/markers would freeze the wrong API and same-project IDE completion remains broken. | [#433 DSL-1](https://github.com/klum-dsl/klum-ast/issues/433), DSL-2/DSL-3, and [#434 DSL-G](https://github.com/klum-dsl/klum-ast/issues/434): truthful interfaces/mirrors, narrow `KlumBuilder`, annotation migration, proven IDE-only Gradle wiring, migration/CHANGES, Groovy 3/4/5. | #433 precedes #437. #434 is implemented; final AnnoDocimal 1.0 adoption/task replacement is tracked by [#461](https://github.com/klum-dsl/klum-ast/issues/461). |
| #459 — KlumCast 0.4 artifact adoption | #391 cannot prove portable module-path behavior while KlumCast 0.3.1 has split packages and unstable automatic names. | Published 0.4.x satisfying `klum-cast#12/#24`; resolved package ownership/module names; service loading and ordinary classpath behavior; Groovy 3/4/5 consumer evidence. | Upstream #12/#24 precede #459; #459 precedes #391's final tracer. The durable check-SPI migration remains separate #460. |
| #461 — AnnoDocimal 1.0 adoption | AnnoDocimal explicitly requires final 1.0 before KlumAST 4.0; current 0.7.1 helpers, task workaround, configuration-cache limitation, and module identities are provisional. | Final 1.0.0 satisfying upstream tracker #47; supported API migration; reusable filtered task; strict configuration-cache reuse; projection/module/capture evidence; preserved IDEA-only mirror isolation. | Upstream #47 precedes #461; #461 then feeds #391 and #394. Development may test pre-releases, but release validation uses final artifacts. |
| #450 — integration audit | The dependency and ownership audit is a release prerequisite even though not every finding blocks. | The [final synthesis](issue-450-integration-audit.md), release classifications, upstream links, native sub-issues #459–#461, and discoverable #391/#394 boundaries are accepted. | Keep #450 open through audit review. Its implementation follow-ups retain their own states and release classifications. |
| #390 — ADR 0006 completed Object support | Decision is complete, but direct proxy/metadata access remains exposed and clients lack the supported Java facade. | [#435 OS-1](https://github.com/klum-dsl/klum-ast/issues/435), OS-2, and OS-3: root/subtree facade, structure, stored validation, proxy lockdown, serialization, and Java-first docs. | Coordinate OS-2 with #438's common companion split. |
| #428 — ADR 0009 Jackson interoperability | JSON-1/#439 and JSON-2/#440 implement useful Builder/property/identity groundwork, but 4.0 still lacks the explicit importer and executable asymmetric YAML contract. Shipping raw root reads alone would freeze the wrong entry point. | [#463 JSON-3](https://github.com/klum-dsl/klum-ast/issues/463): reviewed importer API, four explicit modes, caller-owned configuration, breadcrumbs/errors. [#464 JSON-4](https://github.com/klum-dsl/klum-ast/issues/464): separate import/export fixtures, documentary YAML read/enrich/write, role-based wiki/migration/CHANGES, Groovy 3/4/5. | #439/#440 are done groundwork. #464 is natively blocked by #463. No metadata implementation follows completed #447. |
| #431 — finalized ADR 0004 | AB-1 active sessions, AB-2 adaptable composition, and AB-3 Template/copy/scheduling boundaries are implemented; final compatibility closure remains. | [#436 AB-1](https://github.com/klum-dsl/klum-ast/issues/436), [#437 AB-2](https://github.com/klum-dsl/klum-ast/issues/437), and [#438 AB-3](https://github.com/klum-dsl/klum-ast/issues/438) are complete. AB-4 must close remaining compatibility/documentation gates. | AB-4 may now reconcile #431 with #390/#428 without broadening their remaining scopes. |

### Recommended must-item sequence

```text
#459 KlumCast 0.4 ──> #391 JPMS tracer
#461 AnnoDocimal 1.0 ─┬─> #391 JPMS tracer
                       └─> #394 DSL-3
#433 DSL-1 (done) ──> #437 AB-2 (done) ──> compatibility closure
#436 AB-1 (done) ─────────────────────────> compatibility closure
#435 OS-1 (done) ──> #390 OS-2 <──> #438 AB-3 (done) ──> compatibility closure
#439 JSON-1 (done) ──> #440 JSON-2 (done) ──> #463 JSON-3 ──> #464 JSON-4 ──> compatibility closure
#434 DSL-G ──> #394 DSL-3
```

The Builder-first decisions are no longer blockers; the shown implementation seams are. #391 additionally requires a
module-path feasibility proof before its package ADR can be finalized. The generated namespace must exist before
projected Builder signatures, while Model/Template companion changes and facade lockdown must share one internal boundary.
Jackson groundwork is complete; #463's public API review and implementation now precede #464's compatibility closure.

## 4.0 nice-to-have

These improve confidence or polish at the new public boundary but have a safe deferral path. They are not permission to expand the release until the listed must items are complete.

| Issue | Release value | Deferral safety / evidence |
|---|---|---|
| #201 — parameter names | Improves IDE/static-call clarity for newly frozen generated factories/Builders. | Relationship parameters already use the key field name; missing work is consistency/collision coverage, not runtime correctness. |
| #205 — missing-method diagnostics | Reduces migration pain when users typo Builder DSL methods. | Needs a current 4.0 reproduction; existing targeted lifecycle/adoption diagnostics already cover known Builder-first failures. |
| #240 — existing `@EqualsAndHashCode` exclusions | Protects equality from owner/transient/technical fields after companion generation changed. | Reproduce first: Groovy may already ignore synthetic `$proxy`. Defer if no current failing case exists. |
| #282 — real/virtual field naming conflicts | Prevents ambiguous generated Builder methods before API freeze. | No reported current reproduction; collection member collisions are already checked. |
| #371 — shadowed hierarchy fields | Could expose a real materialization/owner/default bug in the new Builder hierarchy. | Body contains no reproducer. A compile-time rejection can move to 4.1 if focused 4.0 hierarchy tests show no corruption. |
| #383 — generated getter Javadocs | Keeps public Builder/model accessor docs aligned with the migration. | Documentation-only; existing generated mutator/factory docs already cover the core Builder wording. |
| #389 — supported Gradle versions | Makes plugin compatibility claims explicit for the release. | No runtime change; can be published immediately after release if the tested matrix is documented honestly. |
| #460 — KlumCast check SPI migration | Avoids carrying the deprecated 0.4 check adapter into the new major release and improves structured diagnostic coverage. | KlumCast 0.4 intentionally provides the bridge, so #459/#391 can complete without #460. If deferred, document the bridge and its later removal. |

## Explicitly deferred from 4.0

### 4.1 candidates

#63, #132, #135, #140, #183, #202, #269, #281, #304, #310, #342, #357, #406, #420, and #427.

These are bounded enhancements or guards that fit the Builder-first architecture without redefining it. Each still needs an issue brief/acceptance criteria before implementation. #281 must remain separate from #431: ADR 0004's `applyLater < INSTANTIATE` invariant is narrower than general phase guards.

### Later 4.x

#14, #43, #79, #115, #129, #159, #184, #185, #190, #204, #207, #209, #261, #305, #314, #337, #344, #349, #352, #379, #386, and #414.

These are additive tooling, extension, or convenience features. Several need design work or may belong in separate modules. #79 and #314 are separate but related: #79 is a schema-authored resource default for an unset Simple Value, while #314 resolves a separate target from model-supplied seed data.

### 5.0 candidates

#13, #144, #161, #179, #180, #208, #265, #402, and #417.

These change foundational schema types, inheritance/container support, generated API exposure, tracing/storage models, or Java type shapes. They should not be smuggled into 4.0 merely because 4.0 is already breaking.

## Remaining provisional closure queue

The confirmed Jackson normalization completed #251, #430, and #447. The remaining evidence-backed recommendations are:

- **close as completed:** #98, #147, #158, #165, #168, #174, #193, #226, #335, #356, #358, #372, #411;
- **close as obsolete/superseded:** #23, #30, #83, #88, #91, #117, #182, #218, #287, #331;
- **close as duplicate:** #166 → #431, #338 → #207, #343 → #431, #350 → #310, #375 → #184 (provisional), #382 → #402.

Before closing #411, ask whether the maintainer still wants a separately named/public visitation “context path”; current code already separates creation breadcrumbs, model paths, and traversal paths.

## Issues requiring maintainer input before any disposition

- #142: Is arbitrary annotation injection still a desired extension seam after Jackson moved to `KlumAnnotationIntrospector`, and if so which generated targets/retention rules are required?
- #399: Provide a current PostCreate reproduction and the exact expected breadcrumb; the old lifecycle architecture no longer applies unchanged.

The Builder-first architecture decisions are complete in ADRs 0004–0009, with ADR 0007 superseded by ADR 0009. #391's maintainer intent is also confirmed, but
its dedicated package/module ADR follows the mandatory cross-Groovy prototype. These issues remain release blockers because
implementation, compatibility evidence, and user-facing migration work are still outstanding.

## Compatibility gate for release

For every 4.0 must item:

1. run the narrowest Groovy 3 feature first;
2. run affected module Groovy 3 suites;
3. run `groovy4Tests` and `groovy5Tests` because generated signatures/AST behavior change;
4. run the aggregate build/check and affected Jackson/Gradle scenarios;
5. verify user-facing wiki, migration navigation/sidebar where applicable, `CHANGES.md`, linked issues/PR, and SonarCloud findings.

Java 17 remains the minimum. None of the recommended 4.0 work should raise that baseline.
