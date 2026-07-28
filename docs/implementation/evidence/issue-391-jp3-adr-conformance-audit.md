# #391 JP-3 ADR 0014 conformance audit

This fail-closed audit was performed against merged JP-2c-FIX baseline
`9eeb545a5e99d6e1861805df5167e732ac9fcc59` before any JP-3 module-descriptor
source was added. It distinguishes a clean relocation boundary from the
remaining descriptor-design gate.

## Confirmed relocation and classpath boundary

The five freshly built JARs have exclusive final package ownership:

| Artifact | Verified package families |
| --- | --- |
| annotations | `com.blackbuild.klum.ast`, `.copy`, `.layer3`, `.internal.cast` |
| runtime | `com.blackbuild.klum.ast.runtime`, `.runtime.validation`, `.runtime.internal...` |
| compiler | `com.blackbuild.klum.ast.compiler.internal...` only |
| Jackson | `com.blackbuild.klum.ast.jackson`, `.jackson.internal` |
| Bean Validation | `com.blackbuild.klum.ast.validation.bean`, `.bean.internal` |

The source/resource/GDSL scan finds no legacy
`com.blackbuild.groovy.configdsl`, interim compiler, `util`, `process`, or old
runtime-validation reference. Annotation transformation and KlumCast-validator
strings name compiler-internal classes. The runtime and Bean Validation service
resources use `PhaseAction` and `InstanceValidator`'s relocated public names.
The generated JAR ownership/service assertions in
`JpmsPackageBoundaryTest` pass in the Groovy 3, 4, and 5 lanes.

`JpmsGroovyModuleIdentityTest` also passes in every lane: Groovy 3 resolves as
`org.codehaus.groovy`, while Groovy 4 and 5 resolve as `org.apache.groovy`.
The audit found no execution use of `--add-reads`, `--add-exports`, or
`--patch-module`; they occur only in the explicit negative-probe guard and ADR
text.

The ordinary classpath artifact remains the common production artifact and the
three lane tests above remain classpath-compatible evidence. It does not yet
claim a named-schema fixture, which belongs to JP-4.

## Descriptor contract that is already determined

ADR 0014 fixes the five module names, the public export allowlists, the
non-exported package families, Groovy 4/5's `org.apache.groovy` dependency, and
Groovy 3's classpath-only boundary. The current bytecode/import graph also
proves the bounded runtime-internal linkage candidates:

- compiler reads `runtime.internal`, `.internal.process`, and
  `.internal.layer3`;
- Jackson reads `runtime.internal` and `.internal.process`;
- Bean Validation reads only the public runtime-validation contract.

Those observations are inputs to JP-3's qualified exports, not authorization
to add a broad export or a third-party SPI.

## KlumCast reflective-validator inventory

The public annotation bindings name eight compiler implementation checks. Klum
Cast reflectively instantiates them through the established
`@KlumCastValidator` route; they are not exported APIs:

| Compiler package | Bound checks | JP-3b qualified opening |
| --- | --- | --- |
| `compiler.internal.ast` | `FieldAstValidator` | Add `com.blackbuild.klum.cast.compiler` alongside the existing Groovy target. |
| `compiler.internal.ast.mutators` | `WriteAccessMethodCheck` | Add `com.blackbuild.klum.cast.compiler` alongside the existing Groovy target. |
| `compiler.internal.layer3` | `DefaultValuesCheck` | Add `com.blackbuild.klum.cast.compiler` alongside the existing Groovy target. |
| `compiler.internal.validation` | `CheckDslAnnotation`, `CheckForPrimitiveBoolean`, `OverwriteMapCheck`, `OverwriteSingleCheck`, `ValidateAnnotationCheck` | Already open only to `com.blackbuild.klum.cast.compiler`. |

`compiler.internal.ast.converters` has no `@KlumCastValidator` binding and
remains open only to Groovy. No other compiler package is a reflected KlumCast
validator category, so no further directive or architectural decision is
required.

## Blocking descriptor ambiguity

The relocation is clean, but the full ADR conformance check is **not clean**.
ADR 0014 says that compiler activation remains a descriptor-owned Groovy
transformation provider. The compiler JAR has no
`META-INF/services/org.codehaus.groovy.transform.ASTTransformation` provider.
Instead, annotation classes use `@GroovyASTTransformationClass` strings that
point to local transformations in four non-exported compiler packages:

- `com.blackbuild.klum.ast.compiler.internal.ast`
- `com.blackbuild.klum.ast.compiler.internal.ast.converters`
- `com.blackbuild.klum.ast.compiler.internal.ast.mutators`
- `com.blackbuild.klum.ast.compiler.internal.layer3`

The accepted material does not specify whether JP-3 must introduce a module
`provides` activation path, open exactly those packages to `org.apache.groovy`,
or use another approved named-module activation mechanism. These alternatives
have different discovery and encapsulation behavior. Choosing one without a
named-module activation fixture would make an architecture decision rather
than implement ADR 0014.

JP-3 must remain blocked until the maintainer records the activation/qualified-
opens contract. The eventual decision needs an executable Groovy 4/5
named-schema activation assertion; JP-4 owns the full schema fixture, but the
directive choice must be settled before JP-3 creates the descriptor.
