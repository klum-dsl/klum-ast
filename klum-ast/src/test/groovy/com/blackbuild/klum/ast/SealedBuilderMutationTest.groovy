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
import com.blackbuild.klum.ast.runtime.internal.FactoryHelper
import spock.lang.Issue

/** Receiver-preflight regressions derived from the current-master #850 investigation. */
@Issue('855')
class SealedBuilderMutationTest extends AbstractDSLSpec {

    def "sealed #state rejects #operation before changing collection or Map storage"() {
        given:
        createSchema()
        def pair = sealedPair(state)
        def labels = new ArrayList(pair.builder.getInstanceAttribute('labels'))
        def settings = new LinkedHashMap(pair.builder.getInstanceAttribute('settings'))

        when:
        mutate(pair.builder)

        then:
        def error = thrown(KlumModelException)
        error.message.contains(state == 'normal' ? 'Construction session has completed' : 'sealed Builder cannot be configured')
        pair.builder.getInstanceAttribute('labels') == labels
        pair.builder.getInstanceAttribute('settings') == settings
        pair.model.labels == ['initial']
        pair.model.settings == [mode: 'initial']

        where:
        [state, operation, mutate] << ['normal', 'wrapper'].collectMany { state ->
            [
                ['element', { b -> b.label('late') }],
                ['entry', { b -> b.setting('late', 'value') }],
                ['elements', { b -> b.labels(['late']) }],
                ['entries', { b -> b.settings([late: 'value']) }],
                ['empty elements', { b -> b.labels([]) }],
                ['empty entries', { b -> b.settings([:]) }],
                ['empty varargs', { b -> b.labels([] as String[]) }]
            ].collect { [state, it[0], it[1]] }
        }
    }

    def "sealed #state rejects #operation before allocating or configuring a child"() {
        given:
        createSchema()
        def pair = sealedPair(state)
        def children = new ArrayList(pair.builder.getInstanceAttribute('children'))
        def entries = new LinkedHashMap(pair.builder.getInstanceAttribute('entries'))
        def probe = getClass('Probe')
        def allocations = probe.allocations
        boolean configured = false
        def body = { configured = true; value 'late' }

        when:
        mutate(pair.builder, getClass('Entry'), body)

        then:
        def error = thrown(KlumModelException)
        error.message.contains(state == 'normal' ? 'Construction session has completed' : 'sealed Builder cannot be configured')
        !configured
        probe.allocations == allocations
        pair.builder.getInstanceAttribute('children') == children
        pair.builder.getInstanceAttribute('entries') == entries
        pair.model.children.empty
        pair.model.entries.existing.value == 'original'
        pair.model.entries.existing.registry.is(pair.model)
        pair.model.entries.existing.visits == 1

        where:
        [state, operation, mutate] << ['normal', 'wrapper'].collectMany { state ->
            [
                ['collection child', { b, type, configure -> b.child('late', configure) }],
                ['map child', { b, type, configure -> b.entry('late', configure) }],
                ['existing map child', { b, type, configure -> b.entry('existing', configure) }],
                ['selected collection child', { b, type, configure -> b.child(type.Create, 'late', configure) }],
                ['selected map child', { b, type, configure -> b.entry(type.Create, 'late', configure) }],
                ['selected single child', { b, type, configure -> b.primary(type.Create, 'late', configure) }]
            ].collect { [state, it[0], it[1]] }
        }
    }

    def "sealed #state rejects the #relationship factory block before invoking its closure"() {
        given:
        createSchema()
        def pair = sealedPair(state)
        boolean configured = false
        def body = { configured = true }

        when:
        pair.builder."$relationship"(body)

        then:
        def error = thrown(KlumModelException)
        error.message.contains(state == 'normal' ? 'Construction session has completed' : 'sealed Builder cannot be configured')
        !configured
        body.delegate.is(this)
        body.resolveStrategy == Closure.OWNER_FIRST

        where:
        [state, relationship] << ['normal', 'wrapper'].collectMany { state ->
            ['children', 'entries'].collect { [state, it] }
        }
    }

    private void createSchema() {
        createClass('''
            package preflight
            @DSL class Registry {
                String host
                List<String> labels
                Map<String, String> settings
                Entry primary
                @Field(members = "child") List<Entry> children
                Map<String, Entry> entries
            }
            class Probe { static int allocations; static int configurations }
            @DSL class Entry {
                @Key String key
                String value
                int allocation = ++Probe.allocations
                @Owner Registry registry
                int visits
                @PostTree void visit() { visits++ }
            }
        ''')
    }

    private Map sealedPair(String state) {
        def builder
        def model = clazz.Create.With {
            builder = delegate
            host 'original'
            label 'initial'
            setting 'mode', 'initial'
            entry('existing') { value 'original' }
        }
        if (state == 'wrapper') {
            builder = FactoryHelper.createBuilder(clazz, null)
            builder.sealTo(model)
        }
        [builder: builder, model: model]
    }
}
