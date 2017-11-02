/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.groovy.configdsl.transform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue

class ConvenienceFactoriesSpec extends AbstractDSLSpec {

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
        instance = clazz.createFrom(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    def "convenience factory from delegating script class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')


        def scriptClass = createSecondaryClass('''
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            value "bla"
        ''')

        when:
        instance = clazz.createFrom(scriptClass)

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
        instance = clazz.createFrom(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    @Issue("114")
    def "convenience factory from String with validation"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Validate
                String value
            }
        ''')

        def configText = '''
            value "bla"
        '''

        when:
        instance = clazz.createFrom(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    @Issue("114")
    def "convenience factory must call validation at end"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Validate
                String value
            }
        ''')

        def configText = '''
        '''

        when:
        instance = clazz.createFrom(configText)

        then:
        thrown IllegalStateException
    }

    @Issue("114")
    def "post create must be called for convenience factory class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
                
                @PostCreate
                void postCreate() {
                    assert value == null
                }
                
                @PostApply
                void postApply() {
                    assert value != null
                }
            }
        ''')

        def configText = '''
            value "bla"
        '''

        when:
        instance = clazz.createFrom(configText)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    def "convenience factory with template"() {
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
            value "bla"

            pk.Bar.withTemplate(bValue: 'default') {
                bar {}
            }
        '''

        when:
        instance = clazz.createFrom(configText)

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
        instance = clazz.createFrom("blub", configText)

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
        instance = clazz.createFrom(src)

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
        instance = clazz.createFrom(src)

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
        instance = clazz.createFrom(src.toURI().toURL())

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

    def "read model from classpath"() {
        given:
        def classPathRoot = temp.newFolder()
        def properties = new File(classPathRoot, "META-INF/klum-model/pk.Config.properties")
        properties.parentFile.mkdirs()
        createClass'''
            package pk

            @DSL
            class Config {
                String name
                String value
            }'''
        createSecondaryClass'''
            package impl
            pk.Config.create {
                name "hallo"
                value "welt"
            }''', "Configuration.groovy"
        properties.text = "model-class: impl.Configuration"
        loader.addURL(classPathRoot.toURI().toURL())

        when:
        instance = clazz.createFromClasspath()

        then:
        instance.name == "hallo"
        instance.value == "welt"
    }

    def "read model from classpath with delegating script"() {
        given:
        def classPathRoot = temp.newFolder()
        def properties = new File(classPathRoot, "META-INF/klum-model/pk.Config.properties")
        properties.parentFile.mkdirs()
        createClass'''
            package pk

            @DSL
            class Config {
                String name
                String value
            }'''
        createSecondaryClass'''
            package impl
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            name "hallo"
            value "welt"
            ''', "Configuration.groovy"
        properties.text = "model-class: impl.Configuration"
        loader.addURL(classPathRoot.toURI().toURL())

        when:
        instance = clazz.createFromClasspath()

        then:
        instance.name == "hallo"
        instance.value == "welt"
    }

}
