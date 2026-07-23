# Alternatives Syntax

Alternative syntax selects a child class through the generated method name rather than an explicit class argument to the
adder method:

Given the schema:
```groovy
@DSL
class Config {
    String name
    Map<String, Element> elements
}

@DSL
abstract class Element {
    @Key String name
}

@DSL
class SubElement extends Element {
    String role
}

@DSL
class ChildElement extends Element {
    String game
}
```

instead of

```groovy
Config.Create.With {
  elements {
    element(SubElement, "bla") {}
    element(ChildElement, "bli") {}
  }
}
```

You could also write:

```groovy
Config.Create.With {
  elements {
    subElement("bla") {}
    childElement("bli") {}
  }
}
```

Keep these constraints in mind:

- Other than with the regular solution, the `elements` closure is no longer optional.
- The necessary methods are only generated if `Config` and all subclasses of `Element` are compiled in __same compiler run__.
  This means that if you split your schema into different projects, you need to depend on the sources, not on the compiled
  classes of other schema parts (see [[Usage]] for details).
- Child classes with the same simple name, even in different packages, are not allowed.
- [[Convenience Factories]] and source-visible converter methods also work on alternative methods. For the 4.0 Builder
  boundary and opaque-producer diagnostics, see [[Behind the Curtain#builder-projection-for-custom-producers]].

## Naming Strategies

KlumAST chooses the first applicable strategy below. Use the most local strategy that makes the Model readable: field
names for one exceptional relationship, `shortName` for a Schema type's deliberate public DSL name, and `stripSuffix` for
a repeated type-family convention.

### Defining Explicit Names for a Field

Use `@Field(alternatives = ...)` to map method names explicitly to classes:

```groovy
@DSL
class Config {
    String name
    @Field(alternatives = {[subby: SubElement, childy: ChildElement]})
    Map<String, Element> elements
}
```

The map must be inside a closure and contain only literal Strings and literal Classes.

### Explicit Short Names for Subclasses

Set `shortName` on `@DSL` when a subtype has a deliberate public DSL name:

```groovy
@DSL(shortName = 'subby')
class SubElement extends Element {

    String role
}

@DSL(shortName = 'child')
class ChildElement extends Element {

    String game
}
```

allows:

```groovy
Config.Create.With {
  elements {
    subby("bla") {}
    child("bli") {}
  }
}
```

### Strip Common Suffixes

`@DSL(stripSuffix = "Element")` on the common base strips the suffix from child class names when determining the
alternative method name. An explicit `shortName` still takes precedence:

```groovy
given: // Schema
@DSL
class Config {
    Map<String, Element> elements
}

@DSL(stripSuffix = "Element")
abstract class Element {
    @Key String name
}

@DSL
class ServiceElement extends Element {
}

@DSL
class JobElement extends Element {
}

when: // Model
def config = Config.Create.With {
    elements {
        service("api") {}
        job("cleanup") {}
    }
}

then: // Assertions
assert config.elements.api instanceof ServiceElement
assert config.elements.cleanup instanceof JobElement
```

The executable example is `AlternativesSpec.groovy`, feature `uses stripped suffixes for alternative method names`.

### Default: Derived from the Class Name

In any other case, KlumAST lowercases the first character of the subclass name. The opening `SubElement` and
`ChildElement` example therefore produces `subElement` and `childElement`.

For more complex cases, custom [[Factory Classes]] can be used.
