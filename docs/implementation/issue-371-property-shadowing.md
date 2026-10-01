# #371 — DSL hierarchy property shadowing

Canonical contract: [#371](https://github.com/klum-dsl/klum-ast/issues/371), retained for 4.1.

## Current-behavior reproduction

Reproduced with Groovy 3 on base `8fd0847a` before adding the diagnostic. In `AbstractDSLSpec`, compile this Schema and
create a `WebService` with `name 'configured'`:

```groovy
@DSL class Service {
    String name = 'ancestor'
    String fromParent
    @PostTree void recordParent() { fromParent = name }
}
@DSL class WebService extends Service {
    String name = 'subclass'
}

def service = WebService.Create.With { name 'configured' }
```

Compilation succeeds. Reflection during configuration finds `WebService$Builder.name == 'configured'` and
`Service$Builder.name == 'ancestor'`. The inherited callback records `service.fromParent == 'ancestor'`, while the
materialized Model exposes `service.name == 'configured'`. The two declarations are independent construction storage;
name-based configuration/materialization and the inherited callback do not agree on which storage they use.

The minimal compile-only reproduction needs just `@DSL class Service { String name }` and
`@DSL class WebService extends Service { String name }`. The original regression command was:

```shell
./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.DslPropertyShadowingTest
```

Before the change, the rejection test failed because no `MultipleCompilationErrorsException` was thrown. After the
change, the same Schema fails with a diagnostic naming both declarations and pointing at the subclass field.

## Owners and defaults

A parent and child each declaring `@Owner Object container` also compile. Runtime owner discovery sees both
`Parent$Builder.container` and `Child$Builder.container`, while assignment looks up the field by name. In the minimal
owned-child probe, completed owner access resolves to the same Container; duplicate discovery alone does not prove an
exception, but the two independently declared owner contracts are ambiguous.

With `@Default(code = { 'ancestor-default' }) String name` on the ancestor and a same-named subclass default returning
`'subclass-default'`, a completed empty WebService exposes `'subclass-default'`. That result does not establish a supported
property override contract: the initializer/callback reproduction already shows inconsistent construction state.

## Compatibility question found by the full suite

A trial diagnostic covering every user-declared field passed its focused Groovy 3 rejection/control tests, but the full
compiler-module suite ran 1,179 tests and found one failure: `BuilderFirstSpec#'generated Builders preserve DSL inheritance
across compilation units'`. Both Parent and Child deliberately declare `static int initializerCalls`, and the test
requires each class's counter to be exactly one after their independent initializers run. These static fields remain on
the Model; `moveSourceStateToBuilder` explicitly skips static fields. They do not cause the split Builder-storage defect.

This is a legitimate inheritance pattern already protected by executable coverage. The issue's general wording does not
settle whether such static fields are outside the DSL-storage rule or intentionally prohibited for consistency.

## Accepted decision and implementation

The maintainer accepted **instance storage only**: reject same-named user-declared instance fields/properties in a DSL
hierarchy, including owners, defaults, and construction-only `FieldType.BUILDER` state. Static fields remain legal because
`moveSourceStateToBuilder` skips them. The original initializer-counter fixture in `BuilderFirstSpec` remains unchanged.
This is a bounded source-compatibility tightening in 4.1, not a supported override capability removed from the Schema.

`DSLASTTransformation` checks the descendant's declared instance fields before moving state to its Builder. It walks
DSL ancestors and looks for declared storage with the same name. Ordinary source and binary Model fields are visible
there; a transformed or binary ancestor's construction-only declaration is recovered from its copied Builder field and
`FieldType.BUILDER` annotation. The diagnostic names the descendant and the ancestor Model declarations and attaches to
the descendant FieldNode's source position.

The predicate excludes static fields, JVM `ACC_SYNTHETIC` storage, and `@KlumGenerated` fields. Groovy's internal
FieldNode synthetic flag is deliberately accepted: ordinary source properties use it for their backing fields.
Only storage declarations are checked, so ordinary methods/getters and properties implementing abstract getters remain
legal. Traversal stops outside the DSL hierarchy and does not police Java ancestor storage.

## Acceptance coverage

`DslPropertyShadowingTest` converts the defect probes into rejection tests and retains the static-counter and getter
controls. Its declaration matrix checks ordinary properties, private/protected fields, collections, owners, defaults,
ignored/transient storage, Builder-only storage, and mixed Builder/Model declarations with ancestor-first source,
descendant-first source, and separately compiled binary ancestors. It checks the diagnostic's exact descendant line and
column, and separately covers the original split lifecycle scenario and a distant ancestor.

Legal controls cover static counters, inherited fields/defaults/owners, ordinary method/getter overrides, abstract-getter
implementations, Java ancestor fields, Factory/Template behavior, KlumGenerated declarations on either side, and JVM
synthetic ancestor storage in source and bytecode. Normal generated hierarchy storage is also exercised by the unchanged
`BuilderFirstSpec#'generated Builders preserve DSL inheritance across compilation units'` fixture.

The documentary happy path is `DslPropertyShadowingTest#'configures inherited storage without redeclaring it'`, linked to
[Instance storage names](../user/Inheritance.md#instance-storage-names). Migration guidance is linked from the 4.1 section
of [Migration](../user/Migration.md#unique-instance-storage-in-dsl-hierarchies), and the diagnostic is recorded in CHANGES.

## Verification and delivery

Focused command (repeat for `groovy4Tests` and `groovy5Tests`):

```shell
./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.DslPropertyShadowingTest \
  --tests 'com.blackbuild.klum.ast.BuilderFirstSpec.generated Builders preserve DSL inheritance across compilation units'
```

Compiler-module `:klum-ast:check` passes: 1,215 tests in each of Groovy 3, 4, and 5, with zero failures/errors and 15
pre-existing skips per lane. The 50-case acceptance class passes in every lane. License checks and test-lane isolation
pass. The old static initializer fixture is unchanged. Root `./gradlew check` also passes, including downstream modules,
coverage tasks, and documentation renderer checks. New documentation anchors and documentary links are verified.

Independent local Standards and Spec review against base `8fd0847a` and implementation/documentation tip `25e6b970`
found no actionable findings on either axis. Commit-history review retains the original evidence step followed by the
self-contained diagnostic/acceptance-test change, release-facing documentation, and a final validation/audit record. `git diff --check` passes.

Delivery authorization audit: Git transport `authorized` (dry run); gh CLI repository mutation category `authorized`
(repository permission check). No connected GitHub App delivery channel is used.

Draft [PR #821](https://github.com/klum-dsl/klum-ast/pull/821) uses `Closes #371`. Its first revision `e8c231a3` passed CI
and the SonarCloud gate (94.7% new-code coverage, no security hotspots). Detailed analysis reported three missing-brace
maintainability findings in test controls. An additive follow-up supplies those braces without changing production code
or test semantics; all 50 acceptance cases plus the unchanged initializer control pass again in Groovy 3/4/5, along with
`licenseTest` and `git diff --check`. Final remote revalidation is reported in the PR follow-up and Hive handoff. Tracker impact is
`Closes #371`, selected in the implementation assignment; this localized diagnostic has no release-gate or curation
impact. An open draft PR still requires Hive reconciliation and merge and is not an archive-safe outcome.
