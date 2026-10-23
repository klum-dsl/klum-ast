# WIRE-1 private carrier reader qualification

Date: 2026-10-22. Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Historical checkpoint before the approved fixed-key refinement. Scope: internal metadata reading only; the production interpreter/facade is not delivered.

`StructuredInputPlan` reads only the exact Model's packaged WIRE-PLAN/B class bytes through its defining module.
It validates the single private constant, header, Model/Builder identity, source owner, recursive payload facts and
recorded public contract descriptors. It follows explicit generated Builder parents, collapses equal source facts,
retains rejection facts and excludes external-name collisions without selecting a winner. It does not initialize
Schema/enum classes, instantiate a carrier, infer admission from reflected fields or add module exports.
Missing/damaged/inconsistent carriers provide recompile/package guidance. There is no global cache.

## Durable checks

`StructuredInputPlanReaderTest` covers packaged role/signature extraction, seven carrier damage cases, separately
compiled inheritance with a missing-parent negative, accepted/rejected extraction in fresh JVMs, separately compiled
external-name collisions, and the original #938 binary dependency. Initialization traps cover Schema/enum static
initializers, source field initialization and PostCreate. The Java fresh-JVM probe uses static dispatch and does not
catch JVM Errors. It is test code, not a public runtime entry point.

The binary fixture in `klum-ast/src/test/resources/structured-input-938/` was actually compiled using merged #938
commit `3d13ffa2db91aa2245ec1041952b16de43e01d4c` in an isolated historical worktree, then packaged successfully.
Its source, provenance and SHA-256 are retained alongside it. The test loads its unchanged carrier, including the
original plural property descriptor groups. It does not regenerate metadata or introduce legacy execution policy.
The temporary historical worktree was submitted for archive after preserving the fixture.

`JpmsPackageBoundaryTest` puts a temporary probe in a copy of the actual runtime module JAR and calls the private
reader from within that module. A real named Schema module is successfully read using its ordinary package opening;
there are no extra export/open/read flags or module descriptor changes. The previous private-field extraction proof
is retained independently. This qualifies actual byte access on Groovy 4/5, rather than relying on reflection alone.

## Validation

Qualification passed: **14 reader cases on each of Groovy 3/4/5**, plus the independent key-mapping
characterization (1 per lane) and the actual named-module feature (1 each on Groovy 4/5).
All selected cases have zero failures, errors or skips. Module license and test-lane isolation checks pass.
The license plugin reports unknown extensions for the binary JAR and Markdown provenance file; it does not fail,
and no files were renamed or retyped to bypass header handling.

The historical compiler build also passed. A first fixture init-script attempt accidentally applied to `buildSrc`
and was corrected before artifact creation; a first standalone compiler attempt omitted the compile-only Groovy
classpath and produced no compiled fixture. Only the subsequent successful compile/package output is retained.

Manual review confirms this checkpoint is confined to tests, a small historical binary fixture and evidence.
The reader production validation changes are in the preceding `e0794929e` commit. Full interpreter acceptance,
independent review and repository-wide final checks are not claimed by these focused results.
`git diff --check` and Markdown fence/local-link checks pass.

Qualification command:

```shell
./gradlew :klum-ast:test --tests '*StructuredInputPlanReaderTest' --tests '*StructuredInputKeyMappingBoundaryTest' :klum-ast:groovy4Tests --tests '*StructuredInputPlanReaderTest' --tests '*StructuredInputKeyMappingBoundaryTest' --tests '*JpmsPackageBoundaryTest.a real schema*' :klum-ast:groovy5Tests --tests '*StructuredInputPlanReaderTest' --tests '*StructuredInputKeyMappingBoundaryTest' --tests '*JpmsPackageBoundaryTest.a real schema*' :klum-ast:license :klum-ast:verifyTestLaneIsolation --console=plain --no-daemon
```

No Consumer creator factoring, complete input preflight, exact execution dispatcher, public facade, user-documentation
claim, tracker closure, push or pull request follows from this checkpoint. Full interpreter acceptance and final
Standards/Specification review remain required for eventual delivery.

## Approved pre-release refinement

The fixed direct-child key refinement supersedes the original #938 success expectation above. The original binary
fixture is unchanged, but its eleven-token entries now reject with recompile/package guidance. The current strict
reader requires a twelfth optional fixed-key token and verifies that a non-null fact belongs to a keyed owned object.
See [the resolved direct-key gate](issue-932-wire-1-direct-key-stop.md#accepted-resolution). No legacy handling was added.
