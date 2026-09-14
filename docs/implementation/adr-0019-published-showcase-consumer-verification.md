# ADR 0019 implementation plan: Published showcase consumer verification

This is the dependency-ordered plan for proposed [ADR 0019](../adr/0019-published-showcase-consumer-verification.md)
and [#484](https://github.com/klum-dsl/klum-ast/issues/484). It authorizes no production KlumAST change, new public
coordinate, release, `klum-catwalk` repository creation, or AnnoDocimal change until maintainers accept the ADR and
create the consumer repository.

## Confirmed starting behavior and failure paths

| Concern | Confirmed seam | Consequence for the plan |
| --- | --- | --- |
| Current public proof | `release/consumer` is intended to resolve Maven modules and plugin markers, but its current 4.0.0 tracer asks for later `klum-ast-test-support:4.0.0` and fails. | Repair or version-scope its product manifest; keep it as availability proof and add executable consumer behavior elsewhere. |
| Schema plugin | `KlumAstSchemaPlugin` adds published compiler/runtime/test-support dependencies, sources/Javadoc variants, source-mirror task, and referenced compile classpath. | A direct consumer can test all of these through coordinates without copying framework build logic. |
| Source mirrors | `createKlumDslSourceMirrors` projects `*_DSL` only for IDE metadata; `javadoc` excludes them. | Run projection and Javadoc, then assert neither mirror becomes build/publication input. |
| AnnoDocimal | #99 needs the projection classpath; #100 demonstrates Javadoc-sensitive generated documentation parsing. | The direct fixture needs a nested external signature and legacy paragraph/tag Javadoc shape that traverse both paths. |
| Layer 3 contract | ADR 0018's tracer proves ordinary binary project test fixtures and a matched Groovy/Spock pair. | A later Layer 3 consumer owns CT-1 through CT-3; it does not imply #548 source composition. |
| Release proof | `RELEASING.md` requires a clean non-composite checkout and `REL-2` public resolution. | Candidate proof is pre-publication evidence; final public proof remains coordinate-only against real endpoints. |

## Target fixture contract

The repository has an immutable root coordinate manifest per run and a versioned baseline manifest per supported release
line. Together they contain the selected KlumAST version, line-specific expected product set, KlumAST plugin
ID/marker/implementation coordinates, resolved AnnoDocimal version, Gradle/JDK requirements, fixture revision, selected
baseline entries, and one of `public-release` or `candidate-maintenance` repository modes. The build fails if a requested
value is absent, an unapproved repository is consulted, or resolved module/plugin evidence differs from the manifest. It
must not retroactively require a coordinate, such as 4.0.0 test support, that was not part of the released line.

Candidate mode accepts only one complete candidate product: all candidate Maven modules plus the marker and implementation
for the plugin applied by the fixture have the manifest's same candidate version and resolve from its isolated candidate
repository. The consumer declares that repository through `pluginManagement` so Gradle performs ordinary marker-based
plugin resolution. A public marker or implementation, mismatched candidate version, or absent candidate marker makes the
run incomplete candidate evidence and fails it. `mavenLocal()` is excluded from every public or final candidate run.

The harness that proves this topology may first publish marker, implementation, and product artifacts to `mavenLocal()`.
It then tests marker lookup, implementation resolution, missing-marker failure, and mixed-origin rejection. Those runs are
bootstrap tests only: their evidence explicitly identifies `mavenLocal()` and they cannot satisfy the public/candidate
release-evidence acceptance check.

The direct fixture has one compact schema whose generated public signature reaches an external nested declaration and whose
Javadoc contains the compatible paragraph/tag shape from AnnoDocimal #100. Its checks are:

```text
clean -> createKlumDslSourceMirrors -> javadoc -> check -> verifyResolvedConsumerEvidence
```

`verifyResolvedConsumerEvidence` is consumer-owned. It records normal resolution data and fails if generated mirror
sources enter compile, Javadoc, or publication inputs. It does not inspect or use a KlumAST checkout.

The Layer 3 fixture has `:domain-api`, `:schema`, and only an optional minimal `:model` when a completed Model adds a
needed client seam. It chooses one Groovy/Spock pair centrally. The API test fixture provides the generic contract; the
Schema test supplies concrete construction. CT-3's future multi-Groovy extension uses isolated complete builds per pair,
not class-directory reuse. The topology contains normal binary project dependencies only.

## Thin implementation slices

### SC-1 — Establish `klum-catwalk` and the direct public baseline

Create the separate repository with one direct Schema fixture, root coordinate manifest, first versioned baseline manifest,
Maven Central/Plugin Portal only, and no composites, `mavenLocal()`, or local KlumAST dependencies. Add the small
nested-signature and Javadoc test shape. Run the full direct task graph with a fresh Gradle home.

Acceptance: a released KlumAST coordinate resolves normally; source projection, Javadoc, model test, and `check` pass;
evidence lists resolved product/plugin/AnnoDocimal coordinates and confirms no mirror-input leak. The baseline manifest
identifies this fixture as the first retained release-proof entry without making future ordinary showcases release gates.

Commit boundary: `Add catwalk direct published Schema baseline` with fixture, manifests, and executable evidence check.

### SC-1a — Make public-product manifests release-line aware

Before 4.0.x admission, correct `release/consumer` and any shared manifest so the expected artifact set is defined by the
selected released line. The tracer's 4.0.0 failure is evidence that the current source cannot stand as historical public
proof; it must either use a versioned product manifest or declare its test-support assertion only from the first release
that published that artifact. This is release-proof repair, not a justification to fabricate or backfill 4.0.0 artifacts.

Acceptance: the existing resolver passes against 4.0.0 with its actual published set and against the first
test-support-bearing release with that coordinate required; the failure is captured in a focused regression test or
manifest fixture. The direct showcase run follows the same line-aware declaration.

Commit boundary: `Scope public product assertions to released artifact sets` in the release-proof owner, separately from
showcase creation.

### SC-2 — Add isolated candidate-maintenance resolution

Add a candidate manifest and producer/consumer handoff that publishes an exact candidate's Maven modules, Gradle plugin
marker, and plugin implementation to a temporary repository. Use distinct producer and consumer directories and a new
Gradle user home. Configure both dependency resolution and `pluginManagement` with that repository through the manifest;
the fixture applies the candidate plugin by ID and resolves its marker normally. Prohibit `mavenLocal()` and composite
builds in the final candidate path. Retain manifest, marker/implementation and module-origin evidence, task reports, and
repository-content digest.

Bootstrap acceptance: a test may publish marker, implementation, and product artifacts to `mavenLocal()` and exercise
`pluginManagement` resolution, missing-marker failure, and mixed-origin rejection. Its result is marked bootstrap-only and
cannot be recorded as release evidence.

Final acceptance: a deliberately missing candidate module, marker, or implementation fails resolution; altered
coordinate/evidence entries, a public-origin plugin, and mixed candidate versions fail verification. A staged complete
candidate passes the direct task graph by coordinate only when its marker, implementation, BOM/product modules, and
manifest use one candidate version from the isolated repository. The consumer build has no file or project dependency
resolving into the producer checkout.

Commit boundary: `Verify staged KlumAST candidate as an isolated consumer` with negative and positive exercises together.

### SC-3 — Add the ADR 0018 Layer 3 contract lane

Create API and Schema consumer modules. Implement CT-1 through CT-3 exactly as ADR 0018 specifies: explicit
`java-test-fixtures`, explicit Schema fixture dependency and realization hook, and one shared selected Groovy/Spock pair.
Keep the direct fixture unchanged and do not add source-level Schema inputs.

Acceptance: the inherited API assertion appears in Schema test results; the generic fixture names Domain API types only; all
participating modules resolve the matching pair; removing fixture wiring gives a dedicated expected compile-wiring failure
in the test harness. If a 3/4/5 matrix is added, every pair recompiles and runs the fixture.

Commit boundary: `Run Layer 3 Domain API contract in showcase` with fixture and contract evidence.

### SC-4 — Wire baseline cadences without widening ordinary development CI

Run each affected baseline fixture on catwalk PRs using pins. Add scheduled/manual public revalidation with an isolated
cache, and a manual candidate-maintenance proof accepting only the exact coordinate manifest. A minor release runs its
selected current-line baseline and all retained compatible predecessor-line baselines. Preserve KlumAST's local CI and
`release/consumer`; invoke the candidate proof only from release candidate/maintenance orchestration after staging, then
retain its evidence in the release record.

Acceptance: catwalk PRs get fixture evidence; a scheduled run performs no writes; candidate invocation rejects an
unbound or incomplete manifest; ordinary KlumAST PRs never await the catwalk; release evidence links exact manifest,
baseline selection, source SHA, candidate repository digest, resolved marker/implementation/module origins and versions,
and result.

Commit boundary: `Run showcase consumer proof on explicit release inputs` with workflow tests or dry-run validation.

### SC-5 — Admit the minimal direct proof to 4.0.x

When the first accepted 4.0.x maintenance candidate exists, create a reviewed showcase `release/4.0.x` compatibility branch
from a passing direct-fixture revision. Pin the exact maintenance coordinate and run SC-2's candidate proof before release
approval and SC-1's public proof afterwards. Do not add CT modules or broad examples as a condition of admission.

Acceptance: compatibility branch/tag identifies its pin; candidate and public evidence are retained against the same release
identity; #522's KlumAST branch/promotion record remains independent and accurate.

Commit boundary: `Admit direct consumer proof for KlumAST 4.0.x` in the showcase repository, with a neutral
`Related: #484` relationship from any coordinating KlumAST record.

### SC-6 — Add curated showcases, migration rehearsals, and deliberate baseline promotion

Add documentation-focused direct-schema and Layer 3 journeys, migration rehearsals from supported prior lines, links from
current KlumAST user pages where useful, and presentation material. A complex showcase can become a regression baseline
only through a reviewed release-line manifest change that names the compatibility promise, task graph, and expected
coordinates. Otherwise its build may reuse stable fixture conventions but must not silently expand the release-required
task graph.

Acceptance: each showcase or rehearsal declares intended KlumAST pin and audience; every promoted baseline records its
release-line compatibility promise and passes in a fresh-cache candidate/public run; ordinary showcases remain optional;
user-facing documentation is reviewed as documentation rather than release proof.

Commit boundary: one coherent user journey or baseline-promotion rationale per commit; no presentation-only catalog change
is bundled with candidate-release wiring.

## Compatibility, ownership, and release policy

- `main` has one explicit next-release pin; its movement is proof-driven, not tied to every KlumAST commit.
- `release/<major>.<minor>.x` branches are explicit showcase compatibility commitments. They do not create, protect, or
  forward-merge KlumAST release branches; #522 retains that policy.
- Tags preserve the exact fixture and manifest that produced evidence. A historical tag is never rewritten to follow a
  later dependency pin.
- AnnoDocimal coordinate changes have their own manifest/review entry. A showcase failure reports the integration seam to
  its owner; it does not create an unpublished compatibility override.
- Engineering Baseline governs shared evidence and authorization vocabulary. `gradle-conventions` owns any later portable
  plugin decision. Neither owns showcase topology, publication selection, or an application's Domain API fixture.
- Candidate verification uses an explicit temporary Maven repository because it proves metadata and normal
  `pluginManagement` resolution. A successful producer build, project dependency, composite build, ambient Maven local
  repository, or IDE source mirror is never final consumer evidence. Maven local is allowed only in labelled bootstrap
  tests that establish the marker/implementation resolution topology.

## Risks and open questions

| Risk or question | Containment / decision needed |
| --- | --- |
| Candidate Gradle plugin marker or implementation cannot be staged or resolved from the isolated repository. | The candidate run is incomplete and cannot be release evidence. Prove the publication and normal `pluginManagement` route first through bootstrap tests using `mavenLocal()`, then through the isolated repository in SC-2. |
| The current public resolver asserts a post-4.0 artifact against 4.0.0. | Deliver SC-1a before treating 4.0.x showcase evidence as a release gate; do not redefine 4.0.0's published product. |
| AnnoDocimal #99/#100 are unresolved. | Pin observed released behavior first; coordinate upgrades are explicit compatibility runs, not hidden transitive updates. |
| Fresh public repositories are intermittently unavailable. | Record failure separately from product regression; do not fall back to local caches or sources. |
| A fixture becomes a real application. | Move teaching material to SC-6 and preserve the small gate's fixed graph. |
| A shared consumer convention looks attractive. | Require independently governed consumer evidence and `gradle-conventions` owner admission under published extraction gates. |

## Issue-to-slice map

| Item | Relationship |
| --- | --- |
| #484 | Canonical catwalk, consumer proof, showcase, migration-rehearsal, and baseline-promotion owner; SC-1, SC-2 through SC-6. |
| Public-product resolver repair | SC-1a prerequisite owned by the release-proof surface; related to #484 but not absorbed as showcase implementation. |
| ADR 0018 / #755 | Owns Layer 3 contract-test behavior exercised only in SC-3. |
| #548 | Deferred source-level Schema composition; explicitly excluded. |
| #522 | Owns KlumAST 4.0.x branch creation/promotion; SC-5 consumes release identity without changing it. |
| AnnoDocimal #99 / #100 | Upstream projection/Javadoc regressions SC-1 makes executable at a published-consumer boundary. |
| Engineering Baseline / `gradle-conventions#1` | Coordination/extraction constraints only; neither blocks SC-1 or SC-2. |
