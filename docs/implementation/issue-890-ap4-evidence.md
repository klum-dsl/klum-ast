# AP-4 artifact, foreign-transform and module qualification

Date: 2026-10-14. Related: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md) and its
[AP-4 plan](adr-0029-default-only-annotation-propagation.md#ap-4--artifact-foreign-transform-and-jpms-qualification).

## Speculative execution and delivery boundary

Work began in an isolated `codex/issue-890-ap4` worktree from the exact AP-3 head
`563f5dcf773504d32d6ea2084c513d04c8666e0a`, while [PR #898](https://github.com/klum-dsl/klum-ast/pull/898)
was open. The user checkout is unchanged. AP-4 adds shipping qualification and one protected Query-hook linkage correction, without
AP-2 diagnostic/validation changes. AP-3 assumptions remain intact.

The branch remains local until AP-3 merges. At the next natural boundary after that merge, rebase once
onto current `origin/master`, resolve only genuine AP-3 interactions, and rerun affected qualification.
No rebase or remote AP-4 CI has occurred yet. Publication requires that rebase and passing final-head
checks. Tracker impact is `Related: #890`; parent acceptance and release reconciliation remain Hive-owned.

## Reproducible shipping gate

`AnnotationPropagationQualificationTest` carries `@Issue('890')` and consumes actual Java 17 production
JARs, using separate subprocesses and artifact-only boundaries. Its larger matrix is deliberately opt-in;
normal `test`, compatibility lanes and root `check` exclude this class. CI invokes the gate explicitly
and archives its JUnit matrix alongside compatibility evidence. These tasks keep independent
JUnit results under `klum-ast/build/test-results/<task>`:

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew \
  :klum-ast:annotationPropagationArtifactsGroovy3 \
  :klum-ast:annotationPropagationArtifactsGroovy4 \
  :klum-ast:annotationPropagationArtifactsGroovy5 --no-parallel --max-workers=2
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew check --no-parallel --max-workers=2
```

Fixtures live in `klum-ast/src/test/fixtures/annotation-propagation-artifacts/`. They are copied into
isolated temporary compilation directories; only JARs cross library/Schema/consumer boundaries.
No source mirror, test customizer, extra global producer or classpath overlay supplies propagation.
The ordinary build resolves all public KlumCast coordinates at exact `0.5.0-rc.2` from Central.
The test checks their SHA-256 identities against the [AP-1 public-artifact record](issue-890-ap1-rc2-evidence.md).
No Maven Local, init script, dynamic version or publishing/default-dependency change is involved.

Subprocess Groovy compilation uses the normal build's parameter and attached-Groovydoc options for
AnnoDocimal. Early fixture runs lacked these options and hit Groovy's warning renderer when a deliberately
invalid source failed. Aligning the options preserves the real diagnostics without warning suppression.
The Cluster domain alias permits FIELD and METHOD, as required by normal field-to-getter projection.

## Qualification contract

| Boundary | Executable evidence |
| --- | --- |
| Actual shipped discovery | Production compiler JAR's classpath service and JPMS `provides` agree; no compiler package exports. Required/AutoCreate/Query/Cluster and foreign effects require that provider to execute. An introduced Required versus explicit Validate conflict proves KlumCast validates the effective set. |
| Java annotation library | `javac --release 17` builds nested Java domain annotations with repeated requests and explicit containers, independently of Schema compilation. Imported nested types and Groovy aliases remain usable. |
| Groovy annotation library | Separately compiled Groovy annotations exercise both repeated requests and explicit containers. Retained DefaultValues members configure ordinary AutoCreate children for both Java and Groovy domains. |
| Typed consumers | Separate statically compiled Groovy and Java consumers name public Builder/Factory contracts, execute Builder-state Query and a typed Cluster factory, and consume emitted Schema classes. javac performs no propagation. |
| Foreign local transform | Separate Java trigger and Groovy local-transform artifacts; default-valued class/field/method triggers produce equivalent direct/propagated methods through normal local discovery. Duplicate execution fails compilation. Domain arguments are not forwarded. |
| Non-DSL controls | Class/field/method aliases remain inert on ordinary Groovy classes; direct foreign uses still execute with their explicit values. |
| Runtime-only path | Runtime consumers omit KlumAST compiler, KlumCast compiler/SPI and the foreign transform implementation. They retain domain annotations, selected physical copies and generated effects; explicit class-absence checks enforce the boundary. Groovy itself remains an ordinary Model runtime dependency. |
| Request changes | Replacement Java/Groovy libraries remove only AutoCreate requests. Runtime reflection confirms the changed requests. Old Schema JAR bytes remain identical and still create children; recompilation omits the physical AutoCreate copies and leaves those children absent. |
| Explicit AutoCreate migration | A separate negative Schema keeps old explicit AutoCreate beside the domain request and must fail as a duplicate. Positive Schemas omit only that duplicate annotation. No consumer-specific configuration is changed. |
| JPMS | Groovy 4/5 launch the named compiler module with separate named foreign, Java/Groovy annotation-library, Schema and typed consumer modules. Descriptors are authored once with ordinary exports/qualified opens; runtime asserts named identities. Groovy 3 remains classpath-only. No access-bypass flags, descriptor rewrites or split packages. |

Groovy emits transformed Schema classes; javac subsequently compiles the original fixture module descriptor,
not Schema stubs or Schema transformation code. The same descriptor is packaged without rewriting.
The foreign transform module opens its implementation only to Groovy; runtime packaging excludes it.
The Schema opens its domain package only to KlumAST runtime, following the existing module contract.

## Query hook correction discovered by the artifact gate

On the exact AP-3 base, both direct `@Builder.Query` and propagated Query compile and execute on the
classpath but fail when invoked on a named-module Builder: Groovy reports that
`$klum$completedModelOrNull` is missing. The first runtime operation in the direct control reproduces the
same failure before any propagated query executes. This is an existing protected-hook dispatch gap,
not different propagation behavior or a missing Schema opening.

`BuilderQuerySupport` now supplies the existing `GeneratedKlumBuilder` method target for its two
completed-Model hook calls, matching the established generated setter/relationship-hook linkage.
The emitted bytecode directly invokes that exported, generated-only bridge rather than routing a
protected method through Groovy reflection. Module descriptors, public signatures, query semantics
and Model/Builder separation remain unchanged. Ordinary JPMS tests assert the direct linkage, while
AP-4 executes both direct and propagated queries on named-module Builders.

## Minimum-version and authoring limits

The qualified compiler and annotation artifacts are built together from this AP-3-derived 4.1 development
source. Both the public marker and a compiler with the production contributor are required. KlumCast
`0.5.0-rc.2`, Java 17 and Groovy `3.0.25 / 4.0.32 / 5.0.6` are the exact evidence inputs. This establishes a
consistent tested pairing, not a released minimum-version promise or support on an older compiler.
Changing requests requires recompiling consuming Schemas; there is no runtime retrofit.

IntelliJ/editor recognition is **not qualified** here. Compiler success, typed-consumer recognition and
AP-3 source-mirror parity do not prove that source PSI recognizes a domain alias as its selected annotation.
No live editor result is claimed. Actual ScHelm admission and AP-5 public release/migration guidance remain
separate. Foreign-transform success establishes this concrete local-discovery contract only; it does not
establish universal ordering among independent global or same-phase local transformations.

## Validation and review

The complete shipping matrix passes on Zulu Java `17.0.3`, with no failures, errors or skips:

| Lane | Artifact scenarios | Mode |
| --- | ---: | --- |
| Groovy 3.0.25 | 1 | Classpath |
| Groovy 4.0.32 | 2 | Classpath and named modules |
| Groovy 5.0.6 | 2 | Classpath and named modules |

Each scenario includes all separate-library, foreign-transform, runtime, duplicate/conflict and
recompilation controls above; these are five complete scenarios, not five individual assertions.
The strengthened final matrix passes in 48s. The ordinary JPMS suite passes five cases on each
Groovy lane (15 total, zero failures/errors/skips), including the new Query-linkage regression.
License tasks and changed Markdown relative-link/whitespace checks pass; the CI YAML parses.

Ordinary root checks, two-axis reviews and final delivery state are recorded here when complete.
