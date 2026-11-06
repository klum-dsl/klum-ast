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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1038')
class RootStructOverlayTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class application
    private Class environmentType

    private void schema(boolean keyed = true) {
        createSecondaryClass """
            package structoverlay
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.util.concurrent.atomic.AtomicInteger
            class Counts { static AtomicInteger initializations = new AtomicInteger() }
            @DSL class Environment {
                String name
                int applications
                int trees
                static int validations
                @PostApply void applied() { applications++ }
                @PostTree void tree() { trees++ }
                @Validate void valid() { validations++ }
            }
            @DSL class Child {
                String seen
                @PostApply void applied() { seen = 'applied:' + seen }
            }
            @DSL class Application {
                ${keyed ? "@Key @Struct.Name('application-id') String id" : ''}
                static List<String> trace = []
                @Required @Field(FieldType.LINK) Environment environment
                String title
                String derived
                Child child
                int initializerIndex = Counts.initializations.incrementAndGet()
                static int validations
                List<String> events = []
                @Builder.Method void configure(String value) {
                    title = value
                    trace << 'configure:' + title
                    events << "struct:\${environment?.name}".toString()
                }
                @PostCreate void created() {
                    trace << 'create'
                    events << "create:\$title:\${environment?.name}".toString()
                }
                @PostApply void applied() { trace << 'apply'; events << "apply:\${environment?.name}".toString() }
                @AutoLink void linked() { events << "link:\${environment?.name}".toString() }
                @Default void defaults() { derived = environment?.name }
                @PostTree void tree() { events << "tree:\${environment?.name}".toString() }
                @Validate void valid() { validations++; assert derived == environment?.name }
            }
        """
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        application = artifactLoader.loadClass('structoverlay.Application')
        environmentType = artifactLoader.loadClass('structoverlay.Environment')
    }

    def cleanup() { artifactLoader?.close() }

    def 'Struct and overlay share one root lifecycle for keyed=#keyed explicit=#explicit named=#named'() {
        given:
        schema(keyed)
        def selected = environmentType.Create.With(name: 'selected')
        def input = [configure: 'structured']
        if (keyed && !explicit) input['application-id'] = 'orders'
        def captured
        def overlay = {
            captured = delegate
            assert delegate.completedModel == null
            assert delegate.title == 'structured'
            environment selected
            title 'overlay'
        }

        when:
        def result = explicit
                ? (named ? application.Create.FromStruct('orders', input, 'orders.yaml', overlay)
                         : application.Create.FromStruct('orders', input, overlay))
                : (named ? application.Create.FromStruct(input, 'orders.yaml', overlay)
                         : application.Create.FromStruct(input, overlay))

        then:
        result.class == application
        result.title == 'overlay'
        result.environment.is(selected)
        result.derived == 'selected'
        result.initializerIndex == 1
        artifactLoader.loadClass('structoverlay.Counts').initializations.get() == 1
        captured.completedModel.is(result)
        captured.sealed
        application.validations == 1
        result.events == ['create:null:null', 'struct:null', 'apply:selected', 'link:selected', 'tree:selected']
        application.trace == ['create', 'configure:structured', 'apply']
        !keyed || result.id == 'orders'
        selected.applications == 1
        selected.trees == 1
        environmentType.validations == 1

        where:
        keyed | explicit | named
        false | false    | false
        false | false    | true
        true  | false    | false
        true  | false    | true
        true  | true     | false
        true  | true     | true
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#root-builder-overlays')
    def 'supplies a completed environment after Struct input before the root lifecycle tail'() {
        given:
        schema(false)
        def selectedEnvironment = environmentType.Create.With(name: 'production')
        def document = [title: 'orders']

        when:
        def orders = application.Create.FromStruct(document, 'orders.yaml') {
            environment selectedEnvironment
        }

        then:
        orders.title == 'orders'
        orders.environment.is(selectedEnvironment)
        orders.derived == 'production'
        application.validations == 1
    }

    def 'Templates and child callbacks precede the overlay while later actions retain authority'() {
        given:
        schema()
        def initial = environmentType.Create.With(name: 'template')
        def selected = environmentType.Create.With(name: 'overlay')
        def template = application.Create.Template.With(title: 'template', environment: initial)

        when:
        def result = TemplateManager.withTemplate(application, template) {
            application.Create.FromStruct('orders', [configure: 'structured', child: [seen: 'input']]) {
                assert delegate.child.seen == 'applied:input'
                environment selected
                title 'overlay'
                applyLater { title 'late' }
            }
        }

        then:
        result.title == 'late'
        result.child.seen == 'applied:input'
        result.events == ['create:template:template', 'struct:template', 'apply:overlay', 'link:overlay', 'tree:overlay']
        result.environment.is(selected)
        [initial, selected]*.applications == [1, 1]
        [initial, selected]*.trees == [1, 1]
        environmentType.validations == 2
    }

    def 'a reached Struct failure preserves earlier effects skips overlay and cleans up: #bad'() {
        given:
        schema()
        def selected = environmentType.Create.With(name: 'selected')
        boolean overlayRan = false

        when:
        application.Create.FromStruct('orders', [configure: 'first'] + bad, 'orders.yaml') {
            overlayRan = true
            environment selected
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('orders.yaml')
        failure.message.contains(pointer)
        application.trace == ['create', 'configure:first']
        !overlayRan

        when:
        def next = application.Create.FromStruct('next', [title: 'next']) { environment selected }

        then:
        next.id == 'next'
        next.environment.is(selected)
        application.trace == ['create', 'configure:first', 'create', 'apply']

        where:
        bad                         | pointer
        [unsupported: true]         | '/unsupported'
        [title: 42]                 | '/title'
        ['application-id': 'orders'] | '/application-id'
    }

    def 'missing identity and invalid source reject before allocation and cannot be repaired by overlay'() {
        given:
        schema()
        boolean overlayRan = false

        when:
        application.Create.FromStruct([configure: 'first'], 'orders.yaml') { overlayRan = true }

        then:
        def missing = thrown(KlumModelException)
        missing.message.contains('required construction key')
        application.trace.empty
        !overlayRan

        when:
        application.Create.FromStruct('orders', [:], '', { overlayRan = true })

        then:
        thrown(KlumModelException)
        application.trace.empty
        !overlayRan

        when:
        application.Create.FromStruct((String) null, [:], 'orders.yaml', { overlayRan = true })

        then:
        thrown(KlumModelException)
        application.trace.empty
        !overlayRan

        when:
        application.Create.FromStruct((Map) null, 'orders.yaml', { overlayRan = true })

        then:
        thrown(KlumModelException)
        application.trace.empty
        !overlayRan
    }

    def 'overlay and verification failures invalidate Builders and leave subsequent roots usable: #stage'() {
        given:
        schema()
        def selected = environmentType.Create.With(name: 'selected')
        def original = new IllegalArgumentException('overlay failure')
        def captured

        when:
        application.Create.FromStruct('orders', [configure: 'structured']) {
            captured = delegate
            if (stage == 'overlay') throw original
        }

        then:
        def failure = thrown(stage == 'overlay' ? IllegalArgumentException : KlumValidationException)
        stage != 'overlay' || failure.is(original)

        when:
        captured.title('stale')

        then:
        thrown(KlumModelException)

        when:
        def result = application.Create.FromStruct('next', [:]) { environment selected }

        then:
        result.environment.is(selected)

        where:
        stage << ['overlay', 'verification']
    }

    def 'reused overlay retains its original delegate and repeated roots keep distinct LINK identities'() {
        given:
        schema()
        def selected = environmentType.Create.With(name: 'first')
        def overlay = { environment selected }
        def originalDelegate = overlay.delegate
        def originalStrategy = overlay.resolveStrategy

        when:
        def first = application.Create.FromStruct('first', [:], overlay)
        selected = environmentType.Create.With(name: 'second')
        def second = application.Create.FromStruct(['application-id': 'second'], overlay)

        then:
        first.environment.name == 'first'
        second.environment.is(selected)
        !first.is(second)
        !first.environment.is(second.environment)
        overlay.delegate.is(originalDelegate)
        overlay.resolveStrategy == originalStrategy
        first.environment.applications == 1
        second.environment.applications == 1
    }

    def 'null overlay and no-overlay paths retain ordinary Template and failure behavior'() {
        given:
        schema()
        def selected = environmentType.Create.With(name: 'template')
        def template = application.Create.Template.With(environment: selected)

        when:
        application.Create.FromStruct('missing', [:], (Closure<?>) null)

        then:
        thrown(KlumValidationException)

        when:
        def results = TemplateManager.withTemplate(application, template) {
            [application.Create.FromStruct(['application-id': 'document']),
             application.Create.FromStruct('body', [:]),
             application.Create.FromStruct(['application-id': 'null-document'], (Closure<?>) null),
             application.Create.FromStruct('null-body', [:], (Closure<?>) null)]
        }

        then:
        results*.id == ['document', 'body', 'null-document', 'null-body']
        results.every { it.environment.is(selected) }
        results.every { it.events.count { event -> event.startsWith('apply:') } == 1 }
        selected.applications == 1
    }

    def 'Factory-selected concrete default type remains authoritative'() {
        given:
        createClass '''
            package structdefault
            @DSL(defaultImpl = ConcreteApplication)
            abstract class Application { String title }
            @DSL class ConcreteApplication extends Application { String detail }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def declared = artifactLoader.loadClass('structdefault.Application')
        def concrete = artifactLoader.loadClass('structdefault.ConcreteApplication')

        when:
        def result = declared.Create.FromStruct([title: 'structured']) { title 'overlay' }

        then:
        result.class == concrete
        result.title == 'overlay'
        declared.Create.modelType == concrete
    }
}
