# Custom equality selection diagnostic (#240)

Before implementation, the pinned Groovy 3.0.25, 4.0.32 and 5.0.6 source JARs were inspected, and an independent
CompilationUnit probe captured generated equals AST accesses at instruction selection under each compiler.
The selection results agreed in all three versions:

| Configuration | Effective selection |
| --- | --- |
| Default | Declared instance properties; owner and transient modifiers do not exclude a property. |
| `excludes` | Removes the named properties/fields; remaining managed properties still participate. |
| Explicit `includes`, including `[]` | Selects only those names; an empty list selects no state. The diagnostic treats any explicit member as deliberate. |
| `includeFields = true` | Additionally selects instance non-property fields, including synthetic fields. |
| `allNames = true` | Allows `$` names; private `$` fields still require `includeFields`. |
| `allProperties = true` | Adds pseudo-properties from getters, including getters backed by private managed fields. |
| Inheritance | No automatic superclass property traversal; `callSuper` delegates to superclass equality/hash code. |
| Handwritten equals/hashCode | Groovy generates `_equals`/`_hashCode` helpers instead of replacing the public methods. |

Source authorities: the `visit`, `calculateHashStatements` and `createEquals` methods in
[Groovy 3.0.25](https://github.com/apache/groovy/blob/GROOVY_3_0_25/src/main/java/org/codehaus/groovy/transform/EqualsAndHashCodeASTTransformation.java),
[Groovy 4.0.32](https://github.com/apache/groovy/blob/GROOVY_4_0_32/src/main/java/org/codehaus/groovy/transform/EqualsAndHashCodeASTTransformation.java), and
[Groovy 5.0.6](https://github.com/apache/groovy/blob/GROOVY_5_0_6/src/main/java/org/codehaus/groovy/transform/EqualsAndHashCodeASTTransformation.java),
plus each version's `GeneralUtils.getAllProperties`, `getInstanceNonPropertyFields`, and
`AbstractASTTransformation.shouldSkipUndefinedAware` helpers. Groovy 4/5 add POJO/getter access options, while retaining
these selection rules.

## Compiler seam

Both Klum and Groovy equality transforms run at canonicalization. Their order determines whether Groovy can select
Klum's generated `$state` storage. Klum also replaces ordinary source properties with read-only getters, while keeping
transient properties. Thus an annotation after `@DSL` selects transient properties by default but needs `includeFields`
or `allProperties` for ordinary storage/getters; a source-property includes/excludes list may be rejected in that order.
The documentary example places Groovy's annotation first. The diagnostic therefore runs in the existing instruction-selection Model verifier,
after canonicalization, and reads actual generated public equals/hashCode bodies. It records direct field accesses,
property accesses and getter calls for declared managed fields. This preserves annotation contents and generated code,
respects exclusions and annotation ordering, and avoids reimplementing Groovy selection rules.

The diagnostic does not analyze handwritten equality or delegated superclass equality, and explicit includes always
remain silent. Compiler coverage in `CustomEqualityDiagnosticTest` runs unchanged in all three isolated Groovy lanes;
it covers owner/transient state, private and `$` fields, generated storage, ordering, exclusions, includes, getters,
inheritance, handwritten methods, and the documentary remediation example. Groovy's own synthetic hash cache does
not trigger this diagnostic; `cache=true` still receives the pre-existing completed-Model mutation error. Supporting
that mutation is outside #240.
