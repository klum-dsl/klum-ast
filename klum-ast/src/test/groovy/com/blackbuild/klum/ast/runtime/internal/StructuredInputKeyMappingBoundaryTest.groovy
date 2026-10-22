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

/** Characterizes an admitted relationship whose normal creator does not preserve literal input keys. */
@Issue('932')
class StructuredInputKeyMappingBoundaryTest extends AbstractDSLSpec {

    def 'admitted keyed owned map remaps explicit child keys and can collapse distinct literal keys'() {
        given:
        createClass '''
            package mappedboundary
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                @Field(keyMapping = { it.route })
                Map<String, Entry> entries
            }
            @DSL class Entry {
                @Key String name
                String route
                String value
            }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('mappedboundary.Configuration', false, binaryLoader)

        when:
        def plan = StructuredInputPlan.read(configuration)

        then:
        plan.entries().get('entries').role().name() == 'KEYED_OWNED_MAP'
        plan.entries().get('entries').source() == 'mappedboundary.Configuration#entries'
        plan.entries().get('entries').keyType() == 'java.lang.String'
        plan.rejections().empty

        when:
        def model = getClass('mappedboundary.Configuration').Create.With {
            entry('a/b~c') { route 'mapped'; value 'first' }
            entry('second') { route 'mapped'; value 'second' }
        }

        then:
        model.entries.keySet() == ['mapped'] as Set
        model.entries.mapped.name == 'second'
        model.entries.mapped.value == 'second'
        !model.entries.containsKey('a/b~c')
        !model.entries.containsKey('second')

        cleanup:
        binaryLoader?.close()
    }
}
