# #867 LP-2: direct-field participant composition

Date: 2026-10-10. Base: merged LP-1 / master `e1bf38e0c7f510af40008c197bd01927e7e0ae65` (PR #871).
Authority: explicit maintainer LP-2 implementation delegation. Related: #867; issue and release placement remain unchanged.

## Delivered local behavior and boundary

Direct retained Schema DSL fields in AutoLink support one creator plus multiple mutations on one domain annotation.
All creation runs before any mutation at that field slot, including mutations from different domain annotations.
Existing Builders skip creation; a null creator result leaves the field unset and skips mutation. Assignment uses
LP-1's checked path. All existing LP-1 tracer, public context/handler signatures and lifecycle semantics are retained.

Competing direct creators at the same AutoLink field slot fail compilation: two domain annotations, repeated
creators inside one annotation, and built-in LinkTo versus external creation. Runtime declaration checks also
reject a compiled annotation library changed to introduce competing creators after Schema compilation.
Mutators remain composable, including with built-in LinkTo; built-in AutoCreate and external AutoLink creators
coexist across their different phases. No blanket built-in/meta-lifecycle exclusion is introduced.

**Pending scope clarification:** LP-1 accepts only AutoLink, whereas the LP-2 assignment asks for different-phase
composition while excluding LP-3 visitor integration. The implementation currently preserves the public AutoLink-only
restriction. External AutoCreate/Default/PostTree declarations still fail rather than compile without execution.
Cross-phase external composition is not claimed complete; the Hive/maintainer must settle its LP-2/LP-3 boundary.

## Exact ordering conclusion

Within one domain annotation, repeated LifecycleMutator declarations are compiled into an ordered repeatable
container. Execution follows their declaration order. An explicit LifecycleMutator.List executes in its value array
order. Java and Groovy authored libraries, separately compiled Schemas, and binary consumers qualify both forms.
Handlers deliberately named ZFirst and ASecond detect accidental alphabetical sorting.

This is an ordered-container contract, not an inference from the order returned by getDeclaredAnnotations.
[Java 17 AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
defines repeatable expansion in container value order; [JLS 9.7.5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.5)
defines the implicit container array in left-to-right source order. Groovy compiler behavior is established by the
executable lane probes rather than assumed from Java syntax.

The dimensions remain separate:

| Dimension | Contract |
| --- | --- |
| Creator versus all field mutations | Creation always first; null skips mutations |
| Repeated mutators within one domain annotation | Source declaration order, recovered through the emitted container |
| One explicit mutation container | value array order |
| Singular marker mixed with explicit container | Unspecified relative order; both mutations execute after creation |
| Different domain annotations on a field | Unspecified; handlers must be independent of their relative order |
| Optional type-versus-field mutation | Out of scope; no type participation API |

Mixed singular/container probes accept either mutation sequence. They do not turn observed reflection order into a
contract. No priorities, alphabetical sorting, ordering SPI or optional-capability probe API is introduced.

## API change

LifecycleCreator and LifecycleMutator become repeatable through nested public LifecycleCreator.List and
LifecycleMutator.List annotation containers, each declaring value(): marker[]. Existing singular uses remain valid.
All six LP-1 public types retain their existing methods and descriptors. Contexts retain singular annotation lookup;
repeatability is on the meta-annotations, not a plural domain-annotation context operation.

## Executable evidence

- LifecycleParticipantCompositionTest (class-level @Issue('867')): repeated and explicit-container mutation order;
  creator-supplied/existing/null targets; separate domain annotations; external and built-in conflict diagnostics;
  every nested handler validated; LinkTo plus mutation; different-phase AutoCreate coexistence.
- Its documentary case `combines creation and repeated mutations on supplied and existing Builders (#container)`
  links to [user composition guidance](../user/Model-Phases.md#participant-composition-lp-2).
- LifecycleParticipantConsumerTest: Java/Groovy × repeated/container/mixed, with independently compiled domain,
  annotation-library, Schema and Java-consumer JARs. Source and class directories are removed before runtime, and
  the runtime classpath excludes the compiler. Existing LP-1 Java/static/dynamic Groovy consumers remain covered.
- Binary defense replaces the annotation library after Schema compilation with repeated competing creators and
  requires a KlumSchemaException before handler invocation; existing generic-handler replacement controls remain.
- Existing AutoLink and FieldPhaseTraversalCharacterizationTest regressions remain part of qualification.

Focused Groovy 3 coverage passes after correcting the test's explicit LinkTo provider. Initial Groovy 4/5 ordering
and binary-consumer probes pass; final full-repository check is in progress. No new suppression or ignored test.
Standards review found no documented violations and one nonblocking duplicated fixture conversion, subsequently
consolidated. Specification review found no implementation errors or scope creep; its one incomplete acceptance
criterion is the explicit cross-phase scope question above. Final-head CI/SonarCloud and commit-history review remain
pending. An initial repository check was invalidated when a documentation commit changed the Git-derived version
during nested fixture publication: consumers requested dev.334 while the fixture repository contained dev.335.
It is not acceptance evidence; qualification must run with stable Git state and no overlapping Gradle builds.

## Deferred gates

LP-3 four-phase visitor integration and cross-phase external dispatch; LP-4 FAIL/SKIP and graph/Template/import
qualification; optional type mutation (LP-5), Closure helpers (LP-6), HANDLE and containers (LP-7); LP-8 complete
validation/errors/JPMS/final naming/release reconciliation. Container support-or-reject remains a mandatory release
decision. No ScHelm policy, ownership/session/materialization redesign or generic extension framework.
