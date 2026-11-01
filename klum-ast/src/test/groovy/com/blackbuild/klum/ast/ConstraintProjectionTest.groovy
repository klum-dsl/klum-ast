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

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue

@Issue('959')
class ConstraintProjectionTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader

    private Class capacity(String policy) {
        createClass("""
            package projectionruntime
            import com.blackbuild.klum.ast.*
            import static com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy.$policy
            @DSL class Capacity {
                @Key String name
                @Struct.ConstraintProjection($policy) @Validate({ it > 0 }) Integer replicas
                @Struct.ConstraintProjection($policy) String label
            }
        """)
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('projectionruntime.Capacity', false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    def 'policy keeps runtime validation and structured input admission: #policy'() {
        given:
        Class type = capacity(policy)

        when:
        def value = KlumStructuredInput.readRoot(type, [name: 'web', replicas: 2], 'policy input')

        then:
        value.name == 'web'
        value.replicas == 2
        value.label == null

        when:
        KlumStructuredInput.readRoot(type, [name: 'web', replicas: 2, label: null], 'policy input')

        then:
        def nullInput = thrown(KlumModelException)
        nullInput.message.contains('null')

        when:
        KlumStructuredInput.readRoot(type, [name: 'web', replicas: -1], 'policy input')

        then:
        thrown(KlumValidationException)

        when:
        KlumStructuredInput.readRoot(type, [replicas: 2], 'policy input')

        then:
        def missing = thrown(KlumModelException)
        missing.message.contains('name')

        when:
        KlumStructuredInput.readRoot(type, [name: 'web', replicas: 2, unknown: 'bad'], 'policy input')

        then:
        def unknown = thrown(KlumModelException)
        unknown.message.contains('unknown')

        where:
        policy << ['PROJECT', 'TEXT_ONLY', 'OMIT']
    }
}
