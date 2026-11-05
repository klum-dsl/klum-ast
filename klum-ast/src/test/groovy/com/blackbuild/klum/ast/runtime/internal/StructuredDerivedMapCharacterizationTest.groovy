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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1028')
class StructuredDerivedMapCharacterizationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String mapping = 'it.toLowerCase()', String destination = 'Map') {
        createSecondaryClass """
            package derivedmap
            import com.blackbuild.klum.ast.*
            class Audit {
                static List<String> events = []
                static final IllegalArgumentException failure = new IllegalArgumentException('mapping failed')
                static String key(String value) {
                    events.add('key:' + value)
                    if (value == 'FAIL') throw failure
                    value == 'NULL' ? null : value.toLowerCase()
                }
            }
            @DSL class Palette {
                @Field(keyMapping = { $mapping })
                $destination<String, String> values
                @PostCreate void created() { Audit.events.add('create') }
                @PostApply void applied() { Audit.events.add('apply') }
                @PostTree void tree() { Audit.events.add('tree') }
            }
        """
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('derivedmap.Palette', false, artifacts)
    }

    private Class audit() { Class.forName('derivedmap.Audit', false, artifacts) }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#groovy-forms-without-a-struct-equivalent')
    def 'Groovy derives Map keys while the proposed Struct value sequence is not admitted'() {
        given:
        def palette = schema()

        when:
        def ordinary = palette.Create.With { value 'BLUE' }

        then:
        ordinary.values == [blue: 'BLUE']

        when:
        palette.Create.FromStruct([values: ['BLUE']], 'palette.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('palette.yaml')
        failure.message.contains('/values')
    }

    def 'ordinary #destination traverses values before destination ordering and replacement'() {
        given:
        def type = schema('Audit.key(it)', destination)

        when:
        def result = type.Create.With {
            value 'BLUE'
            values(['RED', 'blue'])
            values('GREEN', 'RED')
        }

        then:
        result.values == [blue: 'blue', red: 'RED', green: 'GREEN']
        result.values.keySet().toList() == keys
        audit().events == ['create', 'key:BLUE', 'key:RED', 'key:blue', 'key:GREEN', 'key:RED', 'apply', 'tree']

        where:
        destination    | keys
        'Map'          | ['blue', 'red', 'green']
        'SortedMap'    | ['blue', 'green', 'red']
        'NavigableMap' | ['blue', 'green', 'red']
    }

    def 'ordinary null key or mapping failure leaves prior insertions and stops traversal: #bad'() {
        given:
        def type = schema('Audit.key(it)')
        Throwable observed
        Map partial

        when:
        def result = type.Create.With {
            try {
                values(['BLUE', bad, 'RED'])
            } catch (Exception failure) {
                observed = failure
                partial = new LinkedHashMap(values)
            }
        }

        then:
        observed != null
        partial == [blue: 'BLUE']
        result.values == partial
        audit().events == ['create', 'key:BLUE', 'key:' + bad, 'apply', 'tree']
        causes(observed).any { it.message?.contains(message) }
        (bad == 'FAIL') == causes(observed).contains(audit().failure)

        where:
        bad    | message
        'NULL' | 'Key is null'
        'FAIL' | 'mapping failed'
        null   | 'toLowerCase'
    }

    def 'ordinary null value still runs mapping before native value type rejection'() {
        given:
        def type = schema("Audit.events.add('key:' + it); 'constant'")
        Throwable observed

        when:
        def result = type.Create.With {
            try { value((String) null) } catch (Exception failure) { observed = failure }
        }

        then:
        result.values.isEmpty()
        audit().events == ['create', 'key:null', 'apply', 'tree']
        causes(observed).any { it.message?.contains('Value is not of type') }
    }

    def 'Template definition maps values without lifecycle and recipients copy the resulting entries'() {
        given:
        def type = schema('Audit.key(it)')

        when:
        def recipe = type.Create.Template.With { values('BLUE', 'RED', 'blue') }
        def definitionEvents = audit().events.toList()
        audit().events.clear()
        def first
        def second
        type.Template.With(recipe) {
            first = type.Create.With { value 'GREEN' }
            second = type.Create.With { }
        }

        then:
        definitionEvents == ['key:BLUE', 'key:RED', 'key:blue']
        recipe.values == [blue: 'blue', red: 'RED']
        first.values == [blue: 'blue', red: 'RED', green: 'GREEN']
        second.values == recipe.values
        !first.values.is(second.values)
        audit().events == ['create', 'key:GREEN', 'apply', 'tree', 'create', 'apply', 'tree']
    }

    def 'public derived value adder has no persisted field selection and Struct rejects either input shape: #payload'() {
        given:
        def type = schema('Audit.key(it)')
        def builder = Class.forName('derivedmap.Palette_DSL$Builder', false, artifacts)

        when:
        def contract = new InputContractResolver().read(type)
        def adder = builder.getMethod('value', String)
        def field = builder.getMethod('getValues').getAnnotation(GeneratedInput.Field)

        then:
        !contract.entries().containsKey('values')
        contract.rejections().any {
            it.source() == 'derivedmap.Palette#values' &&
                    it.reason().contains('value(java.lang.String, java.lang.String)')
        }
        adder.getAnnotation(GeneratedInput.Selection) == null
        field.value() == 'values'
        field.exclusion() == 'POPULATION'
        field.detail() == 'value'
        builder.methods.findAll { it.getAnnotation(GeneratedInput.Selection)?.field() == 'values' }.empty
        audit().events.empty

        when:
        type.Create.FromStruct([values: payload], 'palette.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('palette.yaml')
        failure.message.contains('/values')
        !audit().events.any { it.startsWith('key:') }

        where:
        payload << [['BLUE'], [blue: 'BLUE']]
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null && !result.contains(current); current = current.cause) {
            result.add(current)
        }
        result
    }
}
