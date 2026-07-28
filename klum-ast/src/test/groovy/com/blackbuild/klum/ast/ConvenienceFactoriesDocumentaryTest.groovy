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
class ConvenienceFactoriesDocumentaryTest extends AbstractDSLSpec {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#script-classes")
    def "loads a completed deployment from a script class"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                String endpoint
            }
        '''
        def deploymentScript = createSecondaryClass '''
            import pk.Deployment

            Deployment.Create.With {
                endpoint 'https://catalog.example.test'
            }
        ''', 'CatalogDeployment.groovy'

        when:
        def deployment = clazz.Create.From(deploymentScript)

        then:
        deployment.endpoint == 'https://catalog.example.test'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#delegating-scripts")
    def "uses a DelegatingScript class as keyed configuration content"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                @Key String name
                String endpoint
            }
        '''
        def deploymentScript = createSecondaryClass '''
            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            endpoint 'https://catalog.example.test'
        ''', 'CatalogService.groovy'

        when:
        def service = clazz.Create.From(deploymentScript)

        then:
        service.name == 'CatalogService'
        service.endpoint == 'https://catalog.example.test'
    }

    @Issue("198")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#script-and-delegating-script-for-collections-and-maps")
    def "applies DelegatingScript recipes to list and map relationship factories"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                List<Service> services
                Map<String, Worker> workers
            }

            @DSL
            class Service {
                String endpoint
            }

            @DSL
            class Worker {
                @Key String name
                String queue
            }
        '''
        def catalogService = createSecondaryClass '''
            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            endpoint 'https://catalog.example.test'
        ''', 'CatalogService.groovy'
        def billingService = createSecondaryClass '''
            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            endpoint 'https://billing.example.test'
        ''', 'BillingService.groovy'
        def catalogWorker = createSecondaryClass '''
            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            queue 'catalog'
        ''', 'CatalogWorker.groovy'
        def billingWorker = createSecondaryClass '''
            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            queue 'billing'
        ''', 'BillingWorker.groovy'

        when:
        def deployment = clazz.Create.With {
            services(catalogService, billingService)
            workers(catalogWorker, billingWorker)
        }

        then:
        deployment.services*.endpoint == ['https://catalog.example.test', 'https://billing.example.test']
        deployment.workers.keySet() == ['CatalogWorker', 'BillingWorker'] as Set
        deployment.workers*.value.queue == ['catalog', 'billing']
    }

    @Issue("114")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#text")
    def "loads keyed configuration from text"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                @Key String name
                String endpoint
            }
        '''

        when:
        def service = clazz.Create.From('catalog', '''
            endpoint 'https://catalog.example.test'
        ''')

        then:
        service.name == 'catalog'
        service.endpoint == 'https://catalog.example.test'
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#file-or-url")
    def "derives a keyed configuration name from a file or URL"() {
        given:
        createClass '''
            package pk

            @DSL
            class ServiceConfiguration {
                @Key String name
                String endpoint
            }
        '''
        def configuration = temporaryFolder.newFile('catalog.groovy')
        configuration.text = '''
            endpoint 'https://catalog.example.test'
        '''

        when:
        def fromFile = clazz.Create.From(configuration)
        def fromUrl = clazz.Create.From(configuration.toURI().toURL())

        then:
        [fromFile, fromUrl]*.name == ['catalog', 'catalog']
        [fromFile, fromUrl]*.endpoint == ['https://catalog.example.test', 'https://catalog.example.test']
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#classpath")
    def "discovers a deployment entry point from its classpath marker"() {
        given:
        def classPathRoot = temporaryFolder.newFolder()
        def marker = new File(classPathRoot, 'META-INF/klum-model/pk.Deployment.properties')
        marker.parentFile.mkdirs()
        createClass '''
            package pk

            @DSL
            class Deployment {
                String endpoint
            }
        '''
        createSecondaryClass '''
            package impl

            import pk.Deployment

            Deployment.Create.With {
                endpoint 'https://catalog.example.test'
            }
        ''', 'CatalogDeployment.groovy'
        marker.text = 'model-class: impl.CatalogDeployment'
        loader.addURL(classPathRoot.toURI().toURL())

        when:
        def deployment = clazz.Create.FromClasspath()

        then:
        deployment.endpoint == 'https://catalog.example.test'
    }

    @Issue("359")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#map")
    def "adapts external map keys in a custom factory"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class Person {
                String firstName
                String lastName

                static class Factory extends KlumFactory.Unkeyed<Person> {
                    protected Factory() { super(Person) }

                    @Override
                    Person FromMap(Map<String, Object> values) {
                        Map<String, Object> camelCaseValues = values.collectEntries { key, value ->
                            [(key as String).tokenize('-').collect { it.capitalize() }.join('').uncapitalize(), value]
                        }
                        return super.FromMap(camelCaseValues)
                    }
                }
            }
        '''

        when:
        def person = clazz.Create.FromMap(['first-name': 'Klaus', 'last-name': 'Müller'])

        then:
        person.firstName == 'Klaus'
        person.lastName == 'Müller'
    }
}
