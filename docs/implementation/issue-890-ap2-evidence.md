# Issue 890 / ADR 0029 AP-2 evidence

Date: 2026-10-14. Scope: AP-2 contribution normalization, legality and diagnostics only.
Base: `944925e8` (merged AP-1/AP-1b). Branch: `codex/issue-890-ap2`.

## Ownership and dependency route

The existing neutral KlumCast contributor remains the only production entry point. KlumAST returns a
complete default-only batch or no batch on propagation errors; it never mutates the declaration after
handoff. KlumCast owns constraint evaluation, validation state, binding/path diagnostics and rendering.
No VALIDATED metadata is copied, set or cleared. Required/Optional keep their existing local registrations;
their only change is carrying the input annotation's source position onto the lowered Validate annotation.

KlumCast annotations/SPI/compiler remain aligned at public `0.5.0-rc.2`, resolved by the ordinary
Central-only build. No dependency, repository, publishing, release-default or generated API change.

## Acceptance matrix

| Contract | Executable evidence |
| --- | --- |
| Repeated requests and explicit `PropagateAnnotation.List` | `AnnotationPropagationContributionTest`: Groovy source, compiled Groovy and separately javac-compiled Java 17 libraries; both request spellings |
| Genuine applied containers, explicit/default values, retained originals | Same matrix: physical container and domain values retained; selected defaults unchanged; unrelated arrays inert |
| Explicit plus propagated, two requests/domains, repeated applied domains | Same matrix: deterministic duplicate failure, even with repeatable selected Label; ordinary explicit repeats/containers succeed |
| Both contribution identities | `AnnotationPropagationRulesTest`: occurrence paths and container indexes identify repeated requests/domains separately |
| Type/target/default legality and one-hop guard | Source/Groovy/Java matrix: wrong target, required member, self-reference and cycle; rules/diagnostic tests add DSL and non-annotation selections before local casts |
| Default-only members | Both request spellings retain configured domain values and selected defaults; existing Required level/message non-forwarding control remains |
| KlumCast direct-control constraints | `AnnotationPropagationDiagnosticTest`: selected field-type/member constraints, introduced peer conflict, existing peer conflict and independently invalid original domain match direct binding/path and count, with effective origin/context |
| Applied source position | Rules test catches source-default containers pointing at annotation-library definitions; diagnostics now use the applied container |
| Downstream projection/lowering | Instruction-selection compiler observation: Required/Optional Validate nodes, Query Model/Builder twins and Cluster getter retain applied annotation positions |
| Collector interactions | Source and compiled Groovy collectors cannot be selected; early domain rewriting retains ordinary Groovy misplaced-marker failure, without merging/precedence |

The documentary matrix is
`AnnotationPropagationContributionTest#'repeated and explicit request containers retain domains and selected defaults (#kind, #policy)'`,
linked to [repeatable requests and containers](../user/Annotation-Propagation.md#repeatable-requests-and-containers).
Existing AP-1 documentary AutoCreate/DefaultValues coverage remains the primary consumer example.

## Rewrite limitation

Groovy's AnnotationCollector can replace an applied collector domain before the neutral contributor runs,
so no original domain remains to propagate or diagnose by name. Combining collector and propagation
metadata on a field-use domain produces the ordinary misplaced PropagateAnnotation target error in source
and compiled-library controls. Selecting a collector is explicitly rejected. No alias merging, collector
mode precedence, extra scheduler or late rescan is introduced. Independent third-party rewriting and
annotations introduced after handoff remain outside the ordering contract.

## Validation and delivery

Java 17 focused tests and the final Groovy 3/4/5 matrix, root checks, two-axis review and final-head
CI/SonarCloud results are recorded here before handoff.

Tracker discrepancy: GitHub reports issue #890 as closed, with its investigation-era body and no comments.
This implementation follows the accepted ADR/plan and explicit AP-2 assignment. Issue state, labels,
milestone and release/curation placement are unchanged. PR relationship is `Related: #890`.
AP-3/AP-4/AP-5, consumer admission and final release/issue reconciliation remain separate work.
Curation and reusable agent-policy impact: none; no policy files changed.
