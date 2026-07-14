Jackson Integration
===================

__This feature is considered beta. While it works for my use cases, it is bound to have a couple of rough edges. Create issues as necessary.__

KlumAST provides optional integration for Jackson in the optional `klum-ast-jackson` module.

This provides helpers for serialization and deserialization of Klum objects:

- Using `KlumAnnotationIntrospector`, Owner fields, `@Role`-annotated members and members whose name contains `$` are automatically ignored during serialization (they are _not_ converted into back references, since this would usually be done during deserialization anyway)
- the module's internal `KlumDeserializer` reads JSON object state and restores it through generated Builders rather than partially initialized DSL Objects
- after binding, the normal lifecycle, graph materialization, validation, and verification pipeline produces the completed DSL Object
- All enhancements are packaged into a Jackson module (KlumAstModule)

The current raw-state implementation is transitional. [ADR 0007](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0007-jackson-configuration-replay.md)
accepts configuration replay as the stable target: persisted public Builder inputs are bound through resolved Jackson
properties and one normal lifecycle recomputes derived values. [Issue #428](https://github.com/klum-dsl/klum-ast/issues/428)
tracks that implementation, including renamed properties from #251 and explicit `LINK` identity. Until it lands, mutating
lifecycle callbacks should remain idempotent and existing JSON may change.

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

# Extend

`KlumAstModule` can be subclassed to customize module registration. The Builder-backed deserializer remains an internal
implementation detail; ADR 0007 defines property/value customization and an explicit type-level custom deserializer as the
supported escape hatches. The public `KlumValueInstantiator` and `SettableKlumBeanProperty` classes from earlier versions
have been removed.
