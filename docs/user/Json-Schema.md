# JSON Schema generation

The Schema Gradle plugin provides opt-in Draft 2020-12 JSON Schema generation for the admitted structured-input
subset. Generation reflects compiled public Builder/Factory contracts and retained documentation without initializing
or executing Schema code. Use matching plugin, compiler and runtime versions with generated input enrichment.

## Generating files

Apply `com.blackbuild.klum-ast-schema` as described in [Gradle Plugins](Gradle-Plugins.md). Select roots by fully qualified DSL Model binary name,
consistent with GDSL export. `rootTypes` is a `SetProperty<String>`; use strings rather than class literals so build
configuration does not load Model classes. Several roots are supported. The task resolves each Model name to its generated
Builder contract internally and verifies the contract's Model identity through `CompiledBuilderSchema`, without inspecting
or initializing the Model implementation. Generated Builder names are rejected as root configuration values, with guidance
to use the corresponding Model name.

(See: `KlumJsonSchemaGenerationTest#'generate structural input schemas from explicit DSL Model roots with Groovy #generation'`.)

```groovy
klumSchema.jsonSchema {
    rootTypes.add('example.Deployment')
    rootTypes.add('example.Service')
}
```

Run `./gradlew generateKlumJsonSchema` explicitly. The task compiles local classes as needed and writes one file per root
below `build/generated/klum-json-schema`, for example `example.Deployment_DSL$Builder.schema.json`. The ordinary
`assemble`, `check` and publication lifecycles do not select generation. Copying, packaging, publishing and editor
association belong to the consumer; generation adds no artifact variant, classifier or automatic embedding.

The classpath defaults to the main runtime classpath plus the convention's selected Groovy configuration. It includes
local compiled classes, declared API/Schema dependencies, Klum runtime/annotation libraries and embedded documentation.
Sources and IDE mirrors are not extraction inputs. For a binary-only root, declare its dependency normally or supply a
complete, explicitly resolved classpath:

```groovy
configurations { schemaBinary }
dependencies { schemaBinary 'org.example:deployment-schema:1.0' }
klumSchema.jsonSchema {
    rootTypes.add('example.Deployment')
    classpath.setFrom(configurations.schemaBinary, configurations.runtimeClasspath, configurations.groovy)
    outputDirectory.set(layout.buildDirectory.dir('generated/deployment-input-schema'))
}
```

The isolated loader uses only the tool implementation and configured classpath. Missing roots, runtime dependencies or
generated enrichment fail with root/classpath and recompile guidance; the Gradle daemon supplies no fallback classes.
Use a dedicated output directory. Gradle fingerprints roots, full classpath bytes (including annotation/documentation
changes), tool bytes/version and output location. Up-to-date, build-cache and configuration-cache reuse are supported.
Successful regeneration removes files for deleted roots using its `.klum-json-schema-outputs` manifest, preserving
unrelated files. Keep that manifest with the output directory. Contract/projection failure preserves the previous
successful output; it must not be mistaken for a successful fresh generation. Empty root selection fails explicitly.

## Supported input and limits

Reusable definition identities and references retain fully qualified generated Builder names internally.
The generated document uses reusable `$defs`/`$ref` for concrete owned objects, closed object properties, literal String
keys for ordinary data maps and keyed owned maps, scalar/enum values, supported field containers, and admitted authored
`@Builder.Method` operations with one payload. Inherited members and retained prose are preserved. A Map/List operation
payload remains one value. Explicit ignore, literal nonempty rename, then identity fallback determine external names.
No alternate naming strategy is currently configurable.

Unclassified helpers and dynamic factory controls are absent. Unsupported declarations, including abstract relationships,
multi-argument operations, LINK input and unresolved payloads, are omitted with source/reason diagnostics in the
`x-klum-unsupported` annotation. Unknown properties are rejected, rather than represented by permissive placeholders.
An abstract root, an empty enum or malformed/missing enrichment fails generation. Enum names are read without invoking
`values()` or initializing the enum. No Schema constructors, factories, initializers, defaults, callbacks, converters or
annotation closures execute, including while diagnosing unsupported declarations.

JSON Schema validates structure only. It does not guarantee Java numeric conversion, input interpretation, binding,
lifecycle, materialization or completed validation. Integral primitives/wrappers retain exact bounds; float/double use
`number` as a structural approximation, while BigInteger/BigDecimal retain mathematical numeric semantics. Completed
`@Required` is not translated into raw-input `required`; defaults and arbitrary Groovy validation are not evaluated.
There is no ordering, layering, merge/patch, polymorphic alias or round-trip promise. Runtime interpretation is a separate
optional [structured-input feature](Structured-Input.md), tracked under #932.

Real ScHelm root/output qualification remains a consumer-owned acceptance gate for overall
[#529](https://github.com/klum-dsl/klum-ast/issues/529). This Gradle slice selects no ScHelm roots, packaging or publication policy.
