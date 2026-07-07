# Phase-registration DSL and ordering validation for plugins

Short: Provide a minimal declarative DSL for plugin authors to register phases with `dependsOn` / `runsAfter` semantics and validate ordering at startup (reject cycles and ambiguous orderings).

Why: ServiceLoader registration is brittle; third-party plugins or future extensions should be able to declare dependencies and have the system validate them.

Acceptance criteria:
- Simple DSL exists and is documented for plugin authors.
- Startup validator checks for cycles and unresolved dependencies and fails fast with a helpful message.
- Existing core phases are registered via the same mechanism to keep a single ordering source.

Scope & plan:
1. Define a `PhaseDescriptor` POJO with id, dependsOn and runsAfter fields.
2. Move core phase registration into descriptors and run a topological sort at startup.
3. Add tests and examples for plugin authors.

