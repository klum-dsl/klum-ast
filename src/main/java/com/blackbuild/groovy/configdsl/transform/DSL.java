package com.blackbuild.groovy.configdsl.transform;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@GroovyASTTransformationClass(classes={DSLASTTransformation.class})
public @interface DSL {

    /**
     * Which field of this class is used as the key when an instance of this class is used in a collection.
     * The key field must be consistent in the complete hierarchy of DSLObjects, meaning:
     *
     * <ul>
     *     <li>if the top-most dsl class has no key, all child classed must not have a key either</li>
     *     <li>if the top-most dsl class does have a key, all child class must either have the same or no key.</li>
     * </ul>
     *
     * It is an error to let the key attribute point to a non existing field. Also, currently only fields
     * of type String are allowed.
     */
    String key() default "";

    /**
     * If set, automatically sets this field to the containing instance when adding. This attribute
     * must point to an existing field of a DSL Object.
     */
    String owner() default "";
}
