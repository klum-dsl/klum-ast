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

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.KlumGenerated
import com.blackbuild.klum.ast.runtime.KlumBuilder
import groovy.lang.DelegatesTo
import org.intellij.lang.annotations.Language
import spock.lang.Issue

import javax.tools.ToolProvider
import java.io.DataInputStream
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
        builder.isAssignableFrom(getClass('sample.Foo$Builder'))
        collectionFactory.isAssignableFrom(getClass('sample.Foo$_kids'))
        clusterFactory.isAssignableFrom(getClass('sample.Foo$_services'))
        generatedLink(getClass('sample.Foo$Builder')) == builder.name
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

    @Issue('391')
    def "preserves inherited Builder self-model typing"() {
        given:
        Class<?> baseBuilder = getClass('sample.Base_DSL$Builder')
        Class<?> fooBuilder = getClass('sample.Foo_DSL$Builder')
        Class<?> baseImplementation = getClass('sample.Base$Builder')
        Class<?> fooImplementation = getClass('sample.Foo$Builder')

        expect:
        baseBuilder.typeParameters*.name == ['SELF']
        baseBuilder.typeParameters[0].bounds*.typeName == ['sample.Base']
        baseBuilder.genericInterfaces*.typeName.contains('com.blackbuild.klum.ast.runtime.KlumBuilder<SELF>')
        fooBuilder.typeParameters*.name == ['SELF']
        fooBuilder.typeParameters[0].bounds*.typeName == ['sample.Foo']
        fooBuilder.genericInterfaces*.typeName.contains('sample.Base_DSL$Builder<SELF>')
        baseBuilder.getMethod('label', String).genericParameterTypes*.typeName == ['java.lang.String']
        fooBuilder.getMethod('label', String).declaringClass == baseBuilder

        and: 'hidden implementations thread the same leaf model type without a second capability'
        baseImplementation.typeParameters*.name == ['SELF']
        baseImplementation.genericSuperclass.typeName == 'com.blackbuild.klum.ast.runtime.generated.GeneratedKlumBuilder<SELF>'
        fooImplementation.typeParameters*.name == ['SELF']
        fooImplementation.typeParameters[0].bounds*.typeName == ['sample.Foo']
        fooImplementation.genericSuperclass.typeName == 'sample.Base$Builder<SELF>'
        !fooImplementation.genericInterfaces*.typeName.any { it.startsWith('com.blackbuild.klum.ast.runtime.KlumBuilder') }

        and: 'the hidden Builder implementation links only the reviewed generated-runtime bridge'
        [baseImplementation, fooImplementation].every { implementation ->
            classFileConstants(implementation).every { !it.contains('com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder') }
        }
        classFileConstants(baseImplementation).contains('com/blackbuild/klum/ast/runtime/generated/GeneratedKlumBuilder')
    }

    def "public Builder contracts expose the zero-operation KlumBuilder capability"() {
        given:
        Class<?> builder = getClass('sample.Child_DSL$Builder')

        expect:
        KlumBuilder.isAssignableFrom(builder)
        KlumBuilder.declaredMethods.length == 0
        builder.typeParameters*.name == ['SELF']
        builder.genericInterfaces*.typeName.contains('com.blackbuild.klum.ast.runtime.KlumBuilder<SELF>')
    }

    @Issue('391')
    def "Template uses the model-package contract and generated runtime bridge without internal leakage"() {
        given:
        Class<?> base = getClass('sample.Base')
        Class<?> foo = getClass('sample.Foo')
        Class<?> template = getClass('sample.Foo_DSL$Template')
        Class<?> adapter = getClass('sample.Foo$_Template')

        expect: 'the public model descriptor names only the model-package Template contract'
        foo.getField('Template').type == template
        template.interface && Modifier.isPublic(template.modifiers)
        template.isAssignableFrom(adapter)
        !Modifier.isPublic(adapter.modifiers)

        and: 'the full Template capability remains present and only configuration closures expose Builder typing'
        template.getMethod('With', base, Closure).genericReturnType.typeName == 'C'
        template.getMethod('WithAll', Map, Closure).genericReturnType.typeName == 'C'
        template.getMethod('WithAll', List, Closure).genericReturnType.typeName == 'C'
        template.getMethod('Create')
        closureDelegate(template.getMethod('Create', Closure)) == getClass('sample.Foo_DSL$Builder')
        closureDelegate(template.getMethod('Create', Map, Closure)) == getClass('sample.Foo_DSL$Builder')
        template.getMethod('CreateFrom', File)
        template.getMethod('CreateFrom', File, ClassLoader)
        template.getMethod('CreateFrom', URL)
        template.getMethod('CreateFrom', URL, ClassLoader)

        and: 'the generated Template artifacts link only the reviewed public bridge, never runtime internals'
        [template, adapter].every { classFileConstants(it).every { !it.contains('com/blackbuild/klum/ast/runtime/internal') } }
        classFileConstants(adapter).contains('com/blackbuild/klum/ast/runtime/generated/GeneratedTemplateSupport')
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

                public static Child_DSL.Builder<Child> addChild(
                        Foo_DSL.Builder<Foo> owner,
                        Foo_DSL.Builder.CollectionFactory_kids kids) {
                    owner.kids((Closure<?>) null);
                    return kids.kid(Map.of(), (Closure<?>) null);
                }

                public static Child_DSL.Builder<Child> addPrimary(
                        Foo_DSL.Builder.ClusterFactory_services services) {
                    return services.primary(Map.of(), (Closure<?>) null);
                }

                public static Foo_DSL.Builder<Foo> inheritedBuilder() {
                    return Foo.Create.getAsBuilder().With(Map.of("label", "inherited"));
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

    def "AsBuilder projects the concrete public Builder return and closure delegate types"() {
        when:
        compileJavaConsumer('''
            package sample;

            import com.blackbuild.klum.ast.runtime.KlumBuilder;
            import com.blackbuild.klum.ast.runtime.KlumFactory;
            import java.util.Map;

            public final class JavaDslConsumer {
                public static Child_DSL.Builder<Child> childBuilder() {
                    return Child.Create.getAsBuilder().With(Map.of("name", "java child"));
                }

                public static <B extends KlumBuilder<Child>> B builderFrom(
                        KlumFactory.BuilderFactory<Child, B> factory) {
                    return factory.FromMap(Map.of("name", "generic child"));
                }
            }
        ''')

        createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticAsBuilderConsumer {
                static void compileAsBuilderClosure() {
                    Foo.Create.With {
                        Child_DSL.Builder<Child> child = Child.Create.AsBuilder.With {
                            name 'groovy child'
                        }
                    }
                }
            }
        ''', 'sample/StaticAsBuilderConsumer.groovy')

        then:
        noExceptionThrown()
    }

    def "AnnoDocimal source mirror matches the bytecode namespace and nested documentation"() {
        given:
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'sample/Foo_DSL.class')

        when:
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
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

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL abstract class Base {
                String label
            }

            @DSL class Child {
                String name
            }

            @DSL class Foo extends Base {
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

    private Set<String> classFileConstants(Class<?> type) {
        File classFile = new File(compilerConfiguration.targetDirectory, type.name.replace('.', '/') + '.class')
        assert classFile.isFile()
        classFile.withInputStream { input ->
            DataInputStream data = new DataInputStream(input)
            assert data.readInt() == (int) 0xCAFEBABE
            data.readUnsignedShort()
            data.readUnsignedShort()
            int constantPoolCount = data.readUnsignedShort()
            Set<String> utf8Constants = [] as Set
            for (int index = 1; index < constantPoolCount; index++) {
                switch (data.readUnsignedByte()) {
                    case 1:
                        utf8Constants << data.readUTF()
                        break
                    case 3:
                    case 4:
                        data.readInt()
                        break
                    case 5:
                    case 6:
                        data.readLong()
                        index++
                        break
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        data.readUnsignedShort()
                        break
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        data.readUnsignedShort()
                        data.readUnsignedShort()
                        break
                    case 15:
                        data.readUnsignedByte()
                        data.readUnsignedShort()
                        break
                    default:
                        assert false: "Unexpected class-file constant tag"
                }
            }
            utf8Constants
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
