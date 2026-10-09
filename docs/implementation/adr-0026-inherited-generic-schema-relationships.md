# ADR 0026 implementation plan: inherited generic Schema relationships

Authority: [canonical #180](https://github.com/klum-dsl/klum-ast/issues/180) and the proposed
[ADR 0026 extension](../adr/0026-inherited-generic-schema-relationships.md) of ADR 0005.

Classification: **B — extend an accepted ADR**. Scope confirmed; mechanics and implementation unqualified.
4.1 candidate, gated by ADR acceptance and GEN-0 binary evidence; not a release blocker.
Planning base: `7941dd087c704c4f6221f791934e685e493c3cc1` (2026-10-04).

## Confirmed current behavior and unproven paths

| Inspected seam | Fact | Limit / risk to measure |
| --- | --- | --- |
| `GeneratedDslSupport.copyTypeParameters`, constructor, `configureBuilderImplementation`, `addParentBuilderInterface` | Public/hidden Builders copy Model parameters, append SELF and thread superclass arguments | Does not establish inherited Model-variable-to-child-Builder representation or specialized creators/delegates |
| `DSLASTTransformation.createFieldDSLMethods`, `getBuilderFieldType`, relationship creators | Generation visits declaration fields; Builder storage projects DSL values; default creators pass a target class to runtime | Downstream regeneration cannot be assumed; bound-derived targets/class constants need a binary probe |
| `GeneratedDslSupport.publicType`, projected methods/delegates | Projects hidden types and generic arguments into public support interfaces | Projection alone is not contextual inherited relationship substitution |
| `DSLASTTransformation.overrideFactoryMethods` | Existing factory method substitution uses Groovy generics helpers | Reuse candidate, not a proven inherited-field solution or #183 contract |
| `DslHelper.getElementType`, `getClassFromType`, `isRelationship`; `InternalKlumBuilder` child/container paths | Reflection reads declaring generic types; class conversion handles Class/WildcardType/ParameterizedType, not TypeVariable; nested creators use a selected class | Generic containers can reach `Unknown Type`; erased singles can select/classify a bound. Source-derived risks, not an executed #180 failure report |
| `GeneratedDslSupportSpec`, `BuilderFieldGenericTypeTest`, `InheritanceSpec` | Cover SELF inheritance, concrete relationships, simple container generics, public consumers and mirrors | These observations do not prove a separately compiled generic Schema library plus downstream specialization |

Source anchors: [compiler transform](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java),
[public support generator](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java),
[runtime reflection](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/DslHelper.java),
[Builder runtime](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java).

## Affected modules and compatibility

- **klum-ast:** inherited context, relationship detection/projection, storage/accessors, creator targets, public
  Builder/factory contracts, closure metadata, Model getters and materialization hooks.
- **klum-ast-runtime:** contextual field resolution/creation checks if GEN-0 requires it; reuse ownership/materialization.
  New emitted calls respect `runtime.generated`, never `runtime.internal` linkage.
- **klum-ast-annotations:** narrowly scoped generated metadata only if the binary tracer proves signatures insufficient;
  no Schema-author hint is assumed.
- **klum-ast-gradle-plugin / AnnoDocimal:** verify specialized bytecode mirrors and existing refresh/isolation; change
  producers only for a demonstrated gap, without a new IDE transport.
- **Jackson / Bean Validation:** inventory consumers if shared property metadata changes, and run adapter regressions;
  no generic importer or wire-format API is introduced.

Preserve non-generic signatures/source behavior, one leaf SELF, container snapshots/keys, single-session ownership,
LINK/OPTIONAL_LINK semantics, lifecycle order, immutable completed Models and no retained Builders. Resolved named abstract
DSL targets keep existing explicit/default/rejection rules; raw/unresolved instantiable specializations fail actionably.
Abstract generic declarations remain reusable. New map-key generics, nested containers, arbitrary wildcard APIs and
caller-selected generic roots are excluded. Recompile/republish the generic base with the introducing compiler if needed,
then recompile downstream Schemas/consumers; no older-compiler/newer-runtime guarantee. Keep unrelated stable 4.x ABI.

## Tracer slices and reasoned commits

All slices belong to #180; these identifiers are not newly created issues. Use dedicated issue branches and focused green
commits. Keep production changes with driving tests. New tests use the `Test` suffix and `@Issue('180')`, with explicit
compile-success assertions. Do not suppress a matrix dimension to claim acceptance. `@PendingFeature` is appropriate only
for a confirmed deliberately deferred contract, with an actionable reason, not as GEN-0 success evidence.

### GEN-0 — Binary generic-library vertical tracer (hard admission gate)

Compile the ADR example into `generic-schema-base.jar`, containing `Environment`, `EnvironmentProvider<T>` and an abstract
`RegionalProvider<U extends Environment> extends EnvironmentProvider<U>`. Compile `OrderEnvironment`, a direct leaf and
a leaf through `RegionalProvider<OrderEnvironment>` in a fresh downstream compiler with only that JAR. Base source,
live base ClassNodes and mirrors are unavailable. Compile consumers in a third phase against base/leaf binaries.
Runtime uses fresh loaders without compiler metadata. Source co-compilation is a regression control, never primary proof.

Prefer `InheritedGenericSchemaRelationshipsTest` using
[AbstractDSLSpec](../../klum-ast/src/testFixtures/groovy/com/blackbuild/klum/ast/AbstractDSLSpec.groovy) and the Java/mirror
seams in [GeneratedDslSupportSpec](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupportSpec.groovy).
[ScenariosTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ScenariosTest.groovy) and
[src/test/scenarios](../../klum-ast/src/test/scenarios/README.md) offer lexical multi-compilation organization, but its
shared incremental loader alone cannot prove JAR-only consumption. Extend a narrow fixture with packaging/fresh loaders
or keep the binary test separate; no broad harness refactor is required.

GEN-0 tests consumer-visible effective typing, not mandatory physical leaf redeclaration. For completed Models, ordinary
generic inheritance is an accepted encoding when base/leaf class-file Signatures and inheritance substitution express the
exact single/Collection/Map types. Require Java and static Groovy assignments without unchecked casts, correct concrete
runtime values and no duplicate Model storage. Do not generate specialized leaf getters/bridges solely to flatten generic
members. A physical Model override is acceptable only if the chosen mechanism requires it and the tracer proves it truthful
and compatibility-safe; physically declared leaf getters are not an acceptance criterion by themselves.

The Java binary consumer must compile and execute these assignments, whether the getters are inherited or leaf-declared:

```java
import java.util.List;
import java.util.Map;

// provider is an OrderEnvironmentProvider.
OrderEnvironment primary = provider.getPrimary();
List<OrderEnvironment> backups = provider.getBackups();
Map<String, OrderEnvironment> environments = provider.getEnvironments();
```

Static Groovy proves equivalent typed assignments; dynamic Groovy proves the corresponding concrete runtime values.
For each inspected member, the bytecode evidence records:

- its physical declaring class;
- erased descriptor;
- generic Signature;
- inheritance substitution in the downstream leaf context;
- any generated override/bridge, if present, with its necessity and compatibility evidence.

Correctness follows from the resulting public contract, with no preference for flattened leaf bytecode. The separate
Builder problem is representing `T -> OrderEnvironment -> OrderEnvironment_DSL.Builder<OrderEnvironment>` across the
JAR boundary for creators, storage/accessors, returns and delegates. Model substitution alone does not establish that
projection. GEN-0 keeps parameterized inheritance versus specialized overrides/bridges open for the Builder surface.

Mirror acceptance repeats consumer compilation/type checks against the mirror/public contract in an isolated fixture and
compares effective typing with binary consumers. Inherited specialization need not become invented leaf members; any
Builder overrides/bridges or added generic structure must be represented truthfully by the resulting public bytecode and
mirrors. This isolated test does not add mirrors to production or downstream Schema inputs.

**Every dimension below belongs in this first tracer, across single, Collection and Map relationships:**

| Dimension | Executable acceptance |
| --- | --- |
| Runtime creation | Closure-only keyed creators create OrderEnvironment, return its public Builder and attach it once; child-only orderQueue works; materialized subtype and Map keys are exact |
| Storage/accessors and bytecode | Construction reads expose effective public child Builder and List/Map types; completed reads expose exact effective Model types through inherited substitution or necessary overrides; record declaring class, erased descriptor, Signature, substitution and any override/bridge; no duplicate Builder or Model storage, no mandatory leaf getter generation |
| Factories/closure delegates | Leaf root return and active-session AsBuilder return are exact; child returns/parameters and DELEGATE_ONLY metadata name the specialized public contract, with no hidden implementation leaks |
| Java binary consumer | Compile/execute exact public Builder creator/accessor assignments and the completed-Model assignments above without unchecked casts, regardless of physical getter declaration; exercise public leaf Builder and correctly parameterized base view, with a closure callback where needed |
| Static Groovy binary consumer | Compile/execute child-only members in creator closures and exact effective Builder/Model/container assignments without unchecked casts for all shapes, allowing inherited generic Model getters; bound-only delegate cannot satisfy this positive control |
| Dynamic Groovy binary consumer | Execute normal DSL; assert delegate identity, child subtype values and completed results |
| Mirrors | Generate base/leaf Foo_DSL mirrors from class files; isolated consumer compilation observes the same effective types/returns/delegates as binary consumers; preserve truthful inheritance or required emitted Builder structure without invented flattened leaf members; never a production/downstream Schema input |
| Intermediate inheritance | Direct and RegionalProvider paths agree with renamed U; two distinct concrete specializations reject shared-cache contamination |
| Raw/unresolved diagnostics | Raw leaf and still-generic instantiable leaf fail with member/parameter/specialization guidance before child allocation/initializer side effects; abstract generic base still compiles |
| Resolved abstract targets | Existing default implementation works; without a default, existing explicit subtype works and unsupported implicit creation retains its rejection |
| Groovy 3/4/5 | Compile base, leaf and Groovy consumers anew per lane; run all dimensions under the single KlumAST production artifact; no cross-lane fixture output |
| Builder-first state | Composition shares root session, materializes once, and leaves a Model graph containing no Builders |

**Commits:** (1) minimum specialization mechanism and complete green binary tracer for all three shapes; (2) measured
mechanics, descriptors/delegates/mirrors/diagnostics in ADR 0026 and future
`docs/implementation/evidence/issue-180-gen0-binary-library.md`. A preparatory fixture extraction is separate only if
independently useful and green. Iterate focused Groovy 3, then run affected module baseline and Groovy 4/5 at the gate.
If any required dimension cannot pass within the accepted contract, stop expansion and report the failing dimension and
needed design revision. Map-only or source-only success does not admit GEN-1.

### GEN-1 — Complete the existing relationship surface

Extend proven substitution to closure-only/named-value/key-provider creators, dynamic Class selection, typed Factory
selection, Collection/Map factory forwarding, compatible selected-subtype returns and effective inherited Model accessor
typing; this does not require flattening Model getters onto the leaf.
Cover supported List/Set/sorted interfaces and Map/sorted Map values without new container forms. Exercise field/type
default implementations after substitution, protected inherited visibility, member naming, existing duplicate/reconfiguration
semantics, deeper intermediates and shadowed parameter names. Keep existing factory/converter behavior on these surfaces;
#183 generic converters remain separate.

**Acceptance:** binary and source controls agree across supported overloads; Java/static/dynamic consumers and
bytecode/mirrors expose identical types. Reuse keyed/unkeyed/default/factory/alternatives specs. Static wrong-child inputs
fail; dynamic wrong-child inputs fail before attachment.

**Commits:** (1) overload/forwarding implementation with exact consumer tests; (2) container/default/visibility edge cases
with regressions. Production and tests stay together; qualify final slice with affected Groovy 3/4/5 lanes.

### GEN-2 — Lifecycle, ownership, materialization and regression closure

Exercise specialized relationships through initializers, active Templates, PostCreate/PostApply, inherited Builder lifecycle
code, PostTree, materialization and completed validation. Use counters/identity to prove one outer lifecycle, once-only
initializers, preserved cyclic materialization and construction paths. Check composition rejection of completed Models,
LINK sealed wrappers, OPTIONAL_LINK per-entry ownership, cross-session/sealed Builder rejection and Template copy/recipes.

**Acceptance:** completed and Java-serialized/restored graphs have exact Model subtypes and no Builders/sessions or retained
construction machinery. Snapshots remain immutable; Template serialization constraints remain intact. Non-generic inheritance,
public-signature controls, simple-value generics and existing factory consumers pass without source changes. If shared
reflection metadata changes, run focused Jackson/Bean Validation regressions and affected suites; no new import contract.

**Commits:** (1) necessary state/metadata fixes with lifecycle/ownership regressions; (2) serialization/non-generic
compatibility closure. Do not change the ownership engine merely to pass typing tests. Qualify all affected lanes.

### GEN-3 — Documentary, migration and release qualification

Add `InheritedGenericSchemaRelationshipsDocumentaryTest`, `@Tag('documentary')`, `@Issue('180')` and absolute `@See`
for `docs/user/Inheritance.md#generic-schema-relationships`. Evolve the ADR example into the readable happy path;
the binary tracer remains separate-compilation authority.

Update `docs/user/Inheritance.md`, `Convenience-Factories.md`, `Behind-the-Curtain.md` (public Builder types),
`Builder-First-Migration.md`, `Migration.md` navigation and next-release `CHANGES.md`. Explain generic base/named leaf,
all shapes, intermediates, diagnostics, factories, Builder/Model boundary and generic-base recompilation. Update
`_Sidebar.md` only if pages change. Link issue, documentary feature and user example reciprocally, and add ADR/evidence/test
links to #180 without claiming acceptance or closing it before Hive reconciliation. Planning does not advertise support.

**Acceptance:** documentary example matches assertions; specialized Gradle mirror fixture proves refresh, stale cleanup,
archive/classpath/SourceSet/downstream exclusion. Run docs/link/diff checks, affected Groovy 3/4/5 suites and plugin scenarios;
inspect current-revision CI/SonarCloud, record exact commands/revisions/results and remaining gaps.

**Commits:** (1) documentary tests and aligned user/release docs; (2) measured ADR/evidence reconciliation if independently
useful. #180 stays open until every criterion is reconciled.

## Requirement-to-slice map

| Requirement / invariant | First proof | Completion |
| --- | --- | --- |
| Compiled generic base plus downstream specialization | GEN-0 JAR-only three-phase fixture | GEN-1 full surface |
| Exact single/Collection/Map runtime, Builder storage/returns, Model access, factories/delegates | GEN-0 full vertical matrix | GEN-1 overload/container/default matrix |
| Bytecode/mirrors and Java/static/dynamic consumers | GEN-0 binary consumers and metadata | GEN-1 surfaces; GEN-3 mirror isolation |
| Intermediate generic inheritance | GEN-0 renamed U and two-leaf control | GEN-1 deeper/shadowed parameters |
| Raw/unresolved diagnostic; resolved abstract/default rules | GEN-0 negative/default controls | GEN-1 entrypoints |
| Groovy 3/4/5 | GEN-0 lane-local binary matrix | Every slice; GEN-3 final qualification |
| Non-generic compatibility | GEN-0 controls | GEN-2 suites/signature inventory |
| Ownership/session/lifecycle/cycles/materialization; no Builders retained | GEN-0 state smoke | GEN-2 ownership/serialization |
| User docs, factory/Builder types, migration/release notes, recompilation | ADR policy now | GEN-3 documentary and release evidence |
| No generic root/helper API or #183 expansion | GEN-0 design review | Every slice scope review |

## Open mechanics, risks and evidence gates

The six [ADR mechanics](../adr/0026-inherited-generic-schema-relationships.md#mechanics-deliberately-left-to-the-tracer)
remain open: Model-to-Builder variable representation; Builder inheritance/override/bridge encoding; runtime target resolution;
persistent binary metadata; substitution identity/cache boundaries; diagnostic stage/guards. GEN-0 records selections and
rejected alternatives before expansion. Java container invariance can make a seemingly covariant Builder getter illegal;
a Model parameter alone cannot be assumed to name its concrete child Builder. Closure annotation class literals cannot
be treated as freely substitutable variables. Solve these together, without independent runtime/mirror contracts.

Other risks: shared placeholder mutation, erased fields misclassified as simple values, AST-only metadata lost downstream,
wrong defaults/key providers inferred from a bound, serialized construction state. Do not select extra public Builder type
parameters, Schema hints or runtime tokens without evidence and compatibility analysis. Bring any product/API policy
conflict beyond #180 back explicitly. Release deferral is allowed; reduced acceptance is not.

## Planning delivery and later implementation evidence

This documentation PR supplies a proposed decision extension, slice/acceptance map and bounded correction of #180's stale
index row. It changes no production behavior, fixture or user promise. Groovy lanes are not required; run local-link,
Markdown-structure and diff checks, standards/spec review and commit-history review before the authorized draft PR.
Tracker impact: `Related: #180`; no closing relationship, new issue or release-gate mutation.

Every GEN slice is pending. GEN-0's future evidence records compiler inputs, base JAR identity, KlumAST/Groovy revisions,
exact lane commands/results, public signatures/delegates, physical declaring classes, erased descriptors, inheritance
substitutions and any overrides/bridges with necessity/compatibility evidence, runtime subtype/state assertions, diagnostics,
mirror consumer equivalence/isolation and chosen mechanics. Documentation review does not establish ADR acceptance, tracer success,
feature delivery or a 4.1 release commitment.
