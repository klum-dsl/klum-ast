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
import spock.lang.Issue

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Issue('829')
class RootClasspathRecipeOverlayTest extends AbstractDSLSpec {
    private static final String MARKER = 'META-INF/klum-model/classpathrecipe.Application.properties'

    private void schema(boolean keyed = true) {
        createClass """
            package classpathrecipe
            @DSL class Environment {
                String region
                int applications
                @PostApply void applied() { applications++ }
            }
            class RecipeState {
                static Object captured
                static RuntimeException failure
                static int constructed
            }
            @DSL class Application {
                ${keyed ? '@Key String id' : ''}
                @Required @Field(FieldType.LINK) Environment environment
                String name
                String region
                List<String> events = []
                @PostCreate void created() { events << "create:\${environment?.region}".toString() }
                @PostApply void applied() { events << "apply:\${environment?.region}".toString() }
                @Default void defaults() { region = environment.region }
                @Validate void valid() { assert region == environment.region }
            }
        """
    }

    private Class recipe(String body = "name 'recipe'") {
        createSecondaryClass('''
            package classpathrecipe
            import groovy.transform.BaseScript
            import groovy.util.DelegatingScript
            @BaseScript DelegatingScript base
            RecipeState.constructed++
            RecipeState.captured = getDelegate()
        ''' + body, 'SelectedRecipe.groovy')
    }

    private File marker(String content = 'model-class=classpathrecipe.SelectedRecipe') {
        def root = tempFolder.newFolder()
        def file = new File(root, MARKER)
        file.parentFile.mkdirs()
        file.text = content
        root
    }

    private Object construct(boolean explicitLoader, ClassLoader sourceLoader, Closure overlay) {
        def factory = getClass('Application').Create
        explicitLoader ? factory.FromClasspath(sourceLoader, overlay) : factory.FromClasspath(overlay)
    }

    def 'classpath recipe and overlay share one root with Templates and LINK identity for explicit=#explicit keyed=#keyed'() {
        given:
        schema(keyed)
        recipe('events "recipe:$name".toString(); name "recipe"')
        def source = marker()
        def selected = getClass('Environment').Create.With(region: 'eu')
        def factory = getClass('Application').Create
        def template = factory.Template.With(name: 'template')
        def modelLoader = new URLClassLoader([source.toURI().toURL()] as URL[], loader)
        if (!explicit) loader.addURL(source.toURI().toURL())
        def overlay = { environment selected; name 'overlay'; events 'overlay' }
        def originalDelegate = overlay.delegate

        when:
        def result = TemplateManager.withTemplate(getClass('Application'), template) {
            construct(explicit, modelLoader, overlay)
        }

        then:
        result.environment.is(selected)
        result.name == 'overlay'
        result.region == 'eu'
        result.events == ['create:null', 'recipe:template', 'overlay', 'apply:eu']
        !keyed || result.id == 'SelectedRecipe'
        getClass('RecipeState').constructed == 1
        getClass('RecipeState').captured.completedModel.is(result)
        selected.applications == 1
        overlay.delegate.is(originalDelegate)
        overlay.resolveStrategy == Closure.OWNER_FIRST

        cleanup:
        modelLoader?.close()

        where:
        explicit | keyed
        false    | false
        true     | false
        false    | true
        true     | true
    }

    def 'marker lookup uses the effective default Factory type'() {
        given:
        createClass '''
            package classpathrecipe
            @DSL(defaultImpl = ConcreteApplication) abstract class Application { String name }
            @DSL class ConcreteApplication extends Application { String detail }
        '''
        createSecondaryClass('''
            package classpathrecipe
            import groovy.transform.BaseScript
            import groovy.util.DelegatingScript
            @BaseScript DelegatingScript base
            name 'recipe'
            detail 'selected concrete type'
        ''', 'SelectedRecipe.groovy')
        def source = tempFolder.newFolder()
        def effectiveMarker = new File(source, 'META-INF/klum-model/classpathrecipe.ConcreteApplication.properties')
        effectiveMarker.parentFile.mkdirs()
        effectiveMarker.text = 'model-class=classpathrecipe.SelectedRecipe'
        loader.addURL(source.toURI().toURL())

        when:
        def result = getClass('Application').Create.FromClasspath { name 'overlay' }

        then:
        result.class == getClass('ConcreteApplication')
        result.name == 'overlay'
        result.detail == 'selected concrete type'
    }

    def 'explicit loader selects both marker and script instead of the context loader'() {
        given:
        schema()
        recipe()
        loader.addURL(marker('model-class=wrong.ContextRecipe').toURI().toURL())
        def seen = []
        def source = marker()
        def modelLoader = new URLClassLoader([source.toURI().toURL()] as URL[], loader) {
            @Override URL getResource(String name) {
                findResource(name) ?: super.getResource(name)
            }
            @Override Class<?> loadClass(String name) {
                seen << name
                super.loadClass(name)
            }
        }
        def selected = getClass('Environment').Create.With(region: 'eu')

        when:
        def result = getClass('Application').Create.FromClasspath(modelLoader) { environment selected }

        then:
        result.id == 'SelectedRecipe'
        result.environment.is(selected)
        seen.contains('classpathrecipe.SelectedRecipe')
        !seen.contains('wrong.ContextRecipe')

        cleanup:
        modelLoader?.close()
    }

    def 'discovery failure retains #scenario diagnostics for explicit=#explicit'() {
        given:
        schema()
        def source = scenario == 'missing marker' ? tempFolder.newFolder() : marker(content)
        def modelLoader = new URLClassLoader([source.toURI().toURL()] as URL[], loader)
        if (!explicit) loader.addURL(source.toURI().toURL())
        boolean overlayRan = false

        when:
        construct(explicit, modelLoader, { overlayRan = true })

        then:
        def error = thrown(KlumModelException)
        error.message.contains(MARKER)
        error.message.contains(diagnostic)
        (error.cause instanceof ClassNotFoundException) == (scenario == 'missing class')
        !overlayRan

        cleanup:
        modelLoader?.close()

        where:
        scenario         | content                                 | diagnostic                  | explicit
        'missing marker' | ''                                      | 'not found in classpath'     | false
        'missing marker' | ''                                      | 'not found in classpath'     | true
        'missing entry'  | 'other=value'                           | "No entry 'model-class'"    | false
        'missing entry'  | 'other=value'                           | "No entry 'model-class'"    | true
        'missing class'  | 'model-class=missing.UnavailableRecipe' | 'missing.UnavailableRecipe' | false
        'missing class'  | 'model-class=missing.UnavailableRecipe' | 'missing.UnavailableRecipe' | true
    }

    def 'marker IO and class resolution preserve original causes'() {
        given:
        schema()
        def original = new IOException('marker read failed')
        def modelLoader = new ClassLoader(loader) {
            @Override InputStream getResourceAsStream(String name) {
                assert name == MARKER
                new InputStream() {
                    @Override int read() { throw original }
                }
            }
        }

        when:
        getClass('Application').Create.FromClasspath(modelLoader) { }

        then:
        def error = thrown(KlumModelException)
        error.message.startsWith('Error while reading marker properties.')
        error.cause.is(original)

        when:
        def classFailure = new ClassNotFoundException('selected loader failure')
        def failingLoader = new ClassLoader(loader) {
            @Override InputStream getResourceAsStream(String name) {
                new ByteArrayInputStream('model-class=missing.Recipe'.bytes)
            }
            @Override Class<?> loadClass(String name) { throw classFailure }
        }
        getClass('Application').Create.FromClasspath(failingLoader) { }

        then:
        def resolution = thrown(KlumModelException)
        resolution.message.contains(MARKER)
        resolution.message.contains('missing.Recipe')
        resolution.cause.is(classFailure)
    }

    def 'marker #kind is rejected before construction or execution and regular no-overlay remains usable for explicit=#explicit'() {
        given:
        schema()
        def input = createSecondaryClass """
            package classpathrecipe
            class MarkerInput ${kind == 'regular Script' ? 'extends Script' : ''} {
                static int constructed
                static int executed
                MarkerInput() { constructed++ }
                static Object completed
                Object run() { executed++; completed }
            }
        """
        def selected = getClass('Environment').Create.With(region: 'eu')
        input.completed = getClass('Application').Create.With('program-key') { environment selected; name 'completed' }
        def source = marker('model-class=classpathrecipe.MarkerInput')
        loader.addURL(source.toURI().toURL())

        when:
        construct(explicit, loader, { throw new AssertionError('overlay must not run') })

        then:
        def error = thrown(KlumModelException)
        error.message.startsWith('Could not read model from classpathrecipe.MarkerInput')
        error.cause instanceof KlumModelException
        error.cause.message.contains('DelegatingScript')
        error.cause.message.contains('without an overlay')
        input.constructed == 0
        input.executed == 0

        when:
        def completed = kind == 'regular Script' ?
                (explicit ? getClass('Application').Create.FromClasspath(loader) : getClass('Application').Create.FromClasspath()) : null

        then:
        kind != 'regular Script' || completed.is(input.completed)
        input.constructed == (kind == 'regular Script' ? 1 : 0)
        input.executed == (kind == 'regular Script' ? 1 : 0)

        where:
        kind             | explicit
        'regular Script' | false
        'regular Script' | true
        'non-Script'     | false
        'non-Script'     | true
    }

    def 'script or overlay failure keeps source and original cause and aborts the captured Builder for stage=#stage'() {
        given:
        schema()
        def failure = new IllegalArgumentException("$stage failure")
        getClass('RecipeState').failure = failure
        recipe(stage == 'script' ? 'throw RecipeState.failure' : "name 'recipe'")
        loader.addURL(marker().toURI().toURL())
        def selected = getClass('Environment').Create.With(region: 'eu')

        when:
        getClass('Application').Create.FromClasspath {
            assert stage != 'script': 'overlay must not run after script failure'
            throw failure
        }

        then:
        def error = thrown(KlumModelException)
        error.message.startsWith('Could not read model from classpathrecipe.SelectedRecipe')
        error.cause.is(failure)

        when:
        getClass('RecipeState').captured.name('stale')

        then:
        thrown(KlumModelException)

        when:
        recipe()
        def recovered = getClass('Application').Create.FromClasspath { environment selected }

        then:
        recovered.environment.is(selected)

        where:
        stage << ['script', 'overlay']
    }

    def 'script derived key cannot be replaced by the #stage input'() {
        given:
        schema()
        recipe(stage == 'script' ? "id 'replacement'" : "name 'recipe'")
        loader.addURL(marker().toURI().toURL())

        when:
        getClass('Application').Create.FromClasspath { id 'replacement' }

        then:
        def error = thrown(KlumModelException)
        error.cause instanceof MissingMethodException

        where:
        stage << ['script', 'overlay']
    }

    def 'repeated and concurrent classpath roots retain distinct contexts with one reusable overlay'() {
        given:
        schema()
        recipe()
        loader.addURL(marker().toURI().toURL())
        def selected = ['eu', 'us'].collect { getClass('Environment').Create.With(region: it) }
        def byThread = new ConcurrentHashMap<Thread, Object>()
        def entered = new CountDownLatch(2)
        def overlay = {
            entered.countDown()
            assert entered.await(10, TimeUnit.SECONDS)
            environment byThread[Thread.currentThread()]
        }
        def initialDelegate = overlay.delegate
        def modelLoader = loader
        def factory = getClass('Application').Create
        def pool = Executors.newFixedThreadPool(2)

        when:
        def pending = (0..1).collect { index -> pool.submit({
            byThread[Thread.currentThread()] = selected[index]
            factory.FromClasspath(modelLoader, overlay)
        } as Callable) }
        def concurrent = pending.collect { it.get(20, TimeUnit.SECONDS) }
        def repeated = selected.collect { environment -> factory.FromClasspath { delegate.environment environment } }

        then:
        concurrent.indices.every { concurrent[it].environment.is(selected[it]) && repeated[it].environment.is(selected[it]) }
        concurrent*.region == ['eu', 'us']
        repeated*.region == ['eu', 'us']
        concurrent*.id == ['SelectedRecipe', 'SelectedRecipe']
        !concurrent[0].is(concurrent[1])
        overlay.delegate.is(initialDelegate)
        overlay.resolveStrategy == Closure.OWNER_FIRST
        selected*.applications == [1, 1]

        cleanup:
        pool.shutdownNow()
    }
}
