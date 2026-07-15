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
package com.blackbuild.groovy.configdsl.transform.ast

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.generator.AnnoDocGenerator
import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.groovy.configdsl.transform.KlumGenerated
import groovy.lang.DelegatesTo
import org.intellij.lang.annotations.Language

import javax.tools.ToolProvider
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class GeneratedDslSupportSpec extends AbstractDSLSpec {

    def setup() {
        createRepresentativeSchema()
    }

    def "generates the complete public namespace and explicitly links hidden implementations"() {
        given:
        Class<?> foo = getClass('sample.Foo')
        Class<?> namespace = getClass('sample.Foo_DSL')
        Class<?> factory = getClass('sample.Foo_DSL$Factory')
        Class<?> builder = getClass('sample.Foo_DSL$Builder')
        Class<?> collectionFactory = getClass('sample.Foo_DSL$Builder$CollectionFactory_kids')
        Class<?> clusterFactory = getClass('sample.Foo_DSL$Builder$ClusterFactory_services')

        expect:
        namespace.interface && Modifier.isPublic(namespace.modifiers)
        factory.interface && builder.interface && collectionFactory.interface && clusterFactory.interface
        foo.getField('Create').type == factory

        and: 'implementation linkage is carried by generated metadata and JVM interfaces'
        factory.isAssignableFrom(getClass('sample.Foo$_Factory'))
        builder.isAssignableFrom(getClass('sample.Foo$_RW'))
        collectionFactory.isAssignableFrom(getClass('sample.Foo$_kids'))
        clusterFactory.isAssignableFrom(getClass('sample.Foo$_services'))
        generatedLink(getClass('sample.Foo$_RW')) == builder.name
        generatedLink(getClass('sample.Foo$_Factory')) == factory.name
    }

    def "public signatures traverse Builder collection and Cluster APIs without implementation types"() {
        given:
        Class<?> builder = getClass('sample.Foo_DSL$Builder')
        Class<?> childBuilder = getClass('sample.Child_DSL$Builder')
        Class<?> collectionFactory = getClass('sample.Foo_DSL$Builder$CollectionFactory_kids')
        Class<?> clusterFactory = getClass('sample.Foo_DSL$Builder$ClusterFactory_services')

        expect:
        closureDelegate(builder.getMethod('kids', Closure)) == collectionFactory
        closureDelegate(builder.getMethod('services', Closure)) == clusterFactory
        Method kid = collectionFactory.getMethod('kid', Map, Closure)
        Method primary = clusterFactory.getMethod('primary', Map, Closure)
        closureDelegate(kid) == childBuilder
        kid.returnType == childBuilder
        primary.returnType == childBuilder

        and: 'no public support signature leaks a generated implementation class'
        [builder, collectionFactory, clusterFactory].every { publicSignatures(it).every { !it.contains('\$_') } }
    }

    def "preserves inherited and generic Builder typing"() {
        given:
        Class<?> baseBuilder = getClass('sample.Base_DSL$Builder')
        Class<?> fooBuilder = getClass('sample.Foo_DSL$Builder')

        expect:
        baseBuilder.typeParameters*.name == ['T']
        fooBuilder.genericInterfaces*.typeName.contains('sample.Base_DSL$Builder<java.lang.String>')
        baseBuilder.getMethod('label', Object).genericParameterTypes*.typeName == ['T']
        fooBuilder.getMethod('label', Object).declaringClass == baseBuilder
    }

    def "Java and statically compiled Groovy consume only the public namespace"() {
        when:
        compileJavaConsumer('''
            package sample;

            import groovy.lang.Closure;
            import java.util.Map;

            public final class JavaDslConsumer {
                public static Foo_DSL.Factory factory() {
                    return Foo.Create;
                }

                public static Child_DSL.Builder addChild(
                        Foo_DSL.Builder owner,
                        Foo_DSL.Builder.CollectionFactory_kids kids) {
                    owner.kids((Closure<?>) null);
                    return kids.kid(Map.of(), (Closure<?>) null);
                }

                public static Child_DSL.Builder addPrimary(
                        Foo_DSL.Builder.ClusterFactory_services services) {
                    return services.primary(Map.of(), (Closure<?>) null);
                }
            }
        ''')

        Class<?> consumer = createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticDslConsumer {
                static Foo create() {
                    Foo.Create.With {
                        label 'root'
                        kids {
                            kid { name 'list child' }
                        }
                        services {
                            primary { name 'cluster child' }
                        }
                    }
                }
            }
        ''', 'sample/StaticDslConsumer.groovy')

        then:
        consumer.create().kids*.name == ['list child']
        consumer.create().primary.name == 'cluster child'
    }

    def "AnnoDocimal source mirror matches the bytecode namespace and nested documentation"() {
        given:
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'sample/Foo_DSL.class')

        when:
        AnnoDocGenerator.generate(namespaceClass, mirrorRoot)
        String mirror = new File(mirrorRoot, 'sample/Foo_DSL.java').text

        then:
        mirror.contains('interface Foo_DSL')
        mirror.contains('interface Factory')
        mirror.contains('interface Builder')
        mirror.contains('interface CollectionFactory_kids')
        mirror.contains('interface ClusterFactory_services')
        mirror.contains('The generated DSL support namespace for sample.Foo.')
        mirror.contains('Creates a new')
        getClass('sample.Foo_DSL$Builder').getAnnotation(AnnoDoc).value().contains('public Builder contract')
    }

    private void createRepresentativeSchema() {
        createClass '''
            package sample

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Base<T> {
                T label
            }

            @DSL class Child {
                String name
            }

            @DSL class Foo extends Base<String> {
                List<Child> kids
                Child primary
                Child secondary
                @Cluster Map<String, Child> services
            }
        '''
    }

    private static Class<?> closureDelegate(Method method) {
        method.parameters.last().getAnnotation(DelegatesTo).value()
    }

    private static String generatedLink(Class<?> implementation) {
        implementation.getAnnotation(KlumGenerated).tags().find { it.startsWith('dsl-support-interface:') }
                ?.substring('dsl-support-interface:'.length())
    }

    private static List<String> publicSignatures(Class<?> type) {
        type.methods.collect { Method method ->
            ([method.genericReturnType.typeName] + method.genericParameterTypes*.typeName).join(' ')
        }
    }

    private void compileJavaConsumer(@Language('JAVA') String source) {
        File sourceFile = new File(tempFolder.root, 'sample/JavaDslConsumer.java')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                null,
                '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath,
                sourceFile.absolutePath
        )
        assert result == 0
    }
}
