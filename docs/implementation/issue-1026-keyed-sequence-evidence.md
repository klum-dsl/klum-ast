# #1026 — keyed owned child sequences

Base: `059e8de36d4a100add397cd9ac4e7e92e6409663` (verified origin/master, merged #1025).
Authority: [#1026](https://github.com/klum-dsl/klum-ast/issues/1026),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#keyed-owned-sequence-extension--1026).

## Characterization before implementation

Commit `f29a7f3ad` records 13 passing Groovy 3 cases before changing production code. Struct rejects the keyed
sequence at `/routes` before child allocation. Ordinary keyed creation establishes identity before PostCreate,
configures before PostApply, then attaches. Each call creates a fresh child even for repeated keys. Lists retain
all entries; Sets collapse equal completed Models after all fresh Builders receive graph lifecycle; comparator Sets
retain their ordinary representative and only retained Builders receive graph lifecycle. Operations follow call order.
Renaming/exclusion annotations do not change the positional ordinary Groovy String argument. Empty String is accepted.
An explicit null passed to the keyed creator also creates a child; Integer, Boolean and Map arguments have no matching
creator. Struct intentionally reuses its established self-contained required-String rule, which rejects null and
non-String values; it adds no generic conversion or new key policy. Template definition runs no child callbacks,
retains keyed recipes, and copies fresh owned children into recipients.

## Implementation and boundaries

Compiler admission removes only the keyed-child exclusion. Runtime resolution removes the matching exclusion while
retaining field association, concrete type, generic and metadata checks. The interpreter already resolves the member
key through the same child Domain plan as direct children; it now passes that identity to ordinary collection child
preparation. No public/generated signature, annotation member or protocol version changes. Recompile matched Schema
artifacts to replace the old compiler exclusion facts.

No Map derivation/coercion, CollectionFactory, polymorphism, generic target, parser or composition work is included.
Traversal precedes ordinary destination collection semantics; input indices remain diagnostic coordinates. Templates
retain their existing recipes, and a keyless Template root still requires identities for its keyed sequence children.
The existing projector needs no production change: member-key position yields the required external String key.

## Executable and documentary evidence

- `StructuredKeyedSequenceTest` covers fresh identities, duplicates with equal/different bodies, all four destinations,
  lifecycle/order/ownership, naming profiles/renaming, ignored/colliding/missing/malformed keys, partial failure,
  comparator failure indices, child/parent Templates and corrupt child-key metadata.
- Documentary method `creates keyed routes from structured sequence items` links
  [keyed child sequences](../user/Structured-Input.md#keyed-child-sequences) via `@See` and `@Tag('documentary')`.
- `StructuredOwnedSequenceTest` preserves the existing unkeyed and corruption regressions, removing only the now
  admitted keyed case from its exclusion table.
- `BinaryOwnedRelationshipsTest` extends the separately compiled, source-free API/Schema/static-consumer fixture for
  Groovy 3/4/5 with renamed-key List/Set items and an inherited sequence field, plus required-key JSON Schema assertions.

## Local review

Exactly two task-limited subagents reviewed the fixed-base diff: Standards and Specification. Standards found one stale
JSON Schema exclusion sentence; removing `keyed` addressed it. Specification found no actionable mismatch. Both agents
also reviewed the independent schema-validator addition and confirmed zero unresolved findings.

## Validation

- Pre-change Groovy 3 characterization: 13 cases passed.
- Final focused keyed/unkeyed sequence regression run: 43 keyed and 62 unkeyed cases passed in each of Groovy 3/4/5
  (315 cases total), including corruption and comparator failures. Each Gradle task received its own test filters.
- Source-free binary fixture: all seven cases passed, including interpretation and JSON Schema projection on Groovy 3/4/5.
- Independent `jsonschema==4.23.0` validator: 36 accepted / 72 rejected keyed cases and 45 accepted / 225 rejected
  unkeyed cases passed against the actual emitted Groovy 3/4/5 schemas.
- Markdown fence and relative-file checks passed (156 links); final diff and renderer checks are recorded after completion.

The first combined build/render invocation omitted the renderer's required explicit immutable-revision properties.
That attempt was cancelled after the rendering setup error and is not counted as passing validation; root check and
rendering are rerun separately on committed artifacts.
 Hosted CI/SonarCloud and merge state
are separate delivery evidence. Tracker impact is neutral `Related: #1026`; #1026 remains open for Hive reconciliation.
