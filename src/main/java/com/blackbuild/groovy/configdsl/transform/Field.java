package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls specific behaviour for certain fields. Currently, this annotation only makes sense for
 * collection fields.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface Field {

    /**
     * Name of the inner methods for collections. If empty (default), use fieldname stripped of a trailing 's'
     * (i.e. if the field is called environments, the elements are called environment by default. If the fieldname
     * does not end with an 's', the field name is used as is.
     */
    String members() default "";

    /**
     * Names the classes that are available as shortcuts for this field. Only used for list/map fields.
     */
    Class[] alternatives() default {};
}
