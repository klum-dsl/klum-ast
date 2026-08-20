# Validation

Completed DSL Objects can be validated automatically. Builder phases may record provisional issues, which are transferred
to the completed Model companion during [`INSTANTIATE`](Model-Phases.md#instantiate-40); `@Validate` methods and external
`InstanceValidator`s then run on the completed object. Each `InstanceValidator` type is memoized once per completed model.
For reading stored results from a completed model, see [Completed Object Support](Completed-Object-Support.md).

## On Classes
`@Validate` on classes behaves exactly like `@Validate` on fields, but is applied to all fields of the class not yet having an annotation, i.e., all not explicitly marked fields are validated
against Groovy truth (i.e., numbers must be non-zero, collections and Strings non-empty, and other objects not null).

(See: `ValidationDocumentaryTest#'validates unannotated fields when a class is marked for validation'`.)

```groovy
@DSL
@Validate
class Release {
    String name

    @Optional
    String notes
}
```

`Release.Create.One()` reports that `name` must be set, while `Release.Create.With { name "spring-catalog" }`
accepts the omitted optional `notes` field.
   
## On Fields
The `@Validate` annotation controls validation of a single field. If the annotation is not present, the presence on the class  
 controls whether this field will be evaluated. If present, the `value` field contains the actual validation criteria. 
 This can be one of the following:
 
 * `Validate.GroovyTruth` (default), to validate this field against Groovy Truth
 * `Validate.Ignore` excludes this field from validation, this only makes sense when the class itself is marked with `@Validate`
 * A closure that takes a single argument, the value of the field. This closure must either be a single expression that
   is evaluated against Groovy Truth or else an `assert` statement itself.

### Choose a validation form

Use `@Required` for a presence or Groovy-truth rule. For a single-field constraint that is one expression, use a field
`@Validate({ ... })` closure. Use an `@Validate` method when a rule relates fields, is reused, or has several steps; a
method remains the right form for those rules even when it happens to be short.

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate

@DSL
class Deployment {
    @Required
    String image

    @Validate({ it in 1..20 })
    int replicas

    int minimumReplicas

    @Validate
    void replicasMeetMinimum() {
        assert replicas >= minimumReplicas : "replicas must meet the configured minimum"
    }
}
```

(See: `ValidationDocumentaryTest#'validates a numeric field with a closure'`.)

```groovy
@DSL
class Figure {
    @Validate({ it > 2 })
    int edges
}
```

The annotation can also contain an additional `message` value further describing the constraint; this is included in
the error message. For a field rule with an explicit message, the validation result identifies the field and gives the
reader a direct next step:

(See: `ValidationDocumentaryTest#'reports an explicit validation message for a field'`.)

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

(See: `ValidationDocumentaryTest#'uses Required as the concise Groovy-truth field rule'`.)

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

Likewise, `@Optional` is a convenient alias for `@Validate(Validate.Ignore)`, to explicitly ignore a field from validation
when `Validate` is used on a class.

(See: `ValidationDocumentaryTest#'excludes an optional field from class-wide validation'`.)

```groovy
@DSL
@Validate
class Release {
    String name

    @Optional
    String notes
}
```

## On Methods

`@Validate` can also be used on methods. In this case, any method carrying the annotation is executed during the validation phase; private methods work as well, but `static` methods are forbidden at compile time. If it successfully returns, the validation is considered successful. If it throws an exception, the validation fails.

A failed `assert` becomes a validation issue at the method's configured `level` (`ERROR` by default). An explicit
assertion message is retained in the issue message. Without one, KlumAST retains Groovy's power-assertion message,
including the failed expression and its values.

(See: `ValidationDocumentaryTest#'reports failed validation-method assertions at their configured levels'`.)

```groovy
@DSL
class Release {
    int replicas

    @Validate(level = Validate.Level.WARNING)
    void replicasShouldBeConfigured() {
        assert replicas > 0 : 'Configure at least one replica'
    }

    @Validate
    void requiresTwoReplicas() {
        assert replicas >= 2
    }
}
```

For `Release.Create.With { replicas 0 }`, the first assertion produces a `WARNING` whose Groovy assertion message includes
`Configure at least one replica`. The default-level assertion produces an `ERROR` whose message starts with Groovy's power
assertion:

```text
assert replicas >= 2
       |        |
       0        false
```

## Custom Issues

Instead of throwing an exception, report a custom diagnostic through `KlumSchemaSupport`. Its current-object reporter is
available in every framework-managed lifecycle callback and closure, including early Builder phases and completed-model
validation. It preserves the callback's target and member; it does not create a lifecycle or change validation timing.

For Java helpers, obtain the reporter through the gateway:

```java
KlumSchemaSupport.klumValidationForObject(child)
    .error("child needs a release name");
```

In Groovy, statically import the `klumValidation` property for the current lifecycle target:

```groovy
import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

@DSL
class Release {
    List<String> values

    @Validate
    void validateValues() {
        if (values.size() < 2)
            klumValidation.error("Need at least two values")
        values.each {
            if (it.size() < 3)
                klumValidation.errorAt("values", "$it: Need at least three characters")
        }
    }
}
```

Groovy also supports an alias for this static property import when a shorter local name reads better:

```groovy
import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation as report

@Validate
void validateRelease() {
    report.error("Mööp")
}
```

(See: `KlumValidationReporterTest#'supports an alias for the static Groovy reporter property import'`.)

The reporter operations are `error`/`errorAt`, `issue`/`issueAt`, `suppressOn`/`suppressAll`, and `getFailLevel`.
The `*At` operations name a member directly. Current-object operations use the lifecycle member when no member is given;
explicit-target operations use `<none>` unless an `*At` operation supplies a member. Suppression affects only later
issues, as before.

Use `KlumSchemaSupport.klumValidationForObject(target)` when a callback needs to report a diagnostic on another object
in the same construction graph. That object's own construction path is retained in the diagnostic. The reporter can be
called by helpers reached from a lifecycle callback, but it is not a general out-of-lifecycle validation API; issue
[#406](https://github.com/klum-dsl/klum-ast/issues/406) separately owns compile-time placement enforcement.

(See: `KlumValidationReporterTest#'reports a current validation-method issue through the static Groovy property'`.)

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
import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

@DSL
class Server {
    String host
    int port

    @Validate
    class ConnectivityChecks {
        void hostMustBeSet() {
            if (!host) klumValidation.error("host must be set")
        }

        void portMustBeInRange() {
            if (!(port in 1..65535)) klumValidation.error("port must be between 1 and 65535")
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

Validation classes use the same `klumValidation` reporter as validation methods. They no longer extend `ValidatorBase`:
that preliminary base type and `Validator` are not part of the 4.0 API and have no compatibility bridge. Replace their
shortcut methods with the reporter operations shown above.

## Validation of Nested Objects
Validation is done in a separate [phase](Model-Phases.md) after all child objects are created and other relevant
phases are run (postApply, postCreate, and future phases like auto link or auto create). I.e., validation for
the complete model tree runs immediately before the initial create method returns.

This means that nested objects can make use of the complete model tree (provided they have an owner field).

(See: `ValidationDocumentaryTest#'validates a child against parent state configured after the child'`.)

```groovy
@DSL class ReleasePlan {
    String releaseName
    ReleaseCheck check
}

@DSL class ReleaseCheck {
    @Owner ReleasePlan releasePlan

    @Validate
    void releaseNameWasConfigured() {
        assert releasePlan.releaseName == '2026.2'
    }
}

def releasePlan = ReleasePlan.Create.With {
    check {}
    releaseName '2026.2'
}
```

The child check succeeds although `releaseName` is configured after `check {}`: validation waits until the complete
owned tree is materialized.

Validation failures do not stop at the first error, rather all errors are collected and thrown at once, wrapped in a `KlumValidationException`. That exception contains a `List<KlumValidationResult>`, each of which contains the `KlumValidationIssue`s for a single object.

## Validation Levels

There are different levels for validation problems: INFO, WARNING, DEPRECATION, and ERROR.

Usually, validation problems are considered errors, but you can use the `level` parameter of the `@Validate` (or `@Required`) annotation to change this. 

In the normal case, only errors lead to a `KlumValidationException` being thrown, but all validation problems are collected
in `KlumValidationResult` objects. Use `KlumObjectSupport.of(object).getValidation().getResult()` for one object or
`getSubtreeResults()` for that object and its owned subtree.

(See: `ValidationPolicyDocumentaryTest#'records a warning-level validation result without failing construction'`.)

```groovy
@DSL
class Release {
    @Required(level = Validate.Level.WARNING)
    String releaseNotes
}
```

`Release.Create.One()` succeeds and retains a `WARNING` issue for `releaseNotes` in its stored validation result.

The level on which the validation causes an exception can be overridden by the `klum.validation.failOnLevel` system property.

## Deprecations

If a field is marked as deprecated, it is automatically validated against Groovy False, i.e., if the value is not null or empty, a validation problem of level DEPRECATION is reported.

This happens in the early validation phase and therefore considers only values supplied by the Model Writer's initial
configuration. The issue is not raised if a later phase, such as Default, AutoCreate, or AutoLink, sets the field.

The warning message for a deprecated field is taken from the `@deprecated` javadoc annotation, if present.

If a `@Notify` annotation is present alongside the `@Deprecated` annotation, the `@Notify` is used to determine the warning behavior.

(See: `ValidationPolicyDocumentaryTest#'reports a documented deprecation only for a manually configured legacy field'`.)

```groovy
@DSL
class Release {
    /**
     * @deprecated Use releaseChannel instead.
     */
    @Deprecated
    String legacyChannel
}
```

When a Model Writer sets `legacyChannel`, the stored result records a `DEPRECATION` issue with the documented replacement.

## `@Notify`

The `@Notify` annotation can be placed on any field to raise an issue if the field is set or unset after the apply phase. This is especially useful in combination with `@Default` and layer3 annotations `@AutoCreate` and `@LinkTo`.

(See: `ValidationPolicyDocumentaryTest#'reports a missing manually configured field'`.)

```groovy
@DSL
class MyModel {
    @AutoCreate
    @Notify(ifUnset = "Value will be autocreated, which might lead to unexpected behavior")
    String shouldBeSetManually
}
@DSL
class AnotherModel {
    @LinkTo
    @Notify(ifSet = "This value will usually be linked automatically, and should only be set manually if you know what you are doing", level = Validate.Level.INFO)
    String autoLinked
}
```

As with most issue-related annotations, the issue level can be set via the `level` parameter. The default is WARNING.

`@Notify` can deliberately replace the default `@Deprecated` behavior for the same field:

(See: `ValidationPolicyDocumentaryTest#'uses Notify to replace the default deprecated-field policy'`.)

```groovy
@DSL
class Release {
    @Notify(ifSet = "Use releaseChannel instead.", level = Validate.Level.INFO)
    @Deprecated
    String legacyChannel
}
```

When a Model Writer sets `legacyChannel`, the stored result contains the `INFO` issue from `@Notify`, rather than a
`DEPRECATION` issue.

## Suppress Further Issues

`klumValidation.suppressOn(member)` suppresses later issues for a member on the current lifecycle target. Use
`KlumSchemaSupport.klumValidationForObject(target).suppressOn(member)` for an explicit target. By default, issues up to
DEPRECATION are suppressed—everything except ERROR. Provide a level argument to change that threshold.

`suppressAll()` applies the same rule to every member; it replaces the former `Validator.ANY_MEMBER` convention.

Suppression has no effect on already reported issues. For example, this Model suppresses the later warning for `notes`
but preserves the required `owner` error:

```groovy
import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

@DSL
class Release {
    @Required(level = Validate.Level.WARNING)
    String notes

    @Required
    String owner

    @PostTree
    void suppressNotesWarning() {
        klumValidation.suppressOn("notes")
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

(See: `ValidationDocumentaryTest#'verifies stored results without rerunning validators'`.)

```groovy
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException

System.setProperty('klum.validation.skipVerify', 'true')

def model = Release.Create.One()
def validation = KlumObjectSupport.of(model).validation
assert validation.result.issues.size() == 1

try {
    validation.verify()
    assert false: 'the stored error should fail verification'
} catch (KlumValidationException ignored) {
    // verify() inspected the stored result; it did not rerun validation methods
}
```

## JSR380 Validation

The optional `klum-ast-bean-validation` module adds Jakarta Bean Validation support. It exposes the Jakarta Validation API and includes Hibernate Validator as its implementation.

With the module on the classpath, Jakarta constraint annotations are evaluated during the validation phase in addition to standard Klum validation.

(See: `BeanValidationDocumentaryTest#'accepts a release with a satisfied Jakarta validation constraint'`.)

```groovy
import jakarta.validation.constraints.Size

@DSL class Release {
    @Size(min = 2, max = 4, message = "Choose between two and four approvers")
    List<String> approvers
}
```

### Validation Levels and JSR380

Levels are provided using the `jakarta.validation.Payload` interface. The class `com.blackbuild.klum.ast.validation.bean.Level` provides inner classes for each value of `Validate.Level`. When set on a constraint, a violation is recorded at that level.

(See: `BeanValidationDocumentaryTest#'records a Jakarta constraint violation at its payload-selected level'`.)

```groovy
import jakarta.validation.constraints.Size
import com.blackbuild.klum.ast.validation.bean.Level

@DSL class Release {
    @Size(min = 2, payload = Level.WARNING, message = "Choose at least two approvers")
    List<String> approvers
}
```

The resulting issue names the constrained member and retains the constraint message.

### Using the Gradle Plugin

When using the [gradle plugin](Gradle-Plugins.md), the dependency version can be omitted:

(See: `BeanValidationGradleDocumentaryTest#'schema plugin aligns an optional Bean Validation module to its BOM'`.)

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema'
}

dependencies {
    api 'com.blackbuild.klum.ast:klum-ast-bean-validation'
}
```

The schema plugin imports the matching `klum-ast-bom`, which supplies the optional module version.
