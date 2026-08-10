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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.io.DataInputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader

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
        error.message.contains('active-session Create.AsBuilder')
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

    @Issue('710')
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

    @Issue('710')
    def "Template handler retains deprecated creation aliases that forward to the Factory property"() {
        given:
        Class<?> foo = getClass('sample.Foo')
        Class<?> template = getClass('sample.Foo_DSL$Template')

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

        then: 'scope application stays on the Template handler while every legacy creator is explicitly deprecated'
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

    @Issue('710')
    def "Java and statically compiled Groovy consume only the public namespace"() {
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

                public static Foo template() {
                    return Foo.Create.Template.With(Map.of("label", "java template"));
                }

                public static Foo templateFrom(File source) {
                    return Foo.Create.Template.From(source);
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

                static Foo template() {
                    Foo.Create.Template.With {
                        label 'template'
                    }
                }

                static Foo templateFrom(File source) {
                    Foo.Create.Template.From(source)
                }
            }
        ''', 'sample/StaticDslConsumer.groovy')
        File templateSource = new File(tempFolder.root, 'template.groovy')
        templateSource.text = 'label "file template"'

        def javaTemplate
        def javaTemplateFrom
        new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader).withCloseable {
            Class<?> javaConsumer = it.loadClass('sample.JavaDslConsumer')
            javaTemplate = javaConsumer.template()
            javaTemplateFrom = javaConsumer.templateFrom(templateSource)
        }
        def staticTemplate = consumer.template()
        def staticTemplateFrom = consumer.templateFrom(templateSource)

        then:
        consumer.create().kids*.name == ['list child']
        consumer.create().primary.name == 'cluster child'
        [javaTemplate, javaTemplateFrom, staticTemplate, staticTemplateFrom]*.label ==
                ['java template', 'file template', 'template', 'file template']
        [javaTemplate, javaTemplateFrom, staticTemplate, staticTemplateFrom].every { TemplateManager.isTemplate(it) }
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
        mirror.contains('void copyFrom(Builder<Foo> source)')
        mirror.contains('The generated DSL support namespace for sample.Foo.')
        mirror.contains('Creates a new')
        getClass('sample.Foo_DSL$Builder').getAnnotation(AnnoDoc).value().contains('public Builder contract')
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
