# #959 PATTERN-PROJECTION implementation and qualification

Date: 2026-10-27. Bounded implementation rebased onto merged D7-EXAMPLES. Related: #959; tracker state is unchanged.

## Authority and scope

[Accepted maintainer decision](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6442779190).
The evidence-only policy is retained at commit `5b217f1af71ab175999016b3b8670ff4f376e8bd` on
`codex/959-pattern-projection-policy`, file `docs/implementation/issue-959-pattern-projection-policy.md`.
Its proposed protocol bump is superseded by the decision: keep `GeneratedInput.Schema.version=1` during 4.2 development.
[ADR 0031](../adr/0031-structured-schema-input-artifacts.md#conservative-pattern-projection--959) records the decision.

Implement only direct singleton Jakarta Pattern on admitted ordinary String fields, with the exact ASCII-BOUNDED-1
finite grammar, bounds and paired-anchor rule. Emit anchored assertions and the typed printable-ASCII guard in conjunction.
Complete unsupported forms retain whole deterministic runtime declarations. No flags/groups/payload/composed/repeatable/
non-direct hard mapping, keys/operation/container/non-String mapping, execution, new SPI/flags/default/required inference,
interpreter validation or general regex translation is introduced. PROJECT/TEXT_ONLY/OMIT and structural/naming/source/
inheritance/owned-locality rules remain independent. D7 examples code is untouched.

## Owning seams

- Compiler `StructuredInputConstraintMetadata`: explicitly carries decoded regexp and flag emptiness for direct Pattern,
  including unsupported forms. Groovy's one-argument inert AST/ConstantValue folding retains static imported Strings;
  no reflective value read is introduced. The existing KlumCast contributor returns a transient SOURCE marker retaining original field annotation identities
  before collector expansion, without mutating the inspected declaration. It activates only for direct Pattern or
  AnnotationCollector source uses on eligible original fields, separately from default-only propagation; other no-request
  declarations remain untouched. Copied/aliased Pattern facts remain descriptive
  and retain the authored alias declaration. The marker is not emitted to binaries/public contracts; compiler activation
  and JPMS services remain unchanged, with no global transform or new SPI.
  Captured declarations deterministically escape controls and surrogates.
- `GeneratedInput.Constraint` / `CompiledBuilderSchema`: add typed inert fields and strict completeness/type checks on the
  exact public Builder bytes before either consumer runs. Multiple Pattern records cannot claim singleton eligibility.
  Suppression never supplies missing facts. Existing protocol,
  naming, projection, numeric and ownership-closure checks remain active.
- Private plugin `BoundedAsciiPattern`: complete-input finite recognizer, no regex compilation. All lexical tokens,
  bounds/length/piece/maximum ceilings and outer anchors are checked before emitting a hard assertion.
- `JsonSchemaProjection`: original eligible String property placement, empty groups/payload/flags/hierarchy checks,
  anchored pattern plus typed `not` ASCII guard, and stable fallback reasons. Conjunction appends preserve prior `allOf`.

## Pre-rebase qualification (local checkpoint)

Focused Gradle plugin tests pass: 53 recognizer cases plus 18 packaged source-free cases (three G3/G4/G5 producers and
fifteen corruption scenarios). Fixtures package independent API/base, derived Schema and owned Schema JARs, then delete
source and compiler class outputs. All seven flags, empty flags, static constant fallback, groups/payload, sequence,
collector aliases, whole composition/repeat and singleton containers, invalid regex source, wrong types/containers/keys, inherited literal names,
exclusion, child locality, ordinary prose/deprecation, all field policies, cache reuse and positive initialization traps
are covered. Corrupt root/TEXT_ONLY/OMIT/inherited/omitted-owned facts reject in both consumers before execution and
preserve prior schemas and manifest. A separate interpreter control proves raw Pattern does not become input validation;
omission remains allowed and supplied null rejects structurally.

[Independent emitted-schema oracle](evidence/issue-959-pattern-projection/qualify_emitted.py) uses a hand-authored
14-source corpus and actual emitted property fragments, not the production recognizer as its expected-value oracle.
All three producer schema documents are identical and valid Draft 2020-12. JDK 17 full matching, Node 26.12.0 plain/Unicode
RegExp and Python jsonschema 4.23.0 agree on 27,211 Strings; 54,422 ECMA and 27,211 schema String checks pass.
The 66 retained P2/malformed Java controls and 75 fragment neutrality/admission controls pass. Additional full-document
checks cover null/omission, final LF, wrong types, inherited/owned names and exclusion. The naive final-LF leak is
positively reproduced, the guard closes it, and bare-not null rejection is positively demonstrated.

Reproduce after packaged tests:

```shell
/tmp/klum-959-d7-venv/bin/python docs/implementation/evidence/issue-959-pattern-projection/qualify_emitted.py \
  --java-home /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  --repo . --schemas klum-ast-gradle-plugin/build/schema-validation
```

Supply equivalent pinned tools rather than depending on that local virtual environment. Oracle helper classes are
created only in a temporary directory. Final results are recorded below and in the linked result artifact.

Initial independent review found missing collector-origin proof and singleton eligibility consistency; both are addressed
with compiler-origin retention/shared-reader validation and explicit regressions. Standards requested braces and clearer
projection result states; both are addressed. Both final review axes report zero remaining findings at the final implementation checkpoint recorded below.
A later full check caught ADR 0014's explicit no-global-transform invariant; origin capture was corrected to use the
existing KlumCast handoff and a SOURCE-only internal fact. The module-contract test is preserved, not weakened.
An initial full check was deliberately stopped after a mid-run documentation commit caused nested source-mirror fixture
publication to use a different inferred version from the built plugin. That run supplies no passing evidence; the clean
fixed-head qualification below replaces it. No unrelated source-mirror behavior change is made.
The clean final implementation checkpoint `ef7d13d881b1ce63fcbd0061a7c375e450fb6f22` passes Java 17 full root
`check` in 13m51s. Groovy 3/4/5 totals are **3365 / 2743 / 2743**, with **zero failures/errors** and **17 / 16 / 16**
existing skips. All **116** packaged JSON Schema regressions and **53** recognizer cases pass without skips. Root checks
include licenses, lane isolation, plugin validation, JPMS boundaries and the complete module suites. No existing test
or module-service contract was weakened.

The additional explicit `annotationPropagationArtifactsGroovy3`, `annotationPropagationArtifactsGroovy4` and
`annotationPropagationArtifactsGroovy5` matrix passes in 45s, with **1 / 2 / 2** executable matrix cases and no failures,
errors or skips. These tasks are intentionally outside root `check`. Their internal artifact rows qualify actual
classpath/named-module compiler and consumer arrangements. The contributor source change is therefore covered beyond
ordinary unit/lane tests.

The independent oracle was rerun against the successful full run's emitted schemas with the same passing counts above;
[recorded result](evidence/issue-959-pattern-projection/qualified-result.md). Added relative documentation links,
Markdown fences and `git diff --check` pass. Both independent Standards and Specification reviews have zero remaining
findings. The final code review covers bounded parsing, typed facts, activation/origin proof, field policies, source
placement, binary transport and atomicity. The unpublished corrective history was simplified before final qualification;
its abandoned global registration is absent from the final history/diff. A final evidence-only documentation commit
follows this qualified production/test tree.

At the pre-rebase checkpoint no CI/Sonar upload, push, PR or tracker mutation had been performed.

## Documentary and delivery record

Documentary test: `KlumJsonSchemaGenerationTest#'project bounded release identifiers from source-free artifacts with Groovy #generation'`
with `@Issue('959')`, `@Tag('documentary')`, and `@See` to the
[user contract](../user/Json-Schema.md#conservative-pattern-projection).
[Migration](../user/Migration.md#conservative-pattern-projection), existing navigation, ADR and `CHANGES.md` are synchronized.
No issue comment is posted because tracker changes are explicitly excluded; this record retains the traceability handoff.

Original refreshed master base: `6c6d8c568b9cc99e68dc521950373611b4b513b7`.
Worktree: `/Users/stephan/.codex/worktrees/959-pattern-projection/klum-ast`.
Branch: `codex/959-pattern-projection`. Initial production/test checkpoint: `9a5ec72ba58811d0fecfb749df69692f9f655921`.

## Rebase onto merged D7-EXAMPLES

PR #978 is verified merged as `1db75518818ec1c7f5e92caa0c4eebd660f4e73d`. Refreshed `origin/master` is exactly that
merge commit, and the Pattern commits are rebased onto it. The previous sibling delivery hold is satisfied; publication
still requires the post-rebase validation/reviews recorded in the delivery update. The maintainer explicitly authorizes
pushing this dedicated branch and opening a draft PR for #959, without merging.

Conflict resolution retains both the examples-bearing input-entry record and typed Pattern constraint record. Test
fixture switches are independent, so examples and Pattern fixtures select their respective source sets. ADR, migration
and changelog sections preserve both accepted features. No examples production behavior is changed. Source-free G3/G4/G5
Pattern fixtures additionally verify that documentary examples remain advisory under PROJECT/TEXT_ONLY/OMIT, including
an example that does not satisfy the Pattern. `GeneratedInput.Schema.version=1` remains unchanged. No feature protocol
or compatibility mode is added. Tracker impact is `Related: #959`; no issue closure, labels, milestone or release-placement
change is requested.

Post-rebase source checkpoint: `9fddf3569fa931e355a665c88bef8d855520ee88`. The tree is clean and the merge base is exactly
`1db75518818ec1c7f5e92caa0c4eebd660f4e73d`; a final remote read confirms master still points there.

- Focused grammar, Pattern binary/corruption and advisory-examples coexistence run: **74 cases**, zero failures/errors/skips.
- Java 17 full root `check`: **passed in 12m42s**; G3/G4/G5 totals **3387 / 2754 / 2754**, zero failures/errors,
  existing skips **17 / 16 / 16**. All **127** packaged JSON Schema regressions and **53** recognizer cases pass.
- Explicit CI propagation artifact matrix: **passed in 46s**, **1 / 2 / 2** executable matrix cases across G3/G4/G5,
  zero failures/errors/skips, using `--no-parallel --max-workers=2`.
- The independent oracle is rerun after full root generation: all three producer schemas agree; **27,211** Java/Draft
  String comparisons, **54,422** ECMA checks, the retained **66** Java controls and **75** neutrality/admission checks pass.
- Independent Standards review: **zero findings** against the exact merged base at the source checkpoint.
- Independent Specification review: **zero findings** at the same checkpoint, including conflict resolution and examples
  independence. The protocol marker remains 1. Final diff/Markdown/relative-link checks pass.

The rebased commit sequence was reviewed before first publication. The final evidence update changes documentation only;
production and executable test inputs retain the fully qualified source tree above. No no-global-transform/module-service
invariant or existing test is relaxed. Git SSH push preflight and gh CLI repository capability checks are authorized;
no credentials, scopes or raw authentication errors are recorded. The maintainer explicitly requests the dedicated push
and draft PR. PR CI/Sonar is a separate authoritative gate and no passing remote result is inferred from local checks.
No merge, issue closure, label, milestone or release-placement mutation is authorized. Hive reconciles delivery/archive;
this worker does not merge or self-archive.
