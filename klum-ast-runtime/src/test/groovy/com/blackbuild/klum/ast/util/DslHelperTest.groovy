package com.blackbuild.klum.ast.util

import java.lang.reflect.Field
import java.lang.reflect.Method

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
        value.isPresent()
        name.isPresent()
    }

    void "getMethod returns correct Method"() {
        given:
        createClass('''
            class Dummy {
                void name(String bla) {}
            }
            
            class Yummy extends Dummy {
                void value(String bla) {}
            }
        ''')

        when:
        def value = DslHelper.getMethod(getClass("Yummy"), "value", String)
        def name = DslHelper.getMethod(getClass("Yummy"), "name", String)

        then:
        noExceptionThrown()
        value.isPresent()
        name.isPresent()

    }

    void "getFieldOrMethod returns correct result"() {
        given:
        createClass('''
            class Dummy {
                void name(String bla) {}
                String name2
            }
            
            class Yummy extends Dummy {
                void value(String bla) {}
                String value2
            }
        ''')

        when:
        def yummy = getClass("Yummy")
        def value = DslHelper.getFieldOrMethod(yummy, "value", String)
        def name = DslHelper.getFieldOrMethod(yummy, "name", String)
        def value2 = DslHelper.getFieldOrMethod(yummy, "value2", String)
        def name2 = DslHelper.getFieldOrMethod(yummy, "name2", String)

        then:
        noExceptionThrown()
        value.get() instanceof Method
        name.get() instanceof Method
        value2.get() instanceof Field
        name2.get() instanceof Field

    }


}
