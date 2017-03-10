package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Controls specific behaviour for certain fields. Currently, this annotation only makes sense for
 * collection fields.
 * <p>
 *     This can be used to explicitly name the members of a collection. By default, the member name of collection
 *     is the name of the collection minus a trailing 's', i.e. environments :: environment. The member name is used
 *     as name for the generation of adder methods.
 * </p>
 * <p>
 *     Using {@code @Field}, this can be explicitly overridden, for example for values with different plural rules.
 *     For example, the field {@code libraries} would by default contain the wrong elements name {@code librarie},
 *     which could be changed:
 * </p>
 * <p>{@code @Field(member = 'library') Set<String> libraries}</p>
 * <p><b>Note that the member names must be unique across all collections of a DSL hierarchy.</b></p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Field {

    /**
     * Name of the inner methods for collections. If empty (default), use field name stripped of a trailing 's'
     * (i.e. if the field is called environments, the elements are called environment by default. If the field name
     * does not end with an 's', the field name is used as is.
     */
    String members() default "";
}
