# #1129 U2: authored arm facts and effective resolution

Date: 2026-11-28. Scope: the [accepted U2 slice](issue-1129-struct-scalar-alternatives.md#u2--arm-facts-effective-hierarchy-and-rejection-boundaries).
Base: merged U1 PR #1131, `63b06e9076dcadcd511af61e3a5876bce9a20e20`.
Status: implemented and locally verified for bounded U2 using complete-suite coverage plus scoped final-correction checks.
Independent reviews are complete; U3 and hosted final-head results remain separate.
Related: [#1129](https://github.com/klum-dsl/klum-ast/issues/1129); the issue remains open for U3 and final acceptance.

## Implementation

The compiler's existing pre-collector handoff retains original one-payload parameter declarations on authored methods.
The ordinary operation enrichment now freezes count, inert constraint rows and policy regardless of marks or family
membership. It reuses field constraint capture and conservative original-source/composition checks without copying
executable constraints onto generated methods. Harmless annotations can now have known empty facts; collector-derived
constraints remain documentary rather than acquiring direct eligibility.

Protocol remains **1**, with existing unknown defaults. The shared reader transports operation facts generically; the
family resolver separately requires explicit complete facts on both arms, including zero, and checks counts, rows,
composition ownership, policies and duplicates. Missing or malformed family facts reject even under OMIT or absent input.
Single-route artifacts do not gain a completeness requirement. Original exact declaration and parameter-zero identity
remain attached to each immutable arm; no field/converter facts substitute for them.

The projector explicitly qualifies only these two family parameter sites for the existing closed constraint catalog.
Integer Min/Max conjoin with structural int32 bounds; bounded Pattern stays on String. Method/parameter Javadoc, String
examples, deprecation, literal descriptions and PROJECT/TEXT_ONLY/OMIT remain branch-local. The wrapper combines no prose
or assertions. Unsupported/grouped/collector forms and group-sequence contexts remain documentary; ordinary authored
single-route parameter projection stays unchanged. JSON Schema assertions do not become runtime parameter enforcement.

Marked inherited numeric candidates now survive provisional collision handling until descendant overrides and effective
naming resolve. Existing integer/String override guards retain current declaration identity, marks, names and exclusions.
The neutral Destination fixture adds explicitly ignored Number and opaque helpers: neither participates, executes through
Struct nor contributes a schema property/branch. An extra eligible candidate rejects the entire attempted family.
Generated converter selection and dispatch are unchanged.

## Acceptance evidence

Focused Groovy 3 compiler/runtime tests (`StructuredScalarAlternativeTest`, `StructuredOperationSelectionTest`,
`InputNamingProfileTest`) pass **161 cases**. They cover all standard numeric wrappers, integral/scaled decimals and
int32 endpoints; fractions, overflow, nonfinite/custom Number, null, Boolean, mapping/list, GString/mutable CharSequence
and opaque-object rejection; exact signature/call counts and cause/no-retry; ordered partial effects and cleanup;
Template definition/replay; ignored helpers, eligible extras and unsupported families; inherited completion, profile
joining/separation, override attribution and legacy selection; generic nonempty transport without parameter enforcement;
and missing/count/row/policy/role/marker/protocol mutation controls, including absent input and OMIT.

Focused generated-schema checks reuse `KlumJsonSchemaGenerationTest` and its existing producer/consumer and JAR mutation
helpers. They generate actual schemas from Groovy 3/4/5 producers and inspect canonical arm order, exact source identities,
Javadoc including parameter prose, assertions, examples, deprecation and all policies. Integer and String collector-origin
controls, composed constraints and group-sequence controls exercise conservative eligibility. A Schema initialization
trap remains untouched during inspection and an explicit initialization control trips it. Seven parameter mutations under
OMIT reject before replacing previously successful output or its manifest.

Affected-module qualification command:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  :klum-ast:test :klum-ast:groovy4Tests :klum-ast:groovy5Tests \
  :klum-ast-runtime:test :klum-ast-runtime:groovy4Tests :klum-ast-runtime:groovy5Tests \
  :klum-ast-gradle-plugin:test \
  :klum-ast:verifyTestLaneIsolation :klum-ast-runtime:verifyTestLaneIsolation \
  :klum-ast:licenseMain :klum-ast:licenseTest :klum-ast-runtime:licenseMain \
  :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest \
  --console=plain --max-workers=2
```

The complete run produced these results (counts include existing skipped cases):

| Module / lane | Cases | Failures | Existing skips |
| --- | ---: | ---: | ---: |
| Compiler / Groovy 3 | 3538 | 0 | 16 |
| Compiler / Groovy 4 | 3542 | 0 | 15 |
| Compiler / Groovy 5 | 3542 | 0 | 15 |
| Runtime / each of Groovy 3, 4, 5 | 116 | 0 | 1 |
| Plugin / full suite | 714 | 1 before final correction | 0 |

The sole final broad-run failure expected the rejected policy name `FUTURE` in an existing field-policy diagnostic.
The additive correction restores that exact baseline text; it changes no admission or projection behavior. Both reviewers
rechecked it without findings. The four existing field-policy regressions and all focused checks below pass afterwards.
The complete plugin suite was not rerun locally after that one-string correction; the other 713 cases' inputs and
behavior are unchanged. This is recorded explicitly rather than reporting the broad command as wholly successful.

Final correction qualification used the same Java 17 environment and a single Gradle invocation:

```shell
typeset -a u2_validation_args
for u2_lane in test groovy4Tests groovy5Tests; do
  u2_validation_args+=(":klum-ast:$u2_lane" \
    --tests '*StructuredScalarAlternativeTest' --tests '*StructuredOperationSelectionTest' \
    --tests '*InputNamingProfileTest' --tests '*CompiledBuilderSchema*Test' \
    --tests '*ConstraintProjectionTest' --tests '*InputConstraintDescriptionTest')
done
./gradlew "${u2_validation_args[@]}" \
  :klum-ast-runtime:test :klum-ast-runtime:groovy4Tests :klum-ast-runtime:groovy5Tests \
  :klum-ast-gradle-plugin:test \
  --tests '*KlumJsonSchemaGenerationTest.emits one bounded integer and String*' \
  --tests '*KlumJsonSchemaGenerationTest.incomplete family parameter*' \
  --tests '*KlumJsonSchemaGenerationTest.corrupt Pattern facts*' \
  --tests '*KlumJsonSchemaGenerationTest.field policy metadata*' \
  :klum-ast:verifyTestLaneIsolation :klum-ast-runtime:verifyTestLaneIsolation \
  :klum-ast:licenseMain :klum-ast:licenseTest :klum-ast-runtime:licenseMain \
  :klum-ast-gradle-plugin:licenseMain :klum-ast-gradle-plugin:licenseTest \
  --console=plain --max-workers=2
```

Result: **passed**. Compiler/contract tests: **261 cases per generation**, all passing with no skips. Full runtime lanes:
**116 cases each**, zero failures and one existing skip each. Plugin: **29 cases**, all passing, including all four field
policy cases, the three generated-schema producer generations, seven family parameter corruptions and existing Pattern
corruption cases. Both lane-isolation checks and affected license tasks pass. No new tests are suppressed.

The plugin has one test lane with explicit Groovy 3/4/5 fixture producers; it does not define separate
`groovy4Tests`/`groovy5Tests` tasks. Independent artifact, historical-binary, JPMS and root full-build gates belong to U3.

Independent Draft 2020-12 validation uses the retained generated artifacts and the existing `jsonschema==4.23.0` oracle:

```shell
python klum-ast-gradle-plugin/src/test/resources/validate_scalar_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

Result: **39 emitted schemas, 279 accepted / 477 rejected documents**, including all Groovy 3/4/5 variants. No handwritten substitute schema or runtime validator dependency is added.
Affected license tasks, `git diff --check`, touched documentation file/heading links and reciprocal documentary
references pass. This evidence distinguishes local affected
modules from final-head hosted CI/Sonar and later artifact/adopter acceptance.

## Review and delivery boundaries

Independent Standards review found one import-style violation and two optional naming/duplication observations; all
three were corrected and independently rechecked. Specification review found no actionable gaps and rechecked the
follow-up with no remaining findings. Broad plugin regression exposed changed diagnostic precedence for corrupt field
Pattern facts; restoring baseline rule-before-count validation fixes it. The unchanged duplicate-Pattern regressions,
new family checks and focused runtime suite pass after correction. History preserves the feature/test commit, bounded user/status/evidence update and independently rechecked corrections.
Final history review confirms the commits contain only U2; hosted CI/Sonar are reported in the PR/worker handoff.
The existing documentary entry remains
`StructuredScalarAlternativeTest#'one address accepts an integer or a String without changing its meaning'`, with its
`@Issue('1129')`, `@Tag('documentary')`, `@See` and reciprocal user link; it now uses the four-overload fixture.
User pages and the existing unreleased entry reflect U2 behavior. Consolidated migration/delivery-doc qualification
remains U3; specifically, `Struct.Operation` Javadoc and the opening `docs/user/Migration.md` paragraph still describe
U1's empty-only boundary and must be synchronized in that consolidated pass. No release placement or consumer adoption
is established.

No general union/ADT, carrier/registry, generated converter-family arms, alternate resolver, metadata protocol bump,
general parameter projection/enforcement, parser or #1113 reconstruction behavior is introduced. No ScHelm/Kubernetes
source, fixture or dependency changes. U3 remains a separately assigned native stacked successor; this worker neither
merges the PR nor closes #1129. Hive owns delivery and archive reconciliation.

Delivery-channel preflight: GitHub CLI repository-write capability **authorized**; dedicated Git branch publication
channel **authorized**, verified by dry run. Actual publication and hosted results are recorded in the PR/worker handoff.

## First hosted analysis and Sonar follow-up

Hosted [CI run 46089537660](https://github.com/klum-dsl/klum-ast/actions/runs/46089537660) passed on
`fc16fb439ef2f62bc1215747d33c21870e82b1bd`, including the complete existing pipeline and the corrected field-policy
regression. Sonar's gate passed with **93.0% new-code coverage, 0% duplication and zero security hotspots**.
Detailed PR analysis reported two maintainability findings, addressed in an additive follow-up:

- **java:S3358:** extract the field/parameter site into its own local String before composing the TEXT_ONLY reason.
  Generated policy text and behavior remain the same.
- **java:S6539 (INFO):** keep the accepted shared resolver. Its dependency count rose from 20 to 21 through the immutable
  parameter-facts result. A class-local, rule-specific suppression documents ADR 0032's single admission authority and
  this concrete metadata dependency. Splitting that authority is outside the accepted slice. All other checks remain active.

Independent Standards and Specification reviewers rechecked both dispositions with no findings. The existing focused
checks pass after this nonfunctional cleanup:

```shell
./gradlew :klum-ast:test \
  --tests '*StructuredScalarAlternativeTest' --tests '*StructuredOperationSelectionTest' \
  --tests '*InputNamingProfileTest' \
  :klum-ast-gradle-plugin:test \
  --tests '*KlumJsonSchemaGenerationTest.emits one bounded integer and String*' \
  --tests '*KlumJsonSchemaGenerationTest.incomplete family parameter*' \
  --tests '*KlumJsonSchemaGenerationTest.field policy metadata*' \
  --tests '*KlumJsonSchemaGenerationTest.choose field constraint projection*' \
  :klum-ast-runtime:licenseMain :klum-ast-gradle-plugin:licenseMain \
  --console=plain --max-workers=2
```

Result: **161 contract cases and 17 plugin cases pass**; real field/family policy producers cover Groovy 3/4/5.
The independent validator again passes **39 schemas, 279 accepted / 477 rejected documents**. License/diff checks pass.
Final follow-up head, hosted reanalysis and delivery state are reported in the PR/worker handoff; this section records
only the named first-head result and the follow-up's local evidence.
