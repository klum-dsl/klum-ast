# S5 — nested Builder scope entry characterization

Related: #952. Authority: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md#scalar-converters-scopes-and-commands),
the [S5/F2 plan](input-round-plan-4-2.md#acceptance-details-that-must-not-disappear-inside-a-slice), and the
assigned public/generated-entry admission gate. This is an **evidence-only stop**, not a new supported input feature.

## Base and outcome

Intentional speculative base: `13fd709980fe4e4f7c0031732d83f380bc5e6e85`,
`codex/953-structured-factory-builder`, draft [PR #1012](https://github.com/klum-dsl/klum-ast/pull/1012).
S5 branch: `codex/952-nested-builder-scope-proof`.
Worktree: `/Users/stephan/.codex/worktrees/8182/klum-ast`.
On 2026-11-02, #1012 was verified open at that exact head. Its delivery/qualification is independent of this probe.

No additional scope family is admitted. Existing concrete owned children and keyed Maps retain their mapping grammar.
An authored closure scope has no direct public/generated callback entry. A concrete virtual field is a stronger
candidate: the **existing package-private Consumer helper does preserve ordinary semantics** in the tested example.
However, the generated public surface offers a completed configuration call returning a Builder, a named-parameter
Map call, a closure call, or a Builder sink; it does not expose the Consumer configuration interval. Its structured
contract also lacks the compiler-owned scope/dispatch association that backs the existing relationship families.

This bounded result does **not** establish that virtual mapping is impossible or that the internal helper is defective.
It establishes an internal candidate and the public/generated contract gap under this assignment's admission rule.
Promoting that internal candidate requires an accepted generated scope association/direct-entry contract; no such
contract or public API is invented here. CollectionFactory and Cluster-facade exploration remain with F2.

## Executable cases

`StructuredNestedScopeProbeTest` carries `@Issue('952')` and compiles the following small Schema into artifacts before
loading its generated public contracts. The complete fixture adds trace callbacks and ownership fields.

```groovy
@DSL class Deployment {
    Child child
    Map<String, Entry> entries

    @Builder.Method
    void scoped(@DelegatesToBuilder(Child) Closure body) {
        events << 'scope:before'
        child(body)
        events << 'scope:after'
    }

    @Field
    void virtual(Child value) {
        events << "virtual:$value.value".toString()
    }
}
```

The existing supported control is ordinary `child { value 'direct' }` plus
`entry('a/b~c') { value 'mapped' }`, compared with the caller-supplied mapping
`[child: [value: 'direct'], entries: ['a/b~c': [value: 'mapped']]]`.
It preserves callback traces, final owner identity and the outer Map construction key.

| Case | Observed contract |
| --- | --- |
| Concrete child and keyed Map ordinary/mapping parity | Same trace; one creation/apply callback per new child; owners attached by normal lifecycle; key visible at entry creation |
| Reentry of existing children | Same Builder identities, one creation, one apply per configuration, unchanged key, same session and ordinary sealing |
| Failure in a fresh Map child after a prior direct sibling | Prior sibling remains; reached child creates but does not finish apply or attach; later Map sibling never runs; escaped `/entries/a~1b~0c/unknown` diagnostic retained |
| Inner key in a keyed-Map body | Outer key is established at creation; forbidden inner key rejects when reached; no rollback or pre-allocation rejection is claimed |
| Nested active Builder application | Same-thread application succeeds without materialization; wrong-thread and sealed use reject; explicit application adds its own ordinary PostApply |
| Aborted root/session | No Model returned; stale Builder rejects later application; next root succeeds |
| Authored `scoped(Closure)` | Generated parameter has exact `Child_DSL.Builder` delegate; only closure signature exists; mapping rejects without invoking before/after wrapper effects |
| Virtual generated public return route | `virtual()` returns the exact generated Builder, but its apply callback and sink have already run; later `applyStruct` adds a second apply and cannot reproduce ordinary sink input |
| Virtual internal Consumer route | Package-private test call to `createSingleChildWithConfiguration` matches the ordinary virtual trace, owner and materialization; this is an internal feasibility control, not a public mapping operation |
| Static structured admission | Only `child` and `entries` are admitted in this fixture; no virtual Selection/Operation association exists; inspection runs no configuration callback |

The timing counterexample is executable, not inferred from return types:

```groovy
// Ordinary: child:create, child:apply:configured, virtual:configured
Deployment.Create.With { virtual { value 'configured' } }

// Returned-Builder adapter: child:create, child:apply:null, virtual:null,
//                          child:apply:configured
Deployment.Create.With {
    def nested = virtual()
    nested.applyStruct([value: 'configured'])
}
```

Both eventually run normal root/PostTree callbacks. The second route changes observable behavior before those phases.
The named-parameter Map overload is also an ordinary completed configuration call, not a callback accepting recursive
structured traversal; replacing structured grammar with named-map semantics is outside the slice.

## Owning code and missing contract

- `StructuredInputAdmissionPlan.classifyOwnedObject` / `classifyOwnedMap`, `enrichField` and `enrichSelection` emit
  the existing fixed relationship associations on public Builder declarations.
- `StructuredInputSupport.Traversal.execute` uses those admitted associations to call
  `InternalKlumBuilder.createSingleChildWithConfiguration` / `addNewDslElementToMapWithConfiguration`.
- `DSLASTTransformation.createDSLMethodsForVirtualFields` creates ordinary virtual overloads from a temporary field
  description. The retained `@Field` sink and generated return/delegate types prove a concrete candidate, but no
  structured scope/dispatch association or public Consumer entry is emitted for that family.
- `InternalKlumBuilder.resolveSingleChildTarget` also supports a virtual setter. `callSetterOrMethod` invokes the sink
  and retains its virtual child for lifecycle traversal. The test calls this package-private path explicitly, with
  static compilation so Groovy-version differences in dynamic package access cannot disguise the seam used.
- `ClusterFactoryBuilder.createClosureForOuterClass` builds a Collection/Cluster factory closure delegate. It is not
  an additional ordinary Builder scope admitted by this probe; F2 and generic composition are excluded.

For an authored closure method, the missing contract must preserve the method's own before/after effects while entering
its exact target at the proper configuration interval. An annotation declaring a delegate type cannot provide that.
For the virtual candidate, the internal interval exists; what is missing at the assigned boundary is an accepted
compiler-owned association of the exact virtual sink/family with that entry interval, including key, overload and
inheritance authority. A public return type alone is insufficient, as the timing counterexample shows. This report
does not choose a new annotation, generated API, or general scope framework to fill either gap.

## JSON Schema and validation

No new nested mapping is published. The packaged `BinaryOwnedRelationshipsTest` now includes both candidate declarations
and verifies their absence from generated properties. Its G3/G4/G5 source-free artifacts retain direct-child references,
keyless keyed-Map value definitions, `additionalProperties: false`, construction-key exclusion, no-initialization traps
and normal interpreter controls. A closed schema for either new candidate would be premature while runtime rejects it.

Focused validation on 2026-11-02, with Java 17:

- `StructuredNestedScopeProbeTest`: 10 tests on each of Groovy 3, 4 and 5, zero failures/errors/skips.
- `BinaryOwnedRelationshipsTest`: all six G3/G4/G5 projection/interpretation cases passed, zero failures/errors/skips.
- Broader affected-module, compatibility and documentation checks are recorded in the final handoff below.

The first expanded probe corrected two test expectations: Groovy Map emptiness needs `isEmpty()`, and an externally
keyed child's forbidden inner key rejects after allocation when reached. No production correction was required.
No production/generated ABI, parser, Template, Factory, Jackson, container grammar or ownership implementation changes.
Consequently this evidence change does not need a new public-feature documentary test, user-documentation claim or
CHANGES entry. Existing binary/schema tests supply bounded controls, not release qualification or new ABI qualification.
No local SonarCloud run is performed; hosted CI/Sonar remain authoritative for any eventual published revision.

## Delivery and reconciliation

Tracker impact: **Related: #952**; no issue state or curation change. No push or draft PR is warranted under the assignment's
contract-proven publication condition. The probe is committed locally for maintainer/Hive review. A #1012 merge rebase
remains pending before any future PR creation/qualification; this record stays pinned to its intentional speculative base.
Hive must reconcile the evidence and decide whether to accept a bounded virtual-entry contract or retain the stop.
No implementation re-entry, merge, self-archive or CollectionFactory follow-on is authorized by this result.
