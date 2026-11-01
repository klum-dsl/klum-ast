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
import com.blackbuild.klum.ast.Struct
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Issue('952')
class StructuredOperationSelectionTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Service') {
        createClass("""
            package operationselection
            import com.blackbuild.klum.ast.*
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('operationselection.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#select-a-string-operation')
    def 'select the public endpoint operation before interpreting its String payload'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method
                void endpoint(String value) { host = 'primary:' + value }
                @Struct.Name('endpoint') @Builder.Method
                void backupEndpoint(String value) { host = 'backup:' + value }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [endpoint: 'api.internal'], 'service.yaml')
        def direct = type.Create.With { endpoint 'api.internal' }
        def contract = new InputContractResolver().read(type)

        then:
        result.host == 'primary:api.internal'
        result.host == direct.host
        contract.entries().keySet() == ['endpoint'] as Set
        contract.entries().endpoint.methods()*.name == ['endpoint']
        contract.entries().endpoint.methods()[0].getAnnotation(Struct.Operation) != null
        contract.entries().endpoint.methods()[0].getAnnotation(GeneratedInput.Operation).authoritative()
    }

    def 'a unique unmarked String candidate stays callable'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String host
                @Builder.Method void endpoint(String value) { host = value }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'unique.yaml')

        then:
        result.host == 'api'
    }

    def 'competing unmarked String candidates never select from runtime values: #value'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Builder.Method @Struct.Name('endpoint') void primary(String value) {}
                @Builder.Method @Struct.Name('endpoint') void secondary(String value) {}
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [endpoint: value], 'ambiguous.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('endpoint')

        where:
        value << ['primary', 'secondary', 42]
    }

    def 'multiple eligible marks fail clearly while reading the contract'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Operation @Builder.Method @Struct.Name('endpoint') void primary(String value) {}
                @Struct.Operation @Builder.Method @Struct.Name('endpoint') void secondary(String value) {}
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [:], 'multiple.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Multiple @Struct.Operation')
    }

    def 'a profile can separate marked native and literal names that share a source spelling'() {
        given:
        def type = schema('''
            @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void configureEndpoint(String value) { host = 'native:' + value }
                @Struct.Operation @Struct.Name('configureEndpoint') @Builder.Method
                void explicitEndpoint(String value) { host = 'literal:' + value }
            }
        ''')

        when:
        def nativeValue = KlumStructuredInput.readRoot(type, ['configure-endpoint': 'api'], 'native.yaml')
        def literalValue = KlumStructuredInput.readRoot(type, [configureEndpoint: 'api'], 'literal.yaml')

        then:
        nativeValue.host == 'native:api'
        literalValue.host == 'literal:api'
    }

    def 'selection uses effective profile names and rejects multiple effective marks: #secondMark'() {
        given:
        def type = schema("""
            @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void URLValue(String value) { host = value }
                $secondMark @Builder.Method void urlValue(String value) { throw new AssertionError('unselected') }
            }
        """)

        when:
        def outcome
        try { outcome = KlumStructuredInput.readRoot(type, ['url-value': 'api'], 'profile.yaml') }
        catch (KlumModelException failure) { outcome = failure }

        then:
        if (secondMark) {
            assert outcome instanceof KlumModelException
            assert outcome.message.contains('Multiple @Struct.Operation')
        } else {
            assert outcome.host == 'api'
        }

        where:
        secondMark << ['', '@Struct.Operation']
    }

    def 'exclusion disqualifies marked candidates and rename retains only the external name'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Struct.Ignore @Struct.Name('external') @Builder.Method
                void primary(String value) { throw new AssertionError('excluded') }
                @Struct.Name('external') @Builder.Method void secondary(String value) { host = value }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [external: 'api'], 'rename.yaml')
        def contract = new InputContractResolver().read(type)

        then:
        result.host == 'api'
        contract.entries().keySet() == ['external'] as Set

        when:
        KlumStructuredInput.readRoot(type, [secondary: 'api'], 'native.yaml')

        then:
        thrown(KlumModelException)
    }

    def 'a mark cannot make a private Builder method public'() {
        when:
        schema('''
            @DSL class Service {
                @Struct.Operation @Builder.Method private void endpoint(String value) {}
            }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Builder-only methods must not be private')
    }

    def 'a mark does not admit an unsupported operation: #declaration'() {
        given:
        def type = schema("@DSL class Service { $declaration }")

        when:
        KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'unsupported.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('endpoint')

        where:
        declaration << [
            '@Struct.Operation @Builder.Method void endpoint() {}',
            '@Struct.Operation @Builder.Method void endpoint(CharSequence value) {}',
            '@Struct.Operation @Builder.Method void endpoint(Object value) {}',
            '@Struct.Operation @Builder.Query String endpoint(String value) { value }',
            '@Struct.Operation String endpoint(String value) { value }',
            '@Struct.Operation @Struct.Ignore @Builder.Method void endpoint(String value) {}'
        ]
    }

    def 'selection stays String only even when another admitted payload could accept the value'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void endpoint(String value) { host = value }
                @Builder.Method void endpoint(int value) { throw new AssertionError('numeric fallback') }
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [endpoint: 42], 'numeric.yaml')

        then:
        thrown(KlumModelException)

        when:
        def result = KlumStructuredInput.readRoot(type, [endpoint: '42'], 'string.yaml')

        then:
        result.host == '42'
    }

    def 'inherited selected operation dispatches exactly and overrides own their names and intent: #annotation'() {
        given:
        def type = schema("""
            @DSL class Base {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void endpoint(String value) { host = 'base:' + value }
            }
            @DSL class Service extends Base {
                $override
            }
        """)

        when:
        def contract = new InputContractResolver().read(type)
        def result = KlumStructuredInput.readRoot(type, [(external): 'api'], 'inheritance.yaml')
        def direct = type.Create.With { endpoint 'api' }

        then:
        result.host == expected
        result.host == direct.host
        contract.entries().keySet() == [external] as Set
        contract.entries()[external].methods()[0].declaringClass.name.endsWith(owner + '_DSL$Builder')

        where:
        annotation | override | external | expected | owner
        'inherited' | '' | 'endpoint' | 'base:api' | 'Base'
        'unmarked override' | "@Builder.Method void endpoint(String value) { host = 'child:' + value }" | 'endpoint' | 'child:api' | 'Service'
        'renamed override' | "@Struct.Operation @Struct.Name('renamed') @Builder.Method void endpoint(String value) { host = 'child:' + value }" | 'renamed' | 'child:api' | 'Service'
    }

    def 'ignored override shadows the parent mark through a further descendant'() {
        given:
        def type = schema('''
            @DSL class Base { @Struct.Operation @Builder.Method void endpoint(String value) {} }
            @DSL class Middle extends Base { @Struct.Ignore @Builder.Method void endpoint(String value) {} }
            @DSL class Service extends Middle {}
        ''')

        when:
        KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'shadow.yaml')

        then:
        thrown(KlumModelException)
        new InputContractResolver().read(type).entries().isEmpty()
    }

    def 'unmarked String and CharSequence projections have no implicit specificity preference'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Builder.Method void endpoint(String value) {}
                @Builder.Method void endpoint(CharSequence value) {}
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'ambiguous.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('endpoint')
    }

    def 'marked String operation resolves String and CharSequence competition without admitting CharSequence'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void endpoint(String value) { host = value }
                @Builder.Method void endpoint(CharSequence value) { throw new AssertionError('CharSequence selected') }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'string.yaml')

        then:
        result.host == 'api'
    }

    def 'separately compiled inheritance keeps exact selection and override dispatch'() {
        given:
        schema('''
            @DSL class Base {
                @Struct.Ignore String host
                @Struct.Operation @Builder.Method void endpoint(String value) { host = 'base:' + value }
                @Struct.Name('endpoint') @Builder.Method void alternate(String value) { host = 'alternate:' + value }
            }
        ''', 'Base')
        def binaryCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        binaryCompiler.parseClass('''
            package operationselection
            import com.blackbuild.klum.ast.*
            @DSL class Inherited extends Base {}
            @DSL class Overridden extends Base {
                @Struct.Operation @Builder.Method void endpoint(String value) { host = 'override:' + value }
            }
        ''')

        when:
        def inherited = Class.forName('operationselection.Inherited', false, artifacts)
        def overridden = Class.forName('operationselection.Overridden', false, artifacts)
        def inheritedValue = KlumStructuredInput.readRoot(inherited, [endpoint: 'api'], 'binary.yaml')
        def overriddenValue = KlumStructuredInput.readRoot(overridden, [endpoint: 'api'], 'binary.yaml')

        then:
        inheritedValue.host == inherited.Create.With { endpoint 'api' }.host
        inheritedValue.host == 'base:api'
        overriddenValue.host == overridden.Create.With { endpoint 'api' }.host
        overriddenValue.host == 'override:api'

        cleanup:
        binaryCompiler?.close()
    }

    def 'selection annotation is a method-only runtime author marker'() {
        when:
        def target = Struct.Operation.getAnnotation(Target).value()
        def retention = Struct.Operation.getAnnotation(Retention).value()

        then:
        target == [ElementType.METHOD] as ElementType[]
        retention == RetentionPolicy.RUNTIME
        Struct.Operation.declaredMethods.length == 0
    }

    def 'stripped or corrupt selection facts fail closed: #mutation'() {
        given:
        def type = schema('''
            @DSL class Service { @Struct.Operation @Builder.Method void endpoint(String value) {} }
        ''')
        mutateContract(mutation)

        when:
        KlumStructuredInput.readRoot(type, [:], 'corrupt.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        failure.message.toLowerCase().contains(detail)

        where:
        mutation | detail
        'missing count' | 'completeness'
        'wrong count' | 'completeness'
        'missing intent' | 'selection fact'
        'wrong intent' | 'invalid authoritative'
        'flipped intent' | 'disagrees'
        'missing author marker' | 'disagrees'
        'missing operation' | 'completeness'
        'missing both' | 'completeness'
    }

    private void mutateContract(String mutation) {
        File file = new File(compilerConfiguration.targetDirectory, 'operationselection/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def next = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Schema;')) return next
                new AnnotationVisitor(Opcodes.ASM9, next) {
                    @Override void visit(String member, Object value) {
                        if (member == 'operationCount' && mutation == 'missing count') return
                        super.visit(member, member == 'operationCount' && mutation == 'wrong count' ? 99 : value)
                    }
                }
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.endsWith('Struct$Operation;') && mutation == 'missing author marker') return null
                        if (annotation.endsWith('GeneratedInput$Operation;') && mutation in ['missing operation', 'missing both']) return null
                        if (annotation.endsWith('Builder$Method;') && mutation == 'missing both') return null
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Operation;')) return downstream
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (member == 'authoritative' && mutation == 'missing intent') return
                                if (member == 'authoritative' && mutation == 'flipped intent') value = false
                                super.visit(member, member == 'authoritative' && mutation == 'wrong intent' ? 'invalid' : value)
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }
}
