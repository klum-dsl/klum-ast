Resulting objects can be automatically be validated. This is controlled via two annotations `@Validate` and `@Validation`.

# `@Validation`
`@Validation` controls validation on Class level. Using the `option` value, the handling of unmarked fields can
be configured. With `IGNORE_UNMARKED`, the default setting, only those fields are validated that have been marked
with the `@Validate` annotation. With `VALIDATE_UNMARKED`, all non-annotated fields are validated against Groovy truth
 (i.e. numbers must be non-zero, collections and Strings non-empty and other objects not null).
 
 `mode` controls _when_ validation should happen. The default is `AUTOMATIC` which automatically validates objects at the 
 end of the `create()` method. By setting this to `MANUAL` validation must be manually initiated using the `validate()`
 method. This can be used to defer the validation if the final objects is to be assembled in multiple steps.
 
 The same effect as `Option.MANUAL` can be achieved for *single instances* by using the `manualValidation(boolean)` method.
   
# `@Validate`
The `@Validate` annotation controls validation of a single field. If the annotation is not present, the `@Validation.mode` 
 controls whether this field will be evaluated. If present, the `value` field contains the actual validation criteria. 
 This can be one of the following:
 
 * `Validate.GroovyTruth` (default), to validate this field against Groovy Truth
 * `Validate.Ignore` excludes this field from validation, this can be necessary when the validation option is set
  to `VALIDATE_UNMARKED`
 * A closure that takes a single argument, the value of the field. The result of this closure is evaluated according
 to Groovy Truth
```groovy
@DSL
class Figure {
 @Validate({ it > 2})
 int edges
}
```
  
 The annotation can also contain an additional `message` value further describing the constraint, this is included in
 the error message.

Any exception or `AssertionError` thrown during validation is wrapped in an `IllegalArgumentException`. This allows
 the convenient use of Groovy's Power Assertion.

# Validation of inner objects
Since inner objects are not created via a `create` call, their validation is not immediately called. Rather, inner objects are
validated as part of their owner validation.

This means that inner objects can make use of the complete model tree (provided they have an owner field.

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

@DSL Helper {
    @Owner Component component
    Pattern validForStages

    @Validate
    void patternMustMatchAtLeastOneStage {
        assert component.allStages.keySet().any { it ==~ validForStages }
    }
}
```

Thanks to deferred validation, it is irrelevant whether the stages are set before or after the helpers.

