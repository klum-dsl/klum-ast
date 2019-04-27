package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterAnnotation {

    @ParameterAnnotation
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface DelegatesTo {
        groovy.lang.DelegatesTo value();
    }

    @ParameterAnnotation
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface ClosureParams {
        groovy.transform.stc.ClosureParams value();
    }
}
