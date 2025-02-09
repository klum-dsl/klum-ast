package com.blackbuild.klum.ast.util.layer3.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marker interface to designate an interface as a default value provider.
 * The attributes of the annotated annotation type will be used as default values for the annotated dsl object,
 * either as is, or, in case of a closure member, the result of the closure.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface DefaultValues {
}
