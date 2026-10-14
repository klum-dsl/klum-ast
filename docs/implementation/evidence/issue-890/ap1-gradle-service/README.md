# AP-1 ordinary Gradle contributor discovery qualification

Date: 2026-10-14. Related: [#890](https://github.com/klum-dsl/klum-ast/issues/890).

Public KlumCast `0.5.0-rc.2` passes ordinary Gradle provider discovery on Java 17 / Groovy 3, 4 and 5.
Exact Central-only inputs, identities, production coverage, full-check and delivery state are recorded in
[current AP-1 evidence](../../../issue-890-ap1-rc2-evidence.md). All development/Maven-Local and RC.1
inputs are superseded historical diagnostics and supply no current qualification evidence.

Before restoring production, the independent no-op contributor control compiled on all three lanes
with RC.2, repairing the AST-transform/source-loader boundary that failed with RC.1. Its old injected
source/resource route has been removed: the production service now owns that resource path.

Current executable qualification uses only the normal build:

```shell
./gradlew :klum-ast:test :klum-ast:groovy4Tests :klum-ast:groovy5Tests \
  --tests '*AnnotationPropagation*Test' --max-workers=2
```

`AnnotationPropagationGradleTest` compiles its Schemas through the actual Gradle GroovyCompile task with
separately javac-compiled policies. Required, AutoCreate/DefaultValues, Query and Cluster then demonstrate
ordinary consumer behavior. `AnnotationPropagationArtifactTest` qualifies the shipped provider descriptor,
separate artifacts, Groovy 4/5 named activation and compiler-free runtime consumers. No init script,
provider-resource overlay, global replacement, direct local-transform call or loader shim is needed.

RC.1 failed at this ordinary Gradle service-loading boundary. It is immutable and unsuitable for AP-1.
Current acceptance is exclusively the public RC.2 production matrix and its maintained tests.
