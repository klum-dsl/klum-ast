# #959 / PR #977: one required structured-input metadata protocol

Authority: the maintainer's 2026-10-27 protocol-simplification assignment. Follow-up base:
`413bbacd25273dc3e7789fd1a3df48a1a75fe752` on `codex/959-literal-input-names`; original PR base:
`bd39f085423dc747e7429dcd5ebd8fc656a78601`. Related: #959. This supersedes earlier unreleased metadata compatibility
choices in #972–#977; it does not change the authoring vocabulary or valid-Schema semantics.

## Protocol and deletions

The compiler explicitly emits `GeneratedInput.Schema.version=1`. The reader requires exactly 1, and does not apply
annotation defaults to missing classfile facts. Missing/older/future/malformed versions fail with the affected
Schema/public Builder, artifact where available and recompilation guidance. Participating inherited and owned contracts
use the same rule. The interpreter reuses its per-read plan cache to check the admitted ownership closure, including omitted
children, before execution. Caching before following edges closes cycles; excluded targets and unrelated classes are not followed.

Removed:

- `constraintVersion`, `decimalVersion`, `projectionVersion`, `namingVersion`, `literalVersion` declarations, emission,
  decoder state/cases and separate version checks.
- Identity-only acceptance when naming facts were absent, and selected-profile-only ancestry completeness requirements.
- Legacy operation-name/exclusion reconstruction, raw Jackson/canonical annotation scanning for that purpose, and
  inferred name-origin fallback. Current compiler-folded Jackson bridging is preserved.
- Literal-version-zero acceptance and optional older source-deprecation provenance. Required provenance now becomes
  primitive boolean state in validated entries rather than an unknown/absent result.
- Unknown direct-key-authority positions and partial use of missing key facts through explicit-root/Map routes.
  Current key/header consistency is checked once in the shared reader. Valid key positions and externally supplied
  identity remain supported.

Required current facts are still validated independently of the version's presence: naming explicitness/profile and
hierarchy consistency; literal origin/name/exclusion consistency; source provenance; constraint group and field facts;
known field projection policy; exact numeric fact encodings and decimal syntax; selection/role/key authority and source
association. Neither `OMIT` nor an identity profile relaxes completeness. Contradictory numeric assertions remain valid
facts and are projected faithfully; no satisfiability analysis or runtime constraint enforcement is added.

The byte reader remains the sole authority. Missing facts are never inferred from Model bytes, source mirrors,
annotation proxies/defaults or Schema execution. Contract validation precedes interpreter execution and generator output
replacement. Constraint projection, numeric assertions, runtime validation/admission and construction lifecycle stay distinct.

## Retained current requirements

No genuine external metadata compatibility requirement was found that contradicts the assignment. Two pieces are
necessary for current producers and are retained: `Schema.keyMember` for a construction-key getter suppressed by a runtime
name overlap, and separate field/key versus authored-operation name records when sources share one public accessor.
Neither is a legacy adapter. Existing deprecated construction APIs and old output-manifest filename cleanup are unrelated
to generated metadata compatibility and remain outside this task.

## Test replacement and evidence

The inventory removed compatibility-success tests for old identity and literal contracts, feature-version-specific
negative rows/mutations, and handwritten old key/unknown-authority fixtures. Existing key-boundary tests now require
rejection for both member and external-root inputs, and for both direct and Map payloads, when current facts are missing.
No old acceptance rule is recreated to preserve a test.

One shared source-free protocol matrix covers missing, older, future and malformed versions on API/base, derived and
owned JARs across G3/G4/G5. Positive packaged naming/literal cases keep independently compiled API/base, derived and owned
artifacts with deleted source/compiler outputs and runtime-only consumers. Correct-version corruption cases check required
literal, naming, constraint, projection, decimal, key and source-provenance facts. Interpreter rejection, initialization
traps/positive controls and preservation of previous schema output remain asserted.

Initial focused failures were expected old-contract assertions: missing key facts previously permitted explicit-root/Map
use; those tests now assert strict rejection. A malformed-version diagnostic was made explicit with artifact context.
The first affected schema run passed 92 of 93 cases; its wrong-Model fixture used an unversioned empty marker, so the
strict protocol gate correctly rejected it before the intended identity assertion. That fixture now supplies the current
required header and still checks the original wrong-Model diagnostic. No assertion or protocol check was weakened.
The new paired numeric-fact test also exposed one missing expected-error string in its existing test control; it was
corrected to recognize the actual missing-decimal-fact rejection.

Focused qualification has passed 172 compiler/runtime cases per G3/G4/G5 lane and the paired required-fact controls.
The final full qualification and independent reviews are recorded below before delivery. A clean checkout is held frozen
through root qualification so Nebula's outer/nested fixture versions cannot diverge.

## Complexity and delivery

Five independently optional version values become one exact-version gate. Raw-annotation reconstruction state and its
fallback dispatch are deleted, as are nullable validated provenance/authority states and the unknown-authority consumer
branches. The added checks enforce required current facts in the existing reader; there is no registry, adapter, version
negotiation, migration layer or replacement protocol abstraction. The shared reader shrinks from **1,165 to 1,140 lines** with **154 to 149 explicit `if` statements**. Across reader,
interpreter and projector, **27 lines and 8 explicit `if` statements** are removed. The two compiler enrichment classes
also lose four marker emissions; the generated annotation loses four version fields. This simple lexical count is not a
formal complexity score: the material reduction is one supported protocol state, zero legacy acceptance routes and fewer
consumer states, while strict current-fact validation is centralized rather than negotiated.

Current ADR, Structured Input, JSON Schema, migration, changelog and directly affected implementation briefs state the
new recompilation rule. Earlier qualification records remain explicitly historical. The original PR's three Sonar
maintainability observations are addressed: the nested name-origin fallback is deleted, and repeated origin terms use
named constants. Full PR CI/Sonar remains authoritative; no fast pre-PR scan is uploaded to the shared project.

Delivery updates existing PR #977 with additive commits and one consolidated follow-up. No merged PR, issue/milestone,
authoring API or release placement is changed. Do not merge; Hive archive reconciliation remains separate.


### Independent review correction before full qualification

Standards review of `fce804ebb55ed6997c39279d48ac9233f1298117` against the follow-up base reported zero findings.
Specification review found one P2 gap: the original interpreter checked an ordinary direct owned contract only when a
nested value was supplied. A direct Java 17/Groovy 3 probe (`/tmp/977-owned-review.groovy`) demonstrated root initialization
and successful empty input despite a reachable Child protocol changed to 2; supplied child input rejected it.

The correction extends the existing preparation plan cache: store each decoded contract before following only admitted
nested-object payloads. No new graph abstraction/cache/registry is introduced. This required bounded closure validation
matches the generator's definition traversal, handles recursive types and leaves excluded/unrelated targets untouched.
Core regressions cover omitted, transitive and inherited-owned incompatibility, a live root initializer control, active
Builder non-mutation, valid cycles and an excluded incompatible target. The packaged protocol matrix now supplies empty
root input for incompatible owned-artifact cases, removing its earlier dependence on supplied child data.


The owned-closure focused check passes on Groovy 3, including omitted direct/transitive/inherited-owned rejection,
active Builder non-mutation, valid cycles and an excluded incompatible target. The 10 packaged protocol rows also pass
with omitted child input. This correction was reviewed before starting the sole full-root qualification for the follow-up.


Both independent reviewers confirm **zero remaining actionable findings** at
`83df285e2e058b1e13391258fd49d9be827afc33`. Specification independently reran the direct probe: omitted and supplied
incompatible owned contracts both reject, root initialization stays untouched, and the initializer positive control
passes (`/tmp/977-owned-review-after.groovy`). The full-root run follows this review with tracked files frozen.
All **205 relative Markdown targets/anchors** and balanced fences across the changed documentation pass; diff checks pass.
