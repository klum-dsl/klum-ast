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
            @DSL class OtherElement extends Element {}
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

    def 'current multiple alias relationship rejects even one supplied group'() {
        given:
        def catalog = schema()

        when:
        catalog.Create.FromStruct([elements: [fixed: [a: [:]]]], 'aliases.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements')
        catalog.events == ['root:create']
        !new InputContractResolver().read(catalog).entries().containsKey('elements')
    }
}
