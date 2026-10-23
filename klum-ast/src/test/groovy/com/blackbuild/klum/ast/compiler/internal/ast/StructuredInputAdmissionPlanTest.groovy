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
package com.blackbuild.klum.ast.compiler.internal.ast

import com.blackbuild.klum.ast.AbstractDSLSpec
import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes


@Issue(['529', '932'])
class StructuredInputAdmissionPlanTest extends AbstractDSLSpec {

    def "classifies direct and inherited WIRE-PLAN entries from compiler provenance"() {
        given:
        def unit = compilationUnit(StructuredInputContractFixtures.ADMISSION)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'admission.Parent' })

        then:
        plan.model() == 'admission.Parent'
        plan.builder() == 'admission.Parent_DSL$Builder'
        plan.entries().collect { [it.externalName(), it.role().name(), it.operation().name(), it.payload().kind().name()] } == [
                ['annotations', 'PROPERTY', 'annotation', 'MAP'],
                ['child', 'OWNED_OBJECT', 'child', 'NESTED_OBJECT'],
                ['children', 'KEYED_OWNED_MAP', 'children', 'NESTED_OBJECT'],
                ['fallback', 'PROPERTY', 'fallback', 'SCALAR'],
                ['inheritedLabel', 'PROPERTY', 'inheritedLabel', 'SCALAR'],
                ['inheritedOperation', 'OPERATION', 'inheritedOperation', 'SCALAR'],
                ['labels', 'PROPERTY', 'label', 'LIST'],
                ['nestedAnnotations', 'PROPERTY', 'nestedAnnotation', 'MAP'],
                ['promote', 'OPERATION', 'promote', 'SCALAR'],
                ['renamed', 'PROPERTY', 'region', 'ENUM']
        ]
        plan.entries().find { it.externalName() == 'renamed' }.nameOrigin().name() == 'JSON_PROPERTY'
        plan.entries().find { it.externalName() == 'nestedAnnotations' }.payload().with {
            kind().name() == 'MAP' && keyType() == 'java.lang.String' && item().kind().name() == 'LIST' && item().item().kind().name() == 'SCALAR'
        }
        plan.entries().find { it.externalName() == 'children' }.with {
            mapKeyType() == 'java.lang.String' && excludedChildKeyMember() == 'name' && payload().builder() == 'admission.Child_DSL$Builder'
        }
        plan.entries()*.operation().every { it.declaringBuilder().endsWith('_DSL$Builder') && it.descriptorGroup() }
        plan.rejections()*.reason() == ['excluded by @JsonIgnore(true)', 'excluded by @JsonIgnore(true)', 'excluded by @JsonIgnore(true)']
    }

    def "rejects custom Number admission with an actionable source and type diagnostic"() {
        given:
        def unit = compilationUnit(StructuredInputContractFixtures.NUMERIC_REJECTIONS)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'numericboundary.Configuration' })

        then:
        plan.entries().empty
        plan.rejections()*.source() == [
                'numericboundary.Configuration#allocate(numericboundary.OpaqueNumber):void',
                'numericboundary.Configuration#budget',
                'numericboundary.Configuration#budgets',
                'numericboundary.Configuration#limits'
        ]
        plan.rejections()*.reason().every {
            it.contains('unsupported numeric type numericboundary.OpaqueNumber') &&
                    it.contains('primitive/boxed numeric types, BigInteger or BigDecimal')
        }
    }

    def "admits only the approved standard numeric targets: #type"() {
        given:
        def unit = compilationUnit("""
            package standardnumeric
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import java.math.BigInteger
            import java.math.BigDecimal

            @DSL class Configuration {
                $type budget
                @Builder.Method void allocate($type value) { budget = value }
            }
        """)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'standardnumeric.Configuration' })

        then:
        plan.entries()*.externalName() == ['allocate', 'budget']
        plan.entries().every { it.payload().kind().name() == 'SCALAR' }
        plan.rejections().empty

        where:
        type << ['byte', 'short', 'int', 'long', 'float', 'double',
                 'Byte', 'Short', 'Integer', 'Long', 'Float', 'Double', 'BigInteger', 'BigDecimal']
    }

    def "rejects opaque List and Map whole-container operation targets"() {
        given:
        def unit = compilationUnit('''
            package collectionboundary
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL

            final class OpaqueWords<E> extends ArrayList<E> {
                private OpaqueWords() { throw new AssertionError('No input construction route') }
            }
            final class OpaqueLabels<K, V> extends LinkedHashMap<K, V> {
                private OpaqueLabels() { throw new AssertionError('No input construction route') }
            }
            @DSL class Configuration {
                @Builder.Method void words(OpaqueWords<String> value) { }
                @Builder.Method void labels(OpaqueLabels<String, String> value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'collectionboundary.Configuration' })

        then:
        plan.entries().empty
        plan.rejections()*.source() == [
                'collectionboundary.Configuration#labels(collectionboundary.OpaqueLabels):void',
                'collectionboundary.Configuration#words(collectionboundary.OpaqueWords):void'
        ]
        plan.rejections().every {
            it.reason().contains('unsupported whole-container payload target collectionboundary.Opaque') &&
                    it.reason().contains('generated element population')
        }
    }

    def "rejects unsupported authored operations relationships and colliding external names deterministically"() {
        given:
        def unit = compilationUnit(StructuredInputContractFixtures.REJECTIONS)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'rejection.Parent' })

        then:
        plan.entries().empty
        plan.rejections()*.source() == plan.rejections()*.source().sort()
        plan.rejections().collectEntries { [it.source(), it.reason()] }.with {
            get('rejection.Parent#abstractChild') == 'requires one concrete owned relationship'
            get('rejection.Parent#numberedChildren') == 'requires Map<String, concrete keyed DSL Object> ownership'
            get('rejection.Parent#unkeyedChildren') == 'has no admitted scalar, enum, List, or Map payload'
            get('rejection.Parent#marker') == 'has no admitted scalar, enum, List, or Map payload'
            get('rejection.Parent#firstOperation(java.lang.String):void').startsWith("external-name collision for 'operation':")
            get('rejection.Parent#secondOperation(java.lang.String):void').startsWith("external-name collision for 'operation':")
            get('rejection.Parent#replace(java.lang.String, java.lang.String):void') == '@Builder.Method must declare exactly one payload'
            get('rejection.Parent#raw(java.util.Map):void') == '@Builder.Method payload is outside the admitted grammar'
            get('rejection.Parent#reset():void') == '@Builder.Method must declare exactly one payload'
        }
    }

    def "retains unrelated entries while rejecting an ambiguous external name deterministically"() {
        given:
        def unit = compilationUnit('''
            package collision

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Configuration {
                @JsonProperty('endpoint') String primaryEndpoint
                @JsonProperty('endpoint') String secondaryEndpoint
                String region

                @JsonProperty('endpoint') @Builder.Method
                void configure(String value) { }

                @Builder.Method
                void refresh(String value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'collision.Configuration' })

        then:
        plan.entries().collect { [it.externalName(), it.role().name()] } == [
                ['refresh', 'OPERATION'],
                ['region', 'PROPERTY']
        ]
        plan.entries().find { it.externalName() == 'refresh' }.operation().descriptorGroup().any {
            it.startsWith('refresh(java.lang.String):')
        }
        plan.rejections()*.source() == plan.rejections()*.source().sort()
        plan.rejections()*.source() == [
                'collision.Configuration#configure(java.lang.String):void',
                'collision.Configuration#primaryEndpoint',
                'collision.Configuration#secondaryEndpoint'
        ]
        plan.rejections()*.reason().every {
            it == "external-name collision for 'endpoint': collision.Configuration#configure(java.lang.String):void, collision.Configuration#primaryEndpoint, collision.Configuration#secondaryEndpoint"
        }
    }

    def "classifies an owned relationship before the target namespace is generated"() {
        given:
        def unit = compilationUnit('''
            package consumer

            import com.blackbuild.klum.ast.DSL

            @DSL class Parent {
                Child child
            }

            @DSL class Child {
                String value
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'consumer.Parent' })

        then:
        plan.entries()[0].payload().with {
            type() == 'consumer.Child' && builder() == 'consumer.Child_DSL$Builder'
        }
    }

    def "retains distinct renamed primitive and wrapper descriptors and excludes ignored overloads"() {
        given:
        def unit = compilationUnit('''
            package overloadadmission
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Choice {
                @Builder.Method @JsonProperty('primitive')
                void choose(int value) { }
                @Builder.Method @JsonProperty('wrapper')
                void choose(Integer value) { }
                @Builder.Method @JsonProperty('admitted')
                void filtered(int value) { }
                @Builder.Method @JsonIgnore(true)
                void filtered(Integer value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'overloadadmission.Choice' })

        then:
        plan.entries().collectEntries { [(it.externalName()): it.operation().descriptorGroup()] } == [
                admitted: ['filtered(int):void'], primitive: ['choose(int):void'], wrapper: ['choose(java.lang.Integer):void']
        ]
        plan.rejections().any { it.source().contains('filtered(java.lang.Integer)') && it.reason() == 'excluded by @JsonIgnore(true)' }
    }

    def "minimal public Builder annotations retain field selection and source facts"() {
        given:
        def unit = compilationUnit('''
            package annotationcandidate

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL class Child { @Key String name }

            @DSL class Parent {
                String title
                Child child
                Map<String, Child> children
                @Builder.Method void rename(String value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'annotationcandidate.Parent' })
        def annotations = []
        def builder = new File(compilerConfiguration.targetDirectory, 'annotationcandidate/Parent_DSL$Builder.class')
        new ClassReader(builder.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        annotations << [method: name, annotation: annotation]
                        null
                    }
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

        then:
        plan.entries()*.role()*.name().containsAll(['PROPERTY', 'OWNED_OBJECT', 'KEYED_OWNED_MAP', 'OPERATION'])
        annotations.findAll { it.annotation == 'Lcom/blackbuild/klum/ast/Builder$Method;' }*.method == ['rename']
        annotations.findAll { it.annotation == 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Selection;' }*.method.containsAll(['title', 'child', 'children'])
        annotations.findAll { it.annotation == 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Field;' }*.method.containsAll(['getTitle', 'getChild', 'getChildren'])
        !new File(compilerConfiguration.targetDirectory, 'annotationcandidate/Parent_DSL$WirePlan.class').exists()
    }

    def "in-memory classification does not require an output directory"() {
        given:
        File defaultOutput = new File('memoryonly')
        assert !defaultOutput.exists()
        CompilerConfiguration configuration = new CompilerConfiguration()
        def unit = compilationUnit(configuration, '''
            package memoryonly

            import com.blackbuild.klum.ast.DSL

            @DSL class Configuration { String title }
        ''')

        when:
        unit.compile()

        then:
        notThrown(MultipleCompilationErrorsException)
        StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'memoryonly.Configuration' }) != null
        !new File(defaultOutput, 'Configuration_DSL$WirePlan.class').exists()

        cleanup:
        defaultOutput.deleteDir()
    }

    private static CompilationUnit compilationUnit(CompilerConfiguration configuration, String schema) {
        def compilerLoader = new GroovyClassLoader(StructuredInputAdmissionPlanTest.classLoader, configuration)
        def unit = new CompilationUnit(configuration, null, compilerLoader)
        unit.addSource('Schema.groovy', schema)
        unit
    }

    private CompilationUnit compilationUnit(String schema) {
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('JsonAnnotations.groovy', '''
            package com.fasterxml.jackson.annotation

            @interface JsonIgnore { boolean value() default true }
            @interface JsonProperty { String value() default '' }
        ''')
        unit.addSource('Schema.groovy', schema)
        unit
    }
}
