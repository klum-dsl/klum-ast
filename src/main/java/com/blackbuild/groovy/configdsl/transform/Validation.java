package com.blackbuild.groovy.configdsl.transform;

public @interface Validation {

    Options target() default Options.IGNORE_UNMARKED;

    Mode mode() default Mode.AUTOMATIC;

    enum Mode {
        AUTOMATIC, MANUAL
    }

    enum Options {
        IGNORE_UNMARKED, VALIDATE_UNMARKED
    }
}