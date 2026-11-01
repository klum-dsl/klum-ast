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

## Metadata protocol

Structured input and JSON Schema generation require Schemas compiled with a matching supported metadata protocol.
Older or incomplete Builder contracts are rejected; recompile the participating Schema artifacts.

The current compiler explicitly emits `GeneratedInput.Schema.version=1` on every declaring public Builder. Both consumers
require exactly that version and complete naming, exclusion, constraint, projection, source-provenance, key and inert example-array facts.
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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@DSL class ServiceBase { String displayName }
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
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

Use `com.blackbuild.klum.ast.Struct.Name` and `Struct.Ignore` on source fields (including construction keys) or already
admitted authored `@Builder.Method` operations. `@Struct.Name` requires a nonempty exact String; spaces, punctuation and
case are preserved. `@Struct.Ignore` is a marker. Both have runtime retention and FIELD/METHOD targets.

(See: `InputLiteralTest#'choose exact external names and exclude internal configuration'`.)

```groovy
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
@DSL class Service {
    @Struct.Name('service.id') String serviceName
    @Struct.Ignore String internalNote
    @Builder.Method @Struct.Name('configure/URL')
    void configureEndpoint(String value) { serviceName = value }
    @Builder.Method @Struct.Ignore
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
| Zero-argument `@Builder.Method` command | An empty Map (`operation: {}` in YAML) |
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

Keyed owned maps with `@Field.keyMapping` are excluded because derived keys conflict with literal structured instance
keys. Ordinary Builder DSL key mapping keeps its existing behavior. A direct keyed child uses its actual key member when the relationship has no Schema provider;
explicit `@Field.key` and compiler-known fixedKeys Cluster keys keep Schema authority and reject inner identity. LINK/reference lookup, unkeyed relationship
collections, polymorphic selection and CollectionFactory scopes remain outside the admitted grammar. Null at any depth,
arbitrary caller objects, helper/query/lifecycle names and multi-payload authored operations reject.
Zero-argument domain commands have only their explicitly authored behavior; there is no implicit reset/clear operation.
An ordinary unannotated property named `key` is normal domain input; actual `@Key` members follow the position-specific rules above.

Names follow compiler decisions: exclusion, compatible `@Struct.Name`/`@JsonProperty` literal, effective type profile,
then identity. See [literal names and exclusion](#literal-names-and-exclusion). String candidates follow
[operation selection](#select-a-string-operation); unresolved collisions reject without choosing a winner.
An unsupported Schema member can coexist with unrelated admitted entries. These naming annotations do not enable
Jackson codecs, naming strategies, mixins, aliases or mapper coercion.

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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@DSL class Service {
    @Struct.Ignore String host

    @Struct.Operation @Builder.Method
    void endpoint(String value) { host = 'primary:' + value }

    @Struct.Name('endpoint') @Builder.Method
    void backupEndpoint(String value) { host = 'backup:' + value }
}

def service = KlumStructuredInput.readRoot(Service, [endpoint: 'api.internal'], 'service.yaml')
assert service.host == 'primary:api.internal'
```

Only `endpoint` is an external input name here. Ordinary Groovy/Java calls keep their normal callable methods. Exclusion,
unsupported roles and missing public projections cannot be repaired with a mark. Place the marker on a method, never a
field: it cannot choose among several generated converters. An authored marked wrapper can expose an exact String route.

Inherited String operations retain their declaring contract. A String override replaces the parent operation's name and selection
intent, including when the override is renamed or ignored. Overriding an inherited String field's String input signature
also excludes the parent's external field route; overriding an unrelated overload leaves that String route intact.
Existing C1 converter routes remain excluded when a descendant
overrides their signature; the parent's converter constraints cannot describe different code.

A selected String route rejects numeric, null, GString and mutable CharSequence payloads without trying another overload.
Competing String/CharSequence projections have no implicit specificity preference: mark the String operation.
CharSequence invocation remains deferred; exact String converter origins follow the field rules above. The generated JSON Schema describes only the
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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput

@DSL class Service {
    int timeout = 30
    @Builder.Method void resetTimeout() { timeout = 30 }
}

def service = KlumStructuredInput.readRoot(Service,
        [timeout: 90, resetTimeout: [:]], 'service.yaml')
assert service.timeout == 30
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
There is no whole-document payload preflight or rollback. A failed `readRoot` returns no completed Model.
A failed `applyToBuilder` can leave the caller's Builder partially configured; it does not reset or complete it,
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
@DSL class Application { String host; int port }

def failure
// This caller deliberately catches the failure and continues its enclosing construction.
def model = Application.Create.With {
    try {
        KlumStructuredInput.applyToBuilder(delegate,
                [host: 'api', unknown: true, port: 8080], 'application.yaml')
    } catch (KlumModelException caught) { failure = caught }
}
assert failure.message.contains('/unknown')
assert model.host == 'api'
assert model.port == 0
```

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

The executable examples live in
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

def configured = KlumStructuredInput.readRoot(Service, [displayName: 'Configured'], 'service.yaml')
def omitted = KlumStructuredInput.readRoot(Service, [:], 'service.yaml')
assert configured.displayName == 'Configured'
assert omitted.displayName == null
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
