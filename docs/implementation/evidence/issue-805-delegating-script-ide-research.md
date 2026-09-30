# Issue #805 — DelegatingScript IDE completion investigation

Date: 2026-09-30. Status: research only; no IDE mechanism or public API selected.
Related: [#805](https://github.com/klum-dsl/klum-ast/issues/805), [#269](https://github.com/klum-dsl/klum-ast/issues/269).

## Observed seam

An ordinary Model script spells the typed factory call in its source, for example
`Deployment.Create.With { environment 'production' }`. Generated `Foo_DSL.Factory`
and `Foo_DSL.Builder` declarations are available to IntelliJ from the IDE-only
source mirror in the Schema project, or from compiled public classes in a binary
consumer. Generated closure signatures/`@DelegatesToBuilder` metadata then identify
the Builder at the call site. [ADR 0005](../../adr/0005-generated-dsl-support-api.md),
the [Gradle IDEA registration](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java),
and the [existing IntelliJ fixture](issue-797-intellij-named-maps.md) establish that
path. The mirrors represent generated contracts; they are not script body types or
compiler inputs.

A `DelegatingScript` body instead contains bare calls such as `endpoint 'x'`.
`FactoryHelper.createFrom` distinguishes a `DelegatingScript` from a regular
`Script`; it creates the target Builder, calls `script.setDelegate(builder)`, and
runs the script. The text/File/URL path parses using a GroovyShell configured with
`DelegatingScript` as the script base class; an active-session `AsBuilder().From`
also sets the delegate immediately before `run()`. The concrete Builder is chosen
from the *receiving factory/relationship* at runtime, not from the script source.
The source's `@BaseScript DelegatingScript` tells PSI its script superclass but
does not identify the target `Foo_DSL.Builder<Foo>`.
[Runtime paths](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java),
[documentary scripts](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ConvenienceFactoriesDocumentaryTest.groovy),
and [Groovy's `DelegatingScript` contract](https://docs.groovy-lang.org/docs/latest/html/api/groovy/util/DelegatingScript.html)
support this distinction. Groovy documents `@BaseScript` as a way to select a
script superclass and `@DelegatesTo` as a type hint for a *closure parameter*,
not as a type declaration for an arbitrary `DelegatingScript` body
([Groovy DSL documentation](https://docs.groovy-lang.org/docs/groovy-5.1.2/html/documentation/core-domain-specific-languages.html)).
IntelliJ's [BaseScript transformation support](https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/transformations/impl/BaseScriptTransformationSupport.java)
sets the synthetic script class's superclass from `@BaseScript` or a GDSL
`scriptSuperClass`; it does not infer the runtime delegate from `setDelegate`.
Groovy's `DelegatingScript.getDelegate()` is declared as `Object` in the API
linked above. Groovy's [`@BaseScript` API](https://docs.groovy-lang.org/docs/latest/html/api/groovy/transform/BaseScript.html)
says it overrides the compiler-configured base class and exposes methods
actually declared on that class, but makes no promise about methods on the
runtime delegate.

The packaged [GDSL contributors](../../../klum-ast-runtime/src/gdsl/com/blackbuild/klum/ast/gdsl)
do not bridge this gap. `CreateProperties.gdsl` contributes generated static
factory fields to `@DSL` model classes. `PolymorphicMethods.gdsl` starts from a
`closureScope()`, finds its containing call and Class argument, and resolves the
corresponding `_DSL.Builder`; a bare script body has none of those PSI anchors.
The [materialization task](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumDslGdslMaterializationTask.java)
ships only these GDSL resources. No current Eclipse DSLD source or test appears in
this repository; the [curation index](../issue-curation/issue-index.md) assigns
Eclipse DSLD separately to #14.
IntelliJ's [GDSL file index](https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/GroovyDslFileIndex.java)
can also assign a `scriptSuperClass` to PSI by file-path pattern. That is an
editor-only assignment unless the same base class is selected when Groovy
compiles the script, so it is not independently a truthful fix.

## Candidates to prove in a real IDE

| Candidate and illustrative author syntax | Runtime / compilation | IntelliJ feasibility and mirror / binary compatibility | Risk and proof needed |
| --- | --- | --- | --- |
| **Concrete typed BaseScript:** `@BaseScript DeploymentScript base` then `environment 'production'`, where a proposed `DeploymentScript` extends `DelegatingScript` and exposes methods corresponding to `Deployment_DSL.Builder<Deployment>`. | A subclass remains accepted by `Create.From(Class)` and `AsBuilder().From(Class)` under their `isAssignableFrom` checks. Text/File/URL compilation currently forces `DelegatingScript` as base; selecting another base in source or shell configuration needs an explicit compatibility test. A generic `KlumScript<Deployment>` alone does not automatically generate `environment` methods or turn an `Object` delegate into typed bare calls. | A concrete superclass with actual forwarding methods could give PSI declarations and use mirror/binary Builder types in signatures. `@BaseScript` alone proves only superclass selection. | New public script API and possible generated per-Schema type; type must denote the **Builder**, not the completed Model. #269's “actual Model type” should be treated as a model *selector* unless its mapping to `Foo_DSL.Builder<Foo>` is specified. Test Groovy 3/4/5 compile/run and same-project/binary IntelliJ completion/navigation; verify no fake mirror is compiled. |
| **Intentional file convention plus GDSL:** `service.environment.groovy` (or `service.environment`) with bare `endpoint 'x'`, where a *declared* convention maps the suffix to `Environment_DSL.Builder<Environment>`. | The extension alone changes no runtime behavior. A non-`.groovy` file must still be accepted by the chosen parser/Gradle source route; file names and relationship target must agree. A GDSL contribution affects IDEA only and cannot make an invalid runtime call valid. | IntelliJ provides [`scriptScope`](https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/GdslScriptBase.java) and [`delegatesTo(PsiClass)`](https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/dsltop/GroovyDslDefaultMembers.java), so a script-scoped GDSL could select a real generated Builder class available as a mirror or class file. `scriptScope(extension: 'environment')` applies to `.environment`, not `.environment.groovy`; the latter needs a tested name/regex match because IntelliJ's [script scope](https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/groovy-psi/src/org/jetbrains/plugins/groovy/dsl/toplevel/scopes/ScriptScope.java) uses the file's last extension. The existing closure-scope contributor is not reusable unchanged. | A convention is a new authoring contract and may collide across Schemas or mislead when one script is reused for another target. Test positive and negative suffix mappings, script refactors, same-project mirror and binary consumer, and an IntelliJ editor session. No GDSL syntax should be adopted solely from a stub test. |
| **Existing typed factory closure:** `Deployment.Create.With { environment 'production' }` in an ordinary `.groovy` Model script. | Existing, compiled and runtime-proven path; this materializes a root Model, so it is not a drop-in replacement for an active-session `DelegatingScript` recipe. | Uses generated factory/Builder contracts, mirrors or class files, and established IntelliJ evidence. | Lowest risk as interim guidance, but it does not solve the bare recipe use case. Keep a regular-script control in the IDE fixture. A closure wrapper/helper with a typed `@DelegatesTo` parameter is another possible author spelling, but must be checked against active-session semantics before calling it an alternative. |

### Follow-up: implementing the Builder interface on the script

The proposed `abstract EnvironmentScript extends DelegatingScript implements
Environment_DSL.Builder<Environment>` is a useful direction for exposing
ordinary methods to editors, but `implements` alone does not supply those
methods. The generated Builder interface contains abstract projected methods
([projection](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java)).
A standalone Groovy 5.1.2 probe with one `endpoint(String)` method failed to
compile its concrete `@BaseScript` script until the base class supplied an
`endpoint` forwarder. With the forwarder, it compiled and ran against the
delegate. An empty implementation of `endpoint` also compiled, but the script
called that empty method and **did not** call the Builder's `endpoint` method:
`DelegatingScript.invokeMethod` did not override a declared method. Thus an
empty stub would silently discard configuration. These are language probes,
not KlumAST or IDE acceptance tests.

Having the script **implement** `Foo_DSL.Builder` creates a further contract
problem: a script is a recipe forwarding to a Builder, not the Builder's
identity or lifecycle state. It would be `instanceof Foo_DSL.Builder` and
`KlumBuilder`, while `Foo.Create.isBuilder(script)` would remain false because
the runtime predicate requires `InternalKlumBuilder`
([predicate implementation](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedBuilderTypeSupport.java)).
It also conflicts with [ADR 0005](../../adr/0005-generated-dsl-support-api.md),
which reserves implementation of generated Builder interfaces for the hidden
generated Builder. A framework-generated script facade would still need an
explicit exception or contract revision if it implemented the interface.

A narrower typed facade can extend `DelegatingScript`, hold a typed
`Foo_DSL.Builder<Foo>` field, bind that field in `setDelegate`, and publish
concrete forwarding methods **without implementing** the Builder interface.
In the same standalone probe, `@Delegate(interfaces=false)` generated the
forwarder, the recipe compiled and ran, and `script instanceof Builder` was
false. [Groovy's `@Delegate` API](https://docs.groovy-lang.org/docs/groovy-4.0.26/html/api/groovy/lang/Delegate.html)
documents that switch; method and parameter annotations are *not* copied by
default. A real generator must preserve overloads, generics, nested-closure
`@DelegatesTo`, named-parameter metadata, and documentation, or explicitly
generate the forwarding methods instead. Same-project IDEA would need a
truthful source mirror of the **script base class** as well as the Builder
interface; binary consumers would use the compiled script-base class. A
published typed class may be discoverable in IntelliJ and Eclipse without
GDSL/DSLD, but both editors still need direct proof. VS Code depends on the
selected Groovy extension/language server; one
[Groovy language server](https://github.com/GroovyLanguageServer/groovy-language-server)
documents completion and configurable classpath, not this BaseScript scenario.

**Default-method variant.** A standalone Groovy 5.1.2 probe also confirmed
that a default `endpoint(String)` on `EnvironmentBuilder` lets a script
`implements EnvironmentBuilder` compile and forward to its runtime delegate
without a per-script stub. This is technically viable, but changes the
published Builder interface from a pure contract to an implementation carrier
and retains the false Builder identity described above. Hidden generated
Builders already have concrete implementations, so their normal dispatch
would override such defaults; the concern is the new public fallback behavior
and any other object implementing that interface. A second probe used a
separate `EnvironmentScriptMethods` interface with the same default forwarder;
the recipe compiled and ran while `script instanceof EnvironmentBuilder` stayed
false. A generated script-method interface plus a `DelegatingScript` base class
may therefore retain the no-GDSL, cross-editor benefit without redefining
Builder identity. It still duplicates method signatures as a generated public
surface and needs the same overload, annotation, mirror, binary, and
Groovy 3/4/5 proof. Neither default-method variant has been tried with actual
KlumAST generated contracts or any editor.

Generate such a script base class for every `@DSL` type only if that bytecode,
mirror, and public-API cost is justified. Restricting it to “root” DSL types
would miss documented child collection/map recipes. An opt-in marker on the
*target DSL Object type* is a smaller initial scope, provided the real Schelm
use case and child recipe remain covered. The opt-in spelling and any public
generated name are product/API decisions, not selected by this note.

**Recommendation:** use the real Schelm consumer as a comparative IDE tracer.
Keep the typed script facade and an intentional filename plus GDSL as primary
candidates, with an ordinary typed factory script as the control. The typed
facade may be tried first for convenience, but its forwarding signatures,
overloads, generics, `@DelegatesTo`, named-parameter metadata, documentation,
mirrors, and binary compatibility may make its generated/public surface costly.
The filename/GDSL route has different authoring and IDE maintenance costs.
Compare all three against runtime truth, IntelliJ completion/navigation and
inspections, same-project source mirrors, compiled-Schema consumers, authoring
cost, and public/generated API cost before selecting a mechanism. A GDSL
contribution must not claim a Builder or method the runtime would not use.
Select only a mechanism whose visible Builder methods resolve to the real
generated contract and whose script still configures the runtime Builder.
Groovy compilation and the current GDSL unit stubs cannot establish IntelliJ
completion, navigation, or inspection behavior. The published `Foo_DSL` contract
does not permit clients to implement or subclass its interfaces
([ADR 0005](../../adr/0005-generated-dsl-support-api.md)).

## Smallest later tracer and acceptance

Reduce the Schelm use case to one `@DSL Environment` with `String endpoint`, one keyed parent
`Deployment` with a `Map<String, Environment>` relationship, and a
`Catalog.groovy` recipe containing `endpoint 'catalog'`. Exercise both
`Environment.Create.From(Catalog)` and the parent's collection/active-session
path, because those choose the target Builder differently. For the filename
convention candidate, make a separate `catalog.environment.groovy` File/URL
recipe and verify that it is both recognized as a Groovy script in IDEA and
parsed by the runtime path. A second ordinary
`Deployment.Create.With { ... }` script is the completion control. Add a wrong
method and wrong argument type as negative probes.

Acceptance for a later implementation:

1. Groovy 3/4/5 compile and run the recipe; the intended Builder receives its
   value, and regular scripts retain their behavior.
2. In a recorded IntelliJ version, the recipe proposes `endpoint` with the
   generated Builder signature, navigation reaches the real declaration or mirror,
   and invalid calls are not presented as valid. Record actual inspection behavior
   separately from compile-time rejection.
3. Repeat the editor probe with the Schema source mirror refreshed and with only
   a compiled Schema artifact. Neither build compiles, packages, or publishes an
   IDE mirror/GDSL artifact as Schema code.
4. A mismatched script-to-target mapping must fail or be rejected clearly; no
   completion may claim a different Model's Builder operations.
5. If GDSL is selected, test contributor discovery and the intended filename
   convention in IDEA itself; if BaseScript is selected, test `@BaseScript` and
   compiler-configured text/File/URL paths separately.

Schelm is named as the active consumer in [#805](https://github.com/klum-dsl/klum-ast/issues/805),
but no Schelm checkout or source fixture was available in the inspected local
workspace. Its actual Schema, file naming, and invocation path remain inputs for
the tracer. Thus this note makes no claim of having reproduced its editor symptom.

## Eclipse and current documentation

Keep Eclipse DSLD/DSD as a separate follow-up. Groovy-Eclipse has its own DSLD
support: its [descriptor reference](https://github.com/groovy/groovy-eclipse/wiki/DSL-Descriptors)
documents `enclosingScript()`, `fileName`/`fileExtension` pointcuts, and a
`delegatesTo` contribution. That makes a parallel file-convention adapter
plausible, but it is a second artifact and inferencing engine. The descriptor
reference also says project-defined types in DSLD scripts need string names,
which a dynamic Schema mapping would need to handle. The
[project feature catalog](https://github.com/groovy/groovy-eclipse/wiki/Catalog-of-Features)
confirms current DSLD tooling;
an IntelliJ GDSL script, IDEA PSI classes, and the IDEA generated-source-root
registration cannot themselves deliver Eclipse completion. A genuine concrete
BaseScript with compiled methods might be visible to both IDEs, but that is a
hypothesis requiring an Eclipse editor fixture. Do not promise parity from the
same IntelliJ proof. This matches the repository's [separate Eclipse decision](../../adr/0022-conservative-named-map-safety.md).

The [Convenience factories guide](../../user/Convenience-Factories.md) now
states that IntelliJ does not infer the runtime Builder for bare
`DelegatingScript` calls, shows the supported ordinary typed-factory script
path and its materialization boundary, and links #805. Its former File/URL
claim of complete completion from a “small dsld-snippet” has been removed.
No IntelliJ, Eclipse, or VS Code parity is claimed. The
[FAQ IDE section](../../user/FAQ.md) can cross-link the caveat later if users
need it more prominently.
