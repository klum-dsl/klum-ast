# #952 L1-A — exact String scalar List-element routes

Base: `45729e625385fb59bca878cce3771391d6d727d3`, verified against fetched `origin/master` on 2026-11-02.
Dedicated worktree: `/Users/stephan/.codex/worktrees/952-l1a-list-converters/klum-ast`.
Branch: `codex/952-l1a-list-converters`. Tracker impact: **Related: #952**; issue state, labels, milestones,
curation and release gates remain unchanged. Hive owns final reconciliation.

## Contract and seam

The existing compiler pairing can carry the authoritative element route without discovery or another conversion engine.
`StructuredInputConverter` selects one existing public generated String element adder for an exact `List<T>` of non-DSL
scalar values. It retains attempted collisions and pairs the same Converter/Selection facts with explicit getter-side
zero/one completeness. The reflected getter and return type identify the destination element. No new metadata carrier,
version, public authoring API or CollectionFactory change is needed. Matched Schema recompilation remains required.

`InputContractResolver` validates List shape, element destination, exact String public method, role, association and count
before exclusion. List routes have no C1 parameter authority. The immutable compiled payload is List-of-String and the
existing interpreter invokes the selected element adder in index order. Its ordinary runtime converter/population path
owns all actual conversion; a protected generated-code bridge preserves direct JPMS linkage. Schema projection consumes
the same payload and emits an array with String items. Element parameter assertions are not projected; scalar C1 remains.

Initial/default entries, empty-input behavior and earlier successful operations survive unchanged. A reached malformed
element or converter failure retains prior work and original cause with source/index diagnostics; later work is absent.
No whole-container replacement, Map-value conversion, CharSequence-only element route, owned DSL collection, coercion,
selector, parser, Jackson, copy, composition or generic Factory registry is added.

## Acceptance evidence

- `StructuredListConverterTest`: documentary ordered Duration example; exact result types/values; ordinary Groovy parity;
  initializers and empty Lists; strict null/numeric/boolean/GString/Object/mutable CharSequence/Map/List rejection;
  first-failure partial state, original cause and skipped later operations; competing converters, ignored/excluded routes,
  generation collisions, unsupported containers and native List grammar; same-compilation/external/constructor origins;
  binary inheritance/shadowing; source-free metadata corruption and initialization traps with a positive control.
- `CompiledConverterJsonSchemaTest`: packaged G3/G4/G5 producers with sources/classes removed after JAR assembly;
  closure/local/adapter/inherited-constructor String items; Map-value and CharSequence-only exclusions; retained C1 facts,
  no-user-code traps, deterministic/cacheable output and previous-output failure preservation.
- `validate_converter_schema.py`: independent Draft 2020-12 accepted/rejected List corpus against the actual emitted
  schemas, alongside retained C1 JDK/ECMA controls. Structural String contents remain converter-owned at invocation.
- `JpmsPackageBoundaryTest`: Java and static Groovy consumers plus bytecode verification of direct generated element-hook
  linkage, classpath on G3/G4/G5 and named-module Schemas on G4/G5.

User contract: [converter-backed List elements](../user/Structured-Input.md#converter-backed-list-elements), with
`StructuredListConverterTest#'converts duration List elements independently in supplied order'` carrying `@Issue('952')`,
`@Tag('documentary')` and `@See`. Current Converters, JSON Schema, Migration and CHANGES documentation are synchronized.

## Qualification and delivery

Exactly two local read-only reviewers performed Standards and Specification review. Standards requested braces in the
ASM corruption helper and one constant for a repeated stable diagnostic; both are corrected. Specification reproduced
a compiler/resolver mismatch: an ordinary non-DSL `Value<T extends CharSequence>` was mistaken for a Builder SELF type.
The resolver now recognizes generated Builder interfaces explicitly, with runtime ordinary/structured parity and
packaged G3/G4/G5 bounded-generic List controls. Both axes report zero remaining findings in the corrected tree.
Reviewed commits are preserved; corrections are additive.

The corrected focused Java 17 run passed 120 baseline compiler cases (48 List, 27 String-origin, 44 CharSequence,
one real binary/classpath consumer) plus eight packaged-plugin cases with zero failures/errors/skips. The independent
actual-output oracle passed 28 JDK, 270 ECMA/Draft C1, 270 structural scalar String and 201 List grammar controls.
The earlier compatibility run predating the review correction was stopped and is not final qualification.

Full root qualification is pending. No full-check, hosted CI/SonarCloud, publication or merge result is claimed here
until recorded below. No local SonarCloud upload is permitted or performed.
Publication uses a draft PR with **Related: #952**, without merge authorization. Hive must reconcile eventual external
delivery and archive readiness; the worker does not self-archive.
