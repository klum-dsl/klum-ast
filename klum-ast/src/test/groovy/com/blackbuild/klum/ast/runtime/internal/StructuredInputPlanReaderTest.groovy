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
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue

@Issue('932')
class StructuredInputPlanReaderTest extends AbstractDSLSpec {

    def 'reads packaged carrier roles and exact public contracts without Schema or enum initialization'() {
        given:
        createClass '''
            package runtimeplan
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                static { throw new AssertionError('Schema initialized during extraction') }
                String title
                Stage stage
                Branch branch
                Map<String, Entry> entries
                @Builder.Method void words(List<String> value) { }
            }
            enum Stage {
                READY
                static { throw new AssertionError('Enum initialized during extraction') }
            }
            @DSL class Branch { String text }
            @DSL class Entry { @Key String name; String text }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def document = Class.forName('runtimeplan.Document', false, binaryLoader)

        when:
        def plan = StructuredInputPlan.read(document)

        then:
        plan.entries().keySet() == ['branch', 'entries', 'stage', 'title', 'words'] as Set
        plan.entries().get('branch').role().name() == 'OWNED_OBJECT'
        plan.entries().get('entries').role().name() == 'KEYED_OWNED_MAP'
        nameOf(plan.entries().get('stage').payload().type()) == 'runtimeplan.Stage'
        plan.entries().get('words').methods()*.parameterTypes*.toList() == [[List]]
        nameOf(plan.contract()) == 'runtimeplan.Document_DSL$Builder'

        cleanup:
        binaryLoader.close()
    }
    def 'rejects #damage packaged carrier without initializing Schema or enum code'() {
        given:
        createClass '''
            package damagedplan
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration {
                static { throw new AssertionError('Schema initialized during rejected extraction') }
                String title
                Mode mode
            }
            enum Mode {
                READY
                static { throw new AssertionError('Enum initialized during rejected extraction') }
            }
        '''
        def file = new File(compilerConfiguration.targetDirectory, 'damagedplan/Configuration_DSL$WirePlan.class')
        String data = dataOf(file)
        switch (damage) {
            case 'missing': assert file.delete(); break
            case 'truncated class': file.bytes = [0, 1, 2] as byte[]; break
            case 'header': replaceData(file, data.replace('WIRE-PLAN/B', 'UNKNOWN')); break
            case 'identity': replaceData(file, data.replace(token('damagedplan.Configuration'), token('damagedplan.Other'))); break
            case 'signature': replaceData(file, data.replace(token('title(java.lang.String):java.lang.String'), token('title(java.lang.Integer):java.lang.String'))); break
            case 'source owner': replaceData(file, data.replace(token('damagedplan.Configuration#title'), token('damagedplan.Other#title'))); break
            case 'enum type': replaceData(file, data.replace('[ENUM,' + token('damagedplan.Mode'), '[ENUM,' + token('java.lang.String'))); break
        }
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('damagedplan.Configuration', false, binaryLoader)

        when:
        StructuredInputPlan.read(configuration)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile and package the Schema')
        failure.cause != null

        cleanup:
        binaryLoader?.close()

        where:
        damage << ['missing', 'truncated class', 'header', 'identity', 'signature', 'source owner', 'enum type']
    }

    private static String token(String value) {
        Base64.urlEncoder.withoutPadding().encodeToString(value.getBytes('UTF-8'))
    }

    private static String dataOf(File file) {
        String[] data = new String[1]
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name == 'DATA') data[0] = (String) value
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        data[0]
    }

    private static void replaceData(File file, String data) {
        def writer = new ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
                'damagedplan/Configuration_DSL$WirePlan', null, 'java/lang/Object', null)
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                'DATA', 'Ljava/lang/String;', null, data).visitEnd()
        writer.visitEnd()
        file.bytes = writer.toByteArray()
    }

    @CompileStatic
    private static String nameOf(Class<?> type) { type.getName() }
}
