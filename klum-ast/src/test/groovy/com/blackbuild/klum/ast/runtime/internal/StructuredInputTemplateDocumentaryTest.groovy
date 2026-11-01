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

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('953')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#structured-input-templates')
class StructuredInputTemplateDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class deployment

    def setup() {
        createClass '''
            package structuredtemplate
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                static List<String> events = []
                @Key String name
                String region
                String identifier
                Service primary
                Map<String, Service> services
                @Builder.Method void locate(String value) {
                    events << 'configure'
                    region = value
                    applyLater { identifier name.toUpperCase() }
                }
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

    def 'parsed keyless bodies define ordinary reusable Template recipes'() {
        given:
        def parsed = [locate: 'eu', primary: [name: 'api', image: 'api:1'],
                      services: [worker: [image: 'worker:1']]]

        when:
        def template = KlumStructuredInput.readTemplate(deployment, parsed, 'deployment.yaml')

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

    def 'reconfiguring existing Template children does not run lifecycle callbacks'() {
        when:
        def template = KlumStructuredInput.readTemplate(deployment,
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

    def 'root and map identities are not accepted inside bodies and failures return no Template'() {
        when:
        KlumStructuredInput.readTemplate(deployment, [name: 'forbidden'], 'input')

        then:
        def rootFailure = thrown(KlumModelException)
        rootFailure.message.contains('recipient of the Template')

        when:
        KlumStructuredInput.readTemplate(deployment, [services: [worker: [name: 'worker']]], 'input')

        then:
        def childFailure = thrown(KlumModelException)
        childFailure.message.contains('outer Map entry key')

        when:
        def result = null
        result = KlumStructuredInput.readTemplate(deployment, [unknown: 'bad'], 'input')

        then:
        thrown(KlumModelException)
        result == null
    }

    def 'public Builder application rejects a Template Builder'() {
        when:
        deployment.Create.Template.With {
            KlumStructuredInput.applyToBuilder(delegate, [region: 'bad'], 'input')
        }

        then:
        thrown(KlumModelException)
    }
}
