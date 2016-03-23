package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
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