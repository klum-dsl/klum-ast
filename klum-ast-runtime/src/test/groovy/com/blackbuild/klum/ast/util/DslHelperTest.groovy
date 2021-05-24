package com.blackbuild.klum.ast.util

class DslHelperTest extends AbstractRuntimeTest {

    void "getField returns correct Field"() {
        given:
        createClass('''
            class Dummy {
                String name
            }
            
            class Yummy extends Dummy {
                String value
            }
        ''')

        when:
        def value = DslHelper.getField(getClass("Yummy"), "value")
        def name = DslHelper.getField(getClass("Yummy"), "name")

        then:
        noExceptionThrown()
        value != null
        name != null

    }


}
