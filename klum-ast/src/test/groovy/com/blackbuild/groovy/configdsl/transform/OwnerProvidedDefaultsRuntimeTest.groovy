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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Validate
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue("494")
class OwnerProvidedDefaultsRuntimeTest extends AbstractDSLSpec {

    def "owner values run before ordinary defaults and preserve configured recipient values"() {
        given:
        createNonDslClass '''
            package pk

            interface ShipmentDetails {
                String getRepository()
                List<String> getCustomers()
                boolean isPublished()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
                List<String> customers
                boolean published
                ProductRelease inheritedRelease
                ProductRelease configuredRelease
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product

                @Default(code = { 'ordinary-default' })
                String repository
                List<String> customers
                boolean published
            }
        '''

        when:
        def product = getClass('pk.Product').Create.With {
            repository 'owner-repository'
            customers 'owner-customer'
            published true
            inheritedRelease {}
            configuredRelease {
                repository 'recipient-repository'
                customers 'recipient-customer'
            }
        }

        then:
        product.inheritedRelease.repository == 'owner-repository'
        product.inheritedRelease.customers == ['owner-customer']
        !product.inheritedRelease.published
        product.configuredRelease.repository == 'recipient-repository'
        product.configuredRelease.customers == ['recipient-customer']
    }

    def "repeated contracts deduplicate a shared property"() {
        given:
        createNonDslClass '''
            package pk

            interface GeneralDetails {
                String getRepository()
            }

            interface ReleaseDetails {
                String getRepository()
            }

            @DSL
            class Product implements GeneralDetails, ReleaseDetails {
                String repository
                ProductRelease release
            }

            @DSL
            @OwnerProvidedDefaults(GeneralDetails)
            @OwnerProvidedDefaults(ReleaseDetails)
            class ProductRelease implements GeneralDetails, ReleaseDetails {
                @Owner Product product
                String repository
            }
        '''

        when:
        def product = getClass('pk.Product').Create.With {
            repository 'owner-repository'
            release {}
        }

        then:
        product.release.repository == 'owner-repository'
    }

    def "computed getter-only donors are rejected before the runtime phase"() {
        when:
        createNonDslClass '''
            package pk

            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String prefix

                String getRepository() { prefix + '/releases' }
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                String repository
            }
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains("property 'repository'")
        error.message.contains('stored Builder-visible property')
        error.message.contains('DEFAULT against the active Builder')
    }

    def "owned values merge recursively and missing aggregate values are rehydrated as fresh composition"() {
        given:
        createNonDslClass '''
            package pk

            interface ConfigurationDefaults {
                Settings getSettings()
                Map<String, Settings> getConfigurations()
            }

            @DSL
            class Product implements ConfigurationDefaults {
                Settings settings
                Map<String, Settings> configurations
                ProductRelease release
            }

            @DSL
            @OwnerProvidedDefaults(ConfigurationDefaults)
            class ProductRelease implements ConfigurationDefaults {
                @Owner Product product
                Settings settings
                Map<String, Settings> configurations
            }

            @DSL
            class Settings {
                @Key String name
                @Owner Object owner
                String inheritedValue
                String configuredValue
                Leaf leaf
            }

            @DSL
            class Leaf {
                @Owner Settings owner
                String value
            }
        '''

        when:
        def product = getClass('pk.Product').Create.With {
            settings('direct') {
                inheritedValue 'direct-owner'
                configuredValue 'direct-owner'
                leaf { value 'leaf-owner' }
            }
            configurations {
                settings('shared') {
                    inheritedValue 'map-owner'
                    configuredValue 'map-owner'
                }
                settings('ownerOnly') {
                    inheritedValue 'owner-only'
                    leaf { value 'owner-only-leaf' }
                }
            }
            release {
                settings('direct') { configuredValue 'direct-recipient' }
                configurations {
                    settings('shared') { configuredValue 'map-recipient' }
                    settings('recipientOnly') { configuredValue 'recipient-only' }
                }
            }
        }

        then: 'existing owned values are recursively filled without overwriting configured values'
        product.release.settings.inheritedValue == 'direct-owner'
        product.release.settings.configuredValue == 'direct-recipient'
        product.release.configurations.shared.inheritedValue == 'map-owner'
        product.release.configurations.shared.configuredValue == 'map-recipient'

        and: 'missing owned values are fresh recipient composition with recipient owner identity'
        !product.release.settings.leaf.is(product.settings.leaf)
        product.release.settings.leaf.value == 'leaf-owner'
        product.release.settings.leaf.owner.is(product.release.settings)
        !product.release.configurations.ownerOnly.is(product.configurations.ownerOnly)
        product.release.configurations.ownerOnly.inheritedValue == 'owner-only'
        product.release.configurations.ownerOnly.owner.is(product.release)
        product.release.configurations.ownerOnly.leaf.owner.is(product.release.configurations.ownerOnly)

        and: 'recipient-only map entries remain present'
        product.release.configurations.keySet() == ['shared', 'recipientOnly', 'ownerOnly'] as Set
        product.release.configurations.recipientOnly.configuredValue == 'recipient-only'
    }

    def "completed links retain identity and donor deferred actions are not replayed"() {
        given:
        createNonDslClass '''
            package pk

            interface RuntimeDefaults {
                Shared getShared()
                ActionNode getActionNode()
            }

            @DSL
            class Shared {
                String value
            }

            @DSL
            class ActionNode {
                List<String> events

                @Default
                void addOrdinaryDefault() {
                    events 'ordinary-default'
                }
            }

            @DSL
            class Product implements RuntimeDefaults {
                @Field(FieldType.LINK)
                Shared shared
                ProductRelease release
                ActionNode actionNode
            }

            @DSL
            @OwnerProvidedDefaults(RuntimeDefaults)
            class ProductRelease implements RuntimeDefaults {
                @Owner Product product
                @Field(FieldType.LINK)
                Shared shared
                ActionNode actionNode
            }
        '''
        def external = getClass('pk.Shared').Create.With { value 'external' }

        when:
        def product = getClass('pk.Product').Create.With {
            shared external
            actionNode {
                events 'configured'
                applyLater(30) { events 'donor-action' }
            }
            release {}
        }

        then:
        product.shared.is(external)
        product.release.shared.is(external)
        !product.release.actionNode.is(product.actionNode)
        product.actionNode.events == ['configured', 'ordinary-default', 'donor-action']
        product.release.actionNode.events == ['configured', 'ordinary-default']
    }

    def "a standalone recipient records a non-fatal warning that survives materialization"() {
        given:
        createNonDslClass '''
            package pk

            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product

                @Default(code = { 'ordinary-default' })
                String repository
            }
        '''

        when:
        def release = getClass('pk.ProductRelease').Create.One()
        def result = KlumObjectSupport.of(release).validation.result

        then:
        release.repository == 'ordinary-default'
        result.maxLevel == Validate.Level.WARNING
        result.issues.size() == 1
        with(result.issues.first()) {
            level == Validate.Level.WARNING
            member == 'product'
            message.contains('@OwnerProvidedDefaults')
            message.contains('pk.ShipmentDetails')
            message.contains("donor field 'product'")
        }
    }
}
