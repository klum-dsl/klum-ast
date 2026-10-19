# Abstract Builder operations — #925

Implements the maintainer decision in [#925](https://github.com/klum-dsl/klum-ast/issues/925) against
master `e7671cde9a3b3328d8271aab36c4f4cb5e0a0c31`. The setter-specific draft #926 is superseded by this
implementation and was not used as a production approach. Investigation commit `949b880d` remains separate;
only normal behavioral evidence was migrated into shipping tests.

## Red evidence

Before production edits, the original abstract getter / abstract Builder setter / subtype storage/default
regression and the minimal non-setter abstract operation both failed in the normal `klum-ast:test` source set.
The focused Groovy 3 run executed two tests with two failures in canonicalization: the mover attempted
`MethodNode.getCode().visit(...)` on a null abstract body. These were success-expecting regressions, with no
AST perturbations or expected-failure annotations.

## Implementation seams

- `WriteAccessMethodsMover.retargetFieldVariables` skips body traversal for a body-free declaration; method
  movement and generated public Builder signature completion still occur.
- `DSLASTTransformation.createBuilderClass` mirrors abstract Schema modifiers on the hidden Builder.
  Missing concrete subtype implementations are rejected by the normal compiler/JVM contract.
- Template Model generation runs after Builder-only methods move, so `_TemplateModel` only realizes remaining
  Model methods and does not leak Builder operations.
- `TemplateMethods.completeTemplateBuilder` runs after materialization hooks and public interfaces exist.
  For each abstract Schema it generates one concrete synthetic `_TemplateBuilder`, extending its hidden Builder.
  Each remaining abstract operation has a throwing implementation identifying its signature and asking for an
  implementing concrete subtype. It never supplies a return default, discards an invocation, or records operations.
- `GeneratedBuilderSupport.templateBuilderTypeFor` and the existing `FactoryHelper.createBuilder` Template flag
  select this receiver only for Template creation, including nested/import paths. Ordinary abstract Model creation
  retains its rejection. Concrete Templates continue to use the ordinary concrete Builder.

No generated-to-runtime internal reference or new runtime ABI hook is introduced. The throwing receiver names
only the public `KlumModelException` type; the class-selection change stays inside the runtime implementation.

## Acceptance evidence

`AbstractBuilderMethodTest` covers the original setter/default regression, arbitrary non-setter dispatch,
missing subtype implementation, abstract hidden/public Builder signatures, void/reference/primitive/varargs/generic
container forms, dynamic Groovy, Java 17, static Groovy, separately compiled static Schema subtypes, targeted abstract
Input/Result rejection, and completed/Template Model surface separation. Its documentary feature is
`abstract Builder operations dispatch through concrete subtype storage and implementations`, linked to
[Inheritance](../user/Inheritance.md#abstract-builder-operations).

`AbstractBuilderTemplateTest` covers map/closure/combined/empty creation, scoped and anonymous application,
`copyFrom`, direct and indirect precise errors, concrete Template operation behavior, ordinary abstract construction
rejection, file/legacy-alias inputs, abstract intermediate inheritance, nested abstract recipes, and serialization.
`KlumJacksonImporterSpec` adds abstract-parent Template import. The existing packaged-schema fixture in
`JpmsPackageBoundaryTest` adds concrete dispatch, abstract Template creation, and precise failure through a Java
consumer; its Groovy 4/5 lanes exercise the named schema and runtime modules.

The focused core matrix passes all 20 cases on each of Groovy 3, 4, and 5 (60 executions). Error signatures are
formatted explicitly because Groovy 5 changed `MethodNode.getTypeDescriptor()` formatting. Full repository validation is recorded in the
pull request and completion handoff against the final revision.

## Scope and delivery

This is the approved general method contract, without setter-specific inference or Template semantic changes.
Abstract `@Builder.Input` and `@Builder.Result` remain rejected. No general property-inheritance redesign is included.
Tracker relationship: `Related: #925`; issue closure and release acceptance remain with Hive reconciliation.
Curation impact: no release-gate, dependency, owner, or published-release fact changes; the existing #925 decision
continues to own acceptance. Documentation updates cover inheritance, Templates, migration, ADR 0020, and `CHANGES.md`.
