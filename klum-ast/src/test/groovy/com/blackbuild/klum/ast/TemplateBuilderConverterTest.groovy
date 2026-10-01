/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue

@Issue("731")
class TemplateBuilderConverterTest extends AbstractDSLSpec {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "template creation projects nested source converters into owned Template relationships"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                Child child
            }

            @DSL
            class Child {
                String value
                int postCreateCalls

                @PostCreate
                void initialize() {
                    postCreateCalls++
                }

                static Child fromString(String value) {
                    Child.Create.With(value: value)
                }
            }
        '''
        def Parent = clazz

        when:
        def template = Parent.Create.Template.With {
            child 'template-child'
        }
        def mapTemplate = Parent.Create.Template.With(child: 'map-child')
        def first
        def second
        Parent.Template.With(template) {
            first = Parent.Create.One()
            second = Parent.Create.One()
        }

        then:
        template.child.value == 'template-child'
        template.child.postCreateCalls == 0
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.child)
        mapTemplate.child.value == 'map-child'
        TemplateManager.isTemplate(mapTemplate.child)

        and: 'each application rehydrates a distinct ordinary Builder graph'
        first.child.value == 'template-child'
        first.child.postCreateCalls == 1
        !TemplateManager.isTemplate(first)
        !TemplateManager.isTemplate(first.child)
        !first.child.is(template.child)
        !second.child.is(template.child)
        !second.child.is(first.child)
    }

    @Issue(["731", "729"])
    def "Template definition takes precedence over an active Construction session and restores it afterwards"() {
        given:
        createClass '''
            package pk

            @DSL
            class Registry {
                Child child
            }

            @DSL
            class Parent {
                Child child
            }

            @DSL
            class Child {
                String value

                static Child fromString(String value) {
                    Child.Create.With(value: value)
                }
            }
        '''
        def Registry = clazz
        def Parent = getClass('pk.Parent')

        when:
        def template
        def registry = Registry.Create.With {
            template = Parent.Create.Template.With {
                child 'template-child'
            }
            child 'session-child'
        }

        then:
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.child)
        registry.child.value == 'session-child'
        !TemplateManager.isTemplate(registry)
        !TemplateManager.isTemplate(registry.child)

        when: 'the Template definition scope has exited'
        getClass('pk.Child').Create.AsBuilder().One()

        then:
        thrown(KlumModelException)
    }

    def "Template file and URL sources retain converter composition as Template-owned children"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                Child child
            }

            @DSL
            class Child {
                String value

                static Child fromString(String value) {
                    Child.Create.With(value: value)
                }
            }
        '''
        def Parent = clazz
        def templateFile = temporaryFolder.newFile('parent-template.groovy')
        templateFile.text = "child 'source-child'"

        when:
        def fileTemplate = Parent.Create.Template.From(templateFile)
        def urlTemplate = Parent.Create.Template.From(templateFile.toURI().toURL())

        then:
        [fileTemplate, urlTemplate].every {
            it.child.value == 'source-child'
                    && TemplateManager.isTemplate(it)
                    && TemplateManager.isTemplate(it.child)
        }
    }

    @Issue(["731", "729"])
    def "nested and failed Template definitions restore their composition scope"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                Child child
            }

            @DSL
            class Child {
                String value

                static Child fromString(String value) {
                    Child.Create.With(value: value)
                }
            }
        '''
        def Parent = clazz
        def Child = getClass('pk.Child')

        when:
        def nested
        def outer = Parent.Create.Template.With {
            nested = Parent.Create.Template.With { child 'nested-child' }
            child 'outer-child'
        }

        then:
        TemplateManager.isTemplate(outer.child)
        TemplateManager.isTemplate(nested.child)

        when: 'scoped Template application is not Template-definition composition'
        Parent.Template.With(outer) {
            Child.Create.AsBuilder().One()
        }

        then:
        thrown(KlumModelException)

        when: 'an exceptional definition also cleans up its scope'
        Parent.Create.Template.With {
            child 'before-failure'
            throw new IllegalStateException('expected failure')
        }

        then:
        thrown(IllegalStateException)

        when:
        Child.Create.AsBuilder().One()

        then:
        thrown(KlumModelException)
    }
}
