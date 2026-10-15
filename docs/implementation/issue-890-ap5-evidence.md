# ADR 0029 AP-5 acceptance ledger and consumer gate

Date: 2026-10-15. Related: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md) and its
[complete implementation plan](adr-0029-default-only-annotation-propagation.md).
Base: master `ce897d5f7bd4aec2380f8369d12337fec30bf00a` (merged AP-4).
Branch: `codex/issue-890-ap5`.

## Acceptance status

AP-1 through AP-4 are merged and qualified as summarized below. AP-5 user guidance, documentary
coverage and local validation are complete. **The real ScHelm development-consumer gate is PASS**, on
maintainer-authorized evidence supplied through Hive on 2026-10-15. The accepted result and its limits
are recorded below; local examples are not substituted for that consumer run.

Draft-PR delivery and final-head CI/SonarCloud verification follow this admission. Issue #890 remains
open for Hive reconciliation. Development admission does not establish public release availability,
a released minimum version, merge approval or release approval.

| Slice | Verified merged delivery | Decisive record |
| --- | --- | --- |
| AP-1 | [PR #893](https://github.com/klum-dsl/klum-ast/pull/893), `9a298b9a355717dfab5fe311ae5473eaab4d79b5`; [SonarCloud follow-up #894](https://github.com/klum-dsl/klum-ast/pull/894), `944925e859e931129c4d6536b29ec3362ad02c92` | [RC.2 admission and qualification](issue-890-ap1-rc2-evidence.md) |
| AP-2 | [PR #897](https://github.com/klum-dsl/klum-ast/pull/897), `93e5a4529fff9685bc1aef01300fccd8613d4ddb` | [Contribution and diagnostic matrix](issue-890-ap2-evidence.md) |
| AP-3 | [PR #898](https://github.com/klum-dsl/klum-ast/pull/898), `d94cc44c0b5b1d28a7b34589c2abbc20cbbcbe20` | [Generated/inherited/lifecycle parity](issue-890-ap3-evidence.md) |
| AP-4 | [PR #900](https://github.com/klum-dsl/klum-ast/pull/900), `ce897d5f7bd4aec2380f8369d12337fec30bf00a` | [Actual-artifact matrix](issue-890-ap4-evidence.md); final-head [CI 38639133477](https://github.com/klum-dsl/klum-ast/actions/runs/38639133477) passed on `820d2867645abd7a01915fa1f10d3db6faee6df7`, including SonarCloud |
| AP-5 | Local preparation complete; real ScHelm development admission PASS | This ledger; draft delivery and final-head CI/SonarCloud verification pending |

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
| Actual ScHelm admission | **PASS**, maintainer-authorized Hive relay; exact versions, separate artifacts, six targets, 23 Model cases and offline runtime rendering below |
| Issue/release reconciliation | **Pending Hive decision** after draft delivery/final-head checks; #890 remains OPEN, no release or curation mutation |

## Real ScHelm development-consumer admission

Evidence source: maintainer-authorized ScHelm final consumer-side AP-4 admission PASS, relayed by Hive
to this AP-5 task on 2026-10-15. This is supplied external execution evidence; the KlumAST worker did
not rerun or contact ScHelm. Source identities:

- KlumAST `4.1.0-dev.435+ce897d5`, master `ce897d5f7bd4aec2380f8369d12337fec30bf00a`;
- ScHelm `1950b04`: exact dependency pin;
- ScHelm `757de3e`: static artifact qualification; and
- ScHelm `465e969`: runtime qualification.

The admitted route is **Maven Local for development only**. All resolved KlumAST BOM, AST, annotations
and runtime modules were exactly `4.1.0-dev.435+ce897d5`. No public repository resolve-back, released
4.1 availability, released minimum version or release/publication qualification follows from this run.
The relay supplies precise consumer commits and commands/results; it does not supply a separate report
URL or exact consumer Java/Groovy patch versions. Those are not inferred from the KlumAST-local matrix.
The maintainer explicitly admits this result as AP-5's real consumer gate with the stated limits.

| Consumer acceptance | Supplied result |
| --- | --- |
| Complete artifact-boundary workflow | `./qualify-artifact-boundaries.sh` completed end-to-end; every producer passed clean `check` and published to Maven Local |
| Isolated consumer resolution | `render-demo` passed clean `check --offline`; all four KlumAST module families resolved to the exact admitted version |
| Existing concrete Models and DefaultValues consumers | `OrderApplicationSpec`: 15; `OrderModelSpec`: 8; all 23 cases passed, zero skips/failures/errors; concrete deployment Model loaded |
| Concrete propagated relationships | Runtime physical AutoCreate retained on all six targets: OrderApplication `messaging`, `database`, `workload`, `api`; OrderKafka `orderTopic`, `resultTopic` |
| Separate annotation/Schema artifacts | Independently compiled ScHelm annotation JAR containing FactBinding, KafkaTopicDefaults, ResourceClassDefault and HttpEndpointDefaults consumed by a separately compiled Order Schema |
| Generated/static/module contracts | Generated contracts, source mirrors and JPMS descriptors passed in the consumer artifact qualification |
| Real runtime behavior | Both offline demos rendered expected test/prod ConfigMaps, including environment-specific Kafka bootstrap and PostgreSQL host values |
| Consumer defect result | No consumer behavior or diagnostic defect found in the exercised paths |

The concrete Models, retained annotations and expected rendered values supply the real consumer
DefaultValues/relationship evidence. Ordinary supplied-value precedence also remains guarded by the
KlumAST AP-1/AP-3 controls and AP-5 documentary tests; no additional consumer-specific precedence
fixture is inferred from the relay.

**Remaining limits:** negative diagnostic fixtures were not added or run in ScHelm. The successful
consumer workflow therefore does not qualify AP-2 rejection messages in ScHelm; those remain qualified
by KlumAST's AP-2 source/compiled-library/direct-control tests. IntelliJ/editor alias recognition remains
unqualified: compiled contracts and mirrors are not a live editor result. AnnotationCollector rewriting,
universal third-party transform ordering and the existing Required/primitive-boolean limitation retain
their documented boundaries.

KlumAST supplies generic propagation. ScHelm owns annotation meanings, creation/bind decisions and
provider-selection policy. This AP-5 branch changes no ScHelm code or policy.

## Publication and tracker boundary

The supplied external gate passes and local validation is complete. Refresh master, review the evidence
update and run applicable documentation checks before pushing this dedicated branch and opening one
draft PR against master. Final-head CI/SonarCloud must be inspected and actionable findings addressed.
No merge or release is authorized. Tracker relationship remains `Related: #890`; Hive owns closing
syntax, release/curation decisions and final issue closure.

After final-head checks pass, reconcile #890 with the AP-1–AP-5 evidence and the new documentary
class/methods and matching user-guide sections, preserving the existing AP-1–AP-3 cross-links.
The issue remains open while this draft PR and Hive delivery/reconciliation are pending.

## Independent reviews

Fixed review range: `ce897d5f7bd4aec2380f8369d12337fec30bf00a...4b7287100d244b1cbdc8442e43e82f150bfc96e7`.
Both reviewers worked read-only. Their original reports below predate the external consumer relay;
final validation/status additions and the new admission record are documentation-only.

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

Validation is complete on fixed revision `e6fb76027ff79055e4c751715693716164d3bd14`, with a clean
worktree throughout the final run. Zulu Java `17.0.3`; Groovy `3.0.25 / 4.0.32 / 5.0.6`; ordinary
public KlumCast `0.5.0-rc.2` resolution. All six executions of the two new documentary features pass,
with zero failures/errors/skips.

| Compiler lane | Cases | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 | 2035 | 0 | 15 |
| Groovy 4 | 2037 | 0 | 15 |
| Groovy 5 | 2037 | 0 | 15 |

All 163 Gradle plugin cases pass, together with runtime/auxiliary suites, lane isolation, license checks
and the versioned-renderer fixtures. All five explicit artifact scenarios pass (1 / 2 / 2 across Groovy
3 / 4 / 5), with zero failures/errors/skips. The local exact-source render and HTTP site crawl pass;
the renderer's default `4.0.0-tracer` label is only local review output, not a release/version claim.
Rendered propagation examples, migration links and policy table are present. All 95 local Markdown
links/anchors, documentary issue/tag/See/reference checks and `git diff --check` pass.

Reproduce as **separate** invocations with Java 17:

```shell
./gradlew check :klum-ast:annotationPropagationArtifactsGroovy3 \
  :klum-ast:annotationPropagationArtifactsGroovy4 \
  :klum-ast:annotationPropagationArtifactsGroovy5 --no-parallel --max-workers=2
./gradlew renderLocalDocumentation --no-parallel --max-workers=2
```

The complete fixed-revision test/matrix execution took 13m 28s. Its combined command ended only at
render task configuration: Gradle rejects undeclared output dependencies when `renderLocalDocumentation`
is co-scheduled with `check`. Separate rendering succeeds in 3s, and a standalone root/matrix confirmation
succeeds in 2s, reusing the completed matrix (123 up-to-date tasks). No build-configuration workaround
was introduced; the combined-task limitation is recorded for separate tooling follow-up.

Two earlier invocations are not final qualification: creating the review checkpoint during the first
root run changed the Git-derived version, so 11 plugin cases requested missing fixture BOM/artifact
coordinates while nested publication used the new HEAD. A seven-second attempt then supplied a tracer
version override that the renderer's default-configuration fixture intentionally rejects. Holding HEAD
and the worktree fixed, retaining the default tracer setting and using separate render/check invocations
produces the successful results above. No product changes were needed for these execution failures.

Standards and Spec reviews each report zero findings. Commit-history review preserves the reviewed
feature commit, then records reviews/stable-build reasoning and this final evidence-only handoff. No code,
test or user-guide change follows qualification; later edits record results and the supplied consumer
admission in this ledger and synchronize ADR/plan status. At the consumer-admission checkpoint no AP-5
branch was pushed or PR created. Final-head remote CI/SonarCloud verification remains required after
the authorized draft publication. The separate real ScHelm result above satisfies the external gate.

No production implementation, KlumCast, runtime lifecycle, public/generated API, build/publishing
configuration, ScHelm code, source composition or class-level-default policy is changed. No release/run
IDs or external evidence-store authorization were supplied; this ledger is the bounded repository record.
Tracker recommendation: retain #890 OPEN and `Related: #890` while the draft PR remains unmerged;
Hive alone selects final closing syntax and any release/curation update after all accepted evidence
and delivery are reconciled. Retain the isolated worktree while delivery remains open. Archive
reconciliation is not requested as complete.
