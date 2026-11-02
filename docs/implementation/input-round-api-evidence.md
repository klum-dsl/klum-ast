# #953 Factory and Builder structured-input API — A0–A3

Date: 2026-11-02. Base: `59196e3b786d623b5649af4dd3d02f9b5bc19bb0` (master after #1010).
Status: **Locally qualified; both independent review axes clear. Hosted qualification is tracked on the draft PR.**
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
That first Standards review found two minor corrections in `StructuredFactoryTest`: import `CompileStatic` in the source template,
and replace its compound `expect` reflection block with `when`/`then`. Specification found one blocking API/lifecycle
collision. Those findings are corrected and aggregate qualification passed as recorded below.
No local SonarCloud analysis is run. GitHub CI/Sonar on the published head remain authoritative.

## Initial stop and accepted correction: Schema-authored applyStruct collisions

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
The generated implementation and public Builder contract declare the overriding method. At that reviewed head, the non-final runtime
methods at `InternalKlumBuilder.java:628–634` could not enforce structured interpretation or active/unsealed
session guards in this collision case, violating ADR 0032's common-capability restriction.

The assignment explicitly required stopping for unresolved generated API/lifecycle conflicts. The maintainer subsequently
approved reserving both exact signatures, accepting the narrow breaking change, and instructed implementation to resume.
The compiler now rejects matching authored/inherited/generated/default-argument signatures with rename guidance. Both
runtime methods are final, preventing legacy binary overrides from bypassing guards. Distinct overloads remain legal.
`StructuredBuilderCollisionTest` covers raw/generic Maps, bounded generic erasure, both signatures, default overloads,
generated Map configurators, source inheritance in either order, an unrelated String overload, stale-Builder rejection,
and deliberately patched legacy binary overrides. Its seven initial cases failed before the correction and passed after it.
The two Standards observations are corrected (simple imported `CompileStatic`, `when`/`then` inspection).
The same two review agents independently rechecked `b30f14edf8a1015772daff0a6cafda51b8076760` against the exact base.
Both axes report zero outstanding findings. Specification independently reran the original bypass probe and confirmed
the targeted compiler rejection. Standards also identified a committed trailing space, now removed; the complete-base
`git diff 59196e3b786d623b5649af4dd3d02f9b5bc19bb0 --check` passes. No additional review agents were created.

The separate `copyFrom(Object)` override finding was handed to the Hive at the user's explicit request. The Hive created
[issue #1011](https://github.com/klum-dsl/klum-ast/issues/1011); this branch makes no production change for it.

Added/changed repository-relative documentation links were checked: 21, zero failures. `git diff --check` passed.
Existing unrelated stale architecture-map links were not changed.

## Final local qualification

The clean reviewed production/user-documentation head `b30f14edf8a1015772daff0a6cafda51b8076760` passed:

```shell
./gradlew check --continue --console=plain --no-parallel --max-workers=2
./gradlew renderLocalDocumentation --console=plain --no-parallel --max-workers=2
```

Root check completed in 20m 13s (140 actionable tasks: 51 executed, 89 up-to-date). The aggregate XML results contain
9,920 cases, zero failures/errors and 49 existing skips. No new suppression was introduced.

| Module | Groovy 3 / baseline | Groovy 4 | Groovy 5 |
| --- | ---: | ---: | ---: |
| Compiler | 2,724 (16 skipped) | 2,726 (15 skipped) | 2,726 (15 skipped) |
| Annotations | 20 | — | — |
| Bean Validation | 154 | 154 | 154 |
| Gradle plugin / TestKit | 664 | — | — |
| Jackson | 121 | 119 | 119 |
| Runtime | 73 (1 skipped) | 73 (1 skipped) | 73 (1 skipped) |
| Test support | 8 | 6 | 6 |

The documentation render and rendered-site crawl completed in 14s (36 actionable tasks: 8 executed, 28 up-to-date).
They use the committed current user documentation and module Javadocs. No publication occurred through the renderer.

An intermediate aggregate attempt was stopped after evidence edits made the checkout dirty while nested TestKit fixtures
were publishing. The outer build requested the clean version while fixtures published the `.uncommitted` version,
causing missing-artifact failures. Those edits were saved outside the checkout, the clean reviewed tree restored, and
both commands above completed successfully on that stable tree. This was a validation-run setup error, not a passing run.

Final history review retains the ordered Factory, explicit-key, Builder, migration, documentation, collision record and
approved collision-correction commits. Reviewed commits were preserved. The subsequent qualification-record commit
changes this evidence and removes one trailing space in a test source string; production and user documentation remain
identical to the qualified/reviewed head. The complete-base diff check passes.

Remote delivery authorization audit: Git/SSH branch-push channel **authorized** (dry run); GitHub CLI PR channel
**authorized** (repository capability checked). This records channel outcomes only. The user explicitly authorized draft
publication and immediate Hive notification; hosted CI/Sonar are separate gates on the final published head.

## Delivery boundary

Create a draft PR only after local qualification and the two independent local review axes. PR relationship: **Related:
#953**. Notify the Hive immediately with the exact published head for S5's intentional base. Do not merge, close issues,
self-archive, or claim consumer Q1 qualification. Curation classification is unchanged; the architecture map only updates
the common Builder capability description. Hive owns final delivery/archive reconciliation.
