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
import spock.lang.See
import spock.lang.Tag

@Tag('documentary')
class SharedCapabilitiesDocumentaryTest extends AbstractDSLSpec {

    @Issue('689')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Advanced-Techniques.md#builder-only-methods')
    def "declares Builder-only behavior with Builder Method"() {
        given:
        createClass('''
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL

            @DSL
            class Registry {
                String host

                @Builder.Method
                void normalizeHost() {
                    host = host.toLowerCase()
                }
            }
        ''')

        when:
        def registry = clazz.Create.With {
            host 'EXAMPLE.TEST'
            normalizeHost()
        }

        then:
        registry.host == 'example.test'
        hasNoMethod(clazz, 'normalizeHost')
        hasMethod(getClass('Registry_DSL$Builder'), 'normalizeHost')
    }

    @Issue('651')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Advanced-Techniques.md#sharing-a-pure-query-with-builders')
    def "shares a pure URL query between Builder lifecycle code and the completed Model"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.Builder
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry registry
                String configuredRegistryUrl

                @PostTree
                void captureRegistryUrl() {
                    configuredRegistryUrl = registry.toUrl()
                }
            }

            @DSL class Registry {
                String host

                @Builder.Query
                String toUrl() { "https://$host" }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            registry {
                host 'packages.example.test'
            }
        }

        then:
        deployment.configuredRegistryUrl == 'https://packages.example.test'
        deployment.registry.toUrl() == deployment.configuredRegistryUrl
    }

    @Issue('650')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Advanced-Techniques.md#flowing-builders-through-explicit-inputs-and-results')
    def "flows an explicitly selected Builder input into an owned Builder result"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry source
                Registry normalized

                @PostTree
                void normalizeRegistry() {
                    normalized Registry.normalized(source)
                }
            }

            @DSL class Registry {
                String host

                @Builder.Result
                static Registry normalized(@Builder.Input Registry source) {
                    Registry.Create.With(host: source.host.toLowerCase())
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            source { host 'PACKAGES.EXAMPLE.TEST' }
        }

        then:
        deployment.source.host == 'PACKAGES.EXAMPLE.TEST'
        deployment.normalized.host == 'packages.example.test'
    }

    @Issue('648')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Advanced-Techniques.md#narrowing-a-builder-by-model-type')
    def "narrows a related subtype Builder through its factory token"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.Builder
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry registry
                String configuredRegistryUrl

                @PostTree
                void captureSpecialRegistryUrl() {
                    if (SpecialRegistry.Create.isBuilder(registry)) {
                        def special = SpecialRegistry.Create.narrowBuilder(registry)
                        configuredRegistryUrl = special.toUrl()
                    }
                }
            }

            @DSL abstract class Registry {
                String host

                @Builder.Query
                String toUrl() { "https://$host" }
            }

            @DSL class SpecialRegistry extends Registry {
                String tenant
            }
        '''
        def specialFactory = getClass('SpecialRegistry').Create

        when:
        def deployment = clazz.Create.With {
            registry(specialFactory) {
                host 'packages.example.test'
                tenant 'documentation'
            }
        }

        then:
        deployment.configuredRegistryUrl == 'https://packages.example.test'
    }
}
