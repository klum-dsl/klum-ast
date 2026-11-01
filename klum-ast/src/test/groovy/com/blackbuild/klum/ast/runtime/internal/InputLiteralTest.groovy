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
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Issue('959')
class InputLiteralTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Service') {
        createClass("""
            package literals
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.Struct.Naming.Profile
            import com.fasterxml.jackson.annotation.*
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('literals.' + root, false, artifacts)
    }

    def setup() {
        loader.parseClass('''package com.fasterxml.jackson.annotation
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @interface JsonProperty { String value() default '' }
        ''')
        loader.parseClass('''package com.fasterxml.jackson.annotation
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @interface JsonIgnore { boolean value() default true }
        ''')
    }

    def cleanup() { artifacts?.close(); System.clearProperty('literal.executed') }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#literal-names-and-exclusion')
    def 'choose exact external names and exclude internal configuration'() {
        given:
        def type = schema('''
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service {
                @Struct.Name('service.id') String serviceName
                @Struct.Ignore String internalNote
                @Builder.Method @Struct.Name('configure/URL')
                void configureEndpoint(String value) { serviceName = value }
                @Builder.Method @Struct.Ignore
                void configureInternally(String value) { internalNote = value }
            }
        ''')

        when:
        def service = KlumStructuredInput.readRoot(type, ['service.id': 'catalog'], 'service.yaml')
        def operated = KlumStructuredInput.readRoot(type, ['configure/URL': 'operation'], 'service.yaml')
        def configured = type.Create.With { serviceName 'groovy'; configureInternally 'private' }

        then:
        service.serviceName == 'catalog'
        service.internalNote == null
        operated.serviceName == 'operation'
        configured.serviceName == 'groovy'
        configured.internalNote == 'private'
        new InputContractResolver().read(type).entries().keySet() == ['service.id', 'configure/URL'] as Set
    }

    def 'field and authored getter facts stay independent on a shared public accessor: #fieldAnnotation #methodAnnotation'() {
        given:
        def type = schema("""
            @DSL class Service {
                $fieldAnnotation String value
                @Builder.Method $methodAnnotation String getValue() { value }
            }
        """)

        when:
        def plan = new InputContractResolver().read(type)
        def result = KlumStructuredInput.readRoot(type, [(external): 'catalog'], 'shared.yaml')

        then:
        plan.entries().keySet() == [external] as Set
        result.value == 'catalog'
        plan.rejections().find { it.source.contains('#getValue(') }.reason == reason

        where:
        fieldAnnotation             | methodAnnotation                         | external      | reason
        ''                          | ''                                       | 'value'       | '@Builder.Method must declare exactly one payload'
        "@Struct.Name('field/exact')" | "@Struct.Name('getter/exact') @Struct.Ignore" | 'field/exact' | 'excluded by @Struct.Ignore'
        "@Struct.Name('field/exact')" | "@JsonProperty('getter/exact') @JsonIgnore" | 'field/exact' | 'excluded by @JsonIgnore(true)'
    }

    def 'field exclusion does not replace the authored getter rejection on a shared accessor'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Ignore String value
                @Builder.Method String getValue() { value }
            }
        ''')

        when:
        def plan = new InputContractResolver().read(type)

        then:
        plan.entries().isEmpty()
        plan.rejections().find { it.source == 'literals.Service#value' }.reason == 'excluded by @Struct.Ignore'
        plan.rejections().find { it.source.contains('#getValue(') }.reason == '@Builder.Method must declare exactly one payload'
    }

    def 'field and authored setter facts stay independent on a shared fallback accessor'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Name('field/exact') String key
                @Builder.Method @Struct.Name('setter/exact') void setKey(String value) { key = value }
            }
        ''')

        when:
        def plan = new InputContractResolver().read(type)
        def field = KlumStructuredInput.readRoot(type, ['field/exact': 'field'], 'shared.yaml')
        def operation = KlumStructuredInput.readRoot(type, ['setter/exact': 'setter'], 'shared.yaml')

        then:
        plan.entries().keySet() == ['field/exact', 'setter/exact'] as Set
        field.key == 'field'
        operation.key == 'setter'
    }

    def 'field and operation exclusion remain independent on a shared setter: #fieldAnnotation #methodAnnotation'() {
        given:
        def type = schema("""
            @DSL class Service {
                $fieldAnnotation String key
                @Builder.Method $methodAnnotation void setKey(String value) { key = value }
            }
        """)

        when:
        def plan = new InputContractResolver().read(type)
        def result = KlumStructuredInput.readRoot(type, [(external): 'catalog'], 'shared.yaml')

        then:
        plan.entries().keySet() == [external] as Set
        result.key == 'catalog'
        plan.rejections().find { it.source.contains(ignoredSource) }.reason == 'excluded by @Struct.Ignore'

        where:
        fieldAnnotation             | methodAnnotation                            | external       | ignoredSource
        '@Struct.Ignore'              | "@Struct.Name('setter/exact')"                 | 'setter/exact' | '#key'
        "@Struct.Name('field/exact')" | '@Struct.Ignore'                              | 'field/exact'  | '#setKey('
    }

    def 'omitted owned contracts reject incompatible protocol before root execution: #target #version'() {
        given:
        def type = schema('''
            @DSL class Service {
                static { System.setProperty('literal.executed', 'initialized') }
                Middle middle
            }
            @DSL class Middle { Service recursive; Child child }
            @DSL class ChildBase { String inheritedValue }
            @DSL class Child extends ChildBase { String value }
        ''')
        rewriteProtocol(target, version)

        when:
        KlumStructuredInput.readRoot(type, [:], 'omitted.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('metadata protocol version ' + version)
        failure.message.contains('literals.' + target)
        failure.message.contains('/middle')
        failure.message.contains('recompile and package')
        System.getProperty('literal.executed') == null

        when: 'the independent control proves the root initializer is live'
        Class.forName('literals.Service', true, artifacts)

        then:
        System.getProperty('literal.executed') == 'initialized'

        where:
        target   | version
        'Middle' | 0
        'Child'  | 0
        'Child'  | 2
        'ChildBase' | 2
    }

    def 'incompatible omitted ownership leaves active Builder configuration untouched'() {
        given:
        def type = schema('''
            @DSL class Service {
                String value
                Child child
                @Builder.Method void configure(String next) {
                    System.setProperty('literal.executed', 'operation')
                    value = next
                }
            }
            @DSL class Child { String value }
        ''')
        rewriteProtocol('Child', 2)
        Throwable rejected

        when:
        def result = type.Create.With {
            value 'before'
            try {
                KlumStructuredInput.applyToBuilder(delegate, [configure: 'after'], 'active.yaml')
            } catch (KlumModelException failure) { rejected = failure }
        }

        then:
        rejected.message.contains('metadata protocol version 2')
        result.value == 'before'
        result.child == null
        System.getProperty('literal.executed') == null
    }

    def 'owned contract cycles terminate without inspecting excluded targets'() {
        given:
        def type = schema('''
            @DSL class Service { Middle middle; @Struct.Ignore Excluded excluded }
            @DSL class Middle { Service recursive; Child child }
            @DSL class Child { String value }
            @DSL class Excluded { String hidden }
        ''')
        rewriteProtocol('Excluded', 2)

        when:
        def result = KlumStructuredInput.readRoot(type, [:], 'recursive.yaml')

        then:
        result.middle == null
        result.excluded == null
    }

    private void rewriteProtocol(String type, int version) {
        def file = new File(compilerConfiguration.targetDirectory, 'literals/' + type + '_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def downstream = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Schema;')) { return downstream }
                new AnnotationVisitor(Opcodes.ASM9, downstream) {
                    @Override void visit(String member, Object value) {
                        super.visit(member, member == 'version' ? version : value)
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    def 'canonical literals have required exact values and field or method runtime targets'() {
        when:
        def value = Struct.Name.getDeclaredMethod('value')

        then:
        value.returnType == String
        value.defaultValue == null
        Struct.Ignore.declaredMethods.length == 0
        [Struct.Name, Struct.Ignore].every { it.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME }
        [Struct.Name, Struct.Ignore].every { it.getAnnotation(Target).value() == [ElementType.FIELD, ElementType.METHOD] as ElementType[] }
    }

    def 'literal bridge coalesces names and exclusion wins while false ignore is neutral: #annotations'() {
        given:
        def type = schema("""
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service {
                $annotations String serviceName
                @Builder.Method ${annotations.replace('exact/URL', 'operation/URL')} void configureEndpoint(String value) { serviceName = value }
            }
        """)

        when:
        def plan = new InputContractResolver().read(type)

        then:
        plan.entries().keySet() == names as Set
        plan.rejections().size() == excluded

        where:
        annotations                                                             | names                       | excluded
        "@Struct.Name(' exact/URL ')"                                             | [' exact/URL ', ' operation/URL '] | 0
        "@Struct.Name('exact/URL') @JsonProperty('exact/URL')"                     | ['exact/URL', 'operation/URL'] | 0
        "@Struct.Name('exact/URL') @JsonProperty('') @JsonIgnore(false)"            | ['exact/URL', 'operation/URL'] | 0
        "@Struct.Name('exact/URL') @Struct.Ignore @JsonIgnore(false)"                 | []                          | 2
        "@Struct.Name('exact/URL') @JsonIgnore"                                    | []                          | 2
        '@Struct.Ignore @JsonIgnore(false)'                                        | []                          | 2
        '@JsonIgnore(false)'                                                     | ['service-name', 'configure-endpoint'] | 0
    }

    def 'literal names remain exact under any profile and equal Jackson facts are annotation-order independent: #annotations'() {
        given:
        def type = schema("""
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service {
                $annotations String serviceName
            }
        """)

        when:
        def result = KlumStructuredInput.readRoot(type, [(' exact/URL '): 'catalog'], 'literal.yaml')

        then:
        result.serviceName == 'catalog'
        new InputContractResolver().read(type).entries().keySet() == [' exact/URL '] as Set

        where:
        annotations << ["@Struct.Name(' exact/URL ')", "@Struct.Name(' exact/URL ') @JsonProperty(' exact/URL ')",
                        "@JsonProperty(' exact/URL ') @Struct.Name(' exact/URL ')",
                        "@Struct.Name(' exact/URL ') @JsonIgnore(false)"]
    }

    def 'different explicit names fail even with exclusion on #member'() {
        when:
        schema("@DSL class Service { $annotations $member }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Conflicting literal input names on literals.Service#')
        failure.message.contains("@Struct.Name('canonical')")
        failure.message.contains("@JsonProperty('jackson')")

        where:
        annotations                                                           | member
        "@Struct.Name('canonical') @JsonProperty('jackson')"                     | 'String serviceName'
        "@JsonProperty('jackson') @Struct.Name('canonical') @Struct.Ignore"        | 'String serviceName'
        "@Struct.Name('canonical') @JsonProperty('jackson') @JsonIgnore"         | '@Key String serviceId'
        "@Struct.Name('canonical') @JsonProperty('jackson') @JsonIgnore"         | '@Builder.Method void configure(String value) {}'
    }

    def 'empty canonical name is invalid even when excluded: #member'() {
        when:
        schema("@DSL class Service { @Struct.Name('') @Struct.Ignore $member }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('@Struct.Name requires a nonempty exact String on literals.Service#')

        where:
        member << ['String serviceName', '@Key String serviceId', '@Builder.Method void configure(String value) {}']
    }

    def 'literal overrides resolve profile collisions without changing native Groovy configuration'() {
        given:
        def type = schema('''
            @DSL class Base { @Struct.Name('literalURL') String URLValue }
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service extends Base {
                String urlValue
                @Builder.Method @Struct.Name('stringURL') void configureURL(String value) { URLValue = value }
                @Builder.Method @Struct.Name('numericURL') void configureURL(int value) { urlValue = value.toString() }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [literalURL: 'base', 'url-value': 'child', stringURL: 'op', numericURL: 7], 'resolved.yaml')

        then:
        result.URLValue == 'op'
        result.urlValue == '7'
        new InputContractResolver().read(type).entries().keySet() == ['literalURL', 'url-value', 'stringURL', 'numericURL'] as Set
    }

    def 'ignored key keeps member obligation and permits externally supplied root or map identity'() {
        given:
        def type = schema('''
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service {
                @Key @Struct.Name('service/id') @Struct.Ignore String serviceId
                String displayName
            }
            @DSL class Owner { Map<String, Service> services }
        ''')

        when:
        KlumStructuredInput.readRoot(type, ['service/id': 'forbidden'], 'key.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('@Struct.Ignore')

        when:
        def root = KlumStructuredInput.readRoot(type, 'Root/Blue', ['display-name': 'blue'], 'key.yaml')
        def owner = KlumStructuredInput.readRoot(Class.forName('literals.Owner', false, artifacts),
                [services: ['Map/Red': ['display-name': 'red']]], 'map.yaml')

        then:
        root.serviceId == 'Root/Blue'
        owner.services['Map/Red'].serviceId == 'Map/Red'
    }

    def 'ignored members and native aliases reject before any earlier operation can execute: #input'() {
        given:
        def type = schema('''
            @Struct.Naming(Profile.KEBAB_CASE) @DSL class Service {
                @Struct.Name('service/id') String serviceName
                @Struct.Ignore String hiddenValue
                @Builder.Method void execute(String value) { System.setProperty('literal.executed', 'yes') }
                @Builder.Method @Struct.Ignore void hiddenOperation(String value) { throw new AssertionError('excluded') }
            }
        ''')
        Throwable rejected

        when:
        def result = type.Create.With {
            serviceName 'before'
            try {
                KlumStructuredInput.applyToBuilder(delegate, [execute: 'trap'] + input, 'active.yaml')
            } catch (KlumModelException failure) { rejected = failure }
            assert serviceName == 'before'
        }

        then:
        rejected != null
        result.serviceName == 'before'
        System.getProperty('literal.executed') == null

        where:
        input << [[serviceName: 'alias'], ['service-name': 'alias'], ['hidden-value': 'forbidden'], ['hidden-operation': 'forbidden']]
    }
}
