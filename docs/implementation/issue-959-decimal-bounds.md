# #959 exact direct decimal authoring bounds

Date: 2026-10-25. Base: `ac9e73bdc` (master including #968 and #969).
Tracker relationship: **Related: #959**. Issue remains open; no milestone, labels or curation changes.
Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md),
[P2 Decimal bounds](evidence/issue-959-p2-candidates.md#decimal-bounds), and the separately approved narrow assignment.

## Always-on faithful numeric projection

Latest maintainer decision, 2026-10-25: supported direct numeric declarations project automatically whenever JSON Schema
is generated. The unreleased Gradle `integralConstraints` and `decimalConstraints` properties, conventions, task inputs,
wiring and private Boolean options map are removed. There is no alias, replacement global flag or profile selector.
User configuration only needs the existing explicit root selection, for example:

```groovy
klumSchema.jsonSchema { rootTypes.add('example.Capacity') }
```

Direct integral Min/Max and exact DecimalMin/DecimalMax keep the existing eligibility, source, numeric-type, hierarchy
and independent-conjunction behavior. Contradictory representable assertions generate successfully; no solver or
numeric-domain rejection remains. `NumericBounds` stays deleted. BigDecimal parsing/rendering stays exact and sparse.

Both `constraintVersion=1` and `decimalVersion=1` are required for root, declaring/inherited and reachable owned public
Builder contracts. Retaining both avoids unnecessary generated ABI/compiler changes; they already carry the needed
complete facts. The private numeric fact reader now unconditionally checks decimal and integral completeness; its unused selector is removed. Missing,
malformed or incompatible facts fail atomically with source/artifact and recompile/package guidance. The separate
runtime admission reader is unchanged; no Model bytes or authored code supply missing facts.

The tests now exercise one always-on path, retaining numeric/exponent/conjunction/contradiction, binary API/Schema,
inherited/renamed/owned, unsupported-description, no-execution, metadata failure, output ownership and caching checks.
The final focused `KlumJsonSchemaGenerationTest` run passes all 58 cases without skips, including source-free Groovy
3/4/5 producers/consumers. External jsonschema 4.23.0 validation passes ordinary decimal 426 inputs / three metaschemas,
ten contradictory emitted schemas (valid schema, omitted properties accepted, supplied-value corpus rejected), integral
429 / three (now including automatic decimal projection), structural 195 / eighteen, deprecation 96 / six and the retained
97-input P2 hand-authored corpus. Earlier three-mode and solver qualification below is historical and does not qualify
this new head. Independent Standards and Specification reviews confirm zero remaining actionable findings at
`4f2cea62` against `952d651e0`, including the separately gated D7 proposal.

Final local qualification at clean, stable `4f2cea62`: JDK 17 root `check --no-parallel --max-workers=2` passes in 18m03s.
Groovy 3/4/5 totals are 3,145 / 2,634 / 2,634, with zero failures/errors and only existing skips 17 / 16 / 16. Strict
licenses, lane isolation, plugin validation and documentation rendering pass. All five external corpora pass again
after the root run. The publication follow-up changes evidence only. Current-head full PR CI/Sonar results are recorded
on draft PR #972; earlier flag-based runs do not qualify this correction. Local shared-project upload remains skipped.

### Sonar maintainability follow-up

CI analysis of `4fdb8afaa` passes the quality gate (90.4% new coverage, no duplication or unreviewed hotspots), but
reports S3776 on the combined selection/emission method and S5411 on nullable-Boolean unboxing. Both are addressed
without suppressions: private `conjoinBounds` isolates the independent-keyword/`allOf` emission loop, and explicit
Boolean comparison preserves inclusive/exclusive semantics after mandatory fact validation. No configuration, metadata,
runtime, output, numeric-domain or D7 behavior changes. All 58 focused schema cases and all five external corpora pass
after the cleanup. Thirteen retained pre-cleanup generated schema hashes match the newly emitted bytes exactly.
Independent review and full final-head qualification are being rerun; the earlier green gate with two maintainability
findings is not the final readiness result.

D7 is independently proposal-only: see the [field opt-out recommendation](issue-959-d7-naming-profiles.md#field-level-hard-constraint-projection-opt-out--proposal-only).
No suppression annotation, policy object/enum, setter-time validation, callback/SPI, generic filter or compiler metadata
redesign is implemented. A D7 implementation requires approval and its own later PR; it does not block this baseline.

## Historical faithful numeric correction (global flags superseded)

Maintainer directive, 2026-10-25, supersedes the earlier integer-feasibility refinement and all numeric contradiction
rejection in this PR and merged #966.

Supported numeric constraints are projected faithfully, including mutually contradictory bounds. JSON Schema generation does not perform numeric-domain satisfiability analysis.
A valid generated schema may describe a property for which no supplied value satisfies all constraints.

`NumericBounds` and its solver-only tests are removed: no ordering/equality contradiction check, rounding, integer
adjacency, endpoint offsets, machine-domain feasibility, or extreme-exponent solver remains. Each numeric annotation
becomes its exact keyword/value. Existing machine bounds stay in place. A new bound uses the direct keyword when free;
a collision appends the assertion to `allOf`, preserving every independent declaration without selecting tighter bounds.
This reconciles integral-only, decimal-only and combined configurations with the same faithful projection contract.

BigDecimal(String) and existing exact JSON numeric rendering remain. No arithmetic, scale conversion, powers of ten,
integer conversion, precision cap or fallback participates. Pathological positive/negative exponents remain sparse.
Malformed/out-of-range decimal encodings and missing/incompatible compiler facts still fail atomically. Compiler
metadata/version markers, public contracts, admission, null/omission/key behavior and runtime construction are unchanged.

Focused qualification: all 61 `KlumJsonSchemaGenerationTest` cases pass without skips, including source-free Groovy
3/4/5 fixtures, three projection modes and retained genuine-failure atomicity. The precision-capable jsonschema 4.23.0
corpus passes 426 ordinary decimal inputs / three metaschemas and 22 contradictory emitted schemas: omission accepts,
all tested supplied values reject, and sparse exponent boundaries remain exact. Retained integral (426 / three),
structural (195 / eighteen), deprecation (96 / six) and P2 hand-authored (97 inputs) corpora also pass.

Independent Specification review has no findings. Standards review's multiline test branch and stale normative wording
are corrected. No numeric arithmetic counterexample was demonstrated; the corrected defect is the extra semantic
rejection of representable declarations in #966 and earlier #972.

Final local qualification at `7ce365ee`: JDK 17 root `check --no-parallel --max-workers=2` passed in 16m42s, including
Groovy 3/4/5 (3,148 / 2,634 / 2,634 cases, zero failures/errors, existing skips 17 / 16 / 16), strict licenses, lane
isolation, plugin validation and documentation rendering. The working tree and HEAD stayed stable throughout. Rechecking
the precision-capable emitted-decimal corpus after the full run passes again. Both independent reviews confirm zero
remaining findings on the committed correction. The publication follow-up changes evidence only.

Current-head remote CI/Sonar results are recorded on draft PR #972; earlier green runs do not qualify this correction.
The records below describe earlier revisions and their historical qualification. Full PR CI/Sonar is the authoritative
remote gate; the maintainer explicitly chose to skip local uploads.

## Delivered boundary

Generation automatically maps one direct Jakarta DecimalMin and/or DecimalMax on ordinary BigDecimal or existing byte/short/int/long, boxed and BigInteger scalar
properties, with empty groups/payload and no relevant hierarchy group redefinition. Direct integral Min/Max retains its
original P1 numeric-type scope and conjoins with decimal declarations on integral targets. BigDecimal Min/Max stays
descriptive, as outside that scope. D7 naming/profile/example vocabulary remains deferred.

Compiler enrichment adds independent `decimalVersion=1` on each declaring public Builder, and exact compiler-folded
String `decimalValue` plus explicit effective Boolean `inclusive` on direct decimal constraint facts. Original field
source identity, declaring contract, effective selected admission and hierarchy eligibility remain authoritative.
Default inclusive=true is explicitly frozen. Symbolic String constants are folded by the ordinary annotation compiler;
our capture evaluates no expression, reads no user field reflectively and executes no authored callback. Original
Bean annotations remain at their original sites. There is no new metadata framework, provider or extension SPI.

The byte reader consumes only public Builder facts, without Model implementation resources, source files, annotation
proxies or user code. Missing/future integral or decimal completeness and missing decimal String/inclusive members
always fail generation with artifact, public-contract, source/use
and recompile/package guidance, including inherited and owned dependencies. This is matched internal linkage; an older
strict runtime reader is not promised to consume newly enriched contracts. No Model-byte inference or descriptive-text
parsing substitutes for missing facts.

Independent numeric assertions now use direct keywords and minimal `allOf` on collisions. No endpoint normalization
or satisfiability rejection occurs. BigDecimal parsing/rendering preserves exact mathematical value. Genuine malformed
or unrepresentable facts fail before output or the ownership manifest is replaced, with source/use diagnostics.

## Preservation and deferral

Omission, null, construction-key requiredness, String grammar, float/double/Number exclusions, groups/payload,
repeatable/composed/type-use/getter/operation-argument exclusions, Templates, defaults, lifecycle, interpreter and
Jackson behavior remain unchanged. Runtime descriptions append to source prose for complete unsupported declarations.
This is an explicit supplied-input authoring restriction, with no promise of construction success or general Bean
Validation equivalence. Downstream parsers/validators need arbitrary-precision decimal support; JavaScript Number can
round emitted numeric tokens.

Size/Pattern, quantity grammar, defaults and relationship-context defaults, groups/payload extensions, composed/repeatable
rules, broader D3-D6 mappings and D7 remain deferred. #962 retains nullable final-validation ownership. No new release
placement or overall #959 completion is claimed.

## Historical initial qualification

The new integration tests were written before production support. Initial red fixtures exposed ordinary Groovy
annotation compilation behavior for a throwing symbolic-constant owner; the final fixture uses legal binary public
String constants and retains separate throwing Model/operation/default/validator execution controls. No generator
execution exception or source/Model-byte fallback was added.

- Documentary test: `KlumJsonSchemaGenerationTest#'generate exact direct decimal authoring bounds from source-free binaries with Groovy #generation'`
  (`@Issue('959')`, `@Tag('documentary')`, `@See`), linked to
  [user documentation](../user/Json-Schema.md#decimal-constraints).
- Producer API/base and derived Schema JARs compile independently using Groovy 3/4/5. Both source trees are removed;
  the consumer has no Jakarta/provider dependency and no source input. Coverage includes high precision, exponent,
  plus/zero, default/explicit inclusivity, equality ties, inherited renamed provenance, owned use, integral Min/Max,
  machine restrictions, excluded variants and no Model/operation execution.
- Metadata mutation covers root/inherited/owned missing completeness, future version and missing String/inclusive facts;
  structural/integral output still succeeds and decimal failure retains prior schema/manifest bytes.
- Contradiction cases cover equal/exclusive, reversed BigDecimal bounds, fractional intervals without an integer,
  byte overflow and Min/DecimalMax conflict. Malformed and exponent-overflow inputs reject explicitly.
- `NumericBoundsTest` covers integer-survival endpoints and large/tiny exponents without expanding unbounded powers.
- Retained `validate_decimal_constraint_schema.py` uses `jsonschema==4.23.0` with Decimal-parsed schema/input numbers;
  expanded corpus: three metaschemas and 426 input checks, identical output across producer generations.

## Review and validation checkpoint

Independent read-only Standards and Specification reviews compared `ac9e73bdc...db567086`. Specification reported zero
actionable findings. Standards reported one grouped braces-policy finding; the follow-up adds braces to the new endpoint,
integer-rounding, diagnostic and metadata branches and the touched schema-member dispatch chain. The external corpus
also now covers every primitive/boxed integral target. No public behavior was expanded by the review fixes.

Focused decimal/compatibility/interval tests passed before the implementation commit. An overlapping pair of earlier
schema-suite runs produced one packaged-plugin version-marker failure; the duplicate run was stopped. The serial
focused retry passed with no packaging workaround. The first root check passed Groovy 4 and 5, then was intentionally
stopped before the baseline lane to apply review fixes and qualify the final revision with the combined full check and
normal coverage-bearing SonarCloud task. It is not counted as a passing full root check.

Final validation and delivery state is recorded below. PR CI/Sonar remains the separate authoritative PR-specific gate. Git SSH branch delivery and GitHub CLI PR delivery were independently verified
authorized; public repository visibility was verified without exposing credentials. History is ordered as a cohesive
implementation/test commit, matching documentation, and the focused review follow-up. No unrelated artifacts are staged.

## Pre-fix local qualification and delivery boundary

Qualified source/test revision: `8b0d0fae1`. The subsequent handoff commit changes evidence only.

- JDK 17 `check --no-parallel --max-workers=2` ran all Groovy 3/4/5 tests with zero failures/errors. The follow-up
  `check --continue --no-parallel --max-workers=2` completed the remaining tasks without excluding any check.
  Final JUnit totals: **3,174 / 2,634 / 2,634**, with **17 / 16 / 16** existing skips; no new skipped tests.
- All **66** packaged JSON Schema regressions and **21** exact interval cases pass without skips, including independent
  G3/G4/G5 source-free binary producers/consumers. Plugin validation, lane isolation and documentation renderer pass.
- External emitted-schema qualification passes: decimal **426** inputs / **3** metaschemas; retained integral
  **426 / 3**, deprecation **96 / 6** with advisory-removal parity, structural **195 / 18**. Producer generations
  emit byte-identical decimal schemas. All **103** relative documentation targets resolve, including legacy routes.
- Independent Standards and Specification follow-up reviews at `8b0d0fae1` report **zero remaining actionable findings**.
  The initial grouped braces finding is addressed. The evidence-only trailing blank line is removed.

**That pre-fix full root check failed.** Its sole remaining failure was `:klum-ast-runtime:licenseMain`, which
includes a pre-existing untracked generated artifact at
`klum-ast-runtime/src/gdsl/.sonar/spring-context-model.json`. The artifact and other pre-existing scan directories are
preserved and excluded from this branch. No artifact was renamed, retyped, removed or relocated, and no license check
was skipped. AGENTS.md requires a plugin/configuration decision before addressing such a conflict; permission for a
narrow generated-`.sonar` exclusion has been requested and is pending. No license configuration change is included.

**Local SonarCloud analysis was not executed.** Automatic approval review rejected the proposed normal coverage-bearing
branch analysis before execution because explicit human authorization for repository source/analysis upload to the
configured external SonarCloud destination could not be established. This is an authorization rejection, not a scanner
or quality-gate failure. No workaround, indirect analysis or coverage-free upload was attempted. The maintainer has now
explicitly selected **skip local upload; use the full PR CI gate**. That authorization choice is settled; no further
local analysis is planned. No current-branch Sonar result or absence of findings is claimed.

Delivery was **blocked on the generated-cache input/configuration decision at that checkpoint**. The dedicated branch is committed locally; no push, draft PR, tracker
comment, merge or archive was performed. Once the license conflict is addressed, rerun the applicable checks, push and open the assigned draft PR with **Related: #959**. Broader #959 stays open;
no milestone, label, curation, release placement or D7 decision changes. Hive delivery/archive reconciliation remains
pending. This record supplies the concrete implementation, review and validation handoff without claiming completion
of the assigned draft publication.


### Generated-cache classification follow-up

Read-only inspection of the installed Sonar Java analyzer identifies
`org.sonar.plugins.java.SpringContextModelPersistence`: it defaults to `.sonar/spring-context-model.json`, loads that
model opportunistically and saves it during analysis. The local file is an empty generated model (`filesData` is empty),
not Schema or product source. It should be ignored in Git; the tracked `.sonarlint/connectedMode.json` configuration is
separate and should remain versioned.

The runtime's `src/gdsl` directory is a resource source root. Both the local runtime JAR and source JAR contain the
untracked `.sonar` cache. The installed license plugin also derives inputs from `SourceSet.allSource`, independently
of Git ignore rules. Therefore Git ignore alone would not fix artifact pollution or the license failure. The proposed
bounded fix is a `.sonar/` Git ignore plus exclusion of `**/.sonar/**` from Java projects' resource source inputs, so the
cache is neither packaged nor treated as licensed source. No license-header formatting, source renaming or deletion is
needed. The maintainer explicitly approved the prepared Git/resource-input patch, which is now applied.

### Approved generated-cache exclusion

The root Git ignore now covers `.sonar/` at any depth. The shared Java-project convention excludes `**/.sonar/**` from
resource source inputs. This prevents caches from entering product/source JARs and, because the license plugin consumes
SourceSet inputs, removes generated tool data from its source inventory. Strict license checking and header policy are
unchanged; no source/artifact was renamed, retyped or removed. The local generated cache remains on disk, while the
tracked SonarLint connection configuration remains versioned.

Focused JDK 17 validation passes `:klum-ast-runtime:licenseMain`, `jar` and `sourcesJar`. Inspection of both rebuilt JARs
confirms no `.sonar` entries and retention of both authored GDSL resources. Full repository qualification and final
independent follow-up review on the approved configuration revision are recorded below. The earlier failed-check
checkpoint above records the pre-fix state; it is not the final result. The maintainer's full-PR-CI Sonar choice remains in force.


## Qualified delivery checkpoint after approved cache fix

- JDK 17 `check --no-parallel --max-workers=2` **passed** at `640b36d0415e6815a65f65908af3c314bf890247`.
  All **3,174 / 2,634 / 2,634** Groovy 3/4/5 cases completed with zero failures/errors and **17 / 16 / 16** existing skips.
  Licenses, test-lane isolation, plugin validation and documentation renderer all pass; no checks were skipped.
- All **66** packaged JSON Schema regressions and **21** interval tests pass without skips. External corpora pass
  decimal **426 / 3**, integral **426 / 3**, deprecation **96 / 6** and structural **195 / 18** input/metaschema checks.
- Final runtime and source JARs on that revision contain no `.sonar` entries and retain both authored GDSL resources.
  The generated cache remains on disk and Git-ignored; tracked SonarLint configuration is preserved.
- Independent Standards and Specification follow-up reviews at `640b36d0` both report **zero actionable findings**.
  Commit history is reviewed and scoped; this final qualification update changes evidence only.
- Git SSH branch publication and GitHub CLI PR publication were separately verified authorized. Draft delivery uses
  **Related: #959**, with no issue closure, labels/milestone/curation update, merge or self-archive. Live PR/CI results
  are recorded on the canonical issue and PR. Full PR CI/Sonar is authoritative; no local scan is claimed or planned.

The implementation and local qualification are complete. Draft PR publication follows this record; its live state and
any subsequent review/CI result are GitHub facts, not inferred from this local checkpoint. Review/merge and Hive
archive reconciliation remain separate external conditions.

### Current-master documentation reconciliation

Master advanced through merged PR #970 while this slice was being qualified. Its changes are documentation only:
the D7 evidence brief and ADR checkpoint deliberately defer all D7 implementation and explicitly allow the bounded
decimal slice to proceed. A merge preserves the published feature commits and both independent ADR checkpoints;
no production, test or resource-input behavior changes from qualified source/configuration revision `640b36d0`.
Documentation/link/diff checks qualify this merge; the normal PR CI/Sonar gate runs on its resulting head.

## Historical PR #972 integer-feasibility review refinement (superseded)

This historical follow-up, removed by the faithful-projection correction above, changed only the private numeric feasibility calculation and its tests. Configuration, compiler facts,
metadata versions/readers, numeric keywords and serialization, admission/null/omission/key behavior, construction,
validation, output ownership and all deferred features retain the accepted contract.

### Exact comparison and correctness argument

Let `A = ceil(L)` and `B = floor(U)`. Define `eL` as one exactly when the lower endpoint is exclusive and `A = L`,
and `eU` as one exactly when the upper endpoint is exclusive and `B = U`; otherwise each is zero. Thus the smallest
permitted integer is `A + eL` and the largest is `B - eU`. These are exactly the requested floor/ceiling formulas:
excluding a noninteger endpoint does not alter its nearest permitted integer, while excluding an integer shifts it
by one. The interval is feasible precisely when `A + (eL + eU) <= B`. The total adjustment is only zero, one or two.

The replacement compares that inequality without materializing enormous integers:

- If `A` has scale zero, add the adjustment to its already stored integer coefficient and compare with `B`.
- Otherwise, if `B` has scale zero, subtract the adjustment from that coefficient and compare with `A`.
- Otherwise both rounded values have negative scale and are multiples of ten. Any strictly increasing pair has gap
  at least ten, sufficient for either unit exclusion. Equal values allow only zero adjustment; reversed values fail.

This removes `adjacent()`, digit-count comparisons and subtraction between potentially distant sparse endpoints.
It uses no precision cap, floating arithmetic, MathContext or exception-driven acceptance/fallback.

### Resource safety

The retained integer-rounding helper leaves nonpositive scales sparse. For positive-scale values with magnitude less
than one it returns only zero or signed one, avoiding enormous-scale division. Other positive-scale values already
store the coefficient digits needed by their integer part. The final comparison adjusts only a scale-zero coefficient
by at most two, growing that coefficient by at most one digit; it never fills a huge negative scale with zeros.
When both endpoints are sparse multiples of ten, only numeric comparison is required.

### Verification checkpoint

Expanded boundary tests and 2,500 bounded BigInteger-oracle comparisons passed against the original algorithm before
replacement. No correctness defect was demonstrated; this is a maintainability and auditability refinement. The
replacement also passed those focused tests. The final bounded corpus additionally includes nonzero negative-scale
values, giving 3,364 deterministic comparisons across all four inclusivity combinations, with no new testing framework.
Dedicated cases cover machine-limit intersections for 8/16/32/64-bit values, Min/Max plus decimal bounds, equal/scaled,
exclusive/adjacent, negative/zero/fractional/reversed and unbounded intervals, exponents up to `2147483647` in both
signs, and tiny values at scale `2147483647`. Full generation and final-head qualification results are recorded below.

Focused Groovy 3 qualification passes all **67 NumericBoundsTest** cases and all **66 JSON Schema generation**
regressions, with no failures/errors/skips. This includes the **3,364** bounded-reference comparisons within one test.
The emitted precision-capable decimal corpus still passes **426 inputs / 3 metaschemas**; retained integral,
deprecation and structural corpora pass **426 / 3**, **96 / 6** and **195 / 18** respectively. Final repository and
independent review qualification is recorded below.


### Integer-feasibility final qualification

The final code/test revision `bd995a9e7` passes JDK 17 root `check --no-parallel --max-workers=2`: **3,220 / 2,634 / 2,634**
Groovy 3/4/5 cases, zero failures/errors and **17 / 16 / 16** existing skips. Licenses, lane isolation, plugin validation
and documentation checks pass. All **67** numeric and **66** schema-generation cases pass without skips, including
**3,364** bounded-reference comparisons. The external decimal **426 / 3** input/metaschema corpus and retained integral
**426 / 3**, deprecation **96 / 6**, structural **195 / 18** corpora pass against final generated artifacts.

Independent Standards and Specification reviews of `29301c427...bd995a9e7` both report **zero actionable findings**.
Only private numeric logic, its tests and this evidence change; the reviewed PR history is preserved. No correctness
defect was found in the original algorithm by the characterization/reference tests. The replacement is justified by
its smaller correctness argument and bounded coefficient arithmetic, not by a claim of changed public behavior.

This final qualification update changes evidence only. Normal full PR CI/Sonar on the published follow-up head is the
authoritative remote gate; its live result and consolidated review disposition are recorded on PR #972. No local
analysis upload, dependency/public API addition, issue/milestone-state change or merge is part of this refinement.
