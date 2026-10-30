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
import spock.lang.See
import spock.lang.Tag

@Issue('989')
class CopyBuilderInputTest extends AbstractDSLSpec {

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/release/4.1.x/docs/user/Copy-Strategies.md#builder-input')
    def 'copied staging input drives a later Builder lifecycle callback'() {
        given:
        createClass '''
            @DSL
            class Service {
                String name
                String endpoint
                @Field(FieldType.BUILDER) String host

                @PostTree
                void configureEndpoint() {
                    endpoint = "https://$host"
                }
            }
        '''
        def serviceType = clazz

        when:
        def result = serviceType.Create.With {
            copyFrom([host: 'catalog.example', name: 'catalog'])
        }

        then:
        result.name == 'catalog'
        result.endpoint == 'https://catalog.example'
        !result.hasProperty('host')
    }

    def 'Map input ignores Model-only #modelOnly without Builder storage'() {
        given:
        createClass '''
            @DSL
            class Service {
                String name
                static String category = 'service'
                String getLabel() { name.toUpperCase() }
            }
        '''
        def serviceType = clazz

        when:
        def result = serviceType.Create.With {
            copyFrom([name: 'catalog', (modelOnly): 'ignored'])
        }

        then:
        result.name == 'catalog'
        result.label == 'CATALOG'
        serviceType.category == 'service'

        where:
        modelOnly << ['label', 'category']
    }

    def 'unknown Map member #unknown still fails with #policy missing policy'() {
        given:
        createServiceSchema(annotation)
        def serviceType = clazz

        when:
        serviceType.Create.With {
            copyFrom([host: 'catalog.example', name: 'catalog', (unknown): 'rejected'])
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("Field $unknown is missing in target object")

        where:
        [unknown, policy, annotation] << ['unknown', 'sealed', 'constructionSession'].collectMany { name ->
            [[name, 'default', ''],
             [name, 'explicit FAIL', '@Overwrite(missing = @Overwrite.Missing(OverwriteStrategy.Missing.FAIL))']]
        }
    }

    def 'explicit IGNORE skips unknown Map members while copying staging and ordinary state'() {
        given:
        createServiceSchema('@Overwrite(missing = @Overwrite.Missing(OverwriteStrategy.Missing.IGNORE))')
        def serviceType = clazz

        when:
        def result = serviceType.Create.With {
            copyFrom([unknown: 'ignored', host: 'catalog.example', name: 'catalog'])
        }

        then:
        result.name == 'catalog'
        result.endpoint == 'https://catalog.example'
        !result.hasProperty('host')
        !result.hasProperty('unknown')
    }

    def 'inherited Builder-only input reaches a later lifecycle callback'() {
        given:
        createClass '''
            @DSL
            class ServiceBase {
                @Field(FieldType.BUILDER) String host
            }
            @DSL
            class Service extends ServiceBase {
                String endpoint
                @PostTree
                void configureEndpoint() { endpoint = "https://$host" }
            }
        '''
        def serviceType = getClass('Service')

        when:
        def result = serviceType.Create.With { copyFrom([host: 'catalog.example']) }

        then:
        result.endpoint == 'https://catalog.example'
        !result.hasProperty('host')
    }

    def 'Template recipe replay remains intact alongside copied staging input'() {
        given:
        createServiceSchema('')
        def serviceType = clazz
        def template = serviceType.Create.Template.With {
            name 'catalog'
            applyLater { name = name + '-ready' }
        }

        when:
        def result = serviceType.Create.With {
            copyFrom template
            copyFrom([host: 'catalog.example'])
        }

        then:
        template.name == 'catalog'
        result.name == 'catalog-ready'
        result.endpoint == 'https://catalog.example'
        !result.hasProperty('host')
    }

    private void createServiceSchema(String annotation) {
        createClass """
            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            $annotation
            class Service {
                String name
                String endpoint
                @Field(FieldType.BUILDER) String host
                @PostTree
                void configureEndpoint() { endpoint = "https://\$host" }
            }
        """
    }
}
