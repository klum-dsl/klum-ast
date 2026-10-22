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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException

import java.util.concurrent.atomic.AtomicReference

@Issue('932')
class StructuredInputRuntimeTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader

    private Class schema(String source, String name) {
        createClass(source)
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName(name, false, artifactLoader)
    }

    private Class documentSchema() {
        schema('''
            package structuredruntime
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                static List<String> events = []
                static Map<String, Object> supplied
                int count = 2
                boolean enabled
                Mode mode
                String summary
                List<String> words
                List<List<String>> rows
                SortedSet<Integer> ranks
                SortedMap<String, BigDecimal> prices
                Map<String, List<Integer>> matrix
                Child child
                Map<String, Entry> entries
                @Builder.Method void add(int value) { count += value; events << "add:$value".toString() }
                @Builder.Method void multiply(int value) { count *= value; events << "multiply:$value".toString() }
                @Builder.Method void values(List<String> value) { summary = value.join(':') }
                @Builder.Method void settings(Map<String, String> value) { summary = value.host }
                @Builder.Method void mutate(String value) { ((List<String>) supplied.words).clear(); supplied.put('unknown', true) }
                @Builder.Method void fail(String value) { count = 99; throw new IllegalArgumentException(value) }
                @Builder.Method void explode(String value) { throw new AssertionError(value) }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            enum Mode { READY, DONE }
            @DSL class Child {
                @Owner Document owner
                String value = ''
                @Builder.Method void prefix(String text) { value = text + value; Document.events << "prefix:$text".toString() }
                @Builder.Method void suffix(String text) { value += text; Document.events << "suffix:$text".toString() }
                @PostApply void applied() { Document.events << "child:apply:$value".toString() }
            }
            @DSL class Entry {
                @Key String name
                @Owner Document owner
                String value
                @PostCreate void created() { Document.events << "entry:create:$name".toString() }
                @PostApply void applied() { Document.events << "entry:apply:$name:$value".toString() }
            }
        ''', 'structuredruntime.Document')
    }

    def cleanup() { artifactLoader?.close() }

    def 'root operations follow supplied Map order instead of carrier order'() {
        given:
        def document = documentSchema()

        when:
        def first = KlumStructuredInput.readRoot(document, null, [add: 3, multiply: 4], 'first.yaml')
        def firstEvents = new ArrayList(document.events)
        document.events.clear()
        def second = KlumStructuredInput.readRoot(document, null, [multiply: 4, add: 3], 'second.yaml')

        then:
        first.count == 20
        second.count == 11
        firstEvents == ['root:create', 'add:3', 'multiply:4', 'root:apply', 'root:tree']
        document.events == ['root:create', 'multiply:4', 'add:3', 'root:apply', 'root:tree']
    }

    def 'nested ownership keyed creation collection population and whole payloads retain prepared order'() {
        given:
        def document = documentSchema()
        def input = [words: ['b', 'a'], ranks: [3, 1, 3, 2], prices: [b: 2.5G, a: 1G],
                matrix: ['b/a~': [2, 1]], rows: [['b', 'a']], mode: 'READY', enabled: true,
                values: ['one', 'two'], settings: [host: 'api'],
                child: [suffix: 'b', prefix: 'a'],
                entries: ['a/b~c': [value: 'first'], '': [value: 'second']]]

        when:
        def model = KlumStructuredInput.readRoot(document, null, input, 'nested.yaml')

        then:
        model.words == ['b', 'a']
        model.ranks.toList() == [1, 2, 3]
        model.prices == [a: 1G, b: 2.5G]
        model.matrix == ['b/a~': [2, 1]]
        model.rows == [['b', 'a']]
        model.mode.name() == 'READY'
        model.enabled
        model.summary == 'api'
        model.child.value == 'ab'
        model.child.owner.is(model)
        model.entries.keySet().toList() == ['a/b~c', '']
        model.entries['a/b~c'].name == 'a/b~c'
        model.entries[''].name == ''
        model.entries.values().every { it.owner.is(model) }
        document.events == ['root:create', 'suffix:b', 'prefix:a', 'child:apply:ab',
                'entry:create:a/b~c', 'entry:apply:a/b~c:first', 'entry:create:', 'entry:apply::second', 'root:apply', 'root:tree']
        input.words == ['b', 'a']
    }

    def 'complete preflight leaves an active Builder unchanged: #input'() {
        given:
        def document = documentSchema()
        def failure

        when:
        def model = document.Create.With {
            def before = new ArrayList(document.events)
            try { KlumStructuredInput.applyToBuilder(delegate, input, 'bad.yaml') }
            catch (KlumModelException caught) { failure = caught }
            assert failure != null
            assert ((InternalKlumBuilder) delegate).getInstanceAttribute('count') == 2
            assert document.events == before
        }

        then:
        model.count == 2
        failure.message.contains('bad.yaml')
        failure.message.contains(pointer)
        failure.message.contains(contract)
        document.events == ['root:create', 'root:apply', 'root:tree']

        where:
        input                                                         | pointer                    | contract
        [unknown: 1, add: 3]                                           | '/unknown'                  | 'Document_DSL$Builder'
        [add: 3, unknown: 1]                                           | '/unknown'                  | 'Document_DSL$Builder'
        [add: 3, child: [unknown: 'bad']]                               | '/child/unknown'            | 'Child_DSL$Builder'
        [add: 3, entries: [ok: [value: 'first'], 'a/b~c': [name: 'x']]]  | '/entries/a~1b~0c/name'      | 'Entry_DSL$Builder'
        [add: 3, matrix: ['a/b~': [1, 'wrong']]]                        | '/matrix/a~1b~0/1'          | 'Document_DSL$Builder'
        [add: 3, words: [null]]                                        | '/words/0'                  | 'Document_DSL$Builder'
    }

    def 'preflight does not initialize Schema or enum code before a later unknown entry rejects'() {
        given:
        def trapped = schema('''
            package trappedinput
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                static { throw new AssertionError('Schema initialized before complete preflight') }
                Mode mode
            }
            enum Mode {
                READY
                static { throw new AssertionError('Enum initialized during preflight') }
            }
        ''', 'trappedinput.Document')

        when:
        KlumStructuredInput.readRoot(trapped, null, [mode: 'READY', unknown: true], 'trapped.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/unknown')
    }

    def 'preflight snapshots containers before an operation can change caller input'() {
        given:
        def document = documentSchema()
        def input = [mutate: 'go', words: ['first', 'second']]
        document.supplied = input

        when:
        def model = KlumStructuredInput.readRoot(document, null, input, 'snapshot.yaml')

        then:
        model.words == ['first', 'second']
        input.words.empty
        input.unknown
    }

    def 'cyclic containers reject while repeated acyclic data can be prepared twice'() {
        given:
        def document = documentSchema()
        def cycle = [:]
        cycle.child = cycle

        when:
        KlumStructuredInput.readRoot(document, null, cycle, 'cycle.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/child')
        failure.message.contains('cyclic')
        document.events.empty

        when:
        def shared = ['a', 'b']
        def model = KlumStructuredInput.readRoot(document, null, [rows: [shared, shared]], 'shared.yaml')

        then:
        model.rows == [['a', 'b'], ['a', 'b']]
    }

    def 'active route returns the same Builder and rejects use from another thread or after completion'() {
        given:
        def document = documentSchema()
        def captured
        def wrongThread = new AtomicReference<Throwable>()

        when:
        def model = document.Create.With {
            captured = delegate
            def thread = new Thread({
                try { KlumStructuredInput.applyToBuilder(captured, [add: 100], 'thread.yaml') }
                catch (Throwable caught) { wrongThread.set(caught) }
            })
            thread.start()
            thread.join(5000)
            assert !thread.alive
            assert wrongThread.get() instanceof KlumModelException
            def returned = KlumStructuredInput.applyToBuilder(delegate, [add: 3], 'active.yaml')
            assert returned.is(delegate)
            assert !((InternalKlumBuilder) delegate).sealed
            assert document.events == ['root:create', 'add:3', 'root:apply']
        }

        then:
        model.count == 5
        document.events == ['root:create', 'add:3', 'root:apply', 'root:apply', 'root:tree']

        when:
        KlumStructuredInput.applyToBuilder(captured, [:], 'stale.yaml')

        then:
        thrown(KlumModelException)
    }

    def 'operation failure retains cause and external pointer and root cleanup permits another root'() {
        given:
        def document = documentSchema()

        when:
        KlumStructuredInput.readRoot(document, null, [fail: 'authored failure'], 'failure.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('failure.yaml')
        failure.message.contains('/fail')
        failure.cause instanceof IllegalArgumentException
        failure.cause.message == 'authored failure'

        when:
        def model = KlumStructuredInput.readRoot(document, null, [add: 1], 'fresh.yaml')

        then:
        model.count == 3
    }
    def 'standard numeric targets accept exact integral and finite floating values'() {
        given:
        def numeric = schema('''
            package numericinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Numbers {
                byte tiny
                Short small
                int count
                Long large
                float single
                Double floating
                BigInteger whole
                BigDecimal precise
            }
        ''', 'numericinput.Numbers')

        when:
        def model = KlumStructuredInput.readRoot(numeric, null,
                [tiny: -128, small: 32767G, count: 2147483647G, large: 9223372036854775807G,
                 single: 1.5G, floating: 2.5G, whole: 123456789012345678901234567890G, precise: 0.125G], 'numbers.yaml')

        then:
        model.tiny == -128
        model.small == 32767
        model.count == Integer.MAX_VALUE
        model.large == Long.MAX_VALUE
        model.single == 1.5f
        model.floating == 2.5d
        model.whole == 123456789012345678901234567890G
        model.precise == 0.125G
    }

    def 'unsupported numeric values reject before construction at #pointer'() {
        given:
        def numeric = schema('''
            package invalidnumericinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Numbers {
                static { throw new AssertionError('Allocated invalid numeric root') }
                byte tiny
                int count
                float single
                double floating
                BigInteger whole
            }
        ''', 'invalidnumericinput.Numbers')

        when:
        KlumStructuredInput.readRoot(numeric, null, payload, 'numeric-error.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('numeric-error.yaml')
        failure.message.contains(pointer)
        failure.message.contains('expected')
        failure.message.contains('actual')

        where:
        payload                                  | pointer
        [tiny: 128]                              | '/tiny'
        [count: 2147483648G]                      | '/count'
        [count: 1.5G]                            | '/count'
        [count: '1']                             | '/count'
        [whole: 1.25G]                           | '/whole'
        [single: 1E100G]                         | '/single'
        [floating: Double.POSITIVE_INFINITY]     | '/floating'
        [floating: Double.NaN]                   | '/floating'
        [count: new ExplosiveNumber()]           | '/count'
    }

    private static final class ExplosiveNumber extends Number {
        int intValue() { throw new AssertionError('Custom numeric conversion') }
        long longValue() { throw new AssertionError('Custom numeric conversion') }
        float floatValue() { throw new AssertionError('Custom numeric conversion') }
        double doubleValue() { throw new AssertionError('Custom numeric conversion') }
        String toString() { throw new AssertionError('Custom numeric formatting') }
    }

    def 'exact declared signatures preserve rename ignore primitive wrapper and binary inheritance'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
            @interface JsonIgnore { boolean value() default true }
        '''
        def choice = schema('''
            package exactinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL abstract class Base {
                List<String> trace
                @Builder.Method void inherited(String value) { trace << value }
            }
            @DSL class Choice extends Base {
                @Builder.Method @JsonProperty('primitive') void choose(int value) { trace << "primitive:$value".toString() }
                @Builder.Method @JsonProperty('wrapper') void choose(Integer value) { trace << "wrapper:$value".toString() }
                @Builder.Method @JsonProperty('admitted') void filtered(int value) { trace << "admitted:$value".toString() }
                @Builder.Method @JsonIgnore(true) void filtered(Integer value) { trace << 'excluded' }
            }
        ''', 'exactinput.Choice')

        when:
        def model = KlumStructuredInput.readRoot(choice, null, [inherited: 'base', primitive: 7, wrapper: 8, admitted: 9], 'choice.yaml')

        then:
        model.trace == ['base', 'primitive:7', 'wrapper:8', 'admitted:9']

        when:
        KlumStructuredInput.readRoot(choice, null, [choose: 1], 'native.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/choose')
        failure.message.contains('expected an admitted entry')
    }

    def 'keyed roots and direct declared key derivation keep key members outside domain input'() {
        given:
        def keyed = schema('''
            package keyedinput
            import com.blackbuild.klum.ast.*
            @DSL class Root {
                @Key String name
                @Field(key = Field.FieldName) Child child
            }
            @DSL class Child { @Key String name; String value }
        ''', 'keyedinput.Root')

        when:
        def model = KlumStructuredInput.readRoot(keyed, 'root-key', [child: [value: 'payload']], 'keyed.yaml')

        then:
        model.name == 'root-key'
        model.child.name == 'child'
        model.child.value == 'payload'

        when:
        KlumStructuredInput.readRoot(keyed, 'root-key', [name: 'forbidden'], 'bad-key.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/name')
    }

    def 'late validation preserves the framework failure type without an invented input leaf'() {
        given:
        def validated = schema('''
            package validatedinput
            import com.blackbuild.klum.ast.*
            @DSL class Root {
                String value
                @Validate void checked() { assert value == 'accepted' }
            }
        ''', 'validatedinput.Root')

        when:
        KlumStructuredInput.readRoot(validated, null, [value: 'rejected'], 'late.yaml')

        then:
        def failure = thrown(KlumValidationException)
        !failure.message.contains('late.yaml')
        !failure.message.contains('/value')
    }

    def 'all remaining supported field containers are populated through ordinary generated adders'() {
        given:
        def containers = schema('''
            package containerinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Containers {
                Set<String> unique
                NavigableSet<Integer> sorted
                EnumSet<Flag> flags
                NavigableMap<String, Integer> keyed
                Map<String, String> ordinary
            }
            enum Flag { READY, DONE }
        ''', 'containerinput.Containers')

        when:
        def model = KlumStructuredInput.readRoot(containers, null,
                [unique: ['b', 'a', 'b'], sorted: [3, 1, 2], flags: ['DONE', 'READY', 'DONE'],
                 keyed: [b: 2, a: 1], ordinary: ['literal/a~': 'value']], 'containers.yaml')

        then:
        model.unique == ['a', 'b'] as Set
        model.sorted.toList() == [1, 2, 3]
        model.flags*.name() == ['READY', 'DONE']
        model.keyed.keySet().toList() == ['a', 'b']
        model.ordinary == ['literal/a~': 'value']
    }

    def 'invalid root shapes names and scalar kinds reject before allocation: #label'() {
        given:
        def document = documentSchema()

        when:
        KlumStructuredInput.readRoot(document, null, payload, 'shape.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('shape.yaml')
        document.events.empty

        where:
        label           | payload
        'null root'     | null
        'list root'     | []
        'scalar root'   | 'text'
        'numeric key'   | [(1): 'value']
        'null key'      | [(null): 'value']
        'boolean text'  | [enabled: 'true']
        'enum case'     | [mode: 'ready']
        'helper name'   | [copyFrom: [:]]
        'object scalar' | [summary: new Object()]
    }

    def 'execution side effects remain on operation failure while JVM Errors escape and clean up the root'() {
        given:
        def document = documentSchema()
        def capturedFailure

        when:
        def model = document.Create.With {
            try { KlumStructuredInput.applyToBuilder(delegate, [fail: 'after mutation'], 'partial.yaml') }
            catch (KlumModelException caught) { capturedFailure = caught }
            assert ((InternalKlumBuilder) delegate).getInstanceAttribute('count') == 99
        }

        then:
        model.count == 99
        capturedFailure.cause instanceof IllegalArgumentException
        document.events == ['root:create', 'root:apply', 'root:tree']

        when:
        KlumStructuredInput.readRoot(document, null, [explode: 'fatal authored failure'], 'fatal.yaml')

        then:
        def error = thrown(AssertionError)
        error.message == 'fatal authored failure'

        when:
        def fresh = KlumStructuredInput.readRoot(document, null, [add: 1], 'fresh.yaml')

        then:
        fresh.count == 3
    }

    def 'nested root Template application and missing source identity reject through ordinary guards'() {
        given:
        def document = documentSchema()

        when:
        document.Create.With {
            KlumStructuredInput.readRoot(document, null, [:], 'nested-root.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('independent DSL Object factory')
        document.events == ['root:create']

        when:
        document.Create.Template.With {
            KlumStructuredInput.applyToBuilder(delegate, [:], 'template.yaml')
        }

        then:
        thrown(KlumModelException)

        when:
        KlumStructuredInput.readRoot(document, null, [:], '')

        then:
        def emptySource = thrown(KlumModelException)
        emptySource.message.contains('sourceName')
    }

    def 'compiler fixed direct key remains literal when the external field name is renamed'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def plan = schema('''
            package fixedruntime
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.Cluster
            import com.fasterxml.jackson.annotation.JsonProperty
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @DSL abstract class Home {
                @Cluster(value = ManagedZone, fixedKeys = true)
                abstract Map<String, Kitchen> getZones()
            }
            @Retention(RetentionPolicy.RUNTIME) @interface ManagedZone {}
            @DSL class Kitchen { @Key String name; String purpose }
            @DSL class FloorPlan extends Home {
                @ManagedZone @JsonProperty('cooking') Kitchen kitchen
            }
        ''', 'fixedruntime.FloorPlan')

        when:
        def model = KlumStructuredInput.readRoot(plan, null, [cooking: [purpose: 'structured']], 'fixed.yaml')

        then:
        model.kitchen.name == 'kitchen'
        model.kitchen.purpose == 'structured'
    }

}
