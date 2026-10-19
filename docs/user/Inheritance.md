# Inheritance

DSLObjects can inherit from other DSL-Objects (but the child class *must* be annotated with DSL as well). This
allows polymorphic usage of fields. To allow to specify the concrete implementation, setter methods are generated
which take an additional Class parameter.

## Choosing a derived implementation

(See: `InheritanceDocumentaryTest#'configures a derived project through an unkeyed field'`.)

```groovy
@DSL
class Config {
    Project project 
}

@DSL
class Project {
    String name
}

@DSL
class MavenProject extends Project{
    List<String> mvnOpts
}

Config.Create.With {
    project(MavenProject) {
        name "demo"
        mvnOpts "a", "b"
    }
}
```

The generated Builder hierarchy mirrors the DSL Object hierarchy: a derived DSL Object receives a derived Builder whose
superclass is the parent DSL Object's Builder. Parent and child field initializers therefore run on Builders, and the
completed inheritance chain is materialized only after construction phases finish.

These typed methods are not generated, if the declared type is final. Likewise, if the declared type is abstract,
*only* the typed methods are generated.

## Instance storage names

Since 4.1, each instance field/property name that participates in DSL construction storage must be unique across a DSL inheritance hierarchy
([#371](https://github.com/klum-dsl/klum-ast/issues/371)). Redeclaring an inherited name fails compilation at the descendant
declaration with a message identifying both declarations. This includes ordinary fields/properties, `@Owner`,
`@Default`, and construction-only `@Field(FieldType.BUILDER)` storage, including ancestors compiled separately.

Configure the inherited property directly and declare only additional storage on the descendant.

(See: `DslPropertyShadowingTest#'configures inherited storage without redeclaring it'`.)

```groovy
@DSL class Service {
    String name
    @Default(code = { 'https' }) String protocol
}
@DSL class WebService extends Service { Integer port }

def service = WebService.Create.With {
    name 'frontend'
    port 443
}
assert service.name == 'frontend'
assert service.protocol == 'https'
assert service.port == 443
```

Static fields may shadow static ancestor fields: they remain class state and never become Builder storage. `$`-prefixed
implementation fields are also outside the diagnostic because they are excluded from Builder storage, configuration,
and materialization. A generation marker such as `@KlumGenerated`, or a JVM synthetic flag, does not by itself exempt
an ordinary field name: that field can still become construction storage and conflict with an inherited declaration.
Ordinary method/getter overrides, a property implementing an abstract getter, and Java ancestor fields remain legal.

The implementation-field boundary and consistent inherited configuration, lifecycle, defaults, and owners are covered by
`DslPropertyShadowingTest#'#category implementation fields are invisible to DSL storage with a #compilation ancestor'`.

## Abstract Model properties with Builder setters

An abstract parent can declare a read-only Model getter together with an abstract `@Builder.Method` setter. A concrete
subtype supplies the actual property and may derive its default from subtype context. The setter exists only on the
Builder; inherited Builder helpers and lifecycle methods can use it without adding parent storage.

(See: `InheritedBuilderSetterTest#'abstract Builder setter binds to subtype storage and default'`.)

```groovy
@DSL abstract class Sensor {
    abstract String getDisplayName()
    @Builder.Method abstract void setDisplayName(String displayName)

    @Builder.Method void rename(String value) { setDisplayName(value) }
}

@DSL class Window { String displayName }

@DSL class WindowSensor extends Sensor {
    Window window
    @Default(code = { "window sensor $window.displayName" })
    String displayName
}

def sensor = WindowSensor.Create.With {
    window { displayName 'kitchen' }
}
assert sensor.displayName == 'window sensor kitchen'

def renamed = WindowSensor.Create.With {
    window { displayName 'kitchen' }
    rename 'custom sensor'
}
assert renamed.displayName == 'custom sensor'
```

The abstract setter must return `void`, take exactly one argument, and match an abstract `getX()` getter's type and name.
Other abstract Builder-only method shapes receive a compilation diagnostic; provide an implementation for those methods.
The concrete subtype owns the backing property. Redeclaring stored properties across the hierarchy remains prohibited.
Java and statically compiled Groovy consumers can call the setter through `Sensor_DSL.Builder`.

## Keyed inheritance

This works identically with keyed objects.

(See: `InheritanceDocumentaryTest#'configures a keyed derived project through an inherited key'`.)

```groovy
@DSL
class Config {
    Project project 
}

@DSL
class Project {
    @Key String name
}

@DSL
class MavenProject extends Project{
    List<String> mvnOpts
}

Config.Create.With {
    project(MavenProject, "demo") {
        mvnOpts "a", "b"
    }
}
```

## Key hierarchy constraints

Note that it is illegal to let a keyed class inherit from a not keyed non abstract class. The topmost non abstract dsl class in the hierarchy
decides whether the hierarchy is keyed or not. 
