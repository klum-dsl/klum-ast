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

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('867')
class LifecycleParticipantPhaseTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#field-participants-in-four-phases-lp-3')
    def 'creates then mutates direct fields before callbacks in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = Supply)
            @LifecycleMutator(phase = $phase, handler = ZFirst)
            @LifecycleMutator(phase = $phase, handler = ASecond)
            @interface Configured { boolean enabled() default true }
            class Supply implements LifecycleCreationHandler<Configured> {
                KlumBuilder<?> create(LifecycleFieldContext<Configured> c) {
                    c.annotation.enabled() ? Service.Create.AsBuilder().With([value: 'created']) : null
                }
            }
            class ZFirst implements LifecycleMutationHandler<Configured> {
                void mutate(LifecycleMutationContext<Configured> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':first')
                }
            }
            class ASecond implements LifecycleMutationHandler<Configured> {
                void mutate(LifecycleMutationContext<Configured> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':second')
                }
            }
            @DSL class Service { String value }
            @DSL class Application {
                @Configured Service supplied
                @Configured Service existing
                @Configured(enabled = false) Service absent
                String observed
                @$phase void observe() {
                    assert supplied.value == 'created:first:second'
                    assert existing.value == 'configured:first:second'
                    observed = 'method'
                }
                @${phase == 'Default' ? 'Default(code = { null })' : phase} Closure callback = { observed += ':closure' }
            }
        """

        when:
        def application = Application.Create.With { existing { value 'configured' } }

        then:
        application.supplied.value == 'created:first:second'
        application.existing.value == 'configured:first:second'
        application.absent == null
        application.observed == 'method:closure'

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }
}
