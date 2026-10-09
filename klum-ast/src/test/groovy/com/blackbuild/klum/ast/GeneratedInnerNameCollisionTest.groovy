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

    @Unroll
    def "generated overlap between #scenario is not a user nested-type collision"() {
        when:
        createClass("""
            package pk
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL $modifier class Application { $members }
            @DSL class Service { String name }
        """)

        then:
        MultipleCompilationErrorsException error = thrown()
        // A generated/generated conflict keeps its compiler rejection without claiming a source declaration.
        error.message.contains('Invalid duplicate class definition')
        error.message.contains('pk.Application$' + innerName)
        !error.message.contains('Rename this nested type')
        !error.message.contains('reserved for generated KlumAST')

        where:
        scenario                         | innerName                  | modifier   | members
        'collection and Cluster'         | '_services'                | ''         | 'List<Service> services; Service primary; @Cluster Map<String, Service> services() { null }'
        'collection and Cluster getter'  | '_services'                | ''         | 'List<Service> services; Service primary; @Cluster Map<String, Service> getServices() { null }'
        'collection after converter'     | '_dates_converterClosures' | ''         | '@Field(converters = [{long value -> new Date(value)}]) List<Date> dates; List<Service> dates_converterClosures'
        'converter after collection'     | '_dates_converterClosures' | ''         | 'List<Service> dates_converterClosures; @Field(converters = [{long value -> new Date(value)}]) List<Date> dates'
        'Cluster after converter'        | '_dates_converterClosures' | ''         | '@Field(converters = [{long value -> new Date(value)}]) List<Date> dates; Service primary; @Cluster Map<String, Service> dates_converterClosures() { null }'
        'collection and factory'         | '_Factory'                 | ''         | 'List<Service> Factory'
        'collection and Template'        | '_Template'                | ''         | 'List<Service> Template'
        'collection and TemplateFactory' | '_TemplateFactory'         | ''         | 'List<Service> TemplateFactory'
        'collection and TemplateModel'   | '_TemplateModel'           | 'abstract' | 'List<Service> TemplateModel'
        'two Cluster methods'            | '_services'                | ''         | 'Service primary; @Cluster Map<String, Service> services() { null }; @Cluster Map<String, Service> getServices() { null }'
    }

    def "source-written generated annotation does not exempt a nested declaration"() {
        when:
        createClass("""
            package pk
            @DSL class Application {
                @KlumGenerated(generator = 'schema')
                static class _services { }
                List<Service> services
            }
            @DSL class Service { String name }
        """)

        then:
        MultipleCompilationErrorsException error = thrown()
        error.errorCollector.errorCount == 1
        error.message.contains('pk.Application$_services')
        error.message.contains('Rename this nested type')
        error.message.contains('@ line 4, column 17')
        !error.message.contains('Invalid duplicate class definition')
    }

    def "collection and Cluster factories coexist under different binary names"() {
        when:
        createClass("""
            package pk
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Application {
                List<Service> services
                Service primary
                @Cluster Map<String, Service> roles() { null }
            }
            @DSL class Service { String name }
        """)
        def application = create('pk.Application') {
            services { service { name 'collection' } }
            roles { primary { name 'cluster' } }
        }

        then:
        notThrown(MultipleCompilationErrorsException)
        application.services*.name == ['collection']
        application.primary.name == 'cluster'
        getClass('pk.Application$_services').declaringClass == getClass('pk.Application')
        getClass('pk.Application$_roles').declaringClass == getClass('pk.Application')
    }

    def "an empty Cluster leaves its collection namesake factory available"() {
        when:
        createClass("""
            package pk
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Application {
                List<Service> services
                @Cluster Map<String, Date> services() { null }
            }
            @DSL class Service { String name }
        """)
        def application = create('pk.Application') {
            services { service { name 'collection' } }
        }

        then:
        notThrown(MultipleCompilationErrorsException)
        application.services*.name == ['collection']
        application.services() == [:]
        getClass('pk.Application$_services').declaringClass == getClass('pk.Application')
    }
}
