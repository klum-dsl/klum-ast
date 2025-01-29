Resulting objects can be automatically be validated. This is controlled via the `@Validate` annotation.

# On classes
`@Validate` on classes behaves exactly like `@Validate` on fields, but is applied to all fields of the class not yet having an annotation, i.e. all not explicitly marked fields are validated
against Groovy truth
 (i.e. numbers must be non-zero, collections and Strings non-empty and other objects not null).
   
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

For validation closures, take care to account for `null` values, for example by using the groovy
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

If validation fails, an `KlumVisitorException` is thrown, any other encountered exception during validation is also wrapped in an
`KlumVisitorException`. Unfortunately, Groovy's Power Assertion are currently not used in the output.

# `@Required`

`@Required` is a convenient alias for `@Validate` with an empty value (i.e. default validation), also with an optional message.

```groovy
@DSL
class MyModel {

 @Required
 Person administrator

 @Required("We really need another person (4-eyes principle)")
 Person person
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
}
```

# On methods

`@Validate` can also be used on parameterless methods. In this case, the method is executed during the validation phase. If it sucessfully returns, the validation is considered successful. If it throws an exception, the validation fails.

# Validation of inner objects
Validation is done in a separate [phase](Model-Phases.md) after all child objects are created and other relevant
phases are run (postApply, postCreate, and future phases like auto link or auto create). I.e. validation for
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

# Manual validation

Instances can be exempt from running automatically by using the `manualValidation(boolean)` method.

NOTE: this is considered deprecated and will be removed in a future version.

