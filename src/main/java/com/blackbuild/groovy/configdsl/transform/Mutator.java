package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a method as mutator. Mutators can changes the state of a model instance and can only be
 * called inside an create / apply block. Technically, they are transfered to the RW class instance.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Mutator {
}
