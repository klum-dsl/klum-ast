# Deterministic Factory projection order — #1124

The maintenance correction targets `release/4.2.x`. No patch release, version, date, tag or publication is selected.

## Reproduction and correction

The retained investigation used public KlumAST 4.2.1, Groovy 5.1.3 and Zulu Java 21.0.2. Its reduced Schema has two
`ForEnvironment` Factory overloads: the single-argument overload delegates to the closure overload, which calls `With`.
A stock-worker ordering control repeated before this correction reproduced two raw `Schema$Factory.class` hashes in
12 clean compiler invocations. All other emitted classes were identical. The two projected overloads exchange order;
the resulting constant-pool order changes too. The evidence archive is retained as `/private/tmp/klum-1124-evidence.tar.gz`
(SHA-256 `c030d75f795ab6b33a34d34432d6b9381ccfe18c3c7ee2bbff6c3535214922d7`).

`BuilderMethodProjection` already records candidates in a `LinkedHashMap`. Eligibility is resolved through an
identity-backed Set and its existing dependency fixed point. The correction iterates the ordered candidate registry
when appending eligible twins, testing membership in that same Set. Discovery, eligibility, overload resolution,
projected bodies and dependency semantics remain unchanged. No compiler output is sorted or normalized.

## Regression contract

`FactoryProjectionReproducibilityTest` retains the original two-overload Schema and `@DelegatesToBuilder` closure.
Each invocation creates a fresh compiler configuration, classloader and CompilationUnit, with no earlier output on its
classpath. Compilation stops at class generation and reads the in-memory bytes without writing shared output files. The test compares every emitted class's raw bytes against the first invocation across 12 repetitions and
checks the projected overload descriptors in declaration order. It compares compilations within one toolchain; it does
not require byte identity across different JDK or Groovy versions. Independent compiler invocations in one worker are
distinct from independently launched JVMs.

The regression failed against the unchanged implementation before the production correction. Qualification and exact
release/master delivery evidence are recorded separately in the pull requests and the final #1124 issue comment.

This internal ordering fix introduces no DSL feature, generated API shape change, lifecycle change or migration step.
User-documentation navigation is unaffected. A changelog version section is deferred by explicit maintainer direction.

## External corrected-artifact control

The retained stock-worker and fresh-process launchers were copied to `/private/tmp/1124-fixed-probe`, with the compiler,
annotations and runtime JARs replaced by locally built maintenance artifacts. Groovy 5.1.3, Java 21.0.2, the reduced
Schema, ordered remaining dependencies, JVM options and controlled environment were retained. All emitted class bytes
matched across 12 fresh compiler invocations in one worker and 30 independently launched JVMs. The resulting Factory
hash was `d57cd822263242041dcb66a2257e6a7ed0c4b0b2b19a0125d4119414f42a5769`, the declaration-order variant already
observed in the original control. Invocation records, dependency hashes and raw snapshots remain in that local directory.

## Local qualification

`./gradlew :klum-ast:test :klum-ast:groovy4Tests :klum-ast:groovy5Tests :klum-ast:check` passed. The compiler module
reported 3,444 tests on Groovy 3.0.25 and 3,448 each on Groovy 4.0.32 and 5.0.6, with no failures/errors (existing skips: 16/15/15).
This includes the existing Factory/Builder dependency, overload, lifecycle, artifact-consumer and JPMS tests. Module
compilation, license checks and test-lane isolation passed. The corrected in-memory regression was subsequently run
across all three lanes. Hosted root checks and SonarCloud remain separate exact-head gates for each delivery PR.

Independent Standards review found no issues. Specification review identified shared output from the test's default
compiler phase; stopping at class generation addressed that finding. The follow-up review found no remaining issues.
