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
import spock.lang.Issue

@Issue('646')
class BuilderFieldGenericTypeTest extends AbstractDSLSpec {

    def "Builder lifecycle methods retain simple collection and map generic types"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.AutoCreate
            import groovy.transform.CompileStatic

            @DSL
            class Parent {
                List<String> customers = []
                Map<String, Integer> balances = [:]

                @Default
                @CompileStatic
                void normalizeCustomers() {
                    customers.each { String customer ->
                        customer.split(':', 2)
                    }
                    balances.each { String customer, Integer balance ->
                        customer.split(':', 2)
                        balance.byteValue()
                    }
                }
            }

            @DSL
            class Subscription extends Parent {
                @AutoCreate
                @CompileStatic
                void normalizeInheritedCustomers() {
                    customers.each { String customer ->
                        customer.split(':', 2)
                    }
                }
            }
        '''

        when:
        def subscription = getClass('pk.Subscription').Create.With {}
        Class<?> parentBuilder = getClass('pk.Parent$Builder')

        then:
        subscription.customers == []
        parentBuilder.getMethod('getCustomers').genericReturnType.typeName == 'java.util.List<java.lang.String>'
        parentBuilder.getMethod('getBalances').genericReturnType.typeName == 'java.util.Map<java.lang.String, java.lang.Integer>'
        getClass('pk.Subscription$Builder').getMethod('getCustomers').genericReturnType.typeName == 'java.util.List<java.lang.String>'
    }

    @Issue(['646', '728'])
    def "relationship collection getters retain generated public Builder element types"() {
        given:
        createClass '''
            package pk

            @DSL
            class Order {
                List<LineItem> items = []
            }

            @DSL
            class LineItem {
                String sku
            }
        '''

        expect:
        getClass('pk.Order_DSL$Builder').getMethod('getItems').genericReturnType.typeName ==
                'java.util.List<pk.LineItem_DSL$Builder<pk.LineItem>>'
    }
}
