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
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovy.transform.CompileStatic
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@Issue('952')
class StructuredNestedScopeProbeTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema() {
        createClass '''
            package nestedscope
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                static List<String> events = []
                Child child
                Map<String, Entry> entries
                @Builder.Method void scoped(@DelegatesToBuilder(Child) Closure body) {
                    events << 'scope:before'
                    child(body)
                    events << 'scope:after'
                }
                @Field void virtual(Child value) {
                    events << "virtual:$value.value".toString()
                }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            @DSL class Child {
                @Owner Deployment owner
                String value
                @PostCreate void created() { Deployment.events << 'child:create' }
                @PostApply void applied() { Deployment.events << "child:apply:$value".toString() }
                @PostTree void tree() { Deployment.events << "child:tree:${owner != null}".toString() }
            }
            @DSL class Entry {
                @Key String name
                @Owner Deployment owner
                String value
                @PostCreate void created() { Deployment.events << "entry:create:$name".toString() }
                @PostApply void applied() { Deployment.events << "entry:apply:$name:$value".toString() }
                @PostTree void tree() { Deployment.events << "entry:tree:${owner != null}".toString() }
            }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('nestedscope.Deployment', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'ordinary and mapping relationships preserve lifecycle and owner key identity'() {
        given:
        def deployment = schema()

        when:
        def ordinary = deployment.Create.With {
            child { value 'direct' }
            entry('a/b~c') { value 'mapped' }
        }
        def ordinaryEvents = deployment.events.toList()
        deployment.events.clear()
        def mapped = deployment.Create.FromStruct([child: [value: 'direct'], entries: ['a/b~c': [value: 'mapped']]])

        then:
        deployment.events == ordinaryEvents
        ordinary.child.owner.is(ordinary)
        mapped.child.owner.is(mapped)
        mapped.entries['a/b~c'].name == 'a/b~c'
        mapped.entries['a/b~c'].owner.is(mapped)
        mapped.child.value == ordinary.child.value
        mapped.entries['a/b~c'].value == ordinary.entries['a/b~c'].value
        ordinaryEvents.count('child:create') == 1
        ordinaryEvents.count('child:apply:direct') == 1
        ordinaryEvents.count('entry:create:a/b~c') == 1
        ordinaryEvents.count('entry:apply:a/b~c:mapped') == 1
        ordinaryEvents.containsAll(['child:tree:true', 'entry:tree:true'])
    }

    def 'virtual Consumer callback preserves ordinary construction without a closure adapter'() {
        given:
        def deployment = schema()
        def child = artifacts.loadClass('nestedscope.Child')
        def contract = artifacts.loadClass('nestedscope.Deployment_DSL$Builder')

        when:
        def ordinaryChild
        deployment.Create.With {
            ordinaryChild = virtual { value 'virtual' }
        }
        def ordinaryEvents = deployment.events.toList()
        deployment.events.clear()
        def configuredChild
        def model = deployment.Create.With {
            configuredChild = StructuredNestedScopeProbeTest.configureVirtual((InternalKlumBuilder) delegate, child,
                    { nested -> nested.value('virtual') } as Consumer)
        }

        then:
        deployment.events == ordinaryEvents
        ordinaryEvents == ['root:create', 'child:create', 'child:apply:virtual', 'virtual:virtual',
                           'root:apply', 'root:tree', 'child:tree:true']
        configuredChild.completedModel.owner.is(model)
        contract.getMethod('virtual').returnType == artifacts.loadClass('nestedscope.Child_DSL$Builder')
        contract.getMethod('scoped', Closure).parameterAnnotations[0].any { it instanceof DelegatesTo }
        !contract.methods.any { it.parameterTypes.contains(Consumer) }
    }

    def 'returned virtual Builder is too late to preserve callback and sink timing'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.With { virtual { value 'configured' } }
        def ordinaryEvents = deployment.events.toList()
        deployment.events.clear()
        deployment.Create.With {
            def nested = virtual()
            nested.applyStruct([value: 'configured'])
        }

        then:
        ordinaryEvents == ['root:create', 'child:create', 'child:apply:configured', 'virtual:configured',
                           'root:apply', 'root:tree', 'child:tree:true']
        deployment.events == ['root:create', 'child:create', 'child:apply:null', 'virtual:null',
                              'child:apply:configured', 'root:apply', 'root:tree', 'child:tree:true']
    }

    def 'fixed closure delegate exposes no public callback entry and mapping never runs its wrapper'() {
        given:
        def deployment = schema()
        def contract = artifacts.loadClass('nestedscope.Deployment_DSL$Builder')
        def method = contract.getMethod('scoped', Closure)

        when:
        deployment.Create.With { scoped { value 'wrapped' } }
        def ordinaryEvents = deployment.events.toList()
        deployment.events.clear()
        deployment.Create.FromStruct([scoped: [value: 'wrapped']], 'scope.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('scope.yaml')
        failure.message.contains('/scoped')
        ordinaryEvents == ['root:create', 'scope:before', 'child:create', 'child:apply:wrapped',
                           'scope:after', 'root:apply', 'root:tree', 'child:tree:true']
        deployment.events == ['root:create']
        method.parameterAnnotations[0].find { it instanceof DelegatesTo }.value() ==
                artifacts.loadClass('nestedscope.Child_DSL$Builder')
        contract.methods.findAll { it.name == 'scoped' }*.parameterTypes*.toList() == [[Closure]]
    }

    def 'virtual mapping is rejected without running the virtual sink'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.FromStruct([virtual: [value: 'never']], 'virtual.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('virtual.yaml')
        failure.message.contains('/virtual')
        deployment.events == ['root:create']
    }

    def 'public contract keeps existing relationship roles and does not publish an additional scope'() {
        given:
        def deployment = schema()
        def contract = artifacts.loadClass('nestedscope.Deployment_DSL$Builder')

        when:
        def plan = new InputContractResolver().read(deployment)

        then:
        plan.entries().keySet() == ['child', 'entries'] as Set
        plan.entries()['child'].role() == CompiledBuilderSchema.Role.OWNED_OBJECT
        plan.entries()['entries'].role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP
        plan.entries()['entries'].excludedKey() == 'name'
        contract.getMethod('getChild').getAnnotation(GeneratedInput.Field) != null
        contract.methods.findAll { it.name == 'virtual' }.every {
            it.getAnnotation(GeneratedInput.Selection) == null && it.getAnnotation(GeneratedInput.Operation) == null
        }
        deployment.events.empty
    }

    def 'fresh nested failure retains the prior sibling and stops before attachment and later siblings'() {
        given:
        def deployment = schema()
        def captured

        when:
        deployment.Create.With {
            captured = delegate
            delegate.applyStruct([child: [value: 'prior'], entries: [
                    'a/b~c': [value: 'reached', unknown: true], later: [value: 'unreached']]], 'nested.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('nested.yaml')
        failure.message.contains('/entries/a~1b~0c/unknown')
        captured.child.value == 'prior'
        captured.entries.isEmpty()
        deployment.events == ['root:create', 'child:create', 'child:apply:prior', 'entry:create:a/b~c']

        when: 'the failed root has left its session'
        captured.applyStruct([:], 'inactive.yaml')

        then:
        thrown(KlumModelException)

        when: 'a subsequent root starts cleanly'
        def model = deployment.Create.FromStruct([child: [value: 'fresh']])

        then:
        model.child.value == 'fresh'
        model.child.owner.is(model)
    }

    def 'existing children retain Builder identity and keys when the mapping reenters them'() {
        given:
        def deployment = schema()
        def firstChild
        def firstEntry
        def captured

        when:
        def model = deployment.Create.With {
            captured = delegate
            firstChild = child { value 'first' }
            firstEntry = entry('stable') { value 'first' }
            delegate.applyStruct([child: [value: 'second'], entries: [stable: [value: 'second']]])
            assert child.is(firstChild)
            assert entries.stable.is(firstEntry)
            assert !((InternalKlumBuilder) firstChild).sealed
            assert !((InternalKlumBuilder) firstEntry).sealed
        }

        then:
        model.child.value == 'second'
        model.entries.stable.value == 'second'
        model.entries.stable.name == 'stable'
        model.entries.stable.owner.is(model)
        deployment.events.count('child:create') == 1
        deployment.events.count('entry:create:stable') == 1
        deployment.events.count('child:apply:first') == 1
        deployment.events.count('child:apply:second') == 1
        deployment.events.count('entry:apply:stable:first') == 1
        deployment.events.count('entry:apply:stable:second') == 1
        ((InternalKlumBuilder) captured).sealed
    }

    def 'outer Map identity is established before an inner key rejects at its reached position'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.FromStruct([child: [value: 'prior'], entries: [stable: [name: 'stable', value: 'never']]])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/entries/stable/name')
        deployment.events == ['root:create', 'child:create', 'child:apply:prior', 'entry:create:stable']
    }

    def 'prepared nested Builder retains same thread session and sealing controls'() {
        given:
        def deployment = schema()
        def nested
        def wrongThread = new AtomicReference<Throwable>()

        when:
        def model = deployment.Create.With {
            child {
                nested = delegate
                def thread = new Thread({
                    try { nested.applyStruct([value: 'forbidden']) }
                    catch (Throwable failure) { wrongThread.set(failure) }
                })
                thread.start()
                thread.join(5000)
                assert !thread.alive
                assert wrongThread.get() instanceof KlumModelException
                delegate.applyStruct([value: 'accepted'])
                assert !((InternalKlumBuilder) delegate).sealed
                assert ((InternalKlumBuilder) delegate).completedModel == null
            }
        }

        then:
        model.child.value == 'accepted'
        model.child.owner.is(model)
        deployment.events.count('child:create') == 1
        deployment.events.count('child:apply:accepted') == 2

        when:
        nested.applyStruct([value: 'sealed'])

        then:
        thrown(KlumModelException)
        model.child.value == 'accepted'
    }

    @CompileStatic
    private static InternalKlumBuilder<?> configureVirtual(InternalKlumBuilder<?> root, Class child,
                                                          Consumer configuration) {
        root.createSingleChildWithConfiguration('virtual', child, false, null, configuration)
    }
}
