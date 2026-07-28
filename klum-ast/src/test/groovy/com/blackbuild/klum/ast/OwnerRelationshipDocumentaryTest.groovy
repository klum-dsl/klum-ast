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
class OwnerRelationshipDocumentaryTest extends AbstractDSLSpec {

    @Issue("171")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#the-owner-annotation")
    def "assigns each matching owner field for an owned service"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                Service service
            }

            @DSL
            class Service {
                @Owner Deployment deployment
                @Owner Object container
                String image
            }
        '''

        when:
        def deployment = clazz.Create.With {
            service {
                image 'catalog:1.0'
            }
        }

        then:
        deployment.service.deployment.is(deployment)
        deployment.service.container.is(deployment)
    }

    @Issue("176")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#owner-methods")
    def "derives service metadata in an owner method"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                String name
                Service service
            }

            @DSL
            class Service {
                String deploymentName

                @Owner
                void recordDeployment(Deployment deployment) {
                    deploymentName = deployment.name
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            name 'catalog'
            service {}
        }

        then:
        deployment.service.deploymentName == 'catalog'
    }

    @Issue("49")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#transitive-owners")
    def "finds the first matching transitive owner in a deployment path"() {
        given:
        createClass '''
            package pk

            @DSL
            class Estate extends Region {
                Platform platform
            }

            @DSL
            abstract class Region {
            }

            @DSL
            class Platform extends Region {
                @Owner Estate estate
                Deployment deployment
            }

            @DSL
            class Deployment {
                @Owner Platform platform
                Service service
            }

            @DSL
            class Service {
                @Owner Deployment deployment
                @Owner(transitive = true) Region region
            }
        '''

        when:
        def estate = clazz.Create.With {
            platform {
                deployment {
                    service {}
                }
            }
        }

        then:
        estate.platform.deployment.service.deployment.is(estate.platform.deployment)
        estate.platform.deployment.service.region.is(estate.platform)
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#root-owners")
    def "makes the root deployment available without a direct owner path"() {
        given:
        createClass '''
            package pk

            @DSL
            class MyModel {
                SomeElement someElement
            }

            @DSL
            abstract class ModelElement {
                @Owner(root = true) MyModel root
            }

            @DSL
            class SomeElement extends ModelElement {
            }
        '''

        when:
        def model = clazz.Create.With {
            someElement {
            }
        }

        then:
        model.someElement.root.is(model)
    }

    @Issue("189")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#owner-converters")
    def "converts an owner into readable service metadata"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                String name
                Service service
            }

            @DSL
            class Service {
                @Owner Deployment deployment
                @Owner(converter = { Deployment deployment -> deployment.name }) String deploymentName

                String uppercaseDeploymentName

                @Owner(converter = { Deployment deployment -> deployment.name })
                void recordUppercaseDeploymentName(String name) {
                    uppercaseDeploymentName = name.toUpperCase()
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            name 'catalog'
            service {}
        }

        then:
        deployment.service.deployment.is(deployment)
        deployment.service.deploymentName == 'catalog'
        deployment.service.uppercaseDeploymentName == 'CATALOG'
    }
}
