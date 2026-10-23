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
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Issue

@Issue('932')
class StructuredInputCollectionContractTest extends AbstractDSLSpec {

    def 'populates #declaration through its compiler-selected generated element path'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', StructuredInputContractFixtures.collection(declaration))

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'collectioncontracts.Configuration' })
        def fact = plan.entries().find { it.externalName() == 'values' }
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('collectioncontracts.Configuration', true, binaryLoader)
        def contract = Class.forName('collectioncontracts.Configuration_DSL$Builder', false, binaryLoader)
        def itemType = isEnum ? Class.forName('collectioncontracts.Mode', true, binaryLoader) : String
        def single = isMap ? contract.getMethod('addValue', String, itemType) : contract.getMethod('addValue', itemType)
        def operation = isMap ? contract.getMethod(fact.operation().name(), String, itemType) : contract.getMethod(fact.operation().name(), itemType)
        def bulk = contract.getMethod('values', parameter)
        def supplied = isMap ? ['z/key~': 'z', 'a/key': 'a'] : isEnum ? [itemType.Z, itemType.A, itemType.Z] : ['z', 'a', 'z']
        def captured
        def result = configuration.Create.With {
            captured = delegate
            if (isMap) {
                supplied.each { key, value -> operation.invoke(captured, [key, value] as Object[]) }
            } else {
                supplied.each { value -> operation.invoke(captured, [value] as Object[]) }
            }
        }
        println declaration + ' selection=' + fact.operation().descriptorGroup() + ' single=' + single.toGenericString()
        def actual = isMap ? result.values.keySet().toList() : result.values.collect { it.toString() }

        then:
        fact.role().name() == 'PROPERTY'
        fact.payload().kind().name() == (isMap ? 'MAP' : 'LIST')
        fact.payload().type() == declaration.tokenize('<')[0].with { simple ->
            simple == 'EnumSet' ? 'java.util.EnumSet' : 'java.util.' + simple
        }
        fact.operation().name() == 'addValue'
        fact.operation().descriptorGroup().size() == 1
        operation == single
        bulk.parameterTypes.toList() == [parameter]
        single.name == 'addValue'
        actual == expected
        captured.sealed

        cleanup:
        binaryLoader?.close()

        where:
        declaration                  | parameter | isMap | isEnum | expected
        'List<String>'               | Iterable  | false | false  | ['z', 'a', 'z']
        'Set<String>'                | Iterable  | false | false  | ['z', 'a']
        'SortedSet<String>'          | Iterable  | false | false  | ['a', 'z']
        'NavigableSet<String>'       | Iterable  | false | false  | ['a', 'z']
        'EnumSet<Mode>'              | Iterable  | false | true   | ['A', 'Z']
        'Map<String, String>'        | Map       | true  | false  | ['z/key~', 'a/key']
        'SortedMap<String, String>'  | Map       | true  | false  | ['a/key', 'z/key~']
        'NavigableMap<String, String>' | Map     | true  | false  | ['a/key', 'z/key~']
    }
    def 'authored Iterable is outside the current grammar while List and Map payloads are admitted'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', '''
            package authoredcontainers
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration {
                @Builder.Method void iterable(Iterable<String> value) { }
                @Builder.Method void list(List<String> value) { }
                @Builder.Method void map(Map<String, String> value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'authoredcontainers.Configuration' })

        then:
        plan.entries()*.externalName() == ['list', 'map']
        plan.entries()*.role()*.name() == ['OPERATION', 'OPERATION']
        plan.rejections().any {
            it.source() == 'authoredcontainers.Configuration#iterable(java.lang.Iterable):void' &&
                    it.reason() == '@Builder.Method payload is outside the admitted grammar'
        }
    }

    def 'distinguishes an authored plural override from the generated element path'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', StructuredInputContractFixtures.POPULATION)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'shadowedpopulation.Configuration' })
        def implementation = unit.AST.classes.find { it.name == 'shadowedpopulation.Configuration' }
        def contract = GeneratedDslSupport.of(implementation).builderInterface
        def plural = contract.getDeclaredMethods('values').find { it.parameters*.type*.name == ['java.lang.Iterable'] }
        def element = contract.getDeclaredMethods('addValue').find { it.parameters*.type*.name == ['java.lang.String'] }
        def fact = plan.entries().find { it.externalName() == 'values' }

        then:
        fact.role().name() == 'PROPERTY'
        fact.operation().name() == 'addValue'
        fact.operation().descriptorGroup() == ['addValue(java.lang.String):java.lang.String']
        plural.annotations*.classNode*.name.contains('com.blackbuild.klum.ast.Builder$Method')
        !element.annotations*.classNode*.name.contains('com.blackbuild.klum.ast.Builder$Method')
        plan.rejections().any { it.source().contains('#values(java.lang.Iterable)') }
    }

    def 'characterizes #declaration owned maps through the fixed generated keyed creator'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', """
            package keyedpopulation
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                static List<String> events = []
                @Field(members='addEntry') $declaration values
            }
            @DSL class Entry {
                @Key String name
                @Owner Configuration owner
                String text
                @PostCreate void created() { Configuration.events << name }
            }
        """)

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'keyedpopulation.Configuration' })
        def fact = plan.entries().find { it.externalName() == 'values' }
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('keyedpopulation.Configuration', true, binaryLoader)
        def contract = Class.forName('keyedpopulation.Configuration_DSL$Builder', false, binaryLoader)
        def creator = contract.getMethod('addEntry', String, Closure)
        def result = configuration.Create.With {
            creator.invoke(delegate, ['z/key~', { text 'z' }] as Object[])
            creator.invoke(delegate, ['a/key', { text 'a' }] as Object[])
        }
        println declaration + ' selection=' + fact.operation().descriptorGroup() + ' keyed=' + creator.toGenericString()

        then:
        fact.role().name() == 'KEYED_OWNED_MAP'
        fact.payload().type() == 'keyedpopulation.Entry'
        fact.mapKeyType() == 'java.lang.String'
        fact.excludedChildKeyMember() == 'name'
        fact.source() == 'keyedpopulation.Configuration#values'
        configuration.events == ['z/key~', 'a/key']
        result.values.keySet().toList() == expected
        result.values.values().every { it.owner.is(result) }

        cleanup:
        binaryLoader?.close()

        where:
        declaration                    | expected
        'Map<String, Entry>'            | ['z/key~', 'a/key']
        'SortedMap<String, Entry>'      | ['a/key', 'z/key~']
        'NavigableMap<String, Entry>'   | ['a/key', 'z/key~']
    }

    def 'rejects whole-container operation argument #target without restricting supported field declarations'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', StructuredInputContractFixtures.operationArgument(target))

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'restrictedarguments.Configuration' })

        then:
        plan.entries()*.externalName() == ['fieldValues']
        plan.entries()[0].payload().type() == 'java.util.NavigableSet'
        plan.rejections().any {
            it.source().startsWith('restrictedarguments.Configuration#consume(') &&
                    it.reason().contains('unsupported whole-container payload target java.util.' + target.tokenize('<')[0])
        }

        where:
        target << ['Set<String>', 'SortedSet<String>', 'NavigableSet<String>', 'EnumSet<Mode>', 'Collection<String>',
                   'ArrayList<String>', 'LinkedHashMap<String, String>', 'TreeMap<String, String>']
    }

    def 'a shadowed element method is not mistaken for a generated field population path'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', '''
            package shadowedelement
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                @Field(members='store') Set<String> values
                @Builder.Method void store(String value) { throw new AssertionError('Authored method selected') }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'shadowedelement.Configuration' })

        then:
        plan.entries()*.externalName() == ['store']
        plan.entries()[0].role().name() == 'OPERATION'
        plan.rejections().any {
            it.source() == 'shadowedelement.Configuration#values' &&
                    it.reason().contains('no unambiguous compiler-generated element population method store(java.lang.String)')
        }
    }

    def 'nested ordinary payloads use generated element signatures while nested sorted container arguments reject'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Schema.groovy', '''
            package nestedpopulation
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                @Field(members='addRecord') List<Map<String, List<String>>> records
                @Field(members='putRecord') Map<String, List<Map<String, String>>> mappings
                List<NavigableSet<String>> unsupported
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'nestedpopulation.Configuration' })

        then:
        plan.entries()*.externalName() == ['mappings', 'records']
        plan.entries().collectEntries { [(it.externalName()): it.operation().descriptorGroup()] } == [
                mappings: ['putRecord(java.lang.String, java.util.List):java.util.List'],
                records: ['addRecord(java.util.Map):java.util.Map']
        ]
        plan.rejections().any {
            it.source() == 'nestedpopulation.Configuration#unsupported' &&
                    it.reason().contains('unsupported whole-container payload target java.util.NavigableSet')
        }
    }

}
