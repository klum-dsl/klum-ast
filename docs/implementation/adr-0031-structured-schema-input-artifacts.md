# ADR 0031 plan — structured Schema input artifacts

Date: 2026-10-20. Status: **Proposed; every implementation slice below is pending**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Primary issue: [#529](https://github.com/klum-dsl/klum-ast/issues/529).

Outcome of this change: reviewable architecture and implementation/acceptance plan only. No production module, plugin,
fixture, test, user capability, milestone or issue mutation is included. Proposed names and examples are discussion syntax.

## Evidence and current behavior

Planning base: fetched `origin/master` at `6a327addf` on 2026-10-20. The supplied worktree initially contained a detached
release-documentation commit; the issue branch starts at current master so that unrelated commit is not part of this PR.
ADR 0030 already exists on master; 0031 is the next available number at this observation.

Read the live bodies and complete comment histories of #529/#481/#528/#929/#930 on 2026-10-20. The controlling decisions
are [generated-contract/tooling direction](https://github.com/klum-dsl/klum-ast/issues/529#issuecomment-6298846039),
[Schelm consumer findings](https://github.com/klum-dsl/klum-ast/issues/529#issuecomment-6299497005), and
[dynamic factory exclusion and key distinction](https://github.com/klum-dsl/klum-ast/issues/529#issuecomment-6299603337).
Those findings are reported consumer evidence, not a Schelm build performed by this task.

The prior feasibility assessment and standards research were read from local retained commit
`192acd255196f37d11651c11d6547d5c06ef9b00`, files
`docs/implementation/issue-529-structured-metadata-feasibility.md` and
`docs/implementation/evidence/issue-529-json-schema-research.md`. They are not files on this planning base or proof of a
published generator. The assessment's Required/default/lifecycle counterexample remains relevant; its source-first
capture proposal, broad mapper admission list and “4.2” recommendation do not override the later maintainer constraints.

The [architecture map](issue-curation/architecture-map.md) is navigation; current source is authoritative where its older
package links or runtime vocabulary differ. Parent ADRs and [Builder-first migration](../user/Builder-First-Migration.md)
were checked. The relevant observed seams are:

| Seam | Confirmed behavior and consequence |
| --- | --- |
| [GeneratedDslSupport](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java) | `projectImplementation` projects public non-static, non-synthetic methods; `addProjectedMethod`/`cloneParameters` copy annotations, signatures and docs. Public inheritance and public Builder target types are usable discovery inputs. This is more than a storage-field catalog. |
| [GeneratedDslSupportSpec](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupportSpec.groovy) | Existing cases inspect public binary methods, relationship closure omission, meaningful AnnoDoc, delegates, Java/static Groovy consumers and mirror parity. The case `generated public methods and their source mirror retain meaningful AnnoDoc` explicitly inspects keyed child, collection and map creators, simple adders and helpers. These tests are evidence of existing contracts, not executions in this planning task. |
| [FactoryClosureDelegationTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/FactoryClosureDelegationTest.groovy) | Root/custom factories carry concrete public Builder DelegatesTo with DELEGATE_ONLY; custom delegates and returns can differ. Factory return alone is insufficient to infer property creation. |
| [NamedMapMetadata](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/NamedMapMetadata.java), [NamedMapMetadataTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/NamedMapMetadataTest.groovy), [ADR 0022](../adr/0022-conservative-named-map-safety.md) | The 4.1 public catalog includes copyFrom, setX, converters, adders and arbitrary one-value Builder.Methods; overloaded values can become Object. Dynamic Class/Factory maps and custom/import/Template maps are deliberately outside native metadata. It cannot define the narrow artifact grammar. |
| [DSLASTTransformation](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java) | Generates field/adder/relationship operations and moves initializers/state into Builders; BUILDER fields leave the Model. Creator tags exist, but complete operation-to-property identity must be proved in class files. No string matching of generated prose. |
| [Field](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Field.java), [AlternativesSpec](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/AlternativesSpec.groovy) | `alternatives` is a Class-valued closure carrier, source-restricted to literal String-to-Class mappings. Explicit aliases and automatically discovered/named alternatives coexist in existing DSL syntax. Calling the closure or enumerating convenience methods cannot establish explicit closure of a wire subtype set. |
| [DefaultValues](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/layer3/DefaultValues.java), [DefaultPhase](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DefaultPhase.java), [documentary tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/DefaultValuesDocumentaryTest.groovy) | Runtime-retained meta-annotation. Runtime uses nondefault supplied members, valueTarget, hierarchy and owning-field context; it may coerce or execute closures. Descriptor inputs are declarations and candidate values, not evaluated effective defaults. |
| [DocUtil](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/doc/DocUtil.java), [KlumDocumentation](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/KlumDocumentation.java) | Consume AnnoDocimal's immutable Documentation/AstDocumentation contract and author generated docs. Reuse supported extraction, not a new documentation DSL. |
| [AnnotationCopyExceptions](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AnnotationCopyExceptions.java), [ADR 0029](../adr/0029-default-only-annotation-propagation.md) | Bean Validation annotations intentionally do not copy to Builder fields; effective annotation lowering/propagation has ordering and retention limits. Retained Model/annotation declarations are a narrow supplementary source, not a second discovery authority. |
| [KlumAstSchemaPlugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java), [mirror integration tests](../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy) | SourceProjectionTask reads compiled *_DSL.class plus referenced classpath and emits IDE-only mirrors; source sets/archives exclude them. The artifact plugin follows compiled-input/task lifecycle ideas without consuming mirror output. |
| [GDSL producer](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumSchemaGdslProducer.java), [ADR 0025](../adr/0025-portable-schema-gdsl-metadata.md) | A useful precedent for opt-in cached generation, matched component selection and publication tests. Its editor payload, classifier and project-wide materialization are not this descriptor's format or lifecycle. |
| [KlumDeserializer](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/internal/KlumDeserializer.java) | `resolveProperties`/`toResolvedProperty` correlate mapper properties with configurable Model fields; excludes Owner/Role, PROTECTED/IGNORED/BUILDER. Key resolution takes explicit child key before map key hint. Owned map discovery decodes keys, preallocates same-session Builders, then binds once. Existing polymorphism uses configured Jackson type deserialization. None is a generic descriptor API. |
| [YAML documentary test](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonYamlInteroperabilityDocumentaryTest.groovy), [importer tests](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/KlumJacksonImporterSpec.groovy), [ADR 0009](../adr/0009-jackson-interoperability.md) | Existing YAML fixture proves asymmetric enriched output, mapper-owned unknown policy, explicit LINK resolution and value-only Templates. Its JsonAlias/JsonTypeInfo/custom codec examples exceed the first descriptor view. Preserve them; do not narrow the importer to match the new projector. |

### Concrete public-signature check

The existing generated-contract test identifies these actual public signatures (Java-like notation abbreviated here):

```text
Product_DSL.Builder.primaryChild(Map, String, Closure)
Product_DSL.Builder.primaryChild(Map, String)
Product_DSL.Builder.children(Map, String, Closure)
Product_DSL.Builder.childrenByName(Map, String, Closure)
Product_DSL.Builder.metadata(Map)
Product_DSL.Builder.metadata(String, String)
Product_DSL.Builder.label(String)
```

The first four are creator *calls*, with a values bag and instance key; they are not four external properties. The last
three describe an ordinary Map-valued property and a collection element operation; rejecting every Map signature would
lose valid input. The public contract also exposes applyLater and copyFrom, which must not become properties. This
counterexample establishes why signatures need a role/property join and why a NamedParam dump is insufficient.

## Boundaries and proposed module shape

| Owner | Responsibility / dependency boundary |
| --- | --- |
| Proposed optional `klum-ast-schema-artifacts` | Class-file discovery, role normalization, descriptor read/write, fidelity diagnostics and small JSON Schema projector. Tool dependencies are private to this artifact; no Gradle/Jackson mapper types in its generic descriptor contract. Start with internal interfaces, not published extension SPIs. |
| Proposed optional `klum-ast-schema-artifacts-gradle-plugin` | Separate plugin artifact and marker, explicit roots/views, compiled classpath inputs, isolated worker execution, generated resource outputs and optional matched publication. Reuse plugin conventions by narrow implementation reuse, not applying generation automatically through the existing Schema plugin. |
| `klum-ast` / annotations | Unchanged initially. Only a measured gap may justify small additive retained role/origin/literal facts in the existing generation seam. No JSON/YAML serializer or new scheduling framework; source annotation declarations remain Groovy-free where required. |
| `klum-ast-runtime` / Foo_DSL | Existing construction/runtime contracts only; no descriptor state in companions, Builders, Models or generated signatures. Class-file metadata additions must be optional and compatible. |
| Existing `klum-ast-jackson` | Integration/differential test oracle and existing managed importer. Any accepted alias execution adapter remains optional and must join this lifecycle; no duplicate replay engine. Jackson 3 adapter is #929, not an implicit dependency. |
| Schema package / consumer | Schema owns roots, declared aliases and external-contract evolution. Consumer selects resolved Schema components, view, parser and policy; Schelm owns profiles, scaffold rendering, providers and deployment composition. |

The first tracer may keep producer and consumer code private in a fixture. Do not create several libraries for “core
schema,” renderer SPI, metadata registry, policy profiles or composer reuse before one binary consumer demonstrates need.

## Property-normalization matrix

These are semantic classifications, not a regex over method names. A physical declaration or proven generated role joins
an operation to a stable property. If the role is unavailable, emit an unavailable fact; qualified projection rejects it.

| Public operation / declaration | Generic normalized shape | First qualified projection / rejection |
| --- | --- | --- |
| Typed scalar field configurator plus generated setX alias | One property with declared type and operation references; flag shorthand is another operation on the same boolean | Scalar/enum shape once mapper binding is proved; no external setX alias or synthetic flag property |
| Inherited concrete input | Same declaring ID, effective public type in selected root view | Same qualified shape; unresolved generic substitution rejects, no bound-only guess or dependence on proposed #180 |
| Explicit method-first field override; unrelated one-value Builder.Method | Preserve callable operation separately; ambiguous storage meaning unsupported | Exclude arbitrary methods; overridden input requires explicit supported role/binding proof, never run body |
| Virtual field declared with Field | Typed input if declaration/role survives; no invented Model storage | Unsupported if existing managed importer has no field binding; do not silently extend importer |
| Simple List/Set/Collection configurators, singular adders, varargs | One collection property with element shape and operation provenance | JSON array for admitted element types; no automatic uniqueItems from Java Set or sorting assertion |
| Ordinary Map<K,V> setter/bulk/entry adders | One map property with separate key and value shapes | First wire subset String keys; other key codecs unsupported. Map value parameters are eligible, not control bags |
| Fixed concrete nested creator, with targeted closure-only form | One owned object relationship to exact public child Builder | Recurse into child graph; DelegatesTo must resolve to fixed public Builder and generated attachment role |
| Keyed child / keyed collection / keyed map creator | Property + container + instance-key source; Map values bag is not a property | Map object entry keys feed importer key hint; explicit child key precedence preserved. Collection has no map key hint; no invented tag from key |
| Values-bag creator overload with fixed target | Excluded call form; referenced target property graph remains discoverable | No wire values-bag wrapper or synthetic values property |
| Leading Class/Factory selector, or Map then Class/Factory selector (first/second input positions) | Excluded dynamic control operation | No Class strings, factory tokens, subtype inference or dynamic selection route |
| CollectionFactory / ClusterFactory targeted closure | Group/forwarding role only if destination/property membership is explicit | Flatten to original canonical properties only where join is unambiguous; otherwise unsupported, no synthetic Cluster object |
| Concrete custom factory with concrete DelegatesTo | Root capability metadata only; not a new property by return type | No creator-argument mapping or arbitrary factory invocation |
| Untargeted, target-parameter-driven, genericTypes-only or custom non-Builder closure | Unsupported nested input; retain reason | No inferred target from body/return/bound or arbitrary delegate; dynamic target forms excluded |
| Abstract scalar / collection element / map value | Declared target plus explicit alias availability | Fails until closed-alias gate; no defaultImpl shortcut to closedness |
| Literal Field.alternatives | Candidate explicit relationship-scoped alias map when safely retained | No execution of closure; no inference from automatic alternative names; later carrier gate applies |
| BUILDER field | Construction input with participation=Builder-only | Excluded from Jackson input; do not emit writeOnly merely because absent from Model |
| PROTECTED / IGNORED, Owner/Role, synthetic/internal state | Declared/excluded facts as useful, never editable fields by default | Excluded using actual importer policy; immutability/final alone is not readOnly |
| LINK / OPTIONAL_LINK | Record relationship kind and runtime ownership/identity limits | No inline owned-object claim for LINK, no default reference codec. Unsupported without an explicit admitted input representation; OPTIONAL_LINK fallback remains runtime/consumer-owned |
| copyFrom, converters, Builder.Query, getters, lifecycle/session, applyLater, Templates, scripts/import helpers | Excluded operations (diagnostic inventory may explain why) | Never external properties; no merge/patch or Template recipe transport |
| Arbitrary non-DSL Simple Value, raw type, unresolved container | Exact declared type plus unsupported mapping | No recursive POJO schema inference or Object wildcard presented as complete |

### Delegation limits

Read retained DelegatesTo.value/target/strategy/type/genericTypes as metadata, not executable behavior. A fixed concrete
public Builder and DELEGATE_ONLY on a known generated owned creator is sufficient evidence for nested shape; the closure
body is not needed. Delegation alone cannot prove ownership, cardinality, key origin, closed subtype set, runtime
attachment, or a method's role. Explicit collection/Cluster factory membership can redirect shape to its backing property;
a delegate to an arbitrary helper must not. Parameter-driven delegates requiring runtime Class/Factory are excluded even
when a type bound is known. No expression evaluation to resolve DelegatesTo.type strings.

## Test the generated-contract hypothesis before expansion

**Hypothesis:** one descriptor sourced primarily from Foo_DSL is enough for concrete structural documentation and a narrow
rename/ignore projection. It is not enough to recover every declaration fact or a closed polymorphic wire language.
Do not call this hypothesis proved by documentation review. SA-0 must execute the following falsification tests:

1. Compile an annotation library, a base Schema JAR and a concrete leaf Schema JAR separately. Include scalar/enum,
   nested concrete child, collection, ordinary Map<String,String>, keyed Map<String,Child>, renamed/ignored fields,
   DefaultValues, inherited input, helper/query/copyFrom and dynamic-selector overload controls.
2. Give the extractor only those JARs and referenced public metadata. Remove Schema sources, source mirrors and compiler
   from the extraction/consumer path. Read class bytes without initialization; fail the test if a static initializer,
   property initializer, constructor, factory, converter, defaults closure or lifecycle hook runs. Counters alone are
   insufficient if class initialization occurs; use an external marker/throwing static control in a forked process.
3. Produce one generic descriptor. Independently project structural JSON Schema and a small plain reference/property
   table from that descriptor, without rediscovering Schema classes. Assert canonical property IDs, inherited declaring
   IDs, nested targets, docs, DefaultValues candidates and exact exclusion reasons. The table is a test consumer, not
   a second general documentation renderer to ship.
4. Compare qualified rename/ignore acceptance with actual managed JSON/YAML root import on the concrete subset. Cover
   omitted/null/empty/false/zero, ordinary maps versus control Map parameters, key hint and explicit child key precedence,
   once-only lifecycle/materialization and enriched asymmetric output. Validation results are classified separately;
   passing structural validation need not mean completed-model validation succeeds.
5. Add negative controls: broad NamedParam catalog includes helper/copyFrom that descriptor excludes; overloaded Object
   fallback is insufficient; untargeted/custom helper closure rejects; abstract target fails qualified projection even
   if a dynamic Factory can construct it; unsupported mapper settings do not claim qualification.

Success means consumers can interpret the same descriptor without source reconstruction and supplementary facts stay
small. Failure records the missing fact and smallest capture point before any new API. If whole source replay or arbitrary
code execution seems necessary, stop expansion and revise scope; do not generalize the framework to hide the failure.

### Narrow supplementary sources and capture gates

| Fact absent or not guaranteed on Foo_DSL | First permissible source / fallback |
| --- | --- |
| Operation-to-property role, setter/adders/virtual identity, exclusion reason, key source | Try existing generated tags and retained Field/Key declarations joined to public operations. SA-0 measures class-file retention; if insufficient, add minimal role/declaration references at GeneratedDslSupport/DSLASTTransformation generation. Do not parse generated English, source mirror names or hidden storage. |
| Jackson property rename/ignore and original field binding | Retained Model/Schema field/property annotations correlated to the discovered public input. Builder method annotations are not assumed to contain them. Conflicts/unsupported attributes reject view qualification. No mapper runtime required to capture generic facts. |
| DefaultValues and per-relationship applicability | Retained Schema/type/owning-field and annotation-type declarations plus supplied nondefault members/valueTarget; mark closure and context opaque. First test what reaches public contracts; do not duplicate surviving facts. |
| Effective Required/Validate, class defaults, Bean Validation rules | Existing retained effective declarations and annotation definitions; Bean constraints intentionally absent from Builder copies. Apply each feature's actual hierarchy/Ignore/Optional rules. Preserve phase and opaque external-validator coverage; no generic recursive meta-annotation interpreter. |
| Declared literal initializer before Builder relocation | Only if needed, small compiler snapshot before initializer rewrite; literal whitelist, explicit-null versus absent, no ConstantExpression-as-proof for arbitrary expressions. Old binary without capture: unavailable. Generic structural tracer may succeed without this value. |
| Explicit literal alternatives versus automatically named alternatives | Capture original accepted literal map at its compiler validation seam when it is not retained as data. Class-valued closure annotation alone is insufficient; never execute/decompile it. New general carrier is a separate alias decision. |
| SOURCE/CLASS facts, propagation origins, removed Builder-only declarations | Retain only an accepted required fact at the existing compiler seam; otherwise unavailable. Respect ADR 0029's one-batch effective-declaration scheduling. No new transform scheduler, package-default propagation or provenance framework. |
| Structured example values | #930 boundary only; do not introduce a temporary carrier/parser or turn JavaDoc snippets into defaults/examples. |

For every supplementary addition, record why public bytecode plus existing retained declarations failed, owning module,
retention/format, old-binary behavior and Groovy 3/4/5 binary/mirror parity. Generic discovery remains contract-first.

### Authoring intent stays orthogonal

| Requested authoring intent | Generic fact and consumer boundary |
| --- | --- |
| Must be supplied | Completed Required is a validation fact, not an input/layer obligation. No new mandatory-input annotation is selected; consumer defines who must supply which layer. |
| Normally supply, useful default | Declared fallback candidates and applicability can be described; recommendation remains consumer editorial policy. |
| Usually untouched / advanced | Can coexist with mandatory or defaulted values. Consumer controls audience/profile visibility; no four-value enum or general docs DSL. |
| Fixed by application/platform | Override authority depends on composition layer; neither PROTECTED nor immutable completed fields proves this policy. Consumer enforces it. |

## Public choices and gates

Recommendations below are deliberately not accepted API names. Distinguish tool release, descriptor-format evolution,
Schema content version and per-view identity; they solve different compatibility problems.

| Choice | Proposed tracer default | Decision deadline / acceptance |
| --- | --- | --- |
| Artifact/plugin names | Module/plugin artifacts in boundary table; plugin ID `com.blackbuild.klum-schema-artifacts`, extension `klumSchemaArtifacts` | Freeze after SA-0 before publishing markers; naming review with Klum namespace convention |
| Descriptor model and codec | Internal immutable property plan, ordinary deterministic JSON; no public SPI | SA-0 proves two consumers; freeze minimal reader/format at SA-2, no generic framework |
| Location and transport | Prefer opt-in companion `schema-artifacts` classifier/variant JAR with `META-INF/klum/schema-artifacts/manifest.json`, per-root descriptor and per-view JSON Schema resources | SA-2 compares companion versus embedded; exact path/attribute names, Maven fallback and local directories require review. Mirrors never packaged |
| Version alignment | Derive companion from the already selected Schema component/version, not a second independent version choice | SA-2 qualifies Gradle module/project dependencies, Maven classifier fallback, dependency substitution and missing companion diagnostics |
| Content alignment | Manifest binds Schema component/version and relevant public-contract/content digest, referenced component identities, selected roots and view settings | SA-0 prototype; SA-2 choose digest scope avoiding archive/resource self-reference. Reject stale/mismatched data, no absolute paths or timestamps |
| Format evolution | Independent major/minor; unknown major rejects, additive optional minor fields may be ignored only under documented rules; required capabilities cannot be ignored | Freeze at SA-2 with old/new reader tests and corrupt/missing payload controls |
| External schema identity | Explicit Schema-owned base identity plus root/view/version; local `$defs` references; never machine paths or unstable dependency scanning order | Choose exact `$id`/ID encoding at SA-2; test relocation and rename collisions; no automatic network lookup |
| Output location | `build/generated/klum-schema-artifacts/main` for discussion; generated resources/companion packaging only | SA-2 test clean/incremental/cache/stale deletion/task graph. Must not enter Java/Groovy compile inputs or IDE source-mirror root |
| First view and dialect | `generic` plus narrow `jackson-property-input`, Draft 2020-12 JSON Schema; no custom vocabulary | SA-0 pins mapper baseline and validator version for tests; SA-1 freezes supported type/null shapes. Runtime-only validation remains explicit |
| Alias carrier and tag | **Open**: existing literal alternatives where sufficient; compare minimal retained declaration versus Schema-owned sidecar only for measured uncovered scalar/relationship cases | SA-3 before abstract input/projection. Decide tag spelling/collision, scoping, inheritance, key interaction and compatibility with existing importer. No inferred aliases |
| Alias execution | **Open**: smallest optional lowering/adapter into existing managed construction | SA-3 must prove preservation of importer behavior; choose integration seam before coding execution. No general Jackson type resolver inferred by plugin |
| Strict portable-validation profile | Deferred; first output has truthful structural coverage and rule omissions | Revisit only for demonstrated consumer need and separately qualified constraints; no global restriction on validation language |

No choice blocks this documentation delivery or the concrete private tracer. Stable artifact publication waits for SA-2;
abstract feature delivery waits for SA-3. A failed gate may defer its capability without pretending #529 is fully delivered.

## Proposed discussion example

**Not implemented or accepted syntax.** Existing Schema syntax below is ordinary Groovy; only the plugin block and
extension/task naming are proposed. Import/package setup omitted. This concrete example intentionally avoids inventing
an alias or structured-example annotation.

```groovy
// Schema owned by the application package
@DSL
class Deployment {
    /** Name shown to the operator. */
    @JsonProperty('deployment_name')
    String name
    @JsonIgnore String internalNote
    Map<String, String> labels
    Map<String, Service> services
}
@DSL
class Service {
    @Key String id
    int port
}

// build.gradle (proposed optional plugin)
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klumVersion>'
    id 'com.blackbuild.klum-schema-artifacts' version '<klumVersion>'
}
klumSchemaArtifacts {
    roots = ['example.Deployment']
    views = ['generic', 'jackson-property-input']
}
```

Expected discussion outcome: one canonical name property, renamed to deployment_name in the qualified view;
internalNote excluded there; labels remains an ordinary object map; services is a keyed concrete relationship map.
The `api` entry key in `services: {api: {port: 8080}}` is instance data, not a subtype alias. No published output path or
method on Foo_DSL is implied. Evolve this into the documentary test and user page only after API choices are accepted.

## Tracer-first slices and reasoned commits

Each slice is a small vertical proof with a gate. No additional issues are created by this plan. All implementation
requires separate authorization; this document does not authorize production work.

### SA-0 — Binary contract to descriptor to concrete consumers

Build the smallest private producer/consumer fixture described above: scalar, enum, concrete child, ordinary map and keyed
child map, inherited input, AnnoDoc, DefaultValues, rename/ignore and excluded helper/control signatures. Read binary
contracts and publish to a temporary fixture artifact; one descriptor feeds both reference table and 2020-12 projection.
Include honest unsupported abstract NamingStrategy/Environment controls from Schelm's reported shapes. Validate concrete
YAML through the existing importer, without changing its implementation.

**Acceptance:** source-free two-consumer proof; no extraction-time Schema code execution; exact roles/keys/docs/default
availability; no Jackson/core dependency leak; generated contract and mirror identity unchanged. Record every missing fact
and test its narrow supplementary seam. One actual validator and mapper baseline must be named in evidence, not assumed.

**Commits:** (1) one green vertical private tracer and controls; (2) measured gap report and proposed mechanics/choice
updates. A preparatory fixture extraction is separate only if useful and independently green. Do not commit only failing
tests or a production metamodel before this tracer can answer the design question.

### SA-1 — Minimal normalized producer and fidelity contract

Promote only proven discovery/normalization into the optional module. Complete the matrix for currently supported
concrete scalar/container/nested/inherited/virtual/Builder-only/relationship shapes. Add only measured compiler facts.
Classify effective Required/Validate, DefaultValues and literal/computed/unavailable defaults without interpretation as raw
presence or runtime values. Detect method collisions, missing roles and incomplete metadata with declaration-linked reasons.

**Acceptance:** ordinary Map values and excluded control Map signatures coexist; every known public helper has an exclusion;
no broad NamedParam or Model getter fallback; opaque rules/defaults stay opaque; no user code execution on successful or
failed extraction. Old JAR without metadata reports unavailable, not empty success. Effective Required can be satisfied by
a literal/lifecycle in import while still recorded as completed validation. Test direct, supported propagated/collector
and class-default validation cases without expanding package/meta semantics.

**Commits:** (1) producer/grammar plus binary positive and rejection tests; (2) narrowly necessary retained fact capture and
old-binary/propagation parity tests; (3) fidelity/default/validation matrix and diagnostics tests if distinct. Omit commit
(2) when existing metadata suffices. Keep production changes and their green tests together.

### SA-2 — Reproducible Gradle generation and matched artifact contract

Add the optional plugin, explicit roots/view inputs, compiled-output dependency and isolated generator classpath. Freeze
only the tested choices in the public-choice table. Generate descriptor and projection from the same matched property
plan. Qualify companion versus embedded choice with a separately resolved binary consumer; do not copy GDSL format or
package source mirrors. Reuse provider/lazy task conventions and avoid cycles through classes/processResources/jar.

**Acceptance:** clean, unchanged repeated output, deletion/rename stale cleanup, inheritance/dependency changes, relocation,
cache reuse/configuration cache and no absolute paths/time variance. Verify roots do not trigger arbitrary classpath
scanning. Test explicit missing/corrupt/unknown-major/mismatched metadata; deterministic ordering; component/version/digest
alignment; Maven/Gradle and project/dependency substitution behavior for promised transport. No second user-maintained
Schema version, compiler or source mirrors on consumer runtime path. Assert published dependency/JPMS metadata has no
reverse dependency from core/runtime and no implicit Jackson adapter. Generation remains opt-in and core-only consumers
continue compiling/running. Public format/parser additions require compatibility review before first stable publication.

**Commits:** (1) cacheable generation/plugin plus local TestKit vertical test; (2) matched publication/binary consumer and
negative alignment tests; (3) public format/version/location decision record based on results. Combine (3) into the relevant
commit if its only content is documenting the just-proven contract.

### SA-3 — Closed aliases and keyed normalization decision/tracer

This is an explicit **decision gate**, not permission to invent a carrier during implementation. Compare minimal ways to
retain explicit alias declarations for scalar NamingStrategy and Map<String, Environment> and reuse literal alternatives
where available. Choose relationship scope, inherited overrides, missing/duplicate names, tag spelling and collision
policy. Distinguish instance keys from tags. Evaluate the smallest optional input-lowering seam with the existing importer;
Jackson polymorphism is not included in the rename/ignore view by implication.

**Acceptance before implementation expansion:** maintainer accepts the carrier and execution boundary; one scalar and one
map abstract case select concrete same-session Builders through required explicit aliases. Distinct required const-tagged
oneOf branches reject unknown/missing/ambiguous aliases, even with overlapping payload shapes; incompatible/abstract target
and payload/tag collision reject before allocation. Same subtype under multiple instance keys works. A key equal to an
alias remains an instance key. Explicit child key precedence stays as existing importer; conflicts are documented and
tested rather than changed. No open subtype scan, automatic alternative inference, runtime Class/Factory or arbitrary
class-name route; no constructors/closures run during discovery. Missing alias metadata fails qualified projection.

**Commits after acceptance:** (1) minimal carrier/retention and descriptor/JSON Schema tests; (2) optional alias input
adapter plus end-to-end scalar/map/key rejection and lifecycle parity. No separate execution engine. If this gate is
deferred, publish only concrete capability with explicit abstract rejection and keep #529's remaining criterion open.

### SA-4 — Narrow projection/import and consumer qualification

Qualify the frozen property rename/ignore view against managed root import. Keep Jackson's wider API behavior and tests
unchanged. Capture unsupported mapper attributes/settings as outside qualification. Pin YAML parser/data-model assumptions
and resource settings in the test fixture: duplicate keys, tags, aliases/cycles, non-string keys, nulls, numeric limits and
one document. For unsupported syntax, generator/consumer qualification must report the boundary; do not change existing
importer acceptance merely to make schema validation agree.

**Acceptance:** structural schema assertions and import have documented agreement on admitted shapes and documented runtime
validation differences. Test false structural rejections and misleading acceptance, source/renamed pointer diagnostics,
unknown-property policy, defaults, once-only lifecycle, immutable materialization, LINK/OPTIONAL_LINK and Template behavior.
No defaults injected by JSON Schema validation, merge/patch, source layering or round-trip guarantee. Schelm read-only
consumer acceptance is separately commissioned with exact revision/root/artifact/view and limits; it demonstrates descriptor
lookup for its consumer, not deployment/provider/scaffold implementation inside KlumAST.

**Commits:** (1) projector/type/null and importer differential controls; (2) YAML/consumer qualification and bounded fixes
in owning optional modules. Run existing Jackson customization, relationship and YAML documentary regressions if any shared
mapping code changes. Do not refactor the importer solely to share hypothetical future composition metadata.

### SA-5 — Documentary and release qualification

Add `SchemaArtifactsDocumentaryTest` (or a cohesive plugin documentary class), `@Issue('529')`, `@Tag('documentary')` and
absolute `@See` to the final docs/user page. Evolve the proposed example only after names are frozen. Link test feature and
user example reciprocally. Add relevant Schema/Gradle onboarding and Jackson boundary guidance; update docs/user/_Sidebar.md
for a new page, migration navigation only for actual migration guidance, and next-release CHANGES.md. Record supported
concrete/abstract capabilities truthfully, retention/recompilation needs, unavailable old metadata, and format compatibility.
Do not add these user promises or release notes in this planning PR.

**Acceptance:** future issue links to accepted ADR, documentary feature, user page and exact evidence; focused Groovy 3 then
affected suites and Groovy 4/5 lanes pass with lane-local compilation. Binary Java and static/dynamic Groovy consumers,
plugin TestKit, relevant JPMS/publication/signature checks and current-head CI/SonarCloud pass or have an explicit accepted
gap. No issue closure or release placement before Hive reconciliation. #929/#930/#481/#528 remain separate.

**Commits:** (1) documentary test and aligned user/release docs; (2) measured acceptance/ADR update if independently useful.

## Compatibility and validation strategy

Production compatibility gates are prospective, not tests run for this documentation change:

- Preserve public JVM method descriptors, Model/Builder separation, lifecycle/ownership and importer behavior. Supplementary
  annotations/metadata need binary/mirror tests and explicit retention/recompilation guidance; no mandatory rewrite of
  already compiled Schemas. Unavailable metadata is a supported diagnostic state.
- Compile real Schema fixtures anew under Groovy 3 baseline and Groovy 4/5 lanes; never share compiled test fixture output.
  Use existing AbstractDSLSpec/scenario patterns. Every new executable class ends in Test and carries #529; documentary
  examples also carry Tag/See. Do not add pending tests for unresolved alias design.
- Add Java, CompileStatic and dynamic Groovy binary consumers where public types are involved; assert public signatures,
  inherited types and DelegatesTo agree with class-file extraction and AnnoDocimal mirrors. No claim of proposed #180 or
  unqualified nested-Schema support; reject or report those unsupported shapes rather than expanding scope.
- Use schema meta-validation plus a pinned Draft 2020-12 validator and managed-import differential checks. Choose exact
  validator/parser versions in tracer evidence; no universal YAML/editor or Jackson settings parity claim. Runtime rules
  remain authoritative after binding and lifecycle.
- Each slice evidence records contract, exact base/final revisions, artifact digests, commands, results, missing facts and
  failures. A source-level test alone cannot satisfy binary publication or no-initialization gates.

## Requirement-to-slice and external-boundary map

| Requirement / dependency | Proof and completion boundary |
| --- | --- |
| Optional module/plugin, core has no runtime wire dependency | SA-0 isolated generator; SA-2 dependency/publication and core-only consumer tests |
| Foo_DSL enough for concrete consumers; narrow supplementary sources only | SA-0 falsification/two-consumer proof; SA-1 per-fact capture and old-binary behavior |
| Grammar, helper/lifecycle/untargeted filtering; ordinary Map versus dynamic Map/Class/Factory exclusion | SA-0 positive/negative controls; SA-1 complete normalization matrix |
| AnnoDocimal, DefaultValues, effective required/default/validation fidelity | SA-0 surviving metadata inventory; SA-1 retained declaration/opaque fact tests |
| Targeted closure nesting and truthful limits | SA-0 fixed public delegates; SA-1 helper/dynamic delegate rejection |
| Narrow rename/ignore view, generic descriptor independent of Jackson 2/3 | SA-0 concrete differential view; SA-2 dependencies; SA-4 frozen mapping qualification |
| Abstract NamingStrategy and Map<String, Environment>, closed aliases, type tags and key separation | Descriptor reports unavailable in SA-0/1; SA-3 explicit carrier/execution decision and scalar/map proof |
| Artifact version/location/identity/synchronization | SA-0 private choices; SA-2 freeze and binary/cached/stale/mismatch acceptance |
| YAML preservation, no merge/patch, links/Templates/session semantics | SA-0 smoke; SA-3 alias lifecycle where accepted; SA-4 existing importer regressions |
| Structured examples #930 | No design in this plan; independent accepted metadata contract before advertising examples |
| #481 deployment, #528 bridge, #929 Jackson 3 | Independently releasable; no first-descriptor dependency or scope transfer |
| Compose/copy #304/#310/#349 | Potential internal plan reuse only; no dependency or public abstraction |
| User docs, migration/release, compatibility/CI/Sonar | SA-5; not capability claims in this documentation-only PR |

## Release assessment, risks and overengineering check

Primary need is durable Schema-owned input facts; manual docs remain viable but drift-prone. Provisional next 4.x
integration-line placement is reasonable for the concrete descriptor/projection **after** SA-0, not automatic “4.2” or
“5.0”. No milestone is assigned. #481/#528's current candidate labels do not set #529's release. Unknown effort is mainly
binary role retention, packaging/alignment, fidelity and alias/import integration, rather than JSON rendering.

The concrete tracer has no visible architectural blocker. Public operation role identity, DefaultValues/annotation
projection, safe literal retention and supported downstream generic typing require measured evidence. JSON Schema can be
structurally useful while runtime rules remain opaque; the consumer must see that distinction. Abstract polymorphic import
is blocked on a deliberately deferred carrier/execution decision, not solved by Factory signatures. A missed gate reduces
the released capability or defers delivery; it does not weaken alias rejection or runtime semantics.

Review each slice for unnecessary abstraction: one internal property plan, descriptor codec and small projector suffice.
No general schema framework, general docs DSL, annotation-rule engine, universal YAML executor, mapper runtime in core,
policy/profile model or public composer seam. Only concrete failed acceptance can justify another module or SPI.

## Planning validation and handoff

This change adds only this plan and a Proposed ADR. Run diff whitespace checks and local Markdown/link/anchor/structure
checks; inspect decision versus open-question wording, every matrix requirement and commit scope. Groovy lanes are omitted
under the documentation-only rule because no compilation, executable fixture, generated input or runtime behavior changes.
Source/test inspection and standards references do not establish SA-0 execution or feature acceptance.

Observed local planning validation (2026-10-20): both Markdown files passed balanced-fence, heading-separation and trailing
whitespace checks; all 41 local file/heading links resolved. Standards keyword meanings were rechecked against the official
Draft 2020-12 Core/Validation texts and YAML 1.2.2. Local standards/spec review checked this proposal against AGENTS.md,
parent ADRs and all eleven supplied maintainer constraints; no production or executable input changes were introduced.
Final staged diff and commit-history checks are recorded in the delivery report.

Tracker impact: related issue #529; curation impact none. This plan changes no milestone, release gate, dependency, owner,
acceptance status or published release fact. Issue #529 remains open; no closing relationship or new issues. Explicit task
authorization permits a documentation draft PR after validation even though the architecture intentionally remains Proposed.
Request Hive reconciliation after delivery; retain the worktree and PR-open state until delivery is reconciled.
