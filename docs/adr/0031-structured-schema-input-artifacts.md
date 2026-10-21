# Structured-input interpretation and Gradle-driven JSON Schema generation

Date: 2026-10-20. Architecture revised: 2026-10-21.

Status: **Proposed revision for maintainer review**. Ownership and guardrails below record maintainer direction;
operation syntax, naming precedence and interoperability choices require evidence. No production implementation is authorized.

Interpreter/language owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
[One shared feasibility-probe plan](../implementation/adr-0031-structured-schema-input-artifacts.md).
Need horizon: identified ScHelm demand, provisional next 4.x integration line; no milestone assigned.

Parent decisions: [Builder-first construction](0003-builder-first-materialization.md),
[AsBuilder composition](0004-asbuilder-composition-protocol.md),
[public generated contracts](0005-generated-dsl-support-api.md),
[Jackson interoperability](0009-jackson-interoperability.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md),
[named-map dispatch](0022-conservative-named-map-safety.md).

## Problem and ownership

Model Writers need structured input capable of invoking Schema-authored construction operations, including operations
that configure several properties. Schema Developers also need JSON Schema describing that language for authoring tools.
A field-binding schema alone cannot establish the semantics of custom Builder methods, argument forms or nested scopes.

Two related but independent capabilities address these needs:

| Capability | Owns | Boundary |
| --- | --- | --- |
| Structured Input Interpreter (#932) | Restricted operation admission, naming, arguments, nested dispatch, ordering, diagnostics and Construction-session integration over a parsed Map/List/scalar tree | Optional; parsers caller-owned, no YAML/Jackson dependency in core; not a second Model construction engine |
| JSON Schema Generator (#529) | Optional Gradle-driven Draft 2020-12 generation describing the admitted language from compiled contracts | Does not execute input or own language semantics; no public descriptor protocol |

They share the admission/naming contract proven by one probe, not a mandatory runtime dependency or public metadata SPI.
Managed Jackson import remains independently supported foreign-format interoperability under ADR 0009. It is neither
replaced by the interpreter nor the sole grammar for structured input. Any new opt-in operation language leaves existing
Jackson public signatures, modes, mapper behavior and diagnostics intact; this ADR does not redefine Jackson's wire contract.

## Interpreter admission hypothesis, not settled syntax

The minimum hypothesis is to recognize Schema-authored public operations on Foo_DSL.Builder/Factory, distinguish their
roles from internal controls, normalize external names/arguments privately, and dispatch through reusable existing
construction and Groovy overload machinery. `@Builder.Method` operations are first-class candidates even when they have
no matching Model field or change several fields. Assess scalar/enum, multi-argument, inherited and nested operations,
concrete custom factories, CollectionFactories and keyed/unkeyed relationships. Return type alone does not prove attachment.

Generated setters/adders may share a property representation where justified; custom operations must not be flattened into
invented storage properties. Public visibility, NamedParam eligibility or an arbitrary method-name pattern alone is not
admission. Unknown/ambiguous roles require diagnostics. The probe must establish what can be expressed without synthetic
closures or new lifecycle/overload engines before any public API or syntax is selected.

Accepted guardrails:

- Generated dynamic factory overloads with leading generic Map values-bag and/or first/second-position runtime Class/Factory
  selectors are not serializable operations. Ordinary Map<K,V>-valued properties remain eligible with a qualified key/value
  representation; do not confuse data maps with control arguments or every custom factory with a generated selector.
- Filter untargeted closures and framework lifecycle/session/helper/copy controls. Do not blanket-exclude Schema-authored
  Builder methods as helpers. Existing broad named-map calls, including copyFrom, retain their behavior unchanged.
- Nested owned values use Builders and the existing Construction session, not prebuilt Models. LINK/AutoLink resolution
  belongs to ordinary lifecycle work; no generic reference registry or external-context injection framework is introduced.
  Factories are a workaround for required construction context; context acquisition/provider selection remains external.
- Characterize ordering experimentally; a parsed Map's iteration or YAML source order is not an accepted language guarantee.

## Naming and unresolved language decisions

Assess identity/camel/kebab naming and explicit rename/exclusion once for both capabilities, with collision diagnostics.
Existing `@JsonProperty` names such as service-name and class, and `@JsonIgnore`, are narrow compatibility candidates;
no current ScHelm need requires Jackson annotations on factory arguments. These annotations do not imply arbitrary mapper
introspection, naming strategies, JsonAlias, mixins/views, codecs, coercion or Jackson polymorphism support.

The shared probe must present concrete choices for **multi-argument representation** (named objects, positional sequences
or existing maps), binary parameter names/defaults/overloads, **naming precedence** and **Jackson interoperability**.
In particular a Map/List may itself be one argument. Existing importer behavior must not be changed to force parity.
Where semantics overlap, compare them; where they differ, report a qualification boundary rather than invent equivalence.
Diagnostics must include source identity, input path, external operation, selected public contract, expected versus actual
shape/type and preserved cause, while leaving ADR 0009 diagnostics unchanged.

## Generator architecture and fidelity

```text
Compiled Schema + public Foo_DSL.Builder/Factory contracts + proven admission/naming
                                |
                                v
                  Internal operation/property normalization
                                |
                                v
                       JSON Schema Draft 2020-12
```

An optional Gradle plugin accepts explicit roots and compiled dependencies, generating reproducible JSON Schema as ordinary
task outputs with incremental/up-to-date and configuration-cache behavior. Prefer fully qualified generated class identities
for `$defs`; prove deterministic references, escaping and collision handling across modules. Existing retained Schema
annotations may supplement absent facts; narrowly supplementary compiler data requires a measured gap. Source/mirror
reconstruction is not primary discovery. No Schema constructor, factory, initializer/default, converter, lifecycle method
or annotation closure executes during extraction. Runtime interpretation intentionally executes admitted Schema code.

Consumers own packaging, distribution, final location and use. No Maven classifier, embedding requirement, deployment
lookup, publication framework or universal registry is prescribed. JSON Schema is the only initial generator artifact;
normalization stays private, without serialized generic descriptors, codecs, migrations or public readers. Plugin/task/
extension names and internal packaging remain provisional; tooling dependencies do not enter core/runtime.

Structural validity does not imply successful interpretation, binding, materialization or completed validation. Required
may be satisfied by defaults/lifecycle and is not raw-input required. Do not evaluate default closures, translate arbitrary
Groovy validation or imply that schema references encode LINK identities. Advanced defaults/constraints may be omitted
honestly. [Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation) distinguishes presence assertions
from default annotations; no merge/patch, execution-order or round-trip promise follows from schema generation.

## Polymorphism and independent concerns

ScHelm needs scalar abstract NamingStrategy and Map<String, Environment>. Generated factories describe calls, not a closed
subtype set or serialized aliases. Abstract input targets remain explicit unsupported negatives until a qualified mapping
is decided. Eventually require closed Schema-owned aliases, reuse literal Field.alternatives where applicable, and reject
unknown/ambiguous aliases. Instance keys are data, not discriminators. No subtype/classpath/factory inference, class-name
wire selection, overlapping-shape inference or annotation-closure execution. Alias carrier/tag syntax remains open.

#930 examples, #929 Jackson 3, #481 deployment, #528 Spring and #304/#310/#349 composition/copy stay related and independently
releasable. ScHelm provider/environment/root/package selection, context injection, OPTIONAL_LINK fallback, profiles and
scaffold rendering remain out of the interpreter's generic scope. No universal deserializer, synthetic closures, reference
registry, public descriptor/codec/registry, publication system, annotation DSL or lifecycle framework is approved.

## Evidence gate

Plan one bounded shared feasibility probe comparing named-parameter dispatch, all four managed Jackson modes and minimal
Builder-operation interpretation, then emitting a schema for the measured admitted subset. Include the compiled cross-module
Layer 3 and no-execution checks in the plan. #932 owns language/reuse findings; #529 owns generator findings in the same
report. No probe is run here; execution awaits separate authorization. Do not preassign implementation slices or workers.
After evidence, the maintainer selects syntax/interoperability and any implementation scope. This revision changes no
production contract and makes no implementation-readiness claim for unresolved semantics.
