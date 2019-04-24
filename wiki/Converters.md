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

# Factory Method converters

For DSL elements / fields, converter methods can also be created in the class itself.

A converter method is a static (factory) method, returning an instance of the DSL class
(or a subclass). Setters / adders are automatically created for all
factory method of the target type.

A factory method is either a method named `from*`, `of*`, `parse*` or (for non DSL methods) `create*` or explicitly 
annotated with `@Converter`.

```groovy
import java.text.SimpleDateFormat
import java.time.Instant@DSL class Foo {
    Bar bar
}

@DSL class Bar {
    Date birthday
    
    @Converter
    static Bar readFromString(String string) {
        return create(birthday: SimpleDateFormat.dateInstance.parse(string))
    }

    static Bar fromLong(long value) {
        return create(birthday: new Date(value))
    }
}
```

results in the additional methods being created in `Foo`:

```groovy
Bar bar(long value) {
    bar(Bar.create(birthday: new Date(value)))
}
Bar bar(String value) {
    bar(birthday: SimpleDateFormat.dateInstance.parse(string))
}
```

Note that having more than one factory with the same parameters might
lead to unexpected results.

# Factory classes

Additional factory classes can be declared using the `@Constructors` annotation, either for a complete
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
        return create(birthday: SimpleDateFormat.dateInstance.parse(string))
    }
}
```

As with converter methods, if the field in question is a Map of simple elements,
a key field is prepended to the parameters list.

## Customization

The `@Converters` annotation contains a couple of customization options useful
for classes of third-party libraries. Using `includeMethods`, `excludeMethods` and `excludeDefaultMethods`
the selection of valid factories can be customized, using `includeConstructors` all constructors of the
target class are made into converter methods as well.
