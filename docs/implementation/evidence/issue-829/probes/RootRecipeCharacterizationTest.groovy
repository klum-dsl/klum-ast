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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import groovy.transform.CompileStatic
import groovy.util.DelegatingScript
import spock.lang.Issue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/** Local characterization only; no proposed public overload is installed. */
@Issue('829')
class RootRecipeCharacterizationTest extends AbstractDSLSpec {
    private void schema(boolean keyed = true) {
        createClass """
            package rootrecipe
            import com.blackbuild.klum.ast.layer3.AutoLink
            @DSL class Environment {
                String name
                int lifecycleCount
                @PostApply void applied() { lifecycleCount++ }
            }
            @DSL class Child { String seen }
            @DSL class Application {
                ${keyed ? '@Key String id' : ''}
                @Required @Field(FieldType.LINK) Environment environment
                Child child
                String name
                String derived
                List<String> events = []
                @PostCreate void created() { events << "create:\${environment?.name}".toString() }
                @PostApply void applied() { events << "apply:\${environment?.name}".toString() }
                @AutoLink void linked() { events << "link:\${environment?.name}".toString() }
                @Default void defaults() { derived = environment?.name; events << "default:\$derived".toString() }
                @PostTree void tree() { events << "tree:\${environment?.name}".toString() }
                @Validate void valid() { assert environment != null; assert derived == environment.name }
            }
        """
    }

    private Class recipe(String body = 'name "recipe"') {
        createSecondaryClass('''
            package rootrecipe
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
        ''' + body, 'ApplicationRecipe.groovy')
    }

    @CompileStatic
    private static Object runInputs(Class type, String key, Class scriptType, Closure overlay, boolean before = false) {
        Consumer<InternalKlumBuilder<Object>> configure = { InternalKlumBuilder<Object> root ->
            if (before) root.applyOnly(null, overlay)
            BuilderDispatchSupport.run((DelegatingScript) scriptType.getDeclaredConstructor().newInstance(), root)
            if (!before) root.applyOnly(null, overlay)
        } as Consumer<InternalKlumBuilder<Object>>
        FactoryHelper.createWithConfiguration((Class<Object>) type, key, configure)
    }

    def 'two inputs share one preparation and LINK retains identity for keyed=#keyed'() {
        given:
        schema(keyed)
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()

        when:
        def result = runInputs(app, keyed ? script.simpleName : null, script, { environment env; name 'overlay' })

        then:
        result.environment.is(env)
        env.lifecycleCount == 1
        result.name == 'overlay'
        result.derived == 'selected'
        result.events == ['create:null', 'apply:selected', 'link:selected', 'default:selected', 'tree:selected']
        !keyed || result.id == 'ApplicationRecipe'

        where:
        keyed << [false, true]
    }

    def 'ordering changes recipe-time child context visibility and scalar conflict: before=#before'() {
        given:
        schema()
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe('''
            def root = getDelegate()
            name 'recipe'
            child { seen root.environment?.name ?: 'missing' }
        ''')

        when:
        def result = runInputs(getClass('rootrecipe.Application'), script.simpleName, script,
                { environment env; name 'overlay' }, before)

        then:
        result.child.seen == (before ? 'selected' : 'missing')
        result.name == (before ? 'recipe' : 'overlay')

        where:
        before << [false, true]
    }

    def 'configuration failure and rejected nested root leave next invocation usable'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()
        def original = new IllegalArgumentException('overlay failure')

        when:
        runInputs(app, script.simpleName, script, { throw original })

        then:
        def failure = thrown(IllegalArgumentException)
        failure.is(original)

        when:
        def result = runInputs(app, script.simpleName, script, {
            try {
                app.Create.One('nested')
                assert false: 'nested root should fail'
            } catch (KlumModelException expected) {
                assert expected.message.contains('independent DSL Object factory')
            }
            environment env
        })

        then:
        result.environment.is(env)
        result.events.count { it.startsWith('create:') } == 1
    }

    def 'unbound ordinary recipe fails validation and repeated bound roots retain distinct external targets'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def script = recipe()
        def environments = ['eu', 'us'].collect {
            getClass('rootrecipe.Environment').Create.With(name: it)
        }

        when:
        app.Create.From(script)

        then:
        thrown(KlumValidationException)

        when:
        def results = environments.collect { selected ->
            runInputs(app, script.simpleName, script, { environment selected })
        }

        then:
        results[0].environment.is(environments[0])
        results[1].environment.is(environments[1])
        results*.derived == ['eu', 'us']
        results*.id == ['ApplicationRecipe', 'ApplicationRecipe']
        !results[0].is(results[1])
        environments*.lifecycleCount == [1, 1]
    }

    def 'later recipe actions retain normal authority after an overlay'() {
        given:
        schema()
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe('name "recipe"; applyLater { name "late" }')

        when:
        def result = runInputs(getClass('rootrecipe.Application'), script.simpleName, script,
                { environment env; name 'overlay' })

        then:
        result.name == 'late'
        result.environment.is(env)
    }

    def 'cloned shared overlay keeps concurrent delegates invocation-local'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()
        def entered = new CountDownLatch(2)
        def shared = {
            entered.countDown()
            assert entered.await(10, TimeUnit.SECONDS)
            environment env
        }
        def initialDelegate = shared.delegate
        def pool = Executors.newFixedThreadPool(2)

        when:
        def pending = (1..2).collect { n -> pool.submit({
            runInputs(app, "root$n".toString(), script, (Closure) shared.clone())
        } as java.util.concurrent.Callable) }
        def results = pending.collect { it.get(20, TimeUnit.SECONDS) }

        then:
        results*.id == ['root1', 'root2']
        results.every { it.environment.is(env) }
        !results[0].is(results[1])
        shared.delegate.is(initialDelegate)
        env.lifecycleCount == 1

        cleanup:
        pool.shutdownNow()
    }
}
