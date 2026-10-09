# Issue #837: generated inner-name overlap

Related: [#837](https://github.com/klum-dsl/klum-ast/issues/837), [PR #839](https://github.com/klum-dsl/klum-ast/pull/839).

## Existing contract and reproduction

The collection and Cluster mechanisms can occur in one valid Schema without an earlier semantic rejection:

```groovy
@DSL class Application {
    List<Service> services
    Service primary
    @Cluster Map<String, Service> services() { null }
}
@DSL class Service { String name }
```

`AlternativesClassBuilder` generates `Application$_services` for the collection. Later,
`ClusterFactoryBuilder` selects `primary` and requests that same binary name. Their public contracts are separately
registered as `CollectionFactory_services` and `ClusterFactory_services`; the implementation is not intentionally shared.

A focused compilation probe using the actual generator sources from master `cf77369f` (before #837) produced Groovy's
`Invalid duplicate class definition` for this case, the getter spelling `getServices()`, two Cluster methods normalizing
to `services`, converter/collection overlap, and collection fields named `Factory`, `Template`, and `TemplateFactory`.
The abstract `TemplateModel` collection case was also rejected, but #836's narrow guard already misclassified its
generated factory and issued `Rename this nested type` at line -1. Disabling the shared guard demonstrated that all eight
probed combinations otherwise reach duplicate-class rejection. These names were never supported shared infrastructure.

## Audit of other names

- `Builder` has no leading underscore; all other audited implementation-name patterns do. Its generation runs once per
  Model, so none of those paths requests the Model-owned `Builder` binary name.
- `_Factory`, `_Template`, `_TemplateFactory`, and abstract-only `_TemplateModel` are distinct, generated once per Model.
  Collection/Cluster names can overlap them through ordinary capitalized Schema member names; collection controls cover
  all four fixed names.
- Collection and Cluster implementations use `_<name>`. Both collection/Cluster overlap and two normalized Cluster names
  are executable rejection controls.
- Converter implementations use `_<fieldName>_converterClosures`. A collection or Cluster with that suffix can overlap.
  Tests exercise collection/converter overlap in both field orders and converter/Cluster overlap.
- Inherited implementations belong to their original Model's binary namespace, not a descendant's owner namespace.
  Generated public `Foo_DSL` interfaces likewise have a separate owner.

## Focused boundary

Every audited generation site adds `createGeneratedAnnotation(...)` before later generation continues. That existing
`@KlumGenerated` annotation has no source position. The shared helper ignores matching inner nodes with this marker and
leaves their generator interactions to the existing compiler checks. A source-written `@KlumGenerated` has a source
position and remains subject to the actionable source-collision diagnostic. No new marker, global reservation pass,
unique naming, sharing behavior, or Schema-style policy is introduced.

[GeneratedInnerNameCollisionTest](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/GeneratedInnerNameCollisionTest.groovy)
retains all original source-collision/output controls and adds ten generated-overlap rejection rows, an annotated-source
collision, working collection/Cluster factories under different names, and a working same-name empty Cluster (which
emits no factory). Existing #836 static Template inheritance tests remain part of focused validation.
