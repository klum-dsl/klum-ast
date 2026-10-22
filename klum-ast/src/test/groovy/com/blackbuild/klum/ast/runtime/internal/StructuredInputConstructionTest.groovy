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
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver
import spock.lang.Issue

import java.util.function.Consumer

@Issue('932')
class StructuredInputConstructionTest extends AbstractDSLSpec {
    private void schema() {
        createClass '''
            package configuredconstruction
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                static List<String> events = []
                Child child
                @Field(FieldType.LINK) Environment environment
                Map<String, Entry> entries
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            @DSL class Environment { String name }
            @DSL class Child {
                @Owner Document owner
                String value = 'default'
                @PostCreate void created() { Document.events << "child:create:$value".toString() }
                @PostApply void applied() { Document.events << "child:apply:$value".toString() }
            }
            @DSL class Entry {
                @Key String name
                @Owner Document owner
                String value
                @PostCreate void created() { Document.events << "entry:create:$name".toString() }
                @PostApply void applied() { Document.events << "entry:apply:$name:$value".toString() }
            }
        '''
    }

    def 'Consumer and legacy preparation share fresh and reused child lifecycle: consumer=#consumer'() {
        given:
        schema()
        def document = getClass('configuredconstruction.Document')
        def child = getClass('configuredconstruction.Child')
        def entry = getClass('configuredconstruction.Entry')
        def capturedRoot
        def firstChild
        def secondChild
        def firstEntry
        def secondEntry
        def populate = { InternalKlumBuilder root ->
            capturedRoot = root
            PhaseDriver.requireCurrentConstructionSession(root)
            if (consumer) {
                firstChild = StructuredInputConstructionTest.configuredSingle(root, 'child', child, false, null, { it.value('one') } as Consumer)
                secondChild = StructuredInputConstructionTest.configuredSingle(root, 'child', child, false, null, { it.value('two') } as Consumer)
                firstEntry = StructuredInputConstructionTest.configuredMap(root, 'entries', entry, false, 'a/b~c', { it.value('one') } as Consumer)
                secondEntry = StructuredInputConstructionTest.configuredMap(root, 'entries', entry, false, 'a/b~c', { it.value('two') } as Consumer)
            } else {
                firstChild = root.createSingleChild([value: 'one'], 'child', child, false, null, null)
                secondChild = root.createSingleChild([value: 'two'], 'child', child, false, null, null)
                firstEntry = root.addNewDslElementToMap([value: 'one'], 'entries', entry, false, 'a/b~c', null)
                secondEntry = root.addNewDslElementToMap([value: 'two'], 'entries', entry, false, 'a/b~c', null)
            }
            assert firstChild.is(secondChild)
            assert firstEntry.is(secondEntry)
            assert !firstChild.sealed
            assert !firstEntry.sealed
            assert !document.events.contains('root:tree')
        }

        when:
        def model = consumer
                ? FactoryHelper.createWithConfiguration(document, null, populate as Consumer)
                : FactoryHelper.create(document, null, null, { populate(delegate) })

        then:
        document.events == ['root:create', 'child:create:default', 'child:apply:one', 'child:apply:two',
                'entry:create:a/b~c', 'entry:apply:a/b~c:one', 'entry:apply:a/b~c:two', 'root:apply', 'root:tree']
        model.child.value == 'two'
        model.child.owner.is(model)
        model.entries['a/b~c'].owner.is(model)
        model.entries['a/b~c'].name == 'a/b~c'
        firstChild.completedModel.is(model.child)
        firstEntry.completedModel.is(model.entries['a/b~c'])
        capturedRoot.sealed

        where:
        consumer << [false, true]
    }

    def 'Consumer apply adds one PostApply and does not complete or replay PostCreate'() {
        given:
        schema()
        def document = getClass('configuredconstruction.Document')
        def captured

        when:
        def model = document.Create.With {
            captured = delegate
            def returned = StructuredInputConstructionTest.configuredApply((InternalKlumBuilder) delegate, { root ->
                assert !root.sealed
                assert root.completedModel == null
            } as Consumer)
            assert returned.is(delegate)
            assert document.events == ['root:create', 'root:apply']
        }

        then:
        captured.completedModel.is(model)
        document.events == ['root:create', 'root:apply', 'root:apply', 'root:tree']
    }

    def 'Consumer root failure cleans up the session before a subsequent root'() {
        given:
        schema()
        def document = getClass('configuredconstruction.Document')
        def original = new IllegalArgumentException('configuration failure')
        def captured

        when:
        FactoryHelper.createWithConfiguration(document, null, { root ->
            captured = root
            throw original
        } as Consumer)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.is(original)
        document.events == ['root:create']

        when:
        PhaseDriver.requireCurrentConstructionSession(captured)

        then:
        thrown(KlumModelException)

        when:
        def model = FactoryHelper.createWithConfiguration(document, null, { } as Consumer)

        then:
        model != null
        document.events == ['root:create', 'root:create', 'root:apply', 'root:tree']
    }
    def 'Consumer preparation preserves Template defaults and completed LINK identity'() {
        given:
        schema()
        def document = getClass('configuredconstruction.Document')
        def child = getClass('configuredconstruction.Child')
        def environment = getClass('configuredconstruction.Environment').Create.With(name: 'supplied')
        def template = child.Create.Template.With(value: 'templated')

        when:
        def model = TemplateManager.withTemplate(child, template) {
            FactoryHelper.createWithConfiguration(document, null, { root ->
                root.environment(environment)
                StructuredInputConstructionTest.configuredSingle(root, 'child', child, false, null, { nested ->
                    assert nested.getInstanceAttribute('value') == 'templated'
                    nested.value('configured')
                } as Consumer)
            } as Consumer)
        }

        then:
        model.environment.is(environment)
        model.child.value == 'configured'
        model.child.owner.is(model)
        document.events == ['root:create', 'child:create:templated', 'child:apply:configured', 'root:apply', 'root:tree']
    }

    def 'Consumer root guard rejects nested roots before allocation and completed Builders reject apply'() {
        given:
        schema()
        def document = getClass('configuredconstruction.Document')
        def captured

        when:
        FactoryHelper.createWithConfiguration(document, null, { root ->
            captured = root
            try {
                FactoryHelper.createWithConfiguration(document, null, { throw new AssertionError('Nested configuration ran') } as Consumer)
                throw new AssertionError('Nested root accepted')
            } catch (KlumModelException expected) {
                assert expected.message.contains('independent DSL Object factory')
                assert document.events == ['root:create']
            }
        } as Consumer)

        then:
        document.events == ['root:create', 'root:apply', 'root:tree']

        when:
        StructuredInputConstructionTest.configuredApply((InternalKlumBuilder) captured, { throw new AssertionError('Stale configuration ran') } as Consumer)

        then:
        thrown(KlumModelException)
        document.events == ['root:create', 'root:apply', 'root:tree']
    }

    // These package-private seams are Java calls in production; Groovy 5 correctly omits them from inherited dynamic lookup.
    @CompileStatic
    private static <T> InternalKlumBuilder<T> configuredSingle(InternalKlumBuilder<?> root, String field, Class<T> type,
            boolean explicit, String key, Consumer<InternalKlumBuilder<T>> configuration) {
        root.createSingleChildWithConfiguration(field, type, explicit, key, configuration)
    }

    @CompileStatic
    private static <T> InternalKlumBuilder<T> configuredMap(InternalKlumBuilder<?> root, String field, Class<T> type,
            boolean explicit, String key, Consumer<InternalKlumBuilder<T>> configuration) {
        root.addNewDslElementToMapWithConfiguration(field, type, explicit, key, configuration)
    }

    @CompileStatic
    private static <T> InternalKlumBuilder<T> configuredApply(InternalKlumBuilder<T> root,
            Consumer<InternalKlumBuilder<T>> configuration) {
        root.applyConfiguration(configuration)
    }

}
