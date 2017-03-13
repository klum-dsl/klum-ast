package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Designates a method to be executed after apply has been called. There can be an arbitrary number of lifecycle
 * methods in a class. Lifecycle methods must not be private. They can be overridden.
 *
 * <p>Lifecycle methods are called in the order of the model hierarchy, i.e. first lifecycle methods of the ancestor
 * model are called, then of the next level and so one. Overridden lifecycle methods are called in the place where they
 * were originally defined, i.e. if a method is defined in {@code Parent} and overridden in {@code Child}, the
 * overridden method is called along with the {@code Parent}'s methods.</p>
 */
@Target({FIELD, METHOD})
@Retention(CLASS)
@Inherited
@Documented
public @interface PostApply {
}
