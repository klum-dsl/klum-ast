# #1030 — Multiple explicit literal alternatives

Related: [#1030](https://github.com/klum-dsl/klum-ast/issues/1030), F2.1;
[#1015 inventory](issue-1015-struct-capability-inventory.md).
Base: `91b803b4efbcdb44d4c75f64e82e177eb433abc3`, verified against fetched `origin/master` on 2026-11-05.
Issue #1029 is independent and absent from this base.

Status: implemented and locally qualified at `83eb2a11cd05075f8b9e4077cdb24bccd0601830`.
Hosted qualification, publication, merge and parent-issue reconciliation remain separate delivery conditions.

## Characterization and association gate

Commit `083902eaa` adds executable characterization before implementation: ordinary explicit `rolling`/`fixed`
creator calls and keyed traversal, one-literal Struct reuse, lifecycle and Template recipes, cross-alias type collisions,
escaped source diagnostics and partial failure, plus the original multi-literal rejection. The initial focused Groovy 3
run passed 89 cases, including the existing compiler/metadata/runtime literal-alternative suites. Existing packaged
schema assertions retain the original closed one-alias shape alongside the extension.

The existing compiler-owned alternatives map associates each distinct concrete target with its explicit literal alias.
Each generated CollectionFactory configuration creator has exact owning-field provenance and a public subtype-Builder
return type. No class scan or factory-choice policy is needed. The extension admits the entire finite set only when
every target and generated creator satisfies the original F2-B checks. Unsupported/custom/ambiguous routes exclude the
whole relationship. The compiler's existing duplicate-target rejection is unchanged.

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
`GeneratedInput.LiteralAlternatives` container of those same seven-fact records. The explicit field count equals the
complete finite set. The unified `GeneratedInput.Schema.version` remains authoritative. Validation rejects missing,
corrupt, duplicate or incompatible rows, contradictory annotations and incomplete agreement with exact generated
creator markers, including a removed row with an altered count. Inspection never initializes Schema classes or
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
- Existing single-alias suites remain active; custom Factory and authored/inherited scope exclusions now also exercise
  multiple aliases. Compiler exclusions retain List/Set/generic and other unsupported families.
- `LiteralAlternativeMetadataTest`: source-free initialization traps, inherited/named canonical associations, each exact
  creator and concrete payload, corrupt finite counts/containers/rows, stripped creator provenance and missing-row/count
  corruption. Existing single-record corruption coverage remains.
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
