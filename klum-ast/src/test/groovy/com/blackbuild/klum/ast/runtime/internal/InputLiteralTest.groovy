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
import com.blackbuild.klum.ast.InputName
import com.blackbuild.klum.ast.InputIgnore
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
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
            import com.blackbuild.klum.ast.InputNaming.Profile
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
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                @InputName('service.id') String serviceName
                @InputIgnore String internalNote
                @Builder.Method @InputName('configure/URL')
                void configureEndpoint(String value) { serviceName = value }
                @Builder.Method @InputIgnore
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
        CompiledBuilderSchema.read(type).entries().keySet() == ['service.id', 'configure/URL'] as Set
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
        def plan = CompiledBuilderSchema.read(type)
        def result = KlumStructuredInput.readRoot(type, [(external): 'catalog'], 'shared.yaml')

        then:
        plan.entries().keySet() == [external] as Set
        result.value == 'catalog'
        plan.rejections().find { it.source.contains('#getValue(') }.reason == reason

        where:
        fieldAnnotation             | methodAnnotation                         | external      | reason
        ''                          | ''                                       | 'value'       | '@Builder.Method must declare exactly one payload'
        "@InputName('field/exact')" | "@InputName('getter/exact') @InputIgnore" | 'field/exact' | 'excluded by @InputIgnore'
        "@InputName('field/exact')" | "@JsonProperty('getter/exact') @JsonIgnore" | 'field/exact' | 'excluded by @JsonIgnore(true)'
    }

    def 'field exclusion does not replace the authored getter rejection on a shared accessor'() {
        given:
        def type = schema('''
            @DSL class Service {
                @InputIgnore String value
                @Builder.Method String getValue() { value }
            }
        ''')

        when:
        def plan = CompiledBuilderSchema.read(type)

        then:
        plan.entries().isEmpty()
        plan.rejections().find { it.source == 'literals.Service#value' }.reason == 'excluded by @InputIgnore'
        plan.rejections().find { it.source.contains('#getValue(') }.reason == '@Builder.Method must declare exactly one payload'
    }

    def 'field and authored setter facts stay independent on a shared fallback accessor'() {
        given:
        def type = schema('''
            @DSL class Service {
                @InputName('field/exact') String key
                @Builder.Method @InputName('setter/exact') void setKey(String value) { key = value }
            }
        ''')

        when:
        def plan = CompiledBuilderSchema.read(type)
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
        def plan = CompiledBuilderSchema.read(type)
        def result = KlumStructuredInput.readRoot(type, [(external): 'catalog'], 'shared.yaml')

        then:
        plan.entries().keySet() == [external] as Set
        result.key == 'catalog'
        plan.rejections().find { it.source.contains(ignoredSource) }.reason == 'excluded by @InputIgnore'

        where:
        fieldAnnotation             | methodAnnotation                            | external       | ignoredSource
        '@InputIgnore'              | "@InputName('setter/exact')"                 | 'setter/exact' | '#key'
        "@InputName('field/exact')" | '@InputIgnore'                              | 'field/exact'  | '#setKey('
    }

    def 'canonical literals have required exact values and field or method runtime targets'() {
        when:
        def value = InputName.getDeclaredMethod('value')

        then:
        value.returnType == String
        value.defaultValue == null
        InputIgnore.declaredMethods.length == 0
        [InputName, InputIgnore].every { it.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME }
        [InputName, InputIgnore].every { it.getAnnotation(Target).value() == [ElementType.FIELD, ElementType.METHOD] as ElementType[] }
    }

    def 'literal bridge coalesces names and exclusion wins while false ignore is neutral: #annotations'() {
        given:
        def type = schema("""
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                $annotations String serviceName
                @Builder.Method ${annotations.replace('exact/URL', 'operation/URL')} void configureEndpoint(String value) { serviceName = value }
            }
        """)

        when:
        def plan = CompiledBuilderSchema.read(type)

        then:
        plan.entries().keySet() == names as Set
        plan.rejections().size() == excluded

        where:
        annotations                                                             | names                       | excluded
        "@InputName(' exact/URL ')"                                             | [' exact/URL ', ' operation/URL '] | 0
        "@InputName('exact/URL') @JsonProperty('exact/URL')"                     | ['exact/URL', 'operation/URL'] | 0
        "@InputName('exact/URL') @JsonProperty('') @JsonIgnore(false)"            | ['exact/URL', 'operation/URL'] | 0
        "@InputName('exact/URL') @InputIgnore @JsonIgnore(false)"                 | []                          | 2
        "@InputName('exact/URL') @JsonIgnore"                                    | []                          | 2
        '@InputIgnore @JsonIgnore(false)'                                        | []                          | 2
        '@JsonIgnore(false)'                                                     | ['service-name', 'configure-endpoint'] | 0
    }

    def 'literal names remain exact under any profile and equal Jackson facts are annotation-order independent: #annotations'() {
        given:
        def type = schema("""
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                $annotations String serviceName
            }
        """)

        when:
        def result = KlumStructuredInput.readRoot(type, [(' exact/URL '): 'catalog'], 'literal.yaml')

        then:
        result.serviceName == 'catalog'
        CompiledBuilderSchema.read(type).entries().keySet() == [' exact/URL '] as Set

        where:
        annotations << ["@InputName(' exact/URL ')", "@InputName(' exact/URL ') @JsonProperty(' exact/URL ')",
                        "@JsonProperty(' exact/URL ') @InputName(' exact/URL ')",
                        "@InputName(' exact/URL ') @JsonIgnore(false)"]
    }

    def 'different explicit names fail even with exclusion on #member'() {
        when:
        schema("@DSL class Service { $annotations $member }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Conflicting literal input names on literals.Service#')
        failure.message.contains("@InputName('canonical')")
        failure.message.contains("@JsonProperty('jackson')")

        where:
        annotations                                                           | member
        "@InputName('canonical') @JsonProperty('jackson')"                     | 'String serviceName'
        "@JsonProperty('jackson') @InputName('canonical') @InputIgnore"        | 'String serviceName'
        "@InputName('canonical') @JsonProperty('jackson') @JsonIgnore"         | '@Key String serviceId'
        "@InputName('canonical') @JsonProperty('jackson') @JsonIgnore"         | '@Builder.Method void configure(String value) {}'
    }

    def 'empty canonical name is invalid even when excluded: #member'() {
        when:
        schema("@DSL class Service { @InputName('') @InputIgnore $member }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('@InputName requires a nonempty exact String on literals.Service#')

        where:
        member << ['String serviceName', '@Key String serviceId', '@Builder.Method void configure(String value) {}']
    }

    def 'literal overrides resolve profile collisions without changing native Groovy configuration'() {
        given:
        def type = schema('''
            @DSL class Base { @InputName('literalURL') String URLValue }
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service extends Base {
                String urlValue
                @Builder.Method @InputName('stringURL') void configureURL(String value) { URLValue = value }
                @Builder.Method @InputName('numericURL') void configureURL(int value) { urlValue = value.toString() }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [literalURL: 'base', 'url-value': 'child', stringURL: 'op', numericURL: 7], 'resolved.yaml')

        then:
        result.URLValue == 'op'
        result.urlValue == '7'
        CompiledBuilderSchema.read(type).entries().keySet() == ['literalURL', 'url-value', 'stringURL', 'numericURL'] as Set
    }

    def 'ignored key keeps member obligation and permits externally supplied root or map identity'() {
        given:
        def type = schema('''
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                @Key @InputName('service/id') @InputIgnore String serviceId
                String displayName
            }
            @DSL class Owner { Map<String, Service> services }
        ''')

        when:
        KlumStructuredInput.readRoot(type, ['service/id': 'forbidden'], 'key.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('@InputIgnore')

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
            @InputNaming(Profile.KEBAB_CASE) @DSL class Service {
                @InputName('service/id') String serviceName
                @InputIgnore String hiddenValue
                @Builder.Method void execute(String value) { System.setProperty('literal.executed', 'yes') }
                @Builder.Method @InputIgnore void hiddenOperation(String value) { throw new AssertionError('excluded') }
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
