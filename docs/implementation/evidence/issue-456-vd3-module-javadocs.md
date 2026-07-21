# #456 VD-3 isolated module-Javadoc evidence

This is a sanitized, durable local record for the VD-3 implementation slice. It records
only the external facts and local validation needed to review the slice; it excludes prompts,
credentials, raw command output, host details, and inferred telemetry.

## External contract evidence

- [Issue #456](https://github.com/klum-dsl/klum-ast/issues/456) requires durable,
  version-specific Javadocs for the 4.x multi-module product.
- [ADR 0013](../../adr/0013-versioned-documentation-and-javadocs.md) fixes the six-module
  set, `/&lt;version&gt;/api/&lt;module&gt;/` bases, and the BOM/mirror exclusions.
- [PR #537](https://github.com/klum-dsl/klum-ast/pull/537) delivered the VD-1 renderer
  contract that this slice extends without changing the release or Pages boundary.

## VD-3 evidence boundary

- The exact-version renderer invokes only the ordinary `javadoc` tasks for `klum-ast`,
  `klum-ast-runtime`, `klum-ast-annotations`, `klum-ast-jackson`,
  `klum-ast-bean-validation`, and `klum-ast-gradle-plugin`.
- Each output is copied to its own `/&lt;version&gt;/api/&lt;module&gt;/` base and is linked from
  the exact version's `/api/` landing. The BOM has no API base.
- The renderer rejects a missing module output, a non-allowlisted input, a duplicate module
  output, or an IDE-mirror page before creating an exact-version render. A failed module
  `javadoc` task is a task dependency failure, so the renderer action cannot claim a
  partially generated labelled result.

## Local verification

`verifyVersionedDocumentationRenderer` exercises the renderer fixture and all six standard
module-Javadoc tasks. It checks representative public types, separate bases, module-task
wiring, BOM/mirror exclusion, failure containment, and API navigation.
