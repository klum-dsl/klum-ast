package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a method to be executed after create has been called and the templates have been applied. These methods are called
 * before named parameters or the creation closure is applied.
 * There can be an arbitrary number of lifecycle methods in a class. Lifecycle methods must not be private. The can be overridden.
 *
 * <p>Lifecycle methods are called in the order of the model hierarchy, i.e. first lifecycle methods of the ancestor model are
 * called, then of the next level and so one. Overridden lifecycle methods are called in the place where they where
 * originally defined, i.e. if a method is defined in {@code Parent} and overridden in {@code Child}, the overridden
 * method is called along with the {@code Parent}'s methods.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface PostCreate {
}
