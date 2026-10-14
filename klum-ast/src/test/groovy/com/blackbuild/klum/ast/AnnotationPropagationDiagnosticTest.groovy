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

import com.blackbuild.klum.ast.layer3.Cluster
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import spock.lang.Issue

@Issue('890')
class AnnotationPropagationDiagnosticTest extends AbstractDSLSpec {
    def 'KlumCast #caseName matches direct constraints and retains contribution origin'() {
        given:
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.cast.*
            import com.blackbuild.klum.cast.checks.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @KlumCastValidated $selectedConstraint
            @interface Selected { String value() default '' }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @KlumCastValidated $peerConstraint @interface Peer {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @KlumCastValidated $domainConstraint @PropagateAnnotation(Selected) @interface Policy {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @KlumCastValidated $domainConstraint @interface DirectOriginal {}
        """)

        when:
        createSecondaryClass("@DSL class Order { $peer @Policy $member }")

        then:
        def effective = thrown(MultipleCompilationErrorsException)
        effective.errorCollector.errorCount == 1
        effective.message.contains(provenance)
        effective.message.contains('@Policy requests @Selected on Order.name')
        effective.message.contains('Validation binding:')
        effective.message.contains('Validation path:')

        when:
        createSecondaryClass("@DSL class Direct { $peer $direct $member }")

        then:
        def control = thrown(MultipleCompilationErrorsException)
        control.errorCollector.errorCount == 1
        effective.message.find(/Validation binding: [^\n]+/) == control.message.find(/Validation binding: [^\n]+/)
        effective.message.find(/Validation path: [^\n]+/) == control.message.find(/Validation path: [^\n]+/)
        !control.message.contains('Effective annotation origin:')
        !control.message.contains('Effective declaration context:')

        where:
        caseName                     | selectedConstraint          | peerConstraint               | domainConstraint     | peer    | direct            | member        | provenance
        'field type restriction'     | '@NeedsType(String)'        | ''                           | ''                   | ''      | '@Selected'       | 'int name'    | 'Effective annotation origin:'
        'required member selection'  | "@NeedsOneOf(['value'])"    | ''                           | ''                   | ''      | '@Selected'       | 'String name' | 'Effective annotation origin:'
        'introduced peer conflict'   | '@NotTogetherWith(Peer)'    | ''                           | ''                   | '@Peer' | '@Selected'       | 'String name' | 'Effective annotation origin:'
        'existing peer conflict'     | ''                          | '@NotTogetherWith(Selected)'  | ''                   | '@Peer' | '@Selected'       | 'String name' | 'Effective declaration context:'
        'original domain constraint' | ''                          | ''                           | '@NeedsType(String)' | ''      | '@DirectOriginal' | 'int name'    | 'Effective declaration context:'
    }

    def 'non-annotation and DSL selections fail from same-unit metadata (#selected)'() {
        when:
        createSecondaryClass("""
            @PropagateAnnotation($selected) @interface Policy {}
            @DSL class Order { @Policy String name }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.errorCollector.errorCount == 1
        error.message.contains(problem)
        error.message.contains('@Policy requests @')
        error.message.contains('Order.name')
        !error.message.contains('ClassCastException')

        where:
        selected | problem
        'String' | 'not an annotation'
        'DSL'    | 'DSL cannot be propagated'
    }
    def 'lowering and generated projections preserve the applied annotation source positions'() {
        given:
        createSecondaryClass('''
            @PropagateAnnotation(Required) @interface RequiredPolicy {}
            @PropagateAnnotation(Optional) @interface OptionalPolicy {}
            @PropagateAnnotation(Builder.Query) @interface QueryPolicy {}
            import com.blackbuild.klum.ast.layer3.Cluster
            @PropagateAnnotation(Cluster) @interface ClusterPolicy {}
        ''')
        Map<String, List<Integer>> positions = [:]
        compilerConfiguration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.INSTRUCTION_SELECTION) {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode type) {
                if (type.name != 'Order' && type.name != 'Order$Builder') {
                    return
                }
                (type.fields + type.methods).each { AnnotatedNode member ->
                    member.annotations.findAll { it.classNode.name in [Required.name, Optional.name, Validate.name,
                            Builder.Query.name, Cluster.name] }.each { annotation ->
                        positions["${type.name}.${member.name}:${annotation.classNode.nameWithoutPackage}".toString()] =
                                [annotation.lineNumber, annotation.columnNumber]
                    }
                }
            }
        })

        when:
        createSecondaryClass('''@DSL class Order {
    @RequiredPolicy String name
    @OptionalPolicy String description
    @QueryPolicy String summary() { name }
    @ClusterPolicy Map<String, String> names
}''')

        then:
        notThrown(MultipleCompilationErrorsException)
        positions['Order.name:Required'] == [2, 5]
        positions['Order.name:Validate'] == [2, 5]
        positions['Order.description:Validate'] == [3, 5]
        positions['Order.summary:Builder$Query'] == [4, 5]
        positions['Order$Builder.summary:Builder$Query'] == [4, 5]
        positions['Order.getNames:Cluster'] == [5, 5]
    }

}
