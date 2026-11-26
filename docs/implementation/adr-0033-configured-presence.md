# ADR 0033 — decision and conditional tracer plan

Date: 2026-11-26. Supersedes the earlier seven-slice delivery roadmap.

[ADR 0033](../adr/0033-opt-in-configured-presence.md) compares A (broad native), B (restricted native), C (independent
improvements) and D (Schelm-managed presence). **Whether to implement native presence remains undecided.** This document
specifies decision work and a possible consumer tracer, not an approved implementation sequence. No tracer, production
code, executable test, release target, PR merge or issue closure is authorized. [#1111](https://github.com/klum-dsl/klum-ast/issues/1111)
remains the full consumer request; choosing B would leave some of it unsatisfied, requiring explicit scope reconciliation.

## Maintainer decision summary

- **Recommendation:** evaluate B with one bounded consumer tracer only if its restrictions are acceptable; retain D in
  production until a separate go decision. Do not pursue A by accumulation of exceptions. Consider C1 independently.
- **Expected benefit:** ordinary fields/generated operations could replace payload slots, markers, state-maintenance
  setters and presence-only list holders on Schelm's main snapshot path. Wire metadata, domain checks, number handling,
  JSON-tree freezing and serialization remain consumer code; immutable-tree envelopes remain unless C1 is separately solved.
- **Essential complexity:** whole-producer and per-declaring-field positive coverage, object-specific validity, successful
  operation entry (including empties), implicit/default separation, coherent payload/state copy and a neutral completed reader.
- **Avoidable in B:** per-operation runtime manifests, duplicate producer snapshots if presence is unavailable after stream
  reload, reason hierarchies, Builder readers, direct-store instrumentation, general escape analysis, Template/deferred
  origins and every overwrite family. These exclusions narrow useful support; they do not weaken known-state correctness.
- **Still undecided:** funding any native work; class opt-in/API spelling; exact supported/rejected shapes; default/reset/
  null/empty semantics; NULL-container snapshots; excluded transfer handling and stream limits. No release placement.
- **Maintenance:** a small reader does not make AST coverage cheap. Every future change to a promised generator/default/
  copy path must preserve or withhold its field guarantee across supported Groovy versions. Consumer savings must repay this.

## Evidence and current source

The complete issue and six Hive/maintainer comments were reread on 2026-11-26. The accepted boundary is conditional;
this task's maintainer clarification explicitly leaves the implement-or-not decision open. The following local disposable
reports are the only prototype evidence. Read with `git show <commit>:<path>`; do not merge, rebase, publish or copy their
experimental production changes. This design branch starts independently from master at
`d9bc6b5d27f8e7226ea38bbd12e6ca076f4b0e0e`.

| Proof | Local commit | Path under docs/implementation/evidence/ | Decision-relevant result |
| --- | --- | --- | --- |
| Feasibility | `3aad7474825bca1b38f36bd277cf26365835365e` | `issue-1111-presence-feasibility.md` | Companion storage works; runtime setters miss same-value writes/empties and confuse defaults; NULL-container copy skips payload replacement. |
| Mutation authority | `974e0403b88c334197a99f327a844bbe24ebaa9c` | `issue-1111-mutation-authority-prototype.md` | Bounded AST/entry/origin instrumentation works; wrappers fail raw/shared/stale alias semantics. |
| Capability/coverage | `3f46fc58f01762afec5b012999511f4a17409e08` | `issue-1111-capability-coverage-prototype.md` | Complete hierarchy plus declaring-field facts can fail closed; reflection still bypasses detection. Its descriptor/snapshot design is evidence, not the minimum possible mechanism. |

The last proof ran 63 cases in each Groovy 3/4/5 lane, including retained cases; counts are not additive independent proof.
Same-runtime serialization and JAR-only hierarchy cases passed for that experimental representation. No full CI/Sonar,
JPMS, actual historical binaries/streams, mixed-generation matrix or Schelm acceptance was established. In particular,
none proves the proposed field-level protocol or live-only retention design. Negative alias/reflection cases reproduce
false state outside the contract; they do not prove automatic downgrade.

Consumer assessment is pinned to Schelm revision `56e5ba6d6dfe2a6d4332814fb81e03a267c60c0e`, as in #1111, not a claim
about today's consumer branch. Inspected [TargetGenerator](https://github.com/klum-dsl/schelm/blob/56e5ba6d6dfe2a6d4332814fb81e03a267c60c0e/schelm-skeleton/schelm-core/kubernetes-targets/generator/src/main/java/com/blackbuild/schelm/targetgen/TargetGenerator.java)
emits slots, markers, custom operations, ALWAYS_REPLACE and Items holders;
[TargetValues](https://github.com/klum-dsl/schelm/blob/56e5ba6d6dfe2a6d4332814fb81e03a267c60c0e/schelm-skeleton/schelm-core/kubernetes-targets/support/src/main/java/com/blackbuild/schelm/targets/TargetValues.java)
uses JsonTree envelopes for nested immutable containers;
[TargetWire](https://github.com/klum-dsl/schelm/blob/56e5ba6d6dfe2a6d4332814fb81e03a267c60c0e/schelm-skeleton/schelm-core/kubernetes-targets/support/src/main/java/com/blackbuild/schelm/targets/TargetWire.java)
reads marker getters and separately owns field metadata, validation and output. These are concrete savings targets,
not all removable framework workarounds. The generator does not materialize source defaults or invoke Templates here.

Current KlumAST seams inspected: CopyHandler selects replacement/retention/combination directly and reconstructs nested
containers by runtime-class constructor; InternalKlumBuilder stores/transfers ordinary companion metadata; Templates
capture a separate immutable recipe; DefaultPhase uses effective-value emptiness; Struct can execute internal child
paths without public creator dispatch. WriteAccessMethodsMover retargets custom direct writes but supplies no native
presence guarantee. KlumObjectSupport is the established completed-Model entrypoint. Their behavior explains the costs;
no production source changes belong to this assessment.

## Minimum mechanism and copy decisions to review

Prefer one closed protocol version per producer layer with original declaring-field coverage, granted only if the whole
admitted operation family is covered. Class-wide opt-in alone is insufficient. Custom overrides/unsupported shapes can
withhold a field, and a missing hierarchy layer withholds all claims. Internal generator call-site assertions replace
per-operation runtime negotiation; adding one new supported operation still requires qualification or a new contract.
Object birth validity/state must remain distinct from class capability. Missing state storage is unavailable, never ABSENT.

The proposed B limit of unavailable inspection after Java deserialization makes full retained producer snapshots unnecessary
in principle: live birth/loaded-class correspondence is used, and reload loses authoritative validity. It must be tested,
not assumed, and must preserve existing Model serializability. If a consumer requires stream-retained presence, reopen only
that cost/benefit decision. Existing directional ABI commitments still apply; do not add a historical-stream bridge or
mixed-producer support matrix speculatively. No public descriptor or new evidence subsystem is planned.

Use CopyHandler's semantic branches, not equality/diffs, to choose presence. The ADR's five-rule table covers replace,
retain, combine, reset and unavailable evidence. For an eventual configured-values overlay, a **proposal** is: donor ABSENT
contributes no authored event; accepted donor VALUE configuration establishes parent VALUE even when an empty merge inserts
nothing; NULL needs an explicit replace/reject rule; unknown necessary donor/receiver contributions remain unavailable.
This would affect existing effective-value copy choices and needs separate approval. B should withhold combined paths rather
than implement a second policy engine. No generic OR of states proves the resulting payload's validity.

Exceptions requiring focused assertions are NULL container donors (currently skipped before dispatch), NULL receiver storage,
owned nested MERGE (parent retained but children changed), no-op/empty contribution and partial failure. A successful child
transfer cannot certify its parent's configuration; vice versa, a configured parent does not certify every child. Replacing
from an untracked/partial donor stays unavailable. Map/data imports have no retained native history. Preserve completed-donor
isolation and normal composition/LINK identity. The immutable-container allocation issue is C1, independently useful even
under D. Acceptance tables express guarantees; they are not instructions to enumerate all state × shape × strategy cells.

## Stage 0 — choose whether to fund a tracer

Before any new executable work, the maintainer chooses C/D now or explicitly authorizes a bounded B tracer. If authorizing,
record provisional choices for its class opt-in/direct Optional reader, generated-operation-only contract, implicit defaults,
reset/null/empty, NULL snapshot and live-only inspection. State exclusions up front: no custom direct stores/overrides,
mutating callbacks/participants/custom phases, arbitrary initializer helpers, deferred actions, Template replay, combined
copy policies or new Struct/Jackson null grammar. Recommend standalone nongeneric target Schemas initially; inherited or
special/shadowed fields remain unavailable unless qualified. Whole-hierarchy checks remain mandatory even for this narrow
admission. Supported custom Builder helpers may delegate to qualified generated methods only.

Detected excluded declarations should reject or withhold coverage before construction. Known runtime entry into an excluded
transfer/scheduling route must make availability conservative; arbitrary external bypasses remain unsupported without a
promise of detection. Ordinary payload behavior outside the subset is preserved, but a serializer must refuse unavailable
state. Select reject versus unavailable policy explicitly so the tracer is not green merely because all fields are unknown.
This stage authorizes nothing by itself and creates no follow-up issue/task.

## Stage 1 — smallest meaningful consumer tracer, only if separately authorized

Use one source-neutral target Schema family (for example ServicePlan and owned Endpoint) with a defaulted Integer,
Boolean, nullable String, owned child, List of owned children and Map. It should use ordinary fields and generated public
Builders, not consumer slots/markers or Items holders. Construct within one ordinary lifecycle. The public proposal is
Optional<ConfiguredPresence> on KlumObjectSupport; new spelling stays disposable until the consumer decision.

The **serialization fixture comes first**, rather than after a broad compiler implementation: a separately compiled Java
reader emits a plain Map/List/null tree using the public presence query plus ordinary getters. It omits ABSENT, emits NULL,
retains zero/false/empty String/List/Map/object, and rejects Optional.empty(). This means consumer-output serialization,
not Java ObjectOutputStream. It contains no Kubernetes/GVK/YAML/parser/schema-validation infrastructure. Compare parsed
value trees against the corresponding Schelm-shaped cases, including a nullable fixture independent of real non-nullable
resource definitions. Wire-name mapping and nullability/requiredness stay with the fixture consumer.

Keep the vertical bullet to generated operations, neutral completed reads, one implicit scalar default, reset/reconfigure
and an ALWAYS_REPLACE snapshot from a completed donor over a populated receiver. Include one supported delegating custom
helper and reject/withhold a direct-store helper; no production direct-write instrumentation. Treat NULL containers as
an explicit success-or-unavailable decision, never a NULL marker over retained receiver payload. Exercise donor isolation
with ordinary owned containers; keep the nested immutable JSON-tree limitation visible rather than solving C1 inside B.

Reasoned commit boundaries for that possible local disposable tracer: (1) minimal compiler field guarantee, successful
operation/state retention and completed reader with the source-neutral fixture green together; (2) explicit/default/reset/
empty/snapshot exceptions and untracked/unavailable controls, retaining the same fixture; (3) a concise consumer saving and
stop/go report. Tests accompany working changes, never committed red alone. Do not copy prototype production code or create
another general metadata format. Stop if the first boundary already demands the machinery excluded from B.

Proportionate acceptance groups, reusable across a few combined scenarios:

| Guarantee | Evidence needed before the decision |
| --- | --- |
| Useful output and simplification | Known A/N/V produce the expected trees; false/zero/empty survive; the same tree needs no slots/markers/maintenance setters/Items holders. Identify exactly what consumer code remains. |
| Explicit versus implicit | Untouched defaulted field stays ABSENT with its effective default; explicit NULL/zero/empty suppress defaults; reset clears stale payload and is eligible only at the normal default step. Assert getter and state together. |
| Correct mutation boundary | Empty bulk/block entry counts; failed assignment stays neutral; earlier successful partial effects stay coherent; reads do nothing. Excluded direct stores/known transfers cannot yield a false known result. |
| Snapshot and unknown history | A/N/V(empty) and selected nullable cases replace a populated receiver coherently; untracked/partial donor and producer evidence remain unavailable; donor mutation isolation holds. |
| Small coverage mechanism | Missing/unknown producer, field and object validity, ambiguous names and a mixed hierarchy control fail closed. Raw alias/reflection counterexamples remain outside the supported contract, with no detection claim. |
| Real client surface | Separate Schema JAR, Java 17 reader plus Java 21 consumer use, static/dynamic Groovy 3/4/5 narrow fixtures. Recompile each Groovy lane; no marker-name/private-field dependency. |
| Honest exclusions | Template/deferred/combined-copy/import routes are rejected or unavailable; Java stream reload returns unavailable while ordinary serialization remains viable. No current user behavior silently broadens. |

This is a proposed future feedback loop, not new tests in this PR. Use existing Spock/artifact fixtures, `Test` suffix,
@Issue("1111") and actionable suppression reasons. The boundary is compiler-sensitive, so all three focused Groovy lanes
are necessary; a universal history/stream/strategy Cartesian matrix is not. Existing coverage of excluded features is
regression evidence, not a requirement to make their native presence work.

## Early stop/go decision

At the end of Stage 1, a named maintainer chooses a result; no automatic continuation:

- **Proceed with B** only if ordinary target construction and snapshots produce the expected trees through the public
  reader, remove all four targeted representations (slots, markers, state-maintenance setters, presence-only Items holders)
  in that fixture, preserve donor isolation, and keep false/unknown claims out with the small field-level mechanism.
  Review actual changed seams/remaining consumer code and a bounded maintenance estimate before authorizing production work.
- **Narrow once** if one separable capability, such as nullable container snapshot or Struct entry, dominates cost and
  Schelm explicitly accepts retaining its workaround there. Record which #1111 cases remain unmet; do not rename unavailable
  support as success. Reject a hybrid that duplicates most state machinery and adds a native dependency for little benefit.
- **Abandon native presence and choose C/D** if correct ABSENT needs detailed runtime operation negotiation, duplicated
  snapshots, broad AST/escape analysis, arbitrary lifecycle/Template support or another payload; if common generated target
  paths are unavailable; if list holders/markers must mostly remain; or if the consumer needs excluded stream/merge features
  such that B becomes A. A false known state within the promised subset is a hard stop until narrowed or corrected.

The three earlier proofs answer feasibility; this decision must answer value versus ownership cost. No further rounds of
coverage expansion are presumed. Discarding the tracer and leaving #1111 unimplemented is an acceptable outcome.

## Conditional work after a separate go decision

If B is selected, replace the seven-slice roadmap with two independently reviewable production steps, each requiring its
own assignment: **P1**, the accepted generated/native snapshot path with the consumer fixture and documentary coverage;
**P2**, release qualification/documentation for that exact scope. Defer A's direct-write families, Template/deferred origins,
combined policies and stream retention as unaccepted opportunities, not a promised backlog or prerequisite to B.

P1 depends on the tracer decision and finalized API/semantic restrictions. Split compiler/reader and default/reset/copy
work only at green vertical boundaries with accurate field guarantees; no unsupported path receives a broad covered flag.
P2 depends on P1: affected module suites and root check under the cross-module risk matrix, Java/static/dynamic Groovy
artifact consumers, and one minimal Groovy 4/5 named-module scenario using standard exports/opens and runtime.generated
linkage. Groovy 3 remains classpath-only. Preserve existing directional generated ABI with representative older untracked
binary behavior and a frozen new reader/schema pair; additional historic/mixed-version/stream matrices need actual promises.
No --add-exports/add-opens/add-reads workaround or internal companion descriptor is admissible.

Re-use core acceptance examples instead of repeating every combination in every lane. Add TestKit/mirror checks only if
chosen generated contracts affect them. Inspect exact-head CI/Sonar and address findings. A shipped API needs
@Tag("documentary"), @See to matching docs/user examples, issue/test/page traceability, migration/sidebar updates where
needed, and CHANGES in a separately chosen release section. Public guides must explain unsupported raw/custom writes and
unavailable handling. No public docs or changelog claim delivery from this assessment.

## What remains of #1111

B is deliberately narrower than the original request. The issue's eight acceptance criteria reconcile as follows:

| Criterion | Early evidence / remaining gap |
| --- | --- |
| 1. Compiled Schema and Java/Groovy reader without own markers | Stage 1's main success criterion; P2 qualifies the exact published subset. |
| 2. A/N/V, nullable/empty/reset/reconfigure, neutral reads | Stage 1 core; omitted nullable-container cases must be explicitly accepted as gaps. |
| 3. Generated ownership/collections, no presence-only Items holders | Prove in Stage 1, not at a distant final consumer step. |
| 4. Snapshot plus coherent merge/append/missing-only | Snapshot in Stage 1; combined policies remain unavailable in B, so full criterion is unmet. |
| 5. Donor isolation and nested immutable value trees | Ordinary donor isolation in Stage 1; C1/envelopes remain separate and explicitly reported. |
| 6. Defaults/initializers/auto-created/lifecycle semantics | Restricted defaults/reset in Stage 1; arbitrary auto-create/callback/deferred behavior is excluded, so full criterion is unmet. |
| 7. Actual Deployment/KafkaTopic/nullable output and original-schema validation | Bring source-neutral shaped output into Stage 1; real pinned Schelm acceptance must precede any adopter/full-issue claim. No Kubernetes code enters KlumAST. |
| 8. Existing behavior/public compatibility, unchanged Struct null admission | Required throughout; P2 qualifies release scope, not new historic guarantees. |

Neither B nor merging this design completes #1111. The Hive must reconcile any selected scope reduction with the maintainer;
no issue retargeting/closure is performed here. C1/C2/C3 are assessed independently, without tracker creation or code changes.

## Assessment validation and handoff

This refinement changes only the ADR/plan and reduces the former delivery machinery. Validate Markdown structure,
relative links/anchors, whitespace/diff and scope; use independent Standards and Specification reviews against the pasted
2026-11-26 request. No production code, prototypes, executable tests or added evidence infrastructure. Groovy/build/local
Sonar lanes are unnecessary for this documentation-only revision; report hosted checks on its exact head separately.
Earlier reviews/green hosted checks at 69b92bc9c qualify that old design revision only.

Preserve reviewed commits and publish additive follow-ups on draft PR #1112, updating its title/body to this assessment's
scope. Post one consolidated response accounting for the request, intentional exclusions and current validation. Report
the recommendation, unresolved choices and stop/go criteria to Hive and maintainer; request reconciliation rather than
self-archiving. Publication or merge of decision-support documents does not authorize any implementation.
