# WIRE-1 container admission stop

Date: 2026-10-22. Status: **maintainer clarification accepted; generated field population characterized before correction**.

Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932),
[#529](https://github.com/klum-dsl/klum-ast/issues/529).
Authority: [WIRE-RUNTIME v1 payload and container gate](../adr-0031-wire-runtime-v1.md#payload-preparation-admission-and-order).
Baseline: `5446379693117fd2f1790a8f974cfdc40f4c7170`, followed by the separately committed
[approved numeric correction](issue-932-wire-1-numeric-stop.md).

## Concrete mismatch

The runtime plan requires:

> Any unsupported admitted container is a stop, not permission to narrow the shared grammar silently.

Compiler admission bounds numeric targets after the maintainer decision, but still admits arbitrary generic subclasses
of Collection and Map as authored-operation payloads. The new
`StructuredInputAdmissionPlanTest#'qualifies opaque List and Map operations against the runtime container stop gate'`
compiles these Schema-owned classes and operations successfully:

```groovy
final class OpaqueWords<E> extends ArrayList<E> {
    private OpaqueWords() { throw new AssertionError('No input construction route') }
}
final class OpaqueLabels<K, V> extends LinkedHashMap<K, V> {
    private OpaqueLabels() { throw new AssertionError('No input construction route') }
}
@DSL class Configuration {
    @Builder.Method void words(OpaqueWords<String> value) { }
    @Builder.Method void labels(OpaqueLabels<String, String> value) { }
}
```

The emitted admission facts have no rejection:

| External name | Role | Payload | Exact public operation |
| --- | --- | --- | --- |
| words | OPERATION | LIST of String, declared OpaqueWords | words(collectionboundary.OpaqueWords):void |
| labels | OPERATION | MAP String to String, declared OpaqueLabels | labels(collectionboundary.OpaqueLabels):void |

Ordinary caller-parsed `[words: ['one']]` and `[labels: [region: 'eu']]` cannot supply instances of those exact parameter
types. ArrayList/LinkedHashMap snapshots are not instances of the declared subclasses. The classes have no usable
construction route, and arbitrary constructors/converters, broad overload selection and unchecked container assignment
are outside the approved slice. Rejecting these admitted operations only in the interpreter would narrow the shared
compiler grammar without a decision. No conversion policy has been added or admission semantics changed for containers.

The mismatch affects authored operation parameters even though ordinary Schema fields already have a more restrictive
container declaration rule in `DSLASTTransformation.validateSupportedCollectionDeclaration`. That field rule does not filter
these Builder operations. A shared explicit container-target policy is required; the numeric decision does not authorize
silently extending its finite-target rule to containers.

## Recommended decision boundary

Agree the finite collection/map target set for authored-operation payloads and nested data payloads, then reject unsupported
targets at compiler admission with their source and declared type. Reuse existing standard collection contracts and retain
supplied List/Map traversal order. The carrier is unreleased: this recommendation needs no version, legacy-carrier
fallback, constructor discovery, converter policy or new public API. The exact set, including whether broad Collection
and concrete JDK implementations are intended, remains a maintainer decision. The existing field-supported interfaces
and EnumSet provide evidence, not authority to silently choose that set for operations.

Alternatively, a custom-container conversion policy would broaden WIRE-1 and require its own decision. No such policy is
implemented here. This is a shared admission gate for both #932 and #529, not generator implementation work.

## Safe implementation boundary

The numeric correction was completed and committed separately as `ea00fe63`; its rejection test failed on the former
baseline and then passed with all 14 supported numeric types and nested custom-number rejections, 30 cases per Groovy
3/4/5 lane. WIRE-PLAN/B encoding is unchanged and no numeric legacy-carrier branch was added.

Runtime work has only started the package-private `StructuredInputPlan` reader and one packaged-Schema test. The fixture
reads roles and compiler-recorded public signatures without initializing the Schema or enum; compiler array descriptor
spellings are resolved to exact array Classes. This is local work in progress, with no public facade or callers. Reader
corruption, separate-artifact inheritance, full #938 binary compatibility and actual named-module byte-access gates are
still outstanding. No complete carrier-reader acceptance or production runtime delivery is claimed.

No root/active-Builder entry, preflight, dispatcher, Consumer factoring, user page or CHANGES entry exists yet. No parser,
Jackson behavior, source-order guarantee, carrier format, public API, issue state or release scope was changed. Production
implementation stops here pending the container-admission decision.

## Validation

- Numeric correction: 30 admission cases per Groovy 3/4/5 lane, zero failures/errors/skips; compiler module license passed.
- Initial reader fixture and opaque List/Map gate on Groovy 3/4/5: **2 cases per lane, zero failures/errors/skips**.
- Compiler/runtime module license and lane isolation: **pass**.
- Final diff, local manual evidence review and Markdown link/fence checks: **pass**. The reader remains incomplete and does not claim its remaining production acceptance gates.

Full affected-module suites, root check and independent production Standards/Specification review are deferred with the
blocked implementation. These fixture results do not satisfy the remaining runtime slice acceptance inventory.

Tracker impact: Related #932/#529, both remain open. No push, PR, issue update or merge. Maintainer decision and Hive
reconciliation remain outstanding; the worker does not self-archive or treat this stop as completion.

## Maintainer clarification

The maintainer retained all existing supported DSL container field declarations and selected generated element/iterable/
keyed population paths. This supersedes the open target-set alternatives above: it is not a blanket restriction of fields
to List/Map. Authored whole-container targets that require concrete/custom construction reject; authored Iterable remains
outside the existing grammar. [The actual generated-contract characterization](issue-932-wire-1-collection-contracts.md)
records the field/operation distinction, ordered population, keyed ownership, and provenance requirements before policy
changes. The carrier's existing slots suffice; no public API or new encoding is needed.
