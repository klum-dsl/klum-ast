Model Phases
============

Model creation goes through several phases. These phases are local to the current Thread and thus, thus all submodels share the same phase even if created by distinct calls to `create` methods.

# Lifecycle annotations

Many lifecycle phases have a designated annotation. Methods and/or fields annotated with these annotations are handled in the
corresponding phase. Lifecycle annotations are annotations marked with the meta annotation `@WriteAccess(LIFECYCLE)`. Those 
methods must be parameterless and not be private. Their visibility will be downgraded to `protected` and they will be moved to
the RW class.

In addition to methods, fields of type `Closure` can also be annotated with lifecycle annotations (including `@Owner`). These closures will be executed
in their respective phases, with the RW object as delegate (i.e., will behave as like `apply` closures).

The lifecycle annotations `@PostCreate` and `@PostApply` are special cases. These are not run as separate phases, but
instead are part of the creation phase, and run for each object separately. 

Note that lifecycle methods and closures are called unconditionally, regardless of the state of the object (for example,
a `@Default` field will only be handled if the field is not set yet, while a `@Default` method or Closure will always be called). Thus those methods need to check for themselves if they should do anything.

# Creation

The creation phase is started by the first call to any creation method in a thread. It consists of the actual instantiation of the model root as well as calling its apply methods. Creation of an object includes calling of `@PostCreate` and `@PostApply` methods (and closures) on that object.

Before the initial create methods return, control is passed to the PhaseDriver that is responsible to execute all
later phases.

## PhaseActions

PhaseActions are the main execution point for phases. Usually, PhaseActions retrieve the root object from the PhaseDriver and 
use it iterate through the model tree.

PhaseActions are usually registered by using the Java ServiceLoader mechanism, so plugins can extend the functionality.

Each phase has an ordinal defining the execution order of those phases. Main phases are defined in the `DefaultKlumPhase` enum, but 
there ordinals are spaced to allow for plugins to insert phases in between.

# Phase Details

## ApplyLater (1)
The ApplyLater phase is the first phase after the initial creation of the model. It executes all closures registered using the `applyLater` method without a phase argument.

## Early Validation (5)

The early validation phase is used to validate everything model supplied (as opposed to auto created, which are supplied by the schema),
i.e., everything provided by a user-provided script or code. This includes checks for deprecated fields or explicit notifications using the `@Notify` annotation.

## AutoCreate (10)

The AutoCreate phase will create objects that are marked with `@AutoCreate` and have not been created yet. It also runs
any lifecycle methods and Closures that are marked with `@AutoCreate`.

## Owner (15)

The Owner phase is a special variant of the AutoLink phase in that it links objects together, in that case fields
annotated with the `@Owner` annotation. This is done before the AutoLink phase since AutoLink makes usually makes
heavy use of the owner field.

Also resolves `@Role` fields and methods, which are technically special case `@Owner` elements.

## AutoLink (20)

The AutoLink phase is bound to set field with references to existing objects somewhere in the model tree. This is done
by annotating fields with `@LinkTo`. Also, regular lifecycle methods and Closure fields can be annotated with `@AutoLink` to be executed.

## Default (25)

The Default phase is used to set default values for non-DSL fields. See [Default Values](Default-Values.md) for details. As will all lifecycle annotations, methods and Closure fields annotated with `@Default` will also be executed during this phase.

## PostTree (30)

The PostTree phase allows executing actions on a completely realized model tree. This can be used
to create interlinking between objects that are too complex for AutoLink/AutoCreate.

## Validation (50-60)

Validates the correctness of the model according to the presence of the `@Validate` annotation. See [Validation](Validation.md) for details. The validation phase should (must) not change the model anymore. The validation phase as well as custom validation phases provided by plugins only collect validation problems but do not throw exceptions. This is handled by the Verify phase.

## Verify (80)

Verifies that previous phases have raised no validation problems of the fail level or higher (ERROR by default). Throws an exception otherwise.

## Completion (100)

Deletes registered template objects.

Plugins can register actions to be executed after the model has been created and validated.
This could, for example, be used for logging purpose or to register the model in some kind of external registry.

Note that the lifecycle methods for AutoCreate, AutoLink and PostTree are technically identical, the difference being
more of a semantic nature. So AutoCreate methods should actually create objects, AutoLink methods should link existing objects.

# Error Handling

If an exception is thrown in any Phase, the exception is wrapped in a `KlumException` or one of its subclasses (like `KlumVisitorException`). This exception contains the relevant phase as well as potentially the path to the object that caused the exception.

# applyLater methods

RW classes also provide `applyLater` methods that can be used to register actions to be executed in arbitrary phases. If no phase is specified,
the action is executed in the `ApplyLater` phase. 

ApplyLater closures on templates are not executed but are copied along with the other template values to the created object. 
This is especially useful for test cases, where the model needs specific, non-trivial values to be set (e.g., for validation), but these values are irrelevant for the actual test.

```groovy
class PersonText extends Specification {

    def template = Person.Create.Template {
        applyLater {
            // this closure will be executed for all objects created from this template
            street "Main Street " + name
            city "City of " + name
        }
    }

    def "a testcase with person objects"() {
        given:
        def person = Person.Template.With(template) {
            PersonText.Create.With(name: "Hans")
        }

        when:
        def result = service.doSomething(person)

        then:
        //... check something
    }
}
```

# Methods that modify datastructures

In some (usually migration related) cases, a lifecycle methods trying to modify the list or map containing itself. This would
lead to a concurrent modification exception. To avoid this, the code of the lifecycle method can be wrapped in an applyLater closure,
causing it to run directly after the current phase has finished.

```groovy
@AutoLink moveLegacySiblings() {
    if (this.hasLegacySiblings()) {
        // this closure will be executed after the current phase has finished
        applyLater {
            this.legacySiblings.each {
                parent.removeChild(it)
                this.addLegacy(it)
            }
        }
    }
}
```

Note that this example could have been better realized in a parent's autolink method, removing the need for the applyLater closure. 