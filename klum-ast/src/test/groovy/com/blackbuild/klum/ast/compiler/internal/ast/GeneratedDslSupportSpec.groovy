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
import com.blackbuild.klum.ast.runtime.KlumFactory
import com.blackbuild.klum.ast.runtime.KlumFactory.BuilderFactoryProvider
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.generated.GeneratedMaterializationToken
import com.blackbuild.klum.ast.runtime.generated.GeneratedBreadcrumbs
import com.blackbuild.klum.ast.runtime.generated.GeneratedClusters
import com.blackbuild.klum.ast.runtime.generated.GeneratedModelSupport
import com.blackbuild.klum.ast.runtime.generated.GeneratedOmittedProjectionSupport
import com.blackbuild.klum.ast.runtime.generated.GeneratedObjectState
import com.blackbuild.klum.ast.runtime.internal.process.BreadcrumbCollector
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import groovy.lang.DelegatesTo
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.ast.ClassHelper
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.io.DataInputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.stream.Stream

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

    @Issue('729')
    def "Factory exposes only the explicit typed AsBuilder() operation"() {
        given:
        Class<?> factory = getClass('sample.Foo_DSL$Factory')

        expect:
        factory.getMethod('AsBuilder').genericReturnType.typeName ==
                'com.blackbuild.klum.ast.runtime.KlumFactory$UnkeyedBuilderFactory<sample.Foo, sample.Foo_DSL$Builder<sample.Foo>>'
        !factory.methods*.name.contains('getAsBuilder')
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

    @Issue('391')
    def "generated model state links only opaque generated-runtime contracts"() {
        given:
        Class<?> root = getClass('sample.Base')
        Class<?> model = getClass('sample.Foo')
        Class<?> child = getClass('sample.Child')
        Class<?> rootBuilder = getClass('sample.Foo$Builder')

        expect: 'the accepted 4.0 layout keeps state private and does not retain the historical descriptor'
        root.getDeclaredField('$state').type == GeneratedObjectState
        !root.declaredFields*.name.contains('$proxy')
        root.declaredConstructors*.parameterTypes.any { parameters ->
            parameters.contains(GeneratedMaterializationToken)
        }

        and: 'model constructors and generated implementations use the reviewed model-state bridge, never its internal delegates'
        [root, model, child, rootBuilder].every { type ->
            classFileConstants(type).every { constant ->
                !constant.contains('com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder') &&
                        !constant.contains('com/blackbuild/klum/ast/runtime/internal/KlumObjectCompanion')
            }
        }
        classFileConstants(root).any { it.contains('com/blackbuild/klum/ast/runtime/generated/GeneratedObjectState') }
        classFileConstants(model).any { it.contains('com/blackbuild/klum/ast/runtime/generated/GeneratedMaterializationToken') }
        [root, child].every { type ->
            classFileConstants(type).contains('com/blackbuild/klum/ast/runtime/generated/GeneratedModelSupport')
        }
    }

    @Issue('391')
    def "generated breadcrumb owners use only the reviewed bridge and preserve nested diagnostics"() {
        given:
        List<String> paths = []

        when:
        def foo = getClass('sample.Foo').Create.With {
            kids {
                kid {
                    paths << BreadcrumbCollector.instance.fullPath
                    name 'list child'
                }
            }
            services {
                primary {
                    paths << BreadcrumbCollector.instance.fullPath
                    name 'cluster child'
                }
            }
        }

        then: 'factory/Builder registration and collection/Cluster scopes no longer name runtime internals'
        def generatedArtifacts = [
                getClass('sample.Foo$_Factory'),
                getClass('sample.Foo$Builder'),
                getClass('sample.Foo$_kids'),
                getClass('sample.Foo$_services')
        ]
        generatedArtifacts.every { type ->
            def owners = classFileOwners(type)
            owners.contains('com/blackbuild/klum/ast/runtime/generated/GeneratedBreadcrumbs') &&
                    owners.every { owner ->
                        !owner.contains('com/blackbuild/klum/ast/runtime/internal/BreadCrumbVerbInterceptor') &&
                                !owner.contains('com/blackbuild/klum/ast/runtime/internal/process/BreadcrumbCollector')
                    }
        }

        and: 'the bridge retains nested collection and Cluster breadcrumb scopes'
        paths == ['$/s.Foo.With/kids/kid', '$/s.Foo.With/services/primary']
        foo.kids*.name == ['list child']
        foo.primary.name == 'cluster child'
    }

    @Issue('391')
    def "generated Cluster accessors use only the reviewed bridge and preserve query results"() {
        given:
        Class<?> fooType = getClass('sample.Foo')

        when:
        def foo = fooType.Create.With {
            primary { name 'primary' }
            secondary { name 'secondary' }
        }

        then: 'the generated model class names the generated bridge, never the internal query helper'
        def owners = classFileOwners(fooType)
        owners.contains(GeneratedClusters.name.replace('.', '/'))
        !owners.contains('com/blackbuild/klum/ast/runtime/internal/layer3/ClusterModel')

        and: 'the Layer 3 accessor keeps its established non-null property projection'
        foo.services == [primary: foo.primary, secondary: foo.secondary]
    }

    @Issue('391')
    def "generated omitted projection fallback uses only the reviewed bridge and preserves its diagnostic"() {
        given:
        Class<?> fooType = getClass('sample.Foo')
        Class<?> builderType = getClass('sample.Foo$Builder')

        when:
        fooType.Create.With { opaqueChild 'nested' }

        then: 'the emitted methodMissing owner is the generated bridge, never the internal diagnostic helper'
        def error = thrown(KlumModelException)
        def owners = classFileOwners(builderType)
        owners.contains(GeneratedOmittedProjectionSupport.name.replace('.', '/'))
        !owners.contains('com/blackbuild/klum/ast/runtime/internal/OmittedProjectionSupport')

        and: 'the unsupported-projection diagnostic remains unchanged'
        error.message.contains('omitted Builder-producing projection opaqueChild(java.lang.String)')
        error.message.contains('active-session Create.AsBuilder()')
    }

    @Issue('391')
    def "generated omitted projection bridge supplies an unmatched fallback"() {
        when:
        def error = invokeOmittedProjectionBridge(this)

        then:
        error instanceof MissingMethodException
        error.method == 'unprojected'
        error.type == getClass()
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

    @Issue(['391', '737'])
    def "Template uses the model-package TemplateScope contract and generated runtime bridge without internal leakage"() {
        given:
        Class<?> base = getClass('sample.Base')
        Class<?> foo = getClass('sample.Foo')
        Class<?> template = getClass('sample.Foo_DSL$TemplateScope')
        Class<?> adapter = getClass('sample.Foo$_Template')

        expect: 'the public model descriptor names only the model-package TemplateScope contract'
        foo.getField('Template').type == template
        !new File(compilerConfiguration.targetDirectory, 'sample/Foo_DSL$Template.class').isFile()
        template.interface && Modifier.isPublic(template.modifiers)
        template.isAssignableFrom(adapter)
        Modifier.isPublic(adapter.modifiers)
        Modifier.isPublic(adapter.getDeclaredConstructor().modifiers)

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

    @Issue(['710', '737'])
    def "Factory owns the public Template creation property without exposing a Factory method"() {
        given:
        Class<?> foo = getClass('sample.Foo')
        Class<?> factory = getClass('sample.Foo_DSL$Factory')
        Class<?> templateFactory = getClass('sample.Foo_DSL$Factory$Template')
        Class<?> adapter = getClass('sample.Foo$_TemplateFactory')

        expect: 'the model and public Factory descriptors name the generated nested Factory contract'
        foo.getField('Create').type == factory
        factory.getField('Template').with {
            type == templateFactory
            Modifier.isPublic(modifiers)
            Modifier.isStatic(modifiers)
            Modifier.isFinal(modifiers)
        }
        templateFactory.interface && Modifier.isPublic(templateFactory.modifiers)
        templateFactory.isAssignableFrom(adapter)

        and: 'Template root creation is separate from inherited Factory methods'
        !factory.methods*.name.contains('Template')
        !factory.methods*.name.contains('TemplateFrom')
        !factory.methods*.name.contains('getTemplate')
        templateFactory.getMethod('With')
        closureDelegate(templateFactory.getMethod('With', Closure)) == getClass('sample.Foo_DSL$Builder')
        closureDelegate(templateFactory.getMethod('With', Map, Closure)) == getClass('sample.Foo_DSL$Builder')
        templateFactory.getMethod('From', File)
        templateFactory.getMethod('From', File, ClassLoader)
        templateFactory.getMethod('From', URL)
        templateFactory.getMethod('From', URL, ClassLoader)

        and: 'public Factory descriptors and their adapter name only the reviewed generated-runtime bridge'
        [factory, templateFactory, adapter].every { type ->
            classFileConstants(type).every { !it.contains('com/blackbuild/klum/ast/runtime/internal') }
        }
        classFileConstants(adapter).contains('com/blackbuild/klum/ast/runtime/generated/GeneratedTemplateFactorySupport')

        when:
        def template = foo.Create.Template.With {
            label 'template'
        }

        then:
        template.label == 'template'
        TemplateManager.isTemplate(template)
    }

    @Issue(['710', '737'])
    def "TemplateScope retains deprecated creation aliases that forward to the Factory property"() {
        given:
        Class<?> foo = getClass('sample.Foo')
        Class<?> template = getClass('sample.Foo_DSL$TemplateScope')

        when: 'the documented compatibility spelling is used'
        def creationAliases = [
                template.getMethod('Create'),
                template.getMethod('Create', Map, Closure),
                template.getMethod('Create', Closure),
                template.getMethod('Create', Map),
                template.getMethod('CreateFrom', File),
                template.getMethod('CreateFrom', File, ClassLoader),
                template.getMethod('CreateFrom', URL),
                template.getMethod('CreateFrom', URL, ClassLoader)
        ]
        def legacyTemplate = foo.Template.Create(label: 'legacy')

        then: 'scope application stays on TemplateScope while every legacy creator is explicitly deprecated'
        template.getMethod('With', getClass('sample.Base'), Closure)
        template.getMethod('WithAll', Map, Closure)
        template.getMethod('WithAll', List, Closure)
        template.getAnnotation(AnnoDoc).value().contains('scoped Template application contract')
        creationAliases.every { it.getAnnotation(Deprecated)?.since() == '4.0' }
        template.getMethod('Create', Map, Closure).getAnnotation(AnnoDoc).value().contains(
                '@deprecated Use {@code Foo.Create.Template.With(configMap, configuration)} instead.')
        template.getMethod('CreateFrom', URL, ClassLoader).getAnnotation(AnnoDoc).value().contains(
                '@deprecated Use {@code Foo.Create.Template.From(scriptUrl, loader)} instead.')

        and: 'it still creates the same marked root Template as the canonical Factory property'
        legacyTemplate.label == 'legacy'
        TemplateManager.isTemplate(legacyTemplate)
    }

    @Issue(['710', '737'])
    def "Java and statically compiled Groovy consume distinct public Template contracts"() {
        when:
        compileJavaConsumer('''
            package sample;

            import groovy.lang.Closure;
            import java.io.File;
            import java.util.Map;

            public final class JavaDslConsumer {
                public static Foo_DSL.Factory factory() {
                    return Foo.Create;
                }

                public static Foo_DSL.Factory.Template templateFactory() {
                    return Foo.Create.Template;
                }

                public static Foo_DSL.TemplateScope templateScope() {
                    return Foo.Template;
                }

                public static Foo template() {
                    return Foo.Create.Template.With(Map.of("label", "java template"));
                }

                public static Foo templateFrom(File source) {
                    return Foo.Create.Template.From(source);
                }

                public static Foo scopedTemplate(Foo template) {
                    return Foo.Template.With(template, new Closure<Foo>(null) {
                        public Foo doCall() {
                            return Foo.Create.With(Map.of("label", "scoped"));
                        }
                    });
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
                    return Foo.Create.AsBuilder().With(Map.of("label", "inherited"));
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

                static Foo template() {
                    Foo.Create.Template.With {
                        label 'template'
                    }
                }

                static Foo templateFrom(File source) {
                    Foo.Create.Template.From(source)
                }

                static Child scopedTemplate(Child template) {
                    Child_DSL.TemplateScope scope = Child.Template
                    scope.With(template) {
                        Child.Create.With { name 'scoped' }
                    }
                }
            }
        ''', 'sample/StaticDslConsumer.groovy')
        File templateSource = new File(tempFolder.root, 'template.groovy')
        templateSource.text = 'label "file template"'

        def javaTemplate
        def javaTemplateFrom
        def javaScopedTemplate
        new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader).withCloseable {
            Class<?> javaConsumer = it.loadClass('sample.JavaDslConsumer')
            javaTemplate = javaConsumer.template()
            javaTemplateFrom = javaConsumer.templateFrom(templateSource)
            javaScopedTemplate = javaConsumer.scopedTemplate(javaTemplate)
        }
        def staticTemplate = consumer.template()
        def staticTemplateFrom = consumer.templateFrom(templateSource)
        def staticScopedTemplate = consumer.scopedTemplate(Child.Create.Template.With { name 'template child' })

        then:
        consumer.create().kids*.name == ['list child']
        consumer.create().primary.name == 'cluster child'
        [javaTemplate, javaTemplateFrom, staticTemplate, staticTemplateFrom]*.label ==
                ['java template', 'file template', 'template', 'file template']
        [javaTemplate, javaTemplateFrom, staticTemplate, staticTemplateFrom].every { TemplateManager.isTemplate(it) }
        javaScopedTemplate.label == 'scoped'
        staticScopedTemplate.name == 'scoped'
    }

    @Issue(['710', '737'])
    def "statically compiled Groovy rejects the removed Factory Template methods"() {
        when:
        createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class RemovedFactoryTemplateConsumer {
                static Foo createTemplate() {
                    Foo.Create.Template { label 'obsolete Factory method' }
                }
            }
        ''', 'sample/RemovedFactoryTemplateConsumer.groovy')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('Cannot find matching method sample.Foo_DSL$Factory#Template')
    }

    @Issue('729')
    def "AsBuilder() projects the concrete public Builder return and closure delegate types"() {
        when:
        compileJavaConsumer('''
            package sample;

            import com.blackbuild.klum.ast.runtime.KlumBuilder;
            import com.blackbuild.klum.ast.runtime.KlumFactory;
            import java.util.Map;

            public final class JavaDslConsumer {
                public static Child_DSL.Builder<Child> childBuilder() {
                    return Child.Create.AsBuilder().With(Map.of("name", "java child"));
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
                        Child_DSL.Builder<Child> child = Child.Create.AsBuilder().With {
                            name 'groovy child'
                        }
                    }
                }
            }
        ''', 'sample/StaticAsBuilderConsumer.groovy')

        then:
        noExceptionThrown()
    }

    @Issue('729')
    def "AsBuilder() keeps a defaulted abstract keyed model's public contract coherent"() {
        given: 'the declared abstract model selects an implementation at runtime'
        createSecondaryClass('''
            package defaulted

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL(defaultImpl = Impl)
            abstract class Base {
                @Key String name
            }

            @DSL
            class Impl extends Base {
            }

            @DSL
            class Concrete {
                @Key String name
            }

            @DSL
            abstract class AbstractKeyed {
                @Key String name
            }
        ''', 'defaulted/DefaultedKeyedSchema.groovy')
        Class<?> baseFactory = getClass('defaulted.Base_DSL$Factory')
        Class<?> concreteFactory = getClass('defaulted.Concrete_DSL$Factory')
        Class<?> abstractFactory = getClass('defaulted.AbstractKeyed_DSL$Factory')

        when: 'the public source mirror is generated from the same bytecode contract'
        File mirrorRoot = new File(tempFolder.root, 'defaulted-keyed-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'defaulted/Base_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        File mirror = new File(mirrorRoot, 'defaulted/Base_DSL.java')

        and: 'a Java client names the provider return advertised by the declared model'
        compileJavaConsumer('''
            package defaulted;

            import com.blackbuild.klum.ast.runtime.KlumFactory.BuilderFactory;

            public final class JavaDslConsumer {
                public static BuilderFactory<Base, Base_DSL.Builder<Base>> factory() {
                    return Base.Create.AsBuilder();
                }
            }
        ''', 'defaulted/JavaDslConsumer.java')

        then: 'the bytecode signature agrees with the provider contract and Java source mirror'
        baseFactory.genericInterfaces*.typeName == [
                'com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider<defaulted.Base, defaulted.Base_DSL$Builder<defaulted.Base>>'
        ]
        baseFactory.getMethod('AsBuilder').genericReturnType.typeName ==
                'com.blackbuild.klum.ast.runtime.KlumFactory$KeyedBuilderFactory<defaulted.Base, defaulted.Base_DSL$Builder<defaulted.Base>>'
        mirror.text.contains('KeyedBuilderFactory<Base, Builder<Base>> AsBuilder()')
        compileJavaSource(mirror)

        and: 'defaultImpl still selects the runtime factory implementation'
        getClass('defaulted.Base').getField('Create').get(null).modelType == getClass('defaulted.Impl')

        and: 'valid keyed specialization and abstract-without-default behavior remain unchanged'
        concreteFactory.getMethod('AsBuilder').genericReturnType.typeName ==
                'com.blackbuild.klum.ast.runtime.KlumFactory$KeyedBuilderFactory<defaulted.Concrete, defaulted.Concrete_DSL$Builder<defaulted.Concrete>>'
        abstractFactory.getMethod('AsBuilder').genericReturnType.typeName ==
                'com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactory<defaulted.AbstractKeyed, defaulted.AbstractKeyed_DSL$Builder<defaulted.AbstractKeyed>>'
    }

    @Issue('644')
    def "public Builder contracts expose typed same-model copy sources"() {
        given:
        Class<?> fooBuilder = getClass('sample.Foo_DSL$Builder')

        expect:
        fooBuilder.getMethod('copyFrom', fooBuilder).with {
            parameterTypes == [fooBuilder]
            getAnnotation(AnnoDoc).value().contains('active Builder of the same model')
        }

        when: 'Java consumes the generated public Builder type without an implementation leak'
        compileJavaConsumer('''
            package sample;

            public final class JavaDslConsumer {
                public static void copyFromSameModel(
                        Foo_DSL.Builder<Foo> target,
                        Foo_DSL.Builder<Foo> source) {
                    target.copyFrom(source);
                }
            }
        ''')

        then:
        noExceptionThrown()
    }

    @Issue('620')
    def "typed relationship factories select the public Builder delegate without a root lifecycle"() {
        given:
        Class<?> factory = getClass('sample.HttpEndpoint_DSL$Factory')
        Class<?> deploymentBuilder = getClass('sample.Deployment_DSL$Builder')

        expect: 'generated factories expose one generic active-session provider contract'
        factory.typeParameters.length == 0
        factory.genericInterfaces*.typeName == [
                'com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider<sample.HttpEndpoint, sample.HttpEndpoint_DSL$Builder<sample.HttpEndpoint>>'
        ]
        getClass('sample.HttpEndpoint').getField('Create').type == factory

        and: 'the relationship method carries source-valid type variables and exact delegate metadata'
        Method endpointMethod = deploymentBuilder.declaredMethods.find {
            it.name == 'endpoint' && it.parameterTypes.toList() == [BuilderFactoryProvider, Closure]
        }
        endpointMethod.typeParameters*.name == ['T', 'B']
        endpointMethod.typeParameters[0].bounds*.typeName == ['sample.Endpoint']
        endpointMethod.typeParameters[1].bounds*.typeName ==
                ['com.blackbuild.klum.ast.runtime.KlumBuilder<T>']
        endpointMethod.genericReturnType.typeName == 'B'
        endpointMethod.genericParameterTypes[0].typeName ==
                'com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider<T, B>'
        endpointMethod.parameters[1].getAnnotation(DelegatesTo).with {
            target() == 'factory' && genericTypeIndex() == 1 && strategy() == Closure.DELEGATE_ONLY
        }
        endpointMethod.getAnnotation(AnnoDoc).value().with {
            contains('dynamic Class overload') && contains('exact selected public Builder') &&
                    contains('current Construction session') && contains('never starts a root lifecycle')
        }
        Method dynamicEndpointMethod = deploymentBuilder.getMethod('endpoint', Class, Closure)
        dynamicEndpointMethod.getAnnotation(AnnoDoc).value().with {
            contains('Class value') && contains('declared base public Builder') &&
                    contains('generated Create Factory') && contains('without starting a root lifecycle')
        }

        when: 'Java sees the exact selected Builder return type'
        compileJavaConsumer('''
            package sample;

            import groovy.lang.Closure;

            public final class JavaDslConsumer {
                public static HttpEndpoint_DSL.Builder<HttpEndpoint> endpoint(
                        Deployment_DSL.Builder<Deployment> deployment, Closure<?> configuration) {
                    return deployment.endpoint(HttpEndpoint.Create, configuration);
                }
            }
        ''')

        and: 'static Groovy configures every polymorphic relationship shape through Create'
        Class<?> consumer = createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticTypedRelationshipConsumer {
                static Deployment createSingle() {
                    Deployment.Create.With {
                        endpoint(HttpEndpoint.Create) {
                            url 'https://direct.example.test'
                        }
                    }
                }

                static Deployment createCollection() {
                    Deployment.Create.With {
                        routes {
                            route(HttpEndpoint.Create, url: 'https://list.example.test')
                        }
                    }
                }

                static Deployment createMap() {
                    Deployment.Create.With {
                        keyedEndpoints {
                            keyedEndpoint(NamedHttpEndpoint.Create, 'named', url: 'https://map.example.test')
                        }
                    }
                }
            }
        ''', 'sample/StaticTypedRelationshipConsumer.groovy')

        and: 'the established dynamic Class selector remains available'
        Class<?> dynamicConsumer = createSecondaryClass('''
            package sample

            class DynamicTypedRelationshipConsumer {
                static Deployment create() {
                    Deployment.Create.With {
                        endpoint(HttpEndpoint) {
                            url 'https://dynamic.example.test'
                        }
                    }
                }
            }
        ''', 'sample/DynamicTypedRelationshipConsumer.groovy')

        then:
        def single = consumer.createSingle()
        getClass('sample.HttpEndpoint').isInstance(single.endpoint)
        single.endpoint.url == 'https://direct.example.test'

        and:
        def collection = consumer.createCollection()
        collection.routes*.url == ['https://list.example.test']

        and:
        def keyed = consumer.createMap()
        keyed.keyedEndpoints.keySet() == ['named'] as Set
        keyed.keyedEndpoints.named.url == 'https://map.example.test'

        and:
        def dynamic = dynamicConsumer.create()
        getClass('sample.HttpEndpoint').isInstance(dynamic.endpoint)
        dynamic.endpoint.url == 'https://dynamic.example.test'
        !dynamicEndpointMethod.isAnnotationPresent(Deprecated)
    }

    @Issue(['719', '728'])
    def "public Builder contracts and source mirrors declare relationship creators without their optional closure"() {
        given:
        Class<?> fooBuilder = getClass('sample.Foo_DSL$Builder')
        Class<?> childBuilder = getClass('sample.Child_DSL$Builder')
        Class<?> collectionFactory = getClass('sample.Foo_DSL$Builder$CollectionFactory_kids')
        Class<?> clusterFactory = getClass('sample.Foo_DSL$Builder$ClusterFactory_services')
        Class<?> deploymentBuilder = getClass('sample.Deployment_DSL$Builder')

        when: 'the generated public contracts are inspected'
        Method direct = fooBuilder.getMethod('primary', Map)
        Method collection = collectionFactory.getMethod('kid', Map)
        Method cluster = clusterFactory.getMethod('primary', Map)
        Method dynamicWithoutClosure = deploymentBuilder.getMethod('endpoint', Map, Class)
        Method dynamicWithClosure = deploymentBuilder.getMethod('endpoint', Map, Class, Closure)
        Method factoryWithoutClosure = deploymentBuilder.getMethod('endpoint', Map, BuilderFactoryProvider)
        Method factoryWithClosure = deploymentBuilder.getMethod('endpoint', Map, BuilderFactoryProvider, Closure)
        Method deprecatedWithoutClosure = fooBuilder.getMethod('secondary', Map)
        Method deprecatedWithClosure = fooBuilder.getMethod('secondary', Map, Closure)
        List<Method> keyedNoClosureMethods = deploymentBuilder.declaredMethods.findAll {
            it.name == 'keyedEndpoint' && !it.parameterTypes.contains(Closure)
        }

        then: 'direct, collection, Cluster, keyed, dynamic-Class, and typed-Factory relationship creators omit only the optional closure'
        direct.returnType == childBuilder
        collection.returnType == childBuilder
        cluster.returnType == childBuilder
        dynamicWithoutClosure.returnType == dynamicWithClosure.returnType
        factoryWithoutClosure.genericReturnType.typeName == 'B'
        factoryWithoutClosure.typeParameters*.bounds*.typeName == factoryWithClosure.typeParameters*.bounds*.typeName
        factoryWithoutClosure.getAnnotation(AnnoDoc).value().contains('@param factory')
        !factoryWithoutClosure.getAnnotation(AnnoDoc).value().contains('@param closure')
        factoryWithClosure.getAnnotation(AnnoDoc).value().contains('@param closure')
        factoryWithoutClosure.parameters[1].isAnnotationPresent(DelegatesTo.Target)
        Modifier.isPublic(factoryWithoutClosure.modifiers)
        !factoryWithoutClosure.isAnnotationPresent(Deprecated)
        deprecatedWithoutClosure.isAnnotationPresent(Deprecated)
        deprecatedWithClosure.isAnnotationPresent(Deprecated)
        deprecatedWithoutClosure.getAnnotation(AnnoDoc).value().contains('@deprecated')
        !deprecatedWithoutClosure.getAnnotation(AnnoDoc).value().contains('@param closure')
        deprecatedWithClosure.getAnnotation(AnnoDoc).value().contains('@param closure')
        keyedNoClosureMethods.any { it.parameterTypes.contains(String) }
        keyedNoClosureMethods.any { it.parameterTypes.contains(Class) }
        keyedNoClosureMethods.any { it.parameterTypes.contains(BuilderFactoryProvider) && it.genericReturnType.typeName == 'B' }

        and: 'the closure-taking Factory form retains its exact generic delegate metadata'
        factoryWithClosure.typeParameters*.name == ['T', 'B']
        factoryWithClosure.parameters.last().getAnnotation(DelegatesTo).with {
            target() == 'factory' && genericTypeIndex() == 1 && strategy() == Closure.DELEGATE_ONLY
        }

        when: 'a statically compiled extension only knows the public Builder interface'
        Class<?> consumer = createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticBuilderWithoutClosureConsumer {
                static void configure(Foo_DSL.Builder<Foo> target) {
                    target.primary([name: 'from public Builder'])
                }

                static Foo create() {
                    Foo.Create.With {
                        StaticBuilderWithoutClosureConsumer.configure((Foo_DSL.Builder<Foo>) delegate)
                    }
                }

                static Foo createWithEmptyClosure() {
                    Foo.Create.With {
                        ((Foo_DSL.Builder<Foo>) delegate).primary([name: 'from public Builder']) {}
                    }
                }
            }
        ''', 'sample/StaticBuilderWithoutClosureConsumer.groovy')

        and: 'AnnoDocimal projects the same explicit public contract'
        File mirrorRoot = new File(tempFolder.root, 'issue-719-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'sample/Foo_DSL.class')
        File deploymentNamespaceClass = new File(compilerConfiguration.targetDirectory, 'sample/Deployment_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(deploymentNamespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'sample/Foo_DSL.java').text
        String deploymentMirror = new File(mirrorRoot, 'sample/Deployment_DSL.java').text

        then: 'the runtime behavior remains equivalent to the closure-taking creation form'
        consumer.create().primary.name == 'from public Builder'
        consumer.createWithEmptyClosure().primary.name == 'from public Builder'

        and: 'the mirrors list the shorter direct, collection, map, keyed, dynamic-Class, and typed-Factory creator overloads'
        mirror.contains('Child_DSL.Builder<Child> primary(Map<String, ?> values)')
        mirror.contains('Child_DSL.Builder<Child> kid(Map<String, ?> values)')
        mirror.readLines().any { it.contains(' primary(Map<String, ?> values)') && !it.contains('Closure') }
        (deploymentMirror =~ /(?s)Endpoint_DSL\.Builder<Endpoint> endpoint\(Map<String, \?> values,\s+@DelegatesTo\.Target Class<\? extends Endpoint> typeToCreate\);/).find()
        (deploymentMirror =~ /(?s)B endpoint\(Map<String, \?> values,\s+@DelegatesTo\.Target\("factory"\) KlumFactory\.BuilderFactoryProvider<T, B> factory\);/).find()
        (deploymentMirror =~ /(?s)KeyedEndpoint_DSL\.Builder<KeyedEndpoint> keyedEndpoint\(Map<String, \?> values,\s+@DelegatesTo\.Target Class<\? extends KeyedEndpoint> typeToCreate,?\s+String key\);/).find()
        (deploymentMirror =~ /(?s)B keyedEndpoint\(Map<String, \?> values,\s+@DelegatesTo\.Target\("factory"\) KlumFactory\.BuilderFactoryProvider<T, B> factory,*\s+String key\);/).find()
    }

    @Issue(['729', '737'])
    def "AnnoDocimal source mirror matches the distinct TemplateScope and Template Factory contracts"() {
        given:
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'sample/Foo_DSL.class')

        when:
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'sample/Foo_DSL.java').text

        then:
        int factoryStart = mirror.indexOf('interface Factory')
        int factoryTemplateStart = mirror.indexOf('interface Template {')
        int scopedTemplateStart = mirror.indexOf('interface TemplateScope {')
        String factoryMirror = mirror.substring(factoryStart, scopedTemplateStart)
        String templateFactoryMirror = mirror.substring(factoryTemplateStart, scopedTemplateStart)
        String scopedTemplateMirror = mirror.substring(scopedTemplateStart)

        mirror.contains('interface Foo_DSL')
        mirror.contains('interface Factory')
        factoryMirror.contains('AsBuilder()')
        !factoryMirror.contains('getAsBuilder')
        mirror.count('interface Template {') == 1
        mirror.count('interface TemplateScope {') == 1
        factoryMirror.contains('Template Template = null;')
        templateFactoryMirror.contains('Foo With(')
        templateFactoryMirror.contains('Foo From(')
        !templateFactoryMirror.contains('Foo Template(')
        !templateFactoryMirror.contains('Foo TemplateFrom(')
        scopedTemplateMirror.contains('<C> C With(')
        scopedTemplateMirror.contains('<C> C WithAll(')
        scopedTemplateMirror.contains('Foo Create(')
        scopedTemplateMirror.contains('Foo CreateFrom(')
        mirror.contains('interface Builder')
        mirror.contains('interface CollectionFactory_kids')
        mirror.contains('interface ClusterFactory_services')
        mirror.contains('void copyFrom(Builder<Foo> source)')
        mirror.contains('The generated DSL support namespace for sample.Foo.')
        mirror.contains('Creates a new')
        getClass('sample.Foo_DSL$Builder').getAnnotation(AnnoDoc).value().contains('public Builder contract')
    }

    @Issue('736')
    def "generated public methods and their source mirror retain meaningful AnnoDoc"() {
        given: 'a neutral product schema exercises direct and simple collection operations'
        createSecondaryClass('''
            package documentation

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL class Product {
                Child primaryChild
                List<Child> children
                Map<String, Child> childrenByName
                List<String> labels
                Map<String, String> metadata
            }

            @DSL class Child {
                @Key String name
            }
        ''', 'documentation/Product.groovy')
        Class<?> product = getClass('documentation.Product')
        Class<?> builder = getClass('documentation.Product_DSL$Builder')
        Class<?> factoryTemplate = getClass('documentation.Product_DSL$Factory$Template')
        Class<?> scopedTemplate = getClass('documentation.Product_DSL$Template')

        when: 'representative bytecode contract methods are inspected'
        List<Method> directRelationshipCreators = [
                builder.getMethod('primaryChild', Map, String, Closure),
                builder.getMethod('primaryChild', Map, String),
                builder.getMethod('children', Map, String, Closure),
                builder.getMethod('children', Map, String),
                builder.getMethod('childrenByName', Map, String, Closure),
                builder.getMethod('childrenByName', Map, String)
        ]
        List<Method> templateCreation = [
                factoryTemplate.getMethod('With', Map, Closure),
                factoryTemplate.getMethod('From', File, ClassLoader)
        ]
        List<Method> scopedApplication = [
                scopedTemplate.getMethod('With', product, Closure),
                scopedTemplate.getMethod('WithAll', Map, Closure),
                scopedTemplate.getMethod('WithAll', List, Closure)
        ]
        List<Method> applyLater = builder.declaredMethods.findAll { it.name == 'applyLater' }
        List<Method> simpleMapAdders = [
                builder.getMethod('metadata', Map),
                builder.getMethod('metadata', String, String)
        ]
        List<Method> simpleCollectionAdders = [
                builder.getMethod('label', String)
        ]
        List<Method> documentedMethods = directRelationshipCreators + templateCreation + scopedApplication + applyLater + simpleCollectionAdders + simpleMapAdders

        then: 'every supported generated public method carries an informative contract with parameter documentation'
        List<Method> insufficientDocumentation = (documentedMethods - simpleCollectionAdders).findAll { Method method ->
            AnnoDoc documentation = method.getAnnotation(AnnoDoc)
            documentation == null || !documentation.value().contains('@param') || documentation.value().size() <= 80
        }
        assert insufficientDocumentation.empty
        directRelationshipCreators.every { it.getAnnotation(AnnoDoc).value().contains('Creates a new') }
        templateCreation.every { it.getAnnotation(AnnoDoc).value().contains('Template') }
        scopedApplication.every { it.getAnnotation(AnnoDoc).value().contains('Template') }
        applyLater.size() == 3
        applyLater.every { it.getAnnotation(AnnoDoc).value().contains('Schedules') }
        applyLater.find { it.parameterCount == 2 }.getAnnotation(AnnoDoc).value().contains('@param phase')
        simpleCollectionAdders.every { it.getAnnotation(AnnoDoc).value().with {
            contains('Adds') && contains('@param value') && contains('@return the added value')
        } }
        simpleMapAdders.every { it.getAnnotation(AnnoDoc).value().contains('Adds') }

        and: 'AnnoDocimal exposes the same public documentation in the IDE-only source mirror'
        File mirrorRoot = new File(tempFolder.root, 'issue-736-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'documentation/Product_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'documentation/Product_DSL.java').text

        documentedMethods.collect { it.getAnnotation(AnnoDoc).value().split('\\n\\n').first() }.toSet().every { mirror.contains(it) }
        mirror.contains('@param value The value to add')
        mirror.contains('@return the added value')
    }

    @Issue('702')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Builder-First-Migration.md#public-builder-contracts')
    def "projects an unresolved cross-source owner Builder into the public namespace"() {
        given: 'Child is transformed before the Root source is resolved'
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Child.groovy', '''
            package crosssource

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Owner

            @DSL class Child {
                @Owner(root = true) Root root
            }
        ''')
        unit.addSource('Root.groovy', '''
            package crosssource

            import com.blackbuild.klum.ast.DSL

            @DSL class Root {
            }
        ''')

        when:
        unit.compile()
        def crossSourceLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader)
        Class<?> childBuilder = crossSourceLoader.loadClass('crosssource.Child_DSL$Builder')
        Class<?> rootBuilder = crossSourceLoader.loadClass('crosssource.Root_DSL$Builder')

        and: 'the AnnoDocimal mirror is projected from the generated public namespace'
        File mirrorRoot = new File(tempFolder.root, 'cross-source-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'crosssource/Child_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'crosssource/Child_DSL.java').text

        then: 'the public descriptors retain the Root model identity instead of leaking Root$Builder'
        childBuilder.getMethod('getRoot').returnType == rootBuilder
        childBuilder.getMethod('setRoot', rootBuilder)
        childBuilder.declaredMethods.findAll { it.name in ['getRoot', 'setRoot'] }.every { method ->
            !method.toGenericString().contains('Root$Builder')
        }

        and: 'the IDE-only source mirror names the same public Builder contract'
        mirror.contains('Root_DSL.Builder<Root>')
        !mirror.contains('Root.Builder')
    }

    @Issue('728')
    def "projects same-source forward relationship Builders into the public namespace"() {
        given: 'Parent is transformed before its relationship target is resolved from the same Schema source'
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('SameSourceSchema.groovy', '''
            package samesource

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL class Parent {
                Child primaryChild
                List<Child> children
                Map<String, Child> childrenByName
            }

            @DSL class Child {
                @Key String name
            }
        ''')

        when:
        unit.compile()
        def sameSourceLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader)
        Class<?> parentBuilder = sameSourceLoader.loadClass('samesource.Parent_DSL$Builder')
        Class<?> childBuilder = sameSourceLoader.loadClass('samesource.Child_DSL$Builder')
        Class<?> collectionFactory = sameSourceLoader.loadClass('samesource.Parent_DSL$Builder$CollectionFactory_children')

        and: 'a static Groovy extension relies on the generated public contracts only'
        Class<?> consumer = new GroovyClassLoader(sameSourceLoader, compilerConfiguration).parseClass('''
            package samesource

            import groovy.transform.CompileStatic

            @CompileStatic
            class SameSourcePublicBuilderConsumer {
                static Child_DSL.Builder<Child> primary(Parent_DSL.Builder<Parent> parent) {
                    parent.primaryChild
                }
            }
        ''', 'samesource/SameSourcePublicBuilderConsumer.groovy')

        and: 'AnnoDocimal mirrors the generated namespace rather than an implementation descriptor'
        File mirrorRoot = new File(tempFolder.root, 'same-source-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'samesource/Parent_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'samesource/Parent_DSL.java').text

        then: 'accessors and every relationship-creator path use the public Child Builder contract'
        parentBuilder.getMethod('getPrimaryChild').returnType == childBuilder
        parentBuilder.getMethod('setPrimaryChild', childBuilder)
        parentBuilder.getMethod('getChildren').genericReturnType.typeName ==
                'java.util.List<samesource.Child_DSL$Builder<samesource.Child>>'
        parentBuilder.getMethod('setChildren', List).genericParameterTypes[0].typeName ==
                'java.util.List<samesource.Child_DSL$Builder<samesource.Child>>'
        parentBuilder.getMethod('getChildrenByName').genericReturnType.typeName ==
                'java.util.Map<java.lang.String, samesource.Child_DSL$Builder<samesource.Child>>'
        parentBuilder.getMethod('setChildrenByName', Map).genericParameterTypes[0].typeName ==
                'java.util.Map<java.lang.String, samesource.Child_DSL$Builder<samesource.Child>>'
        closureDelegate(parentBuilder.declaredMethods.find {
            it.name == 'primaryChild' && it.returnType == childBuilder &&
                    !it.parameterTypes.toList().contains(Class) && !it.parameterTypes.toList().contains(BuilderFactoryProvider) &&
                    it.parameterTypes.last() == Closure
        }) == childBuilder
        closureDelegate(collectionFactory.declaredMethods.find {
            it.returnType == childBuilder && !it.parameterTypes.toList().contains(Class) &&
                    !it.parameterTypes.toList().contains(BuilderFactoryProvider) && it.parameterTypes.last() == Closure
        }) == childBuilder
        closureDelegate(parentBuilder.declaredMethods.find {
            it.name == 'childrenByName' && it.returnType == childBuilder &&
                    !it.parameterTypes.toList().contains(Class) && !it.parameterTypes.toList().contains(BuilderFactoryProvider) &&
                    it.parameterTypes.last() == Closure
        }) == childBuilder
        parentBuilder.declaredMethods.findAll { it.name in ['primaryChild', 'childrenByName'] }.every { method ->
            !method.toGenericString().contains('Child$Builder')
        }
        collectionFactory.declaredMethods.findAll { it.name == 'child' }.every { method ->
            !method.toGenericString().contains('Child$Builder')
        }

        and: 'dynamic Class and typed Factory creator paths retain their public generic contract for every relationship shape'
        List<Method> dynamicCreators = [
                parentBuilder.declaredMethods.find { it.name == 'primaryChild' && it.parameterTypes.toList().contains(Class) && it.parameterTypes.last() == Closure },
                collectionFactory.declaredMethods.find { it.name == 'children' && it.parameterTypes.toList().contains(Class) && it.parameterTypes.last() == Closure },
                parentBuilder.declaredMethods.find { it.name == 'childrenByName' && it.parameterTypes.toList().contains(Class) && it.parameterTypes.last() == Closure }
        ]
        List<Method> factoryCreators = [
                parentBuilder.declaredMethods.find { it.name == 'primaryChild' && it.parameterTypes.toList().contains(BuilderFactoryProvider) && it.parameterTypes.last() == Closure },
                collectionFactory.declaredMethods.find { it.name == 'children' && it.parameterTypes.toList().contains(BuilderFactoryProvider) && it.parameterTypes.last() == Closure },
                parentBuilder.declaredMethods.find { it.name == 'childrenByName' && it.parameterTypes.toList().contains(BuilderFactoryProvider) && it.parameterTypes.last() == Closure }
        ]
        dynamicCreators*.returnType == [childBuilder, childBuilder, childBuilder]
        dynamicCreators.every { creator ->
            creator.genericParameterTypes.find { it.typeName.startsWith('java.lang.Class') }.typeName ==
                    'java.lang.Class<? extends samesource.Child>'
            creator.parameters.last().getAnnotation(DelegatesTo).with {
                genericTypeIndex() == 0 && strategy() == Closure.DELEGATE_ONLY
            }
        }
        factoryCreators.every { creator ->
            creator.genericReturnType.typeName == 'B'
            creator.genericParameterTypes.find { it.typeName.contains('BuilderFactoryProvider') }.typeName ==
                    'com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider<T, B>'
            creator.parameters.last().getAnnotation(DelegatesTo).with {
                target() == 'factory' && genericTypeIndex() == 1 && strategy() == Closure.DELEGATE_ONLY
            }
        }

        and: 'the source mirror is as truthful as bytecode and the public static consumer compiles'
        mirror.contains('Child_DSL.Builder<Child> getPrimaryChild()')
        mirror.contains('void setPrimaryChild(Child_DSL.Builder<Child>')
        mirror.contains('List<Child_DSL.Builder<Child>> getChildren()')
        mirror.contains('void setChildren(List<Child_DSL.Builder<Child>>')
        mirror.contains('Map<String, Child_DSL.Builder<Child>> getChildrenByName()')
        mirror.contains('void setChildrenByName(Map<String, Child_DSL.Builder<Child>>')
        mirror.contains('Child_DSL.Builder<Child> primaryChild(')
        mirror.contains('Child_DSL.Builder<Child> child(')
        mirror.contains('Child_DSL.Builder<Child> childrenByName(')
        !mirror.contains('Child.Builder')
        !mirror.contains('Child$Builder')
        consumer.getMethod('primary', parentBuilder).returnType == childBuilder
    }

    @Issue('728')
    def "does not project an ordinary Java nested Builder type"() {
        expect:
        GeneratedDslSupport.publicType(ClassHelper.make(Stream.Builder)).name == Stream.Builder.name
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
                @Deprecated Child secondary
                OpaqueChild opaqueChild
                @Cluster Map<String, Child> services
            }

            @DSL class Deployment {
                Endpoint endpoint
                List<Endpoint> routes
                Map<String, KeyedEndpoint> keyedEndpoints
            }

            @DSL abstract class Endpoint {
            }

            @DSL class HttpEndpoint extends Endpoint {
                String url
            }

            @DSL abstract class KeyedEndpoint {
                @Key String name
            }

            @DSL class NamedHttpEndpoint extends KeyedEndpoint {
                String url
            }

            @DSL class OpaqueChild {
                static OpaqueChild fromString(String value) {
                    return materialize(value)
                }

                private static OpaqueChild materialize(String value) {
                    return OpaqueChild.Create.With()
                }
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

    @CompileStatic
    private static RuntimeException invokeOmittedProjectionBridge(Object receiver) {
        GeneratedOmittedProjectionSupport.$klum$handle(receiver, 'unprojected', null, '')
    }

    private Set<String> classFileConstants(Class<?> type) {
        new LinkedHashSet<>(classFileEntries(type).utf8Constants.values())
    }

    private Set<String> classFileOwners(Class<?> type) {
        def entries = classFileEntries(type)
        entries.classNameIndexes.collect { entries.utf8Constants[it] }.findAll { it != null } as Set<String>
    }

    private Map classFileEntries(Class<?> type) {
        File classFile = new File(compilerConfiguration.targetDirectory, type.name.replace('.', '/') + '.class')
        assert classFile.isFile()
        classFile.withInputStream { input ->
            DataInputStream data = new DataInputStream(input)
            assert data.readInt() == (int) 0xCAFEBABE
            data.readUnsignedShort()
            data.readUnsignedShort()
            int constantPoolCount = data.readUnsignedShort()
            Map<Integer, String> utf8Constants = [:]
            List<Integer> classNameIndexes = []
            for (int index = 1; index < constantPoolCount; index++) {
                switch (data.readUnsignedByte()) {
                    case 1:
                        utf8Constants[index] = data.readUTF()
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
                        classNameIndexes << data.readUnsignedShort()
                        break
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
            [utf8Constants: utf8Constants, classNameIndexes: classNameIndexes]
        }
    }

    private void compileJavaConsumer(@Language('JAVA') String source, String filename = 'sample/JavaDslConsumer.java') {
        File sourceFile = new File(tempFolder.root, filename)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        compileJavaSource(sourceFile)
    }

    private void compileJavaSource(File sourceFile) {
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
