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
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.time.Duration

@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#structured-input-templates')
class StructuredInputTemplateDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class deployment

    def setup() {
        createClass '''
            package structuredtemplate
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Deployment {
                static List<String> events = []
                @Key String name
                String region
                String identifier
                @Field(converters = [{ String value -> Deployment.events << 'converter'; Duration.parse(value) }])
                Duration timeout
                Service primary
                Map<String, Service> services
                @Builder.Method void locate(String value) {
                    events << 'configure'
                    region = value
                    applyLater { identifier name.toUpperCase() }
                }
                @Builder.Method void useDefaultRegion() { events << 'command'; region = 'default' }
                @Builder.Method void seed(String value) {
                    primary('api') { image value }
                    service('worker') { image value }
                }
                @PostCreate void created() { events << 'root:create' }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            @DSL class Service {
                @Key String name
                @Owner Deployment owner
                String image
                @PostCreate void created() { Deployment.events << 'child:create' }
                @PostApply void applied() { Deployment.events << 'child:apply' }
            }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        deployment = Class.forName('structuredtemplate.Deployment', false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    @Issue('953')
    def 'parsed keyless bodies define ordinary reusable Template recipes'() {
        given:
        def parsed = [locate: 'eu', primary: [name: 'api', image: 'api:1'],
                      services: [worker: [image: 'worker:1']]]

        when:
        def template = deployment.Create.Template.FromStruct(parsed, 'deployment.yaml')

        then:
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.primary)
        TemplateManager.isTemplate(template.services.worker)
        template.name == null
        template.identifier == null
        deployment.events == ['configure']

        when:
        def ordinary = deployment.Create.Template.With {
            locate 'eu'
            primary('api') { image 'api:1' }
            service('worker') { image 'worker:1' }
        }

        then:
        deployment.events == ['configure', 'configure']

        when:
        deployment.events.clear()
        def first
        def second
        def comparison
        deployment.Template.With(template) {
            first = deployment.Create.With('first')
            second = deployment.Create.With('second')
        }
        def structuredEvents = deployment.events.toList()
        deployment.events.clear()
        deployment.Template.With(ordinary) {
            comparison = deployment.Create.With('comparison')
        }

        then:
        !first.is(second)
        first.name == 'first'
        second.name == 'second'
        first.identifier == 'FIRST'
        second.identifier == 'SECOND'
        comparison.identifier == 'COMPARISON'
        [first.region, first.primary.name, first.primary.image, first.services.worker.name, first.services.worker.image] ==
                [comparison.region, comparison.primary.name, comparison.primary.image, comparison.services.worker.name, comparison.services.worker.image]
        structuredEvents == deployment.events + deployment.events
        first.primary.owner.is(first)
        first.services.worker.owner.is(first)
        second.primary.owner.is(second)
        !first.primary.is(second.primary)
        !first.services.worker.is(second.services.worker)
        !first.primary.is(template.primary)
    }

    @Issue('953')
    def 'reconfiguring existing Template children does not run lifecycle callbacks'() {
        when:
        def template = deployment.Create.Template.FromStruct(
                [seed: 'initial', primary: [name: 'api', image: 'updated'],
                 services: [worker: [image: 'updated']]], 'input')

        then:
        deployment.events.empty
        template.primary.image == 'updated'
        template.services.worker.image == 'updated'

        when:
        def recipient
        deployment.Template.With(template) { recipient = deployment.Create.With('target') }

        then:
        recipient.primary.image == 'updated'
        recipient.services.worker.image == 'updated'
        deployment.events.contains('child:create')
        deployment.events.contains('child:apply')
    }

    @Issue('952')
    def 'field converters execute during Template definition without lifecycle'() {
        when:
        def template = deployment.Create.Template.FromStruct([timeout: 'PT30S'], 'input')

        then:
        deployment.events == ['converter']
        template.timeout == Duration.ofSeconds(30)

        when:
        deployment.events.clear()
        def recipient
        deployment.Template.With(template) { recipient = deployment.Create.With('target') }

        then:
        recipient.timeout == template.timeout
        deployment.events == ['root:create', 'root:apply', 'root:tree']
    }

    @Issue('952')
    def 'zero-argument commands configure Templates without running lifecycle'() {
        when:
        def template = deployment.Create.Template.FromStruct([useDefaultRegion: [:]])

        then:
        deployment.events == ['command']
        template.region == 'default'

        when:
        deployment.events.clear()
        def recipient
        deployment.Template.With(template) { recipient = deployment.Create.With('target') }

        then:
        recipient.region == 'default'
        deployment.events == ['root:create', 'root:apply', 'root:tree']
    }

    @Issue('953')
    def 'Java and static Groovy callers use both generated Template Factory signatures'() {
        given:
        def source = new File(tempFolder.root, 'structuredtemplate/JavaTemplateConsumer.java')
        source.parentFile.mkdirs()
        source.text = '''
            package structuredtemplate;
            import java.util.Map;
            public class JavaTemplateConsumer {
                public static Deployment create(Map<?, ?> body) {
                    return Deployment.Create.Template.FromStruct(body);
                }
                public static Deployment create(Map<?, ?> body, String source) {
                    return Deployment.Create.Template.FromStruct(body, source);
                }
            }
        '''
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)

        when:
        int result = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath, source.absolutePath)
        createSecondaryClass '''
            package structuredtemplate
            import groovy.transform.CompileStatic
            @CompileStatic class StaticTemplateConsumer {
                static Deployment create(Map<?, ?> body) { Deployment.Create.Template.FromStruct(body) }
                static Deployment create(Map<?, ?> body, String source) { Deployment.Create.Template.FromStruct(body, source) }
            }
        '''
        def javaConsumer = artifactLoader.loadClass('structuredtemplate.JavaTemplateConsumer')
        def staticConsumer = artifactLoader.loadClass('structuredtemplate.StaticTemplateConsumer')
        def factory = artifactLoader.loadClass('structuredtemplate.Deployment_DSL$Factory$Template')
        def mirrorRoot = tempFolder.newFolder('mirrors')
        def namespace = new File(compilerConfiguration.targetDirectory, 'structuredtemplate/Deployment_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespace.toPath(), mirrorRoot.toPath())
        def mirror = new File(mirrorRoot, 'structuredtemplate/Deployment_DSL.java')
        def mirrorClasses = tempFolder.newFolder('mirror-classes')
        int mirrorResult = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', mirrorClasses.absolutePath, mirror.absolutePath)

        then:
        result == 0
        mirrorResult == 0
        mirror.text.contains('FromStruct(')
        factory.getMethod('FromStruct', Map).returnType == deployment
        factory.getMethod('FromStruct', Map).genericParameterTypes[0].typeName == 'java.util.Map<?, ?>'
        factory.getMethod('FromStruct', Map, String).returnType == deployment
        [javaConsumer, staticConsumer].each { consumer ->
            assert TemplateManager.isTemplate(consumer.getMethod('create', Map).invoke(null, [region: 'eu']))
            assert TemplateManager.isTemplate(consumer.getMethod('create', Map, String).invoke(null, [region: 'eu'], 'input'))
        }
    }

    @Issue('953')
    def 'abstract Schema Templates apply to concrete descendants'() {
        given:
        createSecondaryClass '''
            package structuredtemplate
            import com.blackbuild.klum.ast.*
            @DSL abstract class Policy { String region }
            @DSL class ConcretePolicy extends Policy { String name }
        '''
        def policy = artifactLoader.loadClass('structuredtemplate.Policy')
        def concrete = artifactLoader.loadClass('structuredtemplate.ConcretePolicy')

        when:
        def template = policy.Create.Template.FromStruct([region: 'eu'], 'input')
        def recipient
        policy.Template.With(template) { recipient = concrete.Create.One() }

        then:
        TemplateManager.isTemplate(template)
        template.region == 'eu'
        recipient.region == 'eu'
    }

    @Issue('953')
    def 'source labels are diagnostic and explicit empty labels reject'() {
        when:
        deployment.Create.Template.FromStruct([unknown: 'bad'])

        then:
        def generated = thrown(KlumModelException)
        generated.message.contains('Create.Template.FromStruct')

        when:
        deployment.Create.Template.FromStruct([:], source)

        then:
        thrown(KlumModelException)

        where:
        source << [null, '']
    }

    @Issue('953')
    def 'root and map identities are not accepted inside bodies and failures return no Template'() {
        when:
        deployment.Create.Template.FromStruct([name: 'forbidden'], 'input')

        then:
        def rootFailure = thrown(KlumModelException)
        rootFailure.message.contains('recipient of the Template')

        when:
        deployment.Create.Template.FromStruct([services: [worker: [name: 'worker']]], 'input')

        then:
        def childFailure = thrown(KlumModelException)
        childFailure.message.contains('outer Map entry key')

        when:
        def result = null
        result = deployment.Create.Template.FromStruct([unknown: 'bad'], 'input')

        then:
        thrown(KlumModelException)
        result == null
    }

    @Issue('953')
    def 'failure retains earlier setup effects and skips later operations'() {
        given:
        def result = null

        when:
        result = deployment.Create.Template.FromStruct(
                [locate: 'eu', unknown: 'bad', useDefaultRegion: [:]], 'input')

        then:
        thrown(KlumModelException)
        result == null
        deployment.events == ['configure']
    }

    @Issue('953')
    def 'public Builder application rejects a Template Builder'() {
        when:
        deployment.Create.Template.With {
            KlumStructuredInput.applyToBuilder(delegate, [region: 'bad'], 'input')
        }

        then:
        thrown(KlumModelException)
    }
}
