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
below `build/generated/klum-json-schema`, for example `example.Deployment.schema.json`. The ordinary
`assemble`, `check` and publication lifecycles do not select generation. Filenames retain the fully qualified Model
name, so Models with identical simple names in different packages produce distinct files; no package directories are
created. Copying, packaging, publishing and editor association belong to the consumer; generation adds no artifact variant, classifier or automatic embedding.

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
unrelated files. It also removes earlier Builder-named files recorded in that manifest when Model-named files replace them;
untracked files are preserved. Keep that manifest with the output directory. Contract/projection failure preserves the
previous successful output; it must not be mistaken for a successful fresh generation. Empty root selection fails explicitly.

## Opt-in integral constraints

The default output remains structural. Select `integralConstraints` separately to add raw-input authoring bounds for
ordinary direct fields carrying Jakarta `@Min` and/or `@Max`. The supported targets are byte, short, int, long, their
boxed counterparts and BigInteger, with empty annotation groups/payload and no relevant group redefinition/sequence.
Bounds apply to supplied values and intersect the existing machine range exactly. Inherited, renamed and nested owned
properties retain their effective input names; ignored properties remain absent.

(See: `KlumJsonSchemaGenerationTest#'generate opt-in integral authoring bounds from binary Builder contracts with Groovy #generation'`.)

```groovy
// Schema source, compiled with the current KlumAST compiler:
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

@DSL class Capacity {
    /** Percentage of available capacity. */
    @Min(0L) @Max(100L) Integer percentage
}

// Schema project's build.gradle:
klumSchema.jsonSchema {
    rootTypes.add('example.Capacity')
    integralConstraints = true
}
```

The `percentage` input now has `type: integer`, `minimum: 0` and `maximum: 100`, retaining its source description.
Both endpoints, mathematical integer `100.0`, zero and omission pass. Supplied -1/101 fail the bounds; explicit null,
false, fractions, empty strings and numeric strings retain their structural rejection for this property. Independent
String/Boolean properties still admit empty strings/false. No `required` comes from `@NotNull`, `@Required` or bounds;
existing construction-key requirements and null admission are unchanged. See [validation](Validation.md) for completed
Model validation, including the separately tracked nullable investigation [#962](https://github.com/klum-dsl/klum-ast/issues/962).

This is a consumer-selected restriction on raw authoring. An external editor or validator checks the generated artifact;
KlumAST import does not automatically run JSON Schema validation. A later default, lifecycle callback or converter may
replace a supplied value, so this artifact can reject input that construction could repair and accept input that later
fails. Defaults, initializers, Templates, lifecycle code, converters and custom validators are never evaluated by generation.
Destination-field bounds do not constrain authored operation arguments. No `default` or `const` is inferred.

Only compiler-enriched public Builder contracts supply constraint facts. Recompile and package every reachable Schema,
including inherited and owned dependency Schemas, with matching compiler/runtime/plugin versions before enabling this
profile. Missing, unsupported-version, malformed or unverifiable required metadata fails generation with root/use,
public Builder, available source/external-name and artifact context plus recompile/package guidance. The generator
never reads Model bytes to fill gaps and does not downgrade incomplete enhanced output. Failure preserves the last
successful files and manifest. A contradictory intersection, including a bound outside the machine range, also fails.
Profile selection is a tracked Gradle input and uses the same filenames/output ownership as structural generation;
use separate task/output configurations if both artifacts are needed simultaneously.

Complete unsupported direct field declarations retain labelled `Runtime constraint (not enforced by this schema)`
descriptions appended to existing prose. Descriptions identify the annotation and its explicitly authored attributes;
they do not evaluate annotation defaults or interpret constraint code. Explicit groups/payload (including explicit
Default groups), composed/repeatable declarations, decimals, Size/Pattern, quantity grammars and relationship closures
receive no hard mapping in this slice. Getter/return, parameter/type-use and container-element projection, contextual
relationship/default projection and provider XML/programmatic configuration are deferred. No projection SPI is provided.
Issue [#959](https://github.com/klum-dsl/klum-ast/issues/959) remains open for these broader decisions.

## Supported input and limits

Reusable definition identities and references retain fully qualified generated Builder names internally. For example,
`example.Deployment.schema.json` retains this root reference:

```json
{"$ref": "#/$defs/example.Deployment_DSL%24Builder"}
```

The generated document uses reusable `$defs`/`$ref` for concrete owned objects, closed object properties, literal String
keys for ordinary data maps and keyed owned maps, scalar/enum values, supported field containers, and admitted authored
`@Builder.Method` operations with one payload. Inherited members and retained prose are preserved. A Map/List operation
payload remains one value. Explicit ignore, literal nonempty rename, then identity fallback determine external names.
Concrete owned relationships in compatible binary API/Schema dependencies follow the same admission rules as local
relationships, including inherited fields and keyed Map values. No alternate naming strategy is currently configurable.

Unclassified helpers and dynamic factory controls are absent. Unsupported declarations, including abstract relationships,
multi-argument operations, LINK input and unresolved payloads, are omitted with source/reason diagnostics in the
`x-klum-unsupported` annotation. Unknown properties are rejected, rather than represented by permissive placeholders.
An abstract root, an empty enum or malformed/missing required enrichment fails generation.
An admitted relationship whose required key cannot be represented also fails, even when the relationship is optional;
it is not silently omitted as unsupported. Enum names are read without invoking
`values()` or initializing the enum. No Schema constructors, factories, initializers, defaults, callbacks, converters or
annotation closures execute, including while diagnosing unsupported declarations.

An ordinary unannotated property named `key` is a normal generated property. Actual `@Key` members follow their construction position, as described below.

## Construction-key definitions

The default root artifact describes self-contained input for the three-argument `KlumStructuredInput.readRoot` overload.
A keyed root and each supplied direct caller-key child require their actual external key member with `type: string`.
This `required` comes from construction identity, independently of completed `@Required`. Optional relationship
properties remain optional; a supplied child must provide its own identity.

(See: `KlumJsonSchemaGenerationTest#'generate structural input schemas from explicit DSL Model roots with Groovy #generation'`
and `StructuredInputKeyDocumentaryTest#'self-contained documents carry root and direct-child keys while Maps supply outer keys'`.)

For the [Deployment/Service example](Structured-Input.md#construction-keys), a self-contained root document contains
`name`, a supplied `service` contains `service-id`, and `services.worker` omits `service-id` because the Map entry
supplies identity. Fixed/derived-key direct relationships, including fixed Cluster fields, also use keyless bodies.
The same Builder can therefore have two closed definitions in one document: its fully qualified Builder name for the
member-key form and a private deterministic `::keyless` suffix for the body form. References escape URI/JSON Pointer
characters, reserve each definition before recursion and reuse the appropriate shape for cyclic or mixed-position
relationships. Unkeyed types use one definition. No `allOf` extension of a closed base is used.

Inherited keys, literal renames, ignores and ordinary property admission use the same compiled facts as interpretation.
An ignored or colliding required root key fails generation. An admitted direct child requiring such a key also fails,
even if that child could be absent from a particular input. The error identifies the selected root, relationship/key
source, public Builder, key position and reason. Independently representable keyed Map or Schema-provider uses of that
type can still generate keyless bodies; a key collision does not globally suppress an ordinary body property.
Compiler-rejected operations continue to be absent with `x-klum-unsupported` diagnostics. There is no new categorical
positional exclusion or arbitrary-input fallback for a projector limitation. Failure retains previous successful output.

This default artifact does **not** describe the payload of the four-argument explicit-rootKey overload or the existing
`applyToBuilder` target: those payloads must omit their already supplied root/target key. There is no additional Gradle
mode for those entry points. See the [migration guidance](Migration.md#explicit-structured-construction-keys).

The default JSON Schema validates structure only; the opt-in profile adds the authoring bounds described above. It does not guarantee Java numeric conversion, input interpretation, binding,
lifecycle, materialization or completed validation. Integral primitives/wrappers retain exact bounds; float/double use
`number` as a structural approximation, while BigInteger/BigDecimal retain mathematical numeric semantics. Completed
`@Required` is not translated into raw-input `required`; defaults and arbitrary Groovy validation are not evaluated.
Schema key providers, state-dependent existing-child key mismatches and duplicate raw parser members are runtime/parser
concerns that structural validation cannot settle. There is no ordering, layering, merge/patch, polymorphic alias or round-trip promise. Runtime interpretation is a separate
optional [structured-input feature](Structured-Input.md), tracked under #932.

Real ScHelm root/output qualification remains a consumer-owned acceptance gate for overall
[#529](https://github.com/klum-dsl/klum-ast/issues/529). This Gradle slice selects no ScHelm roots, packaging or publication policy.
