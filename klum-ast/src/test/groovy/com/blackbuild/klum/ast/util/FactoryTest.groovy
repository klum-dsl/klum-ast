package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import spock.lang.Issue

@Issue("76")
class FactoryTest extends AbstractDSLSpec {

    def "Create field is created and initialized"() {
        when:
        createClass '''import com.blackbuild.groovy.configdsl.transform.DSL
@DSL abstract class AnAbstractClass {}

@DSL class NonAbstractSubclassOfAnAbstractClass extends AnAbstractClass {}

@DSL class AClass {}

@DSL class AKeyedClass {
    @Key String id
}

@DSL class ASubclassOfAKeyedClass extends AKeyedClass {}

@DSL(defaultImpl = DefaultImpl) 
abstract class AbstractWithDefaultImpl {}

@DSL class DefaultImpl extends AbstractWithDefaultImpl {}
'''
        then:
        !getClass("AnAbstractClass").fields.any {it.name == "Create" }

        when:
        def factoryField = getClass(className).getField("Create")

        then:
        factoryField?.type == factoryType

        when:
        KlumFactory factory = getClass(className).Create

        then:
        factory.type.name == factoryTargetClassName

        where:
        className                              || factoryType           | factoryTargetClassName
        "NonAbstractSubclassOfAnAbstractClass" || KlumUnkeyedFactory    | "NonAbstractSubclassOfAnAbstractClass"
        "AClass"                               || KlumUnkeyedFactory    | "AClass"
        "AKeyedClass"                          || KlumKeyedFactory      | "AKeyedClass"
        "ASubclassOfAKeyedClass"               || KlumKeyedFactory      | "ASubclassOfAKeyedClass"
        "AbstractWithDefaultImpl"              || KlumUnkeyedFactory    | "DefaultImpl"
        "DefaultImpl"                          || KlumUnkeyedFactory    | "DefaultImpl"
    }

    def "basic test"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL

@DSL class AClass {}
'''
        when:
        clazz.Create.Empty()

        then:
        noExceptionThrown()
    }



}