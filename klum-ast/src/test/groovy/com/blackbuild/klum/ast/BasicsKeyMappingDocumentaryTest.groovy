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
class BasicsKeyMappingDocumentaryTest extends AbstractDSLSpec {

    @Issue("128")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#keymapping-for-simple-maps")
    def "derives simple map keys from configured values"() {
        given:
        createClass '''
            package pk

            @DSL
            class ReleaseChannels {
                @Field(keyMapping = { it.toLowerCase() })
                Map<String, String> channels
            }
        '''

        when:
        def releaseChannels = clazz.Create.With {
            channel 'STABLE'
            channels 'Preview', 'Development'
        }

        then:
        releaseChannels.channels == [stable: 'STABLE', preview: 'Preview', development: 'Development']
    }

    @Issue("127")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#automatic-key-determination-for-dsl-map-entries")
    def "uses default and configured keys for DSL map entries"() {
        given:
        createClass '''
            package pk

            @DSL
            class Deployment {
                Map<String, Service> defaultServices

                @Field(keyMapping = { it.route })
                Map<String, Service> services
            }

            @DSL
            class Service {
                @Key String name
                String route
                int port
            }
        '''

        when:
        def deployment = clazz.Create.With {
            defaultService('inventory') {
                route 'inventory-api'
                port 8080
            }
            service('catalog') {
                route 'catalog-api'
                port 8443
            }
            service('metrics') {
                route 'metrics-api'
                port 9090
            }
        }

        then:
        deployment.defaultServices.keySet() == ['inventory'] as Set
        deployment.defaultServices.inventory.name == 'inventory'
        deployment.services.keySet() == ['catalog-api', 'metrics-api'] as Set
        deployment.services['catalog-api'].name == 'catalog'
        deployment.services['catalog-api'].port == 8443
    }
}
