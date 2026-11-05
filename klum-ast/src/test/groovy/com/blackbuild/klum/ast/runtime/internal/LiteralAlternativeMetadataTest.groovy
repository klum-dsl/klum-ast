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
import java.util.function.Consumer
import spock.lang.Issue
import org.codehaus.groovy.control.MultipleCompilationErrorsException

@Issue('952')
class LiteralAlternativeMetadataTest extends AbstractDSLSpec {
    private URLClassLoader artifacts
    private static final String ASSOCIATION = 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$LiteralAlternative;'
    private static final String ASSOCIATIONS = 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$LiteralAlternatives;'
    private static final String FIELD = 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Field;'

    private void compile(String rootOptions = '', String fieldOptions = '', String extra = '', int alternativeCount = 1) {
        String aliases = ['fixed: ConcreteElement', 'rolling: OtherElement', 'spare: SpareElement'].take(alternativeCount).reverse().join(', ')
        loader.parseClass '''
            package literalmetadata
            import com.blackbuild.klum.ast.*
            @DSL abstract class Element {
                static { throw new AssertionError('Element initialized') }
                @Key String name
                String value
            }
            @DSL class ConcreteElement extends Element {
                static { throw new AssertionError('ConcreteElement initialized') }
            }
            @DSL class OtherElement extends Element { Nested details }
            @DSL class Nested { String value }
            @DSL class SpareElement extends Element {}
        ''' + """
            $rootOptions
            @DSL class Catalog {
                static { throw new AssertionError('Catalog initialized') }
                $fieldOptions
                @Field(alternatives = { [$aliases] }) Map<String, Element> elements
            }
            $extra
        """
    }

    private Class binary(String name = 'Catalog') {
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('literalmetadata.' + name, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'reads inert exact compiler-literal association from classfiles without user initialization'() {
        given:
        compile()

        when:
        def plan = new InputContractResolver().read(binary())
        def entry = plan.entries().get('elements')

        then:
        GeneratedInput.LiteralAlternative.declaredMethods*.name as Set == ['field', 'alias', 'target', 'key', 'keyType', 'scope', 'creator'] as Set
        new BuilderContractReader().metadata(plan.contract()).version() == 1
        entry.role().name() == 'LITERAL_ALTERNATIVE_MAP'
        entry.source() == 'literalmetadata.Catalog#elements'
        entry.payload().type().name == 'literalmetadata.ConcreteElement'
        entry.excludedKey() == 'name'
        entry.keyType() == String.name
        entry.alternatives().get('fixed').alias() == 'fixed'
        entry.alternatives().get('fixed').scope().name == 'literalmetadata.Catalog_DSL$Builder$CollectionFactory_elements'
        entry.alternatives().get('fixed').creator().name == 'fixed'
        entry.alternatives().get('fixed').creator().parameterTypes.toList() == [String, Closure]
        !entry.contract().methods.any { it.parameterTypes.contains(Consumer) }
    }

    def 'missing or corrupted association rejects before construction even when input omits the relationship: #mutation'() {
        given:
        compile()
        rewrite(mutation)
        def catalog = binary()

        when:
        StructuredInputSupport.readRoot(catalog, [:], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Schema')
        if (mutation == 'removedVersion') {
            assert failure.message.contains('Invalid literal alternative member version')
        }
        if (mutation == 'schemaVersion') {
            assert failure.message.contains('Unsupported structured-input metadata protocol version 2')
        }
        !causes(failure).any { it instanceof AssertionError }

        where:
        mutation << ['strip', 'stripAndCount', 'countMissing', 'countZero', 'countTwo', 'removedVersion', 'schemaVersion', 'field', 'alias', 'target', 'key', 'keyType',
                     'scope', 'creator', 'missing-field', 'missing-alias', 'missing-target', 'missing-key', 'missing-keyType', 'missing-scope', 'missing-creator', 'unknownMember', 'duplicateMember', 'invisible', 'wrongMemberType', 'emptyAlias', 'arrayMember', 'nestedMember', 'enumMember', 'stripSelection', 'stripCreator', 'creatorField', 'creatorRole', 'sequenceRelationship']
    }

    def 'field naming and inherited associations retain the literal alias: #options'() {
        given:
        compile(options, '', '@DSL class Derived extends Catalog {}')

        when:
        def plan = new InputContractResolver().read(binary('Derived'))

        then:
        plan.entries().get('elements').alternatives().get('fixed').alias() == 'fixed'
        plan.entries().get('elements').source() == 'literalmetadata.Catalog#elements'

        where:
        options << ['', '@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)']
    }

    def 'explicit field renaming and exclusion use the shared resolver: #options'() {
        given:
        compile('', options)

        when:
        def plan = new InputContractResolver().read(binary())

        then:
        plan.entries().keySet() == names as Set
        if (!names.isEmpty()) {
            assert plan.entries().get('items').alternatives().get('fixed').alias() == 'fixed'
        }

        where:
        options                  | names
        "@Struct.Name('items')"  | ['items']
        '@Struct.Ignore'         | []
    }

    def 'an inherited field redeclaration rejects during schema compilation'() {
        when:
        compile('', '', '''
            @DSL class Derived extends Catalog {
                @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
            }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('shadows')
    }

    @Issue('1030')
    def 'complete multiple associations are canonical inert exact routes with #options'() {
        given:
        compile(options, '', '@DSL class Derived extends Catalog {}', 2)

        when:
        def entry = new InputContractResolver().read(binary('Derived')).entries().get('elements')

        then:
        entry.alternatives().keySet().toList() == ['fixed', 'rolling']
        entry.alternatives().values()*.payload()*.type()*.name == ['literalmetadata.ConcreteElement', 'literalmetadata.OtherElement']
        entry.alternatives().values().every { alternative ->
            alternative.creator().name == alternative.alias() &&
                alternative.creator().returnType == alternative.payload().builder() &&
                alternative.creator().parameterTypes.toList() == [String, Closure]
        }
        entry.excludedKey() == 'name'
        entry.source() == 'literalmetadata.Catalog#elements'

        where:
        options << ['', '@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)']
    }

    @Issue('1030')
    def 'corrupt multi-alias associations fail even when the relationship is absent: #mutation'() {
        given:
        compile('', '', '', mutation == 'missingRowAndCount' ? 3 : 2)
        rewrite(mutation)

        when:
        StructuredInputSupport.readRoot(binary(), [:], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Schema')
        !causes(failure).any { it instanceof AssertionError }

        where:
        mutation << ['strip', 'stripAndCount', 'countMissing', 'countZero', 'countThree', 'removedVersion',
                     'field', 'alias', 'target', 'key', 'keyType', 'scope', 'creator', 'missing-target',
                     'missing-creator', 'unknownMember', 'duplicateMember', 'invisible', 'wrongMemberType',
                     'emptyAlias', 'arrayMember', 'nestedMember', 'enumMember', 'stripSelection', 'stripCreator',
                     'creatorField', 'creatorRole', 'sequenceRelationship', 'missingRow', 'containerMember',
                     'containerArray', 'rowDescriptor', 'missingRowAndCount']
    }

    @Issue('1030')
    def 'a secondary alias nested contract is validated before any construction even when omitted'() {
        given:
        compile('', '', '', 2)
        rewrite('nestedSchemaVersion')

        when:
        StructuredInputSupport.readRoot(binary(), [:], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/elements/rolling/details')
        failure.message.contains('Unsupported structured-input metadata protocol version 2')
        !causes(failure).any { it instanceof AssertionError }
    }

    private void rewrite(String mutation) {
        File file = new File(compilerConfiguration.targetDirectory, mutation == 'nestedSchemaVersion' ? 'literalmetadata/Nested_DSL$Builder.class' : mutation in ['stripCreator', 'creatorField', 'creatorRole']
                ? 'literalmetadata/Catalog_DSL$Builder$CollectionFactory_elements.class' : 'literalmetadata/Catalog_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                def target = super.visitAnnotation(annotation, visible)
                if (!(mutation in ['schemaVersion', 'nestedSchemaVersion']) || !annotation.endsWith('GeneratedInput$Schema;')) {
                    return target
                }
                new AnnotationVisitor(Opcodes.ASM9, target) {
                    @Override void visit(String member, Object value) {
                        super.visit(member, member == 'version' ? 2 : value)
                    }
                }
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (mutation in ['stripSelection', 'stripCreator'] && annotation.endsWith('GeneratedInput$Selection;')) {
                            return null
                        }
                        if (mutation in ['creatorField', 'creatorRole'] && annotation.endsWith('GeneratedInput$Selection;')) {
                            def selected = super.visitAnnotation(annotation, visible)
                            return new AnnotationVisitor(Opcodes.ASM9, selected) {
                                @Override void visit(String member, Object value) {
                                    super.visit(member, member == (mutation == 'creatorField' ? 'field' : 'role') ? 'other' : value)
                                }
                            }
                        }
                        if (annotation in [ASSOCIATION, ASSOCIATIONS] && mutation in ['strip', 'stripAndCount']) {
                            return null
                        }
                        def target = super.visitAnnotation(annotation, annotation in [ASSOCIATION, ASSOCIATIONS] && mutation == 'invisible' ? false : visible)
                        if (annotation == ASSOCIATIONS) {
                            return multipleAssociations(target, mutation)
                        }
                        if (!(annotation in [ASSOCIATION, FIELD])) {
                            return target
                        }
                        mutateAssociation(annotation, target, mutation)
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static AnnotationVisitor multipleAssociations(AnnotationVisitor target, String mutation) {
        new AnnotationVisitor(Opcodes.ASM9, target) {
            @Override AnnotationVisitor visitArray(String member) {
                def array = super.visitArray(mutation == 'containerArray' ? 'wrong' : member)
                new AnnotationVisitor(Opcodes.ASM9, array) {
                    private int row
                    @Override AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                        if (mutation in ['missingRow', 'missingRowAndCount'] && row++ == 0) {
                            return null
                        }
                        def nested = super.visitAnnotation(ignored, mutation == 'rowDescriptor' ? FIELD : descriptor)
                        mutateAssociation(ASSOCIATION, nested, mutation)
                    }
                }
            }
            @Override void visitEnd() {
                if (mutation == 'containerMember') {
                    super.visit('unknown', 'value')
                }
                super.visitEnd()
            }
        }
    }

    private static AnnotationVisitor mutateAssociation(String annotation, AnnotationVisitor target, String mutation) {
        new AnnotationVisitor(Opcodes.ASM9, target) {
            @Override void visit(String member, Object value) {
                if (annotation == FIELD && member == 'alternativeCount') {
                    if (mutation == 'countMissing') {
                        return
                    }
                    if (mutation in ['countZero', 'stripAndCount']) {
                        value = 0
                    }
                    if (mutation in ['countTwo', 'countThree', 'missingRowAndCount']) {
                        value = mutation == 'countThree' ? 3 : 2
                    }
                }
                if (annotation == ASSOCIATION) {
                    if (mutation == 'missing-' + member) {
                        return
                    }
                    if (mutation == member) {
                        value = [field: 'other', alias: 'other', target: 'literalmetadata.OtherElement', key: 'other',
                                 keyType: 'java.lang.Object', scope: 'literalmetadata.Element_DSL$Builder', creator: 'missing(java.lang.String):void'][member]
                    }
                    if (mutation == 'wrongMemberType' && member == 'alias') {
                        value = 1
                    }
                    if (mutation == 'emptyAlias' && member == 'alias') {
                        value = ''
                    }
                }
                super.visit(member, value)
            }
            @Override void visitEnd() {
                if (annotation == FIELD && mutation == 'sequenceRelationship') {
                    super.visit('sequenceElement', 'element')
                }
                if (annotation == ASSOCIATION && mutation == 'removedVersion') {
                    super.visit('version', 1)
                }
                if (annotation == ASSOCIATION && mutation == 'arrayMember') {
                    super.visitArray('alias').visitEnd()
                }
                if (annotation == ASSOCIATION && mutation == 'nestedMember') {
                    super.visitAnnotation('alias', ASSOCIATION).visitEnd()
                }
                if (annotation == ASSOCIATION && mutation == 'enumMember') {
                    super.visitEnum('alias', 'Lbroken/Enum;', 'VALUE')
                }
                if (annotation == ASSOCIATION && mutation == 'unknownMember') {
                    super.visit('unexpected', 'x')
                }
                if (annotation == ASSOCIATION && mutation == 'duplicateMember') {
                    super.visit('alias', 'fixed')
                }
                super.visitEnd()
            }
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) {
            result << current
        }
        result
    }
}
