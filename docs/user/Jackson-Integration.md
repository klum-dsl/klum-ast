# Jackson Integration

The 4.0 Jackson integration is an interoperability boundary, not a persistence format. Its explicit importer API is
available through [`KlumJacksonImporter`](https://github.com/klum-dsl/klum-ast/blob/master/klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonImporter.java);
[issue #464](https://github.com/klum-dsl/klum-ast/issues/464) supplies the executable asymmetric YAML tracer.

KlumAST provides optional Jackson integration in the `klum-ast-jackson` module. Its purpose is interoperability with
externally owned JSON/YAML formats:

- import foreign structured data into the Klum Builder lifecycle so linkage, defaults, validation, and other phases can
  enrich it;
- serialize a completed DSL Object as the ordinary Jackson POJO projection required by another tool.

Import and export are intentionally asymmetric. KlumAST does not define a JSON/YAML persistence format, does not promise
that exported data can be imported again, and does not add format or producer metadata. A Groovy model script remains the
native durable representation of a Klum model.

[ADR 0009](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0009-jackson-interoperability.md) defines the
interoperability contract and supersedes ADR 0007's persistence framing. [Issues #439](https://github.com/klum-dsl/klum-ast/issues/439) and [#440](https://github.com/klum-dsl/klum-ast/issues/440) provide the existing
resolved-property, Builder, identity, and customization groundwork.

## Responsibilities by Role

- A **Client Developer** supplies a configured mapper, invokes managed import, and writes completed models through normal
  Jackson APIs.
- A **Schema Developer** adapts foreign formats with Jackson annotations, converters, LINK projections, and lifecycle
  behavior.
- A **Model Writer** can combine Groovy-authored and YAML-authored model parts without knowing Jackson or Builder internals.
- In Layer 3, the **Domain API Developer** defines the consumer-facing model contract independently of the concrete Schema.

See [[Terms]] for the project-wide role definitions.

## Setup

### Gradle with the Schema Plugin (Preferred)

The Schema plugin imports the matching KlumAST BOM. Add the optional Jackson integration without repeating the version:

```groovy
dependencies {
    implementation 'com.blackbuild.klum.ast:klum-ast-jackson'
}
```

See [[Usage#gradle-setup-supported]] for the supported plugin and BOM setup.

### Manual Gradle Dependency

For a custom build that does not use the plugin, keep the Jackson module on the same KlumAST version as the other modules:

```groovy
dependencies {
    implementation 'com.blackbuild.klum.ast:klum-ast-jackson:<klum-version>'
}
```

### Maven (Auxiliary)

```xml
<dependencies>
 <dependency>
  <groupId>com.blackbuild.klum.ast</groupId>
  <artifactId>klum-ast-jackson</artifactId>
  <version>...</version>
 </dependency>
</dependencies>
```

### Module Registration

Register the module through discovery or explicitly:

```java
ObjectMapper discovered = new ObjectMapper().findAndRegisterModules();
ObjectMapper explicit = new ObjectMapper().registerModule(new KlumAstModule());
```

The mapper remains caller-owned. Naming strategies, mixins, views, root wrapping, unknown-property policy, formats,
modules, and data-format factories stay under the integration's control.

## Managed Import

`KlumJacksonImporter` resolves Jackson properties and binds them to generated Builders rather than partially initialized
DSL Objects. Configure the caller-owned mapper with `KlumAstModule`, then capture one importer and provide one input per
operation:

```java
KlumJacksonImporter importer = KlumJacksonImporter.using(mapper);
Order order = importer.readRoot(Order.class, KlumJacksonInput.parser(parser));
Order recipe = importer.readTemplate(Order.class, KlumJacksonInput.tree(tree));
Child_DSL.Builder child = importer.readBuilder(Child.Create.getAsBuilder(), KlumJacksonInput.map(values));
Child_DSL.Builder sameChild = importer.applyToBuilder(child, KlumJacksonInput.map(overrides));
```

Groovy may use `Child.Create.AsBuilder`. The four operations are explicit:

1. read a root and run one complete lifecycle;
2. read a value-only Template without running lifecycle processing;
3. create an imported Builder inside an active Construction session;
4. apply an input to an existing unsealed Builder.

The importer does not register modules or change mapper configuration. Parsers remain open and caller-owned;
`named("config.yaml")` adds a diagnostic source name. Missing properties retain the current Builder value, while present
values follow the caller's Jackson null, merge, and replacement configuration. Each operation applies one
`KlumJacksonInput`; arrays, Maps, and YAML multi-document streams do not create a shared Klum lifecycle.

For the importer snapshot, binding order, and Builder-state mechanics, see
[[Behind the Curtain#managed-jackson-import-mechanics]]. Raw `ObjectMapper.readValue(DslType)` remains a discouraged
standalone-root compatibility path; do not use it to start a DSL root inside an active Construction session.

## One Foreign YAML Input, One Enriched Output

This is the deliberately asymmetric workflow. A Client Developer owns the YAML mapper and provides exactly one input;
the Schema Developer owns foreign names, aliases, relationships, lifecycle enrichment, and the output projection.

```groovy
def mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
def deployment = KlumJacksonImporter.using(mapper).readRoot(
        Deployment,
        KlumJacksonInput.parser(mapper.factory.createParser('''
legacy_deployment: storefront
services: [{ id: api }]
primary: api
''')).named("foreign-deployment.yaml"))

mapper.writeValue(output, deployment) // lifecycle-derived output is an intentional addition
```

The output is not input for another lifecycle and it need not retain `legacy_deployment`; it is an ordinary Jackson
projection of the completed model. This exact contract is exercised by
`JacksonYamlInteroperabilityDocumentaryTest#imports one foreign YAML document through one Builder lifecycle and exports an enriched YAML projection`.
YAML streams, repeated importer calls, and combinations of inputs do not define layering or overwrite policy; the
source-neutral coordinator remains [#304](https://github.com/klum-dsl/klum-ast/issues/304).

## Mapping a Foreign Schema

Use ordinary Jackson annotations and configuration to adapt an externally controlled format:

```groovy
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

@DSL
class Policy {
    @JsonProperty("public")
    boolean publiclyVisible

    @JsonAlias("legacy_name")
    String name

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String normalizedName

    @PostApply
    void normalize() {
        normalizedName = name?.trim()
    }
}
```

Managed import honors property names and aliases, naming strategies, access/ignore rules, mixins, views,
unknown-property policy, formats, Simple Value codecs, null/content policies, merge configuration, and polymorphic owned
DSL subtypes.

KlumAST still owns construction. `@JsonCreator`, direct completed-model mutation, and
`@JsonDeserialize(builder = ...)` cannot replace generated Builder allocation and must fail clearly. An explicit
type-level custom deserializer is a full opt-out; it receives no additional Klum lifecycle. Prefer a converter or a
`FieldType.BUILDER` staging field populated into real fields during `PostApply` or a later Builder phase.

## `LINK` Values

`FieldType.LINK` is aggregation rather than owned composition. Import never interprets an inline object at a `LINK` field
as an owned child. Use Jackson identity/reference metadata, a property converter, or lifecycle resolution; otherwise the
input fails.

The standard identity form is:

```groovy
import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIdentityReference
import com.fasterxml.jackson.annotation.ObjectIdGenerators

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
@DSL
class Service {
    @Key String id
}

@DSL
class Deployment {
    List<Service> services

    @Field(FieldType.LINK)
    @JsonIdentityReference(alwaysAsId = true)
    Service primary
}
```

For export, the Schema Developer must choose a representation for every non-null LINK: identity/reference id, omission,
scalar projection, custom structure, or deliberate inline projection. A custom serializer may therefore inline a value if
the external format requires it, but owns recursion and cycle safety. Without an explicit choice, KlumAST fails rather than
inventing a universal representation.

## Templates

Managed Template import is value-only and deliberately skips lifecycle processing. Applying that Template later copies its
values and owned Template composition into fresh Builders and then follows normal Template behavior.

Normal Jackson serialization rejects a marked Template because values cannot preserve executable recipe actions.
Materialize a fresh ordinary model through `Template.With`, `copyFrom`, or another Template/copy API before serialization.
An explicit type-level serializer is a deliberate opt-out and owns its output.

## Export

There is no Klum export facade. Serialize a completed model through ordinary Jackson APIs:

```java
mapper.writeValue(outputStream, completedModel);
```

Lifecycle-derived and Jackson read-only values may appear even though they were not import inputs. Owner, Role, Builder
state, and synthetic members remain omitted in 4.0. Explicit type-level serializers may project public model data and
`KlumObjectSupport`; internal companion state is not a supported extension seam.

## Errors and Compatibility

The 4.0 importer wraps syntax, mapping, and source-I/O failures in `KlumModelException`, preserving the original exception
as the cause and contributing an import source plus Jackson property/index information to the construction path. Raw
non-DSL Jackson operations retain their normal error behavior.

KlumAST commits to the public importer API and managed Builder/lifecycle/customization semantics across 4.x. It does not
commit to byte-for-byte output, property ordering, a universal wire schema, or round trips. External version fields are
ordinary Schema data; any adapters for old or future variants belong in Schema-owned converters, staging DTOs/Builders, or
custom deserializers.

`Create.FromMap` remains a separate value-copy convenience. It does not resolve Jackson property metadata and is not a
substitute for managed import of a foreign format.

## Extend

`KlumAstModule` can be subclassed to customize module registration. Its Builder-backed deserializer remains an internal
implementation detail. Supported extension seams are normal Jackson property/value configuration, explicit type-level
serializer/deserializer opt-outs, converters, and the public importer.
