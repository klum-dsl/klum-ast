### Alternatives syntax

The alternatives syntax allows defining the class of the element not as an attribute to the adder method, but by using
different methods instead:

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
Config.create {
  elements {
    element(SubElement, "bla") {}
    element(ChildElement, "bli") {}
  }
}
```

You could also write:

```groovy
Config.create {
  elements {
    subElement("bla") {}
    childElement("bli") {}
  }
}
```

There are a couple of noteworthy points:

- Other than with the regular solution, the `elements` closure is no longer optional.
- The necessary methods are only generated if `Config` and all subclasses of `Element` are compiled in __same compiler run__.
  This means that if you split your schema into different projects, you need to depend on the sources, not on the compiled
  classes of other schema parts (see [[Usage]] for details).
- Different child classes with the same simple name (in different packages) is not allowed.
- [[Convenience Factories]] also work on alternative methods. This works only for implicit and explicit factory methods in the subclass.


There are four strategies for choosing alternative names (in order of precedence):

## Defining explicit names for a field

Using `@Field.alternatives`, a map of String/Class can be provided, which explicitly maps method names to classes:

```groovy
@DSL
class Config {
    String name
    @Field(alternatives = {[subby: SubElement, childy: ChildElement]})
    Map<String, Element> elements
}
```

Note that the given map must be placed inside a closure and only contain literal Strings and literal Classes.

## Give explicit short names for subclasses

By using the `shortName` value of the `@DSL` annotation, the generated alternative names use that short name instead:

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
Config.create {
  elements {
    subby("bla") {}
    child("bli") {}
  }
}
```

## Strip common suffixes

By using the `stripSuffix` value of DSL, the given suffix is stripped from all child classes when determining the 
method name (if a shortName is given, strip suffix is ignored). 

## Default: derived from the class name

In any other case, the name of the subclass with a lowercase first character is used.

For more complex cases, custom [[Factory Classes]] can be used.

