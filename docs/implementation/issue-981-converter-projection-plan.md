# #981 — compiled converter constraint projection plan

Date: 2026-10-30. **Review-only proposal; no production behavior implemented.**
Related: [#981](https://github.com/klum-dsl/klum-ast/issues/981). No tracker, milestone or release-placement change.

## Authority and evidence

Base: current master `a88a97f6b2012949b18b520b927d73b011def155`; dedicated branch
`codex/issue-981-projection-plan`. This is classification B, a bounded extension of
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#compiled-converter-parameter-extension--981).

The updated issue and effective probe at **`58fbbc4697eb97a8b037080ea90e1ea252f17a11`** are authoritative.
The probe is retained locally on `codex/issue-981-effective-probe`, not merged or assumed remotely published.
Inspect these Git objects with `git show <commit>:<path>`:

- `docs/implementation/issue-981-effective-converter-probe.md` — paired-artifact authority, failure matrix and limits.
- `klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/EffectiveConverterParameterEvidenceTest.groovy`
  — Java/Groovy binaries, constructor/static routes, inherited Schema and controlled resolution.
- `docs/implementation/evidence/issue-981-effective/gate-b.md`, `pattern_probe.py` and `PatternOracle.java` in that directory
  — exact memory predicate and independent oracles.
- `klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/ConverterPatternEvidenceTest.groovy`
  — unchanged recognizer rejects the memory grammar.

Reported evidence: 32 focused and 83 full Bean Validation cases per G3/G4/G5 lane pass; Gate B has 11,244 Java/Draft
and 22,488 ECMA comparisons with no mismatch. These are prior probe results, not new implementation qualification.
The former direct-origin probe at `540c22cd5c3663d3dec93a3bfc710751a764afb5` is historical: the effective compiled-contract
decision supersedes its original-source distinction. It does not establish broader routing or current schema output.

Retain the boundaries and tests of [S1](issue-959-schema-s1-contract.md), [S2](issue-959-schema-s2-catalog.md),
[Pattern projection](issue-959-pattern-projection.md) and [first-level field composition](issue-959-composed-constraints.md).
Public guidance currently excludes these converters from [structured input](../user/Structured-Input.md) and limits
[Pattern output](../user/Json-Schema.md#conservative-pattern-projection) to existing field rules. General Groovy
[converters](../user/Converters.md) have broader capabilities; those do not imply structured admission.
[Builder-first migration](../user/Builder-First-Migration.md) keeps DSL producers distinct from this non-DSL route.

## Current gap and owning seams

`ConverterBuilder.createConverterFactoryCall` / `createConverterConstructorCall` knows the exact `sourceMethod`.
`createConverterMethod` creates a public String overload and rebuilds parameters without their annotations. Its public
signature alone cannot distinguish `fromString(String)` from `parse(String)`. The probe distinguishes them through paired
domain artifacts, but its fixture-only enumeration is not a production selection algorithm.

`StructuredInputAdmissionPlan.classifyProperty` and `InputContractResolver.fieldReason` reject the domain type before
selecting that overload. `GeneratedInput.Selection` identifies field/population groups, not a converter executable.
`BuilderContractReader` validates exact public Builder bytes; `CompiledBuilderSchema` is immutable. The plugin's
`BuiltinConstraintCatalog` owns mappings and `JsonSchemaProjection` owns policy/placement. These S1/S2 responsibilities
stay separate. `ConstraintProjection` currently targets FIELD only. `BoundedAsciiPattern` permits finite ASCII pieces,
not `+`, noncapturing groups, alternatives or escaped dot.

Runtime `InternalKlumBuilder.setSingleFieldViaConverter` already invokes the converter during construction. Inspection
must never call that path. Structured execution will use the selected public Builder overload only after ordinary
preflight; no new converter engine or parameter validator is needed. The unique-route restrictions below must make the
existing dispatch demonstrably select the recorded executable; otherwise stop before admitting that shape.

## Proposed contract

### Eligibility and exact selection

Start with an ordinary, non-key, non-DSL scalar field and a separately compiled public non-generic domain class.
The compiler must already expose exactly one eligible `field(String)` conversion, alongside the normal domain setter.
The selected executable must be declared on that exact domain class, have exactly one raw `java.lang.String` parameter
at index 0, and be either a public constructor or public static method returning exactly that domain class.
Constructor conversion requires existing `includeConstructors=true`; factories obey existing compiler discovery
(default prefixes or `@Converter`). Do not infer eligibility from public visibility alone or create another factory scan.

Require uniqueness across participating String routes, including a competing enabled constructor/factory. Do not use
enumeration order, a preferred method name or an annotation's presence to choose. Reject overloaded selected executable
families, default-generated variants, bridge/synthetic methods, type variables, varargs, inherited domain factories,
external factory classes, closure converters and competing authored Builder inputs. Ordinary generated domain setter
coexistence is not the deferred overload-resolution feature. Non-overridden inheritance of the Schema field/Builder
selection is included; a replacement field is classified afresh. Unsupported shapes retain an explicit rejection and
guidance to expose one supported route, rather than silently publishing an unconstrained domain object.

### Minimum compiler facts and paired inspection

Use existing public generated annotation targets; no companion protocol or general parameter metadata framework.
Proposed internal names are implementation details, not a new author API:

| Fact | Authority and minimum representation |
| --- | --- |
| Field identity, external naming/exclusion, prose/examples/deprecation | Existing declaring Builder getter's `GeneratedInput.Field`; retain original Schema member association independent of external name. |
| Selected public input | Existing `Selection(field, PROPERTY)` on the exact generated `field(String)` overload; public descriptor/return type remain reflected. Do not select the domain setter for structured input. |
| Exact converter | One generated converter annotation on that overload: declaring domain binary name, executable name plus JVM descriptor, parameter index 0. `<init>` distinguishes the constructor without another route enum. |
| Completeness | Explicit field-side converter-selection cardinality (0 or 1) detects a stripped association; validate both directions against selection and overload. No duplicate executable descriptor inventory. |
| Effective constraints and policy | The exact domain parameter's compiled declaration, not copied Jakarta annotations or a second frozen annotation snapshot on the Builder. Normalize only the closed facts described below. |
| Provenance | Declaring Builder/member and its exact resource/artifact, plus exact domain owner/executable/index and resource/artifact. Preserve the original parent Builder under binary inheritance. No authored spelling or collector-origin claim. |

Carry the exact selected `sourceMethod` association through public Builder projection before parameter rebuilding loses
it. Do not reconstruct compiler selection from AnnoDoc `@see`, copied method prose, generated implementation bodies or
method names alone. Emit complete facts explicitly under the existing development marker 1; old incomplete artifacts
receive matching-tool/recompile guidance, not a compatibility decoder.

Keep `BuilderContractReader` responsible for Builder bytes. A small private selected-parameter reader, called by the
resolver, may resolve the recorded public signature with initialization disabled and statically bound Java APIs, then
decode only that declaration's closed annotation facts from its exact bytes. Reuse the existing resource/origin checks
for the paired artifact. No arbitrary classfile discovery or reflection on hidden implementation members. Resolving the
known annotation API and referenced member types without initialization is mandatory: an empty reflection annotation
list is not proof of absence when the Jakarta API is missing. Decode defaults only from that known API's inert contract;
do not instantiate annotation proxies for arbitrary annotations or traverse custom annotation hierarchies.

Validate descriptor, visibility, parameter index/type, return/declaring type and unique compiler association before reading
facts. Cache only successful immutable results by exact Class/signature within the existing consumer session. Retain
separate field and converter constraint sources in `CompiledBuilderSchema`; the catalog receives validated facts and
never resolves classes. Artifact location is diagnostic association, not integrity evidence. There is no digest, source
scan, stale-JAR detector, global cache or new module/dependency. Hostile classloaders/transformers are outside the guarantee.

### Closed facts, policy and placement

One effective declaration-parameter Jakarta Pattern is the first hard candidate. Read its regexp, flags, groups and
payload completely, including default values and missing member types; retain deterministic descriptive declaration
data. Empty flags/groups/payload and singleton cardinality are required. Directly authored and collector-produced
equivalent compiled facts are equal here; do not reuse the field `plain` bit to assert an unproved original origin.
Give this parameter source a distinct internal eligibility context. Repeats/containers, type-use, custom/composed
constraints and group redefinition remain outside hard projection; no flattening or composition traversal is added.

Extend `ConstraintProjection` to PARAMETER while preserving FIELD semantics. The selected parameter's effective value
is authoritative; absent policy means PROJECT. PROJECT emits qualified assertions and describes complete unsupported
Pattern facts; TEXT_ONLY emits their descriptions; OMIT suppresses their constraint-derived output/source attribution.
All three validate required facts before suppression. The container field's own constraint declarations remain bound
to the domain value and retain existing descriptive treatment and field policy; they never become raw String assertions
because the input payload is now String. Field policy cannot suppress or override the parameter's facts. Unknown
parameter constraint types receive no hard assertion or invented custom semantics; this slice promises only the closed
Pattern vocabulary and opaque unsupported identity, not general annotation-documentation transport.

Render at `properties[effectiveExternalName]`, with `type: string`; reuse existing conjunction/ASCII guard and optional
member semantics. No nested parameter object or domain `$defs` entry. Constraint source attribution names the exact
domain executable/index, independently of field-source naming. Preserve ordinary field prose, examples and deprecation
under every parameter policy, inherited names/exclusion and owned-child use locality. Do not adopt converter-method
Javadoc as the field's ordinary documentation. Validate admitted owned closure, including omitted children, before
interpretation or replacing output/manifest; suppression and naming exclusion do not hide malformed carried facts.
Supplied null/non-String values reject structurally; absent optional fields remain allowed. No coercion, required/default
inference, automatic JSON Schema validation or runtime parameter enforcement follows.

| State | Required result before projection/execution |
| --- | --- |
| Complete supported | Resolve exactly, then apply parameter policy and the qualified Pattern mapping. |
| Genuinely absent or producer-stripped Pattern | No Pattern assertion. These are indistinguishable compiled contracts; if annotation was expected, rebuild producer and Schema. Do not invent original-source absence. |
| Complete unsupported flags/groups/payload or valid regexp outside subset | Retain a reasoned description under PROJECT/TEXT_ONLY; OMIT suppresses it. Never weaken to an approximate assertion. |
| Missing domain/API/member type, linkage/ClassFormatError | Fail with field/external name, Builder artifact and selected owner/descriptor/index, plus classpath/rebuild guidance. No alternative-route fallback. |
| Missing/stripped generated association, wrong descriptor/index, ambiguity | Fail completeness/selection; expose one supported route and recompile matched artifacts. |
| Malformed annotation/member or invalid regexp | Fail even under TEXT_ONLY/OMIT. JDK syntax validation is allowed framework work, never matching against user values or invoking validators. This stricter converter-fact rule does not change #959's existing field-regexp text fallback. |

### Exact regex extension, not general translation

C1 uses unchanged ASCII-BOUNDED-1. C2 proposes a converter-only recognition entry that also accepts this small grammar:
existing printable-ASCII literal/classes plus escaped literal dot; concatenation; `+` on a single consuming atom;
one-level noncapturing groups of nonempty alternative sequences, with optional `?` on the group. Inside a group use
atoms with existing finite quantifiers or `+`; no nested groups, empty alternatives, group repetition, `*`, lookaround,
captures, backreferences, inline flags, negated/Unicode classes or generic escapes. Preserve paired outer anchors only.
This includes exactly the motivating grammar without hard-coding its domain name or regexp:

```regex
[0-9]+(?:\.[0-9]+)?(?:[kMGTPE]|[KMGTPE]i)?
```

Preserve existing finite-quantifier bounds and source/token ceilings (256 source characters, 32 consuming pieces counted
across alternatives, each finite bound at most 255, total finite expansion at most 1024). `+` deliberately has no input
length ceiling; imposing the old finite maximum on its accepted Strings would invalidate the 1,100-digit probe control.
Group depth is at most one. Wrap the whole accepted expression in `^(?:...)$` and conjoin the existing typed printable-
ASCII guard. Preserve the old recognizer path and bytes for existing ordinary fields and composing field rules.

The prior proof covers the fixture only. C2 must supply a structural language argument for each newly admitted rule and
their combinations, plus a hand-authored independent corpus of actual emitted schemas against JDK full matching, ECMA
plain/Unicode modes and Draft 2020-12. Include final line terminators, Unicode digits, supplementary/lone surrogates,
empty/long inputs, alternation precedence and JSON escaping. Finite testing alone does not qualify the expanded grammar;
if any proposed rule cannot be justified exactly, stop that slice rather than approximate or silently narrow the fixture.
No regex resource-exhaustion or complete Kubernetes quantity validation promise follows.

## Implementation slices and acceptance

### C1 — exact converter input with existing Pattern mappings

Deliver one end-to-end scalar route using a finite Pattern such as `[0-9]{1,8}`; the memory regexp remains descriptive.
Modules: annotations (parameter policy), compiler (`ConverterBuilder`, public projection and admission enrichment), runtime
(generated facts, reader/resolver/result and existing structured dispatch), plugin (closed catalog context and placement).
Do not add a dependency from runtime to the catalog or a Jakarta provider dependency.

Reasoned commits: (1) exact compiler association, parameter policy target and strict paired reader with binary/failure
tests, leaving admission closed until complete; (2) admission plus projection/dispatch and policy/source separation with
end-to-end tests; (3) synchronized public docs/release note and qualification record. Keep each commit green and test
new behavior with its implementation; combine adjacent steps if otherwise only a temporary unused abstraction remains.

Acceptance: Java/Groovy separately compiled constructor and factory domain JARs; source/class-directory removal; parent
and descendant Schema JARs; exact descriptor/index/artifact matching and `fromString` versus `parse`; direct versus
collector equivalence; every exclusion/negative row above; Pattern present/absent/unsupported and all policies. Prove
field policy and field constraints cannot change parameter assertions, renamed/inherited/excluded member behavior,
independent ordinary docs/examples/deprecation, omitted owned children and unchanged output/manifest on failure.
Controlled counters prove zero initialization/body/default/lifecycle/validator execution in compilation, contract
preparation and generation, with deliberate execution positive controls separately. Retain G3's dynamic-Class-dispatch
negative control. Separately exercise actual structured input with an ordinary converter to prove selection and field
assignment, rejection of numeric scalars without coercion, and lack of automatic Pattern enforcement.

### C2 — qualified memory grammar through actual generated output

Depends on C1. Implement only the syntax corridor above in the private recognizer/catalog, preserving existing field
semantics. Commit recognizer changes with focused grammar/rejection tests, structural proof and independent emitted-
schema oracle; then update the documentary example, user subset description and qualification record. Reuse the probe
oracles as independent controls, not a production expected-value generator or a runtime validation dependency.

Acceptance: exact memory constructor/factory parameter gives `type: string` and the qualified pattern/guard at the field's
external name for all G3/G4/G5 producers. Test grammar compositions and ceilings, existing finite cases unchanged, unsupported
syntax descriptions, policies and all old integral/decimal/composition/schema fixtures unchanged. Preserve quoted numeric
YAML guidance; the fixture is not Kubernetes-specific behavior. No hard memory assertion ships if the oracle/proof fails.

## Verification, documentation and delivery

- Start each slice with focused G3 tests in existing compiler/runtime and Bean Validation binary-fixture seams. All new
  executable classes use `Test`; add `@Issue('981')`, and `@Tag('documentary')` / `@See` for the authoring feature.
  Retain probe controls by porting relevant fixtures, not merging all historical evidence as production dependencies.
- Before implementation handoff run affected full `test`, `groovy4Tests`, `groovy5Tests`, lane isolation, licenses and
  generated-contract/JPMS checks. The plugin has its baseline `test`/`check` and packaged G3/G4/G5 producer matrix, not
  invented compatibility tasks. Run the explicit annotation-propagation artifact matrix if its contributor seam changes.
  End with root `check` and the independent actual-output oracle at a stable tip. Record counts and any existing skips.
- Verify separate classpath and named-module public access, missing/denied classes, corrupt bytes and shadow resources;
  never add opens/access flags or ambient-loader fallbacks. Existing Gradle cache/output atomicity tests must include
  a changed domain contract followed by Schema recompilation. No changed-JAR-without-recompile promise.
- In the implementing slices update `docs/user/Converters.md`, `Structured-Input.md`, `Json-Schema.md`, `Migration.md`
  and next-release `CHANGES.md`; link migration/navigation as needed. Explain the new parameter target, field/parameter
  ownership, exact subset, fail-closed classpath requirements, no #971 enforcement and matched-artifact recompilation.
  Synchronize Builder-first migration wording only where it could conflate DSL producers with this non-DSL route.
  This review-only change does not advertise an available feature in public docs or release notes.
- Use documentation/fence/link/diff checks and independent Standards/Specification reviews. No local SonarCloud upload
  or manual workflow run; ordinary PR CI remains authoritative. Preserve reviewed history for follow-up corrections.

## Stops and maintainer review

Explicitly excluded: runtime/direct-call parameter enforcement (#971); general converter/factory discovery; overload,
default, bridge, generic and varargs resolution; collection/map/DSL conversions; custom/composed/repeated parameter
constraints, Size and editor work (#987). Already-admitted one-payload `void @Builder.Method` operations are a possible
later reuse, not part of C1/C2. No hidden implementation inspection or user-code execution during inspection/generation.
No source-spelling recovery, annotation propagation to Builder methods, protocol framework or historical binary promise.

No unresolved product choice was needed to write these proposed slices. Maintainer review accepts or amends the concrete
minimal association and converter-only regex corridor before an implementation assignment; neither is claimed already
implemented or separately qualified by the probe. Stop for a semantic decision if exact dispatch cannot be retained,
public signature access needs hidden implementation access, or a regex rule lacks an exact proof. Do not broaden scope
to work around such a stop. The need horizon remains Future; no new operational prerequisite for ScHelm is introduced.

Tracker impact: Related: #981 only; no issue completion, curation/release-gate, label or milestone mutation. The assigned
documentation work may finish in a draft PR after review; implementation and maintainer approval remain future work.
Hive owns delivery/archive reconciliation. Retain this worktree and request reconciliation rather than self-archiving.
