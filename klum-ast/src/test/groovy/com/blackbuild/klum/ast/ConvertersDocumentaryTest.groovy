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
class ConvertersDocumentaryTest extends AbstractDSLSpec {

    @Issue("662")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#factory-method-converters")
    def "uses fluent scalar converter syntax for an owned relationship"() {
        given:
        createClass '''
            import java.net.URI

            @DSL class Root { Storage storage }
            @DSL class Registry {
                URI uri
                static Registry fromString(String value) { Registry.Create.With(uri: new URI(value)) }
            }
            @DSL class Storage {
                Registry source
                Registry target
                static Storage fromStrings(String source, String target) {
                    Storage.Create.With(
                        source: Registry.fromString(source),
                        target: Registry.fromString(target)
                    )
                }
            }
        '''
        when:
        def root = clazz.Create.With { storage('uri://bla/blub', 'uri://bli/blu') }
        then:
        root.storage.source.uri == new URI('uri://bla/blub')
        root.storage.target.uri == new URI('uri://bli/blu')
    }

    @Issue("148")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#field-based-converters")
    def "converts timestamp input for a simple field and map entry"() {
        given:
        createClass '''
            package pk

            @DSL
            class PayrollSchedule {
                @Field(converters = [{ long timestamp -> new Date(timestamp) }])
                Date birthday

                @Field(converters = [{ long timestamp -> new Date(timestamp) }])
                Map<String, Date> payDays
            }
        '''

        when:
        def schedule = clazz.Create.With {
            birthday 123L
            payDay 'monthly', 456L
        }

        then:
        schedule.birthday.time == 123L
        schedule.payDays.monthly.time == 456L
    }

    @Issue("148")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#factory-method-converters")
    def "uses a named factory method as an owned DSL relationship creator"() {
        given:
        createClass '''
            package pk

            @DSL
            class Server {
                Endpoint endpoint
            }

            @DSL
            class Endpoint {
                int port

                static Endpoint fromPort(int port) {
                    Endpoint.Create.With(port: port)
                }
            }
        '''
        def endpointType = getClass('pk.Endpoint')

        when:
        def rootEndpoint = endpointType.fromPort(8443)
        def server = clazz.Create.With {
            endpoint 8443
        }

        then:
        rootEndpoint.port == 8443
        server.endpoint.port == 8443
    }

    @Issue("148")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#factory-classes")
    def "uses a converter factory class for convention and annotation-based inputs"() {
        given:
        createClass '''
            package pk

            @Converters(EditionConverters)
            @DSL
            class Deployment {
                Edition edition
            }

            class Edition {
                String label
            }

            class EditionConverters {
                static Edition fromNumber(long number) {
                    new Edition(label: "release-${number}")
                }

                @Converter
                static Edition readLabel(String label) {
                    new Edition(label: label)
                }
            }
        '''

        when:
        def numbered = clazz.Create.With {
            edition 4L
        }
        def named = clazz.Create.With {
            edition 'stable'
        }

        then:
        numbered.edition.label == 'release-4'
        named.edition.label == 'stable'
    }

    @Issue("148")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#customization")
    def "uses an opt-in URI constructor as a converter"() {
        given:
        createClass '''
            package pk

            @Converters(includeConstructors = true)
            @DSL
            class RemoteService {
                URI endpoint
            }
        '''

        when:
        def service = clazz.Create.With {
            endpoint 'https', 'config.example.test', '/v1', 'stable'
        }

        then:
        service.endpoint == new URI('https', 'config.example.test', '/v1', 'stable')
    }
}
