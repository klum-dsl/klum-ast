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

import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumException
import com.blackbuild.klum.ast.runtime.KlumPhase
import com.blackbuild.klum.ast.runtime.PhaseAction
import com.blackbuild.klum.ast.runtime.internal.process.AbstractPhaseAction
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Issue

@Issue('856')
class BuilderRelationshipLifetimeTest extends AbstractDSLSpec {
    def 'early acquired Structure guards each request then exposes the inherited owning Binding at AUTO_LINK'() {
        given:
        createNonDslClass '''
            package metadata
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Binding { String value() }
            @DSL class Consumer {
                static Object observed
                @AutoLink void inspect() {
                    observed = KlumBuilderSupport.of(this).structure.owningRelationship.orElseThrow()
                }
            }
            @DSL class ApplicationBase { @Binding('primary') Consumer consumer }
            @DSL class Application extends ApplicationBase {}
        '''
        def consumerType = getClass('Consumer')
        def applicationType = getClass('Application')
        def bindingType = getClass('Binding')
        def view
        List<Throwable> earlyErrors = []

        when:
        instance = applicationType.Create.With {
            def receiver = consumer {}
            view = KlumBuilderSupport.of(receiver).structure
            ['relationship', 'annotation'].each { request ->
                try {
                    if (request == 'relationship') view.owningRelationship
                    else view.getOwningRelationshipAnnotation(bindingType)
                } catch (Throwable error) { earlyErrors << error }
            }
        }

        then:
        earlyErrors.size() == 2
        earlyErrors.every { it instanceof KlumModelException && it.message.contains('after OWNER(15)') && it.message.contains('metadata.Consumer') }
        consumerType.observed.declaringClass.is(getClass('ApplicationBase'))
        consumerType.observed.name == 'consumer'
        consumerType.observed.getAnnotation(bindingType).orElseThrow().value() == 'primary'
        consumerType.observed == KlumObjectSupport.of(instance.consumer).structure.owningRelationship.orElseThrow()

        when:
        view.owningRelationship

        then:
        thrown(KlumModelException)

        when:
        view.getOwningRelationshipAnnotation(bindingType)

        then:
        thrown(KlumModelException)
    }
    def 'both ownership requests enforce phase boundaries on roots children and missing annotations'() {
        given:
        schema()
        List<Throwable> failures = []
        List<String> successes = []
        def views
        def receiver
        def consumerType = getClass('Consumer')
        def annotationType = getClass('Binding')
        [1, 10, 14, 15, 16, 20, 25, 30, 41, 50, 80, 100].each { number ->
            observe(number) {
                views.each { view ->
                    [ { view.owningRelationship }, { view.getOwningRelationshipAnnotation(annotationType) } ].each { request ->
                        if (number <= 15) failures << failureOf(request)
                        else { request.call(); successes << "phase-$number" }
                    }
                }
                if (number == 16) PhaseDriver.postPhaseApply {
                    assert PhaseDriver.instance.context.phase == null
                    assert views[1].owningRelationship.orElseThrow().name == 'consumer'
                }
            }
        }
        observe(40, true) {
            assert !receiver.sealed
            assert views[1].owningRelationship.orElseThrow().name == 'consumer'
            assert views[1].getOwningRelationshipAnnotation(annotationType).orElseThrow().value() == 'primary'
        }
        observe(40) {
            assert receiver.sealed
            assert views[1].owningRelationship.orElseThrow().name == 'consumer'
            assert views[1].getOwningRelationshipAnnotation(annotationType).orElseThrow().value() == 'primary'
        }

        when:
        instance = create('Application') {
            def rootView = KlumBuilderSupport.of(delegate).structure
            receiver = consumer {}
            def childView = KlumBuilderSupport.of(receiver).structure
            def bare = consumerType.Create.AsBuilder().One()
            def bareView = KlumBuilderSupport.of(bare).structure
            def unmarkedView = KlumBuilderSupport.of(unmarked {}).structure
            views = [rootView, childView, bareView, unmarkedView]
        }

        then:
        failures.size() == 32
        failures.every { it instanceof KlumModelException && it.message.contains('after OWNER(15)') }
        failures.count { it.message.startsWith('getOwningRelationship requires') } == 16
        failures.count { it.message.startsWith('getOwningRelationshipAnnotation requires') } == 16
        successes.size() == 64
        views.every { view -> failureOf { view.owningRelationship } instanceof KlumModelException }
        views.every { view -> failureOf { view.getOwningRelationshipAnnotation(annotationType) } instanceof KlumModelException }
        KlumObjectSupport.of(instance.unmarked).structure.getOwningRelationshipAnnotation(annotationType).empty
    }

    def 'successful views reject foreign threads subsequent sessions and lifecycle aborts'() {
        given:
        schema()
        def view
        def descriptor
        def annotationType = getClass('Binding')
        AtomicReference<Throwable> foreign = new AtomicReference<>()
        observe(abortPhase) {
            descriptor = view.owningRelationship.orElseThrow()
            Thread thread = new Thread({ foreign.set(failureOf { view.owningRelationship }) } as Runnable)
            thread.start()
            thread.join(5000)
            assert !thread.alive
            assert view.getOwningRelationshipAnnotation(annotationType).present
            if (abort) throw new IllegalStateException('RM-1 abort')
        }

        when:
        def error = failureOf {
            create('Application') { view = KlumBuilderSupport.of(consumer {}).structure }
        }

        then:
        abort ? error instanceof KlumException : error == null
        foreign.get() instanceof KlumModelException
        failureOf { view.owningRelationship } instanceof KlumModelException
        failureOf { view.getOwningRelationshipAnnotation(annotationType) } instanceof KlumModelException
        descriptor.name == 'consumer'
        descriptor.getAnnotation(annotationType).orElseThrow().value() == 'primary'

        when:
        create('Application') {
            assert BuilderRelationshipLifetimeTest.failureOf { view.owningRelationship } instanceof KlumModelException
            assert BuilderRelationshipLifetimeTest.failureOf { view.getOwningRelationshipAnnotation(annotationType) } instanceof KlumModelException
        }

        then:
        noExceptionThrown()

        where:
        abortPhase | abort
        16         | false
        16         | true
        50         | true
    }

    def 'sealed LINK adapters read original target declarations and external root absence'() {
        given:
        schema()
        def targetGraph = create('Application') { consumer {} }
        def rootTarget = create('Consumer')
        def wrapper
        def rootWrapper
        def view
        def rootView
        def annotationType = getClass('Binding')
        List<Object> declarations = []
        [16, 40, 100].each { number -> observe(number) {
            assert wrapper.sealed && rootWrapper.sealed
            declarations << view.owningRelationship.orElseThrow()
            assert view.getOwningRelationshipAnnotation(annotationType).orElseThrow().value() == 'primary'
            assert rootView.owningRelationship.empty
            assert rootView.getOwningRelationshipAnnotation(annotationType).empty
        } }

        when:
        instance = create('Application') {
            linked targetGraph.consumer
            linkedRoot rootTarget
            wrapper = delegate.linked
            rootWrapper = delegate.linkedRoot
            view = KlumBuilderSupport.of(wrapper).structure
            rootView = KlumBuilderSupport.of(rootWrapper).structure
        }

        then:
        instance.linked.is(targetGraph.consumer)
        instance.linkedRoot.is(rootTarget)
        declarations.every { it == KlumObjectSupport.of(targetGraph.consumer).structure.owningRelationship.orElseThrow() }
        KlumObjectSupport.of(instance.linked).structure.owningRelationship.orElseThrow().name == 'consumer'
        failureOf { view.owningRelationship } instanceof KlumModelException
    }

    def 'invalid receivers null annotation and Template definition preserve the facade boundary'() {
        given:
        schema()
        def annotationType = getClass('Binding')
        def templateView
        def template = getClass('Application').Template.Create {
            templateView = KlumBuilderSupport.of(delegate).structure
        }

        when:
        def templateError = failureOf { templateView.owningRelationship }

        then:
        templateError instanceof KlumModelException
        failureOf { KlumBuilderSupport.of(null) }.with { it instanceof NullPointerException && it.message == 'builder' }
        failureOf { KlumBuilderSupport.of(new KlumBuilder<Object>() {}) } instanceof KlumModelException
        failureOf { templateView.owningRelationship } instanceof KlumModelException
        failureOf { templateView.getOwningRelationshipAnnotation(annotationType) } instanceof KlumModelException
        failureOf { templateView.getOwningRelationshipAnnotation(null) }.with { it instanceof NullPointerException && it.message == 'annotationType' }
        failureOf { KlumObjectSupport.of(template) } instanceof KlumException
    }


    def 'a child attached after OWNER sees the accepted inherited declaration immediately'() {
        given:
        schema()
        def root
        def observed
        def annotationType = getClass('Binding')
        observe(16) {
            def child = root.consumer {}
            def view = KlumBuilderSupport.of(child).structure
            observed = view.owningRelationship.orElseThrow()
            assert view.getOwningRelationshipAnnotation(annotationType).orElseThrow().value() == 'primary'
        }

        when:
        instance = create('Application') { root = delegate }

        then:
        observed.declaringClass.is(getClass('ApplicationBase'))
        observed == KlumObjectSupport.of(instance.consumer).structure.owningRelationship.orElseThrow()
    }

    private void schema() {
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Binding { String value() }
            @DSL class Consumer {}
            @DSL class ApplicationBase {
                @Binding('primary') Consumer consumer
                Consumer unmarked
                @Field(FieldType.LINK) Consumer linked
                @Field(FieldType.LINK) Consumer linkedRoot
            }
            @DSL class Application extends ApplicationBase {}
        '''
    }

    private static Throwable failureOf(Closure request) {
        try { request.call(); return null }
        catch (Throwable error) { return error }
    }

    // Test-local phase injection qualifies the public seam at the actual lifecycle boundary.
    static void observe(int number, boolean beforeExisting = false, Closure body) {
        KlumPhase phase = [getNumber: { number }, getName: { "RM1-$number".toString() }] as KlumPhase
        PhaseAction action = new AbstractPhaseAction(phase) {
            @Override
            protected void doExecute() { body.call() }
        }
        def driver = PhaseDriver.instance
        if (beforeExisting) {
            def field = PhaseDriver.getDeclaredField('phaseActions')
            field.accessible = true
            (field.get(driver) as Map)[number].add(0, action)
        } else driver.addPhase(action)
    }

}
