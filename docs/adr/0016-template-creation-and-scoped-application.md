# Template creation and scoped Template application

Date: 2026-08-09

Status: Proposed

Tracking issue: [#710 — Separate Template creation from scoped Template application](https://github.com/klum-dsl/klum-ast/issues/710)

Implementation plan: [ADR 0016 implementation plan](../implementation/adr-0016-template-creation-and-scoped-application.md)

Parent decisions: [ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md),
[ADR 0004 — `AsBuilder` composition](0004-asbuilder-composition-protocol.md),
[ADR 0005 — generated DSL support API](0005-generated-dsl-support-api.md), and
[ADR 0015 — generated-schema runtime linkage](0015-generated-schema-runtime-linkage.md).

## Context

Templates are completed DSL Objects with persistent Template identity and a serializable recipe. They are created by a
root lifecycle, then applied as recipes into fresh Builder graphs. ADR 0004 deliberately establishes those semantics; it
does not select a single public language for entering them.

The current generated surface has two equivalent creation routes and one mixed-purpose handler:

```groovy
Service.Create.Template { region 'eu-central' }       // inherited Factory method
Service.Template.Create { region 'eu-central' }       // generated Template handler
Service.Template.With(template) { /* create models */ } // scoped application
```

`Service.Create` is a `Service_DSL.Factory`, but its generated contract mirrors the inherited `KlumFactory.Template(...)`
and `TemplateFrom(...)` methods. `Service.Template` is a final field of type `Service_DSL.Template`; that interface exposes
both `With`/`WithAll` application and `Create`/`CreateFrom` creation methods. The user guide teaches the latter creation
route, while migration and phase guidance still show the former. There is no `Service.Template.From` method: the existing
source-input spelling is `CreateFrom`.

This ambiguity is small in implementation terms but freezes two incompatible stories at the 4.0 public-contract boundary.
In particular, a final `Create.Template` property cannot coexist truthfully with the inherited `Template(...)` method
family: Groovy property/method resolution would make the resulting language and static surface ambiguous.

## Decision

### `Create` owns root creation modes

Every root-creation mode is grouped below `Create`. Ordinary root construction remains unchanged; Template root
construction receives a generated final property:

```groovy
@DSL
class ServiceConfiguration {
    String region
}

def template = ServiceConfiguration.Create.Template.With {
    region 'eu-central'
}

def fromFile = ServiceConfiguration.Create.Template.From(new File('service-template.groovy'))

ServiceConfiguration.Template.With(template) {
    ServiceConfiguration.Create.With { }
}
```

`Create.Template` is a generated public static final Factory field, not a method inherited from `KlumFactory`. Its
generated, nameable type is `ServiceConfiguration_DSL.Factory.Template`. Java and Groovy both retain the DSL spelling:
`ServiceConfiguration.Create.Template.From(...)`. This deliberately follows the existing `Create` field's upper-case
language rather than introducing a JavaBean-style lower-case `template` property.

The nested Template-creation interface has the same root input families as the current Template creation methods:

```java
interface ServiceConfiguration_DSL {
    interface Factory {
        Template Template = /* generated model-specific Template factory */;

        interface Template {
            ServiceConfiguration With();
            ServiceConfiguration With(Map<String, ?> values);
            ServiceConfiguration With(Closure<?> configuration);
            ServiceConfiguration With(Map<String, ?> values, Closure<?> configuration);
            ServiceConfiguration From(File source);
            ServiceConfiguration From(File source, ClassLoader loader);
            ServiceConfiguration From(URL source);
            ServiceConfiguration From(URL source, ClassLoader loader);
        }
    }
}
```

The closure overloads retain `@DelegatesTo(ServiceConfiguration_DSL.Builder)` and `DELEGATE_ONLY`. A return value is the
same completed, marked Template model as today. This is a root lifecycle, not `Create.AsBuilder`: it creates no unsealed
Builder and does not require an active `ConstructionSession`.

### `Template` owns scoped application

`ServiceConfiguration.Template.With` and `WithAll` retain their existing scope, nesting, thread-local restoration, and
return-value contracts. Their public generated type remains `ServiceConfiguration_DSL.Template`. The map overload of
`With` remains an anonymous *scoped application* convenience: it creates an implementation-local temporary recipe and
returns the body result, not a Template creation entrypoint for callers to retain.

Consequently, current documentation calls `Template` the scoped application handler. It must not present it as the
canonical way to create a reusable Template.

### Compatibility disposition

The inherited `KlumFactory.Template(...)` and `TemplateFrom(...)` methods are removed. Generated Factory
implementations must not override or reintroduce them. The model-specific `Foo_DSL.Factory` interface instead defines
the public static final `Template` field, initialized by a generated-only bridge bound to `Foo`. Therefore the current
`Foo.Create.Template(...)` and `Foo.Create.TemplateFrom(...)` routes disappear before the `Foo.Create.Template` field is
generated; source and binary compatibility for those methods cannot be retained without reintroducing the ambiguity this
decision removes.

`Foo.Template.Create(...)` and `CreateFrom(...)` remain on `Foo_DSL.Template` and its generated adapter as deprecated,
forwarding 4.x aliases. They retain all current overloads and semantics, and their deprecation text names the matching
`Foo.Create.Template.With(...)` or `From(...)` route. They are compatibility bridges, not a second canonical interface;
new generated documentation, source mirrors, GDSL, examples, and migration guidance use only `Create.Template` for
creation. The aliases may be removed only by a future explicit major-version compatibility decision.

This keeps existing documented 4.0 RC schema code recompilable while making the one unavoidable breaking route
(`Create.Template(...)`) deliberate, discoverable, and mechanically rejected rather than dynamically shadowed.

The Builder-first migration helper may offer only direct, unambiguous rewrites for this decision: known type-qualified
`Foo.Create.Template*` and `Foo.Template.Create*` calls become the corresponding `Foo.Create.Template.With` or `From`
calls. It must run from a schema-module directory in a clean, version-controlled worktree, show its diff for review, and
leave the source to the migration checklist and compiler. It does not rewrite scoped `Foo.Template.With`/`WithAll`,
receiver variables, custom expressions, or dynamic construction calls.

### Preserve Template semantics and linkage discipline

This decision changes entrypoints only. It does not alter Template identity, synthetic abstract implementations, omitted
lifecycle callbacks, key/owner treatment, recipe capture/replay, copy-source behavior, ownership/adoption, serialization,
Jackson rejection, or scoped restoration from ADR 0004. The new Factory adapter uses the existing template root-creation
path; it never exposes `TemplateManager`, Builder state, `FactoryHelper`, or other runtime internals.

The generated implementation may link a narrow `runtime.generated` bridge under ADR 0015, but all schema/client
descriptors must name only `Foo_DSL.Factory.Template`, `Foo_DSL.Template`, ordinary public runtime types, and model types.
`Foo_DSL.Factory.Template` is a supported generated hook: callers may name it as a receiver/parameter/return type but
must not implement or subclass it.

## Alternatives considered

### Keep `Foo.Template.Create` as the canonical creation route

This retains source compatibility but leaves the public `Template` handler with two unrelated responsibilities. A caller
must learn whether a method creates a Template or scopes one from the operation name, rather than from its locality. It
also leaves the inherited `Foo.Create.Template(...)` route unexplained. The interface is shallow: creation and scope
rules remain spread across the handler and the Factory.

### Add `Foo.Create.AsTemplate.With` instead

`AsTemplate` resembles `AsBuilder`, but the analogy is misleading. `AsBuilder` returns an unsealed child Builder only in
an already-active session; Template creation is a root factory that materializes a completed recipe. It would also revive
the historical `Create.AsTemplate` vocabulary that current migration guidance deliberately moved away from. `Create.Template`
places the mode beside root creation without borrowing active-session semantics.

### Remove every legacy Template creation route immediately

Removing `Foo.Template.Create/CreateFrom` would give the smallest final surface, but the documented spelling is common
in current schemas and its removal provides little additional depth once it is clearly deprecated. Keeping forwarding
aliases concentrates compatibility in one small adapter while new callers learn one interface. The conflicting
`Foo.Create.Template*` method family cannot receive the same treatment because the property uses its name.

## Consequences

- Schema Developers see one canonical root-creation tree and one application tree, with clear IDE completion from their
  first segment.
- Java and static Groovy can name truthful nested generated types and use the same `Foo.Create.Template` field chain; the
  closure delegate remains a concrete public Builder.
- 4.0 RC users of `Foo.Create.Template(...)` migrate to `Foo.Create.Template.With(...)`; users of the documented
  `Foo.Template.Create(...)` spelling receive a deprecation path rather than an immediate rewrite requirement.
- `Foo_DSL.Template` remains a supported generated type but has a deliberately narrow canonical role. Its deprecated
  aliases are compatibility members, not an invitation to add future creation operations there.
- The Factory template adapter is a deep module: the public nested interface expresses creation inputs while the
  existing root lifecycle, Template companion, recipe, and serialization mechanics remain behind its seam.
- The generated public-surface inventory, source mirrors, GDSL completion, generated Javadocs, and user docs must change
  together. A bytecode-only implementation is insufficient because it would leave Java and IDE users with different
  interfaces.

## Acceptance boundary

This proposed decision is ready to implement only after explicit maintainer acceptance. The implementation must prove
the descriptors, Java and static Groovy 3/4/5 consumption, recipe/ownership/serialization preservation, deprecated alias
behavior, absence of the former Factory methods, mirror/GDSL parity, and the documentation/migration/public-inventory
updates listed in the implementation plan.
