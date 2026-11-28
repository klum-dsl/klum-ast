# Narrow prose PR qualification

Related: [#1127](https://github.com/klum-dsl/klum-ast/issues/1127). D1 implementation, 2026-11-28.
This is a scoped PR exception. It supplies no product, generated-site, Javadoc, JUnit, Sonar or release proof.
The research identified PR #1116 as one eligible example; its existing hosted full job took 50m28s.
One historical control establishes neither typical savings nor the frequency of eligible work.

## Admission and activation

Only additions/modifications of regular mode `100644`, UTF-8, non-LFS prose are admitted, in these exact families:

```text
CONTEXT.md
docs/adr/[four digits]-[lowercase alphanumeric hyphenated name].md
docs/implementation/adr-[four digits]-[lowercase alphanumeric hyphenated name].md
```

ADR directories are flat. README, CHANGES, user documentation/navigation/images, evidence/fixtures, examples,
agent instructions, workflows, Gradle, source and resources select full qualification. Copies, renames, deletions,
mode/type changes, binary content, an empty diff and unknown input also select full. A final prose commit cannot hide
an earlier code change: classification compares the unique merge base with the complete head, using a NUL-delimited
raw Git diff with rename/copy detection and full history. No file-list API, broad path filter or partial list is used.

Admission additionally requires a same-repository PR into `master`, an admitted activity, exact full SHAs, and a current
synthetic merge commit whose two parents match the pinned live base/head. Admitted content in the merge must equal the
head. Git failure/warning, shallow or missing history, malformed output and routing-budget exhaustion select full.
Budgets are 500 records, 1 MiB raw diff, 256 KiB per old/new blob, 1 MiB total changed blob bytes, 4 MiB Git output,
and 30 seconds per Git process. These restrict routing, not supported product inputs.

The classifier and prose task execute from the committed PR base, checked out separately and moved outside the product
checkout before building. If that tooling is absent or older than the live base, select full. The initial implementation
PR therefore qualifies as full. No PR-owned classifier or Gradle task can authorize prose admission.

**The shortcut remains inactive unless effective protection requires `PR qualification` from GitHub Actions App
`15368`, with strict/up-to-date enforcement, and no other required check needs full qualification.** Classic protection
and effective branch rules are read without modifying either. Missing, unbound, loose, conflicting or unavailable
policy selects full. A read on 2026-11-28 found no required status-check rule on master, so current settings select full.
The maintainer owns any later policy activation and rollback; this change grants no settings authority.
There is no demonstrated hosted saving until eligible real PRs actually select prose after activation.

PR metadata and the live branch ref must agree on the base tip. Head/base/merge identity and protection are checked again
before success. A changed identity fails the result. Strict enforcement prevents an older successful result admitting a
PR after a later base update; updating the PR then reclassifies. This does not provide an atomic lock over later GitHub
changes. GitHub remains the enforcement authority.

## Executed checks and status semantics

[PR qualification](../../.github/workflows/pr-qualification.yml) has one executing job named `PR qualification`.
It handles `opened`, `synchronize`, `reopened`, and `edited` (including base retargets). Base-retarget events always select
full; other edits recompute the whole diff. Merge-group events always select full. Forks, `main` and release-target PRs
always select full, as do other base targets. PR triggering has no branch or path filters and no conditionally skipped `build` job masquerading as product success.

The prose lane runs policy fixtures, focused buildSrc checker tests and `verifyProseDocumentation`. The task checks:

- Whole merge-base-to-head `git diff --check`.
- Both exact head and merge trees, reading Git objects rather than working-copy text.
- CommonMark 0.28.0/GFM tables parsing, one H1 and a nonempty escaped HTML render for each changed page.
- Parsed inline/reference/image local targets, heading fragments using the existing renderer's Unicode/duplicate-ID
  rules, bounded `#Lx-Ly` source ranges, repository escapes and symlink targets.
- Incoming local links from tracked Markdown to changed pages, including removed headings.

External HTTP(S)/mailto links are syntax-only; availability and semantic accuracy are unverified. Code blocks and spans
stay inert; authored HTML is escaped. This is structural source/render/link validation, without screenshots, a generated
site, executable examples or a whole-repository link-health claim. User docs remain excluded because their actual
renderer/site verification selects Javadocs and compilation; do not omit those dependencies to expand admission.

The checker limits each Markdown document to 1 MiB, tracked paths to 20,000, Git output to 4 MiB per process and reads
to 64 MiB per revision, with 30-second Git timeouts. Exceeding a validation budget fails qualification. A failed docs
check never becomes green by being ignored or relabelled as full.

All other inputs run the existing complete Gradle/Sonar invocation, all three annotation-artifact lanes, genuine JUnit
reporting, compatibility evidence and landing-route tests. Full qualification also waits at most 40 reads, 15 seconds
apart, for the real successful exact-head Sonar check from App `12526`; it rejects missing, failed, ambiguous,
incompletely paginated or untrusted evidence. It reuses the release verifier's read-only GitHub pagination helper.
Selected checks must literally succeed: skipped, cancelled, failed and missing outcomes cannot pass the final step.
A classifier infrastructure failure selects actual full work, or qualification fails if it cannot execute.

[CI](../../.github/workflows/ci.yml) retains its exact `build` job and step sequence for master/main/release pushes and
manual dispatch. Only PR triggering moves to the distinct workflow. The unchanged release-selection contract still
requires the approved CI workflow's exact-SHA push run and authentic Sonar check. A PR, docs or merge-group run cannot
supply release proof. This implementation does not enable merge queue, change release policy, alter rulesets or change
issue state/milestone. Its issue relationship is non-closing; broader #1127 work remains open.

## Validation and comparison

Focused local commands (JDK 17):

```shell
node --test .github/scripts/pr-qualification.test.cjs
./gradlew -p buildSrc test --tests '*ProseDocumentationTest' --no-daemon --offline
./gradlew verifyProseDocumentation -PproseObjectDirectory=<full-history-repository> \
  -PproseClassificationFile=<classification-json> --no-daemon --offline
bash .github/scripts/test-write-documentation-landing.sh
git diff --check
```

The manifest is the classifier's JSON result: lane, pinned identity, mergeBase, complete records and reasons. A local
manifest is a checker input, never authoritative GitHub event or protection evidence. Regression fixtures cover the
allowlist, earlier code, advancing base, both revision identities, unsupported events, retargets, modes/types/content,
raw diff failures, policy trust, missing/skipped/failed outcomes and authentic Sonar selection. BuildSrc tests cover real
CommonMark, outgoing/incoming fragments, source ranges, escapes, unsupported URLs and exact-object reads.

Each hosted job writes the complete classification/identities and outcome map to its summary and the
`pr-qualification-measurement` JSON artifact, including run/attempt, classifier version, classification duration and
elapsed time since classification began. GitHub's native step timestamps show checkout, JDK/Gradle setup, checker,
product and reporting costs. The elapsed JSON field excludes earlier checkout/tool selection and final artifact upload;
use native job timestamps for job wall time, and Actions usage when available for billed runner minutes. Queue time is
separate. No secrets, external telemetry, usage API polling or synthetic hosted controls are added.

For the initial comparison, collect the first five naturally occurring eligible prose heads after activation and the
first five naturally occurring full controls; compare with the existing #1116 full run as a separately labelled historical
control. Report lane/fallback counts over the first ten PR qualification runs as well. Never trigger a run for sampling. Record runner/cache differences, retries, fallback reasons and the continuing post-merge full push cost.
Audit every admitted path/status against the recorded complete diff. Report counts and actual timings before estimating
savings; do not compare a warm local checker with hosted full-build time as measured net improvement. A policy-disabled
run measures full routing overhead and claims no avoided build. Keep measurements in Actions rather than committing
per-run logs. False admission, skipped selected work or release-proof regression requires disabling the prose exception
(removing its strict admission policy makes this classifier run full), followed by maintainer review.

The assigned delivery uses focused local evidence and hosted full PR qualification, per its explicit cost constraint;
it does not repeat root `check` or Groovy 3/4/5 locally. Local fixture success is not a hosted-result claim.

This policy is KlumAST-specific: the exact allowlist, existing renderer dependencies and protected release contract
remain here. No engineering-baseline change is part of this slice.
