# Issue #487 successor proposals

These proposals are ready to file after the planning pull request is accepted. They deliberately leave umbrella
[#487](https://github.com/klum-dsl/klum-ast/issues/487) open and do not select GitHub closing syntax.

## Target recommendations

| Label | Recommended target | Dependency | Tracker relationship |
| --- | --- | --- | --- |
| NAMED-META | 4.1 | ADR 0022 planning merge | child/part of #487 |
| NAMED-DIAG | 4.1 | ADR 0022 planning merge; independent of NAMED-META delivery | child/part of #487 |
| NAMED-IDE | 4.1 | blocked by successful NAMED-META metadata delivery | child/part of #487 |
| NAMED-DSLD | later 4.x, unmilestoned initially | blocked by NAMED-META and NAMED-IDE evidence | child/part of #487 |

Keep #487 as the unmilestoned cross-release umbrella until the maintainer decides whether later Eclipse parity is part of
its closure boundary. Use the `enhancement` label for all four successors. NAMED-META, NAMED-DIAG, and NAMED-IDE are
intended to become `ready-for-agent` after issue creation and dependency wiring; NAMED-DSLD should remain `needs-triage`
until the native IntelliJ result exists.

## NAMED-META proposal

**Title:** Spike conservative compiler metadata for fixed-target named maps

**Body:**

> Part of #487. Implements the NAMED-META slice of ADR 0022.
>
> ## Problem
>
> `Foo.Create.With(name: value)` and fixed-target relationship creators accept `Map<String, ?>`, so statically compiled
> callers receive no key or value guidance even though runtime dispatch targets a known generated Builder contract.
>
> ## Primary use case
>
> A Model Writer, Schema Developer, or statically typed extension author passes a literal map to a generated root or
> fixed-target child creator and receives IDE completion plus immediate, source-local unknown-key and value-type feedback.
> Compiler checking supplies the portable metadata contract and fallback; runtime dispatch remains the later safety net.
>
> - Need horizon: Future, targeted for 4.1.
> - Workaround: viable but error-prone — execute the configuration and interpret a dynamic method failure.
> - Secondary angle: compiler diagnostics remain useful when editor assistance is absent; exact IntelliJ acceptance
>   belongs to NAMED-IDE.
>
> ## Scope
>
> Cover only literal maps passed to generated `Foo.Create.With` and concrete/default fixed-target single, collection, map,
> and Cluster relationship creators. Derive keys from the final public `Target_DSL.Builder` hierarchy so inherited methods,
> method-first overrides, `setX`, collection/map aliases, converters, `copyFrom`, and explicit one-argument Builder methods
> remain truthful.
>
> Exclude `Create.AsBuilder`, Templates, generic/custom Factory map methods, polymorphic `Class` and typed-Factory selection,
> map variables, computed/spread keys, `FromMap`/imports, broad custom-map analysis, `@NamedVariant`, and generic STC
> extensions.
>
> ## Compatibility boundary
>
> Start with executable Groovy 3/4/5 characterization of native `@NamedParam`/`@NamedParams`. The current planning probe
> shows that valid/invalid plain literals and binary consumers work, while computed and spread entries are rejected in all
> three generations. Literal maps are the accepted primary use case, so computed and spread entries are explicitly
> unsupported static forms on annotated calls. Record that boundary in tests and documentation; do not introduce a custom
> checker or new overload to recover those forms in this issue.
>
> ## Acceptance criteria
>
> - Valid root and fixed-target literals compile and run under Groovy 3, 4, and 5.
> - Unknown literal keys and incompatible literal values fail static compilation.
> - Inherited keys, method-first override, `setX`, collection/map aliases, a converter, `copyFrom`, and overloaded keys are
>   covered without rejecting a runtime-valid supported literal.
> - Ambiguous overload groups use a safe common type, up to `Object`.
> - Excluded call shapes retain the explicitly agreed behavior; computed/spread entries are rejected as documented
>   unsupported static forms.
> - Hidden implementation, public `Foo_DSL` interface, runtime-visible class-file annotations, and AnnoDocimal mirror agree
>   and use only public types.
> - Separately compiled static Groovy consumers prove the binary contract in every Groovy lane.
> - Runtime controls prove dispatch, lifecycle, ownership, materialization, and results are unchanged.
> - New tests carry this issue number in `@Issue`; shipped behavior has a linked documentary test, user guidance, and a
>   4.1 `CHANGES.md` entry.
>
> ## Commit plan
>
> 1. Characterize native metadata across Groovy versions.
> 2. Emit conservative metadata from public Builder contracts.
> 3. Preserve metadata in public bytecode and source mirrors.
> 4. Document the bounded behavior, only if the spike ships.
>
> Related: #487. ADR: `docs/adr/0022-conservative-named-map-safety.md`.

## NAMED-DIAG proposal

**Title:** Diagnose unknown dynamic named-map keys with Klum context

**Body:**

> Part of #487. Implements the independent NAMED-DIAG slice of ADR 0022.
>
> ## Problem
>
> Dynamic Model and Grape scripts are intentionally not statically type checked. An unknown named-map key currently
> escapes from Builder dispatch as a low-level `MissingMethodException` naming a generated implementation class.
>
> ## Primary use case
>
> A Model Writer mistypes a key in a dynamic `Create.With` or nested relationship map and receives a Klum-specific message
> that identifies the key and target and explains the one-argument Builder-method contract.
>
> - Need horizon: Future, targeted for 4.1.
> - Workaround: viable but error-prone — inspect the raw missing-method signature and infer the intended Builder member.
> - Secondary angle: suggestions can expose nearby supported public key names without defining a static catalog.
>
> ## Scope and constraints
>
> Change only failure translation in `InternalKlumBuilder.applyNamedParameters`. Invoke every entry through normal Groovy
> dispatch first. Preserve overload resolution, `MetaClass`, `methodMissing`, map order, partial application, lifecycle,
> ownership, and materialization. Translate only a terminal `MissingMethodException` attributable to the attempted
> Builder/key call; do not relabel an exception thrown inside a successfully selected configurator. Retain the cause.
>
> This issue does not add static checking, consult compiler metadata, or prevalidate the map.
>
> ## Acceptance criteria
>
> - Unknown root and fixed-child keys produce an actionable Klum-specific diagnostic naming the key and Model/Builder.
> - `setX`, inherited methods, method-first overrides, `MetaClass`, and `methodMissing` continue to work.
> - A nested `MissingMethodException` from user configurator code remains attributable to that user code.
> - Entry order, prior successful entries, Templates/automatic creation paths, and lifecycle counts are unchanged.
> - Representative dynamic Model and Grape-style scripts remain untyped and receive the improved diagnostic.
> - Focused tests and Groovy 3/4/5 end-to-end lanes pass.
> - New tests carry this issue number in `@Issue`; exception guidance and `CHANGES.md` are updated.
>
> ## Commit plan
>
> 1. Diagnose terminal unknown named-map keys.
> 2. Prove dynamic dispatch and lifecycle preservation.
> 3. Document actionable failures.
>
> Related: #487. ADR: `docs/adr/0022-conservative-named-map-safety.md`.

## NAMED-IDE proposal

**Title:** Verify native IntelliJ named-map support against generated contracts

**Body:**

> Part of #487. Implements NAMED-IDE from ADR 0022. Blocked by successful NAMED-META metadata delivery.
>
> ## Problem
>
> Groovy compiler support and class-file retention do not prove that IntelliJ offers key completion, value-type help, and
> invalid-key feedback from Klum's generated source mirrors and binary contracts. Adding GDSL before measuring native
> behavior would create an unnecessary second truth.
>
> ## Primary use case
>
> A Model Writer, Schema Developer, or statically typed extension author edits a literal named map in IntelliJ and
> receives key completion plus immediate, line-local key/value feedback consistent with the generated metadata contract.
> This editor experience is the primary user-facing outcome; compiler errors and runtime failures are fallbacks.
>
> - Need horizon: Future, targeted for 4.1 after NAMED-META.
> - Workaround: viable but inferior — rely on later compiler or runtime feedback without equivalent editing context.
> - Secondary angle: a narrowly demonstrated native gap may justify a contract-driven GDSL adapter.
>
> ## Scope
>
> Record the IntelliJ build/version and test both a same-project Schema/client using refreshed `Foo_DSL` mirrors and a
> clean consumer using compiled generated contracts. Cover root and fixed single/collection relationship calls, inherited
> keys, `setX`, method-first override, a precise value type, a safe-`Object` overloaded key, unknown key, wrong value type,
> and one excluded map-variable/computed case.
>
> Add no GDSL when native behavior satisfies acceptance. If a gap is demonstrated, a separate commit may add the smallest
> contributor that reads the public generated contract, fails closed for unrelated classes, and remains absent from
> compilation, packaging inputs, and runtime behavior.
>
> ## Acceptance criteria
>
> - Source-mirror and binary-consumer results are recorded separately with exact IntelliJ version evidence.
> - Completion/inspection agrees with NAMED-META's public key and type catalog for the covered cases.
> - No IDE claim exceeds the compiler's literal/fixed-target scope.
> - GDSL remains unchanged unless the evidence identifies a precise native gap.
> - Any added GDSL consumes public annotations/signatures, contributes nothing for unrelated/unresolved types, and has
>   focused materialization/fixture tests.
> - User onboarding/FAQ text states only the behavior actually observed.
>
> ## Commit plan
>
> 1. Record native IntelliJ named-map acceptance.
> 2. Bridge a demonstrated gap, conditionally and separately.
> 3. Document verified IDE behavior.
>
> Related: #487. ADR: `docs/adr/0022-conservative-named-map-safety.md`.

## NAMED-DSLD proposal

**Title:** Explore Eclipse DSLD consumption of named-map metadata

**Body:**

> Part of #487. Implements the later NAMED-DSLD exploration from ADR 0022. Blocked by NAMED-META delivery and NAMED-IDE
> evidence.
>
> ## Problem
>
> Eclipse support has no verified contract for Klum's named-map metadata. IntelliJ behavior and existing GDSL resources do
> not establish Eclipse/DSLD parity.
>
> ## Primary use case
>
> An Eclipse-based Schema Developer wants completion and type guidance for the same bounded root and fixed-target literal
> maps supported by the compiler contract.
>
> - Need horizon: Future, later 4.x; initially unmilestoned.
> - Workaround: viable — use compiler diagnostics or IntelliJ for the proven IDE experience.
> - Secondary angle: determine whether native annotation consumption makes a Klum-specific DSLD unnecessary.
>
> ## Scope
>
> First test a clean binary Schema and record Eclipse/Groovy tooling versions. Establish whether repeatable generated
> parameter annotations are consumed natively for root and fixed relationship maps. Only after a demonstrated gap, propose
> or implement a DSLD adapter that reads the public generated contract. Do not infer keys from Model fields, generated
> implementation names, or IntelliJ GDSL code.
>
> ## Acceptance criteria
>
> - Native Eclipse behavior is recorded against the stable NAMED-META contract and compared with compiler acceptance, not
>   with unverified IDE expectations.
> - Root and fixed-target relationship completion/type feedback use a clean binary Schema fixture.
> - Any DSLD need, supported Eclipse versions, packaging/discovery path, and maintenance cost are explicit.
> - Any adapter is tooling-only, consumes the public contract, fails closed, and has its own fixture and documentation.
> - No general Eclipse parity claim is made without executable/manual evidence.
>
> ## Commit plan
>
> 1. Record native Eclipse/DSLD feasibility evidence.
> 2. Add a contract-driven adapter and documentation only if the evidence justifies production work.
>
> Related: #487. ADR: `docs/adr/0022-conservative-named-map-safety.md`.
