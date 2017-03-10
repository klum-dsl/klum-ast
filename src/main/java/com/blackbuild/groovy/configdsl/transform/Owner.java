package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a field as owner field. The owner is automatically set when an instance of the
 * containing class is first added to another DSL-Object, either as value of a field or as member of a collection.
 * <p>There are two caveats</p>
 * <ul>
 *     <li>no validity checks are performed during transformation time, leading to runtime ClassCastExceptions
 *     if the owner type is incorrect. This allows for the fact that one type of model might be part of different
 *     owners.</li>
 *     <li>If an object that already has an existing owner is reused, the owner is not overridden, but silently ignored.
 *     I.e. the first object that an object is assigned to, is the actual owner.</li>
 * </ul>
 * <p><b>Currently, only one owner field is allowed in a model hierarchy.</b></p>
 * <p><b>The setting of the owner is determined statically during transformation, i.e. if the owner class (Container) has
 * a field of type {@code Parent} and the owner field is defined in the class {@code Child}, the owner field of a Child instance
 * will never be set when added to a Container instance</b></p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Owner {
}
