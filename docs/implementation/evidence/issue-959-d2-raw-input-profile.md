# #959 D2: raw-input assertion profile

## Current always-on numeric projection rule — PR #972

Supported numeric constraints are projected faithfully, including mutually contradictory bounds. JSON Schema generation does not perform numeric-domain satisfiability analysis.

Supported direct Min/Max and DecimalMin/DecimalMax project automatically whenever JSON Schema is generated. The
unreleased `integralConstraints` and `decimalConstraints` flags are removed without aliases or a replacement selector.
Both existing constraint metadata versions are required on reachable root/inherited/owned public Builder contracts.

This supersedes earlier global opt-in, contradiction-rejection and integer-feasibility requirements, including #966.
Historical checks and experiments below describe their original revisions. Missing/malformed/incompatible facts
and structural projection errors retain fail-closed handling and output atomicity.

Date: 2026-10-24. Status: **B accepted; characterization complete; P1 remains separately gated**.
Baseline: `e18f21b24b4017844720ef0072b95635491a32be` (master, including merged D1/P0 documentation).
Authority: [#959 and its decision comments](https://github.com/klum-dsl/klum-ast/issues/959),
[accepted D1](../issue-959-schema-projection-feasibility.md#accepted-d1-metadata-authority-and-missing-artifacts),
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md).
This is a design and characterization result, not P1 authorization or a released feature.

## Accepted decision B

Maintainer decision, 2026-10-24: **B is accepted.** KlumAST may offer an explicitly selected raw-input authoring profile
for direct integral `@Min`/`@Max`: each supplied value must satisfy the bound before construction. The restriction is a
consumer-selected authoring policy derived from declaration facts, not a prediction of final Model state or equivalence
to Bean Validation. The accepted architectural extension is recorded in
[ADR 0031](../../adr/0031-structured-schema-input-artifacts.md#raw-input-assertions--959-d2).

The maintainer clarified that construction-time repairs are hypothetical, not an expected use case or a compatibility
requirement. The repair/replacement fixtures below establish the semantic boundary; they do not justify weakening raw
bounds, inspecting callbacks, or designing an escape mode. Defaults and construction continue independently.

**A**, descriptions only, was considered but not selected as the overall profile. Descriptions remain the fallback for
complete declarations outside the supported subset. **C**, broader assertions, remains deferred. D2 acceptance does not
schedule P1 or settle its metadata encoding, exact configuration spelling or implementation details.

| Alternative | Assessment |
| --- | --- |
| A: descriptions only | Truthful if text identifies final/runtime rules and preserves existing prose. Gives no machine-checkable percentage feedback. Still requires D1 metadata and an approved description policy; not a missing-metadata compatibility mode. |
| B: direct integral authoring bounds (original opt-in superseded) | **Accepted numeric subset; activation now always-on.** Useful Schelm percentage feedback; small, independently understandable predicate over supplied input. Intentionally can reject input that could construct successfully and accept input that later fails. |
| C: broader projection/equivalence | Defer. Coercing operations do not identify a Model property predicate; groups/severity affect selection; container population and pattern grammars have additional semantics. No evidence here qualifies these as hard assertions. |

## Accepted semantic contract

1. **Always-on projection supersedes the original off-by-default activation.** Generating JSON Schema now projects the
   supported numeric subset automatically, with no global flag or profile selector. The historical D2/P1 choice of a
   separately selected artifact is retained as history only. Root descriptions identify raw-input bounds and their
   final-validation limitation. Existing output ownership/cache behavior remains; root/classpath/tool inputs determine
   the task fingerprint. Consumers choose whether editors/validators use the artifact; import does not automatically
   run JSON Schema validation.
2. **Only direct field declarations with a proven ordinary integral input property qualify.** Initially allow one direct
   Jakarta `@Min` and/or one direct `@Max` on `byte`, `short`, `int`, `long`, their boxed counterparts, or `BigInteger`.
   Declaration facts must identify the actual admitted property and its effective external name. No getter/return,
   parameter/type-use, container element, authored operation, relationship-closure or composed/repeatable mapping.
   An authored operation taking an integer is not automatically an ordinary property. A converter's alternate input
   signature must not inherit the destination field's bounds by name.
3. **The selected bound constrains the supplied mathematical integer.** Add inclusive `minimum`/`maximum` conjunctively
   with the existing type and machine bounds; never overwrite a tighter restriction. Mathematically integral numbers
   such as JSON `100.0` remain integral; numeric strings and fractions are not admitted. Use exact integers without
   double rounding. `BigInteger` retains its unbounded structural range; annotation bounds themselves are Java longs.
4. **Absence is unchanged.** No `required` is inferred from `@NotNull`, `@Required`, `@Min`, `@Max`, a default or an initializer.
   Existing structural construction-key requirements remain intact. An optional nested child stays optional, and an
   absent child member is not tested as though a default had already supplied it.
5. **Null admission is unchanged.** Current supplied scalar and owned-object nulls are rejected structurally, including
   boxed integral properties. This rejection is not attributed to `@Min`/`@Max`, which allow a final null value.
   Do not add nullable branches or infer non-null final values from the artifact. Zero, false and empty strings remain
   supplied values of their respective types, not omission. [#962](https://github.com/klum-dsl/klum-ast/issues/962) alone
   owns the separate nullable final-validation investigation.
6. **Use only the simplest selection context.** Effective annotation `groups` and `payload` must both be empty, and the
   required metadata must establish no relevant group redefinition/sequence on the declaring/effective type hierarchy.
   Empty groups mean the ordinary Default context; explicit `groups=Default` is deliberately deferred with other explicit
   group selection. Any payload, even explicit ERROR, is outside this first profile. Do not peel a `@Min` from a composed
   or repeatable declaration. Unsupported complete declarations remain labelled runtime text. This is a selection policy,
   not a claim to reproduce provider XML/programmatic configuration, validation suppression or verification thresholds.
7. **Construction remains independent.** Defaults, initializers, Templates, subsequent active-Builder applications,
   lifecycle participants, operation bodies and conversion can affect the final value. They neither weaken nor strengthen
   an already selected raw bound. Each document associated with this artifact is judged on its own supplied values;
   there is no merged-document or effective-layer evaluation. The current generator's positional/key contract still
   governs whether an artifact describes a particular document; D2 adds no active-Builder/keyless schema variant.
8. **Names and ownership come from the existing admitted contract.** Attach an inherited declaration to its effective
   admitted property, preserve its original source identity, honor rename/ignore and existing collision diagnostics.
   Apply direct child-property bounds inside the child's normal definition at each valid owned use; do not lift an
   owner's relationship annotation onto child properties or modify a shared definition with contextual defaults.
   This permits recursive ordinary direct properties, not relationship constraint projection.

For example, this existing Schema syntax needs no new annotation:

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

@DSL
class Capacity {
    @Min(0L) @Max(100L)
    Integer percentage
}
```

A **hand-authored candidate**, not generated output, is:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "percentage": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "allOf": [{"minimum": 0}, {"maximum": 100}]
    }
  }
}
```

The accepted profile uses ordinary inclusive numeric assertions and omission semantics from
[Draft 2020-12 validation](https://json-schema.org/draft/2020-12/json-schema-validation#section-6).
[Jakarta Validation 3.0](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)
defines the direct constraints' null, integral-type and default-group semantics. Neither standard supplies a claim about
KlumAST construction; the runtime counterexamples below establish that boundary.

## Error, diagnostic and activation expectations

| Situation | Required observable result for future implementation |
| --- | --- |
| Historical enhanced-profile switch | **Superseded:** supported numeric projection is always on; there is no global selector. Runtime behavior remains unchanged. |
| Required Schema facts absent, malformed, or their source association unverifiable | Fail generation under D1. Identify root, declaring public Builder, Schema source declaration, effective external property/use, dependency/artifact when known, and reason; missing required facts give actionable recompile/package guidance. Preserve previous successful outputs. Never fall back to Model bytes or silently downgrade to descriptions. |
| Complete metadata describes a rule outside the tiny profile | Preserve the admitted property and append deterministic `Runtime constraint (not enforced by this schema)` text, including the unsupported selection reason (for example payload or group). Preserve JavaDoc; do not suggest schema success means this rule passed. Generic metadata decoding/formatting remains later work. |
| Projected bounds have an empty intersection, including machine range | **Superseded by the PR #972 maintainer correction:** preserve all assertions and generate successfully. The historical D2 contract required generation failure; an impossible supplied-value policy is now explicitly valid JSON Schema. Omission remains legal. |
| Input has wrong type/null/unknown name | Existing structural schema errors; unchanged interpreter preflight errors retain source name and input pointer. Do not relabel these as Bean violations. |
| Supplied integer violates a selected bound | External JSON Schema validation reports the instance location and violated bound/schema location. Projection must preserve source association sufficiently to explain which declaration supplied that bound. Exact message wording is validator-specific; editor severity is editor policy. No `KlumValidationException` or runtime issue is manufactured by schema generation. |
| Input passes the schema but operation/construction/final validation fails | Existing runtime exception and stored validation result semantics. JSON Schema validity is not materialization success. No fabricated raw-input leaf for a later Model violation. |

An ERROR-level Bean constraint is not identical to an editor error: the adapter records issues and Klum verification has
its own threshold/suppression controls. The adapter calls `validator.validate(instance)` and maps payloads to levels;
it supplies no schema projection SPI. A future generator must not bootstrap that adapter/provider or execute validators.

## Characterization and exact evidence

The retained [runtime characterization](../../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/RawInputConstraintCharacterizationTest.groovy)
uses compiled files with a fresh artifact loader and the real `KlumStructuredInput` and service-loaded Bean adapter.
Its 40 cases assert existing behavior only. It neither extracts constraint metadata nor evaluates a proposed schema.
The separate [Python corpus](../../../klum-ast-bean-validation/src/test/resources/raw-input-constraint-profile.py)
checks four **hand-authored** shapes with `jsonschema==4.23.0` (42 assertions); it is manually invoked, not a Gradle test.
The structural shape is transcribed from `JsonSchemaProjection.scalar`, not emitted by that probe. The current generator
has independent unchanged-output regression coverage below. These are separate observations, not a P1 end-to-end test.

| Fixture / supplied input | Current structural / interpreter result | Final Model / Bean result | Candidate B result |
| --- | --- | --- | --- |
| Direct Integer percentage: 0, 100, 100.0 | Accepted; exact numeric conversion | Same integral value; no issues | Accept |
| Direct percentage: -1, 101 | Structurally accepted; creation counter increments | `KlumValidationException` on percentage | Reject at supplied percentage |
| Omitted boxed percentage | Accepted | Null, accepted by Min/Max | Accept omission |
| Explicit null, false, empty string, numeric string `"50"` at percentage | Preflight rejects, counter stays zero, `/percentage` and source retained | No Model | Structural reject, not bound failure |
| Fraction 0.5; 2147483648; -2147483649 into Integer | Preflight rejects before allocation | No Model | Reject fraction/type or existing machine bound |
| Byte/Short/Integer/Long and primitives; BigInteger | 0 and 100 accepted in all nine target forms | Endpoints pass Bean validation | Same proposed endpoint semantics |
| Literal initializer 75 | Omission keeps 75; supplied 0/100 overrides | 75/0/100 pass | No `const` or projected `default`; omission remains legal |
| Literal `@DefaultValues(percentage=75, label='filled', enabled=true)` | Empty input and `[percentage:0,label:'',enabled:false]` accepted | Both percentages become 75; both labels become filled; supplied false stays false | Test original values, not computed defaults |
| DefaultValues literal String `"50"` targeting Integer | Omission accepted; same String supplied raw rejects | Default coerces to 50 and passes | No String widening for coercible defaults |
| `@PostTree` replaces -1 or 101 with 50 | Accepted and executed | 50, no issues | Reject raw input intentionally |
| `@PostTree` replaces 50 with 101 | Accepted and executed | Final validation fails | Accept raw input; no success guarantee |
| Omitted `@NotNull name` / `@Required description` | Structurally accepted, allocation occurs | Both final presence checks fail | No inferred raw required |
| Independent String/Boolean: empty/false, versus null | Empty/false accepted; null rejects at its pointer | Empty/false survive without defaults | Preserve original type admission |
| Authored `fromText(String)` operation | String payload accepted; body converts it | `"50"` yields valid 50; `"101"` fails finally | Do not attach destination bounds to operation input |
| Owned child percentage 101; later active-Builder application supplies 50 | Each input structurally accepted in one Construction session | Repaired child 50 passes; standalone nested 101 fails finally | Nested bound rejects first document if checked; no layer prediction |
| Inherited field renamed to `percent`, plus ignored `hidden` | `percent` accepted; native `percentage` and ignored `hidden` rejected | Inherited 100 passes; 101 fails | Place future bound only at admitted `percent` |
| Explicit custom group `Later` with Min(0), supplied -1 | Accepted | Default adapter call reports no issue | Describe, not assert |
| Explicit `groups=Default`, supplied -1 | Accepted | Final validation fails | Deliberately defer explicit group selection |
| WARNING payload with Min(0), supplied -1 | Accepted | Model returned with one WARNING issue | Describe, not promote to hard assertion |

The inherited rename/ignore test compiles Base and Capacity together and uses literal Jackson annotation stubs, following
existing interpreter tests; it is not separate-artifact or managed-Jackson qualification. Separate binary metadata and
source association are historical [P0 evidence](issue-959-p0-binary-metadata.md), not newly rerun D2 extraction tests.
No relationship constraint, custom converter factory, XML override or lifecycle participant registration is implemented
by these probes. The `@PostTree` counterexample suffices to disprove equivalence for mutable construction generally.

Inspected production seams:

- [CompiledBuilderSchema](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java)
  and [admission planning](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java):
  declaration identity, effective names, admitted roles, exclusion and selected signatures are structural authority.
- [StructuredInputSupport](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java):
  complete preflight, null/type checks, exact integral conversion, then ordered dispatch. Custom converters/factories remain
  outside this grammar; authored String operations are separate admitted operations, as documented in
  [Structured Input](../../user/Structured-Input.md). Managed Jackson has its own mapper/coercion contract under ADR 0009.
- [JsonSchemaProjection](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java):
  current type/machine bounds, closed objects, positional keys, nested definitions and descriptions; no Bean bounds.
- [DefaultPhase](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DefaultPhase.java),
  [Default Values](../../user/Default-Values.md) and [Model Phases](../../user/Model-Phases.md):
  emptiness, boolean exception, contextual/coercing defaults and mutation before materialization/validation.
- [JSR380Validator](../../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/internal/JSR380Validator.java),
  `TransformedBeanValidationTest`, `BeanValidationDocumentaryTest`, `RelationshipConstraintBeanValidationTest` and
  [Validation](../../user/Validation.md): completed-Model constraints, default-group call, payload levels and independent
  runtime relationship checks. No adapter or final-validation changes belong to D2.

Schelm's `schelm-skeleton/DESIGN-NOTES.md` (defaults/portable constraints and custom projections) and `KLUMAST-NOTES.md`
(annotation defaults and #959/#962 notes) were read at local HEAD `553fdd42fb3cf82ba66c9588df28005ff58a7c84`.
They motivate percentages and contextual defaults; they do not select KlumAST APIs. The stable
[#959 consumer comment](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6385826650) records that input.
No Schelm build or consumer acceptance is claimed. The later deprecation comment is also preserved as future D1/P1
annotation-only work; it is not a D2 assertion and is not implemented here.

## Validation and next slice

On Java 17, all Bean Validation module tests passed in Groovy 3.0.25, 4.0.32 and 5.0.6:
**50 tests per lane, including 40 D2 cases; zero failures, errors or skips**. The module license check passed.
The first probe run exposed only fixture setup errors (domain annotation compilation ordering and treating an interface
as the primary DSL class); these were corrected before the passing runs. No production change was needed.

```shell
JAVA_HOME=/path/to/jdk17 ./gradlew \
  :klum-ast-bean-validation:test \
  :klum-ast-bean-validation:groovy4Tests \
  :klum-ast-bean-validation:groovy5Tests \
  :klum-ast-bean-validation:licenseTest --no-parallel --max-workers=2
/path/to/jsonschema-4.23.0-venv/bin/python \
  klum-ast-bean-validation/src/test/resources/raw-input-constraint-profile.py
```

The unchanged `KlumJsonSchemaGenerationTest.generate structural*` cohort passed three packaged-consumer cases
(Groovy 3/4/5). Its existing external validator accepted all 18 emitted schemas: six roots per generation, 195 input
checks total, valid Draft 2020-12 metaschemas and byte-identical output across generations. This regression contains
no new constraint mappings. Commands:

```shell
JAVA_HOME=/path/to/jdk17 ./gradlew :klum-ast-gradle-plugin:test \
  --tests '*KlumJsonSchemaGenerationTest.generate structural*' --no-parallel --max-workers=2
/path/to/jsonschema-4.23.0-venv/bin/python \
  klum-ast-gradle-plugin/src/test/resources/validate_json_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

`git diff --check` and relative Markdown file-target checks passed. Current user docs, migrations and release notes
are intentionally unchanged because this delivery adds only design evidence and characterization tests.

The next slice is a **separately assigned P1 plan** defining
minimal D1 facts, exact activation and diagnostics for this tiny profile. P1 must qualify binary inheritance,
source association, rename/ignore, absent versus unsupported metadata, contradictory bounds, default-group/payload
exclusions, exact integral endpoints and generated-artifact validation without user-code execution. It must also prove
that profile-off output stays unchanged and generation failure preserves prior artifacts. These are later acceptance
tests, not coverage claimed here.

Groups, warning payloads, composed/repeatable rules, relationship closure constraints, defaults projection, custom
callbacks/SPIs, decimals, Size, Pattern and quantity grammar remain deferred. No public/generated API, metadata extraction,
production mapping, release behavior, current user guide, migration navigation or changelog change is made. Issue #959
stays open and untargeted; D3–D6 are not silently accepted. Repository-local evidence needs no engineering-baseline change.
