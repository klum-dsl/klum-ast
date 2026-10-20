# ADR 0031 plan — a JSON Schema tracer, then Gradle integration

Date: 2026-10-20. Status: **Planning only; all phases unstarted**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529). No issue acceptance/ownership or release placement changes.

The accepted architecture is compiled Schema/public Builder contracts → internal property normalization → JSON Schema
Draft 2020-12. The optional Gradle plugin is the producer; consumers own the file's packaging, distribution and use.
This plan replaces SA-0–SA-5. Only Phase A may receive immediate implementation authorization after review; none is granted
by this documentation PR. Phase B follows its evidence, and Phase C is not a committed backlog.

## Phase A — executable JSON Schema tracer

**Hypothesis:** generated public Foo_DSL contracts, supplemented by existing retained annotations where necessary, contain
enough information to generate a useful concrete input schema. A public generic descriptor is not needed to test this.

Use one small real Schema fixture with a concrete root, scalar/enum fields, a concrete nested child, a collection,
ordinary Map<String,V>, a keyed concrete relationship map and inherited properties. Include a separately compiled base
or child dependency and annotation metadata; extract from JARs without Schema source or mirrors where feasible. Keep
normalization private to the tracer. Generate one JSON Schema and validate representative JSON-compatible input with a
pinned Draft 2020-12 validator, then compare admitted JSON and YAML input with the existing managed importer.

The naming fixture includes these existing Groovy annotations (setup omitted):

```groovy
@DSL
class Service {
    @JsonProperty('service-name') String serviceName
    @JsonProperty('class') String serviceClass
    @JsonIgnore String internalState
}
```

**Acceptance criteria:**

1. The generated schema passes Draft 2020-12 meta-schema validation and correctly describes scalar/enum, nested, collection,
   ordinary map, keyed concrete and inherited input. Repeated generation of unchanged input produces identical output.
2. External properties include service-name and class, omit internalState, and agree with managed JSON/YAML binding under
   the recorded mapper/parser configuration. Pin validator, Jackson, YAML and KlumAST versions in the evidence. Test name
   collisions and unsupported naming metadata as diagnostics, not guessed mappings.
3. Helper/query/copyFrom, Template/lifecycle/session, dynamic Class/Factory selectors and values-bag parameters do not
   become input properties. Ordinary Map<String,V> remains eligible. Prove map entry keys and child-key input follow the
   existing importer, including explicit child key versus map-key hint; no key becomes a subtype tag.
4. Successful and rejected extraction do not initialize Schema classes or run constructors, factories, property/default
   initializers, converters, lifecycle code or annotation closures. Use a forked extraction process with observable
   throwing/marker controls; class initialization must not be hidden by an in-process counter check.
5. Compare omitted/null/empty/false/zero examples where relevant to the admitted shapes. Demonstrate that structural
   success can coexist with completed validation failure and that defaults/lifecycle can satisfy completed Required
   without mandatory raw input. Do not translate or execute those rules during generation.
6. Abstract targets, ambiguous operation roles and other unqualified shapes produce declaration-linked diagnostics;
   generation of the affected root fails rather than producing permissive placeholders. Unsupported mapper behavior is
   reported without changing importer semantics. Full LINK, Template, virtual-field or other advanced coverage is not a gate.
7. Record each required fact unavailable from public contracts, the existing retained annotation that can supplement it,
   or the smallest possible future capture seam. If a necessary fact is absent, report the limitation and stop expansion;
   this phase does not implicitly authorize new annotations, generated-contract changes or runtime APIs.

Use existing Spock/compiler-test seams and lane-local Groovy 3/4/5 Schema compilation for the binary metadata proof.
New tests carry @Issue('529') and the Test suffix. A future documentary example carries @Tag('documentary') and @See.
A single green tracer commit can contain extraction, normalization, generated-schema checks and importer controls;
a separate evidence commit is useful only if it records independently meaningful findings. Keep code and passing tests
together. Record exact revisions, commands/results, supported shapes, omissions and missing metadata. No second consumer,
public reader, metadata codec or publication experiment is required.

**Gate:** review the evidence before promoting the tracer. No maintainer architecture decision currently blocks starting
Phase A once separately authorized; parser/validator versions and internal implementation choices belong to the tracer.

## Phase B — optional Gradle integration

Promote the successful tracer into an optional Gradle plugin following existing compiled-input tooling conventions.
Provide explicit root types, compiled Schema/dependency inputs, reproducible JSON Schema task outputs in the plugin's own
build output, up-to-date/incremental behavior and configuration-cache compatibility. Exact plugin/task/extension names and
internal helper packaging remain provisional until the tracer shows the needed configuration. Keep JSON Schema/tooling
dependencies out of core/runtime; applying the plugin does not change managed import.

Use Gradle/TestKit checks for clean and repeated generation, changed dependency/property input, deletion/stale-output
cleanup, configuration-cache reuse and deterministic output without machine paths or timestamps. Demonstrate a small
consumer task taking a generated file and independently copying or packaging it, as ScHelm could. Do not implement Maven
classifiers, Schema-JAR embedding, publication coordinates, external distribution layout, lookup, deployment or registry
infrastructure. A green plugin/task integration commit followed by an independent consumer example/docs commit is a
reasonable provisional boundary; refine it from Phase A evidence.

Future delivery includes a documentary test, a concise docs/user page and Gradle/Jackson guidance for supported shapes
and limitations, with sidebar/navigation and CHANGES updates as applicable. Run affected module tests and Groovy 3/4/5
qualification under repository rules, plus plugin checks and current-head CI/SonarCloud. None of these capability or
release-note claims belongs in this documentation-only revision.

## Phase C — evidence-driven extensions

Potential extensions are abstract polymorphic aliases, additional default metadata, declarative validation constraints,
more relationship shapes, advanced Jackson compatibility and optional companion metadata. These are candidates, not
mandatory implementation slices. Do not choose carriers, APIs, formats or acceptance matrices before Phase A evidence and
a demonstrated consumer requirement justify a separate decision. Abstract targets need a closed set and unambiguous tags
separate from instance keys; never infer them from classpaths, Class/Factory overloads or overlapping payloads.

## Supporting property-normalization matrix

This research guides classification; it does not require full support for every row before the initial concrete artifact.
Only supported structured-input properties become schema properties. Unsupported requested shapes fail with diagnostics;
known helper/control operations are excluded. Phase A's concrete acceptance list defines the initial proof.

| Observed shape | Normalization guidance |
| --- | --- |
| Scalar/enum field configurator and setX aliases | One property; generated spelling is not an additional external name |
| Concrete nested/fixed-target creator | Resolve public child Builder and attachment role; targeted DelegatesTo helps identify shape |
| Collection and singular/varargs adders | One array-valued property; no automatic uniqueItems or sorting claim from Java container type |
| Ordinary Map<String,V> and entry adders | One object-map property, distinct key/value roles |
| Keyed creator / map relationship | Normalize property and instance key separately; preserve importer key precedence |
| Inherited configurable property | Use effective supported public typing; unresolved generics are unsupported rather than guessed |
| Creator's leading Map values bag | Control parameter, never an input property |
| Runtime Class/Factory selector | Excluded call form, never a static subtype/alias declaration |
| Getter, query, helper, copyFrom, Template, lifecycle/session | Excluded operation; one-value NamedParam eligibility alone is insufficient |
| Untargeted/custom closure or ambiguous method-first override | Do not infer property role or target from body/return/bound |
| Abstract target | Unsupported initially; no permissive placeholder or inferred closed subtype set |
| BUILDER, PROTECTED/IGNORED, Owner/Role, virtual field | Check actual managed-input participation; public Builder availability does not prove input binding |
| LINK/OPTIONAL_LINK, custom Simple Value, unresolved/raw shape | No automatic owned-object, codec or generic POJO interpretation; qualify separately if demanded |

## Supporting source inventory and historical findings

These observations explain the approach and its risks; they are not an expanded initial delivery contract. Source/tests
were inspected, not executed as implementation evidence in this documentation revision.

| Source seam | Useful finding |
| --- | --- |
| [GeneratedDslSupport](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java), [contract tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupportSpec.groovy) | Public signatures, inherited interfaces, parameter annotations and AnnoDoc survive projection; measure operation/property identity in real binary extraction |
| [Factory delegation tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/FactoryClosureDelegationTest.groovy), [named-map tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/NamedMapMetadataTest.groovy) | Fixed delegates are useful; custom/dynamic delegates differ. NamedParam includes helpers/converters/copyFrom, so its catalog is broader than structured properties |
| [Schema Gradle plugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java) | AnnoDocimal source projection provides a compiled-input task lifecycle precedent; mirrors remain IDE-only and are not extraction inputs |
| [KlumDeserializer](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/internal/KlumDeserializer.java), [YAML tests](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonYamlInteroperabilityDocumentaryTest.groovy) | Configurable Model field binding and mapper metadata jointly define managed input; explicit child key precedes map hint. Existing richer mapping/export behavior must stay intact |
| [DefaultValues](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/layer3/DefaultValues.java), [DefaultPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DefaultPhase.java) | Declared candidates can depend on context, coercion and closures; do not evaluate them or mistake annotation defaults for effective input defaults |
| [Field](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Field.java), [copy exceptions](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AnnotationCopyExceptions.java) | Literal alternatives use a closure-class carrier; it must not be executed. Bean Validation annotations intentionally do not copy to Builder fields; advanced extraction can be deferred |

The [#529 history](https://github.com/klum-dsl/klum-ast/issues/529) records contract-first discovery, operation filtering,
ordinary-map eligibility and ScHelm's abstract NamingStrategy/Environment demand. Earlier feasibility/research is retained
at local commit `192acd255196f37d11651c11d6547d5c06ef9b00` in
`docs/implementation/issue-529-structured-metadata-feasibility.md` and
`docs/implementation/evidence/issue-529-json-schema-research.md`; these are historical paths, not files on this PR's base.
Its Required/default/lifecycle counterexample remains useful. Its generic descriptor/publication machinery and provisional
“4.2” scope are superseded; no companion format, digest protocol or future-consumer architecture is carried forward.

Standards references: [JSON Schema 2020-12 Core](https://json-schema.org/draft/2020-12/json-schema-core),
[Validation](https://json-schema.org/draft/2020-12/json-schema-validation) and
[YAML 1.2.2](https://yaml.org/spec/1.2.2/). JSON Schema checks a JSON-compatible instance model, not YAML syntax;
record the tested parser normalization boundary rather than promising arbitrary YAML tags, aliases or key semantics.

## Documentation revision validation and handoff

Validate both documents with Markdown/local-link/diff checks and standards/spec review. No generator, plugin, annotation,
API, Builder contract, importer, issue or milestone changes are part of this PR. Groovy lanes are omitted for this
non-executable documentation-only change. Preserve reviewed commits and add this revision as a follow-up commit.
Tracker impact remains related #529 with no curation/state change. Maintainer acceptance of the revised ADR and separate
Phase A authorization are the next actions; request Hive reconciliation rather than archiving the task.
