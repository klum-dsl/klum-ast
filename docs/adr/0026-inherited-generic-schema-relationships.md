# Inherited generic Schema relationship specialization

Date: 2026-10-04

Status: Proposed extension of ADR 0005; feature intent confirmed in #180, mechanics await GEN-0 evidence

Implementation status: Planning only; no generic relationship support or compatibility qualification delivered

Target: 4.1 candidate, ADR/tracer-gated; not a release blocker

Tracking issue: [#180 — Specialize inherited DSL relationships from generic Schema bases](https://github.com/klum-dsl/klum-ast/issues/180)

Implementation plan: [ADR 0026 implementation plan](../implementation/adr-0026-inherited-generic-schema-relationships.md)

Parent decisions: [ADR 0005 — generated DSL support API](0005-generated-dsl-support-api.md),
[ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md), and
[ADR 0004 — AsBuilder composition](0004-asbuilder-composition-protocol.md).

Compatibility inputs: [ADR 0011 — Groovy lanes](0011-shared-multi-groovy-compatibility-contract.md) and
[ADR 0015 — generated-runtime linkage](0015-generated-schema-runtime-linkage.md).

## Context and authority

The canonical #180 maintainer decision confirms one use case: a reusable, separately compiled generic Schema library
specialized downstream without its source. Repeating concrete Provider declarations is a viable but costly workaround.
ScHelm's planned demand is secondary motivation, not additional scope. ADR 0005 reserved Model-declared generics for #180;
this extension retains its namespace, leaf SELF, and public-versus-hidden Builder decisions.

At planning base `7941dd087c704c4f6221f791934e685e493c3cc1`, `GeneratedDslSupport` already copies Model type parameters
onto public and hidden Builder declarations and appends SELF. Declaration copying does not prove inherited relationship
creation, type substitution or generated metadata. The implementation plan separates inspected facts from likely failure
paths; no binary-library experiment has been executed for this planning PR.

## Decision direction fixed by #180

### Existing relationships specialize in a named downstream Schema

An instantiable downstream DSL Schema resolves inherited DSL-valued parameters to named DSL classes. Supported single
relationships, Collection elements and Map values use that resolved type throughout creation, construction-state storage,
public Builder accessors/returns, closure delegates, completed Model access and source mirrors. Map keys and supported
container policies retain their established semantics. Intermediate generic inheritance composes substitutions rather
than resetting a parameter to its bound.

Illustrative target syntax; an acceptance example, not a claim of current support:

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key

// Separately compiled library.
@DSL
abstract class Environment {
    @Key String name
}

@DSL
abstract class EnvironmentProvider<T extends Environment> {
    T primary
    List<T> backups
    Map<String, T> environments
}

// Downstream compilation: only the library JAR is available.
@DSL
class OrderEnvironment extends Environment {
    String orderQueue
}

@DSL
class OrderEnvironmentProvider extends EnvironmentProvider<OrderEnvironment> {}

def provider = OrderEnvironmentProvider.Create.With {
    primary('main') { orderQueue 'orders' }
    backup('reserve') { orderQueue 'overflow' }
    environment('production') { orderQueue 'orders' }
}
assert provider.primary.class == OrderEnvironment
assert provider.backups[0].class == OrderEnvironment
assert provider.environments.production.class == OrderEnvironment
```

The leaf root factory returns `OrderEnvironmentProvider`; its active-session factory view and construction closure use
`OrderEnvironmentProvider_DSL.Builder<OrderEnvironmentProvider>`. Inherited child creation returns the public
`OrderEnvironment_DSL.Builder<OrderEnvironment>` and delegates to that contract. Completed access exposes
`OrderEnvironment`, `List<OrderEnvironment>` and `Map<String, OrderEnvironment>` in the leaf's effective type context.
The child-only `orderQueue` operation exposes bound-type leakage to static consumers.

Class-file Signatures, effective inherited types, delegate annotations, runtime values and mirrors must agree. An inherited
field can retain its generic declaration and erased JVM descriptor; reflection on the base field alone need not report a
concrete child. GEN-0 settles whether parameterized inheritance or specialized overrides/bridges supply exact public
access to binary Java/static Groovy consumers. Hidden storage must be coherent with that effective type and must not
create two independent values for one inherited relationship.

### Unresolved construction fails; resolved abstract targets retain existing rules

Raw or unresolved DSL-bearing instantiable specializations fail actionably instead of allocating a generic bound. The
diagnostic names the concrete Schema, inherited member and unresolved parameter/inheritance edge, and directs the author
to a named concrete specialization or concrete declarations. Reusable abstract generic bases remain compilable. Diagnostic
stage and guards for dynamic/binary entrypoints are open mechanics.

A resolved named abstract target is distinct from an unresolved variable. Existing explicit-subtype, field/type default
implementation and abstract-target rejection rules apply after substitution. No unresolved variable falls back to its
bound or default implementation.

### Builder-first state, ownership and serialization remain authoritative

Specialized creators join the parent's Construction session and preserve initializer, Template, `PostCreate`, configuration,
`PostApply`, graph-phase, materialization and validation ordering. Roots return completed Models; child creators return
Builders. There is no nested root lifecycle or second materialization engine. Composition, LINK/OPTIONAL_LINK entry
ownership, copy/Template identity and cyclic materialization retain ADRs 0003/0004 semantics.

Ordinary completed Models and serialized Model graphs retain no Builders, sessions or specialization machinery capturing
construction state. Existing Template serialization constraints remain unchanged. Public build-time types stay inside
`Foo_DSL`; clients may name but not implement, subclass or construct them. One leaf SELF threads the hierarchy and
`KlumBuilder<SELF>` remains the narrow capability. Any new generated runtime calls use the generated-only linkage
boundary; metadata does not become a generic client/helper API.

### Compatibility and recompilation are explicit

Preserve established non-generic source behavior and public signatures. The introducing compiler may require recompiling
and republishing the generic base, then recompiling the specialization and its consumers against that new library.
Do not guarantee old-compiler Schema bytecode against a newer runtime. This does not authorize changing unrelated stable
4.x non-generic public API or generated-runtime ABI.

Use one KlumAST production artifact under Groovy 3/4/5, with base, leaf and Groovy consumers compiled anew per lane.
Mirrors derive from actual bytecode and remain IDE-only, excluded from production compilation, archives, published variants
and downstream build inputs.

## Mechanics deliberately left to the tracer

| Open mechanic | Evidence needed before selection |
| --- | --- |
| Generic-base representation of a Model variable and its child Builder counterpart | Binary assignments for all three shapes; invariant container types remain coherent; one leaf SELF; no blanket Model/Builder substitutability |
| Parameterized inheritance versus leaf overrides/bridges | Class-file Signatures/descriptors and overload dispatch for base-typed and leaf-typed consumers; no illegal return override or duplicate state |
| Runtime concrete target resolution | JAR-only base without source AST metadata; exact child subtype and abstract/default rules through intermediate inheritance |
| Standard signatures/annotations versus persistent generated metadata | Fresh downstream compiler/runtime loaders, truthful mirrors/delegates; inventory every added emitted linkage if metadata is needed |
| Substitution identity and caching boundary | Renamed/shadowed parameters and two concrete leaves of the same base without AST/reflection cache contamination |
| Diagnostic stage and entrypoint guards | Raw/unresolved construction fails before child allocation/side effects; reusable abstract base compiles; dynamic/binary calls never silently use the bound |

These questions stay within the confirmed feature contract. GEN-0 records chosen mechanics, evidence and rejected alternatives
here before GEN-1. Failure is a stop/replan signal that may defer the 4.1 candidate; it does not authorize weaker typing
or broader scope.

## Consequences

Compiler, runtime and mirrors need coordinated binary acceptance; copied declarations cannot qualify support. Generic-base
recompilation is an adoption requirement when the introducing compiler needs it. Lifecycle and serialization remain state
boundaries rather than type-token APIs. User guidance and release notes advertise support only after executable qualification.
#180 stays canonical and open pending implementation acceptance; 4.1 can ship without it.

## Rejected alternatives and excluded scope

- **Copy parameters only or prove only co-compilation:** copying already exists; neither proves binary creation/delegates.
- **Construct the bound or require base source downstream:** defeats the accepted use case and conceals wrong creation.
- **Caller-parameterized generic roots or broad Model/helper APIs:** deferred proposals, not #180 acceptance.
  Generic converter methods remain [#183](https://github.com/klum-dsl/klum-ast/issues/183).
- **Models during construction or retained Builders after completion:** violates lifecycle and ownership regardless of erasure.
- **Independent mirrors/GDSL patches:** invents an IDE contract binary consumers cannot use; ADR 0005's bytecode-to-mirror
  lifecycle remains authoritative, with no new IDE transport.
