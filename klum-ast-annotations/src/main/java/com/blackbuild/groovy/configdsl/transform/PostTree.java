package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Designates a method to be executed after the complete model has been created and auto* phases have run.
 * PostTree methods can be use to modify the model as a whole.</p>
 *
 * <p>Due to the late phase in which PostTree methods run, the model is already fully linked.</p>
 *
 * <p>Like all lifecycle methods, PostTree methods must be non-private and parameterless.</p>
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Inherited
@WriteAccess(WriteAccess.Type.LIFECYCLE)
@Documented
public @interface PostTree {
}
