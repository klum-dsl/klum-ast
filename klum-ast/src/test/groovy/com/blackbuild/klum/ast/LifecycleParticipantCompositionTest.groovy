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
import spock.lang.See
import spock.lang.Tag

@Issue(['867', '883'])
class LifecycleParticipantCompositionTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#participant-composition-lp-2')
    def 'combines creation and repeated mutations on supplied and existing Builders (#container)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase = AutoLink, handler = Supply)
            ${mutations(container)}
            @interface Configured { boolean enabled() default true }
            class Supply implements LifecycleParticipant.CreationHandler<Configured> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Configured> c) {
                    c.annotation.enabled() ? Domain.Create.AsBuilder().With([value: 'created']) : null
                }
            }
            class ZFirst implements LifecycleParticipant.MutationHandler<Configured> {
                void mutate(LifecycleParticipant.MutationContext<Configured> c) {
                    def target = Domain.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':first')
                }
            }
            class ASecond implements LifecycleParticipant.MutationHandler<Configured> {
                void mutate(LifecycleParticipant.MutationContext<Configured> c) {
                    def target = Domain.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':second')
                }
            }
            @DSL class Domain { String value }
            @DSL class Application {
                @Configured Domain supplied
                @Configured Domain existing
                @Configured(enabled = false) Domain absent
            }
        """

        when:
        def application = Application.Create.With { existing { value 'configured' } }

        then:
        application.supplied.value == 'created:first:second'
        application.existing.value == 'configured:first:second'
        application.absent == null

        where:
        container << [false, true]
    }

    def 'rejects competing direct creators at the same field and phase (#kind)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase = AutoLink, handler = Supply)
            @interface Supplied {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase = AutoLink, handler = OtherSupply)
            @interface Other {}
            class Supply implements LifecycleParticipant.CreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Supplied> c) { null }
            }
            class OtherSupply implements LifecycleParticipant.CreationHandler<Other> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Other> c) { null }
            }
            @DSL class Domain {}
            @DSL class Application { @Supplied ${competing} Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('Competing lifecycle creators')
        failure.message.contains('domain')
        failure.message.contains('AutoLink')

        where:
        kind       | competing
        'external' | '@Other'
        'built-in' | '@LinkTo'
    }

    def 'different-phase built-in creation and multiple domain mutations coexist'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.layer3.LinkTo
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Creator(phase = AutoLink, handler = Supply)
            @LifecycleParticipant.Mutator(phase = AutoLink, handler = SetName)
            @interface Named {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Mutator(phase = AutoLink, handler = SetPort)
            @interface Port {}
            class Supply implements LifecycleParticipant.CreationHandler<Named> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Named> c) {
                    assert c.fieldName == 'external' : 'AutoCreate should already have supplied the Builder'
                    Domain.Create.AsBuilder().One()
                }
            }
            class SetName implements LifecycleParticipant.MutationHandler<Named> {
                void mutate(LifecycleParticipant.MutationContext<Named> c) { Domain.Create.narrowBuilder(c.targetBuilder).name('service') }
            }
            class SetPort implements LifecycleParticipant.MutationHandler<Port> {
                void mutate(LifecycleParticipant.MutationContext<Port> c) { Domain.Create.narrowBuilder(c.targetBuilder).port(8080) }
            }
            @DSL class Domain { String name; int port }
            @DSL class Application {
                @AutoCreate @Named @Port Domain domain
                @Port @Named Domain external
                Domain source
                @LinkTo(provider = { delegate }, field = 'source') @Port Domain linked
            }
        """

        when:
        def application = Application.Create.With { source { name 'source' } }

        then:
        application.linked.is(application.source)
        application.linked.port == 8080
        application.external.name == 'service'
        application.external.port == 8080
        application.domain.name == 'service'
        application.domain.port == 8080
    }

    def 'validates every handler in an explicit mutation container'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Mutator.List([
                @LifecycleParticipant.Mutator(phase = AutoLink, handler = Correct),
                @LifecycleParticipant.Mutator(phase = AutoLink, handler = Wrong)
            ])
            @interface Binding {}
            class Correct implements LifecycleParticipant.MutationHandler<Binding> {
                void mutate(LifecycleParticipant.MutationContext<Binding> c) {}
            }
            class Wrong implements LifecycleParticipant.MutationHandler<Deprecated> {
                void mutate(LifecycleParticipant.MutationContext<Deprecated> c) {}
            }
            @DSL class Domain {}
            @DSL class Application { @Binding Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('Wrong annotation parameter must resolve exactly to Binding')
    }

    def 'rejects repeated creation claims within one domain annotation (#container)'() {
        when:
        String first = '@LifecycleParticipant.Creator(phase = AutoLink, handler = Supply)'
        String second = '@LifecycleParticipant.Creator(phase = AutoLink, handler = OtherSupply)'
        String markers = container ? "@LifecycleParticipant.Creator.List([$first, $second])" : "$first\n$second"
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            $markers
            @interface Supplied {}
            class Supply implements LifecycleParticipant.CreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Supplied> c) { null }
            }
            class OtherSupply implements LifecycleParticipant.CreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleParticipant.InvocationContext<Supplied> c) { null }
            }
            @DSL class Domain {}
            @DSL class Application { @Supplied Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('Competing lifecycle creators')

        where:
        container << [false, true]
    }

    private static String mutations(boolean container) {
        String first = '@LifecycleParticipant.Mutator(phase = AutoLink, handler = ZFirst)'
        String second = '@LifecycleParticipant.Mutator(phase = AutoLink, handler = ASecond)'
        container ? "@LifecycleParticipant.Mutator.List([$first, $second])" : "$first\n$second"
    }
}
