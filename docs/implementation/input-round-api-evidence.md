# #953 Factory and Builder structured-input API — A0–A3

Date: 2026-11-02. Base: `59196e3b786d623b5649af4dd3d02f9b5bc19bb0` (master after #1010).
Status: **Blocked by a generated Builder signature collision; no PR or publication.**
Branch: `codex/953-structured-factory-builder`. Related: #953; no issue closure or release qualification.

## Contract and implementation

[ADR 0032](../adr/0032-builder-oriented-structured-input.md#public-factory-creation-and-builder-application--953)
and the [input plan](input-round-plan-4-2.md#development-api-migration--953) govern the exact eight overloads.

- `KlumFactory` supplies common `FromStruct(Map<?, ?> document[, String sourceName])`; `KlumFactory.Keyed` supplies
  `FromStruct(String rootKey, Map<?, ?> body[, String sourceName])`. Existing compiler projection specializes these into
  the public generated Factory with truthful concrete return types, wildcard Map parameters and source mirrors.
- Both root routes delegate to the existing `StructuredInputSupport` and single Factory configuration callback. Explicit
  identity is available at PostCreate and both root forms run exactly one PostApply. No `With { applyStruct(...) }` adapter.
- `KlumBuilder` declares two void `applyStruct(Map<?, ?> body[, String sourceName])` methods, implemented once on
  `InternalKlumBuilder`. Generated Builder contracts inherit them. Existing session/thread/sealed/Template restrictions,
  ordered partial failure and one successful application PostApply remain in force. Completed Models expose neither.
- Explicit source names must be nonempty opaque text. Ordinary calls generate a capability label. Source names are never
  opened, normalized or used as keys. Self-contained keyed documents require their actual externally named key;
  explicit-key and active-Builder bodies forbid it. Existing nested positional rules remain unchanged.
- Remove the unreleased `KlumStructuredInput` facade and migrate ordinary runtime, documentary, Bean Validation,
  binary, JPMS and Gradle consumer callers directly. No forwarding shim, registry or parser is introduced.
- A small set of deliberately pre-initialization rejection probes calls the retained internal `StructuredInputSupport`
  seam. They enforce internal contract inspection rather than a public reader API. Access to a generated Factory's
  ordinary `Create` field necessarily initializes its owning class. Public tests separately enforce rejection before
  Builder allocation where required. The no-user-code tooling guarantee remains unchanged.
- S6/#1007 Template behavior is reused, not expanded. `FromMap`, managed Jackson production code, schema grammar,
  file/URL parsing, `AsBuilder.FromStruct`, nested Builder scopes and CollectionFactory remain outside this slice.

## Executable and documentary evidence

[`StructuredFactoryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredFactoryTest.groovy)
carries `@Issue('953')`; its three documentary features carry `@Tag('documentary')` and `@See` links to
[Factory roots](../user/Structured-Input.md#factory-roots) and [active Builders](../user/Structured-Input.md#active-builders):

- `Factory documents carry their own root identity`: inherited renamed root key, direct and keyed-Map child keys.
- `explicit root keys configure one ordinary lifecycle`: exact completed Model, identity before PostCreate, one PostApply.
- `active Builders apply structured bodies without completing construction`: multiple applications, enclosing lifecycle,
  and absence of the method on the completed Model.

Additional features compile Java and static Groovy consumers; inspect all root descriptors and inherited void methods;
compile source mirrors; verify root/body rejection and generated/opaque/null/empty diagnostics; and exclude AsBuilder,
relationship projection, recursive structured invocation and the removed facade. Existing migrated suites preserve
ordering, strict payloads, keys, ownership, exceptions, cause identity, stale/wrong-thread/Template guards and grammar.
`JpmsPackageBoundaryTest` adds explicit-key roots and both inherited Builder methods to its real Java/static Groovy
artifact consumers. Gradle/TestKit consumers and the existing FromMap/Jackson suites are included in the aggregate gate.

## Validation and review/change brief

Test-first cycles observed missing-method failures before implementing common Factory roots, explicit-key roots and
Builder application; each passed after its minimal implementation and was committed with its driving test.

Passed focused commands:

```shell
./gradlew :klum-ast:test --tests '*StructuredFactoryTest' --console=plain --no-parallel --max-workers=2
./gradlew :klum-ast:test --tests '*StructuredFactoryTest' --tests '*BuilderModelTypeTest' \
  --tests '*BuilderFirstSpec' --tests '*GeneratedDslSupportSpec' --tests '*OwningRelationshipTracerTest' \
  --tests '*BuilderRelationshipLifetimeTest' --console=plain --no-parallel --max-workers=2
./gradlew :klum-ast:test --tests '*Structured*Test' --tests '*Input*Test' \
  --tests '*ConstraintProjectionTest' --tests '*SchemaProjectionP2InputTest' \
  --console=plain --no-parallel --max-workers=2
```

The final focused migration run passed 496 cases with zero failures. Initial migration failures identified no-initialization
probes that must retain their internal seam; those probes were preserved, not weakened. Non-Map public roots now reject
at method dispatch, and the unkeyed explicit-key overload is absent as required.

Full root `./gradlew check --continue --console=plain --no-parallel --max-workers=2` was interrupted (exit 130) during
`:klum-ast:groovy5Tests` after the Specification review found the collision below. It is not a passing aggregate gate.
Exactly two independent read-only review agents inspected `59196e3b786d623b5649af4dd3d02f9b5bc19bb0...10c16e43bc1923f14a184463696d5a0efc7e5bf5`.
Standards found two minor pending corrections in `StructuredFactoryTest`: import `CompileStatic` in the source template,
and replace its compound `expect` reflection block with `when`/`then`. Specification found one blocking API/lifecycle
collision. All changes remain local. Final history review and aggregate qualification remain pending.
No local SonarCloud analysis is run. GitHub CI/Sonar on the published head remain authoritative.

## Stop condition: Schema-authored applyStruct overrides the common capability

The Specification reviewer reproduced this valid Schema shape using the built Groovy 3 production classpath:

```groovy
@DSL class Collision {
    static int calls
    @Builder.Method void applyStruct(Map body) { calls++ }
}
KlumBuilder captured
Collision.Create.With { captured = delegate }
captured.applyStruct([value: 'expected']) // Executes the authored method after construction has completed.
```

Observed result: `AFTER_COMPLETION_CALLS=1`. A second probe assigned `value = 'hijacked'` in the authored method;
calling `applyStruct([value: 'expected'])` during construction produced `MODEL_VALUE=hijacked`.
The generated implementation and public Builder contract declare the overriding method. The new non-final runtime
methods at `InternalKlumBuilder.java:628–634` therefore cannot enforce structured interpretation or active/unsealed
session guards in this collision case. This violates ADR 0032's common-capability restriction.

The assignment explicitly requires stopping for unresolved generated API/lifecycle conflicts. No collision policy,
compiler rejection, final-method change, renamed API or other workaround has been selected. Resume requires a maintainer
choice for collisions with both exact `applyStruct` signatures, then test-first implementation and requalification.
The two existing review agents can recheck the correction; no additional review agents were created.

Added/changed repository-relative documentation links were checked: 21, zero failures. `git diff --check` passed.
These checks do not qualify the blocked implementation. Existing unrelated stale architecture-map links were not changed.

## Delivery boundary

Create a draft PR only after local qualification and the two independent local review axes. PR relationship: **Related:
#953**. Notify the Hive immediately with the exact published head for S5's intentional base. Do not merge, close issues,
self-archive, or claim consumer Q1 qualification. Curation classification is unchanged; the architecture map only updates
the common Builder capability description. Hive owns final delivery/archive reconciliation.
