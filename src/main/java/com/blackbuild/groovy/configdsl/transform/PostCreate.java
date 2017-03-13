package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Designates a method to be executed after create has been called and the templates have been applied. These methods
 * are called before named parameters or the creation closure is applied, but after an {@link Owner} has been set.
 * There can be an arbitrary number of {@link PostCreate} methods in a class. Like other lifecycle methods,
 * {@link PostCreate} methods must not be private, and they can be overridden, it is advised to make
 * them protected.
 * <p>A common usage for post create methods is to extract data from the owner that is needed to further configure the
 * object, which might not be feasible using default values (the following example <i>could</i> be done with Default
 * values, though.</p>
 *
 * <pre><code>
 * given:
 * &#064;DSL
 * class Container {
 *   Foo foo
 *   String name
 * }
 *
 * &#064;DSL
 * class Foo {
 *   &#064;Owner Container owner
 *   String childName
 *
 *   &#064;PostCreate
 *   def setDefaultValueOfChildName() {
 *     // set a value derived from owner
 *     childName = "$owner.name::child"
 *   }
 * }
 *
 * when:
 * instance = clazz.create {
 *   name "parent"
 *   foo {}
 * }
 *
 * then:
 * instance.foo.childName == "parent::child"
 * </code></pre>
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
