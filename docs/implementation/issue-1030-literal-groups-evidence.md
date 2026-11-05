# #1030 — finite explicit literal alias groups

Date: 2026-11-05. Related: [#1030](https://github.com/klum-dsl/klum-ast/issues/1030),
[#1015 inventory](issue-1015-struct-capability-inventory.md),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#finite-explicit-literal-groups--1030-2026-11-05).
Fetched master/base: `91b803b4efbcdb44d4c75f64e82e177eb433abc3`.

Status: implementation and local code qualification complete; documentation rendering and final review follow-ups pending. Hosted CI/SonarCloud and merge
are separate delivery conditions. The issue remains open for Hive reconciliation.

## Characterization before admission

Commit `3370b0b85` adds executable characterization with unchanged production code. The focused Groovy 3 command ran
89 cases, zero failures/errors/skips:

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :klum-ast:test \
  --tests '*LiteralAlternative*Test' --tests '*StructuredLiteralGroupsTest' \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
```

The existing F2-B tests characterize exact key/alias/body selection, inner-identity rejection, order and partial
reconfiguration, diagnostics/escaping, ownership, lifecycle failures, Templates, cycles and public entry guards.
The new tests characterize ordinary two-alias call order and keyed reuse, cross-alias collisions and retained earlier
state, ordinary/single-literal Template parity, single-literal failure order/source pointers, and the original
multi-alias relationship rejection (even when input supplies only one alias). The rejection test is replaced by the new
acceptance example in the implementation commit; its executable historical form remains in the characterization commit.

The initial implementation run passed all original one-literal F2-B tests and failed only the historical rejection assertion because
that relationship became admitted. Expanded characterization then exposed the existing per-child Struct collection
breadcrumb scope: later children can carry `elements[2]`, `elements[3]`, whereas one ordinary Groovy collection block
uses `elements`. This attribution behavior is retained explicitly, not repaired or presented as exact path equality.
Ordinary incompatible-type failures retain their type/cause and this existing construction-path distinction; reached
Struct payload errors retain the exact source name and escaped alias/key/member pointer.

An attempted baseline plugin method filter selected no tests; it is not qualification. The full
`BinaryOwnedRelationshipsTest` supplies the executable schema control: its unchanged one-literal `elements` assertions
remain alongside a separate multi-alias `groupedElements` relationship, including concrete keyless definitions.

## Implementation boundary

The compiler records every explicit alias at the existing CollectionFactory generation seam. The original abstract,
keyed, nongeneric `Map<String, Element>`/default-owned/inherited-String-key restrictions apply to every candidate.
An incompatible target or occupied/custom creator excludes the entire relationship; no partial vocabulary is admitted.
The compiler sorts aliases for deterministic metadata. One alias retains its original annotation encoding; multiple
aliases use the internal repeatable `LiteralAlternatives` container and exact `Field.alternativeCount`.
The unified `GeneratedInput.Schema.version` remains the sole version authority. Use matched compiler/runtime/plugin
artifacts and recompile participating Schemas. Older readers fail closed on the new positive count.

The classfile reader verifies all seven facts of each association, container shape/retention, exact count, unique aliases
and targets, owning field and exact marked public creator. No Schema initialization, alternatives-closure execution or
subtype scanning is used. The immutable compiled entry maps each alias to its concrete body. Pre-construction contract
verification follows every selected concrete contract, including omitted input groups. Projection consumes the same
validated set and emits closed alias properties and concrete keyless bodies in canonical alias order.

Runtime traverses the supplied outer Map and then each inner Map in iteration order. Each reached group selects its
exact body and the unchanged ordinary explicit-type keyed callback before child PostApply. Earlier effects and attached
children survive failure; a failed fresh child remains unattached. Compatible children retain identity, and incompatible
cross-alias keys fail through ordinary explicit-type behavior. Templates retain ordinary independent recipe-copy and
recipient ownership semantics. Empty/omitted groups make zero child calls.

No parser-order portability, duplicate-key parser policy, repeated/interleaved event grammar, List/Set alternatives,
concrete-base coexistence, inferred aliases, generic alternatives, producer/CollectionFactory expansion, parser API or
composition is introduced. Existing concrete keyed Map and single-literal syntax remain supported.

## Executable coverage and documentation

- `StructuredLiteralGroupsTest`: ordinary and Struct group/entry order, exact subtype body, keys/owners, keyed identity,
  both collision directions, source pointers/escaping, later-group payload/lifecycle failures, Templates and empty groups.
- `LiteralAlternativeMetadataTest`: source-free inherited canonical associations and multi-row stripping/count/alias/
  target/field/key/creator/container/retention corruption before initialization; the original single-row matrix remains.
- `LiteralAlternativeCompilationTest`: unchanged exclusions plus mixed generic/non-generic and multi-alias List/Set rejection.
- `StructuredLiteralAlternativeTest`: original single-literal controls, plus one custom Factory creator occupying a route
  prevents admission of the complete two-alias relationship.
- `BinaryOwnedRelationshipsTest`: separate producer/API/Schema/client artifacts, sources removed, actual plugin projection
  and fresh runtime interpretation for Groovy 3/4/5; single/group association corruption prevents all new schema outputs.
- `validate_literal_alternative_schema.py`: actual emitted single/group schemas, including distinct concrete body members,
  unknown aliases, malformed envelopes and keyless bodies. Cross-alias collisions are runtime behavior, not schema policy.

The documentary feature is `StructuredLiteralGroupsTest#'configures closed literal alias groups in supplied order'`,
with `@Issue('1030')`, `@Tag('documentary')` and `@See` linking
[the user example](../user/Structured-Input.md#multiple-literal-alias-groups). Structured Input, Alternatives, JSON Schema,
Migration, CHANGES and the #1015 inventory are synchronized. No new page requires sidebar navigation changes.

## Qualification and review

The unfiltered root check passed on clean revision `641efe27c34d920b3263f87d1e993a0267a4d2a5`:
**11,119 module JUnit cases, zero failures/errors, 49 existing skips**. This includes the affected compiler/runtime/plugin
suites, separate source-free Groovy 3/4/5 producer/consumer tests, Java/static Groovy classpath and Groovy 4/5 named-module
checks, test-support publication checks, lane isolation, license and applicable static checks.

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew check \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
```

The compiler suite passed 3,123 baseline cases (16 existing skips) and 3,125 cases in each Groovy 4/5 lane
(15 existing skips per lane). The full plugin suite passed 666 cases without skips. The actual emitted-schema oracle
passed against all three fresh emissions: **39 valid instances accepted and 117 invalid instances rejected**, using
pinned `jsonschema==4.23.0`:

```shell
python klum-ast-gradle-plugin/src/test/resources/validate_literal_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

Exactly two read-only local review children ran against base `91b803b4efbcdb44d4c75f64e82e177eb433abc3` through
`641efe27c34d920b3263f87d1e993a0267a4d2a5`. Neither edited, built, tested, mutated remote state or delegated.

- **Standards:** one P3 finding required braces around the nested annotation-conflict guard. The follow-up braces that
  guard and extracts the same check/visitor choice into a private helper, keeping annotation dispatch flat. It changes
  no protocol, selection, diagnostics or execution behavior.
- **Specification:** no actionable findings, including no new selection policy or excluded grammar expansion.

After that localized review cleanup, focused compiler/runtime/metadata tests passed **120 cases in each Groovy 3/4/5
lane, zero failures/errors/skips**. The full runtime baseline also passed **73 cases, zero failures/errors, one existing
skip**. License and diff checks passed. The earlier complete-build results remain applicable to unchanged consumers,
projection and generation; no second full run is claimed for this guard-only extraction.

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew \
  :klum-ast:test --tests '*LiteralAlternative*Test' --tests '*StructuredLiteralGroupsTest' \
  :klum-ast:groovy4Tests --tests '*LiteralAlternative*Test' --tests '*StructuredLiteralGroupsTest' \
  :klum-ast:groovy5Tests --tests '*LiteralAlternative*Test' --tests '*StructuredLiteralGroupsTest' \
  :klum-ast-runtime:test licenseMain licenseTest \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
```

Both original review children will inspect the committed cleanup; documentation rendering/crawl will run from that clean
revision. The changed Markdown paths/anchors and balanced fences passed (258 local references), as did `git diff --check`.
The commit sequence is reviewed as characterization, bounded implementation, synchronized documentation, and an additive
review cleanup; the reviewed commits are preserved.

No local SonarCloud upload is used. Tracker impact is a draft PR with `Related: #1030`; no issue edits, closure, labels,
milestones or curation mutation are authorized. Documentary cross-linkage is supplied through this record and the PR
because the assignment explicitly prohibits editing #1030. Hive owns final acceptance, issue reconciliation and archive
safety. Hosted CI/SonarCloud belongs to the exact published PR head and remains a separate qualification condition.
