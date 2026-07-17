Jackson Integration
===================

__This feature is considered beta. The 4.0 interoperability contract is accepted, while its explicit importer API remains
to be implemented under issue #428.__

KlumAST provides optional Jackson integration in the `klum-ast-jackson` module. Its purpose is interoperability with
externally owned JSON/YAML formats:

- import foreign structured data into the Klum Builder lifecycle so linkage, defaults, validation, and other phases can
  enrich it;
- serialize a completed DSL Object as the ordinary Jackson POJO projection required by another tool.

Import and export are intentionally asymmetric. KlumAST does not define a JSON/YAML persistence format, does not promise
that exported data can be imported again, and does not add format or producer metadata. A Groovy model script remains the
native durable representation of a Klum model.

[ADR 0009](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0009-jackson-interoperability.md) defines the
interoperability contract and supersedes ADR 0007's persistence framing. Issues #439 and #440 provide the existing
resolved-property, Builder, identity, and customization groundwork.

# Responsibilities by role

- A **Client Developer** supplies a configured mapper, invokes managed import, and writes completed models through normal
  Jackson APIs.
- A **Schema Developer** adapts foreign formats with Jackson annotations, converters, LINK projections, and lifecycle
  behavior.
- A **Model Writer** can combine Groovy-authored and YAML-authored model parts without knowing Jackson or Builder internals.
- In Layer 3, the **Domain API Developer** defines the consumer-facing model contract independently of the concrete Schema.

See [[Terms]] for the project-wide role definitions.

# Dependency and module registration

## Maven

```xml
<dependencies>
 <dependency>
  <groupId>com.blackbuild.klum.ast</groupId>
  <artifactId>klum-ast-jackson</artifactId>
  <version>...</version>
 </dependency>
</dependencies>
```

## Gradle

```groovy
dependencies {
  implementation 'com.blackbuild.klum.ast:klum-ast-jackson:<version>'
}
```

Register the module through discovery or explicitly:

```java
ObjectMapper discovered = new ObjectMapper().findAndRegisterModules();
ObjectMapper explicit = new ObjectMapper().registerModule(new KlumAstModule());
```

The mapper remains caller-owned. Naming strategies, mixins, views, root wrapping, unknown-property policy, formats,
modules, and data-format factories stay under the integration's control.

# Managed import

The existing internal `KlumDeserializer` resolves Jackson properties and binds them to generated Builders rather than
partially initialized DSL Objects. The final 4.0 API adds a public, data-format-neutral `KlumJacksonImporter` with four
explicit modes:

1. read a root and run one complete lifecycle;
2. read a value-only Template without running lifecycle processing;
3. create an imported Builder inside an active Construction session;
4. apply an input to an existing unsealed Builder.

The exact method signatures are finalized in the importer tracer bullet. Until that implementation lands, raw
`ObjectMapper.readValue(DslType)` remains the beta standalone-root path; do not use it to start a DSL root inside an active
Construction session.

Root import uses this order:

1. allocate root and owned child Builders with their source initializers;
2. run `PostCreate`;
3. bind properties present in the external input;
4. run `PostApply`;
5. run graph phases, materialization, validation, and verification once.

Missing properties leave the current Builder value unchanged. Present values use the configured Jackson null, merge, and
replacement behavior; explicitly setting a Collection to `null` is permitted. Ambient Templates, `@Overwrite`, and
`copyFrom` do not alter Jackson binding.

Owned nested DSL values are Builders in the same Construction session. Multiple explicit inputs can be applied in order
before lifecycle completion. Top-level arrays, Maps, and YAML multi-document streams are ordinary Jackson structures and
do not implicitly create a shared Klum lifecycle; format-specific multi-document convenience is later work.

# Mapping a foreign schema

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

# LINK values

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

# Templates

Managed Template import is value-only and deliberately skips lifecycle processing. Applying that Template later copies its
values and owned Template composition into fresh Builders and then follows normal Template behavior.

Normal Jackson serialization rejects a marked Template because values cannot preserve executable recipe actions.
Materialize a fresh ordinary model through `Template.With`, `copyFrom`, or another Template/copy API before serialization.
An explicit type-level serializer is a deliberate opt-out and owns its output.

# Export

There is no Klum export facade. Serialize a completed model through ordinary Jackson APIs:

```java
mapper.writeValue(outputStream, completedModel);
```

Lifecycle-derived and Jackson read-only values may appear even though they were not import inputs. Owner, Role, Builder
state, and synthetic members remain omitted in 4.0. Explicit type-level serializers may project public model data and
`KlumObjectSupport`; internal companion state is not a supported extension seam.

# Errors and compatibility

The 4.0 importer wraps syntax, mapping, and source-I/O failures in `KlumModelException`, preserving the original exception
as the cause and contributing an import source plus Jackson property/index information to the construction path. Raw
non-DSL Jackson operations retain their normal error behavior.

KlumAST commits to the public importer API and managed Builder/lifecycle/customization semantics across 4.x. It does not
commit to byte-for-byte output, property ordering, a universal wire schema, or round trips. External version fields are
ordinary Schema data; any adapters for old or future variants belong in Schema-owned converters, staging DTOs/Builders, or
custom deserializers.

`Create.FromMap` remains a separate value-copy convenience. It does not resolve Jackson property metadata and is not a
substitute for managed import of a foreign format.

# Extend

`KlumAstModule` can be subclassed to customize module registration. Its Builder-backed deserializer remains an internal
implementation detail. Supported extension seams are normal Jackson property/value configuration, explicit type-level
serializer/deserializer opt-outs, converters, and the public importer once implemented.
