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
}
