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

import groovy.util.DelegatingScript
import spock.lang.Issue

@Issue("205")
class BuilderDispatchDiagnosticTest extends AbstractDSLSpec {

    def setup() {
        createClass '''
            @DSL class Container extends ParentContainer {
                Element element
                List<Element> elements
                String value

                String elment() { 'completed only' }
                static String helper() { 'static' }
            }
            @DSL class ParentContainer {
                @Builder.Method String inheritedOperation() { 'inherited' }
            }
            @DSL class Element {
                String value
            }
        '''
        clazz = getClass('Container')
    }

    def "terminal #path typo names the public contract"() {
        given:
        Class script = createSecondaryClass(source)

        when:
        script.getDeclaredConstructor().newInstance().run()

        then:
        def error = thrown(MissingMethodException)
        error.type == getClass(contract)
        !error.static
        error.method == missing
        error.message.contains(candidate)
        String candidates = error.message.substring(error.message.indexOf('Possible solutions:'))
        !candidates.contains('elment(')
        !candidates.contains('helper(')
        !candidates.contains('applyOnly(')

        where:
        path | source | contract | missing | candidate
        'root' | 'Container.Create.With { elment() }' | 'Container_DSL$Builder' | 'elment' | 'element('
        'ordinary nested closure' | 'Container.Create.With { [1].each { elment() } }' | 'Container_DSL$Builder' | 'elment' | 'element('
        'deeply nested closure' | 'Container.Create.With { [1].each { [2].each { elment() } } }' | 'Container_DSL$Builder' | 'elment' | 'element('
        'inherited' | 'Container.Create.With { inheritedOperaton() }' | 'Container_DSL$Builder' | 'inheritedOperaton' | 'inheritedOperation('
        'nested' | 'Container.Create.With { element { vlaue("x") } }' | 'Element_DSL$Builder' | 'vlaue' | 'value('
        'collection factory' | 'Container.Create.With { elements { elment() } }' | 'Container_DSL$Builder$CollectionFactory_elements' | 'elment' | 'element('
        'collection element' | 'Container.Create.With { elements { element { vlaue("x") } } }' | 'Element_DSL$Builder' | 'vlaue' | 'value('
    }

    def "DelegatingScript failures use the public Builder contract"() {
        given:
        compilerConfiguration.scriptBaseClass = DelegatingScript.name
        Class script = createSecondaryClass('elment()')

        when:
        clazz.Create.From(script)

        then:
        def error = thrown(MissingMethodException)
        error.type == getClass('Container_DSL$Builder')
        error.message.contains('element(')
    }

    def "custom inherited MetaClass and static fallback calls continue to succeed"() {
        given:
        createClass '''
            @DSL class Config extends BaseConfig {
                String value
                @Builder.Method void custom(String input) { value = input }
                static String helper() { 'static' }
            }
            @DSL class BaseConfig {
                String inherited
                @Builder.Method void inheritedHelper(String input) { inherited = input }
            }
        '''
        Class builder = getBuilderClass('Config')
        def expando = new ExpandoMetaClass(builder, true, true)
        expando.dynamicHelper = { String input -> delegate.value = input }
        expando.initialize()
        GroovySystem.metaClassRegistry.setMetaClass(builder, expando)
        Class script = createSecondaryClass('''
            Config.Create.With {
                custom('custom')
                assert value == 'custom'
                inheritedHelper('inherited')
                dynamicHelper(helper())
            }
        ''')

        when:
        instance = script.getDeclaredConstructor().newInstance().run()

        then:
        instance.value == 'static'
        instance.inherited == 'inherited'

        cleanup:
        GroovySystem.metaClassRegistry.removeMetaClass(builder)
    }

    def "completed Model methodMissing behavior is retained"() {
        given:
        createClass '''
            @DSL class DynamicConfig {
                String methodMissing(String name, Object args) { name }
            }
        '''

        when:
        def result = clazz.Create.With().custom('value')

        then:
        result == 'custom'
    }

    def "user-code failure from #path retains its receiver and stack"() {
        given:
        createClass '''
            import groovy.transform.TypeChecked
            import groovy.transform.TypeCheckingMode

            @DSL class FailingConfig {
                static MissingMethodException recorded
                @Builder.Method void fail() {
                    recorded = new MissingMethodException('fail', this.class, [] as Object[])
                    throw recorded
                }
                @TypeChecked(TypeCheckingMode.SKIP)
                @Builder.Method void dispatchFailure() { absentInsideHelper() }
                @TypeChecked(TypeCheckingMode.SKIP)
                static void staticFailure() { absentInsideStaticHelper() }
                static void sameNameFailure() {
                    throw new MissingMethodException('sameNameFailure', FailingConfig, [] as Object[])
                }
                @Builder.Method void withoutStack() {
                    recorded = new MissingMethodException('withoutStack', this.class, [] as Object[])
                    recorded.stackTrace = new StackTraceElement[0]
                    throw recorded
                }
            }
        '''
        Class script = createSecondaryClass(source)

        when:
        script.getDeclaredConstructor().newInstance().run()

        then:
        def error = thrown(MissingMethodException)
        error.method == missing
        error.type.name in ['FailingConfig', 'FailingConfig$Builder']
        !error.type.interface
        if (missing in ['fail', 'withoutStack']) {
            assert error.is(clazz.recorded)
        }
        if (missing == 'withoutStack') {
            assert error.stackTrace.length == 0
        }

        where:
        path | source | missing
        'selected Builder method' | 'FailingConfig.Create.With { fail() }' | 'fail'
        'Builder method dispatch' | 'FailingConfig.Create.With { dispatchFailure() }' | 'absentInsideHelper'
        'static helper dispatch' | 'FailingConfig.Create.With { staticFailure() }' | 'absentInsideStaticHelper'
        'same-name static helper exception' | 'FailingConfig.Create.With { sameNameFailure() }' | 'sameNameFailure'
        'stackless user exception' | 'FailingConfig.Create.With { withoutStack() }' | 'withoutStack'
        'explicit static receiver' | 'FailingConfig.Create.With { FailingConfig.absent() }' | 'absent'
    }

    def "explicitly thrown closure exception retains identity with nesting #nested"() {
        given:
        MissingMethodException recorded

        when:
        clazz.Create.With {
            Class receiver = delegate.class
            if (nested) {
                [1].each {
                    recorded = new MissingMethodException('elment', receiver, [] as Object[])
                    throw recorded
                }
            } else {
                recorded = new MissingMethodException('elment', receiver, [] as Object[])
                throw recorded
            }
        }

        then:
        def error = thrown(MissingMethodException)
        error.is(recorded)

        where:
        nested << [false, true]
    }

    def "nested DelegatingScript recipe failures name the child Builder"() {
        given:
        compilerConfiguration.scriptBaseClass = DelegatingScript.name
        Class recipe = createSecondaryClass('vlaue("x")', 'ElementRecipe.groovy')
        def factory = getClass('Element').Create

        when:
        clazz.Create.With {
            element(factory.AsBuilder().From(recipe))
        }

        then:
        def error = thrown(MissingMethodException)
        error.type == getClass('Element_DSL$Builder')
        error.message.contains('value(')
    }


    def "a selected MetaClass operation keeps its user exception"() {
        given:
        Class builder = getBuilderClass('Container')
        MissingMethodException recorded = new MissingMethodException('dynamicFailure', builder, [] as Object[])
        def expando = new ExpandoMetaClass(builder, true, true)
        expando.dynamicFailure = { throw recorded }
        expando.initialize()
        GroovySystem.metaClassRegistry.setMetaClass(builder, expando)

        when:
        clazz.Create.With { dynamicFailure() }

        then:
        def error = thrown(MissingMethodException)
        error.is(recorded)

        cleanup:
        GroovySystem.metaClassRegistry.removeMetaClass(builder)
    }


    def "Template script failures name the public Builder contract"() {
        given:
        File recipe = scriptFile('ContainerTemplate.groovy', 'elment()')

        when:
        clazz.Create.Template.From(recipe, loader)

        then:
        def error = thrown(MissingMethodException)
        error.type == getClass('Container_DSL$Builder')
        error.message.contains('element(')
    }


    def "a MetaClass handler defined inside configuration retains its failure"() {
        given:
        Class script = createSecondaryClass('''
            def handler
            boolean capture = true
            def configuration = {
                if (capture) handler = { delegate.missingFromHandler() }
                else dynamicFailure()
            }
            configuration()
            Class builder = Class.forName('Container$Builder', false, Container.classLoader)
            def methods = new ExpandoMetaClass(builder, true, true)
            methods.dynamicFailure = handler
            methods.initialize()
            GroovySystem.metaClassRegistry.setMetaClass(builder, methods)
            capture = false
            try {
                Container.Create.With(configuration)
            } finally {
                configuration.delegate = null
                handler.delegate = null
                GroovySystem.metaClassRegistry.removeMetaClass(builder)
            }
        ''')

        when:
        script.getDeclaredConstructor().newInstance().run()

        then:
        def error = thrown(MissingMethodException)
        error.method == 'missingFromHandler'
        error.type.name in ['Container', 'Container$Builder']
    }

}
