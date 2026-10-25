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

## Sonar hygiene

- Keep method cognitive complexity and nesting low. Prefer clear guard clauses; when a method mixes multiple decisions,
  extract a cohesive private method that names one responsibility.
- Extract repeated strings into constants only when they represent one stable domain term, external contract,
  annotation or member name, property key, or repeatedly asserted diagnostic. Incidental one-off test data does not
  require constants.
- Use braces for multi-line branches, nested conditionals, and `else` chains. Concise single-line guards remain acceptable
  where clearer. If a Sonar rule makes universal braces a blocking issue, seek a deliberate Sonar-rule decision rather
  than silently escalating this style policy.

This is principle-based preventative review, not a local reproduction of Sonar's metrics or a substitute for SonarCloud.
The full CI/SonarCloud run remains the sole authoritative, coverage-bearing PR quality gate. Do not use a fast upload to
that shared SonarCloud project as a pre-PR gate.

## Generated IDE properties

Contribute a literal IDE property only when an AST-generated field is absent from source PSI, such as `Foo.Create` or
`Foo.Template`. Do not use GDSL to emulate uppercase JavaBean/Groovy-property syntax: IntelliJ normalizes that syntax and
cannot represent the runtime spelling faithfully. Use an explicit operation instead, and verify its Java, Groovy, and
IntelliJ JavaBean behavior together.
