# #959 D7: inherited structured-input naming profiles

Authority: profile-only implementation assignment after merged [PR #975](https://github.com/klum-dsl/klum-ast/pull/975),
based on `bcaa5f00713ed45e70e84a20622a395683e239f0` (`origin/master`). Related: #959.
The [accepted decision](issue-959-d7-external-naming-decision.md) defines semantics; this assignment deliberately defers
`InputName`/`InputIgnore` while preserving the existing Jackson literal override/exclusion bridge.

## Bounded probe before production changes

Two test-only `CompiledBuilderSchemaTest` features ran on Java 17 / Groovy 3, 4 and 5 before production edits:

- `D7 binary probe retains native candidates before identity collision pruning`: separately compiled base/owned, child
  and grandchild JARs, deleted compilation outputs, isolated runtime-only consumer; public fields/selections retain both
  colliding candidates, native names, exact literal overrides, ignored facts, inherited key and authored-operation names.
  An initializer trap stays untouched; a separate positive control initializes the binary producer and proves it live.
- `D7 binary probe folds explicit type choices onto public Builder contracts`: test-only compiler folding records version,
  explicit presence and profile Strings on a disposable public annotation. Separate artifacts preserve absent ancestry,
  first introduction, unannotated/equal/conflicting descendants, explicit identity and independent owned type facts.
  This probes transport rather than claiming that the unmodified production reader already implements profiles.

Both features passed on all three lanes; no counterexample to the accepted contract was found. One initial command was
stopped after Gradle applied its filter only to the final task; the corrected per-task filters ran the bounded cases.
Test-only annotation resolution and a Groovy reflection/class-loading assumption were corrected before qualification;
class loading is distinct from initialization. Production annotation-proxy evaluation is not introduced.

## Selected metadata and resolver

| Fact | Encoding / responsibility |
| --- | --- |
| Completeness | `GeneratedInput.Schema.namingVersion=1`, independent of numeric/decimal/field-policy versions |
| Explicit selection | Explicitly emitted `namingExplicit` Boolean on every declaring Builder |
| Declared profile | Explicitly emitted inert `namingProfile` String, `IDENTITY` for absent declarations |
| Provenance | Exact public Builder identity, corresponding Schema type and verified artifact/resource origin |
| Entry names | Existing native field/key names and public authored-operation names plus existing literal/exclusion facts |

No extra per-member descriptors or profile annotations on Model-byte readers are needed. Both interpreter and projector
consume `CompiledBuilderSchema`'s resolved result. It resolves hierarchy choices first. Identity uses the original recursive
collision pruning; nonidentity collects native candidates before pruning, applies the one bounded ASCII algorithm to
native eligible tokens, and rejects deterministic collisions with both sources. Literal names are never transformed.
Key naming uses the same effective type profile; key collisions remain dependent on member versus external-key positions.
The originating declaration keeps numeric policy, deprecation and documentation association.

An explicit choice binds descendants, including identity; equal repetition is allowed. First introduction over absent
ancestry names inherited members while the standalone ancestor remains native. Owned types resolve independently even
when reached through two owner fields. No ownership cascade, mutable policy, caller selector or plugin option is added.

Unknown versions/profiles, missing explicit/profile facts in version 1, inconsistent absence facts and missing naming
ancestry under a selected profile fail with source/artifact recompile/package guidance. Complete legacy identity facts
remain supported where no selected feature requires unavailable provenance. Matched tools and Schema recompilation are
required for selected hierarchies; no blanket compatibility claim is made for arbitrary old artifacts or erased facts.
Numeric/decimal/projection completeness is neither relaxed nor coupled to naming.

## Preserved Jackson bridge and limits

Existing Jackson-only behavior is unchanged: `JsonIgnore` with absent value/true excludes, then nonempty `JsonProperty`
provides an exact literal, then native/profile fallback. Empty property value falls back; false ignore is neutral.
Fields, construction keys and admitted one-payload authored operations retain source selection. There is no native alias,
resurrection of structurally excluded declarations, or transformation of data/instance keys, fixed values, enums or Map
operation payload keys. Managed Jackson import, mapper strategies and broader `JsonNaming` remain independent.

`InputName`/`InputIgnore`, package/root/relationship configuration, examples transport, ConstraintProjection changes,
defaults, Bean Validation/provider SPI and additional grammar remain deferred. No issue is closed or reprioritized.

## Qualification and delivery

Focused runtime/compiler and packaged plugin results, full root verification, independent Standards/Specification review,
final branch/base/head, delivery authority, PR and final-head CI/Sonar status are recorded below once complete.
The packaged fixture uses separate API/base, derived Schema and owned-value artifacts; sources, mirrors and compiler
outputs are absent from the consumer, whose classpath contains no compiler or databind. The happy path compares exact
closed-schema properties with real interpreter acceptance and rejects native aliases. Negative cases retain prior output
and active Builder state, and use execution traps with positive controls. Malformed UTF-16 is tested as actual classfile
annotation Strings rather than assumed representable source identifiers.

Documentary traceability:

- `InputNamingProfileTest#'choose kebab member names while keeping owned values and data keys independent'`, `@Issue('959')`,
  `@Tag('documentary')`, `@See` to [Structured Input](../user/Structured-Input.md#type-naming-profiles).
- `KlumJsonSchemaGenerationTest#'resolve input naming from source-free hierarchy contracts with Groovy #generation'`,
  the same markers linked to [JSON Schema](../user/Json-Schema.md#type-naming-profiles).
- [Migration](../user/Migration.md#type-naming-profiles), [ADR](../adr/0031-structured-schema-input-artifacts.md#d7-type-profile-implementation-slice--959)
  and [changelog](../../CHANGES.md) describe only delivered profile behavior. The feature issue remains open.
