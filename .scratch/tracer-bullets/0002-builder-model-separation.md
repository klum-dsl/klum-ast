# Builder → Immutable model separation (RWBuilder)

Short: Implement an explicit `RWBuilder` generated type for each model and produce a final immutable `Model` instance at the end of `POST_TREE`. Provide migration shims so existing generated entrypoints remain functional for one major release.

Why: Eliminates user confusion about mutable RW objects, prevents post-creation mutations from silently corrupting state, and makes the runtime model easier to reason about and serialize.

Acceptance criteria:
- Generated code emits `RWBuilder` and `Model` types; runtime tests demonstrate immutability of `Model` instances.
- Backwards compatibility shims preserve the API for common creation flows for at least one major release.
- Documentation updated and a migration guide produced.

Scope & plan:
1. Design the codegen changes: rename RW → RWBuilder and generate immutable Model class skeletons.
2. Implement simple migration shim mapping old factory methods to RWBuilder.
3. Add tests verifying builders mutate and final models are immutable (no setters for non-transient fields).

