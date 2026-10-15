# ADR 0029 AP-5 acceptance ledger and consumer gate

Date: 2026-10-15. Related: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md) and its
[complete implementation plan](adr-0029-default-only-annotation-propagation.md).
Base: master `ce897d5f7bd4aec2380f8369d12337fec30bf00a` (merged AP-4).
Branch: `codex/issue-890-ap5`.

## Acceptance status

AP-1 through AP-4 are merged and qualified as summarized below. AP-5 prepares local user guidance
and documentary coverage. **Final AP-5 acceptance is pending real ScHelm evidence supplied to Hive.**
No ScHelm run or acceptance is inferred from local examples, consumer-fit reports, a publication request,
or the earlier artifact matrix. No PR publication, issue closure or release is claimed by this ledger.

| Slice | Verified merged delivery | Decisive record |
| --- | --- | --- |
| AP-1 | [PR #893](https://github.com/klum-dsl/klum-ast/pull/893), `9a298b9a355717dfab5fe311ae5473eaab4d79b5`; [SonarCloud follow-up #894](https://github.com/klum-dsl/klum-ast/pull/894), `944925e859e931129c4d6536b29ec3362ad02c92` | [RC.2 admission and qualification](issue-890-ap1-rc2-evidence.md) |
| AP-2 | [PR #897](https://github.com/klum-dsl/klum-ast/pull/897), `93e5a4529fff9685bc1aef01300fccd8613d4ddb` | [Contribution and diagnostic matrix](issue-890-ap2-evidence.md) |
| AP-3 | [PR #898](https://github.com/klum-dsl/klum-ast/pull/898), `d94cc44c0b5b1d28a7b34589c2abbc20cbbcbe20` | [Generated/inherited/lifecycle parity](issue-890-ap3-evidence.md) |
| AP-4 | [PR #900](https://github.com/klum-dsl/klum-ast/pull/900), `ce897d5f7bd4aec2380f8369d12337fec30bf00a` | [Actual-artifact matrix](issue-890-ap4-evidence.md); final-head [CI 38639133477](https://github.com/klum-dsl/klum-ast/actions/runs/38639133477) passed on `820d2867645abd7a01915fa1f10d3db6faee6df7`, including SonarCloud |
| AP-5 | Local preparation; external gate pending | This ledger; no AP-5 remote CI/SonarCloud result exists yet |

Live final-head checks for AP-1 follow-up #894 (`f01e8fd5`), AP-2 #897 (`416eb2bd`), AP-3 #898
(`563f5dcf`) and AP-4 #900 (`820d2867`) all report successful build, JUnit and both SonarCloud checks.
These results qualify their respective merged heads, not this unpublished AP-5 branch.

Historical slice records retain their original delivery observations. For example, AP-2 observed #890
closed temporarily; live #890 is OPEN at this reconciliation. No tracker mutation is made here.

## AP-1 acceptance disposition

| Accepted item | Disposition / evidence |
| --- | --- |
| Public retained, repeatable JDK-only marker and nested container ship with production contributor | Merged; marker/compiler paired, normal artifact discovery in `AnnotationPropagationArtifactTest` and AP-4 |
| DSL-only eligibility; original owner-declared sites; interfaces/generated/inherited sites excluded; no carried DSL | `AnnotationPropagationRulesTest`, AP-3 mixed/nested/interface/inheritance controls |
| Unrelated and no-work declarations remain inert, without an alternate scanner or producer | AP-1 contributor/rules controls; AP-3 eligibility; AP-4 ordinary class/field/method aliases |
| One complete guarded batch at the neutral handoff; no later mutation or validation-state manipulation | AP-1 supported RC.2 integration; AP-2 effective-set diagnostic controls; AP-4 shipped provider |
| DefaultValues-only AutoCreate tracer retains domain members; normal supplied-child precedence | `AnnotationPropagationTest` documentary tracer; AP-4 Java/Groovy domain consumers; AP-5 documentary relationships |
| Original retention and SOURCE/CLASS/RUNTIME selected retention | AP-1 rules/bytecode controls and artifact runtime reflection |
| Required/Optional and unrelated direct foreign local-transform discovery remain normal | AP-1 sentinels, AP-3 lowering/compatibility and AP-4 separately compiled foreign control |

The hard KC-0 prerequisite is qualified by actual public KlumCast annotations/SPI/compiler
`0.5.0-rc.2` from Central. It supersedes historical dev and RC.1 probes. KlumCast owns traversal,
constraints, filters, state, aggregation/rendering and at-most-once effective validation; KlumAST owns
only the complete default-only contribution and its legality/provenance. No second validation engine,
independently ordered global producer or direct converter invocation is admitted.

## AP-2 acceptance disposition

| Accepted item | Disposition / evidence |
| --- | --- |
| Repeated requests and explicit nested List in source and compiled Java/Groovy libraries | `AnnotationPropagationContributionTest` documentary matrix |
| Genuine applied-domain containers, including default values, retain originals | Same matrix; unrelated annotation arrays remain inert |
| Explicit plus propagated duplicates | Deterministic failure in rules/contribution tests; AP-4 migration negative control |
| Two requests/domains/repeated applied domains, including repeatable selected types | Same deterministic failure with distinct identities/indexes; explicit-only repeats unchanged |
| Nested selections, self-reference and cycles | Early one-hop rejection in source/compiled matrices |
| Non-annotation, wrong target, DSL and required-member selections | Early provenance-bearing rejection before downstream casts; no fabricated member values |
| Differing domain and selected members | Retained configured domains, selected defaults only; no forwarding |
| Target/member/coannotation constraints and independently invalid original domains | `AnnotationPropagationDiagnosticTest` direct controls preserve binding/path/count and effective origin |
| Applied positions and provenance survive lowering/projection | Required/Optional Validate, Query twins and Cluster getter instruction-selection controls |
| AnnotationCollector/other rewriting interactions | Selected collectors rejected; early domain collector rewriting explicitly unsupported; no merge/priority/rescan |

## AP-3 acceptance disposition

| Accepted item | Disposition / evidence |
| --- | --- |
| Required/Optional ordinary conversion exactly once, custom direct message/level, runtime validation and non-DSL use | `AnnotationPropagationContractTest` paired controls; boxed Boolean guidance records primitive-boolean limitation |
| Query field/method shape, purity, category, visibility/static/result and Builder-state behavior | Paired failure and successful current-Builder/completed-Model controls |
| Cluster field/method conversion, removed owner field, getter/factory projection and generic/static/type restrictions | Paired contract and failure controls |
| Abstract, same-unit and precompiled inheritance; overrides and shadowing | Fresh-loader binary and source controls; inherited emitted behavior, ordinary hierarchy errors |
| Independently eligible nested/inner DSL classes, inert ordinary/mixed/interface sites and non-DSL-subclass errors | AP-3 eligibility matrix |
| Recursive public Builder/Factory/cluster signatures and hidden implementations | Paired namespace contract comparison and linkage checks |
| Java/static Groovy typed consumers and compiled source mirrors | `AnnotationPropagationContractTest`, plus its inherited-contract documentary example |
| Projections are output and do not expand again | Query/Validate/Cluster counts and mirror-domain retention controls |
| AutoCreate assignment, ownership, Templates and supplied-child precedence | `AnnotationPropagationLifecycleTest` paired controls |
| Direct-before-Cluster fallback; creation-before-mutation; callbacks and no catch-up replay | Same lifecycle suite; skipped mutations not replayed |
| Same-phase Creator conflicts; bare keyed/abstract AutoCreate runtime failures | Direct/propagated failures remain equivalent |
| No public API/serialization/runtime lifecycle change beyond equivalent direct behavior | AP-3 tests; AP-4 corrects existing protected Query hook linkage without signature/descriptor changes |

## AP-4 acceptance disposition

| Accepted item | Disposition / evidence |
| --- | --- |
| Actual Java 17 production artifact across Groovy 3/4/5; separately compiled Java/Groovy annotation libraries | `AnnotationPropagationQualificationTest`, explicit three-lane tasks; repeated and container spellings |
| Shipped KlumCast/provider activation and normal service/module visibility | Artifact descriptors/identities checked; no overlay, customizer or independent producer |
| Groovy 4/5 named compiler, foreign/library, Schema and typed consumer modules | Normal exports/qualified opens, no access-bypass flags/rewrites/split packages; Groovy 3 classpath-only |
| Foreign default-valued class/field/method local transforms | Separate artifacts, equivalent direct/propagated effects once; non-DSL aliases inert and direct uses unchanged |
| Runtime-only retained originals, physical copies and generated effects | Compiler/SPI/foreign implementation excluded, class-absence assertions; ordinary Groovy runtime retained |
| Changed requests and old-binary behavior | Replacement libraries; unchanged old Schema bytes/behavior; recompiled Schemas omit removed AutoCreate |
| Consistent compiler/annotation pairing and explicit-annotation cleanup | Exact development pairing recorded in AP-4 PR; duplicate negative migration control; no released minimum-version claim |
| Editor recognition separate from mirrors | Explicitly unqualified; no live IntelliJ acceptance supplied |
| Other AST rewriting and ordering | Collector limitation documented; concrete foreign success supplies no universal ordering promise |
| Query named-module linkage discovered at gate | Existing generated bridge calls now directly bound; direct/propagated and bytecode controls pass in merged AP-4 |

## AP-5 local documentation and documentary disposition

| Accepted item | Local disposition |
| --- | --- |
| Accurate AP-1–AP-4 user contract without ADR internals | Existing [propagation guide](../user/Annotation-Propagation.md) retained; new policy/creation and dependency/editor guidance added |
| Practical concrete relationships and retained DefaultValues | `AnnotationPropagationDocumentaryTest#'creates related services while retaining domain defaults'`: messaging/database/workload/api plus orderTopic/resultTopic; supplied topic keeps precedence |
| Optional defaults versus customized direct creation | `AnnotationPropagationDocumentaryTest#'keeps optional defaults and customized creation explicit'`; bare propagation never receives configured member values |
| Documentary policy | New dedicated Test class has class-level `@Issue('890')`, `@Tag('documentary')` and method-level absolute user-guide `@See` links; no ignored or pending cases |
| Earlier documentary coverage | AP-1 DefaultValues tracer, AP-2 request/container matrix and AP-3 inherited Query/Cluster example remain linked and current |
| Relevant user pages and release note | [Default Values](../user/Default-Values.md#creating-a-child-with-domain-defaults), [Model Phases](../user/Model-Phases.md#autocreate-10), [Migration](../user/Migration.md#composing-domain-annotations) and [CHANGES](../../CHANGES.md) synchronized |
| Navigation | Existing Annotation Propagation, Default Values, Model Phases and Migration sidebar entries remain correct; added section links provide navigation without adding/renaming a page |
| Scope distinctions | Generic propagation versus #888 class-level defaults, #548 source composition, arbitrary meta-annotation execution and ScHelm-owned provider policy made explicit |
| Actual ScHelm admission | **Pending**; local examples are framework documentation, not ScHelm execution evidence |
| Issue/release reconciliation | **Pending Hive decision** after consumer evidence; #890 remains OPEN, no release or curation mutation |

## Outstanding real consumer gate

The requested input is the just-published **development** coordinate `4.1.0-dev.435+ce897d5`, built from
master `ce897d5f7bd4aec2380f8369d12337fec30bf00a`. Maven Local is authorized only for that development
route. This ledger records the request, not an independently verified publication/resolve-back or run.
It does not establish public repository availability, a released minimum version or release qualification.

Hive must supply the actual ScHelm qualification result before AP-5 final acceptance. Evidence must
identify the ScHelm source revision, resolved KlumAST compiler/annotation/runtime coordinates and route,
Java/Groovy versions, commands/results and a durable or precise local evidence source. It must cover:

- OrderApplication `messaging`, `database`, `workload`, `api` concrete propagated relationships;
- OrderKafka `orderTopic`, `resultTopic` relationships;
- retained domain values reaching ScHelm's DefaultValues consumers, with normal supplied-value precedence;
- consistent admitted dependencies and cleanup of explicit annotations that now duplicate propagation; and
- the outcome/limits of the real consumer integration, including any remaining failure or required decision.

No evidence source, ScHelm revision, command/result or pass has yet been supplied to this AP-5 task.
KlumAST supplies generic propagation; ScHelm retains ownership of its annotation meanings, creation/bind
choice and provider-selection policy. No ScHelm code or policy is changed here. This worker has not
contacted ScHelm. A failed qualification returns to Hive for scope/decision; it does not authorize a
lifecycle, propagation or consumer-policy workaround.

## Resume and publication boundary

After Hive supplies the real consumer evidence, reconcile its exact versions, source and relationship/
DefaultValues results against this gate. Record failures and limits instead of converting a development
run into release qualification. Hive decides the final #890 relationship and release/curation impact. That tracker reconciliation must
also add the new documentary class/methods and matching user-guide sections to #890, preserving the
existing AP-1–AP-3 documentary cross-links.

When all accepted local and external conditions pass, refresh master and validate the final branch,
push the dedicated branch and open one draft PR against master. Verify final-head CI and SonarCloud
and address actionable findings additively after review. Keep #890 open until Hive verifies complete
acceptance and selects the tracker action. No merge or release is authorized by AP-5.

## Independent reviews

Fixed review range: `ce897d5f7bd4aec2380f8369d12337fec30bf00a...4b7287100d244b1cbdc8442e43e82f150bfc96e7`.
Both reviewers worked read-only; final validation/status additions are documentation-only.

### Standards

Zero findings. No documented-standard violations or material baseline smells. The documentary class
follows naming, `@Issue('890')`, the class-level tag, per-feature `@See` links and matching guide
references. User guidance, migration, changelog and ADR status are consistent with local preparation.
Pending ScHelm qualification, final validation reporting and issue/test cross-links are delivery conditions.

### Spec

Zero actionable findings. Practical examples, retained values, default-only selection, customized direct
creation, duplicate/one-hop boundaries, dependency/recompilation guidance and policy distinctions match
the authorized preparation. The ledger reconciles AP-1–AP-4 and records editor recognition as unqualified.
There is no implementation or consumer-policy scope expansion. Real ScHelm integration on the admitted
development versions remains deliberately pending; no premature acceptance, publication, closure or
release is claimed.

Summary: Standards 0; Spec 0. Neither axis has an outstanding actionable review finding.

## Local validation and delivery

The two new documentary examples pass on Java 17 / Groovy 3/4/5, zero failures/errors/skips.
The first full root attempt passes all compiler lanes (2035 / 2037 / 2037 cases, 15 existing skips
per lane) but fails 11 of 163 Gradle plugin cases. The worker created its review checkpoint during the
run: the outer build requested the earlier Git-derived version, while nested fixture publication used
the new HEAD's version. The failure is missing fixture BOM/artifact coordinates, not a product assertion.
Repository inspection confirms the two different versions. No build or product workaround is added.

The root and explicit artifact matrix will be rerun with HEAD and the worktree fixed for the complete
run. Final rendered-site verification and results remain pending at this checkpoint. Both independent
reviews pass; no AP-5 remote CI or SonarCloud result is claimed. Local checks cannot satisfy the external
consumer gate.

No production implementation, KlumCast, runtime lifecycle, public/generated API, build/publishing
configuration, ScHelm code, source composition or class-level-default policy is changed. No release/run
IDs or external evidence-store authorization were supplied; this ledger is the bounded repository record.
Tracker recommendation: retain #890 OPEN and `Related: #890`; Hive alone selects final closing syntax
and any release/curation update after all accepted evidence is reconciled. Retain the isolated worktree
while the consumer gate and delivery remain open; archive reconciliation is not requested as complete.
