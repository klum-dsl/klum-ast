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
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue

@Issue('1021')
class StructuredOwnedRelationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String extra = '', boolean inert = false) {
        loader.parseClass '''
            package relationprobe
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                INERT
                static List<String> events = []
                static List<Object> children = []
                static Object active
                static final IllegalArgumentException problem = new IllegalArgumentException('child failed')
                Child child
                @Field(members='entry') Map<String, Entry> entries
                @PostCreate void created() { active = this }
                @PostApply void applied() { events << 'root:apply' }
                EXTRA
            }
            @DSL class Child {
                @Owner Service owner
                String value = 'default'
                @PostCreate void created() { Service.children << this; Service.events << 'child:create' }
                @PostApply void applied() { Service.events << "child:apply:$value".toString() }
                @PostTree void tree() { Service.events << "child:tree:${owner != null}".toString() }
            }
            @DSL class Entry {
                @Key String name
                @Owner Service owner
                String value
                @PostCreate void created() { Service.children << this; Service.events << "entry:create:$name".toString() }
                @PostApply void applied() { Service.events << "entry:apply:$name:$value".toString() }
                @PostTree void tree() { Service.events << "entry:tree:$name:${owner != null}".toString() }
                @Builder.Method void fail(String ignored) { throw Service.problem }
            }
        '''.replace('EXTRA', extra).replace('INERT', inert ? 'static { throw new AssertionError("Schema initialized") }' : '')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('relationprobe.Service', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'source-free relationships retain field facts without public sibling selections'() {
        given:
        def service = schema('', true)

        when:
        def contract = new InputContractResolver().read(service)
        def builder = artifacts.loadClass('relationprobe.Service_DSL$Builder')

        then:
        contract.entries().child.role() == CompiledBuilderSchema.Role.OWNED_OBJECT
        contract.entries().entries.role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP
        contract.entries().child.operation() == 'child'
        contract.entries().entries.operation() == 'entries'
        contract.entries().child.methods().empty
        contract.entries().entries.methods().empty
        contract.entries().child.dispatch() == null
        contract.entries().entries.dispatch() == null
        builder.getMethod('getChild').getAnnotation(GeneratedInput.Selection).role() == 'OWNED_OBJECT'
        builder.getMethod('getEntries').getAnnotation(GeneratedInput.Selection).role() == 'KEYED_OWNED_MAP'
        builder.declaredMethods.findAll { it.getAnnotation(GeneratedInput.Selection) }*.name.sort() == ['getChild', 'getEntries']
    }

    def 'absent sibling overloads preserve preparation reuse identity keys and ownership'() {
        given:
        def service = schema()
        damageContract('no siblings')
        def firstChild
        def firstEntry

        when:
        def model = service.Create.With {
            applyStruct([child: [value: 'one'], entries: ['a/b~': [value: 'one']]], 'service.yaml')
            firstChild = child
            firstEntry = entries['a/b~']
            applyStruct([child: [value: 'two'], entries: ['a/b~': [value: 'two']]], 'service.yaml')
            assert child.is(firstChild)
            assert entries['a/b~'].is(firstEntry)
        }

        then:
        model.child.value == 'two'
        model.child.owner.is(model)
        model.entries['a/b~'].name == 'a/b~'
        model.entries['a/b~'].value == 'two'
        model.entries['a/b~'].owner.is(model)
        firstChild.completedModel.is(model.child)
        firstEntry.completedModel.is(model.entries['a/b~'])
        service.children.size() == 2
        service.events == ['child:create', 'child:apply:one', 'entry:create:a/b~', 'entry:apply:a/b~:one',
                           'root:apply', 'child:apply:two', 'entry:apply:a/b~:two', 'root:apply', 'root:apply',
                           'child:tree:true', 'entry:tree:a/b~:true']
    }

    def 'relation execution preserves first-failure state and source path cause without siblings'() {
        given:
        def service = schema()
        damageContract('no siblings')

        when:
        service.Create.FromStruct([child: [value: 'kept'], entries: [ok: [value: 'kept'], 'b/ad~': [value: 'partial', fail: 'now']]], 'service.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('service.yaml')
        failure.message.contains('/entries/b~1ad~0/fail')
        deepest(failure).is(service.problem)
        service.active.child.value == 'kept'
        service.active.entries.keySet() == ['ok'] as Set
        service.children*.value == ['kept', 'kept', 'partial']
        service.events == ['child:create', 'child:apply:kept', 'entry:create:ok', 'entry:apply:ok:kept', 'entry:create:b/ad~']
    }

    def 'ordinary customized creator still executes while Struct follows the field'() {
        given:
        def service = schema("@Builder.Method Object child(Map values, Closure body) { events << 'custom'; null }")

        when:
        service.Create.With { child([:], {}) }
        def model = service.Create.FromStruct([child: [value: 'structured'], entries: [one: [value: 'structured']]])

        then:
        service.events.count('custom') == 1
        model.child.value == 'structured'
        model.entries.one.value == 'structured'
    }

    def 'descendant creator customizations preserve inherited field relationships'() {
        given:
        def service = schema()
        loader.parseClass '''
            package relationprobe
            import com.blackbuild.klum.ast.*
            @DSL class Derived extends Service {
                @Builder.Method Child_DSL.Builder child(Map values, Closure body) { events << 'custom child'; null }
                @Builder.Method void entries(Closure body) { events << 'custom entries' }
            }
            @DSL class Leaf extends Derived {}
        '''
        def leaf = artifacts.loadClass('relationprobe.Leaf')

        when:
        leaf.Create.With { child([:], {}); entries({}) }
        def model = leaf.Create.FromStruct([child: [value: 'direct'], entries: [one: [value: 'mapped']]])
        def contract = new InputContractResolver().read(leaf)

        then:
        service.events.count('custom child') == 1
        service.events.count('custom entries') == 1
        model.child.value == 'direct'
        model.child.owner.is(model)
        model.entries.one.value == 'mapped'
        model.entries.one.owner.is(model)
        contract.entries().child.methods().empty
        contract.entries().entries.methods().empty
    }

    def 'malformed relation facts reject without initialization: #damage'() {
        given:
        def service = schema('', true)
        damageContract(damage)

        when:
        new InputContractResolver().read(service)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        !(deepest(failure) instanceof AssertionError)

        where:
        damage << ['missing direct relation', 'missing map relation', 'wrong relation field', 'wrong direct role',
                   'wrong map role', 'raw map', 'wrong map item', 'wrong map key', 'missing key authority', 'contradictory relation', 'static relationship']
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'relationprobe/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'no siblings' && name in ['child', 'entries', 'entry']) { return null }
                if (name == 'getEntries') {
                    if (damage == 'raw map') { signature = null }
                    if (damage == 'wrong map item') { signature = '()Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>;' }
                    if (damage == 'wrong map key') { signature = signature.replace('Ljava/lang/String;', 'Ljava/lang/Integer;') }
                }
                boolean staticRelation = damage == 'static relationship' && name == 'getChild'
                if (staticRelation) { access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override void visitEnd() {
                        if (staticRelation) {
                            super.visitCode()
                            super.visitInsn(Opcodes.ACONST_NULL)
                            super.visitInsn(Opcodes.ARETURN)
                            super.visitMaxs(1, 0)
                        }
                        super.visitEnd()
                    }

                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.endsWith('GeneratedInput$Selection;') &&
                                (damage == 'missing direct relation' && name == 'getChild' || damage == 'missing map relation' && name == 'getEntries')) { return null }
                        def downstream = super.visitAnnotation(annotation, visible)
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (annotation.endsWith('GeneratedInput$Selection;')) {
                                    if (damage == 'wrong relation field' && name == 'getChild' && member == 'field') { value = 'entries' }
                                    if (damage == 'wrong direct role' && name == 'getChild' && member == 'role') { value = 'KEYED_OWNED_MAP' }
                                    if (damage == 'wrong map role' && name == 'getEntries' && member == 'role') { value = 'OWNED_OBJECT' }
                                }
                                if (annotation.endsWith('GeneratedInput$Field;') && name == 'getChild') {
                                    if (damage == 'missing key authority' && member == 'schemaKey') { return }
                                    if (damage == 'contradictory relation' && member == 'sequenceElement') { return }
                                }
                                super.visit(member, value)
                            }
                            @Override void visitEnd() {
                                if (damage == 'contradictory relation' && name == 'getChild' && annotation.endsWith('GeneratedInput$Field;')) {
                                    super.visit('sequenceElement', 'child')
                                }
                                super.visitEnd()
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static Throwable deepest(Throwable failure) {
        while (failure.cause != null && !failure.cause.is(failure)) { failure = failure.cause }
        failure
    }
}
