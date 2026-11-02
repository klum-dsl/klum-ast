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

}
