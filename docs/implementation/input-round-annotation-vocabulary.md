# Structured-input annotation namespace audit

Date: 2026-10-31. Status: **Vocabulary proposal; no API migration accepted or implemented.**
Addition to [INPUT-ROUND-PLAN](input-round-plan-4-2.md) and
[ADR 0032](../adr/0032-builder-oriented-structured-input.md). Source baseline remains
`0c6f401b2a561a7767eca369f5aacab33dad0962`; preceding planning head was
`3ac43c82e54e2a054aa96a28249d8ff13f73113a`. This audit changes no runtime, compiler, annotations or release placement.

## Actual vocabulary and release exposure

All six author-facing annotations below are top-level types in `com.blackbuild.klum.ast`, declared in
[`klum-ast-annotations`](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast). Each is `@Documented` and has
RUNTIME retention. None of the six is marked deprecated. The source inventory is distinct from the proposed names.

| Actual annotation | Actual targets and values | Meaning retained by any consolidation | Introduction commit |
| --- | --- | --- | --- |
| `InputName` | FIELD, METHOD; required `String value()` | One nonempty exact external name shared by importer and schema; not a Groovy or Jackson rename. | `a81e168c7` (2026-10-27) |
| `InputIgnore` | FIELD, METHOD; marker | Exclude a field/admitted authored operation from structured input and schema; no runtime-validation change. | `a81e168c7` (2026-10-27) |
| `InputNaming` | TYPE; required `Profile value()` | Explicit inherited naming policy; nested `Profile.IDENTITY` / `KEBAB_CASE`. Literal/data keys retain their own rules. | `000699df8` (2026-10-26) |
| `InputExamples` | FIELD, METHOD; required `String[] value()` | Documentary strings, emitted as string examples without parsing or type validation; not defaults. | `43d148f43` (2026-10-27; original implementation subsequently simplified) |
| `InputConstraintDescription` | ANNOTATION_TYPE; required `String value()` | Literal constraint prose on a domain annotation; retained by PROJECT/TEXT_ONLY and suppressed by OMIT. No assertions or handler execution. | `996a33d68` (2026-10-29) |
| `ConstraintProjection` | FIELD, PARAMETER; `Policy value()` defaults to PROJECT | Constraint-derived artifact output only; nested `Policy.PROJECT`, `TEXT_ONLY`, `OMIT`. Selected converter parameter policy is independent of its containing field. No admission or runtime enforcement. | `c6a8ddd46` (2026-10-26; PARAMETER support added by C1) |

Authoritative operation admission has **no current author annotation**. `InputOperation` was the earlier standalone
proposal in this plan, not an existing type. `Builder.Input` already means Model-to-Builder parameter projection;
`Builder.Method` establishes Builder-only execution, not external-document admission. Neither is renamed or repurposed.

Do not fold these adjacent vocabularies into the proposed author namespace:

- `GeneratedInput.Schema/Key/Field/Operation/Constraint/Selection/Converter/Exclusion` are generated-only runtime
  metadata in `com.blackbuild.klum.ast.runtime.generated`. They already have an outer class but are not author API.
  In particular, `GeneratedInput.Operation` is not the proposed authority marker.
- `Converter` / `Converters` configure ordinary Builder conversion; `DefaultValues` and ordinary lifecycle/validation
  annotations are not structured-document annotations. Existing Java `Deprecated`, Jakarta constraints and supported
  Jackson bridges remain independent external vocabularies. No new annotation is invented for their existing behavior.

Release audit, checked 2026-10-31:

| Evidence | Bounded conclusion |
| --- | --- |
| Remote `v4.*` tags and GitHub release listing | Latest listed stable release is [4.1.1](https://github.com/klum-dsl/klum-ast/releases/tag/v4.1.1), published 2026-10-30; no 4.2 tag in that remote inventory. |
| Source trees for v4.0.0, v4.0.1, v4.1.0 and v4.1.1 | None contains the six annotation classes. v4.1.1 resolves to `3e34e9ef5d0d618021851fd229177afd164b0ee1`. |
| [Maven Central version metadata](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-annotations/maven-metadata.xml) | Latest/release is 4.1.1; no listed 4.2 version. Metadata lastUpdated is `20261030150344`. |
| Published [4.1.1 annotations JAR](https://repo.maven.apache.org/maven2/com/blackbuild/klum/ast/klum-ast-annotations/4.1.1/klum-ast-annotations-4.1.1.jar), ZIP entries inspected without execution | All six classes absent. SHA-256: `40e9afd138b0feb40bb5fe709c461b03d49f62cd8a59951b6b8f724201011d4e`. |
| Current source and [CHANGES](../../CHANGES.md) | All six are implemented on master and listed under Unreleased. They are real APIs in development source, not merely design sketches. |

This supports a possible pre-4.2 consolidation; it does **not** prove nobody has compiled against these names. Private,
local and development publication/consumer inventories were not exhaustively audited. Schelm's Dev.114 qualification
predates these introductions and cannot establish adoption or non-adoption of the six current annotations. Any migration
must refresh actual development coordinates and consumer usage before promising compatibility or selecting removal.

## Outer-name comparison

The namespace describes the **structured external-document contract**: which operations and values the interpreter
accepts and what a generated JSON Schema can describe. It is not the parser, a document object, a serialization format,
the entire domain Schema, or a generator configuration namespace. A final outer class with a private constructor and
nested annotation interfaces follows the existing `Builder` pattern; the outer type itself is not an annotation.

| Candidate | Strength | Misleading interpretation / recommendation |
| --- | --- | --- |
| `Input` | Short; keeps the existing Input* vocabulary recognizable. `Input.Name` reads naturally. | Too broad alongside ordinary Groovy/Factory/named-parameter inputs; simple-name imports can collide with `Builder.Input`. Viable compact alternative only with an explicit structured-document definition. |
| `StructuredInput` | Matches established “structured input,” `KlumStructuredInput`, and the shared runtime/schema contract. `StructuredInput.Name` and `.Operation` identify the same language. | Longer, but precise. Could be mistaken for a facade unless Javadoc says annotation namespace. **Recommended.** It is broader than generation and narrower than all Builder input. |
| `Structured` | Compact; `.Name` / `.Ignore` are readable. | An adjective without a clear subject: could mean structured Models, logging or arbitrary tree data. Omits the input direction and invites output/serialization interpretation. Weaker alternative. |
| `Document` | Emphasizes external authoring and is independent of JSON Schema as a format. | Suggests a parsed document/value type, file/source loading, document lifecycle or import/export symmetry; common DOM/domain name. It obscures the parser-free Map/Builder-body boundary. Not recommended. |
| `Wire` | Familiar transport-contract term; short. | Suggests an interchange/serialization wire format and revives the retired WIRE carrier terminology. Conflicts with the deliberate absence of a round-trip format or carrier protocol. Reject for this namespace. |
| `Schema` | Familiar declaration term; `Schema.Name` is brief. | Klum “Schema” already means the whole DSL model declaration, and JSON Schema is only one projection. It could imply generator-only policy or rename the domain Schema; `GeneratedInput.Schema` adds confusion. Reject for this namespace. |

No existing public outer type with any of these six names was found in the current annotation package. That removes
an immediate package collision, not the semantic ambiguity above. Repository vocabulary favors `StructuredInput`, but
does not make it an accepted decision. Final outer/member names remain maintainer-owned.

## Proposed nested vocabulary

| Current name | Proposed spelling | Boundaries |
| --- | --- | --- |
| `InputName` | `StructuredInput.Name` | Same FIELD/METHOD targets and exact literal semantics. |
| `InputIgnore` | `StructuredInput.Ignore` | Same exclusion marker; no opt-out from ordinary validation. |
| `InputNaming` / `.Profile` | `StructuredInput.Naming` / `.Naming.Profile` | Preserve explicit choice, IDENTITY/KEBAB_CASE, inheritance and literal precedence. No new profile. |
| `InputExamples` | `StructuredInput.Examples` | Same documentary String array. Nesting does not make examples typed input. |
| Proposed `InputOperation` | `StructuredInput.Operation` | New authoritative-admission marker, still proposed; exactly one marked eligible operation wins. Does not replace `Builder.Method` or admit all public methods. |
| `ConstraintProjection` / `.Policy` | `StructuredInput.ConstraintProjection` / `.ConstraintProjection.Policy` | Keep PROJECT/TEXT_ONLY/OMIT and FIELD/PARAMETER targets. This particular member controls constraint output; membership in the shared namespace does not give it runtime authority. |
| `InputConstraintDescription` | `StructuredInput.ConstraintDescription` | Preserve ANNOTATION_TYPE-only literal prose and policy behavior. Prefer the specific name over `.Description`, which suggests arbitrary field documentation. |

Use `.Operation` rather than `.Primary` to avoid suggesting a fallback chain. `.Admission` is a viable member alternative
but is less direct at an annotated method. Keep `.ConstraintProjection` rather than the shorter `.Projection` so it does
not appear to control all structural schema output. A second nested `Constraints` framework or a family of constraint
annotations is not justified. The proposed nested enums keep their present relationship to their annotations.

Illustrative future Schema syntax; these nested annotations **do not exist** today:

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.StructuredInput

@DSL
@StructuredInput.Naming(StructuredInput.Naming.Profile.KEBAB_CASE)
class Service {
    @StructuredInput.Name('service-id')
    @StructuredInput.Examples(['catalog'])
    String serviceName

    @StructuredInput.Operation
    @Builder.Method void endpoint(String value) { serviceName = value }
}
```

## Compatibility and migration decision

Moving a top-level annotation into an outer class changes its source name and binary descriptor, for example
`Lcom/blackbuild/klum/ast/InputName;` to `Lcom/blackbuild/klum/ast/StructuredInput$Name;`. Imports alone do not migrate
compiled class annotations. Enum identity also changes: `ConstraintProjection$Policy` and `InputNaming$Profile` are
different types from their nested replacements even when constants have identical text. No source/binary compatibility
claim follows from equivalent annotation members.

The effect reaches both source Schema annotations and compiled domain parameter/meta-annotations. Current compiler
recognition explicitly names the old classes. `BuilderContractReader` accepts the exact
`ConstraintProjection$Policy` descriptor in generated field facts; `SelectedConverterParameterReader` names the exact
old annotation and enum descriptors. `GeneratedInput.Field.constraintProjection()` itself returns the old enum type.
Changing that return descriptor changes generated metadata ABI even if the outer generated-only names stay unchanged.
Other normalized literal facts need not be redesigned merely because author spellings change.

Two migration strategies are possible; neither is selected by this audit:

1. **Recommended conditional pre-4.2 cutover:** after confirming affected development consumers and maintainer approval,
   introduce the nested vocabulary, migrate repository sources/docs/tests, remove old development spellings and require
   matched compiler/runtime/plugin plus recompilation of Schema hierarchies and annotated domain dependencies. State
   source and binary breaks explicitly, with a coordinate/version boundary and actionable rejection of unsupported old
   facts. This avoids indefinitely maintaining synonyms where the audited stable line never shipped them. It is not
   permission to rename now, silently ignore old annotations, or assume protocol marker 1 makes old bytes compatible.
2. **Transition bridge if compatibility is required:** retain real old annotation/enum types, deprecate them, normalize
   both spellings into one internal fact and implement explicit reader support where old binaries are promised. Merely
   retaining a class or writing a Groovy import alias is insufficient. A maintainer-approved equal/contradictory dual-use
   rule and retirement boundary are necessary; do not silently add precedence or broaden allowed targets. If only old
   source recompilation is supported, say so separately from old compiled domain/Schema binary support.

Keep ordinary `Builder.*`, converter APIs, Jakarta/Jackson annotations, managed Jackson and generated-only metadata
outside the author rename. Do not change runtime validation, annotation placement, inherited naming, literal conflict
rules, examples, constraint suppression or converter authority in a vocabulary-only implementation. Do not copy Jakarta
parameter annotations to Builders. No new runtime module, registry or generic annotation alias engine is needed.

## Plan integration and evidence gates

Add V0 as a **conditional vocabulary migration proposal**, preceding S2 only if namespace consolidation is approved.
S1's execution change and present-behavior documentation can proceed independently. Prefer one cohesive vocabulary
and recognition/test commit; separate generated-descriptor adaptation only if its intermediate state is coherent and
green. Follow with migration/current-user/Javadoc/CHANGES updates. Do not bundle new operation semantics into a rename
commit; S2 remains their separate owner. An additive bridge, if chosen, needs a finite removal plan rather than a second
permanent authority path.

V0 acceptance must include Java and Groovy source imports/annotation use, Groovy 3/4/5, same-compilation and separately
compiled Schema/domain artifacts, inherited and overridden members, converter PARAMETER and description ANNOTATION_TYPE
targets, public generated annotation/enum descriptors and applicable JPMS. Compare runtime admission and generated
schema before/after at the same semantic fixture; no-user-code inspection, initialization traps, policy and diagnostics
must remain. For a cutover, prove old source diagnostics and stale-byte rejection/recompile guidance; for a bridge,
test every explicitly promised old/new combination and conflicting double declarations. Do not claim universal mixed
toolchain compatibility from this matrix. Actual namespace migration remains a future implementation assignment.

Maintainer decisions: outer/member vocabulary (recommended `StructuredInput.*`) and cutover versus the precise bridge
promise after development-exposure inventory. These replace the earlier standalone `InputOperation` naming question;
they do not reopen the accepted sixteen semantic decisions. No API implementation, push, PR or issue mutation occurs
in this audit. Hive owns publication and final reconciliation.
