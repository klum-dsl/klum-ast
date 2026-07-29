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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class CompletedObjectSupportDocumentaryTest extends AbstractDSLSpec {

    @Issue("390")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#construction-and-structural-model-paths")
    def "reports distinct construction and structural paths for a completed deployment"() {
        given:
        createClass '''
            @DSL class Deployment {
                Service service
            }

            @DSL class Service {
            }
        '''

        when:
        def deployment = clazz.Create.With {
            service {}
        }
        def deploymentSupport = KlumObjectSupport.of(deployment)
        def serviceSupport = KlumObjectSupport.of(deployment.service)

        then:
        deploymentSupport.constructionPath == '$/Deployment.With'
        serviceSupport.constructionPath == '$/Deployment.With/service'
        deploymentSupport.modelPath == '<root>'
        serviceSupport.modelPath == '<root>.service'
        deploymentSupport.constructionPath != deploymentSupport.modelPath
        serviceSupport.constructionPath != serviceSupport.modelPath
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#ownership-paths-and-traversal")
    def "traverses a deployment composition without following linked services"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.FieldType
            import com.blackbuild.klum.ast.Owner

            @DSL class Deployment {
                Service api
                List<Service> services
                @Field(FieldType.LINK) Service catalogService
            }

            @DSL class Service {
                @Key String name
                @Owner Deployment deployment
            }
        '''
        def catalog = Service.Create.With('catalog') {}

        when:
        def deployment = clazz.Create.With {
            api('api') {}
            services {
                service('worker') {}
            }
            catalogService catalog
        }
        def structure = KlumObjectSupport.of(deployment).structure
        def visitedServicePaths = []
        structure.visit(Service) { path, service -> visitedServicePaths << path }

        then:
        KlumObjectSupport.of(deployment.api).structure.singleOwner.get().is(deployment)
        structure.getRelativePath(deployment.services[0]) == 'services[0]'
        structure.findAll(Service) == [
                '<root>.api'       : deployment.api,
                '<root>.services[0]': deployment.services[0],
        ]
        visitedServicePaths == ['<root>.api', '<root>.services[0]']
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#stored-validation")
    def "reads stored validation results for a completed deployment"() {
        given:
        createClass '''
            @DSL class Deployment {
                @Required(level = Validate.Level.WARNING)
                String releaseName

                Service service
            }

            @DSL class Service {
                @Required(level = Validate.Level.WARNING)
                String endpoint
            }
        '''

        when:
        def deployment = clazz.Create.With {
            service {}
        }
        def deploymentValidation = KlumObjectSupport.of(deployment).validation
        def deploymentResult = deploymentValidation.result
        def serviceResult = KlumObjectSupport.of(deployment.service).validation.result

        then:
        deploymentResult.issues.size() == 1
        serviceResult.issues.size() == 1
        deploymentValidation.subtreeResults == [deploymentResult, serviceResult]
    }
}
