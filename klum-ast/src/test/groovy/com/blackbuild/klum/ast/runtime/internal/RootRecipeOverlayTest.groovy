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
import groovy.util.DelegatingScript
import spock.lang.Issue

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Issue('829')
class RootRecipeOverlayTest extends AbstractDSLSpec {
    private void schema(boolean keyed = true) {
        createClass """
            package rootrecipe
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.util.concurrent.atomic.AtomicInteger
            @DSL class Environment {
                String name
                int lifecycleCount
                int treeCount
                static int validationCount
                @PostApply void applied() { lifecycleCount++ }
                @PostTree void tree() { treeCount++ }
                @Validate void valid() { validationCount++ }
            }
            class RecipeContext {
                static Environment environment
                static AtomicInteger initializations = new AtomicInteger()
            }
            @DSL class Child {
                String seen
                @PostApply void applied() { seen = "applied:\$seen" }
            }
            @DSL class Application {
                ${keyed ? '@Key String id' : ''}
                @Required @Field(FieldType.LINK) Environment environment
                Child child
                String name
                String derived
                int initializerIndex = RecipeContext.initializations.incrementAndGet()
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
            import groovy.transform.BaseScript
            import groovy.util.DelegatingScript
            @BaseScript DelegatingScript base
        ''' + body, 'ApplicationRecipe.groovy')
    }

    def 'two inputs share one preparation and LINK retains identity for keyed=#keyed'() {
        given:
        schema(keyed)
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()

        def captured

        when:
        def result = app.Create.From(script, { captured = delegate; environment env; name 'overlay' })

        then:
        result.environment.is(env)
        captured.completedModel.is(result)
        captured.sealed
        result.initializerIndex == 1
        getClass('rootrecipe.RecipeContext').initializations.get() == 1
        env.lifecycleCount == 1
        env.treeCount == 1
        getClass('rootrecipe.Environment').validationCount == 1
        result.name == 'overlay'
        result.derived == 'selected'
        result.events == ['create:null', 'apply:selected', 'link:selected', 'default:selected', 'tree:selected']
        !keyed || result.id == 'ApplicationRecipe'

        where:
        keyed << [false, true]
    }

    def 'recipe child callbacks run before the overlay and collection adders append'() {
        given:
        schema()
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe('''
            def root = getDelegate()
            name 'recipe'
            events 'recipe'
            child { seen root.environment?.name ?: 'missing' }
        ''')

        when:
        def result = getClass('rootrecipe.Application').Create.From(script) {
            environment env
            name 'overlay'
            events 'overlay'
        }

        then:
        result.child.seen == 'applied:missing'
        result.name == 'overlay'
        result.events.take(3) == ['create:null', 'recipe', 'overlay']
    }

    def 'configuration failure and rejected nested root leave next invocation usable'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()
        def original = new IllegalArgumentException('overlay failure')

        when:
        app.Create.From(script, { throw original })

        then:
        def failure = thrown(IllegalArgumentException)
        failure.is(original)

        when:
        def result = app.Create.From(script, {
            try {
                app.Create.From(script) { throw new AssertionError('Nested overlay ran') }
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
            app.Create.From(script, { environment selected })
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
        def result = getClass('rootrecipe.Application').Create.From(script,
                { environment env; name 'overlay' })

        then:
        result.name == 'late'
        result.environment.is(env)
    }

    def 'cloned shared overlay keeps concurrent delegates invocation-local'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def environments = ['eu', 'us'].collect { getClass('rootrecipe.Environment').Create.With(name: it) }
        def byThread = new ConcurrentHashMap<Thread, Object>()
        def script = recipe()
        def entered = new CountDownLatch(2)
        def shared = {
            entered.countDown()
            assert entered.await(10, TimeUnit.SECONDS)
            environment byThread[Thread.currentThread()]
        }
        def initialStrategy = shared.resolveStrategy
        def initialDelegate = shared.delegate
        def pool = Executors.newFixedThreadPool(2)

        when:
        def pending = (0..1).collect { n -> pool.submit({
            byThread[Thread.currentThread()] = environments[n]
            app.Create.From(script, shared)
        } as Callable) }
        def results = pending.collect { it.get(20, TimeUnit.SECONDS) }

        then:
        results*.id == ['ApplicationRecipe', 'ApplicationRecipe']
        results.indices.every { results[it].environment.is(environments[it]) }
        !results[0].is(results[1])
        shared.delegate.is(initialDelegate)
        shared.resolveStrategy == initialStrategy
        environments*.lifecycleCount == [1, 1]
        environments*.treeCount == [1, 1]

        cleanup:
        pool.shutdownNow()
    }

    def 'recipe LINK is overwritten by the overlay and active Templates precede both inputs'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def environmentType = getClass('rootrecipe.Environment')
        def initial = environmentType.Create.With(name: 'recipe')
        def selected = environmentType.Create.With(name: 'overlay')
        def script = recipe('''
            environment RecipeContext.environment
            events "recipe:$name".toString()
            name 'recipe'
        ''')
        getClass('rootrecipe.RecipeContext').environment = initial
        def template = app.Create.Template.With(name: 'template')

        when:
        def result = TemplateManager.withTemplate(app, template) {
            app.Create.From(script) { environment selected; name 'overlay' }
        }

        then:
        result.environment.is(selected)
        result.name == 'overlay'
        result.events.take(2) == ['create:null', 'recipe:template']
        [initial, selected]*.lifecycleCount == [1, 1]
        [initial, selected]*.treeCount == [1, 1]
        environmentType.validationCount == 2
    }

    def 'failure in #stage invalidates the captured Builder and the next root remains usable'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe()
        def captured

        when:
        app.Create.From(script) {
            captured = delegate
            if (stage == 'overlay') throw new IllegalArgumentException('overlay failure')
            if (stage != 'verification') environment env
        }

        then:
        thrown(stage == 'verification' ? KlumValidationException : IllegalArgumentException)

        when:
        captured.name('stale')

        then:
        thrown(KlumModelException)

        when:
        def result = app.Create.From(recipe()) { environment env }

        then:
        result.environment.is(env)

        where:
        stage << ['overlay', 'verification']
    }

    def 'recipe failure skips the overlay and leaves the next invocation usable'() {
        given:
        schema()
        def app = getClass('rootrecipe.Application')
        def env = getClass('rootrecipe.Environment').Create.With(name: 'selected')
        def script = recipe("throw new IllegalArgumentException('recipe failure')")
        boolean overlayRan = false

        when:
        app.Create.From(script) { overlayRan = true }

        then:
        thrown(IllegalArgumentException)
        !overlayRan

        when:
        def result = app.Create.From(recipe()) { environment env }

        then:
        result.environment.is(env)
    }

}
