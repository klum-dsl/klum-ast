# Gradle-driven JSON Schema generation from compiled KlumAST Schemas

Date: 2026-10-20

Status: **Proposed revision for maintainer acceptance**. The architecture below records accepted maintainer decisions;
plugin names and implementation mechanics remain provisional. This documentation does not authorize implementation.

Tracking issue: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Plan and supporting research: [ADR 0031 plan](../implementation/adr-0031-structured-schema-input-artifacts.md).
Need horizon: provisional next 4.x integration line; no release milestone assigned.

Parent decisions: [public generated contracts](0005-generated-dsl-support-api.md),
[Jackson interoperability](0009-jackson-interoperability.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md), and
[named-map safety](0022-conservative-named-map-safety.md).

## Problem and primary need

Schema Developers need JSON Schema for the structured YAML/JSON inputs of KlumAST DSL Models. Model Writers should get
useful structural guidance, including readable external property names, without inspecting generated code or maintaining
another description by hand. ScHelm is a primary consumer; it owns how the generated file reaches its users.

The maintainer's refocusing decision supersedes the earlier descriptor-first proposal. Existing metadata-extraction
research remains useful evidence, but neither a generic metadata product nor complete KlumAST feature coverage is needed
before a useful concrete JSON Schema can be generated.

## Accepted architecture

JSON Schema Draft 2020-12 is the primary and only initial public artifact. An **optional Gradle plugin** generates it from
explicitly selected compiled Schema root types and their dependencies:

```text
Compiled Schema + generated Builder contracts
                    |
                    v
       Internal property normalization
                    |
                    v
            JSON Schema 2020-12
```

The plugin consumes compiled classes and required metadata, generates reproducible files as ordinary Gradle task outputs,
and supports incremental/up-to-date execution and configuration cache. Its dependencies stay outside KlumAST core and
runtime. Extraction reads class files without initializing Schema classes or executing constructors, factories, defaults,
converters, lifecycle methods or annotation closures. It does not reconstruct raw Schema source or scrape IDE mirrors.

A small internal normalized property representation is acceptable. It is neither a serialized/versioned descriptor nor a
public reader API or metadata SPI. Exact plugin/task/extension names and internal packaging can follow the first tracer.

The plugin owns generation and its own build output. **Consumers own artifact distribution, location and use**: ScHelm or
another consumer may copy, publish or embed the generated file independently. The plugin prescribes no Maven classifier,
publication coordinates, Schema-JAR embedding, external distribution directory, deployment lookup or registry.

## Discovery and property normalization

Generated public Foo_DSL.Builder and Factory contracts are the primary discovery source. Existing retained Schema
annotations may supplement facts missing from those contracts. Fixed public Builder targets and DelegatesTo metadata can
identify nested input; delegation alone does not establish attachment, key handling or a closed subtype set.

Normalize configurable scalar/enum properties, concrete nested DSL objects, collections, ordinary Map<String,V>, keyed
concrete relationships and inherited configurable properties. Equivalent setter/adder/creator operations describe one
property, rather than additional external keys. The tracer starts with a useful concrete subset; the supporting matrix
is a classification aid, not a demand to implement every relationship or Builder feature initially.

Exclude helper methods, queries, copyFrom, Template, lifecycle/session and other construction-control operations.
A leading creator Map values bag is a control parameter; an ordinary Map-valued property remains eligible. Dynamic
Class/Factory selectors do not define a static wire contract. Map entry keys identify instances, never subtype aliases.
Do not guess unknown or ambiguous operation roles. Diagnose unsupported requested shapes and fail the affected root's
generation rather than silently publishing a misleading permissive schema.

## Supported Jackson naming and exclusion

The initial subset reads field/property-level `@JsonProperty`'s explicit name and `@JsonIgnore`'s exclusion marker.
It must support names such as `service-name` and `class`, and omit ignored input properties. Other annotation attributes,
conflicting bindings or unsupported placement are qualification boundaries rather than implied support.

Qualify generated names, exclusions and admitted shapes against actual managed Jackson/YAML input. This does not claim
compatibility with arbitrary ObjectMapper configuration. Naming strategies, JsonAlias, mixins/views, custom codecs,
Jackson polymorphism, general mapper introspection and arbitrary coercion are deferred. The generator must not modify
the existing importer to force agreement; an unsafe representation is a reported qualification limitation.

## Structural validation and fidelity limits

Emit only demonstrably correct structural constraints initially. KlumAST completed-model Required is not JSON Schema
raw-input required: defaults or lifecycle processing may supply a value. Do not execute default closures, synthesize
contextual values, translate arbitrary Groovy validation or infer input permissions from completed-model immutability.
Advanced default and validation metadata may be omitted; document those omissions without inventing approximations.

JSON Schema validation does not replace KlumAST runtime/completed-model validation. Structurally valid input can still
fail binding, lifecycle or completed validation. The standard's required keyword checks presence and default does not
supply a missing value ([Draft 2020-12 Validation §§6.5.3, 9.2](https://json-schema.org/draft/2020-12/json-schema-validation)).
Existing importer key precedence, unknown/null/empty behavior, ownership, materialization and diagnostics remain intact;
no merge/patch or import/export round-trip promise is added.

## Exclusions and deferred decisions

Abstract NamingStrategy and Environment inputs are real ScHelm needs, but they do not block the concrete generator.
An abstract target without a qualified mapping is unsupported and must not produce a permissive schema. Future support
needs an explicit closed subtype set and tags distinct from instance keys. No classpath subtype scanning, class-name wire
selection, dynamic-factory alias inference, overlapping-payload inference or annotation-closure execution is permitted.
This PR chooses no new alias annotation, carrier, tag syntax or importer adapter; a separate maintainer decision is needed.

Additional defaults, declarative constraints, more relationship shapes, advanced Jackson compatibility and optional
companion metadata are evidence-driven extension candidates only. No companion format is designed now. Structured
examples remain [#930](https://github.com/klum-dsl/klum-ast/issues/930); Jackson 3 remains
[#929](https://github.com/klum-dsl/klum-ast/issues/929). Spring/deployment, composition, provider selection, profiles and
scaffold generation stay independently owned. No general schema framework, publication framework or docs DSL is required.

## First tracer and consequences

Phase A proves generation of one useful JSON Schema from real compiled artifacts: concrete types/containers, keys,
inheritance, rename/ignore, excluded operations, representative structured input and managed-import comparison. It must
prove extraction executes no Schema user code, and record missing metadata with the smallest possible supplementation
point. [Acceptance criteria](../implementation/adr-0031-structured-schema-input-artifacts.md#phase-a--executable-json-schema-tracer)
make that proof actionable. Only Phase A is eligible for separate immediate authorization after review; it has not begun.

Phase B integrates the successful tracer into the optional Gradle producer. Phase C considers extensions only when
consumer evidence justifies them. No unresolved architectural choice blocks Phase A. Existing generated APIs, core/runtime
dependencies, Model serialization and import behavior are unchanged by this decision; future implementation must qualify
its compiled-metadata assumptions across Groovy 3/4/5. Accepting this ADR does not establish tracer success or delivery.
