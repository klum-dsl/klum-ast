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
package com.blackbuild.klum.ast.jackson

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.codehaus.groovy.runtime.InvokerHelper
import spock.lang.Issue

import java.time.Duration

@Issue(['932', '529'])
class StructuredInputHolderProbeTest extends AbstractDSLSpec {

    private static final String SOURCE = '''
        package holderprobe
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.layer3.AutoLink
        import com.blackbuild.klum.ast.layer3.LinkTo
        import java.time.Duration

        @DSL class ApplicationHolder {
            static List<String> events = []
            Application application
            @PostCreate void created() { events << 'holder:create' }
            @PostApply void applied() { events << 'holder:apply' }
            @PostTree void tree() { events << 'holder:tree' }
            @Validate void checked() { events << 'holder:validate' }
        }
        @DSL class Environment {
            static List<String> events = []
            @PostCreate void created() { events << 'environment:create' }
            @PostTree void tree() { events << 'environment:tree' }
            @Validate void checked() { events << 'environment:validate' }
            String name
            String region
            int retryCount
        }
        @DSL class Application {
            String name
            @Owner ApplicationHolder owner
            @Field(FieldType.LINK) @Required Environment environment
            @Field(FieldType.LINK) @LinkTo(field = 'environment', provider = { delegate })
            Environment linkedEnvironment
            @Default(code = { environment.region }) String region
            @Default(code = { environment.retryCount }) int retries
            @Default(code = { true }) boolean enabled
            Map<String, String> labels
            Map<String, String> annotations
            Map<String, String> topicResourceNames
            Duration reservationTimeout
            @PostCreate void created() { ApplicationHolder.events << 'application:create' }
            @PostApply void applied() { ApplicationHolder.events << 'application:apply' }
            @AutoLink void inspectEnvironment() { ApplicationHolder.events << "application:link:${environment.name}".toString() }
            @PostTree void tree() {
                ApplicationHolder.events << "application:tree:${environment.name}:$region:$retries:$enabled".toString()
            }
            @Validate void checked() {
                assert environment != null
                assert linkedEnvironment.is(environment)
                assert owner != null
                ApplicationHolder.events << "application:validate:${environment.name}".toString()
            }
        }
    '''

    def 'ordinary Holder postpones completion while #route loads and receives a completed Environment'() {
        given:
        createClass SOURCE
        def holderType = getClass('holderprobe.ApplicationHolder')
        def applicationType = getClass('holderprobe.Application')
        def environmentType = getClass('holderprobe.Environment')
        def suppliedEnvironment = environmentType.Create.With(name: 'production', region: 'eu-west', retryCount: 7)
        def environmentEvents = new ArrayList(environmentType.events)
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())
        def values = [name: 'orders', retries: 0, enabled: false]
        def capturedApplication
        def capturedHolder

        when:
        def model = holderType.Create.With {
            capturedHolder = delegate
            if (route == 'readBuilder') {
                capturedApplication = importer.readBuilder(applicationType.Create.AsBuilder(), KlumJacksonInput.map(values))
            } else if (route == 'applyToBuilder') {
                capturedApplication = applicationType.Create.AsBuilder().One()
                assert importer.applyToBuilder(capturedApplication, KlumJacksonInput.map(values)).is(capturedApplication)
            } else if (route == 'named') {
                capturedApplication = applicationType.Create.AsBuilder().With(values)
            } else {
                capturedApplication = applicationType.Create.AsBuilder().One()
                StructuredInputHolderProbeTest.admittedOperations(capturedApplication, values)
            }
            assert capturedApplication instanceof KlumBuilder
            assert !capturedApplication.sealed
            assert capturedApplication.completedModel == null
            assert holderType.events.every { it.endsWith(':create') || it.endsWith(':apply') }
            assert capturedApplication.environment == null
            capturedApplication.environment(suppliedEnvironment)
            application(capturedApplication)
            assert capturedApplication.environment.name == 'production'
            assert !capturedHolder.sealed
            assert capturedHolder.completedModel == null
            assert holderType.events.every { it.endsWith(':create') || it.endsWith(':apply') }
        }

        then:
        model.application.name == 'orders'
        environmentEvents == ['environment:create', 'environment:tree', 'environment:validate']
        environmentType.events == environmentEvents
        model.application.owner.is(model)
        model.application.environment.is(suppliedEnvironment)
        model.application.linkedEnvironment.is(suppliedEnvironment)
        model.application.region == 'eu-west'
        model.application.retries == 7
        !model.application.enabled
        capturedApplication.sealed
        capturedHolder.sealed
        capturedApplication.completedModel.is(model.application)
        capturedHolder.completedModel.is(model)
        holderType.events.count('holder:create') == 1
        holderType.events.count('holder:apply') == 1
        holderType.events.count('holder:tree') == 1
        holderType.events.count('holder:validate') == 1
        holderType.events.count('application:link:production') == 1
        holderType.events.count('application:tree:production:eu-west:7:false') == 1
        holderType.events.count('application:validate:production') == 1
        holderType.events.count('application:create') == callbackCount
        holderType.events.count('application:apply') == callbackCount

        where:
        route            | callbackCount
        'readBuilder'    | 2
        'applyToBuilder' | 2
        'named'          | 1
        'operations'     | 1
    }

    def '#route leaves ordinary map keys literal while Jackson kebab naming changes property names'() {
        given:
        createClass SOURCE
        def holderType = getClass('holderprobe.ApplicationHolder')
        def applicationType = getClass('holderprobe.Application')
        def suppliedEnvironment = getClass('holderprobe.Environment').Create.With(name: 'production', region: 'eu-west', retryCount: 7)
        def importer = KlumJacksonImporter.using(new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).findAndRegisterModules())
        def labels = ['app.kubernetes.io/name': 'Orders API', 'camelCase': 'literal']
        def annotations = ['some-domain.example/annotationKey': 'literal']
        def topics = ['OrderCreated': 'orders.created-v1', 'topic.with.dots': 'external-name']

        when:
        def model = holderType.Create.With {
            def childBuilder
            if (route == 'Jackson') {
                childBuilder = importer.readBuilder(applicationType.Create.AsBuilder(),
                        KlumJacksonInput.map([labels: labels, annotations: annotations, 'topic-resource-names': topics]))
            } else if (route == 'named') {
                childBuilder = applicationType.Create.AsBuilder().With(
                        labels: labels, annotations: annotations, topicResourceNames: topics)
            } else {
                childBuilder = applicationType.Create.AsBuilder().One()
                StructuredInputHolderProbeTest.admittedOperations(childBuilder,
                        [labels: labels, annotations: annotations, topicResourceNames: topics])
            }
            childBuilder.environment(suppliedEnvironment)
            application(childBuilder)
        }

        then:
        model.application.labels == labels
        model.application.annotations == annotations
        model.application.topicResourceNames == topics
        model.application.environment.is(suppliedEnvironment)

        where:
        route << ['Jackson', 'named', 'operations']
    }

    def 'plain Duration uses its native ISO converter while compact text and unconfigured Jackson remain unsupported'() {
        given:
        createClass '''
            package holderduration
            import java.time.Duration
            @DSL class DurationApplication {
                @Required Duration reservationTimeout
            }
        '''
        def durationType = getClass('holderduration.DurationApplication')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())
        Map parsedYaml = new ObjectMapper(new YAMLFactory()).readValue(
                'config:\n  reservationTimeout: 30s\n', Map)
        Map compactYaml = parsedYaml.config

        when:
        def model = durationType.Create.With(reservationTimeout: 'PT30S')
        def interpreted = durationType.Create.With {
            StructuredInputHolderProbeTest.admittedOperations(delegate, [reservationTimeout: 'PT30S'])
        }

        then:
        model.reservationTimeout == Duration.ofSeconds(30)
        interpreted.reservationTimeout == Duration.ofSeconds(30)
        compactYaml.reservationTimeout == '30s'

        when:
        durationType.Create.With(compactYaml)

        then:
        def compact = thrown(RuntimeException)
        causeMessages(compact).contains('cannot be parsed')

        when:
        importer.readRoot(durationType, KlumJacksonInput.map(['reservationTimeout': 'PT30S']))

        then:
        def missingCodec = thrown(KlumModelException)
        causeMessages(missingCodec).contains('java.time.Duration')
    }

    /** Fixture-local explicit admission; existing Groovy dispatch owns selection and mutation. */
    private static void admittedOperations(Object builder, Map values) {
        assert values.keySet().every { it in ['name', 'retries', 'enabled', 'labels', 'annotations', 'topicResourceNames', 'reservationTimeout'] }
        values.each { String name, Object value ->
            InvokerHelper.invokeMethod(builder, name, [value] as Object[])
        }
    }

    private static String causeMessages(Throwable failure) {
        List<String> messages = []
        for (Throwable current = failure; current != null; current = current.cause)
            messages << current.message
        messages.join(' | ')
    }
}
