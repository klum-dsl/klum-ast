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

import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue

@Issue('1117')
class StandardContainerCopyTest extends AbstractRuntimeTest {
    def setup() {
        createClass('''
            package containers
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.runtime.KlumModelObject

            @DSL class Values implements KlumModelObject {
                Map<String, Object> data = [:]
            }
            class Values$Builder extends TestRuntimeBuilder<Values> {
                Map<String, Object> data = [:]
                Values$Builder(String key) { super(Values) }
            }
        ''')
    }

    def 'ordinary copy recursively isolates immutable nested #shape'() {
        given:
        def target = newBuilderOf('containers.Values')

        when:
        CopyHandler.copyToFrom(target, [data: [nested: nested]])

        then:
        target.data.nested == nested
        !target.data.nested.is(nested)

        where:
        shape | nested
        'Map' | Map.of('items', List.of('one', 'two'))
        'List' | List.of(Map.of('name', 'one'))
    }

    def 'copies qualified #shape in encounter order into independent mutable storage'() {
        given:
        def target = newBuilderOf('containers.Values')

        when:
        CopyHandler.copyToFrom(target, [data: [nested: nested]])
        def copied = target.data.nested

        then:
        copied == nested
        !copied.is(nested)
        copied instanceof Map ? copied.keySet().toList() == nested.keySet().toList() : copied.toList() == nested.toList()

        when:
        if (copied instanceof Map) {
            copied.put('new', 'value')
        } else {
            copied.add('new')
        }

        then:
        copied.size() == nested.size() + 1

        where:
        shape                 | nested
        'List.of empty'       | List.of()
        'List.of singleton'   | List.of('a')
        'List.of pair'        | List.of('b', 'a')
        'List.of many'        | List.of('c', 'b', 'a')
        'List.copyOf'         | List.copyOf(['b', 'a'])
        'emptyList'           | Collections.emptyList()
        'singletonList'       | Collections.singletonList(null)
        'Map.of empty'        | Map.of()
        'Map.of singleton'    | Map.of('a', 'one')
        'Map.of many'         | Map.of('b', 'two', 'a', 'one')
        'Map.ofEntries'       | Map.ofEntries(Map.entry('b', 'two'), Map.entry('a', 'one'))
        'Map.copyOf'          | Map.copyOf([b: 'two', a: 'one'])
        'emptyMap'            | Collections.emptyMap()
        'singletonMap'        | Collections.singletonMap('a', null)
        'Set.of empty'        | Set.of()
        'Set.of singleton'    | Set.of('a')
        'Set.of pair'         | Set.of('b', 'a')
        'Set.of many'         | Set.of('c', 'b', 'a')
        'Set.copyOf'          | Set.copyOf(['b', 'a'])
        'emptySet'            | Collections.emptySet()
        'singleton'           | Collections.singleton(null)
    }

    def 'nested mutable leaves remain isolated in both directions'() {
        given:
        def target = newBuilderOf('containers.Values')
        def leaf = [null, 'initial']
        def nested = Map.of('items', List.of(Map.of('leaf', leaf)))

        when:
        CopyHandler.copyToFrom(target, [data: [nested: nested]])
        def copied = target.data.nested
        copied.items[0].leaf.add('recipient')
        leaf.add('donor')

        then:
        copied.items[0].leaf == [null, 'initial', 'recipient']
        leaf == [null, 'initial', 'donor']
        !copied.items.is(nested.items)
        !copied.items[0].is(nested.items[0])
    }

    def 'ordinary constructible #shape retains its runtime type and encounter order'() {
        given:
        def target = newBuilderOf('containers.Values')

        when:
        CopyHandler.copyToFrom(target, [data: [nested: nested]])
        def copied = target.data.nested

        then:
        copied.getClass() == nested.getClass()
        copied == nested
        !copied.is(nested)
        copied instanceof Map ? copied.keySet().toList() == nested.keySet().toList() : copied.toList() == nested.toList()

        where:
        shape               | nested
        'ArrayList'         | new ArrayList(['b', 'a'])
        'LinkedList'        | new LinkedList(['b', 'a'])
        'LinkedHashMap'     | new LinkedHashMap([b: ['two'], a: ['one']])
        'LinkedHashSet'     | new LinkedHashSet(['b', 'a'])
        'HashMap'           | new HashMap([a: ['one']])
        'HashSet'           | new HashSet(['a'])
        'custom List'       | new CustomList(['b', 'a'])
        'IdentityHashMap'   | identityMap()
    }

    def 'standard tree containers retain #ordering comparator and lookup semantics'() {
        given:
        def target = newBuilderOf('containers.Values')
        def map = new TreeMap((Comparator) comparator)
        map.putAll([b: ['two'], A: ['one']])
        def set = new TreeSet((Comparator) comparator)
        set.addAll(['b', 'A'])

        when:
        CopyHandler.copyToFrom(target, [data: [map: map, set: set]])
        def copiedMap = target.data.map
        def copiedSet = target.data.set

        then:
        copiedMap.comparator().is(comparator)
        copiedSet.comparator().is(comparator)
        copiedMap.keySet().toList() == map.keySet().toList()
        copiedSet.toList() == set.toList()
        !copiedMap.A.is(map.A)
        !copiedSet.is(set)
        copiedMap.containsKey('a') == map.containsKey('a')
        copiedSet.contains('a') == set.contains('a')

        where:
        ordering           | comparator
        'natural'          | null
        'reverse'          | Comparator.reverseOrder()
        'case insensitive' | String.CASE_INSENSITIVE_ORDER
    }

    def 'unsupported #shape reports its constructor boundary without changing semantics'() {
        given:
        def target = newBuilderOf('containers.Values')

        when:
        CopyHandler.copyToFrom(target, [data: [nested: nested]])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(nested.getClass().name)
        failure.message.contains('usable no-argument constructor')
        failure.cause != null

        where:
        shape                    | nested
        'unmodifiable Map'       | Collections.unmodifiableMap(identityMap())
        'unmodifiable List'      | Collections.unmodifiableList(['a'])
        'unmodifiable Set'       | Collections.unmodifiableSet(['a'] as Set)
        'unmodifiable SortedMap' | Collections.unmodifiableSortedMap(new TreeMap([a: 'one']))
        'unmodifiable SortedSet' | Collections.unmodifiableSortedSet(new TreeSet(['a']))
        'fixed-size array List'  | Arrays.asList('a', 'b')
        'custom constructor'     | new ConstructorOnlyList('a', 1)
    }

    def 'Java caller copies immutable values through the same runtime path'() {
        given:
        def target = newBuilderOf('containers.Values')

        when:
        def donor = StandardContainerCopyJavaProbe.copyInto(target)

        then:
        target.data == donor
        !target.data.tree.is(donor.tree)
        !target.data.tree.items.is(donor.tree.items)
    }

    private static Map identityMap() {
        def result = new IdentityHashMap()
        result.put(new String('same'), ['one'])
        result.put(new String('same'), ['two'])
        result
    }

    static class CustomList extends ArrayList {
        CustomList() {}
        CustomList(Collection values) { super(values) }
    }

    static class ConstructorOnlyList extends ArrayList {
        ConstructorOnlyList(String value, int count) { count.times { add(value) } }
    }
}
