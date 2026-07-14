# Provisional 4.0 issue slate

This release view is derived from the complete [open issue index](issue-index.md), not from the outdated version plan in
`wiki/Roadmap.md`. The policy baseline is README/CHANGES, the Builder-first migration guide, accepted ADRs 0003–0008, and
current source/tests. ADR 0008 is a later-4.x target; ADRs 0004–0007 define the remaining 4.0 boundary.

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
| #394 — ADR 0005 generated DSL namespace | Decision is complete, but shipping current `$_RW`/markers would freeze the wrong API and same-project IDE completion remains broken. | [#433 DSL-1](https://github.com/klum-dsl/klum-ast/issues/433), DSL-2/DSL-3, and [#434 DSL-G](https://github.com/klum-dsl/klum-ast/issues/434): truthful interfaces/mirrors, narrow `KlumBuilder`, annotation migration, proven IDE-only Gradle wiring, migration/CHANGES, Groovy 3/4/5. | #433 precedes #437. #434 is an adoption gate for DSL-3 and may run in parallel. |
| #390 — ADR 0006 completed Object support | Decision is complete, but direct proxy/metadata access remains exposed and clients lack the supported Java facade. | [#435 OS-1](https://github.com/klum-dsl/klum-ast/issues/435), OS-2, and OS-3: root/subtree facade, structure, stored validation, proxy lockdown, serialization, and Java-first docs. | Coordinate OS-2 with #438's common companion split. |
| #428 + #251 — ADR 0007 configuration replay | Decision is complete, but raw Map restoration still duplicates derived state and ignores resolved property naming. | [#439 JSON-1](https://github.com/klum-dsl/klum-ast/issues/439), [#440 JSON-2](https://github.com/klum-dsl/klum-ast/issues/440), and JSON-3: property-aware binding, one lifecycle, LINK identity, customization, Template rejection, migration/CHANGES, Groovy 3/4/5. | #439 may start independently; #440's Template/reference integration aligns with #438. |
| #431 — finalized ADR 0004 | The confirmed regressions and ordinary-model Template state remain in current source. | [#436 AB-1](https://github.com/klum-dsl/klum-ast/issues/436), [#437 AB-2](https://github.com/klum-dsl/klum-ast/issues/437), [#438 AB-3](https://github.com/klum-dsl/klum-ast/issues/438), and AB-4: active session, projections/twins, Template state/copy sources, strict applyLater boundary, compatibility closure. | #437 depends on #433/#436; #438 coordinates with #390/#428; AB-4 waits for both. |

### Recommended must-item sequence

```text
#433 DSL-1 ────────────────> #437 AB-2 ──┐
#436 AB-1 ──────────────────────────────┤
#435 OS-1 ──> #390 OS-2 <──> #438 AB-3 ├──> documentation + full compatibility lanes
#439 JSON-1 ──> #440 JSON-2 <─ #438 AB-3 ┘
#434 DSL-G ──> #394 DSL-3
```

The decisions are no longer blockers; the shown implementation seams are. The generated namespace must exist before
projected Builder signatures, while Model/Template companion changes and facade lockdown must share one internal boundary.
Jackson property binding can proceed independently until Template/LINK serialization integration.

## 4.0 nice-to-have

These improve confidence or polish at the new public boundary but have a safe deferral path. They are not permission to expand the release until the four must items are complete.

| Issue | Release value | Deferral safety / evidence |
|---|---|---|
| #201 — parameter names | Improves IDE/static-call clarity for newly frozen generated factories/Builders. | Relationship parameters already use the key field name; missing work is consistency/collision coverage, not runtime correctness. |
| #205 — missing-method diagnostics | Reduces migration pain when users typo Builder DSL methods. | Needs a current 4.0 reproduction; existing targeted lifecycle/adoption diagnostics already cover known Builder-first failures. |
| #240 — existing `@EqualsAndHashCode` exclusions | Protects equality from owner/transient/technical fields after companion generation changed. | Reproduce first: Groovy may already ignore synthetic `$proxy`. Defer if no current failing case exists. |
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

The 4.0 architecture decisions are complete in ADRs 0004–0007. Their issues remain release blockers because implementation,
compatibility evidence, and user-facing migration work are still outstanding, not because maintainer intent is unknown.

## Compatibility gate for release

For every 4.0 must item:

1. run the narrowest Groovy 3 feature first;
2. run affected module Groovy 3 suites;
3. run `groovy4Tests` and `groovy5Tests` because generated signatures/AST behavior change;
4. run the aggregate build/check and affected Jackson/Gradle scenarios;
5. verify user-facing wiki, migration navigation/sidebar where applicable, `CHANGES.md`, linked issues/PR, and SonarCloud findings.

Java 17 remains the minimum. None of the recommended 4.0 work should raise that baseline.
