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

@Issue('829')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Convenience-Factories.md#compiled-root-recipe-overlays')
class RootRecipeOverlayDocumentaryTest extends AbstractDSLSpec {
    def 'binds a selected completed environment before application validation'() {
        given:
        createClass '''
            package orders
            @DSL class OrderEnvironment { String region }
            @DSL class OrderApplication {
                @Key String name
                @Required @Field(FieldType.LINK) OrderEnvironment environment
                String service
                String image
                @Default void chooseImage() { image ?= "${service}:${environment.region}" }
                @Validate void valid() { assert image.endsWith(environment.region) }
            }
        '''
        createSecondaryClass('''
            package orders
            @groovy.transform.BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            service 'orders'
        ''', 'OrderApplicationScript.groovy')
        def client = createSecondaryClass '''
            package orders
            class OrderClient {
                static OrderApplication create(OrderEnvironment selectedEnvironment) {
                    OrderApplication.Create.From(OrderApplicationScript) {
                        environment selectedEnvironment
                    }
                }
            }
        '''
        def selectedEnvironment = getClass('OrderEnvironment').Create.With(region: 'eu')

        when:
        def application = client.create(selectedEnvironment)

        then:
        application.name == 'OrderApplicationScript'
        application.environment.is(selectedEnvironment)
        application.image == 'orders:eu'
    }
}
