# Explicit structured-input key members — design and implementation plan

Date: 2026-10-24. Status: **Local design recommendation; no implementation or publication**.

Related: [#954](https://github.com/klum-dsl/klum-ast/issues/954).
Authority: the bounded KEY-INPUT assignment and issue #954, read with
[#952](https://github.com/klum-dsl/klum-ast/issues/952),
[#953](https://github.com/klum-dsl/klum-ast/issues/953), and
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#proposed-explicit-key-member-extension--954).
This is a category B clarification/extension of ADR 0031, not a new construction architecture.
The recommendation is concrete enough to implement after design acceptance; none of the examples below claims delivery.

## Evidence baseline

Inspected fresh `origin/master` at `b2ad49bcec865f25a9a1c8f6436a8c2af6b67f3f` (merged #951).
Source anchors:
[facade](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumStructuredInput.java),
[preparation/dispatch](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java),
[compiler admission](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java),
[compiled contract](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java),
[child construction](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java),
[root construction](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java),
[Factory API](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumFactory.java), and
[projection](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java).

All three issue bodies had no comments when read. The current user pages are
[Structured Input](../user/Structured-Input.md), [JSON Schema](../user/Json-Schema.md), and
[Builder-first migration](../user/Builder-First-Migration.md). The architecture map and ADR 0009 establish that managed
Jackson is an independent importer. Findings below are source/test inspection, not newly executed experiments.

| Existing seam | Observed behavior and consequence |
| --- | --- |
| `KlumStructuredInput.readRoot(Class<T>, String, Object, String)` | Only public root overload; `StructuredInputSupport.readRoot` prepares the entire domain, checks keyed/non-null-rootKey parity, then calls `FactoryHelper.createWithConfiguration`. Null is not an inference request. |
| `StructuredInputSupport.Preparation.domain` / `Domain` | Recursively snapshots steps in supplied iteration order, rejects inadmissible names, shapes and cycles before root allocation. Key extraction belongs here, not in dispatch or a parser. |
| `StructuredInputAdmissionPlan.excludedField` / `enrichPublicContract` | Actual key fields are excluded before naming and ordinary field enrichment. `GeneratedInput.Schema.key` records only the native name. It cannot currently describe a renamed/ignored key. An ordinary scalar called `key` is admitted (#950). |
| `CompiledBuilderSchema.collect` / `admitted` | Reflects generated public declarations plus exact annotation bytes, resolves inherited declarations and deterministic collisions; does not expose a construction-key descriptor. |
| `StructuredInputSupport.Step.execute` | Direct children pass `fixedChildKey` into the Consumer creator; keyed Map children pass the outer literal String. Inner keys currently fail as unavailable entries. |
| `InternalKlumBuilder.createSingleChildWithConfiguration` | Resolves `@Field.key` at execution, then reuses/checks an existing child or calls `prepareNestedBuilder`; configuration runs within normal preparation before PostApply. Existing child keys must match. |
| `resolveKeyForFieldFromAnnotation` | `Field.FieldName` uses the native relationship name; closures execute against the parent Builder. Their values must never be computed during preflight. Fixed Cluster facts already have separate compiler enrichment. |
| `KlumFactory.FromMap(Map)` / `FactoryHelper.createFromMap` | Already exists. Deduces a type using a type hint, takes the native key member with `toString`, then uses `copyFrom`. It is not the strict operation interpreter. Reinterpreting it would break an existing API. |
| `JsonSchemaProjection.definition` | Caches one closed object definition per Builder identity and projects no required members. One definition is insufficient once the same keyed type has both inline and externally supplied keys. |
| `KlumDeserializer.resolveKey` (Jackson) | Uses Jackson-resolved bindable names and `asText`, falling back to a key hint. It must not acquire the stricter interpreter's rules. |

Executable evidence already in the tree: `StructuredInputRuntimeTest` covers explicit roots, inner-key rejection,
ordinary scalar `key`, escaped paths, order and renamed fixed Cluster fields;
`StructuredInputConstructionTest` covers Consumer parity, repeated child configuration, lifecycle and failure cleanup;
`StructuredInputDirectKeyBoundaryTest` covers direct and separately compiled inherited fixed Cluster keys;
`StructuredInputDocumentaryTest` covers prepared-Builder/context timing;
`StructuredInputAdmissionPlanTest`, `CompiledBuilderSchemaTest` and `CompiledBuilderSchemaReaderTest`, and
`KlumJsonSchemaGenerationTest` provide admission/reflection/projection seams. Reuse actual fixture infrastructure,
not the earlier serialized carrier design.

## Recommended public contract

Add exactly one overload to `KlumStructuredInput`:

```java
public static <T> T readRoot(Class<T> concreteType, Object parsed, String sourceName);
```

For a keyed root, this overload requires the admitted external name of its actual `@Key String` declaration in the
root Map. For an unkeyed root, it behaves like the existing null-rootKey route. Exact concrete type, source identity,
normal Factory construction, complete preflight and completed return type retain their existing meaning.

Keep the four-argument overload and its root authority unchanged: keyed roots require a non-null explicit `rootKey`;
unkeyed roots require null. An inner key rejects even if it equals the external key. Do not treat null as automatic
selection and do not prefer one of two supplied keys. Both overloads share the extended direct-child grammar below.
`applyToBuilder` retains its signature and rejects a key for its already allocated target; nested newly configured
children use the same direct-child grammar. It cannot change, confirm or infer the target's identity.

No generated Factory/Builder signature changes belong to #954. In particular, preserve `Create.FromMap(Map)`,
`Create.AsBuilder().FromMap(Map)`, `Create.With`, and named parameters. #953 may later select a distinct convenience
spelling/signature delegating to this interpreter; this design neither reserves that spelling nor approves source loading.
Schema-specific factories remain responsible for dependencies required by early callbacks. No arbitrary factory callback,
public prepared-plan handle or dependency injection API is needed for this slice.

Java client example (proposed overload):

```java
Deployment deployment = KlumStructuredInput.readRoot(
        Deployment.class,
        Map.of("name", "production", "service", Map.of("service-id", "catalog", "image", "catalog:1.0")),
        "deployment.yaml");
```

Groovy Schema and Model examples (imports abbreviated only for the client call):

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class Deployment {
    @Key String name
    Service service
    Map<String, Service> services
}
@DSL class Service {
    @Key @JsonProperty('service-id') String name
    String image
}

// Ordinary Factory/DSL spelling remains unchanged.
def ordinary = Deployment.Create.With('production') {
    service('catalog') { image 'catalog:1.0' }
}

// Proposed structured route; the caller owns parsing.
def parsed = [name: 'production', service: ['service-id': 'catalog', image: 'catalog:1.0'],
              services: [worker: [image: 'worker:1.0']]]
def model = KlumStructuredInput.readRoot(Deployment, parsed, 'deployment.yaml')
assert model.service.name == 'catalog'
assert model.services.worker.name == 'worker'
```

Corresponding YAML (illustrative data, not a loading API):

```yaml
name: production
service:
  service-id: catalog
  image: catalog:1.0
services:
  worker:
    image: worker:1.0
```

The `worker` payload rejects both `service-id` and the unrenamed key spelling as key input; it never accepts a second
encoding. Renames do not create aliases. If an old/native spelling independently names a different admitted ordinary
operation, it retains that operation's meaning; spelling resemblance alone does not reserve it as a key alias.

## Position-sensitive admission and diagnostics

“Direct” means a concrete owned single-object relationship at any depth, not only an immediate root child.
Presence of a direct relationship remains optional; when supplied its entire payload must meet the following contract.

| Position | Key authority | Key member in payload |
| --- | --- | --- |
| New three-argument keyed root | Input member | Required exact String |
| Existing four-argument keyed root | `rootKey` argument | Rejected, equal or conflicting |
| Existing target of `applyToBuilder` | Previously allocated Builder | Rejected, equal or conflicting |
| Direct keyed child without Schema key provider | Input member | Required exact String |
| Direct child with `@Field.key`, fixed Cluster key or other proven Schema provider | Existing Schema mechanism | Rejected, even if equal; do not evaluate a provider to compare |
| Keyed owned Map value | Outer literal Map key | Rejected, even if equal |
| Unkeyed object | No construction key | No synthetic member; ordinary admitted properties are unaffected |

Use existing naming precedence on the actual key declaration: `@JsonIgnore(true)`, then literal nonempty
`@JsonProperty`, then native name. `@JsonIgnore(false)` does not exclude. Empty `@JsonProperty` falls back to native.
Do not add aliases, access-mode interpretation, mixins, views or naming strategies. Accept empty String keys, as v1 Map
keys already do; reject null, numeric, boolean, list/map and GString values without coercion.

Inherited keys are in the first slice: use the one compiler-selected hierarchy key, its original declaring source and
its naming/ignore facts. Separately compiled parents must behave identically. Multiple actual keys remain the ordinary
Schema compiler error; do not pick a winner. A renamed key colliding with an admitted property/operation is an ambiguity
in the member-key position: report the name and both declaring sources, admit neither there, and fail construction
before allocation. External-key/body positions retain their old property admission; a disabled key is not an alias
that globally suppresses an unrelated property. Ignored declarations do not participate in admitted-name collisions.

Ignored keys cannot support self-contained keyed roots or input-key direct children. Fail the new root route with
explicit ignore/declaring-source guidance; suggest the existing explicit-rootKey route. Reject a supplied direct child
whose required key is ignored; suggest Schema derivation or a keyed Map relationship, without changing the Schema.
Its omission from the parent input is still legal. Existing external roots, keyed Maps and fixed/derived children work
with ignored keys because their key source is external to the payload.

Missing key: report the expected external member pointer, selected public contract, original declaration and “required
construction key”; distinguish absence from present null. Unknown native spelling after rename remains unknown input
with guidance naming the accepted spelling, except when it independently names an ordinary operation.
Dual sources: explain which source owns identity and that inner identity is forbidden, without executing/comparing a
Schema provider. An equal value is still duplicate authority. Raw repeated YAML/JSON member names cannot be detected
once a parser has collapsed them into a Map: caller parsers own that rejection, and JSON Schema cannot restore it.
No duplicate-preserving parser or repeated-operation envelope is introduced.

Use the existing `KlumModelException` format: opaque source, escaped JSON Pointer (`~0`, `~1`), external name, public
contract, expected versus actual shape/type, and preserved cause where one exists. Missing members have no fabricated
exception cause. Keep existing late lifecycle/materialization/validation errors and their chains; never attribute them
to a guessed last key or input leaf.

## Preparation and construction

1. Resolve compiled contract plus positional key policy without initialization. Validate domain shape and cycles.
2. Walk supplied entries once in their iteration order. At the admitted key name validate and store the immutable String
   separately; it is not a `Step`. Prepare all remaining entries recursively in the same order. Check required-key
   absence after traversing supplied entries, preserving existing first-input-error ordering.
3. Retain a private prepared node containing its resolved input key and ordered steps. Do not remove entries from the
   caller's Map, call setters, create Builders, evaluate providers, initialize enums or run callbacks in preflight.
4. Only after the entire tree succeeds, pass the prepared root key into `createWithConfiguration`. For direct children,
   pass the prepared key into `createSingleChildWithConfiguration`; retain the old fixed-key argument/derivation for
   Schema-supplied cases. Continue the keyed Map Consumer path unchanged.
5. Keyed Builder constructors receive identity before PostCreate. Normal preparation, Templates, configuration,
   PostApply, ownership, PostTree, materialization and validation retain their existing order. Other steps keep their
   relative order regardless of where the key occurred in the input.

An earlier operation or Template can already have populated a direct child when execution reaches it. Keep ordinary
same-key reuse and different-key rejection from `configureExistingChild`; no replacement or rollback. This state-dependent
mismatch is an execution failure, not something pure preflight can promise to detect. Schema execution can have side effects
before it fails. Context-required roots still use appropriate Schema construction and keyless `applyToBuilder` payloads;
the new simple root overload does not promise to invoke arbitrary custom Factory preparation.

## Minimum compiled facts

Current enrichment is insufficient in two specific ways: the key's external admission is lost, and direct `@Field.key`
authority is resolved only at execution. Keep `CompiledBuilderSchema` as the single private shared seam and add:

- A non-operation key description: native name, original declaring source/public contract, external name or ignored
  state. String type follows the compiler's key invariant, checked against public getter metadata where available.
- A direct relationship fact distinguishing caller-key from Schema-supplied key. Record presence of non-default
  `@Field.key` and reuse fixed Cluster knowledge; never serialize or execute the key closure.

Prefer key facts on the declaring public key getter, with explicit hierarchy traversal; if that getter is not available
for a supported shape, a small `GeneratedInput.Schema` member is the already-established type-level target. Do not infer
key authority from zero-argument creator overloads, method names, a null `fixedChildKey`, or Model-field reflection in
the generator. Physical annotation placement is a bounded implementation choice to prove in slice K1, not a public API.
Keep key metadata separate from ordinary `Field` + `Selection`, since no writable key operation exists.

Distinguish missing new facts from an unkeyed/ordinary relationship. Old compiled Schemas must get precise recompile/package
guidance when the new route needs unavailable facts; no unsafe identity-name fallback. Qualify old-runtime/new-Schema
failure too: the existing annotation-byte reader rejects unknown members. This is generated ABI requiring aligned versions,
not a promise that adding annotation defaults makes old artifacts interoperable. Preserve old explicit-root operation where
its required facts suffice; do not invent a historical metadata decoder.

## JSON Schema projection

Default root artifacts describe self-contained input for the new overload. A keyed root has a String property at the
resolved key name and `required: [thatName]`. This requirement is justified by construction, not by completed `@Required`.
Direct caller-key children reference that same member-key shape. Fixed/derived children and keyed Map values reference a
keyless body shape. Required keys do not make optional relationship properties required.

Use private definition identity `(Builder, key-position)` rather than just Builder. Keep the existing fully qualified
Builder name for the self-contained definition; use a deterministic non-Java suffix such as `::keyless` for a distinct
closed body definition. Unkeyed types need only one definition. Reserve each definition before following recursion and
encode JSON Pointer/URI references correctly. Duplicate the small closed property map rather than using `allOf` to extend
a base with `additionalProperties: false`, which would reject the added key. Recursive and mixed-position reuse must be
covered explicitly. Collision/admission resolution is per position as above.

An ignored or colliding required key makes a selected self-contained root unrepresentable: fail generation with source
and reason and preserve prior outputs. An unrepresentable optional direct-child property is omitted with
`x-klum-unsupported`, matching existing unsupported-declaration policy; the keyless variant can still be used by Maps or
Schema-derived relationships. Do not generate an impossible required-but-absent key property.

There is no new Gradle mode in #954. The default keyed root artifact changes from a keyless payload schema to a
self-contained document schema. It does not claim to validate the existing explicit-rootKey or active-Builder payload
contract; document this limitation clearly. Existing task inputs, output names, stale-output handling, isolated loader,
caches and no-execution guarantee remain. Structural validity still says nothing about provider execution, state-dependent
key mismatches, lifecycle success, validation or parser duplicate handling.

## Alternatives rejected and compatibility risks

- Synthetic `key`: loses actual domain vocabulary and breaks ordinary unannotated `key` properties.
- Key setter/rebinding after allocation: too late for initializers/PostCreate and violates identity immutability.
- External key wins, inner key wins, or equal duplicates accepted: two authorities and unnecessary conflict semantics.
- Reuse/replace `Create.FromMap(Map)`: its copy/type-hint/coercion behavior already differs. #953 owns convenience.
- Null-rootKey inference in the existing overload: silently changes a validation contract; an overload makes intent explicit.
- Same `$defs` shape everywhere: either admits forbidden inner Map keys or omits required root/direct keys.
- Evaluate `@Field.key` during preflight or infer it from overloads: executes Schema code or repeats the fixed-Cluster bug.
- Ban inherited keys: excludes an established Schema capability without simplifying runtime construction.
- Add parser, generic construction callback, public plan, source loader, or Jackson parity: not required by #954.

Two migration effects must be accepted with this design. First, a supplied direct caller-key child without a key currently
can reach construction with null (the Consumer path has no required-key guard); this proposal rejects it in preflight.
This is deliberate tightening, not a claim of full source-behavior compatibility. The ordinary Groovy DSL retains its
own behavior. Second, generated keyed root JSON Schema gains a required property and no longer describes external-key
payloads. Existing four-argument roots retain root authority and signatures, but nested direct input follows the new
required-key rule. Other externally sourced key forms remain unchanged. Release placement remains unassigned.

## Implementation slices and acceptance

All slices belong to #954; #952 and #953 get no implementation here. Each slice is one reasoned production-plus-test
commit, except the final documentation/qualification commit. Do not commit pending tests for this unimplemented proposal.

| Slice | Deliverable | Executable acceptance |
| --- | --- | --- |
| K1 — exact identity facts | Compiler/generated enrichment and `CompiledBuilderSchema` key description + direct key-source fact | Native/renamed/empty-rename/ignored/ignore-false keys; local and binary inherited keys; duplicate Schema key error; key/operation collision per position; ordinary scalar `key`; fixed Cluster, FieldName, closure provider; initialization traps; missing/old/malformed facts; no generated public callable changes. |
| K2 — root vertical path | Three-argument facade and pure prepared-key extraction, then existing root Consumer construction | Java and static/dynamic Groovy calls; key available at PostCreate; missing/null/non-String/empty/escaped keys; rename/ignore/collision diagnostics; equal/different inner keys rejected by old overload; old null contract retained; failure anywhere prevents root allocation; source/cause and supplied order. |
| K3 — direct child vertical path | Positional preparation and key argument through existing owned-child Consumer seam; active target remains keyless | Supplied direct child requires key; absent child allowed; nested descendants; same-key reuse/different-key error; key first/middle/last leaves operation order intact; fixed/derived/Cluster inner-key rejection without provider execution in preflight; Map outer-only key authority; ignored inherited key; context/Template/lifecycle parity and error-chain cleanup. |
| K4 — matching projection | Context-sensitive closed definitions and required construction key | Same type as root, direct child, fixed child and Map value; valid/invalid documents checked by an actual Draft 2020-12 validator; recursive/mixed references; ignored/colliding roots fail and retain outputs; unsupported direct properties explain omission; no Schema/enum initialization; deterministic output/cache/classpath cases. |
| K5 — delivery qualification | Documentary tests, user pages, migration navigation, CHANGES and compatibility evidence | Groovy 3 focused then affected suites; Groovy 4/5 at final qualification; named-module Java/static Groovy consumer; Jackson regression suite; Gradle TestKit generation for supported Groovy versions; documentation/diff checks. |

New tests use `Test` suffix and `@Issue('954')`; documentary examples use `@Tag('documentary')` and `@See` to the final
user section. Add a `StructuredInputKeyDocumentaryTest` covering the Groovy/YAML-shaped example and the explicit-root
alternative. Use existing artifact-compilation fixtures for packaged and binary-parent tests; extend the existing
construction/direct-key fixtures rather than introducing another test construction engine. A focused new runtime test
class can be `StructuredInputKeyTest`; exact class grouping is an implementation detail.

K5 updates `docs/user/Structured-Input.md`, `docs/user/Json-Schema.md`, the structured-input section of
`docs/user/Builder-First-Migration.md`, relevant migration navigation and `CHANGES.md`, with reciprocal documentary links.
At authorized delivery synchronize issue acceptance references and inspect CI/Sonar findings; do not close #954 for a
design-only change. No real ScHelm acceptance is claimed by these fixtures; existing consumer-owned gates remain separate.

## Design validation and handoff

This branch changes only Markdown design artifacts. Relative links/anchors and balanced fences passed an automated
local check. Requirement-to-slice mapping and the focused design diff were manually reviewed; `git diff --check` passed.
The local commit is one reasoning unit: the proposed ADR extension and its supporting evidence/implementation plan. Groovy test lanes are not required for non-executable design prose and
were not run. No production sources, executable fixtures, generated output, current user promises or release notes change.
Source inspection establishes feasible seams; implementation tests above remain outstanding acceptance work.

No essential product question prevents a recommendation. The approval unit includes the two explicit migration effects
and context-sensitive projection; rejecting either requires revising the contract before implementation, not silently
weakening required keys. Physical enrichment placement remains a testable implementation choice with a bounded fallback.

Handoff: `(ready:PR)` for local committed design reasoning only. No push, PR, issue mutation or cross-Hive messages.
Tracker impact if later published: **Related: #954**, never issue completion. Execution scope is complete; design
acceptance, implementation, remote delivery and Hive reconciliation are not performed by this assignment.
