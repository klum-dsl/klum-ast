# #959 exact direct decimal authoring bounds

Date: 2026-10-25. Base: `ac9e73bdc` (master including #968 and #969).
Tracker relationship: **Related: #959**. Issue remains open; no milestone, labels or curation changes.
Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md),
[P2 Decimal bounds](evidence/issue-959-p2-candidates.md#decimal-bounds), and the separately approved narrow assignment.

## Current qualification status

The approved feature/configuration revision `640b36d0415e6815a65f65908af3c314bf890247` passes the complete JDK 17 root
check, including Groovy 3/4/5, licenses, lane isolation, plugin validation and documentation rendering. All earlier
blocked checkpoints below are historical. The maintainer chose normal full PR CI/Sonar, with no local upload.
Live draft-PR/CI delivery state belongs to [GitHub issue #959](https://github.com/klum-dsl/klum-ast/issues/959).

## Delivered boundary

`klumSchema.jsonSchema.decimalConstraints = true` is a separate, off-by-default tracked Gradle input. It maps one direct
Jakarta DecimalMin and/or DecimalMax on ordinary BigDecimal or existing byte/short/int/long, boxed and BigInteger scalar
properties, with empty groups/payload and no relevant hierarchy group redefinition. P1's `integralConstraints` Boolean
retains its direct integral Min/Max scope. Decimal mode conjoins that P1 subset on integral targets even with the original
Boolean false; BigDecimal Min/Max stays descriptive, as outside P1. D7 naming/profile/example vocabulary remains deferred.

Compiler enrichment adds independent `decimalVersion=1` on each declaring public Builder, and exact compiler-folded
String `decimalValue` plus explicit effective Boolean `inclusive` on direct decimal constraint facts. Original field
source identity, declaring contract, effective selected admission and hierarchy eligibility remain authoritative.
Default inclusive=true is explicitly frozen. Symbolic String constants are folded by the ordinary annotation compiler;
our capture evaluates no expression, reads no user field reflectively and executes no authored callback. Original
Bean annotations remain at their original sites. There is no new metadata framework, provider or extension SPI.

The byte reader consumes only public Builder facts, without Model implementation resources, source files, annotation
proxies or user code. Missing/future decimal completeness and missing decimal String/inclusive members remain usable
for structural and original integral generation. Decimal mode fails closed with artifact, public-contract, source/use
and recompile/package guidance, including inherited and owned dependencies. This is matched internal linkage; an older
strict runtime reader is not promised to consume newly enriched contracts. No Model-byte inference or descriptive-text
parsing substitutes for missing facts.

The private numeric helper conjoins lower/upper endpoints, with exclusion winning at equal numeric values. BigDecimal
parsing has no MathContext, double conversion or annotation truncation. Its JSON numeric rendering normalizes lexical
spelling as necessary while retaining mathematical value. Integral intersection checking rounds only to prove integer
existence; emitted decimal endpoints remain exact. Existing machine limits and integer types survive semantically.
Malformed/unrepresentable decimal values and empty decimal/integer intersections fail generation before output or the
ownership manifest is replaced, with source, external name and public-contract diagnostics.

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

## Qualification

The new integration tests were written before production support. Initial red fixtures exposed ordinary Groovy
annotation compilation behavior for a throwing symbolic-constant owner; the final fixture uses legal binary public
String constants and retains separate throwing Model/operation/default/validator execution controls. No generator
execution exception or source/Model-byte fallback was added.

- Documentary test: `KlumJsonSchemaGenerationTest#'generate exact direct decimal authoring bounds from source-free binaries with Groovy #generation'`
  (`@Issue('959')`, `@Tag('documentary')`, `@See`), linked to
  [user documentation](../user/Json-Schema.md#opt-in-decimal-constraints).
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
