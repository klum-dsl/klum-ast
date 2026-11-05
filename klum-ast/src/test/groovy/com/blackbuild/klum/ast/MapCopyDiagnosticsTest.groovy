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

            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class Deployment {
                @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES)
                Map<String, Service> services
            }

            @DSL
            class Service {
                @Key String name
                List<Endpoint> endpoints
                Endpoint primary
            }

            @DSL
            class Endpoint {
                @Key String name
                Integer port
                List<Integer> retries
                Map<String, Integer> limits
                Opaque opaque
                Converted converted
            }

            class Opaque {
                private Opaque() {}
            }

            class Converted {
                static final IllegalArgumentException FAILURE = new IllegalArgumentException('converter failed', new IOException('detail'))
                static Converted fromString(String value) { throw FAILURE }
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


    def 'merged existing children retain the relative input location and their keys'() {
        when:
        clazz.Create.With {
            service('eu/west~blue') { primary('edge') { port 80 } }
            copyFrom([services: ['eu/west~blue': [primary: [port: [443]]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains("key 'edge'")
        failure.message.contains('/services/eu~1west~0blue/primary/port')
        failure.breadCrumbPath == '$/d.Deployment.With'
    }

    def 'relative Map member preserves an enclosing relationship construction step'() {
        when:
        clazz.Create.With {
            service('catalog') { copyFrom(['bad/~member': true]) }
        }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('diagnostics.Service')
        failure.message.contains("key 'catalog'")
        failure.message.contains('/bad~1~0member')
        failure.breadCrumbPath == '$/d.Deployment.With/service(catalog)'
    }

    def 'FromMap retains its public Factory step and unkeyed target context'() {
        when:
        clazz.Create.FromMap(['bad/~member': true])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('diagnostics.Deployment')
        failure.message.contains('/bad~1~0member')
        !failure.message.contains("with key '")
        !(failure.message =~ /Builder@[0-9a-fA-F]+/)
        failure.breadCrumbPath == '$/d.Deployment.FromMap'
    }

    def 'a framework String coercion refusal keeps its exception policy and member context'() {
        when:
        clazz.Create.With {
            copyFrom([services: [catalog: [endpoints: [[name: 'edge', opaque: 'unsupported']]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.cause == null
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains('diagnostics.Opaque')
        failure.message.contains("key 'edge'")
        failure.message.contains('/services/catalog/endpoints/0/opaque')
    }

    def 'user converter exceptions keep their identity and cause chain'() {
        given:
        def original = getClass('diagnostics.Converted').FAILURE

        when:
        clazz.Create.With {
            copyFrom([services: [catalog: [endpoints: [[name: 'edge', converted: 'bad']]]]])
        }

        then:
        def failure = thrown(IllegalArgumentException)
        failure.is(original)
        failure.cause.is(original.cause)
    }


    def 'an incompatible Builder item uses its Schema type instead of default identity text'() {
        given:
        def serviceType = getClass('diagnostics.Service')

        when:
        clazz.Create.With {
            service('catalog') {
                def wrongItem = serviceType.Create.AsBuilder().With('other') {}
                copyFrom([endpoints: [wrongItem]])
            }
        }

        then:
        def failure = thrown(KlumModelException)
        failure.cause == null
        failure.message.contains('diagnostics.Service')
        failure.message.contains("key 'catalog'")
        failure.message.contains('/endpoints/0')
        !(failure.message =~ /Builder@[0-9a-fA-F]+/)
    }


    def 'successful Map copying does not render arbitrary scalar Map keys for diagnostics'() {
        given:
        def key = new Object() {
            boolean renderingAllowed

            @Override
            String toString() {
                if (!renderingAllowed) throw new IllegalStateException('copy must not render the key')
                'key'
            }
        }

        when:
        def result = clazz.Create.With {
            copyFrom([services: [catalog: [endpoints: [[name: 'edge', limits: [(key): 7]]]]]])
            key.renderingAllowed = true // Ordinary later lifecycle traversal may render keys.
        }

        then:
        result.services.catalog.endpoints[0].limits[key] == 7
    }


    def 'unrenderable Map keys do not replace an incompatible-value exception'() {
        given:
        def key = new Object() {
            @Override
            String toString() { throw new IllegalStateException('key must not replace failure') }
        }

        when:
        clazz.Create.With {
            copyFrom([services: [catalog: [endpoints: [[name: 'edge', limits: [(key): 'bad']]]]]])
        }

        then:
        def failure = thrown(KlumModelException)
        failure.cause == null
        failure.message.contains('diagnostics.Endpoint')
        failure.message.contains('/services/catalog/endpoints/0/limits/<')
        failure.message.contains(key.class.name)
    }

}
