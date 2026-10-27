# #959 D7: inherited structured-input naming profiles

Authority: profile-only implementation assignment after merged [PR #975](https://github.com/klum-dsl/klum-ast/pull/975),
based on `bcaa5f00713ed45e70e84a20622a395683e239f0` (`origin/master`). Related: #959.
The [accepted decision](issue-959-d7-external-naming-decision.md) defines semantics; this assignment deliberately defers
`InputName`/`InputIgnore` while preserving the existing Jackson literal override/exclusion bridge.

## Bounded probe before production changes

Two test-only `CompiledBuilderSchemaTest` features ran on Java 17 / Groovy 3, 4 and 5 before production edits:

- `D7 binary probe retains native candidates before identity collision pruning`: separately compiled base/owned, child
  and grandchild JARs, deleted compilation outputs, isolated runtime-only consumer; public fields/selections retain both
  colliding candidates, native names, exact literal overrides, ignored facts, inherited key and authored-operation names.
  An initializer trap stays untouched; a separate positive control initializes the binary producer and proves it live.
- `D7 binary probe folds explicit type choices onto public Builder contracts`: test-only compiler folding records version,
  explicit presence and profile Strings on a disposable public annotation. Separate artifacts preserve absent ancestry,
  first introduction, unannotated/equal/conflicting descendants, explicit identity and independent owned type facts.
  This probes transport rather than claiming that the unmodified production reader already implements profiles.

Both features passed on all three lanes; no counterexample to the accepted contract was found. One initial command was
stopped after Gradle applied its filter only to the final task; the corrected per-task filters ran the bounded cases.
Test-only annotation resolution and a Groovy reflection/class-loading assumption were corrected before qualification;
class loading is distinct from initialization. Production annotation-proxy evaluation is not introduced.

## Selected metadata and resolver

| Fact | Encoding / responsibility |
| --- | --- |
| Completeness | `GeneratedInput.Schema.namingVersion=1`, independent of numeric/decimal/field-policy versions |
| Explicit selection | Explicitly emitted `namingExplicit` Boolean on every declaring Builder |
| Declared profile | Explicitly emitted inert `namingProfile` String, `IDENTITY` for absent declarations |
| Provenance | Exact public Builder identity, corresponding Schema type and verified artifact/resource origin |
| Entry names | Existing native field/key names and public authored-operation names plus existing literal/exclusion facts |

No extra per-member descriptors or profile annotations on Model-byte readers are needed. Both interpreter and projector
consume `CompiledBuilderSchema`'s resolved result. It resolves hierarchy choices first. Identity uses the original recursive
collision pruning; nonidentity collects native candidates before pruning, applies the one bounded ASCII algorithm to
native eligible tokens, and rejects deterministic collisions with both sources. Literal names are never transformed.
Key naming uses the same effective type profile; key collisions remain dependent on member versus external-key positions.
The originating declaration keeps numeric policy, deprecation and documentation association.

An explicit choice binds descendants, including identity; equal repetition is allowed. First introduction over absent
ancestry names inherited members while the standalone ancestor remains native. Owned types resolve independently even
when reached through two owner fields. No ownership cascade, mutable policy, caller selector or plugin option is added.

Unknown versions/profiles, missing explicit/profile facts in version 1, inconsistent absence facts and missing naming
ancestry under a selected profile fail with source/artifact recompile/package guidance. Complete legacy identity facts
remain supported where no selected feature requires unavailable provenance. Matched tools and Schema recompilation are
required for selected hierarchies; no blanket compatibility claim is made for arbitrary old artifacts or erased facts.
Numeric/decimal/projection completeness is neither relaxed nor coupled to naming.

## Preserved Jackson bridge and limits

Existing Jackson-only behavior is unchanged: `JsonIgnore` with absent value/true excludes, then nonempty `JsonProperty`
provides an exact literal, then native/profile fallback. Empty property value falls back; false ignore is neutral.
Fields, construction keys and admitted one-payload authored operations retain source selection. There is no native alias,
resurrection of structurally excluded declarations, or transformation of data/instance keys, fixed values, enums or Map
operation payload keys. Managed Jackson import, mapper strategies and broader `JsonNaming` remain independent.

`InputName`/`InputIgnore`, package/root/relationship configuration, examples transport, ConstraintProjection changes,
defaults, Bean Validation/provider SPI and additional grammar remain deferred. No issue is closed or reprioritized.

## Qualification and delivery

The branch is `codex/959-input-naming-profiles`, based on the #975 merge above. Independent review and remote
CI/Sonar are separate gates; the worker callback names the final published head and live remote results.
The packaged fixture uses separate API/base, derived Schema and owned-value artifacts; sources, mirrors and compiler
outputs are absent from the consumer, whose classpath contains no compiler or databind. The happy path compares exact
closed-schema properties with real interpreter acceptance and rejects native aliases. Negative cases retain prior output
and active Builder state, and use execution traps with positive controls. Malformed UTF-16 is tested as actual classfile
annotation Strings rather than assumed representable source identifiers.

### Local verification before independent review

Java 17 root `./gradlew check --no-parallel --max-workers=2` passed at `06b9d90a9887257d188d1108efcfbaf1a276e871`
in 21m14s: G3/G4/G5 totals **3,218 / 2,672 / 2,672**, zero failures/errors, existing skips **17 / 16 / 16**.
This includes all 93 packaged JSON Schema cases, licenses, lane isolation, plugin checks and documentation rendering.
Only review brace corrections and a private typed scalar-dispatch simplification followed that production revision. Subsequent decoder/initialization
controls and import cleanup pass **95 focused compiler/runtime cases per G3/G4/G5 lane** and **24 packaged naming cases** (2m42s).

Actual public Builder classfile deltas from the three explicit naming fields are **75 bytes** for absent/identity facts
and **77 bytes** for kebab facts, identical across G3/G4/G5. All 135 relative Markdown targets/anchors and balanced
fences across affected documentation passed; final diff/commit-history checks precede publication.

The decoder cases exercise unknown/future/missing/inconsistent facts, duplicate members, wrong scalar/enum encodings,
legacy ancestry and actually representable malformed UTF-16 Strings. Positive controls prove initialization traps live.
The packaged cases verify separate API/base, derived and owned JARs on each generation, exact interpreter/schema names,
explicit identity/equal/conflicting hierarchy choices, literal overrides, first-child collision recovery, owned-type
independence, inherited construction keys and position-specific key collisions, preserved old schema output and legacy
identity byte-for-byte output. Direct tests retain active Builder state on rejected contracts.

Documentary traceability:

- `InputNamingProfileTest#'choose kebab member names while keeping owned values and data keys independent'`, `@Issue('959')`,
  `@Tag('documentary')`, `@See` to [Structured Input](../user/Structured-Input.md#type-naming-profiles).
- `KlumJsonSchemaGenerationTest#'resolve input naming from source-free hierarchy contracts with Groovy #generation'`,
  the same markers linked to [JSON Schema](../user/Json-Schema.md#type-naming-profiles).
- [Migration](../user/Migration.md#type-naming-profiles), [ADR](../adr/0031-structured-schema-input-artifacts.md#d7-type-profile-implementation-slice--959)
  and [changelog](../../CHANGES.md) describe only delivered profile behavior. The feature issue remains open.

### Independent review and delivery boundary

The worker launched separate read-only Standards and Specification agents only after the completed branch was ready at
`a052cf1a7f8a7b28f3616fa7333a1b5817599c35` (three occupied slots including the worker). Specification reported zero
actionable findings. Standards reported one grouped P3 finding: missing braces on new nested/multiline conditionals.
The follow-up adds braces to those guards and related new private resolver guards. Both reviewers confirmed zero remaining
findings after that correction. Preventative Sonar hygiene then replaces the grown scalar visitor dispatch chain with a
private typed switch, preserving exact accepted scalar types and unknown-member errors. Both independent agents reviewed
that additive diff and again reported **zero remaining findings**. Reviewed commits are preserved. Final post-correction
verification passed in **6m05s**: 95 compiler/runtime cases per G3/G4/G5 lane and **all 93 packaged JSON Schema regressions**,
plus affected source licenses, lane isolation and plugin validation. A prior committed-head run also passed documentation
rendering. Final Markdown/diff and four-commit history review passed before publication; no history rewrite was needed.

GitHub CLI repository delivery authority is **authorized**; Git SSH branch delivery is independently checked by the
publication action. The base was refreshed before review and remains the #975 merge. History contains focused implementation,
packaged/documentation qualification, decoder qualification and an additive review correction. Tracker impact is
**Related: #959**, with **no curation/release-index impact**: this partial profile slice neither completes the parent issue
nor changes its release placement. Draft publication is authorized; merge and issue closure are outside this assignment.
The final callback and PR checks supply exact published-head CI/Sonar evidence; no clean remote result is declared in advance.
Hive delivery/archive reconciliation is requested after worker completion; the worker does not self-archive.

### PR Sonar follow-up

The original published head `2c36c6436f336a9ff887905bbcaf19f24b029394` passed the complete
[PR CI run](https://github.com/klum-dsl/klum-ast/actions/runs/40378678725), including separate G3/G4/G5 annotation-propagation
artifact qualification and JUnit reporting. PR Sonar quality gate was **OK**, with **95.6%** new-code coverage,
**0.0%** duplication and **zero hotspots to review**. The analysis nevertheless reported **33 unresolved maintainability
observations**; a passing gate is not recorded as a clean finding result.

An additive follow-up addresses all observations: 27 Groovy guard-brace sites (`groovydre:S8306`), three excessive nesting
sites (`groovydre:S134`), explicit Boolean presence checking (`java:S5411`), moving the conditional inside `List.copyOf`
(`java:S9358`) and removing loop-counter mutation from UTF-16 validation (`java:S127`). Archive traversal, diamond emission
and the disposable profile-probe customizer now have cohesive private test helpers. UTF-16 validation checks both adjacent
halves without modifying the loop counter, accepting the same valid strings and rejecting the same malformed ones.
Actual low-surrogate and broken-pair classfile damage cases supplement the original high-surrogate case. No assertion,
metadata check, profile rule or Sonar profile was weakened; no suppression was added.

Both independent reviewers inspected the additive changes and reported **zero remaining findings**. Final follow-up
validation passed in **6m13s**: **97 focused compiler/runtime cases per G3/G4/G5 lane** and **all 93 packaged JSON Schema
regressions**, plus licenses, lane isolation and plugin validation. Published/reviewed commits remain intact; this is one
consolidated Sonar correction commit. Exact final-head CI/Sonar results are recorded in the PR checks and worker callback.

### Bounded candidate-preservation review of PR #976

The remaining inheritance/exclusion concern was reviewed against published head
`aea47c643d36d0287d3c66e87fafebc40d799d91`, starting with existing tests. No production defect was found.
The existing packaged naming cases already cover first-child recovery of ancestor naming collisions, inherited literal
overrides/ignores, exact interpreter/projector names, independent owned types, complete ordinary-collision rejection,
position-specific construction keys and execution traps. Core tests cover source-free diamonds, source deduplication,
identity pruning, unsupported declarations and exact rejection provenance.

One interaction lacked direct coverage: first-child recovery combined with inherited non-naming rejections. The single
new `CompiledBuilderSchemaTest` feature, `first descendant profile preserves non-naming ancestor exclusions: #profile`,
uses separately compiled ancestor/child JARs with deleted compilation outputs. It compares absent, explicit `IDENTITY`
and first `KEBAB_CASE` descendants. Only kebab recovers the native/literal collision pair; protected, unsupported scalar,
abstract-owned, ignored and unsupported-operation sources retain identical rejection reasons. The ignored literal
overlaps a recovered kebab name, verifying exclusion before collision handling. An initialization trap and positive
control establish that resolution does not execute the producer. Production code and naming semantics are unchanged.

Focused qualification on Java 17 passes **100 compiler/runtime cases per G3/G4/G5 lane**, including the three new rows,
and **10 existing packaged naming cases** (happy paths on G3/G4/G5 plus seven invalid-contract/key-position cases).
Affected test licenses, lane isolation and `git diff --check` pass. Separate Standards and Specification reviewers report
zero actionable findings on both the preservation path and the added regression. This closes the bounded correctness
concern; merge remains a maintainer action after the current PR checks pass. No merge, issue closure or curation change
is part of this review.
