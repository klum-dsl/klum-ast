# #959 D7: literal external names and explicit exclusion

Authority: the accepted [#975 naming decision](issue-959-d7-external-naming-decision.md) and the bounded literal-vocabulary
assignment after merged [PR #976](https://github.com/klum-dsl/klum-ast/pull/976).
Base: `bd39f085423dc747e7429dcd5ebd8fc656a78601`. Branch: `codex/959-literal-input-names`. Related: #959.

## Delivered behavior

`com.blackbuild.klum.ast.InputName` requires a nonempty exact String; `InputIgnore` is a marker. Both are documented,
runtime-retained FIELD/METHOD annotations. They shape eligible fields, selected construction keys and already admitted
Schema-authored Builder operations. They change no Groovy member names, runtime validation or managed Jackson import.

Resolve selected source/eligibility first. Validate Klum/Jackson explicit names before applying exclusion: equal nonempty
names coalesce, different names fail with both origins even when excluded, empty Jackson names retain fallback and empty
Klum names fail. Klum or true/default Jackson ignore excludes; false Jackson ignore cannot undo it. A compatible literal
wins over the inherited/local profile and identity; it is never transformed or installed as an alias.

The merged profile resolver already preserves native candidates before nonidentity collision pruning. This slice reuses
that behavior: a literal can separate field/operation/overload candidates; unresolved nonidentity collisions fail with
both source declarations. Existing identity collision behavior, inherited source identity and construction-key positions
remain intact. Excluding a key cannot remove required identity; external root keys and keyed owned Maps remain valid.
Owned types use their own naming policy. Data/instance keys remain literal.

## Current public Builder protocol

The [unified protocol follow-up](issue-959-unified-metadata-protocol.md) supersedes the original feature-specific markers
and older-binary compatibility in this PR. `GeneratedInput.Schema.version=1` is explicitly emitted on every declaring
Builder and is required by both consumers with all current facts. Older or incomplete contracts are rejected; recompile
participating Schema hierarchies and owned artifacts with a matching supported toolchain.

Existing field/key slots and authored-operation facts retain exact `rename`, `ignored` and `nameOrigin` values, including
empty/false values. Separate field/key and operation records remain necessary when declarations share one public
signature. Exact resource/artifact verification and public signatures are the only metadata authority; raw Jackson
annotations cannot overwrite or reconstruct the compiler's facts. Numeric assertions, projection, naming, key positions
and runtime validation retain their semantics. No metadata SPI or new authoring API is introduced.

## Evidence and scope

- `InputLiteralTest` (`@Issue('959')`) covers exact whitespace/punctuation, annotation contracts, bridge equality/conflicts
  including exclusion, empty-name rejection, inherited/profile/overload disambiguation, ignored keys and preflight before
  Builder mutation. Its documentary feature `choose exact external names and exclude internal configuration` links
  [Structured Input](../user/Structured-Input.md#literal-names-and-exclusion).
- `CompiledBuilderSchemaTest` adds separate API/base and derived JAR extraction after deleting compiler outputs. It
  checks exact names, source identity, ignored and unsupported declarations, key facts and no optional Jackson/compiler
  class loading. A static initializer trap has an independent positive control. Corrupt classfile cases cover literal
  version, field/key/operation name origin, rename/exclusion completeness and inconsistent exclusion.
- `KlumJsonSchemaGenerationTest` adds separate API/base, derived Schema and owned-value producers on G3/G4/G5, with sources,
  mirrors and compiler outputs absent from the consumer and no compiler/databind consumer dependency. The documentary
  feature `project literal names and exclusions from source-free artifacts with Groovy #generation` links
  [JSON Schema](../user/Json-Schema.md#literal-names-and-exclusion). Real interpreter acceptance is checked against exact
  closed-schema properties, inherited keys, owned independence, keyless ignored keys, runtime-name key fallback, aliases,
  Map data/instance keys and operation tokens. Negative cases preserve previous output and execution traps. Older/incomplete contracts now reject under the shared
  protocol; the former compatibility-success fixtures have been removed.

The initial new core test run exposed a fixture setup error: the DSL-specific `createClass` helper expected a generated
Builder for the disposable Jackson annotation declarations. Using the existing Groovy loader for those annotation stubs
corrected the setup. No production behavior or assertion was weakened.

Only the relevant ADR checkpoint, current Structured Input/JSON Schema/Migration sections and changelog change. Existing
historical decision/probe records retain their dated deferrals. No new pages or navigation entries are needed. Package,
root/global/relationship profiles, JsonNaming, mapper adapters, examples, defaults, relationship selectors, new runtime
signatures and metadata SPI remain outside scope.

## Historical qualification of the original literal slice

The records below belong to the original PR revisions before the unified-protocol follow-up. They retain the first-red
and correction audit; their former compatibility tests and counts are not the current protocol contract or qualification.
Current protocol rules and results are in the [follow-up evidence](issue-959-unified-metadata-protocol.md).

## Qualification and delivery boundary

Focused Java 17 qualification passed in 2m28s: **141 compiler/runtime cases per G3/G4/G5 lane**, plus **16 packaged literal
cases**, zero failures/errors/skips. This includes byte-for-byte legacy schema output across all three producer generations.
The first independent review found a P3 brace violation and a P2 shared-accessor regression. A source-authored Builder
getter can share a public method with a generated field accessor; treating both naming records as one record rejected a
previously valid identity Schema. The reviewer reproduced the failure with direct CompilationUnit compilation and
confirmed a legacy-fact control. The correction retains separate private name facts for field/key and operation sources,
including opposite exclusions and fallback-setter sharing; it adds direct interpreter and source-free regressions.
The initial root check was canceled (exit 130) after this substantive correction; it is not reported as a passing run.
Final root qualification and review confirmation are recorded below when complete. The final handoff records the
exact final commit, draft PR and live remote validation separately from local evidence. The repository's current Sonar
procedure requires local source hygiene and reserves authoritative coverage-bearing analysis for full PR CI; it prohibits
a fast pre-PR upload to the shared project. No clean Sonar result is inferred from local tests.

Tracker impact is **Related: #959**, with no issue/milestone/label or curation/release-placement change. No merge is
performed. Hive delivery/archive reconciliation is requested after worker delivery; the worker does not self-archive.


### Independent review after correction

Separate read-only Standards and Specification reviewers inspected `432f518fa220dec73209a2f595315cfa3241f9bd` against
the fixed base, then the additive correction `ef931453636133249647aa93d3ebac8728730d68`. The original Standards review found
one P3 unbraced else chain; Specification found the independently reproduced P2 shared-accessor regression described above.
Both reviewers confirm **zero remaining actionable findings** at the corrected revision. Reviewed commits are preserved;
the correction is an additive reasoning step. Focused Groovy 3 shared-accessor/source-isolation regressions pass.

All **130 relative Markdown targets/anchors** and balanced fences across the affected documentation pass, including the
renderer-style extensionless Templates link. Final diff/history review checks focused implementation, synchronized
documentation and the independently required source-isolation correction. GitHub CLI repository-delivery capability is
**authorized**; Git SSH delivery is independently checked at publication. No credential values/scopes or raw auth errors
are retained.


### Retained first completed root failure

The next Java 17 root check at `ef931453636133249647aa93d3ebac8728730d68` finished **failed** in 20m41s; this result is
retained, not replaced by later success. G4/G5 main-module subtotals were **2,731 / 2,731**, zero failures/errors, existing skips **16 / 16**.
The plugin task had two bounded failure causes:

- Eleven source-mirror cases requested clean-version BOM `4.2.0-dev.188+codex.959.literal.input.names.ef93145`, but the
  nested fixture publisher emitted only `4.2.0-dev.188.uncommitted+codex.959.literal.input.names.ef93145`. The worker edited
  this evidence document during the run, changing Nebula's version identity between outer and nested builds. This is an
  invalidated qualification input, not evidence of a product correction. Preserve the first XML/logs and keep the checkout
  unchanged throughout the controlled rerun; no version override, copied dependency, warmup or relaxed assertion is used.
- Three existing G3/G4/G5 deprecation-absence cases removed the whole generated Operation annotation. The reader correctly
  diagnosed missing required literal operation facts. The damage helper now removes only `sourceDeprecated`, preserving
  independent required language facts while retaining every existing structural/advisory assertion. Those historical compatibility-success fixtures are removed by the unified-protocol follow-up.

First-red XMLs, root output and fixture-version inventory are retained under `/tmp/959-literal-root-first-red` and the
adjacent `959-literal-root-fixture-versions.txt`. The controlled check runs the original first source-mirror feature and
all three existing deprecation-absence cases with a frozen checkout before final full-root qualification.


The frozen-input controlled check passed in **28s**: the original first mirror feature and all three advisory-deprecation
G3/G4/G5 cases, **4 cases, zero failures/errors/skips**. This changes no product code, assertion, version selection or
fixture publication procedure. Both independent review axes confirm **zero actionable findings** on the one-line damage-
helper adaptation. The final root run starts only after committing this stable state; no tracked edits occur during it.


### Final frozen qualification

Java 17 `./gradlew check --no-parallel --max-workers=2` passed at
`80fd1699d98a95ce4dfc86b1fb9b8c95e804d012` in **20m51s**, with a clean, unchanged checkout throughout the run.
All root-module test reports total **3,299 / 2,737 / 2,737 G3/G4/G5 cases**, zero failures/errors and existing skips
**17 / 16 / 16**. The compatibility totals include six test-support cases per lane in addition to the earlier main-module
subtotals. The plugin baseline has **540 cases**, including **all 109 packaged JSON Schema cases** and **all 11 source-mirror
cases**, with zero failures/errors/skips. Root documentation, licenses, plugin validation and lane isolation pass.

Final relative-target/anchor/fence verification passes **130 targets**; diff checks pass. The final follow-up changes only
this evidence document after qualification, leaving production/test sources and build configuration unchanged. The four implementation/
documentation/correction commits were reviewed for reasoning and scope; reviewed history is preserved. Draft publication
uses **Related: #959**. No product decision remains; remote PR CI/Sonar and later review/merge are separate delivery gates.
The final worker callback records the published head and PR identity, without implying a remote passing result in advance.
