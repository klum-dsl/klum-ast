# #1030 — Multiple explicit literal alternatives

Related: [#1030](https://github.com/klum-dsl/klum-ast/issues/1030), F2.1;
[#1015 inventory](issue-1015-struct-capability-inventory.md).
Base: `91b803b4efbcdb44d4c75f64e82e177eb433abc3`, verified against fetched `origin/master` on 2026-11-05.
Issue #1029 is independent and absent from this base.

Original qualification: implemented and locally qualified at `83eb2a11cd05075f8b9e4077cdb24bccd0601830`.
The creator-independence reassessment below supersedes the initial creator-provenance policy.
Hosted qualification, merge and parent-issue reconciliation remain separate delivery conditions.

## Characterization and association gate

Commit `083902eaa` adds executable characterization before implementation: ordinary explicit `rolling`/`fixed`
creator calls and keyed traversal, one-literal Struct reuse, lifecycle and Template recipes, cross-alias type collisions,
escaped source diagnostics and partial failure, plus the original multi-literal rejection. The initial focused Groovy 3
run passed 89 cases, including the existing compiler/metadata/runtime literal-alternative suites. Existing packaged
schema assertions retain the original closed one-alias shape alongside the extension.

The compiler-owned literal alternatives map associates each distinct concrete target with its explicit alias and owning
field. Only the complete finite set of qualified concrete nongeneric targets sharing the abstract base's String key is
admitted. No public creator or collection-closure method is selected for Struct execution; customizations of those
convenience APIs do not exclude the field relationship. Unsupported or ambiguous target facts exclude the whole
relationship. The compiler's existing duplicate-target rejection is unchanged.

## Delivered branch behavior

```groovy
@Field(alternatives = { [fixed: FixedElement, rolling: RollingElement] })
Map<String, Element> elements

// Inside the Catalog body, caller-supplied Map order visits b, c, then a:
[elements: [rolling: [b: [:], c: [:]], fixed: [a: [:]]]]
```

Each alias has its exact concrete keyless body, authoritative outer String identity and ordinary explicit-type
fresh/reuse/collision behavior. Alias and keyed-entry iteration follow supplied Maps; metadata and schema order are
canonical and do not control execution. This promises no portable parser order or repeated/interleaved alias events.
Child bodies, first-failure effects, source-pointer escaping, ownership, lifecycle and deferred Template recipes use
the existing construction engine. One collection scope now encloses the grouped traversal, matching ordinary Groovy
construction paths and type-collision attribution for multiple children. Previously the Struct route entered that scope
once per child. No new public Factory/Builder signature, parser API, selector, producer or CollectionFactory operation
is introduced. List/Set alternatives, concrete-base/alias coexistence, automatic names, generic targets and composition
remain outside this slice.

Single associations retain `GeneratedInput.LiteralAlternative`; multiple associations use an inert
`GeneratedInput.LiteralAlternatives` container of the same five-fact field/alias/target/key/key-type records. The explicit
field count equals the complete finite set. The unified `GeneratedInput.Schema.version` remains authoritative.
Validation rejects missing, corrupt, duplicate or incompatible rows and contradictory field/role/key associations. Inspection never initializes Schema classes or
executes user code. Use matched compiler/runtime/plugin artifacts and recompile participating Schemas; no mixed-toolchain
qualification is claimed.

The source-free projector consumes the same validated associations. Every optional alias references its own concrete
keyless body; the alias object remains closed. Schema intentionally permits the same instance key in distinct groups:
existing-child type compatibility, partial effects and lifecycle are runtime behavior, beyond JSON Schema structure.

## Executable coverage

- `StructuredMultipleLiteralAlternativeTest`: ordinary/grouped order and exact concrete bodies, construction-path
  parity, keyed identity, cross-alias collisions in both orders, Factory/active Builder/Template input, empty groups,
  precise second-group failures, lifecycle causes and cyclic-input cleanup. The linked documentary example carries
  `@Issue('1030')`, `@Tag('documentary')` and `@See`.
- Existing single-alias suites remain active; custom Factory and authored/inherited scope cases now prove that ordinary
  Groovy keeps its method behavior while Struct uses the declared field/target, for one or multiple aliases. Compiler exclusions retain List/Set/generic and other unsupported families.
- `LiteralAlternativeMetadataTest`: source-free initialization traps, inherited/named canonical associations, each exact
  field association and concrete payload, and corrupt finite counts/containers/rows. Existing single-record corruption coverage remains.
- `BinaryOwnedRelationshipsTest`: separate producer/API/Schema/client artifacts with sources removed, interpretation and
  actual plugin projection across Groovy 3/4/5; both single and multiple association stripping prevent new output.
- `JpmsPackageBoundaryTest`: actual Java/static Groovy multi-alias consumers in the existing classpath and named-module
  lanes. Existing module descriptors and public generated interfaces are unchanged.
- `validate_literal_alternative_schema.py`: pinned Draft 2020-12 validation of actual plugin output for the original
  one-alias relationship and the additional multi-alias relationship, including distinct concrete bodies and the
  structural/runtime identity boundary.

## Validation and review

Java 17, progressively verified:

```shell
./gradlew :klum-ast:test --tests '*LiteralAlternative*Test' --no-parallel --max-workers=2
./gradlew :klum-ast:test --tests '*LiteralAlternative*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy4Tests --tests '*LiteralAlternative*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy5Tests --tests '*LiteralAlternative*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' --no-parallel --max-workers=2
./gradlew check --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
./gradlew renderLocalDocumentation -PdocumentationVersion=4.2.0-tracer \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
python klum-ast-gradle-plugin/src/test/resources/validate_literal_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

The final unfiltered root `check` passed at `83eb2a11cd05075f8b9e4077cdb24bccd0601830`:
**11,212 module JUnit cases, zero failures/errors, 49 existing skips**. Every literal-alternative suite passed in each
Groovy 3/4/5 lane: **151 cases per generation, no failures/errors/skips**. Core totals are 3,154 cases in Groovy 3
(16 existing skips), and 3,156 in each Groovy 4/5 lane (15 existing skips each). The plugin suite passed all 666 cases,
including source-free multi-alias interpretation/projection and association corruption. Runtime, annotations, Jackson,
bean-validation, test-support, applicable JPMS, license, coverage and lane-isolation checks also passed.

The emitted-schema oracle uses `jsonschema==4.23.0`: **36 valid instances accepted and 195 invalid instances rejected**
across the three actual plugin emissions. All emissions are byte-identical across Groovy 3/4/5, with SHA-256
`6ea7fe27f9d88fa4a08276c51812a2d5244417a482f53f5f1d9186223bf55c3b`; the final full run reproduces the earlier hashes.

Documentation rendered and crawled successfully at the same checked revision. The local site contains **448 HTML files**;
its source and renderer manifests both identify `83eb2a11cd05075f8b9e4077cdb24bccd0601830`. Markdown fences, relative-file
links and `git diff --check` pass. The final evidence-only commit changes no qualified production, tests or user pages.
No local SonarCloud upload or hosted qualification is claimed.

An initial combined `check renderLocalDocumentation -PdocumentationVersion=4.2.0-tracer` invocation failed the existing
renderer fixture's default-version assertion. It is not qualification. Running `check` with its default properties and
rendering separately, as shown above, passed without a source repair.

Exactly two declared read-only local review agents reviewed the task against base
`91b803b4efbcdb44d4c75f64e82e177eb433abc3`, with follow-ups on the final implementation:

- **Standards:** P3 repeated semantic role extracted to a constant; optional fixture booleans replaced by an explicit
  finite count, and association validation isolated for clarity. Final follow-up: no unresolved findings.
- **Specification:** P2 eager-validation gap repaired: every alias's child and nested contracts are now planned before
  construction, including omitted relationships. A secondary-alias nested-corruption/init-trap regression proves it.
  Final follow-up: no unresolved findings.

Review children performed no edits, builds, tests or remote mutations and delegated no further work. History review
retains the passing characterization, bounded implementation, synchronized documentation, and final evidence as distinct
reasoning steps; no history rewrite is needed before publication.

## Tracker impact and reconciliation

Draft PR relationship: **Related: #1030**. Neither #1030 nor #1015 is closed or mutated. The repository inventory records
only this branch's bounded implementation; its other dispositions and release gates remain with Hive. User pages,
existing migration navigation, ADR 0032 and `CHANGES.md` describe the extension; no new page navigation is required.
Request Hive reconciliation after delivery; this worker does not self-archive.


## Recovery integration before publication — 2026-11-05

Recovered the existing branch at `dd9f29c97`; no replacement implementation,
branch or worktree was created. The original full-check and two-axis review results above remain evidence for that
original tree, not a full check of the integration tree.

Before publication, `master` advanced to `669418b0d30a235d951165a05e4bd741094a99d9` (merged PR #1029 / issue #1028).
Rebased this same branch onto that exact revision. Production merged automatically. Mechanical conflicts retained both
changelog entries, both bounded inventory/user-guide contracts, both binary-fixture inputs and both driving issue
annotations. `git range-diff` confirms that these are the only adaptations to the recovered #1030 patch. No substantive
#1030 production repair, new design or additional review children were required.

The integrated implementation/test/user-documentation revision is
`0c3f35c7b526b514f9b68ea05b97125bf4f8367c`. At that revision, Java 17 validation passed:

```shell
./gradlew :klum-ast:test --tests '*LiteralAlternative*Test' --tests '*StructuredDerivedMap*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy4Tests --tests '*LiteralAlternative*Test' --tests '*StructuredDerivedMap*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy5Tests --tests '*LiteralAlternative*Test' --tests '*StructuredDerivedMap*Test' --tests '*JpmsPackageBoundaryTest' \
  :klum-ast-runtime:test :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' license \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
./gradlew renderLocalDocumentation -PdocumentationVersion=4.2.0-tracer \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
```

- Core integration: **197 cases in each Groovy 3/4/5 lane**, no failures/errors/skips. This includes literal alternatives,
  native derived Maps and Java/static Groovy JPMS consumers.
- Runtime baseline: **73 cases**, no failures/errors, one existing skip.
- Source-free combined binary fixture: **8 cases**, no failures/errors/skips; interpretation, projection and corruption
  checks retain both #1028 and #1030 inputs. License checks pass.
- The pinned `jsonschema==4.23.0` oracle, run with system Python matching its retained native dependency, accepts
  **36 valid instances** and rejects **195 invalid instances** across the three fresh emissions. Their common SHA-256 is
  `e8507be8deeb8f30d28157f9d0379b3f1e7bb6e873baac3f44220c5bb5b9b3bb`.
- Documentation rendering/crawl passes with **448 HTML files** across the local-review site. Source and renderer
  manifests both identify `0c3f35c7b526b514f9b68ea05b97125bf4f8367c`.

An initially broader recovery rerun was deliberately interrupted to select this integration scope; it supplies no
qualification. The original full root check is preserved separately above. Hosted CI/SonarCloud remain pending remote
qualification. This final recovery-evidence update changes no production, tests or rendered user documentation.

Delivery preflight: Git SSH dedicated-branch push **authorized** by dry run; GitHub App repository write capability
**authorized**, checked independently from the GitHub CLI repository capability (**authorized**). Tracker relationship
remains **Related: #1030**. The feature PR retains the repository F2.1 inventory update; no additional release gate,
issue state or curation disposition is selected. Request Hive reconciliation once the draft delivery is open.


## Creator-independence reassessment — PR #1032

Authority: the maintainer's focused reassessment of #1032/#1030 under #1021 and ADR 0032, following merged PRs
[#1020](https://github.com/klum-dsl/klum-ast/pull/1020), [#1023](https://github.com/klum-dsl/klum-ast/pull/1023),
[#1024](https://github.com/klum-dsl/klum-ast/pull/1024) and [#1029](https://github.com/klum-dsl/klum-ast/pull/1029).
Comparison point: published head `6f04620f5013a2662c2def2c61919e8636b75ac9`. Reviewed history is preserved; this is an
additive follow-up on the same branch.

### Minimal executable contract

| Decision | Authority and execution use |
| --- | --- |
| Alias | The exact compiler-owned alias in the field's association map; `executeAliasGroups` looks up the supplied String, without class or method-name discovery. |
| Concrete child type | Each record's target and its validated generated Builder self type, assignable to the declared abstract Map value. |
| Construction key | The supplied inner Map String key; the recorded inherited key member agrees with both target and base contracts. Child bodies exclude that key. |
| Destination relationship | The owning accessor's existing `Field` plus `Selection(field, LITERAL_ALTERNATIVE_MAP)` association. `Entry.source` retains the original field despite input renaming. |
| Runtime construction | `executeChildren` calls `addNewDslElementToMapWithConfiguration(field, target, true, key, child::execute)` directly. No public CollectionFactory or outer closure method is invoked. |

`addNewDslElementToMapWithConfiguration` either reuses a compatible existing Builder and applies configuration, or calls
`FactoryHelper.prepareNestedBuilder` and then normal `doAddElementToMap`. The existing engine owns identity/type
collisions, configuration/PostApply timing, attachment/ownership, Construction-session fences, Template recipes and
first-failure partial state. `StructuredInputSupport.planEntry` still plans every alias and its nested contracts before
construction, including omitted secondary groups. Interpretation and projection retain the same validated concrete
payloads. Neither execution nor the projector changes in this follow-up.

### Removed convenience-API dependencies

- `validateAlternativeCompleteness`: creator-set equality supplied no execution authority. A removed unrelated creator
  only made a valid field fail; it could not select a wrong alias or subtype. The closed alias map already makes selection
  unambiguous.
- Per-creator descriptor lookup, signature/return checks, creator selection markers, CollectionFactory class/scope
  validation and outer `DelegatesTo` checks. Those methods are never called by Struct.
- Compiler `literalCreator` and generated/outer-method admission gates, including the inherited closure-override
  exclusion. Literal association capture now depends on the declared alternatives and qualified field/target facts.
- Unused `scope`/`creator` members in `GeneratedInput.LiteralAlternative`, their reader/internal-record fields and the
  retained reflective creator handles. There is no replacement registry or compatibility layer. Matched artifacts and
  Schema recompilation retain their existing requirement.

The existing owning-field/role association is emitted on the accessor, as for #1021 cut 3; runtime entries retain no
unused public methods. Required checks remain: exact public field declaration and classfile origin, field/role agreement,
finite row count, distinct aliases/targets, qualified Map shape, concrete nongeneric subtype/Builder agreement, shared
String-key identity, and eager child-contract validity. Without them, Struct could bind a different field, select a
wrong/incompatible child or construction key, or perform lifecycle effects before discovering a corrupt secondary
contract. Compiler provenance is carried by the field and literal rows; a second proof via unused methods is redundant.

The old test that deleted a row *and* changed its count to match is removed: that coherently rewrites the authoritative
alias vocabulary rather than introducing an execution ambiguity. Ordinary missing-row/count mismatches and stripped
associations still reject. A coherently changed single alias is likewise not compared with an unrelated method name;
multiple contradictory aliases/targets still reject. No adversarial-metadata validation framework is substituted.

### Concrete regressions and bounded qualification

The unchanged production implementation first failed seven regression cases: absent generated public creators/outer
methods caused `Missing exact literal alternative creator`, and real customized Factory or authored/inherited outer
methods excluded the declared relationship. They pass after the simplification.

- `StructuredMultipleLiteralAlternativeTest#'structured aliases execute without public creator or collection scope methods'`
  removes real compiled public methods, then proves concrete selection, input order, identity reuse, ownership, lifecycle
  and Factory/active Builder/Template execution.
- `StructuredLiteralAlternativeTest` compares ordinary custom Factory and same-class/inherited closure behavior against
  Struct's declared field/target route, for both one and multiple aliases. Ordinary method markers run only on Groovy calls.
- `LiteralAlternativeMetadataTest` retains source-free initialization traps, duplicate/incompatible records, wrong field
  and key, incomplete counts/rows, contradictory sequence facts and the secondary-alias nested corruption regression.
  Obsolete creator/provenance-only corruption cases are removed.
- `BinaryOwnedRelationshipsTest#'binary literal relationships execute and project unchanged without public creators with Groovy #generation'`
  strips public methods from a packaged Schema JAR with sources absent, executes Factory/Builder/Template consumers and
  compares both generated schema files byte-for-byte before and after stripping, separately for Groovy 3/4/5.

Java 17 focused compiler/runtime/relationship/JPMS matrix: **158 cases per Groovy lane**, no failures/errors/skips.
Runtime baseline: **73 cases**, no failures/errors, one existing skip. Packaged binary suite: **11 cases**, no
failures/errors/skips. License and diff checks pass. The fresh `jsonschema==4.23.0` oracle accepts **36 valid** and rejects
**195 invalid** instances. All three schema emissions retain SHA-256
`e8507be8deeb8f30d28157f9d0379b3f1e7bb6e873baac3f44220c5bb5b9b3bb`.

The focused run exposed an ignored-field selection-order regression during development; restoring the previous ignored
selection boundary repaired it. A new packaged-test filename interpolation error was corrected before the passing run.
Neither failed attempt is qualification. The removed annotation members span compiler/runtime/plugin consumers, so the
risk matrix warrants a full root `check` on the committed follow-up; its result and final documentation rendering are
recorded in the final qualification update.

Two local review passes, **Standards** and **Specification**, are performed against the published comparison point.
The prior no-subworkers instruction is retained; no additional reviewer agents are created. Remaining general metadata
validation and the representative payload in the shared Entry shape are outside the creator-completeness question;
neither gates unused convenience methods. There is no remaining creator-completeness registry or validation mechanism.


### Final creator-independence qualification

Committed implementation/test/user-documentation revision:
`eababff1a564691cc7aa0db597554a73b74e97dc` (`Use field associations for literal Struct alternatives`).
The unfiltered Java 17 root `check` passed at this revision: **11,296 module JUnit cases**, **zero failures/errors**,
**49 existing skips**. Core totals: Groovy 3 **3,181 / 16 existing skips**; Groovy 4 and 5 each
**3,183 / 15 existing skips**. The complete plugin suite passes **669 cases**, including all eleven packaged relationship
cases. Runtime, annotations, Jackson, Bean Validation, test support, JPMS, lane isolation, coverage, license and applicable
renderer/workflow fixtures also pass. The root invocation used the normal unfiltered `check`, Java 17, a 2 GiB daemon,
`--no-parallel --max-workers=2`; an ephemeral init script logged test progress without changing task selection or filters.

Documentation rendering and crawling pass at the same committed revision, with **448 HTML files** across the local-review
site. Source and renderer manifests both identify `eababff1a564691cc7aa0db597554a73b74e97dc`; the recorded user tree is
`3740135e372980fdf173adadf860ce21eae90f95`. Markdown fences, **200 relative file links** and diff checks pass.
The fresh full-check emissions still accept **36 valid** and reject **195 invalid** oracle instances, with unchanged
cross-Groovy schema SHA-256 `e8507be8deeb8f30d28157f9d0379b3f1e7bb6e873baac3f44220c5bb5b9b3bb`.

Final local reviews against published comparison point `6f04620f5013a2662c2def2c61919e8636b75ac9`:

- **Standards:** zero findings. Removed machinery is local to literal alternatives; public types use imports, existing
  accessor association logic is reused, tests carry their driving issues, and reviewed history is preserved.
- **Specification:** zero findings. Real absent/customized/overridden convenience-method cases execute; alias/type/key/
  ownership/order/lifecycle/Template/failure and secondary-contract controls remain active; no additional grammar,
  runtime dispatch policy, registry or validation framework is added. Byte-identical schema output is proven both before/
  after method stripping and against the previously qualified emissions.

These are two local review passes by this worker, not additional independent reviewer agents. #1021's separate
virtual-child descriptor cleanup (cut 4) remains outside this reassessment. No other creator-completeness concern remains
in F2.1. The final evidence-only commit changes no qualified production, tests or rendered user pages.

Delivery channels were checked separately: Git SSH dedicated-branch update **authorized** by dry run; GitHub App PR
update/comment capability **authorized**. No local SonarCloud upload or hosted final-head result is claimed here.
Publish the additive commits to the existing draft PR #1032, then report exact-head CI/Sonar in its consolidated follow-up.
Issue #1030 remains open; no merge or self-archive is performed. Request Hive reconciliation after this delivery update.


## SonarCloud maintainability repair — PR #1032

The completed hosted analysis at `9bc8246914e50e4a599faa58daea80f9e7817f34` passed its quality gate but reported six
new maintainability issues. All six are addressed without suppressions or behavior changes:

| Sonar issue | Repair |
| --- | --- |
| `AaGr4JbYFZ2Ki6HCUYq-` — repeated `keyMapping` literal | One `KEY_MAPPING_MEMBER` constant supplies the existing three annotation-member lookups. |
| `AaGr4JlkFZ2Ki6HCUYq_` — nested ordinary collision setup | Extract `addRollingCollision`; the same generated collection scope and alias operation execute. |
| `AaGr4JsVFZ2Ki6HCUYrC` — complex method-annotation visitor | Extract literal-alternative annotation dispatch/conflict handling into `alternativeAnnotation`; retention, duplicate and conflict checks are unchanged. |
| `AaGsI6Jp887SUh04PCyc` — eight resolver parameters | Derive the field name from the already-associated `Literals` record instead of passing the same name separately. |
| `AaGr4JllFZ2Ki6HCUYrA` — nested cross-alias comparison setup | Extract `configureCrossAliasCollision`, retaining one ordinary collection scope and the same structured input, both alias orders and error comparison. |
| `AaGr4JsVFZ2Ki6HCUYrB` — repeated `value` literal | Reuse the reader's existing `VALUE` constant. |

Java 17 affected compiler/runtime/relationship/JPMS selection passes **158 cases per Groovy 3/4/5 lane**, without
failures/errors/skips. Runtime baseline passes **73 cases**, with one existing skip. All **11 packaged relationship
cases** pass. License and diff checks pass. The fresh emitted-schema oracle again accepts **36 valid** and rejects
**195 invalid** instances.

This is a localized maintainability refactor: the metadata carrier, generated signatures, interpreter, construction
engine, projector and build topology are unchanged. The prior unfiltered local/hosted full checks remain historical
qualification; risk-based local validation here targets the extracted visitor/resolver/test seams and all supported
Groovy generations rather than repeating the full root check. User behavior, user pages, migration and changelog need
no further change. No local SonarCloud upload is used.

Local Standards and Specification passes find no remaining findings in this four-file repair. Reviewed commits are
preserved, and no reviewer subworkers are created. The repair is published as an additive commit; a fresh hosted analysis
must confirm the result on its exact head. The consolidated PR follow-up records that final CI/Sonar outcome. No merge,
issue closure or self-archive is authorized by this repair.
