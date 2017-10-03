DSLObjects can inherit from other DSL-Objects (but the child class *must* be annotated with DSL as well). This
allows polymorphic usage of fields. To allow to specify the concrete implementation, setter methods are generated
which take an additional Class parameter.

These typed methods are not generated, if the declared type is final. Likewise, if the declared type is abstract, 
*only* the typed methods are generated.

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

Config.create {
    project(MavenProject) {
        name "demo"
        mvnOpts "a", "b"
    }
}
```

This works identically with keyed objects.

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

Config.create {
    project(MavenProject, "demo") {
        mvnOpts "a", "b"
    }
}
```

Note that it is illegal to let a keyed class inherit from a not keyed class. The topmost dsl class in the hierarchy
decides whether the whole hierarchy is keyed or not. 
