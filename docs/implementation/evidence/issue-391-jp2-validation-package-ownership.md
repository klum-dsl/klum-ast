# #391 JP-2 validation package ownership

This JP-2 slice removes the compiler/runtime split package that blocked the
real named-schema fixture recorded in
[JP-1b evidence](issue-391-jpms-real-schema-fixture.md). It makes no module
descriptor or export decision; JP-3 owns those decisions.

## Ownership decision

The runtime artifact owns the public validation contract. Its validation
result, issue, exception, phase, validator, and `InstanceValidator` service
types remain in `com.blackbuild.klum.ast.validation` until the broader JP-2
runtime-public-package migration applies ADR 0014's
`com.blackbuild.klum.ast.runtime.validation` destination.

The compiler artifact owns checks that validate schema annotations while the
AST transformation compiles a schema. They depend on Groovy AST and KlumCast's
compiler SPI, have no runtime consumer, and are therefore compiler
implementation rather than a validation API. The five checks move to the
non-exported `com.blackbuild.klum.ast.compiler.internal.validation` package.

| Artifact owner | Package in this slice | Types |
| --- | --- | --- |
| `klum-ast-runtime` | `com.blackbuild.klum.ast.validation` | `EarlyValidationPhase`, `InstanceValidator`, `KlumAnnotationsValidator`, `KlumFieldAnnotationsValidator`, `KlumInnerClassValidator`, `KlumLayeredAnnotationsValidator`, `KlumMethodAnnotationsValidator`, `KlumValidationException`, `KlumValidationIssue`, `KlumValidationResult`, `SingleObjectValidationHandler`, `ValidationPhase`, `Validator`, `ValidatorBase` |
| `klum-ast` compiler | `com.blackbuild.klum.ast.compiler.internal.validation` | `CheckDslAnnotation`, `CheckForPrimitiveBoolean`, `OverwriteMapCheck`, `OverwriteSingleCheck`, `ValidateAnnotationCheck` |

The annotations artifact retains name-based `@KlumCastValidator` bindings, now
pointing to the compiler-internal checks. That keeps annotation processing
behavior unchanged without creating an annotations-to-compiler dependency.

## Evidence and remaining boundary

`JpmsValidationPackageOwnershipTest` inspects the compiler and runtime JAR
module descriptors in every Groovy lane. It asserts that the runtime package is
present only in the runtime artifact and the compiler checks are present only
in the compiler artifact.

This is intentionally not a positive named-schema fixture. The independent
`com.blackbuild.klum.ast.util.layer3` split still prevents the current runtime
and compiler artifacts from resolving together; JP-3 must also establish the
descriptor set before the JP-1b fixture can become positive evidence.
