# #959 P2: decimal, cardinality, pattern and deprecation characterization

Date: 2026-10-25. Base: `1788ea0f578d8376ae673b543a9fa7b057c3a4f1` (master, merged PR #966).
Status: **Design and characterization only; no additional production mappings approved or implemented.**
Tracker impact: **Related: #959**; issue stays open and untargeted. No release, curation or milestone change.
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[D1 and feasibility](../issue-959-schema-projection-feasibility.md),
[D2](issue-959-d2-raw-input-profile.md), [P1](../issue-959-p1-integral-profile.md).
This record supersedes the feasibility inventory's provisional P2 rows, not accepted D1/D2 or P1 behavior.

## Outcome and assignment boundaries

| Candidate | Truthful bounded raw-input predicate | Recommendation |
| --- | --- | --- |
| Direct DecimalMin/DecimalMax | Mathematical numeric comparison for BigDecimal and the existing integral scalar targets; retain integer/machine restrictions | Separate narrow implementation candidate; requires exact typed decimal/inclusivity metadata and rendering |
| Direct Size on ordinary List/Map | Supplied array length or distinct String-key object member count, at the container use | Separate narrow implementation candidate for exact List/Map declarations; do not infer it from payload kind alone |
| Size on Set or String | Set can deduplicate; String uses UTF-16 units, unlike JSON character length | Descriptive/deferred; no universal minItems/minLength mapping |
| Direct Pattern | A tiny flag-free ASCII grammar can be translated to absolute full matching | Keep descriptive in the next implementation; useful shared grammar exists, but arbitrary copying and a general translator are unjustified |
| Schema-authored Deprecated | Non-rejecting `deprecated: true` at the effective admitted member | Safe independent implementation candidate with explicit source provenance/completeness; no since/forRemoval semantics |

All three proposed implementation candidates need a separate assignment. None is enabled by this record. Existing
`integralConstraints=true` still means the P1 profile only. A future assignment must explicitly settle activation and
metadata-version compatibility rather than silently broaden that Boolean's promise. No public API/SPI, provider,
framework, quantity grammar, runtime rule, default, migration or release change is selected here.

For example, existing Groovy Schema syntax can express the candidate policy:

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size

@DSL class Capacity {
    @DecimalMin(value = '0.10', inclusive = false) BigDecimal amount
    @Size(min = 2, max = 3) List<String> zones
}
```

A **hand-authored candidate**, not generator output, would attach `exclusiveMinimum: 0.10` to the existing numeric
`amount` property and `minItems: 2, maxItems: 3` to `zones`. Neither becomes required. Supplied null and numeric Strings
retain their existing structural rejection. These are authoring restrictions on each supplied document, independent
of final Model validation, later applications and construction-time repairs.

## Actual binary and execution contract

The current compiler's
[`StructuredInputConstraintMetadata`](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputConstraintMetadata.java)
freezes complete field constraint identity/description with explicitly authored attributes on public Builder getters.
Only Min/Max have typed numeric values and eligibility. The internal
[`GeneratedInput`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java)
version 1 has no typed decimal, inclusive, size, regex or flags member. An omitted default is not an explicit effective
value there; symbolic attributes can remain source expressions in text. **Never parse those descriptions into facts.**

The packaged P2 fixture separately compiles an API/base Schema JAR and a derived Schema JAR using producer Groovy 3/4/5,
removes both source trees, then runs the unchanged generator in a source-free consumer without Jakarta/provider linkage.
It proves description-only P2 output for decimal, List/Set/Map, keyed owned Map, String and flagged Pattern declarations;
inherited `inheritedWords` attaches only at `inherited-size` with `example.CapacityBase#inheritedWords` provenance.
Grouped decimal, repeated decimal/pattern, payload-bearing Size and composed Pattern remain whole runtime text.
This is current binary behavior, not evidence that description text can safely drive a new mapping.

Public Builder bytes already carry `java.lang.Deprecated` on source-linked field getters and authored operations,
including separately compiled inherited fields. Inspection uses exact public Builder JAR entries with ASM, not annotation
proxies. Field provenance follows `AbstractMethodBuilder.linkToField` / `inheritDeprecationFrom`; public method copying
preserves authored-operation declarations. Excluded `hidden` can still have deprecated public methods while being absent
from admitted language. The same source-free JARs carry framework-authored deprecated `Create`/`CreateFrom` TemplateScope aliases on the
nondeprecated P2 Schema; neither alias is admitted input. Neither discovery of a method nor a deprecated annotation
proves admission.

P2's derived Model has a throwing static initializer and authored operation body. Existing root/static/default/validator
traps in the reused P1 fixture remain silent. P2 does not invoke the traps or a Bean provider; the earlier P1/P0 positive
controls are historical evidence only. Numeric/container probes use plain Schemas with no user validators, initializers,
converters, default annotations or lifecycle callbacks. They call the actual structured-input route to observe conversion
and population; framework construction is independent of generator inspection. No user code is used to discover facts.

The production admission reader and generator are unchanged. D1 still permits linkage of Model types from public
signatures but excludes Model implementation-byte inspection or fallback. Old/incomplete/malformed required new metadata
must fail closed when a separately approved enhanced mapping needs it, with source/use and recompile/package guidance;
complete unsupported declarations remain text. No P2 named-module run or new generated ABI is claimed.

## Shared declaration selection and placement

For every proposed hard constraint: exactly one direct Jakarta declaration of each supported constraint type, effective
empty groups and payload, and no relevant declaring/effective hierarchy group redefinition/sequence. Explicit Default
group selection remains deferred under D2. Nonempty payloads, even ERROR, are not editor-error authority. Legacy javax,
composed rules, OverridesAttribute, repeatable/List containers, container-element/type-use, getter/return/parameter and
operation-argument constraints remain descriptive as whole declarations. Do not peel their inner constraints. External
XML/programmatic provider configuration and suppression are not reproduced.

Keep declaration source owner/native member and declaring public Builder separate from effective external name/use.
Reuse `CompiledBuilderSchema` admission, exclusion, hierarchy and collision handling, with required facts on each
reachable declaring contract. Apply an ordinary field's facts only to its selected property, never an operation or
converter argument with the same spelling. Missing metadata is not absence of a rule. Invalid typed values/incomplete
facts need source-aware failure; well-formed unsupported variants need labelled text and preserved JavaDoc.

All mappings leave requiredness and null admission untouched, including construction keys. NotNull/Required never infer
raw requiredness; #962 alone owns nullable final validation. No validator initialization/isValid, default evaluation,
converter, lifecycle or relationship closure execution belongs in extraction.

## Decimal bounds

**Raw subset:** supplied JSON numbers for direct ordinary `BigDecimal`; or mathematical integers for byte/short/int/long,
the boxed forms and BigInteger. Keep structural machine limits and type. Numeric CharSequence acceptance in Bean
Validation does not widen the structured-input String grammar. Float/Double and Number are outside this candidate;
finite binary floating targets can round even when preflight succeeds.

**Mapping:** inclusive DecimalMin/DecimalMax become numeric `minimum`/`maximum`; exclusive forms become numeric
`exclusiveMinimum`/`exclusiveMaximum` (Draft 2020-12 uses numeric values, not old Boolean exclusive flags). Conjoin with
all existing restrictions, including P1 Min/Max. Parse the effective annotation String with `BigDecimal(String)`, without
a MathContext/double conversion. Compare with `compareTo`, not scale-sensitive equals. Render a valid exact JSON numeric
token, preserving mathematical value rather than textual scale or sign-of-zero. Exponent input, leading plus and
trailing zero are legal BigDecimal spellings; they are not all suitable verbatim JSON tokens. For example `+2.50E+3`
needs normalized JSON rendering. The existing private JSON writer accepts BigInteger but not BigDecimal.

**Intersections:** retain the tighter lower/upper endpoints; at equal endpoints exclusive wins. Reject L > U, or L = U
when either end excludes equality. For integral targets also prove a surviving integer: inclusive lower uses ceil(L),
exclusive lower floor(L)+1; inclusive upper floor(U), exclusive upper ceil(U)-1, then intersect machine limits. Thus
`0.1 <= integer <= 0.9`, `byte > 127.0`, and `1.00 <= number < 1E+0` are contradictions even though omission remains legal.
A future generator must diagnose those member policies, following P1, before output replacement. Do not round the
annotation into a Java long, truncate fractions, or allow last-wins overwriting.

**Evidence:** actual structured input preserves exact BigDecimal values/scales for `1E-7`, `1.2300`, `-1.2300`, `+2.5E+3`,
zero and `9007199254740993.0000000001`; String/null/Boolean inputs reject. Supplying `0.100000000000000001` to both float
and double produces decimal round-trip text smaller than the supplied value, crossing a hypothetical exclusive 0.1
boundary. The candidate corpus validates positive/negative, equality/exclusivity, exponents/zero, exact high-precision
neighbors, machine/integer conjunctions and contradictions using Decimal-parsed JSON schema numeric tokens.

**Necessary facts/stop conditions:** inert effective decimal String, Boolean inclusive (default true), annotation identity,
eligibility/selection context, source, completeness/version. Compiler constant folding must retain actual String values,
including symbolic constants, without evaluating user fields; authored text is insufficient. Malformed decimal Strings,
unrepresentable exponent/scale, missing attributes and impossible intersections fail explicitly; do not publish rounded
bounds. JSON Schema is mathematical but validators/parsers may use binary floats, including JavaScript Number. The
Decimal-based external corpus proves the candidate, not every editor's arbitrary-precision support or JSON-to-host
parser behavior. A future assignment must retain this limitation and test emitted tokens with a precision-capable
validator. No additional precision limit or silent rounding policy is selected here.

## Container Size and String size

**Ordinary safe subset:** direct Size on exact declared `List<T>` whose selected PROPERTY input is a List, or
`Map<String,T>` whose selected PROPERTY input is a String-key Map, with an already supported payload. Emit
`minItems`/`maxItems` on the array or `minProperties`/`maxProperties` on the object. Use effective nonnegative Java-int
bounds (defaults 0 and Integer.MAX_VALUE), require min <= max, and conjoin other restrictions. The size of a data Map is
its keys, not its values; equal values under different keys still count twice. JSON object duplicate textual keys have
already collapsed at parser level and cannot be described as two Map entries. Do not imply uniqueItems.

**Evidence and excluded shapes:** actual `['same','same']` survives as two List entries and one Set entry. String-key
data Maps with equal values keep both keys. A raw Set/scalar does not coerce into a List; numeric items do not coerce to
String; non-String keys reject for ordinary and keyed owned Maps. `CompiledBuilderSchema.Kind.LIST` covers List, Set and
sorted sets, so kind alone is insufficient. Exclude all Set/SortedSet/NavigableSet/EnumSet forms from this mapping; item
normalization can introduce further equality collisions. Keep sorted Map/List variants and arbitrary Collection/array
forms deferred until individually qualified. No new shape is admitted to make Size projectable.

**Keyed relationship/factory assessment:** the qualified `KEYED_OWNED_MAP` path takes literal String outer keys and invokes
the recorded child-creation path once per key; two same-valued child inputs produce distinct children named `a` and `b`. Its
potential Size mapping belongs on the **outer object** (`minProperties`/`maxProperties`), never on `additionalProperties`
or the shared child `$defs`. This is input count, not a final-graph guarantee. `@Field.keyMapping` is structurally excluded;
Class/Factory selectors, polymorphic aliases, collection-factory scopes, converter/custom factory inputs and arbitrary
relationship closures do not supply that guarantee. Keep keyed relationships outside the first ordinary-container
assignment; a separately scoped extension must qualify all admitted key-authority and selection variants. Owned-object
field Size has no container interpretation and remains descriptive. Operation List/Map payloads are not destination
fields and gain no field Size rule.

**Necessary facts:** typed effective min/max plus source/selection/completeness and exact declared container/population
role from the public contract. Preserve inherited rename/ignore and apply at the containing member use. Group/payload,
composition/repetition and unsupported sites follow the shared exclusions. Defaults, Templates, later applications and
lifecycle population remain D2 gaps, never extraction inputs or reasons to claim final-size equivalence.

**String:** Hibernate Validator 8.0.3.Final uses CharSequence.length (UTF-16 units for String). JSON Schema length counts
characters; `😀` has two UTF-16 units and one code point, while `e` plus combining acute has two of both. Candidate
`maxLength:1` accepts the emoji where Bean Size(max=1) does not; `minLength:2` reverses the discrepancy. Grapheme count is
neither contract. No implicit ASCII/BMP assumption, surrogate workaround, or independent text restriction is authorized.
Keep String Size descriptive. A schema-authored independently proven ASCII restriction could make counts agree, but
inferring such a dependency is beyond this slice.

## Pattern

Hibernate Validator 8.0.3.Final compiles Java Pattern with bitwise annotation flags and calls Matcher.matches. Draft
2020-12 pattern is ECMA-oriented **search**. Copying `[A-Z]+` would accept `xABCx`, which full Java matching rejects.
Java-only quoting, `\A`/`\z`, class intersection, possessive quantifiers and embedded flags are rejected or interpreted
differently by ECMA. Java UNICODE_CHARACTER_CLASS changes `\w`; Unicode/case folding, COMMENTS, UNIX_LINES, CANON_EQ,
DOTALL and MULTILINE do not have one general annotation-to-keyword encoding. JSON Schema has no standard flags keyword.
No arbitrary pattern is copied and no custom keyword is invented.

The shared 64-row corpus compares JDK full matching with Node **v26.12.0** RegExp search, including substring, empty
pattern, anchors, LF/CRLF/U+2028, flags, dot/newlines and Java-specific constructs. Without multiline, Node's `$` in these
cases requires absolute end, so `^ABC$` rejects a trailing newline; Java Matcher.matches rejects it too. Python's regex
backend alone must not be used as evidence of that ECMA behavior. With multiline, ECMA search can accept an interior
line that Java full matching rejects. Neither naive anchoring of arbitrary alternatives nor flag loss is safe.

**Tiny shared grammar exists:** flag-free concatenation of ASCII alphanumeric literals and positive classes limited to
`[A-Z]`, `[a-z]`, `[0-9]`, optionally quantified by `?`, `*`, `+`, `{n}`, `{n,m}`. No escapes, wildcards, negation,
anchors, grouping, alternation, backreferences or inline flags; quantifier bounds must be valid and bounded by an
explicit future implementation policy. Emit `^(?:BODY)(?![\s\S])`: noncapturing grouping protects the body and the
negative lookahead asserts absolute end, including every line terminator. The 44 tiny-grammar rows agree for ASCII,
supplementary characters, empty input and line endings in Java and ECMA with/without `u`. These samples support the
limited construction, not a complete recognizer or universal engine qualification.

**Disposition:** remain descriptive, including this tiny subset, until consumer demand warrants a narrowly specified
recognizer. Kubernetes quantity syntax and semantic positivity/units remain consumer-owned. A later assignment needs
typed effective regexp/flags, empty-flags proof, D2 groups/payload/context, bounded grammar validation, whole-declaration
fallback and same-corpus Java plus pinned ECMA plus emitted-schema checks. Missing facts fail; unsupported complete
regexes describe. No `isValid` execution or provider selection is needed to discover the regex.

## Deprecation

The [recorded requirement](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6389345279) is annotation-only.
Map source-authored `java.lang.Deprecated` to Boolean `deprecated: true` on the **effective admitted input property or
operation payload position**. All structural raw types qualify; it is unrelated to Bean constraint selection, groups,
payloads, composed/repeatable rules or class-level annotation inheritance. No inferred annotation from a deprecated
annotation type, a deprecated Model class, prose, method-name coincidence or framework alias.

The source-free fixture proves renamed `old-child`, inherited renamed `old-inherited` and renamed authored
`old-operation` are admitted, while deprecated ignored `hidden` is absent. At the P2 checkpoint, output emitted no deprecated keyword.
The candidate schema places deprecation as a sibling of the old relationship's `$ref`; the child's shared definition
and ordinary sibling reference stay clean. Draft 2020-12 allows this annotation beside `$ref`. A keyed-map deprecation
would similarly describe its outer member, not every child or all definitions. Member inheritance follows original
source association, not Java annotation @Inherited. Type-level Deprecated does not imply every input is deprecated.

**Necessary facts and independent assignment:** explicitly freeze a source-authored deprecated Boolean for each
source-associated field/key/authored operation at the compiler's existing enrichment seam; distinguish false from
missing version/coverage. Existing public annotations prove availability and a small route, but are not permission to
OR deprecation across every selected overload: generated deprecated compatibility aliases may serve a nondeprecated
source. The current constraint decoder exposes no such Boolean. A bounded follow-up should encode/read provenance
without scanning Model bytes, preserve old structural behavior, and qualify missing facts, excluded members, effective
collisions, ordinary/shared child refs, inherited binary declarations, construction-key placement, and framework aliases
with no source deprecation. Current P2 qualifies TemplateScope framework aliases and exclusion, but does not claim key/collision cases or a
synthetic selected-overload alias collision as new executable coverage. Those are precise implementation acceptance checks, not open product decisions or authority to begin implementation.

No rejection, requiredness, forbidden-member inference, lifecycle change, validator issue, removal policy or replacement
is implied. Preserve JavaDoc; since/forRemoval text is separately deferred. The hand-authored validator corpus confirms
absence/old/new/both inputs retain the same validation behavior and explicit null/unknown inputs still fail structurally.

## Reproduction and qualification

Retained characterization only:

- [`SchemaProjectionP2InputTest`](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/SchemaProjectionP2InputTest.groovy):
  actual structured-input conversions and container/key population, no authored callbacks or Bean annotations.
- [`KlumJsonSchemaGenerationTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumJsonSchemaGenerationTest.groovy),
  `retain P2 descriptions and project source deprecation from binaries with Groovy #generation` (renamed and extended by the
  [deprecation follow-up](../issue-959-deprecation-projection.md)):
  three independently packaged producer generations, cross-module inheritance, no consumer sources/provider, public-byte deprecation sites.
- [`SchemaProjectionP2SemanticsTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/SchemaProjectionP2SemanticsTest.groovy):
  Java side of the shared corpus, exact decimal and UTF-16 observations.
- [`validate_p2_candidate_schemas.py`](../../../klum-ast-gradle-plugin/src/test/resources/validate_p2_candidate_schemas.py):
  **hand-authored** Draft 2020-12 candidates; jsonschema 4.23.0 with exact Decimal parsing, 97 validation checks.
- [`validate_p2_ecma_patterns.js`](../../../klum-ast-gradle-plugin/src/test/resources/validate_p2_ecma_patterns.js):
  independent pinned Node v26.12.0, 108 ECMA comparisons over the same 64-row corpus. External tools are manually invoked,
  not Gradle test claims, and do not validate a production P2 mapping.

```shell
JAVA_HOME=/path/to/jdk17 ./gradlew \
  :klum-ast:test --tests '*SchemaProjectionP2InputTest' --tests '*CompiledBuilderSchemaTest' \
  :klum-ast:groovy4Tests --tests '*SchemaProjectionP2InputTest' --tests '*CompiledBuilderSchemaTest' \
  :klum-ast:groovy5Tests --tests '*SchemaProjectionP2InputTest' --tests '*CompiledBuilderSchemaTest' \
  :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest' --tests '*SchemaProjectionP2SemanticsTest' \
  :klum-ast:licenseTest :klum-ast-gradle-plugin:licenseTest --no-parallel --max-workers=2
/path/to/jsonschema-4.23.0/bin/python klum-ast-gradle-plugin/src/test/resources/validate_p2_candidate_schemas.py
node klum-ast-gradle-plugin/src/test/resources/validate_p2_ecma_patterns.js
```

The initial P2 packaged run disproved a fixture assumption that field deprecation would be absent from public getters;
all three producer generations already propagate it. The assertion was corrected to record the actual binary contract;
no production fix was needed. A later repeatable-description assertion used canonical dots instead of the actual binary-name `$` separator; that fixture expectation was also corrected. Runtime input cases passed on that first run. The corrected focused binary/Java corpus
passed. Final input/contract qualification passes 67 tests in each Groovy lane: 18 P2 input cases and 49 existing
CompiledBuilderSchema cases, zero failures/errors/skips. The packaged-generator regression passes all 42 cases
(39 existing plus three P2 binary cases), and Java semantic characterization passes 66 cases, also with no skips.
The original structural/enhanced external corpora pass again: 195/426 inputs, 18/3 metaschemas, generation-identical
artifacts. The P2 external corpora pass 97/108 checks. Core and plugin test licenses pass. Root check and unrelated test
suites were not rerun: production and compilation behavior are unchanged; the sole build-configuration change is the approved single-file license exclusion described
below. The selected suites exercise the new probes, existing binary authority and generator behavior.

The independent Standards reviewer identified two multi-assertion `expect` blocks; explicit observations now use
`when`/`then`, and the 66-case Java cohort passes again. The Specification reviewer requested a concrete framework alias
control. Public-byte checks now distinguish deprecated TemplateScope Create/CreateFrom from admitted Schema members,
including a deprecated ignored getter; all three source-free binary producers are requalified after that correction.
Both independent read-only reviewers rechecked against the original master base and report zero remaining actionable
findings on their respective Standards and Specification axes. Two reviewer slots were explicitly reserved before
launch; reviewers did not run Gradle or mutate files. The final binary/Java correction run passes all 69 cases.

CommonMark parsing, table/fence checks, all relative document file targets and diff whitespace checks pass. Current user
guides, migration navigation and CHANGES.md remain unchanged because this is characterization/design, not a public
feature. Delivery-channel audit: Git SSH branch publication **authorized** (non-mutating dry run); GitHub CLI repository
mutation **authorized** (read-only repository capability check). No credentials/scopes/errors are recorded.

The maintainer approved the single-file license exemption in this chat. The plugin's source-root-relative pattern
`schema-projection-p2-patterns.json` excludes the valid shared JSON corpus from header handling; it does not exempt other
JSON files or disable strict checks. The initial project-relative pattern did not match, so it was corrected to the
plugin's source-set path semantics. The JavaScript header was corrected normally. AGENTS.md's approval rule
is satisfied; no file was renamed/retyped to bypass header handling.

After approval, plugin validation and the complete 69-case P2 binary/Java cohort pass again, and the external 97/108
candidate/ECMA checks pass again. The plugin test-license check is re-executed explicitly rather than relying on a cached
result. A temporary read-only post-action audit confirms that the corpus is excluded and all 32 other test inputs
remain in the scan. Both independent reviewers rechecked this approved follow-up and report zero actionable findings.
Publication remains a draft with **Related: #959**, leaving issue state and milestone unchanged. CI/SonarCloud
status belongs to the actual published head and is reported separately; local checks do not establish a server result.

## Sources

- [Draft 2020-12 validation](https://json-schema.org/draft/2020-12/json-schema-validation): numeric, array/object/string,
  regex and deprecated keyword contracts. This defines schema semantics, not construction equivalence.
- [Jakarta Validation 3.0](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html):
  supported declaration types/defaults, selection, composition and null handling.
- [JDK 17 BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) and
  [Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html): exact parsing and Java regex semantics.
- Hibernate Validator 8.0.3.Final
  [PatternValidator](https://github.com/hibernate/hibernate-validator/blob/8.0.3.Final/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/bv/PatternValidator.java)
  and [SizeValidatorForCharSequence](https://github.com/hibernate/hibernate-validator/blob/8.0.3.Final/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/bv/size/SizeValidatorForCharSequence.java):
  inspected provider source; no validator invocation.
- [ECMA RegExp specification](https://tc39.es/ecma262/multipage/text-processing.html#sec-regexp-regular-expression-objects):
  standards reference, with the exact local engine version pinned above rather than claiming universal engine parity.


## Assigned decimal delivery checkpoint

The narrow direct DecimalMin/DecimalMax implementation is now separately assigned and recorded in
[the decimal delivery evidence](../issue-959-decimal-bounds.md) and ADR 0031's decimal checkpoint. It selects the separate
`decimalConstraints` property and independent completeness marker, without broadening the P1 integral Boolean or
accepting D7. The Decimal bounds section above remains the governing precision/intersection/exclusion contract.
The initial characterization status and hand-authored candidate corpus remain historical evidence; delivered schemas
are qualified separately. Other P2 candidates and broader #959 work remain deferred.
