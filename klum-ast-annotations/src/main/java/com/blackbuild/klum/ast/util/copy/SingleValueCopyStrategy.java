package com.blackbuild.klum.ast.util.copy;

public interface SingleValueCopyStrategy {
    <T> T copyDslObject(T oldValue, T newValue);
    <T> T copySingleValue(T oldValue, T newValue);

}
