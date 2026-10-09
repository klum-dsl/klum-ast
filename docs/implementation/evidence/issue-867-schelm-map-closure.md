# #867 LP-6 consumer evidence: explicit Fact binding map

Date: 2026-10-10. Status: consumer design input, not implemented or qualified.
Authority: ScHelm consumer refinement; recording and assessment only, with no implementation request.
Related: #867, [ADR 0028 D7](../../adr/0028-annotation-driven-lifecycle-participants.md#d7--closure-members),
[LP-6 plan](../adr-0028-annotation-driven-lifecycle-participants.md#lp-6--optional-closure-evaluation-early-reuse-probe-d7).

## Concrete consumer contract

The proposed ScHelm domain annotation carries a Groovy annotation Closure returning
`Map<String, String>`, from **Environment Fact name** to **target Domain relationship name**:

```groovy
@FactBinding({ [messaging: 'facts'] })
OrderKafka messaging
```

A single annotation can describe several relationships on the same Domain:

```groovy
@FactBinding({ [messaging: 'facts', auditDatabase: 'auditFacts'] })
OrderDomain services
```

The second example illustrates the mapping shape, not an implemented ScHelm Schema declaration.
Domain annotation names, Fact selection and relationship names are entirely consumer-owned.

The external handler would:

1. Select the annotation's Closure member and evaluate a fresh Closure with the expected `Map` result.
2. Read the selected Environment's existing `facts` Cluster map of named, completed Fact values.
3. For each entry, select the Fact by the map key and invoke the generated public target Builder method
   named by the map value with that completed Fact. Existing generated method/checked-assignment
   contracts enforce the accepted relationship type and LINK rules.

This replaces consumer Schema hierarchy scans, reflective field discovery and reflective setter
selection with explicit domain vocabulary, the existing Cluster map and named generated method calls.
It supports several Fact relationships per Domain without introducing an application-specific handler,
Owner backlink or Domain lifecycle binding method. Named invocation is still **dynamic dispatch**;
it is not a statically checked relationship-name contract. No generic property-access or invocation API
is requested from KlumAST. Fact lookup, mapping validation and named invocation remain consumer policy.

## Fit with the accepted helper boundary

The map literal does not need an application-specific delegate. It can ignore a fixed handler-selected
public context delegate, for example the existing mutation context; the **same object** would also be
passed as the single explicit argument. This is a qualification candidate, not a newly fixed helper
signature or delegate requirement. Keep fresh instantiation, `DELEGATE_ONLY`, no lexical owner/caller
fallback and checked results. The literal needs no special zero-delegate mode or configurable
resolution strategy. The handler, rather than KlumAST dispatch, chooses whether and which member to evaluate.

Checking `Map.class` proves only the raw result kind: Java erasure cannot validate `Map<String, String>`
entries. The consumer must check that keys and values are Strings before using them; the probe should
also assess source-level Closure result typing. A generic class token alone is not static typing proof.
No Map-specific validation framework is implied for the reusable helper. The existing LP-6 plan still
owns its Java-first API shape, inline Groovy and Java Closure classes, diagnostics and Groovy 3/4/5 probes.

The earlier [completed-LINK evidence](../issue-867-lp1-evidence.md#external-schelm-qualification-completed-link-reads)
remains applicable: existing dynamic Groovy property reads expose completed values, while direct generated
relationship getters on the wrapper can return null. This refinement uses the Environment's existing
`facts` Cluster read contract; its exact completed-LINK read path still needs a consumer probe. The map
Closure supplies names, not Facts or a completed-value unwrap. Therefore this contract neither requires
nor qualifies a new typed completed-LINK getter; it does not establish that the Closure helper solves
that separate read limitation.

## Qualification cases for later LP-6 work

These are prospective checks, not passing acceptance evidence or additional implementation authority:

- Literal one-entry and multi-entry maps under dynamic and static Groovy; Java-authored Closure and
  separate Groovy 3/4/5 compilation using the public helper once explicitly authorized.
- Fresh Closure instance for every invocation; fixed delegate identity, the same single explicit
  argument, and rejected owner/caller fallback under `DELEGATE_ONLY`.
- Wrong raw result, null result policy and non-String entries; preserve the distinction between helper
  result-kind checks, compile-time generic inference and consumer entry validation.
- Existing Environment `facts` Cluster read on a completed LINK wrapper; exact Fact identity after
  generated Builder assignment; several differently typed Fact relationships on one target Domain.
- Unknown Fact name, absent/nonmatching target method and incompatible Fact type, with preserved
  causes and participant annotation/handler/phase/field context. Consumer diagnostics and policies for
  empty maps, missing values or repeated target names remain ScHelm decisions.

LP-1 currently provides no public annotation-Closure evaluation helper. Its published consumer artifact
remains `4.1.0-dev.329+codex.issue.867.lp1.ce36fb9` from
`ce36fb99e85df2644ea7e687aa74c71b7528c1c4`. The earlier successful ScHelm integration proves the existing
consumer path, not this proposed Closure-map contract. LP-6 stays independently deferrable, names/signatures
remain provisional, and no implementation, ADR scope, release targeting or Maven Local publication changes
follow from this record.
