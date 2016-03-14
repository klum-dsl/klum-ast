package com.blackbuild.groovy.configdsl.transform;

/**
 * Defines how an object handles validation. Is set in the {@link DSL} annotation.
 */
public enum ValidationMode {

    NONE, IGNORE_UNMARKED, VALIDATE_UNMARKED

}
