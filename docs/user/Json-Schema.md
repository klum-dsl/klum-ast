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

## Type naming profiles

Schema `@InputNaming(InputNaming.Profile.KEBAB_CASE)` selects the same resolved member names used by
[`KlumStructuredInput`](Structured-Input.md#type-naming-profiles). No task option or runtime overload selects naming.
`IDENTITY` preserves native spelling and explicitly binds descendants too. First-child introduction, compatible repetition,
conflicting choices, exact Jackson literals/exclusions and independently resolved owned types follow that shared contract.

(See: `KlumJsonSchemaGenerationTest#'resolve input naming from source-free hierarchy contracts with Groovy #generation'`.)

```groovy
@DSL class ServiceBase { String displayName }
@InputNaming(InputNaming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase { String serviceName }
// Generated closed properties: display-name, service-name.
// Standalone ServiceBase still has displayName.
```

Properties include eligible authored-operation tokens and self-contained construction-key tokens. Data and instance keys
stay literal; owned definitions use the owned type's hierarchy rather than the owner's profile. Literal nonempty
`@InputName` or compatible `@JsonProperty` names override the profile; `@InputIgnore`/`@JsonIgnore` exclusions remain effective. The generator and interpreter share one resolver over public Builder facts;
Model bytes, mapper strategies and annotation proxies do not provide naming facts.

Conflicting explicit profiles, nonidentity member collisions and malformed/incomplete required metadata fail before
schema publication, preserving previous successful output. An ignored/colliding required key still makes member-key
positions unrepresentable; keyless definitions retain existing behavior. Properly compiled no-profile/identity Schemas
retain their native output. Recompile participating hierarchies and owned artifacts with the supported protocol; see
[migration](Migration.md#structured-input-metadata-protocol).

## Literal names and exclusion

`@InputName` and `@InputIgnore` share the interpreter's [literal contract](Structured-Input.md#literal-names-and-exclusion).
Exact literals name eligible fields, construction-key members and admitted authored operations; ignored entries are absent
from closed object properties. Equal Jackson literals coalesce, while contradictory names fail even on ignored sources.
Neither annotation widens admission or changes runtime validation. Owned values resolve their own names; data Map keys
and keyed-instance identities remain literal.

(See: `KlumJsonSchemaGenerationTest#'project literal names and exclusions from source-free artifacts with Groovy #generation'`.)

```groovy
@DSL class ServiceBase {
    @Key @InputName('Service/ID') String serviceId
    @InputName('URL/exact') String URLValue
    String urlValue
    @InputIgnore String hiddenValue
}
@InputNaming(InputNaming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase {}
// Closed properties: Service/ID (required), URL/exact, url-value.
```

A literal can separate profile-colliding declarations; remaining nonidentity collisions fail before artifact replacement.
An ignored required key still makes a member-key position unrepresentable; externally keyed owned Map bodies remain keyless.
The compiler freezes exact names, exclusion and origin on declaring public Builder contracts. Both consumers require the
same supported metadata protocol and complete facts. Missing, unknown, inconsistent or malformed facts fail with
Schema/Builder, artifact and recompilation guidance, preserving prior successful output. Older contracts are rejected;
no Model-byte fallback or Schema-code execution fills missing metadata. See
[migration](Migration.md#structured-input-metadata-protocol).

## Deprecation

Generation emits Draft 2020-12 `deprecated: true` by default for an admitted input member whose Schema source
field, construction key or authored `@Builder.Method` declaration carries `java.lang.Deprecated`. This advisory
annotation has no deprecation switch.

(See: `KlumJsonSchemaGenerationTest#'deprecated Schema members remain admitted with advisory metadata by default with Groovy #generation'`.)

```groovy
@DSL
class Service {
    @Deprecated String oldEndpoint
    String endpoint
}
// The oldEndpoint property has type: string and deprecated: true.
// The endpoint property has type: string.
```

Inherited and explicitly renamed members retain their original source association. Ignored, excluded and colliding
members follow the existing admission rules. A deprecated relationship marks its property use beside `$ref`, or the
outer keyed Map property; ordinary sibling uses and shared child definitions receive no inherited deprecation.
A deprecated construction key is marked only where its actual key property is present; keyless bodies gain no key.
Existing JavaDoc descriptions are preserved.

The keyword does not reject old input, require a replacement, change requiredness/null admission or invoke runtime
validation. It changes no defaults, converters, lifecycle or migration behavior. Type-level deprecation and deprecated
framework aliases such as TemplateScope `Create`/`CreateFrom` do not mark admitted members. `since` and `forRemoval`
are not interpreted.

Current compilers retain required explicit true/false source-deprecation facts on public Builder contracts. Missing
facts reject the contract under the shared protocol, even though the generated keyword itself is advisory. Generation
never guesses provenance from an alias or reads Model bytes. Recompile participating Schema artifacts with a matching
toolchain; numeric assertions and deprecation output retain their independent functional semantics.

## Field constraint projection

`@ConstraintProjection` selects the artifact policy for one Schema field's captured effective runtime constraints.
Its nested `ConstraintProjection.Policy` enum has three values:

| Policy | Supported hard assertions | Constraint descriptions and source attribution |
| --- | --- | --- |
| `PROJECT` (default, also when the annotation is absent) | Project faithfully | Keep fallback descriptions for unsupported forms |
| `TEXT_ONLY` | Omit | Describe every captured constraint as a runtime constraint not enforced by this schema |
| `OMIT` | Omit | Omit |

(See: `KlumJsonSchemaGenerationTest#'choose field constraint projection without changing structural facts with Groovy #generation'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.ConstraintProjection
import com.blackbuild.klum.ast.ConstraintProjection.Policy
import jakarta.validation.constraints.Min

@DSL class Capacity {
    /** Desired replica count. */
    @Deprecated @Min(1L)
    @ConstraintProjection(Policy.TEXT_ONLY) Integer replicas

    /** Private capacity rule. */
    @Deprecated @Min(1L)
    @ConstraintProjection(Policy.OMIT) Integer omitted
}
```

Both properties keep integer types, machine limits, ordinary prose and deprecation. `replicas` additionally describes
`@Min` as a runtime constraint; `omitted` has only its ordinary prose. Neither gains the constraint's `minimum: 1`
assertion. Normal input admission, naming/exclusion, construction keys, null/omission, defaults, lifecycle and runtime
validation stay unchanged. This annotation does not make a failing runtime Model valid.

The field's declaring source owns the policy, including inherited and externally renamed members. A replacement
Schema declaration uses its own policy, defaulting to `PROJECT`; there is no separate inherited override switch.
A relationship policy covers that field's constraint contribution only: it never erases a referenced child's own
constraints or structural shape. Every captured constraint on the field follows the selected policy; there is no
per-annotation selector. Future supported contributions will follow the same field policy.

Approved one-hop `@PropagateAnnotation` contributions are captured after propagation and obey the same rules as direct
annotations. For example, propagated and direct `@NotNull` produce the same constraint description under `TEXT_ONLY`,
and neither produces one under `OMIT`. Propagation uses defaults only, so it cannot supply Min/Max or DecimalMin/Max's
required values. This adds no hard mappings for arbitrary composed, repeatable, getter, type-use or container-element
constraints. Unsupported captured forms remain descriptive under `PROJECT` and `TEXT_ONLY`.

Recompile and package every declaring/inherited/owned Schema with matching compiler/runtime/plugin versions. The
generator requires explicit policy facts even for `PROJECT` and `OMIT`; old, missing, incompatible or malformed facts
fail before replacing outputs. Suppression does not waive validation of supported numeric facts. No Model-byte fallback,
Schema execution, required Jackson/Bean Validation adapter or global projection switch is introduced. See
[migration](Migration.md#field-constraint-projection) and [issue #959](https://github.com/klum-dsl/klum-ast/issues/959).

## Integral constraints

Generation automatically adds raw-input authoring bounds for ordinary direct fields carrying Jakarta `@Min` and/or
`@Max`. No global constraint flag or profile selector is required. The supported targets are byte, short, int, long, their
boxed counterparts and BigInteger, with empty annotation groups/payload and no relevant group redefinition/sequence.
Bounds apply to supplied values and intersect the existing machine range exactly. Inherited, renamed and nested owned
properties retain their effective input names; ignored properties remain absent.

(See: `KlumJsonSchemaGenerationTest#'generate integral authoring bounds by default from binary Builder contracts with Groovy #generation'`.)

```groovy
// Schema source, compiled with the current KlumAST compiler:
package example

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
}
```

The `percentage` input retains `type: integer` and its machine bounds, with conjunctive `minimum: 0` and `maximum: 100`
assertions in `allOf`, retaining its source description.
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
including inherited and owned dependency Schemas, with matching compiler/runtime/plugin versions before generation. Missing, unsupported-version, malformed or unverifiable required metadata fails generation with root/use,
public Builder, available source/external-name and artifact context plus recompile/package guidance. The generator
never reads Model bytes to fill gaps and does not downgrade incomplete output. Failure preserves the last
successful files and manifest. Supported numeric constraints are projected faithfully, including mutually contradictory
bounds. JSON Schema generation does not perform numeric-domain satisfiability analysis.
Root selection, binary inputs and output ownership retain the existing tracked Gradle task contract.

Complete unsupported direct field declarations retain labelled `Runtime constraint (not enforced by this schema)`
descriptions appended to existing prose. Descriptions identify the annotation and its explicitly authored attributes;
they do not evaluate annotation defaults or interpret constraint code. Explicit groups/payload (including explicit
Default groups), composed/repeatable declarations, Size/Pattern, quantity grammars and relationship closures
receive no hard mapping in this slice. Getter/return, parameter/type-use and container-element projection, contextual
relationship/default projection and provider XML/programmatic configuration are deferred. No projection SPI is provided.
Issue [#959](https://github.com/klum-dsl/klum-ast/issues/959) remains open for these broader decisions.

## Decimal constraints

Generation automatically projects exact direct Jakarta `@DecimalMin`/`@DecimalMax` bounds on ordinary `BigDecimal`
and supported integral scalar properties. Direct integral Min/Max restrictions conjoin with decimal bounds on integral
targets. BigDecimal Min/Max declarations remain runtime descriptions, preserving the supported numeric-type subset.
No global numeric flag or profile selector is provided.

(See: `KlumJsonSchemaGenerationTest#'generate exact direct decimal authoring bounds by default from source-free binaries with Groovy #generation'`.)

```groovy
@DSL
class Price {
    @DecimalMin(value = '0.10', inclusive = false)
    @DecimalMax('9007199254740993.0000000001')
    BigDecimal amount
}

klumSchema.jsonSchema {
    rootTypes.add('example.Price')
}
// amount: type number, exclusiveMinimum 0.10, maximum 9007199254740993.0000000001
```

Inclusive bounds use numeric `minimum`/`maximum`; exclusive bounds use Draft 2020-12 numeric
`exclusiveMinimum`/`exclusiveMaximum`. Parsing and JSON rendering preserve exact mathematical decimal
values, including exponent, leading-plus and zero spellings, without conversion to double. JSON text need not retain
its authored scale/spelling. Consumers must use precision-capable JSON parsers and validators to retain this guarantee;
JavaScript Number and other binary floating representations may round these tokens.

Each declared bound and the structural integer type/machine restrictions remain conjunctive. Independent keywords
coexist directly; repeated numeric keywords use `allOf`. Contradictory bounds, equal exclusive endpoints and fractional
intervals containing no integer generate valid schemas that reject supplied values for that property. Optional properties
remain omittable. Generation performs no numeric satisfiability analysis. Invalid decimal values and unrepresentable
exponent/scale still fail before replacing previous output, with source, external-name and public-contract diagnostics.
Neither bounds nor contradictions infer raw requiredness.

Only one effective direct declaration of each bound, empty groups/payload and no relevant hierarchy group redefinition
are supported. String, float/double/Number, composed/repeatable, type-use, getter/parameter and authored-operation
arguments receive no decimal assertions. All original requiredness, null admission, interpreter, Jackson, defaults,
Templates and lifecycle behavior remains unchanged. This is a supplied-input authoring policy, with no Bean Validation
equivalence or final construction-success promise.

The matching supported metadata protocol and complete explicit numeric facts are required on root, declaring/inherited
and participating owned contracts. Missing, malformed or incompatible facts fail atomically with Schema/Builder,
artifact and recompilation guidance. Align compiler, runtime and Gradle plugin versions and recompile participating
Schema dependencies. This does not change numeric assertion semantics.

## Supported input and limits

Reusable definition identities and references retain fully qualified generated Builder names internally. For example,
`example.Deployment.schema.json` retains this root reference:

```json
{"$ref": "#/$defs/example.Deployment_DSL%24Builder"}
```

The generated document uses reusable `$defs`/`$ref` for concrete owned objects, closed object properties, literal String
keys for ordinary data maps and keyed owned maps, scalar/enum values, supported field containers, and admitted authored
`@Builder.Method` operations with one payload. Inherited members and retained prose are preserved. A Map/List operation
payload remains one value. Exclusion, compatible literal rename, type profile and identity fallback determine external names.
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

Generated JSON Schema preserves structural restrictions and projects the supported authoring bounds described above. It does not guarantee Java numeric conversion, input interpretation, binding,
lifecycle, materialization or completed validation. Integral primitives/wrappers retain exact bounds; float/double use
`number` as a structural approximation, while BigInteger/BigDecimal retain mathematical numeric semantics. Completed
`@Required` is not translated into raw-input `required`; defaults and arbitrary Groovy validation are not evaluated.
Schema key providers, state-dependent existing-child key mismatches and duplicate raw parser members are runtime/parser
concerns that structural validation cannot settle. There is no ordering, layering, merge/patch, polymorphic alias or round-trip promise. Runtime interpretation is a separate
optional [structured-input feature](Structured-Input.md), tracked under #932.

Real ScHelm root/output qualification remains a consumer-owned acceptance gate for overall
[#529](https://github.com/klum-dsl/klum-ast/issues/529). This Gradle slice selects no ScHelm roots, packaging or publication policy.


## Advisory input examples

`@InputExamples` provides documentary strings. They are emitted as JSON Schema string examples, without parsing or
validation against the property type or runtime constraints.

(See: `KlumJsonSchemaGenerationTest#'project advisory examples at exact source-free input positions with Groovy #generation'`.)

```groovy
@DSL class Service {
    @InputExamples(['localhost', 'database.internal']) String host
    @InputExamples(['42', 'many replicas']) int replicas
    @InputExamples(['API endpoint settings'])
    @Builder.Method void endpoint(Map<String, String> input) { /* configure Builder state */ }
}
```

The `host` property receives `examples: ["localhost", "database.internal"]`. The numeric `replicas` property receives
`examples: ["42", "many replicas"]`: both values are strings, with no promise that they are valid instances of that
property. Text such as `null`, `{not JSON`, quotes, backslashes, newlines and Unicode remains documentation; the ordinary
JSON Schema serializer escapes it. Order, duplicate strings and empty strings are retained. An empty annotation array
supplies no examples. Values must be compile-time constant strings, and no authored code is executed to obtain them.

| Declaration | Placement |
| --- | --- |
| Eligible ordinary field, including List/Map | Exact effective property, rather than its items or values |
| Selected construction key | Actual member-key property; absent from keyless bodies and outer instance keys |
| Admitted authored one-payload `@Builder.Method` | Exact operation payload |
| Owned relationship or keyed owned Map | Parent relationship-use property, alongside its reference/container schema |

Inherited examples retain their effective source declaration. Naming profiles, literal names and exclusions select the
same input member as before. Parent relationship notes stay local to that use; they never enter shared child definitions
or sibling uses. An owned type retains documentation on its own members. `ConstraintProjection`, ordinary prose and
`deprecated` remain independent. Unsupported members gain no admission.

Examples supply no defaults, change no requiredness or runtime input types, and do not participate in validation,
conversion, templates or lifecycle. There is no content grammar or example evaluator. Ordinary annotation/JVM constraints
apply; excessively large carried documentation is rejected defensively. The source-free public Builder metadata remains
authoritative, and malformed or missing required metadata fails before replacing successful output. The unified
[development metadata protocol](Migration.md#structured-input-metadata-protocol) remains version 1.
