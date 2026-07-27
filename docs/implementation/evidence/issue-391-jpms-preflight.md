# #391 JPMS preflight evidence

This record captures the starting artifact boundary for the #391 named-module
tracer. It is a negative preflight, not proof of a supported JPMS arrangement.

## Baseline

The five KlumAST artifacts were built from `45c7b377` before any package move or
module descriptor was introduced:

```shell
./gradlew :klum-ast-annotations:jar :klum-ast-runtime:jar :klum-ast:jar \
  :klum-ast-jackson:jar :klum-ast-bean-validation:jar
```

`jar --describe-module` reports derived automatic module names rather than
explicit descriptors:

| Artifact | Derived module name |
| --- | --- |
| `klum-ast-annotations` | `klum.ast.annotations` |
| `klum-ast-runtime` | `klum.ast.runtime` |
| `klum-ast` | `klum.ast` |
| `klum-ast-jackson` | `klum.ast.jackson` |
| `klum-ast-bean-validation` | `klum.ast.bean.validation` |

These identities are not the #391 module-name contract. The intended names are
the `com.blackbuild.klum.ast…` names recorded in issue #391.

## Reproducible named-layer failure

Resolving the current compiler and runtime artifacts as automatic modules fails
before a schema can be compiled:

```shell
java --module-path klum-ast-runtime/build/libs:klum-ast/build/libs \
  --add-modules klum.ast.runtime,klum.ast --list-modules
```

The JVM rejects the layer because `klum-ast` and `klum-ast-runtime` both contain
`com.blackbuild.klum.ast.validation`; they also both contain
`com.blackbuild.klum.ast.util.layer3`. This confirms the package-split premise
of #391 using the produced artifacts.

## Tracer consequence

The positive tracer cannot add descriptors to the present artifacts and call
that JPMS support. It must first produce a deliberately isolated candidate
package layout with one owner for every package. That candidate remains
unpublished until it passes the named-schema Groovy 3/4/5, service, adapter, and
classpath fixtures required by #391.

The tracer must not use `--add-reads`, `--add-exports`, `--patch-module`, or a
repackaged upstream dependency to hide this failure.

## Cross-Groovy descriptor probe

`JpmsGroovyModuleIdentityTest` compiles a tiny named Java module whose class
extends `GroovyObjectSupport`. It runs in the existing Groovy 3, 4, and 5 lanes.
For each lane it proves all of the following:

- compilation succeeds only when `module-info.java` requires the module found
  in that lane's Groovy JAR;
- compilation fails with the other generation's identity; and
- compilation fails when the descriptor omits a Groovy requirement.

The matching names are `org.codehaus.groovy` for Groovy 3 and
`org.apache.groovy` for Groovy 4 and 5. Therefore, an explicit KlumAST
descriptor that directly reads Groovy cannot name one fixed `requires` target
and compile against all supported Groovy generations.

The second probe separately compiles the Groovy-referencing class on the
classpath, then supplies a descriptor without a Groovy requirement. A named
consumer that itself requires Groovy still fails to load that class with an
`IllegalAccessError`: a consumer cannot donate its read edge to the library.
This rules out attaching a descriptor after ordinary compilation as a portable
workaround.

This applies directly to KlumAST rather than only to the synthetic fixture:
the annotation artifact contains Groovy `Closure`-based annotation helpers,
runtime's `KlumFactory` directly exposes Groovy types, and the compiler's
`DSLASTTransformation` uses Groovy AST types extensively. Any future explicit
descriptor would need a separately approved change that removes or isolates
those dependencies from the descriptor-owning module.

ADR 0014 records the resulting 4.0 decision: explicit named-module support is
for Groovy 4/5, while Groovy 3 remains a supported classpath configuration.

## Confirmed scope boundary

`InternalKlumBuilder` remains internal in 4.0. No general Builder-phase
extension export is introduced by #391, and Klum-Wrap is not a qualified-export
consumer. A later public Builder-phase extension contract must be additive and
backed by a concrete consumer and named-module fixture.
