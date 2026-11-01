# Structured-input annotation namespace audit

Date: 2026-10-31. Status: **Accepted `Struct.*` vocabulary; V0 implements the direct internal rename.**
Addition to [INPUT-ROUND-PLAN](input-round-plan-4-2.md) and
[ADR 0032](../adr/0032-builder-oriented-structured-input.md). Source baseline remains
`0c6f401b2a561a7767eca369f5aacab33dad0962`; preceding planning head was
`3ac43c82e54e2a054aa96a28249d8ff13f73113a`. The original audit was planning only; V0 implements the accepted rename without changing release placement.

## Pre-V0 vocabulary and release exposure

At the audited baseline, all six author-facing annotations below were top-level types in `com.blackbuild.klum.ast`, declared in
[`klum-ast-annotations`](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast). Each was `@Documented` with RUNTIME retention and none was deprecated. The table records the historical source inventory; V0 replaces these names with the accepted nested types.

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

Do not fold these adjacent vocabularies into the accepted author namespace:

- `GeneratedInput.Schema/Key/Field/Operation/Constraint/Selection/Converter/Exclusion` are generated-only runtime
  metadata in `com.blackbuild.klum.ast.runtime.generated`. They already have an outer class but are not author API.
  In particular, `GeneratedInput.Operation` is not the author-facing authority marker.
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

The initial audit did not establish development-consumer exposure. The maintainer has now supplied that authority:
**the current names were never used outside internal tests and Schelm qualification; no migration or cutover is needed.**
Accept a direct internal rename to `Struct.*`. Update the internal uses and Schelm qualification together when implementing
it; no further exposure inventory, compatibility bridge, deprecation window, alias support or versioned transition is
required. This is a maintainer-confirmed usage fact, not an inference from the Maven/tag audit. The earlier conditional
migration recommendations are superseded by this decision.

## Outer-name comparison

The namespace describes the **structured external-document contract**: which operations and values the interpreter
accepts and what a generated JSON Schema can describe. It is not the parser, a document object, a serialization format,
the entire domain Schema, or a generator configuration namespace. A final outer class with a private constructor and
nested annotation interfaces follows the existing `Builder` pattern; the outer type itself is not an annotation.

| Candidate | Strength | Misleading interpretation / recommendation |
| --- | --- | --- |
| `Struct` | Compact; aligns with the recorded future `FromStruct` vocabulary. `Struct.Name` and `Struct.Operation` are readable. | Can suggest a C/Rust record layout; namespace documentation defines the broader structured external-input contract, including operations and scopes. **Accepted by the maintainer.** |
| `Input` | Short; keeps the existing Input* vocabulary recognizable. `Input.Name` reads naturally. | Too broad alongside ordinary Groovy/Factory/named-parameter inputs; simple-name imports can collide with `Builder.Input`. Viable compact alternative only with an explicit structured-document definition. |
| `StructuredInput` | Matches established “structured input,” `KlumStructuredInput`, and the shared runtime/schema contract. `StructuredInput.Name` and `.Operation` identify the same language. | Longer, but precise. Could be mistaken for a facade unless Javadoc says annotation namespace. Earlier recommendation; the maintainer selected the shorter `Struct`. It remains the explicit long-form alternative. |
| `Structured` | Compact; `.Name` / `.Ignore` are readable. | An adjective without a clear subject: could mean structured Models, logging or arbitrary tree data. Omits the input direction and invites output/serialization interpretation. Weaker alternative. |
| `Document` | Emphasizes external authoring and is independent of JSON Schema as a format. | Suggests a parsed document/value type, file/source loading, document lifecycle or import/export symmetry; common DOM/domain name. It obscures the parser-free Map/Builder-body boundary. Not recommended. |
| `Wire` | Familiar transport-contract term; short. | Suggests an interchange/serialization wire format and revives the retired WIRE carrier terminology. Conflicts with the deliberate absence of a round-trip format or carrier protocol. Reject for this namespace. |
| `Schema` | Familiar declaration term; `Schema.Name` is brief. | Klum “Schema” already means the whole DSL model declaration, and JSON Schema is only one projection. It could imply generator-only policy or rename the domain Schema; `GeneratedInput.Schema` adds confusion. Reject for this namespace. |

The original six candidates had no existing public outer type in the annotation package. `Struct` is also absent there.
The maintainer selected `Struct` for concise authoring. Document its shared importer/schema meaning explicitly; it does
not represent a memory layout, parser or completed-model serialization contract.

## Accepted nested vocabulary

| Current name | Accepted spelling | Boundaries |
| --- | --- | --- |
| `InputName` | `Struct.Name` | Same FIELD/METHOD targets and exact literal semantics. |
| `InputIgnore` | `Struct.Ignore` | Same exclusion marker; no opt-out from ordinary validation. |
| `InputNaming` / `.Profile` | `Struct.Naming` / `.Naming.Profile` | Preserve explicit choice, IDENTITY/KEBAB_CASE, inheritance and literal precedence. No new profile. |
| `InputExamples` | `Struct.Examples` | Same documentary String array. Nesting does not make examples typed input. |
| Proposed `InputOperation` | `Struct.Operation` | New authoritative-admission marker, implemented in [S2](input-round-s2-evidence.md) for exact String operations; exactly one marked eligible operation wins. Does not replace `Builder.Method` or admit all public methods. |
| `ConstraintProjection` / `.Policy` | `Struct.ConstraintProjection` / `.ConstraintProjection.Policy` | Keep PROJECT/TEXT_ONLY/OMIT and FIELD/PARAMETER targets. This particular member controls constraint output; membership in the shared namespace does not give it runtime authority. |
| `InputConstraintDescription` | `Struct.ConstraintDescription` | Preserve ANNOTATION_TYPE-only literal prose and policy behavior. Prefer the specific name over `.Description`, which suggests arbitrary field documentation. |

Use `.Operation` rather than `.Primary` to avoid suggesting a fallback chain. `.Admission` is a viable member alternative
but is less direct at an annotated method. Keep `.ConstraintProjection` rather than the shorter `.Projection` so it does
not appear to control all structural schema output. A second nested `Constraints` framework or a family of constraint
annotations is not justified. The nested enums keep their present relationship to their annotations.

Schema syntax implemented by S2 for the exact String operation (CharSequence invocation remains deferred):

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct

@DSL
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
class Service {
    @Struct.Name('service-id')
    @Struct.Examples(['catalog'])
    String serviceName

    @Struct.Operation
    @Builder.Method void endpoint(String value) { serviceName = value }
}
```

## Direct internal rename and implementation consequences

No migration or cutover work is required. Rename the six existing author annotations to the accepted nested names and
update their internal tests, documentation and Schelm qualification uses. Add `Struct.Operation` with S2's new admission
semantics. Do not retain the old names as deprecated aliases or add dual-spelling, compatibility-reader or release-boundary
machinery. The maintainer's usage confirmation removes the earlier migration-policy and exposure-inventory gates.

The mechanical implementation still changes type identities: `InputName` becomes `Struct$Name`, and the policy/profile
enums acquire nested binary names. V0 updates recognition and references consistently: `BuilderContractReader` accepts
the `Struct$ConstraintProjection$Policy` descriptor, `SelectedConverterParameterReader` uses the nested annotation and
enum descriptors, and `GeneratedInput.Field.constraintProjection()` returns the nested enum. Internal qualification
fixtures must be recompiled with matching tools. This is
ordinary verification of the direct rename, not an old/new binary compatibility promise or a migration project. Other
normalized literal facts need not be redesigned merely because author spellings change.

Keep ordinary `Builder.*`, converter APIs, Jakarta/Jackson annotations, managed Jackson and generated-only metadata
outside the author rename. Do not change runtime validation, annotation placement, inherited naming, literal conflict
rules, examples, constraint suppression or converter authority in a vocabulary-only implementation. Do not copy Jakarta
parameter annotations to Builders. No new runtime module, registry or generic annotation alias engine is needed.

## Plan integration and evidence gates

V0 is the **direct vocabulary rename** preceding S2. S1's execution change and present-behavior documentation
can proceed independently. Prefer one cohesive vocabulary, recognition and test commit so no intermediate artifact has
inconsistent type descriptors. Update current user guidance, Javadoc and CHANGES with the final names; no annotation
migration guide or cutover procedure is needed. Keep the new Operation semantics in S2's separate commit.

V0 acceptance covers Java and Groovy source imports/annotation use, Groovy 3/4/5, same-compilation and separately compiled
Schema/domain fixtures, inherited and overridden members, converter PARAMETER and description ANNOTATION_TYPE targets,
public generated annotation/enum references and applicable JPMS. Compare runtime admission and generated schema before
and after using equivalent fixtures; no-user-code inspection, initialization traps, policy and diagnostics remain.
Update and rerun affected internal tests and Schelm qualification using the final names. No old-name compatibility
matrix, dual-declaration rules or stale-byte migration tests are required. The historical audit above records the pre-V0 baseline.

The namespace and direct-rename decision are settled. They replace the earlier `InputOperation` and `StructuredInput.*`
proposals and conditional cutover/bridge discussion without reopening the sixteen semantic decisions. The assignment's
reviewed documentation-PR boundary can now be evaluated without an annotation decision blocker. Issue mutations and
API implementation remain outside this assignment; Hive owns final delivery/archive reconciliation.
