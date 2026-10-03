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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("240")
class CustomEqualityDiagnosticTest extends AbstractDSLSpec {

    def "default custom equality warns for selected #category without changing semantics"() {
        given:
        def source = """
            import groovy.transform.EqualsAndHashCode
            @EqualsAndHashCode @DSL
            class Record {
                String name
                $declaration
            }
        """
        def unit = compile(source)
        createClass(source)

        when:
        def left = clazz.Create.With(name: 'same', setMetadata: 'left')
        def right = clazz.Create.With(name: 'same', setMetadata: 'right')

        then:
        warnings(unit).size() == 1
        warnings(unit)[0].contains('metadata')
        unit.ast.classes.find { it.name == 'Record' }.annotations.find {
            it.classNode.name == 'groovy.transform.EqualsAndHashCode'
        }.members.isEmpty()
        left != right
        left.hashCode() != right.hashCode()

        where:
        category          | declaration
        'owner'           | '@Owner Object metadata'
        'Klum transient'  | '@Field(FieldType.TRANSIENT) String metadata'
        'Java transient'  | 'transient String metadata'
    }

    def "effective selection respects #options for #declaration"() {
        when:
        def unit = compile("""
            import groovy.transform.EqualsAndHashCode
            import groovy.transform.CompileStatic
            $annotations
            class Record {
                String name
                $declaration
            }
        """)

        then:
        warnings(unit).size() == count
        if (count) {
            assert warnings(unit)[0].contains(field)
            assert warnings(unit)[0].contains('explicit excludes or deliberate includes')
        }

        where:
        annotations                                                           | options                     | declaration                                          | count | field
        '@DSL @EqualsAndHashCode'                                              | 'default private'           | '@Owner private Object parent'                       | 0     | 'parent'
        '@DSL @EqualsAndHashCode(includeFields=true)'                          | 'includeFields'             | '@Owner private Object parent'                       | 1     | 'parent'
        '@DSL @EqualsAndHashCode'                                              | 'default dollar property'   | 'String $metadata'                                   | 0     | '$metadata'
        '@DSL @EqualsAndHashCode(allNames=true)'                               | 'allNames property'         | 'String $metadata'                                   | 1     | '$metadata'
        '@DSL @EqualsAndHashCode(allNames=true)'                               | 'allNames private'          | 'private String $metadata'                           | 0     | '$metadata'
        '@DSL @EqualsAndHashCode(includeFields=true)'                          | 'includeFields private'     | 'private String $metadata'                           | 0     | '$metadata'
        '@DSL @EqualsAndHashCode(includeFields=true, allNames=true)'           | 'generated storage'         | ''                                                   | 1     | '$state'
        '@EqualsAndHashCode(includeFields=true, allNames=true) @DSL'           | 'earlier Groovy transform'  | ''                                                   | 0     | '$state'
        '@EqualsAndHashCode(excludes="parent") @DSL'                           | 'excluded owner'            | '@Owner Object parent'                               | 0     | 'parent'
        '@EqualsAndHashCode(excludes=["name"]) @DSL'                           | 'unexcluded owner'          | '@Owner Object parent'                               | 1     | 'parent'
        '@EqualsAndHashCode(includes="parent") @DSL'                           | 'explicit owner includes'   | '@Owner Object parent'                               | 0     | 'parent'
        '@CompileStatic @EqualsAndHashCode @DSL'              | 'static compilation'       | '@Owner Object parent'                               | 1     | 'parent'
        '@DSL @EqualsAndHashCode(includes=[])'                                 | 'explicit empty includes'   | '@Owner Object parent'                               | 0     | 'parent'
        '@EqualsAndHashCode(includes=["name"]) @DSL'                           | 'explicit name includes'    | '@Owner Object parent'                               | 0     | 'parent'
        '@DSL @EqualsAndHashCode'                                              | 'static owner'              | '@Owner static Object parent'                        | 0     | 'parent'
        '@DSL @EqualsAndHashCode(allProperties=true)'                          | 'pseudo getter'             | '@Owner private Object parent; Object getParent() { parent }' | 1 | 'parent'
        '@EqualsAndHashCode @DSL'                                              | 'default owner before DSL'  | '@Owner Object parent'                               | 1     | 'parent'
        '@DSL @EqualsAndHashCode'                                              | 'default owner after DSL'   | '@Owner Object parent'                               | 0     | 'parent'
        '@DSL'                                                                | 'Klum default'              | '@Owner Object parent'                               | 0     | 'parent'
        '@DSL @EqualsAndHashCode'                                              | 'ordinary semantic fields'  | 'String description'                                 | 0     | 'description'
        '@DSL @EqualsAndHashCode'                                              | 'boolean transient'         | '@Field(FieldType.TRANSIENT) boolean seen'            | 1     | 'seen'
    }

    def "handwritten equality and hash code remain silent and unchanged"() {
        given:
        def source = '''
            import groovy.transform.EqualsAndHashCode
            import groovy.transform.Generated
            @DSL @EqualsAndHashCode
            class Record {
                @Owner Object parent
                @Generated boolean equals(Object other) { true }
                @Generated int hashCode() { 37 }
            }
        '''
        def unit = compile(source)
        createClass(source)

        when:
        def record = clazz.Create.One()

        then:
        !warnings(unit)
        record.equals(new Object())
        record.hashCode() == 37
    }

    def "Groovy hash caching receives only its existing model-mutation error"() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Record.groovy', '''
            import groovy.transform.EqualsAndHashCode
            @EqualsAndHashCode(cache=true) @DSL
            class Record { String name }
        ''')

        when:
        unit.compile()

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Assigning a value to a field of a model')
        !warnings(unit)
    }

    def "subclass equality delegates to superclass without selecting inherited owner state"() {
        when:
        def unit = compile('''
            import groovy.transform.EqualsAndHashCode
            @DSL class ParentRecord { @Owner Object parent }
            @DSL @EqualsAndHashCode(callSuper=true)
            class ChildRecord extends ParentRecord { String name }
        ''')

        then:
        !warnings(unit)
    }

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#equals-method")
    def "chooses equality state explicitly while ignoring ownership and transient metadata"() {
        given:
        def source = '''
            import groovy.transform.EqualsAndHashCode
            @EqualsAndHashCode(excludes = ['parent', 'metadata']) @DSL
            class Document {
                String name
                @Owner Object parent
                @Field(FieldType.TRANSIENT) String metadata
            }
        '''
        def unit = compile(source)
        createClass(source)

        when:
        def first = clazz.Create.With(name: 'guide', setParent: new Object(), setMetadata: 'first')
        def second = clazz.Create.With(name: 'guide', setParent: new Object(), setMetadata: 'second')

        then:
        !warnings(unit)
        first == second
        first.hashCode() == second.hashCode()
    }

    private CompilationUnit compile(String source) {
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Record.groovy', source)
        unit.compile()
        unit
    }

    private static List<String> warnings(CompilationUnit unit) {
        (unit.errorCollector.warnings ?: [])*.message.findAll { it.startsWith('Custom @EqualsAndHashCode') }
    }
}
