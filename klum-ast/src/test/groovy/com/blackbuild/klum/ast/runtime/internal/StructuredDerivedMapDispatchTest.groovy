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
import spock.lang.Issue

@Issue('1028')
class StructuredDerivedMapDispatchTest extends AbstractDSLSpec {
    private URLClassLoader artifacts
    private GroovyClassLoader descendantCompiler
    private Class audit
    private Class valueType

    private Class schema(boolean enums) {
        String value = enums ? 'Shade' : 'String'
        createSecondaryClass """
            package mapdispatch
            import com.blackbuild.klum.ast.*
            class Audit {
                static List<String> events = []
                static final IllegalArgumentException mappingFailure = new IllegalArgumentException('mapping failed')
                static final IllegalArgumentException converterFailure = new IllegalArgumentException('converter called')
                static String key(Object value) {
                    events.add('map:' + value)
                    if (value.toString() == 'FAIL') throw mappingFailure
                    value.toString().toLowerCase()
                }
            }
            enum Shade {
                SEED, OLD, BLUE, RED, FAIL;
                @Converter static Shade fromWire(String raw) {
                    Audit.events.add('convert:' + raw)
                    throw Audit.converterFailure
                }
            }
            @DSL class Base {
                ${enums ? "@Converters(excludeMethods = ['valueOf'])" : ''}
                @Field(members = 'putValue', keyMapping = { Audit.key(it) })
                Map<String, $value> values = [seed: ${enums ? 'Shade.SEED' : "'SEED'"}, blue: ${enums ? 'Shade.OLD' : "'OLD'"}]
            }
        """
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        descendantCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        descendantCompiler.parseClass("""
            package mapdispatch
            import com.blackbuild.klum.ast.*
            @DSL class Palette extends Base {
                @Struct.Ignore @Builder.Method $value putValue($value raw) {
                    Audit.events.add('adder:' + raw)
                    super.putValue(raw)
                }
                @Struct.Ignore @Builder.Method $value putValue(${enums ? 'String' : 'CharSequence'} raw) {
                    Audit.events.add('alternate:' + raw)
                    ${enums ? 'Shade.fromWire(raw)' : 'throw Audit.converterFailure'}
                }
            }
        """)
        audit = Class.forName('mapdispatch.Audit', false, artifacts)
        valueType = enums ? Class.forName('mapdispatch.Shade', false, artifacts) : String
        Class.forName('mapdispatch.Palette', false, artifacts)
    }

    def cleanup() {
        descendantCompiler?.close()
        artifacts?.close()
    }

    private Object nativeValue(String raw) { valueType == String ? raw : Enum.valueOf(valueType, raw) }

    private static Map snapshot(Map values) { values.collectEntries { key, value -> [(key): value.toString()] } }

    def 'source free #kind input invokes the native override and preserves seeded entries replacement and order'() {
        given:
        def type = schema(enums)
        def input = enums ? ['BLUE', 'RED', 'BLUE'] : ['BLUE', 'RED', 'blue']
        def nativeItems = input.collect { nativeValue(it) }

        when:
        def ordinary = type.Create.With { nativeItems.each { putValue(it) } }
        def ordinaryEvents = audit.events.toList()
        audit.events.clear()
        def structured = type.Create.FromStruct([values: input], 'palette.yaml')
        def entry = new InputContractResolver().read(type).entries().values

        then:
        structured.values == ordinary.values
        snapshot(structured.values) == [seed: 'SEED', blue: enums ? 'BLUE' : 'blue', red: 'RED']
        structured.values.keySet().toList() == ['seed', 'blue', 'red']
        audit.events == ordinaryEvents
        audit.events == input.collectMany { ['adder:' + it, 'map:' + it] }
        entry.dispatch().declaringClass.name == 'mapdispatch.Base_DSL$Builder'
        entry.dispatch().parameterTypes == [valueType] as Class[]
        !audit.events.any { it.startsWith('alternate:') || it.startsWith('convert:') }

        when: 'the competing ordinary route demonstrates a different executable'
        Throwable alternate
        type.Create.With {
            try {
                if (enums) putValue('BLUE')
                else putValue((CharSequence) new StringBuilder('BLUE'))
            } catch (Exception failure) { alternate = failure }
        }

        then:
        causes(alternate).contains(audit.converterFailure)
        audit.events.contains('alternate:BLUE')

        where:
        kind     | enums
        'String' | false
        'enum'   | true
    }

    def 'source free #kind mapping failure preserves exact cause prior defaults and item index'() {
        given:
        def type = schema(enums)
        def nativeItems = ['BLUE', 'FAIL', 'RED'].collect { nativeValue(it) }
        Throwable ordinaryFailure
        Throwable structuredFailure

        when:
        def ordinary = type.Create.With {
            try { nativeItems.each { putValue(it) } } catch (Exception failure) { ordinaryFailure = failure }
        }
        def ordinaryEvents = audit.events.toList()
        audit.events.clear()
        def structured = type.Create.With {
            try { applyStruct([values: ['BLUE', 'FAIL', 'RED']], 'palette.yaml') }
            catch (KlumModelException failure) { structuredFailure = failure }
        }

        then:
        structured.values == ordinary.values
        snapshot(structured.values) == [seed: 'SEED', blue: 'BLUE']
        audit.events == ordinaryEvents
        audit.events == ['adder:BLUE', 'map:BLUE', 'adder:FAIL', 'map:FAIL']
        causes(ordinaryFailure).contains(audit.mappingFailure)
        causes(structuredFailure).contains(audit.mappingFailure)
        structuredFailure.message.contains('palette.yaml')
        structuredFailure.message.contains('/values/1')
        structuredFailure.message.contains('mapdispatch.Base_DSL$Builder')

        when: 'an explicit generated enum converter stays an ordinary separate route'
        Throwable converterFailure
        if (enums) {
            type.Create.With {
                try { putValue('ignored-literal-key', 'BLUE') }
                catch (Exception failure) { converterFailure = failure }
            }
        }

        then:
        !enums || causes(converterFailure).contains(audit.converterFailure)

        where:
        kind     | enums
        'String' | false
        'enum'   | true
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null && !result.contains(current); current = current.cause) result.add(current)
        result
    }
}
