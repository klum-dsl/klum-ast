# Builder → Immutable model separation (RWBuilder)

Short: Implement an explicit `RWBuilder` generated type for each model and produce a final immutable `Model` instance in a new Phase after of `POST_TREE`. 

Why: Eliminates user confusion about mutable RW objects, prevents post-creation mutations from silently corrupting state, and makes the runtime model easier to reason about and serialize.

Acceptance criteria:
- Generated code emits `RWBuilder` and `Model` types; runtime tests demonstrate immutability of `Model` instances.
- Model instance is the same name as the original class annotated with DSL; all non transient fields are final
- Model instance has a constructor with all fields, constructor should be protected and only used by the builder.
- Creation flows should be working without changes
- Documentation updated and a migration guide produced.

Scope & plan:
1. Design the codegen changes: rename RW → RWBuilder and generate immutable Model class skeletons.
2. Implement simple migration shim mapping old factory methods to RWBuilder.
3. Add tests verifying builders mutate and final models are immutable (no setters for non-transient fields).

