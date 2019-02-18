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

