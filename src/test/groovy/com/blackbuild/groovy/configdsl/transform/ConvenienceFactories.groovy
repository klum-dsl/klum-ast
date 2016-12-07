package com.blackbuild.groovy.configdsl.transform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue

class ConvenienceFactories extends AbstractDSLSpec {

    @Rule TemporaryFolder temp = new TemporaryFolder()

    def "convenience factory from script class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')

        def scriptClass = createSecondaryClass('''
            import pk.Foo

            Foo.create {
                value "bla"
            }
        ''')

        when:
        instance = clazz.createFromScript(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    def "convenience factory from String"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')

        def configText = '''
            value "bla"
        '''

        when:
        instance = clazz.createFromSnippet(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    def "createFromSnippet is deprecated"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')

        when:
        def method = clazz.getDeclaredMethod("createFromSnippet", String)

        then:
        method.getAnnotation(Deprecated) != null
    }

    def "convenience factory with static methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
                Bar bar
            }

            @DSL
            class Bar {
                String bValue
            }
        ''')

        def configText = '''
            pk.Bar.createTemplate {
                bValue "default"
            }
            value "bla"

            bar {}

        '''

        when:
        instance = clazz.createFromSnippet(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.bar.bValue == "default"
    }

    def "convenience factory from String with keyed object"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
            }
        ''')

        def configText = '''
            value "bla"
        '''

        when:
        instance = clazz.createFromSnippet("blub", configText)

        then:
        instance.class.name == "pk.Foo"
        instance.name == "blub"
        instance.value == "bla"
    }

    def "convenience factory from file"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')
        File src = temp.newFile("blub.config")
        src.text = '''
            value "bla"
        '''

        when:
        instance = clazz.createFromSnippet(src)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    def "keyed convenience factory from file"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
            }
        ''')
        File src = temp.newFile("blub.config")
        src.text = '''
            value "bla"
        '''

        when:
        instance = clazz.createFromSnippet(src)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.name == "blub"
    }

    def "keyed convenience factory from URL"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
            }
        ''')
        File src = temp.newFile("blub.config")
        src.text = '''
            value "bla"
        '''

        when:
        instance = clazz.createFromSnippet(src.toURI().toURL())

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.name == "blub"
    }

    def "convenience factory from String with imports"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')

        def configText = '''
            import java.util.List

            value "bla"
        '''

        when:
        instance = clazz.createFrom(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    @Issue("https://github.com/blackbuild/config-dsl/issues/45")
    def "template should be applied for convenience factories"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        def configText = '''
            import java.util.List

            value "bla"
        '''

        and:
        def template = clazz.create(name: 'Dieter')

        when:
        clazz.withTemplate(template) {
            instance = clazz.createFrom(configText)
        }

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.name == "Dieter"
    }

}
