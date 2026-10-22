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
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver
import org.codehaus.groovy.runtime.InvokerHelper
import spock.lang.Issue

import java.util.function.Consumer

/** Fixed calls only: qualifies existing runtime seams, never interprets arbitrary input. */
@Issue('932')
class StructuredInputRuntimeBoundaryProbeTest extends AbstractDSLSpec {

    private void schema() {
        createClass '''
            package runtimeboundary
            import com.blackbuild.klum.ast.*

            @DSL class Document {
                static List<String> events = []
                Section section
                Map<String, Entry> entries
                @PostCreate void created() { events << 'document:create' }
                @PostApply void applied() { events << 'document:apply' }
                @PostTree void tree() { events << 'document:tree' }
                @Validate void checked() { events << 'document:validate' }
            }
            @DSL class Section {
                @Owner Document owner
                List<String> words
                Map<String, String> labels
                String title
                @Builder.Method void summarize(List<String> values) {
                    title = values.join(':')
                }
                @PostCreate void created() { Document.events << 'section:create' }
                @PostApply void applied() { Document.events << "section:apply:$title".toString() }
                @PostTree void tree() { Document.events << 'section:tree' }
                @Validate void checked() { assert owner != null }
            }
            @DSL class Entry {
                @Key String name
                @Owner Document owner
                String value
                @PostApply void applied() { Document.events << "entry:apply:$name:$value".toString() }
            }
        '''
    }

    def 'existing Consumer preparation configures children before PostApply and materializes once'() {
        given:
        schema()
        def document = getClass('runtimeboundary.Document')
        def section = getClass('runtimeboundary.Section')
        def entry = getClass('runtimeboundary.Entry')
        def captured

        when:
        def model = document.Create.With {
            captured = FactoryHelper.prepareNestedBuilder(section, null, false, { child ->
                PhaseDriver.requireCurrentConstructionSession(child)
                InvokerHelper.invokeMethod(child, 'words', [['a', 'b']] as Object[])
                InvokerHelper.invokeMethod(child, 'labels', [['a/b~c': 'literal']] as Object[])
                InvokerHelper.invokeMethod(child, 'summarize', [['one', 'two']] as Object[])
            } as Consumer)
            InvokerHelper.invokeMethod(delegate, 'section', [captured] as Object[])
            def keyed = FactoryHelper.prepareNestedBuilder(entry, 'a/b~c', false, { child ->
                InvokerHelper.invokeMethod(child, 'value', ['payload'] as Object[])
            } as Consumer)
            ((InternalKlumBuilder) delegate).addElementToMap('entries', 'a/b~c', keyed)
            assert !captured.sealed
            assert captured.completedModel == null
            assert !document.events.any { it.endsWith(':tree') || it.endsWith(':validate') }
        }

        then:
        model.section.title == 'one:two'
        model.section.words == ['a', 'b']
        model.section.labels == ['a/b~c': 'literal']
        model.section.owner.is(model)
        model.entries['a/b~c'].name == 'a/b~c'
        model.entries['a/b~c'].owner.is(model)
        document.events == ['document:create', 'section:create', 'section:apply:one:two',
                            'entry:apply:a/b~c:payload', 'document:apply', 'document:tree',
                            'section:tree', 'document:validate']
        captured.sealed
        captured.completedModel.is(model.section)
    }

    def 'a failed Consumer escapes the enclosing lifecycle and session cleanup permits another root'() {
        given:
        schema()
        def document = getClass('runtimeboundary.Document')
        def section = getClass('runtimeboundary.Section')
        def original = new IllegalArgumentException('authored failure')
        def captured

        when:
        document.Create.With {
            FactoryHelper.prepareNestedBuilder(section, null, false, { child ->
                captured = child
                throw original
            } as Consumer)
        }

        then:
        def failure = thrown(Exception)
        causeChain(failure).any { it.is(original) }
        !document.events.any { it.endsWith(':tree') || it.endsWith(':validate') }

        when:
        PhaseDriver.requireCurrentConstructionSession(captured)

        then:
        thrown(KlumModelException)

        when:
        def fresh = document.Create.One()

        then:
        fresh != null
    }

    private static List<Throwable> causeChain(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) result.add(current)
        result
    }
}
