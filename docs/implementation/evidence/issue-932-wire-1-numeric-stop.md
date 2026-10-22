# WIRE-1 numeric admission stop

Date: 2026-10-22. Status: **maintainer decision accepted; compiler correction implemented before runtime work**.

Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932),
[#529](https://github.com/klum-dsl/klum-ast/issues/529).
Authority: [WIRE-RUNTIME v1](../adr-0031-wire-runtime-v1.md#payload-preparation-admission-and-order),
after merged #937/#938/#939. Reproduced from `5446379693117fd2f1790a8f974cfdc40f4c7170`.

## Concrete contradiction

`StructuredInputAdmissionPlan.isLeaf` admits every subclass of `Number`, including Schema-defined numeric types.
The approved runtime plan permits standard numeric conversion only, forbids arbitrary constructors/converters and
arbitrary caller objects, and requires exact declared-signature dispatch. Its explicit stop condition is:

> if the compiler's broad Number-subclass classification admits an unimplementable type, stop with that concrete
> mismatch rather than silently widening conversion.

The compiled fixture in
[StructuredInputAdmissionPlanTest](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlanTest.groovy)
adds `numeric carrier admission includes a custom Number that has no permitted scalar conversion`.
Its `OpaqueNumber extends Number` is final and has only a private constructor that always throws. Its numeric accessors
also throw. The Schema declares:

```groovy
@DSL class Configuration {
    OpaqueNumber budget
    @Builder.Method void allocate(OpaqueNumber value) { budget = value }
}
```

Successful compilation emits these actual carrier facts, with no rejection records:

| External name | Role | Payload | Declared type |
| --- | --- | --- | --- |
| allocate | OPERATION | SCALAR | numericboundary.OpaqueNumber |
| budget | PROPERTY | SCALAR | numericboundary.OpaqueNumber |

The operation descriptor is exactly `allocate(numericboundary.OpaqueNumber):void`.
The test checks these independently specified facts and verifies that the packaged-format carrier constant matches the
compiled plan. It characterizes the unresolved baseline; it does not endorse arbitrary numeric conversion as an accepted
runtime contract. Update this characterization when the maintainer settles admission.

A caller-parsed `[allocate: 7]` cannot supply the required `OpaqueNumber` instance through any approved conversion.
Passing the Integer to an exact OpaqueNumber signature cannot satisfy its parameter type. Treating the entry as unsupported
at runtime alone would silently narrow the shared compiler-admitted grammar; synthesizing/coercing an instance or invoking
constructors/converters would widen runtime conversion. Neither is authorized by WIRE-1. No production reader, facade,
preflight, dispatcher or Consumer factoring was started after discovering the contradiction.

## Maintainer alternatives

1. **Recommended:** explicitly narrow compiler numeric admission to an agreed finite set of standard numeric targets;
   preserve the WIRE-PLAN/B encoding and define actionable rejection for unsupported numeric entries in previously
   compiled carriers. Align #529 with the same decision. This changes admission semantics, not the carrier format.
2. Define a new custom-number conversion policy and its initialization, user-code and binary-compatibility boundaries.
   This broadens the approved runtime slice and needs a separate decision; WIRE-1 must not invent it.

Resume production work only after the maintainer resolves this shared admission mismatch and authorizes the resulting
bounded change. No recommendation here changes the accepted grammar, carrier ABI, issue state or release scope.

## Verification and delivery

- Focused Groovy 3 reproduction: **1 case, zero failures/errors/skips**.
- Full carrier/admission and existing runtime-boundary probes on Groovy 3/4/5: **20 cases per lane, zero failures/errors/skips** (16 admission + 4 boundary).
- Module license and lane-isolation checks: **pass**.
- Local manual Standards/Specification review of this evidence-only change: **no outstanding findings**. Production dual-agent review remains deferred with implementation.
- Markdown local links/fences, `git diff --check`, and final diff/history review: **pass**. One focused evidence commit; no unrelated work.

Reproduction and compatibility commands:

```shell
./gradlew :klum-ast:test --tests '*StructuredInputAdmissionPlanTest.numeric carrier admission*' --console=plain --no-daemon
./gradlew :klum-ast:test --tests '*StructuredInputAdmissionPlanTest' --tests '*StructuredInputRuntimeBoundaryProbeTest' :klum-ast:groovy4Tests --tests '*StructuredInputAdmissionPlanTest' --tests '*StructuredInputRuntimeBoundaryProbeTest' :klum-ast:groovy5Tests --tests '*StructuredInputAdmissionPlanTest' --tests '*StructuredInputRuntimeBoundaryProbeTest' :klum-ast:license :klum-ast:verifyTestLaneIsolation --console=plain --no-daemon
```

No runtime, compiler production, Jackson, generated-contract, user-documentation or CHANGES change is delivered.
Full module suites and root `check` are deferred with implementation: this stop report establishes a metadata contradiction,
not acceptance of a production interpreter. There is no production red-to-green cycle to claim; the new test is a passing
characterization of the pre-existing carrier fact that prevents that cycle from starting.

Tracker impact: related evidence for #932/#529; both remain open. No push, PR, issue update or merge is part of this
blocked return. Hive reconciliation and the maintainer admission decision remain outstanding.

## Accepted correction

The maintainer approved the finite numeric set: primitive/boxed byte, short, int, long, float, double plus BigInteger and
BigDecimal. Other Number targets receive compiler admission rejection records naming the unsupported type and originating
field/Builder operation. The original opaque-Number reproduction is retained as rejection coverage. The carrier is
unreleased, so the earlier recommendation for legacy-carrier handling was explicitly declined: no runtime fallback,
compatibility branch, custom conversion, versioning or public API is authorized. Production WIRE-1 may resume after
this correction is committed separately. Historical stop results above describe the pre-decision baseline.

Correction validation: the rejection reproduction failed on the old admission as expected. After the correction, the full
admission specification passes **30 cases on each Groovy 3/4/5 lane**, including all 14 approved scalar targets and
unsupported custom numbers nested inside List/Map fields. Module license passes. No runtime code is part of this
separate correction commit.
