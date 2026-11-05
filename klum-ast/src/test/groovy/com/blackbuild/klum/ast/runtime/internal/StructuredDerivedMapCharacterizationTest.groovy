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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1028')
class StructuredDerivedMapCharacterizationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String mapping = 'it.toLowerCase()', String destination = 'Map', String valueType = 'String') {
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
                $destination<String, $valueType> values
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
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#derived-key-map-values')
    def 'derives Map keys from structured values through the ordinary Builder adder'() {
        given:
        def palette = schema()

        when:
        def ordinary = palette.Create.With { value 'BLUE' }

        def structured = palette.Create.FromStruct([values: ['BLUE']], 'palette.yaml')

        then:
        structured.values == [blue: 'BLUE']
        structured.values == ordinary.values
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

        def ordinaryEvents = audit().events.toList()
        audit().events.clear()
        def structured = type.Create.FromStruct([values: ['BLUE', 'RED', 'blue', 'GREEN', 'RED']])

        then:
        structured.values == result.values
        audit().events == ordinaryEvents
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

    def 'exact native Map selection is a List payload with one ordinary value-only adder'() {
        given:
        def type = schema('Audit.key(it)')
        def builder = Class.forName('derivedmap.Palette_DSL$Builder', false, artifacts)

        when:
        def contract = new InputContractResolver().read(type)
        def adder = builder.getMethod('value', String)
        def entry = contract.entries().values

        then:
        contract.rejections().isEmpty()
        entry.role() == CompiledBuilderSchema.Role.PROPERTY
        entry.keyType() == String.name
        entry.payload().kind() == CompiledBuilderSchema.Kind.LIST
        entry.payload().item().type() == String
        entry.dispatch() == adder
        adder.getAnnotation(GeneratedInput.Selection).field() == 'values'
        adder.getAnnotation(GeneratedInput.Selection).role() == 'PROPERTY'
        builder.getMethod('getValues').getAnnotation(GeneratedInput.Field).exclusion() == ''
        audit().events.isEmpty()
    }

    def 'Struct validates and invokes reached items without preflight or rollback: #bad'() {
        given:
        def type = schema('Audit.key(it)')
        Throwable observed
        Map partial

        when:
        def result = type.Create.With {
            try {
                applyStruct([values: ['BLUE', bad, 'RED']], 'palette.yaml')
            } catch (KlumModelException failure) {
                observed = failure
                partial = new LinkedHashMap(values)
            }
        }

        then:
        observed != null
        observed.message.contains('palette.yaml')
        observed.message.contains('/values/1')
        observed.message.contains('derivedmap.Palette_DSL$Builder')
        partial == [blue: 'BLUE']
        result.values == partial
        audit().events == ['create', 'key:BLUE'] + mapped + ['apply', 'tree']
        (bad == 'FAIL') == causes(observed).contains(audit().failure)

        where:
        bad    | mapped
        'NULL' | ['key:NULL']
        'FAIL' | ['key:FAIL']
        null   | []
        12     | []
        true   | []
        [:]    | []
        []     | []
    }

    def 'native #valueType values use existing strict scalar conversion before mapping'() {
        given:
        def type = schema('it.toString().toLowerCase()', 'Map', valueType)

        when:
        def result = type.Create.FromStruct([values: supplied])
        def ordinary = type.Create.With { supplied.each { value(it) } }

        then:
        result.values == expected
        result.values == ordinary.values

        where:
        valueType | supplied      | expected
        'Boolean' | [true, false] | ['true': true, 'false': false]
        'Integer' | [7, 3, 7]     | ['7': 7, '3': 3]
    }

    def 'actual mapped keys retain ordinary runtime behavior without added output key coercion'() {
        given:
        def type = schema('it.length()')

        when:
        def structured = type.Create.FromStruct([values: ['BLUE', 'RED']])
        def ordinary = type.Create.With { value 'BLUE'; value 'RED' }

        then:
        structured.values == [(4): 'BLUE', (3): 'RED']
        structured.values == ordinary.values
        structured.values.keySet().every { it instanceof Integer }
    }

    def 'empty sequences preserve initial entries and literal Map payloads reject'() {
        given:
        def type = schema('Audit.key(it)')
        Throwable observed

        when:
        def result = type.Create.With {
            value 'BLUE'
            applyStruct([values: []])
            try { applyStruct([values: [red: 'RED']], 'palette.yaml') } catch (KlumModelException failure) { observed = failure }
        }

        then:
        result.values == [blue: 'BLUE']
        observed.message.contains('/values')
        audit().events == ['create', 'key:BLUE', 'apply', 'apply', 'tree']
    }

    def 'Struct Templates derive entries during definition and copy them into fresh recipients'() {
        given:
        def type = schema('Audit.key(it)')

        when:
        def recipe = type.Create.Template.FromStruct([values: ['BLUE', 'RED', 'blue']])
        def definitionEvents = audit().events.toList()
        audit().events.clear()
        def result
        type.Template.With(recipe) { result = type.Create.FromStruct([values: ['GREEN']]) }

        then:
        recipe.values == [blue: 'blue', red: 'RED']
        definitionEvents == ['key:BLUE', 'key:RED', 'key:blue']
        result.values == [blue: 'blue', red: 'RED', green: 'GREEN']
        audit().events == ['create', 'key:GREEN', 'apply', 'tree']
    }

    def 'renamed inherited enum Map uses the selected custom member without mutating input'() {
        given:
        createSecondaryClass '''
            package derivedmap
            import com.blackbuild.klum.ast.*
            enum Shade { BLUE, RED }
            @DSL class Base {
                @Struct.Name('labels/~')
                @Field(members = 'putShade', keyMapping = { it.name().toLowerCase() })
                Map<String, Shade> shades
            }
            @DSL class Palette extends Base { }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def type = Class.forName('derivedmap.Palette', false, artifacts)
        def input = [('labels/~'): ['BLUE', 'RED', 'BLUE']]

        when:
        def result = type.Create.FromStruct(input)
        def entry = new InputContractResolver().read(type).entries()['labels/~']

        then:
        result.shades.collectEntries { key, value -> [(key): value.name()] } == [blue: 'BLUE', red: 'RED']
        entry.operation() == 'putShade'
        entry.payload().item().kind() == CompiledBuilderSchema.Kind.ENUM
        input == [('labels/~'): ['BLUE', 'RED', 'BLUE']]

        when:
        type.Create.FromStruct([('labels/~'): ['BLUE', 'blue']], 'shades.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/labels~1~0/1')
    }

    def 'separately compiled descendants retain only non overridden native Map population'() {
        given:
        schema('Audit.key(it)')
        def compiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        compiler.parseClass('''
            package derivedmap
            import com.blackbuild.klum.ast.*
            @DSL class Inherited extends Palette { }
            @DSL class Overridden extends Palette {
                @Struct.Name('replacement') @Struct.Ignore @Builder.Method
                String value(String raw) { Audit.events.add('override:' + raw); raw }
            }
            @DSL class Leaf extends Overridden { }
        ''')
        def inherited = Class.forName('derivedmap.Inherited', false, artifacts)
        def leaf = Class.forName('derivedmap.Leaf', false, artifacts)

        when:
        def result = inherited.Create.FromStruct([values: ['BLUE']])
        def rejected = new InputContractResolver().read(leaf)
        def ordinary = leaf.Create.With { value 'RED' }

        then:
        result.values == [blue: 'BLUE']
        ordinary.values.isEmpty()
        !rejected.entries().containsKey('values')
        rejected.rejections().any { it.source() == 'derivedmap.Palette#values' && it.reason().contains('overridden') }
        audit().events.contains('override:RED')

        when:
        leaf.Create.FromStruct([values: ['GREEN']], 'inherited.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/values')
        !audit().events.contains('override:GREEN')

        cleanup:
        compiler?.close()
    }

    def 'non K1 family remains excluded: #declaration'() {
        given:
        createSecondaryClass """
            package derivedmap
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Child { String text }
            @DSL class KeyedChild { @Key String name }
            @DSL class Palette {
                @Field(keyMapping = { it.toString() }) $declaration values
            }
        """
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def type = Class.forName('derivedmap.Palette', false, artifacts)

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('values')

        when:
        type.Create.FromStruct([values: []])

        then:
        thrown(KlumModelException)

        where:
        declaration << ['Map<String, Duration>', 'Map<String, Child>', 'Map<String, KeyedChild>', 'Map<Integer, String>']
    }

    def 'authored value adder cannot acquire generated field provenance'() {
        given:
        createSecondaryClass '''
            package derivedmap
            import com.blackbuild.klum.ast.*
            @DSL class Palette {
                @Field(keyMapping = { it.toLowerCase() }) Map<String, String> values
                @Struct.Ignore @Builder.Method String value(String raw) { throw new AssertionError('authored adder executed') }
            }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def type = Class.forName('derivedmap.Palette', false, artifacts)

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('values')
        contract.rejections().any { it.source() == 'derivedmap.Palette#values' && it.reason().contains('authored overrides') }
    }

    def 'corrupt exact Map selection fails before root allocation: #mode'() {
        given:
        def type = schema('Audit.key(it)')
        corruptSelection(mode)

        when:
        type.Create.FromStruct([values: ['BLUE']], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('metadata.yaml')
        failure.message.contains('recompile')
        failure.message.contains(reason)
        audit().events.isEmpty()

        where:
        mode          | reason
        'missing'     | 'Missing compiler input/population selection'
        'extra'       | 'one exact selection'
        'return'      | 'signature mismatch'
        'parameter'   | 'signature mismatch'
        'synthetic'   | 'signature mismatch'
        'bridge'      | 'signature mismatch'
    }

    private void corruptSelection(String mode) {
        def file = new File(compilerConfiguration.targetDirectory, 'derivedmap/Palette_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean adder = name == 'value' && descriptor == '(Ljava/lang/String;)Ljava/lang/String;'
                if (adder && mode == 'return') signature = '(Ljava/lang/String;)Ljava/lang/Object;'
                if (adder && mode == 'parameter') signature = '(Ljava/lang/Integer;)Ljava/lang/String;'
                if (adder && mode == 'synthetic') access |= Opcodes.ACC_SYNTHETIC
                if (adder && mode == 'bridge') access |= Opcodes.ACC_BRIDGE
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        adder && mode == 'missing' && annotation.endsWith('GeneratedInput$Selection;') ? null : super.visitAnnotation(annotation, visible)
                    }
                }
            }

            @Override void visitEnd() {
                if (mode == 'extra') {
                    def method = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, 'value',
                            '(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;', null, null)
                    def selection = method.visitAnnotation('Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Selection;', true)
                    selection.visit('field', 'values')
                    selection.visit('role', 'PROPERTY')
                    selection.visitEnd()
                    method.visitEnd()
                }
                super.visitEnd()
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null && !result.contains(current); current = current.cause) {
            result.add(current)
        }
        result
    }
}
