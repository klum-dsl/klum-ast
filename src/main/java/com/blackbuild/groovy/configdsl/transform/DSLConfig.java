package com.blackbuild.groovy.configdsl.transform;

import org.codehaus.groovy.transform.CanonicalASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@GroovyASTTransformationClass(classes={CanonicalASTTransformation.class, DSLConfigASTTransformation.class})
public @interface DSLConfig {

    /**
     * Which field of this class is used as the key when an instance of this class is used in a collection.
     */
    String key() default "";

    /**
     * If set, automatically sets this field to the containing instance when adding.
     */
    String owner() default "";

}
