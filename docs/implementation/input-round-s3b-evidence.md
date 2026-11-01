# S3b — deterministic CharSequence scalar routes

Related: #952. Authority: [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[S3b acceptance](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice),
[S2 selection](input-round-s2-evidence.md) and [S3a origins](input-round-s3a-evidence.md).

## Stack and delivery boundary

Base: `39e481e1d532c0f1a8145e79faa9ec6f8eee1f8c`, the assigned S3a PR #1004 head.
Worktree: `/Users/stephan/.codex/worktrees/2e62/klum-ast`.
Branch: `codex/952-charsequence-scalar`.
This is speculative local work stacked behind S3a. No push, PR creation, GitHub issue edits, merge or self-archive is
permitted. Tracker relationship is **Related: #952**, never closure. Hive owns later tracker/documentary-link and delivery
reconciliation. No curation status is changed by this implementation.

Single resume condition: **explicit resume after S3a PR #1004 is verified merged, to rebase only S3b once onto master and
rerun checks invalidated by integration**. Until then retain this clean worktree and local commits.

## Implementation and review focus

The existing strict scalar interpreter accepts only actual Strings for selected public CharSequence signatures.
The compiler records only already-generated exact String/CharSequence converter projections, and retains their exact
source descriptor. Both compiler provisional selection and runtime selection apply S2 authority without a specificity
preference. An authored marked CharSequence method can win over a String peer. Competing generated converter routes
remain excluded; an explicitly marked wrapper or converter exclusion supplies the authoring escape.

The existing invocation path picks and checks the exact public Builder method. Generated scalar CharSequence converters
directly link a protected runtime hook, preserving named-module access. That hook passes a Groovy typed argument wrapper
around the original value to ordinary converter dispatch, so a more-specific String overload cannot replace the recorded
CharSequence route. This is a dispatch type hint, not a new mutable character sequence, generic coercion or reflective
converter discovery. Existing String converter dispatch is unchanged.

CharSequence converter associations are structural-only: C1 selected-parameter authority remains exact String only.
The runtime validates structural source descriptor parameters against the selected public signature. Schema projection
emits `type: string`; domain field constraints never become raw-input assertions. No converter or Schema code executes
for inspection/generation. The generated hook requires matched compiler/runtime/plugin and recompiled Schema artifacts;
protocol marker 1 alone is not a mixed-artifact compatibility promise.

No container routes, String-to-DSL conversion, new discovery, scopes, Templates, body schemas, factories or S1 traversal
changes are included. One deliberate migration consequence is that a String closure plus an implicit CharSequence
factory now competes. The documented Duration closure explicitly excludes `parse`; the direct Duration example needs
no closure.

## Acceptance evidence

| Requirement | Evidence |
| --- | --- |
| Actual projected Duration route, direct equivalence and exact descriptor/source | `StructuredCharSequenceTest` documentary example linked to [Converters](../user/Converters.md#structured-string-input) |
| Original String identity and exact marked signature despite String peer | Same test: authored and converter route-distinguishing controls, constructor/factory controls |
| Strict input; no generic or container expansion | Same test: numeric, boolean, null, GString, arbitrary Object, StringBuilder/StringBuffer/custom mutable CharSequence, maps/lists, Serializable and container signature negatives |
| S2 authority | Same test: unique unmarked, marked CharSequence, competing generated routes, marked wrapper, multiple marks; retained `StructuredOperationSelectionTest` |
| Inheritance and source association | Same test: separately compiled parent/child/grandchild plus renamed/ignored overrides; retained C1/structural binary tests |
| Damaged metadata | Same test: removed projection/association/authority, malformed authority/descriptor; retained S2 and C1 corruption matrices |
| Failure attribution | Same test: source/path and original DateTimeParseException |
| Truthful output, C1 unchanged, no execution | `CompiledConverterJsonSchemaTest`: packaged G3/G4/G5 source-free producers, inherited Duration and marked CharSequence operation, traps and explicit initialization positive control |
| Named modules and direct generated linkage | `JpmsPackageBoundaryTest`: Java/static Groovy consumers and bytecode linkage controls |
| Actual emitted grammar | `validate_converter_schema.py`: Draft 2020-12 structural String corpus alongside retained JDK/ECMA C1 oracle |

## Local validation

Focused Groovy 3 changed-seam tests passed during implementation. Final aggregate validation and exactly two local
review axes are pending. This section will be replaced with completed evidence before the local pause.

The merged risk-tiered policy from PR #1006 (`52b9e0d97d181e8f2ad2222c50ae3a176ea4bfb4`) governs this stacked assignment.
A new generated-code hook spans compiler/runtime ABI, so the shared-contract tier requires root `check`; the run also
covers affected module suites, Groovy 4/5, binary/JPMS, plugin consumers, compilation, license and documentation checks.
No local SonarCloud upload. Hosted PR CI remains the authoritative complete-build qualification after eventual delivery.
