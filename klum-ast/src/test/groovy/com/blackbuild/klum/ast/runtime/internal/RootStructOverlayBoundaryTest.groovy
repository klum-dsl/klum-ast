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
import spock.lang.See

@Issue('1038')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0032-builder-oriented-structured-input.md')
class RootStructOverlayBoundaryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class application

    def setup() {
        createClass '''
            package structoverlayboundary
            import com.blackbuild.klum.ast.*
            @DSL class Application {
                static List<String> events = []
                @Key String name
                String title
                @PostCreate void created() { events << 'create:' + name }
                @Builder.Method void configure(String value) {
                    title = value
                    events << 'configure:' + title
                }
                @PostApply void applied() { events << 'apply:' + name }
            }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        application = artifactLoader.loadClass('structoverlayboundary.Application')
    }

    def cleanup() { artifactLoader?.close() }

    def 'root execution reaches mutations before rejecting a later unsupported input'() {
        when:
        application.Create.FromStruct([name: 'orders', configure: 'first', unsupported: true], 'orders.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('orders.yaml')
        failure.message.contains('/unsupported')
        // ADR 0032 intentionally preserves reached mutations; a whole-document preflight would break this.
        application.events == ['create:orders', 'configure:first']

        when:
        def next = application.Create.FromStruct([name: 'next', configure: 'second'], 'next.yaml')

        then:
        next.name == 'next'
        next.title == 'second'
        application.events == ['create:orders', 'configure:first', 'create:next', 'configure:second', 'apply:next']
    }
    def 'missing construction key rejects before root allocation or configuration'() {
        when:
        application.Create.FromStruct([configure: 'first'], 'missing-key.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('missing-key.yaml')
        failure.message.contains('required construction key')
        application.events.empty
    }
}
