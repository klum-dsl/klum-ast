package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Activates validation for the given Field. The actual validation logic can be configured using a closure.
 * If no closure is set, validation is done using groovy truth.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Validate {

    Class value() default GroovyTruth.class;

    String message() default "";

    interface GroovyTruth {}

    interface Ignore {}
}
