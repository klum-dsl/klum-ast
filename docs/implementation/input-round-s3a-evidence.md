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
competition still uses S2 selection. Existing generic, bridge/synthetic and varargs exclusions remain. An unrelated constructor/factory overload does not
block an existing exact String projection, but keeps that route outside C1 parameter authority. An already-generated
binary default overload can be called as-is; this slice does not generate any additional expansion. An inherited factory that ordinary generation does not expose remains unavailable.

Every converter association explicitly records whether C1 selected-parameter facts apply. C1-qualified separately compiled
declared domain routes retain exact parameter policies, constraints and missing/corrupt-fact failures. Structural routes
retain source association but do not inspect converter implementations or infer parameter constraints. They populate a
constraint-free converter profile so destination-field constraints remain domain-value descriptions, never raw String
assertions. Matched compiler/runtime/plugin and Schema recompilation is required; protocol marker 1 alone does not promise
mixed-artifact compatibility. S1 traversal and runtime invocation mechanics are unchanged.

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

Final focused Groovy 3 converter-origin, S2 selection, C1 and binary/module suites passed, together with the packaged
G3/G4/G5 schema producer matrix. The independent oracle passed 28 JDK, 270 ECMA/Draft and 90 initial S3a structural checks.
Markdown fences, 183 relative file links and `git diff --check` passed. Affected suites, full repository `check`, and the
two local review axes are in progress. This section will record the final results before handoff; it makes no full-suite or
remote CI claim yet. No local SonarCloud upload is used. The first full-check attempt was stopped for review corrections; it is not qualification evidence.

Standards review requested braces and a named corruption-annotation helper. Specification review identified retained C1
source-family cardinality restrictions in structural admission. Corrections retain those restrictions only for C1
parameter authority and add overloaded constructor/factory/adapter dispatch and schema controls, plus a native-scalar
regression. Existing native scalar input remains unchanged. The same two children will review the corrected head.

## Delivery and resume condition

Local commits only: no push, PR, GitHub issue edits or merge. Tracker impact: **Related: #952**; no closure and no curation
mutation. The documentary test/user-documentation links above are ready for later Hive tracker reconciliation.

Retain a clean worktree with a `(paused)` title. Resume only after **S1 PR #1002 merges, S2 rebases onto merged S1 and
itself merges, then this S3a branch rebases onto master**. Revalidate the rebased result before any subsequent delivery.
Hive owns resumption, publication and archive reconciliation. This worker does not self-archive.
