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

import com.blackbuild.klum.ast.InputNaming
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('959')
class InputNamingProfileTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Service') {
        createClass("""
            package naming
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.InputNaming.Profile
            import static com.blackbuild.klum.ast.InputNaming.Profile.KEBAB_CASE
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('naming.' + root, false, artifacts)
    }

    private static CompiledBuilderSchema contract(Class type) { CompiledBuilderSchema.read(type) }

    def cleanup() { artifacts?.close(); System.clearProperty('naming.executed') }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#type-naming-profiles')
    def 'choose kebab member names while keeping owned values and data keys independent'() {
        given:
        def type = schema('''
            @DSL class ServiceBase { String displayName }
            @InputNaming(Profile.KEBAB_CASE)
            @DSL class Service extends ServiceBase {
                String serviceName
                String URLValue
                String XML2Parser
                String version2URL
                String already_snake
                String ÅngströmURL
                Map<String, String> labels
                Details serviceDetails
                Map<String, Worker> workerInstances
                @Builder.Method void configureEndpoint(String value) { serviceName = value }
            }
            @DSL class Details { String nativeName }
            @InputNaming(Profile.KEBAB_CASE)
            @DSL class Worker { @Key String workerId; String imageName }
        ''')

        when:
        def service = KlumStructuredInput.readRoot(type, [
                'display-name': 'Catalog', 'configure-endpoint': 'catalog', 'url-value': 'link',
                'xml2-parser': 'xml', 'version2-url': 'v2', already_snake: 'same', 'Ångström-url': 'unicode',
                labels: ['Team/Blue': 'ops'], 'service-details': [nativeName: 'own policy'],
                'worker-instances': ['Worker/Blue': ['image-name': 'worker:1']]], 'service.yaml')

        then:
        service.displayName == 'Catalog'
        service.serviceName == 'catalog'
        service.URLValue == 'link'
        service.XML2Parser == 'xml'
        service.version2URL == 'v2'
        service.already_snake == 'same'
        service.ÅngströmURL == 'unicode'
        service.labels == ['Team/Blue': 'ops']
        service.serviceDetails.nativeName == 'own policy'
        service.workerInstances['Worker/Blue'].workerId == 'Worker/Blue'
        service.workerInstances['Worker/Blue'].imageName == 'worker:1'
        contract(Class.forName('naming.ServiceBase', false, artifacts)).entries().keySet() == ['displayName'] as Set
        contract(type).entries().keySet() == ['display-name', 'service-name', 'url-value', 'xml2-parser', 'version2-url',
                'already_snake', 'Ångström-url', 'labels', 'service-details', 'worker-instances', 'configure-endpoint'] as Set
    }

    def 'annotation requires an explicit finite runtime-retained type profile'() {
        when:
        def value = InputNaming.getDeclaredMethod('value')

        then:
        value.defaultValue == null
        InputNaming.Profile.values()*.name() == ['IDENTITY', 'KEBAB_CASE']
        InputNaming.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        InputNaming.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]

        when:
        schema('@InputNaming @DSL class Service { String serviceName }')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def 'static enum imports retain the explicit compiler-folded choice'() {
        given:
        def type = schema('@InputNaming(KEBAB_CASE) @DSL class Service { String serviceName }')

        when:
        def names = contract(type).entries().keySet()

        then:
        names == ['service-name'] as Set
    }

    def 'minimal naming facts add a bounded public classfile delta: #choice'() {
        given:
        def type = schema("$annotation @DSL class Service { String serviceName }")
        byte[] original = artifacts.getResourceAsStream('naming/Service_DSL\$Builder.class').bytes
        def stripped = new ClassWriter(0)
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, stripped) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def downstream = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Schema;')) {
                    return downstream
                }
                new AnnotationVisitor(Opcodes.ASM9, downstream) {
                    @Override void visit(String member, Object value) {
                        if (!member.startsWith('naming')) {
                            super.visit(member, value)
                        }
                    }
                }
            }
        }, 0)

        when:
        int delta = original.length - stripped.toByteArray().length
        println "Naming public-contract classfile delta for $choice: $delta bytes"

        then:
        delta > 0
        delta <= 256
        contract(type).entries().keySet() == expected as Set

        where:
        choice       | annotation                            | expected
        'absent'     | ''                                    | ['serviceName']
        'IDENTITY'   | '@InputNaming(Profile.IDENTITY)'       | ['serviceName']
        'KEBAB_CASE' | '@InputNaming(Profile.KEBAB_CASE)'     | ['service-name']
    }

    def 'explicit profiles bind descendants including explicit identity: #ancestor #descendant'() {
        given:
        def type = schema("""
            @InputNaming(Profile.$ancestor) @DSL class Base { String inheritedName }
            $childAnnotation @DSL class Service extends Base { String localName }
        """)

        when:
        def names = contract(type).entries().keySet()

        then:
        names == expected as Set

        where:
        ancestor    | descendant   | childAnnotation                         | expected
        'KEBAB_CASE' | 'absent'     | ''                                      | ['inherited-name', 'local-name']
        'KEBAB_CASE' | 'KEBAB_CASE' | '@InputNaming(Profile.KEBAB_CASE)'       | ['inherited-name', 'local-name']
        'IDENTITY'   | 'absent'     | ''                                      | ['inheritedName', 'localName']
        'IDENTITY'   | 'IDENTITY'   | '@InputNaming(Profile.IDENTITY)'         | ['inheritedName', 'localName']
    }

    def 'conflicting explicit #ancestor and #descendant choices reject before input execution'() {
        given:
        def type = schema("""
            @InputNaming(Profile.$ancestor) @DSL class Base { String inheritedName }
            @InputNaming(Profile.$descendant) @DSL class Service extends Base {
                String localName = executed()
                static String executed() { System.setProperty('naming.executed', 'yes'); 'executed' }
            }
        """)

        when:
        KlumStructuredInput.readRoot(type, ['local-name': 'value'], 'conflict.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Conflicting explicit input profiles')
        failure.message.contains('naming.Base')
        failure.message.contains('naming.Service')
        failure.message.contains('IDENTITY')
        failure.message.contains('KEBAB_CASE')
        failure.message.contains('artifact')
        System.getProperty('naming.executed') == null

        where:
        ancestor     | descendant
        'IDENTITY'   | 'KEBAB_CASE'
        'KEBAB_CASE' | 'IDENTITY'
    }

    def 'native spelling has no alias under a selected profile'() {
        given:
        def type = schema('''
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service { String serviceName }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [serviceName: 'wrong token'], 'alias.yaml')

        then:
        thrown(KlumModelException)
    }

    def 'nonidentity collisions reject fields and authored operations with both sources: #members'() {
        given:
        def type = schema("""
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                static { System.setProperty('naming.executed', 'initialized'); throw new AssertionError('must not initialize') }
                $members
            }
        """)

        when:
        KlumStructuredInput.readRoot(type, [:], 'collision.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains("KEBAB_CASE external-name collision for 'url-value'")
        failure.message.contains(first)
        failure.message.contains(second)
        System.getProperty('naming.executed') == null

        where:
        members                                                                                           | first                    | second
        'String URLValue; String urlValue'                                                                  | 'naming.Service#URLValue' | 'naming.Service#urlValue'
        'String URLValue; @Builder.Method void urlValue(String value) {}'                                    | 'naming.Service#URLValue' | 'naming.Service#urlValue('
        '@Builder.Method void URLValue(String value) {}; @Builder.Method void urlValue(String value) {}'     | 'naming.Service#URLValue(' | 'naming.Service#urlValue('
        '@Builder.Method void urlValue(String value) {}; @Builder.Method void urlValue(int value) {}'         | 'java.lang.String'         | 'int'
    }

    def 'profile construction key spelling follows the member position only'() {
        given:
        def type = schema('''
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service { @Key String serviceId; String displayName }
        ''')

        when:
        def member = KlumStructuredInput.readRoot(type, ['service-id': 'Service/Blue', 'display-name': 'Blue'], 'key.yaml')
        def external = KlumStructuredInput.readRoot(type, 'Service/Red', ['display-name': 'Red'], 'key.yaml')

        then:
        member.serviceId == 'Service/Blue'
        external.serviceId == 'Service/Red'
        contract(type).key().externalName() == 'service-id'
    }

    def 'key collision remains position sensitive under a nonidentity profile'() {
        given:
        def type = schema('''
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service { @Key String URLValue; String urlValue }
        ''')

        when:
        KlumStructuredInput.readRoot(type, ['url-value': 'ambiguous'], 'key.yaml')

        then:
        thrown(KlumModelException)

        when:
        def result = KlumStructuredInput.readRoot(type, 'literal/key', ['url-value': 'property'], 'key.yaml')

        then:
        result.URLValue == 'literal/key'
        result.urlValue == 'property'
    }

    def 'rejected profile leaves an active Builder unchanged'() {
        given:
        def type = schema('''
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                String serviceName
                String URLValue
                String urlValue
                @Builder.Method void configureEndpoint(String value) { serviceName = value }
            }
        ''')
        Throwable rejected

        when:
        def result = type.Create.With {
            serviceName 'before'
            try {
                KlumStructuredInput.applyToBuilder(delegate, ['service-name': 'after', 'configure-endpoint': 'executed'], 'active.yaml')
            } catch (KlumModelException failure) { rejected = failure }
            assert serviceName == 'before'
        }

        then:
        rejected.message.contains('collision')
        result.serviceName == 'before'
    }

    def 'bounded ASCII transformation preserves original adjacency: #nativeName'() {
        when:
        def actual = InputNamingProfiles.kebab(nativeName, 'test#member')

        then:
        actual == expected

        where:
        nativeName       | expected
        'serviceName'    | 'service-name'
        'URLValue'       | 'url-value'
        'XML2Parser'     | 'xml2-parser'
        'version2URL'    | 'version2-url'
        'already_snake'  | 'already_snake'
        'ÅngströmURL'    | 'Ångström-url'
        'ÅURL'           | 'Åurl'
        'a-B'            | 'a-b'
        'URL2XMLParser'  | 'url2-xml-parser'
        'x2ABC3Def'      | 'x2-abc3-def'
        'A__B'           | 'a__b'
        'a😀URL'         | 'a😀url'
    }
}
