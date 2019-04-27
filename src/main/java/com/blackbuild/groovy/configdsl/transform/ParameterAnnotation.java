package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation. Any annotation marked with this annotation will have its value annotation copied over to DSL
 * parameters of classes
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterAnnotation {

    /**
     * Allows a DelegatesTo annotation to be place on the element parameter for adder methods (in case of
     * collection or map only on the single adder methods).
     */
    @ParameterAnnotation
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface ClosureHint {
        DelegatesTo delegate() default @DelegatesTo;
        ClosureParams params() default @ClosureParams(SimpleType.class);
    }
}
