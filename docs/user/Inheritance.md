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
