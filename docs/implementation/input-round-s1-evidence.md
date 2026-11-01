# S1 — ordered structured-input traversal

Implements the S1 runtime slice of [ADR 0032](../adr/0032-builder-oriented-structured-input.md) and the
[4.2 input plan](input-round-plan-4-2.md), related to [#952](https://github.com/klum-dsl/klum-ast/issues/952).
Base: `14683b7b8aad3781a337e639abcad8cea069460f` (includes V0 / PR #1001).

## Delivered boundary

`StructuredInputSupport.Traversal` verifies the complete admitted static contract graph before construction, then
resolves and invokes each supplied operation when reached. A domain scope retains the supplied Map, its contract,
position and required construction key; it no longer retains a prepared operation/child tree. Required member identity
is read before that object's allocation. Externally supplied identity is never rebound.

Direct children and keyed Map children still use the existing Consumer construction methods. Population Lists/Maps
validate and invoke one item at a time. A typed authored List/Map argument is checked as one argument before invoking its
operation. Active container tracking rejects cycles and releases entries in `finally`; repeated acyclic data is accepted.
Exact public-method selection, source/pointer/declaring-contract diagnostics, original causes and normal lifecycle
cleanup remain in their owning seams.

A payload failure stops at the first reached failure. Earlier operations and ordinary effects remain; root reads return
no completed Model, and active Builders can remain partial. There is no rollback, reset, forced completion or whole-input
snapshot. Supplied iteration order is the only ordering contract. No grammar, metadata ABI, generated callable signature,
parser, Template/Factory API or schema projection is added. Existing schema output remains structural and cannot promise
execution order or construction success.

## Acceptance map

| Contract | Executable evidence |
| --- | --- |
| First mutation, bad name/type, no later mutation; partial unsealed Builder | `StructuredInputRuntimeTest`: `active Builder retains only reached operations on payload failure` |
| Direct child and keyed Map first-failure order, escaped paths | Same data-driven test; existing nested ownership/population trace |
| List/Map population versus one typed operation argument | `population retains earlier elements but a typed operation argument is validated as one payload` |
| No root result; subsequent root cleans up normally | `root failure returns no Model after earlier effects and cleans up for the next root`; retained original-cause/Error/lifecycle tests |
| Required identity before allocation, late keys do not precheck bodies | `StructuredInputKeyTest`: `late keys precede allocation while unrelated body members wait for traversal`; missing root/child identity controls |
| External identity cannot be rebound; providers run when reached | Updated active-identity/provider tests; retained fixed/derived/Cluster, reuse, binary key tests |
| Reverse order, recursive traversal, List order and cycles | `StructuredInputRuntimeTest` root-order, nested/population, cyclic/repeated-data tests |
| Static malformed contracts fail before execution, including omitted children | Unchanged `InputLiteralTest` omitted-owned protocol controls and `StructuredInputKeyBoundaryTest` malformed/stripped metadata tests |
| User-facing partial-state example | `StructuredInputRuntimeTest#'supplied operation order configures the Builder until the first invalid member'` (`@Issue('952')`, documentary tag and `@See`) |

The earlier `StructuredInputRuntimeBoundaryProbeTest` contains a deliberately fixture-only preparation experiment from
#932; it is historical seam evidence, not the production interpreter's failure contract. ADR 0031 and its dated evidence
likewise retain their historical preflight description.

## Validation and delivery

Focused Groovy 3 runtime/key/literal tests passed, including the final ordering/allocation assertions.
Affected-module baseline suites passed: runtime 73 cases (1 existing skip), compiler 2,534 cases (16 existing skips).
Compatibility and root checks are pending. Local Markdown targets (161 links) and `git diff --check` passed.
No local SonarCloud upload is performed. CI and SonarCloud remain separate hosted evidence.

Tracker impact: **Related: #952**; this S1 slice leaves the broader coordination issue open. No label, milestone,
dependency, release-gate or curation-index change is part of this localized runtime slice. The documentary example and
[order/failure documentation](../user/Structured-Input.md#order-and-failures) are linked here for issue reconciliation.
Hive owns final acceptance/archive reconciliation and cross-project notification; no Schelm qualification is claimed.
