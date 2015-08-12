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
     * is the Object polymorphic, i.e. should the factory contain an additional class parameter.
     */
    boolean polymorphic() default false;

}
