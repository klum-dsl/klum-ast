# S3a — exact String converter origin parity

Related: #952. Authority: [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[S3a acceptance](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice),
[S2 selection](input-round-s2-evidence.md) and retained [C1 authority](issue-981-c1-evidence.md).

## Local implementation boundary

Base: `4f68e1ec97b6a9e78e9e7eccb300dad7b55ce6d0`, the assigned S2 branch tip.
Branch: `codex/952-string-origin-parity`.
Worktree: `/Users/stephan/.codex/worktrees/2bc6/klum-ast`.
This is a speculative local stack; implementation does not establish upstream delivery or qualification of its base.

The compiler admits an existing exact String projection for field converter closures, same-compilation non-DSL values,
and separately compiled external adapters. It keeps the recorded converter source paired with the actual public Builder
method. Failed or colliding generation remains rejection; no additional discovery, reflection scan or converter execution
is introduced. An unrelated authored overload no longer suppresses the exact generated String projection; effective-name
competition still uses S2 selection. Existing public projections for interface/parameterized targets, subtype-returning
factories and generic/default/bridge/synthetic sources are structural-only when they lack C1 authority. No additional
discovery, expansion or inference creates such projections. A raw String-array varargs signature is not a String route.
An unrelated constructor/factory overload does not block an exact String projection, but keeps it outside C1 authority. An inherited factory that ordinary generation does not expose remains unavailable.

Every converter association explicitly records whether C1 selected-parameter facts apply. C1-qualified separately compiled
declared domain routes retain exact parameter policies, constraints and missing/corrupt-fact failures. Structural routes
retain source association but do not inspect converter implementations or infer parameter constraints. They populate a
constraint-free converter profile so destination-field constraints remain domain-value descriptions, never raw String
assertions. Matched compiler/runtime/plugin and Schema recompilation is required; protocol marker 1 alone does not promise
mixed-artifact compatibility. S1 traversal is unchanged. Exact scalar String converter methods directly link a protected
generated-code hook that delegates to the same runtime conversion/assignment path.

## Acceptance evidence

| Requirement | Evidence |
| --- | --- |
| Field closure with `Duration.parse`, direct/structured equivalence | `StructuredConverterOriginTest` documentary example, linked to [Converters](../user/Converters.md#structured-string-input) |
| Same-compilation domain and adapter use the same exact public String shape | `StructuredConverterOriginTest`; `CompiledConverterInputTest` same-compilation constructor control |
| Separately compiled external adapter | `CompiledConverterInputTest`: packaged source-free adapter, actual ordinary/structured dispatch and field source association |
| Typed payload and original converter cause | Duration test rejects null, numeric, boolean, GString, mutable CharSequence and containers; parse failure retains source/path and `DateTimeParseException` |
| Missing/colliding projection and required facts | New corruption controls remove public method/association/authority or corrupt authority/descriptor; marked colliding source rejects; retained C1 missing/corrupt fact matrix |
| Exact C1 output with no leakage | `CompiledConverterJsonSchemaTest`: packaged G3/G4/G5 producers, same emitted document contains C1 Pattern and structural-only closure/local/adapter fields; field Pattern stays descriptive |
| No execution/source fallback | Packaged fixtures delete source/class directories and retain Schema/domain/adapter/closure/default/lifecycle/validator traps; retained explicit initialization positive control |
| Binary and named-module access | `JpmsPackageBoundaryTest`: Java and static Groovy String-converter calls, G3 classpath and G4/G5 named modules; no new opens |
| Actual Draft 2020-12 output | `validate_converter_schema.py`: retained C1 JDK/ECMA/Draft oracle plus structural scalar acceptance/rejection for each new origin |

No CharSequence invocation, converter containers, String-to-DSL routes, commands, scopes, Templates, body schemas,
CollectionFactories, coercion, parameter validation, discovery widening or source-mirror authority is added.

## Validation and review

The final implementation head `b75482a5c060100b690f5074b993ed5cc9ff0304` passed the full repository
`./gradlew check --no-parallel --max-workers=2` with Java 17 in 24m 25s: 140 actionable tasks, 58 executed and 82 up to date.
The final JUnit reports, including test-support, contain:

| Lane | Tests | Failures / errors | Skipped |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline (`test`) | 3,630 | 0 / 0 | 17 |
| Groovy 4 compatibility | 2,955 | 0 / 0 | 16 |
| Groovy 5 compatibility | 2,955 | 0 / 0 | 16 |

The Gradle plugin contributes 653 passing baseline tests with no skips. The full check includes binary/JPMS,
source-free packaged producers, lane isolation, license, plugin validation and documentation-renderer checks.
Focused converter-origin, S2 selection, C1, converter regression and named-module checks also passed during development.
The final evidence update is documentation-only and does not change the tested implementation.

After the successful full check, the independent actual-output oracle passed **28 JDK, 270 ECMA/Draft C1 and 210 S3a
structural String checks** across G3/G4/G5. It used `jsonschema` 4.23.0, Node v26.12.0 and the Java 17 launcher.
All three emitted `converter.schema.json` documents have SHA-256
`b10e567dfc75e22b1997411782ea29325e66acb0f937d263d1a5a28a17657aa0`.
Markdown fences, relative file links, the linked documentary anchors and `git diff --check` passed.
This is local qualification only; no remote CI dispatch or SonarCloud upload occurred.

Exactly two review children performed the Standards and Specification reviews. Both reported zero remaining findings
at `b75482a5c`. Their corrections are retained as follow-up commits:

- Standards requested braces and a named corruption-annotation helper.
- Specification identified C1 source-family, interface and generic restrictions in structural admission. Those restrictions
  now apply only to C1 parameter authority. Structural admission uses the actual exact public projection and ordinary
  scalar exclusions. Added dispatch/schema controls cover overloaded, interface, subtype and generic routes without C1 leakage.
- Full validation exposed dynamic converter-hook lookup on a non-exported runtime class. Exact scalar String methods now
  directly link the protected `GeneratedKlumBuilder` hook, with no module opens or public Builder signature change.
  Both reviewers accepted the correction; bytecode and named-module controls pass.
- A later full run found a stale S2 hand-authored plugin fixture missing `operationCount=0`. Adding the required zero count
  restores the original model-identity rejection assertion without changing production code. Both reviewers accepted it.

Earlier interrupted or failed runs are diagnostic history, not qualification. The successful full run above includes
all corrections. The final compatibility totals include six test-support tests per lane beyond the earlier intermediate
2,949-test reports.

## Local commit sequence

| Commit | Purpose |
| --- | --- |
| `429e032e9` | Exact String converter origin parity and initial controls |
| `4fd30fc79` | User documentation and local stack boundary |
| `06490fdd5` | Separate C1 source-family authority from structural routes |
| `c7aeffc24` | Make the actual public projection authoritative for structural String input |
| `82432c6fb` | Direct scalar String converter linkage across named modules |
| `b75482a5c` | Complete the inherited model-identity fixture under S2 metadata |

This evidence record is finalized in a subsequent documentation-only commit. The reviewed implementation sequence is
preserved rather than rewritten.

## Delivery and resume condition

Local commits only: no push, PR, GitHub issue edits or merge. Tracker impact: **Related: #952**; no closure and no curation
mutation. The documentary test/user-documentation links above are ready for later Hive tracker reconciliation.

Retain a clean worktree with a `(paused)` title. Resume only after **S1 PR #1002 merges, S2 rebases onto merged S1 and
itself merges, then this S3a branch rebases onto master**. Replay only the S3a commits after the pinned S2 base above; do
not replay the old S1/S2 stack. Revalidate the rebased result before any subsequent delivery.
Hive owns resumption, publication and archive reconciliation. This worker does not self-archive.
