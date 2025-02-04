package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import spock.lang.Issue

@SuppressWarnings('GrPackage')
class CopyHandlerRuntimeTest extends AbstractDSLSpec {

    @Issue("359")
    def "copy from map creates copies of nested DSL objects"() {
        given:
        createClass('''
            package pk

import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL
            class Outer {
                String name
                Inner inner
            }
            
            @DSL
            class Inner {
                String value
                String another
            } 
         ''')

        when:
        def target = Outer.Create.One()
        CopyHandler.copyToFrom(target, [name: "bli", inner: [value: "bla", another: "blub"]])

        then:
        target.name == "bli"
        target.inner.value == "bla"
        getClass("pk.Inner").isInstance(target.inner)
    }


}