# #959 D7-EXAMPLES — documentary string examples

Date: 2026-10-27. Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[draft PR #978](https://github.com/klum-dsl/klum-ast/pull/978),
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#d7-documentary-string-examples--959).

## Accepted simplification

The maintainer replaced the earlier parsed-value design with documentary strings. The old parser/probe design remains in
Git history for provenance; it is not the current contract or retained compatibility infrastructure.

`@InputExamples` provides documentary strings. They are emitted as JSON Schema string examples, without parsing or
validation against the property type or runtime constraints. A note on a numeric, boolean or object property remains a
string. Contents are never expressions or executable payloads. Runtime input, validation, defaults, requiredness,
conversion, templates and lifecycle remain unchanged. No future typed-example infrastructure is prepared.

## Removed code and test infrastructure

- Deleted `JsonExample` and its dedicated grammar test class.
- Deleted the typed/raw JSON writer branch and generator declaration-scan/parse pass.
- Deleted numeric/null, duplicate-key, JSON Unicode/escape and depth machinery and their test corpora.
- Deleted the bespoke recursive constant folder and modified-UTF-8/count/depth public limits.
- Replaced JSON/size/depth corruption tests with a small metadata-shape/atomicity matrix and documentary-string checks.
- Removed the obsolete proposed parser guarantees from current docs and this evidence record.

## Remaining handling and rationale

`InputExamples(String[])` remains documented, runtime-retained and FIELD/METHOD-targeted. Independent field/key/operation
facts, the suppressed-key fallback, and the exact source-free public Builder byte reader retain their authority. Naming,
inheritance, owned uses, exclusions, deprecation and constraint projection retain their existing decisions. The unified
`GeneratedInput.Schema.version` remains **1**, without another feature gate or compatibility mechanism.

The generator puts the string lists directly into `examples`, using its ordinary serializer. No special value wrapper or
content validation remains. Empty arrays emit no keyword; empty strings, order and duplicates are retained.

The compiler uses `ExpressionUtils.transformInlineConstants(Expression)`, Groovy's AST-only overload. Its implementation
was inspected for G3 and qualified on all supported generations. The owner-accepting overload can call reflective
`Field.get` and initialize an authored class, so it is not used. A small VariableExpression-to-owner-property adapter is
needed for a source-local constant before accessed-field resolution. A final String-constant guard and normalization of
the source annotation prevent fallback evaluation during later annotation compilation. There is no custom recursion,
operator implementation, cycle evaluator or speculative expression support. Literal, local and compiled constants are
covered; nonconstant source and binary references have initialization traps.

JVM/compiler constraints bound annotation strings, arrays and constant pools. The reader additionally caps aggregate text
at 1,048,576 UTF-16 units per declaration: a compact defensive guard against classfile constant-pool reuse amplifying output.
This is an internal resource guard, not an elaborate public authoring-limit contract. It does not inspect content.

## Focused acceptance

- `InputExamplesTest`: documentary runtime behavior, distinct field/operation/key facts, literal/local/binary constants,
  nonconstant rejection without execution, string-array transport and active-Builder atomic failure.
- `KlumJsonSchemaGenerationTest#'project advisory examples at exact source-free input positions with Groovy #generation'`:
  G3/G4/G5 separate API/base/owned/derived JARs after deletion of producer sources/classes; plain/non-JSON strings,
  quotes/backslashes/newlines/Unicode, numeric/boolean/object notes as strings, exact placement, exclusions, deprecation,
  constraint policy, deterministic cache restoration and a static-initializer trap with a separate positive control.
- `KlumJsonSchemaGenerationTest#'invalid carried examples fail before replacing any output: #target #kind'`:
  missing/duplicate/wrong-shape/wrong-element metadata, inherited/owned absence and excessive expanded documentation;
  every previously generated document and the output manifest survive failure.

Both documentary features carry `@Issue('959')`, `@Tag('documentary')`, and `@See` links to
[Structured Input](../user/Structured-Input.md#advisory-input-examples) and
[JSON Schema](../user/Json-Schema.md#advisory-input-examples).

## Qualification, reduction and delivery

JDK17 source/test revision `7b5d7e2d`: final sequential `./gradlew check` passed in **12m51s**, 140 tasks (68 executed,
72 up-to-date). Groovy 3/4/5 totals are **3316 / 2754 / 2754**, with **zero failures/errors** and **17 / 16 / 16 existing
skips**. The focused `InputExamplesTest` passes **11 cases in every lane**. All **109 packaged JSON Schema cases** pass.
Separate G3/G4/G5 annotation-propagation artifact qualifiers pass (1 / 2 / 2 cases, no failures/errors/skips). Licenses,
plugin validation, source mirrors, lane isolation, coverage and release/documentation checks pass. No suppression or
dependency was added. A superseded local run was canceled for observed review cleanup; it is not qualification evidence.

Independent `jsonschema 4.23.0` checks pass **93 schema/input controls**, confirm **57 string-only example locations**,
quote/backslash/newline/Unicode round trips, unchanged input acceptance with examples removed, and byte-identical G3/G4/G5
output. All **43 retained schema documents** pass the Draft 2020-12 metaschema. A standalone JDK17/G3 Java static-imported
constant control also passes without initializing its supplying class. Local replay: `/private/tmp/d7-documentary-validate.py`,
`/private/tmp/d7-constant-controls.py`, `/private/tmp/d7-documentary-final-check.log`,
`/private/tmp/d7-documentary-review-tests.log`, `/private/tmp/d7-documentary-artifacts.log`.

### Before/after complexity

Compared with the previously published `1159c5ec4`, using the fixed PR base `6c6d8c568`:

| Measure | Parsed-value implementation | Documentary strings |
| --- | ---: | ---: |
| Production Java net physical LOC introduced by this PR | 462 | 160 |
| Test Java/Groovy net physical LOC introduced by this PR | 533 | 448 |
| Dedicated JSON grammar cases | 53 | 0 |
| Feature test executions, including G3/G4/G5 lanes | 127 | 44 |

LOC is reproducible from `git diff --numstat <base> <revision>`, scoped to production/test Java and Groovy; it includes
comments/license headers. Production contribution is **302 lines smaller (65%)**; test contribution is **85 lines smaller
(16%)**. The deleted parser alone was 211 physical Java lines. The runtime feature cases shrink from 17 to 11 per lane;
packaged examples cases shrink from 23 to 11. The remaining tests target the documentary contract and metadata integrity.

### Review disposition and risks

Independent Standards and Specification reviews report **zero remaining findings**, including the final metadata cleanup.
The previous PR head's 18 Sonar observations are addressed in source by deleting parser/budget machinery or by narrow
retained-code cleanup: metadata-local diagnostics, a semantic member-name constant, a cohesive array-reader method, and
local test guard braces. No global style policy, broad suppression or future feature abstraction was introduced. The new
head's CI/Sonar remains a separate gate; these observations are not claimed closed in the service before reanalysis.

No known correctness defect remains after local qualification. Documentary strings intentionally provide no guarantee of
valid instances for numeric, boolean, object or constrained properties. The internal aggregate-text guard can reject
pathological documentation before output replacement. Compiler constant capture is qualified on the supported Groovy
versions; unsupported nonconstant expressions are rejected without reflective fallback. Earlier typed implementation
results are historical and do not qualify this changed contract.

The existing PR is updated with additive commits; reviewed history is preserved. Tracker relationship is **Related: #959**;
issue labels, milestone and state are unchanged by this slice. No merge is authorized. PR CI/Sonar is a separate
current-head gate; local results do not imply a remote pass. Hive delivery/archive reconciliation remains pending.
