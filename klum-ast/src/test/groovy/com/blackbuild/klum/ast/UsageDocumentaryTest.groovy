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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class UsageDocumentaryTest extends AbstractDSLSpec {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Issue("110")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Usage.md#schema---model---consumer")
    def "loads a configured model through its classpath entry point"() {
        given:
        def classPathRoot = temporaryFolder.newFolder()
        def marker = new File(classPathRoot, "META-INF/klum-model/pk.Deployment.properties")
        marker.parentFile.mkdirs()
        createClass '''
            package pk

            @DSL
            class Deployment {
                String endpoint
            }
        '''
        createSecondaryClass '''
            package impl

            import pk.Deployment

            Deployment.Create.With {
                endpoint 'https://catalog.example.test'
            }
        ''', "CatalogDeployment.groovy"
        marker.text = "model-class: impl.CatalogDeployment"
        loader.addURL(classPathRoot.toURI().toURL())

        when:
        def deployment = clazz.Create.FromClasspath()

        then:
        deployment.endpoint == "https://catalog.example.test"
    }
}
