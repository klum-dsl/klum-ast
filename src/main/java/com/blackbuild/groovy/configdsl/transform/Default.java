package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a default value for the given field. This automatically creates a getter providing that default value.
 *
 * The default value can be one of the following (as defined by which attribute of the annotation is set, which is used
 * if the current value is not set (as defined by Groovy Truth):
 * - field: return the value of the target field
 * - delegate: return the value of an identically named field of the given delegate field
 * - closure: execute the closure (in the context of `this` and return the result
 *
 * Note that it is illegal to set more than one member of the `Default` annotation.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface Default {

    @Deprecated
    String value() default "";

    String field() default "";

    Class code() default None.class;

    String delegate() default "";

    class None {}
}
