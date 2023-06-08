Model Phases
============

Model creation goes through several phases. These phases are local to the current Thread and thus, thus all submodels share the same phase even if created by distinct calls to `create` methods.

# Creation

The creation phase is started by the first call to any creation method in a thread. It consists of the actual instantiation of the model root as well as calling its apply methods. Creation of an object includes calling of postCreate and postApply methods of that object, as well as any owner fields or methods.

Before the initial create methods return, control is passed to the PhaseDriver that is responsible to execute all
subsequent phases.

## PhaseActions

PhaseActions are the main execution point for phases. Usually, PhaseActions retrieve the root object from the PhaseDriver and 
use it iterate trough the model tree.

PhaseActions are usually registered by using the Java ServiceLoader mechanism, so plugins can extends the functionality.

Each phase has an ordinal defining the execution order of those phases. Main phases are defined in the `KlumPhase` enum, but 
there ordinals are spaced to allow for plugins to insert phases in between.

# Phase Details

## AutoCreate (10)

Not yet implemented. The AutoCreate phase will create objects that are marked with `@AutoCreate` and have not been created yet.

## AutoLink (20)

Not yet implemented. The AutoLink phase is bound to set field with references to existing objects somewhere in the model tree.

## PostTree (30)

The PostTree phase allows to execute actions on a completely realized model tree. This can be used
to create interlinks between objects that are too complex for AutoLink/AutoCreate.

## Validation (50)

Validates the correctness of the model according to the presence of the `@Validate` annotation. See [Validation](Validation.md) for details.

## Completion (100)

Has no default actions. Plugins can register actions to be executed after the model has been created and validated.
This could, for example, be used for logging purpose or to register the model in some kind of external registry.