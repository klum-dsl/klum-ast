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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('952')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#concrete-virtual-builder-scopes')
class StructuredVirtualScopeDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    def 'a repository mapping configures its Builder before the virtual field consumes it'() {
        given:
        createClass '''
            package repositoryexample
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field

            @DSL class Registry {
                List<String> urls
                @Field void repository(Repository config) {
                    urls << config.url
                }
            }
            @DSL class Repository { String url }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def registry = Class.forName('repositoryexample.Registry', false, artifacts)

        when:
        def ordinary = registry.Create.With {
            repository { url 'https://packages.example' }
        }
        def mapped = registry.Create.FromStruct([repository: [url: 'https://packages.example']])

        then:
        mapped.urls == ['https://packages.example']
        mapped.urls == ordinary.urls

        when:
        def template = registry.Create.Template.FromStruct([repository: [url: 'https://packages.example']])
        def recipient = registry.Template.With(template) { registry.Create.One() }

        then:
        recipient.urls == ordinary.urls
    }
}
