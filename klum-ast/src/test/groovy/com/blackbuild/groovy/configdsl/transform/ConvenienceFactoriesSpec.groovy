/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

            Foo.Create.With {
                value "bla"
            }
        ''')

        when:
        instance = clazz.Create.From(scriptClass)

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
        instance = clazz.Create.From(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/195")
    def "convenience factory from script class for abstract class"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Foo {
                String value
            }
            
            @DSL
            class Bar extends Foo {}
        ''')

        def scriptClass = createSecondaryClass('''
            import pk.Bar

            Bar.Create.With {
                value "bla"
            }
        ''')

        when:
        instance = getClass("pk.Foo").Create.From(scriptClass)

        then:
        instance.class.name == "pk.Bar"
        instance.value == "bla"
    }


    def "convenience factory from delegating script class calls post-apply method"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
                boolean paCalled
                
                @PostApply
                void postApply() {
                    paCalled = true
                }
            }
        ''')


        def scriptClass = createSecondaryClass('''
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            value "bla"
        ''')

        when:
        instance = clazz.Create.From(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.paCalled
    }

    def "convenience factory from delegating script class for keyed class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
            }
        ''')


        def scriptClass = createSecondaryClass('''
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            value "bla"
        ''', 'BuBu.groovy')

        when:
        instance = clazz.Create.From(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.name == 'BuBu'
    }

    def "convenience factory from delegating script class for keyed class calls postapply"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value

                boolean paCalled
                
                @PostApply
                void postApply() {
                    paCalled = true
                }
            }
        ''')

        def scriptClass = createSecondaryClass('''
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            value "bla"
        ''', 'BuBu.groovy')

        when:
        instance = clazz.Create.From(scriptClass)

        then:
        instance.class.name == "pk.Foo"
        instance.value == "bla"
        instance.name == 'BuBu'
        instance.paCalled
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
        instance = clazz.Create.From(configText)

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
        instance = clazz.Create.From(configText)

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
        instance = clazz.Create.From(configText)

        then:
        thrown AssertionError
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
        instance = clazz.Create.From(configText)

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
        instance = clazz.Create.From(configText)

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
        instance = clazz.Create.From("blub", configText)

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
        File src = new File(temp.newFolder("bla", "bli"), "blub.config")
        src.text = '''
            value "bla"
        '''

        when:
        instance = clazz.Create.From(src)

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
        File src = new File(temp.newFolder("bla", "bli"), "blub.config")
        src.text = '''
            value "bla"
        '''

        when:
        instance = clazz.Create.From(src)

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
        instance = clazz.Create.From(src.toURI().toURL())

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
        instance = clazz.Create.From(configText)

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
        def template = clazz.Create.With(name: 'Dieter')

        when:
        clazz.withTemplate(template) {
            instance = clazz.Create.From(configText)
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
            pk.Config.Create.With {
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

    @Issue("https://github.com/klum-dsl/klum-ast/issues/195")
    def "read model from classpath for abstract class"() {
        given:
        def classPathRoot = temp.newFolder()
        def properties = new File(classPathRoot, "META-INF/klum-model/pk.Config.properties")
        properties.parentFile.mkdirs()
        createClass'''
            package pk

            @DSL
            abstract class Config {
                String name
            }

            @DSL
            class ConfigImpl extends Config {
                String value
            }'''
        createSecondaryClass'''
            package impl
            pk.ConfigImpl.Create.With {
                name "hallo"
                value "welt"
            }''', "Configuration.groovy"
        properties.text = "model-class: impl.Configuration"
        loader.addURL(classPathRoot.toURI().toURL())

        when:
        instance = getClass("pk.Config").createFromClasspath()

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

    @Issue("https://github.com/klum-dsl/klum-ast/issues/198")
    def "multiple convenience factory calls for lists"() {
        given:
        createClass '''
package model

@DSL
class Container {
    List<Element> elements
} 

@DSL
class Element {
    String name
}
'''
        def first = createSecondaryClass '''
package scripts

import model.Element

Element.Create.With {
  name "first-from-script"
}
''', "First.groovy"
        def second = createSecondaryClass '''
package scripts

import model.Element

Element.Create.With(name: "second-from-script")
''', "Second.groovy"

        when:
        instance = clazz.Create.With {
            elements(first, second)
        }

        then:
        instance.elements[0].name == "first-from-script"
        instance.elements[1].name == "second-from-script"
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/198")
    def "multiple convenience factory calls for maps"() {
        given:
        createClass '''
package model

@DSL
class Container {
    Map<String, Element> elements
} 

@DSL
class Element {
    @Key String id
    String name
}
'''
        def first = createSecondaryClass '''
package scripts

import model.Element

Element.Create.With("a") {
  name "first-from-script"
}
''', "First.groovy"
        def second = createSecondaryClass '''
package scripts

import model.Element

Element.Create.With("b", name: "second-from-script")
''', "Second.groovy"

        when:
        instance = clazz.Create.With {
            elements(first, second)
        }

        then:
        instance.elements.a.name == "first-from-script"
        instance.elements.b.name == "second-from-script"
    }

}
