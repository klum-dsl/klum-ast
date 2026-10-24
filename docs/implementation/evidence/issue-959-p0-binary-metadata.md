# #959 P0: inert binary metadata experiment

Date: 2026-10-24. **Historical P0 probe evidence; subsequent D1 authority/missing-artifact decision accepted. No production implementation.**
Issue: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[design comment](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6385826650).
Baseline: `28f2d7ed6e8d4b75bd67a5b20e5a4d40ffa460d2`.
P0 authority: the feasibility study's “Conditional slices, fixtures and commit boundaries” at
`c65a92270de37a5f5f036cea05100e4477f975b8`. Its [current reconciliation](../issue-959-schema-projection-feasibility.md)
records the accepted D1 decision below. [ADR 0031](../../adr/0031-structured-schema-input-artifacts.md) remains authoritative.
The historical local probe is retained at `9a0b029ba2d4ff755d2157e79ea1bf455d62617e` on
`codex/959-p0-binary-metadata`. Its fixture code is not included in this documentation delivery; paths below identify
that local commit, not test files published by this PR. Historical qualification does not establish a production feature.

## Accepted D1 reconciliation

Maintainer decision, 2026-10-24: **Option A only**. The compiler must expose minimal inert, source-associated facts on the
generated public Builder contract; the generator reads that contract only as its constraint/default metadata authority.
Declaring Model class-byte reading is excluded from production authority and fallback. B remains comparison evidence.

Existing structural JSON Schema generation stays as-is. When a consumer explicitly enables the future enhanced
constraint/default projection profile, missing required new metadata in a required Schema artifact must fail closed with
actionable guidance to recompile and package that artifact. Missing facts must not be silently omitted. No compatibility
or reporting mode is introduced now.

Rationale: P0 demonstrates A's feasibility for the tested sites and relationship-use placement without consumer Model-byte
inspection. Keeping the public contract authoritative preserves ADR 0031's boundary; explicit enhanced-profile failure
prevents incomplete output while preserving current structural behavior for existing artifacts. Production encoding,
exact fact set, ABI/emission hook and profile configuration remain undecided. D2–D6 and P1 remain unapproved.
This reconciliation records a decision; it changes no compiler, generator, runtime or test behavior.

## Answer and limits

**Yes for the bounded corpus, with additive inert capture. Current public metadata alone is insufficient.**
A producer-side compiler-output experiment preserves original type/field annotation sites in one inert annotation on
the corresponding public generated Builder. Its consumer does not need the Model class bytes to read these facts.
The test's stripped-classfile/Base64 envelope is a disposable transport for comparing facts, not a proposed production
encoding or a claim of minimum byte size. No existing annotation is copied onto a different executable-validation site.
No compiler, runtime, generator or public API production source changes.

Without enrichment, exact bytes of the **original declaring Model**, plus exact annotation definitions for defaults and
meta-annotations, suffice for the tested metadata. Hidden Builder fields, constructors, method bodies, source files,
source mirrors and annotation instances are unnecessary. Using Model implementation bytes at generator time would
require a specific amendment to ADR 0031 even though reading bytes executes no user code. P0 did not make
that choice; the later D1 decision excludes B from production. No fact in this corpus forces that amendment once inert
public capture is available.

This proves availability, identity and placement, **not** constraint applicability, effective runtime defaults, raw-input
validation equivalence, semantic interpretation of composed annotations, or universal support for arbitrary Schemas.
The producer experiment operates on emitted classfiles; it does not qualify a new AST contributor or production emission
hook, SOURCE-retained annotations, annotation-size limits, ABI evolution, hostile/corrupt byte parsing or packaging-origin
validation for a new reader. Existing `CompiledBuilderSchema` remains the admission/name/type authority.

## Reproducible fixture

At the retained local P0 commit,
`klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/ProjectionMetadataP0Test.groovy` compiles a Java annotation/API
library, a Groovy base Schema library, a separately compiled derived Schema library, then a Java consumer. Only JARs cross those boundaries. Producer source and classes directories are deleted before downstream
compilation/extraction. The consumer has neither Schema sources nor IDE mirrors nor compiler dependencies. All compilation
and positive controls run in separate Java 17 processes. Groovy 4/5 additionally qualify public annotation access in named
modules with no `--add-opens`, `--add-exports`, `--add-reads` or patched modules. Construction controls run separately on
the classpath; they are not evidence of new JPMS construction support.

`klum-ast/src/test/groovy/com/blackbuild/klum/ast/schema/ProjectionMetadataProbe.groovy`
uses Groovy's shaded ASM with code/debug/frame skipping. The test fixture indexes only its three explicitly produced
artifacts; A is then given a map with all four Model class entries removed, while the B comparison is given only the
three exact owning/base/child Model entries. This bounds consumer reads independently of availability in the original
Schema JAR. Fixture indexing is test packaging, not generator discovery or a proposed directory/JAR scanning contract.
The extractor accepts exact binary names and logs inspected declarations. Annotation descriptors, class literals and enum constants
remain strings/trees. Neither the extractor nor candidate emission creates annotation proxies or loads fixture classes.

Candidate A retains fields on each **declaring** Builder, rather than copying inherited fields into descendants. Candidate
B reads the corresponding declaring Model. Public authored method/parameter metadata already survives and is inspected
separately; the candidate does not duplicate those declarations. Java `AnnotationDefault` entries remain in the annotation
library, so omitted and explicit members remain distinguishable before effective-value decoding.

## Observed site and value matrix

| Case | Observation | Consequence |
| --- | --- | --- |
| Direct numeric field | Model `Plan#percentage` retains `Min(0)`, `Max(100)`, composition and domain annotations. Public `getPercentage` lacks those field rules. | Current retained field annotations are not generally public Builder metadata. |
| Binary inheritance | `Base#inherited` retains `Rule(12)`; admission preserves that source owner in the derived contract. | Read/capture the declaring type; do not infer the owner from the effective root. |
| Renames and ignore | `percentage` is `cpu-percent`; `orders` is `orders-topic`; child `partitions` is `partition-count`. Ignored scalar and relationship declarations retain bytes but are absent from admitted input. | Capture is independent of admission; never invent ignored inputs from discovered annotations. |
| Authored method vs input | Public Builder `configure` has declaration `Rule(21)` and parameter 0 has `Rule(22)` separately. Java `read` and `write` controls independently retain return-declaration and input-parameter rules. | Same spelling never establishes the same validation target. No fabricated property association. |
| Type-use | Java return type reference `0x14000000`, input type reference `0x16000000`, and List element path `0;` survive independently. | Preserve reference/path; declaration annotations are not type-use annotations. This does not prove Groovy propagates every Java type-use shape. |
| FIELD-only annotation | Defaults, relationship bounds and repeatable field annotations survive Model bytes and inert capture without changing their targets. | Retargeting actual annotations onto getters is unnecessary and can be illegal. |
| Repeatables | Physical `FieldRules` contains both `FieldRule(4)` and `FieldRule(9)` in order; `Repeatable` identifies the container. | Extraction does not take first/last or treat every annotation array as repetition. No normalization semantics adopted. |
| Composition/overrides | `Percentage` definition retains `Min(0)`, `Max(100)` and the `OverridesAttribute` on `lower`; use retains explicit `lower=3`. | All ingredients survive; interpreting override/group/reporting semantics remains separate. |
| Java defaults | Omitted `Rule.value` resolves from `AnnotationDefault` to 7. Nested omitted annotation value resolves separately to `nested-default`. | Definitions must be present and versioned inputs; do not initialize enum/class attributes to read defaults. |
| Symbolic attributes | Explicit and default class/enum/nested attributes, empty groups/payload and validator class references decode as inert trees. | No annotation-method invocation, enum construction or validator initialization is needed. |
| Inaccessible definition | Package-private `HiddenRule` and its Java default are readable from exact bytes. | Byte availability and reflective access to a proxy method are different boundaries. |
| Missing definition | Occurrence survives a missing `Rule.class`; effective decoding fails naming that dependency. A real stripped API JAR still permits the safe candidate consumer. | Missing metadata is not evidence of no constraint. No ambient dependency fallback. |
| Same names | `Policies.Rule` and `Other.Rule` coexist as distinct descriptors. Two fields renamed to `shared` remain distinct captured sources and become explicit admission rejections. | Qualified annotation identity and original source association are required. |
| Old artifact | Unmodified Builder lacks the candidate annotation; the probe reader fails with recompile/package guidance. | Historical probe behavior. D1 now requires fail-closed recompile guidance only when the future enhanced profile is explicitly enabled and required Schema metadata is absent; current structural generation stays as-is. |

## Relationship placement is a parent-use fact

The same separately compiled `Topic` is used by these independent native fields:

| Parent source | Effective relationship input | Captured defaults | Captured relationship constraint |
| --- | --- | --- | --- |
| `Plan#orders` | `orders-topic` | partitions 8, enabled false, label empty | Bounds 5..10 |
| `Plan#results` | `results` | partitions 2 | Bounds 1..3 |
| `Plan#cancellations` | `cancellations` | `value=4`, definition `valueTarget=partitions` | none |
| `Plan#hiddenTopic` | excluded | partitions 20 | none |

`Topic` has its own type-level `partitions=11` declaration. Neither 8 nor 2 nor either parent Bounds annotation appears
on the child's native partitions field or captured child definition. A future overlay needs: declaring parent source,
effective parent-use entry, exact child Builder identity, native child target, and that child's effective external name
(`partition-count`). Bounds has no declarative child-member mapping: a closure-class symbol and `min/max` attributes do
not establish what its code checks. No rule is lifted from parent to child, and this probe emits no `$defs` or keywords.

Explicit `partitions=-1` and omitted partitions have different use bytes but the same decoded default `-1`; the runtime's
scalar sentinel rule skips both. Required `value=4` and unequal literals are distinguishable. False and empty String are
retained exactly. `DefaultPhase`/`AnnotationHelper` source establishes runtime emptiness, member comparison, coercion,
`valueTarget` and execution ordering; this probe does not change or reproduce that runtime. Arrays, precedence/conflicts,
coercions, virtual setters, Templates and owner/environment context remain unsupported for any projection claim.

## Execution boundary and positive controls

The source-free consumer references only the public Builder and its safe string annotation. It asserts that **no**
`p0.*` side-effect marker exists. The extraction test also calls the existing `CompiledBuilderSchema` with initialization
disabled; its loader records Model resolution from signatures, but no enum, validator or provider resolution. Therefore:

- Candidate byte extraction itself needs no Model class loading or Model resources at the consumer.
- The **existing structural reflection route resolves Model types**. A claim that the whole generator never loads or
  links a Model is false; its supported boundary is no implementation inspection, initialization or execution.

A separate control process first checks the same silence, then deliberately activates every trap: Model/base/child static
initializers, an instrumented Model constructor, the Builder's source initializer, lifecycle callback, `@Default` closure,
`@DefaultValues` closure, relationship closure construction/invocation, class literal initialization, enum initialization,
validator static initializer/constructor/`initialize`/`isValid`, and an actual service-registered Jakarta provider bootstrap.
Each marker is required. In that control, broad Model-field `getDeclaredAnnotations()` **initializes the enum** while
parsing the annotation. This directly demonstrates why unrestricted annotation-proxy reflection is unsafe here. A literal
class reference decoded from bytes does not initialize that class. No user callback is invoked during metadata extraction.

Unsafe/unsupported routes: broad annotation proxies, reflection-based enum/default decoding, Model/Builder/factory
construction, `Create` reads, runtime relationship facades, runtime defaults/lifecycle evaluation, validator construction,
provider discovery/bootstrap, closure interpretation and assuming literal attribute names describe relationship logic.
A future arbitrary metadata decoder must bound recursion, check origin/dependency identity and handle malformed metadata;
this finite test reader is not a hardened production implementation.

## Historical A versus B comparison and accepted disposition

| Route | Minimum semantic addition / read | Tradeoff established by this probe |
| --- | --- | --- |
| A: inert public enrichment | Preserve original owner/site and explicit annotation tree for retained source type/fields on the declaring public Builder; reuse existing public method/parameter annotations and annotation definitions. | Covers this corpus without consumer Model-byte inspection. Selected by D1. Needs a separately decided small encoding, compiler hook, ABI and compatibility qualification; the missing-artifact rule is now accepted above. No companion-only fact demonstrated. |
| B: exact Model bytes | For admitted source-field entries, read only the known declaring Model class resource's relevant type/field annotations; follow exact annotation definition resources for member defaults, repeatable/composition markers and overrides. Preserve public method/parameter/type-use sites separately. | Available as P0 comparison evidence without new emission or execution. D1 excludes it as a production authority or fallback; it would expand ADR 0031's Model-implementation inspection boundary. |

D1 is now accepted as recorded above. The next maintainer decisions are D2–D6; none is implicitly approved by the
availability of metadata. Production encoding/placement, raw-input assertion strength, default meaning and any public SPI
remain unapproved, and P1 has not started. The historical probe adds no JSON Schema keywords, callback/provider API,
runtime validation changes, quantity grammar, #962 behavior or #954/#953 structured-input extensions. This documentation
reconciliation changes no current feature guidance or release scope.

## Historical P0 validation and review

The focused Java 17 run passed with zero failures, errors or skips:

| Lane | P0 cases | Existing `CompiledBuilderSchemaTest` cases |
| --- | ---: | ---: |
| Groovy 3.0.25 | 1 classpath | 49 |
| Groovy 4.0.32 | 2: classpath + named-module annotation consumer | 49 |
| Groovy 5.0.6 | 2: classpath + named-module annotation consumer | 49 |

Each lane is filtered explicitly (Gradle task options bind to the preceding task):

```shell
JAVA_HOME=/path/to/jdk17 ./gradlew \
  :klum-ast:test --tests '*ProjectionMetadataP0Test' --tests '*CompiledBuilderSchemaTest' \
  :klum-ast:groovy4Tests --tests '*ProjectionMetadataP0Test' --tests '*CompiledBuilderSchemaTest' \
  :klum-ast:groovy5Tests --tests '*ProjectionMetadataP0Test' --tests '*CompiledBuilderSchemaTest' \
  --no-parallel --max-workers=2
```

An earlier invocation attached filters only to the final task, unintentionally started the full baseline suite, and was
stopped. It supplies no pass/fail evidence.

The existing `KlumJsonSchemaGenerationTest` passed five selected cases: structural generation from packaged source-free
API/Schema artifacts for Groovy 3/4/5; root/output ownership and changed binary dependency documentation; configuration
and build-cache reuse, deletion and relocation. `:klum-ast:licenseTest` passed. The exact command was:

```shell
JAVA_HOME=/path/to/jdk17 ./gradlew :klum-ast-gradle-plugin:test \
  --tests '*KlumJsonSchemaGenerationTest.generate structural*' \
  --tests '*KlumJsonSchemaGenerationTest.tracks roots*' \
  --tests '*KlumJsonSchemaGenerationTest.reuses configuration*' \
  :klum-ast:licenseTest --no-parallel --max-workers=2
```

The existing `validate_json_schema.py` ran in a temporary environment pinned to `jsonschema==4.23.0`: all three
Draft 2020-12 metaschemas passed, each with 6 accepted and 14 rejected input cases, and emitted JSON was byte-identical
across generations. This is unchanged-generator regression evidence, not constraint/default projection acceptance.
`git diff --check` also passed.

Two declared read-only children reviewed `28f2d7ed6…016a6c96b`: `p0_standards` found one import-style issue in the
embedded positive-control Groovy script; `p0_specification` found no scope or acceptance gap. The script now imports its
Model explicitly. This review fix preserves the reviewed commits and is validated by rerunning P0 on Groovy 3/4/5
(1/2/2 cases, zero failures/errors/skips) plus the license check. The final range and both final child-review results are
recorded in the delivery callback; reviewers do not mutate files or run Gradle.
P0 stopped at local evidence commits without push, PR or issue mutation. The subsequent D1 reconciliation is an
independently authorized documentation/tracker delivery; it does not publish the probe code or rerun its historical
Groovy qualification. Its diff/Markdown/link checks and final read-only reviews are reported in the PR and delivery callback.
