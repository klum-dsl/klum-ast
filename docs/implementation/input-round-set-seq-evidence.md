# SET-SEQ — exact String converter-backed Set elements

Refreshed `origin/master` base: `8f789abbd4cf49a07414b89931f8fbfcbe5b614b` (2026-11-06).
Branch: `codex/952-set-seq`. Authority: the accepted SET-SEQ assignment, #952, ADR 0032 and the R1
characterization at local commit `b2923977354bd9f1d1ce0e194e3a42f5a3fc08e0`.
Tracker relationship: **Related: #952**. Curation impact: **feature pull-request update**: the #1015 inventory's
Set-converter row records the bounded local implementation; CharSequence container routes remain deferred.
No issue closure, label/milestone, release qualification or adopter qualification change is claimed.

## Implementation and compatibility

`StructuredInputConverter` recognizes exact declared List/Set converter sequences. One compiler-selected generated
public exact String adder retains the existing Converter/Selection facts and explicit association count; no new metadata
shape, protocol version, public authoring API or converter discovery is added. `ConverterBuilder` uses the existing
direct String collection hook for Set, as for List. `StructuredInputAdmissionPlan` and `InputContractResolver` normalize
to the existing List-of-String input payload. Schema projection consumes that same validated payload without user code.
The interpreter and ordinary population engine are unchanged.

Every raw String converts in supplied order, followed by ordinary Set insertion. Defaults, equality/comparators,
first-equal retention, final iteration order, partial failures, Template retained values and lifecycle remain native.
Empty input makes zero calls, preserving defaults without replacement. JSON Schema emits String arrays without
uniqueness/cardinality/comparator/final-order/replacement assertions. Matched compiler/runtime/plugin and Schema
recompilation are required; generated callable signatures and module descriptors are unchanged.

Exact declared SortedSet/NavigableSet/EnumSet, custom collection grammar, owned DSL converter values, CharSequence
container routes, named-map/CollectionFactory work, generic coercion and constraint policy are outside this slice.
Existing native/owned grammars remain authoritative for their independently supported shapes.

## Acceptance map

- `StructuredSetConverterTest`: caller-parsed YAML documentary example, ordinary Groovy parity; equality/default
  collisions, distinct raw Strings converting equal, every-conversion order/identity, backing comparator order;
  converter/insertion failure with original cause and indexed source, earlier effects and skipped later work;
  empty/default/replacement-setter and input-Set traps; Template definition versus recipient replay;
  strict String-only item grammar, competing/excluded/colliding routes and unsupported shapes;
  same-compilation/adapter/constructor/bounded-generic origins, binary inheritance and descendant shadow rejection;
  source-free positive/no-initialization controls, missing/corrupt descriptor/association/count/type metadata.
- `StructuredListConverterTest` moves the formerly rejected Set case to a still-excluded SortedSet declaration.
- `CompiledConverterJsonSchemaTest`: sources/classes removed after G3/G4/G5 JAR assembly, inherited/local/closure/
  adapter/generic Set routes, no-execution traps and no uniqueness/cardinality assertions.
- `validate_converter_schema.py`: independent Draft 2020-12 validation of actual emitted Set arrays, accepting repeated
  strings and rejecting non-String elements/containers, while retaining scalar C1 and List/Map controls.
- `JpmsPackageBoundaryTest`: direct Set collection-hook bytecode linkage plus packaged Java/static Groovy invocation;
  the existing fixture exercises classpath G3/G4/G5 and named modules G4/G5.

The documentary method `converts every supplied tag and retains ordinary Set equality semantics` carries `@Issue('952')`,
`@Tag('documentary')` and `@See` for [Set elements](../user/Structured-Input.md#converter-backed-set-elements).
Structured Input, Converters, JSON Schema, Migration, CHANGES and the accepted ADR/plan/inventory are synchronized.
No page was added/renamed, so sidebar navigation needs no change.

## Validation and review status

Focused Java 17 Groovy 3 Set/List/Map converter regressions pass: **157 cases, zero failures/errors/skips**.
Initial red runs exposed two fixture errors (native EnumSet was incorrectly treated as unsupported, and comparator
failure checked the incumbent rather than the inserted value); both were corrected before the successful run.

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :klum-ast:test --tests '*StructuredSetConverterTest' --tests '*StructuredListConverterTest' --tests '*StructuredMapConverterTest' --no-parallel --max-workers=2 --console=plain
```

Root `check` is required by the actual shared compiler/runtime contract change, not a generic pre-push rule. It includes
affected module suites, compatibility lanes, packaged plugin and binary/JPMS consumers, license and lane-isolation checks.
Final root-check, actual-output oracle, documentation rendering and exactly two independent Standards/Specification
reviews are pending at this checkpoint. No local SonarCloud upload; hosted final-head CI/Sonar remains final qualification.

## Delivery boundary

Local implementation is not remote delivery. Draft publication requires completed applicable checks, both local review
axes and commit-history review. No merge, issue closure or self-archive. Hive owns final reconciliation.
