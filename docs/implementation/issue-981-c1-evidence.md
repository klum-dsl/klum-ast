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
- Focused G3 converter and existing compiled-Builder reader checks pass. Full repository `check` and independent review
  results will be appended at the final validated revision; they are not yet claimed by this checkpoint.

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
