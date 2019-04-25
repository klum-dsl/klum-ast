package com.blackbuild.groovy.configdsl.transform.helper

class HelperCategories {

    static boolean hasNoMethod(Class type, String methodName, Class... parameterTypes) {
        type.metaClass.getMetaMethod(methodName, parameterTypes) == null
    }

    static boolean hasMethod(Class type, String methodName, Class... parameterTypes) {
        type.metaClass.getMetaMethod(methodName, parameterTypes) != null
    }

}
