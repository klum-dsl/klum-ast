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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('932')
@Tag('documentary')
class StructuredInputDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader
    private Class app
    private Class holder
    private Class environment

    def setup() {
        createClass '''
            package documentaryinput
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.layer3.LinkTo
            @DSL class Application {
                String host
                @Default(field = 'host') String identifier
                String linkedName
                Worker worker
                int port
                @Field(FieldType.LINK) Environment environment
                @AutoLink void contextApplied() { if (environment != null) linkedName = environment.name }
                @Builder.Method void endpoint(Map<String, String> settings) {
                    host = settings.host
                    port = Integer.parseInt(settings.port)
                }
            }
            @DSL class Environment { String name }
            @DSL class Worker {
                @Owner Application application
                @Field(FieldType.LINK)
                @LinkTo(provider = { application }, field = 'environment') Environment context
                @Validate void contextBound() { assert context.name == 'supplied' }
            }
            @DSL class ApplicationHolder {
                Application application
                @Validate void contextAvailable() { assert application.environment.name == 'supplied' }
            }
        '''
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        app = Class.forName('documentaryinput.Application', false, artifactLoader)
        holder = Class.forName('documentaryinput.ApplicationHolder', false, artifactLoader)
        environment = Class.forName('documentaryinput.Environment', false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#read-a-concrete-root')
    def 'one structured operation configures several root properties'() {
        given:
        def parsed = [endpoint: [host: 'api', port: '8080']]

        when:
        def application = KlumStructuredInput.readRoot(app, null, parsed, 'application.yaml')

        then:
        application.host == 'api'
        application.port == 8080
        application.identifier == 'api'
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#apply-to-an-active-builder')
    def 'Holder supplies completed LINK context for later phases after applying an already-prepared Builder'() {
        given:
        def parsed = [endpoint: [host: 'api', port: '8080']]
        parsed.worker = [:]
        def suppliedEnvironment = environment.Create.With(name: 'supplied')
        def captured
        def applicationType = app

        when:
        def model = holder.Create.With {
            def applicationBuilder = applicationType.Create.AsBuilder().One()
            def same = KlumStructuredInput.applyToBuilder(applicationBuilder, parsed, 'application.yaml')
            assert same.is(applicationBuilder)
            assert !((InternalKlumBuilder) applicationBuilder).sealed
            applicationBuilder.environment(suppliedEnvironment)
            application(applicationBuilder)
            captured = applicationBuilder
        }

        then:
        model.application.host == 'api'
        model.application.port == 8080
        model.application.environment.is(suppliedEnvironment)
        model.application.worker.context.is(suppliedEnvironment)
        model.application.linkedName == 'supplied'
        model.application.identifier == 'api'
        captured.completedModel.is(model.application)
    }
}
