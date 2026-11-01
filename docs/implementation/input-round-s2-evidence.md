# S2 — exact String operation selection

Related: [#952](https://github.com/klum-dsl/klum-ast/issues/952),
[ADR 0032](../adr/0032-builder-oriented-structured-input.md) and the
[4.2 implementation plan](input-round-plan-4-2.md).
Base: S1 head `038702ef6f256d26e5d1126d3f1d2163b1671584` / `codex/952-ordered-structured-input`.
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

## Validation and local review

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
The complete compiled-contract, naming, selection and C1 focused suites passed. Reviews of this final compatibility
correction and full repository `check` are in progress. This record will be completed before
handoff; these focused results do not yet claim complete qualification.

## Delivery boundary

Local commits only. No push, PR, issue mutation, local SonarCloud upload or Schelm dispatch is authorized in this stacked
implementation. Tracker impact: **Related: #952**; the parent remains open. Curation impact: none.
The sole resume condition is **S1 PR #1002 merged, then rebase this branch onto master**. Hive owns resumption, later
publication and archive reconciliation. This worker does not self-archive.
