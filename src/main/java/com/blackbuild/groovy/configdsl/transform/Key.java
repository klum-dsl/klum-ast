package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a filed as the key of the containing class. This is mainly used, when an instance of the class is used
 * in a map.
 * The key field must be consistent in the complete hierarchy of DSL-Objects, meaning:
 *
 * <ul>
 *     <li>if the top-most dsl class has no key, all child classed must not have a key either</li>
 *     <li>if the top-most dsl class does have a key, all child class must either have the same or no key.</li>
 * </ul>
 *
 * It is an error to let the key attribute point to a non existing field. Also, currently only fields
 * of type String are allowed.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Key {
}
