package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.Closure;

/**
 * Activates validation for the given Field. The actual validation logic can be configured using a closure.
 * If no closure is set, validation is done using groovy truth.
 */
public @interface Validate {

    Class<? extends Closure> value() default GroovyTruth.class;

    String message() default "";

    class GroovyTruth extends Closure {
        public GroovyTruth(Object owner, Object thisObject) {
            super(owner, thisObject);
        }

        public GroovyTruth(Object owner) {
            super(owner);
        }

        public Object doCall(Object value) {
            return value;
        }
    }

    class Ignore extends Closure {
        public Ignore(Object owner, Object thisObject) {
            super(owner, thisObject);
        }

        public Ignore(Object owner) {
            super(owner);
        }

        public Object doCall(Object value) {
            return Boolean.TRUE;
        }
    }

}
