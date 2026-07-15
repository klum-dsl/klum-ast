Jackson Integration
===================

__This feature is considered beta. While it works for my use cases, it is bound to have a couple of rough edges. Create issues as necessary.__

KlumAST provides optional integration for Jackson in the optional `klum-ast-jackson` module.

This provides helpers for serialization and deserialization of Klum objects:

- Using `KlumAnnotationIntrospector`, Owner fields, `@Role`-annotated members and members whose name contains `$` are automatically ignored during serialization (they are _not_ converted into back references, since this would usually be done during deserialization anyway)
- the module's internal `KlumDeserializer` replays resolved Jackson properties into generated Builders rather than partially initialized DSL Objects
- owned nested DSL values are populated as Builders in the same Construction session
- after binding, the normal lifecycle, graph materialization, validation, and verification pipeline produces the completed DSL Object
- serialization rejects a marked Template, including one nested in an ordinary value, because JSON would retain values
  while silently losing Template recipe actions. Rehydrate the Template into an ordinary completed model before exporting it
- All enhancements are packaged into a Jackson module (KlumAstModule)

[ADR 0007](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0007-jackson-configuration-replay.md)
defines this as configuration replay: JSON persists public Builder configuration intent, not a snapshot of every completed
field. [Issue #439](https://github.com/klum-dsl/klum-ast/issues/439) implements the property-aware JSON-1 slice, including
renamed and aliased properties from #251. [Issue #440](https://github.com/klum-dsl/klum-ast/issues/440) implements
identity-safe `LINK` persistence and the supported Jackson customization boundary. Existing beta JSON may change when
moving from the earlier raw-state behavior.

> JSON serialized by different KlumAST versions is not guaranteed to be mutually compatible.

Marked Templates remain unsupported Jackson values because value serialization cannot preserve their recipe behavior.

# Usage

In order to use the module, it needs to be included in the classpath.

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

To actually activate the module, you need to register it in the ObjectMapper:

```java
ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
```

It is also possible to register the module explicitly:

```java
ObjectMapper mapper = new ObjectMapper().registerModule(new KlumAstModule());
```

# Configuration replay

Jackson allocates each root and owned child Builder with its source initializers, then executes this order:

1. `PostCreate`
2. bind properties that are present in JSON
3. `PostApply`
4. the normal graph phases, materialization, validation, and verification

Missing properties leave initializer, default, auto-create, or lifecycle behavior intact. A present scalar replaces the
current value; present `null` clears a nullable value; and a present container replaces the initialized container, with an
empty container clearing it. JSON binding does not apply ambient Templates, `@Overwrite`, or `copyFrom` merge behavior.

The persistence input surface is the public configurable Builder surface. Owner, Role, synthetic, `PROTECTED`, `IGNORED`,
and `BUILDER` state is not input. `PROTECTED` state is read-only output; other lifecycle-derived output should be marked
read-only explicitly:

```groovy
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

@DSL
class Person {
    @JsonProperty("display_name")
    @JsonAlias("name")
    String configuredName

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String normalizedName

    @PostApply
    void normalize() {
        normalizedName = configuredName?.trim()
    }
}
```

Resolved `@JsonProperty` names, `@JsonAlias`, configured naming strategies, mixins, ignore/access rules, and the mapper's
unknown-property policy are honored. Serialization uses the same external property metadata. An explicit type-level custom
deserializer opts that DSL type out of Klum configuration replay.

# LINK references

A `FieldType.LINK` is aggregation, never owned nested configuration. Its JSON value must therefore use an explicit
reference strategy. The normal Jackson identity form puts `@JsonIdentityInfo` on the target type and
`@JsonIdentityReference(alwaysAsId = true)` on the `LINK` property:

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

The `primary` value is persisted as an id such as `"database"`, never as an inline Service object. Backward and forward
references resolve to Builders allocated in the same configuration-replay session and preserve identity after
materialization. A custom `ObjectIdResolver` may resolve an existing completed DSL Object as an aggregation target. The
same rules apply to Collection and Map `LINK`s, whose members or values are reference ids.

Custom property serializers/deserializers are the alternative for a domain-specific reference format. They must write and
resolve a reference rather than treat the target as owned configuration. Inline object input fails with `JsonMappingException`.
A non-null `LINK` without both target `@JsonIdentityInfo` and property `alwaysAsId`, or without explicit property codecs,
also fails instead of silently embedding or omitting the target. Owner and Role JSON input is ignored; both are recomputed
by the framework.

# Supported customization

Jackson customization remains supported at property and Simple Value seams:

- active views control both configuration input and output;
- inclusion and formats retain normal Jackson behavior;
- property serializers/deserializers can encode Simple Values or an explicit `LINK` reference;
- naming strategies, aliases, access/ignore rules, and mixins use resolved Jackson metadata;
- polymorphic owned DSL subtypes select the concrete subtype but still allocate a Builder in the root Construction session.

Construction itself remains owned by KlumAST. `@JsonCreator`, direct model mutators, `@JsonDeserialize(builder = ...)`,
owned-relationship deserializers that produce completed models, and generic managed/back references cannot replace Builder
allocation, ownership, or Materialization. An explicit type-level custom deserializer is the deliberate opt-out and owns
the complete construction result for that type.

# Current boundaries

Jackson persistence is configuration replay rather than an exact completed-state snapshot. Lifecycle-derived fields are
recomputed, ambient Templates and copy/overwrite semantics do not participate, and a property cannot safely be both
non-idempotently transformed output and persisted input.

Marked Templates are unsupported Jackson values. `KlumAstModule` rejects a marked Template at the root or nested in an
ordinary value because JSON cannot preserve its recipe actions. Rehydrate a fresh ordinary completed model through
`Template.With`, `copyFrom`, or another Template/copy API before passing it to an `ObjectMapper`.

# Extend

`KlumAstModule` can be subclassed to customize module registration. The Builder-backed deserializer remains an internal
implementation detail; ADR 0007 defines property/value customization and an explicit type-level custom deserializer as the
supported escape hatches. The public `KlumValueInstantiator` and `SettableKlumBeanProperty` classes from earlier versions
have been removed.
