# A coherent, bounded structured-authoring stack

Date: 2026-11-26

Status: **Accepted strategy; new projection, codec and default-metadata guarantees require separate qualification.**

Authority: the maintainer's accepted decisions 1–16 in the structured-authoring strategy interview of 2026-11-26.
The [reconciliation record](../implementation/adr-0034-structured-authoring-reconciliation.md) identifies the evidence,
tracker ownership and remaining decisions. This documentation assignment authorizes no implementation or release change.

Parent decisions retain their responsibilities:

- [ADR 0009](0009-jackson-interoperability.md): managed foreign-format Jackson import and ordinary completed-Model export,
  including the existing 4.x compatibility commitments.
- [ADR 0031](0031-structured-schema-input-artifacts.md): compiled-contract authority, source-free inspection and qualified
  JSON Schema projection for explicit editor/consumer validation.
- [ADR 0032](0032-builder-oriented-structured-input.md): compiler-admitted Struct operations, Factory/Builder/Template
  construction, positional key authority and ordered execution with earlier effects retained on failure.

This ADR extends product responsibility and sets future qualification requirements. It does not supersede those
contracts or rewrite their historical checkpoints. It supersedes #982's unaccepted-preference wording and stale capability
matrix as current guidance; no Jackson sunset, parity or migration program is accepted.

## Context

Recurring consumer integration needs justify KlumAST owning more than examples of caller composition. Schelm's
[descriptor delivery](https://github.com/klum-dsl/schelm/pull/64) demonstrates a bounded composition of FromStruct,
Jackson in-memory projection and checked YAML emission. Its
[decoder evidence](https://github.com/klum-dsl/schelm/pull/47) also shows that parser/emitter defaults can change scalar
types, key identity or String values. Those consumer fixtures support a bounded strategy, not universal reconstruction.

Four responsibilities answer different questions:

| Responsibility | Authority | What it does not establish |
| --- | --- | --- |
| Authored input language | Compiler-admitted Struct operations alongside richer Groovy DSL authoring | Every data tree or public Builder method is executable input |
| Text parsing/emission | Optional, separately qualified YAML/shared-tree integration | Model construction, projection or reconstruction |
| Completed-Model projection | Initially existing optional Jackson, with mapper/Schema-owned shape | Recovery of an authored program or automatic FromStruct compatibility |
| Trusted source migration | Separate optional research in #955 | A prerequisite for the structured-authoring stack |

Current parsing remains caller-owned. No Klum YAML integration is delivered by this decision.

## Accepted decisions

### 1. Product responsibility

KlumAST owns a coherent bounded structured-authoring stack alongside Groovy: Struct construction, aligned
documentation/schema/editor guidance, and optional text integration under the qualification gates below. Struct is
preferred for Schema-owned configuration where its admitted language fits. Managed Jackson remains the supported
foreign-format mapping route; existing Groovy, FromStruct/applyStruct, FromMap and managed Jackson behavior stays compatible.

Product ownership does not require a new parser or serializer. Core/runtime remain parser-free.

### 2–4. Shared values, numbers and identity

The shared value tree consists of ordered String-keyed Maps, Lists, Strings, booleans, supported finite numbers and null.
It is finite and acyclic, with no object-identity semantics. Repeated acyclic containers may represent repeated structural
values without preserving shared identity. An absent member differs from a present null when supplied to projection/emission;
this says nothing about original authoring presence retained on a completed Model.

Tree representability is independent of Struct admission. In particular, current FromStruct still rejects null at every
depth, requires its root Map and retains its own operation grammar and positional keys. No preflight or rollback is added.

Future Klum-owned adapters must preserve exact integer/decimal values, using BigInteger/BigDecimal where needed, and
reject unsupported or nonfinite values rather than silently round. Original numeric spelling, exponents and trailing
zeros are not preserved. Qualification must specify how preexisting Float/Double nodes are handled without inventing
lost precision. Existing finite float/double Schema-target conversion may round; caller-owned Jackson settings are unchanged.

Model references require explicit mapper/domain projection into data, omission or rejection. Neither `$ref` nor any
other ordinary String member becomes a framework reference instruction. Generic graph rehydration is excluded.

### 5–6. Effective-value projection and qualified reconstruction

Completed-Model output describes effective values by default. Reconstruction is a distinct claim, limited to explicitly
named and qualified Model/projection subsets with stated comparison values and external context. Neither schema validity
nor successful text reparsing proves it. Derived values, defaults, commands, noninvertible converters, keys and lifecycle
recomputation can prevent an inverse; original source, omission history, authoring intent and object identity are not recovered.

For example, ordinary Jackson output may include `[services: [api: [name: 'api', port: 8080]]]`, while admitted Struct
uses `[services: [api: [port: 8080]]]`: the outer Map entry owns the child's key and the body forbids an inner key.

Existing optional Jackson in-memory conversion with the configured mapper and `KlumAstModule` is the initial Model-to-tree
route to qualify. There is no justified parallel core Model serializer. Jackson naming, inclusion, ordering and codecs
remain mapper/Schema-owned; Struct annotations do not become output directives. A successful `convertValue` alone does
not qualify arbitrary Models/codecs for exact numbers, acyclicity or reconstruction. Template rejection and explicit LINK
output choices remain intact. This selects neither a new convenience facade nor separate projection/reconstruction APIs.

### 7. Optional YAML responsibility

The optional integration should own both YAML-to-tree parsing and tree-to-YAML emission. Callers compose construction
with FromStruct/applyStruct and output with separately configured Jackson projection. The YAML boundary owns no Model
introspection, construction engine or required `klum-ast-jackson` dependency. Library transitive dependencies require later
assessment. No library, public API, module/artifact name or dependency is selected here.

### 8–12. Fixed YAML input policy

The future adapter's policy is fixed rather than selected implicitly by library defaults:

| Concern | Accepted requirement |
| --- | --- |
| Scalars (8) | YAML 1.2 Core resolution, restricted to finite exact shared-tree values. Plain `yes`, `on` and dates are Strings; `true`/`True` are booleans; `01` is decimal 1; `0o10` is 8; `null`, `~` and an empty value are null. Quoted forms remain Strings. Target types never choose resolution. |
| Mapping keys (9) | Resolve under the same policy and require decoded Strings. Reject non-String/complex keys without coercion. Reject duplicates at every depth by exact decoded String equality before insertion; apply no case folding or Unicode normalization. Plain `a` and quoted `"a"` collide. |
| Reuse and merges (10) | Reject anchors, including unused anchors, and all aliases. Do not expand merges. Plain/quoted `<<` are the same ordinary String data key; an explicit merge tag is unsupported. Emit repeated structural values without anchors/aliases. |
| Explicit tags (11) | Allow only standard Core `str`, `bool`, `int`, `float`, `null`, `map`, `seq`, including standard full URI forms. Validate node kind/value under the same contract; tags cannot bypass numeric/key checks or select a Model. Reject custom/JVM, timestamp, binary, set, omap, pairs and merge tags. |
| Documents and roots (12) | Exactly one document per parse/emission operation; reject a second document and consume the whole input. Any accepted tree root is allowed; FromStruct's root Map restriction remains a separate consumer check. Empty-input/document and directive behavior require explicit qualification decisions. |

The [YAML 1.2.2 Core resolution rules](https://yaml.org/spec/1.2.2/#1032-tag-resolution) describe scalar meaning;
this ADR deliberately accepts only the bounded feature set above. Existing caller parsers and managed Jackson YAML
retain their contracts. A tree null remains data even though current Struct construction rejects it.

### 13. Emission fidelity

Emission preserves supplied scalar types/values, exact numbers, decoded Strings, Map traversal order and List order.
Parsing preserves encountered mapping/sequence order. For the same ordered tree and fixed codec version/configuration,
emitted bytes are deterministic. Do not sort containers to achieve determinism; projection owns deliberate property ordering.
Quote/escape ambiguous Strings and keys to preserve value and type. Reject unsupported or value-changing emission.

Exact Unicode preservation concerns supplied decoded Strings, not recovery of source folding/line normalization.
Unpreservable String forms may be rejected with documented diagnostics. This promises no source comments/style recovery,
canonical YAML, byte stability across upgrades or universal downstream decoder compatibility. Consumer decoder compatibility
requires separate fixtures. Source-comment loss does not preclude a separately justified generated-comment capability.
Ordinary Jackson output gains no new order/byte guarantee from this decision.

### Retained boundary: validation is explicit

**JSON Schema is editor/explicit consumer validation, never an automatic FromStruct, applyStruct or YAML runtime gate.**
Compiled admission facts govern Struct execution; completed-Model validation governs finished invariants. Generated input
schemas do not become output/reconstruction schemas. `Struct.Required` affects generated presence assertions only;
completed `Required`/Bean constraints and defaults do not imply raw-input presence.

Executable parameter enforcement (#971) remains separate from raw selected-parameter projection (#981). No parser,
projection annotation or generated schema silently enables enforcement or changes runtime defaults.

### 14. Literal defaults as authoring metadata

Statically known literal declarative defaults belong in authoring documentation/JSON Schema metadata at the exact
field/relationship use site. Do not execute defaults, Templates, constructors, lifecycle or validators to discover them.
Computed/context-dependent defaults remain runtime behavior and prose. Eligibility, inert compiled transport and exact
projection placement remain future proof work under #959; no metadata ABI or keyword-overlay design is selected.

Two relationships to one child type may declare defaults 2 and 8; one default on their shared type definition would be
false. Advisory metadata inserts no values, changes no requiredness and does not establish that omission creates a
relationship. It changes neither zero/default handling nor Struct execution/validation.

### 15. Qualification before library/API commitment

Require bounded executable qualification of a candidate YAML library plus a thin fixed-policy adapter before choosing
the library or public API. Source inspection and library resolver labels alone are insufficient. #1114 owns this gate:

- Verify scalar/tag/key resolution before information is lost, duplicates, exact numbers/nonfinite rejection,
  anchors/aliases, literal non-expanding `<<`, whole single-document consumption and empty-document/directive behavior.
- Compare original and reparsed typed values and ordered entries for emission, including Unicode/ambiguous Strings,
  and check deterministic bytes for fixed inputs/version/configuration.
- Enforce finite input byte/code-point, depth, node/entry, scalar/numeric-token magnitude/length and output-size budgets
  at the relevant parse/emit boundaries. Choose measured budgets from consumer fixtures, not arbitrary strategy constants.
- Exclude parser-selected JVM/domain construction or reflection, schema execution and hidden I/O. Use ordinary library
  event/node seams with a small adapter; no custom YAML parser, dialect framework or generic serializer platform.

A failed requirement remains a documented gap for a later decision, never a silently weakened guarantee. #1113 separately
qualifies Jackson-to-tree fidelity and named reconstruction subsets; it need not wait for a YAML library. An input-only
qualification slice need not depend on Model output, although the accepted integration responsibility includes both directions.
No research execution or implementation is assigned by this ADR.

### 16. Trusted source migration stays separate

#955 remains optional adopter-driven research, outside required stack acceptance. A future trusted-source probe must
bound eligible Groovy/Template forms and compare retained values/recipe state with scoped admitted-call recording.
Neither approach is selected. Running source is not a security boundary. Diagnose unsupported closures, deferred actions
and lifecycle-dependent behavior rather than silently flatten them. Loading an ordinary script that returns a completed
Model may already run its lifecycle; calling that loading route Template conversion does not remove the effect.

For example, `applyLater { label name.toUpperCase() }` computes per-recipient values that a current value snapshot cannot
preserve. Normal Jackson Template serialization remains rejected. Migration is independent of effective-Model output.

## Consequences and alternatives

KlumAST accepts cross-boundary consistency and future qualification cost because recurring integrations warrant product
ownership. Keeping only caller composition would cost less maintenance and remains a viable current workaround, but leaves
the demonstrated fidelity work duplicated. Reusing Jackson avoids a second Model serializer while retaining mapper-owned
asymmetry. A bounded YAML policy trades full YAML reuse/features for explicit, testable values and operation order.

Reject automatic Model/Struct inversion, automatic schema runtime gating, a generic call/reference/dialect framework,
and silently selected parser defaults: each crosses an independently owned contract. Product ownership implies neither
full Groovy parity nor general producer/converter/polymorphic admission. #1065 and #1033 retain their separate decisions.

This strategy changes no source/runtime code, public/generated API, lifecycle, ownership, serialization, metadata protocol,
module, parser dependency or Groovy compatibility contract. Existing managed Jackson 2 remains supported; #929's separately
compiled Jackson 3 question is independent. File/URL/network loading (#953), active-child Factory projection (#1085), modular
schema topology (#1082) and runtime parameter enforcement (#971) remain separately owned. No milestone, release placement,
issue closure or implementation assignment follows. The [reconciliation record](../implementation/adr-0034-structured-authoring-reconciliation.md)
maps all affected issues and pre-implementation decisions.
