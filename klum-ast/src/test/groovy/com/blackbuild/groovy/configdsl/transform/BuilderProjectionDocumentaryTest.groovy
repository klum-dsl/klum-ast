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
@Issue("642")
class BuilderProjectionDocumentaryTest extends AbstractDSLSpec {

    @Issue("719")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Builder-First-Migration.md#relationship-creator-overloads")
    def "configures an owned relationship through a public Builder without an empty closure"() {
        given:
        createClass '''
            @DSL class Workspace {
                Repository repository
            }

            @DSL class Repository {
                String url
            }
        '''

        Class<?> consumer = createSecondaryClass('''
            import groovy.transform.CompileStatic

            @CompileStatic
            class WorkspaceConfigurer {
                static void configure(Workspace_DSL.Builder<Workspace> workspace) {
                    workspace.repository([url: 'ssh://git@example.test/catalog.git'])
                }

                static Workspace create() {
                    Workspace.Create.With {
                        WorkspaceConfigurer.configure((Workspace_DSL.Builder<Workspace>) delegate)
                    }
                }
            }
        ''', 'WorkspaceConfigurer.groovy')

        expect:
        consumer.create().repository.url == 'ssh://git@example.test/catalog.git'
    }

    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Factory-Classes.md#creator-methods-and-collection-factories")
    def "uses an unqualified static converter chain in an owned relationship"() {
        given:
        createClass '''
            import java.io.File

            @DSL class Order {
                Customer customer
            }

            @DSL class Customer {
                @Key String name
                String source

                static Customer fromFile(File file) {
                    return fromYaml(file)
                }

                static Customer fromYaml(File file) {
                    return Customer.Create.With(file.name, source: file.name)
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            customer new File('customer.yaml')
        }

        then:
        instance.customer.source == 'customer.yaml'
    }
}
