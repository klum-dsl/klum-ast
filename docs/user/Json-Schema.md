# JSON Schema generation

The Schema Gradle plugin provides opt-in Draft 2020-12 JSON Schema generation for the admitted structured-input
subset. Generation reflects compiled public Builder/Factory contracts and retained documentation without initializing
or executing Schema code. Use matching plugin, compiler and runtime versions with generated input enrichment.

Start with the [Groovy/YAML adoption recipe](Structured-Input.md#adopt-yaml-for-a-schema): caller code parses YAML,
then `Service.Create.FromStruct(document)` constructs the Model through its ordinary lifecycle. Generate a schema from
that same compiled Schema for editor feedback or an explicitly invoked validator. Neither generation nor `FromStruct`
selects a parser, opens input files or automatically runs JSON Schema validation.

## Generating files

Apply `com.blackbuild.klum-ast-schema` as described in [Gradle Plugins](Gradle-Plugins.md). Select roots by fully qualified DSL Model binary name,
consistent with GDSL export. `rootTypes` is a `SetProperty<String>`; use strings rather than class literals so build
configuration does not load Model classes. Several roots are supported. The task resolves each Model name to its generated
Builder contract internally and verifies the contract's Model identity through the shared internal input-contract resolver, without inspecting
or initializing the Model implementation. Generated Builder names are rejected as root configuration values, with guidance
to use the corresponding Model name.

(See: `KlumJsonSchemaGenerationTest#'generate structural input schemas from explicit DSL Model roots with Groovy #generation'`.)

```groovy
klumSchema.jsonSchema {
    rootTypes.add('example.Service')
}
```

Run `./gradlew generateKlumJsonSchema` explicitly. The task compiles local classes as needed and writes one file per selected root purpose
below `build/generated/klum-json-schema`, for example `example.Service.schema.json`. The ordinary
`assemble`, `check` and publication lifecycles do not select generation. Filenames retain the fully qualified Model
name, so Models with identical simple names in different packages produce distinct files; no package directories are
created. For the adoption example, associate `example.Service.schema.json` with `service.yaml` in your YAML editor. This
self-contained document schema requires the external `service-id` key. If caller code supplies the root key separately,
select the [Builder-body schema](#caller-pre-keyed-builder-bodies) for that keyless input instead. Copying, packaging,
publishing and editor association belong to the consumer; generation adds no artifact variant, classifier or automatic embedding.

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
Use a dedicated output directory. Gradle fingerprints roots by purpose, full classpath bytes (including annotation/documentation
changes), tool bytes/version and output location. Up-to-date, build-cache and configuration-cache reuse are supported.
Successful regeneration removes files for deleted roots using its `.klum-json-schema-outputs` manifest, preserving
unrelated files. It also removes earlier Builder-named files recorded in that manifest when Model-named files replace them;
untracked files are preserved. Keep that manifest with the output directory. Contract/projection failure preserves the
previous successful output; it must not be mistaken for a successful fresh generation. An empty selection across both root sets fails explicitly.

## Authoring structured YAML in an editor

For a Model Writer authoring [structured input](Structured-Input.md), the generated Draft 2020-12 JSON Schema is
editor/validator input for completion and structural diagnostics. After configuring `example.Deployment` as a root
[above](#generating-files), run:

```shell
./gradlew generateKlumJsonSchema
```

The default output is `build/generated/klum-json-schema/example.Deployment.schema.json`. Normally regenerate it after
Schema changes; generation, output distribution and editor association are consumer-managed.

With [YAML Language Server](https://github.com/redhat-developer/yaml-language-server#using-a-modeline), including VS Code
with the [YAML extension](https://github.com/redhat-developer/vscode-yaml#associating-schemas), add this comment at the top
of `config/deployment.yaml`:

```yaml
# yaml-language-server: $schema=../build/generated/klum-json-schema/example.Deployment.schema.json
```

Relative modeline paths resolve from the YAML document. Here `config/` and `build/` are siblings under the project root.
The comment is parser-inert: it does not enter the parsed Map. A literal top-level `$schema:` mapping entry is **not a
KlumAST input convention**: it becomes input data seen by structured import and can be rejected as an unknown operation.

Alternatively, configure the Language Server's `yaml.schemas` workspace setting. In a single-folder VS Code workspace
opened at the project root, put this in `.vscode/settings.json` (merge it with existing settings):

```json
{
  "yaml.schemas": {
    "./build/generated/klum-json-schema/example.Deployment.schema.json": ["config/deployment.yaml"]
  }
}
```

This mapping leaves the YAML document untouched. Its relative schema path resolves from the workspace root; adapt the
file patterns to your inputs. See [workspace schema mappings](https://github.com/redhat-developer/vscode-yaml#using-yamlschemas).

In [IntelliJ IDEA](https://www.jetbrains.com/help/idea/json.html#ws_json_schema_add_custom), open **Settings | Languages &
Frameworks | Schemas and DTDs | JSON Schema Mappings**, add a mapping, select the generated schema file and Draft 2020-12,
then associate the target YAML file or file pattern. The [YAML editor](https://www.jetbrains.com/help/idea/yaml.html)
uses that mapping for completion and inspections.

JSON Schema validation is deliberately editor/consumer-owned. KlumAST `FromStruct` / `applyStruct` do **not** automatically
validate against the generated schema and must remain independent of that validation. A consumer may explicitly validate
parsed input before importing it if it wants that gate. Structural validation does not replace KlumAST construction or
completed Model validation: structured import still invokes the admitted Builder language and normal construction
lifecycle. See [runtime order and failure](#runtime-order-and-failure) for limits that a schema cannot predict.

## Raw-input field presence

Use `@Struct.Required` on an already admitted Schema field to require its resolved member name in generated JSON
Schema. This is an explicit **input member presence policy** ([#1055](https://github.com/klum-dsl/klum-ast/issues/1055)).
It is independent of completed-Model `@Required` and Jakarta Bean Validation. Neither those constraints nor a default,
initializer, Template or lifecycle callback implies raw-input presence.

A domain annotation may carry the marker, including through another domain annotation. Direct and effective domain
markers contribute the same field fact through Schema annotation composition; they add no runtime validator.

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Struct.Required
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
@interface AuthorValue {}

@DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
class AuthoringService {
    @Struct.Required @Struct.Name('service-name') String serviceName = 'local'
    @AuthorValue boolean publicAccess
}

// Both ordinary construction paths still accept omission:
assert AuthoringService.Create.FromStruct([:]).serviceName == 'local'
assert !AuthoringService.Create.With { }.publicAccess
// Empty String and false are present values:
assert AuthoringService.Create.FromStruct(['service-name': '', 'public-access': false]).serviceName == ''
```

The containing definition has `required: ['public-access', 'service-name']`. An external Draft 2020-12 validator rejects
`{}` and accepts `{"service-name": "", "public-access": false}`. `FromStruct` and active Builder `applyStruct` do not run
that validator or enforce this presence policy. A consumer may explicitly validate parsed input before construction;
its editor can report omission during authoring. Supplying null satisfies presence alone, but each unchanged property
schema still determines whether null is allowed. This example's String and boolean properties reject null externally;
normal Struct null admission and ordered execution remain unchanged.

Inherited fields retain the declaring field's policy, including across compiled parent JARs. A descendant naming profile
resolves native inherited names, while a nonempty `@Struct.Name` or compatible Jackson literal name wins. Only the final
external token enters `required`; native aliases do not. Field shadowing remains rejected; there is no clear/override
policy. Domain markers on Builder methods do not make operations required, and direct `@Struct.Required` cannot target
a method, parameter or type. It does not admit a new field or operation grammar.

`@Struct.Ignore` and this marker conflict. Effective Jackson exclusion, Owner/key/transient/internal fields and other
non-admitted declarations cannot produce phantom required properties. Compilation rejects invalid declarations;
malformed or contradictory binary facts fail contract verification, including when an authored operation displaces the
required field. Recompile the participating Schema artifacts rather than relying on an ignored declaration.

Construction keys keep their separate identity rules. Self-contained roots, direct keyed children and keyed sequence
items retain their required key; caller-pre-keyed Builder bodies and outer-keyed Map/fixed/derived child bodies omit that
key. Field-presence requirements remain in either position. The containing object's array is the sorted, deduplicated
union of its construction key (where applicable) and marked field tokens; an empty array is omitted.

A marker on an owned relationship requires that member in its containing object. A marker inside the child requires only
the child's field when that child body is supplied. It does not require an optional parent relationship. Requirements
stay in each child's definition, including shared definitions; they add no collection cardinality, literal-alternative
alias or Map data-key requirements. `ConstraintProjection.PROJECT`, `TEXT_ONLY` and `OMIT` do not suppress presence or
alter its meaning. Defaults, lifecycle, Jackson mapping and runtime/Jakarta validation retain their existing behavior.

Executable documentary coverage:
`KlumJsonSchemaGenerationTest#'documents raw-input presence without enforcing import with Groovy #generation'`
and its external Draft 2020-12 counterpart `validate_required_schema.py` cover the exact generated example with
Groovy 3/4/5, omitted import/DSL/active Builder input, and external omission rejection. The Schema example uses an
independently compiled domain annotation. See [recompilation guidance](Migration.md#raw-input-presence-and-the-42-contract).

## Zero-argument commands

Admitted [zero-argument Builder commands](Structured-Input.md#zero-argument-commands) project as closed empty objects:

```json
{"type": "object", "properties": {}, "additionalProperties": false}
```

This accepts `{}` and rejects null, arrays, scalars and objects with members. The command is optional unless supplied;
projection adds no default or implicit reset/clear behavior. Generation reads its exact compiled role/signature and retained
prose without invoking the command or initializing the Schema. Inherited names and exclusions use the shared resolver.
The packaged `CompiledConverterJsonSchemaTest` String/command matrix checks source-free parent/descendant artifacts
produced with Groovy 3, 4 and 5, including throwing command-body and Schema-initialization controls.

## Type naming profiles

Schema `@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)` selects the same resolved member names used by
[structured input](Structured-Input.md#type-naming-profiles). No task option or runtime overload selects naming.
`IDENTITY` preserves native spelling and explicitly binds descendants too. First-child introduction, compatible repetition,
conflicting choices, exact Jackson literals/exclusions and independently resolved owned types follow that shared contract.

(See: `KlumJsonSchemaGenerationTest#'resolve input naming from source-free hierarchy contracts with Groovy #generation'`.)

```groovy
@DSL class ServiceBase { String displayName }
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase { String serviceName }
// Generated closed properties: display-name, service-name.
// Standalone ServiceBase still has displayName.
```

Properties include eligible authored-operation tokens and self-contained construction-key tokens. Data and instance keys
stay literal; owned definitions use the owned type's hierarchy rather than the owner's profile. Literal nonempty
`@Struct.Name` or compatible `@JsonProperty` names override the profile; `@Struct.Ignore`/`@JsonIgnore` exclusions remain effective. The generator and interpreter share one resolver over public Builder facts;
Model bytes, mapper strategies and annotation proxies do not provide naming facts.

Conflicting explicit profiles, unresolved nonidentity member collisions and malformed/incomplete required metadata fail before
schema publication, preserving previous successful output. An ignored/colliding required key still makes member-key
positions unrepresentable; keyless definitions retain existing behavior. Properly compiled no-profile/identity Schemas
retain their native output. Recompile participating hierarchies and owned artifacts with the supported protocol; see
[migration](Migration.md#structured-input-metadata-protocol).

## Literal names and exclusion

`@Struct.Name` and `@Struct.Ignore` share the interpreter's [literal contract](Structured-Input.md#literal-names-and-exclusion).
Exact literals name eligible fields, construction-key members and admitted authored operations; ignored entries are absent
from closed object properties. Equal Jackson literals coalesce, while contradictory names fail even on ignored sources.
Neither annotation widens admission or changes runtime validation. Owned values resolve their own names; data Map keys
and keyed-instance identities remain literal.

(See: `KlumJsonSchemaGenerationTest#'project literal names and exclusions from source-free artifacts with Groovy #generation'`.)

```groovy
@DSL class ServiceBase {
    @Key @Struct.Name('Service/ID') String serviceId
    @Struct.Name('URL/exact') String URLValue
    String urlValue
    @Struct.Ignore String hiddenValue
}
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase {}
// Closed properties: Service/ID (required), URL/exact, url-value.
```

A literal can separate profile-colliding declarations. [String operation selection](Structured-Input.md#select-a-string-operation)
can select one eligible marked operation; remaining nonidentity collisions fail before artifact replacement.
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

`@Struct.ConstraintProjection` selects the artifact policy for one Schema field's captured effective runtime constraints.
Its nested `Struct.ConstraintProjection.Policy` enum has three values:

| Policy | Supported hard assertions | Constraint descriptions and source attribution |
| --- | --- | --- |
| `PROJECT` (default, also when the annotation is absent) | Project faithfully | Keep fallback descriptions for unsupported forms |
| `TEXT_ONLY` | Omit | Describe every captured constraint as a runtime constraint not enforced by this schema |
| `OMIT` | Omit | Omit |

(See: `KlumJsonSchemaGenerationTest#'choose field constraint projection without changing structural facts with Groovy #generation'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct
import com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy
import jakarta.validation.constraints.Min

@DSL class Capacity {
    /** Desired replica count. */
    @Deprecated @Min(1L)
    @Struct.ConstraintProjection(Policy.TEXT_ONLY) Integer replicas

    /** Private capacity rule. */
    @Deprecated @Min(1L)
    @Struct.ConstraintProjection(Policy.OMIT) Integer omitted
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
required values. The bounded first-level composed subset below is supported; arbitrary nested, repeatable, getter, type-use or container-element
constraints. Unsupported captured forms remain descriptive under `PROJECT` and `TEXT_ONLY`.

Recompile and package every declaring/inherited/owned Schema with matching compiler/runtime/plugin versions. The
generator requires explicit policy facts even for `PROJECT` and `OMIT`; old, missing, incompatible or malformed facts
fail before replacing outputs. Suppression does not waive validation of supported numeric or Pattern facts. No Model-byte fallback,
Schema execution, required Jackson/Bean Validation adapter or global projection switch is introduced. See
[migration](Migration.md#field-constraint-projection) and [issue #959](https://github.com/klum-dsl/klum-ast/issues/959).

## Conservative Pattern projection

A direct singleton `jakarta.validation.constraints.Pattern` on an admitted ordinary `String` field projects automatically
under `Struct.ConstraintProjection.Policy.PROJECT` when it belongs to the finite `ASCII-BOUNDED-1` subset. This supports bounded
identifiers and codes. It requires empty groups, payload and flags and no hierarchy group sequence/redefinition/provider.

(See: `KlumJsonSchemaGenerationTest#'project bounded release identifiers from source-free artifacts with Groovy #generation'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import jakarta.validation.constraints.Pattern

@DSL class Release {
    /** Release slug. */
    @Pattern(regexp = '[a-z][a-z0-9-]{0,62}') String slug
}
// slug: type string, allOf [pattern ^[a-z][a-z0-9-]{0,62}$,
//                         not {type string, pattern [^ -~]}].
```

The grammar accepts concatenated ASCII letters, digits, `_`, `-`, and positive classes containing letters, digits,
`_`, `.`, the exact ranges `A-Z`, `a-z`, `0-9`, and an optional final literal hyphen. For example `[.]`, `[-]` and
`[A-Za-z0-9_.-]` qualify. Each atom may use `?`, `{n}` or `{m,n}`. Bounds are decimal 0..255 with no leading zeros;
lower must not exceed upper. The decoded source is 1..256 UTF-16 units, the body has 1..32 pieces, and summed maximum
repetitions must not exceed 1024. `{0}` is allowed. Optional outer anchors must be paired `^...$`; the body cannot be empty.
No trimming or normalization occurs.

The generated assertion is anchored and conjoined with `not: {type: string, pattern: "[^ -~]"}`. This rejects every
non-printable-ASCII character, including final newlines that some validators otherwise accept with `$`. Every accepted
source consumes only printable ASCII, so the guard preserves Java full matching on supplied Strings. Its inner type
keeps the constraint fragment neutral on nonstrings; the existing structural String rule still rejects supplied null
and other types. Optional omission stays allowed. No `required`, `minLength`, default or interpreter validation is inferred.

Every complete unsupported form becomes deterministic runtime-constraint description text with a stable reason: flags,
nonempty groups/payload, group context, unsupported syntax or an admission ceiling. Escapes, arbitrary ranges, wildcard
dot, negation, intersections, groups, alternation, Unicode, unbounded/lazy/possessive repetitions, repeated containers and
unsupported composition receive no hard Pattern assertion. The first-level AND subset below reuses this same Pattern proof. Even malformed runtime regex source is retained as text; generation
does not repair it. Declaration text retains the whole captured annotation and escapes control characters. Messages are
uninterpreted data, with no bundle or EL evaluation.

`TEXT_ONLY` describes every captured declaration without assertions; `OMIT` removes constraint-derived output. Ordinary
prose/deprecation, structural types, naming/exclusion, original inherited source identity and owned-definition locality
remain independent. Keys, operation payloads, container elements, non-String values and uncertain origins are outside
this hard subset. Qualified first-level composing constraints use the same grammar and guard. Existing capture sites are unchanged. No regex engine/transpiler, provider, custom flag or SPI is added.

The compiler emits explicit decoded `regexp` and `patternFlagsEmpty` facts even for unsupported direct Patterns. Both
consumers require complete facts before applying any field policy, including omitted owned relationships. Corrupt/missing
facts fail atomically with public Builder/artifact/source and recompile/package guidance. Use matching compiler/runtime/
plugin versions and recompile participating artifacts; see [migration](Migration.md#conservative-pattern-projection).
The current shared metadata marker remains unchanged during 4.2 development; release-protocol preparation is separate.

## Composed constraints and documentary meta-annotations

A **meta-annotation** is an annotation placed on an annotation type. Jakarta Bean Validation calls the outer domain
annotation a **composed constraint** and its hosted constraints **composing constraints**. **Constraint projection**
converts supported facts into JSON Schema output. **Propagated annotation** refers specifically to
[`@PropagateAnnotation`](Annotation-Propagation.md); Bean Validation composition does not copy annotations onto Schema fields.

On ordinary supported Schema fields, first-level composing Jakarta `Min`, `Max`, `DecimalMin`, `DecimalMax` and bounded
`Pattern` use the existing built-in mappings and all their type, numeric precision, group, regex and structural safeguards.
Default Bean Validation AND composition and explicit Hibernate `ConstraintComposition(AND)` are supported. Each supported
constituent is independent: two minima remain two assertions, and `Min(10)` plus `Max(5)` remains a valid schema rejecting
every supplied value. Generation does not reduce constraints or solve satisfiability. An independent direct constraint
keeps its assertion even beside an unsupported composed branch. Unsupported constituents such as Email and Size retain
runtime descriptions; Pattern-plus-Email has no inferred `format` assertion.

OR, ALL_FALSE, attribute overrides, deeper/nested composition, repeats, uncertain origins, nonempty groups/payload and
unknown composition semantics remain descriptive. No recursive flattening, attribute substitution, validator execution,
custom handler, contributor SPI or new validation engine is provided. An outer custom validator remains descriptive even
when some composing constraints project. Keys, authored operations and container elements gain no composing assertions.

`@Struct.ConstraintDescription(String)` is a literal documentary meta-annotation targeted only at annotation types. It may
be hosted on a plain domain annotation, a Bean composed constraint, or an annotation that requests propagation. Its text
is appended to ordinary field documentation under `PROJECT` and `TEXT_ONLY`, and omitted under `OMIT`. It never supplies
assertions, defaults, examples or runtime enforcement. There is no interpolation or member substitution. Existing explicit
field examples retain their placement and values independently.

(See: `InputConstraintDescriptionTest#'document a domain rule without adding enforcement'`.)

```groovy
@Struct.ConstraintDescription('Percentage of traffic sent to this service.')
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface TrafficPercentage {}

@DSL
class Service {
    @TrafficPercentage Integer traffic
}
```

This plain annotation adds only prose. Add `@Constraint(validatedBy = [])`, `@Min(1L)` and `@Max(100L)` to its declaration
and declare the standard Bean constraint members (`message`, `groups`, `payload`) to make it a composed constraint whose
first-level bounds also project. A real validator can coexist independently;
generation never initializes or invokes it. The packaged `KlumJsonSchemaGenerationTest` feature
`project independent first-level composing constraints without domain classes with Groovy #generation` qualifies that
variant, policies, inheritance/renaming, independent contributors and descriptive fallbacks on Groovy 3/4/5.

The compiler freezes each outer declaration, composing origin and literal description into public Builder facts. Generation
needs neither domain annotations nor Bean Validation on its classpath. Both consumers validate complete facts before
policy suppression, including `OMIT`; malformed or stripped facts fail before replacing output. Recompile participating
Schema hierarchies with matching compiler/runtime/plugin versions. The shared development protocol marker remains 1;
release protocol preparation is separate. See [migration](Migration.md#composed-constraints-and-documentary-meta-annotations).

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
Default groups), unsupported composed/repeatable declarations, Size and unsupported Pattern forms, quantity grammars and relationship closures
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
are supported. String, float/double/Number, unsupported composed/repeatable, type-use, getter/parameter and authored-operation
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
For a [selected String operation](Structured-Input.md#select-a-string-operation), only that operation's `type: string`,
prose and examples are projected. Superseded candidates supply no assertions. Multiple eligible marks and incomplete
selection facts fail generation before output replacement; excluded/unsupported operations remain unavailable.
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

## Owned child sequences

[Admitted ordinary owned sequences](Structured-Input.md#owned-child-sequences) emit `type: array` with `items`
referencing the concrete child's closed input grammar. List, Set, SortedSet and NavigableSet use this same input
shape. Duplicate mappings are valid input operations: no `uniqueItems`, cardinality, final-order or comparator rules are
emitted. The schema describes Struct input, not every Groovy expression or the completed collection's contents.
Structural validity cannot guarantee successful insertion, lifecycle or materialization. The destination collection
remains authoritative at runtime, including native comparison failures.

The compiler records the owned field relationship and its element invocation name on the public field accessor.
Keyed sequence items use the child's closed self-contained definition, including its required externally named String
construction key. Key renaming and naming profiles match Struct interpretation. This differs from outer-keyed Map
values, whose bodies omit their construction key. Repeated keys remain permitted by the input schema; destination
collection equality and comparator behavior still determine the completed result.

Source-free inspection checks the declared container and concrete nongeneric child Builder/DSL type without invoking
initializers, comparators or other user code. Public creator overloads are not selected by this route; customized,
overridden or absent overloads do not exclude an otherwise usable relationship. Missing/corrupt required relationship
facts fail before output replacement. Compatible binary and
inherited declarations use the same grammar; abstract, generic, custom/specialized and aggregation forms do not
acquire sequence admission. `BinaryOwnedRelationshipsTest` and `validate_owned_sequence_schema.py` exercise the actual
G3/G4/G5 emitted outputs and accepted/rejected inputs independently of runtime success.

## Concrete virtual scopes

An [admitted concrete virtual field](Structured-Input.md#concrete-virtual-builder-scopes) is a reference to the target
Builder's closed object definition, just like an unkeyed direct child. For the executable Registry example, `repository`
accepts an object whose `url` property is a String; unknown properties, scalar bodies and null reject structurally.
The virtual sink itself is not a completed-model property or export rule.

The generator reads the compiler-owned association between the exact public virtual sink and its existing creator.
Missing or inconsistent association facts fail generation before output replacement. An unchanged inherited scope
retains its target; unsupported/redeclared virtual families are not advertised. Child-field constraints retain their
own authority; no sink-parameter constraints are inferred or projected onto the mapping. Inspection does not invoke
the creator, sink, defaults or lifecycle. Structural validity cannot guarantee successful runtime configuration.

## Native derived-key Map values

[Native derived-key Maps](Structured-Input.md#derived-key-map-values) project an array of the selected scalar/enum
inputs, even though the completed destination is a Map. The generator validates the existing exact field/adder
selection against the declared String-keyed Map value type. It never evaluates `keyMapping`, predicts stored keys or
adds uniqueness assertions. Supplied order, duplicate replacement, mapping failures and destination ordering remain
runtime behavior. A structurally valid item can still fail when its ordinary Builder adder derives a key.

## Compiled converter parameters

For an [admitted generated String or CharSequence converter route](Converters.md#structured-string-input), the field's effective external
member has `type: string`. Field closures, same-compilation values and external adapters retain field prose/examples;
they do not gain C1 parameter assertions. CharSequence routes likewise emit String structure without parameter assertions. Their destination-field constraints describe the converted domain value and
never constrain the raw String. No converter or user initialization runs during generation.

[Converter-backed List elements](Structured-Input.md#converter-backed-list-elements) emit an array with
`items: {type: string}` for the selected exact String or CharSequence element route. This is structural grammar only: no element
parameter assertions are inferred or projected, including when the element's scalar-field route would qualify for C1.
The List retains field prose/examples and domain-value constraint attribution.
[Converter-backed Set elements](Structured-Input.md#converter-backed-set-elements) likewise emit an array of String
items, without `uniqueItems`, cardinality, comparator, final-order or replacement claims. Every supplied item remains an
ordered conversion/addition operation, including duplicates. Source-free inspection executes no converters.

[Converter-backed Map values](Structured-Input.md#converter-backed-map-values) similarly emit an object with
`additionalProperties: {type: string}` for exact `Map<String, T>` value routes. Keys are literal data; root/Builder members
remain closed. Map values carry no converter parameter assertions, including for C1-qualified domain constructors.
Neither shape initializes Schemas or runs converters; a structurally valid String may still fail during conversion.

For C1-qualified separately compiled declared domain routes, selected-parameter projection remains exact. A qualified Pattern appears at that same property, without a parameter object or domain
`$defs` entry. For `@Pattern(regexp='[0-9]{1,8}')`, the existing finite recognizer emits:

```json
{
  "type": "string",
  "allOf": [
    { "pattern": "^[0-9]{1,8}$" },
    { "not": { "type": "string", "pattern": "[^ -~]" } }
  ]
}
```

Only one effective declaration-parameter Jakarta Pattern with empty flags, groups and payload is eligible. An equivalent
Groovy annotation-collector result has the same compiled meaning. The current [bounded ASCII subset](#conservative-pattern-projection)
is unchanged: `+`, groups, alternation and escaped-dot memory grammars are not newly admitted. Complete unsupported
Pattern facts remain descriptions; custom/composed/repeated/type-use parameter constraints receive no hard assertions.
No custom annotation hierarchy is inspected.

`@Struct.ConstraintProjection` also targets the selected parameter. PROJECT (default) contributes qualified assertions and
unsupported descriptions; TEXT_ONLY retains descriptions; OMIT suppresses parameter constraint output and attribution.
All policies validate required facts first. An invalid regexp or missing API/member type is an error even under OMIT.
A genuinely absent and a producer-stripped Pattern are indistinguishable compiled contracts and emit no Pattern assertion;
rebuild the producer and Schema if a constraint was expected.

Parameter policy is independent of field policy. The field keeps its external naming/exclusion, ordinary documentation,
explicit examples and deprecation. Its own constraints still describe the domain value and never become raw String
assertions. `x-klum-converter-constraint-source` identifies the exact domain executable descriptor and parameter index;
field constraint attribution remains separate. Converter-method Javadoc is not field documentation.

Generation reads paired public Builder/domain artifacts with non-initializing, statically bound inspection of the selected
signature and closed annotation facts. It executes no domain initializer, constructor, converter, default, lifecycle or
validator code. Keep all participating artifacts and compiler/runtime/plugin matched, and recompile Schema hierarchies
after domain contract changes. Missing or corrupt facts fail before output/manifest replacement, including omitted owned
children and excluded members. No stale-JAR or artifact-integrity guarantee is introduced.

Actual Pattern enforcement at converter invocation remains absent (#971); JSON Schema validation is not run automatically.
An optional absent property remains allowed; supplied null and non-String values reject structurally.

## Construction-key definitions

The default root artifact describes a self-contained structured document, independently of runtime entry-point delivery.
A keyed root and each supplied direct caller-key child require their actual external key member with `type: string`.
This `required` comes from construction identity, independently of completed `@Required`. Optional relationship
properties remain optional unless explicitly marked `@Struct.Required`; a supplied child must provide its own identity.

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

This default artifact describes self-contained input. For a caller-pre-keyed root, explicitly select the
[Builder-body artifact](#caller-pre-keyed-builder-bodies). See the
[migration guidance](Migration.md#caller-pre-keyed-builder-body-schemas).

Generated JSON Schema preserves structural restrictions and projects the supported authoring bounds described above. It does not guarantee Java numeric conversion, input interpretation, binding,
lifecycle, materialization or completed validation. Integral primitives/wrappers retain exact bounds; float/double use
`number` as a structural approximation, while BigInteger/BigDecimal retain mathematical numeric semantics. Completed
`@Required` is not translated into raw-input `required`; defaults and arbitrary Groovy validation are not evaluated.
Schema key providers, state-dependent existing-child key mismatches and duplicate raw parser members are runtime/parser
concerns that structural validation cannot settle. There is no ordering, layering, merge/patch, polymorphic alias or round-trip promise. Runtime interpretation is delivered as the separate optional
[structured-input feature](Structured-Input.md), including public Factory/Builder entry points.

[#529](https://github.com/klum-dsl/klum-ast/issues/529) is closed delivered work. Qualification of a particular ScHelm
root/output against a newer matched build remains consumer-owned; historical acceptance does not qualify that build.
Generation selects no ScHelm roots, packaging or publication policy.


## Literal collection alternative

For the [finite compiler-literal keyed Map family](Structured-Input.md#literal-collection-alternative), the generated
relationship is a closed object with exactly the recorded alias properties, emitted in deterministic alias order. Each
alias is a String-keyed object whose values reference that concrete subtype's keyless body definition:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "fixed": {
      "type": "object",
      "additionalProperties": {"$ref": "#/$defs/example.ConcreteElement_DSL%24Builder%3A%3Akeyless"}
    },
    "rolling": {
      "type": "object",
      "additionalProperties": {"$ref": "#/$defs/example.RollingElement_DSL%24Builder%3A%3Akeyless"}
    }
  }
}
```

Without an explicit `@Struct.Required` on the relationship field, neither the relationship nor any alias is required.
Marking the relationship requires only that member, not any alias. One or several groups may be present. Empty mappings are valid; a child
body cannot supply its construction key, owner, unknown member or type selector. This shape promises only structural admission. Ordering, partial effects,
existing-child reuse, lifecycle and completed validation remain runtime behavior. Unsupported alternative families remain
omitted with diagnostics. Source-free producer/plugin/binary coverage lives in `BinaryOwnedRelationshipsTest`; the
emitted-schema oracle is `validate_literal_alternative_schema.py`.

## Caller-pre-keyed Builder bodies

A caller that supplies root identity before configuring its Builder needs a body schema that omits that identity.
Select `bodyRootTypes` explicitly, using the same fully qualified DSL Model binary names as `rootTypes`
([#953](https://github.com/klum-dsl/klum-ast/issues/953), S7).

(See: `KlumJsonSchemaGenerationTest#'select self-contained and Builder-body schemas for the same binary roots with Groovy #generation'`.)

```groovy
klumSchema.jsonSchema {
    rootTypes.add('example.Service')
    bodyRootTypes.add('example.Service')
}
```

Running `generateKlumJsonSchema` emits both `example.Service.schema.json` (self-contained) and
`example.Service.builder-body.schema.json` (caller-pre-keyed body). Either set may be empty if the other selects a root;
`bodyRootTypes` defaults to empty. The selections are independent Gradle task inputs. An unkeyed Model has identical
schema contents in the two separately named artifacts. Output names identify generation purpose; they never supply an
instance key. Consumers explicitly choose the matching artifact for their editor/validator and own packaging/use.

For the externally renamed `Service#name` key in the
[construction-key example](Structured-Input.md#construction-keys), the self-contained artifact requires `service-id`.
The body artifact rejects that member through its closed root definition. Direct caller-key children still require
their keys; fixed/derived/Cluster children and keyed Map values still omit theirs. Recursive relationships reuse the
appropriate member or keyless definition independently of the selected root purpose. Private definition suffixes are
implementation details and are not root-selection values or a public body-schema API. Ordinary body properties retain
admission, including a property whose external spelling overlaps a suppressed construction key.

The body variant describes the admitted structural grammar for caller-pre-keyed roots. Public construction belongs on
the accepted Factory/Builder capabilities; the interpreter is internal. Common/keyed Factory creation and active-Builder application are delivered under
[#953](../adr/0032-builder-oriented-structured-input.md#public-factory-creation-and-builder-application--953);
[structured Templates](Templates.md#structured-input-templates) are also delivered. Selecting a body artifact only changes
schema output; it adds no runtime entry point, parser/file/URL loading, key derivation or Template mode. It does not qualify every independently keyed consumer format or prescribe editor/file discovery. Schema validity
still does not guarantee construction, lifecycle or completed validation success.

Both purposes use the same isolated compiled-contract inspection and no-user-code boundary. Build/configuration caches,
manifest ownership, root removal and failure preservation cover both selections: a failed root in either set leaves all
last-successful artifacts and the manifest intact. A successful selection change removes only previously tracked files.
Use a dedicated output directory, as for default generation; unrelated files are not generation inputs or identity.

## Advisory input examples

`@Struct.Examples` provides documentary strings. They are emitted as JSON Schema string examples, without parsing or
validation against the property type or runtime constraints.

(See: `KlumJsonSchemaGenerationTest#'project advisory examples at exact source-free input positions with Groovy #generation'`.)

```groovy
@DSL class Service {
    @Struct.Examples(['localhost', 'database.internal']) String host
    @Struct.Examples(['42', 'many replicas']) int replicas
    @Struct.Examples(['API endpoint settings'])
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
or sibling uses. An owned type retains documentation on its own members. `Struct.ConstraintProjection`, ordinary prose and
`deprecated` remain independent. Unsupported members gain no admission.

Examples supply no defaults, change no requiredness or runtime input types, and do not participate in validation,
conversion, templates or lifecycle. There is no content grammar or example evaluator. Ordinary annotation/JVM constraints
apply; excessively large carried documentation is rejected defensively. The source-free public Builder metadata remains
authoritative, and malformed or missing required metadata fails before replacing successful output. The unified
[development metadata protocol](Migration.md#structured-input-metadata-protocol) remains version 1.

### Runtime order and failure

Schema validation describes structural grammar; it does not predict execution order, side effects or successful
construction. The interpreter validates and invokes operations in supplied Map/List order and stops on the first
encountered failure, retaining earlier effects. See [order and failures](Structured-Input.md#order-and-failures).
