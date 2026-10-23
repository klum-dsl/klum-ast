# WIRE-REPLACE — enriched public compiled Builder seam

Date: 2026-10-23. Owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Base: `b8fb774a927ef2815bcbfea3b78b35858ccdbd68` (master, merged #945 and #946).
Authority: [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md), the
[implementation plan](../adr-0031-schema-0.md), and the separately approved WIRE-REPLACE assignment.
Prerequisites: [SCHEMA-R](issue-529-schema-r.md) and [WIRE-SCHEMA-PROBE](issue-529-wire-schema-probe.md).

## Delivered seam

Production `StructuredInputSupport` consumes the package-private runtime `CompiledBuilderSchema` facade. It resolves
normalized admission, external naming, source attribution, public operation groups, recursive payloads and rejection
facts from public Builder/Factory contracts. Runtime dispatch, preflight, construction and lifecycle rules remain #932's
existing behavior. No generator, Gradle generator task, parser, CLI, public descriptor or registry is introduced.

The old `StructuredInputPlan` reader has moved to test Java as `LegacyStructuredInputPlan`. Compiler carrier emission
remains solely for historical comparison fixtures until separately authorized WIRE-REMOVE. No production code reads it.
The disposable test-only reflection facade remains a historical gap observer; passing its expected-gap corpus alone is
still not replacement evidence. Additional complete-parity comparisons use the actual production seam and no gap allowlist.

## Minimal generated linkage

`runtime.generated.GeneratedInput` follows the existing generated-linkage namespace and is framework-owned, not a
Schema-author API. Its nested annotations have RUNTIME retention, explicit TYPE/METHOD targets and no `@Inherited`.

- `Schema` marks every declaring Builder as enriched, carries the otherwise invisible child Key member, and supplies
  exclusion source/category only when no public declaration survives. It carries no Model/Builder identity or name catalog.
- `Field` joins one public getter to its original native field. Nonempty rename, true ignore, compiler-only ownership/
  keyMapping/population exclusion and fixed-key membership are retained only where needed. Numeric/container/type-shape
  exclusions are recomputed from reflected types. The native fixed key comes from the field association; a boolean
  membership fact avoids copying that String a second time.
- `Selection` placement joins exactly the compiler-selected public input/population declarations to that field and
  records its admitted category. It serializes no signature/descriptor group or payload tree.
- Existing `Builder.Method` markers and authored naming annotations are retained together on projected declarations
  even when a generated projection previously occupied their signature. Existing Jackson literals and AnnoDoc remain their original annotation facts.

Signatures, declaring owners, generic containers, nested Builder/self Model linkage, factory shapes and embedded docs
remain observed. False ignore and empty rename resolve to identity, matching the carrier's factual distinctions. Enum
names require no static value access. Effective defaults remain explicitly unavailable without execution; the old carrier
never encoded them. No new user-facing DSL surface or documented client capability is introduced, so user pages and
CHANGES need no release entry.

## Safety, inheritance and compiled-artifact boundary

Reflection discovers only explicitly supplied public contracts and their actual public parents/signatures. Model/API
Classes may resolve incidentally; their implementations, fields, methods, constructors, static Create fields and user
code are never inspected or invoked. The Java implementation avoids Groovy metaclass inspection of those Classes.

Selective ASM is private and limited to allowlisted annotation literals in exact public contract resources, skipping
code/debug/frames. This is necessary because SCHEMA-R proved ordinary Java annotation parsing can initialize unrelated
user enum annotations. Runtime-visible and CLASS-retained naming literals are both honored; the latter are used by
existing compiler-qualified authored overloads. The seam exposes neither annotation objects, ASM nor extraction provenance.
Exact internal class identity/access, loader-owned protocol dependencies and unnamed-module code-source/resource origin
are checked. Named-module bytes use the declaring Module's resource access without opening implementation packages.

Parent traversal caches actual declaring contracts only within one read and deduplicates their source facts. Each parent
hierarchy resolves admission before its descendants: already rejected parent declarations do not reserve an alias against
a later child declaration. Same-source conflicts fail; inherited external-name collisions retain all sources and choose
no winner. Source diagnostics retain the reflection-recoverable JVM spelling for unsupported array arguments. Required missing, malformed or older enrichment fails with root/classpath and
recompile/package guidance. Optional missing prose remains absent. The untouched #938 artifact is a negative compatibility
control. This continues the explicitly unreleased recompile boundary; it adds no legacy reader fallback, version protocol
or silent carrier substitution.

## Validation

Final code qualification at `acfe7694a5446e5a2b2fb4d2b03a50cf36e91fb0`:

| Compiler module lane | Cases | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 `test` | 2,341 | 0 | 16 |
| Groovy 4 `groovy4Tests` | 2,343 | 0 | 15 |
| Groovy 5 `groovy5Tests` | 2,343 | 0 | 15 |

Focused Groovy 3 replacement/reflection/WIRE tests preceded full-module qualification. Full baseline and compatibility
runs include existing runtime dispatch, ordered preflight, construction/lifecycle, collection/key and managed Jackson
boundaries. Closed named-layer inspection and the runtime-module reader probe pass in Groovy 4/5. Every required new
comparison uses complete normalized entries/rejections, including selected operation groups, exact types, inherited docs,
Factory/Cluster shapes, fixed keys, invisible exclusions, numeric/container precedence, raw/array/generic negatives,
missing/malformed metadata, resource-origin damage and old-artifact recompile guidance. No expected-gap allowlist is used
as replacement acceptance.

Final `./gradlew check --continue --no-parallel --console=plain` passes repository-wide, including all three compiler
lanes, runtime/Jackson/Bean Validation/Test Support lanes, 359 Gradle-plugin cases, generated-source/JPMS checks, license
checks, lane isolation and coverage reports. Existing skip reasons remain unchanged. Markdown fences/local link targets
and `git diff --check` pass. An earlier parallel root run reported a changed `settings.gradle` in the existing mirror
configuration-cache fixture; the unchanged fixture passes in the final serial run. No plugin code or assertion was
changed to obtain that result.

Independent cumulative local review at `acfe7694a`: **Standards zero remaining findings; Specification zero remaining
findings**. Initial review corrected the stale JPMS reader reference and retained authored naming alongside a previously
lost operation marker; production docs/Factory assertions and a single exclusion formatter address the coverage and
maintainability observations. Subsequent exact oracle cases preserve array source spelling and parent-first collision
boundaries. Both reviewers reread the cumulative final code; neither reran builds.

History review retains three focused reasoning steps (replacement seam, compatibility qualification, semantic/review
corrections) followed by this evidence checkpoint. No unrelated edits or history rewrite. Master remains the agreed
`b8fb774a9` base; no rebase was required. Delivery capability audit: **Git transport authorized; GitHub CLI authorized**.
Tracker relationship is **Related: #529**; curation impact is **none**. Issue/milestone/release placement is unchanged.
PR checks and SonarCloud are separate remote evidence and must be inspected at the published head.

## Remaining WIRE-REMOVE criteria

WIRE-REMOVE is a separate deletion assignment. Audit and remove compiler `emitCarrier`/`emitWirePlanCarrier` plumbing,
`StructuredInputAdmissionPlan` serialization support and `ModelVerifierTransformation` emission only after checking every
historical reader/tool fixture. Retire or migrate test-only `LegacyStructuredInputPlan`, `StructuredInputReaderProbe`,
`SchemaTracerProjector`, carrier encoding/packaging/linkage and JPMS assumptions while preserving useful acceptance cases
and original fixture provenance. No production carrier reader remains after this seam; emission is not permission to
keep a production fallback. Recheck Java/static/dynamic Groovy and JPMS, Groovy 3/4/5 and root checks after deletion.
#529 generator projection/Gradle delivery and #932 language extensions remain separately selected work. Neither issue is
closed or retargeted by this slice.
