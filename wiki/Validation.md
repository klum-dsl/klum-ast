Resulting objects can be automatically be validated. This is controlled via the `@Validate` annotation.

# On classes
`@Validate` on classes behaves exactly like `@Validate` on fields, but is applied to all fields of the class not yet having an annotation, i.e. all not explicitly marked fields are validated
against Groovy truth
 (i.e. numbers must be non-zero, collections and Strings non-empty, and other objects not null).
   
# On fields
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

 The annotation can also contain an additional `message` value further describing the constraint, this is included in
 the error message.

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

Any failed validation is wrapped in a `KlumValidationProblem`, all 
problems of a single object are collected in a `KlumValidationResult`. The result of each object is stored in the KlumInstanceProxy where it can be accessed via `Validator.getValidationResult(Object)` method.

# `@Required`

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

# On methods

`@Validate` can also be used on parameterless methods. In this case, the method is executed during the validation phase. If it successfully returns, the validation is considered successful. If it throws an exception, the validation fails.

## Custom issues

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

# Validation of inner objects
Validation is done in a separate [phase](Model-Phases.md) after all child objects are created and other relevant
phases are run (postApply, postCreate, and future phases like auto link or auto create). I.e., validation for
the complete model tree runs immediately before the initial create method returns.

This means that inner objects can make use of the complete model tree (provided they have an owner field).

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

Validation failures do not stop at the first error, rather all errors are collected and thrown at once, wrapped in a `KlumValidationException`.

# Suppress Further issues

Using the new methods `Validator.suppressFurtherIssues(Object, String)` and `Validator.suppressFurtherIssues(String)` it is possible to suppress further issues on a specific object (or the current object, for the one argument version).

# Validation levels

There are different levels for validation problems: INFO, WARNING, DEPRECATION, and ERROR.

Usually, validation problems are considered errors, but you can use the `level` parameter of the `@Validate` (or `@Required`) annotation to change this. 

In the normal case, only errors lead to a `KlumValidationException` being thrown, but all validation problems are collected in the `KlumValidationResult` and can be accessed via the `Validator.getValidationResult(Object)`.

The level on which the validation causes an exception can be overridden by the `klum.validation.failOnLevel` system property.

# Deprecations

If a field is marked as deprecated but has no `@Validate` annotation, it is automatically validated against Groovy False, i.e., if the value is not null or empty, a validation problem of level DEPRECATION is reported.

This behavior can be overridden by explicitly setting a `@Validate` annotation on the field.

The warning message for a deprecated field is taken from the `@deprecated` javadoc annotation, if present.

# Validation and Verify

The collection of validation problems and the actual throwing of the KlumValidationException is done in two separate phases.

The actual check against the fail level is done in the Verify phase. This allows for custom validations provided by plugins
(like the bean validation framework) to add their own checks.

# Manual validation

Instances can be exempt from running automatically by using the `manualValidation(boolean)` method.

NOTE: this is considered deprecated and will be removed in a future version.

