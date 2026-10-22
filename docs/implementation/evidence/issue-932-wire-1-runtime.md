# WIRE-1 runtime qualification and publication handoff

Date: 2026-10-22. Status: **validated, committed implementation; unpublished; ready for Hive publication decision**.
Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Independent generator: [#529](https://github.com/klum-dsl/klum-ast/issues/529).

## Implemented outcome

The approved first slice implements `KlumStructuredInput.readRoot` and `applyToBuilder` in runtime, complete ordered
input preparation and exact recorded-signature MetaClass execution. It snapshots Lists/Maps, rejects cycles/null/unknown
names/invalid types before execution, converts only bounded standard numbers, and reads enum constant names from bytes
without initialization. Execution materializes enums and plain data containers only after complete preflight, invokes
the same selected MetaMethod, and shares ordinary root/apply/owned-child/keyed-map creators. No parser, mapper behavior,
public descriptor, cache, registry, synthetic Groovy Closure or session API was introduced.

Tests cover caller and nested/keyed/List order, all eight supported field container categories, exact primitive/wrapper
rename/ignore dispatch, inherited operations, one-payload Map/List calls, source/escaped pointer diagnostics, late validation
types, active/session/thread/Template guards, mutation-before-throw/no rollback, JVM Error propagation, root cleanup,
snapshots, enum/Schema no-initialization preflight and numeric bounds/non-finite/custom Number rejection. Documentary tests
link both facade routes to the [user page](../../user/Structured-Input.md). Actual Java/static Groovy named-module consumers
exercise public linkage. The Holder fixture exercises ordinary Default, AutoLink and LinkTo after caller LINK setup.

## Approved admission refinements

The runtime work exposed and resolved four bounded compiler facts: standard numeric targets only; generated element/keyed
population for the eight supported field container declarations, distinct from ordinary List/Map arguments; exclusion of
keyed owned-map `@Field.keyMapping`; and the precise compiler-known fixed direct-child key. The linked runtime plan and gate
evidence record the maintainer decisions and their separate commits.

[The fixed-key resolution](issue-932-wire-1-direct-key-stop.md#accepted-resolution) adds a required twelfth optional-key
token to each unreleased private carrier entry. The reader consumes that fact directly without runtime Cluster inference
or observable synthetic `@Field` metadata. The unchanged original #938 binary now rejects with recompile/package guidance;
its earlier successful-reader checkpoint is historical, not a current compatibility claim. No legacy handling was added.
Direct and separately compiled inherited fixed-key paths, strict malformed-role diagnostics and named-module access pass.

## Final validation

The focused structured-input matrix passes **125 cases per Groovy 3/4/5 lane**, zero failures/errors/skips. Actual named-module
features pass on Groovy 4/5. The current reader suite passes **17 cases per lane**, including fixed-key initialization traps,
damaged carriers, fresh JVMs and unchanged earlier-artifact recompile guidance. Module license and lane-isolation checks pass.

The final repository-wide command passed in **12m 10s**, with 136 actionable tasks (66 executed, 70 up-to-date):

```shell
./gradlew check --console=plain --no-daemon
```

| Module | Groovy 3 | Groovy 4 | Groovy 5 | Existing skips per lane |
| --- | ---: | ---: | ---: | --- |
| klum-ast | 2264 | 2266 | 2266 | 15 / 15 / 15 |
| klum-ast-runtime | 73 | 73 | 73 | 1 / 1 / 1 |
| klum-ast-jackson | 121 | 119 | 119 | 0 / 0 / 0 |
| klum-ast-bean-validation | 10 | 10 | 10 | 0 / 0 / 0 |
| klum-ast-test-support | 8 | 6 | 6 | 0 / 0 / 0 |
| klum-ast-gradle-plugin | 359 | n/a | n/a | 0 |

All listed suites have zero failures and errors. Root coverage reporting, plugin validation, license checks and lane
isolation pass. Existing Gradle deprecation and unknown license-extension warnings do not fail the build; no files or
configuration were changed to bypass them. The root log is `/private/tmp/wire-1-final-root-check.log` (local temporary evidence).
Changed Markdown fences, 31 added local link targets, and `git diff --check` pass.

## Review and history

Independent Standards and Specification reviews covered the complete assigned-base-to-`02eb522bc` implementation.
Standards found two documentation issues (stale ADR/plan status and missing adjacent documentary references); Specification
also identified the status issue, with no runtime/spec discrepancy or scope creep. Additive commit `8866d1634` addresses
all findings, and both reviewers verified that correction with no remaining findings. The final evidence update changes
only this Markdown file; it does not affect the validated sources or test execution.

History review preserves the focused reasoning sequence: numeric and container admission, strict reader qualification,
keyMapping exclusion, shared Consumer construction, fixed-key fact, complete runtime, then documentation corrections.
The decision-gate evidence is explicitly historical where superseded. No unrelated work is included or reviewed history
rewritten. Production code is pinned at `02eb522bc23fc7c57c729aa26b694d8b42bf380d`.

## Delivery boundary

Worktree: `/Users/stephan/.codex/worktrees/wire-1-runtime/klum-ast`.
Branch: `codex/932-wire-1-runtime`.
Assigned base: `5446379693117fd2f1790a8f974cfdc40f4c7170`; live upstream master still matches that base at handoff, so no rebase
was needed. The user's IDE-main checkout is unchanged. The temporary historical-fixture worktree is archived; its original
binary and provenance remain in the test resources.

This is completed execution of the approved first runtime slice, not full #932 or #529 delivery. Live issues remain open;
there is no branch PR, push, issue mutation or merge. Remote CI/SonarCloud checks remain unavailable until publication.
Tracker impact: **related issue** (#932; #529 stays independent). Curation impact: **bounded follow-up** recommended for Hive
to reconcile the implemented runtime acceptance status; no milestone, owner or release placement is changed here.

Under [the publication boundary](../../agents/pull-requests.md#worker-draft-pull-request-authorization), this cross-cutting
change stops at `(ready:PR)` for the Hive/maintainer publication decision and issue relationship selection. No archive-safe
state is claimed. The final callback carries the final documentation/evidence commit and requests reconciliation.
