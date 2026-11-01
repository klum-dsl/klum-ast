# S2 — exact String operation selection

Related: [#952](https://github.com/klum-dsl/klum-ast/issues/952),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) and the
[4.2 implementation plan](input-round-plan-4-2.md).
Original stacked base: S1 head `038702ef6f256d26e5d1126d3f1d2163b1671584` / `codex/952-ordered-structured-input`.
Integrated base: merged master `2ddb18c61e31d595a97881cc6dcf834580037c2a` (S1 PR #1002).
Local branch: `codex/952-string-operation-selection`.

## Implementation boundary

`@Struct.Operation` is a runtime-retained, method-only author marker. The compiler retains it and an explicit
`GeneratedInput.Operation.authoritative` fact on the actual public projection. Every declaring Builder emits an explicit
`operationCount`, including zero. The byte reader checks count, role, presence, boolean shape and agreement with the
retained author marker; stripping both the role and its fact is still detected by the count. Development protocol 1 is
retained; participating compiler/runtime/plugin and Schema artifacts must match and be recompiled.

The shared resolver collects declarations without resolving name collisions early, removes overridden authored String
candidates, applies exclusions and the effective hierarchy naming profile, then selects one exact operation. Exactly one
eligible String mark wins; multiple eligible marks fail; without a mark a unique candidate is required. This uses no input
values, method enumeration preference or trial invocation. Previously rejected unmarked parent sources remain excluded
under identity naming; a marked group's surviving candidates remain available for exact descendant override resolution.
Renamed operations expose only their effective names. An
override owns its own names and intent, including exclusion through further descendants. Non-String override behavior
remains outside S2. Overriding an inherited ordinary String field's selected String signature excludes the parent's field
entry, so its old name and constraints cannot describe the descendant body. An override of an unrelated generated
Integer overload does not remove the unchanged String route. Existing C1 converter-override rejection remains intact.

A projected CharSequence operation counts as a String competitor but cannot be admitted for invocation in S2; one marked
String route can win. Unsupported roles, nonpublic declarations and omitted/colliding generated routes cannot gain
callability through the marker. Field placement is unavailable; a marked authored wrapper is the one-to-one escape.

The compiler's AST plan remains provisional before effective hierarchy/profile resolution. It does not fail compilation
merely because two marked native spellings coincide: a literal can remain unchanged while a native spelling is profiled.
The byte reader and resolver are the authoritative runtime/plugin seam. No new public descriptor API or module is added.

JSON Schema consumes the same selected entry. Only its String grammar, documentation and examples are emitted;
superseded field/converter constraints do not transfer to an authored wrapper. Inspection executes no Schema/domain code.
S1 order, first-failure/partial-state behavior, construction keys and the existing integrity fence remain unchanged.

No S3 origin expansion or CharSequence invocation, commands, scopes, Templates, body schemas, named arguments,
CollectionFactories, parsers, coercion or parameter validation are implemented.

## Acceptance evidence

| Requirement | Executable evidence |
| --- | --- |
| Unique unmarked and one marked winner; actual direct dispatch equality | `StructuredOperationSelectionTest`: unique control and documentary endpoint operation |
| Competing Strings, zero eligible, multiple marks, no runtime preference | Same test: ambiguous values, unsupported roles, multiple eligible marks, numeric fallback rejection |
| Exclusion, effective rename/profile and conflicting mark groups | Same test: excluded marked candidate, native-name rejection, effective profile collision and literal/native separation |
| Inheritance, exact overrides and shadowing | Same test: same-compilation inheritance/override/rename, ignored override through grandchild, ordinary String-field override/rename/exclusion, unrelated-overload control, separately compiled parent/descendant dispatch |
| String/CharSequence competition without S3b admission | Same test: unmarked competition rejection, marked String winner, CharSequence-only negative |
| No marker escape from missing/invalid public projection | Same test: private-method compilation rejection, unprojected Model/query methods; `CompiledConverterInputTest`: marked colliding converter source remains excluded |
| Required generated facts fail closed | Same test: missing/wrong count, missing/malformed/flipped intent, stripped author marker, stripped operation fact and jointly stripped role/fact |
| Marker target, retention and exact projection | Same test: method-only runtime target and public declaration author/generated facts |
| Java and static Groovy consumers; named module | `JpmsPackageBoundaryTest`: structured selection equals direct public Builder invocation in packaged consumers; G3 classpath and G4/G5 named modules |
| Selected schema only; C1 facts never transfer | `CompiledConverterJsonSchemaTest`: separate parent/child JARs across producer G3/G4/G5; String output, selected prose/examples, wrapper has no superseded Pattern/provenance; initialization/body traps remain untouched |
| C1 inheritance and descendant override controls | Retained `CompiledConverterInputTest` and packaged converter projection controls |

The documentary method carries `@Issue('952')`, `@Tag('documentary')` and `@See` for
[the user-facing selection contract](../user/Structured-Input.md#select-a-string-operation). New tests and materially
extended JPMS controls carry #952; existing C1 controls retain their original issue links.

## Qualification before integration

Focused Groovy 3 selection/naming/admission/reader controls passed. Packaged converter/schema controls passed, including
the G3/G4/G5 producer matrix. The C1 baseline suite passed with the collision control. The updated direct Java and static
Groovy consumer check passed on Groovy 4's named module path. An initial full run was stopped after exposing a fixture-only
Java Closure reflective-access failure; the fixture now overrides `Closure.call()` directly and needs no added module opens.
A private-method negative was corrected to assert its existing compilation-time rejection.

Exactly two read-only local children reviewed base through `576338ee8`: Standards and Specification. Standards requested
one documentary code block and braces for multiline branches; both are corrected. A targeted parent regression and
Specification follow-up identified an inherited ordinary String-field alias surviving a renamed/ignored descendant
override. The correction examines only the actual String signature, preserves unrelated overloads and C1 guards, and
adds runtime/binary/schema controls. Both review axes accepted that correction at `d87049fce`. The aggregate run then
exposed the existing `rejectedparent` fixture: previously excluded unmarked parent collisions must remain excluded. That
behavior is restored without pruning a marked group's surviving competitors; the original expected contract is unchanged.
The complete compiled-contract, naming, selection and C1 focused suites passed. Both review axes accepted the
compatibility correction at `4f68e1ec9`, with no remaining findings.

The next full `check` completed with one failure among 653 plugin cases: a handwritten wrong-Model fixture lacked the
new required `operationCount=0`, so it failed before reaching its intended Model-identity assertion. Commit `4c9075096`
adds that metadata without weakening the original assertion; its focused rerun passed. Both review axes accepted this
mechanical correction with no remaining findings.

A subsequent aggregate attempt was invalidated by editing documentation during execution: the outer build requested the
clean commit version while a nested fixture publication used the `.uncommitted` version. The missing fixture BOM was
confirmed directly. Draft documents were moved outside the checkout, the tree was restored clean, and validation was
restarted without changing source or assertions. Git state must remain stable during this build's nested fixture runs.
The final clean-tree aggregate rerun passed on 2026-11-01 at
`4c9075096b605f79ac55ca24cd1a31360fdbba71`, using JDK 17:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew check --continue --no-parallel --max-workers=2 --console=plain
```

Result: **BUILD SUCCESSFUL**, 13m 25s; 140 actionable tasks (29 executed, 111 up-to-date). The completed aggregate reused
unchanged passing lane results and reran the complete plugin suite. All JUnit XML reports were inspected: zero failures
and zero errors. Counts below include the existing skipped cases; S2 adds no ignored or pending test.

| Module | Groovy 3 baseline: cases / skipped | Groovy 4: cases / skipped | Groovy 5: cases / skipped |
| --- | ---: | ---: | ---: |
| Compiler | 2580 / 16 | 2582 / 15 | 2582 / 15 |
| Runtime | 73 / 1 | 73 / 1 | 73 / 1 |
| Jackson | 121 / 0 | 119 / 0 | 119 / 0 |
| Bean validation | 148 / 0 | 148 / 0 | 148 / 0 |
| Test support | 8 / 0 | 6 / 0 | 6 / 0 |
| Annotations | 20 / 0 | — | — |
| Gradle plugin | 653 / 0 | — | — |
| **Total** | **3603 / 17** | **2928 / 16** | **2928 / 16** |

Coverage includes all 40 String-selection cases per compiler lane, all 97 C1 converter cases per bean-validation lane,
packaged Java/static Groovy consumers (G3 classpath and G4/G5 JPMS), and the separate G3/G4/G5 producer matrix in the
plugin's selected-schema controls. Root `check` also passed license, test-lane isolation, plugin validation, coverage-report
and versioned-documentation-renderer checks. Local Markdown checks resolved 171 targets/anchors; `git diff --check`
passed. The final evidence/plan wording change does not alter the qualified production code, tests, fixtures or build inputs.

Local diagnostic logs were retained as `/tmp/s2-check-stable.log` and `/tmp/s2-final-results.json`; this committed record
contains the durable qualification summary. This is local evidence, not hosted CI or SonarCloud qualification.

## Original local commit sequence

Reviewed commits are preserved; corrections are additive. Every implementation commit uses the neutral relationship
`Related: #952`.

| Commit | Purpose |
| --- | --- |
| `f2c8f8922` | Select exact structured String operations from public Builder contracts. |
| `576338ee8` | Qualify packaged consumers and selected schema projection; document S2. |
| `d87049fce` | Remove stale inherited String-field routes after exact overrides; address initial local review. |
| `4f68e1ec9` | Preserve rejected unmarked parent sources without discarding marked-group competitors early. |
| `4c9075096` | Keep the wrong-Model negative fixture complete under the new metadata requirement. |

The final documentation-only commit completes this evidence and distinguishes the plan's dated baseline matrix from
its subsequent local S2 implementation. It changes no production code, test, fixture or build input.

## Integration onto merged S1

S1 PR #1002 merged on 2026-11-01 at `2ddb18c61e31d595a97881cc6dcf834580037c2a`. Hive explicitly resumed this worker
and authorized rebase, integration validation, local reviews, push and a draft PR against master, without merge.
The six original S2 commits rebased without conflicts; `git range-diff` reports all six patches unchanged. The later S1
production changes move the pointer helper into Traversal and extract the repeated cycle diagnostic; no S2 adjustment
was required. Rebased commits are `6093a7416`, `3b0a05a8b`, `7144823be`, `01d4b01cf`, `1057cb94c` and `ebbe55c64`.

Fresh aggregate validation and the same two local review axes are pending on the integrated branch. The earlier counts
above describe qualification before integration and are not a substitute for this gate. This section will record the
integrated result before publication.

## Delivery boundary

Tracker impact: **Related: #952**; the parent remains open. Curation impact: none.
Push and a draft PR against master are now authorized after integrated validation and local review. Merge, issue mutation,
local SonarCloud upload and Schelm dispatch remain outside this task. The coverage-bearing hosted CI/SonarCloud result
will be inspected for the published head. Hive owns subsequent delivery and archive reconciliation; this worker does not
self-archive.
