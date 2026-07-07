# Breadcrumbs: capture source-location (file + line) for better diagnostics

Short: Enhance `BreadcrumbCollector` to capture source-location information (where available) for objects created from Groovy scripts/files. When creating models from in-memory strings, fall back to best-effort context.

Why: Providing file:line context in validation errors greatly reduces time to fix user models.

Acceptance criteria:
- Breadcrumbs include optional `sourceFile` and `line` fields when models are created from files.
- Error messages printed to users include these fields and an annotated snippet where feasible.
- Tests demonstrate improved error messages for typical scripts loaded from files.

Scope & plan:
1. Investigate Groovy API for AST or runtime metadata giving source location for closures/expressions.
2. Add optional fields to Breadcrumb and populate when available.
3. Update error formatting to include file:line and on-demand context snippets.

