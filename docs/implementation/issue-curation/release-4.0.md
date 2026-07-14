# Provisional 4.0 issue slate

This release view is derived from the complete [open issue index](issue-index.md), not from the outdated version plan in `wiki/Roadmap.md`. The current policy baseline is README/CHANGES, the Builder-first migration guide, accepted ADRs 0003/0004, and current source/tests.

## Release thesis

4.0 is the breaking Builder-first release. Its minimum coherent scope is to finish and freeze the public boundaries introduced by PR #429:

1. owned composition stays in one Construction session and materializes once;
2. completed DSL Objects have no construction-time mutation path;
3. the generated Builder layout and stable client entry points are intentional rather than accidental;
4. completed-model companion and deserialization behavior are documented contracts rather than provisional implementation details;
5. Java 17 and Groovy 3/4/5 remain supported.

## 4.0 must

| Issue | Why it blocks 4.0 | Required evidence before release | Dependencies / ordering |
|---|---|---|---|
| #394 — final generated Builder layout | ADR 0003 explicitly leaves the Builder's public name/location and `KlumRwObject` removal undecided. Shipping `$_RW` plus a deliberately redundant marker would accidentally freeze an implementation detail. | Decision recorded in an ADR or accepted issue comment; generated-interface tests updated; GDSL/AnnoDoc/delegate targets updated; migration and CHANGES updated; Groovy 3/4/5 AST lanes pass. | Decide **before** finalizing #431 generated covariant `AsBuilder` signatures and before #390 documents stable entry points. |
| #390 — stable companion entry API | `KlumModelProxy.getProxyFor` is public but the migration guide says client access is not finalized. Breadcrumb/model paths, metadata, validation state, and validator memoization need a supported surface or explicit internal status. | Enumerated supported operations; stable facade or explicit supported `KlumModelProxy` API; internal members hidden/deprecated as appropriate; wiki/Javadoc/migration examples; serialization tests. | Coordinate with #394 naming and #431's separate Template companion. Avoid exposing Template recipe internals. |
| #428 — deserialization lifecycle semantics | Current Jackson behavior persists fields and reruns mutating lifecycle callbacks, which is explicitly provisional. The three candidate policies can produce different completed models and validation results. | Maintainer chooses one contract; ADR/wiki/CHANGES updated; `KlumDeserializer` and `JsonExportSpec` cover persisted, derived, owner/link, validation, and non-idempotent lifecycle behavior; Groovy 3/4/5 module lanes pass. | Can be decided in parallel with #394, but implementation/docs must land after/with any #431 Template deserialization implications. |
| #431 — implement ADR 0004 | PR #429 intentionally regressed established collection factory/converter behavior from #198/#270/#300/#319. ADR 0004 is accepted and pending tests record the target. Releasing without it would make temporary incompatibilities permanent and leave Template recipe state on ordinary Model companions. | All reasoned `@PendingFeature` cases enabled; `Create.AsBuilder`, framework-owned inputs, projections/twins, opaque-producer rejection, lifecycle counts, ownership/cycles, Template identity/state, and `applyLater < INSTANTIATE` covered; docs/CHANGES/ADR status current; Groovy 3/4/5 and aggregate build pass. | Implement after #394's type-layout decision or keep generated type indirection isolated until that decision lands. Must respect #428 policy and feed #390's public/internal companion split. |

### Recommended must-item sequence

```text
#394 generated type decision
        ├──> #431 Builder-producing composition
        └──> #390 stable companion/facade ──┐
#428 deserialization policy ────────────────┴──> release documentation + full compatibility lanes
```

#394 is first because #431 must generate truthful concrete Builder return/delegate types, and #390 must not document a facade around names about to change. #428 can be decided concurrently, but its final implementation must be checked against Template companion work in #431.

## 4.0 nice-to-have

These improve confidence or polish at the new public boundary but have a safe deferral path. They are not permission to expand the release until the four must items are complete.

| Issue | Release value | Deferral safety / evidence |
|---|---|---|
| #201 — parameter names | Improves IDE/static-call clarity for newly frozen generated factories/Builders. | Relationship parameters already use the key field name; missing work is consistency/collision coverage, not runtime correctness. |
| #205 — missing-method diagnostics | Reduces migration pain when users typo Builder DSL methods. | Needs a current 4.0 reproduction; existing targeted lifecycle/adoption diagnostics already cover known Builder-first failures. |
| #240 — existing `@EqualsAndHashCode` exclusions | Protects equality from owner/transient/technical fields after companion generation changed. | Reproduce first: Groovy may already ignore synthetic `$proxy`. Defer if no current failing case exists. |
| #251 — renamed Jackson properties | Fixes a real raw-map deserializer limitation. | Jackson remains documented beta and #428 can explicitly preserve this known limitation for 4.0; do not claim renamed-property support. |
| #282 — real/virtual field naming conflicts | Prevents ambiguous generated Builder methods before API freeze. | No reported current reproduction; collection member collisions are already checked. |
| #371 — shadowed hierarchy fields | Could expose a real materialization/owner/default bug in the new Builder hierarchy. | Body contains no reproducer. A compile-time rejection can move to 4.1 if focused 4.0 hierarchy tests show no corruption. |
| #383 — generated getter Javadocs | Keeps public Builder/model accessor docs aligned with the migration. | Documentation-only; existing generated mutator/factory docs already cover the core Builder wording. |
| #389 — supported Gradle versions | Makes plugin compatibility claims explicit for the release. | No runtime change; can be published immediately after release if the tested matrix is documented honestly. |

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

## Provisional closure queue

No closure was performed. After maintainer review, the evidence supports:

- **close as completed:** #98, #147, #158, #165, #168, #174, #193, #226, #335, #356, #358, #372, #411, #430;
- **close as obsolete/superseded:** #23, #30, #83, #88, #91, #117, #182, #218, #287, #331;
- **close as duplicate:** #166 → #431, #338 → #207, #343 → #431, #350 → #310, #375 → #184 (provisional), #382 → #402.

Before closing #411, ask whether the maintainer still wants a separately named/public visitation “context path”; current code already separates creation breadcrumbs, model paths, and traversal paths.

## Issues requiring maintainer input before any disposition

- #142: Is arbitrary annotation injection still a desired extension seam after Jackson moved to `KlumAnnotationIntrospector`, and if so which generated targets/retention rules are required?
- #391: What modules/packages are to be changed, and is this a 4.x compatibility task or a 5.0 namespace redesign?
- #399: Provide a current PostCreate reproduction and the exact expected breadcrumb; the old lifecycle architecture no longer applies unchanged.

The four 4.0 must issues also require maintainer decisions, but they already have enough evidence to remain release blockers rather than `needs-info` candidates.

## Compatibility gate for release

For every 4.0 must item:

1. run the narrowest Groovy 3 feature first;
2. run affected module Groovy 3 suites;
3. run `groovy4Tests` and `groovy5Tests` because generated signatures/AST behavior change;
4. run the aggregate build/check and affected Jackson/Gradle scenarios;
5. verify user-facing wiki, migration navigation/sidebar where applicable, `CHANGES.md`, linked issues/PR, and SonarCloud findings.

Java 17 remains the minimum. None of the recommended 4.0 work should raise that baseline.
