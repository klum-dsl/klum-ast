# ADR 0031 implementation plan — `WIRE-PLAN` admission tracer

Date: 2026-10-21. Status: **bounded tracer planned; no production interpreter or generator authorized**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Language/interpreter owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Downstream generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Authority: merged [PR #933](https://github.com/klum-dsl/klum-ast/pull/933) and its
[WIRE-ARGS/WIRE-NAMES/WIRE-ORDER evidence](evidence/issue-932-structured-input-probe.md).

## Outcome and stopping boundary

`WIRE-PLAN` is the first implementation tracer. It gives the compiler one private, immutable admission plan for the
accepted subset and proves that the same facts can later drive both #932 runtime interpretation and #529 Draft 2020-12
generation. It does not add a public descriptor, SPI, registry, parser, interpreter, Gradle plugin or Jackson mode.

The tracer is complete only when the compiler can classify the admitted entries, reject every excluded or ambiguous
entry, carry the plan through separately compiled Schema artifacts without executing Schema code, and expose equivalent
facts to two test-only consumers: a runtime-shaped reader and a JSON-Schema-shaped projector. Both are proof adapters,
not production delivery. Stop after the evidence report and maintainer review; do not continue into runtime dispatch.

## Confirmed baseline and affected seams

| Existing seam | Confirmed behavior / failure path | `WIRE-PLAN` use |
| --- | --- | --- |
| [`GeneratedDslSupport`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java) | final public Builder/Factory contracts retain types, inheritance and `@Builder.Method`; method visibility and NamedParam catalogs are broader than wire admission | build the plan only after public-contract and relationship-role generation completes |
| [`InternalKlumBuilder`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java) | named maps invoke each key/value through ordinary Groovy dispatch; `copyFrom`, apply and lifecycle helpers demonstrate why broad one-argument callability is unsafe admission | characterize unchanged behavior; do not call or modify its private named-map helper in this tracer |
| [`KlumJacksonImporter`](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonImporter.java) | four managed field-binding modes remain supported; `readBuilder`/`applyToBuilder` prove active-session input but replay create/apply callbacks independently of the proposed language | rerun its suites as compatibility evidence, never use it as the plan classifier |
| [`ConstructionSession`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/ConstructionSession.java), `FactoryHelper` and `PhaseDriver` | one enclosing lifecycle can receive an unsealed child Builder, then attach a completed LINK before materialization/validation | preserve this future runtime seam without exposing session authority |
| [#933 binary/no-execution fixture](evidence/issue-932-structured-input-probe.md#529--compiled-contract-projection) | public contracts plus retained field annotations describe the measured concrete subset; abstract targets reject and dynamic Class access can initialize Schema code | replace its fixture allowlist with compiler-owned plan evidence, preserving fresh-JVM no-execution controls |

The affected implementation modules for the tracer are `klum-ast` and its compiler tests. `klum-ast-runtime`,
`klum-ast-jackson` and the future optional #529 Gradle module are characterization consumers only; a required production
edit in any of them is a stop signal for this tracer.

## Exact first admitted grammar

The public input remains a caller-owned parsed Map/List/scalar tree. A root or nested domain object is a Map whose keys are
unique admitted external names. It is not a sequence of Builder calls.

```text
domain-object     := { external-name: admitted-payload, ... }
admitted-payload  := property-value | nested-object | keyed-domain-map | operation-payload
nested-object     := domain-object
keyed-domain-map  := { instance-key: domain-object-without-key-member, ... }
operation-payload := exactly one payload of the selected @Builder.Method contract
```

For `WIRE-PLAN`, `property-value` is deliberately bounded to String, boolean, integral/decimal numeric and enum values,
plus ordinary `List<T>` and `Map<String,T>` values whose concrete non-DSL `T` is recursively one of those leaf types.
Map/List-valued properties and one-payload operations receive the complete Map/List as one payload. `null` is rejected;
the tracer establishes no reset, clear or replace convention. Converter-specific formats such as compact Duration text
remain outside the first tracer unless an existing generated one-payload contract states the accepted wire type without
executing Schema code.

One concrete owned relationship accepts one nested domain object. One keyed `Map<String, Child>` relationship accepts a
Map from literal instance key to concrete child object. The outer key is the sole child key source: the child's generated
key member is not an admitted nested external name, so duplicate or conflicting key encodings reject before invocation.
Keys inside an ordinary data map remain values and are never transformed or reinterpreted.

An authored domain operation is eligible only when it is an inherited or directly declared `@Builder.Method`, appears on
the final public generated Builder contract, accepts exactly one payload in the supported shapes above, and belongs to one
unambiguous external-name group. The operation may mutate several Builder properties; the plan records its payload contract,
not its effects. Generated property/converter overloads may form one compiler-known property group, but competing authored
one-payload overloads reject rather than relying on runtime guessing.

External-name precedence is exact:

1. `@JsonIgnore(true)` excludes the entry.
2. A literal, nonempty `@JsonProperty` supplies the only external spelling.
3. Otherwise one selected identity, camel or kebab fallback supplies the only external spelling.

`@JsonIgnore(false)` does not exclude and an empty `@JsonProperty` falls through. The selected fallback applies to domain
entry names only, never to ordinary data-map or keyed-domain-map instance keys. Inherited, transformed-name and overload-
group collisions reject deterministically with all conflicting declarations named. The tracer exercises all three fallback
policies internally; it does not choose or publish a configuration interface.

The grammar does not admit generic Builder call records, `$args`, positional spreading, zero-argument commands, duplicate
keys, repeated calls, source-order guarantees, an ordering escape hatch, unkeyed relationship collections, polymorphic or
abstract targets, aliases, dynamic Class/Factory selection, generic values bags, Template/copy/session/lifecycle/query
controls, context acquisition, reference ids, layering/patch input or round trips. Domain-object member order is not a
language contract; a future runtime must use deterministic framework ordering without treating parser iteration order as
user syntax.

## Smallest compiler-owned plan

The plan is a deep internal module: classification, naming, collision detection and payload shape stay behind one small
compiler interface. A concrete target plan contains only its generated Model/Builder identity, the selected fallback and
a deterministic map of entries. Each entry contains:

| Fact | Why both future consumers need it |
| --- | --- |
| one external name and its ignore/rename/fallback origin | runtime lookup, JSON object property and collision diagnostics |
| role: property, concrete owned object, keyed concrete map or one-payload domain operation | choose validation/projection without method-name heuristics |
| declaring public Builder contract plus selected operation name and descriptor group | invoke existing dispatch and report the selected contract |
| payload node: scalar/enum/plain list/plain map/nested plan/keyed nested plan | pre-invocation shape checks and exact JSON Schema projection |
| nested concrete Model/Builder identity and, for keyed maps, String key plus excluded child key member | reuse generated creators while preserving instance keys |
| source declaration identity | deterministic collision and unsupported-shape diagnostics |

It deliberately omits Builder implementation classes, reflective handles, closures, default values, `@Required` as raw-
input required, validation rules, inferred Model effects, lifecycle phases, order/repeat policy, provider context, LINK ids,
polymorphic alternatives and JSON-Schema-specific keywords. Invocation handles are resolved only by the future runtime;
schema keywords are projected only by #529.

The compiler builds the plan after the final public `Foo_DSL.Builder`/Factory contracts and relationship provenance are
known. Direct source AST supplies roles; separately compiled parent plans supply inherited roles. Optional `JsonIgnore` and
`JsonProperty` facts are read by annotation name and literal members without loading Jackson types or adding a Jackson
dependency to compiler/runtime. Public method visibility, NamedParam metadata, return type and arbitrary reflection are
never admission evidence.

## Compiler/runtime seam

The proposed persistence seam is one package-private, compiler-generated carrier beside each `Foo_DSL` contract. Its name
is reserved implementation vocabulary, it has no public methods or fields, and it contains only constant plan facts. The
runtime-shaped proof reader locates it from the concrete Model identity through the Schema package already opened to the
runtime. The #529-shaped proof reader inspects the class file without loading or initializing the Schema. There is no
classpath scan, registration step, service lookup, public reader or serialized general-purpose descriptor.

`WIRE-PLAN` must prove the carrier shape before it is accepted as generated ABI. Generated bytecode may name only JDK,
Schema and already approved `runtime.generated` types; it must not name `runtime.internal`. If a package-private carrier
cannot satisfy JPMS, binary compatibility and no-execution extraction without a new exported descriptor type, stop and
revise the design rather than widening ADR 0015 implicitly.

The future runtime adapter consumes the plan but delegates behavior:

- existing generated Builder/Factory operations and `InvokerHelper` retain Groovy overload and converter selection;
- existing fixed-target relationship creators allocate and attach concrete nested Builders;
- `ConstructionSession`, `FactoryHelper` and `PhaseDriver` retain root ownership, sealing, lifecycle, materialization and
  validation; the interpreter neither exposes nor replaces them;
- the existing named-map path remains broader and unchanged, including `copyFrom` and method-first behavior;
- `KlumJacksonImporter.readRoot`, `readTemplate`, `readBuilder` and `applyToBuilder` retain ADR 0009 signatures, mapping,
  callback behavior and diagnostics. The new route may reuse the demonstrated active-Builder capability, not Jackson's
  property binder or wire semantics.

## Fixture and characterization matrix

Reuse the merged #933 fixtures where they already prove behavior. Add only compiler/binary cases needed for the plan.

| Concern | Positive proof | Rejection or preservation proof |
| --- | --- | --- |
| Groovy generations | identical normalized plan on Groovy 3, 4 and 5 | no lane-local role/name drift |
| scalar/property | String, boolean, integer/decimal, enum, `List<String>`, `Map<String,String>` | null, raw/unresolved generic, DSL value hidden in ordinary containers and wrong payload shapes |
| concrete nesting | one nested owned child uses its own plan | abstract target, dynamic selector and unkeyed relationship list absent with explicit reason |
| keyed domain map | outer String key plus concrete child payload; generated key member excluded | non-String key, child key duplicate/conflict and key-as-operation interpretation reject |
| ordinary data map | labels, annotations and topic names preserve punctuation/case literally | fallback policy never transforms data keys |
| names | ignore, literal rename, identity/camel/kebab fallback and inherited members | inherited/transformed/overload collision lists every declaration; empty rename falls back |
| domain operation | inherited/direct one-payload `@Builder.Method`; Map/List remains one payload | genuine multi-/zero-argument, query/helper/copy/session/lifecycle/Template controls and competing authored overloads reject |
| active Builder | #933 ApplicationHolder route remains unsealed and unmaterialized while input is applied | no public manual-session interface and no nested root lifecycle |
| completed LINK | caller attaches completed Environment before enclosing completion; identity and later lifecycle remain intact | serialized context acquisition/reference registry absent |
| managed Jackson | all four importer modes retain their existing characterization tests | no signature, callback-count, mapping or diagnostic parity rewrite |
| diagnostics | source identity, escaped input path, external name, selected contract, expected/actual and preserved cause | unknown/ambiguous names and malformed nested/keyed shapes reject before invocation |
| compiled contracts | separate Layer 3 API -> Schema -> API-only consumer, inherited plan and dependency invalidation | no source mirror, subtype scan or Schema initialization |
| extraction | runtime-shaped and #529-shaped readers produce equivalent normalized facts | constructors, factories, initializers/defaults, converters, lifecycle and annotation closures never execute |

New executable classes use the `Test` suffix and `@Issue("932")`; tests that solely prove the downstream projection use
`@Issue("529")`. `WIRE-PLAN` is internal and needs no documentary test or user page. Keep the disposable probe adapter as
evidence; do not promote it into production.

## Proof gates before runtime implementation

1. **Admission gate:** every accepted grammar production has one plan role and every non-goal has an explicit compile-time
   rejection or omission. Plan order and diagnostics are byte-for-byte deterministic across repeated compilation.
2. **Binary gate:** inherited and concrete plan facts survive separate API/Schema compilation and dependency changes;
   source and AnnoDocimal mirrors are absent from extraction inputs.
3. **Carrier gate:** the generated-linkage inventory finds no internal-package descriptor/call; Groovy 4/5 named-schema
   linkage needs no workaround flag; no public type/member or registry is added. Optional Jackson naming metadata adds no
   compiler/runtime Jackson dependency.
4. **No-execution gate:** accepted and rejected extraction in fresh JVMs initializes no Schema, enum, factory or callback
   code. The runtime-shaped reader may instantiate only framework plan values, never Schema code.
5. **Dual-consumer gate:** the two proof readers agree on names, roles, payload trees, nested references and rejection
   reasons. The #529-shaped projector describes no entry the runtime-shaped reader would reject.
6. **Reuse gate:** the named-map suite, all four managed Jackson modes and #933 Holder/LINK characterization remain green;
   no second overload resolver, lifecycle driver, owned-Model preconstruction or callback-order claim appears.
7. **Review gate:** Standards and Specification reviews have zero unresolved findings, the history is focused, and the
   maintainer accepts the private carrier seam. Only then may a separate #932 runtime slice be planned.

Any failed gate stops `WIRE-PLAN`. In particular, stop if classification needs a public annotation/SPI, if the carrier
requires a public descriptor or `runtime.internal` linkage, if one-payload operation/property groups cannot be distinguished
without runtime guessing, if ordinary map keys or keyed instance keys leak into name resolution, or if #529 needs a fact
that the accepted runtime language does not need.

## Tracer slices and commit boundaries

Keep the stable tracer label `WIRE-PLAN` throughout; the letters below are local commit boundaries, not new roadmap items.

### `WIRE-PLAN/A` — classify the accepted plan in the compiler

Add the package-private plan model and classifier after generated public-contract completion. Cover direct/inherited fields,
concrete relationships, keyed maps, one-payload `@Builder.Method`, name precedence and deterministic rejections through a
focused compiler test. Do not emit a carrier or touch runtime/Jackson/Gradle code in this commit.

**Stop condition:** if existing compiler provenance cannot distinguish property, relationship and authored operation roles,
record the exact missing fact. Do not infer from method names or add a public marker.

### `WIRE-PLAN/B` — prove one private binary carrier

Emit the smallest package-private carrier and add separate-compilation, class-file/no-execution, JPMS-linkage and dual-reader
tests. Reuse the #933 Layer 3 fixture shape; compare canonical plan dumps rather than exposing a reader publicly. Update the
implementation evidence with exact classfiles, commands and omissions.

**Stop condition:** if compiler, runtime-shaped reader and #529-shaped reader require different facts or if the carrier
cannot remain private and initialization-free, stop before any interpreter or generator implementation.

These two commits are the complete tracer. No production runtime entry point, JSON Schema Gradle task, user documentation,
`CHANGES.md` entry or parser dependency belongs in them.

## Dependency sequence after `WIRE-PLAN`

1. #932 owns `WIRE-PLAN` and the maintainer gate.
2. A later #932 runtime tracer may apply the admitted root object to an existing active Builder, then add concrete nested
   and keyed-map creation through existing generated operations. Its corpus must be exactly the accepted plan corpus and it
   must preserve the Holder/completed-LINK route.
3. Only after that runtime corpus is accepted may #529 implement compiled-contract Draft 2020-12 generation from the same
   private carrier. The generator reuses names and payload shapes; it never leads admission, adds a grammar production or
   requires runtime interpretation.
4. Gradle task incrementality/configuration-cache and artifact copying are #529 work after semantic parity. Distribution,
   publication and editor association remain consumer concerns.

#529 is therefore explicitly downstream, even though `WIRE-PLAN/B` contains a test-only projection adapter proving that
the carrier has enough information. A projector test is not a generator implementation.

| Owner | Slice / evidence |
| --- | --- |
| #932 | `WIRE-PLAN/A`, `WIRE-PLAN/B`, later runtime interpreter and its accepted/rejected corpus |
| #529 | test-only adequacy projection in `WIRE-PLAN/B`; production generator only after #932 runtime acceptance |
| ADR 0009 | unchanged four-mode Jackson characterization throughout |

The tracer has no user-visible feature, so it adds no documentary test, user page, migration entry or `CHANGES.md` item.
The later runtime slice must add those release-facing artifacts together with its first public entry point and concise
domain-tree example; #529 documents structural-validation limits with its plugin delivery.

## Risks and open questions

| Risk / question | Control |
| --- | --- |
| A private carrier accidentally becomes generated public ABI. | Package-private inventory, JPMS fixture and the carrier stop gate; no exported plan type or client reader. |
| Identity/camel/kebab policy needs a public configuration surface. | `WIRE-PLAN` accepts one injected internal selection and proves exactly one spelling; it does not invent the later surface. Runtime work waits until the selected policy reaches runtime and #529 identically. |
| Converter overloads imply more than one wire payload shape. | Admit only compiler-proven one-payload groups in this tracer; record the first irreducible converter case and stop instead of adding coercion rules. |
| A separately compiled parent plan cannot be composed without loading code. | The binary/no-execution gate must demonstrate composition from carrier class files; source fallback is forbidden. |
| Internal deterministic application order becomes observable. | Domain input remains unordered and no escape hatch is added. Runtime planning must characterize a fixed framework order before executing side-effecting operations. |

## Overengineering audit

The module earns depth by centralizing four decisions that would otherwise be duplicated: role admission, name resolution,
collision rejection and payload shape. Its private carrier is the seam; the runtime-shaped and generator-shaped readers
are the two adapters that justify it. The interface is only the immutable facts in the table above, and those facts are
also the test surface.

Deletion test: without the plan, runtime and generator would each rediscover roles and names and could drift; with the
plan removed, that duplicated complexity returns. By contrast, a public descriptor/codec/registry, universal deserializer,
generic call AST, subtype scanner, reference registry, context provider, parser, lifecycle engine or publication framework
would add interface without serving the first grammar and therefore fails the audit.

## Validation and delivery for this planning revision

This revision changes only ADR and implementation-plan Markdown. Validate CommonMark structure, local file links and
`git diff --check`; Groovy 3/4/5 lanes are intentionally omitted under the documentation-only rule. Conduct the repository's
independent Standards and Specification reviews against the branch base. A focused documentation commit may be pushed as a
draft PR only after those reviews are clean; use `Related: #932` and `Related: #529`, never closing either. Request KlumAST
Hive reconciliation with the exact commit, review findings and validation. An open PR remains `(PR:open)`, not archive-safe.
