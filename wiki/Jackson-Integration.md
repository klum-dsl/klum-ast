Jackson Integration
===================

__This feature is considered beta. While it works for my use cases, it is bound to have a couple of rough edges. Create issues as necessary.__

KlumAST provides optional integration for Jackson in the optional `klum-ast-jackson` module.

This provides helpers for serialization and deserialization of Klum objects:

- Using `KlumAnnotationIntrospector`, Owner fields, `@Role`-annotated members and members whose name contains `$` are automatically ignored during serialization (they are _not_ converted into back references, since this would usually be done during deserialization anyway)
- the module's internal `KlumDeserializer` replays resolved Jackson properties into generated Builders rather than partially initialized DSL Objects
- owned nested DSL values are populated as Builders in the same Construction session
- after binding, the normal lifecycle, graph materialization, validation, and verification pipeline produces the completed DSL Object
- All enhancements are packaged into a Jackson module (KlumAstModule)

[ADR 0007](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0007-jackson-configuration-replay.md)
defines this as configuration replay: JSON persists public Builder configuration intent, not a snapshot of every completed
field. [Issue #439](https://github.com/klum-dsl/klum-ast/issues/439) implements the property-aware JSON-1 slice, including
renamed and aliased properties from #251. Existing beta JSON may change when moving from the earlier raw-state behavior.

> JSON serialized by different KlumAST versions is not guaranteed to be mutually compatible.

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

# Current boundaries

JSON-1 does not implement `LINK` identity or forward references, `@JsonCreator`, direct model setters, Jackson Builder
construction, completed-model owned deserializers, managed/back-reference ownership replacement, or polymorphic owned DSL
subtypes. Those boundaries remain with [#440](https://github.com/klum-dsl/klum-ast/issues/440).

Marked Templates are unsupported Jackson values. Reliable rejection is blocked until
[#438](https://github.com/klum-dsl/klum-ast/issues/438) gives completed Templates stable identity separate from ordinary
models; until then, do not pass Templates to an `ObjectMapper`.

# Extend

`KlumAstModule` can be subclassed to customize module registration. The Builder-backed deserializer remains an internal
implementation detail; ADR 0007 defines property/value customization and an explicit type-level custom deserializer as the
supported escape hatches. The public `KlumValueInstantiator` and `SettableKlumBeanProperty` classes from earlier versions
have been removed.
