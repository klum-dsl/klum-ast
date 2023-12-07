package com.blackbuild.klum.ast.util.copy;

public interface CopyStrategy {

    <T> T getCopiedValue(T oldValue, T newValue);

}
