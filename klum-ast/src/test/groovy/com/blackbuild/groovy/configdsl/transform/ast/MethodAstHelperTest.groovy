package com.blackbuild.groovy.configdsl.transform.ast

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

class MethodAstHelperTest extends Specification {

    def "check class distance"(Object arg, Object parent, int distance) {
        expect:
        MethodAstHelper.classDistance(cn(arg), cn(parent), 0) == distance

        where:
        arg         | parent    || distance
        Object      | Object    || 0
        String      | Object    || 1
        Properties  | Map       || 2
        String[]    | Object    || 1
        String[]    | String[]  || 0
    }

    protected ClassNode cn(Object type) {
        if (type instanceof ClassNode)
            return type
        if (type instanceof Class)
            return ClassHelper.make(type)
        throw new IllegalArgumentException("Unsupported type: $type")
    }


}
