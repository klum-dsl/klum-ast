# #391 JP-2b Layer 3 package ownership

This JP-2b slice removes the remaining compiler/runtime split package after
the merged [JP-2 validation ownership](issue-391-jp2-validation-package-ownership.md)
slice. It does not introduce module descriptors or make the named-schema
fixture positive; JP-3 owns descriptor work.

## Ownership decision

The runtime artifact owns the existing
`com.blackbuild.klum.ast.util.layer3` package for this blocker-removal slice.
It contains Layer 3 runtime mechanics: automatic creation/linking phases,
composition traversal, structural paths, model visitors, and model/Builder
structure helpers.

The compiler artifact owns transformations and schema-annotation checks. They
depend on Groovy AST or KlumCast's compiler SPI and have no runtime consumer,
so they move to the non-exported
`com.blackbuild.klum.ast.compiler.internal.layer3` package.

| Artifact owner | Package in this slice | Types |
| --- | --- | --- |
| `klum-ast-runtime` | `com.blackbuild.klum.ast.util.layer3` | `AutoCreationPhase`, `AutoLinkPhase`, `BuilderStructureSupport`, `ClusterModel`, `CompositionTraversal`, `KlumVisitorException`, `LinkHelper`, `ModelVisitor`, `StructuralPath`, `StructureUtil` |
| `klum-ast` compiler | `com.blackbuild.klum.ast.compiler.internal.layer3` | `ApplyDefaultTransformation`, `ClusterFactoryBuilder`, `ClusterFieldTransformation`, `ClusterTransformation`, `DefaultValuesCheck` |

The annotations artifact continues to name the compiler transformations and
check by class name, now using their compiler-internal names. That preserves
classpath schema compilation without creating an annotations-to-compiler
dependency.

## Evidence and remaining boundary

`JpmsLayer3PackageOwnershipTest` inspects the compiler and runtime JAR module
descriptors in every Groovy lane. It asserts that the Layer 3 runtime package
is present only in the runtime artifact and the compiler-internal package only
in the compiler artifact.

Both previously observed compiler/runtime package splits are now removed.
JP-3 must still create the explicit descriptor set before JP-1b can be retried
as a positive named-schema fixture.
