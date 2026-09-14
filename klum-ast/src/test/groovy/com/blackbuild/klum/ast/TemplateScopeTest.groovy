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

import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import com.blackbuild.klum.ast.testsupport.TemplateScope
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicReference

@Issue("658")
class TemplateScopeTest extends AbstractDSLSpec {

    Class<?> scopeType
    Class<?> otherScopeType

    def setup() {
        scopeType = createClass '''
            package pk

            @DSL
            class ScopeTemplateValue {
                String label
            }

            @DSL
            class OtherScopeTemplateValue {
                String label
            }
        '''
        otherScopeType = getClass('pk.OtherScopeTemplateValue')
    }

    def "adds materialized Templates through both forms and removes an empty prior scope"() {
        given:
        def first = templateFor(scopeType, 'first')
        def second = templateFor(scopeType, 'second')

        expect:
        TemplateManager.isTemplate(first)
        TemplateManager.isTemplate(second)

        when:
        try (TemplateScope scope = new TemplateScope().with(first).with([second])) {
            assert appliedLabel(scopeType) == 'second'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "restores outer materialized Templates after nested shadowing"() {
        given:
        def outer = templateFor(scopeType, 'outer')
        def inner = templateFor(scopeType, 'inner')

        when:
        try (TemplateScope ignored = new TemplateScope().with(outer)) {
            assert appliedLabel(scopeType) == 'outer'
            try (TemplateScope nested = new TemplateScope().with(inner)) {
                assert appliedLabel(scopeType) == 'inner'
            }
            assert appliedLabel(scopeType) == 'outer'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "keeps an inner field ahead of an outer update"() {
        given:
        def initial = templateFor(scopeType, 'initial')
        def replacement = templateFor(scopeType, 'replacement')
        def specialized = templateFor(scopeType, 'specialized')

        when:
        try (TemplateScope firstField = new TemplateScope().with(initial)) {
            TemplateScope secondField = new TemplateScope().with(specialized)
            try {
                firstField.with(replacement)
                assert appliedLabel(scopeType) == 'specialized'
            } finally {
                secondField.close()
            }
            assert appliedLabel(scopeType) == 'replacement'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "retains the last duplicate materialized Template from varargs and collections"() {
        given:
        def first = templateFor(scopeType, 'first')
        def second = templateFor(scopeType, 'second')
        def third = templateFor(scopeType, 'third')

        when:
        try (TemplateScope ignored = new TemplateScope().with(first, second).with([first, third])) {
            assert appliedLabel(scopeType) == 'third'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "snapshots caller-owned materialized Template inputs"() {
        given:
        def varargsValue = templateFor(scopeType, 'varargs')
        def collectionValue = templateFor(otherScopeType, 'collection')
        Object[] varargs = [varargsValue]
        def collection = [collectionValue]

        when:
        try (TemplateScope ignored = new TemplateScope().with(varargs).with(collection)) {
            varargs[0] = templateFor(scopeType, 'replacement')
            collection[0] = templateFor(otherScopeType, 'replacement')

            assert appliedLabel(scopeType) == 'varargs'
            assert appliedLabel(otherScopeType) == 'collection'
        }

        then:
        appliedLabel(scopeType) == null
        appliedLabel(otherScopeType) == null
    }

    def "restores state when the scoped body fails"() {
        given:
        def value = templateFor(scopeType, 'value')

        when:
        try {
            try (TemplateScope ignored = new TemplateScope().with(value)) {
                assert appliedLabel(scopeType) == 'value'
                throw new IllegalArgumentException('expected')
            }
            assert false
        } catch (IllegalArgumentException ignored) {
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "rejects wrong-thread and out-of-order closure without changing the active mapping"() {
        given:
        def outerValue = templateFor(scopeType, 'outer')
        def innerValue = templateFor(scopeType, 'inner')

        when:
        try (TemplateScope outer = new TemplateScope().with(outerValue);
             TemplateScope inner = new TemplateScope().with(innerValue)) {
            try {
                outer.close()
                assert false
            } catch (IllegalStateException ignored) {
            }
            assert appliedLabel(scopeType) == 'inner'

            AtomicReference<Throwable> failure = new AtomicReference<>()
            Thread thread = new Thread({
                try {
                    inner.close()
                } catch (Throwable throwable) {
                    failure.set(throwable)
                }
            })
            thread.start()
            thread.join()

            assert failure.get().class == IllegalStateException
            assert appliedLabel(scopeType) == 'inner'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "makes successful close idempotent and rejects further values"() {
        given:
        TemplateScope scope = new TemplateScope().with(templateFor(scopeType, 'value'))

        when:
        scope.close()
        scope.close()
        def failure
        try {
            scope.with(templateFor(scopeType, 'replacement'))
            assert false
        } catch (IllegalStateException exception) {
            failure = exception
        }

        then:
        failure.class == IllegalStateException
        appliedLabel(scopeType) == null
    }

    def "rejects non-Templates without changing the active mapping"() {
        given:
        def baseline = templateFor(scopeType, 'baseline')

        when:
        try (TemplateScope scope = new TemplateScope().with(baseline)) {
            try {
                scope.with(new Object())
                assert false
            } catch (IllegalArgumentException ignored) {
            }
            assert appliedLabel(scopeType) == 'baseline'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "rejects mixed Template input atomically"() {
        given:
        def baseline = templateFor(scopeType, 'baseline')
        def replacement = templateFor(scopeType, 'replacement')

        when:
        try (TemplateScope scope = new TemplateScope().with(baseline)) {
            try {
                scope.with(replacement, new Object())
                assert false
            } catch (IllegalArgumentException ignored) {
            }
            assert appliedLabel(scopeType) == 'baseline'
        }

        then:
        appliedLabel(scopeType) == null
    }

    def "rejects null Template values without changing the active mapping"() {
        given:
        def baseline = templateFor(scopeType, 'baseline')

        when:
        try (TemplateScope scope = new TemplateScope().with(baseline)) {
            try {
                scope.with([null] as Object[])
                assert false
            } catch (IllegalArgumentException ignored) {
            }
            assert appliedLabel(scopeType) == 'baseline'
        }

        then:
        appliedLabel(scopeType) == null
    }

    private Object templateFor(Class<?> modelType, String label) {
        modelType.Create.Template.With(label: label)
    }

    private Object appliedLabel(Class<?> modelType) {
        modelType.Create.One().label
    }
}
