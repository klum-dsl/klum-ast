package com.blackbuild.klum.ast.util

class KlumInstanceProxyTest extends AbstractRuntimeTest {

    void "getKey returns the correct key for inherited classes"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
            }
            @DSL
            class Bar extends Foo {
            }
        ''')

        expect:
        DslHelper.getKeyField(getClass("pk.Bar")).get() == "name"

    }




}
