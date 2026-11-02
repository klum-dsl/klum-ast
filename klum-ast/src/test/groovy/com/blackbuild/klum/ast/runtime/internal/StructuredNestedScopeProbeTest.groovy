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
import com.blackbuild.klum.ast.runtime.KlumSchemaException
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@Issue('952')
class StructuredNestedScopeProbeTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String additional = '', String root = 'Deployment') {
        // Read the emitted public contracts without the fixture's dynamic class-pool introspection.
        loader.parseClass '''
            package nestedscope
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                static List<String> events = []
                static List<Object> seen = []
                Child child
                Map<String, Entry> entries
                @Builder.Method void scoped(@DelegatesToBuilder(Child) Closure body) {
                    events << 'scope:before'
                    child(body)
                    events << 'scope:after'
                }
                @Field void virtual(Child value) {
                    events << "virtual:$value.value".toString()
                    seen << value
                    if (value.value == 'fail') throw new IllegalArgumentException('virtual sink failed')
                }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            @DSL class Child {
                @Owner Deployment owner
                String value
                @PostCreate void created() { Deployment.events << 'child:create' }
                @PostApply void applied() {
                    Deployment.events << "child:apply:$value".toString()
                    if (value == 'lifecycle-fail') throw new IllegalStateException('child lifecycle failed')
                }
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
        ''' + additional
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('nestedscope.' + root, false, artifacts)
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

    def 'virtual mapping uses the existing configuration interval before its sink'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.FromStruct([virtual: [value: 'configured']], 'virtual.yaml')

        then:
        deployment.events == ['root:create', 'child:create', 'child:apply:configured', 'virtual:configured',
                              'root:apply', 'root:tree', 'child:tree:true']
    }

    def 'public contract associates the virtual sink with its exact existing creator'() {
        given:
        def deployment = schema()
        def contract = artifacts.loadClass('nestedscope.Deployment_DSL$Builder')

        when:
        def plan = new InputContractResolver().read(deployment)

        then:
        plan.entries().keySet() == ['child', 'entries', 'virtual'] as Set
        plan.entries()['child'].role() == CompiledBuilderSchema.Role.OWNED_OBJECT
        plan.entries()['entries'].role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP
        plan.entries()['entries'].excludedKey() == 'name'
        contract.getMethod('getChild').getAnnotation(GeneratedInput.Field) != null
        plan.entries()['virtual'].role().name() == 'VIRTUAL_OBJECT'
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

        when:
        def model = deployment.Create.With {
            child {
                nested = delegate
                StructuredNestedScopeProbeTest.assertWrongThreadApplyRejected(nested)
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

    def 'virtual invocations create distinct owned children in the same session'() {
        given:
        def deployment = schema()

        when:
        def model = deployment.Create.With {
            delegate.applyStruct([virtual: [value: 'first']])
            delegate.applyStruct([virtual: [value: 'second']])
        }
        def children = deployment.seen

        then:
        children.size() == 2
        !children[0].is(children[1])
        children.every { it.completedModel.owner.is(model) }
        deployment.events.count('child:create') == 2
        deployment.events.count('child:apply:first') == 1
        deployment.events.count('child:apply:second') == 1
        deployment.events.count('virtual:first') == 1
        deployment.events.count('virtual:second') == 1
    }

    def 'virtual payload failure leaves an earlier sibling and never calls the sink'() {
        given:
        def deployment = schema()
        def captured

        when:
        deployment.Create.With {
            captured = delegate
            delegate.applyStruct([child: [value: 'prior'], virtual: [value: 'partial', unknown: true],
                                  entries: [later: [value: 'never']]], 'virtual-failure.yaml')
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('virtual-failure.yaml')
        failure.message.contains('/virtual/unknown')
        captured.child.value == 'prior'
        captured.entries.isEmpty()
        deployment.seen.empty
        deployment.events == ['root:create', 'child:create', 'child:apply:prior', 'child:create']
    }

    def 'virtual sink failure preserves ordinary exception and skips later input'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.FromStruct([child: [value: 'prior'], virtual: [value: 'fail'],
                                      entries: [later: [value: 'never']]], 'sink.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('sink.yaml')
        failure.message.contains('/virtual')
        failure.cause instanceof IllegalArgumentException
        failure.cause.message == 'virtual sink failed'
        deployment.events == ['root:create', 'child:create', 'child:apply:prior', 'child:create',
                              'child:apply:fail', 'virtual:fail']
    }

    def 'virtual child lifecycle failure retains ordinary attribution before the sink'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.With { virtual { value 'lifecycle-fail' } }

        then:
        def ordinary = thrown(IllegalStateException)
        ordinary.message == 'child lifecycle failed'

        when:
        deployment.events.clear()
        deployment.Create.FromStruct([virtual: [value: 'lifecycle-fail']], 'lifecycle.yaml')

        then:
        def mapped = thrown(IllegalStateException)
        mapped.message == ordinary.message
        deployment.events == ['root:create', 'child:create', 'child:apply:lifecycle-fail']
        deployment.seen.empty
    }

    def 'inherited virtual family keeps ordinary semantics'() {
        given:
        def deployment = schema('''
            @DSL class Specialized extends Deployment { }
        ''', 'Specialized')

        when:
        def model = deployment.Create.FromStruct([virtual: [value: 'inherited']])

        then:
        deployment.seen.size() == 1
        deployment.seen[0].completedModel.owner.is(model)
        deployment.events.contains('virtual:inherited')
    }

    def 'a redeclared virtual sink is excluded because ordinary selection is not unique'() {
        given:
        def deployment = schema('''
            @DSL class Specialized extends Deployment {
                @Field @Struct.Name('custom') void virtual(Child value) {
                    events << "override:$value.value".toString()
                }
            }
        ''', 'Specialized')

        when:
        def plan = new InputContractResolver().read(deployment)
        deployment.Create.FromStruct([custom: [value: 'overridden']])

        then:
        thrown(KlumModelException)
        !plan.entries().containsKey('virtual')
        !plan.entries().containsKey('custom')
        deployment.events == ['root:create']

        when:
        deployment.Create.With { virtual { value 'ordinary' } }

        then:
        def ordinaryFailure = thrown(KlumSchemaException)
        ordinaryFailure.message.contains('more than one virtual setter')
    }

    def 'virtual scope requires a mapping before child allocation: #payload'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.FromStruct([virtual: payload], 'payload.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/virtual')
        deployment.events == ['root:create']

        where:
        payload << [null, 42, true, 'text', ['value']]
    }

    def 'virtual literal naming preserves exact dispatch and escaped nested diagnostics'() {
        given:
        def boundary = schema('''
            @DSL class Value { String text }
            @DSL class Boundary {
                String result
                @Field @Struct.Name('a/b~c') void input(Value value) { result = value.text }
            }
        ''', 'Boundary')

        when:
        def model = boundary.Create.FromStruct(['a/b~c': [text: 'accepted']])

        then:
        model.result == 'accepted'

        when:
        boundary.Create.FromStruct(['a/b~c': [text: 42]], 'named.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('named.yaml')
        failure.message.contains('/a~1b~0c/text')
    }

    def 'a virtual scope does not win an external name collision with an ordinary field'() {
        given:
        def boundary = schema('''
            @DSL class Value { String text }
            @DSL class Boundary {
                String collision
                @Field @Struct.Name('collision') void input(Value value) { }
            }
        ''', 'Boundary')

        when:
        def plan = new InputContractResolver().read(boundary)
        boundary.Create.FromStruct([collision: [text: 'never']])

        then:
        thrown(KlumModelException)
        !plan.entries().containsKey('collision')
        plan.rejections().any { it.reason().contains('collision') }
    }

    def 'virtual Template definition retains ordinary lifecycle silence'() {
        given:
        def deployment = schema()

        when:
        deployment.Create.Template.With { virtual { value 'recipe' } }
        def ordinaryEvents = deployment.events.toList()
        deployment.events.clear()
        deployment.Create.Template.FromStruct([virtual: [value: 'recipe']])

        then:
        ordinaryEvents == ['virtual:recipe']
        deployment.events == ordinaryEvents
    }

    def 'unsupported virtual shape stays outside the mapping grammar: #shape'() {
        given:
        def boundary = schema("""
            @DSL abstract class AbstractChild { String value }
            @DSL class Alternative { String value }
            @DSL class Boundary { $declaration }
        """, 'Boundary')

        when:
        def plan = new InputContractResolver().read(boundary)
        boundary.Create.FromStruct([input: [:]], 'unsupported.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/input')
        !plan.entries().containsKey('input')
        artifacts.loadClass('nestedscope.Deployment').events.empty

        where:
        shape       | declaration
        'keyed'     | '@Field void input(Entry value) {}'
        'abstract'  | '@Field void input(AbstractChild value) {}'
        'options'   | '@Field(FieldType.DEFAULT) void input(Child value) {}'
        'raw value' | '@Field void input(Object value) {}'
        'ignored'   | '@Field @Struct.Ignore void input(Child value) {}'
        'overloads' | '@Field void input(Child value) {}; @Field void input(Alternative value) {}'
        'authored creator' | '@Field void input(Child value) {}; @Builder.Method void input(Map values, Closure body) {}'
    }

    def 'generic virtual target has no scope association and fails closed during contract inspection'() {
        given:
        def boundary = schema('''
            @DSL class GenericChild<T> { T value }
            @DSL class Boundary { @Field void input(GenericChild<String> value) {} }
        ''', 'Boundary')
        def contract = artifacts.loadClass('nestedscope.Boundary_DSL$Builder')

        when:
        boundary.Create.FromStruct([input: [:]])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('input')
        !hasVirtualScope(contract)
        artifacts.loadClass('nestedscope.Deployment').events.empty
    }

    def 'scope association corruption rejects before any construction: #mutation'() {
        given:
        def deployment = schema()
        mutateScope(mutation)

        when:
        deployment.Create.FromStruct([child: [value: 'never']], 'corrupt.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        deployment.events.empty

        where:
        mutation << ['scope', 'operation', 'both', 'sink', 'creator', 'descriptor', 'role', 'query']
    }

    private static void assertWrongThreadApplyRejected(Object nested) {
        def wrongThread = new AtomicReference<Throwable>()
        def thread = new Thread({
            try { nested.applyStruct([value: 'forbidden']) }
            catch (Throwable failure) { wrongThread.set(failure) }
        })
        thread.start()
        thread.join(5000)
        assert !thread.alive
        assert wrongThread.get() instanceof KlumModelException
    }

    private void mutateScope(String mutation) {
        File contract = new File(compilerConfiguration.targetDirectory, 'nestedscope/Deployment_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(contract.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (mutation == 'creator' && name == 'virtual' && descriptor.startsWith('(Ljava/util/Map;Lgroovy/lang/Closure;)')) {
                    return null
                }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != 'virtual' || !descriptor.startsWith('(Lnestedscope/Child_DSL$Builder;)')) {
                    return next
                }
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override void visitEnd() {
                        if (mutation == 'query') {
                            super.visitAnnotation('Lcom/blackbuild/klum/ast/Builder$Query;', true).visitEnd()
                        }
                        super.visitEnd()
                    }

                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        boolean scope = annotation.endsWith('GeneratedInput$VirtualScope;')
                        boolean operation = annotation.endsWith('GeneratedInput$Operation;')
                        if (scope && mutation in ['scope', 'both'] || operation && mutation in ['operation', 'both']
                                || mutation == 'sink' && annotation == 'Lcom/blackbuild/klum/ast/Field;') {
                            return null
                        }
                        def downstream = super.visitAnnotation(annotation, visible)
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (scope && mutation == 'descriptor' && member == 'creator') {
                                    value = 'absent():void'
                                }
                                if (operation && mutation == 'role' && member == 'role') {
                                    value = 'OPERATION'
                                }
                                super.visit(member, value)
                            }
                        }
                    }
                }
            }
        }, 0)
        contract.bytes = writer.toByteArray()
    }

    @CompileStatic
    private static boolean hasVirtualScope(Class<?> contract) {
        contract.getDeclaredMethods().any { it.getAnnotation(GeneratedInput.VirtualScope) != null }
    }

    @CompileStatic
    private static InternalKlumBuilder<?> configureVirtual(InternalKlumBuilder<?> root, Class child,
                                                          Consumer configuration) {
        root.createSingleChildWithConfiguration('virtual', child, false, null, configuration)
    }
}
