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
import spock.lang.See
import spock.lang.Tag
import java.util.concurrent.atomic.AtomicInteger

@Issue('1129')
class StructuredScalarAlternativeTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String numeric = 'Integer', String numericMark = '@Struct.Operation',
                         String stringMark = '@Struct.Operation', String parameterAnnotation = '', String extra = '') {
        declareMinimumConstraint()
        createSecondaryClass("""
            package scalaralternatives
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            import groovy.transform.AnnotationCollector
            import jakarta.validation.constraints.Min
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.PARAMETER])
            @interface InputBound { long value() }
            @AnnotationCollector @interface Unqualified {}
            @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)
            @AnnotationCollector @interface HiddenPolicy {}
            @DSL class Destination {
                @Struct.Ignore Object value
                @Struct.Ignore int integerCalls
                @Struct.Ignore int stringCalls
                $numericMark @Builder.Method void address($parameterAnnotation $numeric input) {
                    integerCalls++; value = input
                    if (input == 13) throw new IllegalStateException('integer body failed')
                }
                $stringMark @Builder.Method void address(String input) { stringCalls++; value = input }
                @Struct.Ignore @Builder.Method void address(Number input) { throw new AssertionError('Number helper') }
                @Struct.Ignore @Builder.Method void address(Opaque input) { throw new AssertionError('opaque helper') }
                $extra
            }
            class Opaque {
            }
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('scalaralternatives.Destination', false, artifacts)
    }

    private void declareMinimumConstraint() {
        createSecondaryClass('''
            package jakarta.validation.constraints
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
            @interface Min { long value(); Class[] groups() default []; Class[] payload() default [] }
        ''')
    }

    def cleanup() { artifacts?.close() }

    def 'transports authored parameter constraints independently of family membership'() {
        given:
        def destination = schema('Integer', numericMark, stringMark, '@Min(7L)')

        when:
        def builder = Class.forName('scalaralternatives.Destination_DSL$Builder', false, artifacts)
        def metadata = new BuilderContractReader().metadata(builder)
        def facts = metadata.methods().values().find { it.parameterConstraints()?.any { it.value() == '7' } }
        def result = destination.Create.FromStruct([address: input])

        then:
        facts != null
        facts.parameterConstraintCount() == 1
        facts.parameterConstraints().first().annotation() == 'jakarta.validation.constraints.Min'
        facts.parameterConstraints().first().eligible()
        // Capture does not add executable parameter validation, including for a family.
        result.value == input

        where:
        numericMark         | stringMark          | input
        '@Struct.Operation' | '@Struct.Operation' | 3
        ''                  | '@Struct.Operation' | '8080'
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#integerstring-alternatives')
    def 'one address accepts an integer or a String without changing its meaning'() {
        given:
        def destination = schema()

        when:
        String numericInput = new String('8080')
        def numbered = destination.Create.FromStruct([address: 8080])
        def named = destination.Create.FromStruct([address: 'http'])
        def numericText = destination.Create.With { applyStruct([address: numericInput]) }

        then:
        numbered.value == 8080
        numbered.value.class == Integer
        numbered.integerCalls == 1
        numbered.stringCalls == 0
        named.value == 'http'
        named.integerCalls == 0
        named.stringCalls == 1
        numericText.value == '8080'
        numericText.value.class == String
        numericText.value.is(numericInput)
        numericText.integerCalls == 0
        numericText.stringCalls == 1
    }

    def 'exact int32 conversion uses the recorded #numeric signature for #input'() {
        given:
        def destination = schema(numeric)

        when:
        def result = destination.Create.FromStruct([address: input])
        def family = new InputContractResolver().read(destination).entries().address.scalarAlternatives()

        then:
        result.value == expected
        result.value.class == Integer
        result.integerCalls == 1
        result.stringCalls == 0
        family.integerArm().dispatch().parameterTypes*.name == [numeric == 'int' ? 'int' : 'java.lang.Integer']
        family.stringArm().dispatch().parameterTypes == [String] as Class[]
        family.integerArm().dispatch().getAnnotation(GeneratedInput.Operation).parameterConstraintCount() == 0
        family.stringArm().dispatch().getAnnotation(GeneratedInput.Operation).parameterConstraintCount() == 0

        where:
        numeric   | input                     | expected
        'Integer' | 8080L                     | 8080
        'int'     | new BigDecimal('8080.0')   | 8080
        'Integer' | Integer.MIN_VALUE         | Integer.MIN_VALUE
        'int'     | Integer.MAX_VALUE         | Integer.MAX_VALUE
        'Integer' | (byte) 7                  | 7
        'int'     | (short) 7                 | 7
        'Integer' | 7                         | 7
        'int'     | 7G                        | 7
        'Integer' | 7.0f                      | 7
        'int'     | 7.0d                      | 7
        'Integer' | new BigDecimal('7.000')    | 7
    }

    def 'invalid family input rejects without invoking an arm: #input'() {
        given:
        def destination = schema()
        def observed

        when:
        destination.Create.With {
            observed = delegate
            applyStruct([address: input])
        }

        then:
        thrown(KlumModelException)
        observed.integerCalls == 0
        observed.stringCalls == 0

        where:
        input << [null, true, [:], [], new BigDecimal('1.5'), 2147483648L, Double.NaN,
                  new StringBuilder('8080'), "${8080}", new Object(),
                  -2147483649L, new BigInteger('2147483648'), new BigDecimal('2147483648.0'),
                  Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY,
                  0.5f, 0.5d, new AtomicInteger(7)]
    }

    def 'integer body failure keeps its cause and never retries String'() {
        given:
        def destination = schema()
        def observed

        when:
        destination.Create.With {
            observed = delegate
            applyStruct([address: 13])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.cause instanceof IllegalStateException
        failure.cause.message == 'integer body failed'
        observed.integerCalls == 1
        observed.stringCalls == 0
    }

    def 'an ignored integer override cannot execute through parent family facts'() {
        given:
        createSecondaryClass('''
            package scalaralternatives
            import com.blackbuild.klum.ast.*
            @DSL class Base {
                @Struct.Ignore Object value
                @Struct.Operation @Builder.Method void address(Integer input) { value = input }
                @Struct.Operation @Builder.Method void address(String input) { value = input }
            }
            @DSL class Destination extends Base {
                @Struct.Ignore @Builder.Method void address(Integer input) {
                    throw new AssertionError('ignored override invoked')
                }
            }
        ''')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def destination = Class.forName('scalaralternatives.Destination', false, artifacts)

        when:
        destination.Create.FromStruct([address: 8080])

        then:
        thrown(KlumModelException)

        when:
        def result = destination.Create.FromStruct([address: '8080'])
        def entry = new InputContractResolver().read(destination).entries().address

        then:
        result.value == '8080'
        entry.scalarAlternatives() == null
        entry.dispatch().parameterTypes == [String] as Class[]
    }

    def 'zero or one String mark retains existing admission'() {
        given:
        def destination = schema('Integer', '', mark)

        when:
        destination.Create.FromStruct([address: 8080])

        then:
        thrown(KlumModelException)

        when:
        def contract = new InputContractResolver().read(destination)

        then:
        contract.entries().containsKey('address') == marked
        if (marked) {
            assert contract.entries().address.scalarAlternatives() == null
            assert destination.Create.FromStruct([address: '8080']).value == '8080'
        }

        where:
        mark                | marked
        ''                  | false
        '@Struct.Operation' | true
    }

    def 'a legacy single selected String route needs no new parameter completeness facts'() {
        given:
        def destination = schema('Integer', '', '@Struct.Operation')
        mutateParameterFacts('absent count')

        when:
        def result = destination.Create.FromStruct([address: '8080'])

        then:
        result.value == '8080'
        result.value.class == String
        result.integerCalls == 0
        result.stringCalls == 1
    }

    def 'an explicit OMIT policy alone retains complete empty parameter facts'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '@Struct.Operation',
                '@Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)')

        when:
        def result = destination.Create.FromStruct([address: 8080])
        def family = new InputContractResolver().read(destination).entries().address.scalarAlternatives()
        def facts = family.integerArm().dispatch().getAnnotation(GeneratedInput.Operation)

        then:
        result.value == 8080
        facts.parameterConstraintCount() == 0
        facts.parameterConstraints().length == 0
        facts.parameterConstraintProjection().name() == 'OMIT'
    }

    def 'original harmless annotations and collector policies no longer make parameter capture unknown: #annotation'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '@Struct.Operation', annotation)

        when:
        def result = destination.Create.FromStruct([address: 7])
        def family = new InputContractResolver().read(destination).entries().address.scalarAlternatives()

        then:
        result.value == 7
        family.integerConstraints().rules().isEmpty()

        where:
        annotation << ['@InputBound(1L)', '@Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT) @InputBound(1L)',
                       '@Unqualified', '@HiddenPolicy']
    }

    def 'missing or inconsistent empty facts reject before family use: #mutation'() {
        given:
        def destination = schema()
        mutateParameterFacts(mutation)

        when:
        destination.Create.FromStruct([:])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('authored parameter facts')
        failure.message.contains('recompile')

        where:
        mutation << ['absent count', 'wrong count', 'absent rows', 'absent policy']
    }

    def 'an extra eligible candidate rejects the complete family without fallback: #mark'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '@Struct.Operation', '',
                "$mark @Builder.Method void address(Long input) { throw new AssertionError('extra executed') }")

        when:
        destination.Create.FromStruct([:])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Multiple @Struct.Operation')
        failure.message.contains('exactly one authored int/Integer and one String')

        where:
        mark << ['', '@Struct.Operation']
    }

    def 'a lone integer mark has no priority over the unmarked String'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '')

        when:
        def contract = new InputContractResolver().read(destination)

        then:
        !contract.entries().containsKey('address')

        when:
        destination.Create.FromStruct([address: '8080'])

        then:
        thrown(KlumModelException)
    }

    def 'ordered family failure retains earlier effects and cleans up the next root'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '@Struct.Operation', '', '''
            @Struct.Ignore int earlier
            @Struct.Ignore int later
            @Builder.Method void first(String input) { earlier++ }
            @Builder.Method void last(String input) { later++ }
        ''')
        def observed

        when:
        destination.Create.With {
            observed = delegate
            applyStruct([first: 'before', address: 1.5, last: 'after'])
        }

        then:
        thrown(KlumModelException)
        observed.earlier == 1
        observed.later == 0
        observed.integerCalls == 0
        observed.stringCalls == 0

        when:
        def next = destination.Create.FromStruct([address: 'http'])

        then:
        next.value == 'http'
        next.stringCalls == 1
    }

    def 'Template construction and replay retain the selected arm value'() {
        given:
        def destination = schema()

        when:
        def template = destination.Create.Template.FromStruct([address: input])
        def result = destination.Create.With { copyFrom(template) }

        then:
        result.value == input
        result.value.class == input.class
        result.integerCalls == integerCalls
        result.stringCalls == 1 - integerCalls

        where:
        input  | integerCalls
        8080   | 1
        '8080' | 0
    }

    def 'malformed nonempty parameter rows reject before absent input even under OMIT: #mutation'() {
        given:
        def destination = schema('Integer', '@Struct.Operation', '@Struct.Operation',
                '@Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT) @Min(7L)')
        mutateParameterFacts(mutation)

        when:
        destination.Create.FromStruct([:])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(diagnostic)
        failure.message.contains('recompile')

        where:
        mutation          | diagnostic
        'unknown policy'  | 'Unknown authored parameter constraint policy'
        'missing value'   | 'Missing direct integral bound'
        'missing origin'  | 'Missing composition/description facts'
        'invalid origin'  | 'Inconsistent composition origin'
        'duplicate count' | 'Duplicate or invalid authored parameter constraint count'
        'unknown count'   | 'Incomplete constraint facts'
    }

    def 'inconsistent family selection metadata rejects before absent input: #mutation'() {
        given:
        def destination = schema()
        mutateParameterFacts(mutation)

        when:
        destination.Create.FromStruct([:])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(diagnostic)
        failure.message.contains('recompile')

        where:
        mutation                | diagnostic
        'absent operation'      | 'operation'
        'absent marker'         | 'Author marker disagrees'
        'wrong role'            | 'operation role'
        'unsupported protocol'  | 'protocol'
        'wrong operation count' | 'operation'
    }

    private void mutateParameterFacts(String mutation) {
        File file = new File(compilerConfiguration.targetDirectory, 'scalaralternatives/Destination_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                def downstream = super.visitAnnotation(annotation, visible)
                if (!annotation.endsWith('GeneratedInput$Schema;')) { return downstream }
                new AnnotationVisitor(Opcodes.ASM9, downstream) {
                    @Override void visit(String member, Object value) {
                        if (member == 'version' && mutation == 'unsupported protocol') { value = 2 }
                        if (member == 'operationCount' && mutation == 'wrong operation count') { value = ((int) value) - 1 }
                        super.visit(member, value)
                    }
                }
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.endsWith('GeneratedInput$Operation;') && mutation == 'absent operation') { return null }
                        if (annotation.endsWith('Struct$Operation;') && mutation == 'absent marker') { return null }
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Operation;')) {
                            return downstream
                        }
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (member == 'role' && mutation == 'wrong role') { value = 'COMMAND' }
                                if (member == 'parameterConstraintCount' && mutation == 'absent count') {
                                    return
                                }
                                if (member == 'parameterConstraintCount') {
                                    if (mutation == 'wrong count') { value = 1 }
                                    if (mutation == 'unknown count') { value = -1 }
                                    if (mutation == 'duplicate count') { super.visit(member, value) }
                                }
                                super.visit(member, value)
                            }
                            @Override AnnotationVisitor visitArray(String member) {
                                if (member == 'parameterConstraints' && mutation == 'absent rows') {
                                    return null
                                }
                                def rows = super.visitArray(member)
                                if (member != 'parameterConstraints') { return rows }
                                new AnnotationVisitor(Opcodes.ASM9, rows) {
                                    @Override AnnotationVisitor visitAnnotation(String rowName, String rowDescriptor) {
                                        new AnnotationVisitor(Opcodes.ASM9, super.visitAnnotation(rowName, rowDescriptor)) {
                                            @Override void visit(String attribute, Object value) {
                                                if (mutation == 'missing value' && attribute == 'value') { return }
                                                if (mutation == 'missing origin' && attribute == 'origin') { return }
                                                if (mutation == 'invalid origin' && attribute == 'origin') { value = 'FUTURE' }
                                                super.visit(attribute, value)
                                            }
                                        }
                                    }
                                }
                            }
                            @Override void visitEnum(String member, String enumDescriptor, String value) {
                                if (member == 'parameterConstraintProjection' && mutation == 'absent policy') {
                                    return
                                }
                                if (member == 'parameterConstraintProjection' && mutation == 'unknown policy') { value = 'FUTURE' }
                                super.visitEnum(member, enumDescriptor, value)
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

}
