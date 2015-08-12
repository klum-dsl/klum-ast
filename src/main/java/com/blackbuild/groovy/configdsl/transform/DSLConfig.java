package com.blackbuild.groovy.configdsl.transform;

import org.codehaus.groovy.transform.CanonicalASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@GroovyASTTransformationClass(classes={CanonicalASTTransformation.class, DSLConfigASTTransformation.class})
public @interface DSLConfig {

    /**
     * Which field of this class is used as the key when an instance of this class is used in a collection.
     */
    String key() default "";

    boolean polymorphic() default false;

}
