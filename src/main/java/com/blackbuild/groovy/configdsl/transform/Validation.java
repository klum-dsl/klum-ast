package com.blackbuild.groovy.configdsl.transform;

public @interface Validation {

    Target target() default Target.IGNORE_UNMARKED;

    Mode mode() default Mode.AUTOMATIC;

    enum Mode {
        AUTOMATIC, MANUAL
    }

    enum Target {
        IGNORE_UNMARKED, VALIDATE_UNMARKED
    }
}