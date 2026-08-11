# ADR 0016 implementation plan: Template creation and scoped application

This plan implements accepted [ADR 0016](../adr/0016-template-creation-and-scoped-application.md) for
[#710](https://github.com/klum-dsl/klum-ast/issues/710) and its final naming correction [#737](https://github.com/klum-dsl/klum-ast/issues/737). It is a public 4.0 generated-contract change, not a change to
the Template recipe/materialization protocol in ADR 0004.

## Confirmed #710 starting surface and failure path

| Receiver | Current public members | Current role | Problem |
| --- | --- | --- | --- |
| `Foo.Create : Foo_DSL.Factory` | inherited/projected `Template()` / `Template(Map, Closure)` / `Template(Closure)` / `Template(Map)` and `TemplateFrom(File/URL[, ClassLoader])` | root Template creation | It occupies the name required by the proposed final `Template` property. |
| `Foo.Template : Foo_DSL.Template` | `With(template, body)`, `With(map, body)`, `WithAll(map/list, body)`, `Create(...)`, `CreateFrom(File/URL[, ClassLoader])` | both scope application and root Template creation | One generated interface presents two distinct interfaces to callers. |
| `Foo_DSL.Template` | public interface implemented by `Foo$_Template` through `GeneratedTemplateSupport` | nameable generated contract | It must retain scope methods and may retain only deprecated creation aliases. |

`TemplateMethods` creates `Foo.Template`, its adapter, and every member of `Foo_DSL.Template`. `GeneratedDslSupport`
creates the namespace/interface and projects factory implementation members to `Foo_DSL.Factory`. `KlumFactory` supplies
the inherited `Template*` methods, while `BoundTemplateHandler`, `GeneratedTemplateSupport`, and `FactoryHelper` execute
the current Template creation/application paths. `FactoryHelper.createAsTemplate` is the semantic authority for marked
Template root construction.

Issue #737 changes only the delivered scoped-application type name: `Foo.Template` is now typed as
`Foo_DSL.TemplateScope`, and `Foo_DSL.Template` is absent. It does not revisit the #710 root-creation or scoped-behavior
decisions captured below.

The user documentation mostly teaches `Foo.Template.Create` and `CreateFrom`, but `Model-Phases.md`, `Migration.md`,
and historical changelog material still contain `Foo.Create.Template` or older spellings. Existing focused behavior is
distributed through `TemplatesSpec`, `BoundTemplatesSpec`, `TemplateCompanionSpec`, copy-source coverage, and
`TemplatesDocumentaryTest`; generated descriptor/Java/static-Groovy coverage is in `GeneratedDslSupportSpec`.

## Target contract

```groovy
@DSL
class ServiceConfiguration {
    String region
}

def baseline = ServiceConfiguration.Create.Template.With {
    region 'eu-central'
}

ServiceConfiguration.Template.With(baseline) {
    ServiceConfiguration.Create.With { }
}
```

The exact public descriptor intent is:

```text
ServiceConfiguration.Create                        : ServiceConfiguration_DSL.Factory
ServiceConfiguration_DSL.Factory.Template          : public static final ServiceConfiguration_DSL.Factory.Template field
ServiceConfiguration.Create.Template                : same generated field, selected through the Factory contract
ServiceConfiguration_DSL.Factory.Template.With(...) : ServiceConfiguration
ServiceConfiguration_DSL.Factory.Template.From(...) : ServiceConfiguration
Service.Template                                   : Service_DSL.TemplateScope
Service_DSL.TemplateScope.With/WithAll             : body result
Service_DSL.TemplateScope.Create/CreateFrom        : deprecated compatibility aliases
```

The Template-creation `With` closure carries `@DelegatesTo(ServiceConfiguration_DSL.Builder)` and
`Closure.DELEGATE_ONLY`. The scoped `With` closure preserves its existing generic body result and does not gain a Builder
delegate. For Java and `@CompileStatic` Groovy 3, 4, and 5, the canonical chain is
`ServiceConfiguration.Create.Template.With(...)`. The upper-case spelling is intentional: it is a generated public
static final field on each model-specific Factory interface, matching `Foo.Create`, not a JavaBean getter.

`Foo.Create.Template(...)` and `Foo.Create.TemplateFrom(...)` must be absent—not merely deprecated—so the Factory
property cannot be shadowed. `Foo.Template.From` is not a current member and must not be invented as an alias.

## Compatibility and migration matrix

| Current spelling | 4.0 target | Compatibility action |
| --- | --- | --- |
| `Foo.Create.With(...)`, `Foo.Create.From(...)` | unchanged | no migration |
| `Foo.Create.Template(...)` | `Foo.Create.Template.With(...)` | remove Factory method; document source/binary break and compiler absence |
| `Foo.Create.TemplateFrom(source)` | `Foo.Create.Template.From(source)` | remove Factory method; document source/binary break |
| `Foo.Template.Create(...)` | `Foo.Create.Template.With(...)` | retain deprecated forwarding alias throughout 4.x |
| `Foo.Template.CreateFrom(source)` | `Foo.Create.Template.From(source)` | retain deprecated forwarding alias throughout 4.x |
| `Foo.Template.With(template/map) { ... }` and `WithAll(...)` | unchanged | application remains source/binary compatible |

The deprecated aliases preserve all currently supported map/closure and File/URL/ClassLoader overloads. Deprecation
documentation must state the one exact replacement for each overload.

## Builder-first migration-helper candidate

This design admits a convenience-only migration-helper stage, not an automated migration guarantee. It may make only the
following direct rewrites when the receiver is a statically written DSL type name:

| Direct established form | Mechanical result |
| --- | --- |
| `Foo.Create.Template(...)` or `Foo.Create.Template { ... }` | `Foo.Create.Template.With(...)` or `Foo.Create.Template.With { ... }` |
| `Foo.Create.TemplateFrom(source)` | `Foo.Create.Template.From(source)` |
| `Foo.Template.Create(...)` or `Foo.Template.Create { ... }` | `Foo.Create.Template.With(...)` or `Foo.Create.Template.With { ... }` |
| `Foo.Template.CreateFrom(source)` | `Foo.Create.Template.From(source)` |

The helper must preserve map/closure argument text and every other surrounding expression. It must not touch
`Foo.Template.With` or `WithAll`, anonymous scoped-map application, a receiver variable such as `type.Template.Create`,
method pointers, reflection, string/script content, custom factory expressions, or any syntax it cannot recognize
unambiguously. Those cases stay for the migration checklist and compiler diagnostics.

The published script is as-is convenience text, not a build tool. Its instructions require a clean, version-controlled
worktree and explicitly say to run it from the **schema module directory**, never the project root. The prescribed order
is: update to the target KlumAST version; run the script; inspect and commit/revert its diff; follow the Builder-first
migration checklist; then compile and repair remaining errors. A partially migrated, non-compilable source tree is an
expected intermediate state. The implementation must add an executable migration-helper fixture that runs the script in
a copied schema-module tree, proves these four direct forms change, and proves scoped application and excluded forms do
not change. It also needs a short `Builder-First-Migration.md` section that links to the canonical Template migration
guidance and the new Template documentary example.

## Modules and seams

| Area | Owner/seam | Required change |
| --- | --- | --- |
| Root creation | `klum-ast-runtime`: `KlumFactory`, `FactoryHelper` | Remove public `Template*` root methods from `KlumFactory`; retain the internal/root `createAsTemplate` mechanics. Add or extract a narrow generated-only Template-creation adapter rather than exposing `FactoryHelper`. |
| Scoped application and aliases | `klum-ast-runtime`: `BoundTemplateHandler`, `GeneratedTemplateSupport` | Keep `With`/`WithAll`; make `Create`/`CreateFrom` deprecated forwarders to the one root creation implementation. Keep no raw `TemplateManager` public seam. |
| Generated Factory property | `klum-ast`: `TemplateMethods`, factory-generation seam, `GeneratedDslSupport` | Generate a model-specific public static final `Template` field on `Foo_DSL.Factory`, initialized through a generated-only bridge and typed as new nested public `Foo_DSL.Factory.Template`. Do not add a JavaBean getter or hide the field on the implementation. |
| Existing Template handler | `klum-ast`: `TemplateMethods` | Keep the static `Foo.Template` field and `Foo_DSL.TemplateScope` scope methods; retain creation members only as explicitly deprecated aliases. |
| Generated linkage | `runtime.generated` under ADR 0015 | If a runtime bridge is needed, keep it generated-only and descriptor-minimal. No schema descriptor names `BoundTemplateHandler`, `FactoryHelper`, or `TemplateManager`. |
| IDE surfaces | source-mirror task, AnnoDocimal projection, #703 GDSL work | Mirrors must include the static `Factory.Template` field plus `With` and `From` with the real nested type; GDSL must expose the property consistently with bytecode. Do not duplicate the mirror into compilation. |
| Contract inventory | #468 public inventory | Refresh the generated factory/template row and artifact comparison; classify nested `Factory.Template` as generated hook and aliases as deprecated compatibility members. |

## Thin vertical slices and commit boundaries

### TC-1 — Establish the generated Factory Template type and property

Add `Foo_DSL.Factory.Template`, create the model-specific public static final `Foo_DSL.Factory.Template` field, and
supply the narrow bridge that exposes the current Template root creation inputs. Remove `Template*` from the
inherited/projected Factory surface in the same commit; do not rely on Groovy dispatch to choose between a field and a
method.

**Acceptance:** reflection sees a public static final `Foo_DSL.Factory.Template` field of the nested public type; Java
and static Groovy call `Foo.Create.Template.With/From`; no `getTemplate()` compatibility getter or public Factory
`Template(...)`/`TemplateFrom(...)` method exists; closure delegate metadata is the concrete public Builder; all produced
objects are `TemplateManager.isTemplate`-true.

**Commit boundary:** runtime adapter plus AST/property generation plus one focused descriptor/behavior test are one vertical
commit. Do not move scope behavior or unrelated Template semantics in this slice.

### TC-2 — Preserve scoped application and introduce deprecated aliases

Name the scoped application contract `Foo_DSL.TemplateScope` in new documentation while retaining `Create` and `CreateFrom` as
deprecated forwarding methods. Ensure the generated adapter, generated-runtime bridge, and abstract-Template path all
delegate to the same Template root creation mechanism.

**Acceptance:** `With`/`WithAll` nesting and restoration stay byte-for-byte behaviorally covered; existing `Create` and
`CreateFrom` calls compile with deprecation and return the same marked recipe; abstract Template creation, `copyFrom`,
ownership rejection, recipe replay, and Java serialization retain their established outcomes.

**Commit boundary:** adapter/runtime deprecation work and focused compatibility/Template-companion tests. A follow-up
commit is acceptable only for mechanical Javadoc deprecation text if implementation/test review keeps TC-2 clearer.

### TC-3 — Prove full public and IDE contract parity

Extend the generated public-interface fixture to inspect both nested Template interfaces and all descriptor absences.
Add Java and `@CompileStatic` Groovy consumer fixtures, then run the existing fixture in Groovy 3/4/5 lanes. Refresh the
IDE source mirror assertion and the GDSL contributor/fixture if #703 has landed; otherwise state and coordinate that
dependency rather than duplicating its work.

**Acceptance:** Java names `Foo_DSL.Factory.Template` and uses `Foo.Create.Template`; static Groovy uses the same
upper-case property syntax and catches a deliberate old Factory-method compile failure; mirrors contain the nested type
and static field, and neither source mirror nor GDSL advertises the removed methods. The public-inventory comparison
records no internal runtime descriptor.

**Commit boundary:** public-surface fixture/inventory assertion first; source-mirror/GDSL parity in a dependent commit only
when its implementation owner permits it.

### TC-4 — Publish the migration language

Rewrite `Templates.md` so creation uses `Create.Template.With/From` and application uses `Template.With/WithAll`; retain
a concise deprecated-alias note. Update `Migration.md`, `Model-Phases.md`, `Copy-Strategies.md` examples where necessary,
`CHANGES.md`, generated Javadocs, navigation if headings move, and the #468 inventory. Add a #710 documentary example
whose code is the canonical three-line target example.

**Acceptance:** repository search finds no canonical/documented `Create.Template(...)` method calls and no invented
`Template.From`; the documentary test has `@Issue("710")`, `@Tag("documentary")`, and `@See`; the migration-helper
fixture demonstrates only the approved direct rewrites; render/link checks pass.

**Commit boundary:** executable documentation test and user/migration/changelog/inventory update together. Do not
retrofit unrelated Template examples beyond the entrypoint spelling.

## Executable test map

| Contract | Primary seam | Required proof |
| --- | --- | --- |
| Generated descriptor/property | `GeneratedDslSupportSpec` or a new focused `TemplateEntrypointTest` | reflection for the public static final `Factory.Template` field, nested `Factory.Template` type, and absence of a `getTemplate` compatibility getter or Factory `Template*` methods |
| Java/static Groovy language | generated consumer compilation helpers | Java and `@CompileStatic` Groovy 3/4/5 `Create.Template.With/From`, plus Builder closure delegation |
| Deprecated compatibility | Template adapter/handler tests | each `Template.Create*` overload remains callable and annotated `@Deprecated` |
| Root Template semantics | `TemplatesSpec`, `TemplateCompanionSpec`, `TemplatesDocumentaryTest` | marked identity, abstract synthetic implementation, lifecycle omissions, recipe replay, copy/serialization/ownership boundaries unchanged |
| Scoped application | `BoundTemplatesSpec` | single/map/list scopes, nesting/restoration, body result and anonymous map behavior unchanged |
| Generated/IDE surface | source-mirror and GDSL tests | source mirror emits both nested public interfaces and correct property; completion does not offer removed Factory methods |
| Public release surface | #468 inventory fixture | new generated hook classified and no internal generated descriptor leak |

New executable tests carry `@Issue("710")` or `@Issue("737")` according to their contract; the canonical user-facing happy path is marked `@Tag("documentary")` and
links back to the final Templates page as required by `docs/agents/testing.md`.

## Risks, rollback, and open choices

| Risk or question | Control |
| --- | --- |
| A Factory method survives through inheritance and shadows the property. | Remove the `KlumFactory.Template*` family before generating the property; assert absence by reflection and negative static compilation. |
| A nested type or static field collides with `Foo_DSL.TemplateScope`. | Use the distinct binary names `Foo_DSL$Factory$Template` and `Foo_DSL$TemplateScope`; assert both fields/types in bytecode and mirrors. |
| Aliases become a permanent second documented route. | Deprecate every creation alias, keep `Foo.Template` application-only in user docs, and prohibit new creation methods on the handler in review. |
| Moving adapters changes recipe/materialization behavior. | Delegate every route to existing `FactoryHelper.createAsTemplate`; retain ADR 0004 behavioral suite before refactoring implementation. |
| Generated bridge expands into a handwritten runtime API. | Use only generated-runtime linkage permitted by ADR 0015 and inspect public descriptors/inventory. |
| GDSL work has not landed when the core contract is ready. | Land bytecode/mirror truth first; coordinate the additive GDSL completion change with #703 rather than creating a parallel completion contract. |
| A release-candidate adopter depends on `Foo.Create.Template(...)`. | The explicit migration row and compile failure give a mechanical replacement. If release timing makes this unacceptable, defer #710 rather than retaining property/method ambiguity. |

ADR 0016 was accepted for 4.0, and #710 delivered TC-1 through TC-4. Issue #737 completes the final scoped-application
type-name correction; no additional product choice is required.
