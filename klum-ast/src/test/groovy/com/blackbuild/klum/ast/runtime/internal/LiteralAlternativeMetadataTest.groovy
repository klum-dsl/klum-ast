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
    private static final String FIELD = 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Field;'

    private void compile(String rootOptions = '', String fieldOptions = '', String extra = '') {
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
            @DSL class OtherElement extends Element {}
        ''' + """
            $rootOptions
            @DSL class Catalog {
                static { throw new AssertionError('Catalog initialized') }
                $fieldOptions
                @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
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
        entry.role().name() == 'LITERAL_ALTERNATIVE_MAP'
        entry.source() == 'literalmetadata.Catalog#elements'
        entry.payload().type().name == 'literalmetadata.ConcreteElement'
        entry.excludedKey() == 'name'
        entry.keyType() == String.name
        entry.alternative().alias() == 'fixed'
        entry.alternative().scope().name == 'literalmetadata.Catalog_DSL$Builder$CollectionFactory_elements'
        entry.alternative().creator().name == 'fixed'
        entry.alternative().creator().parameterTypes.toList() == [String]
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
        !causes(failure).any { it instanceof AssertionError }

        where:
        mutation << ['strip', 'stripAndCount', 'countMissing', 'countZero', 'countTwo', 'version', 'field', 'alias', 'target', 'key', 'keyType',
                     'scope', 'creator', 'missingMember', 'unknownMember', 'duplicateMember', 'invisible', 'wrongMemberType', 'stripSelection']
    }

    def 'field naming and inherited associations retain the literal alias: #options'() {
        given:
        compile(options, '', '@DSL class Derived extends Catalog {}')

        when:
        def plan = new InputContractResolver().read(binary('Derived'))

        then:
        plan.entries().get('elements').alternative().alias() == 'fixed'
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
        if (!names.isEmpty()) assert plan.entries().get('items').alternative().alias() == 'fixed'

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

    private void rewrite(String mutation) {
        File file = new File(compilerConfiguration.targetDirectory, 'literalmetadata/Catalog_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (mutation == 'stripSelection' && annotation.endsWith('GeneratedInput$Selection;')) return null
                        if (annotation == ASSOCIATION && mutation in ['strip', 'stripAndCount']) return null
                        def target = super.visitAnnotation(annotation, annotation == ASSOCIATION && mutation == 'invisible' ? false : visible)
                        if (!(annotation in [ASSOCIATION, FIELD])) return target
                        new AnnotationVisitor(Opcodes.ASM9, target) {
                            @Override void visit(String member, Object value) {
                                if (annotation == FIELD && member == 'alternativeCount') {
                                    if (mutation == 'countMissing') return
                                    if (mutation in ['countZero', 'stripAndCount']) value = 0
                                    if (mutation == 'countTwo') value = 2
                                }
                                if (annotation == ASSOCIATION) {
                                    if (mutation == 'missingMember' && member == 'key') return
                                    if (mutation == member) {
                                        value = [version: 2, field: 'other', alias: 'other', target: 'literalmetadata.OtherElement', key: 'other',
                                                 keyType: 'java.lang.Object', scope: 'literalmetadata.Element_DSL$Builder', creator: 'missing(java.lang.String):void'][member]
                                    }
                                    if (mutation == 'wrongMemberType' && member == 'alias') value = 1
                                }
                                super.visit(member, value)
                            }
                            @Override void visitEnd() {
                                if (annotation == ASSOCIATION && mutation == 'unknownMember') super.visit('unexpected', 'x')
                                if (annotation == ASSOCIATION && mutation == 'duplicateMember') super.visit('alias', 'fixed')
                                super.visitEnd()
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) result << current
        result
    }
}
