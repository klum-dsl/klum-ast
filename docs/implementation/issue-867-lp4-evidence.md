# #867 LP-4: sealed policy and construction routes

Date: 2026-10-10. Base: current origin/master, merged LP-3 PR #873,
`d821e4198db1421894b8c9dcb60f76a14951325c`.
Authority: explicit maintainer LP-4 delegation. Related: #867; issue/release/curation placement unchanged.

## Delivered policy and public boundary

`LifecycleMutator.onSealed(): LifecycleMutator.SealedPolicy` is additive and defaults to FAIL.
The nested enum exposes only FAIL and SKIP. Both act before handler construction for a sealed target:
FAIL throws through the existing participant/handler/phase/original-field wrapper, preserving the rejection
cause; SKIP returns without invocation. Each mutator occurrence checks its own target/policy. Unsealed
Builders still invoke a fresh handler. LP-1 handler/context signatures, LP-2 composition/order, LP-3 phase
slots, generated Builder contracts and the annotations/runtime dependency direction are unchanged.
Creators have no sealed policy: checked assignment retains LINK/OPTIONAL_LINK/composition/ownership/session
rules. A mutator following a successful creator applies its own sealed policy. There is no caching,
unsealing, mutation interception, traversal, earlier-phase replay or domain-specific policy.

Test-first record: the first SKIP factory tracer failed in all four phases because the policy API was
absent; the localized annotation/dispatcher change made all four pass. Subsequent controls qualify the
existing paths without modifying their implementation. A polymorphic test fixture initially resolved its
class name through DELEGATE_ONLY; capturing the class before DSL execution repaired that test setup.

## Route and graph evidence

All new classes carry class-level @Issue('867').

| Seam | Observable evidence |
| --- | --- |
| Sealed FAIL, four phases | `LifecycleParticipantSealedTest`: original inherited declaring field, handler and phase context; exact unsealed-rejection cause; handler constructor cannot run; later lifecycle remains usable |
| Sealed SKIP, four phases | Documentary `skips completed LINK targets before constructing handlers in #phase`, @Tag documentary and @See [user guidance](../user/Model-Phases.md#sealed-participant-targets-lp-4); throwing constructors/invocations prove omission; completed subtype/aliases retain identity |
| Active SKIP, four phases | Same test class: concrete subtype with base-declared field, self LINK cycle and alias, both occurrences mutate, fresh handler state across fields and two root sessions |
| Creator assignment | Same test class: sealed wrappers from an existing LINK attach by exact identity through LINK/OPTIONAL_LINK, followed by SKIP; retained LP-1 controls reject fresh LINK and foreign-session results |
| Template definition/application, four phases | `LifecycleParticipantRoutesTest`: recipe values stay untouched; separate recipients receive exactly one mutation and fresh state/owner identity |
| FromMap roots, four phases | Same test class: root mapping receives one mutation and ordinary Owner assignment |
| Late creation after OWNER | AutoLink/Default/PostTree creators use existing AsBuilder.FromMap; immediate mutator reads original owning declaration/annotation in the active session and configures the child; AutoCreate flag remains false, proving no replay |
| Completed serialization | Same test class: ObjectOutputStream rejects every Builder/context/handler instance; round trip preserves mutation, Owner cycle and self LINK identity |
| Jackson routes, four phases | `LifecycleParticipantImportTest`: readRoot, ordinary ObjectMapper root, Template application, readBuilder and applyToBuilder each dispatch once; readTemplate stays value-only with a distinct recipient |

The test surfaces are root factories, Template APIs, Jackson import APIs and completed Model values;
no production Template/import/session/ownership/serialization implementation was changed. Existing sealed
mutation guards remain covered by `SealedBuilderMutationTest` and repository checks. Coverage is bounded to
these scenarios; it is not a comprehensive wire-format, container or all-consumer qualification claim.

## HANDLE feasibility outcome

**Deferred; no HANDLE API.** `LifecycleParticipantRoutesTest#'HANDLE probe exposes completed LINK typed reads and validation target gaps'`
uses an ordinary statically compiled AutoLink callback, keeping the actual sealed wrapper and existing
public support APIs. Dynamic `InvokerHelper.getProperty(wrapper, 'facts')` returns the completed Facts;
generated typed `wrapper.getFacts()` returns null. Reporting a warning through
`KlumSchemaSupport.klumValidationForObject(wrapper)` does not attach that warning to the already completed
Service. The completed target's values and LINK identity remain intact.

Source evidence: `InternalKlumBuilder.sealTo` forwards dynamic properties to the completed target, while
generated relationship getters use wrapper storage. `InternalKlumObjectSupport.getOrCreateValidationResult`
stores Builder reports in that Builder's metadata; completed LINK wrappers do not undergo a new owned
materialization/issue-transfer lifecycle. This agrees with the earlier [LP-1 completed-LINK evidence](issue-867-lp1-evidence.md#external-schelm-qualification-completed-link-reads).

Bypassing the guard would allow limited dynamic inspection, but would publish an inconsistent typed read
and target-validation contract. Repair would need a separately accepted completed-target exposure/reporting
contract or lifecycle changes, beyond a small dispatch extension. No framework, getter fix, validation
transfer change, unsealing or mutation privilege is introduced. Optional deferral does not block FAIL/SKIP.

## Validation and review

Focused baseline Groovy 3 factory, route and Jackson import checks passed. Final affected compatibility,
full repository checks, independent Standards/Specification review and exact-head remote CI/SonarCloud
results are recorded below once observed; focused success alone is not full acceptance.

## Remaining gates and delivery boundary

Optional LP-5 type mutation and LP-6 Closure helpers remain unqualified. LP-7 container support is optional,
but an explicit support-or-reject decision/diagnostics is mandatory before release. LP-8 complete errors,
existing-validation guidance, Java/static/dynamic/binary/JVM/JPMS compatibility, final names and release
qualification remain pending. HANDLE is deferred as above. No claim that #867 is complete and no issue,
label, milestone, curation or conditional 4.1 placement change.

Draft publication is explicitly requested with Related #867. Hive owns final delivery/release/archive
reconciliation; this worker requests reconciliation and does not self-archive. The isolated worktree is
retained while its PR is open. Migration and sidebar navigation already reach Model-Phases; this additive
policy needs no navigation or migration recipe change.
