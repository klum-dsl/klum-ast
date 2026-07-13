# Builder-first materialization of DSL Objects

Date: 2026-07-12

Status: Accepted

Sub-decisions:

- [0004 — AsBuilder composition and Builder-producing factory projections](0004-asbuilder-composition-protocol.md)

## Context

The generated `RW` class currently mutates an already allocated DSL Object throughout creation. This leaves completed objects mutable through generated `apply` methods and `KlumInstanceProxy`, while also conflating construction state with model state. A new design must retain cyclic `LINK` relationships, templates, Jackson deserialization, lifecycle callbacks, and model validation without exposing mutation after construction.

## Decision

Replace generated `RW` classes with generated `Builder` classes. A root Builder extends `KlumBuilder`; derived Builders mirror the DSL Object inheritance hierarchy. Builders hold all materializable DSL fields plus construction-only `FieldType.BUILDER` state. They are the sole receiver of configuration, mutators, and lifecycle callbacks through `POST_TREE`.

Source field initializers execute only when a Builder is created; materialization copies their resulting values and does not re-evaluate initializer code.

Add `INSTANTIATE` at phase 40, after `POST_TREE` (30) and before `VALIDATE` (50). Materialization allocates completed DSL Objects for the composition graph, records a one-way Builder-to-model reference, then assigns relationship fields internally. This supports cyclic graphs without retaining Builders from completed objects. `@Validate` and subsequent phases operate on completed DSL Objects.

Plugin traversal is state-typed: `BuilderVisitingPhaseAction` is used before materialization and `ModelVisitingPhaseAction` afterward, rather than a visitor whose target type changes implicitly with phase number.

Completed DSL Objects are structurally immutable: they expose no `apply` method or public mutation path; Collections are read-only; non-transient, non-relationship fields are final. Direct DSL Object references and Collections of DSL Objects may be assigned internally during materialization. `FieldType.TRANSIENT` remains mutable, and arbitrary Simple Values are not deep-copied. Generated constructors are internal-only, used by materialization and controlled deserialization.

Materialization publishes independent unmodifiable snapshots for the supported `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, and `SortedMap`/`NavigableMap` interfaces, preserving order and comparators where applicable. `EnumSet` is retained defensively and exposed as a copy. Schema compilation rejects concrete or custom collection declarations that cannot be snapshot safely, with guidance to use a supported interface.

Create separate deep modules for the two lifecycle states: `KlumBuilder` owns construction-time state and behavior, while `KlumModelProxy` owns completed-object breadcrumbs, model paths, validation state, and per-`InstanceValidator` execution markers. `KlumInstanceProxy` becomes a narrow Builder-only compatibility adapter; asking it for a completed DSL Object fails with migration guidance.

Builders retain provisional validation issues raised by `EARLY_VALIDATE` and lifecycle callbacks; materialization transfers them to the Model companion before completed-object validators run.

The Model companion is serializable with the completed DSL Object, preserving breadcrumbs and validation results. Builder-only construction state is excluded.

Composition is built in one Builder lifecycle. A completed DSL Object may be used only as an aggregation `LINK` target and is never re-owned, mutated, or rehydrated. Templates are the intentional exception: they remain client-facing DSL Object recipes but are copied into fresh Builders when applied. Builder traversal is internal and skips sealed Builders; completed-object traversal is the client-visible/read-only traversal.

Builder relationship fields uniformly hold Builders. An externally completed DSL Object is represented by an immediately sealed Builder with a one-way reference to that object; pre-materialization lifecycle code therefore works with Builder state, not arbitrary completed-model methods.

Jackson deserialization restores all serializable fields into Builders, then runs normal lifecycle, materialization, and validation. This provisional choice may alter results if mutating lifecycle callbacks are non-idempotent; revisit it in #428. Potential diagnostics for mutable Simple Values are tracked in #427. Phase guards are outside this ADR and remain tracked by #281.

## Consequences

This is a documented breaking change. Completed DSL Objects no longer expose generated `apply`, direct construction is unsupported for clients, and direct completed-model access through `KlumInstanceProxy` is rejected. Existing factory-oriented client code remains the supported path. The change aligns with the removal tracked by #323 and the broader generated-base-helper cleanup in #331, while enabling lifecycle-class work in #420.

The generated Builder's final name and whether generated types are nested or top-level remain intentionally undecided pending #394. That issue must be resolved or rejected before this change is released.
