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

import com.blackbuild.klum.ast.runtime.KlumException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('867')
class LifecycleParticipantSealedTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#sealed-participant-targets-lp-4')
    def 'skips completed LINK targets before constructing handlers in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Configure,
                onSealed = LifecycleMutator.SealedPolicy.SKIP)
            @interface Configured {}
            class Configure implements LifecycleMutationHandler<Configured> {
                Configure() { throw new IllegalStateException('must not construct a skipped handler') }
                void mutate(LifecycleMutationContext<Configured> c) { throw new AssertionError('must not invoke') }
            }
            @DSL class Service { String value }
            @DSL class SpecializedService extends Service {}
            @DSL class Application {
                @Configured @Field(FieldType.LINK) Service service
                @Configured @Field(FieldType.LINK) Service alias
            }
        """
        def completed = SpecializedService.Create.With { value 'completed' }

        when:
        def application = Application.Create.With {
            service completed
            alias completed
        }

        then:
        application.service.is(completed)
        application.alias.is(completed)
        application.service.value == 'completed'

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'default FAIL preserves sealed rejection context and cause in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Configure)
            @interface Configured {}
            class Configure implements LifecycleMutationHandler<Configured> {
                Configure() { throw new IllegalStateException('handler must not be constructed') }
                void mutate(LifecycleMutationContext<Configured> c) {}
            }
            @DSL class Service { String value }
            @DSL class BaseApplication { @Configured @Field(FieldType.LINK) Service service }
            @DSL class Application extends BaseApplication {}
        """
        def completed = Service.Create.With { value 'completed' }

        when:
        Application.Create.With { service completed }

        then:
        KlumException failure = thrown()
        failure.cause.message.contains("Participant Configured handler Configure during $phase on BaseApplication.service")
        failure.cause.cause.message.contains('unsealed Builder (onSealed=FAIL)')
        completed.value == 'completed'

        when:
        def subsequent = Service.Create.With { value 'subsequent' }

        then:
        subsequent.value == 'subsequent'

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'SKIP still mutates unsealed polymorphic aliases with fresh handlers across sessions in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Configure,
                onSealed = LifecycleMutator.SealedPolicy.SKIP)
            @interface Configured {}
            class Configure implements LifecycleMutationHandler<Configured> {
                boolean used
                void mutate(LifecycleMutationContext<Configured> c) {
                    assert !used
                    used = true
                    assert c.declaredType == Service
                    assert KlumBuilderSupport.of(c.targetBuilder).modelType == SpecializedService
                    def service = Service.Create.narrowBuilder(c.targetBuilder)
                    service.value(service.value + ':' + c.fieldName)
                }
            }
            @DSL class Service {
                String value
                @Field(FieldType.LINK) Service peer
            }
            @DSL class SpecializedService extends Service {}
            @DSL class Application {
                @Configured Service service
                @Configured @Field(FieldType.LINK) Service alias
            }
        """

        when:
        def specializedType = SpecializedService
        def results = (1..2).collect {
            Application.Create.With {
                def child = service(specializedType) { value 'active' }
                child.peer(child)
                alias(child)
            }
        }

        then:
        results.every { it.service.is(it.alias) && it.service.peer.is(it.service) }
        results.every { it.service.value.count(':') == 2 && it.service.value.contains(':service') && it.service.value.contains(':alias') }
        !results[0].service.is(results[1].service)

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'creators use ordinary checked assignment for completed wrappers (#kind)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = AutoLink, handler = Supply)
            @LifecycleMutator(phase = AutoLink, handler = Configure, onSealed = LifecycleMutator.SealedPolicy.SKIP)
            @interface Supplied {}
            class Supply implements LifecycleCreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleFieldContext<Supplied> c) {
                    Application.Create.narrowBuilder(c.containingBuilder).source
                }
            }
            class Configure implements LifecycleMutationHandler<Supplied> {
                void mutate(LifecycleMutationContext<Supplied> c) { throw new AssertionError('sealed result must skip') }
            }
            @DSL class Service { String value }
            @DSL class Application {
                @Field(FieldType.LINK) Service source
                @Supplied @Field(FieldType.$kind) Service service
            }
        """
        def completed = Service.Create.With { value 'completed' }

        when:
        def result = Application.Create.With { source completed }

        then:
        result.service.is(completed)

        where:
        kind << ['LINK', 'OPTIONAL_LINK']
    }
}
