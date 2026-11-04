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

/** The caller parses YAML data before invoking Struct; KlumAST supplies no parser or managed importer here. */
@Issue('1015')
@Tag('documentary')
class StructuredYamlDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    @See(['https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#factory-roots',
          'https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#apply-to-an-active-builder'])
    def 'caller parsed YAML creates keyed roots and configures an active Builder body'() {
        given:
        createClass '''
            package yamlroot
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.blackbuild.klum.ast.Struct
            @DSL class Service {
                @Key @Struct.Name('service-id') String name
                String host
            }
        '''
        def service = packagedSchema('yamlroot.Service')
        Map document = parseYaml '''
service-id: catalog
host: api
'''
        Map body = parseYaml '''
host: api
'''

        when:
        def root = service.Create.FromStruct(document, 'service.yaml')
        def explicit = service.Create.FromStruct('catalog', body, 'body.yaml')
        def active = service.Create.With('catalog') {
            applyStruct(body, 'body.yaml')
        }

        then:
        root.name == 'catalog'
        root.host == 'api'
        explicit.name == root.name
        explicit.host == root.host
        active.name == root.name
        active.host == root.host
        !body.containsKey('service-id')
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#read-a-concrete-root')
    def 'caller parsed YAML supplies one nested Map payload to an authored operation'() {
        given:
        createClass '''
            package yamloperation
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            @DSL class Application {
                String host
                int port
                @Builder.Method void endpoint(Map<String, String> settings) {
                    host = settings.host
                    port = Integer.parseInt(settings.port)
                }
            }
        '''
        def application = packagedSchema('yamloperation.Application')
        Map parsed = parseYaml '''
endpoint:
  host: api
  port: "8080"
'''

        when:
        def mapped = application.Create.FromStruct(parsed, 'application.yaml')
        def ordinary = application.Create.With {
            endpoint(host: 'api', port: '8080')
        }

        then:
        parsed.endpoint.port instanceof String
        mapped.host == ordinary.host
        mapped.port == ordinary.port
        mapped.host == 'api'
        mapped.port == 8080
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#construction-keys')
    def 'caller parsed YAML supplies direct child identity and literal Map entry identity'() {
        given:
        createClass '''
            package yamlkeys
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.blackbuild.klum.ast.Struct
            @DSL class Deployment {
                @Key String name
                Service service
                Map<String, Service> services
            }
            @DSL class Service {
                @Key @Struct.Name('service-id') String name
                String image
            }
        '''
        def deployment = packagedSchema('yamlkeys.Deployment')
        Map parsed = parseYaml '''
name: production
service:
  service-id: catalog
  image: catalog:1.0
services:
  worker:
    image: worker:1.0
'''

        when:
        def model = deployment.Create.FromStruct(parsed, 'deployment.yaml')

        then:
        model.name == 'production'
        model.service.name == 'catalog'
        model.service.image == 'catalog:1.0'
        model.services.keySet() == ['worker'] as Set
        model.services.worker.name == 'worker'
        model.services.worker.image == 'worker:1.0'
        !parsed.services.worker.containsKey('service-id')
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#owned-child-sequences')
    def 'caller parsed YAML preserves owned sequence order and duplicate child bodies'() {
        given:
        createClass '''
            package yamlsequence
            import com.blackbuild.klum.ast.DSL
            @DSL class Service { List<Route> routes }
            @DSL class Route { String path }
        '''
        def service = packagedSchema('yamlsequence.Service')
        Map parsed = parseYaml '''
routes:
  - path: /health
  - path: /metrics
  - path: /health
'''

        when:
        def mapped = service.Create.FromStruct(parsed, 'routes.yaml')
        def ordinary = service.Create.With {
            routes {
                route { path '/health' }
                route { path '/metrics' }
                route { path '/health' }
            }
        }

        then:
        parsed.routes instanceof List
        mapped.routes*.path == ordinary.routes*.path
        mapped.routes*.path == ['/health', '/metrics', '/health']
        !mapped.routes[0].is(mapped.routes[2])
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#structured-input-templates')
    def 'caller parsed keyless YAML defines a reusable Template recipe'() {
        given:
        createClass '''
            package yamltemplate
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            @DSL class Deployment {
                @Key String name
                String region
                Service primary
                Map<String, Service> services
                @Builder.Method void locate(String value) { region = value }
            }
            @DSL class Service { @Key String name; String image }
        '''
        def deployment = packagedSchema('yamltemplate.Deployment')
        Map parsed = parseYaml '''
locate: eu
primary:
  name: api
  image: api:1
services:
  worker:
    image: worker:1
'''

        when:
        def template = deployment.Create.Template.FromStruct(parsed, 'template.yaml')
        def first
        def second
        deployment.Template.With(template) {
            first = deployment.Create.With('first')
            second = deployment.Create.With('second')
        }

        then:
        template.name == null
        first.name == 'first'
        second.name == 'second'
        first.region == 'eu'
        second.region == first.region
        first.primary.name == 'api'
        first.primary.image == 'api:1'
        second.services.worker.name == 'worker'
        second.services.worker.image == 'worker:1'
        !first.primary.is(second.primary)
        !first.services.worker.is(second.services.worker)
    }

    private Class packagedSchema(String binaryName) {
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        artifacts.loadClass(binaryName)
    }
}
