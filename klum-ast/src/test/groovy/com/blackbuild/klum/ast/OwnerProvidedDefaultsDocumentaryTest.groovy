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

@Issue("494")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#owner-provided-defaults")
class OwnerProvidedDefaultsDocumentaryTest extends AbstractDSLSpec {

    def "inherits conservative release defaults from an owner contract"() {
        given:
        createNonDslClass '''
            package pk

            interface ReleaseDefaults {
                String getRepository()
                List<String> getAudiences()
                ReleasePolicy getPolicy()
                Registry getRegistry()
            }

            @DSL
            class Registry {
                String url
            }

            @DSL
            class ReleasePolicy {
                @Owner Object owner
                String channel
                Integer retentionDays
            }

            @DSL
            class Product implements ReleaseDefaults {
                String repository
                List<String> audiences
                ReleasePolicy policy

                @Field(FieldType.LINK)
                Registry registry

                ProductRelease release
            }

            @DSL
            @OwnerProvidedDefaults(ReleaseDefaults)
            class ProductRelease implements ReleaseDefaults {
                @Owner Product product
                String repository
                List<String> audiences
                ReleasePolicy policy

                @Field(FieldType.LINK)
                Registry registry
            }
        '''
        def externalRegistry = getClass('pk.Registry').Create.With {
            url 'https://registry.example.test'
        }

        when:
        def product = getClass('pk.Product').Create.With {
            repository 'owner-repository'
            audiences = ['internal', 'public']
            policy {
                channel 'stable'
                retentionDays 30
            }
            registry externalRegistry
            release {
                repository 'release-specific'
                policy {
                    retentionDays 7
                }
            }
        }

        then: 'explicit recipient values remain authoritative while absent values inherit'
        product.release.repository == 'release-specific'
        product.release.audiences == ['internal', 'public']
        product.release.policy.channel == 'stable'
        product.release.policy.retentionDays == 7

        and: 'owned defaults become fresh recipient composition'
        !product.release.policy.is(product.policy)
        product.release.policy.owner.is(product.release)

        and: 'completed links retain aggregation identity'
        product.release.registry.is(externalRegistry)
    }
}
