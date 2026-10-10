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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue

@Issue('867')
class LifecycleParticipantDiagnosticsTest extends AbstractDSLSpec {
    def 'preserves #role constructor failures and restores lifecycle context in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.TYPE])
            @${marker}(phase = $phase, handler = Handler)
            @interface Binding {}
            class Handler implements ${handlerRole}<Binding> {
                Handler() { throw new IllegalArgumentException('constructor failed') }
                $resultType $operation($contextType<Binding> context) { ${returnValue} }
            }
            @DSL class Domain {}
            @DSL class BaseApplication { ${role == 'type' ? '' : '@Binding'} Domain domain }
            ${role == 'type' ? '@Binding' : ''}
            @DSL class Application extends BaseApplication {}
            @DSL class Recovery {
                @PostTree void recovered() {
                    KlumSchemaSupport.klumValidation.issue('recovered', Validate.Level.INFO)
                }
            }
        """

        when:
        if (role == 'creator') {
            Application.Create.One()
        } else {
            Application.Create.With { domain {} }
        }

        then:
        RuntimeException failure = thrown()
        def chain = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            chain << cause
        }
        chain.any { it.message.contains("Participant Binding handler Handler during $phase on $location") }
        chain.last() instanceof IllegalArgumentException
        chain.last().message == 'constructor failed'

        when:
        def recovery = Recovery.Create.One()

        then:
        KlumObjectSupport.of(recovery).validation.result.issues*.member == ['recovered']

        where:
        [role, phase] << [['creator', 'mutator', 'type'], ['AutoCreate', 'AutoLink', 'Default', 'PostTree']].combinations()
        marker = role == 'creator' ? 'LifecycleCreator' : 'LifecycleMutator'
        handlerRole = role == 'creator' ? 'LifecycleCreationHandler' : 'LifecycleMutationHandler'
        resultType = role == 'creator' ? 'KlumBuilder<?>' : 'void'
        operation = role == 'creator' ? 'create' : 'mutate'
        contextType = role == 'creator' ? 'LifecycleFieldContext' : 'LifecycleMutationContext'
        returnValue = role == 'creator' ? 'return null' : ''
        location = role == 'type' ? 'Application' : 'BaseApplication.domain'
    }
}
