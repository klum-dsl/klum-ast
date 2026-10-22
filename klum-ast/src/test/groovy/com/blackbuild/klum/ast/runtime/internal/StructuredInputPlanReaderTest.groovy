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
import groovy.transform.CompileStatic
import spock.lang.Issue

@Issue('932')
class StructuredInputPlanReaderTest extends AbstractDSLSpec {

    def 'reads packaged carrier roles and exact public contracts without Schema or enum initialization'() {
        given:
        createClass '''
            package runtimeplan
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                static { throw new AssertionError('Schema initialized during extraction') }
                String title
                Stage stage
                Branch branch
                Map<String, Entry> entries
                @Builder.Method void words(List<String> value) { }
            }
            enum Stage {
                READY
                static { throw new AssertionError('Enum initialized during extraction') }
            }
            @DSL class Branch { String text }
            @DSL class Entry { @Key String name; String text }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def document = Class.forName('runtimeplan.Document', false, binaryLoader)

        when:
        def plan = StructuredInputPlan.read(document)

        then:
        plan.entries().keySet() == ['branch', 'entries', 'stage', 'title', 'words'] as Set
        plan.entries().get('branch').role().name() == 'OWNED_OBJECT'
        plan.entries().get('entries').role().name() == 'KEYED_OWNED_MAP'
        nameOf(plan.entries().get('stage').payload().type()) == 'runtimeplan.Stage'
        plan.entries().get('words').methods()*.parameterTypes*.toList() == [[List]]
        nameOf(plan.contract()) == 'runtimeplan.Document_DSL$Builder'

        cleanup:
        binaryLoader.close()
    }
    @CompileStatic
    private static String nameOf(Class<?> type) { type.getName() }
}
