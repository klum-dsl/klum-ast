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

import groovy.transform.NamedParams
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue('792')
class NativeNamedParamCompatibilityTest extends AbstractDSLSpec {

    private static String api() {
        '''
            package nativeparams

            import groovy.transform.NamedParam
            import groovy.transform.NamedParams

            class NamedApi {
                static Map accept(
                        @NamedParams([
                            @NamedParam(value = 'name', type = String),
                            @NamedParam(value = 'count', type = Integer)
                        ]) Map<String, ?> values) {
                    values
                }
            }
        '''
    }

    def "native named-parameter metadata supports source and binary static consumers"() {
        when: 'the API and its consumer compile in one source unit'
        createNonDslClass api() + '''
            @groovy.transform.CompileStatic
            class SourceConsumer {
                static Map create() { NamedApi.accept(name: 'source', count: 1) }
            }
        '''

        then:
        getClass('nativeparams.SourceConsumer').create() == [name: 'source', count: 1]

        when: 'a consumer is compiled separately against the emitted class file'
        Class<?> consumer = createSecondaryClass '''
            package nativeparams

            import groovy.transform.CompileStatic

            @CompileStatic
            class BinaryConsumer {
                static Map create() { NamedApi.accept(name: 'binary', count: 2) }
            }
        '''

        then:
        consumer.create() == [name: 'binary', count: 2]
        getClass('nativeparams.NamedApi').getMethod('accept', Map).parameters[0]
                .getAnnotation(NamedParams).value()*.value() == ['name', 'count']
    }

    def "native metadata rejects invalid literals including computed and spread keys"() {
        given:
        createNonDslClass api()

        when:
        createSecondaryClass invalidConsumer("NamedApi.accept(unknown: 'value')")
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass invalidConsumer("NamedApi.accept(count: 'wrong')")
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass invalidConsumer("String key = 'name'; NamedApi.accept([(key): 'computed'])")
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass invalidConsumer("Map values = [name: 'spread']; NamedApi.accept(*:values)")
        then:
        thrown(MultipleCompilationErrorsException)
    }

    private static String invalidConsumer(String body) {
        """
            package nativeparams

            import groovy.transform.CompileStatic

            @CompileStatic
            class InvalidConsumer {
                static Map create() { $body }
            }
        """
    }
}
