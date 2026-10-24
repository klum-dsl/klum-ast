# Structured Input

`KlumStructuredInput` applies caller-parsed Maps, Lists and scalars through a Schema's admitted Builder operations.
It belongs to `klum-ast-runtime` and adds no JSON/YAML parser dependency. Parse the format with your chosen library,
then pass the resulting data together with an opaque source name for diagnostics.

## Read a concrete root

A Schema can expose domain operations that configure several properties:

(See: `StructuredInputDocumentaryTest#'one structured operation configures several root properties'`.)

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL

@DSL class Application {
    String host
    int port

    @Builder.Method
    void endpoint(Map<String, String> settings) {
        host = settings.host
        port = Integer.parseInt(settings.port)
    }
}
```

Java callers use the concrete Schema class:

```java
Map<String, Object> parsed = Map.of("endpoint", Map.of("host", "api", "port", "8080"));
Application app = KlumStructuredInput.readRoot(Application.class, null, parsed, "application.yaml");
```

The same route in Groovy:

(See: `StructuredInputDocumentaryTest#'one structured operation configures several root properties'`.)

```groovy
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

def parsed = [endpoint: [host: 'api', port: '8080']]
def app = KlumStructuredInput.readRoot(Application, null, parsed, 'application.yaml')
assert app.host == 'api'
assert app.port == 8080
```

The root input must be a Map with String keys. Select an exact concrete Schema; no subtype or default implementation
is chosen for an abstract type. Supply `null` as `rootKey` for an unkeyed Schema, or an explicit String for a keyed Schema.
A key member is outside domain input, including inside nested objects. The nonempty `sourceName` is diagnostic text and
is never opened as a file or URL.

Compile Schemas successfully with the current KlumAST compiler and distribute their generated classes together.
Missing or inconsistent generated structured-input metadata reports recompile/package guidance. An in-memory-only
Schema without packaged generated metadata cannot use this route.

## Apply to an active Builder

The second entry point configures an existing generated Builder and returns that exact Builder type and instance:

```java
Application_DSL.Builder<Application> same =
        KlumStructuredInput.applyToBuilder(activeApplication, parsed, "application.yaml");
```

In Groovy, the Builder can be prepared through the ordinary enclosing construction:

(See: `StructuredInputDocumentaryTest#'Holder supplies completed LINK context for later phases after applying an already-prepared Builder'`.)

```groovy
ApplicationHolder.Create.With {
    def applicationBuilder = Application.Create.AsBuilder().One()
    KlumStructuredInput.applyToBuilder(applicationBuilder, parsed, 'application.yaml')
    applicationBuilder.environment(suppliedEnvironment)
    application(applicationBuilder)
}
```

This example assumes an owned `application` field and a completed `environment` LINK needed only by later phases.
`AsBuilder().One()` has already run PostCreate and PostApply. Dependencies needed by those early callbacks must be
provided by an appropriate Schema-specific construction path before they run; assigning a LINK afterward cannot supply
an earlier callback. Structured input does not inject context or change lifecycle timing.

Application requires an active same-thread Construction session and an unsealed ordinary generated Builder.
It adds one PostApply for this application, without replaying PostCreate or completing the Builder. The enclosing
construction still performs its own ordinary application/completion work. Completed, stale, wrong-thread and Template
Builders reject. Root construction cannot start an independent nested root.

## Admitted input

| Schema entry | Parsed payload |
| --- | --- |
| String or boolean property/one-payload operation | String or Boolean of the matching kind |
| Numeric property/one-payload operation | A supported standard Number; integral targets require exact range and no fraction |
| Enum | An exact declared constant name as a String |
| Ordinary List/Map operation argument | One List/Map payload, recursively typed; never spread or treated as named arguments |
| Supported scalar/enum/data collection field | A List of declared element payloads, added through ordinary generated population |
| Supported scalar/enum/data map field | A Map of literal String keys to declared value payloads |
| Concrete owned object | A domain Map interpreted against the child's Builder |
| Map of concrete keyed owned objects | A Map whose literal String keys select children, each configured by a domain Map |

Concrete owned relationships follow the same rules when their generated Builder contracts come from compatible binary
API/Schema dependencies. Interpretation needs no Schema sources, IDE source mirrors or Javadocs.

Supported field containers are List, Set, SortedSet, NavigableSet, EnumSet, Map, SortedMap and NavigableMap.
Normal Builder mechanics manage the declared container, including set deduplication and sorted storage. Operation
arguments and nested ordinary data containers use List/Map; there is no construction of concrete/custom container
arguments or broad Collection/Iterable arguments. Custom converters and factories are outside this input grammar.

Numeric targets are primitive/boxed byte, short, int, long, float and double, plus BigInteger and BigDecimal.
Floating targets reject non-finite/overflow values. Standard numeric input values are Byte, Short, Integer, Long,
Float, Double, BigInteger and BigDecimal; custom Number subclasses reject. Numeric/boolean strings are not coerced.
An authored operation can interpret its own admitted String payload, as `endpoint` does above.

Keyed owned maps with `@Field.keyMapping` are excluded because derived keys conflict with literal structured instance
keys. Ordinary Builder DSL key mapping keeps its existing behavior. A direct keyed child uses only its existing
Schema-declared key derivation, including explicit `@Field.key` and compiler-known fixedKeys Cluster keys; there is no serialized child-key selector. LINK/reference lookup, unkeyed relationship
collections, polymorphic selection and CollectionFactory scopes are outside v1. Null at any depth, reset/clear,
arbitrary caller objects, helper/query/lifecycle names and zero/multi-payload authored operations reject.
An ordinary unannotated property named `key` is normal domain input; only an actual `@Key` member is outside that input.

Names follow compiler decisions: `@JsonIgnore(true)` excludes an entry; a nonempty literal `@JsonProperty` renames it;
otherwise the declared identity name applies. Inherited or overload-name collisions reject without choosing a winner.
An unsupported Schema member can coexist with unrelated admitted entries. These naming annotations do not enable
Jackson codecs, naming strategies, mixins, aliases or mapper coercion.

## Preparation, order and failures

The complete input is checked before any input operation or root allocation: names, shapes, types, numeric limits,
enum names and container cycles. Preparation executes no Schema initialization, callbacks, operations, converters,
constructors or enum initialization. A bad entry anywhere prevents all execution for this call. Existing active Builder
state can predate the call. Repeated acyclic shared values are allowed, and prepared containers are snapshots.

Execution follows each supplied Map's iteration order recursively and each List's element order. An insertion-ordered
Map preserves the caller's chosen order; an unordered Map exposes its own iteration order. There is no portable parser
source-order guarantee or sorting imposed by the interpreter. Reversing order-sensitive operations can change the result.
Normal lifecycle, ownership, defaults, AutoLink, LinkTo, materialization and validation remain in their usual phases.

Local input/dispatch failures use `KlumModelException`, naming the source, escaped JSON Pointer, external name, public
Builder contract and expected/actual shape or type. Pointer tokens escape `/` as `~1` and `~` as `~0`; List indices are
included. Original operation causes are retained. Late lifecycle/validation failures keep their normal framework behavior
and are not attributed to an invented last input leaf. No input provenance is retained on the completed Model.
Once Schema execution begins, ordinary side effects remain if a later operation fails; there is no rollback.

[Jackson integration](Jackson-Integration.md) remains an independent foreign-data binding route with its existing
callback and codec behavior. Structured input is opt-in and does not change named-parameter configuration or Jackson.

The executable examples live in
[`StructuredInputDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputDocumentaryTest.groovy):
“one structured operation configures several root properties” and
“Holder supplies completed LINK context for later phases after applying an already-prepared Builder”.
Related language work: [#932](https://github.com/klum-dsl/klum-ast/issues/932); optional matching JSON Schema generation
remains [#529](https://github.com/klum-dsl/klum-ast/issues/529).
