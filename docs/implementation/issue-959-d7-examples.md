# #959 D7-EXAMPLES implementation and qualification

Authority: [accepted maintainer decision](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6442779190), 2026-10-27.
Stable label: **D7-EXAMPLES**. Base: master `6c6d8c568b9cc99e68dc521950373611b4b513b7`.

## Implemented contract

The explicit documented runtime FIELD/METHOD `InputExamples(String[])` annotation folds ordered JSON texts independently
onto generated public Builder field, key and authored-operation facts. The compiler uses an AST-only String constant
folder; Groovy's generic folder is deliberately avoided because its resolved-field fallback can reflectively initialize
an authored class. No defaults, execution, validation, lifecycle, admission, Pattern or package/root policy changes.

The exact byte reader requires explicit bounded arrays, including empty absence arrays and suppressed-getter key facts,
and carries examples with existing source identity through naming and hierarchy selection. It does not parse JSON.
The private plugin `JsonExample` parser validates complete RFC 8259 values with the accepted bounds, rejects duplicate
decoded keys/BOM/raw controls/unpaired surrogates, and constructs a private validated-literal writer value. Original number
lexemes and JSON null are emitted without Java numeric parsing or generic-null rendering. Every carried participating fact
is checked before projection, including excluded/unsupported declarations. Relationship use examples remain local beside
references and whole container payloads; key examples occur only in member-key definitions.

**The shared metadata version remains 1.** This accepted revision supersedes the probe's version-2 recommendation below.
There is no feature gate or release/recompile cutover in this slice. Actual 4.2 release preparation owns any formal bump.
No historical/mixed-toolchain compatibility promise follows from unchanged development markers.

## Executable evidence

- `InputExamplesTest` covers documentary construction/advisory timing, independent field/operation/key transport,
  constant folding, invalid authoring/size boundaries, no initialization fallback and active-Builder atomic failure.
- `JsonExampleTest` covers valid scalar/container/exact-number/null/Unicode/depth cases and malformed/ambiguous/unsafe
  syntax with safe diagnostics.
- `KlumJsonSchemaGenerationTest#'project advisory examples at exact source-free input positions with Groovy #generation'`
  is the `@Issue('959')`, `@Tag('documentary')`, `@See` binary G3/G4/G5 example. Separate API/owned/derived JARs remain after
  producer sources/classes are deleted. Consumers use runtime/annotations/Groovy without compiler, Jackson databind/validation-provider libraries or
  source mirrors. A throwing static initializer has a separate deliberate positive control.
- `KlumJsonSchemaGenerationTest#'invalid carried examples fail before replacing any output: #target #kind'` mutates
  root/inherited/owned arrays and proves that every previous document and the manifest survive failed generation.
  Missing/duplicate/wrong-type/nested/enum/count/byte/total/depth/duplicate-key and ignored-carried controls are included.
  Successful output also exercises up-to-date and build-cache restoration with byte-identical content.

## Final local qualification and review

JDK 17, reviewed production/test commit `290694e83`; final sequential `./gradlew check` passed in **15m15s**, 140 tasks
(70 executed, 70 up-to-date). Groovy 3/4/5 JUnit totals: **3387 / 2760 / 2760**; **zero failures/errors**;
**17 / 16 / 16 existing skips**. `InputExamplesTest` contributes 17 passing cases in each lane. All **121 packaged JSON
Schema cases** and the **53-case private JSON corpus** pass. Licenses, plugin validation, lane isolation, aggregate
coverage, release/documentation checks and the full source-mirror suite pass. No new suppression or dependency is added.

Independent Python `jsonschema 4.23.0` qualification passes **111 metaschema/input controls**, using the full Draft 2020-12
metaschema plus annotation/validation vocabularies and 17 positive/negative inputs per producer, compared with examples
removed. G3/G4/G5 bytes are identical. Exact long-decimal and arbitrarily large-exponent lexemes and JSON null survive;
the independent decoder validates JSON syntax without imposing numeric-range limits on advisory values. A disposable
JDK17/G3 static-imported Java constant control also passes with a throwing supplying-class initializer. Replay scripts and
logs are local `/private/tmp/d7-validate.py`, `/private/tmp/d7-constant-controls.py`, `/private/tmp/d7-final-check.log`,
`/private/tmp/d7-review-final.log`; generated corpus lives in the plugin's `build/schema-validation/`.

An initial overlapping full/focused run is not qualification evidence: it changed class files during coverage and reported
mirror-fixture failures. It was canceled. The stable sequential focused mirror control and final full suite above
supersede that run; no historical failure cause is claimed from the canceled run.

Independent code-review **Standards: zero remaining findings**; **Specification: zero remaining findings**. The Standards
naming observation was addressed by `collectDeclarationProfiles` / declaration-metadata diagnostics. The Specification
finding was addressed by exact later-element/count indices and top-level enum/nested-annotation handling; the packaged
negative matrix now asserts example 2, count 17, total-byte example 5 and safe UTF-16 diagnostics. Both axes independently
rechecked the changes. The four local reasoning commits were reviewed for focus/order; no rewrite was necessary.
The final evidence-only documentation commit does not change the qualified production/test inputs.

## Delivery and tracker impact

Tracker relationship: **Related: #959**. Parent issue remains open; enhancement/needs-triage labels, no milestone, curation
and release placement are unchanged. No Pattern changes or merge are assigned. Draft publication is authorized by the
implementation assignment after local qualification. Authoritative PR CI/Sonar remains a separate gate and must not be
inferred from local results. Review/merge and Hive delivery/archive reconciliation remain pending.

## Historical probe (superseded proposal)

The remainder reproduces the committed probe at `cfd157d24dc02ca6f70c062036a603029f0dea38` for evidence only. Its
unapproved-state, protocol-bump and future-next-slice wording describes the earlier proposal, not the accepted contract.

# #959 D7-EXAMPLES: explicit advisory input examples

Date: 2026-10-27. **Decision proposal and bounded probe; no production API approved or implemented.**
Base: merged #977, master `6c6d8c568b9cc99e68dc521950373611b4b513b7`.
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md).

## Decision proposed

Add one focused Klum annotation, `com.blackbuild.klum.ast.InputExamples`, with required `String[] value()`,
@Documented, RUNTIME retention, FIELD/METHOD targets, no repeatability, type/package/root policy, labels, registry or provider SPI.
Each constant String contains **one complete JSON value**. An annotation array contains one or more examples; a JSON
array inside one string is one example. Absence means no examples. Reject an explicitly empty annotation array.

This would extend ADR 0031's accepted small-family direction and explicitly supersede its examples deferral **only after
maintainer approval**. It does not reopen #974 constraint policy, #975/#976 naming, #977 literals or independent Jackson
import. The original examples/Javadoc-tag prerequisite is unnecessary for this route.

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.InputExamples // proposed
import com.blackbuild.klum.ast.InputName
import com.blackbuild.klum.ast.Key

@DSL class Service {
    @Key @InputExamples(['"catalog"']) String id
    @InputName('display.name')
    @InputExamples(['"Catalog"', 'null']) String displayName
    @InputExamples(['["blue","green"]']) List<String> tags
    @InputExamples(['42']) int replicas
    @Builder.Method @InputExamples(['{"host":"api","port":"8080"}'])
    void endpoint(Map<String, String> settings) { /* configure Builder state */ }
}
@DSL class Deployment {
    @InputExamples(['{"id":"catalog","display.name":"Catalog"}'])
    Service primary
    @InputExamples(['{"worker":{"display.name":"Worker"}}'])
    Map<String, Service> services
}
```

These are Groovy **constant strings containing JSON**, not Groovy maps/lists, GStrings, expressions, files or plain
unquoted scalar strings. `'"Catalog"'` denotes a JSON string, `'42'` a number, `'true'` a Boolean, `'null'` JSON null;
actual annotation `null` is illegal. A literal string, or a compiler-resolved String constant/constant concatenation,
may be used only where it folds without code execution
([JLS §9.7.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.1)). Never read a static field reflectively to obtain a value.

Emit `examples: ["Catalog", null]`, not the JSON-text strings `["\"Catalog\"", "null"]`.
The outer array order and duplicate values are preserved. JSON Schema's annotation keyword admits arbitrary example
values and recommends schema-valid samples; it does not assert their validity
([Draft 2020-12 §9.5](https://json-schema.org/draft/2020-12/json-schema-validation#section-9.5)).
Klum should not validate examples against the generated schema or completed construction. The Schema Developer owns
usefulness: the null example above neither permits null input nor promises successful construction.
Examples never supply defaults, change requiredness/omission or validation constraints, run lifecycle/validation, select or
execute converters, or construct/execute Schema or Model code. Defaults, templates and owner/environment context supply
no examples. Existing documentation and constraint descriptions remain independent.

## Placement and resolution rules

| Source/position | Proposed output and authority |
| --- | --- |
| Direct eligible ordinary field, including supported List/Map | `examples` on the effective property schema; complete container payload, never `items`, map keys or `additionalProperties` values |
| Selected `@Key` field | String examples on the actual key property in member-key definitions only; absent from keyless bodies, outer keyed-map keys, fixed/derived identity and explicit-root-key inputs |
| Already admitted authored `@Builder.Method` with exactly one payload | `examples` on that operation's payload schema; Map/List stays one payload; never infer affected Model properties |
| Direct owned relationship | Complete child input object on the **parent property alongside `$ref`**; content uses that child's effective external names and member/keyless position |
| Keyed owned Map relationship | Whole outer instance-keyed object on the parent Map property; child bodies omit their outer-supplied key |
| Unsupported/excluded declaration or generated helper/factory/selector | No examples output and no new admission; examples never resurrect a member |

Only directly authored annotation occurrences count. No meta-annotation/default-only propagation, Bean Validation bridge,
Jackson example bridge, getter/parameter/type-use examples, annotation inheritance or automatically copied overload facts.
METHOD targets are for authored admitted operations, not arbitrary Model getters. A marker on an unsupported method
cannot make it serializable. No per-item or owner-dependent child-definition policy is introduced.

Resolve existing admission, hierarchy selection, naming and exclusions first; examples follow the **same exact effective
source identity**, not a method name or transformed token. Inherited fields/operations retain their declaration's examples;
an accepted replacement/override uses only its own examples, or none when absent. Never concatenate ancestor and local
annotations or synthesize clearing/merging policy. Existing ambiguous hierarchy/collision errors remain authoritative.

`InputName`, compatible Jackson literals and `InputNaming` change the property token, never payload text. JSON object
keys inside examples are literal author responsibility, including inherited examples under a first child profile.
`InputIgnore`/Jackson exclusion suppresses output. Equivalent names coalesce and conflicting names fail even when excluded,
as today. A deprecated effective source can have both `deprecated: true` and `examples`; `ConstraintProjection.OMIT` or
`TEXT_ONLY` does not suppress examples. Generated compatibility deprecation and class deprecation add no source examples.

Owned types retain examples on **their own member declarations** in reusable definitions. Parent relationship examples
never enter either shared child definition or sibling uses. Ownership/Owner/LINK annotations supply no examples;
unsupported LINK inputs stay unsupported. No root-object or whole-type examples are selected by this v1.

## Constant transport, completeness and failure

Extend existing `GeneratedInput.Field`, `Key` and `Operation` with one explicitly emitted `String[] examples` fact;
absence in source emits `[]`, not a missing element. Preserve the nested `Schema.keyMember` route when the public key getter
is suppressed. A method carrying both field and authored-operation facts needs **separate arrays on those facts**; do not
reuse one method-level array or reconstruct source annotations. Existing declaring Builder/field or full operation
signature plus artifact provenance is sufficient; no source paths or sidecar are needed.

Recommend advancing the **single** shared metadata protocol from 1 to 2, changing both consumers atomically. Both require
exactly the supported version and explicit well-typed example arrays throughout participating inherited/owned contracts,
including empty arrays and omitted owned inputs. Validate duplicate fact members, unexpected encodings, bounds and
conflicting duplicate origin facts; reject missing facts with Schema/Builder/artifact and recompile guidance.
Older readers reject the newer version instead of silently accepting lost examples. No per-feature version or fallback.

Compiler responsibility: fold constant Strings only; reject empty authored arrays, repeated direct annotations, nonconstant/
wrong-type values and transport bounds before emission, naming declaration and index. Consumer byte reading never
instantiates authored annotations or loads a Class/enum from a payload. The interpreter checks **inert transport facts**
and completeness only; it does not parse JSON or change admission/execution. This preserves its parser-free core.

Generator responsibility: validate every carried example array on a participating contract before filtering/projecting,
including ignored/deprecated/unsupported facts that remain on that contract. Excluded targets/unrelated classpath classes
are not traversed. Thus exclusion suppresses output, not malformed carried JSON diagnostics. Unsupported sources without
any surviving public fact carry no examples and are not scanned through Model bytes.

Use one private dependency-free JSON-value syntax reader in the plugin, accepting [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259)
values with complete-input consumption. Permit JSON whitespace, multiline whitespace, escapes and scalar roots. Reject
comments, trailing data/commas, NaN/Infinity, invalid numbers/escapes, BOM and raw controls.
Additionally reject unpaired Unicode surrogates as a stricter Klum interoperability rule: RFC 8259's ABNF can admit them,
but its §8.2 records unpredictable interoperability.
Reject duplicate **decoded** object keys (`"a"` and `"\u0061"` conflict), rather than selecting first/last. Preserve duplicate
example occurrences and nested array boundaries; semantic equality/deduplication is unnecessary.

A private validated-JSON-literal value in the existing writer can emit the checked text verbatim, preserving numeric
lexemes/precision and JSON null without double encoding, BigDecimal range limits or arbitrary raw insertion. Its constructor
must be reachable only after full syntax validation. No YAML/Groovy parsing, interpolation, substitutions, converter or
schema validation occurs. Ordinary generator values keep existing rendering. The current writer rejects Java null, so
blindly parsing into nullable generic collections and passing them to it is insufficient.

Malformed syntax, bounds or metadata fails atomically before output replacement, with declaration, external name when
resolved, artifact, one-based example index and UTF-16 character offset/reason. Do not echo an entire payload in diagnostics.
No warn-and-drop mode, truncation, schema-validity conflict resolution or runtime evaluation. JSON syntax failure is a
**generation** error; ordinary compilation and structured interpretation can still succeed with syntactically invalid
example text whose transport is valid. This timing distinction must be documented and accepted explicitly.

## Bounds and binary impact

Proposal: at most **16 examples/declaration**, **8,192 modified-UTF-8 bytes/example**, **32,768 bytes total/declaration**,
and **32 nested JSON containers**. Apply count/encoded-size limits before compiler emission and at byte consumption;
check JSON depth during generation. Bound measurement includes JSON quotes/escapes and uses classfile modified UTF-8,
not UTF-8 or character count. No chunking, resource loading, include file or examples registry.

These are small-example product limits, not JVM maxima. The JVM independently limits each Utf8 constant to 65,535 bytes,
annotation-array counts to a `u2`, and the shared class constant pool; many individually legal annotations can still overflow
a large Builder. Normal classfile-overflow diagnostics remain a hard failure; no per-class budget or split protocol is
promised. Equal strings may share pool entries; do not depend on that optimization
([JVMS §§4.1, 4.4.7, 4.7.16.1](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4.7)).

The annotation is binary-additive but the generated protocol change intentionally requires recompilation of participating
API/base, derived and owned Schema JARs with matching compiler/runtime/plugin. No older-artifact compatibility or
mixed-toolchain promise. Keep annotation/runtime dependencies and generated public support classes/resources in the
consumer classpath; sources, IDE mirrors, Model-byte annotation inspection and producer compiler outputs are unnecessary.
Existing public Builder signatures, Model APIs, lifecycle, materialization, ownership and serialization do not change.

## Evidence and proof boundary

Live #959 and merged #974/#975/#976/#977 were read on 2026-10-27. The last PR merged as the base above; current source
seams are `StructuredInputAdmissionPlan.enrichField/enrichKey/restoreAuthoredFacts`, `GeneratedInput`,
`CompiledBuilderSchema`'s exact-declaration ASM visitor/entry selection, and `JsonSchemaProjection.property/definition/json`.
Their owning modules are respectively compiler, runtime and Gradle plugin. Current producer and projector have no examples
contract; naming, deprecation and member/keyless placement already exist and are reused.

The completed AnnoDocimal investigation, `docs/implementation/structured-tag-transport-feasibility.md` at local commit
`1e482a2` on `codex/research-structured-tag-transport`, was read from that checkout (not published). It characterized ten Java/G3/G4/G5 capture paths: normalized prose
survives binaries, but payload folding/template processing, generated association and Java APT sidecars prevent a reliable
separate machine-tag protocol today. Future additive transport can feed canonical Klum facts after its own decision;
AnnoDocimal, Javadoc and mirrors are **not v1 dependencies**.

Current-master executable probe, JDK 17.0.3 and Groovy 3.0.25 / 4.0.32 / 5.0.6:

| Executed control | Result / limit |
| --- | --- |
| Current-master compiler/runtime build via temporary classpath task | PASS, 18 tasks; not root `check` |
| Disposable `probe.Examples` plus temporary instruction-selection folding onto actual generated public Builders | PASS all three lanes; 16 ordered payload facts/lane, including scalar, null text, nested object/array, duplicate string, inherited source, operation and suppressed-key type fact |
| Separate API/base/owned and child JARs; delete producer source/class directories; Java consumer with runtime/annotation/Groovy/AnnoDoc annotation JARs only | PASS all lanes; no compiler, Jackson, Bean Validation provider or disposable annotation definitions on consumer classpath |
| Unmodified current reader and projector over those JARs | PASS naming/exclusion/owned roles, inherited key, member/keyless refs and baseline output; **zero examples emitted**, as expected |
| Throwing Schema static initializer | Inspection and projection did not trigger it; explicit initialization afterward triggered the positive control in every lane |
| Proposed grammar/placement oracle, independent Python JSON reader | 11 valid / 13 invalid texts; duplicate decoded keys, surrogate, exact-number syntax and shared-definition locality controls; **not a production parser/projector test** |
| Independent Draft 2020-12 check of the placement oracle | Two metaschemas and seven baseline/example validity comparisons pass unchanged, including rejected null; not production output qualification |
| Annotation element controls | G3/G4/G5 accept constant String arrays, reject method calls and actual null; isolated Java binary retrieval passes |
| JVM per-string boundary (isolated javac 25.0.4.1) | 65,535 modified-UTF-8 bytes accepted, 65,536 rejected; earlier AnnoDocimal javac 17 evidence agrees; not a Groovy size-limit matrix |

The folded-vs-unfolded public Builder size deltas were **201 bytes Base, 65 Owned, 50 suppressed-key, 177 Child** on every
Groovy lane. These compare the disposable fact carrier over otherwise identical source, not a production budget estimate.
RUNTIME matches existing authoring/metadata conventions; CLASS could also support ASM retention, so runtime reflective
annotation access is not the reason for requiring it.

The folding hook and annotations exist only in the temporary harness. It proves feasible classfile transport on the real
Builder surface, **not** that the current compiler already supports InputExamples or that the proposed decoder, protocol 2,
JSON grammar, override rules or atomic failures are implemented. No user code ran in the inspection/projection phase;
this does not prove every future implementation or runtime construction is execution-free.

Local replay artifacts: `/private/tmp/d7-examples-probe/` (scripts, reports and JARs; `RUN.md` gives commands).
The retained local archive `/private/tmp/d7-examples-evidence.zip` includes those artifacts and isolated annotation-limit
controls; SHA-256 `454346609cd49a9d22c9507caf1408cb672cc5c71465e0eaf82c8f3085dd6b91`. It is an uncommitted local evidence artifact, not a distributed consumer fixture.

## Next slice and acceptance matrix

One medium vertical slice across annotations/compiler/runtime/plugin, with three reasoned commits:
(1) focused annotation, folded facts, single protocol advance and exact-reader checks;
(2) strict private JSON-literal validation/emission and use placement;
(3) packaged negative/documentary qualification and synchronized documentation. Keep tests with each behavior commit.
No separate registry/parser API or generalized projection framework is justified.

| Required acceptance group | Cases |
| --- | --- |
| Authoring/encoding | Scalars, quoted/empty strings, Boolean, JSON null, objects, arrays/nested arrays, multiline escapes, constant references/concatenation; reject GString/call/closure/actual null, empty/repeated annotation |
| Effective source | Inherited field/key/operation, unannotated replacement, distinct field/operation facts on one accessor, suppressed key getter, explicit/first-child profile, compatible/conflicting literals, ignore, deprecation, all three constraint policies |
| Composition | Two parents/sibling uses of one owned type, direct member key, fixed/provider keyless, keyed Map, inherited owned field, recursive owned types, omitted owned input, unsupported LINK/abstract/helper shapes; no definition leakage |
| Determinism/failure | Preserve payload order/duplicates; byte-for-byte reruns; duplicate decoded JSON keys, malformed JSON/Unicode, null writer path, invalid numeric syntax, extreme exact numeric lexemes, count/size/depth boundaries; no partial output replacement |
| Strict binary gate | Separate API/base, derived, owned JARs and no sources/mirrors/compiler on consumer path; absent/future/old version, missing/duplicate/wrong-type facts, ignored carried malformed facts, unrelated/excluded target not scanned; active Builder unchanged on gate failure |
| Qualification | G3 baseline plus G4/G5; parser corpus; packaged Gradle cache/output-atomicity regressions; initialization traps with positive controls; documentary `@Issue('959')`, `@Tag('documentary')`, `@See`; normal full checks/reviews at implementation end |

Update ADR 0031 only upon approval; then annotation Javadocs, `docs/user/Json-Schema.md` (syntax, placement, grammar,
advisory limits), `Structured-Input.md` (metadata-only/no runtime semantics), `Migration.md`/Builder-first navigation,
`CHANGES.md`, implementation evidence and linked issue reporting. No current user page or release claim changes in this
probe; no milestone, issue state, PR, package policy or #962 default semantics is assigned.

## Maintainer choices and handoff

The remaining product decisions are small but real:

1. Approve the explicit `InputExamples(String[])` route now, replacing the old AnnoDocimal-dependent deferral, or keep
   examples deferred. Spelling, FIELD/METHOD scope and raw JSON values are proposed together.
2. Accept syntax-only advisory generation with hard failure (including ignored carried examples), no schema-validity
   check, and the explicit compile/interpreter-versus-generator timing above.
3. Accept the proposed count/size/depth caps and single protocol advance/recompilation boundary, or choose different
   concrete bounds before implementation.

**#959 has a technically bounded next slice, but it is not implementation-ready until these choices are accepted.**
Research execution is complete; evidence is local, no remote delivery is required by this assignment. Hive reconciliation
is requested; the worker does not mark itself archive-safe. The completion callback supplies final commit, worktree,
validation and local evidence bundle. No runtime-evidence stream was authorized; run-duration/quota audit is unavailable.
