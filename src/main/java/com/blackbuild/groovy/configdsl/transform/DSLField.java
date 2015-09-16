package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.Closure;
import org.codehaus.groovy.transform.CanonicalASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface DSLField {

    /**
     * Name of the method(s) of the dsl for this field. If empty, use fieldname. If the annotated field is a collection,
     * use, this is the name of a wrapping closure. In this case, a '-' denotes no wrapping closure at all.
     */
    String value() default "";

    /**
     * If true, this field is optional, i.e. during validation is accepted to be empty/null.
     */
    boolean optional() default false;

    /**
     * Name of the inner methods for collections. If empty (default), use fieldname/value stripped of a trailing 's'
     * (i.e. if the field is called environments, the elements are called environment by default. If the fieldname
     * does not end with an 's', the field name is used as is.
     */
    String element() default "";

    /**
     * Names the classes that are available as shortcuts for this field. Only used for list/map fields.
     */
    Class[] alternatives() default {};
}
