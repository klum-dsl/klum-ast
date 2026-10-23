# SCHEMA-R — reflection versus compiler enrichment

Date: 2026-10-23. Owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Authority: [revised ADR 0031](../../adr/0031-structured-schema-input-artifacts.md#generator-architecture-and-fidelity)
and [SCHEMA-R assignment](../adr-0031-schema-0.md#next-required-slice--schema-r-reflection-versus-enrichment).
Base: `22b3cff17` (current master, including merged reflection-first revision #944).
Scope: disposable tests and measured evidence; no generator, plugin task/dependency, generated metadata or WIRE change.

## WIRE-REMOVE disposition — 2026-10-23

This report preserves its measured migration checkpoint. [Final removal evidence](issue-529-wire-remove.md) records
retirement of the private carrier and disposable carrier-specific helpers/resources, plus migration of durable assertions
to the production facade's fixed expected-contract corpus. Historical file names and commands below belong to their pinned
revision/merged PR; they are not current architecture or runnable instructions for the removed transport. The next #529
implementation entry point is separately assigned reflective projection and Gradle delivery; no generator resumes here.

## Outcome and production boundary

Public contracts expose Model/Builder identities, exact signatures, generic containers, inherited declarations, nested
Builder scopes and genuine embedded `@AnnoDoc` text. Most type data in WIRE is redundant for generation. Existing public
metadata does **not** retain field provenance, population selection, original field rename/ignore or every authored
marker when a generated projection occupies the same signature. These are small annotation candidates. No fact in this
inventory demonstrates a need for a private companion.

One concrete safety decision remains before production: **ordinary Java annotation reflection can execute user code**.
On a public `@Builder.Method @Policy(Mode.SAFE)` declaration, even selecting only `getAnnotation(AnnoDoc)` parses the
other annotation and initializes `Mode`. The deliberate hazard control catches the exact enum initialization trap;
the stack passes through `Class.getEnumConstantsShared`, `Enum.valueOf` and Java's annotation parser. Loading the
contract with `Class.forName(name, false, loader)` does not prevent this subsequent initialization. Avoiding calls to
annotation enum-valued members is insufficient.

Recommended next decision: permit selective annotation-byte reading of **exact named public contract resources** for
safe protocol extraction (or a prior safety check), while keeping signatures/types/inheritance reflective. This is not
root discovery, directory scanning, Model-field inspection or a carrier reader. That adjustment needs maintainer
selection before implementation. Neither a small new annotation nor a private companion automatically makes
`getAnnotation` safe on an existing declaration carrying arbitrary Schema-authored annotations. Do not weaken the
no-execution guarantee or silently omit operation/naming metadata. This probe deliberately implements no workaround.

## Executable evidence and isolation

[`SchemaReflectionTest`](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/SchemaReflectionTest.groovy)
compiles an abstract API and a concrete Schema independently into class-only JARs. Compiler output directories are
deleted after packaging. Packaging enumerates compiler output only; extraction never enumerates directories/JAR entries,
chooses roots from filenames, or consumes sources, mirrors, compiler ASTs or WIRE metadata.
[`SchemaReflectionProbe`](../../../klum-ast/src/test/java/com/blackbuild/klum/ast/schema/SchemaReflectionProbe.java)
is a disposable Java reflection observer, not an admission normalizer, generator or reusable reader API.

Each extraction uses a fresh closeable URL loader whose parent is the platform loader. Its explicit inputs are the API
and Schema JARs, active-lane Groovy JAR, packaged Klum runtime/annotations and AnnoDocimal annotation JAR. No compiler or
test classes enter that loader. The test harness alone reads the exact sibling carrier's constant with ASM for comparison;
the reflection observer has no carrier access. Its recorded resolution list shows incidental Model types
`Application`, `ApplicationApi`, `Component`, and enum `Mode`, plus public nested Collection/Cluster contracts and
runtime/annotation dependencies. No hidden `Application$Builder`, `Application$Factory`, closures or `WirePlan` is
loaded during extraction. Model classes are not requested as discovery/inspection targets, and none of their fields,
methods, constructors, `Create` values or implementation members is inspected during extraction.

Fresh-loader extraction, missing-dependency and changed-dependency paths leave the execution trap unset. Separate
positive controls deliberately initialize API/Schema/child/enum/factory types and separately reach factory construction,
Builder initializers, lifecycle, default and converter closures. Only that execution-control phase accesses `Create`
and invokes construction; it sets the isolated context loader for runtime services and restores it afterwards. The
annotation hazard is another deliberate control, not a successful no-execution extraction claim.

Two independently resolved API versions retain different inherited `@AnnoDoc` text under the same binary names, with
distinct contract and annotation `Class` identities. This proves dependency ownership/isolation rather than relying on
plugin/application casts. Missing inherited contract fails linkage; missing AnnoDoc protocol class fails explicit
resolution. Java may silently drop annotations whose types are missing, so production must explicitly resolve mandatory
protocol types and distinguish optional prose absence from incomplete dependency inputs. The observer reports missing linkage/protocol dependencies with root and corrective classpath context, and explicitly
rejects a root Class from a different loader. Full generation diagnostics remain future implementation.

Groovy 4/5 additionally compile real `module-info` descriptors for API and Schema, resolve a named ModuleLayer from the
explicit JARs and reflect public exported contracts and inherited documentation. Schema packages are **not open**; no
`setAccessible`, `--add-opens`, `--add-exports`, `--add-reads`, patching or hidden implementation inspection is used.
The named-runtime descriptor requires `org.apache.groovy`, so the Groovy 3 lane explicitly leaves that case to 4/5.

## Complete current carrier inventory

Source: [`StructuredInputAdmissionPlan.canonicalForm`](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java).
Categories: **a** direct reflection; **b** existing generated/projected annotation; **c** small generated annotation
needed; **d** private companion genuinely necessary. A mixed row separates the reflected part from the missing fact.
Execution-only information may be omitted by #529; that does not authorize its removal from #932's carrier.

| Current fact/token | Measured category and evidence | Minimum generator consequence |
| --- | --- | --- |
| Header `WIRE-PLAN/B`, `DATA`, `model`/`entry`/`rejection` record tags, Base64/null spelling, order | Transport, not semantic facts; no a/b/c/d equivalent needed | Do not reproduce an encoding/version/order protocol; sort eventual output independently. |
| `model` identity | **a**: Builder self type-variable bound is `Application`; Factory `One()` returns `Application` | Resolve actual bound/return identity inside the loader, not a stripped `_DSL` string. |
| `builder` identity | **a** exact root/nested interface Class identity; **b** Builder type `KlumGenerated.tags` contains `dsl-support-role:Builder` | Use one loader's identities; retain parent declaring owner. |
| Entry `externalName` | **b** authored `endpoint(Map)` retains literal `@JsonProperty('connect')` and ignored `hidden(String)` retains `JsonIgnore`; **c** field `serviceName(String)` lacks field rename, despite carrier `service-name` | Attach original field rename/ignore to its attributed public input declaration; derive precedence once. |
| Entry `nameOrigin` | **b** for exact authored naming annotations; **c** for missing field naming attribution | `unchanged` with false ignore/empty rename stays IDENTITY. Do not guess origin from spelling; the cross-artifact Collision fixture retains the local rename in its carrier but not its public setter, colliding with inherited API `region`. |
| Entry `role=OPERATION` | **b** ordinary authored `endpoint` retains `Builder.Method`; **a** signature proves one Map payload. **c** where generated projection shadows original marker: authored `values(Iterable<String>)` is public without `Builder.Method` | Keep field-origin metadata distinct from authored candidates; preserve compiler exclusion where the public projection lost provenance. Query markers do survive on `status`, and do not admit it. |
| Entry `role=PROPERTY/OWNED_OBJECT/KEYED_OWNED_MAP` | **c** missing field role/group/ownership; **a** public getters/creators expose value, child and container types | Annotate selected input/population method with original field member and admitted category. LINK and unkeyed relationship list remain excluded although callable public methods exist. |
| Entry `source` | **a** operation owner/signature and its Model bound; **c** field member linkage to `addValue`, or omitted field | Owner normally follows declaring public contract's bound. Preserve exceptional original owner only if projection loses it; do not duplicate every owner name. |
| `operation.declaringBuilder` | **a** `Method.getDeclaringClass`; inherited `region(String)` belongs to the API Builder | Traverse/deduplicate actual public parents, not simple-name methods. |
| `operation.name` | **a** exact `Method.getName` | No new name list. |
| `operation.descriptorGroup` | **a** exact erased argument/return types; **c** which methods serve a field and which population path was compiler-selected | `values` selects `addValue(String):String`, not the public plural Iterable override. Annotation placement identifies selected declaration; do not serialize all overload descriptors. |
| Entry `mapKeyType` | **a** Map getter generics and keyed creator String parameter | Distinguish data map from control Map; do not rename literal keys. |
| Entry `excludedChildKeyMember` | **c** for exact `@Key` source identity: child public `getName()` exists but no `name(...)` input or Key marker identifies it | Omit for initial schema when only admitted members are annotated: closed child definition already rejects key input. Extra key-specific diagnostic metadata is optional, not a prerequisite. |
| Entry `fixedChildKey` | **c** compiler Cluster membership selects native `primary`; field alias is `main-slot`; public zero-key creator does not identify literal origin | Omit from projection: construction-only fixed key changes neither child properties nor required/discriminator rules. No new key annotation needed for #529. |
| Payload `kind=SCALAR/ENUM` | **a** reflected parameter/getter Class; `Field.isEnumConstant` yields SAFE/FAST without static reads | Never call `getEnumConstants`, values/valueOf or user enum methods. Annotation parsing is the separate hazard above. |
| Payload `kind=LIST/MAP` | **a** generic return/parameter Types; getter preserves NavigableSet and nested Map/String/List/String; **c** field-versus-operation origin as above | Normalize supported field storage to arrays using the field annotation; a custom Set/Iterable operation is not admitted by that rule. |
| Payload `kind=NESTED_OBJECT` | **a** exact child Builder generic return and getter references; **c** owning eligibility as above | Reachability alone is not admission; dynamic Map/Class/Factory selectors remain callable controls. |
| Payload `type` | **a** primitive `int`, enum Mode, String, generic container and self Model bounds | No recursive type catalog; generic Types carry nested values. Raw/unresolved types must be diagnosed, never expanded as Object. |
| Payload `builder` | **a** `Component_DSL.Builder<Component>` creator/return/getter linkage | No duplicate nested-type registry. |
| Payload `keyType` | **a** data-map getter/operation generic key and keyed creator signature | Literal String keys; no transformations or source-property guessing. |
| Payload `item` recursively | **a** ParameterizedType arguments on `endpoint(Map<String,List<String>>)` and `getLabels()` | Remains one Map argument; no argument spread. |
| Rejection `source` | **a** surviving authored public method owner/signature; **c** field/omitted source provenance (`reserved` has no public input method) | A type-level annotation on declaring public Builder is a truthful target even when no input method survives. This is not evidence for d. |
| Rejection `reason` | **a/b** derive signature/numeric/shape rules from reflected types and exact supported annotations where retained; **c** ignore, ownership, population failure, collision or lost authored provenance decided in the compiler | Retain minimal exclusion category/source for otherwise invisible decisions, not a copy of runtime prose or complete normalized plan. |

**d: none demonstrated.** All missing source facts have a method-level or declaring-public-type annotation target.
This is an inventory of the finite grammar, not a claim that reflection alone can reproduce compiler admission.

## Documentation, defaults and proposed enrichment

The actual protocol is runtime `com.blackbuild.annodocimal.annotations.AnnoDoc.value()`. Inherited `region` text comes
from the separately compiled API declaration; authored `endpoint` retains its prose/parameter text. Generated population
and relationship documentation also exists. `@AnnoDoc` is not parsed for grammar or used to join singular/plural names.
Field provenance enrichment is still needed for truthful field-to-method prose attribution.

`KlumGenerated` type tags survive. Contrary to the source-derived hypothesis, the public `addValue`/relationship methods
do not retain implementation-generated role tags: the observed public projection carries AnnoDoc but no KlumGenerated method annotation. Endpoint's
source-authored annotations survive. This evidence does not assume the cause of that omission. This is a
concrete correction to the plan's proposed use of method-level generated hints, not an emitter defect fixed here.

`computed(String)` and `getSupplied()` do not expose `@Default` or an effective initializer value. The fixture's initializer,
default and converter closures remain unexecuted during extraction and are reached only by positive controls.
`DefaultValues` is annotation-type metadata, not a promised effective value on generated input methods; its presence or
class-valued closures would still describe declarations only. No first-slice JSON `default`, required-presence or
validation assertion is justified, and no enrichment is requested merely to make such values available.

Proposed **minimum facts**, not selected public annotation names/API:

- On a compiler-selected public field input/population declaration: original field member, admitted input category,
  and original nontrivial rename/ignore facts. The method itself supplies signature, owner, payload/nested types and
  selected population identity. Where property grouping needs its getter, retain only that declaration association,
  rather than every related overload.
- On the declaring public Builder type: otherwise unrepresented source member and exclusion/collision category,
  sufficient for deterministic diagnostics. Prefer references to existing declarations where available. No WIRE text,
  descriptor arrays, recursive payload tree, fixed keys or complete admitted-name catalog.

Any implementation must specify RUNTIME retention, METHOD/TYPE targets, stable annotation identity/member defaults,
explicit declaring-owner lookup (not Java `@Inherited` for interfaces), duplicate/diamond handling, and missing-metadata
upgrade/recompile behavior. Those are generated ABI obligations. New annotations, their concrete encoding and any
recompile policy require the separately assigned enrichment slice; this evidence changes none of them.

## Plugin placement and Gradle inputs

A private plugin implementation remains viable: the probe accesses exported public runtime/annotation contracts and
has no generator dependency in runtime. Keep the inspected runtime/Groovy/annotation versions in an isolated resolved
loader; the plugin's own classes are not cast-compatible with them. Resolve annotation protocol types through that
loader (the two-version test demonstrates distinct AnnoDoc identities), close loaders, and keep Classes/proxies out of
configuration-cache state. The current probe validates loader separation, not arbitrary historical runtime-version ABI
compatibility or an actual Gradle Worker task. No additional published module is justified by this evidence.

Declare explicit root contract names, projection options/tool implementation and the complete resolved runtime classpath
as task inputs. Use classpath fingerprinting that retains **method annotations and embedded prose**: dependency docs
changed with identical binary names and the resulting reflective text changed without rebuilding the Schema. A compile-
ABI-only fingerprint which discards those attributes would be insufficient. Include annotation implementation/retention
changes; a missing protocol JAR cannot mean a valid empty result. Source/mirror directories are not inputs. Runtime
classpath changes, member/root deletion, deterministic outputs, stale cleanup, relocation/build cache and configuration
cache still need future TestKit qualification; no incrementality or cache success is claimed by this probe.

## Validation and handoff

Qualification commands and results are recorded after execution below. This internal evidence adds no user-visible
feature, documentary test, user page, migration note or CHANGES entry. #529/#932 remain independent and open; no issue,
milestone, release, carrier or runtime state is changed. SCHEMA-1 stays paused.

- Baseline: `:klum-ast:test` filtered to SchemaReflectionTest, AnnoDocTest, GeneratedDslSupportSpec,
  StructuredInputAdmissionPlanTest and StructuredInputCollectionContractTest — 102 cases, zero failures/errors,
  one reasoned named-module skip reserved for Groovy 4/5.
- Compatibility: `:klum-ast:groovy4Tests --tests '*SchemaReflectionTest'` and
  `:klum-ast:groovy5Tests --tests '*SchemaReflectionTest'` — eight cases in each lane, including named modules.
- `:klum-ast:licenseTest`, Markdown fences/local links/anchors, and `git diff --check`.
- No full build or root check: this slice changes disposable tests/evidence only; the focused corpus covers the
  generated/reflected compatibility seam. Production runtime/Jackson behavior is unchanged.

Independent local review against `22b3cff17`: Standards **zero findings**; Specification **zero findings**.
Single-commit history reviewed before first publication. Tracker impact: **Related: #529** only; no issue-state,
milestone or curation/release-index update is needed for this evidence slice. Future production annotation-access
selection remains a maintainer decision, not unfinished probe implementation.
