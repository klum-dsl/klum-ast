# S5 — concrete virtual Builder scopes

Related: #952. Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#scalar-converters-scopes-and-commands),
the [S5/F2 plan](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice), and retained
[S1](input-round-s1-evidence.md), [S2](input-round-s2-evidence.md), [S3a](input-round-s3a-evidence.md) and
[S3b](input-round-s3b-evidence.md) evidence.

## Base, outcome and integration boundary

Intentional speculative base: `13fd709980fe4e4f7c0031732d83f380bc5e6e85`,
`codex/953-structured-factory-builder`, [PR #1012](https://github.com/klum-dsl/klum-ast/pull/1012).
S5 branch: `codex/952-nested-builder-scope-proof`.
Worktree: `/Users/stephan/.codex/worktrees/8182/klum-ast`.
On 2026-11-02, #1012 was verified merged at `a162c7d3b2c864f2952b7125583fd7d2b32913ef`.
S5 was rebased onto that verified `origin/master` after its full local check. The original base and merge have identical
trees; `range-diff` reports all three S5 commits equivalent, and the rebased tree
`7ccafc2ec955a256e7f56c9126834717eb8e599f` is identical to tested head
`1a62d7000834f0753f5878c78c05d7142b62b0b2`. Integration did not change the tested inputs.

S5 implements exactly one additional family: a public bare `@Field void method(ConcreteType value)` virtual sink whose
DSL target is concrete, unkeyed and nongeneric, with one compiler-proven generated creator. The interpreter enters the
existing Consumer configuration interval in the current Construction session. It does not synthesize a Closure, infer
an arbitrary delegate, create an independent completed child, or add a public callable API.

Ordinary direct children and keyed Maps remain controls. Keyed/generic/abstract/customized/overloaded virtual families,
redeclared sinks with ambiguous ordinary selection, authored closure scopes, CollectionFactory/F2, polymorphic and
Class/Factory selectors, container grammar, composition, Template implementation, #953 APIs and Jackson are unchanged.
The bare-annotation restriction deliberately includes rejecting explicit `@Field` options; this is one bounded family,
not general virtual-field completion.

## Contract proof and implementation

`DSLASTTransformation.createDSLMethodsForVirtualFields` already sends a concrete virtual target and name through
`createSingleDSLObjectFieldCreationMethods`; generated relationship-creator provenance identifies the real projection.
Ordinary creation reaches `InternalKlumBuilder.createSingleChildWithConfiguration`. The initial probe demonstrates that
its Consumer callback preserves the complete ordinary virtual trace, owner attachment and materialization.

The public returned-Builder route is not equivalent:

```groovy
// Ordinary: child:create, child:apply:configured, virtual:configured
Deployment.Create.With { virtual { value 'configured' } }

// Too late: child:create, child:apply:null, virtual:null, child:apply:configured
Deployment.Create.With {
    def nested = virtual()
    nested.applyStruct([value: 'configured'])
}
```

The implementation therefore uses the existing pre-PostApply interval. It shares the existing child preparation and
attachment path; only the virtual sink invocation is supplied through the interpreter's exact public-method dispatcher.
That keeps source/path/original-cause diagnostics on sink failures while leaving child-body and lifecycle attribution
intact. A successful sink is followed by the ordinary virtual-child retention and model-path assignment.

The compiler binds the exact public sink to its generated `(Map, Closure)` creator using the internal
`GeneratedInput.VirtualScope` association and `Operation` role `VIRTUAL_OBJECT`. Existing operation completeness counts
cover the new association. These are generated facts, not Schema-author annotations or a new projection SPI. The reader
checks association presence, exact creator shape/return, bare retained virtual sink, concrete unkeyed/nongeneric target,
role and public visibility before execution/projection. Stripped operation/association pairs still fail their count.
Names and exclusions use the existing shared resolver. No user code executes during contract inspection or generation.

Unchanged inheritance preserves the association. Redeclaring the sink is rejected because the existing ordinary virtual
setter resolver sees both declarations; this is proven by an ordinary-call control, not repaired through a new ownership
or dispatch engine. External-name collisions do not select a virtual winner. The matched compiler/runtime/plugin and
recompiled Schema requirement remains; protocol marker 1 alone is not a mixed-development-artifact promise.

## Acceptance map

`StructuredNestedScopeProbeTest` carries class-level `@Issue('952')` and exercises emitted artifacts. The separate
`StructuredVirtualScopeDocumentaryTest` carries the documentary tag and a user-page `@See` link.

| Requirement | Evidence |
| --- | --- |
| Existing concrete child and keyed Map ordinary/mapping traces | Same callbacks, final owner identity, outer key at creation; reentry retains existing Builder identities |
| New virtual ordinary/Consumer/mapping parity | Correct pre-sink configuration, one child creation/apply per invocation, owner attachment, fresh virtual children on repeated calls |
| Earlier sibling and nested failure | Earlier direct child remains; failed fresh Map or virtual child does not attach; later sibling/sink is not invoked; escaped input paths retained |
| Positional key authority | Outer Map key creates identity; forbidden inner key rejects when reached, after allocation; no rollback/preflight claim |
| Session/sealing | Same-thread active nested apply, no premature materialization, wrong-thread and stale/sealed rejection, aborted-root cleanup and next-root success |
| Sink failure versus lifecycle failure | Sink error wraps its original cause with source/scope path; ordinary child lifecycle exception stays ordinarily attributed and prevents the sink |
| Static family and naming | Concrete association, inherited declaration, redeclared-sink rejection, literal alias/escaped nested diagnostics, external collision |
| Negative grammar | Non-Map bodies, keyed/generic/abstract/customized/overloaded targets, raw Object, ignored scope, authored creator and closure-only scope |
| Damaged contracts | Missing scope/operation/both/sink/creator, inconsistent creator descriptor and role, conflicting query marker; omitted scope payload cannot hide corruption |
| Existing Template mode | Ordinary and structured virtual definition both run the sink without lifecycle; documentary recipe produces the same recipient values |
| Binary and JSON Schema | `BinaryOwnedRelationshipsTest`: source-free G3/G4/G5 producer artifacts, same ordinary/interpreter trace, virtual `$ref`, closure scope absent, initialization trap silent during projection |
| Actual emitted grammar | `validate_virtual_scope_schema.py`: Draft 2020-12 validation of all three retained emitted schemas, 12 accepted and 33 rejected instances |
| Java/static Groovy named modules | Expanded `JpmsPackageBoundaryTest` real producer/consumer case on G4/G5, using the public Factory and ordinary virtual comparison |
| User example | `StructuredVirtualScopeDocumentaryTest#'a repository mapping configures its Builder before the virtual field consumes it'`; [Structured Input](../user/Structured-Input.md#concrete-virtual-builder-scopes) |

The generic negative intentionally makes no generic-signature support promise. Its generated generic virtual contract
can fail complete inspection before member traversal; it has no admitted scope association. The probe reads artifacts
directly to avoid unrelated dynamic class-pool/JavaBeans introspection of that unsupported generic fixture.

## Local review and validation

Exactly two independent read-only review children were started against the exact intentional base: **Standards** and
**Specification**. Initial reviewed commit `951f1ce4675b7d4fb6b825d7a6ca3bd928df11c8` retained an evidence-only stop.
Standards found no violations. Specification correctly rejected that stop: absence of a public Consumer overload or
preexisting admission metadata does not invalidate the already proven framework callback. This successor withdraws the
stop, adds the static proof/negative boundaries and implements the admitted route. Reviewed history is preserved.
The returned-Builder counterexample remains valid; it rules out that adapter, not the Consumer route.

Initial controls at `951f1ce46` passed 30 new G3/G4/G5 probes, six packaged cases, the 2,734-case compiler baseline
(16 existing skips), 113 retained focused cases per compatibility lane, license and documentation checks. These are
historical evidence for the unchanged base, not qualification of the subsequent implementation.

The implementation's focused matrix passed its scope/documentary tests on G3/G4/G5, including the expanded G4/G5 named
module cases; packaged binary/schema projection and interpretation also passed. Because this implementation changes
shared compiler/runtime/generated metadata and plugin interpretation, root `check` was required under the risk-tiered
policy. On Java 17, `./gradlew check --continue --no-parallel --max-workers=2 --console=plain` passed at
`1a62d7000834f0753f5878c78c05d7142b62b0b2` in 27m 3s (140 tasks). Retained JUnit counts follow; all failures/errors are zero.

| Module | Groovy 3 tests / skips | Groovy 4 tests / skips | Groovy 5 tests / skips |
| --- | --- | --- | --- |
| Compiler | 2,764 / 16 | 2,766 / 15 | 2,766 / 15 |
| Runtime | 73 / 1 | 73 / 1 | 73 / 1 |
| Bean validation | 154 / 0 | 154 / 0 | 154 / 0 |
| Jackson | 121 / 0 | 119 / 0 | 119 / 0 |
| Test support | 8 / 0 | 6 / 0 | 6 / 0 |
| Annotations | 20 / 0 | — | — |
| Gradle plugin / TestKit | 664 / 0 | Included producer/consumer lanes | Included producer/consumer lanes |

The remaining review corrections are additive: Standards requested braces at two multi-branch conditionals;
Specification found that a conflicting query marker on a generated virtual sink was not rejected. An emitted-bytecode
corruption regression reproduced that finding (one failing query case among eight); the reader now rejects it before
any construction, including when the input omits that scope. No accepted source-language query family is added.

Post-rebase code head `1df34943eadebdc1469f2d79ae58ffbabf1ddf96` passed the following combined Gradle run
in 2m 43s (75 tasks), using Java 17 and `--no-parallel --max-workers=2 --console=plain`:

```text
:klum-ast:test --tests '*Structured*'
:klum-ast:groovy4Tests --tests '*Structured*' --tests '*JpmsPackageBoundaryTest.a real schema*'
:klum-ast:groovy5Tests --tests '*Structured*' --tests '*JpmsPackageBoundaryTest.a real schema*'
:klum-ast-runtime:check
:klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest'
:klum-ast:licenseTest :klum-ast-gradle-plugin:licenseTest
```

Results: 400/401/401 structured-input and named-module cases across G3/G4/G5, 73 runtime cases per lane
(one existing skip each), and six source-free binary cases; no failures/errors. The emitted-schema oracle with pinned
`jsonschema==4.23.0` again accepted 12 and rejected 33 instances across three generated schemas. Balanced Markdown fences,
227 local file links and `git diff --check` passed. No new skipped tests were introduced.

The same two children re-reviewed the exact-base tree diff including all corrections: **Standards: zero remaining
violations/material smells; Specification: zero remaining findings**. Commit-history review retained the preparatory
proof, implementation, corrected documentation and additive review fix, with only the explicitly requested integration
rebase rewriting their IDs. The initial documentation-only evidence commit did not change tested code.

The full-check result remains evidence for the byte-identical integrated implementation; the localized invalid-metadata
guard has the post-rebase regression evidence above. No local SonarCloud is run. Hosted CI/Sonar on the final PR head
remains authoritative external qualification; local completion does not imply a hosted green result.

## Hosted findings and bounded consumer qualification

At published head `fe87f064f93bbfc2cef2d37a78d13b216943a81b`, CI build, JUnit and both SonarCloud checks
were verified successful on 2026-11-02. A green quality gate still reported 11 open maintainability findings.
The follow-up addresses both Java cognitive-complexity findings (role validation and virtual-scope execution), the nested
role-selection ternary, the `Objects::nonNull` suggestion, the probe's nested thread check and six missing-brace findings.
The maintainer requested retaining the S135 loop shape and recording its false-positive rationale in source.
A method-local `@SuppressWarnings("java:S135")` on `enrichVirtualScopes` documents that each independent proof failure
skips the candidate; flat guards keep this fail-closed admission readable. Sonar already records the finding as
resolved/false-positive. No Sonar status mutation, protocol change or feature expansion was introduced.
The same two review children found no remaining Standards or Specification issues in the follow-up diff.

Schelm's handoff reported **PASS_LOCAL_PREMERGE_CONSUMER_QUALIFICATION** for that same exact published head:
the existing harness passed nine builds / thirteen invocations, 110 tests and all four renders; seven explicit
virtual-scope cases also passed, with no candidate defect reported. This is external, bounded pre-merge consumer evidence
reported through the Hive handoff, not a release result or qualification of the later Sonar follow-up head.

The Sonar follow-up passed on Java 17 with `--no-parallel --max-workers=2 --console=plain`:

```text
:klum-ast:test --tests '*Structured*'
:klum-ast:groovy4Tests --tests '*Structured*'
:klum-ast:groovy5Tests --tests '*Structured*'
:klum-ast-runtime:check
:klum-ast-gradle-plugin:test --tests '*BinaryOwnedRelationshipsTest'
:klum-ast:license :klum-ast-runtime:license
```

All 400 structured cases per compiler lane, 73 runtime cases per lane (one existing skip each) and six binary cases
passed; the schema oracle again accepted 12 and rejected 33 instances. After adding the requested source suppression,
a fresh compiler run passed `StructuredNestedScopeProbeTest`, `StructuredVirtualScopeDocumentaryTest` and
`StructuredInputAdmissionPlanTest`, plus `:klum-ast:licenseMain`. Markdown fences, 227 relative file links and diff checks
passed. The extracted thread assertion initially needed an explicit test-class receiver to avoid DSL delegation;
that correction passed both its isolated regression and the full selected matrix.

This localized maintainability follow-up does not require another full local root run under the risk policy. No local
Sonar upload was performed. Hosted results for the prior head do not qualify this later revision; the next PR CI/Sonar
run remains authoritative for the follow-up.

## Delivery and reconciliation

Tracker impact: **Related: #952**, never closing syntax. Curation impact: none. The new documentary/user/schema/migration
references are synchronized locally; Hive owns issue traceability and delivery reconciliation. No remote issue update,
merge or archive action is part of this work. The bounded external Schelm result above applies only to its exact head.

#1012 integration is complete. The GitHub CLI delivery channel was verified authorized for this repository before
publication. After delivery, Hive must reconcile the assigned outcome and issue traceability; this worker does not merge,
self-archive, or convert an open pull request into archive safety.
