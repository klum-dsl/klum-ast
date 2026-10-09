# #867 LP-1 consumer evidence: ordinary Groovy Fact binding map

Date: 2026-10-10. Status: executed ScHelm consumer evidence; corrects the earlier prospective LP-6 classification.
Authority: ScHelm implementation report and independently inspected consumer source; documentation only.
Related: #867, [LP-1 evidence](../issue-867-lp1-evidence.md),
[LP-6 plan](../adr-0028-annotation-driven-lifecycle-participants.md#lp-6--optional-closure-evaluation-early-reuse-probe-d7).

## Executed consumer contract

ScHelm's domain annotation uses ordinary Groovy to express
`Map<String, String>`, from **Environment Fact name** to **target Domain relationship name**:

```groovy
@FactBinding({ [messaging: 'facts'] })
OrderKafka messaging
```

Its annotation member is typed:

```groovy
Class<? extends Closure<Map<String, String>>> value()
```

The external LP-1 mutator instantiates and calls the Closure using existing public Groovy APIs:

```groovy
Closure<Map<String, String>> bindingClosure = InvokerHelper.invokeConstructorOf(
    context.annotation.value(),
    [null, null] as Object[]
) as Closure<Map<String, String>>
Map<String, String> bindings = bindingClosure.call()
```

The Closure contains a map literal and needs no delegate. Each invocation constructs a fresh Closure
with null owner/thisObject and calls it without arguments. This consumer code does not use a KlumAST
Closure helper, fixed-delegate protocol or single-argument evaluation protocol.

The handler reads `environment` and its existing `facts` Cluster through `InvokerHelper.getProperty`.
The completed-LINK wrapper forwards these dynamic property reads to completed Model values, so the
Cluster supplies the existing named Facts. For each mapping, it selects the Fact by key and calls
`InvokerHelper.invokeMethod(context.targetBuilder, domainField, fact)`. Generated Builder methods
and existing checked assignment enforce the accepted relationship types and LINK rules.

Explicit names and the Cluster replace consumer Schema hierarchy scans, reflective field discovery
and reflective setter selection. Iterating the map allows several Fact relationships on a Domain.
The handler preserves missing-Fact and incompatible-target diagnostics without an Owner backlink or
Domain binding lifecycle method. Named invocation remains dynamic; this is not compile-time checking
of relationship names or a new typed completed-LINK getter contract. Generic declarations record the
consumer's Map typing; they are not evidence of exhaustive runtime validation for arbitrary expressions.

## Qualification result and correction

ScHelm reports its full `./gradlew check` passing with this implementation. Consumer implementation
commit `e00e18f69230a6df0252423e85114ca5c865bab3` and documentation commit
`06c7c51e004de39568a9173ee302945294b21947` were independently verified, and the annotation/handler
source above was inspected. This is successful **LP-1 consumer code using ordinary Groovy**, including
the completed-LINK Cluster read. It supersedes the earlier description of this literal-map contract as
prospective LP-6 qualification requiring a public helper, raw `Map.class` result check or additional
helper-driven entry validation.

LP-6 may still be useful for richer typed delegates or provider expressions. Any future KlumAST helper
remains subject to [ADR 0028 D7](../../adr/0028-annotation-driven-lifecycle-participants.md#d7--closure-members):
fresh evaluation, handler-selected fixed delegate, `DELEGATE_ONLY`, the same delegate as the single
explicit argument, and checked results. This ordinary zero-argument literal does not qualify that helper
protocol or create a need to implement it. No public helper name/signature is proposed here.

The tested KlumAST artifact remains `4.1.0-dev.329+codex.issue.867.lp1.ce36fb9` from
`ce36fb99e85df2644ea7e687aa74c71b7528c1c4`. No KlumAST implementation, public API, accepted ADR scope,
optional-gate status, release targeting or Maven Local publication changes follow from this correction.
