package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;


/**
 * Meta-annotation to mark annotations that mark methods that change the model.
 * WriteAccess marked methods are moved into the RW class during compilation.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WriteAccess {

    /**
     * @return the type of write access. LIFECYCLE means the method ist automatically called during
     * a KlumPhase. MANUAL means the method is called manually by the user as part of the model.
     * Lifecycle methods must not have any parameters.
     */
    Type value() default Type.LIFECYCLE;

    enum Type { LIFECYCLE, MANUAL }
}
