# Validation

Completed DSL Objects can be validated automatically. Builder phases may record provisional issues, which are transferred
to the completed Model companion during [[Model Phases#instantiate-40|`INSTANTIATE`]]; `@Validate` methods and external
`InstanceValidator`s then run on the completed object. Each `InstanceValidator` type is memoized once per completed model.
For reading stored results from a completed model, see [[Completed Object Support]].

## On Classes
`@Validate` on classes behaves exactly like `@Validate` on fields, but is applied to all fields of the class not yet having an annotation, i.e., all not explicitly marked fields are validated
against Groovy truth
 (i.e., numbers must be non-zero, collections and Strings non-empty, and other objects not null).
   
## On Fields
The `@Validate` annotation controls validation of a single field. If the annotation is not present, the presence on the class  
 controls whether this field will be evaluated. If present, the `value` field contains the actual validation criteria. 
 This can be one of the following:
 
 * `Validate.GroovyTruth` (default), to validate this field against Groovy Truth
 * `Validate.Ignore` excludes this field from validation, this only makes sense when the class itself is marked with `@Validate`
 * A closure that takes a single argument, the value of the field. This closure must either be a single expression that
   is evaluated against Groovy Truth or else an `assert` statement itself.

```groovy
@DSL
class Figure {
 @Validate({ it > 2})
 int edges

 @Validate({ assert defining <= edges })
 int defining
}
```

The annotation can also contain an additional `message` value further describing the constraint; this is included in
the error message. For a field rule with an explicit message, the validation result identifies the field and gives the
reader a direct next step:

```groovy
@DSL
class Release {
 @Validate(message = "A release name is required")
 String name
}
```

`Release.Create.One()` reports:

```text
<root>($/Release.One):
- ERROR #name: A release name is required
```

 For validation closures, it is advisable to use the message feature of the `assert` keyword instead:

```groovy
@DSL
class Figure {
 @Validate({ assert it > 2 : "need more than 2 edges, but got only $it"})
 int edges
}
```

For validation closures, take care to account for `null` values, for example, by using the groovy
safe operator or short circuit operators:

```groovy
@DSL
class MyModel {

 @Validate({ it?.job == "manager"})
 Person administrator

 @Validate({ it && it.age < 65 })
 Person person

 @Validate({ !it || it.age < 65 })
 Person optionalPerson
}
```

Any failed validation is represented by a `KlumValidationIssue`, all
issues of a single object are collected in a `KlumValidationResult`. The result is stored in the completed object's Model
companion and is accessed through `KlumObjectSupport.of(object).getValidation().getResult()` rather than through a proxy.

## `@Required` and `@Optional`

`@Required` is a convenient alias for `@Validate` with an empty value (i.e., default validation), also with an optional message and level.

```groovy
@DSL
class MyModel {

    @Required
    Person administrator

    @Required("We really need another person (4-eyes principle)")
    Person person

    /**
     * @deprecated Use person instead
     */
    @Required(level = Validate.Level.DEPRECATION)
    Person manager
}
```

Is the same as

```groovy
@DSL
class MyModel {

 @Validate
 Person administrator

 @Validate(message="We really need another person (4-eyes principle)")
 Person person

 /**
  * @deprecated Use person instead
  */
 @Validate(level = Validate.Level.DEPRECATION)
 Person manager
}
```

Likewise, `@Optional` is a convenient alias for `@Validate(Validate.Ignore)`, to explicitly ignore a from validation when
`Validate` is used on a class.

## On Methods

`@Validate` can also be used on methods. In this case, any method carrying the annotation is executed during the validation phase; private methods work as well, but `static` methods are forbidden at compile time. If it successfully returns, the validation is considered successful. If it throws an exception, the validation fails.

## Custom Issues

Instead of throwing an exception, the method can also use static methods of the `Validator` class to report issues. There are several methods:

* `Validator.addError(String)`
* `Validator.addErrorToMember(String, String)`
* `Validator.addIssue(String, Validate.Level)`
* `Validator.addIssueToMember(String, String, Validate.Level)`

These add the issue to the object whose method lifecycle is currently being executed. The `*toMember` variants allow 
specifying the field name of the member that caused the issue, the other to use the lifecycle method's name.

```groovy
@DSL
class AnObject {
    
    List<String> values
    
    @Validate
    void validate() {
        if (values.size() < 2)
            Validator.addError("Need at least two values")
        values.each {
            if (it.size() < 3)
                Validator.addErrorToMember("values", "$it: Need at least three characters")
        }
    }    
    
}
```

Using these methods, it is possible to report multiple issues in the same method.

Additionally, there are methods to provide an explicit object to report the issue on, this allows for a upper level object to report issues on a lower level object.

* `Validator.addErrorTo(Object, String)`
* `Validator.addErrorTo(Object, String, String)`
* `Validator.addIssueTo(Object, String, String, Validate.Level)`

The member name is optional, if not provided, a generic `<none>` is used. Also note that there is no three argument version of `addIssueTo`, to prevent argument confusion.

## On Inner Classes (Validation Classes)

`@Validate` can also be placed on a public, non-static inner class, making it a **validation class**. A concrete
validation class may declare at most one no-argument constructor; omit it unless initialization is needed. Parameterized
constructors are not allowed. All public, non-static, parameterless methods in the class are considered validation
methods, like above. Groovy methods are public by default unless their visibility is explicitly restricted.

Validation classes keep validation logic out of the main model declaration and can cluster rules by topic. Their class name
is preserved in the emitted issue target, so `ConnectivityChecks.portMustBeInRange()` identifies both the topic and the
failing rule.

During actual validation, each public, non-abstract validation class is instantiated and its validation methods are
executed.

There can be an unlimited number of validation classes, and validation classes of parent model classes are also
instantiated during validation if they are not overridden by a child's validation class.

```groovy
import com.blackbuild.klum.ast.validation.ValidatorBase

@DSL
class Server {
    String host
    int port

    @Validate
    class ConnectivityChecks extends ValidatorBase {
        void hostMustBeSet() {
            if (!host) addError("host must be set")
        }

        void portMustBeInRange() {
            if (!(port in 1..65535)) addError("port must be between 1 and 65535")
        }
    }
}
```

For example, `Server.Create.One()` reports both failures as one validation result:

```text
<root>($/Server.One):
- ERROR #ConnectivityChecks.hostMustBeSet(): host must be set
- ERROR #ConnectivityChecks.portMustBeInRange(): port must be between 1 and 65535
```

Use distinct validation classes when a model has independent concerns. This keeps each group readable and makes the
emitting class visible in the validation output:

```groovy
@DSL
class Deployment {
    String image
    int replicas

    @Validate
    class ImageChecks {
        void imageMustBeSet() {
            assert image
        }
    }

    @Validate
    class CapacityChecks {
        void replicasMustBePositive() {
            assert replicas > 0
        }
    }
}
```

The inner class can also define a shared validation level for all methods, which can be overridden by the method level:

```groovy
@DSL
class Job {
    String owner
    String ticket

    @Validate(level = Validate.Level.WARNING)
    class Warnings {
        void ownerShouldBeSet() {
            assert owner
        }

        @Validate(level = Validate.Level.ERROR)
        void ticketShouldBeSet() {
            assert ticket
        }
    }
}
```

Validation inheritance also works with inner validation classes. In this case, the validation methods of the parent class are executed by the child validation class (unless overridden by the child class):

```groovy
@DSL
class BaseComponent {
    String id

    @Validate
    class Checks {
        void idMustBeSet() {
            assert id
        }
    }
}

@DSL
class WebComponent extends BaseComponent {
    String endpoint

    @Validate
    class Checks extends BaseComponent.Checks {
        void endpointMustBeSet() {
            assert endpoint
        }
    }
}
```
## Custom Issues on Inner Classes

For convenience, validation classes can inherit from `ValidatorBase`, which provides convenience methods to add issues for the outer instance. These map to `Validator` static methods. The methods available are:

* `addError(message)`
* `addErrorToMember(message, member)`
* `addIssue(message, level)`
* `addIssueToMember(message, member, level)`
* `addWarning(message)`
* `addWarningToMember(message, member)`
* `suppressFurtherIssuesOn(member)`
* `suppressAllFurtherIssues()`
* `suppressFurtherIssuesOn(member, level)`
* `suppressAllFurtherIssues(level)`

## Validation of Nested Objects
Validation is done in a separate [phase](Model-Phases.md) after all child objects are created and other relevant
phases are run (postApply, postCreate, and future phases like auto link or auto create). I.e., validation for
the complete model tree runs immediately before the initial create method returns.

This means that nested objects can make use of the complete model tree (provided they have an owner field).

```groovy
@DSL
class Component {
    @Owner project
    Map<String, Stage> stages
    List<Helper> helpers

    Map<String, Stage> getAllStages() {
        owner.stages + stages
    }
}

@DSL class Stage {
    @Key String name
}

@DSL class Helper {
    @Owner Component component
    Pattern validForStages

    @Validate
    void patternMustMatchAtLeastOneStage() {
        assert component.allStages.keySet().any { it ==~ validForStages }
    }
}
```

Thanks to deferred validation, it is irrelevant whether the stages are set before or after the helpers.

Validation failures do not stop at the first error, rather all errors are collected and thrown at once, wrapped in a `KlumValidationException`. That exception contains a `List<KlumValidationResult>`, each of which contains the `KlumValidationIssue`s for a single object.

## Validation Levels

There are different levels for validation problems: INFO, WARNING, DEPRECATION, and ERROR.

Usually, validation problems are considered errors, but you can use the `level` parameter of the `@Validate` (or `@Required`) annotation to change this. 

In the normal case, only errors lead to a `KlumValidationException` being thrown, but all validation problems are collected
in `KlumValidationResult` objects. Use `KlumObjectSupport.of(object).getValidation().getResult()` for one object or
`getSubtreeResults()` for that object and its owned subtree.

The level on which the validation causes an exception can be overridden by the `klum.validation.failOnLevel` system property.

## Deprecations

If a field is marked as deprecated, it is automatically validated against Groovy False, i.e., if the value is not null or empty, a validation problem of level DEPRECATION is reported.

This happens in the early validation phase and therefore considers only values supplied by the Model Writer's initial
configuration. The issue is not raised if a later phase, such as Default, AutoCreate, or AutoLink, sets the field.

The warning message for a deprecated field is taken from the `@deprecated` javadoc annotation, if present.

If a `@Notify` annotation is present alongside the `@Deprecated` annotation, the `@Notify` is used to determine the warning behavior.

## `@Notify`

The `@Notify` annotation can be placed on any field to raise an issue if the field is set or unset after the apply phase. This is especially useful in combination with `@Default` and layer3 annotations `@AutoCreate` and `@LinkTo`.

```groovy
@DSL
class MyModel {
    @AutoCreate
    @Notify(isUnset = "Value will be autocreated, which might lead to unexpected behavior")
    String shouldBeSetManually
}
@DSL
class AnotherModel {
    @LinkTo
    @Notify(isSet = "This value will usually be linked automatically, and should only be set manually if you know what you are doing", level = Validate.Level.INFO)
    String autoLinked
}
```

As with most issue-related annotations, the issue level can be set via the `level` parameter. The default is WARNING.

## Suppress Further Issues

`Validator.suppressFurtherIssues(Object, String)` and `Validator.suppressFurtherIssues(String)` suppress later issues on
a specific object or, in the one-argument form, on the current object. By default, issues up to DEPRECATION are
suppressed—everything except ERROR. Provide a different final level argument to change that threshold.

Also, using the `Validator.ANY_MEMBER` as member name, all further issues on the object are suppressed.

Suppression has no effect on already reported issues. For example, this Model suppresses the later warning for `notes`
but preserves the required `owner` error:

```groovy
@DSL
class Release {
    @Required(level = Validate.Level.WARNING)
    String notes

    @Required
    String owner

    @PostTree
    void suppressNotesWarning() {
        Validator.suppressFurtherIssues("notes")
    }
}
```

`Release.Create.One()` reports only:

```text
<root>($/Release.One):
- ERROR #owner: Field 'owner' must be set
```

The executable example is `ValidationSpec.groovy`, feature `suppresses a later non-error issue for one member`.

## Validation and Verify

The collection of validation problems and the actual throwing of the KlumValidationException is done in two separate phases.

The actual check against the fail level is done in the Verify phase. This allows for custom validations provided by plugins
(like the bean validation framework) to add their own checks.

## Skipping Verification

By setting the system property `klum.validation.skipVerify` to `true`, the verify phase is skipped. Validation is still
executed. Read the stored results through `KlumObjectSupport.of(object).getValidation().getSubtreeResults()`, or call `verify()`
later to apply the configured failure level without rerunning validators.

## JSR380 Validation

Using the optional module `klum-ast-bean-validation` it is possible to use the JSR380 validation framework. By default, this includes Hibernate-Validator 8 as dependency, this can be exchanged using default gradle mechanisms (with version 3, this will change to hibernate validator 9).

With the dependency in the classpath, JSR380 annotations are automatically evaluated during the validation phase in addition to standard klum validation.

```groovy
import jakarta.validation.constraints.Size

@DSL class Foo {
    @Size(min = 2, max = 4, message = "Must be between 2 and 4 values")
    List<String> values
}
```

### Validation Levels and JSR380

Levels are provided using the `jakarta.validation.Payload` interface. The class `com.blackbuild.klum.ast.validation.bean.Level` provides inner classes for each value of `Validate.Level`. When set on an annotation, an occuring violation will be set to that level:

```groovy
import jakarta.validation.constraints.Size
import com.blackbuild.klum.ast.validation.bean.Level

@DSL class Foo {
    @Size(min = 2, payload = Level.ERROR)
    List<String> values
}
```

Note that other payloads are ignored.

### Using the Gradle Plugin

When using the [gradle plugin](Gradle-Plugins.md), the dependency version can be omitted:

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema:2.2.0'
    id "maven-publish"
}

dependencies {
    // will be set to the version of the plugin (2.2.0)
    api 'com.blackbuild.klum:klum-ast-bean-validation'
}
```
