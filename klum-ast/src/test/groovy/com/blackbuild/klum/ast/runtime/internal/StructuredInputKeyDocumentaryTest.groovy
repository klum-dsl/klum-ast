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

@Issue('954')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#construction-keys')
class StructuredInputKeyDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class deployment

    def setup() {
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        createClass '''
            package documentarykeys
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                @Key String name
                Service service
                Map<String, Service> services
            }
            @DSL class Service {
                @Key @JsonProperty('service-id') String name
                String image
            }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        deployment = Class.forName('documentarykeys.Deployment', false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    def 'self-contained documents carry root and direct-child keys while Maps supply outer keys'() {
        given:
        def parsed = [name: 'production',
                      service: ['service-id': 'catalog', image: 'catalog:1.0'],
                      services: [worker: [image: 'worker:1.0']]]

        when:
        def model = KlumStructuredInput.readRoot(deployment, parsed, 'deployment.yaml')

        then:
        model.name == 'production'
        model.service.name == 'catalog'
        model.service.image == 'catalog:1.0'
        model.services.worker.name == 'worker'
        model.services.worker.image == 'worker:1.0'
    }

    def 'explicit-rootKey payloads omit root identity and still supply direct-child identity'() {
        given:
        def parsed = [service: ['service-id': 'catalog', image: 'catalog:1.0']]

        when:
        def model = KlumStructuredInput.readRoot(deployment, 'production', parsed, 'deployment.yaml')

        then:
        model.name == 'production'
        model.service.name == 'catalog'

        when: 'even an equal inner key is a second identity authority'
        KlumStructuredInput.readRoot(deployment, 'production', [name: 'production'], 'deployment.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('rootKey argument')
        failure.message.contains('inner identity is forbidden even if equal')
    }
}
