# Structured Input

Structured input applies caller-parsed Maps, Lists and scalars through a Schema's admitted Builder operations.
The supported public boundary is generated Factory creation and Builder application; the interpreter is framework
internal. Use `Create.FromStruct` for completed roots, inherited `KlumBuilder.applyStruct` for an active Builder,
and [`Create.Template.FromStruct`](Templates.md#structured-input-templates) for reusable keyless Template bodies.

Parsing remains caller-owned. No JSON/YAML parser, file or URL loading is added. An optional source name is opaque
nonempty diagnostic text, never a locator or a model key.

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

For that Schema, the parsed configuration is a Map such as:

```groovy
def parsed = [endpoint: [host: 'api', port: '8080']]
def application = Application.Create.FromStruct(parsed, 'application.yaml')
assert application.host == 'api'
assert application.port == 8080
```

The admitted `endpoint` operation sets `host` to `api` and `port` to 8080. Use a Factory selecting a concrete Schema; its ordinary default-implementation selection still applies. Root input must be a Map with String keys. A self-contained
keyed document supplies its actual key member; a caller-pre-keyed body omits it. Supplied direct children retain their
own positional key rules in either form.

Compile Schemas successfully with the current KlumAST compiler and distribute their generated classes together.
Missing or inconsistent generated structured-input metadata reports recompile/package guidance. An in-memory-only
Schema without packaged generated metadata cannot use this route.

## Factory roots

The common Factory signatures are `T FromStruct(Map<?, ?> document)` and
`T FromStruct(Map<?, ?> document, String sourceName)`. Keyed Factories additionally expose
`T FromStruct(String rootKey, Map<?, ?> body)` and the trailing `String sourceName` overload.
All return completed Models after ordinary lifecycle, materialization and validation.

Java callers use the generated Factory directly (for the `Service` Schema below):

```java
Service document = Service.Create.FromStruct(Map.of("service-id", "catalog", "host", "api"));
Service explicit = Service.Create.FromStruct("catalog", Map.of("host", "api"), "request");
```

(See: `StructuredFactoryTest#'Factory documents carry their own root identity'` and
`StructuredFactoryTest#'explicit root keys configure one ordinary lifecycle'`.)

```groovy
@DSL class Identity {
    @Key @Struct.Name('service-id') String name
}
@DSL class Service extends Identity { String host }

def document = Service.Create.FromStruct(['service-id': 'catalog', host: 'api'])
def explicit = Service.Create.FromStruct('catalog', [host: 'api'], 'request')
assert document.name == explicit.name
```

The explicit-key body forbids `service-id`, even if its value equals the argument. Both root forms run exactly one
root PostApply, with identity available before PostCreate. `sourceName` is optional, opaque diagnostic text only;
null and empty explicit labels reject. Ordinary calls generate a label identifying the Factory capability.
File-like and URL-like labels are never opened, normalized or used as identity. Factory access initializes its ordinary
`Create` field; the internal contract inspector's no-user-code guarantee is a tooling property, not a promise to suppress
normal class initialization when accessing a Factory.

`FromStruct` does not appear on `AsBuilder()` or relationship factories. `Create.FromMap` remains the separate
copy/type-hint/conversion/custom-Factory route; [managed Jackson](Jackson-Integration.md) retains its independent contract.

## Metadata protocol

Structured input and JSON Schema generation require Schemas compiled with a matching supported metadata protocol.
Older or incomplete Builder contracts are rejected; recompile the participating Schema artifacts.

The current compiler explicitly emits `GeneratedInput.Schema.version=1` on every declaring public Builder. Both consumers
require exactly that version and complete naming, exclusion, constraint, projection, source-provenance, key, literal-alternative association/count and inert example-array facts.
`Schema.version` is the sole protocol version; `LiteralAlternative` has no separate version member. Recompile participating
Schemas with matching compiler/runtime/plugin artifacts; removed or unknown members have no compatibility decoder.
The rule also applies to inherited and participating owned contracts across project/JAR boundaries. Missing, older,
future, malformed or incomplete metadata fails before input execution; a version marker alone does not establish
completeness. There is no identity-only mode, annotation reconstruction or partial legacy support. The admitted ownership closure is checked
before execution, including omitted owned inputs; the per-read cache terminates recursive contracts. Excluded targets
and unrelated classpath classes are not scanned.

## Type naming profiles

Select `@Struct.Naming` on a Schema type to use the same exact member tokens in structured input and generated
[JSON Schema](Json-Schema.md#type-naming-profiles). Its required value is `Struct.Naming.Profile.IDENTITY` or
`Struct.Naming.Profile.KEBAB_CASE`; there is no default value or caller/plugin selector.

(See: `InputNamingProfileTest#'choose kebab member names while keeping owned values and data keys independent'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct

@DSL class ServiceBase { String displayName }
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase {
    String serviceName
    Map<String, String> labels
    Details serviceDetails
}
@DSL class Details { String nativeName }

def parsed = ['display-name': 'Catalog', 'service-name': 'catalog', labels: ['Team/Blue': 'ops'],
              'service-details': [nativeName: 'own policy']]
// Configures serviceName as 'catalog'; the data key 'Team/Blue' stays literal.
```

An unannotated child inherits an explicit ancestor profile. Repeating that profile is permitted; a different explicit
choice rejects the subtype with both declaring types/artifacts in the diagnostic. An explicit `IDENTITY` also binds
descendants. When every ancestor is unannotated, the first annotated child chooses its complete effective grammar,
including inherited members; the standalone ancestor keeps native spelling. Owned values resolve their own type hierarchy,
as `Details` does above. Ownership does not cascade the owner's profile.

`KEBAB_CASE` inserts a hyphen before ASCII uppercase after ASCII lowercase/digits, and at an uppercase acronym-to-word
boundary, then lowercases only ASCII A-Z. It uses original adjacency: `URLValue` becomes `url-value`, `XML2Parser`
becomes `xml2-parser`, and `version2URL` becomes `version2-url`. Existing separators and non-ASCII characters remain
unchanged: `already_snake` stays as written and `ÅngströmURL` becomes `Ångström-url`. There is no Unicode normalization,
transliteration or locale rule. Malformed UTF-16 in transformed native metadata is rejected.

Profiles affect only eligible field, construction-key and admitted authored-operation member names. They add no native-name
alias. Data Map keys, keyed-instance keys, fixed identity values, enum values and operation Map-payload keys remain literal.
Existing supported nonempty `@JsonProperty` names remain exact; they are never transformed. Empty `@JsonProperty` falls
back to native/profile spelling. `@JsonIgnore`/`@JsonIgnore(true)` excludes before naming, and `@JsonIgnore(false)` is neutral.
Naming does not admit otherwise unsupported declarations. Use [literal names and exclusion](#literal-names-and-exclusion)
for Klum-owned overrides without a Jackson dependency.

Under a nonidentity profile, collisions between eligible member tokens reject the type with both source declarations,
before input executes. A literal rename can remove the collision. Construction-key collisions still depend on whether the
input position supplies the key as a member; outer instance keys and explicit root keys retain authority. No-profile and
identity collision behavior is unchanged. Match compiler/runtime/plugin versions and recompile a selected type hierarchy;
missing, unknown or malformed required naming facts fail with recompile/package guidance. See
[migration](Migration.md#type-naming-profiles).

## Literal names and exclusion

Use `com.blackbuild.klum.ast.Struct.Name` and `Struct.Ignore` on source fields (including construction keys) or already
admitted authored `@Builder.Method` operations. `@Struct.Name` requires a nonempty exact String; spaces, punctuation and
case are preserved. `@Struct.Ignore` is a marker. Both have runtime retention and FIELD/METHOD targets.

(See: `InputLiteralTest#'choose exact external names and exclude internal configuration'`.)

```groovy
import com.blackbuild.klum.ast.*

@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
@DSL class Service {
    @Struct.Name('service.id') String serviceName
    @Struct.Ignore String internalNote
    @Builder.Method @Struct.Name('configure/URL')
    void configureEndpoint(String value) { serviceName = value }
    @Builder.Method @Struct.Ignore
    void configureInternally(String value) { internalNote = value }
}

def parsed = ['service.id': 'catalog'] // Configures serviceName as 'catalog'.
// Ordinary Groovy configuration keeps its authored names, including ignored members.
def configured = Service.Create.With { serviceName 'groovy'; configureInternally 'private' }
assert configured.internalNote == 'private'
```

Eligibility and exclusion come first, then a compatible explicit literal, then the inherited/local type profile, then
identity. Naming cannot admit unsupported types or operations. A literal adds no native/profile alias and can separate
entries that would collide under a nonidentity profile. Unresolved nonidentity collisions fail with both source declarations;
existing identity collision rejection is unchanged.

Supported `@JsonProperty`/`@JsonIgnore` bridging remains available. Equal nonempty `Struct.Name` and `JsonProperty` names
coalesce; different names fail deterministically with both annotation origins, **even when excluded**. Empty `JsonProperty`
falls back; empty `Struct.Name` is invalid. `Struct.Ignore` or true/absent-value `JsonIgnore` excludes; false `JsonIgnore` cannot
undo exclusion. Exclusion and a compatible rename can coexist.

Both consumers use compiler-folded facts on the exact public Builder declarations. Exclusion removes members from
structured input and generated JSON Schema; it changes no ordinary Groovy configuration, runtime validation, defaults or
completed Model API. An ignored required construction key still needs an external root key, Schema derivation or keyed-Map
identity; it cannot make an otherwise self-contained key position valid. Data/instance keys remain literal.
Managed Jackson import remains independently configured: use the Jackson literal too when both routes need one spelling.
There is no automatic mapper adapter. Recompile Schemas and align compiler/runtime/plugin versions; see
[migration](Migration.md#literal-names-and-exclusion).

## Construction keys

Self-contained keyed documents require the actual Schema `@Key String` member. A supplied direct owned keyed child requires its key too, at any depth, unless its
relationship supplies identity through `@Field.key` or a fixed Cluster key. An absent optional child remains absent.
Unkeyed roots have no synthetic identity property.

(See: `StructuredInputKeyDocumentaryTest#'self-contained documents carry root and direct-child keys while Maps supply outer keys'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.fasterxml.jackson.annotation.JsonProperty

@DSL class Deployment {
    @Key String name
    Service service
    Map<String, Service> services
}
@DSL class Service {
    @Key @JsonProperty('service-id') String name
    String image
}
```

The self-contained parsed document includes root and direct-child identity:

```groovy
def parsed = [name: 'production',
              service: ['service-id': 'catalog', image: 'catalog:1.0'],
              services: [worker: [image: 'worker:1.0']]]
```

Root identity is `production`, direct-child identity is `catalog`, and the literal Map entry supplies `worker`.

This Map could come from YAML such as the following. The caller owns parsing; the source name is only diagnostic text.

```yaml
name: production
service:
  service-id: catalog
  image: catalog:1.0
services:
  worker:
    image: worker:1.0
```

| Object position | Identity authority | Actual key member in that object's payload |
| --- | --- | --- |
| Self-contained keyed root | Input | Required String |
| Caller-pre-keyed root body | Explicit `rootKey` | Forbidden, even if equal |
| Active Builder body | Allocated Builder | Forbidden, even if equal |
| Supplied direct keyed child | Input, unless Schema supplies identity | Required String |
| Fixed/derived-key direct child, including fixed Cluster fields | Schema relationship | Forbidden, even if equal |
| Keyed owned Map value | Outer literal Map entry | Forbidden, even if equal |

Thus `services.worker` must omit `service-id`: `worker` already supplies its identity. A nested direct child inside
that body still requires its own key when its relationship has no provider. Key names are resolved on the actual
compiler-selected declaration, including binary inherited keys: `@Struct.Ignore` or `@JsonIgnore(true)` excludes it, a compatible nonempty literal
`@Struct.Name` or `@JsonProperty` renames it, then the effective type profile/native name applies. Empty `@JsonProperty` uses that fallback;
`@JsonIgnore(false)` leaves the declaration admitted under its otherwise resolved name. Renames add no alias.
An ordinary unannotated `key` property remains normal input; an independently
admitted operation with an old/native spelling keeps its meaning.

Key values must be exact Strings. Empty Strings are accepted; null, numbers, booleans, GStrings and containers reject
without coercion. An ignored key cannot support a self-contained root or an input-key direct child. An external-name
collision between that required key and an admitted operation rejects before allocation, naming both declarations.
Keyless positions retain their ordinary operation admission. Use an explicit root key, a Schema key provider or a keyed
Map where appropriate for an ignored key. A provider is never evaluated to compare an inner key.

A caller-pre-keyed root body leaves out the root key while retaining the supplied direct child's identity:

```groovy
def body = [service: ['service-id': 'catalog', image: 'catalog:1.0']]
```

The caller supplies root identity separately through the keyed Factory argument or an already prepared Builder.

Compile and ship matching compiler/runtime/plugin versions with current generated key facts. Missing facts produce
recompile/package guidance; the interpreter does not guess key names or derive identity from a filename or URL.
`Create.FromMap` retains its separate copy/type-hint behavior. Schema generation adds no runtime entry point or file/URL loading API.

For editor/validator tooling of caller-pre-keyed bodies, explicitly select the
[Builder-body schema artifact](Json-Schema.md#caller-pre-keyed-builder-bodies). Default root schemas continue to describe
self-contained input; generation never derives identity from source names.

## Apply to an active Builder

The public capability is void `applyStruct(Map<?, ?> body[, String sourceName])` on `KlumBuilder`, inherited
by generated Builder contracts. It configures the existing receiver and does not replace or complete it. The interpreter remains internal. See
[the accepted signature and migration contract](../adr/0032-builder-oriented-structured-input.md#public-factory-creation-and-builder-application--953).

A caller prepares the Builder through ordinary construction before applying a parsed body. The documentary
fixture qualifies the grammar and lifecycle through the public Builder capability:
`StructuredInputDocumentaryTest#'Holder supplies completed LINK context for later phases after applying an already-prepared Builder'`.

### Active Builders

Java helpers can preserve their exact Builder type while calling the void capability:

```java
static <B extends KlumBuilder<?>> B configure(B target, Map<?, ?> body, String sourceName) {
    target.applyStruct(body, sourceName);
    return target;
}
```

(See: `StructuredFactoryTest#'active Builders apply structured bodies without completing construction'`.)

```groovy
def service = Service.Create.With('catalog') {
    applyStruct([host: 'first'])
    applyStruct([host: 'api'], 'request')
}
assert service.host == 'api'
```

Each successful `applyStruct` adds one PostApply; the enclosing `With` performs its own PostApply afterward.
The common Map signatures are framework capabilities, excluded from structured operations and absent from completed Models.
`applyStruct(Map)` and `applyStruct(Map, String)` are reserved: a colliding Schema method, field configurator or
default-argument overload fails compilation. Rename the conflicting declaration; distinct overloads remain legal.

That fixture uses an owned `application` field and a completed `environment` LINK needed only by later phases.
`AsBuilder().One()` has already run PostCreate and PostApply. Dependencies needed by those early callbacks must be
provided by an appropriate Schema-specific construction path before they run; assigning a LINK afterward cannot supply
an earlier callback. Structured input does not inject context or change lifecycle timing.

The target payload cannot carry its actual construction key; the target already owns identity.
Nested direct children continue to follow [construction keys](#construction-keys).

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
| Zero-argument `@Builder.Method` command | An empty Map (`operation: {}` in YAML) |
| Ordinary List/Map operation argument | One List/Map payload, recursively typed; never spread or treated as named arguments |
| Supported scalar/enum/data collection field | A List of declared element payloads, added through ordinary generated population |
| Exact `List<T>` with one admitted String element converter | A List of Strings, each passed to its selected generated element adder |
| Exact `Map<String, T>` with one admitted String value converter | A Map of literal String keys to Strings, each passed to the generated value adder |
| Supported scalar/enum/data map field | A Map of literal String keys to declared value payloads |
| Admitted concrete virtual field | A domain Map configured before the virtual sink consumes the child Builder |
| Ordinary owned unkeyed `List<T>`, `Set<T>`, `SortedSet<T>`, `NavigableSet<T>` | A List of concrete child-body Maps, executed in supplied order |
| Concrete owned object | A domain Map interpreted against the child's Builder |
| Map of concrete keyed owned objects | A Map whose literal String keys select children, each configured by a domain Map |
| One literal CollectionFactory alternative for an abstract keyed Map value | A closed literal alias mapping, then outer String instance keys, then concrete child bodies |

Concrete owned relationships follow the same rules when their generated Builder contracts come from compatible binary
API/Schema dependencies. Interpretation needs no Schema sources, IDE source mirrors or Javadocs.

Supported field containers are List, Set, SortedSet, NavigableSet, EnumSet, Map, SortedMap and NavigableMap.
Normal Builder mechanics manage the declared container, including set deduplication and sorted storage. Operation
arguments and nested ordinary data containers use List/Map; there is no construction of concrete/custom container
arguments or broad Collection/Iterable arguments. An ordinary non-DSL scalar field also admits an exact, unambiguous
[generated String converter route](Converters.md#structured-string-input), including field closures, same-compilation
values and separately compiled external adapters. This uses an already-generated public Builder projection; it does not
add converter discovery or make omitted/colliding projections callable.

Converter input is a String scalar at the field's effective external name. Quote numeric YAML values; neither numeric
coercion nor null admission is added. An absent optional field remains allowed. C1-qualified separately compiled domain
routes retain their selected-parameter facts and policies. Other origins describe a String plus field prose/examples,
without inferring a grammar by executing conversion or copying destination-field assertions onto the raw String.
Missing/malformed required associations or C1 facts fail during static contract verification, even behind naming exclusion
or OMIT. Recompile participating Schemas after domain contract changes. Pattern projection is an authoring aid;
structured import does not enforce parameter constraints or automatically validate input against JSON Schema (#971).

Numeric targets are primitive/boxed byte, short, int, long, float and double, plus BigInteger and BigDecimal.
Floating targets reject non-finite/overflow values. Standard numeric input values are Byte, Short, Integer, Long,
Float, Double, BigInteger and BigDecimal; custom Number subclasses reject. Numeric/boolean strings are not coerced.
An authored operation can interpret its own admitted String payload, as `endpoint` does above.

### Converter-backed List elements

An exact `List<T>` of non-DSL scalar values admits one compiler-selected, public element converter taking an exact
String. Field closures, ordinary factories, enabled constructors and adapters use their existing generated element
adder. Native scalar Lists retain their declared element grammar. Competing String or String/CharSequence routes,
missing generated methods and descendant overrides reject; converter discovery and overload preferences are unchanged.

(See: `StructuredListConverterTest#'converts duration List elements independently in supplied order'`.)

```groovy
import com.blackbuild.klum.ast.Converters
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Field
import java.time.Duration

@DSL class Schedule {
    @Converters(excludeMethods = ['parse'])
    @Field(converters = [{ String text -> Duration.parse(text) }])
    List<Duration> intervals
}

def schedule = Schedule.Create.FromStruct(
    [intervals: ['PT5S', 'PT2S']], 'schedule.yaml')
assert schedule.intervals == [Duration.ofSeconds(5), Duration.ofSeconds(2)]
```

Each String converts independently in supplied List order and uses ordinary element population. Initializer/default
entries remain, and an empty List performs no element calls. On the first malformed element or converter failure,
earlier work remains and later elements/operations are never invoked. Diagnostics retain the source, indexed pointer
(for example `/intervals/1`) and original converter cause. Null, numeric, boolean, GString, mutable CharSequence,
objects, Maps and nested Lists reject as elements; the container itself must be a List.

Generated JSON Schema emits `type: array` with `items: {type: string}` without running user code. Element converter
parameter constraints are not projected in this slice; C1 scalar-field assertions retain their existing behavior.
A String that the converter cannot interpret is still structurally valid. Import does not automatically validate JSON
Schema. Recompile matched Schema/compiler/runtime/plugin artifacts for the authoritative element association.

CharSequence-only element routes and other converter-backed collection shapes remain deferred under [#952](https://github.com/klum-dsl/klum-ast/issues/952).

### Converter-backed Map values

An exact `Map<String, T>` of non-DSL scalar values admits one compiler-selected public value converter taking an exact
String. Each raw String value calls the existing generated adder with its literal String data key. Field closures,
ordinary factories, enabled constructors and adapters follow the same selection rules as
[List elements](#converter-backed-list-elements). Native scalar Maps retain their declared value grammar.

(See: `StructuredMapConverterTest#'converts duration Map values independently under literal String keys'`.)

```groovy
import com.blackbuild.klum.ast.Converters
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Field
import java.time.Duration

@DSL class Schedule {
    @Converters(excludeMethods = ['parse'])
    @Field(converters = [{ String text -> Duration.parse(text) }])
    Map<String, Duration> intervals
}

def schedule = Schedule.Create.FromStruct(
    [intervals: [primary: 'PT5S', retry: 'PT2S']], 'schedule.yaml')
assert schedule.intervals == [primary: Duration.ofSeconds(5), retry: Duration.ofSeconds(2)]
```

Traversal follows the supplied Map's iteration order. Ordinary entry population preserves initializer/default entries,
replaces an existing key's value according to ordinary Builder behavior, and performs no calls for an empty Map. It
never calls a whole-container converter or replacement setter. Data keys remain literal even with an external naming
profile or rename. Non-String keys reject without coercion. Custom `@Field.keyMapping`, non-String declared keys,
CharSequence-only routes, ambiguous or missing generated routes, descendant overrides and owned DSL values are excluded.

Each reached value must be a String: null, numeric, boolean, GString, mutable CharSequence, arbitrary Object, Map and List
values reject. A converter failure retains its original cause, source and escaped key-specific pointer, for example
`/intervals/bad~1~0key` for key `bad/~key`. Earlier entries and operations remain applied; later work is never invoked.

JSON Schema emits `type: object` with `additionalProperties: {type: string}` without initializing the Schema or running
converters. This describes String structure, including arbitrary String contents; successful conversion is a runtime
concern. Value converter parameter assertions are not projected; scalar C1 behavior remains unchanged. Recompile the
Schema hierarchy with matched compiler/runtime/plugin artifacts for authoritative route/completeness metadata.

## Owned child sequences

Ordinary owned relationship fields declared as `List<T>`, `Set<T>`, `SortedSet<T>` or `NavigableSet<T>` admit a supplied
List of child-body Maps when `T` is a concrete, nongeneric, unkeyed DSL type with its complete ordinary generated creator family. Same-Schema replacements or descendant overrides of any
ordinary creator spelling exclude that relationship from sequence input.
Each mapping creates a fresh child in the parent's Construction session. Normal preparation applies initializers and
child Templates, runs PostCreate, interprets the body, runs PostApply, then attaches through the ordinary collection path.
An empty sequence makes no child calls; existing/default entries remain.

(See: `StructuredOwnedSequenceTest#'creates ordinary owned children from supplied body sequences'`.)

```groovy
@DSL class Service { List<Route> routes }
@DSL class Route { String path }

def service = Service.Create.FromStruct([
    routes: [[path: '/health'], [path: '/metrics'], [path: '/health']]
])
assert service.routes*.path == ['/health', '/metrics', '/health']
```

The corresponding caller-parsed YAML form is:

```yaml
routes:
  - path: /health
  - path: /metrics
  - path: /health
```

The supplied sequence is an operation stream. Every reached mapping runs in supplied order, including equal mappings.
The destination collection owns insertion, equality, comparator behavior, final cardinality and iteration order. For
example, a Set can collapse equal completed children; a sorted Set can reject a comparator-equivalent Builder after its
PostApply, so only retained children receive later graph lifecycle work. No deduplication, sorting or comparator
preflight is performed by Struct. Ordinary natural-order insertion can fail because generated Builders do not inherit
Model Comparable behavior. A comparator must already work with the ordinary Builder and completed Model stages;
Struct does not repair initializer typing or infer comparator eligibility.

The first reached body, lifecycle or insertion failure stops later entries and operations, retaining earlier effects.
Diagnostics identify the zero-based **supplied input index**, even if a Set has collapsed earlier entries or changes
iteration order. Existing Builder placement paths keep their ordinary size-based behavior and are not input indices.
No rollback occurs: an insertion failure can leave the failed Builder claimed without a placement path, exactly as in
ordinary Groovy. Materialization failures keep their normal lifecycle attribution.

`Create.Template.FromStruct` can define these sequences through ordinary Template construction. Applying a Template
copies its retained recipe into fresh recipients; it does not replay the original supplied sequence or entries already
collapsed while defining that Template. Initial/default/template semantics and recipient ownership remain ordinary.
See `StructuredOwnedSequenceTest#'Template definition and fresh recipients preserve ordinary recipe semantics for #declaration'`.

This route excludes keyed children, abstract/subtype alternatives, CollectionFactory creators, custom/specialized
collection declarations, EnumSet, LINK/OPTIONAL_LINK and scalar converter elements. Unknown child members, invented keys,
null, scalar and sequence child bodies reject. Nested child members can use their independently admitted grammar; this
adds no arbitrary recursive collection language. The [JSON Schema](Json-Schema.md#owned-child-sequences) describes the
Struct body grammar rather than every expression available in Groovy or the destination collection's final semantics.

## Other relationship boundaries

Keyed owned maps with `@Field.keyMapping` are excluded because derived keys conflict with literal structured instance
keys. Ordinary Builder DSL key mapping keeps its existing behavior. A direct keyed child uses its actual key member when the relationship has no Schema provider;
explicit `@Field.key` and compiler-known fixedKeys Cluster keys keep Schema authority and reject inner identity. LINK/reference lookup, keyed relationship
sequences, arbitrary polymorphic selection and other CollectionFactory scopes remain outside the admitted grammar. The
[single literal alternative](#literal-collection-alternative) below is the bounded exception. Null at any depth,
arbitrary caller objects, helper/query/lifecycle names and multi-payload authored operations reject.
Zero-argument domain commands have only their explicitly authored behavior; there is no implicit reset/clear operation.
An ordinary unannotated property named `key` is normal domain input; actual `@Key` members follow the position-specific rules above.

Names follow compiler decisions: exclusion, compatible `@Struct.Name`/`@JsonProperty` literal, effective type profile,
then identity. See [literal names and exclusion](#literal-names-and-exclusion). String candidates follow
[operation selection](#select-a-string-operation); unresolved collisions reject without choosing a winner.
An unsupported Schema member can coexist with unrelated admitted entries. These naming annotations do not enable
Jackson codecs, naming strategies, mixins, aliases or mapper coercion.

## Literal collection alternative

A default owned `Map<String, Element>` relationship can admit one explicitly declared literal alternative when `Element`
is an abstract keyed DSL type and the selected subtype is concrete and nongeneric. Both use the same inherited String
construction key, with an exact generated public alias creator. The compiler records that route; structured input cannot choose a
Class or Factory. Matching custom Factory methods are excluded when they occupy the literal configuration route. Authored or overridden
collection scopes are excluded too.

(See: `StructuredLiteralAlternativeTest#'configures a concrete element through its literal collection alias'`.)

```groovy
@DSL class Catalog {
    @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
}
@DSL abstract class Element {
    @Key String name
    @Owner Catalog owner
    String value
}
@DSL class ConcreteElement extends Element {}

// Ordinary Model authoring:
Catalog.Create.With {
    elements { fixed('primary') { value 'configured' } }
}
// The corresponding caller-parsed input:
def catalog = Catalog.Create.FromStruct([elements: [fixed: [primary: [value: 'configured']]]])
assert catalog.elements.primary.name == 'primary'
assert catalog.elements.primary instanceof ConcreteElement
assert catalog.elements.primary.owner.is(catalog)
```

A YAML parser would supply that same Map for:

```yaml
elements:
  fixed:
    primary:
      value: configured
```

`fixed` is the exact literal alias. `primary` is the authoritative outer String construction key and is available before
child PostCreate. Omit the child's actual key member from the body: both equal and conflicting inner identities reject
when reached. Empty relationship or alias mappings create no children; `fixed: {primary: {}}` creates a child with an
empty body. Every actual instance key must be a String; no key is inferred from a body member.

The relationship field follows ordinary external naming, exclusion and collision rules. Alias and instance keys remain
literal. Child member names follow the concrete subtype's own input contract. Caller iteration order is preserved across
aliases, instance entries and child members. A reached failure retains earlier effects, skips later operations and does
not attach a failing fresh child. Reconfiguration of an existing compatible Builder retains its identity and prior
attachment; an incompatible existing type fails through the ordinary explicit-type route. Each successful child call runs
one PostApply inside the existing Construction session. Active Builder input shares this grammar; Template Factory
input retains the ordinary deferred recipe lifecycle.

Unknown aliases, class-name selectors, key-first wrappers, scalar/List/null envelopes and non-mapping child bodies reject.
Multiple literal alternatives, auto-discovered names, generic targets, unkeyed collections, LINK relationships and custom
key mapping are outside this family. Existing concrete keyed-Map syntax is unchanged. YAML duplicate-key rejection belongs
to the parser. Recompile participating Schema artifacts to obtain complete association metadata; corruption fails during
contract verification even when the relationship is omitted. [JSON Schema](Json-Schema.md#literal-collection-alternative)
projects the same closed structural shape.

## Concrete virtual Builder scopes

A public `@Field void method(ConcreteType value)` can accept a nested mapping when its target is an unkeyed,
nongeneric concrete DSL type and the compiler can associate it with one ordinary generated creator. The `@Field`
annotation must have no explicit options. The interpreter enters the existing Construction session, configures the
child Builder, runs its ordinary `PostApply`, then invokes the virtual sink. It does not create a completed child Model
independently. Each invocation creates a fresh virtual child, as ordinary virtual-field creation does.

(See: `StructuredVirtualScopeDocumentaryTest#'a repository mapping configures its Builder before the virtual field consumes it'`.)

```groovy
@DSL class Registry {
    List<String> urls
    @Field void repository(Repository config) {
        urls << config.url
    }
}
@DSL class Repository { String url }

def ordinary = Registry.Create.With {
    repository { url 'https://packages.example' }
}
def mapped = Registry.Create.FromStruct([repository: [url: 'https://packages.example']])
assert mapped.urls == ordinary.urls
assert mapped.urls == ['https://packages.example']
```

Literal names, naming profiles, exclusions and external-name collision rules still apply. An unchanged inherited scope
retains its association. Redeclared virtual sinks are excluded where ordinary virtual-setter selection is not unique.
Keyed, generic, abstract, customized, overloaded or missing/generated-creator replacements are outside this family.
An authored closure method does not become a mapping scope merely because it has `@DelegatesToBuilder` metadata.
Other CollectionFactory families, polymorphic selectors and arbitrary closure adaptation remain unsupported.

Earlier siblings can already have run when a virtual child fails. A child-body failure prevents that sink and later
operations; a sink failure keeps its original cause and identifies the source label and scope path. Lifecycle failures
retain ordinary attribution. Calling a no-argument creator and then `applyStruct` on its result is not equivalent:
the creator has already run `PostApply` and the sink before returning.

Recompile Schemas with matched compiler/runtime/plugin artifacts for the required exact association facts. The
[JSON Schema](Json-Schema.md#concrete-virtual-scopes) describes the closed child body, without running the sink or
promising that construction will succeed. Existing Factory, active-Builder and Template entry modes retain their
ordinary lifecycle rules.

## Select a String operation

`@Struct.Operation` marks one Schema-authored method with an existing public Builder projection. For each effective
external name, one eligible marked String operation wins; multiple eligible marks fail. Without a mark, there must be
exactly one candidate. Selection is fixed while reading the compiled contract, before seeing any input value.

(See: `StructuredOperationSelectionTest#'select the public endpoint operation before interpreting its String payload'`,
related to [#952](https://github.com/klum-dsl/klum-ast/issues/952).)

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct

@DSL class Service {
    @Struct.Ignore String host

    @Struct.Operation @Builder.Method
    void endpoint(String value) { host = 'primary:' + value }

    @Struct.Name('endpoint') @Builder.Method
    void backupEndpoint(String value) { host = 'backup:' + value }
}

def parsed = [endpoint: 'api.internal'] // Selects endpoint; host becomes 'primary:api.internal'.
```

Only `endpoint` is an external input name here. Ordinary Groovy/Java calls keep their normal callable methods. Exclusion,
unsupported roles and missing public projections cannot be repaired with a mark. Place the marker on a method, never a
field: it cannot choose among several generated converters. An authored marked wrapper can expose an exact String or CharSequence route.

Inherited String operations retain their declaring contract. A String override replaces the parent operation's name and selection
intent, including when the override is renamed or ignored. Overriding an inherited String field's String input signature
also excludes the parent's external field route; overriding an unrelated overload leaves that String route intact.
Existing C1 converter routes remain excluded when a descendant
overrides their signature; the parent's converter constraints cannot describe different code.

A selected String route rejects numeric, null, GString and mutable CharSequence payloads without trying another overload.
Competing String/CharSequence projections have no implicit specificity preference: mark one eligible operation.
An exact selected CharSequence signature receives the original String; caller-supplied CharSequence objects are not input
Strings. Generated String and CharSequence converter origins follow the field rules above. The generated JSON Schema describes only the
selected operation's String grammar, prose and examples; constraints from a superseded field/converter do not transfer.

Recompile all participating Schema hierarchies with matching compiler/runtime/plugin artifacts. Explicit operation counts
and selection facts are required even for unmarked or excluded operations; missing or inconsistent facts fail closed.
The development metadata protocol remains version 1. See [migration](Migration.md#structured-string-operation-selection).

## Zero-argument commands

A public Schema-authored `@Builder.Method` with no arguments is a command. Supply an empty mapping to invoke its exact
public Builder operation once. Omission makes no call. Its return value, if any, is ignored. The method body defines its
behavior: KlumAST adds no reset, clear, scheduling or repeated-call semantics.

(See: `StructuredCommandTest#'an empty mapping invokes the domain reset command once'`,
related to [#952](https://github.com/klum-dsl/klum-ast/issues/952).)

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL

@DSL class Service {
    int timeout = 30
    @Builder.Method void resetTimeout() { timeout = 30 }
}

def parsed = [timeout: 90, resetTimeout: [:]] // Sets 90, then invokes resetTimeout, yielding 30.
```

The corresponding YAML command member is:

```yaml
resetTimeout: {}
```

A blank YAML value (`resetTimeout:`) and explicit null are not call markers. Null, nonempty Maps, Lists and scalars reject;
`resetTimeout!` is an unknown name. Parsing stays caller-owned. The command follows the same literal/profile naming,
exclusion, exact inherited override and collision rules as other admitted entries; payload shape never chooses an overload.
`@Struct.Operation` retains its String-selection role and is not needed on a command. Query, lifecycle and framework helpers
are not commands. Inherited overrides own their names and exclusions, so an old parent spelling cannot call renamed code.

[Order and failures](#order-and-failures) apply: a command reached before a later invalid member may execute; no later
operation runs after failure. Generated [JSON Schema](Json-Schema.md#zero-argument-commands) describes a closed empty object.
Recompile participating Schema hierarchies with matching compiler/runtime/plugin artifacts for the required command-role
facts; see [migration](Migration.md#structured-zero-argument-commands).

## Order and failures

Input is checked one operation at a time and execution stops at the first encountered invalid name, payload, type,
child or invocation. Earlier operations and their ordinary side effects remain; later operations are not reached.
There is no whole-document payload preflight or rollback. A failed root construction returns no completed Model.
A failed Builder application can leave the caller's Builder partially configured; it does not reset or complete it,
and does not run PostApply for the failed application.

Source, root Map, active same-thread unsealed target and complete static contract checks precede traversal. The full
admitted ownership contract graph is verified even for omitted children, without executing user code. A newly allocated
keyed root or direct child reads its required construction key before allocation and PostCreate, wherever that key occurs
in its Map. Its remaining body is not checked ahead. A missing or invalid required key prevents that object's allocation;
earlier siblings may already have run. Externally supplied identity remains authoritative and inner keys reject when reached.

A typed List/Map passed as one authored operation argument is validated as one payload before that invocation. Generated
population routes instead check and invoke each element or Map entry in supplied order. Cyclic containers reject when
reached; repeated acyclic shared values remain allowed. The interpreter does not take a whole-input snapshot.

(See: `StructuredInputRuntimeTest#'supplied operation order configures the Builder until the first invalid member'`.)

```groovy
def parsed = [host: 'api', unknown: true, port: 8080]
// host is applied first; unknown then fails; port is not reached.
```

A caller that catches the failure must explicitly choose whether to abandon construction or retain the partial state.
The internal grammar test qualifies this behavior; no public reader or new construction API is introduced here.

Execution follows each supplied Map's iteration order recursively and each List's element order. An insertion-ordered
Map preserves the caller's chosen order; an unordered Map exposes its own iteration order. There is no portable parser
source-order guarantee or sorting imposed by the interpreter. Reversing order-sensitive operations can change the result.
Normal lifecycle, ownership, defaults, AutoLink, LinkTo, materialization and validation remain in their usual phases.

Local input/dispatch failures use `KlumModelException`, naming the source, escaped JSON Pointer, external name, public
Builder contract and expected/actual shape or type. Pointer tokens escape `/` as `~1` and `~` as `~0`; List indices are
included. Original operation causes are retained. Late lifecycle/validation failures keep their normal framework behavior
and are not attributed to an invented last input leaf. No input provenance is retained on the completed Model.
A Template or earlier operation may already have populated a child. Ordinary same-key reuse and different-key
rejection still apply during execution.
A parser must reject repeated raw JSON/YAML member names before collapsing them into a Map; neither this API nor JSON
Schema can recover discarded duplicates.

[Jackson integration](Jackson-Integration.md) remains an independent foreign-data binding route with its existing
callback and codec behavior. Structured input is opt-in and does not change named-parameter configuration or Jackson.

The internal grammar qualification fixtures live in
[`StructuredInputDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputDocumentaryTest.groovy):
“one structured operation configures several root properties” and
“Holder supplies completed LINK context for later phases after applying an already-prepared Builder”.
The key examples live in
[`StructuredInputKeyDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputKeyDocumentaryTest.groovy),
linked to [#954](https://github.com/klum-dsl/klum-ast/issues/954).
Related language work: [#932](https://github.com/klum-dsl/klum-ast/issues/932); optional matching JSON Schema generation
remains [#529](https://github.com/klum-dsl/klum-ast/issues/529).


## Advisory input examples

`@Struct.Examples` provides documentary strings. They are emitted as JSON Schema string examples, without parsing or
validation against the property type or runtime constraints. See [generated JSON Schema](Json-Schema.md#advisory-input-examples).

(See: `InputExamplesTest#'document example values without supplying defaults or changing input admission'`.)

```groovy
@DSL class Service {
    @Struct.Examples(['Catalog', 'catalog.internal']) String displayName
}

def configured = [displayName: 'Configured']
def omitted = [:]
// The explicit value configures displayName; omission does not supply an example as a default.
```

Documentation strings do not supply values or change input admission, naming/exclusion, defaults, validation or lifecycle.
The shared byte reader checks required string-array metadata without interpreting its contents. Compiler constant capture
never reads a static field reflectively to obtain a value. Owned/inherited public Builder declarations remain the authority;
no Model-byte fallback, Javadoc transport, provider or callback participates.

## Documentary constraint meta-annotations

`@Struct.ConstraintDescription('literal text')` belongs on an annotation type. It adds documentary text for Schema fields
using that annotation; it supplies no validation, default, example or input-admission rule. Bean composed constraints may
host first-level composing constraints for the built-in JSON Schema mappings. These facts do not enforce constraints at
Java/Groovy call boundaries or change structured-input interpretation. Projection policies and the exact composition
boundary are described in [JSON Schema](Json-Schema.md#composed-constraints-and-documentary-meta-annotations).
