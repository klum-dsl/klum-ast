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

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class TemplatesDocumentaryTest extends AbstractDSLSpec {

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
}
