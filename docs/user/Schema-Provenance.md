# Schema Compiler Provenance

## Generated compiler provenance

Starting with 4.2, every transformed `@DSL` Schema class, including an abstract Schema, declares a runtime-retained
`com.blackbuild.klum.ast.runtime.generated.GeneratedSchema` annotation. Its two required members are `format = 1`
(the marker encoding version) and `compilerVersion` (the exact emitting `klum-ast` artifact version). Development and
RC suffixes are preserved. Each class records its own emitter; the marker is not inherited or copied to generated
Builders, Factories, or support types. Interfaces skipped by the transformation and plain Domain API classes are unmarked.
Do not write this annotation on a Schema yourself.

Java consumers can inspect a compiled Schema with ordinary reflection:

```java
import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema;

GeneratedSchema provenance = WebService.class.getDeclaredAnnotation(GeneratedSchema.class);
if (provenance != null) {
    int format = provenance.format();
    String compilerVersion = provenance.compilerVersion();
}
```

Groovy consumers use the same declared annotation. The following Schema example is covered by
(See: `GeneratedSchemaProvenanceTest#'each transformed Schema declares its own compiler provenance'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema

@DSL abstract class Service { String name }
@DSL class WebService extends Service { int port }

assert Service.getDeclaredAnnotation(GeneratedSchema).format() == 1
assert WebService.getDeclaredAnnotation(GeneratedSchema).compilerVersion()
```

Reading a Schema JAR requires the KlumAST runtime containing the annotation, without the Klum compiler or Gradle plugin
in the consuming application. A consuming project's version, plugin, BOM, or runtime selection does not replace the
recorded producer value. Schema compilation fails with a compiler-provenance error if the executing compiler artifact
has missing, blank, or ambiguous version metadata.

An older unmarked Schema returns `null`; absence conveys no provenance fact and introduces no new runtime rejection.
The compiler version is an emitter identity, **not a minimum runtime or compatibility guarantee**. Format 1 is not an ABI
feature level. The marker performs no runtime lookup, comparison, aggregation, or compatibility check. Continue to
coordinate dependencies and inspect resolved classpaths; provenance alone does not establish a supported version range.
See [#896](https://github.com/klum-dsl/klum-ast/issues/896) for the separate compatibility work.
