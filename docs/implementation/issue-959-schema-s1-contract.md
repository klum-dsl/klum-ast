# #959 / SCHEMA-S1: source-free Builder/input contract extraction

Date: 2026-10-28. Authority: the maintainer's explicit S1 implementation assignment following acceptance of
ADR 0031 proposed P1/P2. Only P1/S1 is implemented. Related: #959; no closure candidate.

## Pinned execution and scope

- Independent base: current master `d232c81f88d513c8c65d9c67b1939dc717b00067`, fetched 2026-10-28.
- Branch: `codex/959-schema-s1-contract`.
- Accepted architecture evidence: proposal commit `b49579d3faeab174a558d9b228695f1410be329a`.
  The proposal worktree is read only; its branch is neither a dependency nor modified.
- All implementation/build work uses the isolated S1 worktree; the user's IDE-main checkout is unchanged.
- No compiler, generated annotations, public types, packages/modules, dependencies, projection mappings or task options change.
  P2's catalog/S2, Size, custom projectors, parameter facts/#971, IDE tooling and Jackson sunset remain outside S1.

## Baseline defined before editing

The preserved baseline is exact public Builder/Factory byte authority, supported protocol/fact completeness,
source/artifact diagnostics, effective source-associated fields/operations/keys and payload/population selection.
Native candidates survive naming resolution, independently of exclusion and prior identity collision pruning.
Interpreter preparation validates the admitted owned closure including omitted children; renderer traversal validates
owned definitions even when constraint output is suppressed. Recursive contracts use the existing consumer caches.
Neither preparation nor projection executes Model/enum initialization, constructors, defaults, lifecycle or validators.
Runtime interpretation, rejected language, current JSON Schema serialization and Gradle task/publication behavior stay unchanged.

Before the extraction was built, the pinned baseline passed **334** focused G3 compiler/runtime cases and **127**
packaged schema cases, zero failures/errors/skips. All **46** emitted schema fixtures were retained for byte comparisons.
The existing fixed-fact contract expectations remain the independent structural oracle; no expectations are regenerated.

## Responsibilities and result choice

`BuilderContractReader` owns exact resource/module/origin checks, inert ASM decoding, strict protocol/fact validation,
and public signature/documentation inventories. Mutable visitor state remains private. It returns copied immutable
Metadata/Literals/NameFacts snapshots and caches only successful declarations by exact Class identity for one session.
Required facts are validated before any naming or projection suppression; no Model implementation or annotation proxy
is an alternate authority.

`InputContractResolver` owns hierarchy membership, native candidates, effective profiles/literals, admission/collisions,
field versus authored-operation roles, key selection/authority and payload/population signatures. It binds existing
constraint facts to their declaring contract, without interpreting their supported projection mappings. Both production
consumers create a resolver for their existing preparation/projection session. Their owned-closure traversal, cache
reservations and error paths are preserved.

`CompiledBuilderSchema` is the normalized immutable result, with source identities, selected handles, payloads, key facts,
rejections, declarations and declaring constraint profiles. Every nested collection is copied, retaining deterministic
insertion order where used. Key collisions are computed during resolution; the result performs no loading, resolution,
rendering, execution or lazy mutation. Owned references remain Class identities, not cyclic result values.

**Final class decision:** retain the existing result's identity semantics and controlled package-private construction.
Its loader/session-local dispatch handles and owned-type references are not a value-equality contract. A record would
publish component-wise equality/hash semantics without improving the consumer interface. Smaller inert fact records
remain appropriate and defensively copy their lists/maps. No classloader/global cache or Gradle configuration state is added.

Javadocs now state the inspection/initialization boundary, completeness/source identity, naming/role/key separation,
owned references, collection ordering and cache lifetime. Constraint facts document `plain` as eligibility rather than
validity and the carried declaration as canonical descriptive metadata, never original source text.

## Focused coverage and acceptance map

Only two tests are added, both with `@Issue('959')`, in `CompiledBuilderSchemaReaderTest`:

- Immutable declaration snapshots reject mutation of the method/exclusion inventories and nested examples/constraint lists.
- Effective contracts expose copied profiles/fields/key and entry examples. One session validates each declaring Builder
  resource once across hierarchy naming, key selection and profile binding; another session reads resources again.
  A separate deliberate initialization proves the same binary trap is live.

The counter observes exact resource-origin checks (`getResource`); it does not instrument JDK Module stream internals.
The first test iteration incorrectly hooked `getResourceAsStream` and failed only this new counting assertion; correcting
that fixture hook required no production workaround, admission change or suppression.

| Acceptance area | Existing qualification retained |
| --- | --- |
| Exact fact/source output; binary inherited/owned contracts | CompiledBuilderSchemaTest fixed expectations, separate artifacts, shared accessors, hierarchy diamonds and raw generated SELF qualification |
| Missing/malformed/suppressed facts, policy and omitted children | Unified protocol/literal/naming/examples/Pattern corruption cases in both compiled and packaged suites; failed outputs/manifests remain intact |
| Roles, payloads, key positions and runtime language | StructuredInput construction/runtime/key/direct-key/mapping boundaries, InputNamingProfileTest, InputLiteralTest and InputExamplesTest |
| Cycles and per-read lifetime | Existing recursive owned/schema and runtime cycle cases, plus the new one-session/fresh-session identity counter |
| Source identity and no execution | Exact resource/shadow/missing dependency tests, initialization traps with positive controls in compiled and packaged suites |
| Plugin/loader/Gradle behavior | KlumJsonSchemaGenerationTest, including G3/G4/G5 binary producers, packaged G5 plugin, platform-parent isolation, task/output/configuration/build-cache behavior |
| Generated linkage/JPMS | Root check's generated/public contract and package-boundary suites plus the explicit G3/G4/G5 annotation-propagation artifact tasks |

## Initial full-check correction

The first root check reached the full G4 suite and failed one existing JPMS fixture with
`NoSuchMethodException: CompiledBuilderSchema.read(Class)`. Its test-only reflective probe still expected the old
static method, unlike the already updated ordinary bridges. The additive correction constructs the new package-private
resolver inside the same runtime module and reads the immutable result separately. Module declarations, accessibility
assertions, fixture outputs and the prohibition on portability workarounds remain unchanged. The existing feature gains
#959 traceability; no production change, compatibility bypass or test suppression is needed.

The corrected probe passes in all three Groovy generations. That qualification also executes the full core G3/G4
suites: 2,506 / 2,508 cases with zero failures/errors and 16 / 15 existing skips; G5's focused JPMS suite passes all
five cases without skips. The final full root check below still qualifies every required lane and module together.

## Loader diagnostic preservation

A standalone missing-runtime audit exposed a genuine extraction gap: allocating the new resolver outside the
projector's existing root-error handler bypassed its root-specific diagnostic. Allocation now follows root-name/type
loading inside that handler, preserving both lookup order and the existing message. An independent JDK 17 program
compiles the baseline and corrected projector and compares their complete missing-runtime diagnostic bytes: identical.
The existing packaged missing-runtime feature gains #959 and a root-message assertion; its focused G3 run passes.
No new test class, production mapping or loader-access workaround is added.

The intermediate root attempt was invalidated by editing these files during the build: the outer plugin marker used a
clean version while the nested source-mirror fixture published an `.uncommitted` version. The observed BOM lookup failure
matches those exact different version strings. That run was stopped and is not passing qualification evidence.
No source-mirror implementation, fixture or build configuration is changed. The correction is committed before the
next complete root check, with both HEAD and the working tree then held fixed until the build finishes.

## Final qualification and delivery boundary

The clean, frozen `50ad635aa1ff5839482fe0297a65b4edd3a91bf5` source/test tree passes JDK 17 full root
`check --no-parallel --max-workers=2` plus the CI-required annotation-propagation artifact tasks in **24m48s**.

| Lane | Cases | Failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 baseline | 3,389 | 0 / 0 | 17 |
| Groovy 4 compatibility | 2,756 | 0 / 0 | 16 |
| Groovy 5 compatibility | 2,756 | 0 / 0 | 16 |
| Propagation artifact G3 / G4 / G5 | 1 / 2 / 2 | 0 / 0 | 0 |

All **127** packaged JSON Schema regressions pass, including the strengthened loader diagnostic. Plugin validation,
licenses, test-lane isolation, generated/public contract/JPMS coverage and documentation renderer checks pass.
All **46** retained emitted schema fixtures have identical paths and bytes to the pinned baseline; the fixed compiled
contract expectations are unchanged. The canonical sorted path-to-SHA256 manifest digest is
`c8e8ccd767d23430f9cf1bac7eb2c71789c5869cb84d37897dfd345a9552cdb3`.
Markdown fenced-block/relative-file-link checks and `git diff --check` pass.

Independent read-only Standards and Specification children review the complete base-to-implementation range and both
additive corrections. **Standards: zero findings. Specification: zero findings.** Root remains the sole build owner;
no other implementation/research children are used. Reviewed commits are preserved and the history is checked for
focused reasoning steps and neutral issue references.

### Documentation-only master refresh

During qualification, master advances to `4fa253d6b2d4ec2da4945419bd9aa8b99fe81e57` by merging architecture documentation
PR #980. Its only changes are ADR 0031 and the design plan; it changes no compiler, runtime, tests, build inputs or user
behavior. An additive merge preserves the reviewed implementation commits. Its ADR conflict retains both the accepted
architecture and the subsequent S1 implementation record; current status links to S1 qualification. This consumes current
master, not the formerly unmerged proposal branch. Original execution base remains
`d232c81f88d513c8c65d9c67b1939dc717b00067`; the refreshed PR comparison base is the master commit above.

The refreshed branch's production/test/build tree is byte-identical to the qualified source/test tree. Only Markdown
changes after that qualification, so the documentation-only policy reuses the completed Groovy/plugin/JPMS evidence and
reruns applicable Markdown/diff checks. No extra full lane execution is claimed for this documentation-only merge.

### Analysis and publication

The current pre-PR policy is local tests and independent reviews followed by a draft PR; its CI owns the SonarCloud
analysis. The original S1 task brief erroneously retained an obsolete final-local-Sonar requirement. The maintainer and
Hive explicitly correct that brief and direct push/draft creation only, without a local upload or manual workflow
dispatch. No passing analysis or CI result is inferred from repository tests.

The earlier local scan attempt was denied by automatic approval review before execution because its external
source/analysis payload was not explicitly approved. No local source upload, analysis result or quality gate exists.
The maintainer's subsequent instruction is to omit that upload and proceed directly to the draft PR, rather than seek
upload approval or retry through another route. The final revision and PR are recorded in the task handoff.

Git SSH feature-branch delivery preflight and GitHub CLI repository capability are **authorized**. No credentials,
credential scopes or raw authentication errors are inspected or recorded. Draft publication is explicitly assigned;
tracker impact is **Related: #959**, with no closure candidate, state/milestone/label change, release placement or merge.
Hive owns final delivery/archive reconciliation; the worker retains the worktree while review/merge remain open.
