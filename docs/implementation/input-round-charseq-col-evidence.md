# CHARSEQ-COL — exact CharSequence container dispatch

Related: #952. Repository inventory for #1015 is updated by this feature; neither GitHub issue is mutated.
Authority: explicit maintainer assignment, [ADR 0032](../adr/0032-builder-oriented-structured-input.md),
[4.2 plan](input-round-plan-4-2.md). R1 probe: `b2923977354bd9f1d1ce0e194e3a42f5a3fc08e0`.

Base: refreshed `origin/master` at `6b62920f91afbaf4ba91ff927eb9d2fa0a04ba6b` (merged SET-SEQ #1035).
Worktree: `/Users/stephan/.codex/worktrees/4172/klum-ast`.
Dedicated branch: `codex/952-charseq-col`.

## Contract and compatibility

One exact generated public CharSequence List-element or literal String-keyed Map-value converter accepts a raw Struct
String. Ordinary generated Groovy and Struct calls honor that same recorded signature. Protected/unprojected
`parse(String)` cannot win through runtime redispatch. Literal String and ordinary GString arguments retain identity
through `parse(CharSequence)`; Struct still rejects GString and mutable/arbitrary CharSequence inputs. This is the
explicitly accepted ordinary-call compatibility correction, analogous to scalar S3b. Competing public String/CharSequence
routes retain rejection without a new specificity/selection policy. CharSequence Set routes stay excluded.

Compiler association/count/descriptor metadata is reused. Map payload classification reads actual value parameter 1,
independently of String key parameter 0. New protected generated runtime hooks use direct typed linkage and the existing
PojoWrapper signature hint around the original argument. They reuse ordinary conversion/population; there is no converter
discovery, generic coercion, alternate construction engine or new public author vocabulary. Match compiler/runtime/plugin
and recompile participating Schema hierarchies; no old-binary automatic admission or mixed-development-artifact promise.

Schema output is structural String items / String additionalProperties only. C1 constraints, general grammar, owned DSL
values, key mapping and specialized container declarations are unchanged. No user code executes during inspection.

## Regression evidence

- `StructuredContainerCharSequenceTest`: natural caller-parsed YAML, defaults, replacement, exact literal/GString
  identity through protected-overload traps, strict input/index/escaped-key attribution, original converter cause,
  partial effects and Template definition/recipient lifecycle. Documentary method:
  `natural duration strings populate Lists and literal keyed Maps`, with reciprocal current user documentation.
- `StructuredListConverterTest` / `StructuredMapConverterTest`: renewed exact CharSequence admission plus both String
  and CharSequence binary inheritance/shadowing and corruption matrices; retained competing-route and strict boundaries.
- `StructuredSetConverterTest` / `StructuredCharSequenceTest`: retained String Set exclusion boundary and scalar S3b.
- `JpmsPackageBoundaryTest`: named-module Java/static Groovy consumers now exercise natural CharSequence List/Map
  routes, with ASM asserting direct hook linkage rather than invokedynamic.
- `CompiledConverterJsonSchemaTest`: source-free packaged G3/G4/G5 List/Map String output and no-initialization traps;
  retained CharSequence Set rejection. `validate_converter_schema.py` validates actual emitted grammar independently.

Focused Groovy 3 converter qualification passes **201 cases**, zero failures/errors/skips (58 List, 69 Map, 53 Set,
21 container CharSequence). Shared compiler/runtime linkage selects root `check`, including G3/G4/G5, binary/JPMS,
plugin producers, licenses, lane isolation and documentation renderer. Root `check` passed in **31m 49s**, 140 tasks (79 executed, 61 up to date). Exactly two read-only local review children completed: Standards reports zero hard
violations; Specification reports zero substantive findings. The shared documentation cross-reference correction is
applied. Standards' optional extraction of the existing duplicated ASM hook list is deferred: this narrow change extends
the established check without changing its test infrastructure.
Hosted final-head CI and SonarCloud remain authoritative and separate from local evidence.

## Delivery boundary

No merge, issue closure/milestone update, release or archive action is authorized. Exactly two read-only local reviews
(Standards and Specification) are required before delivery. The reviewed implementation follows the established S3b hook pattern and has no unresolved product or compatibility
decision. Local validation/reviews and required documentation are complete; the authorized next delivery is a draft PR. Hive retains final delivery/archive reconciliation.

## Commands

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :klum-ast:test --tests '*StructuredContainerCharSequenceTest' --tests '*StructuredListConverterTest' --tests '*StructuredMapConverterTest' --tests '*StructuredSetConverterTest' --console=plain --no-parallel --max-workers=2
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --no-parallel --max-workers=2 --console=plain
```

The compiler-module lanes passed 3291 / 3293 / 3293 cases for G3/G4/G5 respectively, with zero failures/errors and
16 / 15 / 15 existing skips. The 21 new exact-dispatch container cases have no skips in any lane. Both compatibility
lanes pass their five JPMS/module-boundary cases. The complete plugin suite passed **669 cases**, zero failures/errors/skips. Root qualification includes binary and
module-path consumers, plugin validation, licenses, lane isolation and documentation rendering. No new test is suppressed.

Independent actual-output validation passes on all three emitted converter schemas using jsonschema 4.23.0, Node
v26.12.0 and Java 17: 28 JDK, 270 ECMA/Draft C1, 270 structural scalar String, 234 List, 243 Map and 213 ordered Set
checks. G3/G4/G5 emitted SHA-256 is identically
`c4f9665513a654435aa632982e21238db2be5fcc8414f40b7e6ec241b8beac30`.

```shell
/private/tmp/1026-schema-venv/bin/python klum-ast-gradle-plugin/src/test/resources/validate_converter_schema.py klum-ast-gradle-plugin/build/schema-validation /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java
```

Markdown fences and 292 relative links passed, including existing extensionless historical page links. New documentary
`@Issue`, `@Tag('documentary')` and `@See` link to the current section. Existing page navigation is retained.

## Final local qualification

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 | 4336 | 0 / 0 | 17 |
| Groovy 4 | 3645 | 0 / 0 | 16 |
| Groovy 5 | 3645 | 0 / 0 | 16 |

These totals are read from the final JUnit XML reports. No retry, test weakening or suppressed new case was needed.
The initial sandbox attempts could not write external Git/Gradle metadata; authorized escalated commands succeeded.
No automatic approval-review rejection occurred. No local Sonar upload was performed. Hosted CI/SonarCloud must inspect
the eventual final PR head; local success is separate from hosted qualification, merge, release and consumer adoption.

Implementation/test commit: `2388d8a47933cf583edc1624695c3d70978d0764`. The complete root run exercised these exact production/test inputs; only
documentation and evidence are committed afterward. History is reviewed as two focused reasoning steps: dispatch and
regression behavior, then current contract/migration/inventory and qualification evidence. No reviewed commit is rewritten.
Delivery preflight: GitHub CLI repository write is **authorized**; branch push is checked by dry run before publication.
