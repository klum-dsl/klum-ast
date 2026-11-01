# S3a — exact String converter origin parity

Related: #952. Authority: [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[S3a acceptance](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice),
[S2 selection](input-round-s2-evidence.md) and retained [C1 authority](issue-981-c1-evidence.md).

## Local implementation boundary

Integrated base: `325cfda9d373b235caaa7590475ecde01c33fdb7`, the verified S2 PR #1003 merge on master.
Original stack base: `4f68e1ec97b6a9e78e9e7eccb300dad7b55ce6d0`, the assigned S2 branch tip.
Branch: `codex/952-string-origin-parity`.
Worktree: `/Users/stephan/.codex/worktrees/2bc6/klum-ast`.
The S1/S2 merge prerequisite is satisfied. Only S3a commits were replayed onto the integrated base; the old S1/S2 stack
was not replayed. S3a delivery remains a draft pull request, without merge authorization.

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

## Dispatch-equivalence verification for PR #1004

The concern was that a syntactically valid structural converter descriptor does not independently prove which executable
the generated Builder invokes. The trace and route-distinguishing tests found **no production dispatch defect**.

The complete path is:

1. `ConverterBuilder` discovers closures, enabled factories/adapters and constructors. A field closure becomes a static
   generated converter method. `createConverterMethod` embeds the field name, converter class and converter method name
   (null for a constructor) in the ordinary Builder implementation, then records that source/implementation pair through
   `StructuredInputConverter.recordRoute`. Failed optional generation is recorded too.
2. `GeneratedDslSupport` links the implementation to its public projection. `StructuredInputConverter.selected` requires
   exactly one recorded raw String route and a real public instance projection with the exact String parameter and field
   return type. Competing or unsuccessful projections are rejected. `enrich` attaches `GeneratedInput.Converter` to that
   same projection, with source provenance and the separate C1 `parameterFacts` flag.
3. `BuilderContractReader` checks metadata completeness. `InputContractResolver` verifies the selected PROPERTY/public
   signature, preserves S2 effective-name selection, and rejects inherited converter fields shadowed by descendant methods
   under the #991 safeguards. These override checks apply regardless of `parameterFacts`.
4. `StructuredInputSupport` accepts a genuine String, selects the one recorded public method and checks Groovy's selected
   method against its name, native parameter types, public visibility and compatible return type. It invokes that Builder
   method. It never dispatches a converter using the descriptor.
5. The generated method directly calls `GeneratedKlumBuilder.$setSingleFieldViaStringConverter`, then the existing
   `InternalKlumBuilder.setSingleFieldViaConverter/createObjectViaConverter` path. Ordinary Builder calls enter the same
   generated body, with the same embedded converter owner/name and String argument. Both therefore share the existing
   Groovy constructor/static-method dispatch and field assignment.

`validStructuralConverterDescriptor()` remains sufficient for its limited sanity-check role: constructor descriptors
must be `(String)void`, while factory descriptors must take String and return a reference type. It is not an independent
bytecode-to-metadata attestation or a second executable selector. Adding descriptor-driven dispatch would duplicate the
ordinary Builder route. Matched compiler/runtime/plugin and Schema artifacts remain the compatibility boundary; this
verification does not claim protection against deliberate arbitrary bytecode or runtime-metaclass rewriting.

| Evidence | Existing coverage / focused addition |
| --- | --- |
| Closure route | Existing Duration equivalence and strict payload/cause/path checks; new control yields 37 seconds while the implicit `Duration.parse` peer yields 30 |
| Same-compilation value | Existing factory/adapter `converted:` results; constructor control now distinguishes `constructor:1234` from excluded factory `constructor:factory:1234` |
| Binary external adapter | Existing source-free runtime dispatch; strengthened with excluded `peer:` result and inherited adapter route producing `adapter:` |
| Inheritance/overrides | Existing C1 binary override matrix and S2 route-selection tests; new structural-only renamed/ignored override through a further descendant distinguishes `adapter:` from `override:` and rejects the old field name |
| Collisions/projection integrity | Existing competing-factory and closure collisions, missing projection/association/authority and malformed-descriptor rejection |
| C1 isolation | Existing PROJECT/TEXT_ONLY/OMIT and malformed-fact matrix; structural controls assert absent authority/assertions, including a constrained adapter parameter |
| No execution during inspection/schema generation | Existing deleted-source/class packaged producers, domain/Schema/adapter/closure/lifecycle/validator traps and explicit initialization positive control |
| Failure attribution | Existing converter-thrown `DateTimeParseException` cause-chain and input source/field-path assertions; runtime unwraps invocation wrappers while preserving the original cause |

Validation after the focused test-only additions:

- G3, G4 and G5 each passed `StructuredConverterOriginTest` (25), `StructuredOperationSelectionTest` (43), and
  `CompiledConverterInputTest` (103): **171 tests per lane**, no failures/errors/skips.
- `CompiledConverterJsonSchemaTest`: **8 passing cases**, including packaged G3/G4/G5 producers.
- Independent actual-output oracle: **28 JDK + 270 ECMA/Draft C1 + 210 structural checks**, all passed.
- Standards and Specification review of the test delta: zero findings on both axes.
- No production change, new metadata, dispatcher, reflective fallback, admission widening or broad-check rerun.
  The full implementation qualification below remains applicable; hosted checks must be read for the updated PR head.

## Master integration and current validation

On 2026-11-01, the S2 merge was verified at the exact requested master above. Rebase preserved the production changes
without conflicts. The plan's slice-status paragraph was reconciled with merged S1/S2, and the model-identity fixture
correction was already present upstream. Range-diff confirmed those were the only integration differences.

Rebased head `7d81b9e0784a2302d2f78648bcd464f6a31436e2` passed
`./gradlew check --no-parallel --max-workers=2` with Java 17 in **26m 29s**: 140 tasks, 73 executed and 67 up to date.

| Lane | Tests | Failures / errors | Skipped |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline (`test`) | 3,633 | 0 / 0 | 17 |
| Groovy 4 compatibility | 2,958 | 0 / 0 | 16 |
| Groovy 5 compatibility | 2,958 | 0 / 0 | 16 |

The full run covers the merged S2 inherited-candidate corrections, converter origin parity, C1, binary/JPMS,
source-free packaged producers, plugin integration, lane isolation, license and documentation-renderer checks.
The independent oracle then passed **28 JDK, 270 ECMA/Draft C1 and 210 structural String checks**. The three schema files
remain byte-identical with the SHA-256 recorded below. Both existing review children repeated their review against the
integrated base and reported zero findings at `7d81b9e07`; no new implementation correction was needed.
This final integration evidence update is documentation-only. Markdown/link/diff checks apply to it.

## Pre-integration validation and review (historical)

The pre-integration implementation head `b75482a5c060100b690f5074b993ed5cc9ff0304` passed the full repository
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

## Rebase audit

| Original commit | Rebased commit | Purpose |
| --- | --- | --- |
| `429e032e9` | `3b2871615` | Exact String converter origin parity and initial controls |
| `4fd30fc79` | `0c8dca000` | User documentation and local stack boundary; plan status reconciled |
| `06490fdd5` | `9b7a20813` | Separate C1 source-family authority from structural routes |
| `c7aeffc24` | `2f2f64956` | Make the actual public projection authoritative for structural String input |
| `82432c6fb` | `125a28a8a` | Direct scalar String converter linkage across named modules |
| `b75482a5c` | `22f32a31a` | Retained diagnostic evidence; fixture correction is already in master |
| `9ad811e46` | `7d81b9e07` | Prior local qualification and paused-stack evidence |

The reviewed sequence is preserved through the authorized rebase. The current integration record is a subsequent
documentation-only commit.

## Delivery boundary

The resumed assignment authorizes pushing this dedicated branch and opening a draft pull request against master.
Tracker impact: **Related: #952**; no issue closure or curation mutation. The documentary test/user-documentation links
above remain available for Hive tracker reconciliation. No merge or self-archive is authorized.

Delivery-channel audit: Git transport push **authorized** (dry run); GitHub CLI pull-request channel **authorized**.
No alternative connector authority is inferred. CI and coverage-bearing SonarCloud results belong to the published
revision and are reported in the PR handoff; local checks do not establish those remote results.

The previous pause condition is satisfied by the verified S1/S2 merge sequence and this rebase. Hive retains merge and
archive reconciliation; this worker stops with the draft PR open.
