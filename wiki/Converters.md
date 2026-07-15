To make a model even more compact, KlumAST suuports the notion of converters:

# Field based converters
Field based converters are a list of converting closures that create additional
setter/adder methods.

Converter methods have the same name as regular setter / adders, but
different parameters. A converter is a closure with zero or more explicit
parameters that is called to create the target type (or the element type 
for collections / maps). Note that for maps of simple types, a key parameter
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
Date birthday(int $date, int $month, $year) {
    birthday({int date, int month, int year -> new Date(year, month, date)}.call($date, $month, $year))
}

Date payDay(String $key, long $value) {
    payDay($key, {long value -> new Date(value)}.call($value))        
}
Date payDay(int $date, int $month, $year) {
    payDay($key, {int date, int month, int year -> new Date(year, month, date)}.call($date, $month, $year))
}

```

The closures must return an instance of the field (or element) type.

For a DSL Object result, source-visible converter closures are transformed onto active-session Builder production and the
returned Builder is attached to the owning relationship. Field converters for Simple Values are unaffected.

# Factory Method converters

For non-DSL values, factory-method converters continue to return the converted value and pass it to the Builder setter or
adder.

For DSL Object relationships, a source-visible static factory method returning a model through a recognizable
`Create.With`, `Create.One`, `Create.FromMap`, or adaptable `Create.From` path receives a linked Builder-producing twin.
Generated converter methods call that twin directly, attach its unsealed Builder to the active construction session, and
expose the concrete `Foo_DSL.Builder` return type. Recursive source factory calls are rebound to their own twins. The
original factory method remains unchanged for direct completed-root calls.

A factory method is either a method named `from*`, `of*`, `parse*` or (for non DSL methods) `create*` or explicitly 
annotated with `@Converter`.

An opaque or precompiled model-returning converter without an explicit `KlumBuilder<Foo>` result is not advertised on the
generated Builder API or its IDE mirror. A matching dynamic invocation throws a targeted `KlumModelException` explaining
how to migrate; unrelated unknown method names retain normal Groovy `MissingMethodException` behavior. Such producers can
be migrated by returning a concretely parameterized `KlumBuilder<Foo>` or by making their source-visible construction path
adaptable. The parent's normal closure method remains the simplest alternative:

```groovy
Foo.Create.With {
    bar {
        birthday new Date(123L)
    }
}
```

An independently completed converter result remains valid as a root result or when explicitly passed to an aggregation
`LINK`; it is not valid as new composition. [ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md)
records the Builder-producing composition protocol.

Note that having more than one factory with the same parameters might
lead to unexpected results.

# Factory classes

Additional factory classes can be declared using the `@Converters` annotation, either for a complete
dsl class or for a single field. 

By default, all valid factory methods (i.e. public static methods returning the expected type or
a subclass with the default prefixes or annotated with `@Converter`) are used as base for a converter. 

```groovy
import java.text.SimpleDateFormat

@DSL(converters = [BarUtil]) class Foo {
    Bar bar
}

class Bar {
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

As with converter methods, if the field in question is a Map of simple elements,
a key field is prepended to the parameters list.

## Customization

The `@Converters` annotation contains a couple of customization options useful
for classes of third-party libraries. Using `includeMethods`, `excludeMethods` and `excludeDefaultPrefixes`
the selection of valid factories can be customized, using `includeConstructors` all constructors of the
target class are made into converter methods as well.
