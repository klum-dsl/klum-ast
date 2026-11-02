# S5 — concrete virtual Builder scopes

Related: #952. Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#scalar-converters-scopes-and-commands),
the [S5/F2 plan](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice), and retained
[S1](input-round-s1-evidence.md), [S2](input-round-s2-evidence.md), [S3a](input-round-s3a-evidence.md) and
[S3b](input-round-s3b-evidence.md) evidence.

## Base, outcome and integration boundary

Intentional speculative base: `13fd709980fe4e4f7c0031732d83f380bc5e6e85`,
`codex/953-structured-factory-builder`, draft [PR #1012](https://github.com/klum-dsl/klum-ast/pull/1012).
S5 branch: `codex/952-nested-builder-scope-proof`.
Worktree: `/Users/stephan/.codex/worktrees/8182/klum-ast`.
On 2026-11-02, #1012 was verified open at that exact head. Its delivery/qualification is independent of this slice.
A rebase after its merge is required before final PR creation/qualification.

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
| Damaged contracts | Missing scope/operation/both/sink/creator, inconsistent creator descriptor and role; omitted scope payload cannot hide corruption |
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

The implementation's focused matrix passes its scope/documentary tests on G3/G4/G5, including the expanded G4/G5 named
module cases; packaged binary/schema projection and interpretation also pass. Final root qualification, final review
findings and exact implementation head are recorded below when complete. Because this implementation changes shared
compiler/runtime/generated metadata and plugin interpretation, root `check` is required under the risk-tiered policy.
No local SonarCloud is run. Hosted CI/Sonar on a future rebased PR head remains authoritative external qualification.

## Delivery and reconciliation

Tracker impact: **Related: #952**, never closing syntax. Curation impact: none. The new documentary/user/schema/migration
references are synchronized locally; Hive owns issue traceability and delivery reconciliation. No remote issue update,
merge or archive action is part of this work. No Schelm qualification is claimed.

#1012 rebase is pending while that PR remains open. Do not publish this speculative branch as finally qualified before
integration. After local work is complete, Hive must reconcile the assigned outcome and determine its next delivery step;
this worker does not self-archive or convert execution completion into archive safety.
