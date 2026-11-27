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
package com.blackbuild.klum.ast

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import spock.lang.Issue
import spock.lang.Specification

@Issue('1124')
class FactoryProjectionReproducibilityTest extends Specification {

    private static final String SCHEMA = '''
        import com.blackbuild.klum.ast.DSL
        import com.blackbuild.klum.ast.DelegatesToBuilder
        import com.blackbuild.klum.ast.runtime.KlumFactory

        @DSL
        class Schema {
            String environment
            static class Factory extends KlumFactory.Unkeyed<Schema> {
                Factory() { super(Schema) }
                Schema ForEnvironment(String environment) { ForEnvironment(environment) {} }
                Schema ForEnvironment(String environment, @DelegatesToBuilder(Schema) Closure<?> input) {
                    With(environment: environment, input)
                }
            }
        }
    '''

    def "independent compiler invocations emit identical class bytes for dependent Factory overloads"() {
        given:
        Map<String, byte[]> baseline = compileSchema()

        when:
        List<Map<String, byte[]>> repetitions = (1..12).collect { compileSchema() }

        then:
        repetitions.each { classes ->
            assert classes.keySet() == baseline.keySet()
            classes.each { name, bytes ->
                assert Arrays.equals(bytes, baseline[name]): "Class bytes changed for $name"
            }
        }
        projectedDescriptors(baseline['Schema$Factory']) == [
                '(Ljava/lang/String;)LSchema_DSL$Builder;',
                '(Ljava/lang/String;Lgroovy/lang/Closure;)LSchema_DSL$Builder;'
        ]
    }

    private Map<String, byte[]> compileSchema() {
        // Each invocation owns fresh compiler state and has no prior output on its classpath.
        try (GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)) {
            CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(), null, loader)
            unit.addSource('Schema.groovy', SCHEMA)
            unit.compile(Phases.CLASS_GENERATION)
            return unit.classes.collectEntries { [(it.name): it.bytes] }
        }
    }

    private static List<String> projectedDescriptors(byte[] bytes) {
        List<String> descriptors = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name == '$klum$asBuilder$ForEnvironment') {
                    descriptors.add(descriptor)
                }
                return null
            }
        }, 0)
        return descriptors
    }
}
