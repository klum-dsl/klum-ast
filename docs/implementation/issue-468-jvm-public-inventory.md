# #468 JVM-public inventory and export handoff

This local release artifact supplies the public-surface decision that [#391](https://github.com/klum-dsl/klum-ast/issues/391) consumes. It does not choose #391's package moves or module descriptors.

## Repeatable artifact comparison

The comparison examines every public/protected top-level or nested type and every public/protected member of those types. It excludes `buildSrc`, source/Javadoc JARs, and members of non-public nested implementation types.

```shell
./gradlew :klum-ast-annotations:jar :klum-ast-runtime:jar :klum-ast:jar \
  :klum-ast-jackson:jar :klum-ast-bean-validation:jar

scripts/compare-jvm-public-api.sh /path/to/v3.0.1 /path/to/candidate /tmp/klum-api-diff
scripts/classify-jvm-public-api.sh /tmp/klum-api-diff/added-public-api.txt \
  /tmp/klum-api-diff/classified-inventory.tsv
```

At freeze candidate `57ea5b2`, the generated TSV contains 371 added exact records. Every row contains classification, owner, export treatment, 4.x status, artifact, binary type, record kind (`type` or `member`), and Java descriptor. Regenerate and review the complete TSV for each new release candidate; source visibility is not a contract decision.

## Selected contract records

| Exact type/member set | Classification and owner | Export treatment and 4.x commitment |
| --- | --- | --- |
| `KlumObjectSupport.of`, `getObject`, `getConstructionPath`, `getModelPath`, `getStructure`, `getValidation`, and public members of `Structure`/`Validation` | Client entrypoint; #390 | Export the runtime API package. Source and binary compatible throughout 4.x. `getConstructionPath(): String` is the sole construction-string getter. |
| `KlumJacksonImporter.using(ObjectMapper)`, `using(ObjectReader)`, `readRoot`, `readTemplate`, `readBuilder`, `applyToBuilder`; `KlumJacksonInput.parser/tree/map/named` | Client entrypoint; #463 | Export the Jackson API package; source and binary compatible throughout 4.x. |
| `new KlumAstModule()` and `KlumAstModule.MODULE_NAME` | Client entrypoint; Jackson integration | Export the module type. The public nested serializer/deserializer modifiers are implementation linkage, not extension points. |
| `PhaseAction`, `BuilderVisitingPhaseAction`, `ModelVisitingPhaseAction`, `InstanceValidator`, and callable public members | Extension seam; #305 / ADR 0008 | Export runtime/validation SPI packages. `BuilderVisitingPhaseAction` has a deliberate qualified linkage to `InternalKlumBuilder` for its protected visitor descriptor; it does not make `util.layer3.ModelVisitor` generally exportable. Direct `ServiceLoader<PhaseAction>` providers keep the ADR 0008 bounded transition; no general SPI follows. |
| Generated `Foo_DSL.Factory`, `Foo_DSL.Builder`, collection/cluster factory interfaces; `Foo.Create`, `Foo.Template`, and Java `Foo.Create.getAsBuilder()` / Groovy `Foo.Create.AsBuilder` | Generated hook; #394, #431, #474 | Export runtime types referenced by generated descriptors. Generated interfaces may be named in signatures, never implemented or subclassed. |
| `KlumBuilder<T>` and `KlumFactory` Builder-factory descriptors | Generated hook; #394 / #431 | Runtime API export required for emitted bytecode; compatible in 4.x after intentional 3.x-to-4.0 recompilation. |
| `FieldType.OPTIONAL_LINK` and generated relationship descriptors | Generated hook; #474 | Annotation/runtime linkage required by emitted bytecode; compatible in 4.x after recompilation. |
| `BreadcrumbCollector`, `FactoryHelper`, `PhaseDriver`, `InternalKlumBuilder`, `InternalKlumObjectSupport`, raw companions/proxies, traversal helpers, lifecycle phases, `TemplateManager.isTemplate`, and Jackson helper/modifier types | Implementation; #468 inventory, owning behavior issue where applicable | Internalize where #391 can do so. Otherwise record generated or shipped-adapter linkage as a qualified export only; none is a third-party client/extension contract. |
| Added Gradle task, extension, convention, and plugin members | Implementation; #434 | Published build-adapter linkage only. No JPMS export or 4.x Java-client compatibility commitment. |

## Generated-bytecode and language evidence

`GeneratedDslSupportSpec` compiles `JavaDslConsumer` and `StaticAsBuilderConsumer` against emitted classes, proving Java `getAsBuilder()`, static-Groovy `AsBuilder`, factory/collection/cluster interfaces, self-typed Builders, and hidden implementations. `KlumObjectSupportSpec` compiles Java and `@CompileStatic` Groovy root/subtree consumers. `KlumJacksonImporterSpec` and `JacksonImporterConsumerTest` compile Java and `@CompileStatic` Groovy consumers for every importer descriptor.

These Groovy sources compile separately in Groovy 3, 4, and 5 lanes. No AnnoDocimal mirror is a production or downstream compiler input.

## #391 positive export handoff

#391 should export only the finalized annotation, runtime, runtime-validation, and Jackson API packages required by the client-entrypoint, extension-seam, and generated-hook rows. It must not generally export `util`, `process`, proxy, companion, lifecycle, traversal, reflection, generated-support, Jackson modifier, or Gradle implementation packages. The protected `BuilderVisitingPhaseAction` descriptor is the recorded qualified runtime linkage exception; `ModelVisitor` remains internal. A qualified export is never a new third-party extension mechanism.

The intentional 3.x-to-4.0 package/recompilation break remains #391's decision. These commitments start at the 4.0 freeze and do not preserve legacy package aliases.
