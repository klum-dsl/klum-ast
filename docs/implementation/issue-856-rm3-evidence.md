# #856 RM-3: artifact consumer and JPMS qualification

Date: 2026-10-08

Status: Local qualification and review complete; draft delivery and CI are recorded by the attached PR/handoff.

Authority: the RM-3 assignment, [ADR 0027](../adr/0027-owning-relationship-metadata.md), and its
[implementation plan](adr-0027-owning-relationship-metadata.md). D1/D2/D3 (same-version only)/D4=4.1/D6 remain fixed.
D5 copied-container/alias/OPTIONAL_LINK copy repairs and RM-4 final acceptance remain separate gates.

Base: `df433708`, current master after merged [PR #862](https://github.com/klum-dsl/klum-ast/pull/862).
Branch: `codex/issue-856-rm3-compatibility`.
Worktree: `/Users/stephan/.codex/worktrees/2aab/klum-ast`.

## Qualification fixture

`RelationshipMetadataConsumerTest` carries class-level `@Issue('856')` and runs real Java 17 compiler/JVM processes.
It builds four artifacts in dependency order: annotation/readers, base Schema, leaf Schema, and writers. The annotation
and Java generic reader compile before the Groovy readers; the inherited callback and original private annotated field
are in the base Schema package. Base and leaf Schema sources/classes are removed after packaging, before downstream
compilation. Consumer runtime launches use only JARs and exclude the KlumAST compiler and upstream AST/compiler adapters.

Each successful launch constructs graphs through Java, static Groovy and dynamic Groovy writers, then exercises all
three reader kinds in inherited static PostTree callbacks and on completed Objects. Java and static Groovy compile exact
`KlumBuilderSupport<T>`, `Structure<T>`, `Optional<KlumSchemaRelationship>` and `Optional<Binding>` assignments. Java also
names the generated `Root_DSL.Factory`, `Root_DSL.Builder<Root>` and `Child_DSL.Builder<Child>` interfaces, uses the typed
factory relationship overload and setter, and adapts a generated Builder to `KlumBuilder<Child>` without internal types.

Assertions cover original declaring Class/member, concrete runtime annotation identity/value, missing annotation/root
Optional absence, independence from Owner values, equality/hash code across graphs, retained detached descriptors, and
both expired live-view operations. Generated Builder and Model operations contain no facade/metadata getters; the marker
stays zero-operation. Reflection checks public return/parameter type signatures for internal types. AnnoDocimal projects
base and leaf generated namespace mirrors and checks for metadata operation leakage. Projection uses its own combined
bytecode tree for sibling nested-class lookup; that tree never enters consumer compilation/runtime paths. Generator names
in `@KlumGenerated` annotation strings are implementation provenance, not public signature types.

Groovy 3 runs the classpath consumer and asserts the shipped runtime descriptor's deliberate Groovy-4/5 module identity.
Groovy 4/5 additionally compile and launch named probe/base/leaf/consumer modules. The annotation/readers module has no
opens. Base and leaf Schema packages use only qualified opens to the runtime required for construction; the writer's
anonymous Groovy closures use a qualified consumer-package open to Groovy. No add-reads/add-exports/add-opens/patch-module
launch workaround or change to production module descriptors is allowed by the harness.

## Acceptance mapping and compatibility limits

- A20: JAR-only separately compiled inherited Schema/annotation/Java/dynamic/static Groovy consumers.
- A21: private owning fields, exact annotation Classes and supported Groovy 4/5 named-module launches; existing
  `OwningSchemaRelationshipTest` supplies decisive same-named declaration/different-classloader identity checks.
- A22: public generated Factory/Builder linkage and facade generics, marker/Model/mirror/signature checks; existing
  generated-support and RM-2 Jackson tests retain their broader mirror and no-wire-metadata checks.
- A10/A11/A17: artifact live reads, retained descriptor and expired-view rejection supplement the full existing lifetime suite.

These checks prove separate source compilation and execution of the resulting consumer bytecode against the current
artifact set. They do not promise arbitrary historical Schema/runtime ABI combinations or serialization across versions.
RM-1/RM-2 did not change compiler source, generated support generation, or annotations artifact dependencies; this slice
adds qualification only. Same-version ordinary/Template serialization remains qualified by RM-2 and its regressions.

No production defect or behavioral repair is included. No selection algorithm, public Template inspection,
D5 repair, ownership extension, Builder operation or new compatibility promise follows from this fixture.

## Validation and review

Environment: Java 17.0.3, Gradle 8.14.4, Groovy 3.0.25 / 4.0.32 / 5.0.6 and matching Spock 2.4 lanes.
Executable qualification commit: `2d0e8c03`. Later documentation/evidence edits do not alter executable inputs.

Final explicitly filtered run on the committed executable sources: **2 cases per lane, zero failures/errors/skips**.
G3 has one artifact launch plus the deliberate module-identity boundary assertion; G4/G5 have classpath and named-module
artifact launches. Each artifact launch runs all three writers/readers. The exact per-task filter placement is intentional:
Gradle's task options apply to the preceding task, so one trailing filter must not be reported as filtering every lane.

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 \
  :klum-ast:test --tests '*RelationshipMetadataConsumerTest' \
  :klum-ast:groovy4Tests --tests '*RelationshipMetadataConsumerTest' \
  :klum-ast:groovy5Tests --tests '*RelationshipMetadataConsumerTest' --console=plain
```

The preceding regression invocation ran full G3/G4 AST suites (**1561 tests / 15 existing skips each**) and the selected
G5 metadata/lifetime/JPMS/generated-support fixtures (**88 cases / 0 skips**), all with zero failures/errors. It selected
RelationshipMetadataConsumerTest, OwningSchemaRelationshipTest, OwningRelationshipTracerTest, BuilderRelationshipLifetimeTest,
RelationshipCompositionTest, RelationshipTemplateCopyTest, RelationshipSerializationTest, Jpms*Test and GeneratedDslSupportSpec.
No new suppressed test is introduced. The final repository-wide check is recorded below.

### Standards review

Parallel read-only Standards review of `df433708...2d0e8c03`: **0 findings**. Imports, license headers, Test suffix,
class-level Issue linkage and explicit compile/process success assertions conform. Separate Java/static/dynamic reader
assertions deliberately retain independent dispatch evidence. No new user-visible DSL feature needs an additional example.

### Spec review

Parallel read-only Spec review of the same base/tip: **0 findings**. A20–A22 are satisfied by the JAR-only fixtures together
with decisive existing classloader/lifetime/generated-support/Jackson coverage. No scope expansion or historical ABI/
serialization promise is introduced. The broad validation result below supports the qualification claims.

Commit-history review retains one coherent qualification commit plus the dependent evidence/status synchronization.
No unrelated edits, production/API changes, build changes or new user-documentation navigation are included. Migration,
Completed Object Support and the existing CHANGES entry remove their stale binary/JPMS gate and state the qualified
behavior; no new behavioral entry or documentary example is introduced. CONTEXT and ADR/plan status are synchronized while retaining D5/RM-4 gates.

Repository-wide `check` passed on executable tip `2d0e8c03`: 130 tasks, 64 executed and 66 up-to-date, in 8m 40s.
It includes all configured suites, named-module/package checks, Java consumers, license checks, lane isolation, plugin
validation and documentation-renderer checks. Documentation-only status/evidence finalization afterward receives
relative Markdown page-link and `git diff --check` verification, without another Groovy run.

| Module | G3 tests / skips | G4 tests / skips | G5 tests / skips | Failures/errors |
| --- | ---: | ---: | ---: | --- |
| klum-ast | 1561 / 15 | 1561 / 15 | 1561 / 15 | 0 / 0 |
| klum-ast-annotations | 20 / 0 | — | — | 0 / 0 |
| klum-ast-runtime | 73 / 1 | 73 / 1 | 73 / 1 | 0 / 0 |
| klum-ast-jackson | 77 / 0 | 75 / 0 | 75 / 0 | 0 / 0 |
| klum-ast-bean-validation | 10 / 0 | 10 / 0 | 10 / 0 | 0 / 0 |
| klum-ast-test-support | 7 / 0 | 6 / 0 | 6 / 0 | 0 / 0 |
| klum-ast-gradle-plugin | 151 / 0 | — | — | 0 / 0 |

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 check --console=plain
git diff df433708...HEAD --check
```

All new fixture cases pass without skips in every lane. The 15 AST and one runtime skip per lane are pre-existing;
qualification adds no suppression. The final documentation review identified one stale binary/JPMS gate in Completed Object Support; it is synchronized
with Migration and CHANGES in a documentation-only follow-up. Spec found no issues. No actionable finding remains.

## Delivery and remaining work

Parent #856 remains open. Tracker relationship: Related: #856. This qualification leaves D5 and RM-4 acceptance open.
The Hive owns parent issue/release-curation reconciliation and eventual archive-safety reconciliation.
GitHub CLI repository publication authorization and SSH Git branch-push dry-run authorization were both verified; only
channel/category and safe outcomes are recorded, with no credentials or scopes. Draft delivery and live CI status will
be recorded by the attached PR and final handoff.
