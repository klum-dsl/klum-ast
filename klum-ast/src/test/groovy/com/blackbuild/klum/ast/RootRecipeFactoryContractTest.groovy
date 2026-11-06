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
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue

@Issue('829')
class RootRecipeFactoryContractTest extends AbstractDSLSpec {
    private void schema(boolean keyed = false) {
        createClass """
            package recipecontract
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Application {
                ${keyed ? '@Key String id' : ''}
                String name
            }
            @DSL class Portfolio {
                Application application
                List<Application> applications
                @Cluster Map<String, Application> getSelected() { null }
            }
        """
    }

    def 'generated root signature preserves exact return delegate and recipe bound for keyed=#keyed'() {
        given:
        schema(keyed)
        def model = getClass('Application')
        def publicBuilder = getClass('Application_DSL$Builder')
        def factory = getClass('Application_DSL$Factory')

        when:
        def method = factory.getMethod('From', Class, Closure)
        def implementation = model.Create.class.getMethod('From', Class, Closure)

        then:
        [method, implementation].every {
            def annotation = it.parameters.last().getAnnotation(DelegatesTo)
            it.returnType == model && annotation.value() == publicBuilder && annotation.strategy() == Closure.DELEGATE_ONLY
        }
        method.genericParameterTypes[0].actualTypeArguments[0].upperBounds == [DelegatingScript]
        factory.getMethod('From', Class).returnType == model

        where:
        keyed << [false, true]
    }

    def 'overlay is absent from AsBuilder relationship Cluster Template and other source operations'() {
        given:
        schema()
        def app = getClass('Application')
        def portfolio = getClass('Portfolio_DSL$Builder')

        when:
        def excluded = [app.Create.AsBuilder().class, app.Create.Template.class, app.Template.class] +
                portfolio.declaredClasses.findAll { it.simpleName.startsWith('CollectionFactory_') || it.simpleName.startsWith('ClusterFactory_') }

        then:
        portfolio.declaredClasses.any { it.simpleName.startsWith('CollectionFactory_') }
        portfolio.declaredClasses.any { it.simpleName.startsWith('ClusterFactory_') }
        excluded.every { type -> !hasMethod(type, 'From', Class, Closure) }
        !hasMethod(portfolio, 'From', Class, Closure)
        !hasMethod(getClass('Application_DSL$Factory'), 'FromClasspath', Closure)
        !hasMethod(getClass('Application_DSL$Factory'), 'FromStruct', Map, Closure)
    }

    def 'static consumer accepts the exact Builder delegate and Model result'() {
        given:
        schema()
        createSecondaryClass('''
            package recipecontract
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            name 'recipe'
        ''', 'ApplicationRecipe.groovy')

        when:
        def client = createSecondaryClass '''
            package recipecontract
            import groovy.transform.CompileStatic
            @CompileStatic class Client {
                static Application create() {
                    Application.Create.From(ApplicationRecipe) { name 'overlay' }
                }
            }
        '''

        then:
        client.create().name == 'overlay'
    }

    def 'regular Script is rejected dynamically before construction and execution but no-overlay still works'() {
        given:
        schema()
        def script = createSecondaryClass '''
            package recipecontract
            class CompletedScript extends Script {
                static int constructed
                static int executed
                CompletedScript() { constructed++ }
                Object run() { executed++; Application.Create.With(name: 'completed') }
            }
        '''
        def app = getClass('Application')

        when:
        app.Create.From(script) { name 'overlay' }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('DelegatingScript')
        error.message.contains('Create.From(scriptClass) without an overlay')
        script.constructed == 0
        script.executed == 0

        when:
        def result = app.Create.From(script)

        then:
        result.name == 'completed'
        script.constructed == 1
        script.executed == 1
    }

    def 'static consumer rejects regular Script recipe classes'() {
        given:
        schema()

        when:
        createSecondaryClass '''
            package recipecontract
            import groovy.transform.CompileStatic
            class RegularScript extends Script { Object run() { null } }
            @CompileStatic class InvalidClient {
                static Application create() { Application.Create.From(RegularScript) { name 'invalid' } }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('DelegatingScript')
    }
}
