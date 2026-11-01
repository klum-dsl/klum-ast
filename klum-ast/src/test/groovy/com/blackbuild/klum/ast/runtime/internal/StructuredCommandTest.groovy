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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('952')
class StructuredCommandTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations = SERVICE, String root = 'Service') {
        createClass("""
            package structuredcommand
            import com.blackbuild.klum.ast.*
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('structuredcommand.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    private static final String SERVICE = '''
        @DSL class Service {
            static List<String> events = []
            int timeout = 30
            @Builder.Method void resetTimeout() { timeout = 30; events << 'reset' }
            @Builder.Method void later() { events << 'later' }
            @Builder.Method @Struct.Name('fail/~') void fail() {
                events << 'fail'
                throw new IllegalArgumentException('command failed')
            }
            @PostCreate void created() { events << 'created' }
            @PostApply void applied() { events << 'applied' }
            @PostTree void tree() { events << 'tree' }
            @Builder.Query int currentTimeout() { timeout }
            String modelMethod() { 'model' }
        }
    '''

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#zero-argument-commands')
    def 'an empty mapping invokes the domain reset command once'() {
        given:
        def type = schema()

        when:
        def result = KlumStructuredInput.readRoot(type, [timeout: 90, resetTimeout: [:]], 'service.yaml')
        def events = type.events.toList()
        def direct = type.Create.With { timeout 90; resetTimeout() }
        def entry = new InputContractResolver().read(type).entries().resetTimeout

        then:
        result.timeout == 30
        result.timeout == direct.timeout
        events == ['created', 'reset', 'applied', 'tree']
        entry.role() == CompiledBuilderSchema.Role.COMMAND
        entry.payload().kind() == CompiledBuilderSchema.Kind.EMPTY_OBJECT
        entry.methods()[0].parameterCount == 0
        entry.methods()[0].getAnnotation(GeneratedInput.Operation).role() == 'COMMAND'
    }

    def 'only an empty Map invokes a command: #payload'() {
        given:
        def type = schema()
        def failure

        when:
        def result = type.Create.With {
            try {
                KlumStructuredInput.applyToBuilder(delegate,
                        [timeout: 90, resetTimeout: payload, later: [:]], 'bad-command.yaml')
            } catch (KlumModelException caught) { failure = caught }
        }

        then:
        failure.message.contains('bad-command.yaml')
        failure.message.contains('/resetTimeout')
        failure.message.contains('empty Map')
        result.timeout == 90
        !type.events.contains('reset')
        !type.events.contains('later')

        where:
        // Both blank YAML values and explicit YAML null are supplied as null by the caller's parser.
        payload << [null, [unexpected: true], [], [[:]], '', 'reset', 0, false]
    }

    def 'a reached command persists before a later invalid member and later commands do not run'() {
        given:
        def type = schema()
        def failure

        when:
        def result = type.Create.With {
            try {
                KlumStructuredInput.applyToBuilder(delegate,
                        [timeout: 90, resetTimeout: [:], unknown: [:], later: [:]], 'ordered.yaml')
            } catch (KlumModelException caught) { failure = caught }
        }

        then:
        failure.message.contains('/unknown')
        result.timeout == 30
        type.events.count('reset') == 1
        !type.events.contains('later')
    }

    def 'unknown framework lifecycle query and Model names remain unavailable: #name'() {
        given:
        def type = schema()

        when:
        KlumStructuredInput.readRoot(type, [(name): [:], later: [:]], 'excluded.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/' + name)
        !new InputContractResolver().read(type).entries().containsKey(name)
        !type.events.contains('later')

        where:
        name << ['unknown', 'resetTimeout!', 'created', 'applied', 'tree', 'currentTimeout', 'modelMethod', 'apply', 'copyFrom', 'getClass']
    }

    def 'command failure retains its escaped pointer original cause and root cleanup'() {
        given:
        def type = schema()
        def result = null

        when:
        result = KlumStructuredInput.readRoot(type, [resetTimeout: [:], 'fail/~': [:], later: [:]], 'broken.yaml')

        then:
        def failure = thrown(KlumModelException)
        result == null
        failure.message.contains('broken.yaml')
        failure.message.contains('/fail~1~0')
        failure.message.contains('Service_DSL$Builder')
        failure.cause instanceof IllegalArgumentException
        failure.cause.message == 'command failed'
        type.events == ['created', 'reset', 'fail']

        when:
        def recovered = KlumStructuredInput.readRoot(type, [resetTimeout: [:]], 'recovered.yaml')

        then:
        recovered.timeout == 30
    }

    def 'commands use inherited effective names and exact descendant overrides'() {
        given:
        def type = schema('''
            @DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) class Parent {
                static List<String> events = []
                @Builder.Method void resetTimeout() { events << 'parent' }
                @Builder.Method @Struct.Name('old-name') void refresh() { events << 'old' }
                @Builder.Method @Struct.Name('hidden') void hiddenCommand() { events << 'hidden' }
            }
            @DSL class Child extends Parent {
                @Builder.Method @Struct.Name('current-name') void refresh() { events << 'child' }
                @Builder.Method @Struct.Ignore void hiddenCommand() { events << 'ignored' }
            }
            @DSL class Grandchild extends Child {}
        ''', 'Grandchild')

        when:
        KlumStructuredInput.readRoot(type, ['reset-timeout': [:], 'current-name': [:]], 'inherited.yaml')
        def entries = new InputContractResolver().read(type).entries()

        then:
        type.events == ['parent', 'child']
        entries.keySet() == ['reset-timeout', 'current-name'] as Set
        entries.'reset-timeout'.contract().name.endsWith('Parent_DSL$Builder')
        entries.'current-name'.contract().name.endsWith('Child_DSL$Builder')
    }

    def 'commands do not guess among colliding payload shapes'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Builder.Method void resetTimeout() {}
                @Builder.Method void resetTimeout(String value) {}
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [resetTimeout: [:]], 'collision.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('resetTimeout')
    }

    def 'missing inconsistent or malformed command role fails before any command executes: #mutation'() {
        given:
        def type = schema()
        mutateRole(mutation)

        when:
        KlumStructuredInput.readRoot(type, [later: [:]], 'corrupt.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('role')
        failure.message.contains('recompile')
        type.events.empty

        where:
        mutation << ['missing', 'OPERATION', 'UNSUPPORTED', 17]
    }

    private void mutateRole(Object mutation) {
        File contract = new File(compilerConfiguration.targetDirectory, 'structuredcommand/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(contract.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != 'resetTimeout') return next
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Operation;')) return downstream
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (member == 'role' && mutation == 'missing') return
                                super.visit(member, member == 'role' ? mutation : value)
                            }
                        }
                    }
                }
            }
        }, 0)
        contract.bytes = writer.toByteArray()

    }
}
