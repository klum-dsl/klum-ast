package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.Closure;

/**
 * Dummy annotation to mark a member field accepting a closure as a special value
 * (like Undefine, Ignored, etc.), i.e. the closure will not be executed, but a special
 * handling is done. This allows to mark the member as Class&lt;? extends Closure&gt; instead
 * of simply class, reducing the need for manual validation.
 * @param <T>
 */
public abstract class NamedAnnotationMemberClosure<T> extends Closure<T> {
    protected NamedAnnotationMemberClosure(Object owner, Object thisObject) {
        super(owner, thisObject);
    }

    public Object doCall(Object... ignored) {
        return null;
    }
    public Object doCall(Object ignored) {
        return null;
    }
    public Object doCall() {
        return null;
    }

}
