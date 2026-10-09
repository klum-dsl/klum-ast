# #867 external field lifecycle participants: feasibility decision brief

Date: 2026-10-09
Status: Historical initial investigation; superseded for design authority by ADR 0028.

Initial proposals below are historical evidence, not current decisions. See
[ADR 0028](../adr/0028-annotation-driven-lifecycle-participants.md) and its
[implementation plan](adr-0028-annotation-driven-lifecycle-participants.md).
AUTO_LINK-only scope, scalar fields, reflective Field/list context, setter/expiry machinery,
single-branch dispatch and alphabetical ordering are obsolete proposals.
Issue: [#867](https://github.com/klum-dsl/klum-ast/issues/867)
Evidence base: `f0f4a0de95acaaf11005f9bca460d92e14b15f56` (remote-default worktree base).

## Recommendation

A narrow runtime dispatch seam is feasible. Do not yet add #867 to the committed 4.1 scope.
Consider a conditional 4.1 QoL slice restricted to direct materializable fields in the existing
AUTO_LINK visitor, after accepting the public context, phase-selector placement, and Closure
contract below and passing the consumer tracer. Otherwise defer and retain `@AutoLink` as the
supported workaround. No milestone, label, production source, or public handler API changes
are made by this investigation.

The source establishes traversal feasibility; it does not prove that an arbitrary domain
handler, annotation Closure typing, or named-module consumer already works. Those are explicit
acceptance gates. This is a decision brief with a conditional implementation sequence, not an
accepted ADR. An ADR now would falsely present unsettled extension semantics as decided.

## Authority and current behavior

Read #867 in full, including its absence of comments and untargeted release status. Relevant
parents are ADRs 0003 (materialization), 0005 (public generated Builders), 0008 (phase
registration), 0010 (public interface classification), 0011 (Groovy lanes), 0014 (JPMS), and
0021 (Builder-owned lifecycle classes). ADR 0002 is superseded; its old opt-in migration and
materialization timing are not authority. ADR 0008 is accepted but its registration SPI is
planned, not implemented. ADR 0021's generated lifecycle-class ordering must not be inferred
from current reflection helpers. No accepted ADR is overridden by the bounded proposal.

The curation architecture map is useful for module direction and the phase table but contains
historical package paths. Current source and descriptors take precedence over those links.
`KlumBuilder<T>` is currently a zero-operation public capability, not a mutable helper base.
ADR 0014 explicitly says the existing Builder phase visitor is not a general JPMS extension
seam because its callback names `InternalKlumBuilder`.

### Actual traversal and dispatch seams

- `BuilderVisitingPhaseAction.doExecute` traverses the current root using
  `BuilderStructureSupport`, which delegates to `CompositionTraversal`.
- `CompositionTraversal.doVisit` calls `shouldVisit`, checks identity, then invokes the
  object's visitor **before** collecting its composition property values and descending.
  Thus all parent visitor work precedes every child visitor in that same action's traversal.
  Field values assigned or children created during the parent callback are subsequently read.
- The traversal has an identity set, skips nulls, follows declared instance fields, skips
  synthetic/static/`$`/Owner/LINK fields, expands collections by index and maps by key.
  Builder visitors skip non-Builders and sealed aggregation wrappers. OPTIONAL_LINK ownership
  remains subject to current relationship/traversal rules; #867 must not reinterpret it.
- This is an object visitor, **not an existing per-field callback**. The new seam would enumerate
  containing-Builder field declarations within that visitor. It must not move dispatch into
  the child visit: nulls and noncomposition relationships then disappear, and alias visits can
  be deduplicated before a field handler runs.
- `AutoLinkPhase.doVisit` first resolves unset `@LinkTo` fields, then invokes `@AutoLink`
  methods followed by lifecycle Closure fields via `LifecycleHelper`. DEFAULT first applies
  owner defaults, containing-field/type defaults and direct defaults, then its callbacks.
  POST_TREE invokes callbacks. AUTO_CREATE and OWNER have their own mechanics.
- The containing-field DEFAULT facility receives the visitor's container/name at a child
  visit. It is precedent for annotation parameters, **not** the #867 dispatch location.
- `LifecycleHelper` preserves virtual method dispatch by name and clears phase member state
  in `finally`; lifecycle Closure fields are consumed and cleared. Its `ClosureHelper` sets
  DELEGATE_FIRST. #867 requires DELEGATE_ONLY, so reusing that invocation helper unchanged is
  incorrect. Existing lifecycle Closure behavior must stay unchanged.
- Numeric action order is global. Equal-number actions currently follow service discovery
  order, not ADR 0008's future ID ordering. A second independently service-loaded action at
  phase 20 is insufficient: a whole-tree pass cannot interleave each parent field handler with
  the same built-in child's callback.

Source evidence: runtime `CompositionTraversal`, `BuilderStructureSupport`,
`BuilderVisitingPhaseAction`, `AutoLinkPhase`, `DefaultPhase`, `PostTreePhase`, `LifecycleHelper`,
`ClosureHelper`, `DefaultKlumPhase`, `PhaseDriver`, `AutoCreationPhase`, `OwnerPhase`; annotations `AutoLink` and `DefaultValues`;
compiler `DSLASTTransformation.moveSourceStateToBuilder`, annotation copying and
`retargetBuilderAnnotationClosures`. Existing tests: `StructureUtilTest` proves preorder and
cycle termination; `PhaseActionTypingTest` proves state-specific classpath types;
`LifecycleSpec` covers callback inheritance; `ModelPhasesDocumentaryTest` exercises Builder
callbacks and the materialization boundary. None proves the proposed external API.

## Deliberately bounded candidate contract

Every item here is a proposal for acceptance, not a silently settled requirement.

| Decision | Recommended initial boundary | Acceptance or deferral |
| --- | --- | --- |
| Phase | AUTO_LINK only, inside its current Builder visit | Additional built-in Builder phases need explicit relative-order contracts; custom/numeric and Model phases deferred |
| Relative callback order | Existing LinkTo resolution, existing methods/Closure callbacks, external field handlers, then child descent | Preserves existing callback-to-callback order; means handlers can overwrite local callback assignments; maintainer must accept |
| Field shapes | Direct nonstatic materializable fields, including null, scalar/simple values and DSL relationships | Reject Collection/Map/array, Owner, Builder-only, synthetic and unsupported declarations; no silent per-element interpretation |
| Occurrence | One containing Builder plus original declaring Schema field plus annotation instance per dispatch | No child owning-metadata lookup, no #856 dependency, no lookup through Owner/path |
| Multiple annotations | Initially reject more than one participant annotation per field and repeatable participant annotations | Avoid hidden priority/ambiguity policy; domain policy stays with consumers |
| Inheritance | Original declaring field retained across schema inheritance; reject shadowed same-name participant fields | Do not select by reflective field-name collision; polymorphic child Builder retains its own generated type |
| Field dispatch order | Enumerate original Schema hierarchy and sort eligible fields by declaring class/name; no reliance on reflection order | Maintainer must accept ordering; shadowed names rejected, no priority or consumer ordering SPI |
| Instance lifetime | Fresh public concrete handler with public no-arg constructor per field invocation | No DI, singleton, static mutable instance, global cache, serialization, or cross-session retention |
| Metadata cache | None initially | Later cache only immutable descriptors scoped by declaring class/classloader; never Builder/annotation invocation state |
| Handler typing | `Handler<A extends Annotation>`; concrete resolved A exactly matches the marked annotation | Reject raw, wildcard, unresolved type variable and mismatched A; resolve generic inheritance rather than inspect only direct interfaces |
| Returned value | `void`; assignment is an explicit context operation | Avoid implicit unset/default/overwrite policy; handler chooses whether and what to assign |
| Errors | Schema configuration fails before handlers execute where knowable; callback failure retains cause and field occurrence | Include annotation, handler, Schema declaring field, phase, structural occurrence path and construction path |

### Smallest public context boundary

Provisional Java vocabulary only (no API introduced by this branch):

```java
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import com.blackbuild.klum.ast.runtime.KlumBuilder;

interface FieldLifecycleHandler<A extends Annotation> {
    void handle(FieldLifecycleContext<A> context);
}
interface FieldLifecycleContext<A extends Annotation> {
    A getAnnotation();
    Field getSchemaField();
    KlumBuilder<?> getContainingBuilder();
    Object getValue();
    String getModelPath();
    String getConstructionPath();
    void assign(Object value);
}
```

`getSchemaField` supplies
original name, declaring class, annotation and generic declaration; it is metadata, not a
promise of reflective write access to a completed Model. The value is read at invocation time;
DSL values are Builders (including sealed wrappers), never asserted to be completed Models.
`assign` delegates to existing Builder field-assignment normalization and ownership/session
checks; it does not call `Field.set`, bypass generated methods, or offer arbitrary metadata.
The capability is usable only during the invocation and must expire afterward. Public
`KlumBuilder<?>` permits consumer factory-token narrowing or a generated public Builder cast;
no internal Builder, proxy, session, or companion appears in signatures. A universal property
lookup/invoke API is deliberately excluded. Consumer helpers can take generated interfaces.
This new context is an additive extension seam under ADR 0010/0014, not a new operation on
`KlumBuilder` or `Foo_DSL.Builder`.

Illustrative Schema syntax only, with a consumer-owned field annotation:

```groovy
@DSL
class Service {
    @NormalizeText
    String endpoint
}
```

The Java-first handler implementation would use the proposed context explicitly:

```java
public void handle(FieldLifecycleContext<NormalizeText> context) {
    Object value = context.getValue();
    if (value instanceof String text) context.assign(text.trim());
}
```

Equivalent statically typed Groovy handler body:

```groovy
void handle(FieldLifecycleContext<NormalizeText> context) {
    Object value = context.value
    if (value instanceof String) context.assign(((String) value).trim())
}
```

No generic field-value parameter is promised: Model-declared relationship T is not the same
runtime state as its Builder. Consumer handlers validate domain values themselves. Consumer
selection, defaults, candidate ambiguity and result policy remain outside KlumAST.

### Module direction and vocabulary

Recommended candidate: place the meta-annotation and its nested AUTO_LINK-only selector enum
in `com.blackbuild.klum.ast.runtime`, alongside handler/context. It may type its handler member
against the runtime interface without an annotations-to-runtime edge. Schema/consumer annotation
modules using this feature explicitly depend on runtime. Existing runtime -> annotations
remains unchanged; no split package or descriptor export of internals is needed. Keep
`DefaultKlumPhase` unmoved. Avoid exposing every runtime phase through the annotation merely
because that enum already exists.

Alternative: annotation and selector in annotations, `Class<?> handler()` there, typed runtime
handler validation later. This preserves a lighter custom-annotation dependency but loses
annotation-member handler bounds. An annotations-owned handler/context cannot name runtime
Builder types without a cycle. Maintainer must choose the dependency tradeoff and final names;
`LifecycleParticipant` is provisional and must not suggest Model/custom-phase support.

### Closure annotation members

Java annotation values cannot contain Closure instances. A member can be a Class literal with
a bound such as `Class<? extends Closure<String>>`; Java authors supply a concrete Closure
subclass, Groovy can encode annotation Closure expressions as generated classes. Generic
return information does not itself validate a Closure expression's result across Groovy lanes.
Current AST retargeting visits annotation members broadly and adjusts Model/Builder positions;
there is no proven generic extension-return/delegate typechecker in #867.

A consumer may declare a selector member and a consumer-owned sentinel class. KlumAST should
not assume the member name `selector`, call every Closure-valued member, or prescribe a
universal sentinel. The handler contract must document one fixed delegate type and argument
shape for each member. Recommended first tracer: containing public Builder delegate,
DELEGATE_ONLY, same Builder as the single explicit argument; fresh Closure with null owner and
thisObject, no captured lexical caller, expected result checked by the consumer. Unknown
properties must fail rather than resolve on owner/caller. A handler that needs a different
consumer context must define that fixed context explicitly and demonstrate equivalent tests.
No caller-selected resolve strategy or delegate override is permitted.

Whether enforceable DELEGATE_ONLY is merely a normative SPI obligation or requires a public
context execution helper is a **blocking design choice**. The latter increases public surface
and needs an exact Java signature, static-Groovy inference and runtime result contract. Do not
claim static result/delegate checking from the generic Class bound. Bare-property static
Groovy syntax, generated Closure constructor shape, sentinel behavior and inherited annotations
must be traced before accepting Closure expressions for 4.1. Restricting the first delivery to
simple annotation members would require explicit maintainer agreement to stage #867's Closure
requirement; it must not silently close the full issue.

## Conditional implementation plan and executable gates

All slices map to #867. Each slice is a green reasoned commit, with its required tests in the
same commit; no failing or pending proposed-API tests are added in this investigation.

1. **Characterize the insertion point.** Preserve only tests for existing behavior: parent
   mutation observed by child callback and null field populated in parent callback visited in
   the same action. Add actual occurrence tracer covering inherited declarations, nulls,
   LINK skips and alias identity; freeze no sibling reflection order. Confirm AUTO_LINK's
   current LinkTo/method/Closure sequence. Gate: existing semantics proven without handlers.
2. **Prove the public boundary vertically.** After accepted ADR, introduce the minimum runtime
   meta-annotation/handler/context and wire one AUTO_LINK field occurrence after existing
   callbacks. A consumer-owned `@NormalizeText` String field annotation trims through assign;
   a separate relationship example uses only generated Builder interfaces/factory narrowing.
   Gate: Java 17, dynamic and static Groovy consumers, Groovy 3/4/5 classpath; no internal imports;
   runtime -> annotations only, unchanged generated Builder descriptors. Field declaration and
   actual annotation survive separate schema/annotation compilation and inheritance.
3. **Enforce bounds and isolation.** Compile-time diagnostics for supported source declarations;
   equivalent runtime checks for precompiled annotation/handler inputs. Test raw/wildcard/generic
   superclass handler cases, invalid constructors, missing runtime retention, invalid target,
   collections/maps/arrays, duplicate participants, Owner/Builder-only fields and shadowing.
   Gate: error names remediation and actual field; no unsupported dispatch silently ignored;
   two sessions/two occurrences receive fresh handlers and expired contexts cannot assign.
4. **Prove Closure contract or stop.** Consumer sentinel, Class-literal Java Closure, Groovy
   inline Closure and static result/delegate tests. Explicitly test owner/caller fallback failure,
   wrong result type, missing constructor, explicit argument count and thrown-cause location.
   Gate: selected contract works in all three separately compiled Groovy lanes; existing
   DELEGATE_FIRST lifecycle Closures retain behavior. If failed, defer or explicitly stage scope.
5. **Freeze compatibility and document.** Groovy 4/5 named-module consumer fixture with runtime
   and custom handler/annotation module, exported public handler constructor package and Schema
   packages opened only to runtime as currently required; no add-exports/add-reads workaround.
   Java reflection cannot instantiate a handler in an unexported package: diagnose the exact
   export/open remediation, do not broadly open consumer modules. Groovy 3 named modules stay
   unsupported under ADR 0014. Verify fresh same-session assignment, foreign Builder rejection,
   aggregation wrapper immutability, imported root dispatch and Template copy replay in the
   recipient lifecycle; value-only Template creation performs no extra lifecycle. Completed
   serialization retains no handler/context/Builder. Add documentary `@Issue("867")`,
   `@Tag("documentary")`, `@See` coverage and Model-Phases/user migration navigation plus CHANGES
   only when delivered. Inventory the new SPI; review Sonar and all affected compatibility lanes.

No new ownership selection, rehydration, Template event replay model, importer mode or domain
algorithm is required. Existing assignment rejects invalid relationships. A handler creating a
child in phase 20 must not imply AUTO_CREATE/OWNER phases rerun for it: characterize late-created
ownership state separately and restrict the consumer tracer to already constructed relationships
until that existing behavior is understood. This is a release acceptance boundary, not a promise
that every mutation is suitable in every phase.

## Maintainer decisions and delivery state

Required before an ADR: AUTO_LINK-only first phase; callback-before-handler order; direct field
allowlist and rejection policy; runtime-owned annotation versus weakly typed annotations-owned
marker; exact generic validation; per-invocation construction; multiple/shadowed declarations;
deterministic field order; context assignment/lifetime; enforceable Closure delegate/result boundary and whether full
Closure support gates 4.1. These are public compatibility choices, not implementation details.

The narrow seam warrants continued design, not forced deferral of the whole concept. Exact 4.1
recommendation: **conditional candidate, not committed scope; leave #867 untargeted until the
choices and Java/Groovy/JPMS consumer tracer pass**. Existing `@AutoLink` remains supported.
No ScHelm code, annotation, type, algorithm or policy is proposed for KlumAST.

## Validation evidence

- `:klum-ast:test --tests '*FieldPhaseTraversalCharacterizationTest'`: passed, two tests.
- `:klum-ast:test`: affected module baseline suite passed: 1,610 tests, zero failures/errors,
  15 pre-existing skips. The two new tests have no skips.
- `:klum-ast:groovy4Tests :klum-ast:groovy5Tests --tests '*FieldPhaseTraversalCharacterizationTest'`:
  passed. Gradle applies the trailing filter to the last requested task: Groovy 4 ran the full
  module suite (1,610 tests, zero failures/errors, 15 pre-existing skips), while Groovy 5 ran
  the two characterization tests with no skips. Both lanes independently compiled test sources.
- Referenced ADR existence checks and staged `git diff --check`: passed.
- No proposed handler, Java consumer, Closure member, or JPMS tracer has been implemented or
  validated; their acceptance gates remain open. Compatibility evidence here is only current
  traversal behavior, not the proposed extension API. The full Groovy 5 module suite was not run.
- No user-visible behavior shipped; CHANGES and current user documentation remain unchanged.
  Tracker impact: Related #867 only; issue stays open/untargeted with labels and milestone intact.

This work requires Hive reconciliation after maintainer decisions; it must not be archived by
its worker. The branch stops at a local committed decision brief, with no PR publication.
