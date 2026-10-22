# WIRE-1 runtime candidate qualification

Date: 2026-10-22. Status: **unpublished implementation undergoing final qualification; direct fixed-key decision resolved**.
Related: [#932](https://github.com/klum-dsl/klum-ast/issues/932).

The local candidate implements the planned `KlumStructuredInput.readRoot` and `applyToBuilder` facade in runtime,
complete ordered input preparation and exact recorded-signature MetaClass execution. It snapshots Lists/Maps,
rejects cycles/null/unknown names/invalid types before execution, converts only bounded standard numbers, and reads enum
constant names from bytes without initialization. Execution materializes enums and plain data containers only after
complete preflight, invokes the same selected MetaMethod, and shares normal root/apply/owned-child/keyed-map creators.
No parser, mapper behavior, public descriptor, cache, registry, synthetic Groovy Closure or session API was introduced.

Current tests cover caller and nested/keyed/List order, all eight supported field container categories, exact primitive/
wrapper rename/ignore dispatch, inherited operations, one-payload Map/List calls, source/escaped pointer diagnostics,
late validation types, active/session/thread/Template guards, mutation-before-throw/no rollback, JVM Error propagation,
root cleanup, snapshots, enum/Schema no-initialization preflight and numeric bounds/non-finite/custom Number rejection.
Documentary tests link both facade routes to the user page; actual Java/static Groovy named-module consumers exercise
public linkage. The extended Holder fixture also exercises ordinary Default, AutoLink and LinkTo after caller LINK setup.

The complete focused structured-input matrix initially passed **121 cases per Groovy 3/4/5 lane**, zero failures/errors/skips;
actual named-module features passed on Groovy 4/5. Module license and lane-isolation checks passed. The initial decoder incorrectly accepted forged scalar Object and concrete ArrayList facts; after adding bounded
payload validation, the reader suite passes 16 cases per lane, including the untouched #938 artifact.
The extended Holder Default/AutoLink/LinkTo fixture and precise active-Builder static consumer also pass the subsequent
selected Groovy 3/4/5 and named-module runs recorded with the gate.

[The direct-key decision](issue-932-wire-1-direct-key-stop.md#accepted-resolution) now authorizes the precise compiler-owned
fixed-key fact. The implementation consumes it directly; the facade and release-facing documentation remain local until
final qualification and review. Final independent review, full affected
compiler suites, root check and delivery are still required. No GitHub issue state or release scope changes follow.

The approved fixed-key refinement passes the expanded matrix: **125 cases per Groovy 3/4/5 lane** and the actual
named-module feature on Groovy 4/5; zero failures/errors/skips. The current reader suite is **17 per lane**, including
fixed-key child initialization traps, strict role diagnostics and unchanged earlier-artifact recompile guidance.
Final repository-wide validation and independent review remain pending before the delivery handoff.
