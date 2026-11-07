# #896: generated Schema provenance for 4.2

Date: 2026-11-07. Status: provenance-first design from PR #1047; RF-1 implementation record below.

Tracking issue: [#896](https://github.com/klum-dsl/klum-ast/issues/896).
Design PR: [#1047](https://github.com/klum-dsl/klum-ast/pull/1047).
Parent decisions: [ADR 0015](../adr/0015-generated-schema-runtime-linkage.md),
[ADR 0019](../adr/0019-published-showcase-consumer-verification.md), and
[ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md).

## Maintainer decision and delivery scope

**RF-1, generated compiler provenance, is the primary 4.2 objective.** Provenance records which compiler actually
produced a transformed Schema. A minimum runtime is a compatibility claim requiring independent qualification.
The two facts must stay separate in metadata, interpretation, and implementation planning.

| Capability | Target |
| --- | --- |
| Generated compiler provenance | 4.2 |
| Runtime-floor metadata | Optional / independently decided |
| Runtime compatibility comparison | Post-4.2 candidate |
| Actionable runtime-floor diagnostics | Post-4.2 candidate |
| Historical binary/Catwalk qualification | Separate adoption decision |
| Release-gate enforcement | Not part of RF-1 |

RF-2 has no 4.2 implementation requirement. RF-3 and RF-4 are separately scheduled follow-ups, not prerequisites for
RF-1. RF-1 does not add runtime comparisons/enforcement, Builder guards, ancestor-floor aggregation, runtime identity
lookup, a compatibility cache, dependency substitution, or version-equality policy. No ABI feature level, capability
bitset, or separate metadata protocol is proposed. Both ADRs retain Proposed status.

Design PR #1047 corrects the design only. It introduces no production annotation, emitted metadata, runtime code, or tests.
Updating the design does not complete #896 or admit a new release gate.

## Selected marker: Option A

Use one runtime-retained, non-inherited TYPE annotation, with the working name `GeneratedSchema` in the existing
exported `com.blackbuild.klum.ast.runtime.generated` package:

```java
@GeneratedSchema(
    format = 1,
    compilerVersion = "4.2.0"
)
```

| Member | Meaning |
| --- | --- |
| `format` | Explicit marker encoding version, initially 1. It is not an ABI feature level or runtime version. |
| `compilerVersion` | Exact version of the actual emitting `klum-ast` compiler artifact, including development/RC suffixes where present. It is not a minimum runtime version. |

**Option A is selected.** It records the two facts RF-1 can establish and has no unused compatibility member.
Option B (`minimumRuntime = ""`) would reserve an unknown value, but no current reader, binary contract, or
implementation need requires it. Adding that member later with a backward-compatible unknown default preserves the
same extension option, so reserving it now has no concrete advantage.

Emit both RF-1 values explicitly. Record a declared marker on every transformed Schema class, including abstract
Schemas. Each class records its own emitting compiler; do not inherit or copy a parent's provenance. Its marker
identifies that class's generated family without duplicating metadata on every Builder/Factory/support class.
Interfaces skipped by the transformation and plain Java Domain APIs have no transformed Schema class to mark.

The compiler obtains its own artifact version from build-owned provenance bound to the implementation actually
executing the transformation. Do not infer it from a consuming project, Gradle plugin, BOM, resolved runtime, or Schema
GAV. Preserve exact version text without ordering it. A missing or ambiguous own-artifact version must not be replaced
with an unrelated version; report a compiler-provenance error during Schema compilation. Resource packaging and lookup
must work for the normal packaged compiler and local build/test layout; no general runtime identity service is needed.

This is generated metadata, not a handwritten Schema option. Avoid duplicate/overwritten markers during annotation
propagation. Runtime retention makes it readable through ordinary reflection in independently compiled Java and Groovy
consumers, without the Klum compiler or Gradle plugin in the consuming application. Reading a declared annotation does
not initiate a compatibility check or aggregate superclass metadata.

### Future optional metadata evolution

If a separately accepted design later adds `String minimumRuntime() default ""`, a reader using that updated annotation
type obtains the unknown default for a 4.2 marker that omitted the member. Retain `format` and `compilerVersion` and
preserve their meanings. An additive optional member with defined unknown semantics does not by itself require a new
format or invalidate format-1 provenance. Future readers must treat the default as unknown, never as the compiler
version or a compatibility guarantee. The new member, its interpretation, and any checking policy need their own
adoption decision; RF-1 neither implements nor requires them.

Annotation defaults are applied when metadata is read, including for older compiled classes, as specified by
[JLS 9.6.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.2).
Once introduced, keep the omitted floor's default meaning unknown; changing it would affect historical markers.

## Current evidence and affected seams

The source investigation used `651f072fd25e3680cf3daf2dd2065e39c442ed7b`. The
[retained binary investigation](evidence/issue-896-binary-version-compatibility.md) found seven basic 4.0.1/4.1.0 rows
passing, but a published 4.1.0 `@Builder.Query` Schema failed on an explicitly downgraded 4.0.1 runtime with
`NoSuchMethodError` for `GeneratedKlumBuilder.$klum$completedModelOrNull()`. Model compilation succeeded; Model and
Java-only execution failed, while the same binaries passed on 4.1.0. Those are historical Groovy-3/JDK-17 results,
not a universal compatibility range or a fresh execution by this design correction.

RF-1's relevant source seams are:

- `klum-ast`'s `DSLASTTransformation`: emit metadata on every transformed abstract/concrete Schema class. Its existing
  interface early return does not represent a transformed class. Keep Builder/Factory generation unchanged.
- `klum-ast-runtime`'s already exported `runtime.generated` package: proposed home of the metadata annotation.
  No new module or broad internal export is required.
- `klum-ast/build.gradle`: bind the emitting compiler version to its own build artifact. The Gradle plugin's existing
  version resource is a packaging precedent, not the compiler's authority.
- Existing generated public/protected descriptors and `FactoryHelper`/`GeneratedKlumBuilder` construction paths:
  preserve them. They require no new guards or identity/comparison code for provenance.

[`RootRecipeConsumerTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/RootRecipeConsumerTest.groovy) provides
an existing independent JAR/Java/Groovy consumer seam. Its reconstructed pre-addition Factory surface is not historical
producer bytecode. Existing module-boundary tests can verify that metadata uses the existing export. The ordinary BOM
platform and plugin-version resource do not establish the emitting compiler's identity or enforce runtime compatibility.

The earlier Catwalk source inspection used
[`97a787ecc2696569327d42f6cf5d59999c5503ac`](https://github.com/klum-dsl/klum-catwalk/tree/97a787ecc2696569327d42f6cf5d59999c5503ac):
direct evidence was pinned to public 4.0.1, Layer 3 contracts lacked an independently published Model/Java runtime leaf
and version matrix, and smart-home handoffs used file JARs. Complete candidate-maintenance qualification was absent.
These dated findings explain separate RF-3 work; refreshing or delivering that infrastructure is not an RF-1 gate.

## RF-1 — Generated Schema provenance: target 4.2

One thin implementation slice adds the two-member annotation, own-compiler version packaging/lookup, and per-class
emission. Keep marker generation with its focused acceptance coverage in one reasoned commit. Any provenance-specific
user/release note required by normal repository policy accompanies that delivery; it does not wait for broad RF-4
compatibility guidance.

RF-1 acceptance:

- Every transformed concrete and abstract Schema has its own runtime-visible marker with format 1 and the exact
  emitting compiler artifact version. Include inheritance and development/RC version preservation.
- Separately compiled Java and Groovy consumers can read the marker from a Schema JAR without the Klum compiler/plugin
  at runtime. Consumer project/plugin/BOM/runtime selections do not replace the recorded producer value.
- Marker placement/annotation propagation preserves each class's origin. Existing public/protected descriptors,
  construction behavior, and binary consumption of older unmarked Schemas remain unchanged. No runtime enforcement is
  added; an absent historical marker conveys no provenance fact.

Use the existing compiler/artifact consumer seams and repository risk-based validation for a generated-contract change;
new tests carry `@Issue("896")`. RF-1 does not need a cross-version compatibility matrix to establish provenance, nor
does its acceptance include implementing any RF-2 check or compatibility scaffold. Design PR #1047 adds none of those tests.

## RF-1 implementation record

The implementation adds `GeneratedSchema` in the existing exported runtime package and emits both required values once
on every transformed abstract/concrete Schema. Handwritten markers are rejected; default-only annotation propagation
cannot fabricate either required member. Interfaces and generated support types remain unmarked.

`generateCompilerVersionMarker` binds the exact compiler project artifact version, including development/RC text, to a
build-owned properties file. `compileJava` tracks that file as an input and copies it into its class output. This keeps
provenance in the same code-source directory or JAR as the executing compiler, including local build/test layouts and
cached compiler output. The compiler reads only that location, never a classloader-wide resource, consuming-project
version, plugin, BOM, or runtime identity. Missing, blank, or duplicate version facts cause a Schema compilation error.

Acceptance coverage:

- `GeneratedSchemaProvenanceTest` starts with a failing marker-absence test and then checks exact local emitter values,
  abstract/concrete inheritance, a collection Schema, unmarked interface/plain/support controls, ordinary construction,
  handwritten-marker rejection, and propagation rejection. Its happy path is documentary and links to
  [Schema Compiler Provenance](../user/Schema-Provenance.md#generated-compiler-provenance).
- `GeneratedSchemaProvenanceConsumerTest` compiles parent and child JARs in separate processes using controlled compiler
  artifact copies carrying distinct development/RC provenance. Independent Java and static Groovy clients read the two
  origins with no compiler/plugin at runtime; source projection retains the marker. Classpath cases run in Groovy 3/4/5;
  named modules run in Groovy 4/5 through the existing runtime export. Negative metadata cases include a decoy resource
  that must not supply the executing compiler's identity.
- Removing only the marker reconstructs an unmarked binary control and leaves ordinary construction usable. This is a
  local absence/consumption control, not retained historical producer or mixed-release compatibility qualification.

The production change adds no runtime reader, comparison, floor, construction guard, ancestor aggregation, or release
policy. Existing generated member descriptors and construction paths remain unchanged. User guidance, migration
navigation, and `CHANGES.md` cover basic provenance availability only. RF-2/RF-3/RF-4 remain separate; #896 stays open
with a non-closing `Related` relationship. Curation and release-gate placement have no impact from this localized slice.
The original design validation below describes PR #1047; current implementation validation belongs to its draft PR.

## Future compatibility constraints and independently scheduled work

These constraints preserve options; they are not 4.2 implementation requirements:

- Compiler provenance identifies an emitter. Actual ABI calls and behavior, runtime support, Groovy, adapters, and
  application dependencies determine compatibility. Equal strings do not prove it, and different strings do not
  disprove it. Never reinterpret `compilerVersion` as a runtime requirement.
- Older runtimes cannot learn a newly introduced check merely from a marker. A new generated helper call can itself
  fail to link there. Any future diagnostics design must bound its coverage; passive completed-Model use and earlier
  linkage/class initialization may bypass a construction boundary. No retroactive protection is promised.
- Directional older-Schema/API-on-newer-runtime qualification needs retained historical producer binaries and their
  publication metadata/hashes. Recompiling them against the candidate removes the historical generated ABI under test.
- Newer Schema consuming older Domain API is a different axis: retain the older API binary, stay within that contract,
  and qualify the API-only client boundary independently. A Schema marker does not version the application's API.

**RF-2 — Runtime-floor comparison and diagnostics: post-4.2 candidate.** Runtime-floor metadata is optional and
independently decided. A floor is a qualified compatibility claim, not a version copied from the emitter. A future
proposal must decide its meaning, evidence, and check coverage. This plan prescribes no comparison algorithm,
constructor guard, ancestor aggregation, runtime lookup, compatibility cache, pre-release ordering, or release gate.

**RF-3 — Independent Catwalk qualification: separate adoption decision.** Retain predecessor producer artifacts and
qualify only explicitly supported directions/lines. Preserve ADR 0019's complete marker/implementation/BOM/product
manifest, isolated repository, and public/candidate evidence boundaries. Deliberate mixed-version experiments remain
separate from aligned candidate-product proof. No Catwalk delivery or baseline promotion is prerequisite to RF-1.

**RF-4 — Release-facing compatibility adoption: separately scheduled.** Broader migration/version-selection guidance,
including the ordinary BOM's misleading “enforced” wording, and any release-gate admission follow their own accepted
scope and evidence. Basic provenance availability can be documented with RF-1. This follow-up does not block RF-1 and
cannot turn its marker into a compatibility promise. Both ADRs remain Proposed pending separate acceptance.

## Readiness and validation

The minimal RF-1 target and Option A are settled by this correction. No additional runtime-floor, compatibility,
Catwalk, or release-policy decision is needed before implementing RF-1. Select concrete version-resource packaging and
ordinary annotation-generation details during implementation under the provenance contract above.

This design-only revision is validated with relative Markdown links, document-consistency review and diff hygiene.
No production code, annotation, generated metadata, runtime check, or test is added. Groovy lanes are unnecessary for
this documentation correction. Prior retained-evidence verification is historical record consistency, not current
product or Catwalk qualification. #896 remains open for implementation and separately adopted follow-ups.
