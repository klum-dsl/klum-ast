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
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import java.util.concurrent.atomic.AtomicReference

@Issue('1030')
class StructuredLiteralGroupsTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(boolean multiple = true) {
        loader.parseClass '''
            package literalinput
            import com.blackbuild.klum.ast.*
            @DSL class Catalog {
                static List<String> events = []
                @Field(alternatives = { ALIASES }) Map<String, Element> elements
                @Builder.Method void after(String text) { events << "after:$text".toString() }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
            }
            @DSL abstract class Element {
                @Key String name
                @Owner Catalog owner
                String value
                @Builder.Method void note(String text) { Catalog.events << "note:$name:$text".toString() }
                @Builder.Method void last(String text) { Catalog.events << "last:$name:$text".toString() }
                @PostCreate void created() { Catalog.events << "child:create:$name".toString() }
                @PostApply void applied() {
                    Catalog.events << "child:apply:$name:$value".toString()
                    if (value == 'explode') throw new IllegalStateException('child lifecycle trap')
                }
                @PostTree void tree() { Catalog.events << "child:tree:$name:${owner != null}".toString() }
            }
            @DSL class ConcreteElement extends Element {}
            @DSL class OtherElement extends Element { String window }
        '''.replace('ALIASES', multiple ? '[fixed: ConcreteElement, rolling: OtherElement]' : '[fixed: ConcreteElement]')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('literalinput.Catalog', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'ordinary alias groups preserve call order ownership lifecycle and keyed reuse'() {
        given:
        def catalog = schema()
        def first
        def reused

        when:
        def model = catalog.Create.With {
            elements {
                rolling('b') { value 'rolling' }
                fixed('z') { value 'initial' }
                first = fixed('a') { value 'first' }
                reused = fixed('a') { value 'changed' }
            }
        }

        then:
        first.is(reused)
        model.elements.keySet().toList() == ['b', 'z', 'a']
        model.elements.b.class.simpleName == 'OtherElement'
        model.elements.a.class.simpleName == 'ConcreteElement'
        model.elements.a.value == 'changed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events == ['root:create', 'child:create:b', 'child:apply:b:rolling',
                           'child:create:z', 'child:apply:z:initial', 'child:create:a',
                           'child:apply:a:first', 'child:apply:a:changed', 'root:apply',
                           'child:tree:b:true', 'child:tree:z:true', 'child:tree:a:true']
    }

    def 'ordinary cross-alias collision fails before reconfiguration and retains earlier effects'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            elements {
                fixed('same') { value 'kept' }
                rolling('same') { note 'never' }
                rolling('later') {}
            }
        }

        then:
        thrown(Exception)
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['same']
        catalog.events == ['root:create', 'child:create:same', 'child:apply:same:kept']
    }

    def 'ordinary and single literal Struct share Template lifecycle and fresh recipient ownership'() {
        given:
        def catalog = schema(false)

        when:
        def ordinary = catalog.Create.Template.With { elements { fixed('a') { value 'recipe' } } }
        def ordinaryTrace = catalog.events.toList()
        catalog.events.clear()
        def structured = catalog.Create.Template.FromStruct([elements: [fixed: [a: [value: 'recipe']]]])
        def structuredTrace = catalog.events.toList()
        def first = catalog.Create.With { copyFrom(ordinary) }
        def second = catalog.Create.With { copyFrom(structured) }

        then:
        structuredTrace == ordinaryTrace
        first.elements.a.value == second.elements.a.value
        first.elements.a.owner.is(first)
        second.elements.a.owner.is(second)
        !first.elements.a.is(second.elements.a)
    }

    def 'single literal Struct preserves supplied inner order and reached diagnostic partial effects'() {
        given:
        def catalog = schema(false)
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [z: [value: 'kept'], 'a/~': [note: 'kept', name: 'a/~'], later: [:]]], after: 'never'], 'aliases.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('aliases.yaml')
        failure.message.contains('/elements/fixed/a~1~0/name')
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['z']
        catalog.events == ['root:create', 'child:create:z', 'child:apply:z:kept', 'child:create:a/~', 'note:a/~:kept']
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#multiple-literal-alias-groups')
    def 'configures closed literal alias groups in supplied order'() {
        given:
        def catalog = schema()

        when:
        def ordinary = catalog.Create.With {
            elements {
                rolling('b') { value 'rolling'; window 'daily' }
                fixed('z') { value 'last' }
                fixed('a') { value 'first' }
            }
        }
        def trace = catalog.events.toList()
        catalog.events.clear()
        def model = catalog.Create.FromStruct([
            elements: [rolling: [b: [value: 'rolling', window: 'daily']],
                       fixed: [z: [value: 'last'], a: [value: 'first']]]
        ])

        then:
        model.elements.keySet().toList() == ['b', 'z', 'a']
        model.elements.b.window == 'daily'
        model.elements.a.class.simpleName == 'ConcreteElement'
        model.elements.b.class.simpleName == 'OtherElement'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events == trace
        KlumObjectSupport.of(model.elements.b).constructionPath.replace('.FromStruct/', '.With/') ==
            KlumObjectSupport.of(ordinary.elements.b).constructionPath
        KlumObjectSupport.of(model.elements.z).constructionPath.contains('/elements[2]/fixed:')
        KlumObjectSupport.of(model.elements.a).constructionPath.contains('/elements[3]/fixed:')
        new InputContractResolver().read(catalog).entries().elements.alternatives().keySet().toList() == ['fixed', 'rolling']
    }

    def 'supplied group order governs cross-alias collisions: #first'() {
        given:
        def catalog = schema()
        def second = first == 'fixed' ? 'rolling' : 'fixed'
        List<String> messages = []
        List<List<String>> traces = []
        List<Object> states = []

        when:
        [false, true].each { structured ->
            catalog.events.clear()
            catalog.Create.With {
                try {
                    StructuredLiteralGroupsTest.configureCollision(delegate, structured, first, second)
                    assert false
                } catch (Exception failure) {
                    messages << failure.message
                    states << delegate.getElements().same
                }
            }
            traces << catalog.events.toList()
        }

        then:
        messages.size() == 2
        messages[0] == messages[1].replace('/elements[2]/', '/elements/')
        traces[0] == traces[1]
        states*.value == ['kept', 'kept']
        states.every { it.getModelType().simpleName == (first == 'fixed' ? 'ConcreteElement' : 'OtherElement') }
        !traces.flatten().any { it.startsWith('note:') || it.contains('later') }

        where:
        first << ['fixed', 'rolling']
    }

    def 'multiple groups preserve compatible keyed identity during active Builder reuse'() {
        given:
        def catalog = schema()
        def original
        def reused

        when:
        def model = catalog.Create.With {
            elements { rolling('b') { value 'initial' } }
            original = delegate.getElements().b
            delegate.applyStruct([elements: [fixed: [a: [:]], rolling: [b: [value: 'changed']]]])
            reused = delegate.getElements().b
        }

        then:
        original.is(reused)
        model.elements.keySet().toList() == ['b', 'a']
        model.elements.b.value == 'changed'
        catalog.events.count('child:create:b') == 1
        catalog.events.count('child:apply:b:changed') == 1
    }

    def 'later alias failure retains earlier groups and stops subsequent operations: #body'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [rolling: [b: [window: 'kept']],
                                            fixed: [('a/~'): body, later: [:]]], after: 'never'], 'groups.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('groups.yaml')
        failure.message.contains('/elements/fixed/a~1~0/' + member)
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['b']
        catalog.events == ['root:create', 'child:create:b', 'child:apply:b:null',
                           'child:create:a/~', 'note:a/~:kept']

        where:
        body                              | member
        [note: 'kept', name: 'a/~']        | 'name'
        [note: 'kept', name: 'conflict']    | 'name'
        [note: 'kept', window: 'wrong']     | 'window'
        [note: 'kept', value: 3]            | 'value'
    }

    def 'later malformed or unknown groups stop when reached: #tail'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: ([rolling: [b: [:]]] + tail), after: 'never'], 'groups.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(pointer)
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['b']
        catalog.events == ['root:create', 'child:create:b', 'child:apply:b:null']

        where:
        tail              | pointer
        [unknown: [:]]    | '/elements/unknown'
        [fixed: []]       | '/elements/fixed'
        [fixed: [a: []]]  | '/elements/fixed/a'
    }

    def 'multi-alias lifecycle failure keeps earlier groups without attaching failed child'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [rolling: [b: [:]], fixed: [a: [value: 'explode'], later: [:]]]])
        }

        then:
        def failure = thrown(Exception)
        StructuredLiteralGroupsTest.causes(failure).any { it instanceof IllegalStateException && it.message == 'child lifecycle trap' }
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['b']
        catalog.events == ['root:create', 'child:create:b', 'child:apply:b:null', 'child:create:a', 'child:apply:a:explode']
    }

    def 'multi-alias Templates copy independent owned concrete children'() {
        given:
        def catalog = schema()
        Map input = [elements: [rolling: [b: [window: 'daily']], fixed: [a: [value: 'recipe']]]]

        when:
        def template = catalog.Create.Template.FromStruct(input)
        def definitionTrace = catalog.events.toList()
        catalog.events.clear()
        def ordinary = catalog.Create.Template.With {
            elements { rolling('b') { window 'daily' }; fixed('a') { value 'recipe' } }
        }
        def ordinaryTrace = catalog.events.toList()
        def first = catalog.Create.With { copyFrom(template) }
        def second = catalog.Create.With { copyFrom(ordinary) }

        then:
        definitionTrace == ordinaryTrace
        first.elements.keySet().toList() == second.elements.keySet().toList()
        first.elements.b.window == second.elements.b.window
        first.elements.a.value == 'recipe'
        first.elements.every { key, child -> child.owner.is(first) && !child.is(second.elements[key]) }
    }

    def 'empty and omitted alias groups perform zero child calls'() {
        given:
        def catalog = schema()

        when:
        def model = catalog.Create.FromStruct([elements: [rolling: [:], fixed: [:]]])

        then:
        model.elements.isEmpty()
        catalog.events == ['root:create', 'root:apply']
    }

    private static void configureCollision(Object builder, boolean structured, String first, String second) {
        if (structured) {
            builder.applyStruct([elements: [(first): [same: [value: 'kept']], (second): [same: [note: 'never'], later: [:]]]])
        } else {
            builder.elements {
                "$first"('same') { value 'kept' }
                "$second"('same') { note 'never' }
                "$second"('later') {}
            }
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) { result << current }
        result
    }
}
