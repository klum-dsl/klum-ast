# Issue #808 — VS Code support boundaries

Date: 2026-09-30. Status: evidence-only research; recommendations require maintainer selection.
Related: [#808](https://github.com/klum-dsl/klum-ast/issues/808), [#805](https://github.com/klum-dsl/klum-ast/issues/805).
Repository base: `b747422ca5a149065cf612cae749563f9d92329d` (includes the merged #805 manual recipe).

## Recommended support statement

> KlumAST's documented DSL editor integration targets IntelliJ IDEA. VS Code can be used for Groovy/text editing;
> completion, navigation, and diagnostics depend on the selected Groovy extension and its classpath configuration.
> KlumAST has not validated VS Code DSL completion, generated-source-mirror registration, or DelegatingScript support.
> The IntelliJ GDSL recipes are specific to IntelliJ. Use the project's Gradle build and runtime validation to check models.

This is proposed wording, not a compatibility policy change. The existing
[onboarding boundary](../../user/Gradle-Onboarding.md#intellij-and-generated-dsl-support) already avoids IDE-parity claims.
No user guide, release note, product code, GitHub state, or milestone was changed by this investigation.
Keep #808 unmilestoned: there is enough evidence for a narrow documentation clarification or acceptance experiment,
not a VS Code support commitment or a 4.1 release gate. The IntelliJ result of #805 remains independently useful.

## Tooling inspected

These are sampled options, not an exhaustive extension census. Repository source versions below are pinned;
Marketplace descriptions establish availability and claims, not tested KlumAST compatibility or current package/source identity.
A latest commit date alone does not establish maintenance guarantees.

| Tool / inspected source | Verified capability or boundary | Consequence for KlumAST |
| --- | --- | --- |
| [Groovy-Guru](https://marketplace.visualstudio.com/items?itemName=DontShaveTheYak.groovy-guru), source `da6846d5449fd7c762760d72a84f8951257b50b9` (2022-08-28) | Its own listing calls IntelliSense basic/work-in-progress. The [submodule declaration](https://github.com/DontShaveTheYak/groovy-guru/blob/da6846d5449fd7c762760d72a84f8951257b50b9/.gitmodules) points to its own server fork; pinned server gitlink is `39a12bbbbafa4cc68147e9ea26836664f73af16e`. | Do not attribute newer Moonshine or groovyls results to a released Guru package. No current Guru KlumAST acceptance was run. |
| [Moonshine/Prominic server](https://github.com/GroovyLanguageServer/groovy-language-server/tree/347d098a928707223ce44b52cc45174a6327a5f3), `347d098a928707223ce44b52cc45174a6327a5f3` (2026-05-19) | Completion, definition, hover, type-definition and other LSP methods; manually configured JAR classpath; Groovy 4.0.26 in the inspected build. The README's VS Code client is a sample with no planned Marketplace release. | Headless probes below verify ordinary typed completion, but not delegated closure completion. Java mirrors are not ordinary inputs to its Groovy-only workspace scanner. |
| [groovyls](https://github.com/trustytrojan/groovyls/tree/c31000bd59403ae3f14e0f5b89a273807effabcf), `c31000bd59403ae3f14e0f5b89a273807effabcf` (2026-09-19) | Fork adds a static type-checking pass, inferred method targets, and Jenkins GDSL symbol injection. Inspected build uses Groovy 5.1.1/JDK 21; bundled VS Code manifest is a private sample, version `0.0.0`. | Existing delegated source calls can navigate in the probe; delegate completion is still missing. Jenkins GDSL support does not execute the #805 contributor correctly. |
| [TomaszRup Language Support for Groovy](https://marketplace.visualstudio.com/items?itemName=TomaszRup.groovy-spock-support), source `0cb14033dd7e1be12bfef635c762d3f2e9298b42` (2026-04-07), manifest `1.2.38` | JDK 21+, Red Hat Java classpath integration, JDT/Groovy-Eclipse compiler stack; advertised completion, diagnostics, navigation, external-library source views. Its [product assembly](https://github.com/tomaszrup/groovy-language-server-edt/blob/0cb14033dd7e1be12bfef635c762d3f2e9298b42/org.eclipse.groovy.ls.product/build.gradle) excludes the Eclipse IDE code-assist bundles and does not bundle the DSLD plugin. | Credible candidate for a compiled-Schema fixture. Its separate completion provider does not demonstrate the full Eclipse delegate inference/DSLD behavior; no native or headless KlumAST acceptance was run for this server. |
| [JulienTAHON Groovy Language Support](https://marketplace.visualstudio.com/items?itemName=JulienTAHON.groovy-vscode), source `5d5a3e87a8b4b542889b0eb272d93559a62c9106` (2026-04-29), manifest `1.2.3` | The inspected [TypeScript completion handler](https://github.com/djukxe/groovy-vscode/blob/5d5a3e87a8b4b542889b0eb272d93559a62c9106/server/src/server.ts#L538) returns a fixed keyword list; definition search uses source-text patterns. | Its feature listing is insufficient evidence for typed DSL completion or consumption of compiled generated contracts. |

## What generated contracts do and do not provide

**Verified repository facts.** [ADR 0005](../../adr/0005-generated-dsl-support-api.md) defines real generated
`Foo_DSL.Factory` and `Foo_DSL.Builder` bytecode interfaces. AnnoDocimal mirrors describe those interfaces;
mirrors are IDE metadata and must never be added to compilation, packaging, or downstream build inputs.
The [Schema plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java)
registers IDEA source/resource roots. This is not a VS Code generated-source registration mechanism.
[GeneratedDslSupport.projectDelegatesTo](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java)
projects closure metadata onto public Builder types.

**Verified external source.** Moonshine's
[CompilationUnitFactory](https://github.com/GroovyLanguageServer/groovy-language-server/blob/347d098a928707223ce44b52cc45174a6327a5f3/src/main/java/net/prominic/groovyls/config/CompilationUnitFactory.java)
walks `.groovy` sources and adds JARs through its classpath configuration. An arbitrary directory setting is not a Java
source-root attachment: that method adds JAR children of directories. Its Java `Foo_DSL` mirrors therefore do not enter
this compilation unit through the ordinary workspace scanner.

**Inference.** Compiled public contracts are the smallest plausible VS Code path, especially in a separate binary
consumer: an explicit typed receiver needs less editor inference than `Foo.Create.With { bareCall(...) }`.
Same-project Schema authoring additionally needs the AST-generated static factory field to be visible and mirror/type
resolution to agree. Merely opening a mirror cannot establish that chain. The #805 GDSL's JAR packaging also cannot
make a language server understand its dialect. Do not add mirrors to a Gradle SourceSet to obtain editor discovery.

**Unknown.** Actual KlumAST transform execution inside these servers, same-project mirror attachment, generics,
polymorphic Class-selected Builders, nested closure strategies, named-map metadata, inherited operations, and fidelity of
editor diagnostics remain untested. A server's bundled Groovy version can differ from the project's 3/4/5 lane;
project compiler success does not prove editor compiler compatibility. A type-definition LSP capability does not prove
navigation from every DSL expression or into every generated class file.

## DelegatesTo: evidence differs by editor operation

Groovy documents `@DelegatesTo` as a closure type/strategy hint consumed by static checking and potentially IDEs
([language documentation](https://docs.groovy-lang.org/docs/groovy-5.1.2/html/documentation/core-domain-specific-languages.html)).
Groovy-Eclipse itself has implementation evidence:
[VariableScope.CallAndType](https://github.com/groovy/groovy-eclipse/blob/59264ff5e65c692d622009d82eaa5e18751e8012/base/org.eclipse.jdt.groovy.core/src/org/eclipse/jdt/groovy/search/VariableScope.java#L1258)
reads annotation modes and strategies, and
[FieldCompletionTests](https://github.com/groovy/groovy-eclipse/blob/59264ff5e65c692d622009d82eaa5e18751e8012/ide-test/org.codehaus.groovy.eclipse.codeassist.test/src/org/codehaus/groovy/eclipse/codeassist/tests/FieldCompletionTests.groovy#L641)
assert delegate/owner proposals. These tests were inspected, not executed here, and are Eclipse evidence.

The TomaszRup server's
[CompletionProvider](https://github.com/tomaszrup/groovy-language-server-edt/blob/0cb14033dd7e1be12bfef635c762d3f2e9298b42/org.eclipse.groovy.ls.core/src/main/java/org/eclipse/groovy/ls/core/providers/CompletionProvider.java#L1840)
has its own identifier/type/member traversal. No call to the upstream `TypeInferencingVisitorWithRequestor`,
`VariableScope.CallAndType`, or annotation-specific delegate handling was found in its main sources.
**Inference:** its compiler lineage is not sufficient proof of Eclipse-equivalent closure completion.
This does not establish that every annotation-assisted diagnostic or expression can never work there.

groovyls explicitly
[runs static checking](https://github.com/trustytrojan/groovyls/blob/c31000bd59403ae3f14e0f5b89a273807effabcf/src/main/java/net/prominic/groovyls/GroovyServices.java#L654)
and its [definition lookup](https://github.com/trustytrojan/groovyls/blob/c31000bd59403ae3f14e0f5b89a273807effabcf/src/main/java/net/prominic/groovyls/compiler/util/GroovyASTUtils.java#L92)
consumes `DIRECT_METHOD_CALL_TARGET`. The probe confirms useful annotation-driven source navigation even while completion
fails. Thus neither “VS Code ignores DelegatesTo” nor “DelegatesTo gives VS Code DSL IntelliSense” is supported.

## Minimal headless probes performed

Disposable harnesses used each server's existing `GroovyServicesCompletionTests` setup: `CompilationUnitFactory`,
workspace root, a no-op LSP client, `didOpen`, completion, and definition requests. Final runs used JDK 21.0.9.
Groovy's runtime JAR was explicitly put on the language-server classpath so the annotation resolved.
The fixture is intentionally ordinary Groovy, with no KlumAST transformation:

```groovy
import groovy.lang.DelegatesTo
class ServiceBuilder { void title(String value) {} }
class API {
    static void configure(@DelegatesTo(value=ServiceBuilder, strategy=Closure.DELEGATE_ONLY) Closure c) {}
}
// Explicit receiver control:
ServiceBuilder builder = new ServiceBuilder()
builder.title('x')
// Separate delegated script variant:
API.configure { title('x') }
```

The two variants were opened separately. Completion used the prefix `tit` within the complete `title('x')` call,
then definition requested the method token. Source variants contained the two classes in the same source file;
binary variants compiled the classes with Groovy to a temporary JAR and supplied only that JAR plus Groovy runtime to
the server. The compiler inputs/class files were outside the script workspace. This simulates a contract shape;
it is not proof against actual generated `Foo_DSL` classes or a VS Code extension-host session.

| Server / contract input | Explicit receiver: `title` completion | Explicit receiver: definition targets | Delegated closure: `title` completion | Delegated closure: definition targets |
| --- | --- | --- | --- | --- |
| Moonshine 347d098 / source | Yes | 1, Builder method | No | 0 |
| Moonshine 347d098 / JAR only | Yes | 0 | No | 0 |
| groovyls c31000b / source | Yes | 1, Builder method | No | 1, Builder method |
| groovyls c31000b / JAR only | Yes | 0 | No | 0 |

The positive source navigation target was checked against the `ServiceBuilder.title` declaration, not merely a nonempty
response. No source attachments were supplied for the JAR control; zero definition targets does not establish that
attachment-aware/decompiling servers cannot navigate. No unresolved-annotation diagnostics occurred in the final runs.
An initial missing-classpath harness was corrected; an initial JDK 25 binary compile failed because Groovy 4.0.26 could
not read major version 69, then passed on JDK 21. These were probe setup limits, not KlumAST defects.

Reproduction command in either pinned checkout, with the temporary `Klum808ProbeTest` harness installed:
`JAVA_HOME=<JDK-21> ./gradlew test --tests net.prominic.groovyls.Klum808ProbeTest --no-daemon`.
Both final runs passed two tests, zero skips/failures. The first test records source/binary completion and definition;
the second exercises the #805 GDSL. The temporary harness/output locations are recorded in the task handoff rather than
made product test infrastructure. For a fresh harness use the pinned upstream completion-test setup and the protocol
above; `CompilationUnit.compile(Phases.OUTPUT)` produces the JAR control.

## GDSL / DSLD boundary and DelegatingScript

The exact [#805 recipe](../../user/Convenience-Factories.md#optional-intellij-completion-for-one-script-family),
merged in [PR #809](https://github.com/klum-dsl/klum-ast/pull/809), uses:

```groovy
contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('.environment.groovy'))
        delegatesTo(findClass('example.Environment_DSL.Builder'))
}
```

**Verified:** groovyls's
[JenkinsGdslParser](https://github.com/trustytrojan/groovyls/blob/c31000bd59403ae3f14e0f5b89a273807effabcf/src/main/java/net/prominic/groovyls/gdsl/JenkinsGdslParser.java)
provides a small set of bindings for extracting Jenkins methods/properties; it does not provide these PSI/Builder hooks.
The exact recipe evaluated with the fork's actual parser returned zero symbols and logged a null `place.containingFile`
error. The parser was also run from Moonshine's harness using the fork's two unchanged parser/symbol Java classes;
both runtimes gave the same outcome. This is parser-level evidence, not a native editor test.
Its [symbols manager](https://github.com/trustytrojan/groovyls/blob/c31000bd59403ae3f14e0f5b89a273807effabcf/src/main/java/net/prominic/groovyls/gdsl/GdslSymbolsManager.java)
looks at the root and one directory level and injects symbols into script classes. Neither descriptor discovery nor
filename-scoped delegate resolution matches IntelliJ's engine.

The packaged [CreateProperties](../../../klum-ast-runtime/src/gdsl/com/blackbuild/klum/ast/gdsl/CreateProperties.gdsl)
and [PolymorphicMethods](../../../klum-ast-runtime/src/gdsl/com/blackbuild/klum/ast/gdsl/PolymorphicMethods.gdsl)
helpers additionally import IntelliJ PSI classes. They cannot simply be reused by the inspected VS Code servers.
Eclipse [DSLD](https://github.com/groovy/groovy-eclipse/wiki/DSL-Descriptors) is a distinct pointcut/contribution dialect;
a filename-to-Builder adapter is plausible in Eclipse but no usable VS Code equivalent was verified.
A new server adapter would be separate design/development work, not a documentation recipe conversion.

**General language limitation:** [DelegatingScript](https://docs.groovy-lang.org/latest/html/api/groovy/util/DelegatingScript.html)
accepts an `Object` delegate assigned at runtime. A bare script body provides no concrete Builder type just by extending
that base class, and closure-parameter `@DelegatesTo` is not a script-body declaration.
**KlumAST-specific seam:** [FactoryHelper](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java)
selects the Builder from the receiving factory and calls `script.setDelegate(builder)` before execution.
KlumAST could deliberately expose a concrete script context in future, but #805's current suffix mapping supplies that
knowledge only to IntelliJ. The [#805 research](issue-805-delegating-script-ide-research.md) also explains why an abstract
script merely implementing the Builder interface is not a truthful fix. No missing Groovy feature was attributed to a
KlumAST compiler defect, and no new script API is selected here.

## Smallest possible follow-ups

1. **Optional 4.1 documentation clarification:** adopt the proposed paragraph in onboarding/FAQ after maintainer review.
   Primary use case: a Model Writer choosing VS Code needs accurate expectations. Need horizon: near-term documentation;
   workaround: existing IntelliJ integration or ordinary text editing plus Gradle validation is viable. No parity claim,
   automatic mirror registration, or extension recommendation without acceptance evidence.
2. **Bounded #808 acceptance experiment, milestone left open:** pick one exact extension package/version and a real
   operator consumer, start with a compiled Schema JAR and regular typed factory script, then compare same-project
   refreshed mirrors. Candidate: TomaszRup for classpath/source-navigation integration, or groovyls for the demonstrated
   delegated-call navigation. Neither is endorsed as supported. Primary need: determine whether regular Model editing is
   useful; bare DelegatingScript parity remains a separate question. Workaround: IntelliJ remains viable.

Use one unkeyed `@DSL Environment { String region }`, an ordinary `Environment.Create.With { region 'eu' }` script,
`catalog.environment.groovy` with `@BaseScript DelegatingScript`/bare `region 'eu'`, and a nonmatching
`catalog.other.groovy` control. Record extension ID/version/VS Code/JDK/server Groovy, dependencies and classpath,
mirror refresh/discovery, completion at `reg`, method and type navigation target, and diagnostics for a wrong method/value.
Run the actual model to check the configured value; keep editor diagnostics separate from compilation and runtime
validation. If a provider supports editor-only source attachment, prove mirrors remain excluded from compilation,
artifacts and downstream inputs. If not, record that limit rather than changing ADR 0005.
This is a proposed manual/operator fixture, not added executable infrastructure or a passed acceptance result.
No broader extension-development follow-up is justified until this small experiment identifies an actionable seam.
