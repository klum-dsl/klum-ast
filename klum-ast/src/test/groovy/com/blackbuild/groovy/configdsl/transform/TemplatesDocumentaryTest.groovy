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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class TemplatesDocumentaryTest extends AbstractDSLSpec {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#creating-templates")
    def "creates an unkeyed reusable template without lifecycle callbacks"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                @Key String name
                String region
                boolean postApplyCalled
                boolean postCreateCalled

                @PostApply
                void recordPostApply() {
                    postApplyCalled = true
                }

                @PostCreate
                void recordPostCreate() {
                    postCreateCalled = true
                }
            }
        '''

        when:
        def template = clazz.Template.Create {
            region 'eu-central'
        }

        then:
        template.name == null
        template.region == 'eu-central'
        !template.postApplyCalled
        !template.postCreateCalled
    }

    @Issue("322")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#creating-templates")
    def "creates a template from a DelegatingScript file"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                String url
                String region
            }
        '''
        def templateFile = temporaryFolder.newFile('service-template.groovy')
        templateFile.text = '''
            url 'https://config.example.test'
            region 'eu-central'
        '''

        when:
        def template = clazz.Template.CreateFrom(templateFile)

        then:
        template.url == 'https://config.example.test'
        template.region == 'eu-central'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#copyfrom")
    def "copies a template into one completed service configuration"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                String url
                List<String> roles
            }
        '''
        def template = clazz.Template.Create {
            url 'https://config.example.test'
            roles 'developer', 'guest'
        }

        when:
        def service = clazz.Create.With {
            copyFrom template
            url 'https://catalog.example.test'
        }

        then:
        service.url == 'https://catalog.example.test'
        service.roles == ['developer', 'guest']
    }

    @Issue("376")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#templatewith")
    def "applies one scoped template to multiple service configurations"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                String url
                List<String> roles
            }
        '''

        and:
        def template = clazz.Template.Create {
            url 'https://config.example.test'
            roles 'developer', 'guest'
        }

        when:
        def catalog
        def billing
        clazz.Template.With(template) {
            catalog = clazz.Create.With {
                roles 'catalog'
            }
            billing = clazz.Create.With {
                roles 'billing'
            }
        }

        then:
        catalog.url == 'https://config.example.test'
        catalog.roles == ['developer', 'guest', 'catalog']
        billing.url == 'https://config.example.test'
        billing.roles == ['developer', 'guest', 'billing']
    }

    @Issue("376")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#with-an-anonymous-template")
    def "applies named values through an anonymous scoped template"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                String url
                List<String> roles
            }
        '''

        when:
        def service
        clazz.Template.With(url: 'https://config.example.test') {
            service = clazz.Create.With {
                roles 'catalog'
            }
        }

        then:
        service.url == 'https://config.example.test'
        service.roles == ['catalog']
    }

    @Issue("82")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#templates-for-collection-factories")
    def "applies one collection-factory template to every created server"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                List<Server> servers
            }

            @DSL
            class Server {
                @Key String name
                boolean clusterMember
            }
        '''

        when:
        def deployment = clazz.Create.With {
            servers(clusterMember: true) {
                server('catalog') {}
                server('billing') {}
            }
        }

        then:
        deployment.servers*.name == ['catalog', 'billing']
        deployment.servers*.clusterMember == [true, true]
    }

    @Issue("376")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#templatewithall")
    def "applies templates for multiple configuration types in one scope"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                Service service
                Database database
            }

            @DSL
            class Service {
                String tier
            }

            @DSL
            class Database {
                String engine
            }
        '''
        def serviceType = getClass('pk.Service')
        def databaseType = getClass('pk.Database')
        def serviceTemplate = serviceType.Template.Create { tier 'application' }
        def databaseTemplate = databaseType.Template.Create { engine 'postgresql' }

        when:
        def deployment
        clazz.Template.WithAll([serviceTemplate, databaseTemplate]) {
            deployment = clazz.Create.With {
                service {}
                database {}
            }
        }

        then:
        deployment.service.tier == 'application'
        deployment.database.engine == 'postgresql'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#templates-for-abstract-classes")
    def "creates a template implementation for an abstract configuration type"() {
        given:
        createClass '''
            package pk

            @DSL
            abstract class RetryPolicy {
                String name

                abstract int retries()
            }
        '''

        when:
        def template = clazz.Template.Create {
            name 'resilient'
        }

        then:
        getClass('pk.RetryPolicy$Template').isInstance(template)
        template.name == 'resilient'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#order-of-precedence")
    def "lets child templates and explicit configuration override parent defaults"() {
        given:
        createClass '''
            package pk

            @DSL
            class ParentConfiguration {
                String environment = 'development'
            }

            @DSL
            class ServiceConfiguration extends ParentConfiguration {
            }
        '''
        def parentType = getClass('pk.ParentConfiguration')
        def serviceType = getClass('pk.ServiceConfiguration')
        def parentTemplate = parentType.Template.Create { environment 'shared' }
        def serviceTemplate = serviceType.Template.Create { environment 'production' }

        when:
        def fromTemplates
        def explicit
        serviceType.Template.WithAll([parentTemplate, serviceTemplate]) {
            fromTemplates = serviceType.Create.One()
            explicit = serviceType.Create.With {
                environment 'preview'
            }
        }

        then:
        fromTemplates.environment == 'production'
        explicit.environment == 'preview'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#order-of-precedence")
    def "lets explicit collection assignment replace inherited template values"() {
        given:
        createClass '''
            package pk

            @DSL
            class ParentConfiguration {
                List<String> regions = ['development']
            }

            @DSL
            class ServiceConfiguration extends ParentConfiguration {
            }
        '''
        def parentType = getClass('pk.ParentConfiguration')
        def serviceType = getClass('pk.ServiceConfiguration')
        def parentTemplate = parentType.Template.Create { regions 'shared' }
        def serviceTemplate = serviceType.Template.Create { regions 'production' }

        when:
        def explicit
        serviceType.Template.WithAll([parentTemplate, serviceTemplate]) {
            explicit = serviceType.Create.With {
                regions = ['preview']
            }
        }

        then:
        explicit.regions == ['preview']
    }

    @Issue("376")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#applylater-and-templates")
    def "replays a template applyLater recipe for each completed configuration"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                String name
                String identifier
            }
        '''
        def template = clazz.Template.Create {
            applyLater {
                identifier name.toUpperCase()
            }
        }

        when:
        def catalog
        def billing
        clazz.Template.With(template) {
            catalog = clazz.Create.With {
                name 'catalog'
            }
            billing = clazz.Create.With {
                name 'billing'
            }
        }

        then:
        template.identifier == null
        catalog.identifier == 'CATALOG'
        billing.identifier == 'BILLING'
    }
}
