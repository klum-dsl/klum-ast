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
package com.blackbuild.klum.ast.gradle

import spock.lang.Issue
import spock.lang.Specification

import java.util.Collection
import java.util.Optional

@Issue('391')
class SchemaModuleValidationTest extends Specification {

    def "accepts valid Groovy 4 and 5 core-only Schema modules"(int groovyMajor) {
        expect:
        !diagnostic(groovyMajor, coreDescriptor()).present

        where:
        groovyMajor << [4, 5]
    }

    def "rejects a Groovy 3 named Schema module with classpath remediation"() {
        when:
        String diagnostic = diagnostic(3, coreDescriptor()).orElseThrow()

        then:
        diagnostic.contains("KlumAST schema module ':schema' cannot use module-info.java with Groovy 3.")
        diagnostic.contains('Groovy 3 is a supported classpath-only configuration')
        diagnostic.contains('Remove module-info.java and compile/run this Schema on the ordinary classpath')
        diagnostic.contains('will not rewrite module-info.java or add JVM module-path workaround flags')
    }

    def "reports each missing core module and a non-static compiler requirement"(String descriptor, String expected) {
        expect:
        diagnostic(5, descriptor).orElseThrow().contains("missing `${expected}`")

        where:
        descriptor                                                                    | expected
        descriptorWithout('com.blackbuild.klum.ast.annotations')                       | 'requires com.blackbuild.klum.ast.annotations;'
        descriptorWithout('com.blackbuild.klum.ast.runtime')                           | 'requires com.blackbuild.klum.ast.runtime;'
        descriptorWithout('com.blackbuild.klum.ast.compiler')                          | 'requires static com.blackbuild.klum.ast.compiler;'
        descriptorWithout('org.apache.groovy')                                         | 'requires org.apache.groovy;'
        coreDescriptor().replace('requires static com.blackbuild.klum.ast.compiler;',
                'requires com.blackbuild.klum.ast.compiler;')                         | 'requires static com.blackbuild.klum.ast.compiler;'
    }

    def "requires and opens configured adapter modules"(String adapterModule, String target) {
        when:
        String diagnostic = diagnostic(4, coreDescriptor(), [adapterModule]).orElseThrow()

        then:
        diagnostic.contains("missing `requires ${adapterModule};`")
        diagnostic.contains("missing `opens example.schema to ${target};`")
        diagnostic.contains("requires ${adapterModule};")
        diagnostic.contains("opens example.schema to com.blackbuild.klum.ast.runtime, ${target};")

        where:
        adapterModule                             | target
        'com.blackbuild.klum.ast.jackson'         | 'com.fasterxml.jackson.databind'
        'com.blackbuild.klum.ast.validation.bean' | 'org.hibernate.validator'
    }

    def "requires an adapter opening when the adapter is declared directly"(String adapterModule, String target) {
        given:
        String descriptor = coreDescriptor().replace('opens example.schema to com.blackbuild.klum.ast.runtime;', """
            requires ${adapterModule};
            opens example.schema to com.blackbuild.klum.ast.runtime;
        """.stripIndent())

        expect:
        diagnostic(5, descriptor).orElseThrow().contains("missing `opens example.schema to ${target};`")

        where:
        adapterModule                             | target
        'com.blackbuild.klum.ast.jackson'         | 'com.fasterxml.jackson.databind'
        'com.blackbuild.klum.ast.validation.bean' | 'org.hibernate.validator'
    }

    def "aggregates missing runtime and adapter-qualified openings"() {
        given:
        String descriptor = coreDescriptor().replace('opens example.schema to com.blackbuild.klum.ast.runtime;', '')

        when:
        String diagnostic = diagnostic(5, descriptor, [
                'com.blackbuild.klum.ast.jackson',
                'com.blackbuild.klum.ast.validation.bean']).orElseThrow()

        then:
        diagnostic.contains('missing `opens example.schema to com.blackbuild.klum.ast.runtime;`')
        diagnostic.contains('missing `opens example.schema to com.fasterxml.jackson.databind;`')
        diagnostic.contains('missing `opens example.schema to org.hibernate.validator;`')
    }

    def "finds Java and Groovy Schema packages through comments and multiline declarations"() {
        given:
        String descriptor = coreRequirements() + '''
            opens alpha.java to com.blackbuild.klum.ast.runtime;
        '''
        Collection<String> sources = [
                '''/* package ignored.block; */
                   package alpha.java;
                   class JavaSchema {}''',
                '''// package ignored.line
                   package beta
                       .groovy
                   class GroovySchema {}''',
                '''package gamma /* comment */ . mixed
                   class MixedSchema {}'''
        ]

        when:
        String diagnostic = diagnostic(5, descriptor, [], sources).orElseThrow()

        then:
        diagnostic.contains('missing `opens beta.groovy to com.blackbuild.klum.ast.runtime;`')
        diagnostic.contains('missing `opens gamma.mixed to com.blackbuild.klum.ast.runtime;`')
        !diagnostic.contains('ignored.block')
        !diagnostic.contains('ignored.line')
    }

    def "returns one copyable aggregate remediation and accepts the complete descriptor"() {
        given:
        String incomplete = '''
            /* requires com.blackbuild.klum.ast.runtime; */
            module example.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.compiler;
                // requires org.apache.groovy;
            }
        '''

        when:
        String validationMessage = diagnostic(5, incomplete, ['com.blackbuild.klum.ast.jackson']).orElseThrow()

        then:
        validationMessage.contains("KlumAST schema module validation failed for ':schema':")
        validationMessage.contains('missing `requires com.blackbuild.klum.ast.runtime;`')
        validationMessage.contains('missing `requires static com.blackbuild.klum.ast.compiler;`')
        validationMessage.contains('missing `requires org.apache.groovy;`')
        validationMessage.contains('missing `requires com.blackbuild.klum.ast.jackson;`')
        validationMessage.contains('module-info.java remains user-owned')
        validationMessage.contains('requires static com.blackbuild.klum.ast.compiler;')
        validationMessage.contains('opens example.schema to com.blackbuild.klum.ast.runtime, com.fasterxml.jackson.databind;')

        and:
        !diagnostic(5, fullDescriptor(), [
                'com.blackbuild.klum.ast.jackson',
                'com.blackbuild.klum.ast.validation.bean']).present
    }

    def "ignores an absent descriptor and malformed optional input without throwing"() {
        expect:
        !SchemaModuleValidation.diagnostic(null).present
        !diagnostic(3, null).present
        !diagnostic(5, coreDescriptor(), [null]).present
    }

    private static Optional<String> diagnostic(int groovyMajor, String descriptor, Collection<String> adapters = [], Collection<String> sources = defaultSources()) {
        SchemaModuleValidation.diagnostic(new SchemaModuleValidation.Input(':schema', groovyMajor, descriptor, sources, adapters))
    }

    private static Collection<String> defaultSources() {
        ['package example.schema\nclass Example {}']
    }

    private static String coreDescriptor() {
        coreRequirements() + '''
            opens example.schema to com.blackbuild.klum.ast.runtime;
            }
        '''
    }

    private static String coreRequirements() {
        '''
            module example.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.runtime;
                requires static com.blackbuild.klum.ast.compiler;
                requires org.apache.groovy;
        '''
    }

    private static String descriptorWithout(String module) {
        coreDescriptor().readLines().findAll { !it.contains("requires ${module};") && !it.contains("requires static ${module};") }.join('\n')
    }

    private static String fullDescriptor() {
        coreRequirements() + '''
                requires com.blackbuild.klum.ast.jackson;
                requires com.blackbuild.klum.ast.validation.bean;
                opens example.schema to com.blackbuild.klum.ast.runtime, com.fasterxml.jackson.databind, org.hibernate.validator;
            }
        '''
    }
}
