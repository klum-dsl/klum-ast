# Original WIRE-PLAN/B binary fixture

`wire-plan-b-938.jar` was successfully compiled from `Original.groovy` with the Groovy 3 compiler and
KlumAST compiler sources pinned to merged PR #938, commit `3d13ffa2db91aa2245ec1041952b16de43e01d4c`.
It retains the original carrier bytes, including pre-correction plural property descriptor groups.
The runtime reader test loads this JAR directly; it never regenerates its carrier with the current compiler.

The isolated historical build used `:klum-ast:classes` and its main compile classpath, followed by
`org.codehaus.groovy.tools.FileSystemCompiler -d classes Original.groovy` and `jar --create`.
Both compiler and packaging completed successfully. The Schema and enum have initialization traps.
The fixture is for metadata compatibility only; it introduces no legacy interpretation or runtime policy.

SHA-256: `33cb043219683b6ef11b5cf7b85676a0be4857dc874d13644b913d036e2b5bec`.
