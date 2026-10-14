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
package com.blackbuild.klum.ast

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue
import spock.lang.Unroll

@Issue('890')
class AnnotationPropagationRulesTest extends AbstractDSLSpec {
    private void library(String code) {
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            $code
        """)
    }

    @Unroll
    def 'rejects #reason before attaching a partial batch'() {
        given:
        library("""
            $selected
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation($type) @interface Policy {}
        """)

        when:
        createSecondaryClass("@DSL class Order { $declaration }")

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(reason)
        error.message.contains('@Policy requests @')
        error.message.contains('Order')

        where:
        selected                                                                          | type       | declaration                       | reason
        ''                                                                                | 'String'   | '@Policy String name'             | 'not an annotation'
        ''                                                                                | 'DSL'      | '@Policy String name'             | 'DSL cannot be propagated'
        ''                                                                                | 'Required' | '@Policy String name() { null }'  | 'target METHOD'
        '@Target(ElementType.METHOD) @interface Selected {}'                               | 'Selected' | '@Policy String name'             | 'target FIELD'
        '@Target(ElementType.FIELD) @interface Selected { String value() }'                | 'Selected' | '@Policy String name'             | "member 'value' has no default"
        '@PropagateAnnotation(Required) @Target(ElementType.FIELD) @interface Selected {}' | 'Selected' | '@Policy String name'             | 'nested propagation'
        '@PropagateAnnotation(Selected) @Target(ElementType.FIELD) @interface Selected {}' | 'Selected' | '@Policy String name'             | 'nested propagation'
    }

    @Unroll
    def 'rejects propagated duplicate from #description'() {
        given:
        library("""
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @PropagateAnnotation(Required) $extraRequest @interface Policy {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @PropagateAnnotation(Required) @interface OtherPolicy {}
        """)

        when:
        createSecondaryClass("@DSL class Order { $annotations String name }")

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.errorCollector.errorCount == 1
        error.message.contains('duplicate propagated annotation')
        error.message.contains('@Policy')
        error.message.contains('Required')
        error.message.contains('Order.name')
        error.message.contains('already supplied by')

        where:
        description              | extraRequest                     | annotations
        'explicit annotation'    | ''                               | '@Required @Policy'
        'multiple domains'       | ''                               | '@OtherPolicy @Policy'
        'repeated request'       | '@PropagateAnnotation(Required)'  | '@Policy'
    }

    def 'genuine repeated domains reject duplicate selected types while unrelated array annotations stay inert'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Repeatable(Policies) @PropagateAnnotation(Required) @interface Policy {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Policies { Policy[] value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Unrelated { Policy[] value() default [] }
        ''')

        when:
        createSecondaryClass('@DSL class Order { @Policies([@Policy, @Policy]) String name }')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('duplicate propagated annotation')

        when:
        createSecondaryClass('@DSL class Plain { @Unrelated([@Policy]) String name }')

        then:
        notThrown(MultipleCompilationErrorsException)
        !Plain.getDeclaredField('name').isAnnotationPresent(Required)
    }

    def 'domain member values are retained without forwarding selected members'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @PropagateAnnotation(Required) @interface Policy {
                String value() default 'domain default'
                Validate.Level level() default Validate.Level.WARNING
            }
        ''')

        when:
        createSecondaryClass("@DSL class Order { @Policy(value='domain configured') String name }")

        then:
        notThrown(MultipleCompilationErrorsException)
        def field = Order.getDeclaredField('name')
        field.getAnnotation(Validate).message() == ''
        field.getAnnotation(Validate).level() == Validate.Level.ERROR
        field.getAnnotation(Policy).value() == 'domain configured'
    }

    def 'parameters constructors and non DSL sites do not expand invalid requests'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR])
            @PropagateAnnotation(String) @interface Policy {}
        ''')

        when:
        createSecondaryClass('''
            @Policy class Ordinary {
                @Policy String name
                @Policy Ordinary() {}
                @Policy String read(@Policy String value) { value }
            }
            @DSL class Order { String read(@Policy String value) { value } }
            @DSL interface Contract { @Policy Object value = null }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Ordinary.getDeclaredField('name').declaredAnnotations*.annotationType() == [Policy]
        Order.Create.One().read('orders') == 'orders'
    }

    def 'source annotation defaults and target guards work without reflection'() {
        when:
        createSecondaryClass('''
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Selected { String value() default 'selected default' }
            @PropagateAnnotation(Selected) @Target(ElementType.FIELD)
            @interface Policy { String value() default 'domain' }
            @DSL class Order { @Policy('configured') String name }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Order.getDeclaredField('name').getAnnotation(Selected).value() == 'selected default'
    }
    def 'repeatable selected annotations still reject propagated duplicates but explicit repeats stay legal'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Repeatable(Tags) @interface Label { String value() default 'default' }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Tags { Label[] value() }
            @PropagateAnnotation(Label) @Target(ElementType.FIELD) @interface Policy {}
        ''')

        when:
        createSecondaryClass('@DSL class Order { @Policy @Tags([@Label]) String name }')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('duplicate propagated annotation')

        when:
        createSecondaryClass('@DSL class Direct { @Label @Label String name }')

        then:
        notThrown(MultipleCompilationErrorsException)
        Direct.getDeclaredField('name').getAnnotationsByType(Label).length == 2
    }

    def 'SOURCE CLASS and RUNTIME retention follow ordinary physical annotation rules'() {
        given:
        library('''
            @Retention(RetentionPolicy.SOURCE) @Target(ElementType.FIELD) @interface SourceMarker {}
            @Retention(RetentionPolicy.CLASS) @Target(ElementType.FIELD) @interface ClassMarker {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @interface RuntimeMarker {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @PropagateAnnotation(SourceMarker) @PropagateAnnotation(ClassMarker) @PropagateAnnotation(RuntimeMarker)
            @interface Policy {}
        ''')

        when:
        createSecondaryClass('@DSL class Order { @Policy String name }')

        then:
        notThrown(MultipleCompilationErrorsException)
        def field = Order.getDeclaredField('name')
        field.isAnnotationPresent(Policy)
        field.isAnnotationPresent(RuntimeMarker)
        !field.isAnnotationPresent(SourceMarker)
        !field.isAnnotationPresent(ClassMarker)

        and:
        Map<String, Boolean> emitted = [:]
        new ClassReader(new File(compilerConfiguration.targetDirectory, 'Order.class').bytes).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        if (name != 'name') return null
                        new FieldVisitor(Opcodes.ASM9) {
                            @Override
                            AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                emitted[annotation] = visible
                                null
                            }
                        }
                    }
                }, 0)
        !emitted.containsKey('LSourceMarker;')
        emitted['LClassMarker;'] == false
        emitted['LRuntimeMarker;'] == true
    }

    def 'a selected annotation with no Target is legal on classes fields and methods'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME) @interface Selected { String value() default 'selected' }
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(Selected) @interface Policy {}
        ''')

        when:
        createSecondaryClass('''
            @DSL @Policy class Order {
                @Policy String name
                @Policy String summary() { name }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Order.getAnnotation(Selected).value() == 'selected'
        Order.getDeclaredField('name').getAnnotation(Selected).value() == 'selected'
        Order.getDeclaredMethod('summary').getAnnotation(Selected).value() == 'selected'
    }

    def 'genuine containers use defaulted domain annotations (#precompiled)'() {
        given:
        String definitions = '''
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Repeatable(Policies) @PropagateAnnotation(Required) @interface Policy {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Policies { Policy[] value() default [@Policy] }
        '''
        if (precompiled) library(definitions)

        when:
        createSecondaryClass("""
            import java.lang.annotation.*
            ${precompiled ? '' : definitions}
            @DSL class Order { @Policies String name }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        Order.getDeclaredField('name').getAnnotationsByType(Validate).size() == 1

        where:
        precompiled << [false, true]
    }

    def 'explicit container identity participates in duplicate rejection'() {
        given:
        library('''
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Repeatable(Policies) @interface Policy {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Policies { Policy[] value() default [] }
            @PropagateAnnotation(Policies) @Target(ElementType.FIELD) @interface Carrier {}
        ''')

        when:
        createSecondaryClass('@DSL class Order { @Policies @Carrier String name }')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('duplicate propagated annotation')
        error.message.contains('@Carrier requests @Policies on Order.name')
        error.message.contains('explicit @Policies')
    }

}
