package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a method to be executed after create has been called and the templates have been applied.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface PostCreate {
}
