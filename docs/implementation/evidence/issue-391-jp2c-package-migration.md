# #391 JP-2c public/internal package migration map

This is the durable old-to-new source, resource, and generated-name map for
ADR 0014's JP-2c migration. It is intentionally a 4.0 recompilation break:
no old package is retained as a forwarding alias.

JP-2c relocates the annotations and runtime artifacts atomically. The map also
records the compiler destinations that JP-3 will use, so generated metadata and
call sites are not left with an implicit intermediate contract. JP-2c does not
add `module-info.java` files or choose JP-3's qualified exports.

## Package map

| Current package | Final package | Owning artifact | JP-2c action |
| --- | --- | --- | --- |
| `com.blackbuild.groovy.configdsl.transform` | `com.blackbuild.klum.ast` | annotations | move the schema-authoring annotations and annotation helpers |
| `com.blackbuild.groovy.configdsl.transform.cast` | `com.blackbuild.klum.ast.internal.cast` | annotations | move as annotation implementation linkage; it is not exported |
| `com.blackbuild.klum.ast.util.copy` | `com.blackbuild.klum.ast.copy` | annotations | move the copy-policy annotations |
| `com.blackbuild.klum.ast.util.layer3.annotations` | `com.blackbuild.klum.ast.layer3` | annotations | move the Layer 3 annotations |
| `com.blackbuild.klum.ast` | `com.blackbuild.klum.ast.runtime` | runtime | move the model marker interfaces, eliminating the annotations/runtime root collision |
| `com.blackbuild.klum.ast.util` | `com.blackbuild.klum.ast.runtime` or `com.blackbuild.klum.ast.runtime.internal` | runtime | retain only the #468 client and generated-hook types in the runtime API; move mechanics, companions, proxies, lifecycle, reflection helpers, and generated implementation support under `internal` |
| `com.blackbuild.klum.ast.process` | `com.blackbuild.klum.ast.runtime` or `com.blackbuild.klum.ast.runtime.internal.process` | runtime | retain `PhaseAction`, `BuilderVisitingPhaseAction`, and `ModelVisitingPhaseAction` as the bounded extension seam; move phase mechanics under `internal` |
| `com.blackbuild.klum.ast.util.layer3` | `com.blackbuild.klum.ast.runtime.internal.layer3` | runtime | move Layer 3 runtime mechanics; `ModelVisitor` is not exported |
| `com.blackbuild.klum.ast.validation` | `com.blackbuild.klum.ast.runtime.validation` or `com.blackbuild.klum.ast.runtime.internal.validation` | runtime | retain the result/issue/exception and `InstanceValidator` API; move validation mechanics under `internal` |
| `com.blackbuild.groovy.configdsl.transform.ast…` | `com.blackbuild.klum.ast.compiler.internal…` | compiler | JP-3 destination; update imports and generated references in JP-2c without moving compiler sources or adding a descriptor |
| `com.blackbuild.klum.ast.jackson` helper/modifier types | `com.blackbuild.klum.ast.jackson.internal` | Jackson | JP-3 destination; retain the documented importer and `KlumAstModule` API |
| `com.blackbuild.klum.ast.validation.bean` | unchanged public adapter package | Bean Validation | no source move in JP-2c |

The exported package allowlist after JP-2c is therefore annotations
`com.blackbuild.klum.ast`, `.copy`, `.layer3`, and runtime
`com.blackbuild.klum.ast.runtime`, `.runtime.validation`. Package export
statements remain JP-3 work.

## Resource and generated-name map

| Current name | JP-2c name | Consumer |
| --- | --- | --- |
| `META-INF/services/com.blackbuild.klum.ast.process.PhaseAction` | `META-INF/services/com.blackbuild.klum.ast.runtime.PhaseAction` | `ServiceLoader` runtime phase registration |
| `META-INF/services/com.blackbuild.klum.ast.validation.InstanceValidator` | `META-INF/services/com.blackbuild.klum.ast.runtime.validation.InstanceValidator` | runtime and Bean Validation service loading |
| `@KlumCastValidator` class-name bindings | compiler-internal class names listed in the package map | KlumCast compiler validation binding |
| `@GroovyASTTransformationClass` class-name bindings | still resolve compiler-owned transformation classes; their JP-3 target is `com.blackbuild.klum.ast.compiler.internal…` | Groovy AST activation |
| generated `Foo_DSL` descriptors and factory signatures | annotations in `com.blackbuild.klum.ast`; runtime hooks in `com.blackbuild.klum.ast.runtime` | generated Java and static-Groovy consumers |
| runtime GDSL imports | the same annotations/runtime public names | IDE-only GDSL metadata |

## Verification boundary

The JP-2c ownership test scans the produced annotations, runtime, compiler,
Jackson, and Bean Validation JARs. It rejects an annotations/runtime root
collision, a legacy package in either migrated artifact, runtime mechanics in
an exported package, duplicate package ownership, and stale service-resource
names. The existing generated Java/static-Groovy and service tests remain the
behavioral proof across Groovy 3, 4, and 5.

JP-3 consumes this map when it creates descriptors. It must not restore a
legacy package, add an alias, or use an `--add-reads`, `--add-exports`, or
`--patch-module` workaround.
