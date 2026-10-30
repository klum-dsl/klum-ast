# #981 C1 — exact compiled converter String input

C1 implements the approved finite-Pattern slice of the
[converter projection plan](issue-981-converter-projection-plan.md). C2 remains deferred; no memory-regex extension,
runtime parameter enforcement (#971), general converter discovery or release-placement change is included.

## Implementation and review boundary

Base: `ee1b9a65ab064401f4aff3297f8bec755bcbacd8` (verified `origin/master`, 2026-10-30).
Branch: `codex/issue-981-c1-converter-projection`. Tracker impact: **Related: #981**, which remains open; no milestone,
label, curation or release-gate update is needed for this partial implementation slice. Hive owns final reconciliation.

The compiler retains every attempted raw String route, including signature collisions, paired with its exact generated
method. It admits only one eligible separately compiled declared domain signature and enriches that exact public Builder
overload. Getter-side zero/one completeness is mandatory even where no converter is admitted. Public call signatures
and development marker 1 remain unchanged; incomplete artifacts require recompilation with matched tools.

The resolver validates both sides before exclusion/policy suppression. Its Java selected-parameter reader resolves the
known signature without initialization, verifies artifact resources, and decodes only the closed Pattern/policy vocabulary.
It validates the known Jakarta API/default contract and referenced member types. No annotation proxy, custom hierarchy,
hidden implementation or domain code is used. Successful immutable facts are cached within one resolver session.
Field and parameter constraints have separate profiles and attribution. Domain-value field constraints remain descriptive;
the existing finite Pattern recognizer and field projection behavior are unchanged.

## Qualification

- `CompiledConverterInputTest`: separately compiled Java/Groovy constructor/fromString/parse JARs, source/class removal,
  inherited Schema JARs, descriptor/index/artifact identity, collector equivalence, policies, excluded malformed facts,
  absence/stripping, unsupported groups/payload/flags/repeats, API/member/linkage/corrupt/shadow failures, ambiguity,
  overloaded/generic/varargs/disabled/default-generated/bridge/synthetic routes, inherited/external factory ownership,
  same-compilation exclusion, authored collisions, annotation discovery, named-module access without opens,
  dynamic-Groovy negative control, explicit initialization controls and real structured-input/no-coercion behavior.
- `CompiledConverterJsonSchemaTest`: actual packaged plugin with G3/G4/G5 producer artifacts; effective member placement,
  unchanged finite assertion/guard, parameter/field policy separation, field-domain constraint separation, inheritance,
  names/exclusion, docs/examples/deprecation, absent/unsupported Pattern descriptions and zero initialization/default/
  lifecycle/validator execution. Changed domain plus Schema recompilation is checked against unchanged output/manifest
  on failure, using a corrupt domain reachable only through an omitted child; successful unchanged inputs retain Gradle
  up-to-date behavior.
- Independent `validate_converter_schema.py`: 28 JDK full-match checks plus 270 ECMA plain/Unicode and Draft 2020-12
  actual-output checks across G3/G4/G5 pass. Corpus includes line terminators, Unicode digits, supplementary/lone surrogates,
  empty/long inputs, literal escaping and structural null/non-String rejection. This reuses the existing finite language;
  it is not qualification for C2. Runtime: JDK 17, Node v26.12.0, jsonschema 4.23.0.
- Qualified code revision: `72caabd0d588a937f6fcb84a65b65279acbd12aa`. On JDK 17, the unchanged-revision full
  `./gradlew check --no-parallel --max-workers=2` passed in 29m 18s: 140 actionable tasks, 80 executed and 60 up-to-date.
  This includes packaged-plugin tests, test-lane isolation, plugin validation, license checks and the versioned
  documentation renderer. Existing generated-contract and JPMS controls are included in the module test suites.
- JUnit report totals: baseline `test` 3,505 tests (17 skipped); `groovy4Tests` 2,835 (16 skipped); `groovy5Tests`
  2,835 (16 skipped). Every lane has zero failures and errors. The plugin suite has 648 tests with no skips;
  the new converter runtime suite has 75 cases per lane and the packaged converter matrix has four cases, all passing.
  Existing skips remain unchanged. Focused converter and existing compiled-Builder reader checks also pass.
- The independent oracle passed against the final generated schemas; their SHA-256 values remained unchanged at
  full-check completion. Markdown fences, 169 relative file links, seven introduced heading anchors and
  `git diff --check` pass.
- Independent Standards and Specification reviews report zero remaining findings at the qualified code revision.
  The subsequent evidence-only commit `de30eadda` did not change source, fixtures or build configuration.

No local SonarCloud upload or manual workflow dispatch is used. Ordinary PR CI remains authoritative. Artifact locations
are diagnostic associations, not integrity or stale-JAR proof. Matching producer/Schema recompilation is required.

## Independent review corrections

The initial Standards review of `1d80a0c10` found one fixture-import violation; the source fragment now imports
AnnotationCollector. Specification review found three gaps: malformed duplicate/invisible closed annotations, missing
Pattern.List API preflight, and incomplete negative/omitted-child coverage. Each has an explicit regression. A synthetic
binary method control additionally exposed Groovy's separate AST synthetic flag; eligibility now checks JVM access bits.
The first root-check attempt crossed the local commit/version boundary and was deliberately stopped; it supplies no
full-check success claim. The final full check uses the stable post-review code revision.

A bounded follow-up specification check confirmed that all three existing group-redefinition marker identities belong
to the conservative exclusion: Jakarta GroupSequence, legacy javax GroupSequence and Hibernate GroupSequenceProvider.
The reader checks only those identities on the selected domain declaration, without loading providers or traversing
annotation hierarchies. New binary marker controls and emitted-schema coverage pass. The pre-correction full-check run
was stopped; final qualification follows this correction on an unchanged commit.

A final diagnostic review identified the early Builder signature-resolution boundary: a missing domain could throw before
the selected-parameter wrapper supplied field/external-name/artifact/signature context. The resolver now reports already
validated compiler-recorded converter candidates, preserves the original cause, and handles explicit and profile-derived
names. It never guesses which candidate caused linkage failure. Both missing and shadow-domain controls assert the full
context. Interrupted pre-correction full checks are not used as final qualification evidence.

Deprecated converter controls retain otherwise valid constructor/factory routes: ASM's attribute-derived ACC_DEPRECATED
pseudo flag is masked before comparing true method access bits with reflection. Bridge/synthetic exclusions remain
unchanged. Actual-output coverage verifies converter deprecation does not become field deprecation.

## Delivery boundary

The dedicated branch contains only C1. Reviewed commits are preserved, with additive review corrections. The combined
initial compiler/resolver/projection commit is one green end-to-end slice; it avoids introducing an unused temporary
abstraction between the first two planned steps. Later documentation and review corrections remain separate commits.

Delivery-channel audit: GitHub CLI repository delivery **authorized**; SSH dedicated-branch push **authorized**.
Draft publication uses `Related: #981`. This record establishes local qualification, not remote CI/SonarCloud or merge
success. Ordinary PR checks must be inspected at the published head. C2, #971 runtime enforcement, issue disposition,
release placement and final Hive reconciliation remain outside this implementation slice. The worktree is retained;
this worker does not self-archive.

## PR #991 dispatch-equivalence follow-up

The bounded review at `de30eadda` reproduced a binary descendant overriding the selected public Builder `port(String)`
method under a different external input name. The inherited entry still described the parent converter while structured
construction executed the authored override (`authored:1234` on Groovy 3, 4 and 5). The original metadata/initialization
matrix did not positively exercise factory construction, so its earlier success did not establish this invariant.

The resolver now rejects an inherited converter entry when a descendant public Builder declaration has the same name
and parameter types. It preserves validation of the parent facts before exclusion, ignores external renaming/suppression
when detecting the override, and propagates the exclusion through further descendants and naming profiles. This is an
ordinary rejection, not a name-collision record; an inherited construction key can reuse the excluded spelling. Normal
Builder dispatch, converter admission and C2 remain unchanged. User documentation and CHANGES describe the boundary.

New positive controls cover Java/Groovy binaries, constructor/fromString/parse, and direct/non-overridden inherited
Schemas. They compare normal Builder and structured construction using route-specific values, distinguishing the selected
factory from a disabled peer and direct construction. Eight override controls cover identity/kebab profiles, renamed/
ignored methods and children/grandchildren; they prove rejection before execution and unchanged ordinary override calls.
An inherited-key control prevents false collision handling. The packaged plugin verifies that an ignored binary override
removes the inherited converter property from the closed generated schema without executing user code.

Final follow-up validation on JDK 17 passed: runtime `check` and bean-validation `check` (G3/G4/G5), the five-case packaged
converter plugin suite, plugin license/validation tasks and the versioned documentation renderer. Runtime reports 73 tests
per lane with one unchanged skip; bean-validation reports 147 per lane, including all 96 converter cases, with no skips.
There are zero failures/errors. The independent actual-output oracle again passed 28 JDK and 270 ECMA/Draft checks.
`git diff --check` passes. Both independent review axes report zero remaining findings after their corrections.
The earlier whole-repository check above remains evidence for its stated revision; this follow-up reran the affected
module suites and packaged integration controls rather than claiming a new whole-repository run. Remote CI/SonarCloud
remain authoritative for the published follow-up head. No merge or issue-state change is authorized by this follow-up.

## PR #991 readability and SonarCloud follow-up

The review of `f9ae4f76` found 37 open maintainability findings despite successful SonarCloud gates: four cognitive
complexity findings, 20 Groovy branch-format findings, eight duplicated contract literals, one nested test mutation,
one nested ternary, one restricted identifier and two findings for the same unused local variable.

Code revision `38d80fb395531d5960201cd74fe83737b9c70a7e` addresses their source causes without suppressions. The selected
parameter reader separates API preflight, executable verification and annotation-kind decoding. Field metadata enrichment
and converter-fact validation have cohesive helpers. Stable annotation member names have constants; class-resource paths
share a helper. The internal route-recording method has an explicit name, and the unused local/nested ternary are removed.
The fixture JAR rewrite is linear: copying entries, corrupting a selected class and mutating its annotations are distinct
steps. Named visitors separate class markers, Builder associations and parameter members, preserving every negative
control. Long conditions and non-exit bodies are expanded; concise Java return guards remain. No runtime contract,
converter admission, dispatch, projection syntax or validation policy changes are intended.

Independent Standards and Specification reviews have zero remaining findings after the readability correction.
The full unchanged-revision `check` passed on JDK 17 in 26m 19s (140 tasks; 76 executed, 64 up-to-date). JUnit totals:
baseline 3,527 tests with 17 unchanged skips; G4/G5 each 2,856 with 16 unchanged skips; zero failures/errors. All 649
plugin tests pass with no skips. The independent oracle passed 28 JDK and 270 ECMA/Draft checks, and its schema hashes
were unchanged at full-check completion. `git diff --check` passes. This subsequent evidence-only update does not change
qualified code. The next ordinary CI/SonarCloud analysis must verify the finding list on the published follow-up head;
a green quality gate alone is not evidence that all findings are closed.
