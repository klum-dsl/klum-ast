# Source prevention for Builder copy-route conflicts (#1011)

Issue: [#1011](https://github.com/klum-dsl/klum-ast/issues/1011)

## Accepted 4.2 RC boundary

The maintainer selected compiler rejection with required recompilation. This slice rejects effective Schema-authored
Builder `copyFrom(Object)` signatures, including inheritance, generic erasure and Groovy default-argument overloads.
It preserves distinct overloads and ordinary Map/Model copying. The diagnostic names the declaring Builder and authored
method descriptor, identifies the effective conflicting signature, explains the shadowed framework route and requests
renaming/recompilation.

This does not change copy execution in `GeneratedKlumBuilder` or `InternalKlumBuilder`, make routing non-overridable,
rewire Templates/imports/Factories, or adapt older compiled conflicting Schemas. Those binaries retain the old behavior.
For an inherited collision, rename/recompile the declaring parent as well as affected descendants. See
[the migration guide](../user/Migration.md#reserved-builder-copy-signature).

## Compiler seam and coverage

`DSLASTTransformation.diagnoseCopyFromOverrides` inspects effective Builder methods after delayed projection actions,
using the existing structured-application collision check as its pattern. It excludes the framework's own declaration,
checks the JVM-erased parameter type through `redirect()`, and simulates omission of default parameters from right to
left before Groovy emits those overloads. It does not reserve the entire `copyFrom` name.

`CopyFromCollisionTest` is driven by `@Issue('1011')` and covers:

- Object, untyped, legacy `@Mutator`, generic-erased and default-generated rejection;
- source inheritance in either declaration order;
- descendant compilation against a separately compiled conflicting parent (an ordinary generated parent whose Builder
  method is renamed in bytecode to reproduce an older compiler's output without an external artifact dependency);
- allowed String and bounded-generic overloads, required extra parameters, and Model-only methods;
- ordinary Map/Model copy results and retained-Builder completed-session rejection;
- sealed LINK-recipient and sealed Builder-source rejection with a distinct String overload present.

The binary-parent fixture only proves descendant source rejection. It does not claim runtime protection for old binaries.
Before implementation, the focused 17-case baseline had 12 failing rejection assertions and five passing controls;
after implementation all 17 passed. The final suite adds the sealed-recipient/source control.

## Remaining follow-up and tracker impact

Runtime hardening is deliberately deferred. A future change must qualify a non-overridable guarded route across all
framework copy execution, including internal Template/default application and Factory/import routes, and must account
for old conflicting binaries. Making the existing overridable method final alone risks linkage failure. No old-binary
repair, release qualification or runtime-hardening completion follows from this compiler change.

Issue #1011 remains open for Hive reconciliation of the accepted source-only delivery and any separately tracked runtime
follow-up. This localized correction does not alter curation, dependencies or release-gate ownership.
