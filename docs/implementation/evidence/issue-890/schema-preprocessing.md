# #890 / ADR 0029: DSL preprocessing and validation feasibility

Date: 2026-10-14. Scope: bounded architecture investigation for draft PR #891.
Authority: [ADR 0029](../../../adr/0029-default-only-annotation-propagation.md) and its
[implementation plan](../../adr-0029-default-only-annotation-propagation.md).
No production transform, annotation, registration, converter migration, KlumCast code, generated API,
runtime behavior or tracker/release state is changed.

## Recommendation and limits

Prefer B: a DSL-guarded early entry, contribution checks, complete insertion and a supported KlumCast
validation handoff; keep Required and Optional local. C can invoke existing converters deterministically,
but current registrations cause double lowering, and independent validation can then report false
coannotation conflicts. The converters have no demonstrated mutual dependency and already precede DSL
canonicalization. There is no evidence that moving them buys a useful ordering guarantee.

Copying alone does **not** preserve validation. Effective-declaration validation after insertion is a
prerequisite, including re-evaluation of previously validated explicit coannotation constraints. Recommend
a separately authorized KlumCast prerequisite establishing that state/diagnostic protocol. Current public
engine calls and raw node-metadata invalidation prove feasibility, not a supported production integration.
AP-1 remains unauthorized and not ready for authorization pending the maintainer's architecture/integration decisions.

The tests are characterization, including successful assertions of unsafe designs. A green probe suite
means the described positive and negative results were reproduced, not that production propagation is ready.

## Reproduction and isolation

Sources:

- [SchemaPreprocessingInvestigationTest](probes/SchemaPreprocessingInvestigationTest.groovy): new DSL guard,
  scheduling, validation, library, generated-contract and module probes.
- [SchedulingTransforms fixture](fixtures/SchedulingTransforms.groovy): compiled into a TemporaryFolder,
  then discovered through its temporary META-INF/services entry. No service resource is installed in a
  production or normal test source set.
- [LifecycleCompositionInvestigationTest](probes/LifecycleCompositionInvestigationTest.groovy): preserved
  historical baseline, late-customizer counterexample, defaults/participant and generic-global experiments.
- [Opt-in init script](probes.gradle): adds only the investigation test directory and evidence-path property
  to the selected compiler test lanes. Normal build behavior does not load the fixtures.

Run from the repository root with JDK 17:

```shell
./gradlew -I docs/implementation/evidence/issue-890/probes.gradle \
  :klum-ast:test --tests '*SchemaPreprocessingInvestigationTest' --tests '*LifecycleCompositionInvestigationTest' \
  :klum-ast:groovy4Tests --tests '*SchemaPreprocessingInvestigationTest' --tests '*LifecycleCompositionInvestigationTest' \
  :klum-ast:groovy5Tests --tests '*SchemaPreprocessingInvestigationTest' --tests '*LifecycleCompositionInvestigationTest' \
  --no-parallel --console=plain
```

Each probe compiles its global/local fixture classes before consuming annotations. For controlled ordering,
it disables the native KlumCast service **only in the isolated test CompilerConfiguration** and registers
a wrapper that invokes the actual KlumCastTransformation before or after the preprocessing fixture. The
checks and validation engine are real 0.4.0 code, not facsimiles. Trace assertions verify the chosen order.
Historical unmodified service-discovery probes remain controls; artificial ordering permutations establish
the risk, not a promise about the service enumeration order in every project.

The configurable fixture is deliberately incomplete: it does not implement the production duplicate,
nested-propagation or applied-domain-container contract. Its test-only flags select hypotheses (copy-only,
new-copy validation, full-declaration revalidation, converter invocation), not a proposed public configuration.

## Executed qualification

Java 17 (Zulu), Groovy 3.0.25 / 4.0.32 / 5.0.6: **34 new investigation cases per lane,
102 total; zero failures, errors or skipped cases**. These include the successful assertions of the
coordinator/global-validation/JPMS counterexamples described below. The final combined run includes the preserved 17-case investigation,
LifecycleParticipantCompositionTest, LifecycleParticipantPhaseTest, AutoCreateDSLTest and the precompiled
runtime-rejection consumer control: **98 cases per lane, 294 total, zero failures/errors/skips** (1m 20s).
Append those regression filters to each lane in the command above to reproduce the combined run. XML evidence is generated under klum-ast/build/test-results/{test,groovy4Tests,groovy5Tests}.

Standards and specification reviews found two corrections: use independent converter passes to establish
fixed ordering with reversed field declarations, and align the stated coannotation bypass with the Key/Field
probe. Both are addressed; no outstanding review findings remain. Markdown tables/fences render through
CommonMark, relative document links resolve, and git diff --check passes. This is no claim of shipped
production activation, full IDE acceptance or a ScHelm integration run.

## Actual Groovy scheduling

Primary sources inspected: Groovy **3.0.25**, **4.0.32**, **5.0.6** source jars, specifically
CompilationUnit, ASTTransformationVisitor, ASTTransformationCollectorCodeVisitor and AnnotationNode.
These are the dependency versions executed in the three lanes, not inferred from online documentation.

1. CompilationUnit installs its built-in phase operations and AST transformation operations before
   compilation customizers. Semantic resolution precedes the normal global semantic pass used here.
2. ASTTransformationVisitor discovers global classes from the compiler transform loader's
   META-INF/services/org.codehaus.groovy.transform.ASTTransformation resources and registers source-unit
   operations at each transform's declared phase. Discovery is compiler setup, not annotation use-site discovery.
3. Its normal setup registers globals before the semantic per-class local-transform collector and the
   per-phase local execution visitors. Therefore this service-discovered semantic global can append a
   resolved annotation before local collection. Selected ClassExpression types and imported/member
   references are resolved in the separately compiled library probes.
4. The collector expands AnnotationCollector aliases and discovers local registrations using the resolved
   annotation type's **compiled Class reflection annotations**. It does not merely inspect mutable AST
   meta-annotation nodes. Local transforms cannot declare a phase earlier than semantic analysis.
5. Semantic local execution follows collection. Required/Optional and ClusterFieldTransformation operate
   there; DSLASTTransformation and ClusterTransformation run at canonicalization. Builder.Query feeds
   KlumCast and DSL/BuilderQuerySupport, without a separate Query local registration.
6. A semantic CompilationCustomizer is appended too late to enter that local collection pass. The preserved
   counterexample emits Required but no Validate. The early global does discover Required/Cluster and a
   separately compiled foreign semantic local transform; its generated methods are callable.

Representative observed traces (nonessential eligibility/copy entries omitted):

```text
KlumCast first:       cast → preprocess → foreign:copied → foreign:direct
Preprocessing first: preprocess → cast → foreign:copied → foreign:direct
C direct calls:      preprocess → required → optional → lowered → local lowering
```

The last local lowering is detected by a canonicalization global audit: two Validate nodes on each
field instead of one. No inference is made about Required versus Optional local order or about order
between foreign and KlumAST locals beyond the phase/discovery boundary. The coordinator controls its
own calls only. Global resource enumeration, independent same-phase passes, third-party AST mutation,
and generated annotations introduced after collection are not universally ordered by this design.

## Required and Optional: actual code and retirement cost

Inspected production annotations and compiler converters:

- Required and Optional are FIELD/RUNTIME, each registered through GroovyASTTransformationClass, each
  KlumCastValidated with NotTogetherWith(Validate).
- RequiredToValidateTransformation at SEMANTIC_ANALYSIS casts the target to FieldNode and appends Validate.
  Explicit value maps to message; explicit level maps to level; omitted values retain Validate defaults.
- OptionalToValidateTransformation at SEMANTIC_ANALYSIS appends Validate(value = Validate.Ignore).
- Neither converter tests idempotency, sets the new Validate's source position, or distinguishes explicit
  from already lowered Validate. Both effects must be available to canonical DSL/validation processing;
  no dependency on each other's output was found.

The C probe calls these **actual implementations**, in a fixed Required/Optional sequence on distinct
fields declared in the opposite order. Their local registrations then append a second node. Groovy 3 permits the resulting compilation;
Groovy 4/5 reject duplicate annotations (the suite asserts each lane's observed behavior). Coordination
before independent KlumCast also causes NotTogetherWith to reject its generated Validate as a conflict.

Retiring the annotation registrations would affect direct non-DSL uses, which currently still lower and
are included in the inertness controls. Safe conversion would need a guarded compatibility adapter with
per-use completion metadata, a validation-before-lowering handoff, source-position/provenance handling,
and generated/non-DSL parity tests. Removing AST registration metadata is ineffective because collection
reads compiled reflection metadata. Do not use transform-map surgery or reflection hacks to simulate a
production retirement. These changes are possible future work, but there is no demonstrated benefit to
justify migrating either converter for this feature.

## DSL eligibility and inertness observations

The fixture uses DslAstHelper.isDSLObject, excludes annotation definitions and interfaces as the main
DSL transform does, and snapshots owner-declared members. It never traverses non-DSL members for requests.

| Probe | Observation |
| --- | --- |
| Ordinary class with domain field and method | No copied AutoCreate/Query, generated foreign alias effect or propagation error |
| Mixed source unit | Only explicitly annotated DSL classes expand; ordinary siblings/enclosing classes stay unchanged |
| Abstract DSL base and concrete DSL subclass | Base field expands once; inherited creation works |
| Precompiled DSL base with propagated Optional | Subclass gets inherited behavior; no second copy for inherited field |
| DSL interface | No expansion; interface DSL generation remains outside this path |
| Static nested DSL class inside ordinary holder | Its own child expands; ordinary nested sibling does not |
| Nonstatic inner DSL class | Its own declaration expands under existing DSL eligibility; no new enclosing-class eligibility |
| Unannotated subclass of DSL class | No propagation; the existing non-DSL-subclass hierarchy diagnostic remains |
| Domain annotation definitions | Carry metadata, never execute their requests against themselves |
| DSL without propagation / direct Required/Optional | One Validate per field in DSL and ordinary classes; foreign direct behavior preserved |
| Domain requesting DSL | Ignored on ordinary class, explicitly rejected at eligible Schema use |

The precompiled-base probe qualifies inherited scalar validation metadata; same-unit probes qualify
structural inheritance. A preliminary mixed precompiled-relationship experiment hit a DSL builder-lookup
NPE and was narrowed to the actual isolated question rather than changing production code. Full binary
relationship/override parity remains AP-3/AP-4 acceptance; this investigation does not assert it is solved.
No performance benchmark or count of all Groovy/compiler scans is claimed. The guard avoids member
traversal outside eligibility; production must avoid unnecessary mutations/rescans on no-work Schemas.

## Foreign local transformation and generated contracts

ForeignMarker and ForeignSchedulingProbe are separately compiled before consumer source. Direct field use
and propagated field use each generate a unique callable foreign_<field>() method returning 'generated'.
A duplicate execution fails on an existing generated method. Both global order permutations pass. Ordinary
classes retain direct foreign use and gain no effect from the domain alias. This establishes observable
foreign local discovery, not just annotation-node insertion or universal third-party interoperability.
The fixture has no KlumAST-specific foreign processing; broader method/class and rewriting combinations
remain actual-artifact qualification, not a hidden expansion of the public scope.

Direct and propagated Query/Cluster/Required/Optional controls compare public Foo_DSL.Builder and Factory
method signatures. Query works against in-progress Builder state and completed Model. Cluster fields
become projection getters, method projections and factory routes work, and source mirrors contain the
same query/cluster surface. Retained domain annotation differences are intentional; full source files
need not be byte-for-byte identical. AutoCreate plus repeated Required/DefaultValues requests creates
children and keeps the original domain member. Original generic baseline supports the earlier lifecycle
and creation ordering controls. Full typed binary/static-client and editor recognition gates remain in the plan.

## KlumCast 0.4.0 characterization

Primary sources: klum-cast-compile, klum-cast-annotations and klum-cast-spi **0.4.0** source jars, especially
KlumCastTransformation, ValidationHandler, RepeatableAnnotationsSupport, NotTogetherWithCheck, CheckContext
and Diagnostic. Local sources include Cluster, Default, Required, Optional, NeedsDSLClass and BuilderQueryCheck.

KlumCastTransformation is global SEMANTIC_ANALYSIS. It recursively finds actual annotation-type metadata
ending in KlumCastValidated, KlumCastValidator or CheckBinding. It skips annotation definitions carrying
validation bindings as use sites. Normal fields/methods/classes and method parameters are visited; properties
are skipped to avoid Groovy 4 double visiting. It sees original domain annotations and any copies already
present, but does not follow the selected Class value of the experimental propagation marker.

ValidationHandler requires a resolved/precompiled validated annotation. It reads its typeClass's reflection
annotations, traverses actual meta-annotations transitively, normalizes genuine reflection repeatable
containers, and invokes existing Check SPI bindings on the actual annotation and declaration. Explicitly
supplied members receive member constraints. Diagnostic carries code, binding, composition path and
related positions. VALIDATED metadata is stored per AnnotationNode, even after violations; a subsequent
engine invocation returns no diagnostics without a state transition.

Definition checks and use checks are distinct. Propagation does not make a domain definition a Schema
field or method. Copying metadata onto a source ClassNode does not amend precompiled Java/Groovy reflection
classes. Applying selected-member constraints to an unrelated domain member shape is not equivalent to
validating a new selected annotation use. A validated domain still needs separate compilation, as the
same-source DefaultValues-domain counterexample asserts. A simple unvalidated propagation domain has
no new KlumCast binding merely because its selected Class does.

### Executable parity and gaps

| Case | Direct / propagated result |
| --- | --- |
| Bare Default field | Direct fails NeedsOneOf; copy-only fails only when KlumCast runs after insertion |
| Existing engine called after copying | Default fails in both orders with domain/selected/field and original source coordinates |
| Cluster String field, raw Map field, String-returning method | Direct and engine-validated propagation fail applicable shape/generics checks |
| Bare Cluster on Schema class | Missing bounded configuration fails with provenance |
| Required on method | Propagation target guard fails before the converter's FieldNode cast |
| Valid Query, Cluster field/method, Required, Optional | Pass with direct generated-contract controls |
| Java and Groovy separately compiled domain annotations | Both resolve copied Default constraints; javac itself performs no Groovy transforms |
| Repeated requests / explicit request containers | Java and Groovy metadata consumed; copies retain normal validation/lowering |
| Key plus copied Field, validating only the copy | Key's previously validated NotTogetherWith is missed; direct Key/Field fails |
| Required plus copied Validate, full effective-declaration revalidation | Conflict caught in either global order, before local lowering |
| Missing annotation member default | Groovy 3/4 permit the bare direct form; Groovy 5 rejects it; propagation explicitly rejects this unrepresentable request |

The fixture's full-declaration variant removes ValidationHandler.METADATA_KEY **only on changed targets**,
then calls the real engine for all effective uses. It proves coannotation correctness can be recovered.
It does not qualify duplicate-reporting behavior when the first pass already produced a diagnostic, or
make metadata removal a supported API. Those are KC-0 prerequisite criteria. Do not defer these correctness
requirements or ship the new-copy-only variant.

The fixture prefixes engine diagnostic messages with domain → selected type → declaration and puts the
copy at the original use's source position. Production must also retain engine binding/composition and
related positions and qualify provenance through projections. Existing KlumCast composition paths alone
do not describe the propagation edge. Further editorial improvements are deferrable only after this
minimum understandable context and constraint correctness are present.

### Integration alternatives and ownership

Physical copying of constraints onto domains is unsuitable for precompiled libraries and changes what
member/target the check sees. Transitive request resolution inside KlumCast would need an agreed new
coupling/protocol and still manufacture effective selected uses. Prefer an agreed narrow compiler entry
into KlumCast's existing engine, with explicit effective-target state and at-most-once diagnostics; KlumAST
supplies the final batch and origin and retains normal target/default legality guards.

Recommend a separately authorized KlumCast prerequisite; integrate it in AP-1, not a diagnostic-only
follow-up. No KlumCast production edit or prerequisite issue is created here. A maintainer may instead
explicitly support the current public engine's use/state protocol, but that choice and its tests remain
outstanding. Compiler/API access ownership must not be inferred from current automatic-module exports.

## JPMS observations and remaining production gates

Current KlumCast 0.4.0 jar manifests declare automatic module names for annotations/compiler/SPI. The
independent engine probe calls its actual Default validation on a compiler-owned AST field using named compiler
modules on Groovy 4/5 (Groovy 3 uses classpath). No access-bypass flags are used.

The external temporary service overlay is in an unnamed module. On Groovy 4/5 its call to compiler-internal
DslAstHelper fails with IllegalAccessError, as expected from the production descriptor. This is a useful
negative boundary result, not a passing shipped global-coordinator proof. Production must place the entry
and helper call within the existing compiler module, preserve encapsulation, and qualify actual service/
module discovery there. Do not export internal packages just to make an external probe pass.

The annotation marker must remain JDK-only. Actual named annotation libraries, named Schema consumers,
production global registration, runtime-only use, complete binary inheritance and IntelliJ recognition
remain AP-4 gates. Global ordering against arbitrary third-party transforms is intentionally unsupported.
A real ScHelm integration run belongs after production implementation.
