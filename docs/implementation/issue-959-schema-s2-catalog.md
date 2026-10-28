# #959 / SCHEMA-S2: closed built-in constraint catalog

Date: 2026-10-28. Authority: the maintainer's explicit S2 assignment after accepted ADR 0031 P1/P2 and merged S1.
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959); no issue closure or milestone change.

## Pinned execution and scope

- Exact base: `e7514c3fd37a3f1d056c8567ef5a22557a2bc11c`, merged S1 PR #983.
- Branch: `codex/959-schema-s2-constraint-catalog`; all work uses an isolated worktree, preserving IDE-main.
- Decision: [ADR 0031 P2](../adr/0031-structured-schema-input-artifacts.md#p2--keep-built-in-mappings-in-a-closed-internal-catalog).
- S1's [reader/resolver/result evidence](issue-959-schema-s1-contract.md) remains the contract-reading baseline.
- No new mappings, metadata/protocol/ABI, public types, modules, dependencies, package moves or task options.
  Size remains paused. Custom projectors/SPI/discovery, parameter facts/#971, Jackson, runtime validation,
  defaults/lifecycle evaluation and user-code execution are outside this slice.

## Baseline and responsibility boundary

Before editing, the exact base passes all **127** packaged JSON Schema cases without failures/errors/skips.
All **46** retained emitted schema fixtures are captured for byte comparison. Their canonical sorted path-to-SHA256
manifest digest is `c8e8ccd767d23430f9cf1bac7eb2c71789c5869cb84d37897dfd345a9552cdb3`, identical to S1's evidence.
Existing fixed expectations and independent precision/Pattern corpora are preserved, never regenerated.

`BuiltinConstraintCatalog` is package-private, stateless and plugin-only, beside the existing projector in the same
isolated consumer loader. Its closed annotation-identity selection dispatches to coherent numeric and Pattern families.
It consumes validated inert `ConstraintRule` facts and effective `Entry` role/raw type/source context plus the existing
group-redefinition fact. Integral eligibility explicitly matches the resolver's finite admitted integral types, rather
than inspecting a renderer-owned JSON map. It reads no bytes or annotation proxies and executes no Schema code.

The result contains ordered immutable numeric/Pattern assertion contributions or the existing unsupported reason.
Factories use immutable maps, including Pattern's nested typed ASCII guard, and the result copies its lists.
The existing generic numeric/subset fallback is retained for unrelated annotations as well as unsupported numeric forms,
so descriptions and diagnostics do not change. Decimal construction retains exact literals and the original contextual
failure. The finite Pattern recognizer is byte-identical to the base.

`JsonSchemaProjection` still owns policy routing and never publishes catalog assertions under TEXT_ONLY or OMIT.
Required fact validation remains in the reader before policy suppression. The renderer retains numeric keyword collision
handling and ordered `allOf`, machine fences, contradictory independent bounds, descriptions, explicit examples,
deprecation, source markers, use-site placement, `$defs`, keys/null/omission and deterministic serialization/diagnostics.
The catalog receives no mutable target schema and cannot alter another input use or shared definition.

No interpreter, compiler or reader dependency points toward the catalog. The only runtime-source edit is a Javadoc
correction naming the new mapping owner; the binary contract and generated metadata remain unchanged.
There is no new user-visible syntax or behavior, so no documentary feature, user-page, migration or changelog entry.

## Extraction-seam coverage

`BuiltinConstraintCatalogTest` adds **21** cases with class-level `@Issue('959')`:

- Selection of all existing numeric annotations, with integral/decimal eligibility, exclusive endpoints and exact
  beyond-double-precision/exponent/trailing-scale literals.
- Pattern selection with the unchanged ordered anchored assertion and typed printable-ASCII guard.
- Existing unsupported reasons for unknown/paused annotations, wrong types/sites, operation payloads, non-plain/grouped
  facts, keys/containers, flags, unsupported syntax and admission ceilings; site/context precedence is explicit.

The plugin intentionally retains its existing `testRuntimeOnly` runtime arrangement. Seam tests construct inert internal
facts dynamically, adding no dependency or synthetic Schema implementation. Existing packaged field-policy,
name/inheritance, source/use placement, conjunction, contradiction, malformed/suppressed metadata and no-execution tests
remain the behavioral oracle. Their expected schemas and outcomes are unchanged.
The existing G3/G4/G5 `PatternAdmissionControl` now also proves the plugin catalog class is absent while ordinary
interpreter admission still succeeds. This is an executable dependency boundary, not a source-text search alone.

Focused G3 catalog/recognizer/P2 semantics qualification passes **140** cases without failures/errors/skips.
Broader frozen-head qualification and independent reviews are recorded below.

## Delivery boundary

The assigned publication is a dedicated branch and draft PR with **Related: #959** only.
Tracker impact: related issue #959; curation impact **none**. This internal extraction changes no release gate,
dependency, owner, issue acceptance status or published release fact. Size/P3 remain separate decisions.
Local SonarCloud analysis/upload and manual workflow dispatch are excluded; normal draft-PR CI owns SonarCloud.
The worker retains its worktree for review/merge and requests Hive reconciliation; it does not self-archive.


## Frozen-head qualification and local review

Qualified head: `ca040b1d30bb0223e5a7a9487c1b437ae808d638`. JDK 17 root `check --no-parallel --max-workers=2` plus the explicit
`annotationPropagationArtifactsGroovy3`, `annotationPropagationArtifactsGroovy4` and
`annotationPropagationArtifactsGroovy5` tasks pass in **15m 53s**. No source, test, build or documentation file
changes during this run. Top-level Gradle JUnit reports record:

| Task / lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| annotationPropagationArtifactsGroovy3 | 1 | 0 / 0 | 0 |
| annotationPropagationArtifactsGroovy4 | 2 | 0 / 0 | 0 |
| annotationPropagationArtifactsGroovy5 | 2 | 0 / 0 | 0 |
| groovy4Tests | 2,756 | 0 / 0 | 16 |
| groovy5Tests | 2,756 | 0 / 0 | 16 |
| test | 3,410 | 0 / 0 | 17 |

The explicit propagation-artifact reports are separate from the ordinary lanes. All **127** packaged schema cases pass,
including G3/G4/G5 source-free producers and the executable interpreter-without-catalog control. All **632** plugin cases
and **336** focused core contract/input cases passed before the root check. Plugin validation, licenses, lane isolation,
generated/public-contract and JPMS suites, and documentation renderer/repository checks pass. No suppression is added.

Every retained schema is regenerated after the frozen commit and all **46** paths/hashes still match the exact-base
manifest above. The fixed contract/schema expectations, independent test resources and Pattern recognizer are unchanged.
The artifact audit finds the catalog only in the plugin JAR; runtime/compiler JARs contain neither it nor a compiled
reference to it.

Independent output qualification also passes:

- Exact-number validation: decimal **426** inputs / **3** metaschemas, integral **429** / **3**. Ten contradictory
  schemas remain valid Draft 2020-12, admit omission and reject the supplied-value contradiction corpus.
- Structural **195** input checks / **18** metaschemas; deprecation **96** / **6**, including advisory-removal parity.
- Retained hand-authored P2 corpus: **97** Draft 2020-12 and **108** ECMA checks.
- Pattern: **27,211** strings, **54,422** ECMA checks, **27,211** schema checks, **66** Java/P2/malformed controls,
  **75** neutrality/admission checks; three producer outputs agree. Positive controls reproduce the naive final-LF
  leak and bare-not nonstring rejection, while the typed guard closes the widening. Tools: JDK 17.0.3,
  Node 26.12.0, Python 3.9.6 and jsonschema 4.23.0.

Independent read-only Standards and Specification reviewers examine the exact base-to-`ca040b1d30bb0223e5a7a9487c1b437ae808d638` range.
**Standards: zero findings. Specification: zero findings.** No ordinary implementation children or additional build
processes are delegated. History review retains focused extraction/tests, architecture ownership, and this final
qualification record. Required production/test/build inputs at the evidence-only final head remain identical to the
qualified tree; only this Markdown evidence changes afterward. Markdown fences/relative-file links and
`git diff --check` are rerun before publication.

Delivery preflight independently verifies Git SSH feature-branch capability and GitHub CLI repository capability as
**authorized**; no credentials or scopes are recorded. Remote master still equals the exact base at preflight, so no
refresh/rebase is required. The final branch/commit and draft PR are reported in the task handoff. PR CI/Sonar,
review/merge and Hive reconciliation remain external; no remote passing result or merge is inferred from local checks.
