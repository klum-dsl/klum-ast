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
The four-argument route rejects an inner root key, even if it equals `rootKey`.
The three-argument route below admits self-contained root identity; supplied direct children follow its key rules
under both routes. The nonempty `sourceName` is diagnostic text and
is never opened as a file or URL.

Compile Schemas successfully with the current KlumAST compiler and distribute their generated classes together.
Missing or inconsistent generated structured-input metadata reports recompile/package guidance. An in-memory-only
Schema without packaged generated metadata cannot use this route.

## Type naming profiles

Select `@InputNaming` on a Schema type to use the same exact member tokens in structured input and generated
[JSON Schema](Json-Schema.md#type-naming-profiles). Its required value is `InputNaming.Profile.IDENTITY` or
`InputNaming.Profile.KEBAB_CASE`; there is no default value or caller/plugin selector.

(See: `InputNamingProfileTest#'choose kebab member names while keeping owned values and data keys independent'`.)

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.InputNaming
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@DSL class ServiceBase { String displayName }
@InputNaming(InputNaming.Profile.KEBAB_CASE)
@DSL class Service extends ServiceBase {
    String serviceName
    Map<String, String> labels
    Details serviceDetails
}
@DSL class Details { String nativeName }

def service = KlumStructuredInput.readRoot(Service,
        ['display-name': 'Catalog', 'service-name': 'catalog', labels: ['Team/Blue': 'ops'],
         'service-details': [nativeName: 'own policy']], 'service.yaml')
assert service.serviceName == 'catalog'
assert service.labels == ['Team/Blue': 'ops']
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

Use `com.blackbuild.klum.ast.InputName` and `InputIgnore` on source fields (including construction keys) or already
admitted authored `@Builder.Method` operations. `@InputName` requires a nonempty exact String; spaces, punctuation and
case are preserved. `@InputIgnore` is a marker. Both have runtime retention and FIELD/METHOD targets.

(See: `InputLiteralTest#'choose exact external names and exclude internal configuration'`.)

```groovy
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@InputNaming(InputNaming.Profile.KEBAB_CASE)
@DSL class Service {
    @InputName('service.id') String serviceName
    @InputIgnore String internalNote
    @Builder.Method @InputName('configure/URL')
    void configureEndpoint(String value) { serviceName = value }
    @Builder.Method @InputIgnore
    void configureInternally(String value) { internalNote = value }
}

def service = KlumStructuredInput.readRoot(Service, ['service.id': 'catalog'], 'service.yaml')
assert service.serviceName == 'catalog'
// Ordinary Groovy configuration keeps its authored names, including ignored members.
def configured = Service.Create.With { serviceName 'groovy'; configureInternally 'private' }
assert configured.internalNote == 'private'
```

Eligibility and exclusion come first, then a compatible explicit literal, then the inherited/local type profile, then
identity. Naming cannot admit unsupported types or operations. A literal adds no native/profile alias and can separate
entries that would collide under a nonidentity profile. Unresolved nonidentity collisions fail with both source declarations;
existing identity collision rejection is unchanged.

Supported `@JsonProperty`/`@JsonIgnore` bridging remains available. Equal nonempty `InputName` and `JsonProperty` names
coalesce; different names fail deterministically with both annotation origins, **even when excluded**. Empty `JsonProperty`
falls back; empty `InputName` is invalid. `InputIgnore` or true/absent-value `JsonIgnore` excludes; false `JsonIgnore` cannot
undo exclusion. Exclusion and a compatible rename can coexist.

Both consumers use compiler-folded facts on the exact public Builder declarations. Exclusion removes members from
structured input and generated JSON Schema; it changes no ordinary Groovy configuration, runtime validation, defaults or
completed Model API. An ignored required construction key still needs an external root key, Schema derivation or keyed-Map
identity; it cannot make an otherwise self-contained key position valid. Data/instance keys remain literal.
Managed Jackson import remains independently configured: use the Jackson literal too when both routes need one spelling.
There is no automatic mapper adapter. Recompile Schemas and align compiler/runtime/plugin versions; see
[migration](Migration.md#literal-names-and-exclusion).

## Construction keys

Self-contained documents use `readRoot(Class<T>, Object parsed, String sourceName)`. A keyed root requires the actual
Schema `@Key String` member. A supplied direct owned keyed child requires its key too, at any depth, unless its
relationship supplies identity through `@Field.key` or a fixed Cluster key. An absent optional child remains absent.
Unkeyed roots use the same overload without a synthetic identity property.

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

Java callers pass their parsed document directly:

```java
Deployment model = KlumStructuredInput.readRoot(Deployment.class,
        Map.of("name", "production",
               "service", Map.of("service-id", "catalog", "image", "catalog:1.0"),
               "services", Map.of("worker", Map.of("image", "worker:1.0"))), "deployment.yaml");
```

The corresponding Groovy call is the executable example:

(See: `StructuredInputKeyDocumentaryTest#'self-contained documents carry root and direct-child keys while Maps supply outer keys'`.)

```groovy
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

def parsed = [name: 'production',
              service: ['service-id': 'catalog', image: 'catalog:1.0'],
              services: [worker: [image: 'worker:1.0']]]
def model = KlumStructuredInput.readRoot(Deployment, parsed, 'deployment.yaml')
assert model.name == 'production'
assert model.service.name == 'catalog'
assert model.services.worker.name == 'worker'
```

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
| Three-argument keyed root | Input | Required String |
| Four-argument keyed root | Explicit `rootKey` | Forbidden, even if equal |
| Existing `applyToBuilder` target | Allocated Builder | Forbidden, even if equal |
| Supplied direct keyed child | Input, unless Schema supplies identity | Required String |
| Fixed/derived-key direct child, including fixed Cluster fields | Schema relationship | Forbidden, even if equal |
| Keyed owned Map value | Outer literal Map entry | Forbidden, even if equal |

Thus `services.worker` must omit `service-id`: `worker` already supplies its identity. A nested direct child inside
that body still requires its own key when its relationship has no provider. Key names are resolved on the actual
compiler-selected declaration, including binary inherited keys: `@InputIgnore` or `@JsonIgnore(true)` excludes it, a compatible nonempty literal
`@InputName` or `@JsonProperty` renames it, then the effective type profile/native name applies. Empty `@JsonProperty` uses that fallback;
`@JsonIgnore(false)` leaves the declaration admitted under its otherwise resolved name. Renames add no alias.
An ordinary unannotated `key` property remains normal input; an independently
admitted operation with an old/native spelling keeps its meaning.

Key values must be exact Strings. Empty Strings are accepted; null, numbers, booleans, GStrings and containers reject
without coercion. An ignored key cannot support a self-contained root or an input-key direct child. An external-name
collision between that required key and an admitted operation rejects before allocation, naming both declarations.
Keyless positions retain their ordinary operation admission. Use an explicit root key, a Schema key provider or a keyed
Map where appropriate for an ignored key. A provider is never evaluated to compare an inner key.

The existing explicit-root route stays available, with `null` required for an unkeyed root and a non-null String for a
keyed root. Its nested direct children still follow the table above.

```java
Deployment model = KlumStructuredInput.readRoot(Deployment.class, "production",
        Map.of("service", Map.of("service-id", "catalog", "image", "catalog:1.0")), "deployment.yaml");
```

(See: `StructuredInputKeyDocumentaryTest#'explicit-rootKey payloads omit root identity and still supply direct-child identity'`.)

```groovy
def parsed = [service: ['service-id': 'catalog', image: 'catalog:1.0']]
def model = KlumStructuredInput.readRoot(Deployment, 'production', parsed, 'deployment.yaml')
assert model.name == 'production'
assert model.service.name == 'catalog'
```

Compile and ship matching compiler/runtime/plugin versions with current generated key facts. Missing facts produce
recompile/package guidance; the interpreter does not guess key names or derive identity from a filename or URL.
`Create.FromMap` retains its separate copy/type-hint behavior. These overloads add no Factory or file/URL loading API.

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
keys. Ordinary Builder DSL key mapping keeps its existing behavior. A direct keyed child uses its actual key member when the relationship has no Schema provider;
explicit `@Field.key` and compiler-known fixedKeys Cluster keys keep Schema authority and reject inner identity. LINK/reference lookup, unkeyed relationship
collections, polymorphic selection and CollectionFactory scopes are outside v1. Null at any depth, reset/clear,
arbitrary caller objects, helper/query/lifecycle names and zero/multi-payload authored operations reject.
An ordinary unannotated property named `key` is normal domain input; actual `@Key` members follow the position-specific rules above.

Names follow compiler decisions: exclusion, compatible `@InputName`/`@JsonProperty` literal, effective type profile,
then identity. See [literal names and exclusion](#literal-names-and-exclusion). Inherited or overload-name collisions reject without choosing a winner.
An unsupported Schema member can coexist with unrelated admitted entries. These naming annotations do not enable
Jackson codecs, naming strategies, mixins, aliases or mapper coercion.

## Preparation, order and failures

The complete input is checked before any input operation or root allocation: names, shapes, types, numeric limits,
enum names, required construction keys and container cycles. Keys are extracted without mutating the supplied Map
and reach root/child allocation before PostCreate. Extracting a key does not reorder the remaining operations. Preparation executes no Schema initialization, callbacks, operations, converters,
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
A Template or earlier operation may already have populated a child. Ordinary same-key reuse and different-key
rejection still apply during execution; preflight cannot predict that state.
A parser must reject repeated raw JSON/YAML member names before collapsing them into a Map; neither this API nor JSON
Schema can recover discarded duplicates.
Once Schema execution begins, ordinary side effects remain if a later operation fails; there is no rollback.

[Jackson integration](Jackson-Integration.md) remains an independent foreign-data binding route with its existing
callback and codec behavior. Structured input is opt-in and does not change named-parameter configuration or Jackson.

The executable examples live in
[`StructuredInputDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputDocumentaryTest.groovy):
“one structured operation configures several root properties” and
“Holder supplies completed LINK context for later phases after applying an already-prepared Builder”.
The key examples live in
[`StructuredInputKeyDocumentaryTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputKeyDocumentaryTest.groovy),
linked to [#954](https://github.com/klum-dsl/klum-ast/issues/954).
Related language work: [#932](https://github.com/klum-dsl/klum-ast/issues/932); optional matching JSON Schema generation
remains [#529](https://github.com/klum-dsl/klum-ast/issues/529).
