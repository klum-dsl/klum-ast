# F2-B — one literal CollectionFactory structured-input family

Date: 2026-11-02. Related: #952. Authoritative base: `ed199e0eb319fbd4927c197121864422a08a40e9`.

Status: implemented locally. Validation and review are recorded below; hosted checks and merge are separate delivery
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
field accessor with `GeneratedInput.LiteralAlternative` and explicit `Field.alternativeCount` (zero or one). Its version,
field, literal alias, concrete target, inherited key/String authority and exact scope/creator descriptor are validated
from classfiles. Missing/stripped/conflicting facts fail before construction, even if input omits the relationship.
Metadata extraction neither initializes Schema classes nor executes an alternatives closure or user factory.

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
| Runtime and documentary contract | `StructuredLiteralAlternativeTest`, including exact concrete type, key-before-create, owner/lifecycle trace and ordinary breadcrumb parity; malformed envelopes, equal/conflicting inner keys, strict body types, escaping, partial order, reuse/type mismatch, lifecycle cause, cycles/cleanup, session/thread/sealing, Templates and arbitrary String keys |
| Source-free metadata | `LiteralAlternativeMetadataTest`, including initialization traps, exact public descriptor, count/association stripping and corruption, protocol version/type/member/visibility errors, naming/exclusion, inheritance and compiler shadowing rejection |
| Compiler exclusion | `LiteralAlternativeCompilationTest`, checking admission before bytecode generation for multiple/automatic aliases, List/non-String Map/custom mapping, generic targets and unchanged concrete keyed-Map syntax; excluded generic shapes receive no ordinary DSL qualification |
| Packaged plugin/binary consumers | `BinaryOwnedRelationshipsTest`, separate producer/API/Schema/client artifacts with sources removed, fresh runtime consumers and actual plugin emission for Groovy 3/4/5; corrupted association prevents every new output and leaves initialization traps silent |
| JPMS | `JpmsPackageBoundaryTest` real Java and static Groovy artifact consumers on classpath for Groovy 3/4/5 and module path for Groovy 4/5, including literal key/body/owner assertions and existing descriptor/package checks |
| Actual emitted schema | `validate_literal_alternative_schema.py`, pinned `jsonschema==4.23.0`: 3 emitted schemas, 24 valid inputs accepted and 75 invalid inputs rejected |

## Validation

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

Focused checks and the emitted-schema oracle passed. Full root check and the two local review axes are in progress.
No local SonarCloud upload is used. Final hosted CI/SonarCloud belongs to the exact pull-request head.

User documentation: [Structured Input](../user/Structured-Input.md#literal-collection-alternative),
[JSON Schema](../user/Json-Schema.md#literal-collection-alternative),
[Migration](../user/Migration.md#literal-collectionfactory-alternative-input), and
[Alternatives](../user/Alternatives-Syntax.md#structured-input). Existing page navigation remains valid.
