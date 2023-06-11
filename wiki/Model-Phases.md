Model Phases
============

Model creation goes through several phases. These phases are local to the current Thread and thus, thus all submodels share the same phase even if created by distinct calls to `create` methods.

# Lifecycle annotations

Many lifecycle phases have a designated annotation. Methods and/or fields annotated withe these annotations are handled in the
corresponding phase. Lifecycle annotations are annotations marked with the meta annotation `@WriteAccess(LIFECYCLE)`. Those 
methods must be parameterless and not be private. Their visibility will be downgraded to `protected` and they will be moved to
the RW class.

The lifecycle annotations `@PostCreate` and `@PostApply` are a special case. These are not run as separate phases, but
instead are part of the creation phase, and run for each object separately. 

# Creation

The creation phase is started by the first call to any creation method in a thread. It consists of the actual instantiation of the model root as well as calling its apply methods. Creation of an object includes calling of postCreate and postApply methods of that object, as well as any `@Owner` fields or methods. (Note that `@Owner` handling might be promoted to a separate phase in the future)

Before the initial create methods return, control is passed to the PhaseDriver that is responsible to execute all
subsequent phases.

## PhaseActions

PhaseActions are the main execution point for phases. Usually, PhaseActions retrieve the root object from the PhaseDriver and 
use it iterate through the model tree.

PhaseActions are usually registered by using the Java ServiceLoader mechanism, so plugins can extend the functionality.

Each phase has an ordinal defining the execution order of those phases. Main phases are defined in the `KlumPhase` enum, but 
there ordinals are spaced to allow for plugins to insert phases in between.

# Phase Details

## AutoCreate (10)

The AutoCreate phase will create objects that are marked with `@AutoCreate` and have not been created yet. It also runs
any lifecycle methods that are marked with `@AutoCreate`.

## AutoLink (20)

Not yet implemented. The AutoLink phase is bound to set field with references to existing objects somewhere in the model tree.

## PostTree (30)

The PostTree phase allows to execute actions on a completely realized model tree. This can be used
to create interlinks between objects that are too complex for AutoLink/AutoCreate.

## Validation (50)

Validates the correctness of the model according to the presence of the `@Validate` annotation. See [Validation](Validation.md) for details. The validation phase should (must) not change the model anymore.

## Completion (100)

Has no default actions. Plugins can register actions to be executed after the model has been created and validated.
This could, for example, be used for logging purpose or to register the model in some kind of external registry.