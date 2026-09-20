# Advanced Techniques

## Builder-only Methods

Use `@Builder.Method` for Schema methods that change construction-time state and must not appear on the completed DSL
Object. KlumAST moves the method to the generated Builder, retargets its field access to Builder state, and publishes a
matching method on `Foo_DSL.Builder`. The outer `Builder` type is only an annotation namespace; it is not the generated
Builder interface.

(See: `SharedCapabilitiesDocumentaryTest#'declares Builder-only behavior with Builder Method'`.)

```groovy
import com.blackbuild.klum.ast.Builder

@DSL
class Registry {
    String host

    @Builder.Method
    void normalizeHost() {
        host = host.toLowerCase()
    }
}

def registry = Registry.Create.With {
    host 'EXAMPLE.TEST'
    normalizeHost()
}

assert registry.host == 'example.test'
```

The legacy `@Mutator` spelling remains source-compatible in 4.1 but is deprecated. Replace it when the Schema is next
edited; compilation already promotes it to `@Builder.Method`, including the annotation visible through reflection on the
generated Builder API. The spelling change does not alter receiver state, visibility, generated signatures, or lifecycle
timing. Do not combine both annotations on one method. See
[Builder First Migration](Builder-First-Migration.md#mutator-to-buildermethod) for the mechanical migration.

## Sharing a Pure Query with Builders

Ordinary Model methods execute on completed DSL Objects. If a side-effect-free query is also meaningful during
construction, mark it with `@Builder.Query`. KlumAST keeps the original Model method and projects the same signature onto
the generated public Builder contract.

(See: `SharedCapabilitiesDocumentaryTest#'shares a pure URL query between Builder lifecycle code and the completed Model'`.)

```groovy
import com.blackbuild.klum.ast.Builder

@CompileStatic
@DSL class Deployment {
    Registry registry
    String configuredRegistryUrl

    @PostTree
    void captureRegistryUrl() {
        configuredRegistryUrl = registry.toUrl()
    }
}

@DSL class Registry {
    String host

    @Builder.Query
    String toUrl() { "https://$host" }
}

when:
def deployment = Deployment.Create.With {
    registry {
        host 'packages.example.test'
    }
}

then:
deployment.configuredRegistryUrl == 'https://packages.example.test'
deployment.registry.toUrl() == deployment.configuredRegistryUrl
```

The lifecycle call reads the current `Registry` Builder; the final call reads the completed `Registry` Model. A Builder
query may read fields available in both states, call another projected query, and use ordinary non-DSL values. Its result
must not contain a DSL Object or Builder. It must not assign DSL fields, call a mutator or lifecycle operation, read a
`FieldType.BUILDER` field, or start construction.

`@Builder.Query` is an explicit, per-method projection, not Model/Builder substitutability. Unannotated Model methods stay
off the hidden Builder, `Foo_DSL.Builder`, and its IDE source mirror. KlumAST checks locally visible purity constraints;
calling foreign non-DSL code remains the Schema Developer's assertion that the call is observational. If a projected
query calls a query on a separately compiled DSL Object, that dependency must itself have been compiled with its emitted
`@Builder.Query` contract.

## Flowing Builders through Explicit Inputs and Results

Use `@Builder.Input` and `@Builder.Result` when one domain helper must also participate in Builder-phase composition. The
annotations select only the marked parameter and result positions. Where a Model form remains, its signature still
consumes and returns completed DSL Objects, while the generated Builder contract uses the corresponding exact public
Builder types.

(See: `SharedCapabilitiesDocumentaryTest#'flows an explicitly selected Builder input into an owned Builder result'`.)

```groovy
@CompileStatic
@DSL class Deployment {
    Registry source
    Registry normalized

    @PostTree
    void normalizeRegistry() {
        normalized Registry.normalized(source)
    }
}

@DSL class Registry {
    String host

    @Builder.Result
    static Registry normalized(@Builder.Input Registry source) {
        Registry.Create.With(host: source.host.toLowerCase())
    }
}
```

In completed-Model code, `Registry.normalized(Registry)` remains the authored signature and owns its normal root factory
lifecycle. During Builder execution KlumAST binds the same source call to a linked operation equivalent to
`Registry_DSL.Builder<Registry> normalized(Registry_DSL.Builder<Registry>)`. The returned Builder stays unsealed in the
active Construction session and can be attached through the generated `normalized` relationship method.

The annotations are orthogonal signature facets. An unmarked DSL Object parameter still means a completed Model—for
example a `LINK` target—and an unmarked DSL Object result remains a Model result. A static helper can use the facets
directly. An instance method must also declare its category: `@Builder.Query` may use `@Builder.Input` but cannot produce
an owned Builder result, while `@Builder.Method @Builder.Result` retargets the Builder-only method directly. Supported
Collection and Map positions preserve their declared outer type, element order, map keys, and concrete collection
behavior while projecting their DSL Object elements or values.

Projection is deliberately strict. Raw or wildcard Builder types, unresolved DSL-bearing generics, unsupported nested
containers, non-DSL annotated positions, and overloads that collapse after projection fail at Schema compilation. A
separately compiled Schema must expose the generated linked contract; recompile older bytecode when the diagnostic says
that its Builder twin is unavailable. These annotations never convert a completed Model into composition and never adopt,
reopen, or move a Builder across Construction sessions; the normal session, sealing, attachment, and ownership checks
remain authoritative.

## Narrowing a Builder by Model Type

Builder-phase relationship values are Builders rather than completed Models, so `value instanceof SpecialRegistry` does
not test the intended state. Use the subtype's generated factory token to test and narrow the value explicitly.

(See: `SharedCapabilitiesDocumentaryTest#'narrows a related subtype Builder through its factory token'`.)

```groovy
import com.blackbuild.klum.ast.Builder

@CompileStatic
@DSL class Deployment {
    Registry registry
    String configuredRegistryUrl

    @PostTree
    void captureSpecialRegistryUrl() {
        if (SpecialRegistry.Create.isBuilder(registry)) {
            def special = SpecialRegistry.Create.narrowBuilder(registry)
            configuredRegistryUrl = special.toUrl()
        }
    }
}

@DSL abstract class Registry {
    String host

    @Builder.Query
    String toUrl() { "https://$host" }
}

@DSL class SpecialRegistry extends Registry {
    String tenant
}

when:
def deployment = Deployment.Create.With {
    registry(SpecialRegistry.Create) {
        host 'packages.example.test'
        tenant 'documentation'
    }
}

then:
deployment.configuredRegistryUrl == 'https://packages.example.test'
```

Every generated `Foo.Create` factory token exposes three related operations:

- `isModelOrBuilder(value)` accepts a completed `Foo` Model or a Builder whose declared Model type is `Foo` or a subtype.
- `isBuilder(value)` accepts only the matching Builder state.
- `narrowBuilder(value)` returns that same value as the factory's exact generated `Foo_DSL.Builder<Foo>` contract. It throws
  `KlumModelException` for a completed Model, mismatched Builder, `null`, or a non-DSL value.

Do not confuse `narrowBuilder(value)` with `Foo.Create.AsBuilder()`. `AsBuilder()` enters the active-session
Builder-producing factory API; `narrowBuilder(value)` only type-narrows an existing matching Builder and preserves its
identity and lifecycle state.

These operations follow ordinary subtype assignability and are deterministic outside an active Construction session.
They do not create, adopt, unseal, or materialize a Builder. A narrowed sealed or inactive Builder therefore retains its
read-only projected queries, while existing lifecycle, ownership, and mutation guards continue to reject invalid work.
The predicates inspect only framework-owned Model identity; protected discriminator fields stay protected and need not be
made configurable or public.

## Delegation Hints for Builder Closures

Generated DSL methods that accept configuration closures automatically receive the appropriate `@DelegatesTo` metadata,
so modern IDEs can infer the available Builder methods.

Schema-defined Builder-only methods can also accept and forward configuration closures. Because the generated Builder
type does not exist when that source method is parsed, use `@DelegatesToBuilder` for those parameters. It tells the IDE
and static type checker about the generated Builder; it does not make the completed DSL Object mutable.

The optional annotation value names the DSL Object whose Builder receives the closure:

```groovy
@DSL
class Container {
    List<Element> elements

    @Builder.Method
    def circle(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'circle', body)
    }

    @Builder.Method
    def square(@DelegatesToBuilder(Element) Closure body) {
        element(type: 'square', body)
    }
}
```

Here both methods execute on the `Container` Builder and delegate `body` to a newly created `Element` Builder.
`@DelegatesToBuilder` does not add a completed-model `apply` or `configure` path. An API that configures a DSL Object must
participate in factory/Builder construction; see [Builder First Migration](Builder-First-Migration.md) for the lifecycle boundary.

## Behavior Models and Parameter Hints

Fields can hold behavior as a closure or interface value. This lets a Model choose an algorithm without creating a new
Schema type.

Consider the following example:

```groovy
class ValueProvider {
    String name

    String getDescription(Map<String, String> environment) {
        "Value: $name: ${environment.name} -> ${environment.value}"
    }
}
```

If different Models need different descriptions, this design could require a subclass of `ValueProvider` for each
algorithm. That is inconvenient when the behavior varies by Model rather than by Schema.

Make the description algorithm configurable instead (the Strategy pattern). Use either an interface or abstract class,
or a closure.

### Interface

```groovy
interface DescriptionProvider {
    String getDescription(Map<String, String> environment)
}

@DSL class ValueProvider {
    String name

    @Required
    DescriptionProvider descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

The Model can now supply the description algorithm. In Groovy, a closure can implement a single-abstract-method (SAM)
interface:

```groovy
ValueProvider.Create.With {
    name "Blub"
    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

The closure has one `Map` parameter and returns `String`, so the compiler can check both parts of the contract.

`DescriptionProvider` could instead be an abstract class, for example to add [Converters#factory-method-converters](Converters.md#factory-method-converters).

### Closure Attributes

The description provider could also be a Closure itself:

```groovy
@DSL class ValueProvider {
    String name

    Closure<String> descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}

ValueProvider.Create.With {
    name "Blub"

    descriptionProvider { "Value: $name: $it.name -> $it.value" }
}
```

The Model call looks the same as the SAM-interface form. With an unannotated Closure field, however, the IDE and type
checker do not know the parameter type. `Closure<String>` describes the return type, not the parameter type, so they
cannot offer parameter completion.

In normal Groovy, a method parameter can carry `@ClosureParams`. Because KlumAST generates this setter, use
`@ParameterAnnotation.ClosureHint` on the field instead. The hint supplies the required parameter annotation:

```groovy
@DSL class ValueProvider {
    String name

    @ParameterAnnotation.ClosureHint(params = @ClosureParams(value = FromString, options = "Map<String,Object>"))
    Closure<String> descriptionProvider

    String getDescription(Map<String, String> environment) {
        descriptionProvider.getDescription(environment)
    }
}
```

This provides parameter completion and type checking for the generated methods. `@ParameterAnnotation` copies annotations
from a Schema field to the generated setter or single-element adder; see the
[`@ParameterAnnotation` API source and Javadoc](https://github.com/klum-dsl/klum-ast/blob/master/klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/ParameterAnnotation.java)
for the advanced annotation-mapping rules.

## Choosing a SAM Interface or Closure

Prefer a SAM interface when only parameter typing is needed, especially with factory converters.

Use a Closure with parameter annotations when its Groovy delegate mechanism makes the DSL materially clearer; a SAM
interface cannot reproduce that delegate behavior.

In short: use a SAM interface for ordinary typed behavior and a Closure with `@ParameterAnnotation` when delegation is
part of the DSL.
