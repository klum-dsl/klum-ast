# Structured Schema input artifacts from generated Builder contracts

Date: 2026-10-20

Status: **Proposed**. Decisions below are the proposed architectural contract; recorded maintainer constraints are
identified separately. No artifact, plugin, importer capability or release commitment is delivered by this document.

Implementation status: planning only. [Implementation and qualification plan](../implementation/adr-0031-structured-schema-input-artifacts.md).

Tracking issue: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Need horizon: provisional next 4.x integration line, subject to tracer evidence and release review; no milestone selected.

Parent decisions: [ADR 0003](0003-builder-first-materialization.md), [ADR 0005](0005-generated-dsl-support-api.md),
[ADR 0009](0009-jackson-interoperability.md), [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0022](0022-conservative-named-map-safety.md), [ADR 0023](0023-schema-owned-constraint-annotations.md),
[ADR 0025](0025-portable-schema-gdsl-metadata.md), [ADR 0027](0027-owning-relationship-metadata.md),
and [ADR 0029](0029-default-only-annotation-propagation.md).

## Context and authority

Schema Developers need publishable input documentation that Model Writers and tools can consume without reconstructing
Groovy source or inspecting an instantiated Model. Schelm supplies concrete demand for a scalar abstract NamingStrategy
and a Map of abstract Environment values. Generated factory signatures describe calls, but cannot establish a closed
subtype set or stable external aliases. Schelm owns deployment interpretation and authoring workflow.

The October 20 maintainer comments on #529 settle these constraints:

- An optional separate module and Gradle plugin materialize artifacts, analogous to AnnoDocimal source mirrors. Core has
  no runtime wire-format dependency; neither Jackson adapter is a descriptor-generation prerequisite.
- Generated public Foo_DSL.Builder and Factory contracts are primary discovery inputs, including AnnoDocimal documentation
  and DefaultValues. AST capture supplements missing facts only; raw Schema reconstruction is not the primary design.
- Eligible operations require normalization into input properties. A public method or named-map key alone is insufficient.
- The first Jackson-qualified view supports property rename and ignore only. General Jackson semantics remain outside it.
- Dynamic factories with leading values-bag Map and/or runtime Class/Factory selector parameters are excluded. Ordinary
  Map-valued properties remain eligible. Abstract targets require explicit closed Schema-owned aliases; instance keys
  are separate data. Subtype scanning, overload inference, untargeted closures and lifecycle/helper inputs are excluded.

The [plan's evidence inventory](../implementation/adr-0031-structured-schema-input-artifacts.md#evidence-and-current-behavior)
reconciles source and tests against current master. Earlier feasibility evidence at commit
`192acd255196f37d11651c11d6547d5c06ef9b00` proposed broader source capture and conditional “4.2” placement. Its fidelity
counterexamples remain useful; those source-first and release assumptions are superseded by the instructions above.
[ADR 0026](0026-inherited-generic-schema-relationships.md) remains proposed: its generic specialization must not be
advertised as delivered by this work.

## Decision

### D1 — One optional producer with a small normalized descriptor

Propose one optional schema-artifact module containing a class-file reader, a property normalizer, a versioned descriptor
codec and conservative projections. A separate optional Gradle plugin invokes it after Schema compilation and owns
reproducible output/publication. Naming and packaging details remain gated below. No serializer, YAML parser, Jackson
mapper or descriptor reader becomes a dependency of core, runtime or generated Foo_DSL signatures.

Discover explicitly selected roots and their reachable public contracts, including inherited members and concrete
Collection/Map value types. Read class-file signatures, annotations and AnnoDocimal carriers without initializing Schema
classes, invoking factories or evaluating code. Source mirrors remain IDE-only: never parse, compile or publish them as
semantic metadata. Do not scrape rendered JavaDoc prose for property identities or generator English for operation kinds.

Normalize into a small internal property plan: declaration identity; public operation references and role; value/container/
key shape; nested Builder target; relationship kind; participation per input view; documentation/default/rule facts; and
fact availability. It describes input capabilities, not Model storage or an executable command list. Stable declaration
identity precedes wire rename; inherited views retain their declaring identity. No general schema metamodel or public
property-plan SPI is justified by the first consumer.

Use the public contract for all discoverable facts. Join narrowly scoped retained Schema/type/annotation declarations for
facts not projected there; only then consider a compiler-emitted supplementary fact. The plan enumerates these gaps and
requires a binary-only falsification tracer before expanding capture. Never resolve a missing fact by invoking a closure,
parsing hidden implementation names or decompiling an initializer. Older binaries can yield useful partial descriptions;
missing metadata must be explicit, never an apparently complete empty artifact.

### D2 — Define a property grammar, separate from named-map dispatch

Admit typed generated scalar setters/configurators, declared collection/map value operations, fixed-target nested creators
with resolvable public Builder delegation, and explicitly declared virtual fields when their input role is provable.
Collapse generated setters, singular adders and equivalent fixed-target overloads into one canonical property. Retain
operation provenance; do not expose every spelling as another external key. Return type alone does not prove attachment.

Exclude getters, queries, arbitrary Builder.Method operations, copyFrom, converters without a separately admitted wire
mapping, script/import/Template operations, lifecycle/session helpers and untargeted closures. A method-first override or
ambiguous overload must produce a diagnostic or an explicit unsupported classification, never guessed storage semantics.
The [normalization matrix](../implementation/adr-0031-structured-schema-input-artifacts.md#property-normalization-matrix)
is the acceptance contract for these distinctions.

ADR 0022 deliberately describes *every* one-value Builder call, including copyFrom and user operations. Its NamedParam
catalog can cross-check target/type metadata; it cannot be reused as the wire-property catalog. No change to that broader
named-map contract is proposed.

Control Map parameters in generated creator/factory signatures are excluded as external operations, even when a fixed
target is known. The fixed target's property graph can still be described through its Builder contract and a supported
closure-only/fixed-target role. Ordinary Map<K,V> property values remain candidates; distinguish them by operation role,
not by testing whether a parameter erases to Map. Dynamic selector exclusion applies to leading first/second selector
positions with or without the values bag; it is not a prohibition on map properties.

### D3 — Keep generic facts and mapper-qualified input views distinct

The generic descriptor includes eligible construction-only inputs and participation limits. The first optional
`jackson-property-input` view describes the managed root-import subset with direct field/property-level JsonProperty
name and JsonIgnore exclusion. The view name is provisional. Unsupported annotation attributes, conflicting names,
getter/setter conflicts or unavailable binding correspondence prevent a qualified projection; they are not silently
interpreted as broader Jackson support.

No naming strategies, JsonAlias, mixins, views, serializers/deserializers, creator-argument mapping, coercion model,
Jackson polymorphism or general mapper introspection is included. The existing importer continues supporting its current
wider contract. The first view does not claim to describe an arbitrary caller-configured mapper. Jackson 2 annotations
may be read in optional tooling; a Jackson runtime is needed only for adapter integration tests/import execution.
[#929](https://github.com/klum-dsl/klum-ast/issues/929) Jackson 3 remains separate and untargeted.

Builder-only, PROTECTED/IGNORED, Owner/Role, key and virtual-field participation must be checked against the selected
importer rather than inferred from completed getters. In particular BUILDER inputs may be described generically but are
excluded from the existing Jackson input. A virtual field may be a valid Builder input yet have no managed Jackson field
binding. This asymmetry is recorded per view.

### D4 — JSON Schema is a qualified projection, with truthful fidelity

Propose ordinary JSON as the descriptor transport and JSON Schema Draft 2020-12 as a separately identified optional
projection. Include one useful concrete rename/ignore projection in the first tracer to test the descriptor abstraction.
The descriptor preserves facts and limits the projection cannot express. Do not introduce a custom required vocabulary.
Artifacts carry separate descriptor-format, producer, Schema component/content and projection identities. Schema package
owners retain responsibility for compatibility of their external inputs.

A projection states its root, view, dialect, admitted shapes and omissions. Requested unsupported input shapes fail
qualified generation with declaration-linked reasons; an explicitly requested descriptive/partial result may retain
unknowns but must not claim full input validation. Structural coverage and runtime-validation coverage are distinct:
even a complete admitted structural projection does not prove full Klum acceptance.

Preserve effective Required/Validate facts, message/severity and completed-model timing, including supported propagation
and annotation composition. Required does not establish mandatory raw input: declared defaults and lifecycle completion
may satisfy it. Defaults distinguish known declared literals, DefaultValues candidates, contextual/computed mechanisms,
known absence and unavailable capture. Annotation member defaults are not automatically applied property defaults.
No Model, Template, closure, converter or lifecycle execution is allowed during extraction.

Do not translate arbitrary Groovy validation, RelationshipConstraint, provider/group-dependent Bean Validation, regexes,
links or cross-object checks into assertions. The first projector emits proven structural types, enums, containers and
concrete nested shapes; declarative constraint translation is a later, independently qualified extension. A declared
literal is not automatically a JSON Schema default; a computed default is never synthesized. Fixed/advanced/recommended
editorial intent remains consumer policy, orthogonal to completed validation and override authority.

The standard's `required` asserts presence; `default` is annotation, and readOnly/writeOnly are annotations rather than
permission enforcement ([Validation §§6.5.3, 9.2, 9.4](https://json-schema.org/draft/2020-12/json-schema-validation)).
`$ref` identifies schemas, not existing Model instances ([Core §8.2.3](https://json-schema.org/draft/2020-12/json-schema-core)).
These are reasons to retain a companion descriptor rather than promise universal validation or import/export round trips.

### D5 — Constrain polymorphism now; defer its carrier and execution decision

**Deliberate open decision:** the first ADR does not choose a new alias annotation, JavaDoc tag, sidecar format or wire-tag
spelling. No actual alias carrier or generic subtype-selection API is approved here. Concrete descriptor/projection
slices can proceed; abstract-property import/projection release waits for the explicit gate in the plan.

The following are fixed admission requirements for that later choice:

- A Schema-owned declaration closes the allowed set for the particular relationship/input. Reuse literal Field.alternatives
  where available; do not execute its generated closure class or infer the set from generated convenience methods.
  Existing automatic alternative names are not proof of an explicit closed declaration.
- Abstract scalar NamingStrategy and abstract Map<String, Environment> values both need an explicit type tag. A concrete
  default implementation does not silently establish a closed subtype set or remove the abstract-target gate.
- Qualified JSON Schema uses oneOf branches with a required tag and distinct const values. The tag is Schema-owned,
  collision-checked and scoped to the relationship. Unknown, missing or ambiguous aliases reject before child allocation.
  Alias targets must be assignable, concrete and describable; overlapping payload shapes cannot select a type by inference.
- Map entry keys identify instances; they never select a subtype. Normalize property, entry key, child key and type tag
  independently. Key/data conflict behavior follows the chosen importer, rather than an invented generic policy.
- Class names, classpath scans, runtime Class/Factory selection and general Jackson polymorphism remain excluded.

Until the gate passes, preserve the declared abstract target and `closed-alias-contract-unavailable` fact. A requested
qualified projection containing it fails; do not publish permissive empty schemas or infer aliases. The generic descriptor
is useful without resolving this question. Even after a carrier is selected, importing aliases needs a bounded optional
adapter/lowering decision that preserves ADR 0009. No second lifecycle/replay engine or new universal importer is approved.

### D6 — Preserve runtime semantics and consumer ownership

YAML remains caller/parser-owned input to existing managed import. Documentation/projection does not reroute FromMap,
change key precedence, unknown/null/empty behavior, Templates, LINK identity, OPTIONAL_LINK fallback, lifecycle phases,
materialization, immutable completed state or construction paths. JSON Schema checks a JSON-compatible data model;
YAML tags, aliases, non-string keys and duplicate keys need explicit parser qualification
([YAML 1.2.2 §§3.2, 10](https://yaml.org/spec/1.2.2/)). No merge/patch behavior is added.

Schelm owns profiles, scopes, provider and deployment-environment selection, context injection, package/root resolution,
scaffolding, source-layer precedence, ConfigMap/Spring projection and user-facing diagnostics. Klum reports generic facts
and extraction diagnostics. A later structured example-values facility is owned by
[#930](https://github.com/klum-dsl/klum-ast/issues/930): this artifact may eventually consume its safe metadata but does not
choose its carrier, syntax or propagation now. Example omission does not block the concrete tracer; advertising structured
examples requires #930's accepted contract.

[#481](https://github.com/klum-dsl/klum-ast/issues/481) deployment documentation and this work are coherent, independently
releasable tracks. [#528](https://github.com/klum-dsl/klum-ast/issues/528) Spring bridge remains separate.
Compose/copy [#304](https://github.com/klum-dsl/klum-ast/issues/304),
[#310](https://github.com/klum-dsl/klum-ast/issues/310), and
[#349](https://github.com/klum-dsl/klum-ast/issues/349) may later reuse an internal property plan, but neither track depends
on the other. No public framework is extracted in anticipation of that reuse.

## Open choices and decision gates

The [plan](../implementation/adr-0031-structured-schema-input-artifacts.md#public-choices-and-gates) recommends defaults for
artifact/module names, embedded versus companion publication, resource path, format evolution and projection identity.
They are proposals to test, not settled public API. Freeze them only after the binary producer/consumer tracer. Unknown
format majors and mismatched Schema artifacts must reject; exact failure spelling and compatibility details need review.

Alias carrier, tag spelling/collisions, input lowering and migration coexistence with existing Jackson subtype mapping are
one later decision gate before abstract support. No maintainer choice is needed to finish this planning PR or start the
private concrete tracer. Do not publish a stable external artifact contract or claim Schelm polymorphic support first.

## Consequences

- The real layer-specific public input API remains authoritative; a small supplementary fact seam can fill measured gaps
  without duplicating Schema compilation or adding a runtime wire dependency.
- Public generated signatures, ownership, materialization, Model serialization and existing import behavior stay unchanged.
  Any necessary additive retained compiler facts require source/binary/mirror and Groovy 3/4/5 qualification before shipping.
- No runtime cost is imposed on users who do not opt into artifacts. Plugin application does not install an importer.
- Build output becomes a compatibility surface only after format/publication choices are accepted. Stale or partial facts
  are observable conditions, not permission to invent accurate-looking schemas.
- The next 4.x integration line is a plausible horizon for concrete metadata; abstract import and full consumer workflow
  carry separate gates. This proposal does not revise milestones or promise “4.2” or “5.0”.

## Rejected alternatives and overengineering review

Reject raw source reconstruction, reflection over completed getters alone, scraping IDE mirrors, dumping all Builder
methods, reusing the broad NamedParam catalog as wire properties, running an empty Model to discover defaults, subtype
scanning and aliases inferred from Factory overloads. Each loses either real input semantics or the extraction boundary.

Do not build a general schema framework, general documentation DSL, mapper abstraction, universal JSON/YAML execution
model, arbitrary validator translator, profile/scaffold system or shared composer API. One normalizer, one descriptor and
one narrow projection provide the first useful proof. Add another seam only for a concrete failing acceptance case.
