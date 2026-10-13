# Default-only annotation propagation on Schema declarations

Date: 2026-10-13

Status: Proposed for Hive review. The maintainer confirmed the public shape and semantic scope below;
the internal compiler architecture is feasibility-probed, not production-qualified.

Implementation status: Local design and opt-in probes only. Production implementation is not authorized.

Release: Conditional 4.1 candidate, subject to Hive release review; this ADR assigns no milestone.

Tracking issue: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Implementation plan: [ADR 0029 plan](../implementation/adr-0029-default-only-annotation-propagation.md).
Evidence: [decision brief and consumer-fit report](../implementation/issue-890-lifecycle-composition-decision-brief.md).

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0005](0005-generated-dsl-support-api.md), [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md), [ADR 0020](0020-explicit-shared-model-builder-capabilities.md),
and [ADR 0028](0028-annotation-driven-lifecycle-participants.md).
Related: [ADR 0023](0023-schema-owned-constraint-annotations.md),
[#888](https://github.com/klum-dsl/klum-ast/issues/888).

## Context and authority

A domain annotation can select an external lifecycle participant or supply DefaultValues, but currently
cannot itself add ordinary AutoCreate behavior. Schema Developers repeat annotations on each structural
child. The original #890 investigation compared a built-in Creator token with AutoCreate meta-annotation
resolution. The maintainer instead selected additive compile-time annotation propagation, preserving the
applied domain annotation and the ordinary consumers of copied annotations.

The scope extends to classes, fields and methods: a domain annotation may carry Builder.Query or Cluster,
not only runtime lifecycle metadata. The maintainer confirmed default-only copying, duplicate failure,
one level with nested propagation rejected, and a repeatable propagator selecting one annotation per
occurrence. Existing domain members remain available to DefaultValues and participant handlers. Schema
recompilation when propagation declarations change is explicitly accepted as safe.

The ScHelm consumer-fit review, relayed by KlumAST Hive on 2026-10-13, accepts this contract. Its current
structural fields are concrete, unkeyed direct DSL relationships: OrderApplication.messaging/database/
workload/api and OrderKafka.orderTopic/resultTopic. No current case needs AutoCreate key/type/value or
coexisting explicit AutoCreate after migration. FactBinding currently always represents a fixed structural
domain. KafkaTopicDefaults, ResourceClassDefault and HttpEndpointDefaults fit the DefaultValues use case.
No demonstrated forwarding or nesting requirement remains. This is an externally reported fit review;
the local investigation did not execute a ScHelm build.

## Decision

### D1 — One generic, retained meta-annotation

Introduce `com.blackbuild.klum.ast.PropagateAnnotation` in klum-ast-annotations. It has runtime retention,
targets ANNOTATION_TYPE, is documented and is repeatable through a nested `List` container with matching
target, retention and documentation. Its only member is `Class<? extends Annotation> value()`.
No configuration, forwarding, priority or override member is published in this version.

Groovy contract sketch:

```groovy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Documented
@Repeatable(PropagateAnnotation.List)
@interface PropagateAnnotation {
    Class<? extends Annotation> value()

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @Documented
    @interface List { PropagateAnnotation[] value() }
}
```

A Schema Developer places one or more requests on a domain annotation. When that domain annotation is
applied to a source class, field or method, the compiler retains the domain annotation and adds an
ordinary annotation of each selected type, with no explicitly supplied members. Copied annotations use
their normal member defaults. The applied domain annotation may have its own arguments; they are not
copied into the selected annotation.

Retaining the domain annotation means preserving its AST use and ordinary retention/projection behavior,
not upgrading SOURCE/CLASS retention to runtime. Existing consumers keep their own retention and
precompiled-annotation requirements; propagation does not loosen KlumCast validation prerequisites.

```groovy
@DefaultValues
@PropagateAnnotation(AutoCreate)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MessagingDefaults { String name() default '' }

@DSL class Kafka { String name }
@DSL class Application {
    @MessagingDefaults(name = 'orders') Kafka messaging
}

assert Application.Create.One().messaging.name == 'orders'
```

No LifecycleParticipant declaration or handler is required. A participant domain annotation can use the
same propagator, independently or together with DefaultValues. DefaultValues keeps its current behavior
of applying nondefault domain members; copying defaults does not redefine that consumer contract.

Each request identifies one annotation so future per-request customization can be designed without
parallel configuration arrays. Repeatability is not authorization to add that future behavior now.

### D2 — Declaration scope and ordinary validation

Expand on original source classes, declared fields and declared methods, including non-DSL classes;
otherwise a domain class annotation could not carry DSL itself. Do not add parameter, constructor,
package or type-use propagation. Definition of a domain annotation is not execution of its requests
against that annotation definition.

Each copied type must be an annotation legal at the actual target. Ordinary semantic checks remain
authoritative: Query purity and result restrictions, Cluster shape/static/generic rules, and AutoCreate
eligibility/key/type/value behavior do not change. Generic propagation grants no right to use a copied
annotation at a target where direct use would fail. Missing required annotation members are compilation
errors; syntactic defaults do not waive checks such as Default requiring field/delegate/code on a field.

Do not move existing runtime configuration errors to compilation as incidental cleanup. For example,
bare AutoCreate's keyed/abstract failures retain ordinary behavior when creation is needed. Generic
propagation introduces no new relationship whitelist beyond the copied annotations' existing contracts.

### D3 — One level, duplicates fail

Expand requests from the actually applied domain annotations once. If a selected annotation itself
declares propagation, reject the request; do not recurse or leave a nested request silently ineffective.
This also excludes cycles and self-propagating declarations.

Reject every duplicate annotation contribution involving propagation at one declaration, even if the
copies would have identical defaults or the selected annotation is repeatable. This includes explicit
plus propagated annotations, two requests on one domain annotation and contributions from different
domain annotations. No deduplication, member merge, explicit precedence or collector override mode.
Existing explicit-only repeatable declarations retain their ordinary behavior.

Recognize repeated PropagateAnnotation declarations and explicit containers in source and precompiled
domain metadata. Normalizing a legitimate repeatable container is not recursive propagation. Repeated
applied domain occurrences must not hide propagation contributions inside a container: recognize the
standard repeatable relationship and apply the same duplicate rule. This adds no plural participant
context or new runtime dispatch guarantee for repeatable domain annotations.

After expansion, existing semantic conflicts still fail. AutoCreate plus an external same-phase Creator
remains an ADR 0028 competing-creator error; Query plus Builder.Method remains an incompatible-category
error. Diagnosis is independent of a runtime value that might otherwise skip creation.

Normalizing request/container order supplies deterministic diagnostics, not a new runtime execution
order between annotations. Each consumer's existing ordering contract remains authoritative.

### D4 — Expand before local-transform discovery

Proposed compiler architecture: one global AST pass in klum-ast at semantic analysis, after type/import
resolution and before Groovy collects local AST transforms. Use the ordinary global-transform discovery
path; no schema-specific runtime interpreter, custom lifecycle phase or second graph traversal.

The pass snapshots original declarations, resolves direct requests and standard containers, validates
contributions and appends fresh annotation nodes. Preserve the originating use's source position and
enough internal provenance for usable errors. Process each original declaration once; generated getters,
Builder twins, source mirrors and inherited declarations are not new expansion sites.

This ordering is substantive. A late semantic-analysis CompilationCustomizer can make AutoCreate work
but miss Required's converter and Cluster's early field conversion. The global-pass feasibility probe
does discover Required and both Cluster transforms, and produces Query twins and a DSL class normally.
Production service/module packaging, foreign local transforms and interactions with other global
transforms remain mandatory qualification gates. Do not use alphabetical transform sorting or reflection
order as a public ordering contract.

Required's direct runtime validation refactor was permitted by the maintainer, but is not a prerequisite:
the demonstrated early architecture discovers the existing converter. Keep it outside the implementation
slices unless new evidence requires an independently reviewed change.

### D5 — Existing generated and runtime contracts

The resulting declaration is interpreted like the equivalent directly annotated declaration. Original
domain annotations retain their ordinary retention/projection behavior. Copied runtime-retained annotations
are physically visible to reflection and singular participant lookup. Ordinary source retention rules also apply to copies.
Do not preserve the earlier runtime-only candidate's absence of synthetic annotations as a contract.

Builder.Query and Cluster intentionally affect generated contracts, exactly as their direct use does.
Generated Foo_DSL Builder/Factory interfaces, hidden implementations, child factories and IDE source
mirrors must match explicit-annotation controls. Propagation grants no Model/Builder substitutability,
new public generated type, metadata facade or client construction API.

Inherited fields/methods retain their original declarations; do not re-expand them on each subclass.
For type annotations, normal copied-annotation inheritance applies. Do not manufacture inherited copies
because a domain annotation is @Inherited, or promise to rerun a superclass's transform in subclasses.
Existing shadowing and DSL interface restrictions remain.

AutoCreate still uses the same FactoryHelper, checked assignment, ownership/session, Templates scope,
construction path and phase slot. Creation precedes field mutation; direct fields precede Cluster
fallback and callbacks; earlier phases do not replay for late-created children. Mutator FAIL/SKIP,
DefaultValues precedence, materialization, validation and serialization behavior are unchanged.
Handlers/contexts and new propagation compiler state are not serialized into Models or Templates.

### D6 — Compilation and compatibility

Domain annotation libraries may be authored in Java or Groovy. The marker/container contain only JDK
types and preserve the annotation artifact's Groovy-free interface. Expansion occurs when Groovy
compiles consuming declarations with the new klum-ast compiler available; Java javac does not execute
this Groovy transform. Runtime-only consumers need no compiler or runtime propagation interpreter.

Changing a domain annotation's propagation requests requires recompiling consuming Schemas. Existing
Schema binaries keep their emitted annotation presence and generated code. Member-default evolution
of the copied annotation itself follows ordinary annotation semantics; this is not a promise to freeze
all runtime defaults independently of their defining annotation library.

New requests are not supported by older compilers. Document the minimum compiler version and consistent
annotation/compiler dependencies; qualify the consumer build instead of claiming runtime fallback.
Switching an established domain annotation to imply creation is a deliberate consumer migration:
remove old explicit AutoCreate uses in recompiled Schemas or choose a separate structural annotation.

Qualify one Java-17 production artifact across Groovy 3/4/5 and applicable Groovy 4/5 JPMS using normal
module access. Source IDE recognition is a separate authoring gate; compiled source mirrors alone do
not demonstrate that IntelliJ understands every domain alias before compilation.

## Consequences and exclusions

Schema authors reuse domain annotations without repeating default-valued framework annotations, while
all existing compiler/runtime semantics remain at their current consumers. Complexity concentrates in
one early compiler producer and its declaration diagnostics rather than multiple runtime resolvers.

No fixed annotation values, member forwarding, nested expansion, generic annotation execution framework,
runtime retrofit, ScHelm provider policy, lifecycle containers, HANDLE or catch-up lifecycle is added.
This is annotation expansion, not a new lifecycle-participant API.

[#888](https://github.com/klum-dsl/klum-ast/issues/888) remains separate. This ADR acts only where a domain
annotation requests propagation. Supporting class declarations does not make AutoCreate legal on a class,
opt a whole Schema into child defaults, reinterpret Required/Optional or add an auto-create opt-out.

## Rejected alternatives

- Creator handler omission/sentinel: narrow lifecycle-only representation; superseded by the confirmed
  generic class/field/method requirement. No public no-op handler is introduced.
- AutoCreate on ANNOTATION_TYPE with runtime transitive resolution: adds per-consumer interpretation and
  does not cover Query/Cluster generation as ordinary early annotations.
- Plain AnnotationCollector alias: can bundle AutoCreate and a separate domain annotation today, but
  replaces its applied alias and has broader argument-mapping/duplicate policies than this contract.
- Late copying before only DSL canonicalization: misses early local-transform discovery.
- Recursive composition, duplicate precedence and configured-member forwarding: explicitly excluded.
- One class-array request: replaced by the confirmed repeatable single-annotation shape.

## Acceptance and decision boundary

Local probes qualify feasibility: 17 cases per Groovy 3/4/5 lane, zero failures/errors/skips. The earlier
focused lifecycle baseline passed 56 cases per lane. ScHelm's reported review qualifies current use-case
fit; neither is full production or IDE acceptance. The [plan](../implementation/adr-0029-default-only-annotation-propagation.md)
names the required gates and commit boundaries.

Hive owns review of this proposed ADR, canonical #890 scope reconciliation, implementation admission
and release placement. This local design document changes no GitHub state and authorizes no implementation,
push, pull request, merge or release.
