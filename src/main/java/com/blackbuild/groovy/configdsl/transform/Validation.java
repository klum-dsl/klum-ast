package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Configures validation for a given model class. There are two configurable parameters:
 *
 * <ul>
 *     <li>{@link #option()} configures <b>which fields</b> are validated.</li>
 *     <li>{@link #mode()} configures <b>when</b> validation is performed</li>
 * </ul>
 *
 * <p>If this annotation is not present, the model behaves with default values {@code Validation(option = IGNORE_UNMARKED, mode = AUTOMATIC)},
 * in other words, it does not make sense to apply this annotation without any values.</p>
 *
 * <h1>option</h1>
 * <p>By default ({@link Option#IGNORE_UNMARKED}), only fields explicitly marked with {@link Validate} are validated,
 * unmarked fields are not.
 * By setting the option to {@link Option#VALIDATE_UNMARKED}, this behaviour is inverted. In that case,
 * all fields are validated except for the following:</p>
 * <ul>
 *     <li>fields explicitly marked with {@link Ignore}</li>
 *     <li>fields explicitly marked with {@code @Validate(IGNORE)}</li>
 *     <li>{@code transient} fields</li>
 *     <li>fields whose name starts with '$' (as these fields are ignored by KlumAST alltogether)</li>
 * </ul>
 * <p>Unmarked fields are validated agains Groovy Truth.</p>
 *
 *
 * <h1>mode</h1>
 * <p>The default behaviour ({@link Mode#AUTOMATIC}) for validation is to be executed directly
 * after the {@code apply()} method has executed. By setting {@link #mode()} to {@link Mode#MANUAL}, the validation
 * is never called automatically and needs to instead be called manually by calling the {@code validate()} method.</p>
 * <p>Note that it is also possible to configure a single instance of a model class to defer validation by calling
 * the {@code manualValidation()} method during the create/apply closure.</p>
 * @see Validate
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Validation {

    /**
     * Configures which fields are validated. When set to default {@link Option#IGNORE_UNMARKED}, only fields
     * explitcly marked with {@link Validate} are validated, when set to {@link Option#VALIDATE_UNMARKED}, all fields
     * not explicitly ignored (by {@link Ignore} or {@code Validate(IGNORE)} are validated.
     */
    Option option() default Option.IGNORE_UNMARKED;

    /**
     * Controls whether validation is automatically executed. When set to the default {@link Mode#AUTOMATIC}, validation
     * is performed directly after the apply method. When set to {@link Mode#MANUAL}, the {@code validate()} method
     * must be called manually.
     */
    Mode mode() default Mode.AUTOMATIC;

    /**
     * Validation mode, either automatically (call validation after apply), or manually.
     */
    enum Mode {
        /**
         * Valdation is performed automatically after the apply method has been executed.
         */
        AUTOMATIC,

        /**
         * Validation is not called automatically.
         */
        MANUAL
    }

    /**
     * Controls how to handle fields not marked with {@link Validate}
     */
    enum Option {
        /**
         * Ignore all fields not explicitly marked as validating.
         */
        IGNORE_UNMARKED,

        /**
         * Validate all unmarked (and not ignored) fields against Groovy truth.
         */
        VALIDATE_UNMARKED
    }
}