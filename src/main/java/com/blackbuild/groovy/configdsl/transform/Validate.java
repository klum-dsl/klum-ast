package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activates validation for the given Field. The actual validation logic can be configured using a closure.
 * If no closure is set, validation is done using groovy truth.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Validate {

    Class value() default GroovyTruth.class;

    String message() default "";

    class GroovyTruth {}

    class Ignore {}
}
