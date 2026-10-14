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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue('890')
class AnnotationPropagationLifecycleTest extends AbstractDSLSpec {
    def setup() {
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(AutoCreate) @interface Created {}
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(Cluster) @PropagateAnnotation(AutoCreate) @interface Managed {}
        """)
    }

    def 'AutoCreate retains assignment ownership Templates and callback order (#propagated)'() {
        given:
        String creation = propagated ? '@Created' : '@AutoCreate'
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.*
            @DSL class Service {
                @Owner Application owner
                String value = 'initialized'
                String observed = ''
                $creation void observe() { observed += 'method' }
                $creation Closure callback = { observed += ':closure' }
            }
            @DSL class Application {
                $creation Service child
                String observed
                $creation void observe() {
                    assert child != null
                    observed = child.value
                }
                $creation Closure callback = { observed += ':closure' }
            }
            @DSL class Container { Application application }
        """)
        Class service = Service
        def template = service.Create.Template.With(value: 'template')

        when:
        def fresh = Application.Create.One()

        then:
        fresh.child.value == 'initialized'
        fresh.child.owner.is(fresh)
        fresh.child.observed == 'method:closure'
        fresh.observed == 'initialized:closure'

        when:
        def container = Container.Create.With {
            service.Template.With(template) { application() }
        }

        then:
        container.application.child.value == 'template'
        container.application.child.owner.is(container.application)
        container.application.observed == 'template:closure'

        when:
        def supplied = Container.Create.With {
            service.Template.With(template) {
                application { child { value 'supplied' } }
            }
        }

        then:
        supplied.application.child.value == 'supplied'
        supplied.application.child.owner.is(supplied.application)
        supplied.application.child.observed == 'method:closure'
        supplied.application.observed == 'supplied:closure'

        where:
        propagated << [false, true]
    }

    def 'direct fields precede Cluster fallback without replaying mutations (#propagated)'() {
        given:
        String cluster = propagated ? '@Managed' : '@Cluster @AutoCreate'
        String creation = propagated ? '@Created' : '@AutoCreate'
        createSecondaryClass("""
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase=AutoCreate, handler=Decline)
            @LifecycleParticipant.Mutator(phase=AutoCreate, handler=MustSkip)
            @interface Fallback {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Mutator(phase=AutoCreate, handler=Tune)
            @interface Tuned {}
            class Decline implements LifecycleParticipant.CreationHandler<Fallback> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Fallback> c) { null }
            }
            class MustSkip implements LifecycleParticipant.MutationHandler<Fallback> {
                void mutate(LifecycleParticipant.MutationContext<Fallback> c) {
                    throw new AssertionError('Cluster fallback must not replay field mutation')
                }
            }
            class Tune implements LifecycleParticipant.MutationHandler<Tuned> {
                void mutate(LifecycleParticipant.MutationContext<Tuned> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':mutated')
                }
            }
            @DSL class Service {
                String value = 'initialized'
                String observed = ''
                @AutoCreate void observe() { observed += 'method' }
                @AutoCreate Closure callback = { observed += ':closure' }
            }
            @DSL abstract class Group { $cluster Map<String, Service> services }
            @DSL class Application extends Group {
                $creation @Tuned Service direct
                $creation @Tuned Service existing
                @Fallback Service fallback
                String observed
                $creation void observe() {
                    assert direct.value == 'initialized:mutated'
                    assert existing.value == 'supplied:mutated'
                    assert fallback.value == 'initialized'
                    observed = 'method'
                }
                $creation Closure callback = { observed += ':closure' }
            }
        """)

        when:
        def application = Application.Create.With { existing { value 'supplied' } }

        then:
        application.direct.value == 'initialized:mutated'
        application.existing.value == 'supplied:mutated'
        application.fallback.value == 'initialized'
        application.services.size() == 3
        application.services.values().every { it.observed == 'method:closure' }
        application.observed == 'method:closure'

        where:
        propagated << [false, true]
    }

    def 'late-created children do not catch up an earlier AutoCreate phase (#propagated)'() {
        given:
        String creation = propagated ? '@Created' : '@AutoCreate'
        createSecondaryClass("""
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase=AutoLink, handler=Supply)
            @interface Late {}
            class Supply implements LifecycleParticipant.CreationHandler<Late> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Late> c) {
                    Service.Create.AsBuilder().One()
                }
            }
            @DSL class Service {
                String observed = 'pending'
                $creation void early() { observed = 'ran' }
                $creation Closure callback = { observed += ':closure' }
                $creation Leaf leaf
            }
            @DSL class Leaf {}
            @DSL class Application { @Late Service service }
        """)

        when:
        def fresh = Application.Create.One()

        then:
        fresh.service.observed == 'pending'
        fresh.service.leaf == null

        where:
        propagated << [false, true]
    }

    def 'propagated AutoCreate retains same-phase Creator conflicts'() {
        when:
        createSecondaryClass("""
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase=AutoCreate, handler=Supply)
            @interface Supplied {}
            class Supply implements LifecycleParticipant.CreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Supplied> c) { null }
            }
            @DSL class Service {}
            @DSL class Application { @Supplied $creation Service child }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Competing lifecycle creators for AutoCreate on child')

        where:
        creation << ['@AutoCreate', '@Created']
    }

    def 'bare AutoCreate retains ordinary keyed and abstract runtime restrictions (#propagated, #kind)'() {
        given:
        String creation = propagated ? '@Created' : '@AutoCreate'
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.AutoCreate
            @DSL $shape
            @DSL class Application { $creation Service child }
        """)

        when:
        Application.Create.One()

        then:
        def error = thrown(Exception)
        error.message.toLowerCase().contains(expected)

        where:
        [propagated, kind, shape, expected] << [false, true].collectMany { flag ->
            [
                [flag, 'keyed', 'class Service { @Key String name }', 'key'],
                [flag, 'abstract', 'abstract class Service {}', 'abstract']
            ]
        }
    }
}
