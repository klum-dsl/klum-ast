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

## Minimal public Builder transport

Existing generated field/key `rename` and `ignored` slots carry the effective language facts. The existing generated
`Operation` annotation gains the same slots; exact source-authored operations no longer depend on annotation order when
consumed. Each declaration also emits `nameOrigin` (`IDENTITY`, `JSON_PROPERTY`, `INPUT_NAME`, `IGNORE`, `INPUT_IGNORE`).
This distinguishes source exclusion diagnostics and permits strict consistency/completeness checks. All three facts are
explicit, including empty/false values. Declaring `Schema.literalVersion=1` is independent of profile/numeric/decimal/
projection completeness. No descriptor duplication, companion, runtime overload, plugin selector or public metadata SPI
is added.

`CompiledBuilderSchema` reads only exact public Builder resources and reflected public signatures, verifies artifact
identity and validates literal versions/facts before constructing the shared interpreter/projector contract. Missing,
future, unknown, inconsistent, duplicate or wrongly encoded required facts fail with source/artifact recompile guidance.
Current raw Jackson annotations cannot overwrite compiler-folded facts. Complete legacy facts with no literal marker
retain the old field/key slots and authored-operation Jackson reader. New canonical operation annotations without their
required marker fail; arbitrary removal of every sign of new facts is not a compatibility promise. Older strict readers
reject the new marker rather than silently losing the language choice. Use matched tools and recompile affected Schemas.

The retained profile binary evidence established that fields/keys retain their existing exact name/exclusion slots and
that operations need explicit folding on their selected public declaration. The implementation adds no new transport
kind. A test-only byte comparison of the documentary public Builder measures **103 bytes** for the new marker/origin/
operation-slot facts on each G3/G4/G5 producer (4,364 / 4,364 / 4,383 total Builder bytes respectively). The first focused regression run passed before the new literal coverage. The bounded source-free fixtures then
qualify the actual new vocabulary rather than inferring support from annotation defaults.

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
  Map data/instance keys and operation tokens. Negative cases preserve previous output and execution traps. Complete legacy
  literal facts retain byte-for-byte schema output independently of naming-profile completeness.

The initial new core test run exposed a fixture setup error: the DSL-specific `createClass` helper expected a generated
Builder for the disposable Jackson annotation declarations. Using the existing Groovy loader for those annotation stubs
corrected the setup. No production behavior or assertion was weakened.

Only the relevant ADR checkpoint, current Structured Input/JSON Schema/Migration sections and changelog change. Existing
historical decision/probe records retain their dated deferrals. No new pages or navigation entries are needed. Package,
root/global/relationship profiles, JsonNaming, mapper adapters, examples, defaults, relationship selectors, new runtime
signatures and metadata SPI remain outside scope.

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
