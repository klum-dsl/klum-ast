# Completed DSL Object support facade

Date: 2026-07-14

Status: Accepted

Tracking issue: [#390 — Stable Entry object for internal methods](https://github.com/klum-dsl/klum-ast/issues/390)

Implementation status: OS-1 construction-path and structure support is implemented in [#435](https://github.com/klum-dsl/klum-ast/issues/435).
OS-2 validation support and companion lockdown are implemented. OS-3 construction-path terminology and compatibility
closure are implemented. See the [implementation plan](../implementation/adr-0006-completed-object-support.md).

Parent decision: [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)

## Context

Completed DSL Objects need supported access to construction paths, validation, and composition structure without exposing their
internal companion. `KlumModelProxy` currently contains raw technical metadata and mutable implementation operations. Its
name also implies a whole root model even though the entry operation can sensibly target any completed DSL Object subtree.

The primary consumer is Java code. A Groovy-property-only facade or a bag of static helpers would make the supported
boundary harder to discover and would keep internal companion state effectively public.

## Decision

Provide the Java-first facade `KlumObjectSupport<T>.of(T dslObject)`. It accepts any completed DSL Object, including a
subtree. The facade stores only the target object. The object's generated private field retains the companion, so the facade
has a de facto transitive reference but does not expose, serialize, or cache the companion separately.

The direct facade operations are:

```text
getObject()
getConstructionPath()
getModelPath()
getStructure()
getValidation()
```

`getConstructionPath()` is the immutable DSL invocation path through which an object was constructed, while
`getModelPath()` is its structural location in owned composition. A traversal path is contextual traversal output; an import
source and validation location are separate diagnostics. These direct methods do not promise provenance or lineage: a
richer generation/event record remains #402 work.

`getConstructionPath()` is the sole public construction-string getter on the facade; it has no breadcrumb alias.
`BreadcrumbCollector` remains internal generated/runtime terminology. Existing exception and helper descriptors retain
their current JVM names under their separate compatibility policy.

`getStructure()` returns the public nested helper `KlumObjectSupport.Structure<T>`. Its contract is composition-only and
cycle-safe: direct owners and optional single owner, owner hierarchy/ancestors, typed visit and find-all operations, and
relative structural paths. Java overloads include the existing four-argument `ModelVisitor`,
`visit(Class<R>, BiConsumer<String,R>)`, and typed `findAll`. Client examples use explicit Java access such as
`support.getStructure().getDirectOwners()`. Existing `StructureUtil` methods may delegate and be deprecated; internal
Builder traversal remains separate.

`getValidation()` returns a helper that reads stored completed-model validation only. `getResult()` returns the target's
result, `getResults()` returns results for its owned subtree, and `verify()` / `verify(level)` inspect those results and
throw as appropriate. These operations never rerun validators and never mutate lifecycle issue state.

`KlumModelProxy` is strictly internal. Raw metadata read/write is removed from the supported surface as an intentional 4.0
break because arbitrary keys and serializable values create an unsafe extension protocol. Needed features must later expose
explicit operations behind a designed plugin mechanism; speculative `extension(Class<?>)` is not part of this ADR.

The facade initially has no model-class-specific generated methods. If annotated model methods later justify such a
surface, that is a separate design decision.

## Consequences

- Java callers get one discoverable support object for any completed DSL Object or subtree.
- Construction paths, structure, and validation are supported without leaking companion identity or metadata.
- Static structure utilities can migrate gradually without mixing Builder and completed-model traversal.
- Existing direct `KlumModelProxy` and metadata clients receive a deliberate 4.0 migration break.
- Template recipe state and construction operations remain inaccessible.

## Rejected alternatives

`KlumModel` is rejected because the target may be a subtree. `KlumInstance` is rejected because the facade is a framework
support object, not the domain instance. `KlumObjectSupport` is distinct from generated `Foo_DSL` build-time contracts.
Exposing the companion or a generic metadata Map is rejected because it freezes internal storage and invites accidental
protocols. Promoting every structure operation onto the root facade is rejected because it pollutes the core surface.
`getProvenance()` is rejected because the retained String carries neither source lineage nor event history.
