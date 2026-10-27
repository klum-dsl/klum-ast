# #959 PATTERN-PROJECTION implementation and qualification

Date: 2026-10-27. Local speculative sibling of D7-EXAMPLES. Related: #959; tracker state is unchanged.

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

## Qualification

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
created only in a temporary directory. Results are recorded in the final handoff/evidence update.

Initial independent review found missing collector-origin proof and singleton eligibility consistency; both are addressed
with compiler-origin retention/shared-reader validation and explicit regressions. Standards requested braces and clearer
projection result states; both are addressed. Re-review and full root qualification remain in progress at this checkpoint.
A later full check caught ADR 0014's explicit no-global-transform invariant; origin capture was corrected to use the
existing KlumCast handoff and a SOURCE-only internal fact. The module-contract test is preserved, not weakened.
An initial full check was deliberately stopped after a mid-run documentation commit caused nested source-mirror fixture
publication to use a different inferred version from the built plugin. That run is not claimed as passing; a clean,
fixed-head rerun is required. No unrelated source-mirror behavior change is made.
No CI/Sonar upload, push, PR or tracker mutation is performed.

## Documentary and delivery record

Documentary test: `KlumJsonSchemaGenerationTest#'project bounded release identifiers from source-free artifacts with Groovy #generation'`
with `@Issue('959')`, `@Tag('documentary')`, and `@See` to the
[user contract](../user/Json-Schema.md#conservative-pattern-projection).
[Migration](../user/Migration.md#conservative-pattern-projection), existing navigation, ADR and `CHANGES.md` are synchronized.
No issue comment is posted because tracker changes are explicitly excluded; this record retains the traceability handoff.

Original refreshed master base: `6c6d8c568b9cc99e68dc521950373611b4b513b7`.
Worktree: `/Users/stephan/.codex/worktrees/959-pattern-projection/klum-ast`.
Branch: `codex/959-pattern-projection`. Production/test checkpoint: `9a5ec72ba58811d0fecfb749df69692f9f655921`.

Remote delivery is prohibited until D7-EXAMPLES is verified merged, this branch is rebased onto that exact merge head,
and validation/reviews/final checks pass after rebase. If pending, hand off `(ready:commit)` as expressly requested, even
when local commits exist. Hive must reconcile delivery/archive separately; this worker does not self-archive.
