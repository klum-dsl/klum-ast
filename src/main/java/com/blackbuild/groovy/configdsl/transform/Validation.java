package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Validation {

    Option option() default Option.IGNORE_UNMARKED;

    Mode mode() default Mode.AUTOMATIC;

    enum Mode {
        AUTOMATIC, MANUAL
    }

    enum Option {
        IGNORE_UNMARKED, VALIDATE_UNMARKED
    }
}