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

@Issue('1030')
class StructuredMultipleLiteralAlternativeTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(boolean multiple = true) {
        loader.parseClass '''
            package multipliteral
            import com.blackbuild.klum.ast.*
            @DSL abstract class Element {
                @Key String name
                @Owner Catalog owner
                String value
                @Builder.Method void note(String text) { Catalog.events << "note:$name:$text".toString() }
                @PostCreate void created() { Catalog.events << "create:$name".toString() }
                @PostApply void applied() {
                    Catalog.events << "apply:$name:$value".toString()
                    if (value == 'explode') throw new IllegalStateException('lifecycle trap')
                }
                @PostTree void tree() { Catalog.events << "tree:$name:${owner != null}".toString() }
            }
            @DSL class FixedElement extends Element { String fixedValue }
            @DSL class RollingElement extends Element { String rollingValue }
        ''' + """
            @DSL class Catalog {
                static List<String> events = []
                @Field(alternatives = { ${multiple ? '[fixed: FixedElement, rolling: RollingElement]' : '[fixed: FixedElement]'} })
                Map<String, Element> elements
                @Builder.Method void after(String text) { events << "after:\$text".toString() }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
            }
        """
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('multipliteral.Catalog', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'ordinary Groovy traverses explicit alias groups and keyed entries in call order'() {
        given:
        def catalog = schema()

        when:
        def model = catalog.Create.With {
            elements {
                rolling('b') { rollingValue 'rolling' }
                rolling('c') {}
                fixed('a') { fixedValue 'fixed' }
            }
        }

        then:
        model.elements.keySet().toList() == ['b', 'c', 'a']
        model.elements.b.class.name == 'multipliteral.RollingElement'
        model.elements.a.class.name == 'multipliteral.FixedElement'
        model.elements.b.rollingValue == 'rolling'
        model.elements.a.fixedValue == 'fixed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events == ['root:create', 'create:b', 'apply:b:null', 'create:c', 'apply:c:null',
                           'create:a', 'apply:a:null', 'root:apply', 'tree:b:true', 'tree:c:true', 'tree:a:true']
    }

    def 'one literal Struct preserves ordinary keyed reuse lifecycle and Templates'() {
        given:
        def catalog = schema(false)
        def first
        def reused

        when:
        def recipe = catalog.Create.Template.FromStruct([elements: [fixed: [a: [value: 'recipe']]]])
        catalog.events.clear()
        def model = catalog.Create.With {
            copyFrom(recipe)
            first = delegate.getElements().a
            delegate.applyStruct([elements: [fixed: [a: [value: 'changed'], b: [:]]]])
            reused = delegate.getElements().a
        }

        then:
        first.is(reused)
        model.elements.keySet().toList() == ['a', 'b']
        model.elements.a.value == 'changed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events.count('apply:a:changed') == 1
        catalog.events.count('root:apply') == 2
        catalog.events.findAll { it.startsWith('tree:') } == ['tree:a:true', 'tree:b:true']
    }

    def 'ordinary cross-alias key collision fails before incompatible body effects'() {
        given:
        def catalog = schema()
        def captured
        String message

        when:
        def model = catalog.Create.With {
            elements { fixed('same') { value 'retained' } }
            captured = delegate.getElements().same
            try { elements { rolling('same') { note 'never' } } }
            catch (Exception failure) { message = failure.message }
        }

        then:
        message.contains('same')
        model.elements.same.class.name == 'multipliteral.FixedElement'
        model.elements.same.value == 'retained'
        captured != null
        !catalog.events.any { it.startsWith('note:') }
        catalog.events.count('create:same') == 1
    }

    def 'one literal Struct failure retains prior child and escapes the exact source path'() {
        given:
        def catalog = schema(false)
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [first: [:], 'a/~': [note: 'kept', name: 'a/~'], later: [:]]], after: 'never'], 'catalog.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('catalog.yaml')
        failure.message.contains('/elements/fixed/a~1~0/name')
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['first']
        catalog.events == ['root:create', 'create:first', 'apply:first:null', 'create:a/~', 'note:a/~:kept']
    }

    def 'current multi-literal Struct admission rejects the relationship before child creation'() {
        given:
        def catalog = schema()

        when:
        catalog.Create.FromStruct([elements: [fixed: [a: [:]], rolling: [b: [:]]]], 'catalog.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements')
        catalog.events == ['root:create']
        !new InputContractResolver().read(catalog).entries().containsKey('elements')
    }
}
