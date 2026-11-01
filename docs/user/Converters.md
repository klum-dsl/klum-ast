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

(See: `ConvertersDocumentaryTest#'converts timestamp input for a simple field and map entry'`.)

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
Simple Values are unaffected. See [Behind the Curtain#builder-projection-for-custom-producers](Behind-the-Curtain.md#builder-projection-for-custom-producers) when the distinction
matters to an integration.

## Factory Method Converters

For non-DSL values, factory-method converters continue to return the converted value and pass it to the Builder setter or
adder.

For a DSL Object relationship, a recognized static factory method can be used inside the owning Model just like a normal
child creator. The same method still returns a completed object when called as a root factory:

(See: `ConvertersDocumentaryTest#'uses a named factory method as an owned DSL relationship creator'`.)

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
        Endpoint.Create.With(port: port)
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
[Behind the Curtain#builder-projection-for-custom-producers](Behind-the-Curtain.md#builder-projection-for-custom-producers).

Source-visible same-compilation converters also work in Builder lifecycle methods and in nested converter bodies. For
example, `storage('uri://bla/blub', 'uri://bli/blu')` can select `Storage.fromStrings`, whose implementation calls
qualified `Registry.fromString(...)` converters for its owned children. Builder-phase calls use active-session Builder
twins; the same `Registry.fromString(...)` call outside Builder construction remains a completed-model factory.

(See: `ConvertersDocumentaryTest#'uses fluent scalar converter syntax for an owned relationship'`.)

A factory method is named `from*`, `of*`, or `parse*`; a non-DSL factory may also be named `create*`. Alternatively,
annotate the method with `@Converter`.

Avoid multiple factory methods with the same parameter signature; selection can otherwise be ambiguous.

## Factory Classes

Declare additional factory classes with `@Converters`, either for a complete DSL class or one field.

By default, KlumAST uses public static factory methods that return the expected type or a subtype, have a recognized
prefix, or carry `@Converter`.

(See: `ConvertersDocumentaryTest#'uses a converter factory class for convention and annotation-based inputs'`.)

```groovy
import java.text.SimpleDateFormat

@Converters(BarUtil)
@DSL class Foo {
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

For example, opt into constructors when a value type has a useful multi-argument constructor.

(See: `ConvertersDocumentaryTest#'uses an opt-in URI constructor as a converter'`.)

```groovy
@Converters(includeConstructors = true)
@DSL
class RemoteService {
    URI endpoint
}

def service = RemoteService.Create.With {
    endpoint 'https', 'config.example.test', '/v1', 'stable'
}

assert service.endpoint == new URI('https', 'config.example.test', '/v1', 'stable')
```


## Structured String input

An ordinary non-key, non-DSL scalar field can accept a String through its exact, unambiguous public Builder projection.
This includes field converter closures, same-compilation domain values and separately compiled external adapters.
Existing converter discovery and generation rules still apply: a factory without a generated projection is not callable,
and competing converters that collide during generation reject. The ordinary domain-value setter remains available.

(See: `StructuredConverterOriginTest#'a field converter accepts the same duration String through ordinary and structured construction'`.)

```groovy
import java.time.Duration

@DSL class Service {
    @Field(converters = [{ String value -> Duration.parse(value) }])
    Duration timeout
}

def direct = Service.Create.With { timeout 'PT30S' }
def structured = KlumStructuredInput.readRoot(Service, [timeout: 'PT30S'], 'service.yaml')
assert structured.timeout == direct.timeout
assert structured.timeout == Duration.ofSeconds(30)
```

These routes produce `type: string` and the field's prose/examples in JSON Schema. Conversion runs only during ordinary
Builder invocation; its failures retain the structured input source/path and original cause. Schema generation never
executes converters to infer constraints. Domain-value field constraints remain descriptive and do not become assertions
on the raw String.

C1's existing parameter-constraint authority remains narrower: a separately compiled public, non-generic domain with
one supported declared public String constructor (enabled by `@Converters(includeConstructors=true)`) or static factory
returning exactly that domain. Only those qualified routes inspect the selected compiled parameter's facts.

(See: `CompiledConverterInputTest#'reads a separately compiled port converter without coercion or runtime Pattern enforcement'`.)

```groovy
// Compile and package this non-DSL domain before compiling Service.
import jakarta.validation.constraints.Pattern

class PortNumber {
    final String raw
    PortNumber(@Pattern(regexp = '[0-9]{1,8}') String raw) { this.raw = raw }
}

// In the separately compiled Schema:
@DSL class Service {
    @Struct.Name('port-number')
    @Converters(includeConstructors = true)
    PortNumber port
}

// The Model Writer supplies a String scalar, not a nested parameter object.
def service = KlumStructuredInput.readRoot(Service, ['port-number': '1234'], 'ports.yaml')
assert service.port.raw == '1234'
```

Quote numeric YAML values: `port-number: "1234"`. Numbers, null and other non-String values reject structurally; an
absent optional field is allowed. The selected generated public Builder overload performs conversion during ordinary
construction. Reading contracts and generating JSON Schema never execute the converter or initialize the domain.

The selected compiled parameter owns its Jakarta Pattern facts and optional `@Struct.ConstraintProjection` policy. PROJECT
is the default; TEXT_ONLY describes complete constraints; OMIT suppresses their constraint-derived output. Field policy
cannot suppress or override parameter facts. The field retains names, exclusion, prose, examples, deprecation and its
own domain-value constraints. See [converter Pattern projection](Json-Schema.md#compiled-converter-parameters).

Overloaded/default-generated, bridge/synthetic, generic and varargs converter sources remain outside this bounded route.
Collection, map and DSL-object conversions remain outside this structured-input feature. CharSequence invocation is
separate. An unrelated authored overload does not suppress the generated String route; competing external String names
use the [shared selection rules](Structured-Input.md#select-a-string-operation).
Non-overridden binary Schema inheritance preserves the original declaring Builder and converter association;
replacement fields are classified afresh.
A descendant declaration of the same Builder method signature excludes the inherited converter input, even when the
override is renamed or ignored for structured input. Ordinary Builder override dispatch remains unchanged.

Keep compiler/runtime/plugin, domain and Schema artifacts matched. Recompile the Schema hierarchy after changing a
participating domain contract. Missing or corrupt selection/parameter facts or incomplete API classpaths fail before
interpretation or replacing generated output, including suppressed or excluded members. Artifact locations identify the
paired declarations; they are not integrity checks or stale-JAR detection.

Parameter annotations do **not** add runtime validation. For example, `'not digits'` still reaches this constructor:
automatic parameter enforcement remains separate in [#971](https://github.com/klum-dsl/klum-ast/issues/971).
