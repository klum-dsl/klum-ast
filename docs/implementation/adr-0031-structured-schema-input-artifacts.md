# ADR 0031 plan — one shared structured-input feasibility probe

Date: 2026-10-21. Status: **Planning only; probe not started; no production implementation authorized**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Language/interpreter: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Generator: [#529](https://github.com/klum-dsl/klum-ast/issues/529).

## Ownership and stopping boundary

#932 owns the shared probe's admission/naming/argument/dispatch decision record; #529 owns its JSON Schema and Gradle
feasibility evidence. Produce **one report from one bounded probe**, using the same fixtures and proposed admitted subset.
Execution ownership is unassigned until the maintainer authorizes the probe; no new worker or implementation task is
launched by this plan. The prior SA-0–SA-5 and Phase A/B/C implementation sequences are superseded.

The probe stops with a measured reuse comparison, minimal admission-contract hypothesis, generated-schema evidence,
missing metadata and concrete maintainer choices. It does not deliver a production interpreter/plugin, select public
APIs, add annotations or alter generated contracts/importer behavior. Implementation planning follows the evidence.

## Prior art: observed behavior and reuse candidates

These are source/test observations, not executions performed in this documentation revision. Internal visibility is not
proof of an available public seam: record accessibility and the smallest needed bridge, without promoting internals now.

| Path / source | Existing mechanism | Probe question |
| --- | --- | --- |
| [InternalKlumBuilder](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java), [named-map documentary test](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/NamedMapBuilderOperationsDocumentaryTest.groovy) | applyNamedParameters iterates entries and invokes one value through InvokerHelper; normal Groovy overload/method-first resolution, inherited operations, converters and copyFrom participate | Can restricted admission/name translation reuse dispatch without changing existing named-map semantics? How are a collection/map single value and multi-argument call distinguished? |
| [KlumJacksonImporter](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonImporter.java), [ADR 0009](../adr/0009-jackson-interoperability.md) | Explicit readRoot/readTemplate/readBuilder/applyToBuilder, caller-owned input/reader, session checks, diagnostics and mode-specific lifecycle | Which lifecycle/input/diagnostic seams are reusable, and where does property-oriented mapping differ from Schema-authored operation dispatch? |
| [KlumDeserializer](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/internal/KlumDeserializer.java) | Resolves mapper properties to configurable Schema fields, discovers owned Builders before binding, handles same-session composition and existing explicit LINK codecs | Measure preallocation/binding order and field-binding versus method-side-effect semantics; do not treat this as the sole operation grammar |
| [FactoryHelper](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java), [PhaseDriver](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/PhaseDriver.java) | Existing root/nested/AsBuilder/Template construction, PostCreate/PostApply, active Construction sessions and graph materialization | Can admitted root/factory/nested operations reuse these paths without prebuilt Models, synthetic closures or another construction engine? |
| [GeneratedDslSupport](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java), [metadata tests](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/NamedMapMetadataTest.groovy) | Public contracts preserve types, inheritance, parameters/delegation and documentation; NamedParam catalog is intentionally broader than admission | Which role/name/argument facts survive binary compilation, especially custom Builder.Method and factory operations? |
| [Schema Gradle plugin](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java) | Compiled-input AnnoDocimal generation lifecycle; mirrors stay IDE-only | How can an optional generator expose reproducible task outputs and invalidation without consuming mirrors or owning distribution? |

The minimum hypothesis is a private admission/name/argument plan feeding existing dispatch/session mechanisms and a JSON
Schema projection. This is not a public descriptor. A Schema-authored operation remains an operation even when it changes
several properties; its schema describes input arguments, not inferred Model effects. No need to recreate named dispatch,
Jackson lifecycle, overload resolution or construction may be asserted without an executable counterexample and reuse audit.

## Probe sequence

### 1. One compiled fixture and baseline observations

Build separately resolved API → Schema → consumer binary artifacts. Use actual [Layer 3](../user/Layer3.md): abstract
Domain API DSL types, concrete Schema types, at least one Cluster projection and an API-only completed-model client.
Include an inherited API scalar property, a concrete Schema dependency/child and explicit concrete roots; abstract bases
are not themselves abstract input targets. Add an abstract relationship as a deliberate unsupported negative. Modify an
API property/dependency and prove metadata/output invalidation, without source/mirrors on extraction inputs where feasible.
Do not assume proposed generic-specialization support or classpath subtype discovery.

Exercise scalar/enum values, ordinary Map<K,V> (String keys first), collections, keyed/unkeyed nested relationships,
CollectionFactory scope, inherited custom Builder.Method, an operation changing multiple properties, and a concrete
Schema-authored factory requiring caller-supplied context. Include helper/query/copyFrom, dynamic Map/Class/Factory and
untargeted-closure negatives. Do not serialize generated control signatures as operations; measure whether existing
fixed-target creator/value-map paths can support internal dispatch without exposing those control forms externally.

### 2. Compare three paths on the same cases

Record success, failure, missing seam and semantics for (a) existing named-map calls, (b) managed Jackson, and (c) minimal
restricted operation interpretation. Use ordinary supported Groovy construction as a baseline, not a generated closure
program. Probe code remains private and disposable; no new overload resolver, synthetic closures or lifecycle engine.

| Existing Jackson mode | Required comparison |
| --- | --- |
| readRoot | One independent completed root; reject inappropriate active-session root use; observe allocation and lifecycle |
| readTemplate | Value-only marked Template, no lifecycle now; retain its distinct mode without promising interpreter Template syntax |
| readBuilder | Exact generated child Builder in active session; attach it as owned composition without prior Model creation |
| applyToBuilder | Identical active unsealed Builder; characterize repeated application without claiming layering/merge policy |

Compare ownership, delegate targets, keys, state and overload resolution as well as final values. Use counters/events to
observe once-only PostCreate/PostApply, later AutoLink/PostTree, materialization and validation. Exercise ordinary LINK/
AutoLink lifecycle resolution and a supplied-context factory; no generic ref registry or provider/context acquisition.
Jackson's existing custom codecs/polymorphism and sealed LINK wrapper behavior remain supported independently; do not
silently narrow or extend them. Compare only overlapping semantics and explain differences.

### 3. Admission, naming, arguments, ordering and diagnostics

Use this classification table as a probe aid, not automatic implementation admission:

| Candidate | Question / guardrail |
| --- | --- |
| Generated scalar/enum and collection/map property operations | Canonical structured shape versus synonymous setter/adder calls; no inferred Model-effect schema |
| Schema-authored Builder.Method / inherited method | First-class scalar, enum, multi-arg and multi-property inputs; distinguish explicit operations from internal controls |
| Concrete authored Factory / nested Builder or CollectionFactory | Fixed target, caller context, attachment and session reuse; no prebuilt Models |
| Generated leading Map values-bag / Class/Factory selector | Excluded serializable control form; ordinary Map-valued property stays eligible |
| Query/getter, copyFrom, Template/lifecycle/session/helper, untargeted closure | Excluded operation; preserve existing broader named-map/Jackson API behavior |
| Abstract target / ambiguous role / unresolved key encoding | Diagnostic failure; no permissive schema or inferred subtype/role |

Compare identity/camel/kebab and explicit rename/exclusion once for interpreter and generator. Include service-name,
reserved class and ignored internal configuration using existing JsonProperty/JsonIgnore declarations, plus inherited
and transformed-name collisions. Measure whether annotations on fields versus authored methods suffice; do not invent
factory-argument annotations. Report naming precedence choices rather than choosing Jackson semantics by implication.

For one Schema operation, compare the following **alternative argument encodings**, not accepted syntax. Example Schema
uses existing Groovy syntax (setup omitted):

```groovy
@DSL
class Service {
    String host
    int port
    @Builder.Method void endpoint(String host, int port = 8080) {
        this.host = host
        this.port = port
    }
}
```

Compare an endpoint input value `{host: 'api', port: 8080}` with `['api', 8080]` and the currently accepted one-map-argument
call when a real Map-taking overload exists. Inspect binary parameter names, omission/default-generated overloads,
ambiguity, nulls and Map/List-as-one-value cases. Reuse Groovy resolution; do not choose an argument envelope or resolver.

Measure two side-effecting operations in opposite orders, repeat/apply behavior, unordered versus ordered parsed maps,
list order, and Jackson's discovery-before-binding behavior. Record whether each representation can express repetition;
do not assume YAML order or invent duplicate-key semantics. Ordering and multi-arg syntax need explicit policy if observable.

Failure checks cover unknown/ambiguous external name, excluded control, wrong argument shape/count/type, missing context,
wrong target/session, key conflict and an exception inside an admitted operation. Diagnostics must report source, input
path, external operation, selected contract (or candidate contracts if unresolved), expected/actual and preserved cause.
Retain existing Jackson exception identity/path guarantees and avoid changing importer behavior to make tests agree.

### 4. One schema for the measured admitted subset

Generate Draft 2020-12 from compiled public Foo_DSL.Builder/Factory contracts and the *same* private admission/naming plan.
Use `$defs`/`$ref`, preferring fully qualified generated class identities; test deterministic escaping/collisions and
cross-module references, inherited API inputs and invalidation. Existing retained Schema annotations supplement missing
facts first. Record the smallest compiler seam only for a demonstrated irrecoverable gap; do not implement it implicitly.

Validate emitted schema and representative parsed inputs with a pinned validator; compare actual minimal interpretation,
named dispatch and overlapping Jackson behavior. No second consumer, public codec or digest/publication protocol is needed.
Structural success may still fail runtime Required/lifecycle/validation. Omitted/null/empty/false/zero and computed default
controls must expose that limit; do not evaluate closures, emit false raw-input required or generic LINK reference promises.

In a separate extraction process, prove class initialization, constructors, factories, initializers/defaults, converters,
lifecycle and annotation closures never execute during successful or rejected metadata extraction. Runtime interpretation
intentionally executes admitted Schema operations. Repeat generation for deterministic output; assess Gradle task inputs,
outputs, up-to-date/configuration-cache feasibility and one independent consumer copy/package action without building a
publication framework. A production plugin is outside this probe.

## Single report and maintainer gate

Record exact commits, binary inputs, supported/unsupported cases, commands/results and lane-local Groovy 3/4/5 metadata
behavior. Reuse existing Spock/compiler/Gradle fixtures; any later probe tests use Test suffix and the appropriate driving
issue's @Issue, with documentary Tag/See only for settled examples. No pending tests or stable syntax for open decisions.
The report contains the three-path comparison, reuse/accessibility inventory, minimal admission-contract hypothesis,
metadata gaps, generator/cross-module/no-execution evidence and explicit limits. Stop rather than silently expanding scope.

Present evidence-backed choices for multi-argument syntax, naming precedence and Jackson interoperability/coexistence;
also identify any observable ordering policy requiring maintainer selection. Closed alias carrier/tag syntax remains a
separate deferred decision. Unknown/ambiguous aliases eventually reject; reuse literal Field.alternatives where applicable
without executing its closure. No subtype scan, Class/factory inference, class-name selection or payload-shape guessing.

#930, #481/#528, #304/#310/#349 and #929 remain independently releasable. ScHelm's abstract NamingStrategy and
Map<String, Environment> demand is reported consumer evidence, not implemented support. Provider/environment/root/package
selection, context injection, OPTIONAL_LINK fallback and scaffolding stay external. Overengineering gate: no universal
deserializer, synthetic closure, ref registry, public descriptor/codec/registry, publication or annotation/lifecycle framework.

## This documentation/tracker delivery

Only this ADR/plan revision and the explicitly requested issue creation/scope normalization are delivered. The probe has
not run. Validate Markdown/local links/diffs and standards/spec correspondence; omit Groovy lanes under the documentation
rule. Keep reviewed commits, PR #931 draft/open and issue milestones untouched. Update the PR body and consolidated response;
request Hive reconciliation with new #932, revised #529, exact head and validation, not archive or implementation admission.
