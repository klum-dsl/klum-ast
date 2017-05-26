package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException

class SpecialCasesSpec extends AbstractDSLSpec {

    def "methodMissing in class should be allowed"() {
        when:
        createClass '''
package pk

@DSL
class DynamicModel {

    def methodMissing(String name, args) {
        throw new MissingMethodException(name, this.class, args)
    }
}
'''
        then:
        notThrown(MultipleCompilationErrorsException)

    }

    def "methodMissing should work"() {
        given:
        createClass '''
package pk

@DSL
class DynamicModel {
    static int count = 0

    def methodMissing(String name, args) {
        count++
    }
}
'''
        when:
        instance = clazz.create().bla()

        then:
        clazz.count == 1
    }

}