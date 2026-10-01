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

## Runnable handoff

The final branch adds investigation evidence only; the trial production diagnostic and release-facing edits were removed.
Run the current-behavior probes plus the existing inheritance control with:

```shell
./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.DslPropertyShadowingProbeTest \
  --tests 'com.blackbuild.klum.ast.BuilderFirstSpec.generated Builders preserve DSL inheritance across compilation units'
```

All six selected Groovy 3 cases pass on unchanged production code. `licenseMain`, `licenseTest`, and `git diff --check`
also pass. Groovy 4/5 were not reached in the trial full-suite run because its Groovy 3 lane failed; compatibility lanes
remain required after the static-field decision and a settled implementation. The probes intentionally characterize the
current defect and must be converted into rejection tests when the diagnostic is implemented.

## Exact decision required

Should #371 reject only user-declared **instance** fields/properties in DSL classes, leaving static field shadowing legal,
or reject static fields too and deliberately migrate the existing initializer-counter fixture?

- **Instance storage only (recommended):** reject ordinary instance properties/fields, owners, defaults, and
  construction-only `FieldType.BUILDER` state. Keep independent class constants and counters legal because they never
  enter Builder storage. Amend the issue acceptance wording to say instance fields/properties, and add a static-shadowing
  success control.
- **All user-declared fields:** explicitly accept the additional compatibility restriction on static constants/counters.
  Rename the existing test counters while retaining its per-class initializer assertions; document the static restriction.

Neither alternative changes ordinary method/getter overrides or a property implementing an abstract getter. The current
Groovy 3 controls also establish legal inherited owners and fields, ordinary Java ancestor fields, and Factory/Template
behavior. The instance-storage diagnostic must retain these controls.

For either choice, exclude JVM synthetic storage and `@KlumGenerated` fields. Do not exclude Groovy's internal synthetic
flag on source property backing FieldNodes. For `FieldType.BUILDER`, inspect the ancestor's copied Builder fields when
construction-only state has already disappeared from its Model; this is needed for both transformed source and binary
ancestors.
