# #1129 U2: authored arm facts and effective resolution

Date: 2026-11-28. Scope: the [accepted U2 slice](issue-1129-struct-scalar-alternatives.md#u2--arm-facts-effective-hierarchy-and-rejection-boundaries).
Base: merged U1 PR #1131, `63b06e9076dcadcd511af61e3a5876bce9a20e20`.
Status: implementation complete; affected-module qualification and independent review in progress.
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

Result: pending. The plugin has one test lane with explicit Groovy 3/4/5 fixture producers; it does not define separate
`groovy4Tests`/`groovy5Tests` tasks. Independent artifact, historical-binary, JPMS and root full-build gates belong to U3.

Independent Draft 2020-12 validation uses the retained generated artifacts and the existing `jsonschema==4.23.0` oracle:

```shell
python klum-ast-gradle-plugin/src/test/resources/validate_scalar_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

Result: final expanded-corpus run pending. No handwritten substitute schema or runtime validator dependency is added.
License/diff and documentation link/anchor checks are recorded at final handoff. This evidence distinguishes local affected
modules from final-head hosted CI/Sonar and later artifact/adopter acceptance.

## Review and delivery boundaries

Independent Standards and Specification reviews: pending. History is one cohesive feature/test step plus a bounded
user/status/evidence update; final history review and hosted CI/Sonar are reported in the PR/worker handoff.
The existing documentary entry remains
`StructuredScalarAlternativeTest#'one address accepts an integer or a String without changing its meaning'`, with its
`@Issue('1129')`, `@Tag('documentary')`, `@See` and reciprocal user link; it now uses the four-overload fixture.
User pages and the existing unreleased entry reflect U2 behavior. Consolidated migration/delivery-doc qualification
remains U3; no release placement or consumer adoption is established.

No general union/ADT, carrier/registry, generated converter-family arms, alternate resolver, metadata protocol bump,
general parameter projection/enforcement, parser or #1113 reconstruction behavior is introduced. No ScHelm/Kubernetes
source, fixture or dependency changes. U3 remains a separately assigned native stacked successor; this worker neither
merges the PR nor closes #1129. Hive owns delivery and archive reconciliation.
