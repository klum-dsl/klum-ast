# WIRE-1 generated collection population contract

Date: 2026-10-22. Status: **characterized before policy; compiler-selected population correction implemented locally**.
Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932),
[#529](https://github.com/klum-dsl/klum-ast/issues/529).
Decision context: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[WIRE-RUNTIME plan](../adr-0031-wire-runtime-v1.md),
[previous opaque-container gate](issue-932-wire-1-container-stop.md).

The maintainer clarified that field declaration support and authored-operation argument admission are distinct.
Supported Schema fields keep the existing compiler's finite container set. Structured input populates them through
compiler-known generated adders, allowing normal Builder mechanics to allocate/manage the actual container. It never
chooses a replacement setter or constructs a guessed/custom container to satisfy a whole-container signature.

## Actual compiled baseline

`StructuredInputCollectionContractTest` compiles and loads real generated public Builder classes, compares carrier roles
and descriptor groups, invokes exact public methods, and completes ordinary construction. Every field uses
`@Field(members='addValue')` (or `addEntry`) so the evidence does not depend on guessing singular names.

| Declared Schema field | Supplied external payload | Baseline carrier role/group | Generated population path |
| --- | --- | --- | --- |
| List<String> | List of String | PROPERTY; values(Iterable), values(String[]) | addValue(String), or values(Iterable) |
| Set<String> | List of String | PROPERTY; same group | same; normal deduplication |
| SortedSet<String> | List of String | PROPERTY; same group | same; normal sorted storage |
| NavigableSet<String> | List of String | PROPERTY; same group | same; normal navigable sorted storage |
| EnumSet<Mode> | List of enum text | PROPERTY; values(Iterable), values(Mode[]) | addValue(Mode), or values(Iterable) after enum materialization |
| Map<String,String> | Map from literal String key to String | PROPERTY; values(Map) | addValue(String key,String value), or values(Map) |
| SortedMap<String,String> | same Map shape | PROPERTY; values(Map) | same; normal sorted keys |
| NavigableMap<String,String> | same Map shape | PROPERTY; values(Map) | same; normal navigable sorted keys |
| Map<String,Entry> (concrete keyed owned) | Map from literal instance key to child domain Map | KEYED_OWNED_MAP; plural relationship group | addEntry(String,Closure) / its existing internal fixed-target Consumer creator path |
| SortedMap<String,Entry> | same keyed domain Map | same role/group | same fixed-target keyed creator |
| NavigableMap<String,Entry> | same keyed domain Map | same role/group | same fixed-target keyed creator |

The actual collection descriptor encoding includes both JVM array names for resolved JDK types (`[Ljava.lang.String;`)
and source array spellings for generated types (`Mode[]`). These are existing carrier spellings, not a new format.
The actual owned-map plural group contains Collection/Builder-array/script/Closure/control overloads, with no universal
replacement-Map setter. The keyed creator is a separate explicitly named generated method.

The native collection tests add `z,a,z`. List retains that order; Set yields `z,a`; sorted/navigable fields yield `a,z`;
EnumSet retains enum declaration order. Native keyed creation calls `z/key~` before `a/key` in all three map categories,
confirmed by PostCreate events and final owner identity. Sorted output keys are normal container semantics, not execution
reordering. Runtime preparation/execution must still visit supplied Maps recursively and List elements in supplied order.

## Authored operations and provenance

Exactly-one-payload `@Builder.Method` operations taking ordinary List<String> or Map<String,String> are already admitted;
these keep one payload. An authored Iterable<String> method is **not** admitted: it has a public Builder signature but
receives the existing outside-the-admitted-grammar rejection. Generated iterable population for a PROPERTY does not
admit authored Iterable operations. No Iterable language extension is approved here.

A shadowing fixture declares NavigableSet<String> values and an authored values(Iterable<String>) that throws.
The baseline field carrier's plural descriptor group still includes that authored method, even though its authored
operation is rejected. A generated addValue(String) remains available. Consequently native-name/descriptor-group membership
alone does not prove a population method is generated for the field. Runtime reflection must not infer a replacement or
rank candidates from that broad group.

The existing carrier already has the required slots for compiler selection: field source identity, PROPERTY/KEYED role,
payload shape, exact native operation name and exact descriptor group. A compiler-selected generated element/keyed
operation can be recorded in those same slots, including an explicit members name, without adding a field, codec,
public descriptor or registry. Provenance must be established while compiler generation still knows the owning field;
the runtime consumes the recorded selection. A conflicting authored replacement must not become field population.

## Clarified mapping for implementation

- A PROPERTY collection consumes a List, visits its prepared elements in order, and uses its compiler-selected generated
  element adder or recorded generated iterable population path. It does not instantiate the declared field container.
- A PROPERTY data map consumes a Map, retains literal keys and visits prepared entries in supplied iteration order,
  using its compiler-selected generated keyed adder/Map population path.
- A KEYED_OWNED_MAP consumes literal instance-key-to-domain-Map entries and uses the existing fixed-target nested
  Builder creator/attachment path with Consumer configuration. It never treats the plural factory scope as a Map setter.
- An OPERATION keeps the existing one-payload scalar/enum/ordinary List/Map grammar. Whole-container arguments requiring
  Set/NavigableSet, broad Collection, concrete/custom Collection/Map implementations or constructor guessing reject.
  Authored Iterable remains rejected. Nested ordinary data payloads obey the same no-guessed-container rule.

Generated keyed element calls may internally take key and value. This introduces no authored multi-argument operation,
call-record syntax, parser, CollectionFactory syntax, replacement policy, reflection resolver or public API.
Carrier WIRE-PLAN/B remains unreleased and unchanged in format, with no legacy/version branch.

## Validation

- Groovy 3: **13 cases, zero failures/errors/skips** (8 ordinary field categories, 3 keyed categories, authored Iterable,
  and shadowed plural provenance).
- Groovy 4/5: **13 cases per lane, zero failures/errors/skips**; module license and lane isolation pass.
- This is characterization before policy. Opaque List/Map fixtures will become rejection coverage in the next correction.
- Runtime facade/preflight/dispatch/lifecycle work and the reader's remaining acceptance gates are still outstanding.

Tracker impact: related evidence for #932/#529, both stay open. No user-facing feature or release claim is delivered by
this characterization; no user page or CHANGES update is warranted until runtime delivery.

## Compiler correction after characterization

The new tests first failed against the old compiler: PROPERTY records still named the plural group instead of the required
generated element method, and the opaque whole-container operations remained admitted. After that red run, the compiler
was corrected without a carrier-format change:

- The existing generated-method builder records the owning field as private AST-only metadata. Optional methods that
  lose to authored signatures acquire no generated provenance.
- Ordinary collection/map PROPERTY records select the owning field's generated element/keyed adder by exact declared
  signature, preserving explicit members names. Collection descriptors have the element argument; map descriptors have
  the String key and value arguments. Only that selected public signature is persisted in the existing descriptor slot.
- Supported top-level field declaration types reuse DSLASTTransformation's existing finite set. Authored and nested plain
  payloads retain the ordinary List/Map grammar and reject other collection/map argument targets with source/type-specific
  admission diagnostics. Authored Iterable remains rejected.
- An authored plural Iterable override cannot hijack the field path. If an authored element method replaces the actual
  generated element path, field admission records the missing generated path rather than using the authored operation.
  The authored method can still be independently admitted under its own normal operation name.

The normative collection tests now execute the compiler-recorded generated element/keyed signatures, adding prepared
values one at a time in supplied order. The original baseline matrix above remains historical evidence of available
public bulk and element methods before narrowing compiler selection. Keyed owned-map roles continue to use the already
approved fixed-target internal Consumer creator/attachment path; no singular-name inference or replacement Map call is
introduced. The opaque List/Map fixture is now rejection coverage.

Final focused Groovy 3: **59 cases, zero failures/errors/skips**: 31 admission, 23 collection-contract, one initial
carrier-reader, four existing runtime-boundary probes. The 23 contract cases include all eight field categories, three
keyed categories, the authored Iterable boundary, plural and element shadowing, eight forbidden whole-container targets,
and nested ordinary versus sorted-container arguments. Compatibility/full-root validation and independent local review
remain pending for this correction. No public runtime feature, user page or CHANGES claim is part of this commit.
