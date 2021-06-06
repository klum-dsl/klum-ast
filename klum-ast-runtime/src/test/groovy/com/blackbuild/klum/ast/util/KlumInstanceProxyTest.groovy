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
        DslHelper.getKeyField(getClass("pk.Bar")).get().getName() == "name"

    }

    def "inherited instanceProperties"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
            @DSL
            class Bar extends Foo {
                String child
            }
        ''')

        when:
        instance = newInstanceOf("pk.Bar")
        instance.name = "myName"
        instance.child = "myChild"
        def proxy = new KlumInstanceProxy(instance)

        then:
        proxy.getInstanceProperty("name") == "myName"
        proxy.getInstanceProperty("child") == "myChild"
    }
    def "Default values"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String child
                @Default(field = "child") String withDefaultValue
                @Default(code =  {child.toLowerCase()}) String withDefaultCode
                
            }
        ''')

        instance = newInstanceOf("pk.Foo")
        instance.child = "myChild"
        def proxy = new KlumInstanceProxy(instance)

        expect:
        proxy.getInstanceProperty("child") == "myChild"
        proxy.getInstanceProperty("withDefaultValue") == "myChild"
        proxy.getInstanceProperty("withDefaultCode") == "mychild"

        when:
        instance.withDefaultValue = "my"
        instance.withDefaultCode = "child"

        then:
        instance.withDefaultValue == "my"
        instance.withDefaultCode == "child"
    }

    def "invoke via getProperty"() {
        given:
        createClass('''
            package pk
            
            class Foo {
                final KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
            
                String name 
                
                String getName() {
                    $proxy.getInstanceProperty('name')
                }
            }

            @DSL
            class Bar extends Foo {
                String child
                
                String getChild() {
                    $proxy.getInstanceProperty('child')
                }
            }
        ''')

        when:
        instance = newInstanceOf("pk.Bar")
        instance.name = "myName"
        instance.child = "myChild"

        then:
        instance.name == "myName"
        instance.child == "myChild"
    }

    def "Field can be set via various methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String provider
                
                @Field(key = { provider })
                Object viaProvider

                @Field(key = Field.FieldName)
                Object byFieldName
                
                @Field
                Object noKeyMember

                Object noFieldAnnotation
            }
         ''')

        when:
        instance = newInstanceOf("pk.Foo")
        instance.provider = "bar"
        def proxy = new KlumInstanceProxy(instance)

        then:
        proxy.resolveKeyForFieldFromAnnotation("viaProvider", proxy.getField("viaProvider")).get() == "bar"
        proxy.resolveKeyForFieldFromAnnotation("byFieldName", proxy.getField("byFieldName")).get() == "byFieldName"
        !proxy.resolveKeyForFieldFromAnnotation("noKeyMember", proxy.getField("noKeyMember")).isPresent()
        !proxy.resolveKeyForFieldFromAnnotation("noFieldAnnotation", proxy.getField("noFieldAnnotation")).isPresent()
    }

}
