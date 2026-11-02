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

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumModelException
import javax.tools.ToolProvider
import com.blackbuild.klum.ast.AbstractDSLSpec
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('953')
class StructuredFactoryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class service
    private Class deployment

    def setup() {
        createClass ''' 
            package structuredfactory
            import com.blackbuild.klum.ast.*
            @DSL class Identity {
                @Key @Struct.Name('service-id') String name
            }
            @DSL class Service extends Identity {
                static List<String> events = []
                String host
                @PostCreate void created() { events << 'create:' + name }
                @PostApply void applied() { events << 'apply:' + name }
                @PostTree void tree() { events << 'tree:' + name }
                @Validate void validateModel() { events << 'validate:' + name }
            }
            @DSL class Deployment {
                Service primary
                Map<String, Service> services
            }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        service = artifactLoader.loadClass('structuredfactory.Service')
        deployment = artifactLoader.loadClass('structuredfactory.Deployment')
    }

    def cleanup() { artifactLoader?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#factory-roots')
    def 'Factory documents carry their own root identity'() {
        when:
        def root = service.Create.FromStruct(['service-id': 'catalog', host: 'api'], 'request')
        def graph = deployment.Create.FromStruct([primary: ['service-id': 'primary', host: 'api'],
                                                  services: [worker: [host: 'jobs']]])

        then:
        root.class == service
        root.name == 'catalog'
        root.host == 'api'
        graph.class == deployment
        graph.primary.name == 'primary'
        graph.services.worker.name == 'worker'
        service.events.take(4) == ['create:catalog', 'apply:catalog', 'tree:catalog', 'validate:catalog']
    }
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#factory-roots')
    def 'explicit root keys configure one ordinary lifecycle'() {
        when:
        def root = service.Create.FromStruct('catalog', [host: 'api'], 'https://invalid.example/not-opened')
        def second = service.Create.FromStruct('worker', [host: 'jobs'])

        then:
        root.class == service
        root.name == 'catalog'
        root.host == 'api'
        second.name == 'worker'
        service.events == ['create:catalog', 'apply:catalog', 'tree:catalog', 'validate:catalog',
                           'create:worker', 'apply:worker', 'tree:worker', 'validate:worker']
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#active-builders')
    def 'active Builders apply structured bodies without completing construction'() {
        given:
        def captured
        def events = service.events

        when:
        def root = service.Create.With('catalog') {
            captured = delegate
            applyStruct([host: 'first'])
            assert events == ['create:catalog', 'apply:catalog']
            applyStruct([host: 'api'], 'request')
            assert events == ['create:catalog', 'apply:catalog', 'apply:catalog']
        }

        then:
        root.host == 'api'
        captured.completedModel.is(root)
        service.events == ['create:catalog', 'apply:catalog', 'apply:catalog', 'apply:catalog',
                           'tree:catalog', 'validate:catalog']
        !root.class.methods*.name.contains('applyStruct')
    }
    def 'Java and static Groovy consumers use exact Factory and inherited void Builder contracts'() {
        given:
        def source = new File(tempFolder.root, 'structuredfactory/JavaConsumer.java')
        source.parentFile.mkdirs()
        source.text = '''
            package structuredfactory;
            import java.util.Map;
            import com.blackbuild.klum.ast.runtime.KlumBuilder;
            public class JavaConsumer {
                public static Service document(Map<?, ?> document) { return Service.Create.FromStruct(document); }
                public static Service document(Map<?, ?> document, String source) { return Service.Create.FromStruct(document, source); }
                public static Service body(String key, Map<?, ?> body) { return Service.Create.FromStruct(key, body); }
                public static Service body(String key, Map<?, ?> body, String source) { return Service.Create.FromStruct(key, body, source); }
                public static Deployment unkeyed(Map<?, ?> document) { return Deployment.Create.FromStruct(document); }
                public static Deployment unkeyed(Map<?, ?> document, String source) { return Deployment.Create.FromStruct(document, source); }
                public static <B extends KlumBuilder<?>> B configure(B target, Map<?, ?> body) {
                    target.applyStruct(body);
                    target.applyStruct(body, "request");
                    return target;
                }
                public static void inherited(Service_DSL.Builder<Service> target, Map<?, ?> body) {
                    target.applyStruct(body);
                    target.applyStruct(body, "request");
                }
            }
        '''
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath].join(File.pathSeparator)

        when:
        int compilation = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath, source.absolutePath)
        createSecondaryClass source.text.replace('public class JavaConsumer', '@groovy.transform.CompileStatic class StaticConsumer')
        def consumers = ['JavaConsumer', 'StaticConsumer'].collect { artifactLoader.loadClass('structuredfactory.' + it) }
        def factory = artifactLoader.loadClass('structuredfactory.Service_DSL$Factory')
        def builder = artifactLoader.loadClass('structuredfactory.Service_DSL$Builder')
        def mirrors = tempFolder.newFolder('mirrors')
        def namespace = new File(compilerConfiguration.targetDirectory, 'structuredfactory/Service_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespace.toPath(), mirrors.toPath())
        def mirror = new File(mirrors, 'structuredfactory/Service_DSL.java')
        def mirrorClasses = tempFolder.newFolder('mirror-classes')
        int mirrorCompilation = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', mirrorClasses.absolutePath, mirror.absolutePath)

        then:
        compilation == 0
        mirrorCompilation == 0
        factory.getMethod('FromStruct', Map).returnType == service
        factory.getMethod('FromStruct', Map, String).returnType == service
        factory.getMethod('FromStruct', String, Map).returnType == service
        factory.getMethod('FromStruct', String, Map, String).returnType == service
        factory.getMethod('FromStruct', Map).genericParameterTypes[0].typeName == 'java.util.Map<?, ?>'
        factory.getMethod('FromStruct', String, Map).genericParameterTypes[1].typeName == 'java.util.Map<?, ?>'
        mirror.text.contains('Service FromStruct(Map<?, ?> document)')
        mirror.text.contains('Service FromStruct(String rootKey, Map<?, ?> body, String sourceName)')
        builder.getMethod('applyStruct', Map).declaringClass == KlumBuilder
        builder.getMethod('applyStruct', Map, String).declaringClass == KlumBuilder
        builder.getMethod('applyStruct', Map).returnType == void
        builder.getMethod('applyStruct', Map, String).returnType == void
        builder.getMethod('applyStruct', Map).genericParameterTypes[0].typeName == 'java.util.Map<?, ?>'
        !builder.declaredMethods*.name.contains('applyStruct')
        consumers.each { consumer ->
            assert consumer.document(['service-id': 'document']).name == 'document'
            assert consumer.document(['service-id': 'named'], 'request').name == 'named'
            assert consumer.body('body', [host: 'api']).name == 'body'
            assert consumer.body('named-body', [host: 'api'], 'request').name == 'named-body'
            assert consumer.unkeyed([:]).class == deployment
            assert consumer.unkeyed([:], 'request').class == deployment
            def model = service.Create.With('active') {
                assert consumer.configure(delegate, [host: 'api']).is(delegate)
                consumer.inherited(delegate, [host: 'final'])
            }
            assert model.host == 'final'
        }
    }

    def 'keyed Factory bodies forbid an inner root key even if equal: #inner'() {
        when:
        service.Create.FromStruct('catalog', ['service-id': inner], 'request')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('request')
        failure.message.contains('service-id')
        service.events == ['create:catalog']

        where:
        inner << ['catalog', 'different', null]
    }

    def 'default diagnostics identify the capability while explicit labels remain opaque'() {
        when:
        deployment.Create.FromStruct([unknown: true])

        then:
        def rootFailure = thrown(KlumModelException)
        rootFailure.message.contains('structuredfactory.Deployment.Create.FromStruct')

        when:
        service.Create.FromStruct('catalog', [unknown: true])

        then:
        def bodyFailure = thrown(KlumModelException)
        bodyFailure.message.contains('structuredfactory.Service.Create.FromStruct')

        when:
        service.Create.With('catalog') { applyStruct([unknown: true]) }

        then:
        def applyFailure = thrown(KlumModelException)
        applyFailure.message.contains('structuredfactory.Service.Builder.applyStruct')

        when:
        service.Create.FromStruct(['service-id': 'actual', unknown: true], '../raw/../file.yaml?key=other#')

        then:
        def opaque = thrown(KlumModelException)
        opaque.message.contains('../raw/../file.yaml?key=other#')
        opaque.message.contains('/unknown')
        service.events.last() == 'create:actual'
    }

    def 'every new named entry rejects a missing diagnostic label: #source'() {
        when:
        deployment.Create.FromStruct([:], source)

        then:
        thrown(KlumModelException)

        when:
        service.Create.FromStruct('catalog', [:], source)

        then:
        thrown(KlumModelException)

        when:
        service.Create.With('catalog') { applyStruct([:], source) }

        then:
        thrown(KlumModelException)

        where:
        source << [null, '']
    }

    def 'root creation does not project into AsBuilder or relationship factories and apply is not input'() {
        given:
        def unkeyedFactory = artifactLoader.loadClass('structuredfactory.Deployment_DSL$Factory')

        expect:
        unkeyedFactory.methods.findAll { it.name == 'FromStruct' }*.parameterTypes*.toList().toSet() == [[Map], [Map, String]] as Set
        !service.Create.AsBuilder().class.methods*.name.contains('FromStruct')
        !deployment.Create.AsBuilder().class.methods*.name.contains('FromStruct')
        artifactLoader.loadClass('structuredfactory.Deployment_DSL$Builder').declaredClasses.every {
            !it.methods*.name.contains('FromStruct')
        }

        when:
        service.Create.FromStruct('catalog', [applyStruct: [:]], 'request')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('applyStruct')
    }

    def 'completed models and the removed reader facade expose no structured application'() {
        when:
        artifactLoader.loadClass('com.blackbuild.klum.ast.runtime.KlumStructuredInput')

        then:
        thrown(ClassNotFoundException)

        when:
        service.Create.FromStruct(['service-id': 'catalog']).applyStruct([host: 'late'])

        then:
        thrown(MissingMethodException)
    }

}
