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
| Packaged binary relationships preserve ordered effects on G3/G4/G5 | `BinaryOwnedRelationshipsTest` interpreter controls: earlier sibling, bad child payload, untouched later sibling; projection controls retain no-execution traps |
| Static malformed contracts fail before execution, including omitted children | Unchanged `InputLiteralTest` omitted-owned protocol controls and `StructuredInputKeyBoundaryTest` malformed/stripped metadata tests |
| User-facing partial-state example | `StructuredInputRuntimeTest#'supplied operation order configures the Builder until the first invalid member'` (`@Issue('952')`, documentary tag and `@See`) |

The earlier `StructuredInputRuntimeBoundaryProbeTest` contains a deliberately fixture-only preparation experiment from
#932; it is historical seam evidence, not the production interpreter's failure contract. ADR 0031 and its dated evidence
likewise retain their historical preflight description.

## Local review

Exactly two read-only children reviewed base `14683b7b8` through `07fdfdfc9`, then verified implementation correction
`a204c0447` and constraint-characterization follow-up `a5e2ea3fc`: **Standards** and **Specification**. Capacity was parent plus two local review children (3); no implementation
or cross-project delegation occurred. Their scope was source/specification inspection, with no builds or remote changes.

Standards reported no code violations or actionable smells; it identified a delivery traceability gap on #952.
Specification found that Map lookup semantics could accept a comparator alias or reject an equal but distinct String key.
Follow-up `a204c0447` scans the required key by exact String equality, with IdentityHashMap and case-insensitive TreeMap
root/direct-child regressions. Both children verified that follow-up and the later Bean Validation characterization update with no remaining
code/specification findings. The latter updates the old zero-root-creation expectation for invalid payloads;
Bean Validation G3/G4/G5 suites passed with the new per-operation expectation.
The final packaged-consumer follow-up updates the same retired expectation and adds an earlier/later sibling trace.
The parent task records both reviewers' final committed-head confirmation and the issue traceability reconciliation at delivery.
Reviewed history is preserved; corrections remain focused follow-up commits.

## Validation and delivery

Validation on 2026-11-01 used the final production/test source tree:

- Focused Groovy 3 runtime/key/literal tests, then affected runtime/compiler baseline suites: passed.
- Exact-key review correction: focused runtime/key tests passed.
- Bean Validation characterization correction: focused baseline plus full G3/G4/G5 suites passed.
- Packaged binary fixture correction: all six projection/interpretation cases across G3/G4/G5 passed.
- `./gradlew check --continue --no-parallel --max-workers=2 --console=plain`: **passed**, 140 actionable tasks.
  Includes baseline/compatibility suites, binary/JPMS controls, schema tooling, license checks, lane isolation,
  coverage-report generation and versioned-documentation renderer verification. Groovy 4 and 5 each ran all 11 JPMS cases.
- Local Markdown targets (161 links across eight changed documents) and `git diff --check`: passed.

| Module | G3 cases (existing skips) | G4 cases (existing skips) | G5 cases (existing skips) |
| --- | ---: | ---: | ---: |
| Compiler | 2540 (16) | 2542 (15) | 2542 (15) |
| Runtime | 73 (1) | 73 (1) | 73 (1) |
| Jackson | 121 (0) | 119 (0) | 119 (0) |
| Bean Validation | 147 (0) | 147 (0) | 147 (0) |
| Test support | 8 (0) | 6 (0) | 6 (0) |

The plugin suite passed 650 cases; annotations passed 20. Every final suite has zero failures/errors. No test suppression
was added. Earlier full runs exposed seven Bean Validation and three packaged-consumer assertions of the retired preflight
contract; those expectations were updated and the final aggregate run is green. Final evidence text does not change the
exercised production/test sources. No local SonarCloud upload was performed; hosted CI/SonarCloud remain separate evidence.

Delivery authorization checks: Git SSH dedicated-branch transport **authorized** (dry run); GitHub CLI repository channel
**authorized**. The live target remained the exact assigned base when checked before publication. No credentials or scopes
are recorded.

Tracker impact: **Related: #952**; this S1 slice leaves the broader coordination issue open. Curation impact: **none**. No label, milestone,
dependency, release-gate or curation-index change is part of this localized runtime slice. The documentary example and
[order/failure documentation](../user/Structured-Input.md#order-and-failures) are linked here for issue reconciliation.
Hive owns final acceptance/archive reconciliation and cross-project notification; no Schelm qualification is claimed.
