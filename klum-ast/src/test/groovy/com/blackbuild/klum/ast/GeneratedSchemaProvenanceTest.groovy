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
import spock.lang.See
import spock.lang.Tag

@Issue('896')
class GeneratedSchemaProvenanceTest extends AbstractDSLSpec {
    private static final String MARKER = 'com.blackbuild.klum.ast.runtime.generated.GeneratedSchema'

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Schema-Provenance.md#generated-compiler-provenance')
    def 'each transformed Schema declares its own compiler provenance'() {
        when:
        createClass('''
            package provenance
            @DSL abstract class Service { String name }
            @DSL class WebService extends Service { int port }
            @DSL class Catalog { List<Service> services }
            @DSL interface ServiceContract { }
            class PlainDomainApi { }
        ''')

        then:
        ['Service', 'WebService', 'Catalog'].every { name ->
            def markers = getClass('provenance.' + name).declaredAnnotations.findAll { it.annotationType().name == MARKER }
            markers.size() == 1 && markers[0].format() == 1 &&
                    markers[0].compilerVersion() == System.getProperty('klumVersion')
        }
        loader.loadedClasses.findAll { !(it.name in ['provenance.Service', 'provenance.WebService', 'provenance.Catalog']) }.every { type ->
            !type.annotations.any { it.annotationType().name == MARKER }
        }
        getClass('provenance.WebService').Create.With { name 'web'; port 8080 }.port == 8080
    }

    def 'a handwritten marker cannot replace compiler-owned provenance'() {
        when:
        createClass('''
            import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema
            @DSL @GeneratedSchema(format = 1, compilerVersion = 'consumer-project')
            class Service { }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('GeneratedSchema is compiler-owned metadata; remove the handwritten marker')
    }

    def 'annotation propagation cannot fabricate compiler provenance'() {
        when:
        createClass('''
            import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema
            import java.lang.annotation.*
            @Target(ElementType.TYPE)
            @PropagateAnnotation(GeneratedSchema)
            @interface Domain { }
            @DSL @Domain class Service { }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('has no default')
    }
}
