# Coding style

Follow the conventions already established in the surrounding module. The rules below document recurring review findings
that are not currently enforced by formatting tools.

## Imports and qualified names

- Import referenced Java and Groovy types and use their simple names in declarations, method bodies, annotations, class
  literals, generic arguments, and method references.
- Do not embed a fully qualified type name merely to avoid adding an import or to emphasize the type's package. Imports are
  the source file's explicit vocabulary and keep implementation code readable.
- When two referenced types have the same simple name, import the type used most often and fully qualify the less frequent
  type only at the ambiguous use sites. Keep the qualification as local as possible.
- A fully qualified name is also acceptable when an import is unavailable because of a real language, generated-source, or
  source-template constraint. Make a non-obvious exception clear with a short comment or by the surrounding generator API.
- Generated output is governed by its generator. Handwritten source snippets and templates should still declare imports
  when their format provides an import section.

Code review treats an unnecessary fully qualified name as a documented style violation, not merely an optional readability
suggestion. Replace it with an import unless one of the exceptions above applies.
