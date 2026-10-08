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

    def "sealed #state rejects #operation before invoking a converter"() {
        given:
        createSchema()
        def pair = sealedPair(state)
        def probe = getClass('Probe')
        def allocations = probe.allocations
        def configurations = probe.configurations

        when:
        convert(pair.builder)

        then:
        def error = thrown(KlumModelException)
        error.message.contains(state == 'normal' ? 'Construction session has completed' : 'sealed Builder cannot be configured')
        probe.allocations == allocations
        probe.configurations == configurations
        pair.builder.getInstanceAttribute('children').empty
        pair.builder.getInstanceAttribute('values').empty
        pair.builder.getInstanceAttribute('namedValues').isEmpty()
        pair.model.primary == null
        pair.model.entries.existing.value == 'original'

        where:
        [state, operation, convert] << ['normal', 'wrapper'].collectMany { state ->
            [
                ['single value', { b -> b.singleValue('late') }],
                ['collection value', { b -> b.value('late') }],
                ['Map value', { b -> b.namedValue('late', 'value') }],
                ['single Builder', { b -> b.primary(new URI('late')) }],
                ['collection Builder', { b -> b.child(new URI('late')) }],
                ['Map Builder', { b -> b.entry(new URI('late')) }]
            ].collect { [state, it[0], it[1]] }
        }
    }

    def "retained #relationship factory rejects #operation before producing children in a new session"() {
        given:
        createSchema()
        def factory
        def receiver
        def completed = clazz.Create.With {
            receiver = delegate
            delegate."$relationship" { factory = delegate }
        }
        def probe = getClass('Probe')
        def allocations = probe.allocations
        boolean configured = false
        def body = { configured = true }

        when:
        clazz.Create.With { produce(factory, body) }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('Construction session has completed')
        !configured
        probe.configurations == 0
        probe.allocations == allocations
        receiver.getInstanceAttribute(relationship).isEmpty()
        completed.children.empty
        completed.entries.isEmpty()

        where:
        [relationship, operation, produce] << ['children', 'entries'].collectMany { relationship ->
            [
                ['projected collection', { f, configure -> f.batch('late') }],
                ['projected Map', { f, configure -> f.mapped('late') }],
                ['projected converter', { f, configure -> f.entry(new URI('late')) }]
            ].collect { [relationship, it[0], it[1]] }
        }
    }

    private void createSchema() {
        createClass('''
            package preflight
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import java.net.URI
            @DSL class Registry {
                String host
                List<String> labels
                Map<String, String> settings
                Value singleValue
                List<Value> values
                Map<String, Value> namedValues
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
                static Entry fromUri(URI uri) {
                    Probe.configurations++
                    return Entry.Create.With(uri.toString(), value: 'converted')
                }
                static class Factory extends KlumFactory.Keyed<Entry> {
                    Factory() { super(Entry) }
                    List<KlumBuilder<Entry>> batch(String key) {
                        Probe.configurations++
                        return [(KlumBuilder<Entry>) (Object) AsBuilder().With(key, value: 'batch')]
                    }
                    Map<String, KlumBuilder<Entry>> mapped(String key) {
                        Probe.configurations++
                        return [(key): (KlumBuilder<Entry>) (Object) AsBuilder().With(key, value: 'mapped')]
                    }
                }
            }
            class Value {
                String text
                static Value fromString(String text) {
                    Probe.configurations++
                    return new Value(text: text)
                }
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
