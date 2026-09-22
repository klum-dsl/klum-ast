# Issue #420 — Lifecycle-class contract decision brief

Date: 2026-09-22

Status: Superseded by [ADR 0021](../adr/0021-builder-owned-lifecycle-classes.md)

Issue: [#420 — Lifecycle classes](https://github.com/klum-dsl/klum-ast/issues/420)

## Recorded design direction

The maintainer's preferred source contract is a non-static inner lifecycle
class, structurally relocated into the generated Builder before type checking.
It can have several public, parameterless callback methods; its ordinary
unqualified field and method access then resolves through the Builder outer
class. This keeps IDE completion and avoids a one-method callback wrapper.

This is not a general Model-to-Builder delegation. The source class disappears
from the completed Model and becomes a Builder-private lifecycle class; its
callbacks receive the Builder only through their lexical outer relationship.
The compiler must reject a reference for which the generated Builder has no
truthful counterpart, especially an unannotated Model-only instance method.

The original issue and #415 provenance did not select this contract. ADR 0021
now records it as the accepted Builder-first decision. Invoking the source class
with a completed Model would still violate Builder-first immutability.

The ADR must fix class visibility, callback ordering/inheritance, permitted
constructors and helper members, and diagnostics. `this` remains the lifecycle
class instance; explicit `Foo.this` has no Builder-safe meaning and must fail
with a targeted diagnostic.

## Re-established state contract

The lifecycle boundary is a receiver boundary, not merely an execution-order
detail.

| Work | Current receiver | Lifecycle-class implication |
| --- | --- | --- |
| `@PostCreate`, `@PostApply`, `@AutoCreate`, `@AutoLink`, `@Default`, `@PostTree` | mutable Builder | A grouped callback must receive Builder state and obey construction-session, ownership, template, and pre-40 scheduling rules. |
| `EARLY_VALIDATE` | Builder metadata | It is a built-in provisional issue pass, not an existing user lifecycle-method annotation; it must not be accidentally turned into a completed-model validator. |
| `INSTANTIATE` (40) | state switch | Materialization copies Builder state, resolves relationship links, transfers provisional issues, and drops Builder-only state. It is not a callback receiver. |
| `@Validate` / validation `InstanceValidator`s (50+) | completed Model | Existing `@Validate` inner classes remain Model-owned validation classes. They need no Builder projection and must not mutate. |

`@Owner` is a Builder-only relationship operation with an owner argument, not a
parameterless lifecycle callback. It therefore cannot join a generic
zero-argument lifecycle-class protocol without an independently designed
parameter contract.

## Why the existing validation-class mechanism cannot be generalized

Validation classes are public, non-static inner classes. At validation time
`KlumInnerClassValidator` obtains the class from the completed Model type and
reflectively calls its synthetic outer constructor with that Model. Public,
parameterless methods then read completed state and report through the stored
validation result. This is correct only after materialization.

By contrast, mutating lifecycle methods carry `@WriteAccess(LIFECYCLE)` and the
compiler moves them to the generated Builder. `LifecycleHelper` resolves and
invokes those methods on an `InternalKlumBuilder` through `POST_TREE`; it does
not retain a Model. A source inner-class method captures its declaring Model as
its lexical outer instance, so moving only that method cannot make `host`,
`this`, `owner`, helper calls, inherited members, or constructor state refer to
the Builder. Passing a completed Model instead would expose a state that does
not yet exist and could not safely mutate it.

Consequently, the inner class itself—not only an individual method—must be
relocated under the Builder before its members are resolved. Each callback family
still needs a distinct receiver contract: Builder components before 40 and Model
validation components after 40. This rules out a generic `KlumBuilder` callback
API: it deliberately has no schema-specific member surface, and a generic
Model-to-Builder delegation would repeat the rejected blanket projection model.

## Consequences that the chosen contract must preserve

- **Construction and ownership.** A Builder callback may only create/attach
  fresh, same-session owned Builders; completed Models remain aggregation
  `LINK`/`OPTIONAL_LINK` targets and are never re-owned or rehydrated.
- **Materialization and templates.** It must finish before phase 40. It cannot
  retain a Builder in the completed Model, schedule `applyLater` at or after
  phase 40, or make a Template's reusable recipe state capture a Builder.
- **Validation and serialization.** Pre-40 callbacks can create provisional
  validation issues, which materialization transfers to the Model companion.
  Validation classes execute only on Models. Builder callback instances and
  Builder-only fields are construction machinery and are not serialized.
- **Public/generated API.** An explicit Builder receiver would name the exact
  generated `Foo_DSL.Builder` type only where the selected callback contract
  requires it. It must update generated public-surface/mirror documentation and
  reject raw, wildcard, opaque, or model-typed substitutes rather than guessing.
- **Compatibility.** Annotation target expansion, AST transformation, generated
  source/mirror output, and static Groovy use are compiler-facing changes. The
  accepted three-lane Groovy 3/4/5 contract, Java consumer coverage, and the
  Groovy 4/5 JPMS boundary all apply.

## Smallest viable 4.1 scope after a decision

Only one receiver family should be added first: grouped callbacks for the
parameterless, Builder-phase lifecycle annotations through `POST_TREE`, with
one explicit source grammar and targeted compiler rejection diagnostics.
`@Validate` inner classes remain unchanged and serve as the completed-Model
precedent, not as an implementation shortcut.

Explicit non-goals are post-materialization mutating callbacks, phase-plugin
registration (#305/ADR 0008), a shared Model/Builder interface, blanket
Model-method delegation, Builder persistence/serialization, Template recipe
extensions, and an `@Owner`-class protocol. The first implementation must not
expand callback behavior to custom phase actions.

## Evidence

- #420 says only that lifecycle methods may be grouped; it predates Builder-first
  and speculates about the old RW type. #415 deliberately delivered only
  `@Validate` classes; #416 explicitly supersedes the old premise and says #420
  targets Builders.
- [ADR 0003](../adr/0003-builder-first-materialization.md) assigns all mutation
  through `POST_TREE` to Builders, materializes at 40, and reserves validation
  for Models. [ADR 0020](../adr/0020-explicit-shared-model-builder-capabilities.md)
  rejects blanket Model/Builder projection and keeps `KlumBuilder` zero-operation.
- [Model phases](../user/Model-Phases.md), the Builder-first migration guidance,
  and the architecture map record the exact phase/state split and the required
  Groovy 3/4/5/generated-surface seams.
- `KlumInnerClassValidator` and `LifecycleHelper` demonstrate the incompatible
  current receiver mechanisms; `WriteAccessMethodsMover` demonstrates that a
  lifecycle method is currently a Builder projection, not a Model callback.

### Throwaway relocation probe (2026-09-22)

`/private/tmp/lifecycle-inner-class-relocation-poc.groovy` is a disposable,
one-command Groovy probe. Under `@CompileStatic`, it transferred the original
`TreeRules` `MethodNode`s into a newly constructed non-static
`DeploymentBuilder$TreeRules` `InnerClassNode`, without manual field or method
reference rewriting. The relocated class compiled, received a synthetic
`DeploymentBuilder` outer constructor parameter, invoked the Builder's query
and Builder-only method, and changed Builder state as expected.

This establishes the critical language/AST feasibility, not feature readiness.
`InnerClassNode` has no mutable outer-class setter, so the implementation must
construct a Builder-owned node and transfer or clone its members before variable
scope/type checking. The probe used the locally installed Groovy 5.1.2 runtime;
the real tracer must prove the repository's Groovy 3, 4, and 5 lanes, generated
DSL/mirror behavior, runtime callback discovery, and rejection diagnostics.

## Next action

Execute LC-1 from ADR 0021: add the real-transform `@PostTree` tracer that
relocates a direct inner class into the Builder, emits one ordered Builder
runner, proves transferred instance state across multiple callbacks, and rejects
invalid class shapes. Keep inheritance rejected until LC-4 proves or records its
feasibility.
