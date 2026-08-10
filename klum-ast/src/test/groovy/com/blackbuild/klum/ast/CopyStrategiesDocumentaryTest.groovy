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
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class CopyStrategiesDocumentaryTest extends AbstractDSLSpec {

    @Issue("309")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Copy-Strategies.md#single-object")
    def "merges a nested service configuration from a template"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class Endpoint {
                String host
                Integer port
            }

            @DSL
            class Service {
                @Overwrite.Single(OverwriteStrategy.Single.MERGE)
                Endpoint endpoint
            }
        '''
        def serviceType = getClass('pk.Service')
        def baseline = serviceType.Create.Template.With {
            endpoint {
                host 'catalog.example.test'
            }
        }

        when:
        def service = serviceType.Create.With {
            endpoint {
                port 8443
            }
            copyFrom baseline
        }

        then:
        service.endpoint.host == 'catalog.example.test'
        service.endpoint.port == 8443
    }

    @Issue("309")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Copy-Strategies.md#collections")
    def "adds template roles to a service configuration"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class Service {
                @Overwrite.Collection(OverwriteStrategy.Collection.ADD)
                List<String> roles
            }
        '''
        def baseline = clazz.Create.Template.With {
            roles 'observer'
        }

        when:
        def service = clazz.Create.With {
            roles 'operator'
            copyFrom baseline
        }

        then:
        service.roles == ['operator', 'observer']
    }

    @Issue("309")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Copy-Strategies.md#maps")
    def "merges environment map values from a template"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class Environment {
                @Key String name
                String region
                Integer replicas
            }

            @DSL
            class Deployment {
                @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES)
                Map<String, Environment> environments
            }
        '''
        def deploymentType = getClass('pk.Deployment')
        def baseline = deploymentType.Create.Template.With {
            environment('production') {
                region 'eu-central'
            }
        }

        when:
        def deployment = deploymentType.Create.With {
            environment('production') {
                replicas 3
            }
            copyFrom baseline
        }

        then:
        deployment.environments.production.region == 'eu-central'
        deployment.environments.production.replicas == 3
    }

    @Issue("581")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Copy-Strategies.md#nested-annotations")
    def "applies the packaged Helm copy policy to a deployment"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.copy.HelmOverwrite

            @DSL
            class Service {
                @Key String name
                String host
                String port
            }

            @HelmOverwrite
            @DSL
            class Deployment {
                String image
                List<String> arguments
                Map<String, Service> services
            }
        '''
        def deploymentType = getClass('pk.Deployment')
        def baseline = deploymentType.Create.Template.With {
            image 'catalog:2.0'
            arguments = []
            service('web') {
                port '8443'
            }
            service('metrics') {
                host 'metrics.example.test'
            }
        }

        when:
        def deployment = deploymentType.Create.With {
            image 'catalog:1.0'
            arguments '--verbose'
            service('web') {
                host 'catalog.example.test'
            }
            copyFrom baseline
        }

        then:
        deployment.image == 'catalog:2.0'
        deployment.arguments == []
        deployment.services.web.host == 'catalog.example.test'
        deployment.services.web.port == '8443'
        deployment.services.metrics.host == 'metrics.example.test'
    }
}
