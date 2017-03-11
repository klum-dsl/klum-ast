package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Activates validation for the given field or marks the annotated method as validation method.
 * <h1>On a field</h1>
 * If this annotation is set on a field, this field is validated as part of the object validation, either after the
 * {@code apply()} method or during manual validation, as determined by the {@link Validation} annotation. The actual
 * validation can be one of the following:
 * <table summary='Valid options to use Validate annotation on a field'>
 *     <tr><td>empty</td><td>Validates the content of the field according to groovy truth</td></tr>
 *     <tr><td>{@link Validate.Ignore}</td><td>Don't validate this field. This can be used if {@link Validation#option()}
 *     is set to {@link Validation.Option#VALIDATE_UNMARKED}</td></tr>
 *     <tr><td>a closure</td><td>The given closure is evaluated called with the field value as parameter. If the result
 *     of the call satisfies Groovy Truth, the field is assumed valid.</td></tr>
 * </table>
 *
 * <pre><code>
 * &#064;DSL
 * class Foo {
 *   &#064;Validate
 *   String notEmpty
 *   &#064;Validate({ {@literal it.length > 3} })
 *   String minLength
 * }
 * </code></pre>
 *
 * {@link #message()} can be used to provide a custom message for failed validations.
 *
 * <h1>On a method</h1>
 * On a method, this annotation is used to designate a validation method which is called as part of the validation process.
 * If the method returns without throwing an exception / an {@link AssertionError}, the method is considered to be passed.
 * <p>Validation methods are commonly used for interdependent fields (field a must have a value matching field b) or
 * to perform a validation that would be to long to comfortably include in a closure</p>
 *
 * <pre><code>
 * given:
 * &#064;DSL
 * class Foo {
 *   String value1
 *   String value2
 *
 *   &#064;Validate
 *   private def stringLength() {
 *     {@literal assert value1.length() < value2.length()}
 *   }
 * }
 *
 * when:
 * clazz.create {
 *   value1 "abc"
 *   value2 "bl"
 * }
 *
 * then:
 * thrown(IllegalStateException)
 * </code></pre>
 * <p>Validation methods should not change the state of an object, use {@link PostApply} or {@link PostCreate} for that.</p>
 * <p>When using validation on a method, neither a {@link #message()} nor a {@link #value()} must be given.</p>
 *
 * <h1>Order of validation</h1>
 * When validating an object, the following order is executed.
 * <ul>
 *     <li>Validation of the superclass, if the superclass is also a model class</li>
 *     <li>validation of all fields</li>
 *     <li>custom validation methods</li>
 * </ul>
 *
 * <p>if the validation fails for any validation field or method, an {@link IllegalStateException} is thrown.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Validate {

    /**
     * A closure to be executed to validate the annotated field. If empty, Groovy Truth is used to validate the field.
     * Illegal when annotating a method.
     */
    Class value() default GroovyTruth.class;

    /**
     * A message to be returned when validation fails.
     * Illegal when annotating a method.
     */
    String message() default "";

    /**
     * Default value for {@link Validate#value()}. Designates the field to be validated against Groovy Truth.
     */
    interface GroovyTruth {}

    /**
     * If used as value for {@link Validate#value()}, configures validation to ignore this field. Makes only sense
     * in combination with {@link Validation.Option#VALIDATE_UNMARKED}.
     */
    interface Ignore {}
}
