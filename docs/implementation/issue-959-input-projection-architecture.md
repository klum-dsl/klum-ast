# #959: input-contract and constraint-projection architecture plan

Date: 2026-10-28. Status: **Design complete for review; decisions proposed, implementation unassigned.**
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[#971](https://github.com/klum-dsl/klum-ast/issues/971), [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Decision source: [ADR 0031 proposed P1–P5](../adr/0031-structured-schema-input-artifacts.md#proposed-contract-and-projection-separation--959).
The design assignment authorizes this analysis and local documentation only. It does not accept these recommendations,
resume Size, implement a feature, change a tracker or publish anything. Existing accepted behavior remains authoritative.

## Evidence and current behavior

Inspected master: `d232c81f88d513c8c65d9c67b1939dc717b00067`, fetched on 2026-10-28; includes merged examples/Pattern.
Earlier #959 issue comments are dated checkpoints: the later documentary-string simplification and unified protocol
supersede parsed examples, separate feature versions and global numeric switches.

| Owning source / pinned evidence | Consequence for this plan |
| --- | --- |
| [CompiledBuilderSchema](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/CompiledBuilderSchema.java): `read`, `collect`, `resolveNaming`, `metadata`, `constraintProfile` | One class owns loading, exact byte validation, effective resolution and data. `Entry` carries selected methods and source/name facts; the separate constraint call decodes metadata again. Its one-line class Javadoc and numeric-only profile Javadoc omit important invariants. |
| [StructuredInputConstraintMetadata](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputConstraintMetadata.java), [GeneratedInput](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedInput.java) | Compiler-frozen field facts, direct Pattern origin proof, canonical rendered descriptions and explicit completeness are distinct from executable annotations; no production parameter or custom-projector transport. |
| [JsonSchemaProjection](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/runtime/internal/JsonSchemaProjection.java): `constrain`, `contributeConstraints`, `projectBound`, `projectPattern`, `property` | Mapping, field policy, unsupported text, conjunction and rendering share one plugin class; operations explicitly bypass constraints. Explicit documentary examples are applied before constraints. |
| [StructuredInputSupport](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/StructuredInputSupport.java): `Preparation`; [unified protocol](issue-959-unified-metadata-protocol.md) | Per-read plans validate the admitted owned closure, including omitted children, before execution. Missing/stripped/malformed facts reject even under OMIT. Preserve source/artifact/recompile diagnostics. |
| [GenerateKlumJsonSchema](../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/GenerateKlumJsonSchema.java): `project`, `ContractLoader` | Compile-only runtime linkage; plugin/runtime private types share the isolated consumer loader. Only JDK String/collection results cross back. Projection failure precedes output replacement; filesystem publication is not a general transactional rollback guarantee. |
| SIZE-PROBE, local commit `da5e8bd059fcc80773d8a4556378c56178310cca`, `docs/implementation/issue-959-size-projection-feasibility.md` | Read at its exact commit; not copied into master. List/Map raw cardinality is plausible but undecided; String UTF-16 versus JSON character length, Set deduplication, sorted/custom containers and existing state block a blanket mapping. No executable Size metadata probe was run. |
| #971 local evidence commit `b0c0c27ebcec10cda0e242ff194a2b5806d9e0d4`, `docs/implementation/issue-971-input-parameter-feasibility.md`; current canonical [issue](https://github.com/klum-dsl/klum-ast/issues/971) | Read exact report and current disposition. Report predates accepted deferral. Test-only Min transport passes G3/G4/G5; full probe retains 0/3/3 failures in 13 cases per lane. Generated Factory hierarchy and G4/G5 duplicate type-use failures are not repaired. The issue remains Future/untargeted, not assigned to 4.2. |

Both local evidence commits are research artifacts, not merged/publication claims; their reports can be recovered with
`git show <commit>:<path>` in their owning evidence repositories. #971's issue body also retains the decisive findings.
No executable probe or Groovy suite was rerun for this documentation task.

## Recommended first implementation slice: contract extraction only

After explicit acceptance, assign S1 below from a freshly pinned master. Extract the exact-byte reader and immutable
validated declaration facts first; route **both** existing consumers through it, preserving the current facade until the
resolver/result extraction is complete. Do not make consumers learn decoder details or add pass-through public interfaces.
The normalized contract is an internal result, not a serialized descriptor, new published protocol or extension surface.

Responsibility flow (names provisional):

```text
compiler source facts -> generated public Builder bytes + public signatures
                                      |
                           BuilderContractReader
                    completeness and exact-source validation
                                      |
                           InputContractResolver
                   hierarchy / native candidates / naming / roles
                                      |
                           BuilderInputContract
                         /                     \
             interpreter preparation      plugin catalog + renderer
             then normal execution        Draft 2020-12 documents
```

Keep constraint extraction in two explicit places: the compiler captures source facts; the reader decodes/verifies those
facts. The catalog interprets only their approved projection meaning. Effective resolution binds facts to source identity,
never just to the last segment of an external name. Preserve separate field/operation facts sharing an accessor, inherited
versus replaced declarations, keys omitted at a particular use, and relationship annotations local to that use.

Natural seams are initially package-private in `runtime.internal` and the plugin's private implementation, matching
today's loader arrangement. No new Gradle module or JPMS export is justified for S1/S2. Preserve the compiler module's
existing emission seam. If a record is selected after extraction, require defensive copies throughout and document
identity/ordering/cache lifetime; store recursive ownership as stable contract references rather than cyclic values.
The result must have no reader, resolver, mutable lazy state or rendering methods. A final class remains acceptable.

## Custom constraint proposal and output contract

Illustrative Groovy syntax only: `InputConstraintProjector`, its package and `SemVerProjection` are **not shipped APIs**.
The example assumes separately authored `SemVerValidator` and `SemVerProjection`; it defines no SemVer regex or validator.
The advisory example does not establish whether prereleases/build metadata are accepted; that grammar needs qualification.

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.InputExamples
import com.blackbuild.klum.ast.schema.InputConstraintProjector // proposed
import jakarta.validation.Constraint
import jakarta.validation.Payload
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.annotation.ElementType

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.PARAMETER])
@Constraint(validatedBy = [SemVerValidator])
@InputConstraintProjector(SemVerProjection) // proposed class-valued association
@interface SemVer {
    String message() default 'must be a semantic version'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@DSL class Release {
    @SemVer
    @InputExamples(['1.2.3']) // explicit field example wins over handler advice
    String version
}
```

Prefer an optional, narrowly scoped build-tool projection contract if this is later approved. A bounded class-valued
member would refer to that contract, not Gradle implementation or Bean `ConstraintValidator` types. The meta-annotation
and minimal JDK-only request/result types can live together in that optional artifact; neither core runtime nor the base
annotation artifact should depend on Gradle, a JSON library or a Bean provider. This possible new artifact needs a
packaging/JPMS proof before acceptance; it is not introduced for the internal refactor. An unbounded `Class<?>` in the
existing annotation artifact avoids that dependency but loses compile-time handler checking; choose it only if that
tradeoff is justified. Do not revert to authored class-name Strings merely to evade the dependency decision.

Proposed first custom scope: direct singleton custom constraint on an admitted ordinary scalar field, empty groups/payload,
no group redefinition, composition/repeats/type-use/relationship/key/operation support. Freeze effective constant members
and annotation defaults at compilation, with explicit declaration identity and completeness, or reject the selected
projection as unsupported. Default capture must not use reflective static reads, class initialization or annotation proxies.
Do not claim today's description transport already contains these typed facts. Keep default-only annotation propagation
separate from custom direct-origin proof.

| Contribution | Proposed rule |
| --- | --- |
| Hard constraint fragment | Restricts the already admitted raw value at the exact property use. First scope permits only type-compatible scalar assertions: exact numeric bounds, qualified ECMA pattern, const/enum, and conjunction. Require finite, bounded JSON data and validate keyword/value/type shape. No `$ref`, `$defs`, `$id`, remote reference, custom vocabulary, parent/sibling property mutation, `required`, `default`, type replacement or language expansion. Container fragments need separate admission. |
| Combination | Conjoin with structural fences and every built-in/custom assertion using `allOf` where needed; no last-wins override or satisfiability analysis. A custom association does not replace Jakarta built-ins. Attach to the property use, never a shared child definition. |
| Description amendment | Append stable text after existing AnnoDoc prose and the canonical metadata-rendered annotation description; do not overwrite them or claim source spelling/whitespace/comments were retained. Use deterministic source/rule ordering and label user-supplied projection text. Never parse either text channel as a machine fact. |
| Advisory example | At most one documentary String, matching current `InputExamples` semantics; never parse it, turn it into a default or claim validity. Use only when no explicit nonempty field examples exist. An empty `InputExamples([])` currently means no examples, not a veto; treating explicit emptiness as a veto would require a new presence fact and decision. |
| Projection policy | PROJECT may call an explicitly selected handler and accept all three contributions. TEXT_ONLY calls none and retains canonical constraint descriptions; OMIT calls none and removes constraint-derived output. Existing ordinary prose, deprecation and explicit InputExamples remain independent. Required metadata validation always precedes these choices. |
| Unavailable/unsupported | No selection means descriptive fallback with a precise reason and no handler load. An explicitly selected but absent/incompatible handler, malformed facts, thrown exception or invalid fragment fails before schema/manifest replacement, with source/use/handler/artifact and cause. Never silently downgrade a selected failure. |

This is a **narrow JSON Schema projection extension**, not a generic validation engine or arbitrary schema-mutation SPI.
The handler author's raw-language correctness is a trust assertion: structural fragment validation cannot prove equivalence
to their validator. Require an independent accepted/rejected corpus for each sanctioned projector, including regex dialect,
full-match/search and Unicode cases. Do not infer SemVer or Kubernetes grammar from a class name.

Built-ins-only S1/S2 retains no user-code execution. Future custom mode requires both compiled association and a
build-owned explicit handler allowlist/classpath; neither transitive dependency presence nor a schema annotation is opt-in.
Initialization and execution are trusted build code, including possible ambient I/O; loader isolation is not containment.
Do not pass capability-bearing objects, auto-discover providers, or call handlers from compiler, interpreter or IDE.
Keep custom-mode task outputs non-cacheable/not up-to-date initially; only a later reproducibility proof may relax that.
Process isolation may bound crashes/timeouts but must not be marketed as a security sandbox. If this trust model is
unacceptable, stop with built-ins and descriptions; do not disguise execution as reflection-only metadata.

## Narrow future parameter corridor and IDE evidence

Prospective one-parameter conversion through an **already admitted Builder operation** (imports/domain types abbreviated):

```groovy
@DSL class ReleasePlan {
    Version version

    @Builder.Method
    void versionLiteral(@SemVer String raw) {
        version = Version.parse(raw) // domain parser must validate today
    }
}
// Intended future schema position: versionLiteral's raw String, not the Version property.
// KlumStructuredInput.readRoot(ReleasePlan, [versionLiteral: '1.2.3'], 'release.yaml')
```

The method's one String payload is in the existing structural language; the annotation has no current parameter-projection
or automatic before-body enforcement effect. `Version.parse` is illustrative application code. Start a future transport
probe with direct Min on Integer as already evidenced by #971; this String/SemVer example is the later use case, not that
first probe's acceptance result. Do not transplant completed `Version` field constraints onto `raw`.

An eventual admitted `Version(@SemVer String raw)` converter or custom Factory needs a separate #932 grammar/selection
decision. Freeze original executable descriptor, declaring artifact, parameter index and source-to-public mapping,
admitted operation identity, raw type, direct-origin/eligibility and explicit empty/present constraints. Require unambiguous
single selection, including inherited/overridden/bridge and default-argument cases; defer multi-argument, varargs and overload
alternatives. Projection-only work neither calls the operation nor changes preflight enforcement, Bean Validation or direct
Java/Groovy invocation. Parameter policy/defaults and binary completeness require a decision before production transport.

The #971 G3 Factory hierarchy failure and G4/G5 duplicate type-use failures remain explicit negatives. A source-fact probe
can be independent of executable validation; passing it is not evidence that those failures are fixed or the full issue
can be implemented. No ExecutableValidator integration or provider-rule relaxation belongs in this plan.

| Assistance | What is established / what remains future |
| --- | --- |
| Current Klum IntelliJ/GDSL | [Qualified native evidence](evidence/issue-805-portable-gdsl-intellij.md) and [support scope](../user/Portable-GDSL.md#qualified-ide-boundary): source/binary completion, navigation, Parameter Info, wrong-argument diagnostics in IDEA 2026.2.3. These concern public Builder types/signatures, not Bean parameter predicates. Existing GDSL contributes Create/Template members, closure delegates and recipe context. |
| General Groovy/IntelliJ mechanisms | [JetBrains Groovy assistance](https://www.jetbrains.com/help/idea/coding-assistance-in-groovy.html) documents type/data-flow completion and inspections. [Groovy 3.0.25 type-checking extensions](https://docs.groovy-lang.org/docs/groovy-3.0.25/html/documentation/core-semantics.html#_type_checking_extensions) can add compiler diagnostics, but need explicit integration. Neither source proves automatic SemVer/Bean-parameter value checks or IntelliJ support for a custom compiler extension. |
| Potential semantic assistance | An LSP/IDE plugin could consume inert parameter facts to show grammar/examples or check literal arguments; a GDSL documentation experiment could investigate display. Dynamic expressions remain unknown without evaluation. Native source/binary, G3/G4/G5 and selected IDE-version tests are required; JSON/YAML JSON Schema editor support is a separate channel from Groovy argument inspection. No editor executes custom handlers. |

No new native IDE observation was made in this task. “Unqualified” is not a claim that all third-party plugins lack the
capability. Current evidence supports type assistance, not the proposed semantic service.

## Import responsibility and sunset gate

The preference is conditional: new Klum-authored configuration should use Structured Import when it fits its grammar;
externally owned documents and consumers relying on mapper semantics retain managed Jackson Import. Parser ownership
is independent of binding. [ADR 0009](../adr/0009-jackson-interoperability.md) commits to managed import across 4.x.

| Capability | Structured Import today | Managed Jackson Import today / concrete migration evidence needed |
| --- | --- | --- |
| Names, aliases, mapper customization | InputNaming, InputName/InputIgnore and bounded JsonProperty/JsonIgnore bridge; no general aliases, mixins/views or mapper strategies | Aliases, mapper naming, mixins/views/access/unknown-property policies. Inventory each consumer's spellings and negative collision/exclusion cases; prove explicit rewrites or retain Jackson. |
| Polymorphism | Concrete admitted owned types; abstract selection and unkeyed relationship collections excluded | Supported Jackson polymorphic owned DSL binding. Prove closed alias/discriminator mapping, every subtype/container and unknown-id diagnostics before migration; no classpath inference. |
| References and identity | Construction keys and literal keyed owned maps; no raw LINK/reference lookup | Identity/object-id resolution and explicit LINK codecs preserve supported identity. Compare shared references/cycles, forward/unresolved references, keyed ownership and completed LINK context; no automatic inline-to-owned substitution. |
| Null, coercion and population | Supplied null rejected, strict scalar admission, generated population/order; converters/factories excluded | Caller null/content/coercion/merge/replacement and value codecs. Compare omission/null, numeric strings, collection overwrite/append, parser ordering/duplicates and custom codecs using real fixtures. |
| Diagnostics | Whole-tree structural preflight; import source and escaped JSON Pointer; later lifecycle causes stay ordinary | Syntax/mapping/I/O wrapping and Jackson path/line/column where available; stable managed diagnostic grammar. Establish replacement error timing, paths and cause preservation, not just positive values. |
| Templates and Builders | Root and apply-to-active-Builder; no readTemplate or standalone readBuilder facade | Four explicit modes, including value-only Template and active-session Builder creation. Prove equivalent callbacks/session/thread/sealing and Template reuse, or keep affected modes. |
| Parser ownership | Caller parses Map/List/scalars and rejects duplicate raw names before collapse | Caller-owned parser/tree/Map and mapper configuration, parser remains open. Test JSON/YAML parsing policies, syntax errors and input ownership separately from binding. |
| Export | No Structured Import export contract or round-trip guarantee | Ordinary completed-Model Jackson serialization, asymmetric output, LINK and Template rules. Retain export dependency/support even if any import mode later migrates; inventory custom serializers and output consumers. |

Evidence sources: [Structured Input](../user/Structured-Input.md), [Jackson Integration](../user/Jackson-Integration.md),
`StructuredInputDocumentaryTest`, `StructuredInputKeyDocumentaryTest`, `KlumJacksonImporterSpec`,
`JacksonCustomizationSpec`, `LinkIdentitySpec`, `JacksonYamlInteroperabilityDocumentaryTest` and
`JacksonImporterDocumentaryTest`. These establish today's contracts, not parity between them.

Sunset would require named consumer/version inventory, a pass/fail/not-used result for **every matrix row and mode**,
executable migration fixtures with negative diagnostics, documented adapters/costs for gaps, a supported migration window,
and an explicit compatibility/release decision consistent with 4.x commitments. A failed or unknown required row blocks
sunset for that consumer. Keep managed Jackson indefinitely where it remains the appropriate foreign adapter; this
proposal supplies neither parity evidence nor a deprecation date. `Create.FromMap` and the deferred FromStruct naming
work remain separate; input preference changes neither API.

## Conditional slice and acceptance map

Every row requires a fresh assignment; only this documentation artifact is delivered now. S1 is the explicit first slice.

| Slice / scope | Reasoned commit boundary and acceptance | Stop condition |
| --- | --- | --- |
| S1 — #959 / ADR P1, reader/result/resolver extraction | First commit: exact-byte reader + validated facts and both consumers, with regression tests. Second: effective resolver + immutable result (record only if justified), removal of duplicated profile reading, Javadocs and evidence. Preserve final schema bytes, admitted/rejected language and diagnostics. Use CompiledBuilderSchemaReaderTest/CompiledBuilderSchemaTest, naming/key/runtime tests and packaged KlumJsonSchemaGenerationTest; source-free inherited/owned artifacts, omitted children, cycles, malformed/suppressed facts, exact resource identity, initialization traps with positive controls, loader/JPMS and G3/G4/G5. | Any required admission, generated ABI/protocol, output or public dependency change; lost diagnostic/source identity; user-code execution; unavailable binary evidence. Resolve through design, never widen scope to make extraction green. |
| S2 — #959 / ADR P2, built-in catalog | Move only existing numeric/Pattern families and reasoned unsupported results into the internal catalog, preserving combination/policy in the renderer. One behavior-preserving commit with existing independent precision/Pattern corpora and all policy/name/use-site tests, followed by evidence/Javadocs if useful. Interpreter has no catalog dependency. | New mapping, weaker Pattern proof, numeric satisfiability logic or user registry appears. Size stays paused. |
| C0 — #959 / ADR P3, optional custom proof | Only after trust/API choices: isolated proof of class-valued association, compiler-frozen arguments/defaults, no handler initialization when unselected, explicit selection, fragment bounds/conjunction/policy/example precedence and deterministic diagnostics. Separate annotation/handler artifacts, no provider/runtime dependencies, malicious/throwing/invalid/missing handlers, source-free G3/G4/G5/JPMS, failed-output preservation and cache policy. Keep opt-in proof out of normal production tasks. | Handler auto-execution, string/prose reconstruction, dependency cycle, unsafe fragment scope or claim of sandbox/no-user-code safety. No public API or custom production implementation until proof and another decision. |
| P0 — #971 / #932 / ADR P4, future inert parameter proof | One direct Min parameter on an existing admitted Builder method, exact descriptor/index/source/type/completeness with binary inheritance; corruption and no-initialization controls on all lanes and applicable JPMS. One research commit; retain original #971 reds separately. The SemVer converter example is later and requires grammar proof. | Ambiguous source/overload/annotation identity, new structured admission, provider invocation or any enforcement promise. Full #971 remains deferred; this is not its acceptance completion. |
| I0 — ADR P4/P5, independently selected consumer evidence | Separate IDE experiment or named-consumer import matrix only when commissioned. Native observations for value diagnostics versus type completion; migration fixtures for every used matrix row. One evidence commit per question. | No actual observation/consumer inventory, required Jackson gap, or request to deprecate/remove without compatibility authority. |

All future production slices use focused G3 iteration, final affected G4/G5 lanes and required binary/plugin checks;
G3 alone cannot establish compatibility. New tests carry the driving `@Issue`, use `Test` suffixes and, for approved
user-visible behavior, a Groovy documentary test with `@Tag("documentary")` and `@See`. Reuse the existing source-free
generator fixtures and initialization controls; compare fixed expected outputs/corpora, not only refactored helpers.
Do not add pending/ignored tests for unaccepted proposals.

S1/S2 need implementation/Javadocs and ADR status updates; no invented user feature or release note. Any accepted custom
or parameter feature must update Json-Schema/Structured-Input/Validation and relevant converter/factory user pages,
Builder-First-Migration/Migration navigation, sidebar if pages change, CHANGES, and documentary/issue traceability.
An import preference or eventual migration needs an explicit approved update to Jackson-Integration and Structured-Input.
No current user page, changelog or tracker is changed to imply these proposals already shipped.

## Maintainer choices and completion boundary

1. **Recommended now:** accept the internal P1/P2 separation and commission S1 only. Decide record versus final class
   from the extracted interface, not before it. Size does not resume as a side effect.
2. **Recommended default:** built-ins only. Decide whether trusted, explicitly selected custom build code warrants C0;
   then settle meta-annotation name, typed handler/artifact, finite fragment vocabulary and failure/cache semantics.
   Recommended empty-example semantics preserve today's empty-means-none rule; an explicit veto is a separate change.
3. **Future only:** decide whether projection-only parameter evidence is worth commissioning independently of full #971;
   choose any converter/Factory admission and parameter policy separately. No direct-call or executable-validation promise.
4. **Recommended guidance:** prefer Structured Import within its grammar for new Klum-authored data, retain managed
   Jackson for foreign mapping. Approve guidance separately; no sunset without the matrix and compatibility decision.

Design execution can complete with these choices open because the deliverable is a proposal. Repository delivery remains
local and unpublished; Hive reconciliation and maintainer acceptance/publication disposition are external conditions.
Tracker impact is design context related to #959; #971/#932 remain independent and open. No issue closure, label,
milestone, curation/release placement, agent-policy change or production implementation is part of this task.

## Documentation validation and review

The documentation-only change passes `git diff --check`, balanced fenced-block checks and all 60 relative link/anchor
checks across the ADR and this plan. Groovy lanes and renderer/build suites were not run: no production, build, executable
fixture or rendered user-documentation input changes. Review compares the pinned base above with this branch's final
commit; the task handoff records the exact reviewed commit and final results.

The initial independent Standards review found no actionable issue. The independent Specification review found one
example-call omission: the prospective `readRoot` example lacked the current API's required source name. The follow-up
adds `release.yaml`, checked against `KlumStructuredInput`'s existing three-argument overload. No API change is proposed.
Both axes receive the corrected revision for confirmation; publication and maintainer decisions remain external.
