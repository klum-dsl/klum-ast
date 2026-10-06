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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.Unroll

@Issue("837")
class GeneratedInnerNameCollisionTest extends AbstractDSLSpec {

    @Unroll
    def "rejects #innerName before generating the #role"() {
        when:
        createClass("""
            package pk
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL $modifier class Application {
                static class $innerName { }
                $members
            }
            @DSL class Service { String name }
        """)

        then:
        MultipleCompilationErrorsException error = thrown()
        error.errorCollector.errorCount == 1
        error.message.contains('pk.Application$' + innerName)
        error.message.contains('reserved for generated KlumAST ' + role)
        error.message.contains('Rename this nested type')
        error.message.contains('@ line 5, column 17')
        !error.message.contains('Invalid duplicate class definition')

        where:
        innerName                  | modifier   | members                                                                    | role
        '_TemplateModel'           | 'abstract' | ''                                                                         | 'abstract Template implementations'
        'Builder'                  | ''         | ''                                                                         | 'Builder implementations'
        '_Factory'                 | ''         | ''                                                                         | 'factory implementations'
        '_Template'                | ''         | ''                                                                         | 'Template scope adapters'
        '_TemplateFactory'         | ''         | ''                                                                         | 'Template factory adapters'
        '_services'                | ''         | 'List<Service> services'                                                   | 'collection/Cluster factory implementations'
        '_services'                | ''         | '@Cluster Map<String, Service> getServices() { null }; Service primary'      | 'collection/Cluster factory implementations'
        '_dates_converterClosures' | ''         | '@Field(converters = [{long value -> new Date(value)}]) List<Date> dates'    | 'converter closure implementations'
    }

    @Unroll
    def "generator output confirms the #role binary name"() {
        when:
        createClass("""
            package pk
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL $modifier class Application { $members }
            @DSL class Service { String name }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        getClass('pk.Application$' + innerName).declaringClass == getClass('pk.Application')

        where:
        innerName                  | modifier   | members                                                                    | role
        '_TemplateModel'           | 'abstract' | ''                                                                         | 'abstract Template model'
        'Builder'                  | ''         | ''                                                                         | 'Builder'
        '_Factory'                 | ''         | ''                                                                         | 'factory'
        '_Template'                | ''         | ''                                                                         | 'Template scope'
        '_TemplateFactory'         | ''         | ''                                                                         | 'Template factory'
        '_services'                | ''         | 'List<Service> services'                                                   | 'collection factory'
        '_services'                | ''         | '@Cluster Map<String, Service> getServices() { null }; Service primary'      | 'Cluster factory'
        '_dates_converterClosures' | ''         | '@Field(converters = [{long value -> new Date(value)}]) List<Date> dates'    | 'converter closures'
    }

    def "permits nested implementation-like names when those types are not generated"() {
        when:
        createClass("""
            package pk
            @DSL class Application {
                static class _Recipe { }
                static class _TemplateModel { }
                static class _services { }
                static class _dates { }
                static class _FactoryHelper { }
                static class Helpers { static class _Factory { } }
                static class _dates_converterClosures { }
                List<Date> dates
            }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        getClass('pk.Application$_Recipe') != null
        getClass('pk.Application$_TemplateModel') != null
        getClass('pk.Application$_services') != null
        getClass('pk.Application$_dates') != null
        getClass('pk.Application$_FactoryHelper') != null
        getClass('pk.Application$Helpers$_Factory') != null
        getClass('pk.Application$_dates_converterClosures') != null
    }
}
