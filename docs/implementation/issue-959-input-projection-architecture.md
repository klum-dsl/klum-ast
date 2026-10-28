# #959: input-contract and constraint-projection architecture plan

Date: 2026-10-28. Status: **P1/P2 accepted; S1 qualified; S2 implemented under a separate assignment; P3 unaccepted.**
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959).
Decision source: [ADR 0031 P1/P2 decision and P3 proposal](../adr/0031-structured-schema-input-artifacts.md#proposed-contract-and-projection-separation--959).
The maintainer accepted P1/P2 and authorized recording that disposition and publishing a draft documentation PR.
P3 remains in scope because custom projection can affect this architecture; it is not an accepted extension interface.
Former P4 and P5 are separate investigations in [#981](https://github.com/klum-dsl/klum-ast/issues/981) and
[#982](https://github.com/klum-dsl/klum-ast/issues/982). The subsequent [S1 extraction](issue-959-schema-s1-contract.md)
is implemented and locally qualified under its own assignment. The separately assigned
[S2 catalog extraction](issue-959-schema-s2-catalog.md) is now implemented; Size stays paused.
The design-checkpoint inventory and prospective scopes below retain their original planning context.

## Evidence and behavior at the design checkpoint

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

The Size evidence commit is a local research artifact, not a merged/publication claim; recover its report with
`git show <commit>:<path>` in its owning evidence repository.
No executable probe or Groovy suite was rerun for this documentation task.

## Recommended first implementation slice: contract extraction only

P1/P2 are accepted. This section records the S1 plan, subsequently assigned and implemented in
[the S1 evidence](issue-959-schema-s1-contract.md); the later S2 assignment is recorded in
[the catalog evidence](issue-959-schema-s2-catalog.md). Extract the exact-byte reader and immutable
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

## Conditional slice and acceptance map

This table records the original sequencing. S1 and S2 are implemented under separate assignments; C0 still
requires its own decisions and assignment. Neither extraction admits a later slice.

| Slice / scope | Reasoned commit boundary and acceptance | Stop condition |
| --- | --- | --- |
| S1 — #959 / ADR P1, reader/result/resolver extraction | First commit: exact-byte reader + validated facts and both consumers, with regression tests. Second: effective resolver + immutable result (record only if justified), removal of duplicated profile reading, Javadocs and evidence. Preserve final schema bytes, admitted/rejected language and diagnostics. Use CompiledBuilderSchemaReaderTest/CompiledBuilderSchemaTest, naming/key/runtime tests and packaged KlumJsonSchemaGenerationTest; source-free inherited/owned artifacts, omitted children, cycles, malformed/suppressed facts, exact resource identity, initialization traps with positive controls, loader/JPMS and G3/G4/G5. | Any required admission, generated ABI/protocol, output or public dependency change; lost diagnostic/source identity; user-code execution; unavailable binary evidence. Resolve through design, never widen scope to make extraction green. |
| S2 — #959 / ADR P2, built-in catalog | Move only existing numeric/Pattern families and reasoned unsupported results into the internal catalog, preserving combination/policy in the renderer. One behavior-preserving commit with existing independent precision/Pattern corpora and all policy/name/use-site tests, followed by evidence/Javadocs if useful. Interpreter has no catalog dependency. | New mapping, weaker Pattern proof, numeric satisfiability logic or user registry appears. Size stays paused. |
| C0 — #959 / ADR P3, optional custom proof | Only after trust/API choices: isolated proof of class-valued association, compiler-frozen arguments/defaults, no handler initialization when unselected, explicit selection, fragment bounds/conjunction/policy/example precedence and deterministic diagnostics. Separate annotation/handler artifacts, no provider/runtime dependencies, malicious/throwing/invalid/missing handlers, source-free G3/G4/G5/JPMS, failed-output preservation and cache policy. Keep opt-in proof out of normal production tasks. | Handler auto-execution, string/prose reconstruction, dependency cycle, unsafe fragment scope or claim of sandbox/no-user-code safety. No public API or custom production implementation until proof and another decision. |

All future production slices use focused G3 iteration, final affected G4/G5 lanes and required binary/plugin checks;
G3 alone cannot establish compatibility. New tests carry the driving `@Issue`, use `Test` suffixes and, for approved
user-visible behavior, a Groovy documentary test with `@Tag("documentary")` and `@See`. Reuse the existing source-free
generator fixtures and initialization controls; compare fixed expected outputs/corpora, not only refactored helpers.
Do not add pending/ignored tests for unaccepted proposals.

S1/S2 need implementation/Javadocs and ADR status updates; no invented user feature or release note. Any later accepted
custom-projection feature must update Json-Schema/Validation and applicable migration/navigation, CHANGES and documentary
test/issue traceability.
No current user page, changelog or tracker is changed to imply these proposals already shipped.

## Maintainer choices and completion boundary

1. **Accepted:** the internal P1/P2 separation. S1 and the separately assigned S2 are implemented. The S1 evidence records its
   final-class decision; for later contracts, decide record versus final class
   from the extracted interface, not before it. Size does not resume as a side effect.
2. **Recommended default:** built-ins only. Decide whether trusted, explicitly selected custom build code warrants C0;
   then settle meta-annotation name, typed handler/artifact, finite fragment vocabulary and failure/cache semantics.
   Recommended empty-example semantics preserve today's empty-means-none rule; an explicit veto is a separate change.
P3 informs the refactor's design: keep inert annotation facts and source/use identity separate from mapping output and
handler execution. S1/S2 must not accidentally make the reader depend on a renderer or force a handler into runtime
preflight. This does not justify adding a public SPI, custom metadata format or speculative handler machinery now.

Draft documentation publication is authorized; PR review/merge, Hive reconciliation and P3 choices remain external.
The separate issues preserve the P4/P5 evidence and open decisions without making them part of this implementation plan.
No issue closure, release placement or production implementation follows from this split.

## Design-task documentation validation and review

This change affects architecture documentation only. Run relative link/anchor, fenced-block and diff checks; Groovy lanes
and build/renderer suites are unnecessary because no executable, build or rendered user-documentation inputs change.
The initial independent reviews were clear at `b49579d3faeab174a558d9b228695f1410be329a`; status-only acceptance edits
reused them. The substantive P4/P5 scope split requires fresh independent Standards and Specification review before push.
The PR follow-up records the final reviewed head and check results.
