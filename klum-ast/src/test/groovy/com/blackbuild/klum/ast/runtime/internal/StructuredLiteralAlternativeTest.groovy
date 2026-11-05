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

@Issue('952')
class StructuredLiteralAlternativeTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema() {
        loader.parseClass '''
            package literalinput
            import com.blackbuild.klum.ast.*
            @DSL class Catalog {
                static List<String> events = []
                @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
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
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('literalinput.Catalog', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#literal-collection-alternative')
    def 'configures a concrete element through its literal collection alias'() {
        given:
        def catalog = schema()

        when:
        def ordinary = catalog.Create.With {
            elements { fixed('primary') { value 'configured' } }
        }
        def trace = catalog.events.toList()
        catalog.events.clear()
        def model = catalog.Create.FromStruct([elements: [fixed: [primary: [value: 'configured']]]])

        then:
        model.elements.primary.class.name == 'literalinput.ConcreteElement'
        model.elements.primary.value == 'configured'
        model.elements.primary.name == 'primary'
        model.elements.primary.owner.is(model)
        catalog.events == trace
        trace == ['root:create', 'child:create:primary', 'child:apply:primary:configured', 'root:apply', 'child:tree:primary:true']
        KlumObjectSupport.of(model.elements.primary).constructionPath.replace('.FromStruct/', '.With/') == KlumObjectSupport.of(ordinary.elements.primary).constructionPath
    }

    def 'malformed alias envelopes reject before child construction: #input'() {
        given:
        def catalog = schema()

        when:
        catalog.Create.FromStruct([elements: input], 'catalog.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(pointer)
        catalog.events == ['root:create']

        where:
        input                                   | pointer
        null                                    | '/elements'
        []                                      | '/elements'
        [unknown: [:]]                          | '/elements/unknown'
        [(42): [:]]                             | '/elements'
        [ConcreteElement: [:]]                  | '/elements/ConcreteElement'
        [primary: [fixed: [:]]]                 | '/elements/primary'
        [fixed: null]                           | '/elements/fixed'
        [fixed: []]                             | '/elements/fixed'
        [fixed: [value: 'configured']]           | '/elements/fixed/value'
        [fixed: [(null): [:]]]                   | '/elements/fixed'
        [fixed: [(42): [:]]]                     | '/elements/fixed'
        [fixed: [(new StringBuilder('x')): [:]]]  | '/elements/fixed'
        [fixed: [primary: null]]                 | '/elements/fixed/primary'
        [fixed: [primary: []]]                   | '/elements/fixed/primary'
        [fixed: [primary: 'value']]              | '/elements/fixed/primary'
    }

    def 'outer key is authoritative and invalid body stops in supplied order: #body'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [earlier: [value: 'kept'], 'a/b~c': body + [last: 'never'], later: [:]]], after: 'never'], 'catalog.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements/fixed/a~1b~0c/' + member)
        captured.getInstanceAttributeOrGetter('elements').keySet() == ['earlier'] as Set
        catalog.events == ['root:create', 'child:create:earlier', 'child:apply:earlier:kept', 'child:create:a/b~c', 'note:a/b~c:retained']

        where:
        body                                            | member
        [note: 'retained', name: 'a/b~c']                | 'name'
        [note: 'retained', name: 'conflict']              | 'name'
        [note: 'retained', value: 9]                     | 'value'
        [note: 'retained', value: null]                  | 'value'
        [note: 'retained', unknown: 'x']                 | 'unknown'
        [note: 'retained', owner: [:]]                   | 'owner'
        [note: 'retained', '@type': 'ConcreteElement']    | '@type'
    }

    def 'later unknown alias preserves an earlier successful child'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [primary: [:]], unknown: [:]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements/unknown')
        captured.getInstanceAttributeOrGetter('elements').keySet() == ['primary'] as Set
        catalog.events == ['root:create', 'child:create:primary', 'child:apply:primary:null']
    }

    def 'reuse retains identity and partial body effects with one PostApply per successful call'() {
        given:
        def catalog = schema()
        def first
        def reused

        when:
        def model = catalog.Create.With {
            delegate.applyStruct([elements: [fixed: [primary: [value: 'initial']]]])
            first = delegate.getElements().primary
            delegate.applyStruct([elements: [fixed: [primary: [value: 'changed']]]])
            reused = delegate.getElements().primary
            try {
                delegate.applyStruct([elements: [fixed: [primary: [note: 'retained', value: 9], later: [:]]]])
                assert false
            } catch (KlumModelException expected) { assert expected.message.contains('/primary/value') }
        }

        then:
        first.is(reused)
        model.elements.primary.value == 'changed'
        model.elements.primary.owner.is(model)
        catalog.events == ['root:create', 'child:create:primary', 'child:apply:primary:initial', 'root:apply',
                           'child:apply:primary:changed', 'root:apply', 'note:primary:retained', 'root:apply', 'child:tree:primary:true']
    }

    def 'incompatible existing type has the ordinary explicit-type failure'() {
        given:
        def catalog = schema()
        def other = artifacts.loadClass('literalinput.OtherElement')
        List<String> messages = []

        when:
        [false, true].each { structured ->
            catalog.Create.With {
                element(other, 'primary') { value 'initial' }
                try {
                    StructuredLiteralAlternativeTest.configureExistingElement(delegate, structured)
                    assert false
                } catch (Exception failure) { messages << failure.message }
            }
        }

        then:
        messages.size() == 2
        messages[0] == messages[1]
        !catalog.events.any { it.startsWith('note:') }
    }

    def 'lifecycle failure retains its original cause and does not attach or visit later children'() {
        given:
        def catalog = schema()
        InternalKlumBuilder captured

        when:
        catalog.Create.With {
            captured = (InternalKlumBuilder) delegate
            delegate.applyStruct([elements: [fixed: [primary: [value: 'explode'], later: [:]]]])
        }

        then:
        def failure = thrown(Exception)
        causes(failure).any { it instanceof IllegalStateException && it.message == 'child lifecycle trap' }
        captured.getInstanceAttributeOrGetter('elements').isEmpty()
        catalog.events == ['root:create', 'child:create:primary', 'child:apply:primary:explode']
    }

    def 'empty scopes and arbitrary String keys preserve the ordinary key contract'() {
        given:
        def catalog = schema()

        when:
        def empty = catalog.Create.FromStruct([elements: [:]])
        def emptyAlias = catalog.Create.FromStruct([elements: [fixed: [:]]])
        def model = catalog.Create.FromStruct([elements: [fixed: [(''): [:], fixed: [:], value: [:], 'a/~': [:]]]])

        then:
        empty.elements.isEmpty()
        emptyAlias.elements.isEmpty()
        model.elements.keySet() == ['', 'fixed', 'value', 'a/~'] as Set
        model.elements.every { key, child -> child.name == key && child.owner.is(model) }
    }

    def 'cyclic envelopes reject and traversal cleanup permits a subsequent call: #at'() {
        given:
        def catalog = schema()
        Map input = [elements: [fixed: [primary: [:]]]]
        switch (at) {
            case 'scope': input.elements.fixed = input.elements; break
            case 'alias': input.elements.fixed.primary = input.elements.fixed; break
            case 'body': input.elements.fixed.primary = input; break
        }
        String message

        when:
        def model = catalog.Create.With {
            try { delegate.applyStruct(input) }
            catch (KlumModelException failure) { message = failure.message }
            delegate.applyStruct([elements: [fixed: [primary: [value: 'valid']]]])
        }

        then:
        message.contains('cyclic container')
        model.elements.primary.value == 'valid'

        where:
        at << ['scope', 'alias', 'body']
    }

    def 'Template and active Builder entry share the bounded grammar'() {
        given:
        def catalog = schema()

        when:
        def template = catalog.Create.Template.FromStruct([elements: [fixed: [primary: [value: 'recipe']]]])
        def model = catalog.Create.With {
            copyFrom(template)
            delegate.applyStruct([elements: [fixed: [primary: [value: 'configured']]]])
        }

        then:
        model.elements.primary.value == 'configured'
        model.elements.primary.owner.is(model)
    }

    @Issue('1030')
    def 'custom Factory aliases cannot masquerade as generated literal creators'() {
        given:
        loader.parseClass '''
            package literalcollision
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import com.blackbuild.klum.ast.runtime.KlumFactory
            class Trace { static List<String> events = [] }
            @DSL abstract class Element {
                @Key String name
                String value
                static class Factory extends KlumFactory.Keyed<Element> {
                    protected Factory() { super(Element) }
                    KlumBuilder<ConcreteElement> fixed(String key) {
                        Trace.events << 'factory:no-body'
                        (KlumBuilder<ConcreteElement>) (Object) ConcreteElement.Create.AsBuilder().With(key, null)
                    }
                    KlumBuilder<ConcreteElement> fixed(String key, @DelegatesToBuilder(ConcreteElement) Closure body) {
                        Trace.events << 'factory:body'
                        (KlumBuilder<ConcreteElement>) (Object) ConcreteElement.Create.AsBuilder().With(key, body)
                    }
                }
            }
            @DSL class ConcreteElement extends Element {}
            @DSL class OtherElement extends Element {}
            @DSL class Catalog {
                @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
            }
        '''.replace('[fixed: ConcreteElement]', multiple ? '[fixed: ConcreteElement, rolling: OtherElement]' : '[fixed: ConcreteElement]')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def catalog = Class.forName('literalcollision.Catalog', false, artifacts)
        def trace = Class.forName('literalcollision.Trace', false, artifacts)

        when:
        def ordinary = catalog.Create.With { elements { fixed('primary') { value 'configured' } } }
        def plan = new InputContractResolver().read(catalog)

        then:
        ordinary.elements.primary.value == 'configured'
        trace.events == ['factory:body']
        !plan.entries().containsKey('elements')

        when:
        catalog.Create.FromStruct([elements: [fixed: [primary: [value: 'configured']]]])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements')
        trace.events == ['factory:body']

        where:
        multiple << [false, true]
    }

    @Issue('1030')
    def 'authored #placement collection scope retains ordinary behavior and rejects structured bypass'() {
        given:
        String customScope = "@Builder.Method void elements(Closure body) { Trace.events << 'custom:scope' }"
        String source = '''
            package literalscope
            import com.blackbuild.klum.ast.*
            class Trace { static List<String> events = [] }
            @DSL abstract class Element { @Key String name; String value }
            @DSL class ConcreteElement extends Element {}
            @DSL class OtherElement extends Element {}
        ''' + """
            @DSL class Catalog {
                @Field(alternatives = { ${multiple ? '[fixed: ConcreteElement, rolling: OtherElement]' : '[fixed: ConcreteElement]'} }) Map<String, Element> elements
                ${placement == 'same-class' ? customScope : ''}
            }
            @DSL class SpecializedCatalog extends Catalog {
                ${placement == 'inherited' ? customScope : ''}
            }
        """
        loader.parseClass(source)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def catalog = Class.forName('literalscope.' + (placement == 'inherited' ? 'SpecializedCatalog' : 'Catalog'), false, artifacts)
        def trace = Class.forName('literalscope.Trace', false, artifacts)

        when:
        def ordinary = catalog.Create.With { elements { fixed('primary') { value 'configured' } } }
        def plan = new InputContractResolver().read(catalog)

        then:
        ordinary.elements.isEmpty()
        trace.events == ['custom:scope']
        !plan.entries().containsKey('elements')

        when:
        catalog.Create.FromStruct([elements: [fixed: [primary: [value: 'configured']]]])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements')
        trace.events == ['custom:scope']

        where:
        [placement, multiple] << [['same-class', 'inherited'], [false, true]].combinations()
    }

    def 'literal input obeys ordinary same-thread session and sealing guards'() {
        given:
        def catalog = schema()
        Map input = [elements: [fixed: [primary: [value: 'configured']]]]
        def wrongThread = new AtomicReference<Throwable>()
        def captured

        when:
        def model = catalog.Create.With {
            captured = delegate
            def worker = new Thread({
                try { captured.applyStruct(input) }
                catch (Throwable failure) { wrongThread.set(failure) }
            })
            worker.start()
            worker.join()
            assert wrongThread.get() instanceof KlumModelException
            assert delegate.getElements().isEmpty()
            delegate.applyStruct(input)
        }
        def trace = catalog.events.toList()
        captured.applyStruct(input)

        then:
        thrown(KlumModelException)
        catalog.events == trace
        model.elements.primary.value == 'configured'
        trace.count('child:create:primary') == 1
    }

    private static void configureExistingElement(Object builder, boolean structured) {
        if (structured) {
            builder.applyStruct([elements: [fixed: [primary: [note: 'never']]]])
        } else {
            builder.elements { fixed('primary') { note 'never' } }
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) {
            result << current
        }
        result
    }
}
