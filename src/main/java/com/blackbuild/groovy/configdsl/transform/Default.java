package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a default value for the given field. This automatically creates a getter providing that default value.
 *
 * Can be used in one of two modes. The simple mode, using the value of the annotation delegates simply to another field,
 * the complex one provides a closure for the default value.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@GroovyASTTransformationClass(classes={DSLASTTransformation.class})public @interface Default {

    String value() default "";

    Class code() default None.class;

    class None {}
}
