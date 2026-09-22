# Issue #420 — Lifecycle-class contract decision brief

Date: 2026-09-22

Status: **Needs maintainer decision — do not implement yet**

Issue: [#420 — Lifecycle classes](https://github.com/klum-dsl/klum-ast/issues/420)

## Decision required

Choose the source contract for a lifecycle class whose callback runs before
`INSTANTIATE`:

1. **Explicit state-typed callback (recommended).** A Builder-phase lifecycle
   class is a separate callback component. Its entry method receives the exact
   generated `Foo_DSL.Builder<Foo>` receiver (or an equally explicit generated
   Builder contract), so its source has no implicit `Foo.this` access. The
   compiler validates the state-typed receiver and the runtime invokes the
   component only while that Builder is in the active construction session.
2. **Projected lexical-inner class.** A source non-static inner class keeps
   direct model-member syntax, but the compiler creates and maintains a distinct
   Builder-side counterpart, including every lexical outer reference, field,
   method, inheritance, constructor, and diagnostic projection needed to make
   that syntax truthful before materialization.

The first option preserves the Builder-first state boundary with a small,
reviewable public contract. The second is a substantial compiler feature and a
new source-compatibility promise. Neither the original issue nor its #415
provenance chooses between them. Implementing the current shorthand
`@PostTree class Checks { void normalize() { host = ... } }` would silently
choose option 2, while invoking that class with a completed Model would silently
violate option 1 and Builder-first immutability.

This is the only decision needed before an ADR can fix the remaining spelling,
method ordering, and tracer slices. The recommended option should also decide
whether the component is static/no-outer-state and whether one named entry point
or several ordered public entry methods form the callback protocol; those are
part of the same public receiver contract, not runtime details.

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

Consequently, a lifecycle-class method cannot be mechanically moved or
delegated. Each callback family needs a distinct receiver contract: Builder
components before 40 and Model validation components after 40. This also rules
out a generic `KlumBuilder` callback API: it deliberately has no
schema-specific member surface, and a generic Model-to-Builder delegation would
repeat the rejected blanket projection model.

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

## Handoff condition

After the maintainer selects one receiver contract, draft a dedicated ADR and
an implementation plan. The ADR must state the exact annotation/type grammar,
entry-method and inheritance ordering, diagnostics, generated API/mirror
visibility, and Model/Builder boundary. The plan can then split the work into
independently testable compiler, runtime, documentation, and three-Groovy-lane
tracer slices.
