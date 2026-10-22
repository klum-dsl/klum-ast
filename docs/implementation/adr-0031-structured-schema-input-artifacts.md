# ADR 0031 implementation plan — compiler seam and `WIRE-RUNTIME` v1

Date: 2026-10-21. Updated: 2026-10-22. Status: **`WIRE-PLAN` merged; `WIRE-RUNTIME` v1 proposed; no production interpreter or generator authorized**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Language/interpreter owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Independent generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Authority: merged [PR #933](https://github.com/klum-dsl/klum-ast/pull/933) and its
[WIRE-ARGS/WIRE-NAMES/WIRE-ORDER evidence](evidence/issue-932-structured-input-probe.md).

## Current handoff — `WIRE-PLAN` complete, `WIRE-RUNTIME` proposed

[PR #937](https://github.com/klum-dsl/klum-ast/pull/937) and
[PR #938](https://github.com/klum-dsl/klum-ast/pull/938) delivered the compiler-owned admission seam. The selected binary
representation is the package-private, constant-only `<Model>_DSL$WirePlan` carrier; annotations alone lose normalized
roles and source identities. [Carrier qualification](evidence/issue-932-structured-input-probe.md#wire-planb-carrier-qualification)
records separate-artifact inheritance, classfile-only/no-execution extraction, generated ABI, JPMS, Groovy 3/4/5 and Gradle
output-lifecycle evidence. The production baseline for the next plan is merge `3d13ffa2d`.

The next reviewable proposal is [WIRE-RUNTIME v1](adr-0031-wire-runtime-v1.md). It keeps the accepted grammar, defines root
and active-Builder boundaries, and names the internal Consumer extension needed to preserve relationship creation without
synthetic closures. This planning revision adds only that proposal and a fixed-call runtime-boundary test. No interpreter
API, runtime reader, dispatcher, parser or behavior change is delivered. #932 and #529 remain open and independent.

The remaining `WIRE-PLAN` sections below preserve the original tracer specification and its stop conditions as historical
acceptance criteria. Their prospective representation comparison and implementation gates have been satisfied by the
linked merged work; they are not instructions to repeat that work or authorization to implement either consumer.

## Outcome and stopping boundary

`WIRE-PLAN` is the first implementation tracer. It gives the compiler one private, immutable normalized admission plan for the
accepted subset and proves that the same facts can later drive both #932 runtime interpretation and #529 Draft 2020-12
generation. It does not add a public descriptor, SPI, registry, parser, interpreter, Gradle plugin or Jackson mode.

The tracer is complete only when the compiler can classify the admitted entries, reject every excluded or ambiguous
entry, carry the plan through separately compiled Schema artifacts without executing Schema code, and expose equivalent
facts to two test-only consumers: a runtime-shaped reader and a JSON-Schema-shaped projector. Both are proof adapters,
not production delivery. The binary representation is selected only by the bounded annotation-versus-carrier comparison
below. Stop after the evidence report and maintainer review; do not continue into either production consumer.

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
not its effects. Custom Builder methods remain first-class language operations, not exceptional escape hatches. Generated
property/converter overloads may form one compiler-known property group, but competing authored one-payload overloads reject
rather than relying on runtime guessing.

External-name precedence is exact:

1. `@JsonIgnore(true)` excludes the entry.
2. A literal, nonempty `@JsonProperty` supplies the only external spelling.
3. Otherwise the selected fallback supplies the only external spelling. `WIRE-PLAN` selects identity.

`@JsonIgnore(false)` does not exclude and an empty `@JsonProperty` falls through. Identity applies to domain entry names
only, never to ordinary data-map or keyed-domain-map instance keys. Inherited, explicit-rename and overload-group collisions
reject deterministically with all conflicting declarations named. Camel/kebab fallback selection remains outside this
tracer; no unused strategy hook or configuration interface is introduced.

The `WIRE-PLAN` grammar does not admit generic Builder call records, `$args`, positional spreading, zero-argument commands, duplicate
keys, repeated calls, source-order guarantees, an ordering escape hatch, unkeyed relationship collections, polymorphic or
abstract targets, aliases, dynamic Class/Factory selection, generic values bags, Template/copy/session/lifecycle/query
controls, context acquisition, reference ids, layering/patch input or round trips. Domain-object member order is not a
language contract; a future runtime must use deterministic framework ordering without treating parser iteration order as
user syntax.

Those omissions bound this tracer; they are not permanent product exclusions. Intended later language extensions include
multi-argument `@Builder.Method` operations, custom converter and factory operations, camelCase and kebab-case naming,
unkeyed relationship collections, `CollectionFactory` scopes and element creators, and additional argument representations
where justified. Each needs its own language decision and shared consumer corpus. `WIRE-PLAN` neither selects their syntax
nor adds placeholder strategy or descriptor hooks for them.

## Smallest compiler-owned plan

The plan is a deep internal module: classification, naming, collision detection and payload shape stay behind one small
compiler interface. A concrete target plan contains only its generated Model/Builder identity and a deterministic map of
entries. Identity fallback is a `WIRE-PLAN` compile-time constant, not carried plan data. Each entry contains:

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
known. Direct source AST supplies roles; the selected binary representation must preserve inherited roles across separately
compiled parent artifacts. Optional `JsonIgnore` and `JsonProperty` facts are read by annotation name and literal members
without loading Jackson types or adding a Jackson dependency to compiler/runtime. Public method visibility, NamedParam
metadata, return type and arbitrary reflection are never admission evidence.

## Binary representation comparison and runtime seam

`WIRE-PLAN/B` compares exactly two persistence candidates against the same normalized plan and corpus:

| Dimension | A. Retained annotations on public generated methods | B. Package-private generated carrier |
| --- | --- | --- |
| Required metadata completeness | prove operation role, external name, payload shape and source identity without inference | prove the same facts exactly; record which one annotations could not preserve |
| Binary retention | read inherited facts from separately compiled public Builder/Factory contracts | compose inherited carrier facts from separately compiled Schema/API artifacts |
| No-execution extraction | inspect class-file annotations without loading or initializing Schema code | inspect carrier class bytes without loading or initializing Schema code |
| Runtime lookup | resolve retained facts from the concrete public contract deterministically | locate one carrier from the concrete Model identity without scanning or registration |
| JPMS accessibility | use only readable public contract and annotation types | prove package-private access through the existing Schema-package opening, with no flags or new export |
| Generated ABI compatibility | qualify retained annotation identities, members and encodings | qualify carrier identity, members and encoding as generated ABI |
| Complexity | count additional emitted annotation uses/members, extraction branches and maintenance points | count new class-file shape, emitter/reader code and maintenance points |

Prefer annotations if they preserve every required fact. Select the carrier only when the evidence names a concrete missing
annotation capability and shows materially better inherited-role, nested-relationship, deterministic-extraction or
dual-consumer support. Do not introduce a third metadata architecture, generalized descriptor framework, public reader,
classpath scan, registration step or service lookup. Candidate A may reuse existing retained annotation types only; this
tracer does not authorize a new public annotation or API.

If selected, the carrier is one package-private compiler-generated class beside each `Foo_DSL` contract, with reserved
implementation naming, no public members and constant plan facts only. It is not a public Schema Developer API, yet its
identity and encoding become part of the published generated binary contract. Supported newer KlumAST tooling must remain
able to read separately compiled Schema/API artifacts from supported older 4.x lines. Package privacy does not remove this
ABI obligation; [ADR 0015](../adr/0015-generated-schema-runtime-linkage.md) and
[#896](https://github.com/klum-dsl/klum-ast/issues/896) supply the directional compatibility context. `WIRE-PLAN` adds no
version negotiation, compatibility shim, runtime-floor enforcement or public descriptor version protocol.

Generated bytecode may name only JDK, Schema and already approved `runtime.generated` types; it must not name
`runtime.internal`. If neither annotations nor a package-private carrier can satisfy JPMS, the smallest supportable binary
contract and no-execution extraction without a new exported descriptor type, stop and revise the design rather than widening
ADR 0015 implicitly.

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
| names | ignore, literal rename, identity fallback and inherited members | inherited/explicit-rename/overload collision lists every declaration; empty rename falls back |
| domain operation | inherited/direct one-payload `@Builder.Method`; Map/List remains one payload | genuine multi-/zero-argument, query/helper/copy/session/lifecycle/Template controls and competing authored overloads reject |
| active Builder | #933 ApplicationHolder route remains unsealed and unmaterialized while input is applied | no public manual-session interface and no nested root lifecycle |
| completed LINK | caller attaches completed Environment before enclosing completion; identity and later lifecycle remain intact | serialized context acquisition/reference registry absent |
| managed Jackson | all four importer modes retain their existing characterization tests | no signature, callback-count, mapping or diagnostic parity rewrite |
| diagnostics | source identity, escaped input path, external name, selected contract, expected/actual and preserved cause | unknown/ambiguous names and malformed nested/keyed shapes reject before invocation |
| compiled contracts | both candidates preserve the same separate Layer 3 API -> Schema -> API-only consumer facts, inherited roles and dependency invalidation | no source mirror, subtype scan or Schema initialization |
| extraction | runtime-shaped and #529-shaped readers produce equivalent normalized facts | constructors, factories, initializers/defaults, converters, lifecycle and annotation closures never execute |

New executable classes use the `Test` suffix and `@Issue("932")`; tests that solely prove the static projection use
`@Issue("529")`. `WIRE-PLAN` is internal and needs no documentary test or user page. Keep the disposable probe adapter as
evidence; do not promote it into production.

## Proof gates before runtime implementation

1. **Admission gate:** every accepted grammar production has one plan role and every non-goal has an explicit compile-time
   rejection or omission. Plan order and diagnostics are byte-for-byte deterministic across repeated compilation.
2. **Binary gate:** inherited and concrete plan facts survive separate API/Schema compilation and dependency changes;
   source and AnnoDocimal mirrors are absent from extraction inputs.
3. **Representation gate:** compare retained annotations and one package-private carrier on the bounded dimensions above.
   If annotations are complete, use them. If not, name the missing annotation capability and prove the smallest carrier;
   the generated-linkage inventory finds no internal-package descriptor/call, Groovy 4/5 named-schema linkage needs no
   workaround flag, and no public type/member or registry is added. Optional Jackson naming metadata adds no compiler/runtime
   Jackson dependency.
4. **No-execution gate:** accepted and rejected extraction in fresh JVMs initializes no Schema, enum, factory or callback
   code. The runtime-shaped reader may instantiate only framework plan values, never Schema code.
5. **Dual-consumer gate:** the two proof readers agree on names, roles, payload trees, nested references and rejection
   reasons. The #529-shaped projector describes no entry the runtime-shaped reader would reject.
6. **Reuse gate:** the named-map suite, all four managed Jackson modes and #933 Holder/LINK characterization remain green;
   no second overload resolver, lifecycle driver, owned-Model preconstruction or callback-order claim appears.
7. **Review gate:** Standards and Specification reviews have zero unresolved findings, the history is focused, and the
   maintainer accepts the evidence-backed binary representation. Only then may separate #932 or #529 production slices be
   planned.

Any failed gate stops `WIRE-PLAN`. In particular, stop if classification needs a public annotation/SPI, if neither candidate
can carry the required facts without a public descriptor or `runtime.internal` linkage, if the selected representation lacks
a supportable directional binary contract, if one-payload operation/property groups cannot be distinguished without runtime
guessing, if ordinary map keys or keyed instance keys leak into name resolution, or if #529 needs a fact that the accepted
runtime language does not need.

## Tracer slices and commit boundaries

Keep the stable tracer label `WIRE-PLAN` throughout; the letters below are local commit boundaries, not new roadmap items.

### `WIRE-PLAN/A` — classify the accepted plan in the compiler

Add the package-private plan model and classifier after generated public-contract completion. Cover direct/inherited fields,
concrete relationships, keyed maps, one-payload `@Builder.Method`, name precedence and deterministic rejections through a
focused compiler test. Do not emit a carrier or touch runtime/Jackson/Gradle code in this commit.

**Stop condition:** if existing compiler provenance cannot distinguish property, relationship and authored operation roles,
record the exact missing fact. Do not infer from method names or add a public marker.

### `WIRE-PLAN/B` — select and prove the smallest binary representation

Compare retained annotations with one package-private carrier, using the same separate-compilation,
class-file/no-execution, JPMS-linkage and dual-reader tests. Reuse the #933 Layer 3 fixture shape; compare canonical plan
dumps rather than exposing a reader publicly. If annotations are complete, retain the smaller solution. Otherwise record the
exact missing capability, emit the smallest carrier and update the implementation evidence with its classfiles, commands,
compatibility surface and omissions.

**Stop condition:** if compiler, runtime-shaped reader and #529-shaped reader require different facts, or if neither
candidate remains initialization-free and JPMS-accessible without a public descriptor, stop before any interpreter or
generator implementation.

These two commits are the complete tracer. No production runtime entry point, JSON Schema Gradle task, user documentation,
`CHANGES.md` entry or parser dependency belongs in them.

## Dependency sequence after `WIRE-PLAN`

`WIRE-PLAN` establishes the common admission, naming and argument contract. Once it is accepted and its binary metadata is
available, the production consumers may progress independently:

```text
                    WIRE-PLAN accepted
                           |
             +-------------+-------------+
             |                           |
             v                           v
   #932 runtime interpreter      #529 JSON Schema generator
```

#932 owns language semantics, runtime diagnostics and active-Builder interpretation through existing generated operations;
its corpus preserves the Holder/completed-LINK route. #529 owns static Draft 2020-12 projection and optional Gradle
integration; it reuses the accepted names and payload shapes without invoking or depending on the runtime interpreter.
Both begin from the same accepted/rejected corpus, but neither waits for the other's complete implementation. The runtime
may later admit operations beyond the generator's representable subset, provided #529 diagnoses unsupported shapes rather
than emitting permissive placeholders or claiming semantic parity. Gradle incrementality/configuration-cache and artifact
copying remain #529 work; distribution, publication and editor association remain consumer concerns.

| Owner | Slice / evidence |
| --- | --- |
| Common contract | `WIRE-PLAN/A`, `WIRE-PLAN/B` and the shared accepted/rejected corpus, tracked under #932 |
| #932 | later runtime interpreter, diagnostics and Construction-session integration |
| #529 | test-only adequacy projection in `WIRE-PLAN/B`, then independently scheduled static generation and Gradle integration |
| ADR 0009 | unchanged four-mode Jackson characterization throughout |

The tracer has no user-visible feature, so it adds no documentary test, user page, migration entry or `CHANGES.md` item.
The later runtime slice must add those release-facing artifacts together with its first public entry point and concise
domain-tree example; #529 documents structural-validation limits with its plugin delivery.

## Risks and open questions

| Risk / question | Control |
| --- | --- |
| The tracer freezes an unnecessary generated carrier. | Compare retained annotations first and select the carrier only for a named missing capability; no third representation. |
| A package-private carrier is mistaken for compatibility-free implementation detail. | Treat its identity and encoding as generated ABI, qualify older supported artifacts on newer tooling, and keep the contract minimal under ADR 0015/#896 direction. |
| A later camel/kebab fallback needs a selection surface. | `WIRE-PLAN` fixes identity and adds no strategy hook. A later slice requires concrete adopter need and must feed one selected spelling to runtime and #529 identically. |
| Converter overloads imply more than one wire payload shape. | Admit only compiler-proven one-payload groups in this tracer; record the first irreducible converter case and stop instead of adding coercion rules. |
| Separately compiled parent facts cannot be composed without loading code. | The binary/no-execution gate must demonstrate composition from retained annotation or carrier class bytes, according to the selected representation; source fallback is forbidden. |
| Internal deterministic application order becomes observable. | Domain input remains unordered and no escape hatch is added. Runtime planning must characterize a fixed framework order before executing side-effecting operations. |

## Overengineering audit

The module earns depth by centralizing four decisions that would otherwise be duplicated: role admission, name resolution,
collision rejection and payload shape. Its normalized plan is the seam; the runtime-shaped and generator-shaped readers
are the two adapters that justify it. The binary encoding is the smaller of complete retained annotations or a necessary
package-private carrier. The interface is only the immutable facts in the table above, and those facts are also the test
surface.

Deletion test: without the plan, runtime and generator would each rediscover roles and names and could drift; with the
plan removed, that duplicated complexity returns. By contrast, a public descriptor/codec/registry, universal deserializer,
generic call AST, subtype scanner, reference registry, context provider, parser, lifecycle engine or publication framework
would add interface without serving the first grammar and therefore fails the audit.

## Validation and delivery for this planning revision

For the current runtime-planning revision, validate Markdown links and `git diff --check`, and run the bounded runtime
probe on Groovy 3/4/5 alongside the existing admission and Jackson characterization. No production source changes. Conduct the repository's
independent Standards and Specification reviews against the branch base. A focused documentation commit may be pushed as a
draft PR only after those reviews are clean; use `Related: #932` and `Related: #529`, never closing either. Request KlumAST
Hive reconciliation with the exact commit, review findings and validation. An open PR remains `(PR:open)`, not archive-safe.
