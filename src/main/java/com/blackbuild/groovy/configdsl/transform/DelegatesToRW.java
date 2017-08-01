package com.blackbuild.groovy.configdsl.transform;

import groovy.transform.Undefined;

import java.lang.annotation.*;

/**
 * This annotation can be used to mark a closure method to be delegating to an RW class. This works
 * similar to `DelegatesTo`, but since the RW class does not yet exist during parsing of the schema, it
 * is not possible to simply write `{@link groovy.lang.DelegatesTo}(MyClass._RW)`. Can take an optional
 * value of the target class whose RW class should be delegated to.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface DelegatesToRW {

    /**
     * The type to which the closure argument should delegate. If not set, the type owning the method
     * is taken.
     */
    Class value() default Undefined.class;

}
