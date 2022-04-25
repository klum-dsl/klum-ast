Jackson Integration
===================

__This feature is considered beta. While it works for my use cases, it is bound to have a couple of rough edges. Create issues as necessary.__

KlumAST provides optional integration for Jackson in the optional `klum-ast-jackson` module.

This provides helpers for serialization and deserialization of Klum objects:

- Using `KlumAnnotationIntrospector`, Owner field are automatically ignored during serialization (they are _not_ converted into back references, since this would usually be done during deserialization anyway)
- SettableKlumBeanProperty handles the setting of properties via the proxy object
- KlumValueInstantiator handles instantiating Keyed Objects via the factory helper
- All enhancements are packaged into a Jackson module (KlumAstModule)

__Note that currently postApply and validation is deactivated for deserialized objects__

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

It is also possible to extend KlumAstModule and/or one of the other class to further customize the behaviour.

