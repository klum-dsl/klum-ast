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
//file:noinspection GrPackage
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class AlternativesDocumentaryTest extends AbstractDSLSpec {

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Alternatives-Syntax.md#default-derived-from-the-class-name")
    def "uses alternative names derived from child class names"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                Map<String, Endpoint> endpoints
            }

            @DSL
            abstract class Endpoint {
                @Key String name
                String address
            }

            @DSL
            class ServiceEndpoint extends Endpoint {
            }

            @DSL
            class GatewayEndpoint extends Endpoint {
            }
        '''

        when:
        def deployment = clazz.Create.With {
            endpoints {
                serviceEndpoint('catalog') {
                    address 'https://catalog.example.test'
                }
                gatewayEndpoint('public') {
                    address 'https://gateway.example.test'
                }
            }
        }

        then:
        deployment.endpoints.catalog.class.simpleName == 'ServiceEndpoint'
        deployment.endpoints.public.class.simpleName == 'GatewayEndpoint'
        deployment.endpoints*.value.address == [
                'https://catalog.example.test',
                'https://gateway.example.test'
        ]
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Alternatives-Syntax.md#defining-explicit-names-for-a-field")
    def "uses field-local alternative names for one endpoint relationship"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                @Field(alternatives = {[internal: InternalEndpoint, publicEndpoint: PublicEndpoint]})
                Map<String, Endpoint> endpoints
            }

            @DSL
            abstract class Endpoint {
                @Key String name
            }

            @DSL
            class InternalEndpoint extends Endpoint {
            }

            @DSL
            class PublicEndpoint extends Endpoint {
            }
        '''

        when:
        def deployment = clazz.Create.With {
            endpoints {
                internal('catalog') {}
                publicEndpoint('gateway') {}
            }
        }

        then:
        deployment.endpoints.catalog.class.simpleName == 'InternalEndpoint'
        deployment.endpoints.gateway.class.simpleName == 'PublicEndpoint'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Alternatives-Syntax.md#explicit-short-names-for-subclasses")
    def "uses deliberate subtype short names for endpoint alternatives"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                Map<String, Endpoint> endpoints
            }

            @DSL
            abstract class Endpoint {
                @Key String name
            }

            @DSL(shortName = 'service')
            class ServiceEndpoint extends Endpoint {
            }

            @DSL(shortName = 'gateway')
            class GatewayEndpoint extends Endpoint {
            }
        '''

        when:
        def deployment = clazz.Create.With {
            endpoints {
                service('catalog') {}
                gateway('public') {}
            }
        }

        then:
        deployment.endpoints.catalog.class.simpleName == 'ServiceEndpoint'
        deployment.endpoints.public.class.simpleName == 'GatewayEndpoint'
    }
}
