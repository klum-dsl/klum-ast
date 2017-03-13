package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Designates a method to be executed after create has been called and the templates have been applied. These methods
 * are called before named parameters or the creation closure is applied.
 * There can be an arbitrary number of {@link PostCreate} methods in a class. Like other lifecycle methods,
 * {@link PostCreate} methods must not be private, and they can be overridden.
 * <p>A common usage for post create methods is to extract data</p>
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
public @interface PostCreate {
}
