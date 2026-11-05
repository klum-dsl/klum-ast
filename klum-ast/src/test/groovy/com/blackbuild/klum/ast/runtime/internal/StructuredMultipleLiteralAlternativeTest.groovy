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
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

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

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#literal-collection-alternative')
    def 'multiple explicit literal groups preserve supplied group and keyed entry order'() {
        given:
        def catalog = schema()
        def ordinary = catalog.Create.With {
            elements {
                rolling('b') { rollingValue 'rolling' }
                rolling('c') {}
                fixed('a') { fixedValue 'fixed' }
            }
        }
        def trace = catalog.events.toList()
        catalog.events.clear()

        when:
        def model = catalog.Create.FromStruct([elements: [
            rolling: [b: [rollingValue: 'rolling'], c: [:]],
            fixed: [a: [fixedValue: 'fixed']]
        ]], 'catalog.yaml')

        then:
        model.elements.keySet().toList() == ['b', 'c', 'a']
        model.elements.b.class.name == 'multipliteral.RollingElement'
        model.elements.a.class.name == 'multipliteral.FixedElement'
        model.elements.b.rollingValue == 'rolling'
        model.elements.a.fixedValue == 'fixed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events == trace
        model.elements.every { key, child ->
            KlumObjectSupport.of(child).constructionPath.replace('.FromStruct/', '.With/') ==
                KlumObjectSupport.of(ordinary.elements[key]).constructionPath
        }
        new InputContractResolver().read(catalog).entries().get('elements').alternatives().keySet().toList() == ['fixed', 'rolling']
    }

    def 'structured aliases execute without public creator or collection scope methods'() {
        given:
        schema()
        artifacts.close()
        ['multipliteral/Catalog_DSL$Builder.class',
         'multipliteral/Catalog_DSL$Builder$CollectionFactory_elements.class'].each { name ->
            File file = new File(compilerConfiguration.targetDirectory, name)
            def writer = new ClassWriter(0)
            new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    method in ['elements', 'element', 'fixed', 'rolling'] ? null :
                        super.visitMethod(access, method, descriptor, signature, exceptions)
                }
            }, 0)
            file.bytes = writer.toByteArray()
        }
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def catalog = Class.forName('multipliteral.Catalog', false, artifacts)
        def builder = artifacts.loadClass('multipliteral.Catalog_DSL$Builder')
        def scope = artifacts.loadClass('multipliteral.Catalog_DSL$Builder$CollectionFactory_elements')
        Map input = [elements: [rolling: [b: [rollingValue: 'rolling'], c: [:]], fixed: [a: [fixedValue: 'fixed']]]]
        def first
        def reused

        when:
        def model = catalog.Create.FromStruct(input)
        def recipe = catalog.Create.Template.FromStruct(input)
        def applied = catalog.Create.With {
            copyFrom(recipe)
            first = delegate.getElements().b
            delegate.applyStruct([elements: [rolling: [b: [value: 'updated']]]])
            reused = delegate.getElements().b
        }

        then:
        !builder.declaredMethods.any { it.name in ['elements', 'element'] }
        !scope.declaredMethods.any { it.name in ['fixed', 'rolling'] }
        model.elements.keySet().toList() == ['b', 'c', 'a']
        model.elements.b.class.name == 'multipliteral.RollingElement'
        model.elements.a.class.name == 'multipliteral.FixedElement'
        model.elements.b.rollingValue == 'rolling'
        model.elements.a.fixedValue == 'fixed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        applied.elements.every { key, child -> child.name == key && child.owner.is(applied) }
        first.is(reused)
        applied.elements.b.value == 'updated'
        catalog.events.contains('tree:b:true')
        catalog.events.contains('tree:a:true')
    }

    def 'each alias alone and empty groups remain valid: #groups'() {
        given:
        def catalog = schema()

        when:
        def model = catalog.Create.FromStruct([elements: groups])

        then:
        model.elements.keySet().toList() == keys
        catalog.events.count('root:apply') == 1

        where:
        groups                                        | keys
        [:]                                           | []
        [rolling: [:], fixed: [:]]                     | []
        [rolling: [b: [:]]]                            | ['b']
        [fixed: [a: [:]]]                              | ['a']
        [fixed: [(''): [:], fixed: [:]], rolling: [:]]  | ['', 'fixed']
    }

    def 'multi-alias active Builder reuse and Template recipes retain keyed identity'() {
        given:
        def catalog = schema()
        def first
        def reused

        when:
        def recipe = catalog.Create.Template.FromStruct([elements: [rolling: [b: [value: 'recipe']], fixed: [a: [:]]]])
        catalog.events.clear()
        def model = catalog.Create.With {
            copyFrom(recipe)
            first = delegate.getElements().b
            delegate.applyStruct([elements: [rolling: [b: [value: 'changed']], fixed: [a: [value: 'fixed']]]])
            reused = delegate.getElements().b
        }

        then:
        first.is(reused)
        model.elements.keySet().toList() == ['b', 'a']
        model.elements.b.value == 'changed'
        model.elements.a.value == 'fixed'
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
        catalog.events.count('apply:b:changed') == 1
        catalog.events.count('apply:a:fixed') == 1
        catalog.events.count('root:apply') == 2
        catalog.events.findAll { it.startsWith('tree:') } == ['tree:b:true', 'tree:a:true']
    }

    def 'a cross-alias repeated key has the ordinary explicit-type collision in #firstAlias order'() {
        given:
        def catalog = schema()
        def retained
        List<String> messages = []

        when:
        [false, true].each { structured ->
            catalog.Create.With {
                try {
                    if (structured) {
                        delegate.applyStruct([elements: [(firstAlias): [same: [value: 'retained']], (secondAlias): [same: [note: 'never'], later: [:]]]])
                    } else if (firstAlias == 'fixed') {
                        elements { fixed('same') { value 'retained' }; rolling('same') { note 'never' } }
                    } else {
                        elements { rolling('same') { value 'retained' }; fixed('same') { note 'never' } }
                    }
                    assert false
                } catch (Exception failure) { messages << failure.message }
                retained = delegate.getElements().same
            }
        }

        then:
        messages.size() == 2
        messages[0] == messages[1]
        retained.value == 'retained'
        catalog.events.count('create:same') == 2
        !catalog.events.any { it.startsWith('note:') || it == 'create:later' }

        where:
        firstAlias | secondAlias
        'fixed'    | 'rolling'
        'rolling'  | 'fixed'
    }

    def 'a reached second group failure keeps earlier effects and the exact pointer: #groups'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [a: [:]]] + groups, after: 'never'], 'catalog.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('catalog.yaml')
        failure.message.contains(pointer)
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['a']
        catalog.events.take(3) == ['root:create', 'create:a', 'apply:a:null']
        !catalog.events.any { it == 'create:later' || it == 'root:apply' || it.startsWith('after:') }

        where:
        groups                                                    | pointer
        [unknown: [:]]                                            | '/elements/unknown'
        [RollingElement: [:]]                                     | '/elements/RollingElement'
        [rolling: null]                                           | '/elements/rolling'
        [rolling: []]                                             | '/elements/rolling'
        [rolling: [(42): [:]]]                                    | '/elements/rolling'
        [rolling: ['a/~': [note: 'kept', name: 'a/~'], later: [:]]]  | '/elements/rolling/a~1~0/name'
        [rolling: ['a/~': [name: 'conflict'], later: [:]]]           | '/elements/rolling/a~1~0/name'
        [rolling: [b: [fixedValue: 'wrong subtype'], later: [:]]]    | '/elements/rolling/b/fixedValue'
        [rolling: [b: [value: 42], later: [:]]]                     | '/elements/rolling/b/value'
        [rolling: [b: [owner: [:]], later: [:]]]                    | '/elements/rolling/b/owner'
        [rolling: [b: ['@type': 'FixedElement'], later: [:]]]        | '/elements/rolling/b/@type'
    }

    def 'second alias lifecycle failure preserves cause and stops attachment'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [a: [:]], rolling: [b: [value: 'explode'], later: [:]]], after: 'never'])
        }

        then:
        def failure = thrown(Exception)
        causes(failure).any { it instanceof IllegalStateException && it.message == 'lifecycle trap' }
        captured.getInstanceAttributeOrGetter('elements').keySet().toList() == ['a']
        catalog.events == ['root:create', 'create:a', 'apply:a:null', 'create:b', 'apply:b:explode']
    }

    def 'second alias cyclic input rejects and cleanup permits later reuse'() {
        given:
        def catalog = schema()
        Map groups = [fixed: [a: [:]], rolling: [:]]
        groups.rolling.b = groups
        String message

        when:
        def model = catalog.Create.With {
            try { delegate.applyStruct([elements: groups]) }
            catch (KlumModelException failure) { message = failure.message }
            delegate.applyStruct([elements: [rolling: [b: [:]]]])
        }

        then:
        message.contains('/elements/rolling/b')
        message.contains('cyclic container')
        model.elements.keySet().toList() == ['a', 'b']
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) {
            result << current
        }
        result
    }
}
