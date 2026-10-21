# Shared structured-input feasibility probe — #932 / #529

Date: 2026-10-21. **Research evidence, not a production language/API decision.**
Governing records: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[probe plan](../adr-0031-structured-schema-input-artifacts.md),
[ADR 0009](../../adr/0009-jackson-interoperability.md),
[interpreter #932](https://github.com/klum-dsl/klum-ast/issues/932),
[generator #529](https://github.com/klum-dsl/klum-ast/issues/529).

## Result and recommendation

**Recommend B: a restricted adapter over existing Builder dispatch, with managed Jackson remaining independently
supported.** The smallest evidence is a custom `shout(String)` and `endpoint(String,int)` operation: existing Groovy
Builder dispatch invokes their Schema-authored behavior; Jackson rejects those names because there is no configurable
Schema field. Conversely Jackson already supplies external naming, typed scalar conversion, owned graph replay, four
managed modes and foreign-format diagnostics. Extending Jackson to interpret arbitrary operations (A) would change its
field-binding contract. A new shared dispatch abstraction (C) has no demonstrated need: the test adapter uses
`InvokerHelper.invokeMethod` and existing generated creators, never its own overload resolver or materializer.

This recommends reuse, not a finished adapter. CollectionFactory scope entry, omitted arguments, name collisions,
order/repetition, keyed scalar encoding and abstract aliases remain decision boundaries. Native named maps and Jackson
are deliberately broader/different than the proposed restricted input language.

## Revisions, fixture and evidence keys

Production baseline: `b3fcf827e03787f6341d61c0a38b76653cc01234` (`origin/master`, including merged ADR 0031).
Dedicated branch: `codex/932-structured-input-probe`. The probe revision is the commit containing this report and its
four `StructuredInput*ProbeTest` classes; production files and build configuration match that baseline.

All executable evidence is under
[`klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/):

| Key | Fixture/test | Scope |
| --- | --- | --- |
| D | [StructuredInputDispatchProbeTest](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputDispatchProbeTest.groovy) | Three-path values, admission, callbacks, modes, arguments, order and diagnostics |
| F | [StructuredInputProbeFixture](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputProbeFixture.groovy) | Shared concrete Schema and disposable explicit operation-name/shape adapter |
| E | [StructuredInputEdgeProbeTest](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputEdgeProbeTest.groovy) | Supplied-context factory, binary names/defaults/overloads, naming/collisions, keys and callback order |
| H | [StructuredInputHolderProbeTest](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputHolderProbeTest.groovy) | Ordinary Holder orchestration, active Builder input, completed LINK context, defaults, maps and Duration |
| S | [StructuredInputSchemaProbeTest](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputSchemaProbeTest.groovy) | Binary API → Schema → API-only consumer, no-execution extraction and narrow Draft 2020-12 projection |

D/F provide the runtime comparison. S compiles a Layer 3 variant of that same property/operation subset in separate API
and Schema directories, including an abstract API parent, inherited property, Cluster projection, concrete dependency
and an API-only consumer. Binary-only extraction receives class directories, never sources or AnnoDocimal mirrors.
S also exercises the overlapping operation object through F and the actual binary Schema after extraction; its concrete
dependency child is constructed through the same operation adapter during that intentional runtime phase.
Each table row below identifies its direct test evidence; source-only observations are labelled explicitly.

## Three-path observations

“Operations” means the disposable fixture-specific adapter in F, not a proposed public interpreter. Its list of calls
makes ordering/repetition observable for experiments. Object-shaped input is converted to calls only within tests.

| Observation / evidence | Existing named dispatch | Managed Jackson | Test-only operations |
| --- | --- | --- | --- |
| Scalar and enum (D ordinary graph) | Typed scalar succeeds; enum instance succeeds; enum String needs translation | `SAFE` string binds enum | Explicit fixture enum conversion followed by existing setter |
| Ordinary `Map<String,String>` (D/F, S) | A single Map remains data | Typed map data | One argument, unchanged keys; not a generic values-bag control |
| Concrete child, keyed map, unkeyed list (D ordinary graph) | Child named values-map succeeds; ordinary collection scopes provide baseline | Reconstructs owned Builders | Fixed concrete creators receive admitted nested values maps, outer key and each list entry |
| CollectionFactory (D ordinary scopes; E context factory) | Existing Groovy scope supports projected authored factory; explicit member name distinguishes plural scope | Property collections bind values; no CollectionFactory-operation language | Fixed member calls work; no public accessor returns a CollectionFactory scope without Closure |
| Custom single/inherited operation (D custom operations) | `shout` and `inheritedLocale` invoke authored code | Unknown property; not an operation input | Admitted calls invoke the same authored code |
| Custom multi-property operation (D arguments; S) | `endpoint` sets host and port; see argument results below | No `endpoint` property | Explicit positional pair variant invokes existing Groovy method |
| Converter (D source; H Duration) | Existing generated converter overloads execute | Uses Jackson codecs, not generated native converters | Invokes existing generated operation; does not recreate conversion |
| Authored context factory (E) | `children { supplied(context) }` produces exact public child Builder and attaches it | No external dependency acquisition is supplied by property binding | Reusable existing factory, not a generic context injector; standalone minimal F does not invent scope entry |
| `copyFrom` / helpers (D broad dispatch) | `copyFrom`, `apply(Map)` and a zero-argument lifecycle method via null are callable | Known configurable fields only; other known excluded fields can be ignored | Explicit name list rejects copy/apply/lifecycle/session/template/query controls |
| Abstract owned target (D, S) | Existing construction cannot instantiate an abstract owned child | Managed root/owned target unsupported; abstract Template is a distinct existing capability | Omitted and explicitly diagnosed; no subtype scan, alias protocol or permissive schema |
| External names / ignore (D names; E naming) | Uses native operation spelling, including ignored field operation | Honors configured Jackson names/ignore and naming strategy | Small explicit identity/rename list only; generator uses retained field annotations |
| Diagnostics (D diagnostic iterations) | Unknown call has Builder/Model construction path and cause, no source JSON Pointer | Source identity, escaped external input pointer and original mapping cause | Test wrapper records `probe.json`, operation path, public contract, expected/actual shape and cause chain |

D proves completed child owner identity, keys and LINK identity; later phases/validation/sealing are supplied by existing
construction. D’s three successful graph roots each have one root create/apply/link/tree/validation and one create/apply/
validation per owned child. No completed child is constructed in advance for composition.

### Exactly which admission is reusable

Source: [`InternalKlumBuilder.applyNamedParameters`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java)
iterates `entrySet` and invokes `InvokerHelper.invokeMethod(this, key, value)`. Its helpers are private; the containing
class is internal. NamedParam metadata is intentionally a completion/type-checking catalog, not admission. Its overload
catalog may collapse alternatives to `Object`, while actual public operation signatures remain available.

Measured helper leakage is `copyFrom`, `apply(Map)` and lifecycle `created` via null (D). Source-only additional callable
runtime candidates include `setCurrentTemplates`, `setBreadcrumbPath`, `scheduleApplyLater`, `sealTo` and `setMetaClass`
when matching runtime values are supplied. A scalar tree's inability to encode some of these values is no admission rule.
Public visibility, return type, one-argument eligibility and naming patterns cannot establish safe roles.

Jackson source: [`KlumDeserializer`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/internal/KlumDeserializer.java)
resolves Jackson properties against Schema fields, filters framework/protected/ignored/Builder-only/read-only state and
binds storage through `setSingleField`. It does not infer operation side effects. Existing unknown-property policy and
known ignored-property behavior remain independently supported.

Generated leading generic values-bag Map and first/second-position Class/Factory selectors are excluded external controls.
The probe invokes a **selected fixed concrete** Map-taking creator internally; that is not publishing its dynamic overloads
or claiming Map itself is forbidden. Queries, untargeted closures, template/copy/session/lifecycle helpers are excluded.
An ordinary data-map setter and a dynamic control map have different roles despite the same erased Map type.

## Managed modes and active-Builder capability

| Mode / evidence | Completion and identity | Observed callbacks |
| --- | --- | --- |
| `readRoot` (D graph; E callback order) | Independent root, all later phases and materialization; active root session rejects | Graph replay: all create, then all binding, then all apply; later validation once |
| `readTemplate` (D modes) | Marked value-only root and child Template | No callbacks before normal future application |
| `readBuilder` (D modes; H Holder) | Exact generated active-session Builder, no completed Model, no late phases until enclosing lifecycle; outside-session call rejects | Existing allocation plus replay produce **two** create/apply callbacks for the imported child |
| `applyToBuilder` (D modes; H Holder) | Identical active unsealed Builder; late/inactive target rejects; later phases still enclosing lifecycle's responsibility | Each call replays create/apply. D's two calls plus enclosing root preparation total three root create/apply callbacks |

These are measured existing behaviors, not fixes or a claim that duplicate creation callbacks are an approved design.
Source explains the result: importer `readBuilder` calls `factory.FromMap(emptyMap)` before managed replay; replay runs
PostCreate/PostApply again. Any later change to that behavior belongs in separate, explicitly authorized work. No such
issue or scope mutation is made here. Exactly-once claims in this report apply to final graph phases/materialization and
to the specified successful baseline route, never universally to importer pre-completion callbacks.

H proves the same capability for a restricted operation adapter: allocate an owned child via `Create.AsBuilder` inside
an ordinary Holder root, invoke admitted operations on it, then let the Holder finish. Root completion is not required
at the interpretation seam. No public manual-session API is necessary.

### Ordinary ApplicationHolder + completed Environment

H runs four routes: named values, fixture-only operation calls, `readBuilder`, and `applyToBuilder`. A completed concrete
Environment is supplied externally. Application input is loaded **before** Environment attachment and before completion.
The test asserts no completed Model, no sealed Builder and no late-phase events at that boundary, then calls
`applicationBuilder.environment(environment)` and attaches that Builder through Holder's normal relationship operation.

The measured existing Groovy orchestration is:

```groovy
ApplicationHolder.Create.With {
    def app = importer.readBuilder(Application.Create.AsBuilder(), input)
    app.environment(suppliedEnvironment)
    application(app)
}
```

The single enclosing Holder completion assigns ownership, resolves LinkTo/AutoLink, applies defaults, executes PostTree,
materializes and validates once. The final Application owns no copy of Environment: both environment and derived LINK
are the identical caller-supplied completed object. Owner is the final Holder. Defaults read environment's region and
retry count; validation sees that same context. `Application.environment` is `@Required` in the completed model, yet
absent from serialized input. The explicit integer `0` becomes the declared default `7`; explicit false remains false despite the declared
boolean default true. No schema raw-input `required` or `default` is inferred from this.

Attachment must occur before the enclosing root configuration returns. Earlier PostCreate/PostApply callbacks during
loading still see no Environment; this is not proof of environment-aware early binding callbacks. If those callbacks
need context, use the existing supplied-context factory/attach-before-apply pattern and qualify that separately.

This uses ordinary Schema-root orchestration, not a lifecycle mechanism or context registry. It leaves ordinary Order/
Worker `copyFrom` composition separate from the serialized operation language. Those scripts remain existing consumer
composition; supplying a completed LINK does not copy it or rerun its lifecycle.

## Arguments, names, order and diagnostics decisions

D compares the alternatives on one Schema method:

```groovy
@Builder.Method void endpoint(String host, int port = 8080) {
    this.host = host
    this.port = port
}
```

- Native named `endpoint: 'api'` uses the default-generated implementation overload: 8080.
- Native named `endpoint: ['api',9090]` spreads to the two-argument operation; this follows Groovy selection, despite the
  existing diagnostic's “exactly one argument” wording.
- Native named `endpoint: [host:'api',port:9090]` fails: an object is one Map, not automatic named argument binding.
- `mapping: [host:'data']` and `sequence: ['a','b']` remain **one Map/List argument** when matching such a method exists.
- F's explicitly selected candidate positional pair uses `Object[]`; it does not reinterpret arbitrary Lists globally.
- E demonstrates null overload ambiguity, retaining Groovy's original failure. S records parameter names with and without
  optional compilation attributes and absence of the default one-argument overload from public `Foo_DSL.Builder`.

**Maintainer choice — multi-arg syntax:** positional pairs are the smallest measured form; named objects need stable
parameter-name/omission policy. A Map/List-valued single argument must be distinguishable. Select syntax per admitted
operation/arity, not by globally treating every list as argument spread. No syntax is accepted by this probe.

**Maintainer choice — naming:** propose explicit ignore first, explicit rename next, then chosen identity/camel/kebab
fallback, with collisions rejected across inherited operations and overload groups. This is a hypothesis. E measures
Jackson's explicit `class` rename overriding configured fallback, ignored fields and inherited names in identity/camel/
kebab modes. `URLValue` and inherited `urlValue` both transform to `url-value`; no silent winner is selected. F/S qualify
only identity plus explicit renames/exclusions. Annotation data on fields survives; fluent public operation annotations
alone do not carry field JsonProperty/JsonIgnore. No factory-argument annotation DSL is needed.

**Maintainer choice — Jackson coexistence:** keep its four methods and mapper-qualified property contract. Opt-in
operations are a separate restricted view. Sharing a narrow field rename/ignore interpretation is not compatibility
with aliases, mixins, views, codecs, Jackson polymorphism or arbitrary naming strategy behavior. Matching final values
on overlapping cases does not establish identical callback timing or helper admission.

**Maintainer choice — ordering:** D reverses `host` and side-effecting `append`, producing different results. An explicit
call sequence expresses repeated append; a Map cannot express repeated keys. E reverses child/property input order:
Jackson still performs root-create, child-create, root-apply, child-apply; named child construction performs child-apply
before root-apply. Parsed Map iteration is observable today, not a portable YAML execution guarantee. A future language
must either define sequencing explicitly or restrict order-sensitive combinations. No merge/layer/repeated-key policy is
inferred from separate apply calls. Jackson outer key `outer` and child explicit key `inner` remain distinct data (E);
the new language must decide conflicting key handling rather than claim existing Jackson rejects it.

D's wrapper retains the cause chain for a deliberately throwing operation, wrong pair size and unknown name. Jackson
retains source pointers/cause on rejected `shout`, with existing comprehensive importer diagnostic tests unchanged.
The minimal wrapper is not a complete diagnostic implementation: collision candidate sets, arbitrary nested pointer
stacks and exact overload-signature diagnostics remain future work. Schema role/shape rejection must precede dispatch.

## Duration and ordinary data maps — Schelm qualification

Consumer checkout inspected read-only: Schelm `7c767e8ebbfd96c572acbac4c5460eeb339d7fce`, clean at inspection.
Paths relative to its `schelm-skeleton/` root:
`order-app/order-schema/src/main/groovy/com/acme/schelm/order/OrderApplication.groovy` declares plain
`@Required Duration reservationTimeout`; `operator/order-model/src/test/resources/fixtures/application.yaml` supplies
`reservationTimeout: 30s`. The Groovy model instead supplies `Duration.ofSeconds(30)`. Order and Worker environment
scripts in `operator/{order-model,worker-model}/models/` use `copyFrom shared.environments.test/prod`.

H mirrors the plain Duration field and parses the YAML value. Native Builder converter discovery includes the existing
`parse` prefix (`ConverterBuilder.DEFAULT_PREFIXES`), so ISO `PT30S` is the measured supported text representation;
`30s` fails Duration parsing. Fixture operation calls reuse that converter. The current Jackson test classpath has no
Java Time codec, so even ISO text is unsupported there. That is a mapper-module qualification, not a reason to alter
importer semantics. No compact-duration parser, extra module or new conversion contract is introduced.

H keeps labels, annotations and topicResourceNames/explicit topic-name maps as ordinary data. Identity/kebab property
naming never transforms entries such as `camelCase`, `app.kubernetes.io/name`, `OrderCreated` or `topic.with.dots`.
Literal map entries are values, not Schema operation names; this applies equally to the adapter's ordinary map input.

## #529 — compiled-contract projection

S emits Draft 2020-12 for only its explicitly selected, concretely admitted subset: strings/integers/booleans/enums,
ordinary string maps, concrete nested children, keyed-map/unkeyed-list creation and authored operations. `endpoint`
uses one **candidate** two-item positional tuple; mapping/sequence remain one map/list argument. No default omission
or abstract alias syntax is implied. The projection includes inherited region and inheritedLocale.

- `$defs` identities are fully qualified generated Builder names. Local/API dependency children with the same simple
  name retain distinct references. JSON Pointer escaping and URI-fragment encoding keep references deterministic.
- Separate API, Schema and API-only consumer compilations prove actual Layer 3 inheritance and Cluster. Extraction uses
  binaries only, and rejection of the abstract target emits no permissive definition/property.
- Retained field JsonProperty/JsonIgnore supplement method signatures for `service-name`, `class`, ignored configuration.
  Safe AnnoDocimal documentation and literal DefaultValues metadata are recorded, not executed as defaults.
- Repeated independent extraction processes produce byte-identical output. Changing only API dependency bytes adds inherited
  `zone` and changes output without needing source/mirrors. This is binary-input invalidation evidence.
- A consumer independently copies the output unchanged. No distribution registry, Maven classifier or packaging policy.
- A fresh JVM loads classes with initialization disabled and statically compiled reflection; enum names are read from
  class-file enum field flags. Execution traps cover Schema/abstract/dependency/enum class initialization, initializers,
  factory, converters/default/validation closures and callbacks. Success and abstract rejection do not execute them.
  Early exploratory dynamic Groovy Class access **did** trigger the static initializer; direct statically compiled
  reflection removed that hazard. Discovery must not call `Create`, instantiate classes, evaluate defaults or enumerate
  enum values through initialization.

S measures exact public operation types, generic maps and projected child Builder types,
inheritance, Builder.Method role, Factory methods and parameter names **when emitted**. No parameter attributes gives
`arg0/arg1`; runtime parameter names are not a universal guarantee. A callable default-generated one-argument implementation
overload is absent from the public contract. If named-object arguments/default omission are selected, these are the exact
additional facts needing qualification: stable authored argument names and legal reduced arities. Existing public
signatures/retained annotations suffice for the measured explicit-arity subset; no new compiler metadata is required
now. A later narrow compiler supplement may record omitted-argument arities/names if they are required and cannot be
made available through existing contracts. Default values/closure results are not inferred by executing code.

A future optional Gradle task can declare explicit root identities, naming/admission options and compiled Schema plus
API/dependency classpaths as inputs, and JSON output as files; changed dependency metadata must invalidate it. Determinism
and dependency-change observations support feasibility. A production task's up-to-date/configuration-cache behavior is
**not validated** by these Spock tests, and no plugin/task/publication implementation is delivered.

### Structural validity limits

The pinned Python `jsonschema==4.23.0` Draft202012Validator checks the emitted schema against its metaschema and validates
representative inputs. Positive controls include omitted fields, empty maps/lists, false, 0, concrete relationships,
operation arguments and `port:-1`; negatives include null/wrong scalar/enum/map/list/argument forms, unknown/internal/raw
renamed names, excluded helpers and unsupported abstract input. D/H demonstrate why structural success can still fail
completed validation or produce values from lifecycle/defaults. Conversely Jackson can accept null ignored/known fields
that this deliberately narrow artifact does not admit. That is a declared subset boundary, not round-trip parity.

No automatic raw-input required, default values, arbitrary closure constraints, LINK/reference ids, polymorphism,
context injection, execution ordering, merge/patch/layering or serialization round trips are promised. An integer
schema accepting -1 is not a claim runtime validation accepts it. Required Environment can be supplied after input.
Keyed scalar creators without an agreed key representation remain unsupported; keyed map instance keys are data.

## Minimum admission-contract hypothesis

Hypothesis only, pending maintainer choice:

1. Begin with explicit concrete root/public Builder/Factory contracts and an admitted role/name/argument plan.
2. Admit actual declared configurable property operations and explicit Schema-authored Builder.Method candidates;
   retain inherited members, fixed concrete child/keyed-map/list creators and authored context factories only with
   qualified arguments/attachment. Do not infer attachment from return type alone.
3. Distinguish ordinary data maps from generic values bags/dynamic Class/Factory selectors. Reject unknown roles,
   helpers/queries/session/lifecycle/copy/template controls and untargeted closures.
4. Resolve external operation names once, with explicit rename/ignore plus a chosen fallback; diagnose collisions.
5. Invoke existing Groovy dispatch/generated creators inside an existing Construction lifecycle. Loading an active
   Builder is a supported capability hypothesis; the enclosing existing lifecycle alone completes the graph.
6. Emit schema only for the same concretely measured argument forms. Reject abstract/unresolved shapes explicitly.

## Reuse and overengineering audit

| Existing mechanism | Accessibility / smallest reuse |
| --- | --- |
| InvokerHelper + generated public operation interfaces | Existing method/overload dispatch; no new resolver |
| Named-map application | Private inside internal runtime Builder; normalization can precede existing invocation, without changing named behavior |
| FactoryHelper / PhaseDriver | Existing internal adapter seams own root/nested/Template/session/materialization; no second construction engine |
| CollectionFactory | Existing public contract has Closure entry, no public scope accessor; hidden implementation constructor is not promoted to public API |
| KlumJacksonImporter | Four public methods remain unchanged; independent field-binding/naming/codec/diagnostic route |
| Retained contracts/annotations + class bytes | Private fixture-specific extraction; no Schema user code or new metadata SPI |

There is no generic deserializer, subtype/classpath scanner, synthetic closure program, reference registry, provider
framework, public descriptor/codec/registry, new annotation DSL, lifecycle engine, Gradle plugin, build dependency or
production API in this diff. The fixture's allowlist and handful of shape branches deliberately do not scale into a
universal interpreter. Unsupported shapes are evidence for later decisions, not permission to implement them.

## Commands, verification and delivery

Focused Groovy 3 commands (run progressively; early red runs corrected fixture/scoping assumptions and documented
unsupported existing behavior rather than changing production):

```shell
./gradlew :klum-ast-jackson:test --tests '*KlumJacksonImporterSpec' --console=plain
./gradlew :klum-ast-jackson:test --tests '*StructuredInputDispatchProbeTest' --console=plain
./gradlew :klum-ast-jackson:test --tests '*StructuredInputEdgeProbeTest' --console=plain
./gradlew :klum-ast-jackson:test --tests '*StructuredInputSchemaProbeTest' --console=plain
./gradlew :klum-ast-jackson:test --tests '*StructuredInputHolderProbeTest' --console=plain
./gradlew :klum-ast-jackson:test --tests '*StructuredInput*ProbeTest' --console=plain
```

Final verification below qualifies this evidence only; it does not authorize production implementation. No user feature or production behavior changes, so CHANGES.md/user pages/migration navigation and issue
milestones/scopes remain unchanged. Tracker impact: related evidence for #932 and #529, both stay open; no closing syntax.
Hive reconciliation is requested after draft delivery; this worker does not self-archive or merge.


### Final verification

Runtime: Gradle 8.14.4; launcher JDK 25.0.4.1; repository Groovy lanes 3.0.25, 4.0.32 and 5.0.6; configured Java 17
production target unchanged. Baseline suite was run before compatibility lanes. Final commands:

```shell
./gradlew :klum-ast-jackson:test --console=plain
./gradlew :klum-ast-jackson:check --console=plain
python3 -m venv /private/tmp/structured-input-schema-validator
/private/tmp/structured-input-schema-validator/bin/python -m pip install jsonschema==4.23.0
/private/tmp/structured-input-schema-validator/bin/python klum-ast-jackson/src/test/resources/structured-input-probe/validate_schema.py
git diff --check
```

| Check | Result |
| --- | --- |
| Complete Jackson Groovy 3 suite, final check | **121 tests, 0 failures** |
| Complete Jackson Groovy 4 suite | **119 tests, 0 failures** |
| Complete Jackson Groovy 5 suite | **119 tests, 0 failures** |
| New probe cases | **39 per lane, all pass** |
| Module check | **PASS**, includes license checks and verifyTestLaneIsolation |
| Draft 2020-12 metaschema | **PASS**, each lane's emitted artifact; schemas equal across lanes |
| Representative structural cases | **6 valid + 23 invalid per lane, PASS** |
| Diff and report local links | **PASS** |

The two extra baseline cases are the existing consumer acceptance tests excluded from Groovy 4/5 by existing module
configuration. Root-wide check was not repeated: this diff changes only Jackson test fixtures, one validator resource and
this report; production modules/build logic are unchanged, and the full affected-module check covers all three lanes.
The external Python validator is a pinned temporary environment, not a new Gradle/runtime dependency; its reproducible
source is committed as [a test resource](../../../klum-ast-jackson/src/test/resources/structured-input-probe/validate_schema.py).

Groovy 5 initially exposed a **test-harness** collision between Map's `properties` entry and Groovy's property access;
explicit `get('properties')` preserves the same assertion on every lane. The Python resource uses the license plugin's
standard SCRIPT_STYLE header; no plugin configuration or file-type bypass was needed. Existing JPMS/Gradle deprecation
warnings remain outside this test-only scope.

### Standards review

Independent Standards review against `b3fcf827e...934d131f9`: **0 actionable findings**. New tests have Test suffix and
class-level Issue annotations; no production/API/build changes, unrelated edits, suppressed tests or unnecessary
qualified types. Follow-up review of shape checks, Environment instrumentation, Map access and Python header: **0 new
findings**. The fixture-specific allowlists are appropriate bounded research; generalization would add unsupported scope.

### Specification review

Independent Specification review found **2 P2 evidence gaps**, both addressed and re-reviewed: reject non-string
sequence/map values and malformed selected shapes before invocation, matching the projection; instrument the supplied
Environment's create/tree/validation events and prove they remain unchanged across all four Holder routes. Added
rejection cases and lifecycle assertions pass in every lane. Follow-up review: **0 unresolved findings**; no production
scope creep. Standards total unresolved **0**; Specification total unresolved **0**.

### Delivery boundary and history audit

The local sequence was reviewed before publication: one bounded feasibility-evidence commit followed by its review and
compatibility qualifications. Each step has a distinct reason; no production behavior is delivered or implied. Final
checks above cover the final source tree. A source-unchanged final documentation/delivery commit requires only applicable
diff/link checks. The PR is authorized as a **draft evidence PR only**; no merge, ready-for-review transition, issue
closure, milestone/scope mutation or new implementation task is authorized here.

Delivery authorization audit: Git push channel **authorized** (dry-run accepted); GitHub CLI PR channel **authorized**
(repository capability verified). Connected GitHub App was not used for remote delivery. Credentials/authentication details
are not recorded. Remote CI/SonarCloud state belongs to the eventual draft PR revision and must be checked there; local
success is not a claim that pending remote checks passed. Request Hive reconciliation with the final commit, draft PR,
related #932/#529 and the remaining maintainer choices; retain PR:open until live merged evidence exists.
