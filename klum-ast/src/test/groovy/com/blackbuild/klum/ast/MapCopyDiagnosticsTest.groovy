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

import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue

@Issue('992')
class MapCopyDiagnosticsTest extends AbstractDSLSpec {

    def setup() {
        createClass '''
            package diagnostics

            @DSL
            class Deployment {
                Map<String, Service> services
            }

            @DSL
            class Service {
                @Key String name
                List<Endpoint> endpoints
            }

            @DSL
            class Endpoint {
                @Key String name
                Integer port
                List<Integer> retries
                Map<String, Integer> limits
            }
        '''
    }

    def 'nested unknown Map member identifies Schema key and escaped input location'() {
        when:
        clazz.Create.With {
            copyFrom([services: ['eu/west~blue': [endpoints: [[name: 'edge', 'bad/~member': true]]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.class == KlumModelException
        failure.cause == null
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains("key 'edge'")
        failure.message.contains('/services/eu~1west~0blue/endpoints/0/bad~1~0member')
        !(failure.message =~ /Builder@[0-9a-fA-F]+/)
        failure.breadCrumbPath == '$/d.Deployment.With'
    }

    def 'nested incompatible Map value identifies Schema key and input location'() {
        when:
        clazz.Create.With {
            copyFrom([services: ['eu/west~blue': [endpoints: [[name: 'edge', port: [443]]]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.class == KlumModelException
        failure.cause == null
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains("key 'edge'")
        failure.message.contains('java.lang.Integer')
        failure.message.contains('/services/eu~1west~0blue/endpoints/0/port')
        !(failure.message =~ /Builder@[0-9a-fA-F]+/)
        failure.breadCrumbPath == '$/d.Deployment.With'
    }


    def 'incompatible container item reports its supplied #kind location'() {
        when:
        clazz.Create.With {
            copyFrom([services: ['eu/west~blue': [endpoints: [[name: 'edge', (field): value]]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.class == KlumModelException
        failure.cause == null
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains("key 'edge'")
        failure.message.contains('/services/eu~1west~0blue/endpoints/0/' + location)
        !(failure.message =~ /Builder@[0-9a-fA-F]+/)

        where:
        kind         | field     | value                          | location
        'List index' | 'retries' | [1, 'bad']                     | 'retries/1'
        'Map key'    | 'limits'  | ['eu/west~blue': 'bad']         | 'limits/eu~1west~0blue'
    }

}
