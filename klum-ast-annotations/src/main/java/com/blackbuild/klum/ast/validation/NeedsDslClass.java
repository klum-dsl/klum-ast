package com.blackbuild.klum.ast.validation;

import java.lang.annotation.*;

/**
 * Declares that the annotated annotation needs to be put on a class or member of a class annotated with {@link com.blackbuild.groovy.configdsl.transform.DSL}..
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NeedsDslClass {
}
