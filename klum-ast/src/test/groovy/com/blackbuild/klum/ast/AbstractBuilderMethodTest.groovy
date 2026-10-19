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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.Tag
import spock.lang.See
import javax.tools.ToolProvider
import java.lang.reflect.Modifier

@Issue('925')
class AbstractBuilderMethodTest extends AbstractDSLSpec {
    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Inheritance.md#abstract-builder-operations")
    def "abstract Builder operations dispatch through concrete subtype storage and implementations"() {
        when:
        createClass('''
            package abstractoperations
            @DSL abstract class Parent {
                abstract String getDisplayName()
                @Builder.Method abstract void setDisplayName(String value)
                @Builder.Method abstract String render(String prefix, int times)
                @Builder.Method void rename(String value) { setDisplayName(value) }
            }
            @DSL class Child extends Parent {
                @Default(code = { 'fallback' }) String displayName
                @Builder.Method String render(String prefix, int times) { prefix * times + displayName }
            }
        ''')
        Class staticConsumer = createSecondaryClass('''
            package abstractoperations
            import groovy.transform.CompileStatic
            @CompileStatic
            class StaticConsumer {
                static void configure(Parent_DSL.Builder<? extends Parent> builder) {
                    builder.rename('static')
                    assert builder.render('!', 2) == '!!static'
                }
            }
        ''')
        Class javaConsumer = compileJavaConsumer('''
            package abstractoperations;
            public class JavaConsumer {
                public static void configure(Parent_DSL.Builder<? extends Parent> builder) {
                    builder.rename("java");
                    if (!builder.render("!", 2).equals("!!java")) throw new AssertionError();
                }
            }
        ''')
        def staticallyConfigured = getClass('abstractoperations.Child').Create.With { staticConsumer.configure(delegate) }
        def javaConfigured = getClass('abstractoperations.Child').Create.With { javaConsumer.configure(delegate) }
        def configured = getClass('abstractoperations.Child').Create.With { rename('chosen'); assert render('!', 2) == '!!chosen' }
        then:
        configured.displayName == 'chosen'
        staticallyConfigured.displayName == 'static'
        javaConfigured.displayName == 'java'
        Modifier.isAbstract(getBuilderClass('abstractoperations.Parent').getDeclaredMethod('render', String, Integer.TYPE).modifiers)
        !Modifier.isAbstract(getBuilderClass('abstractoperations.Child').getDeclaredMethod('setDisplayName', String).modifiers)
        hasNoMethod(getClass('abstractoperations.Parent'), 'render', String, Integer.TYPE)
        hasNoMethod(getClass('abstractoperations.Child'), 'render', String, Integer.TYPE)
        hasNoMethod(getClass('abstractoperations.Parent$_TemplateModel'), 'render', String, Integer.TYPE)
        hasNoMethod(getClass('abstractoperations.Parent$_TemplateModel'), 'setDisplayName', String)
        getClass('abstractoperations.Child').Create.One().displayName == 'fallback'
    }

    def "abstract Builder projection covers signatures beyond setters: #signature"() {
        when:
        createClass("@DSL abstract class Parent { @Builder.Method abstract $signature }")
        then:
        Modifier.isAbstract(builderClass.modifiers)
        builderClass.declaredMethods.any { it.name == methodName && Modifier.isAbstract(it.modifiers) }
        getClass('Parent_DSL$Builder').declaredMethods.any { it.name == methodName }
        hasNoMethod(clazz, methodName, *(parameterTypes as Class[]))
        where:
        signature                                       | methodName | parameterTypes
        'void normalize()'                              | 'normalize' | []
        'String compute(String prefix, int count)'       | 'compute' | [String, Integer.TYPE]
        'int count()'                                   | 'count' | []
        'void accept(String... values)'                 | 'accept' | [String[].class]
        'List<String> values(Map<String, Integer> input)' | 'values' | [Map]
    }

    def "a concrete subtype still must realize the abstract Builder operation"() {
        when:
        createClass('''
            @DSL abstract class Parent { @Builder.Method abstract void normalize() }
            @DSL class Child extends Parent { }
        ''')
        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Child$Builder')
        error.message.contains('normalize')
    }

    def "static Schema and separately compiled subtype realize Builder operations"() {
        given:
        createClass('''
            package staticoperations
            import groovy.transform.CompileStatic
            @DSL @CompileStatic abstract class Parent {
                abstract String getDisplayName()
                @Builder.Method abstract void setDisplayName(String value)
                @Builder.Method abstract int count(int input)
                @Builder.Method void rename(String value) { setDisplayName(value) }
            }
        ''')
        when:
        createSecondaryClass('''
            package staticoperations
            import groovy.transform.CompileStatic
            @DSL @CompileStatic class Child extends Parent {
                String displayName
                @Builder.Method int count(int input) { input * 2 }
            }
        ''')
        def result = getClass('staticoperations.Child').Create.With { rename('static-schema'); assert count(3) == 6 }
        then:
        result.displayName == 'static-schema'
    }

    def "abstract Builder Input and Result keep their targeted diagnostics: #facet"() {
        when:
        createClass("""
            @DSL class Value { String name }
            @DSL abstract class Parent { $declaration }
        """)
        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('methods must declare an implementation')
        where:
        facet | declaration
        'Input' | '@Builder.Method abstract void accept(@Builder.Input Value value)'
        'Result' | '@Builder.Method @Builder.Result abstract Value produce()'
    }

    def "original abstract Builder setter reaches concrete subtype field and default"() {
        when:
        createClass('''
            @DSL abstract class Parent {
                abstract String getDisplayName()
                @Builder.Method abstract void setDisplayName(String displayName)
                @Builder.Method void rename(String value) { setDisplayName(value) }
            }
            @DSL class Child extends Parent {
                @Default(code = { 'fallback' }) String displayName
            }
        ''')
        def defaulted = Child.Create.One()
        def configured = Child.Create.With { rename('chosen') }
        then:
        defaulted.displayName == 'fallback'
        configured.displayName == 'chosen'
    }

    private Class compileJavaConsumer(String source) {
        File sourceFile = new File(tempFolder.root, 'abstractoperations/JavaConsumer.java')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath].join(File.pathSeparator)
        ByteArrayOutputStream errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(null, null, errors,
                '-classpath', classpath, '-d', compilerConfiguration.targetDirectory.absolutePath, sourceFile.absolutePath)
        assert result == 0: errors.toString()
        new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader).loadClass('abstractoperations.JavaConsumer')
    }

}
