# WIRE-1 direct fixed-key resolution gate

Date: 2026-10-22. Status: **maintainer approved a precise private fact; refinement validated**.
Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932),
[approved runtime plan](../adr-0031-wire-runtime-v1.md).

The reproduction and alternatives below describe the historical stopped revision; the accepted resolution at the end
records the implemented compiler fact and supersedes that stop.

## Concrete mismatch

`StructuredInputDirectKeyBoundaryTest` uses the existing `ClusterFixedKeysTest` contract:

```groovy
@DSL abstract class Home {
    @Cluster(value = ManagedZone, fixedKeys = true)
    abstract Map<String, Kitchen> getZones()
}
@Retention(RetentionPolicy.RUNTIME) @interface ManagedZone {}
@DSL class Kitchen { @Key String name; String purpose }
@DSL class FloorPlan extends Home { @ManagedZone Kitchen kitchen }
```

The carrier admits `FloorPlan#kitchen` as `OWNED_OBJECT`, with exact Kitchen Model/Builder identity and a public creator
group including the no-key overload. Both direct-entry key slots are null. The physical source field has no `@Field`
annotation; the existing `ClusterFixedKeysTest` explicitly verifies that absence.

Ordinary public creation `kitchen(purpose: 'ordinary')` yields child key **kitchen**. Calling the committed fixed-target
Consumer creator with the admitted field/type and no external child key configures the child correctly but yields key
**null**. The passing characterization compares both completed Models. This is not an ordinary DSL regression: the
compiler embeds the fixed key into the generated public creator call, while the runtime creator resolves only explicit
`@Field.key` metadata or its supplied key argument.

The current unpublished structured-input candidate uses this fixed-target Consumer path, so it cannot preserve that
existing declared key derivation. Explicit `@Field(key = Field.FieldName)` is separately covered and works; fixed Cluster
membership is not equivalent metadata. `ClusterFactoryBuilder.isFixedKeysClusterMember` determines membership in the
compiler, including inherited Cluster methods and annotation/type filters. There is no runtime fixed-key resolver to reuse.

## Why execution must stop here

The approved plan requires direct keyed children to use existing declared key derivation and ordinary preparation, with
configuration before PostApply. It also forbids reflection-based discovery, guessing from public overload patterns,
synthetic Closures, child-root creation and a second attachment engine. Invoking an empty generated creator first and
applying the prepared input afterward would replay PostApply and violate the required timing. Treating the no-key
signature as permission to invent a field-name key would infer a semantic fact not recorded by the carrier.

The fixed key exists in compiler knowledge, but is not available to the approved fixed-target runtime creator through its
current explicit facts. This is the runtime plan's contract/role-resolution stop condition, not permission to guess or
silently diverge from normal Builder behavior.

## Bounded alternatives for the maintainer

1. Exclude direct owned fields whose keys come from fixed Cluster membership during shared compiler admission, with a
   source-specific unsupported-key-derivation reason. Keep ordinary DSL contracts/behavior and explicit `@Field.key`
   support unchanged. This narrows shared wire admission and needs an explicit decision, like the prior keyMapping gate.
2. Authorize a precise compiler-known key-derivation fact and its private ABI/creator consumption. This requires deciding
   how to preserve the current carrier obligation. Runtime reflection scanning or public-signature heuristics are not a
   substitute for that decision. Synthesizing an observable `@Field` annotation is also not a silent correction: the
   existing fixed-Cluster contract explicitly keeps that annotation absent.

No alternative has been implemented. There is no new key encoding, registry, public descriptor/API, generated linkage,
reflection resolver, legacy handling or callback-order workaround.

## Verification and branch boundary

The characterization passes **1 case on each Groovy 3/4/5 lane**, proving the mismatch rather than claiming the
interpreter is green for this contract. The extended documentary context fixture passes 2 per lane; actual Java/static
Groovy named-module checks pass 1 each on Groovy 4/5. Every selected case has zero failures/errors/skips.

```shell
./gradlew :klum-ast:test --tests '*StructuredInputDirectKeyBoundaryTest' --tests '*StructuredInputDocumentaryTest' :klum-ast:groovy4Tests --tests '*StructuredInputDirectKeyBoundaryTest' --tests '*StructuredInputDocumentaryTest' --tests '*JpmsPackageBoundaryTest.a real schema*' :klum-ast:groovy5Tests --tests '*StructuredInputDirectKeyBoundaryTest' --tests '*StructuredInputDocumentaryTest' --tests '*JpmsPackageBoundaryTest.a real schema*' --console=plain --no-daemon
```

Manual review of the characterization/evidence, their Markdown links/fences and `git diff --check` pass. No policy or
production dispatch change is included in this gate's evidence commit.

Completed commits before this gate include the separate keyMapping compiler correction (`c3644815`) and shared Consumer
construction (`31f4ba8f`). The latter passed 10 construction/boundary cases per Groovy lane, the full runtime suites
(73 per lane, one existing skip) and Jackson (121/119/119).

The unpublished runtime candidate passed 121 structured-input cases per Groovy 3/4/5 lane, plus actual named-module
checks on Groovy 4/5, before this additional direct-key characterization. That run includes numeric/dispatch/preflight,
reader, admission and documentary coverage; it is not full issue acceptance. Final dual review, affected compiler-wide
suites, root check, final history review and delivery remain pending. The subsequent extended context fixture and precise active-Builder static consumer now pass all selected lanes above.

Runtime facade/preflight/dispatch, associated user documentation and latest strict-payload reader hardening remain local
candidate edits. This gate's test and evidence can stand on the committed private reader/Consumer seams without shipping
the candidate facade. No push, PR, issue closure or merge. #932/#529 remain open; Hive/maintainer resolution is required.

## Accepted resolution

The maintainer approved the precise compiler-owned fact rather than excluding a normal DSL capability. The compiler
records `fixedChildKey` as the twelfth entry token only for affected fixed-key Cluster owned objects, preserving null
for absence. Runtime consumes the literal directly via the committed Consumer seam. No Cluster scan, synthetic Field
annotation, overload inference, callback replay or generated public contract was introduced.

The original characterization now requires equal ordinary/Consumer child keys. It has direct-fact and separately
compiled inherited-fact rows, and retains the no-Field-annotation assertion. The reader rejects a fixed-key token on a
scalar property with its source declaration in the cause. The historical #938 JAR is unchanged and now proves strict
recompile guidance for pre-refinement entries; no compatibility branch was introduced. The candidate public route also
covers a renamed external field whose native fixed child key remains `kitchen`, plus Java/static Groovy named-module use.

Refinement validation: the expected missing-fact and old-artifact cases went red before implementation, then the full
structured-input matrix passed **125 per Groovy 3/4/5 lane**, plus the real named-module feature on Groovy 4/5; zero
failures/errors/skips. A subsequent reader run with a fixed-key child initialization trap passed **17 per lane**.
Direct/inherited Consumer parity is 2 cases per lane. License, lane isolation, Markdown/diff checks pass.
The refinement is committed separately from the facade/interpreter delivery, retaining the source no-Field assertion
and keeping the original #938 fixture bytes unchanged.
