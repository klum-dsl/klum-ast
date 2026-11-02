# F2-B — one literal CollectionFactory structured-input family

Date: 2026-11-02. Related: #952. Authoritative base: `ed199e0eb319fbd4927c197121864422a08a40e9`.

Status: implemented and locally qualified. Validation and review are recorded below; hosted checks and merge are separate delivery
conditions. This slice leaves the broader grammar coordination issue open.

## Accepted behavior

```groovy
@DSL class Catalog {
    @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
}
@DSL abstract class Element { @Key String name; @Owner Catalog owner; String value }
@DSL class ConcreteElement extends Element {}

Catalog.Create.With { elements { fixed('primary') { value 'configured' } } }
Catalog.Create.FromStruct([elements: [fixed: [primary: [value: 'configured']]]])
```

Only this family is admitted: one explicit compiler-literal alias, a default owned `Map<String, abstract keyed DSL type>`,
a concrete nongeneric target inheriting the same String key, and an exact generated public alias creator. Automatic
names, multiple alternatives, generic targets, Class/Factory input selectors, unkeyed collections and custom key mapping
remain excluded. Existing concrete keyed Map grammar is unchanged.

The outer String instance key is authoritative before PostCreate. Equal/conflicting inner construction-key members
reject when reached. Empty relationship/alias mappings perform zero child calls. Caller traversal order is preserved;
earlier effects remain after a reached failure, a failing fresh child is not attached, and later body/child/root
operations do not run. Existing compatible children retain identity and partial reconfiguration effects. Incompatible
existing types use the ordinary explicit-type failure. Template input retains ordinary recipe lifecycle.

## Implementation and inspection boundary

The compiler freezes inert facts at the existing CollectionFactory generation site, then enriches the public owning
field accessor with `GeneratedInput.LiteralAlternative` and explicit `Field.alternativeCount` (zero or one). The unified
`GeneratedInput.Schema.version` is the sole metadata protocol version; the alternative annotation has no independent
version member. Its field, literal alias, concrete target, inherited key/String authority and exact scope/configuration-creator
descriptor are validated from classfiles. Removed/unknown members, including a legacy annotation-specific `version`, reject;
participating Schemas must be recompiled with matching compiler/runtime/plugin artifacts. Missing/stripped/conflicting facts fail before construction, even if input omits the relationship.
Metadata extraction neither initializes Schema classes nor executes an alternatives closure or user factory. The exact
public configuration creator also carries a field/role marker. Admission requires its implementation to be the
owning-field-linked generated relationship creator; matching custom Factory signatures are insufficient authority. Same-class authored scope replacements and inherited
collection-scope overrides are excluded so direct configuration cannot bypass their ordinary behavior.

`CompiledBuilderSchema.Entry` carries the validated association alongside its existing ownership payload and key facts.
The interpreter visits alias, instance key and concrete body in order, includes envelope containers in cycle detection,
and binds `addNewDslElementToMapWithConfiguration` before child PostApply. It uses the existing explicit-type route,
including ownership, reuse, failure and breadcrumb behavior. Public entry guards remain in force. No public receiver or
Consumer capability, synthetic closure, production private-method reflection or second construction engine is added.

The projector consumes the same decoded entry: a closed alias object, one recorded alias property, then String instance
keys referencing the concrete keyless body. It adds no discriminator, arbitrary polymorphism or unconstrained child body.
JSON Schema describes structure; it does not promise lifecycle success, ordering, partial effects or duplicate YAML-key
detection. The parser owns duplicate-key rejection.

## Executable coverage

| Seam | Evidence |
| --- | --- |
| Runtime and documentary contract | `StructuredLiteralAlternativeTest`, including exact concrete type, key-before-create, owner/lifecycle trace and ordinary breadcrumb parity; malformed envelopes, equal/conflicting inner keys, strict body types, escaping, partial order, reuse/type mismatch, lifecycle cause, cycles/cleanup, session/thread/sealing, Templates, arbitrary String keys and custom Factory collision exclusion |
| Source-free metadata | `LiteralAlternativeMetadataTest`, including initialization traps, exact public descriptor, count/association stripping and corruption, unified Schema version/type/member/visibility errors, naming/exclusion, inheritance, creator-marker corruption and compiler shadowing rejection |
| Compiler exclusion | `LiteralAlternativeCompilationTest`, checking admission before bytecode generation for multiple/automatic aliases, List/non-String Map/custom mapping, generic targets and unchanged concrete keyed-Map syntax; excluded generic shapes receive no ordinary DSL qualification |
| Packaged plugin/binary consumers | `BinaryOwnedRelationshipsTest`, separate producer/API/Schema/client artifacts with sources removed, fresh runtime consumers and actual plugin emission for Groovy 3/4/5; corrupted association prevents every new output and leaves initialization traps silent |
| JPMS | `JpmsPackageBoundaryTest` real Java and static Groovy artifact consumers on classpath for Groovy 3/4/5 and module path for Groovy 4/5, including literal key/body/owner assertions and existing descriptor/package checks |
| Actual emitted schema | `validate_literal_alternative_schema.py`, pinned `jsonschema==4.23.0`: 3 emitted schemas, 24 valid inputs accepted and 75 invalid inputs rejected |

## Initial F2-B qualification

Java 17; progressive local checks:

```shell
./gradlew :klum-ast:test --tests '*LiteralAlternative*Test' --no-parallel --max-workers=2
./gradlew :klum-ast:groovy4Tests --tests '*LiteralAlternative*Test' \
  :klum-ast:groovy5Tests --tests '*LiteralAlternative*Test' \
  :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' --no-parallel --max-workers=2
./gradlew :klum-ast:test --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy4Tests --tests '*JpmsPackageBoundaryTest' \
  :klum-ast:groovy5Tests --tests '*JpmsPackageBoundaryTest' \
  :klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest' --no-parallel --max-workers=2
./gradlew check --no-parallel --max-workers=2
python klum-ast-gradle-plugin/src/test/resources/validate_literal_alternative_schema.py \
  klum-ast-gradle-plugin/build/schema-validation
```

Focused checks passed in all three Groovy generations: **72 cases per lane, no failures/errors/skips** after review fixes.
The final unfiltered root `check` passed on clean revision `1af87607fa0d004e1ed181fb36b286c0ea2844fd`:
**10,275 module JUnit cases, zero failures/errors, 49 existing skips**. This includes the affected compiler/runtime/plugin
suites, packaged binary consumers and Java/static Groovy JPMS qualification. The emitted-schema oracle accepted 24 valid
and rejected 75 invalid inputs across three actual plugin emissions; the existing virtual-scope oracle also passed.

Current user documentation rendered and crawled successfully at that same revision using:

```shell
./gradlew renderLocalDocumentation -PdocumentationVersion=4.2.0-tracer \
  --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --no-parallel --max-workers=2
```

The verified local tree contains 447 HTML files. Changed-page local links/anchors and `git diff --check` pass. The final
evidence-only commit leaves production, tests and the rendered user-documentation tree unchanged.

Earlier attempts are not counted as qualification: a mid-run Git-derived version change invalidated TestKit fixture
coordinates, and the next full run exposed 21 catalog-fixture failures from choosing the first reflective constructor.
The fixture now selects the unique matching arity; its targeted retry and the final unfiltered full run pass. A warmed
Gradle daemon exhausted its heap after the first documentation command; a fresh 2 GiB single-use daemon completed the
render/crawl with exit status zero. The generated heap dump was removed.

## Local review

Exactly two read-only local review children ran against base `ed199e0eb319fbd4927c197121864422a08a40e9`
through `424008ca8672c9373da5417a44f2aa78c67bfa96`, with read-only follow-ups on the resulting fix tree and the catalog-fixture repair through
`1af87607fa0d004e1ed181fb36b286c0ea2844fd`.
The root plus both reviewers used three concurrency slots. Neither reviewer edited, built, tested, changed remote state
or delegated further work.

- Standards: one P3 ADR status-summary inconsistency was corrected. Final follow-up found no remaining findings.
- Specification: P2 custom Factory creator collisions and a related P2 collection-scope override bypass were corrected
  with owning-field provenance, an exact public configuration-creator marker, bounded override exclusion and executable
  ordinary-versus-structured regressions. Final follow-up found no remaining substantive findings.
No local SonarCloud upload is used. Final hosted CI/SonarCloud belongs to the exact pull-request head.

User documentation: [Structured Input](../user/Structured-Input.md#literal-collection-alternative),
[JSON Schema](../user/Json-Schema.md#literal-collection-alternative),
[Migration](../user/Migration.md#literal-collectionfactory-alternative-input), and
[Alternatives](../user/Alternatives-Syntax.md#structured-input). Existing page navigation remains valid.

## Unified-version simplification — PR #1017

The mechanical follow-up removes only `LiteralAlternative.version`, its compiler emission and its reader validation.
`GeneratedInput.Schema.version=1` remains unchanged and authoritative. Every remaining association fact is mandatory;
`alternativeCount`, creator provenance and all runtime/projector behavior remain unchanged. Removed `version` members
are rejected as unknown, with no compatibility alias or fallback. Recompile matched compiler/runtime/plugin and Schema
artifacts.

The metadata regression first failed against the previous annotation API, then passed after the simplification. Focused
Groovy 3 and Groovy 4/5 literal-alternative tests, separate binary/plugin consumers and Java/static Groovy JPMS checks
passed. Each missing association member is tested, alongside duplicate, unknown, empty, wrongly typed, array, nested and
enum members; a corrupt unified Schema version still rejects. Both read-only local review axes found no findings.

All three actual plugin emissions are byte-identical to the pre-change snapshots, with SHA-256
`b2c38640dee9f2e2279595a624fd120b516112d961c4b33081b246a8b2a42be4`. The Draft 2020-12 oracle again accepted 24 valid
and rejected 75 invalid inputs across Groovy 3/4/5. The final unfiltered root check for this follow-up is pending.
