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

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import groovy.lang.DelegatesTo
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets

@Issue('952')
class CollectionFactoryContractTest extends AbstractDSLSpec {

    def 'literal alternatives expose the complete public contract with #layout declarations'() {
        given:
        compileSchema(layout)

        when: 'fresh loaders inspect emitted classes without producer AST state'
        def contracts = [
                [field: 'elements', alternative: 'fixed', model: 'FixedElement'],
                [field: 'mappedElements', alternative: 'mapped', model: 'FixedElement'],
                [field: 'plainElements', alternative: 'plain', model: 'PlainElement']
        ]

        then:
        contracts.each { contract ->
            Class<?> factory = loader.loadClass("alternatives.Catalog_DSL\$Builder\$CollectionFactory_${contract.field}")
            Class<?> implementation = loader.loadClass("alternatives.Catalog\$_${contract.field}")
            Class<?> publicBuilder = loader.loadClass("alternatives.${contract.model}_DSL\$Builder")
            def methods = factory.methods.findAll { it.name == contract.alternative }
            def callableMethods = implementation.methods.findAll { it.name == contract.alternative }
            assert methods.size() == 4
            assert methods.collect { it.parameterTypes*.name } as Set == callableMethods.collect { it.parameterTypes*.name } as Set
            methods.each { method ->
                assert method.returnType == publicBuilder
                assert method.genericReturnType.typeName == "${publicBuilder.name}<alternatives.${contract.model}>"
                if (method.parameterCount && method.parameterTypes.last() == Closure) {
                    assert method.parameters.last().getAnnotation(DelegatesTo).value() == publicBuilder
                }
            }
        }

        and: 'public namespace class files contain no hidden Builder descriptors or delegate references'
        def namespaceFiles = new File(compilerConfiguration.targetDirectory, 'alternatives').listFiles()
                .findAll { it.name.startsWith('Catalog_DSL') && it.name.endsWith('.class') }
        !namespaceFiles.empty
        namespaceFiles.each { file ->
            String binary = new String(file.bytes, StandardCharsets.ISO_8859_1)
            ['Element', 'FixedElement', 'PlainBase', 'PlainElement'].each { model ->
                assert !binary.contains("alternatives/${model}\$Builder")
            }
        }

        where:
        layout << ['owner-first', 'subtype-first', 'separate-files']
    }

    def 'separately compiled Java and static Groovy consumers invoke literal alternatives'() {
        given:
        compileSchema('owner-first')
        File javaSource = scriptFile('alternatives/JavaClient.java', '''
            package alternatives;

            import java.util.Map;

            public final class JavaClient {
                public static void configure(Catalog_DSL.Builder.CollectionFactory_elements factory) {
                    FixedElement_DSL.Builder<FixedElement> child = factory.fixed("java");
                    child.value("typed-return");
                    factory.fixed(Map.of("value", "named-default"), "java-named");
                    factory.fixed("java-null-closure", null);
                    factory.fixed(Map.of("value", "full"), "java-full", null);
                }
            }
        ''')
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)

        when:
        int javaResult = ToolProvider.systemJavaCompiler.run(null, null, null,
                '-classpath', classpath, '-d', compilerConfiguration.targetDirectory.absolutePath, javaSource.path)
        Class<?> consumer = loader.parseClass('''
            package alternatives

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticClient {
                static Catalog create() {
                    Catalog.Create.With {
                        elements {
                            JavaClient.configure(delegate)
                            FixedElement_DSL.Builder<FixedElement> child = fixed('groovy')
                            child.value('typed-return')
                            fixed('groovy-closure') { value 'delegate' }
                            fixed(value: 'named-default', 'groovy-named')
                            fixed(value: 'full', 'groovy-full') { value 'overridden' }
                        }
                        mappedElements {
                            mapped('map-default')
                            mapped(value: 'map-named', 'map-named')
                            mapped('map-closure') { value 'map-delegate' }
                            mapped(value: 'map-full', 'map-full') { }
                        }
                        plainElements {
                            PlainElement_DSL.Builder<PlainElement> child = plain()
                            child.value('unkeyed-default')
                            plain(value: 'unkeyed-named')
                            plain { value 'unkeyed-delegate' }
                            plain(value: 'unkeyed-full') { }
                        }
                    }
                }
            }
        ''')
        def result = consumer.create()

        then:
        javaResult == 0
        result.elements*.name == ['java', 'java-named', 'java-null-closure', 'java-full',
                                 'groovy', 'groovy-closure', 'groovy-named', 'groovy-full']
        result.elements*.value == ['typed-return', 'named-default', null, 'full',
                                  'typed-return', 'delegate', 'named-default', 'overridden']
        result.mappedElements.collectEntries { key, value -> [key, value.value] } == [
                'map-default': null, 'map-named': 'map-named', 'map-closure': 'map-delegate', 'map-full': 'map-full']
        result.plainElements*.value == ['unkeyed-default', 'unkeyed-named', 'unkeyed-delegate', 'unkeyed-full']
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Alternatives-Syntax.md#public-collection-factory-contract')
    def 'dynamic DSL behavior and source mirrors retain the literal alternative surface'() {
        given:
        compileSchema('owner-first')
        Class<?> catalog = loader.loadClass('alternatives.Catalog')

        when:
        def result = catalog.Create.With {
            elements {
                fixed('default')
                fixed(value: 'named', 'named')
                fixed('closure') { value 'delegated' }
                fixed(value: 'initial', 'full') { value 'overridden' }
            }
        }
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        File namespace = new File(compilerConfiguration.targetDirectory, 'alternatives/Catalog_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespace.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'alternatives/Catalog_DSL.java').text

        then:
        result.elements*.name == ['default', 'named', 'closure', 'full']
        result.elements*.value == [null, 'named', 'delegated', 'overridden']
        mirror.contains('fixed(String key)')
        mirror.contains('FixedElement_DSL.Builder<FixedElement>')
        !mirror.contains('FixedElement.Builder')
    }

    private void compileSchema(String layout) {
        String owner = '''
            @DSL
            class Catalog {
                @Field(alternatives = { [fixed: FixedElement] })
                List<Element> elements
                @Field(alternatives = { [mapped: FixedElement] })
                Map<String, Element> mappedElements
                @Field(alternatives = { [plain: PlainElement] })
                List<PlainBase> plainElements
            }
        '''
        String targets = '''
            @DSL
            abstract class Element {
                @Key String name
            }
            @DSL
            class FixedElement extends Element {
                String value
            }
            @DSL
            abstract class PlainBase { }
            @DSL
            class PlainElement extends PlainBase {
                String value
            }
        '''
        CompilationUnit unit = new CompilationUnit(compilerConfiguration, null, loader)
        if (layout == 'separate-files') {
            unit.addSource('Catalog.groovy', 'package alternatives\n' + owner)
            unit.addSource('Elements.groovy', 'package alternatives\n' + targets)
        } else {
            unit.addSource('Schema.groovy', 'package alternatives\n' +
                    (layout == 'owner-first' ? owner + targets : targets + owner))
        }
        unit.compile()
        loader = new GroovyClassLoader(loader.parent, compilerConfiguration)
        loader.addClasspath(compilerConfiguration.targetDirectory.path)
    }
}
