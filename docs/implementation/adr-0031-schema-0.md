# SCHEMA-0 — reflective JSON Schema delivery plan

Date: 2026-10-23. Status: **WIRE migration complete; next separately assigned slice is reflective projection; SCHEMA-1 paused**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md#generator-architecture-and-fidelity).
Generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Independent interpreter owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Reviewed master: `de76710dfc649f17857215d5c86cbd88165ab85c` (merged #942).
Classification: **B — revise ADR 0031 and its implementation plan**, under the explicit 2026-10-23 maintainer decision.
This documentation revision changes no production code, generated contract, WIRE carrier or runtime implementation.


## Final compiled-contract checkpoint — 2026-10-23

[WIRE-REPLACE #947](https://github.com/klum-dsl/klum-ast/pull/947) selected the internal, package-private
`CompiledBuilderSchema`: reflected public Builder/Factory signatures and types, exact-contract selective annotation
bytes, and minimal generated field/selection/exclusion and child/fixed-key facts. [WIRE-REMOVE evidence](evidence/issue-529-wire-remove.md)
records removal of companion emission, serialization, readers, binary resources and transitional comparison machinery.
There is no production carrier dependency, fallback or compiled-carrier compatibility promise: the carrier was internal
and unreleased. Required missing enrichment retains recompile/package guidance. Fixed expected contracts now guard the
final facade; useful binary/no-execution, inheritance, documentation, runtime and JPMS regressions remain.

SCHEMA-R #945 and WIRE-SCHEMA-PROBE #946 are complete historical checkpoints. The next separately assigned #529 work is
reflective structural projection inside the Schema Gradle plugin, followed by its isolated classpath and output/cache
qualification. No generator starts here. The historical SCHEMA-1 branch stays paused and untouched; #932 runtime language
and lifecycle semantics remain independent. Earlier carrier descriptions and probe instructions below record migration
history, not active architecture or outstanding assignments.

## SCHEMA-R measured checkpoint

[SCHEMA-R evidence](evidence/issue-529-schema-r.md) now measures the source-derived inventory below against separately
compiled public contracts. Two concrete corrections govern subsequent implementation:

- Type-level `KlumGenerated` tags survive, but public generated population/relationship methods in the probe do not
  carry implementation role tags. Use measured field/source enrichment rather than assuming those method hints exist.
- Even selecting `getAnnotation(AnnoDoc)` can initialize a user enum through an unrelated retained annotation on that
  declaration. The bounded hazard control reproduces this; initialization-disabled class loading alone is insufficient.
  A production-safe annotation access strategy needs explicit selection before the generator resumes. The evidence
  recommends selective reading of exact public-contract annotation resources while retaining reflective type discovery;
  this checkpoint neither implements nor approves that strategy.

The measured inventory proposes only field attribution/admission/population/naming facts and otherwise invisible
exclusion provenance. No companion-only datum is demonstrated. Fixed-child keys and child-key-specific diagnostics
need no new metadata for the first structural projection. The original table remains the planning hypothesis/history;
use the evidence for actual R/A/N/C outcomes. SCHEMA-1 remains paused and #932/WIRE are unchanged.

## WIRE-SCHEMA-PROBE compatibility checkpoint

The [binary contract comparison and full parity matrix](evidence/issue-529-wire-schema-probe.md) consume merged
SCHEMA-R #945. A package-private test facade observes public Builder/Factory facts with selective exact-contract
annotation-byte access; the carrier remains solely a comparison oracle. Thirty-two probe cases expose 77 exact admission,
field/group/naming/key/exclusion differences in the admission corpus rather than declaring reflection-only parity.
Supported container types, nested generics, inherited declarations, authored eligibility and no-execution binary loading
are qualified. The linked evidence records explicit **WIRE-REPLACE** and **WIRE-REMOVE** exit criteria.

Shared runtime migration needs the exact child Key member and fixed native child key that generator-only projection
can omit. These remain small annotation candidates alongside SCHEMA-R's field/exclusion provenance, with no demonstrated
companion necessity. This checkpoint implements no production metadata, selects no production annotation-access strategy,
and authorizes no WIRE replacement/removal, generator resumption or #932 language/runtime change.

## Authority and issue boundary

JSON Schema generation is classpath-oriented reflective tooling normally invoked by the Schema Gradle plugin. It receives
an explicit root public generated contract type and isolated resolved classpath. Reflection over public `Foo_DSL.Builder`
/ `Foo_DSL.Factory` declarations, existing annotations and embedded `@AnnoDoc` is the primary input. Compiler enrichment
supplies only irreducible facts that reflection cannot recover truthfully. Prefer small generated annotations; retain a
private companion only for demonstrated necessity. No filesystem discovery API or standalone CLI is required.

| Owner | Responsibility | Independence |
| --- | --- | --- |
| #529 | Reflective contract extraction, minimal enrichment inventory, conservative Draft 2020-12 projection, Gradle inputs/outputs and generation diagnostics | Does not invoke the interpreter, read its private plan reflectively, or require WIRE runtime implementation. Runtime contract types may be dependencies. |
| #932 | Optional parsed-tree interpretation, preflight, operation execution, traversal order, Construction/session integration and runtime diagnostics | WIRE-1 is merged; this revision does not change it or dictate its carrier migration. It may later reuse the same small enrichment annotations. |
| Shared overlap | Explicitly accepted names, operation roles and structural shapes | Keep a shared acceptance corpus where useful; neither implementation defines the other. Reconcile a discrepancy explicitly rather than treating runtime internals as generator authority. |
| ADR 0009 / consumers | Jackson interoperability / schema publication, copying and editor association | Existing Jackson modes and consumer packaging remain independently supported. |

Retain the accepted concrete scalar/enum, supported field containers, data maps, nested owned objects, literal keyed owned
maps and eligible one-payload `@Builder.Method` subset. Identity fallback and explicit ignore/rename are the current
naming boundary. No new language, polymorphism, converter syntax, lifecycle rule or public descriptor SPI is selected.

## Confirmed baseline and affected seams

| Evidence / seam | What is established | Limit for this revision |
| --- | --- | --- |
| [#937](https://github.com/klum-dsl/klum-ast/pull/937), [#938](https://github.com/klum-dsl/klum-ast/pull/938) | Compiler admission and the private constant-only `<Model>_DSL$WirePlan` carrier preserve source/provenance and normalized runtime facts across artifacts. | Comparing then-existing annotations with a full carrier did not test minimal new annotations or prove every serialized datum irreducible. |
| [#939](https://github.com/klum-dsl/klum-ast/pull/939), [#940](https://github.com/klum-dsl/klum-ast/pull/940) | WIRE runtime plan and restricted WIRE-1 are merged; fixed-child-key and twelve-token/recompile policy are current. | Historical runtime evidence, not a generator prerequisite or authorization to change #932. |
| [#941](https://github.com/klum-dsl/klum-ast/pull/941), [#942](https://github.com/klum-dsl/klum-ast/pull/942) | SCHEMA-0 planning and test-only SCHEMA-T emitted real Draft 2020-12 and validated a bounded cross-artifact corpus without execution. | Carrier-first and artifact-byte-reader architecture is superseded. Reflective loading, minimal enrichment and production placement remain unproved. |
| [`GeneratedDslSupport`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java) | Projects public signatures, generic parents, method/parameter annotations, relationship-creator tags and exact documentation. | Public visibility or a generated tag alone does not classify every field/operation group; don't infer source provenance from names or prose. |
| [`AbstractMethodBuilder`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AbstractMethodBuilder.java) | Source-field linkage currently includes compiler-local AST metadata. | AST identity is not automatically retained on public bytecode declarations. |
| [`AnnoDocTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/AnnoDocTest.groovy), [`KlumGenerated`](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/KlumGenerated.java) | Public Builder documentation is read through `getAnnotation(AnnoDoc)`; generated tags have runtime retention. | Exact attribution and availability across separate artifacts still need a generator probe. Ordinary reflection lacks raw Javadoc, not embedded `@AnnoDoc`. |
| [`KlumAstSchemaPlugin`](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java), [plugin dependencies](../../klum-ast-gradle-plugin/build.gradle) | Existing Schema plugin supplies source-mirror lifecycle precedent; runtime is currently a test-only plugin dependency. | Assess a private implementation depending on runtime with an internal extraction/projection seam; do not add that dependency, a module or a task yet. |

Current failures include compiler rejection/collisions, missing packaged carriers in the WIRE route, and absent or
unresolvable referenced types. SCHEMA-T proves its chosen fixture, not reflection viability or full generator readiness.
SCHEMA-1's unmerged implementation is paused, not the master baseline and not a source of new requirements.

## Reflection-versus-enrichment inventory

This is an exhaustive **source-derived classification to verify**, not executed reflective-probe evidence. Source of
truth for the current persisted shape is
[`StructuredInputAdmissionPlan.canonicalForm` and its records](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlan.java).
`entry` has twelve tokens including its record tag; payloads recursively contain kind/type/builder/keyType/item.
The classifications below cover the model record, all entry and payload members, and rejection records.

- **R — directly reflected:** use public contract identity, signatures, generics and inheritance.
- **A — existing generated annotation:** use only the fact actually retained; verify target and attribution.
- **N — needs a small generated annotation:** preserve missing compiler provenance/decision on an appropriate public declaration; exact annotation name/members remain unselected.
- **C — genuinely requires a private companion:** only after showing why R/A/N cannot carry that fact truthfully. **No C datum is established yet.**

| Current carrier datum | Classification / proposed replacement | Probe obligation |
| --- | --- | --- |
| `WIRE-PLAN/B` header, record tags, `DATA`, Base64/null encoding and deterministic record order | Transport only; no semantic R/A/N/C datum to reproduce | Do not transplant a carrier protocol into the reflective generator. Deterministic schema output does not require deterministic discovery order. |
| `model` identity | R: model type referenced by public Builder/Factory generic contracts | Resolve without initialization; no `Class.forName(Model)` discovery or Model field inspection. Do not rely only on stripping `_DSL`. |
| `builder` identity | R, with A role check through existing `KlumGenerated` namespace/Builder tags | Exact class/loader identity; separately compiled parent and nested contracts. |
| Entry `externalName` | N where original field rename/ignore is not retained on public methods; R/A where an exact authored declaration already retains the literal | `JsonIgnore(true)` precedes nonempty `JsonProperty`, then identity; false/empty cases, inherited collisions. Prefer retaining minimal original facts and deriving the selected name once, not storing a second name catalog. |
| Entry `nameOrigin` (`IDENTITY`, `JSON_PROPERTY`; ignore is a rejection) | A for exact retained ignore/rename; N for missing field-to-public attribution | Explain origin in diagnostics; don't take a copied annotation on a different overload as source authority. No camel/kebab options added. |
| Entry `role`: authored `OPERATION` | A: retained `@Builder.Method`, plus R signature eligibility | Exclude query/lifecycle/helpers; exactly-one-payload admissibility and overload ambiguity are decisions applied to reflected facts, not visibility heuristics. |
| Entry `role`: `PROPERTY`, `OWNED_OBJECT`, `KEYED_OWNED_MAP` | N for source role/ownership and group provenance, with R type shape and A generated relationship hints | Distinguish composition, LINK/OPTIONAL_LINK, field container and authored whole-container operation without guessing from return type or singular/plural names. |
| Entry `source` | R for a public authored operation's declaring type/signature; N for original Schema field owner/member or a moved operation's original declaration | Source identity survives rename, projection and separate compilation; no source path, line table, Model-field scan or Javadoc parsing required. |
| `operation.declaringBuilder` | R | Traverse declared public interfaces with declaring-owner identity; deduplicate true diamonds, retain conflicting declarations. |
| `operation.name` | R | Reflect exact methods; field membership/group selection is the separate N fact below. |
| `operation.descriptorGroup` | R for exact parameter/return descriptors; N only for compiler-selected group membership/population provenance | Broad plural overload groups, `@Field.members` and authored overrides show why signature existence alone does not select a population method. Do not duplicate all descriptors if an annotation on the selected method suffices. |
| Entry `mapKeyType` | R from selected public signature/generics; N only if generated projection erased its owning declaration's key type | Literal String instance/data keys; prove keyed creators versus ordinary data maps. |
| Entry `excludedChildKeyMember` | N if the original `@Key` member is absent from public contract metadata; A only where actually projected | Do not load/inspect the child Model's fields. Preserve exclusion and child-key-duplication diagnostics; no derived guess from factory parameter spelling. |
| Entry `fixedChildKey` | N for compiler-known fixedKeys Cluster provenance, only if extraction/diagnostics need it | Value is native field name, not external alias. It affects construction, not a schema discriminator or required child input; omit from generator state if projection needs no such fact. |
| Payload `kind`: scalar/enum/list/map | R for types and generic/container shape; N only for field-versus-operation origin already identified above | Field Set-family storage projects to arrays, while arbitrary Set operation arguments remain unsupported. No need to serialize a recursive type tree recoverable by reflection. |
| Payload `kind`: `NESTED_OBJECT` | R nested contract identity plus N owning relationship eligibility | Type reachability is not permission to admit dynamic selector overloads or abstract/polymorphic input. |
| Payload `type` (leaf/container/Model) | R | Generic substitution, primitive/boxed numeric types, raw/unresolved negatives and concrete referenced target; no defaultImpl inference. |
| Payload `builder` | R from public relationship/generic contract linkage, with A role hints | Prove exact nested contract mapping; annotate only a demonstrated lost association, not a duplicate type inventory. |
| Payload `keyType` | R from generic map type or selected keyed signature | Reject unresolved/non-String input keys; never transform literal data/instance keys. |
| Payload `item`, recursively | R from generic element/value types after inheritance resolution | Preserve nested List/Map payload as one argument, not spread. Erasure/missing bounds are explicit errors, not Object placeholders. |
| Rejection `source` | R for an existing public declaration, N for compiler-only/omitted source provenance | If no public member survives, test annotation on the declaring public type; an absent method alone does not prove C necessity. |
| Rejection `reason` | Derive from R/A/N for known eligibility rules; N for irreducible compile-time exclusion/collision fact | Keep actionable attribution without copying runtime prose as a generator protocol. If no truthful annotation target exists, bring the concrete case as a C candidate. |

Useful non-carrier inputs are **R/A**, not new enrichment: inherited public interfaces, `@Builder.Method`/Query/Input/Result,
`@KlumGenerated` tags, `@AnnoDoc` strings and runtime-retained parameter annotations such as `@DelegatesTo` where present.
`DefaultValues` or other closure-valued annotations are declarations, never permission to instantiate/evaluate their code.
Enum constants can be identified by reflected `Field.isEnumConstant()` / field names without reading static values;
`getEnumConstants`, `values`, `valueOf`, `toString` and annotation enum-value access that initializes user enums are forbidden.
The probe must test actual annotation access paths, not assume all annotation proxies are harmless.

## Next required slice — SCHEMA-R reflection versus enrichment

**Historical probe specification, completed in merged #945.** The final checkpoint above supersedes its next-slice and
carrier-preservation instructions; the measured evidence and later #946/#947 migration are prerequisites already met.

This is the next bounded #529 assignment **before any production generator resumes**. SCHEMA-1 stays paused; do not
resume it, edit its branch, reuse its commits as approved architecture, or alter WIRE runtime/carrier implementation.
SCHEMA-R is test/probe evidence only and makes no generated contract change.

Use one separately compiled API -> Schema fixture with public Builder/Factory contracts and real `@AnnoDoc`. Reuse
[historical `SchemaTracerTest`](https://github.com/klum-dsl/klum-ast/blob/de6337d434b3f46b9f0ccce478bf9c150010f8e9/klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/SchemaTracerTest.groovy) fixture shapes and
[`GeneratedDslSupportSpec`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupportSpec.groovy)
where useful; don't promote the ASM carrier reader. Give new executable classes a `Test` suffix and `@Issue("529")`.

1. Load only explicit root/reachable public contracts with initialization disabled in a fresh isolated loader/JVM containing
   resolved API/Schema, Groovy, Klum runtime and annotation dependencies. No source/mirror/compiler AST inputs. Record which
   referenced types the JVM resolves; do not deliberately load or inspect hidden Schema, Builder or Factory implementations.
2. Extract signatures, generic parents/container shape, existing annotations and `@AnnoDoc`. Include one inherited member,
   scalar/enum, concrete/keyed relationship, authored single Map payload, rename/ignore and a field population override.
   Exercise fixed-key/omitted-source cases only as necessary to settle inventory rows, not a broad new language matrix.
3. Compare every datum above with current compiler/carrier facts **in the test harness only**. For each R/A row, cite the
   reflective operation/annotation and result; for each N row, identify the smallest missing compiler fact and truthful
   annotation target. Prototype candidate annotations only in a disposable fixture if necessary, not the production emitter.
   Record facts unnecessary for static projection as omitted, with the reason. A full normalized plan duplicate fails the gate.
4. Trap initialization/constructors/factories/defaults/lifecycle/converters/closures and enum code on success and failure.
   Verify the traps with deliberate execution controls in a separate phase. Cover missing dependency/annotation classes,
   inherited docs and loader version collisions with actionable errors. Qualify Groovy 3/4/5; public named-module access
   on Groovy 4/5 is required before claiming that path, without new opens/exports/flags.
5. Report an inventory with measured R/A/N/C outcomes, projected-versus-execution-only facts, annotation retention/target
   requirements, and the plugin-private placement assessment. If C is necessary, show the smallest failing fixture and why
   type-level/method-level annotations cannot preserve it; return that concrete decision before production work.

**Acceptance:** every datum accounted for; no Schema execution, hidden implementation inspection or source fallback;
truthful minimal enrichment proposed; dependency/isolation/docs risks evidenced. No production generator, module, task,
carrier format or #932 change. Keep the probe and its evidence in one reasoned commit, followed only by qualification fixes
if useful. Do not make a separately published descriptor a prerequisite. Successful evidence returns for explicit slice
selection; it never resumes SCHEMA-1 automatically.

## Justified production sequence after SCHEMA-R

All production slices require a separate assignment and the completed inventory. Names below describe outcomes, not new
public modules/APIs or a mandatory chain of separately published PRs.

| Slice / owner | Reasoned commit boundary | Executable acceptance / documentation |
| --- | --- | --- |
| Minimal enrichment — #529, only measured N/C facts | Add the smallest runtime-retained generated annotation(s) with emitter and attribution tests together. A companion requires the explicit concrete justification above. Skip this slice if R/A suffice. Preserve WIRE consumers and public signatures. | Separate-artifact inheritance, role/group/source fidelity, missing metadata behavior, ABI/linkage and Groovy 3/4/5. Completed separately by WIRE-REPLACE/WIRE-REMOVE; no additional migration is needed. Record annotation ABI consequences and any recompile requirement. |
| Reflective projection — #529 | A usable vertical path inside the existing Gradle-plugin implementation, depending on runtime as justified, with a private extraction/projection seam. Keep extraction and real Draft 2020-12 output/validator tests together; no public generator module/API or CLI by default. | Silent traps; reusable/recursive `$defs` and escaped `$ref`; all admitted shapes, exact names and deterministic errors. Pinned metaschema/instance validation. Interpreter comparison only as an optional overlapping test corpus, never a production dependency. |
| Gradle delivery and qualification — #529 | Wire ordinary task outputs from explicit root contract names and isolated resolved classpath; commit cache/invalidation/failure behavior with TestKit evidence, then consumer/docs qualification. Can join projection if that is the smallest useful path. | Clean/repeat/up-to-date, dependency/docs changes, root/member deletion, relocation/build cache, configuration cache, failure/stale-output cleanup. One real ScHelm root/output through consumer-owned acceptance. Add documentary `@Tag("documentary")`/`@See`, user page/sidebar, migration navigation and `CHANGES.md` with actual delivery. |

Placement assessment: the Schema plugin already owns compiled-contract build integration; a private implementation avoids
an additional published artifact and keeps the extraction/projection seam testable. Its runtime dependency provides
contract/annotation types; JSON/rendering libraries remain plugin implementation dependencies. The probe must qualify
plugin versus inspected-Schema versions and loader ownership. Only a demonstrated isolation/distribution requirement
justifies proposing a separate module. Do not add any module, dependency or plugin configuration in this revision.

## Conservative projection and compatibility acceptance

Keep these useful constraints from SCHEMA-0; their binary source is now reflection plus minimal enrichment:

| Concern | Required assertion |
| --- | --- |
| Domain surface | Object `properties` contains only admitted external names; close domain objects, not data maps. All input fields remain optional; no inferred `required`, default evaluation or permissive placeholder for an unknown admitted payload. |
| Scalars and containers | String/boolean; exact straightforward byte/short/int/long bounds; mathematical BigInteger/BigDecimal. Float/double use accepted `number` approximation without Java conversion/rounding guarantees. Supported field collections project to arrays without inferred uniqueness/sorting; ordinary List/Map operations keep one whole payload. |
| Owned/keyed shape | Concrete child references; outer keyed-map keys are literal instance data; child key is excluded. No inline LINK/OPTIONAL_LINK, keyMapping, abstract/polymorphic guessing or subtype scan. Construction-only fixed keys do not become external constants. |
| Names and identity | Ignore, literal rename, identity fallback; deterministic inherited/overload collisions. Flatten inherited admitted members before closure; fully qualified public Builder binary names distinguish same-simple-name definitions. Recursion reuses `$ref`; reference escaping covers punctuation/non-ASCII. |
| Documentation | Read exact `@AnnoDoc`; preserve declaring source and inherited attribution. Missing prose is informational; missing required enrichment or linkage is actionable failure. Never infer grammar or defaults from prose. |
| Honest limits | Root `$comment` states structural coverage, numeric approximation and no binding/execution/lifecycle/materialization/completed-validation guarantee. No ordering, patch, round-trip, Jackson-codec or context-injection claim. |
| Failure | Diagnose root/declaring public contract/name/shape/type and missing metadata/dependency. Do not leave stale success files; no silent dropping of an admitted unprojectable member. Known excluded members remain absent with reasons. |
| Independence | Generator works without calling WIRE-1; intentional runtime comparison occurs only in separate tests. Generated signatures, ownership/lifecycle and Model/Template serialization retain behavior. |

### Risk and compatibility analysis

| Risk | Control / required evidence |
| --- | --- |
| Class loading initializes user code or inspects implementation detail | Explicit public contract roots, `Class.forName(name, false, loader)` or equivalent, no static field reads/invocation/service discovery. Generic/annotation access may resolve Model/API dependencies without initialization; trace that boundary. Trap enum and annotation access as well as constructors/defaults. |
| Incomplete/mixed classpath yields false facts | Gradle resolves declared complete dependencies before extraction. Isolated loader/worker owns consistent Klum/Groovy/annotation versions and exact type identity; close loaders, reject linkage failures, do not borrow daemon classes. Test separate artifacts and missing/conflicting dependencies. |
| Retention/docs loss or false attribution | Probe packaged runtime annotations and `@AnnoDoc`, including inherited declarations. New annotations specify retention/targets. No raw-Javadoc/source/mirror fallback; missing prose differs from missing semantic metadata. |
| Incorrect Gradle incrementality | Declare root names/options, tool implementation and full resolved classpath including annotation/docs bytes; conservative classpath fingerprinting is acceptable initially. No resolution/class loading at configuration time or `Class`/loader objects in cache state. Test byte changes, deletion, relocation, build/configuration cache and stale cleanup. |
| Generated contract compatibility | Preserve public signatures and existing consumers. New metadata is generated ABI with tested older/missing/newer behavior; do not promise support for artifacts lacking required facts. The removed unreleased carrier has no compatibility promise; missing required public enrichment needs recompile guidance. Compiler changes later require Groovy 3/4/5 and relevant named-module/Java/static-Groovy clients. |

There is no source or behavior change in this revision, so Groovy lanes are unnecessary. Source inspection and prior
reported tests establish only the baseline, not successful SCHEMA-R execution.

## Historical feasibility — not active architecture

### Executable feasibility proposal — SCHEMA-0/T

This anchor is retained for existing evidence links. The original A/B/C proposal is complete in merged #942:
A read explicit API/Schema JARs as data using ASM and twelve-token carriers; B emitted real Draft 2020-12 with reusable
definitions; C compared representative structures with intentional WIRE-1 execution in a separate phase. See the
[dated evidence and reproduction commands](evidence/issue-529-schema-t.md). Its five accepted/twelve rejected inputs,
no-execution controls and validator approach are reusable. No claim of reflective extraction follows from that result.

### Production acceptance inventory — deferred from SCHEMA-0/T

This anchor also remains for historical evidence links. Useful determinism, inheritance/recursion, name/shape, numeric,
no-execution, Gradle failure/cache and consumer acceptance cases survive in the revised sequence above. Carrier decoder
hardening, exact runtime-reader equality and a standalone classfile reader are not generator architecture requirements.
The old instruction that successful SCHEMA-T must lead directly to production without reexamining enrichment is
superseded by the maintainer decision; SCHEMA-R is now mandatory. Old WIRE carrier evidence remains relevant to #932.

## Open maintainer decisions and delivery

**No unresolved maintainer decision blocks this revision or SCHEMA-R.** Return only evidence-backed questions after the
probe: a concrete C fact that cannot use annotations, or a concrete failure of plugin-private placement/isolation.
Do not reopen settled reflection, classpath, optional-interpreter, runtime-dependency or no-CLI boundaries. Deferred
polymorphic/extended language syntax belongs to a separately selected #932 discussion, not this plan.

Validate Markdown structure/fences, local links/anchors, source/PR references, requirement coverage and `git diff --check`.
No Groovy lanes or source-derived executable proof are added. Review Standards and Specification explicitly; preserve
historical test attribution. Commit/publish this documentation revision as a draft PR, then update #529 with the rationale,
commit/PR links and the new next slice. #932 needs no issue-body/runtime change or milestone update.
Tracker impact: **Related: #529; Related: #932**; both remain open. Curation/release impact: **none**.
Report worktree, branch, exact base/head, review/validation and live PR/check status. Keep `(PR:open)`, request Hive
reconciliation, and leave SCHEMA-1 paused. This assignment does not merge, archive or start SCHEMA-R/production work.

### Validation of this revision — 2026-10-23

Four changed Markdown files only. Balanced fences, 51 local link targets, 11 outgoing fragments, nine inbound fragments
and `git diff --check` pass. Local Standards self-review covers document-only scope, domain vocabulary, historical
attribution and issue/publication boundaries; Specification self-review accounts for every maintainer requirement,
including all carrier fields and the mandatory next probe. Neither review has an outstanding finding; no independent
agent review is claimed. No executable proof, Groovy lane, generator or ScHelm acceptance was run for this revision.
Refreshed `origin/master` still equals the reviewed base above; no rebase is needed. Delivery authority checks: Git/SSH
branch publication **authorized**; GitHub CLI repository writes **authorized**. The explicit assignment authorizes the
draft PR and bounded #529 revision rationale; it does not authorize #932 or milestone changes.
