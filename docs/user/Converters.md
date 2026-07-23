# Converters

Converters make a Model more compact by accepting a convenient input and turning it into the field's declared value.

## Field-Based Converters

Field-based converters are closures that create additional setter/adder methods.

Converter methods have the same name as regular setters/adders, but
different parameters. A converter is a closure with zero or more explicit
parameters that is called to create the target type (or the element type 
for collections/maps). For maps of simple types, a key parameter
is added to the adder as well.

Examples:

```groovy
@DSL class Foo {
  @Field(converters = [
    {long value -> new Date(value)},
    {int date, int month, int year -> new Date(year, month, date)}
  ])
  Date birthday

  @Field(converters = [
    {long value -> new Date(value)},
    {int date, int month, int year -> new Date(year, month, date)}
  ])
  Map<String, Date> payDays
}
```
Creates additional methods:
```groovy
Date birthday(long $value) {
    birthday({long value -> new Date(value)}.call($value))
}
Date birthday(int $date, int $month, int $year) {
    birthday({int date, int month, int year -> new Date(year, month, date)}.call($date, $month, $year))
}

Date payDay(String $key, long $value) {
    payDay($key, {long value -> new Date(value)}.call($value))
}
Date payDay(String $key, int $date, int $month, int $year) {
    payDay($key, {int date, int month, int year -> new Date(year, month, date)}.call($date, $month, $year))
}

```

The closures must return an instance of the field (or element) type.

For a DSL Object result, a source-visible converter participates in the active Builder construction. Field converters for
Simple Values are unaffected. See [[Behind the Curtain#builder-projection-for-custom-producers]] when the distinction
matters to an integration.

## Factory Method Converters

For non-DSL values, factory-method converters continue to return the converted value and pass it to the Builder setter or
adder.

For a DSL Object relationship, a recognized static factory method can be used inside the owning Model just like a normal
child creator. The same method still returns a completed object when called as a root factory:

```groovy
given: // Schema
@DSL
class Server {
    Endpoint endpoint
}

@DSL
class Endpoint {
    int port

    static Endpoint fromPort(int port) {
        Endpoint.Create.With { port port }
    }
}

when: // Model
def server = Server.Create.With {
    endpoint 8443
}

then: // Assertions
assert server.endpoint.port == 8443
```

For the Builder-projection rules, source-visibility boundary, and diagnostics for opaque producers, see
[[Behind the Curtain#builder-projection-for-custom-producers]].

A factory method is named `from*`, `of*`, or `parse*`; a non-DSL factory may also be named `create*`. Alternatively,
annotate the method with `@Converter`.

Avoid multiple factory methods with the same parameter signature; selection can otherwise be ambiguous.

## Factory Classes

Declare additional factory classes with `@Converters`, either for a complete DSL class or one field.

By default, KlumAST uses public static factory methods that return the expected type or a subtype, have a recognized
prefix, or carry `@Converter`.

```groovy
import java.text.SimpleDateFormat

@DSL(converters = [BarUtil]) class Foo {
    Bar bar
}

class Bar {
    // Regular POGO/POJO, not a DSL Object.
    Date birthday
}

class BarUtil {
    static Bar fromLong(long value) {
        return new Bar(birthday: new Date(value))
    }

    @Converter
    static Bar readFromString(String string) {
        return new Bar(birthday: SimpleDateFormat.dateInstance.parse(string))
    }
}
```

For a Map of simple elements, KlumAST prepends the key parameter to the converter parameters.

## Customization

`@Converters` can customize factory discovery for third-party types: `includeMethods`, `excludeMethods`, and
`excludeDefaultPrefixes` change method selection, while `includeConstructors` exposes constructors as converters. See the
[`@Converters` API source and Javadoc](https://github.com/klum-dsl/klum-ast/blob/master/klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Converters.java)
for every option and its default.
