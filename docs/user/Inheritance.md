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

## Abstract Builder operations

An abstract DSL class may declare abstract `@Builder.Method` operations. They move to the generated Builder,
including its public `Parent_DSL.Builder` contract, and disappear from completed Models. A concrete subtype must
implement each operation on its Builder; normal compiler checks reject a missing implementation. The generated
property setter can satisfy an inherited abstract setter when the subtype declares the corresponding storage.
An abstract Model getter remains on the Model and can be implemented by that same subtype property.

(See: `AbstractBuilderMethodTest#'abstract Builder operations dispatch through concrete subtype storage and implementations'`.)

```groovy
@DSL abstract class Parent {
    abstract String getDisplayName()
    @Builder.Method abstract void setDisplayName(String value)
    @Builder.Method abstract String render(String prefix, int times)
    @Builder.Method void rename(String value) { setDisplayName(value) }
}
@DSL class Child extends Parent {
    @Default(code = { 'fallback' }) String displayName
    @Builder.Method String render(String prefix, int times) { prefix * times + displayName }
}

def child = Child.Create.With {
    rename 'chosen'
    assert render('!', 2) == '!!chosen'
}
assert child.displayName == 'chosen'
assert Child.Create.One().displayName == 'fallback'
```

This applies to arbitrary Builder operations, including non-void methods, rather than only accessors.
Abstract `@Builder.Input` and `@Builder.Result` methods still require an implementation and retain their targeted
compilation diagnostics. An ordinary Model method does not satisfy a Builder-only abstract operation.

Abstract-parent [Templates](Templates.md#templates-for-abstract-classes) can still define field recipes. If a Template
definition invokes an unrealized abstract Builder operation, directly or through another Builder method, it fails
with a diagnostic asking for a concrete subtype which implements the operation. Define that Template on the concrete
subtype to execute its behavior. Templates neither infer nor defer abstract operations.

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
