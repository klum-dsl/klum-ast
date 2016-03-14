package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.Closure;

/**
 * Activates validation for the given Field. The actual validation logic can be configured using a closure.
 * If no closure is set, validation is done using groovy truth.
 */
public @interface Validate {
    // Class<? extends Closure> value() default;

}
