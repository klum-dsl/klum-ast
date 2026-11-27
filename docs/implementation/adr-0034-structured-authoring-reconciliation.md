# ADR 0034 strategy and tracker reconciliation

Date: 2026-11-26. Scope: documentation and directly affected tracker guidance only.

[ADR 0034](../adr/0034-bounded-structured-authoring.md) records the accepted strategy. This is a reconciliation and
qualification handoff, not an implementation plan or research assignment.

Update, 2026-11-27: the maintainer selected public interface `KlumYaml`, a builder-owned immutable implementation and
captured client-overridable limits. [The interface extension](../adr/0034-bounded-structured-authoring.md#selected-public-yaml-interface--1114),
[working implementation plan](issue-1114-yaml-integration.md) and
[qualification record](evidence/issue-1114-yaml-qualification.md) supersede the original #1114 interface-open checkpoint
below. No parser release, production implementation, artifact/dependency addition or release placement is selected.

## Authority and evidence

The maintainer accepted decisions 1–16 in `structured-authoring-strategy-brief.md` on 2026-11-26 and subsequently
assigned their ADR/tracker reconciliation. That interview brief was read from the originating evidence worktree;
its source baseline was `d9bc6b5d27f8e7226ea38bbd12e6ca076f4b0e0e`, also this branch's fetched master base.
ADR 0034 preserves all sixteen decisions by numbered sections and separately retains the explicit validation boundary.
No remaining strategy answer from that interview is treated as pending.

Complete current issue bodies/comments for #982, #953, #952, #1113, #1114, #959, #971, #981, #1082, #929, #955,
#1065, #1033 and #1085 were read on 2026-11-26. All were open. Current
[Structured Input](../user/Structured-Input.md), [JSON Schema](../user/Json-Schema.md),
[Jackson Integration](../user/Jackson-Integration.md) and ADRs 0009/0031/0032 distinguish delivered behavior from historical
checkpoints. Source checks of Factory/Builder signatures, numeric conversion and keyed Jackson export support the specific
boundary examples; no runtime tests or new codec/projection qualification were performed by this assignment.

## Issue ownership and tracker impact

These are related tracks, not a new dependency chain. No native dependency edges, issue state, labels or milestones change.

| Issue | Reconciled responsibility / remaining scope | Tracker action |
| --- | --- | --- |
| [#982](https://github.com/klum-dsl/klum-ast/issues/982) | Accepted Struct preference for Schema-owned configuration; durable managed foreign-format Jackson contract; current capability matrix, no sunset/parity claim | Refine body, replacing stale current guidance while linking historical proposal |
| [#953](https://github.com/klum-dsl/klum-ast/issues/953) | Root/keyed Factory, Builder application, Template input, overlays and root/body schema artifacts are delivered; file/URL/network/source convenience remains undecided | Refine body to remove delivered API/key prerequisites from future scope |
| [#952](https://github.com/klum-dsl/klum-ast/issues/952) | Coordination of separately justified grammar increments; ordered execution, commands, selected converters, concrete sequences and finite aliases are delivered | Refine current scope; preserve dated slice history |
| [#1113](https://github.com/klum-dsl/klum-ast/issues/1113) | Qualified completed-Model → typed-tree fidelity through existing Jackson and explicitly named reconstruction subsets | Refine body with accepted effective-value boundary and outstanding proof |
| [#1114](https://github.com/klum-dsl/klum-ast/issues/1114) | Bidirectional YAML/tree qualification; public interface/factory and configurable captured limits now selected; exact supporting contract and adoption remain future work | Reconcile selected interface in body; retain library/implementation/release gates |
| [#959](https://github.com/klum-dsl/klum-ast/issues/959) | Static literal-default eligibility, compiled transport and field/relationship-use placement proof; computed defaults remain runtime/prose | Add dated current guidance above retained history |
| [#971](https://github.com/klum-dsl/klum-ast/issues/971) | Future executable parameter enforcement, separately commissioned after its proof gates; historical preflight wording is not current execution policy | Add dated boundary clarification; do not claim its old failures reproduced |
| [#981](https://github.com/klum-dsl/klum-ast/issues/981) | Delivered C1 raw selected-parameter assertions; C2/broader projection remains separate from enforcement | Read/link only; existing separation is accurate |
| [#1082](https://github.com/klum-dsl/klum-ast/issues/1082) | Modular schema documents/external refs; preserve root/body purpose and use-site meaning if pursued | Read/link only; no new default/output/runtime-validation promise |
| [#929](https://github.com/klum-dsl/klum-ast/issues/929) | Separate future parallel Jackson 3 adapter; retain Jackson 2 | Read/link only; no new support commitment |
| [#955](https://github.com/klum-dsl/klum-ast/issues/955) | Separate optional trusted source migration, comparing retained state with scoped call recording | Add dated guidance separating migration from stack/projection/codec acceptance |
| [#1065](https://github.com/klum-dsl/klum-ast/issues/1065) | Enum String-converter precedence; current Struct.Name workaround remains viable | Read/link only; no automatic admission |
| [#1033](https://github.com/klum-dsl/klum-ast/issues/1033) | Exact compiler-owned scalar child-producer admission; current mapping workaround remains viable | Read/link only; no automatic admission |
| [#1085](https://github.com/klum-dsl/klum-ast/issues/1085) | Active-child FromStruct Factory/projection gap; With/applyStruct workaround remains viable | Read/link only; separate from codec/loading convenience |

## Remaining pre-implementation decisions and evidence

These gates require later bounded assignments; they neither reopen the accepted strategy nor authorize work now.

| Owner | Required next evidence / decision |
| --- | --- |
| #1114 | Local Engine 2.10 proof recorded; public interface/factory and captured overridable limits selected. Finalize supporting proposals and qualified ranges, choose a library only under separate adoption authority, then qualify public API/dependencies, packaging/JPMS and Java/Groovy consumers. See the working plan; proof is not shipment. |
| #1113 | Name projection/subset, mapper/codecs, inclusion and reference policy; qualify exact shared-tree values/numbers/acyclicity. For each reconstruction claim name compared effective values and external context, including key/default/lifecycle counterexamples. No YAML-library prerequisite. |
| #959 | Prove literal eligibility without execution, compiler-owned inert transport/completeness and exact use-site placement, including the same child type with relationship defaults 2 and 8. Decide metadata compatibility and projection representation only with that evidence. |
| #953 / #1085 | Independently decide source loading/resource ownership and active-child Factory ergonomics. Diagnostic source text is not a locator or key; preserve delivered positional-key APIs and explicit schema entry purposes. |
| #952 / #1065 / #1033 | Concrete adopter need and separately accepted grammar/selection/ownership/lifecycle/projection contract for each increment. Parsing or output fidelity cannot decide admission. |
| #971 / #981 | Recommission executable-enforcement proof only explicitly; retain C1's separate bounded projection authority and deferred broader projection. Never insert an automatic JSON Schema gate. |
| #1082 / #929 / #955 | Their own topology, Jackson-major or trusted-migration questions remain independently scoped and placed; none blocks the other tracks merely by adjacency. |

## Validation and delivery boundary

Only Markdown architecture/glossary records change. Current user documentation, migration navigation and CHANGES remain
accurate because this assignment delivers no user-facing runtime capability or compatibility change. No examples or
generated/test inputs change. Applicable checks are Markdown structure, relative links/fragments, strategy/issue coverage
and `git diff --check`; Groovy 3/4/5 lanes and the user-site renderer are not applicable to these architecture-only files.
Independent Standards and Specification reviews cover both the repository diff and prepared tracker refinements before
publication. Exact review, check, tracker-update and hosted CI outcomes belong in the PR handoff; pending CI/Sonar must
remain pending rather than being replaced by local documentation checks.

GitHub CLI repository reads and push capability were verified as authorized; Git SSH fetch was authorized. Each delivery
channel is checked separately before use, with no credentials or raw authentication errors retained. Publication uses
non-closing Related issue references. Review/merge and final Hive reconciliation remain separate from documentation work;
the task stays `(PR:open)` while its draft PR is open and does not self-archive.
