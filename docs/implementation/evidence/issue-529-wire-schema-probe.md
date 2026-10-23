# WIRE-SCHEMA-PROBE — compiled public contract compatibility bridge

Date: 2026-10-23. Owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Base: `2a28534638d3ad08aa59a63937b96f4a6d0bed29`, current master including merged
[SCHEMA-R #945](https://github.com/klum-dsl/klum-ast/pull/945).
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md),
[revised implementation plan](../adr-0031-schema-0.md), and the bounded WIRE-SCHEMA-PROBE assignment.
[#932](https://github.com/klum-dsl/klum-ast/issues/932) remains the language/runtime owner.

## Outcome and boundary

A package-private, **test-only** `CompiledBuilderSchema` facade normalizes public Builder/Factory declarations without
returning reflection objects, ASM objects, annotations or carrier records. The probe is complete; replacement parity
is **not achieved**. Its admission fixture reports **77 exact differences**: nine unavailable decisions for each of
eight fields, two authored operations whose local eligibility cannot prove global admission, and three invisible field
exclusions. These are repeated observations of a small set of missing compiler facts, not 77 proposed annotation members.
The [checked-in gap snapshot](../../../klum-ast/src/test/resources/wire-schema-probe/admission-gaps.tsv) preserves
expected and actual values, including unknown absence, rather than just counting or suppressing missing facts.

The probe consumes [SCHEMA-R's published inventory](issue-529-schema-r.md) before choosing this boundary. No generated
metadata is added. WIRE emission, runtime readers, dispatch, #932 grammar and historical carrier fixtures are unchanged.
No generator, plugin/dependency/task, public API, CLI, interpreter change or classpath/directory discovery is introduced.
`SCHEMA-1` stays paused. The byte-access experiment below is authorized probe evidence, not a production safety-strategy
selection or authorization to resume production.

## Facade and executable comparison

- [CompiledBuilderSchema](../../../klum-ast/src/test/java/com/blackbuild/klum/ast/schema/CompiledBuilderSchema.java)
  takes an isolated loader and exact public Builder/Factory names. Its immutable declaration values retain declaring
  owner, erased descriptor, generic parameters/return, enum names, literal names/origin, documentation, local operation
  eligibility and explicit default unavailability. An explicitly named Collection/Cluster scope uses the same boundary.
  `UNKNOWN` never means admitted; `ELIGIBLE` needs the complete field/name inventory before admission can be decided.
- [CompiledBuilderSchemaTest](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/CompiledBuilderSchemaTest.groovy)
  compares every entry/rejection from the oracle. Missing selected methods fail immediately. Deliberate invented
  descriptor, scalar-kind and nested-Builder damage controls prove that matching signatures cannot mask metadata drift. A complete-parity assertion deliberately fails with the expected/actual matrix;
  the test catches that failure and verifies useful attribution. The exact 77-row snapshot also detects changed expected
  values within already-known gaps. Additional fixtures check exclusions, population overrides, all eight field container
  declarations, custom Number rejection, fixed keys, child keys, LINK, keyMapping, docs and missing dependencies/resources.
- [StructuredInputContractFixtures](../../../klum-ast/src/testFixtures/groovy/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputContractFixtures.groovy)
  shares the existing WIRE admission, rejection, numeric, population-override and container source fixtures with their
  original regression tests. Their source and expectations are preserved. Additional fixed-key/annotation witnesses
  extend those comparisons without changing language or runtime behavior.

The legacy constant reader exists only inside the comparison harness. The facade cannot read carriers. Public signatures
and payload types are checked **using oracle-supplied field/group associations in the harness**; those equalities do not
prove that reflection independently recovered the missing associations. Neither a matching getter nor matching prose is
used as field-admission authority. Full descriptor-group selection remains a provenance gap even though every recorded
method is verified present with the exact owner, parameters and return.

A carrier transport detail is explicit: generated overload groups can mix JVM array names (`[Ljava.lang.Class;`) and
source-style names (`Child_DSL$Builder[]`). The harness records each converted spelling in `transportNormalizations`
and compares a single readable type spelling. It changes no type, operation, role or payload. An altered method signature
still fails the comparison. Carrier header/record/token counts and the full recursive payload are checked; unknown
record shapes fail rather than being ignored.

## Full parity matrix

**Equal** means a public fact is observed and compared. **Association gap** means the oracle supplies the source/group
join only for comparison. **Missing** means the facade reports unavailable/unknown. No missing row is accepted as parity.

| Carrier or consumer fact | Measured result | Consequence for replacement |
| --- | --- | --- |
| Header, DATA, record tags, Base64/null spelling and record ordering | Transport only; strict oracle checks | Do not reproduce the wire encoding in a facade or annotation API. |
| Model identity | Equal: public Builder self bound and Factory `One` return | No Model discovery, static `Create` read or suffix-based Model inference. |
| Builder identity | Equal: explicit public class identity | Resolve in the selected isolated loader. |
| Admission | Authored local eligibility observed; field/global admission missing | Complete compiler field/exclusion/name attribution is required before collision resolution and admission. |
| Entry external name | Exact authored annotation literals available; field names/aliases missing | Preserve field rename/ignore provenance, including false ignore and empty rename. |
| Entry name origin | Equal for surviving authored naming; missing for fields | Identity cannot be inferred merely from an unchanged-looking spelling. |
| Entry role | Authored marker observed; PROPERTY/OWNED_OBJECT/KEYED_OWNED_MAP missing | A callable child or container method does not prove ownership or field input. |
| Entry source | Exact authored declaring Model/descriptor; field association missing | Preserve original field member/owner association independently of an external alias. |
| Operation declaring Builder | Every recorded method owner is equal, including inherited API owner | Declaring identity survives separate compilation; field/group join still needs enrichment. |
| Operation name | Every selected name is present | Population selection remains missing; do not infer singular/plural membership. |
| Operation descriptor group | Every erased parameter and return is equal; selection/group membership is an association gap | Selected public input/population declaration and field association are required. Broad plural overrides are not authority. |
| Entry mapKeyType | String signature/generic type equal where oracle-associated; relationship meaning missing | Keep data-map keys and keyed instance keys literal; do not confuse selectors with payload maps. |
| Entry excludedChildKeyMember | Missing exact Key source identity; child getter exists without a key input setter | Runtime child-key diagnostics need attribution; generator-only omission of a non-admitted property is insufficient evidence for WIRE replacement. |
| Entry fixedChildKey | Missing: aliased `main-slot` has native key `primary` | Runtime needs the literal/native key; alias, creator signature and Cluster method spelling are insufficient authority. |
| Payload kind SCALAR/ENUM | Reflected type/enum field names equal after association | No enum value reads or initialization. |
| Payload kind LIST/MAP | Recursive generic shape equal; field-versus-authored origin missing | Preserve supported field storage versus whole-operation payload rules. |
| Payload kind NESTED_OBJECT | Exact public child Builder/self Model linkage equal after association; owning admission missing | No subtype/default-implementation or LINK inference. |
| Payload type | Exact scalar/enum/container/Model types equal | Primitive/wrapper and declared storage distinctions retained. |
| Payload builder | Exact nested generated Builder type in getter/creator generics equal | No duplicate nested-type inventory or root scan needed. |
| Payload keyType | Exact String generic arguments and selected signatures equal after association | Literal data/instance keys remain data. |
| Payload item recursively | Nested Map/List types equal; operation is one whole payload | No spreading a Map/List into arguments. |
| Rejection source | Surviving authored source equal; field/omitted-source provenance missing | A declaring-public-type annotation can retain otherwise invisible exclusions. |
| Rejection reason | Zero/multi/raw, direct/nested custom Number and unsupported container authored reasons equal; collisions and field reasons missing | Preserve compiler exclusion/collision categories; do not manufacture aliases from rejection prose. |
| Inheritance | Equal declaring owner/signatures in same-unit and separate API/Schema JARs | Complete field provenance remains necessary for inherited-name collisions. Diamond/conflict replacement qualification remains open. |
| Factory / Collection / Cluster shapes | Reflected exact public signatures/returns and explicit nested scope; no factory admission invented | WIRE-1 factory/control overloads remain outside input admission. No custom-factory language is introduced. |
| Excluded helpers/query/controls and silently omitted declarations | Queries/Factory scopes excluded; unclassified Builder declarations remain UNKNOWN; child key input absent | Visibility is not admission. Complete field/protected/Owner/transient provenance is still needed before a production migration. |
| Documentation | Actual exact `@AnnoDoc` operation text available through selective public bytes; source field attribution missing | Keep declaring-source prose and admission separate. Optional missing prose is not a semantic default. |
| Defaults/initializers/lifecycle | Not encoded by the legacy carrier; effective defaults explicitly unavailable | No parity can promise effective values that the oracle never carried. No initializer, closure or lifecycle evaluation. |
| Unsupported cases | All oracle rejection records retained in comparisons; explicit missing gaps remain visible | No permissive Object fallback or silent dropping of an unsupported declaration. |

The simpler shared WIRE population fixture retains its authored `values(Iterable)` marker, allowing an exact rejection
comparison. SCHEMA-R's larger fixture loses that marker when a generated projection occupies the signature. Both are
qualified; marker survival in one fixture cannot replace field/group provenance in general.

## Binary-only and no-execution evidence

The harness separately compiles API and Schema JARs, packages class files only and deletes compiler output directories.
The facade receives only exact root names and explicit JAR/runtime URLs in a fresh platform-parent loader. An additional
packaging control removes **every carrier** from both artifacts: extraction still works, preserving the same explicit
field-admission gaps. No source, IDE mirror, compiler AST or Schema implementation invocation is available to extraction.
Model/API/enum Classes may resolve incidentally from signatures and bounds, without becoming inspection targets.

The facade is Java to avoid Groovy metaclass inspection of inspected Class objects. During development, a Groovy facade
caused incidental hidden Builder type resolution even with static compilation; it was discarded. The final success path
checks that the root's hidden Builder/Factory and every WirePlan remain unloaded. No Model fields/methods are inspected.
Enum names use `isEnumConstant` fields without reading values.

Selective ASM access is limited to exact public contract `.class` resources, skipping code/debug/frames and ignoring
unrelated annotations. The safe-annotation witness keeps the SCHEMA-R user-enum hazard silent while reading real AnnoDoc,
Builder markers and literal naming. It does not resolve the unrelated enum at all. Default/initializer/operation traps
stay silent; the separate intentional initialization control fires. Missing inherited contracts or exact public contract
resources fail with root and corrective classpath context while traps stay silent. SCHEMA-R's separate positive controls
and named-module tests are rerun as supporting evidence; this facade does not claim its own named-module byte-access
qualification or production classloader/resource-origin hardening.

## Smallest proposed enrichment; no implementation authorization

SCHEMA-R established no companion-only fact, and this probe establishes none. Proposed method-level information is the
original field association and admitted field category on the compiler-selected public input/population declaration,
plus nontrivial original rename/ignore facts and only a needed getter/group association. Signatures, owner, recursive
types, enum names and documentation remain observed. Type-level information covers otherwise invisible source members
and exclusion/collision categories, including projections that lose an authored marker.

For a **shared WIRE replacement**, add the two runtime-required facts that generator-only projection can omit: the exact
child Key member association and the literal fixed native key where selected. Method/type annotation targets can retain
them truthfully; no recursive payload tree, descriptor arrays, complete name catalog or copied WIRE plan is justified.
This is a semantic fact proposal, not selected annotation names, retention defaults, encoding or ABI policy. Those choices,
production safe-annotation access, duplicate/diamond handling and missing-metadata/recompile behavior need the separately
authorized enrichment/migration work. Nothing here adds production annotations or changes twelve-token WIRE-PLAN/B.

## Exit criteria — WIRE-REPLACE

WIRE-REPLACE may change consumers only after a separate assignment and all of the following:

1. Approve and emit the measured minimal enrichment, including WIRE-required child/fixed-key facts, with explicit targets,
   retention, owner lookup, generated ABI and missing/older metadata policy. Preserve all public signatures and #932 rules.
2. Replace every `UNAVAILABLE`/global-eligibility gap required by an existing consumer with truthful facts. Run complete
   normalized entry, rejection and inheritance comparisons against the existing WIRE oracle; no expected-gap allowlist
   may count as parity. Include collisions, invisible exclusions, shadowed population, supported containers/numerics,
   nested/keyed ownership, fixed keys, keyMapping, abstract/unresolved negatives, and malformed/missing metadata.
3. Qualify public-only isolated loading, safe annotations, exact resource/loader identity, no execution on success/failure,
   cross-artifact inheritance and named modules, Groovy 3/4/5, and existing runtime dispatch/preflight/lifecycle fixtures.
4. Migrate each required consumer behind the internal facade without exposing a public protocol or changing runtime
   semantics. Keep the carrier as the temporary oracle until that migration and its own focused review are complete.

## Exit criteria — WIRE-REMOVE

Removal requires separately authorized work **after** WIRE-REPLACE, with no remaining production carrier dependency.
Audit compiler emission/support/verification (`StructuredInputAdmissionPlan`, `GeneratedDslSupport`,
`ModelVerifierTransformation`), runtime `StructuredInputPlan`/`StructuredInputSupport`, and test tooling/readers including
`SchemaTracerProjector`, `StructuredInputReaderProbe`, WIRE carrier/reader and JPMS fixtures. Either migrate each retained
consumer or explicitly retire its historical carrier experiment; preserve useful acceptance cases. Search repository and
packaging/linkage tests for carrier assumptions. Decide the supported compiled-artifact/recompile boundary explicitly;
do not silently discard a currently required artifact reader or invent version negotiation. Re-run the runtime/compiler,
Java/static/dynamic Groovy and JPMS compatibility corpus and synchronize ADR/evidence only after the consumer audit passes.
Neither replacement nor removal is authorized or declared ready by this probe. A JSON Schema generator is not an exit
criterion for the probe or permission to change #932 semantics.

## Validation and delivery

Groovy 3 focused corpus: **114 cases, zero failures/errors, one reasoned named-module skip** reserved by SCHEMA-R for
Groovy 4/5. The new probe contributes 32 cases. `licenseTest`, `licenseTestFixtures` and diff checks pass.
Groovy 4 and 5: **114 cases per lane, zero failures/errors/skips**, including SCHEMA-R's named modules. The final
seven-class corpus uses the same filters in each lane: `CompiledBuilderSchemaTest`, `SchemaReflectionTest`,
`StructuredInputAdmissionPlanTest`, `StructuredInputCollectionContractTest`, `StructuredInputPlanReaderTest`,
`StructuredInputDirectKeyBoundaryTest`, and `StructuredInputKeyMappingBoundaryTest`. `verifyTestLaneIsolation`, both
license tasks, twenty local Markdown link targets, balanced fences and diff checks pass. Initial independent review at `b74cf9720`: Standards **zero findings**; Specification found one rejection-precedence
mismatch for nested unsupported numerics. The additive correction adds five oracle cases and preserves compiler
numeric-before-container-before-map-key/shape rejection precedence. Four numeric cases were first reproduced failing;
all five and the complete 114-case corpus then passed in each lane. Final independent re-review at `ab9f519e022cfed79b5687d9ebe8caf7d27346f7`: Standards **zero findings**;
Specification **zero remaining findings**. Both reviewers checked the cumulative base-to-head change; neither independently
reran tests. The reviewed commits are preserved, followed only by this final documentation record. Local history review
confirms the probe, focused review correction and final evidence are separate reasoning steps with no unrelated changes.
Delivery capability audit: **Git transport authorized; GitHub CLI authorized**. No production-metadata decision is made
by publication of this evidence.

A broader intermediate Groovy 3 module run passed **2,291 cases, zero failures/errors, 16 existing skips** before the
last fifteen probe cases were added. The final tip is qualified by the 114-case corpus per lane above; no final full-module,
repository-wide root check or full-module compatibility run is claimed. Production code is unchanged. No user-visible feature, migration, documentary API or release
entry is introduced; current user pages/CHANGES and curation/release indexes need no edit. Tracker impact is **related
issue #529**, with **no curation impact**; #529/#932 and milestone states remain unchanged.
