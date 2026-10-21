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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Issue
import spock.lang.Unroll

@Issue(['932', '529'])
class StructuredInputDispatchProbeTest extends AbstractDSLSpec {
    def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())

    def setup() { createNonDslClass(StructuredInputProbeFixture.SOURCE) }

    def "ordinary DSL, named maps, Jackson and restricted calls share owned lifecycle"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        def childType = getClass('inputprobe.ProbeChild')
        List results = []

        when:
        results << root.Create.With(serviceName: 'named', mode: getClass('inputprobe.Mode').SAFE, labels: [team: 'core'], child: [value: 'nested']) {
            entries { entry('one', value: 'keyed') }
            items { item(value: 'listed') }
        }
        results << importer.readRoot(root, KlumJacksonInput.map([
                'service-name': 'named', mode: 'SAFE', labels: [team: 'core'], child: [value: 'nested'],
                entries: [one: [name: 'one', value: 'keyed']], items: [[value: 'listed']]]))
        results << root.Create.With {
            StructuredInputProbeFixture.operations(delegate, [['service-name', 'named'], ['mode', 'SAFE'], ['labels', [team: 'core']],
                    ['child', [value: 'nested']], ['entries', [one: [value: 'keyed']]],
                    ['items', [[value: 'listed']]]])
        }

        then:
        results.every { it.serviceName == 'named' && it.mode.name() == 'SAFE' && it.labels == [team: 'core'] }
        results.every { it.child.value == 'nested' && it.child.owner.is(it) && it.linked.is(it.child) }
        results.every { it.entries.one.name == 'one' && it.items*.value == ['listed'] }
        root.events.count('root:create') == 3
        root.events.count('root:apply') == 3
        root.events.count('root:link') == 3
        root.events.count('root:tree') == 3
        root.events.count('root:validate') == 3
        root.events.count('child:create') == 6
        root.events.count('child:apply') == 6
        root.events.count('child:validate') == 6
        childType != null
    }

    def "managed modes preserve Template and active Builder identity"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        def childType = getClass('inputprobe.ProbeChild')
        def childFactory = childType.Create.AsBuilder()
        def managed = importer
        def template = importer.readTemplate(root, KlumJacksonInput.map(['service-name': 'recipe', child: [:]]))
        assert root.events.empty
        List captured = []

        when:
        def result = root.Create.With {
            def nested = managed.readBuilder(childFactory, KlumJacksonInput.map([value: 'imported']))
            captured << nested
            child(nested)
            assert managed.applyToBuilder(delegate, KlumJacksonInput.map([host: 'first'])).is(delegate)
            assert managed.applyToBuilder(delegate, KlumJacksonInput.map([host: 'second'])).is(delegate)
            captured << delegate
        }

        then:
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.child)
        result.child.value == 'imported'
        result.child.owner.is(result)
        result.host == 'second'
        // Existing managed APPLY replays callbacks; this probe records the observed qualification.
        root.events.count('root:create') == 3
        root.events.count('root:apply') == 3
        root.events.count('child:create') == 2
        root.events.count('child:apply') == 2
        root.events.count('root:validate') == 1
        root.events.count('child:validate') == 1

        when:
        importer.applyToBuilder(captured.last(), KlumJacksonInput.map([host: 'late']))
        then:
        thrown(KlumModelException)

        when:
        managed.readBuilder(childFactory, KlumJacksonInput.map([:]))
        then:
        thrown(KlumModelException)

        when:
        root.Create.With { managed.readRoot(root, KlumJacksonInput.map([:])) }
        then:
        thrown(KlumModelException)
    }

    def "custom operations and converters remain distinct from Jackson properties"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        def copied = root.Create.With(serviceName: 'seed')

        when:
        def named = root.Create.With(shout: 'hello', inheritedLocale: 'eu', source: 'https://example.test',
                mapping: [a: 'b'], sequence: ['one', 'two'])
        def copy = root.Create.With(copyFrom: copied)
        def interpreted = root.Create.With { StructuredInputProbeFixture.operations(delegate, [['shout', 'hi'], ['inheritedLocale', 'us']]) }

        then:
        named.serviceName == 'one:two'
        named.region == 'eu'
        named.labels == [a: 'b']
        named.source.toString() == 'https://example.test'
        copy.serviceName == 'seed'
        interpreted.serviceName == 'HI'
        interpreted.region == 'us'

        when:
        importer.readRoot(root, KlumJacksonInput.map([shout: 'hello']))
        then:
        def exception = thrown(KlumModelException)
        exception.message.contains('/input(#/shout)')
        exception.cause != null

        when:
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['copyFrom', copied]]) }
        then:
        def rejected = thrown(IllegalArgumentException)
        rejected.message.contains('excluded or unknown')
    }

    def "broad named dispatch exposes apply and lifecycle helpers that restricted admission excludes"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        when:
        def result = root.Create.With(apply: [host: 'helper'], created: null)
        then:
        result.host == 'helper'
        root.events.count('root:create') == 2

        when:
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['apply', [host: 'x']]]) }
        then:
        thrown(IllegalArgumentException)
    }

    def "restricted child selection rejects runtime type controls and native enum String is unsupported"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        def childType = getClass('inputprobe.ProbeChild')
        when:
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['child', childType]]) }
        then:
        def rejected = thrown(IllegalArgumentException)
        rejected.message.contains('no Class/Factory selector')

        when:
        root.Create.With(mode: 'SAFE')
        then:
        thrown(KlumModelException)

        when:
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['child', [copyFrom: [:]]]]) }
        then:
        def nested = thrown(IllegalArgumentException)
        nested.message.contains('nested controls are not admitted')
    }

    def "one map or list value is not a multi-argument call"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')

        when:
        def defaulted = root.Create.With(endpoint: 'api')
        def positional = root.Create.With { StructuredInputProbeFixture.operations(delegate, [['endpoint', ['api', 9090]]]) }
        def values = root.Create.With(mapping: [host: 'data'], sequence: ['a', 'b'])

        then:
        defaulted.host == 'api'
        defaulted.port == 8080
        positional.host == 'api'
        positional.port == 9090
        values.labels == [host: 'data']
        values.serviceName == 'a:b'

        when:
        root.Create.With(endpoint: [host: 'api', port: 9090])
        then:
        thrown(KlumModelException)

        when:
        def spread = root.Create.With(endpoint: ['api', 9090])
        then:
        spread.host == 'api'
        spread.port == 9090
    }

    def "order and repeated operations are observable but maps cannot encode repetition"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        when:
        def first = root.Create.With(host: 'a', append: 'b')
        def second = root.Create.With(append: 'b', host: 'a')
        def repeated = root.Create.With { StructuredInputProbeFixture.operations(delegate, [['append', 'a'], ['append', 'b']]) }
        then:
        first.host == 'ab'
        second.host == 'a'
        repeated.host == 'ab'
    }

    def "renames exclusions and unsupported abstract inputs do not establish parity"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        when:
        def mapped = importer.readRoot(root, KlumJacksonInput.map(['service-name': 'public', 'class': 'tier',
                internalSetting: 'hidden']))
        def named = root.Create.With(internalSetting: 'visible')
        then:
        mapped.serviceName == 'public'
        mapped.category == 'tier'
        mapped.internalSetting == null
        named.internalSetting == 'visible'

        when:
        root.Create.With('service-name': 'external')
        then:
        thrown(KlumModelException)

        when:
        importer.readRoot(getClass('inputprobe.UnsupportedRoot'), KlumJacksonInput.map([target: [value: 'x']]))
        then:
        thrown(KlumModelException)
    }

    def "structural omission defaults false zero empty and null differ from completed validation"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        def managed = importer
        when:
        def omitted = managed.readRoot(root, KlumJacksonInput.map([:]))
        def explicit = root.Create.With { StructuredInputProbeFixture.operations(delegate,
                [['enabled', false], ['port', 0], ['labels', [:]], ['items', []]]) }
        def nulled = managed.readRoot(root, KlumJacksonInput.map(['service-name': null]))
        then:
        omitted.requiredValue == 'provided'
        explicit.enabled == false
        explicit.port == 0
        explicit.labels.isEmpty()
        explicit.items.empty
        nulled.serviceName == null

        when: 'a raw string shape does not imply completed Required validation'
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['requiredValue', '']]) }
        then:
        thrown(RuntimeException)

        when: 'integer shape alone does not imply a valid completed model'
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [['port', -1]]) }
        then:
        thrown(RuntimeException)
    }

    @Unroll
    def "restricted diagnostic retains path contract and cause for #name"() {
        given:
        def root = getClass('inputprobe.ProbeRoot')
        when:
        root.Create.With { StructuredInputProbeFixture.operations(delegate, [[name, value]]) }
        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains('probe.json#/0/' + name)
        failure.message.contains('inputprobe.ProbeRoot_DSL')
        failure.cause != null
        if (name == 'fail') {
            def original = failure.cause
            while (original.cause != null) original = original.cause
            assert original instanceof IllegalStateException
            assert original.message == 'inside'
        }
        where:
        name       | value
        'endpoint' | ['api']
        'fail'     | 'inside'
        'unknown'  | 'x'
    }

}
